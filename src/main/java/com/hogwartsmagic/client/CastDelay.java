package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.EnumSet;
import java.util.Set;

/**
 * Charms whose move builds up to the release (circles, a stir, a key turned and pushed home): the cast is sent
 * to the server when the move lets the spell go, not on the press, so the build-up comes before the spell
 * instead of after it. Only charms cast with a plain press: held and channelled spells, and the fighting
 * spells, are sent at once as before. The arm starts its move on the press as usual (FirstPersonCast).
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class CastDelay {

    private static final Set<SpellType> DELAYED = EnumSet.of(SpellType.CONFUNDO, SpellType.REPARO,
            SpellType.CAVE_INIMICUM, SpellType.CAELUM_FASCINO, SpellType.HEAL, SpellType.MORSMORDRE,
            SpellType.COLLOPORTUS, SpellType.RIDDIKULUS);

    /**
     * Spells sent on the press, with no anticipation: shields and blocks must be instant, held and channelled
     * spells have their own key handling, and the Killing Curse has its own charge.
     */
    private static final Set<SpellType> NO_WAIT = EnumSet.of(SpellType.PROTEGO, SpellType.PROTEGO_MAXIMA,
            SpellType.ALOHOMORA, SpellType.REVELIO, SpellType.CRUCIO, SpellType.FIENDFYRE,
            SpellType.EXPECTO_PATRONUM, SpellType.WINGARDIUM_LEVIOSA, SpellType.IMPERIO, SpellType.AQUA_ERUCTO,
            SpellType.APPARITION, SpellType.TENEBRIS_FLIGHT, SpellType.AVADA_KEDAVRA, SpellType.LUMOS);
    /** Ticks the arm cocks back before a move strikes (the anticipation), for the spells that have one. */
    public static final float ANTICIPATION = 2.0F;

    private static SpellType pending;
    private static int due;

    private CastDelay() {
    }

    /** Ticks between the press and the cast for {@code spell}: its move's release, less the tick a beam takes. */
    public static int ticks(SpellType spell) {
        if (spell == null || NO_WAIT.contains(spell)) {
            return 0;
        }
        FirstPersonWandRig.Motion motion = FirstPersonWandRig.motionOf(spell, FirstPersonCast.forSpell(spell));
        if (DELAYED.contains(spell)) {
            return Math.max(0, Math.round(motion.release()) - 1);
        }
        // The arm cocks back, then strikes: the spell leaves on the strike, about a tenth of a second on.
        return Math.max(0, Math.round(ANTICIPATION + FirstPersonWand.releaseAfter(motion)) - 1);
    }

    /** How long {@code spell}'s move cocks back before it strikes, in ticks; 0 for none. */
    public static float anticipation(SpellType spell) {
        return spell == null || NO_WAIT.contains(spell) || DELAYED.contains(spell) ? 0.0F : ANTICIPATION;
    }

    /** Sends a plain cast of {@code spell} now, or when its move lets it go. */
    public static void send(Player player, SpellType spell) {
        int wait = ticks(spell);
        if (pending != null) {
            sendNow(); // a new press before the last one went: let that one go first
        }
        if (wait <= 0 || !(player instanceof LocalPlayer)) {
            sendNow(null);
            return;
        }
        pending = spell;
        due = player.tickCount + wait;
    }

    private static void sendNow() {
        sendNow(pending);
    }

    private static void sendNow(SpellType spell) {
        pending = null;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        // A delayed cast goes only if the same spell is still ready on a wand in hand.
        if (spell != null && (!MagicWandItem.isAnyWand(player.getMainHandItem())
                || MagicWandItem.getSelectedSpell(player.getMainHandItem()) != spell)) {
            return;
        }
        SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, false, false, false),
                PacketDistributor.SERVER.noArg());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending == null) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            pending = null;
            return;
        }
        if (player.tickCount >= due || player.tickCount < due - 40) {
            sendNow();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        pending = null;
    }
}
