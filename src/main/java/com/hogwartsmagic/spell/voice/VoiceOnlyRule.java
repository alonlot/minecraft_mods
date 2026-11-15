package com.hogwartsmagic.spell.voice;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code voiceOnlySpells} gamerule: while it is true, the server casts a player's spell only when the player
 * said it ({@link VoiceStamp}). Keys, clicks, the radial wheel and the slot keys still select spells but cast
 * nothing. Casts made for a possessed puppet ({@code PossessionManager}) and casts the server makes itself (the
 * Avada Kedavra wind-up finishing) are not player casts and are not checked.
 *
 * <p>Server side only. The value is sent to each client on login and on every change, so the client can say why a
 * click does nothing instead of swinging the wand for no reason.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VoiceOnlyRule {
    public static final String NAME = "voiceOnlySpells";
    private static GameRules.Key<GameRules.BooleanValue> key;
    private static final Map<UUID, VoiceStamp> STAMPS = new ConcurrentHashMap<>();
    /** Game time of the last refusal line per player, so a held key does not flood the action bar. */
    private static final Map<UUID, Long> LAST_NOTICE = new ConcurrentHashMap<>();
    private static final long NOTICE_GAP_TICKS = 20;

    private VoiceOnlyRule() {
    }

    /** Called once from the mod constructor, before any world loads. */
    public static void register() {
        if (key == null) {
            key = GameRules.register(NAME, GameRules.Category.PLAYER,
                    GameRules.BooleanValue.create(false, (server, value) -> broadcast(server, value.get())));
        }
    }

    public static boolean isOn(ServerPlayer player) {
        return key != null && player.serverLevel().getGameRules().getBoolean(key);
    }

    /** The client heard a spell (and, for {@code selfCast}, the player was sneaking). */
    public static void stamp(ServerPlayer player, String spellId, boolean selfCast) {
        STAMPS.put(player.getUUID(), new VoiceStamp(spellId, selfCast, player.level().getGameTime()));
    }

    /**
     * The gate at the top of the cast packet handler. A release always passes (and ends the stamp). Otherwise,
     * with the gamerule on, only a cast or heartbeat the player's stamp covers passes.
     */
    public static boolean allowsCastPacket(ServerPlayer player, String selectedId, boolean release, boolean heartbeat,
                                           boolean selfCast) {
        if (release) {
            STAMPS.remove(player.getUUID());
            return true;
        }
        if (!isOn(player)) {
            return true;
        }
        VoiceStamp stamp = STAMPS.get(player.getUUID());
        long now = player.level().getGameTime();
        if (stamp != null && selectedId != null && stamp.allowsCast(selectedId, heartbeat, selfCast, now)) {
            return true;
        }
        if (!heartbeat) {
            notice(player, now);
        }
        return false;
    }

    /** The Imperio Master hold start, which comes in its own packet. */
    public static boolean allowsHoldStart(ServerPlayer player, String selectedId) {
        if (!isOn(player)) {
            return true;
        }
        VoiceStamp stamp = STAMPS.get(player.getUUID());
        return stamp != null && stamp.allowsHoldStart(selectedId, player.level().getGameTime());
    }

    private static void notice(ServerPlayer player, long now) {
        Long last = LAST_NOTICE.get(player.getUUID());
        if (last == null || now - last >= NOTICE_GAP_TICKS) {
            LAST_NOTICE.put(player.getUUID(), now);
            player.displayClientMessage(Component.translatable("gui.hogwartsmagic.voice_only")
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    private static void broadcast(MinecraftServer server, boolean on) {
        SpellPackets.CHANNEL.send(new SpellPackets.VoiceOnlyRulePacket(on), PacketDistributor.ALL.noArg());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpellPackets.CHANNEL.send(new SpellPackets.VoiceOnlyRulePacket(isOn(player)),
                    PacketDistributor.PLAYER.with(player));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STAMPS.remove(event.getEntity().getUUID());
        LAST_NOTICE.remove(event.getEntity().getUUID());
    }
}
