package com.hogwartsmagic.spell.dark;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.mind.PossessionManager;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.UUID;

/**
 * Per-player Darkness, stored as {@code PlayerPersisted/<modid>/DarkMagic} (float, two decimals) with
 * {@code DarkMagicLastDarkCast} (game time of the last paid dark cast) and {@code DarkMagicDecayProgress} (online
 * quiet ticks toward the next point of decay). Forge copies PlayerPersisted across death, so it survives dying.
 * Server only: the client gets its own effective value through {@link SpellPackets.SyncDarkMagicPacket} and keeps it
 * in client/DarkMagicClientState. Every change goes through write, which syncs and announces a tier change.
 */
public final class DarkMagicData {
    public static final String KEY = "DarkMagic";
    public static final String KEY_LAST_DARK_CAST = "DarkMagicLastDarkCast";
    public static final String KEY_DECAY_PROGRESS = "DarkMagicDecayProgress";
    private static final String KEY_TIER_UP = "gui.hogwartsmagic.dark.tier_up";
    private static final String KEY_TIER_DOWN = "gui.hogwartsmagic.dark.tier_down";

    private DarkMagicData() {
    }

    /** The stored Darkness, 0..100, whatever the toggle says. A missing key (every existing player) reads 0. */
    public static float getStored(Player player) {
        return DarkRules.sanitize(PlayerMagicData.getModData(player).getFloat(KEY));
    }

    /** What the world and the client see: the stored value, or 0 with enableDarkMagic off. */
    public static float get(ServerPlayer player) {
        return DarkRules.effective(Config.isDarkMagicEnabled(), getStored(player));
    }

    /** The tier every cost and reward reads (Phases 2 and 3): 0 with enableDarkMagic off. */
    public static int getTier(ServerPlayer player) {
        return DarkRules.effectiveTier(Config.isDarkMagicEnabled(), getStored(player));
    }

    /**
     * Losses stop here: DarkRules.HORCRUX_FLOOR while the player's Horcrux exists (bound and forged), else none. The
     * binding is cleared when the Horcrux shatters (rebirth) or is reconciled away (destroyed), so the floor goes too.
     */
    public static float floor(ServerPlayer player) {
        return DarkRewardRules.floor(com.hogwartsmagic.util.HorcruxData.hasActiveHorcrux(player)
                && !com.hogwartsmagic.util.HorcruxData.isRitualActive(player));
    }

    /**
     * The cast hook: called right after a cast's mana is paid (castAs main block, Fiendfyre channel start), hit or
     * miss. A puppet cast charges the puppeteer; any other forced cast charges nobody; creative and spectator payers
     * gain nothing. A paid dark cast also restarts the quiet window and the decay progress.
     */
    public static void onDarkCastPaid(ServerPlayer body, SpellType spell) {
        if (!Config.isDarkMagicEnabled()) {
            return;
        }
        float amount = DarkRules.gain(spell.getId(), Config.getDarkMagicGainMultiplier());
        if (!(amount > 0.0f)) {
            return;
        }
        UUID puppeteer = PossessionManager.castingPuppeteer;
        DarkRules.Payer who = DarkRules.payer(puppeteer != null, PossessionManager.isForcedCaster(body));
        ServerPlayer payer = null;
        if (who == DarkRules.Payer.BODY) {
            payer = body;
        } else if (who == DarkRules.Payer.PUPPETEER) {
            payer = body.server.getPlayerList().getPlayer(puppeteer);
        }
        if (payer == null || !DarkRules.earns(payer.isCreative(), payer.isSpectator())) {
            return;
        }
        CompoundTag modData = PlayerMagicData.getModData(payer);
        modData.putLong(KEY_LAST_DARK_CAST, payer.level().getGameTime());
        modData.putInt(KEY_DECAY_PROGRESS, 0);
        PlayerMagicData.saveModData(payer, modData);
        write(payer, DarkRules.afterGain(getStored(payer), amount));
    }

    /** Deeds and redemption (Phase 3) and the command: a signed change through the floor and the clamp. */
    public static void add(ServerPlayer player, float delta) {
        write(player, DarkRules.add(getStored(player), delta, floor(player)));
    }

    /** The command only: writes the value as given (clamped and rounded), ignoring the floor. */
    public static void set(ServerPlayer player, float value) {
        write(player, value);
    }

    /**
     * From ModEvents.onPlayerTick (server, END phase). Does nothing on 199 ticks out of 200, before any config or
     * persistent-data read. On the check tick: 200 ticks of online quiet time toward the next point, through the floor.
     */
    public static void tickDecay(ServerPlayer player) {
        if (!DarkRules.dueToDecayCheck(player.tickCount, player.getId()) || !Config.isDarkMagicEnabled()) {
            return;
        }
        CompoundTag modData = PlayerMagicData.getModData(player);
        float stored = DarkRules.sanitize(modData.getFloat(KEY));
        int progress = modData.getInt(KEY_DECAY_PROGRESS);
        if (stored <= DarkRules.MIN_DARKNESS && progress == 0) {
            return;
        }
        boolean quiet = DarkRules.quiet(player.level().getGameTime(), modData.getLong(KEY_LAST_DARK_CAST));
        DarkRules.Decay next = DarkRules.decay(stored, progress, DarkRules.DECAY_CHECK_INTERVAL_TICKS, quiet,
                floor(player), Config.getDarkMagicDecayTicks());
        if (next.progressTicks() != progress) {
            modData.putInt(KEY_DECAY_PROGRESS, next.progressTicks());
            PlayerMagicData.saveModData(player, modData);
        }
        if (next.darkness() != stored) {
            write(player, next.darkness());
        }
    }

    /** Sends this player their own effective Darkness (never anyone else's) and re-applies the Lost health cost. */
    public static void sync(ServerPlayer player) {
        SpellPackets.CHANNEL.send(new SpellPackets.SyncDarkMagicPacket(get(player)),
                PacketDistributor.PLAYER.with(player));
        // Phase 2: every sync point (each write, so each tier change; login, respawn, dimension change; a config
        // reload, so the toggle) also re-applies the Lost health cost and drops the NPCs' darkest-online cache.
        DarkCosts.refreshMaxHealth(player);
        DarkCosts.invalidatePresence();
    }

    /** After a config reload (the toggle may have changed): resend everyone online, on the server thread. */
    public static void broadcastAfterReload() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                sync(player);
            }
        });
    }

    private static void write(ServerPlayer player, float value) {
        boolean enabled = Config.isDarkMagicEnabled();
        CompoundTag modData = PlayerMagicData.getModData(player);
        float before = DarkRules.sanitize(modData.getFloat(KEY));
        float after = DarkRules.sanitize(value);
        modData.putFloat(KEY, after);
        PlayerMagicData.saveModData(player, modData);
        sync(player);
        int oldTier = DarkRules.effectiveTier(enabled, before);
        int newTier = DarkRules.effectiveTier(enabled, after);
        if (newTier != oldTier) {
            announce(player, oldTier, newTier);
        }
    }

    /** One chat line and one sound, to this player only: nobody else learns a tier. */
    private static void announce(ServerPlayer player, int oldTier, int newTier) {
        boolean darker = DarkRules.tierChange(oldTier, newTier) > 0;
        int colour = DarkRules.tierColor(newTier) & 0xFFFFFF;
        Component tierName = Component.translatable(DarkRules.tierKey(newTier))
                .withStyle(style -> style.withColor(colour));
        player.sendSystemMessage(Component.translatable(darker ? KEY_TIER_UP : KEY_TIER_DOWN, tierName)
                .withStyle(darker ? ChatFormatting.DARK_GREEN : ChatFormatting.GRAY));
        if (darker) {
            player.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.8f, 0.5f);
        } else {
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.2f);
        }
    }
}
