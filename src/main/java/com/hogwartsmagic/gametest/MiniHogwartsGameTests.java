package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ambience.AmbienceNet;
import com.hogwartsmagic.ambience.AmbienceRegistry;
import com.hogwartsmagic.ambience.AmbientEffect;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The Mini Hogwarts interiors: the ambience registry and the sync that carries it to clients. */
@GameTestHolder(ExampleMod.MODID)
public final class MiniHogwartsGameTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";

    private MiniHogwartsGameTests() {
    }

    /**
     * A real login with a fresh player object: {@code placeNewPlayer} places the player and posts
     * PlayerLoggedInEvent, which the ambience events hear. No hand-called listener.
     */
    private static ServerPlayer login(GameTestHelper helper, ServerPlayer old) {
        var server = helper.getLevel().getServer();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(old.getGameProfile(), false);
        ServerPlayer np = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var conn = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(conn);
        server.getPlayerList().placeNewPlayer(conn, np, cookie);
        np.setOnGround(true);
        return np;
    }

    private static AmbientEffect ceiling() {
        return new AmbientEffect(UUID.randomUUID(), AmbientEffect.KIND_CEILING, 100.5, 70, -40.5, 12f, 10f, 8f, 0f, 0,
                null, 5L);
    }

    private static AmbienceNet.AmbienceSyncPacket lastSyncTo(List<Object[]> sent, ServerPlayer p) {
        AmbienceNet.AmbienceSyncPacket last = null;
        for (Object[] s : sent) {
            if (s[0] == p && s[1] instanceof AmbienceNet.AmbienceSyncPacket sync) {
                last = sync;
            }
        }
        return last;
    }

    private static void assertCarries(GameTestHelper helper, AmbienceNet.AmbienceSyncPacket sync, ServerPlayer p,
                                      AmbientEffect effect, String when) {
        helper.assertTrue(sync != null, "no AmbienceSyncPacket was sent on " + when);
        helper.assertTrue(sync.dimension().equals(p.serverLevel().dimension().location().toString()),
                "the sync on " + when + " names the player's own dimension, got " + sync.dimension());
        helper.assertTrue(sync.all().contains(effect), "the sync on " + when + " carries the registered effect");
    }

    @GameTest(template = EMPTY, batch = "ambience_sync", timeoutTicks = 100)
    public static void syncSentOnLoginAndDimensionChange(GameTestHelper helper) {
        List<Object[]> sent = new ArrayList<>();
        AmbienceNet.setSendObserverForTests((player, packet) -> sent.add(new Object[]{player, packet}));
        var level = helper.getLevel();
        AmbienceRegistry registry = AmbienceRegistry.get(level);
        AmbientEffect effect = ceiling();
        registry.add(level, effect);
        ServerPlayer mock = helper.makeMockServerPlayerInLevel();
        // The mock is already in the player list; log it out so the real login below is the only player of that profile.
        level.getServer().getPlayerList().remove(mock);
        ServerPlayer p = null;
        try {
            p = login(helper, mock);
            helper.assertTrue(p.serverLevel() == level, "the test player logs in to the test level");
            assertCarries(helper, lastSyncTo(sent, p), p, effect, "login");

            sent.clear();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent(
                            p, Level.NETHER, level.dimension()));
            assertCarries(helper, lastSyncTo(sent, p), p, effect, "dimension change");

            sent.clear();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent(p, false));
            assertCarries(helper, lastSyncTo(sent, p), p, effect, "respawn");

            // A level with no effects still syncs, so the client drops whatever it held.
            registry.remove(level, effect.id());
            sent.clear();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent(p, false));
            AmbienceNet.AmbienceSyncPacket empty = lastSyncTo(sent, p);
            helper.assertTrue(empty != null && !empty.all().contains(effect), "the removed effect is not synced");
        } finally {
            AmbienceNet.setSendObserverForTests(null);
            registry.remove(level, effect.id());
            if (p != null) {
                level.getServer().getPlayerList().remove(p);
            }
        }
        LOGGER.info("MINIHOGWARTS-GAMETEST PASS syncSentOnLoginAndDimensionChange");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "ambience_delta", timeoutTicks = 100)
    public static void addAndRemoveBroadcastDeltas(GameTestHelper helper) {
        List<Object[]> sent = new ArrayList<>();
        AmbienceNet.setSendObserverForTests((player, packet) -> sent.add(new Object[]{player, packet}));
        var level = helper.getLevel();
        AmbienceRegistry registry = AmbienceRegistry.get(level);
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        AmbientEffect effect = ceiling();
        try {
            registry.add(level, effect);
            boolean added = sent.stream().anyMatch(s -> s[0] == p && s[1] instanceof AmbienceNet.AmbienceDeltaPacket d
                    && d.add() && d.effect().equals(effect));
            helper.assertTrue(added, "add broadcasts an add delta to the level's players");
            helper.assertTrue(registry.ceilingAt(new net.minecraft.world.phys.Vec3(100.5, 65, -40.5)) != null,
                    "a point under the ceiling finds it");
            helper.assertTrue(registry.remove(level, effect.id()), "remove reports success");
            boolean removed = sent.stream().anyMatch(s -> s[0] == p && s[1] instanceof AmbienceNet.AmbienceDeltaPacket d
                    && !d.add() && d.effect().id().equals(effect.id()));
            helper.assertTrue(removed, "remove broadcasts a remove delta");
            helper.assertTrue(!registry.remove(level, effect.id()), "a second remove reports false");
        } finally {
            AmbienceNet.setSendObserverForTests(null);
            registry.remove(level, effect.id());
        }
        LOGGER.info("MINIHOGWARTS-GAMETEST PASS addAndRemoveBroadcastDeltas");
        helper.succeed();
    }
}
