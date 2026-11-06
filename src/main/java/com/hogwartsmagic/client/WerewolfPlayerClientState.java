package com.hogwartsmagic.client;

import com.hogwartsmagic.entity.LycanthropeVillagerEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.lycanthropy.Lycanthropy;
import com.hogwartsmagic.network.SpellPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client: cursed players in beast form, and the werewolf drawn in each one's place. The stand-in is a
 * {@link LycanthropeVillagerEntity} that never joins the world, kept in werewolf form and driven from the player
 * (position, sprint = the hunting gallop, arm swing = a claw swipe), so it moves with the real werewolf's animator.
 */
public final class WerewolfPlayerClientState {
    private static final class Beast {
        /** Play the villager-to-werewolf morph when the stand-in is built. */
        boolean morphIn;
        /** Turning back: drawn until the morph-to-human clip has played, then dropped. */
        boolean leaving;
        boolean lastSwinging;
        LycanthropeVillagerEntity surrogate;
    }

    private static final Map<UUID, Beast> BEASTS = new ConcurrentHashMap<>();

    private WerewolfPlayerClientState() {
    }

    public static void accept(SpellPackets.SyncWerewolfPlayerPacket packet) {
        UUID id = packet.playerId();
        byte action = packet.action();
        Beast beast = BEASTS.get(id);
        if (action == Lycanthropy.SHOW_HUMAN) {
            BEASTS.remove(id);
        } else if (action == Lycanthropy.SHOW_BEAST) {
            if (beast == null || beast.leaving) {
                BEASTS.put(id, new Beast());
            }
        } else if (action == Lycanthropy.MORPH_TO_BEAST) {
            Beast fresh = new Beast();
            fresh.morphIn = true;
            BEASTS.put(id, fresh);
        } else if (action == Lycanthropy.MORPH_TO_HUMAN) {
            if (beast != null && beast.surrogate != null && !beast.leaving) {
                beast.leaving = true;
                beast.surrogate.clientSetSurrogateForm(false); // plays the morph back to human
            } else {
                BEASTS.remove(id);
            }
        } else if (action == Lycanthropy.HOWL) {
            if (beast != null && beast.surrogate != null && !beast.leaving) {
                beast.surrogate.clientHowl();
            }
        }
    }

    /** Draw this player as the beast? */
    public static boolean isShown(Player player) {
        return BEASTS.containsKey(player.getUUID());
    }

    /** The local player is a beast: no human hands in first person. */
    public static boolean localIsBeast() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && isShown(minecraft.player);
    }

    /** The stand-in for this frame, built on first use; null when the player is not a beast. */
    public static LycanthropeVillagerEntity surrogateFor(Player player, float partialTick) {
        Beast beast = BEASTS.get(player.getUUID());
        if (beast == null) {
            return null;
        }
        LycanthropeVillagerEntity surrogate = ensureSurrogate(player, beast);
        if (surrogate == null) {
            return null;
        }
        PolyjuiceClientState.copyState(player, surrogate, partialTick);
        surrogate.clientSyncMorphBeforeRender();
        return surrogate;
    }

    private static LycanthropeVillagerEntity ensureSurrogate(Player player, Beast beast) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        // A dimension change replaces the ClientLevel: a stand-in built in the old one is rebuilt here.
        if (beast.surrogate != null && beast.surrogate.level() == level) {
            return beast.surrogate;
        }
        LycanthropeVillagerEntity surrogate = ModEntities.LYCANTHROPE_VILLAGER.get().create(level);
        if (surrogate == null) {
            return null;
        }
        surrogate.setId(-1);
        surrogate.setPos(player.getX(), player.getY(), player.getZ());
        surrogate.tickCount = player.tickCount; // the morph clip is timed from tickCount
        if (beast.morphIn && !beast.leaving) {
            surrogate.clientSyncMorphBeforeRender(); // first sight: human
            surrogate.clientSetSurrogateForm(true);  // and the change plays from there
        } else {
            surrogate.clientSetSurrogateForm(!beast.leaving);
        }
        beast.morphIn = false;
        beast.surrogate = surrogate;
        return surrogate;
    }

    /** Once per client tick for every player in the level: animate the stand-in, drop it once the change back ends. */
    public static void tick(Player player) {
        Beast beast = BEASTS.get(player.getUUID());
        if (beast == null) {
            return;
        }
        LycanthropeVillagerEntity surrogate = ensureSurrogate(player, beast);
        if (surrogate == null) {
            return;
        }
        surrogate.xo = player.xo;
        surrogate.yo = player.yo;
        surrogate.zo = player.zo;
        surrogate.setAggressive(player.isSprinting() && !beast.leaving);
        PolyjuiceClientState.advanceLiving(player, surrogate, 1.0F);
        boolean swinging = player.swinging;
        if (swinging && !beast.lastSwinging && !beast.leaving) {
            surrogate.clientSwipe();
        }
        beast.lastSwinging = swinging;
        if (beast.leaving && !surrogate.isClientMorphingToHuman()) {
            BEASTS.remove(player.getUUID());
        }
    }

    /** Logging out: nothing from the last server's players may leak into the next world. */
    public static void clearAll() {
        BEASTS.clear();
    }
}
