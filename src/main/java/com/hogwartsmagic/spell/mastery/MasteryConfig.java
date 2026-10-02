package com.hogwartsmagic.spell.mastery;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * The mastery numbers every rank and cost calculation reads. The config file is COMMON, so a client
 * has its own copy that can disagree with the server's. The server sends its values on login and
 * after a config reload ({@link SpellPackets.SyncMasteryConfigPacket}); client-side reads (Grimoire,
 * spell wheel, cost pre-check) use that copy, server-side reads always use the server's own config.
 * Pure Java plus common Minecraft types: safe on a dedicated server.
 */
public final class MasteryConfig {
    public record Values(int adept, int expert, int master, double rankDiscount, double manaPerRank, double manaCap) {
        /** Validated thresholds: the defaults if the three values aren't strictly rising. */
        public int[] thresholds() {
            return MasteryMath.thresholdsOrDefault(adept, expert, master);
        }
    }

    /** The copy the server sent; null until the first sync and after logout. Client side only. */
    private static volatile Values synced;

    private MasteryConfig() {
    }

    public static Values server() {
        return new Values(Config.masteryXpAdept, Config.masteryXpExpert, Config.masteryXpMaster,
                Config.getMasteryRankDiscount(), Config.getMasteryManaPerRank(), Config.getMasteryManaCap());
    }

    public static Values forSide(boolean clientSide) {
        Values copy = synced;
        return clientSide && copy != null ? copy : server();
    }

    public static Values of(Player player) {
        return forSide(player.level().isClientSide());
    }

    /** Client: store the server's values, clamped to the same ranges the config spec allows. */
    public static void acceptSynced(Values values) {
        synced = new Values(values.adept(), values.expert(), values.master(),
                MasteryMath.clampFinite(values.rankDiscount(), 0.0, 0.3, 0.08),
                MasteryMath.clampFinite(values.manaPerRank(), 0.0, 100.0, 1.0),
                MasteryMath.clampFinite(values.manaCap(), 0.0, 100000.0, 50.0));
    }

    public static void clearSynced() {
        synced = null;
    }

    public static void sendTo(ServerPlayer player) {
        Values values = server();
        SpellPackets.CHANNEL.send(new SpellPackets.SyncMasteryConfigPacket(values.adept(), values.expert(),
                        values.master(), values.rankDiscount(), values.manaPerRank(), values.manaCap()),
                PacketDistributor.PLAYER.with(player));
    }

    /**
     * After the config file is edited and reloaded: resend the values and the (possibly changed) max
     * mana to everyone online. ModConfigEvent.Reloading can arrive on the config watcher thread, so the
     * work is handed to the server thread. No server running (main menu) means nothing to do.
     */
    public static void broadcastAfterReload() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                sendTo(player);
                PlayerMagicData.resyncMana(player);
            }
        });
    }
}
