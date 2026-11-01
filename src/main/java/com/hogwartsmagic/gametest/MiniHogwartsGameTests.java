package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ambience.AmbienceNet;
import com.hogwartsmagic.ambience.AmbienceRegistry;
import com.hogwartsmagic.ambience.AmbientEffect;
import net.minecraft.core.BlockPos;
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
            helper.assertTrue(registry.ceilingAt(new net.minecraft.world.phys.Vec3(100.5, 71, -40.5), 2) != null,
                    "a point just above the plane finds it");
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

    // ---------------------------------------------------------------- the castle as a structure

    /** Width, height and length of the small stand-in castle the structure tests place instead of the real one. */
    private static final int TW = 20, TH = 8, TL = 20;
    /** Test-castle locals of its two cave-air cells: inside the walls, and under the ledge. */
    private static final BlockPos CAVE_INSIDE = new BlockPos(5, 2, 5), CAVE_UNDER_LEDGE = new BlockPos(18, 2, 7);

    /**
     * A 20 x 8 x 20 stand-in for the 1.5M-block castle, so the structure tests stay cheap: a stone-brick floor with
     * a cobblestone perimeter wall over x 0..15, and an oak-plank ledge at y 4 over x 16..19 with nothing under it,
     * so the stone skirt has something to fill. Two cave-air cells (the schematic's "clear this" marker): one inside
     * the wall at {@link #CAVE_INSIDE}, and one under the ledge at {@link #CAVE_UNDER_LEDGE}, where it must neither
     * count as the column's lowest solid block nor be filled by the skirt.
     */
    private static com.hogwartsmagic.worldgen.MiniHogwartsSchematic testCastle() {
        net.minecraft.world.level.block.state.BlockState[] palette = {
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(),
                net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState(),
                net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(),
                net.minecraft.world.level.block.Blocks.CAVE_AIR.defaultBlockState()
        };
        short[] cells = new short[TW * TH * TL];
        for (int y = 0; y < TH; y++) {
            for (int z = 0; z < TL; z++) {
                for (int x = 0; x < TW; x++) {
                    short id = 0;
                    if (x < 16) {
                        if (y == 0) {
                            id = 1;
                        } else if (y <= 4 && (x == 0 || x == 15 || z == 0 || z == TL - 1)) {
                            id = 2;
                        }
                    } else if (y == 4) {
                        id = 3;
                    }
                    if ((x == CAVE_INSIDE.getX() && y == CAVE_INSIDE.getY() && z == CAVE_INSIDE.getZ())
                            || (x == CAVE_UNDER_LEDGE.getX() && y == CAVE_UNDER_LEDGE.getY()
                            && z == CAVE_UNDER_LEDGE.getZ())) {
                        id = 4;
                    }
                    cells[x + z * TW + y * TW * TL] = id;
                }
            }
        }
        return com.hogwartsmagic.worldgen.MiniHogwartsSchematic.forTests(TW, TH, TL, palette, cells);
    }

    /** Forces (and loads) the chunks within {@code radius} of {@code centre}; returns them for release. */
    private static List<net.minecraft.world.level.ChunkPos> forceChunks(net.minecraft.server.level.ServerLevel level,
                                                                       net.minecraft.world.level.ChunkPos centre,
                                                                       int radius) {
        List<net.minecraft.world.level.ChunkPos> forced = new ArrayList<>();
        for (int cx = -radius; cx <= radius; cx++) {
            for (int cz = -radius; cz <= radius; cz++) {
                var c = new net.minecraft.world.level.ChunkPos(centre.x + cx, centre.z + cz);
                level.setChunkForced(c.x, c.z, true);
                level.getChunk(c.x, c.z); // a test, not a tick path: load it now so the placement sees it
                forced.add(c);
            }
        }
        return forced;
    }

    private static void release(net.minecraft.server.level.ServerLevel level,
                                List<net.minecraft.world.level.ChunkPos> forced) {
        for (var c : forced) {
            level.setChunkForced(c.x, c.z, false);
        }
    }

    /**
     * One chunk's {@code postProcess} on a castle that straddles four chunks writes only inside that chunk's box:
     * every block of the piece's volume outside it is unchanged, and every mob it furnished stands inside it.
     */
    @GameTest(template = EMPTY, batch = "mh_structure_piece", timeoutTicks = 100)
    public static void pieceWritesOnlyInsideChunkBox(GameTestHelper helper) {
        var level = helper.getLevel();
        // Far from every test structure, so the castle cannot touch another test.
        BlockPos far = helper.absolutePos(BlockPos.ZERO).offset(3072, 0, 3072);
        var chunk = new net.minecraft.world.level.ChunkPos(far);
        List<net.minecraft.world.level.ChunkPos> forced = forceChunks(level, chunk, 2);
        com.hogwartsmagic.worldgen.MiniHogwartsSchematic.overrideForTests(testCastle());
        try {
            int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                    chunk.getMinBlockX() + 8, chunk.getMinBlockZ() + 8);
            // x and z run 8..27 into the chunk: the castle covers this chunk and three neighbours.
            BlockPos anchor = new BlockPos(chunk.getMinBlockX() + 8, ground - 2, chunk.getMinBlockZ() + 8);
            var piece = new com.hogwartsmagic.worldgen.structure.MiniHogwartsPiece(anchor);
            var pieceBox = piece.getBoundingBox();
            var chunkBox = new net.minecraft.world.level.levelgen.structure.BoundingBox(
                    chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                    chunk.getMaxBlockX(), level.getMaxBuildHeight(), chunk.getMaxBlockZ());

            java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> outside = new java.util.HashMap<>();
            int minY = Math.max(level.getMinBuildHeight(), pieceBox.minY());
            int maxY = Math.min(level.getMaxBuildHeight() - 1, pieceBox.maxY());
            for (BlockPos p : BlockPos.betweenClosed(pieceBox.minX() - 1, minY, pieceBox.minZ() - 1,
                    pieceBox.maxX() + 1, maxY, pieceBox.maxZ() + 1)) {
                if (!chunkBox.isInside(p)) {
                    outside.put(p.immutable(), level.getBlockState(p));
                }
            }
            var mobArea = net.minecraft.world.phys.AABB.of(pieceBox).inflate(2);
            java.util.Set<UUID> before = new java.util.HashSet<>();
            for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, mobArea)) {
                before.add(m.getUUID());
            }

            piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(),
                    level.getRandom(), chunkBox, chunk, anchor);

            int changed = 0;
            for (var e : outside.entrySet()) {
                if (level.getBlockState(e.getKey()) != e.getValue()) {
                    changed++;
                    if (changed == 1) {
                        LOGGER.error("first block changed outside the chunk box: {}", e.getKey());
                    }
                }
            }
            helper.assertTrue(changed == 0, changed + " blocks changed outside the chunk box");
            helper.assertTrue(level.getBlockState(anchor).is(net.minecraft.world.level.block.Blocks.STONE_BRICKS),
                    "the chunk's own part of the castle was placed");
            for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, mobArea)) {
                if (!before.contains(m.getUUID())) {
                    helper.assertTrue(chunkBox.isInside(m.blockPosition()),
                            "a furnished mob stands outside the chunk box at " + m.blockPosition());
                    m.discard();
                }
            }
        } finally {
            com.hogwartsmagic.worldgen.MiniHogwartsSchematic.overrideForTests(null);
            release(level, forced);
        }
        LOGGER.info("MINIHOGWARTS-GAMETEST PASS pieceWritesOnlyInsideChunkBox");
        helper.succeed();
    }

    /**
     * The structure placed through the vanilla {@code /place structure} code path: every schematic cell stands in
     * the world, the overhanging ledge has its stone skirt down to the ground, cave-air cells clear what was there,
     * and the tracker learned the castle.
     */
    @GameTest(template = EMPTY, batch = "mh_structure_place", timeoutTicks = 100)
    public static void structurePlacesWholeCastle(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos far = helper.absolutePos(BlockPos.ZERO).offset(-3072, 0, 3072);
        var chunk = new net.minecraft.world.level.ChunkPos(far);
        List<net.minecraft.world.level.ChunkPos> forced = forceChunks(level, chunk, 3);
        var schem = testCastle();
        com.hogwartsmagic.worldgen.MiniHogwartsSchematic.overrideForTests(schem);
        BlockPos anchor;
        try {
            var structure = level.registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                    .getHolderOrThrow(com.hogwartsmagic.worldgen.structure.ModStructures.MINI_HOGWARTS);
            var source = level.getServer().createCommandSourceStack().withLevel(level)
                    .withPosition(net.minecraft.world.phys.Vec3.atCenterOf(far)).withSuppressedOutput();
            // The castle is centred on the start chunk's middle column, its base two blocks under the ground.
            int ground = level.getChunkSource().getGenerator().getFirstOccupiedHeight(chunk.getMiddleBlockX(),
                    chunk.getMiddleBlockZ(), net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
                    level, level.getChunkSource().randomState());
            anchor = new BlockPos(chunk.getMiddleBlockX() - TW / 2, Math.max(level.getMinBuildHeight() + 1, ground - 2),
                    chunk.getMiddleBlockZ() - TL / 2);
            // Stone where the inner cave-air cell will land: the castle must clear it.
            level.setBlock(anchor.offset(CAVE_INSIDE), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
            try {
                net.minecraft.server.commands.PlaceCommand.placeStructure(source, structure, far);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                throw new net.minecraft.gametest.framework.GameTestAssertException("placement failed: " + e.getMessage());
            }
            int wrong = 0;
            for (int y = 0; y < TH; y++) {
                for (int z = 0; z < TL; z++) {
                    for (int x = 0; x < TW; x++) {
                        var want = schem.blockAt(x, y, z);
                        if (!want.isAir() && level.getBlockState(anchor.offset(x, y, z)) != want) {
                            wrong++;
                        }
                    }
                }
            }
            helper.assertTrue(wrong == 0, wrong + " schematic cells do not match the world");
            // Under the ledge (y 4) the skirt fills the air down to the first solid block.
            for (int z = 2; z < TL; z += 5) {
                BlockPos under = anchor.offset(18, 3, z);
                helper.assertTrue(level.getBlockState(under).is(net.minecraft.world.level.block.Blocks.STONE),
                        "no stone skirt under the ledge at " + under + ", found " + level.getBlockState(under));
            }
            // Cave air is written: it replaced the stone placed there beforehand.
            helper.assertTrue(level.getBlockState(anchor.offset(CAVE_INSIDE)).is(net.minecraft.world.level.block.Blocks.CAVE_AIR),
                    "the cave-air cell did not clear the pre-placed stone, found "
                            + level.getBlockState(anchor.offset(CAVE_INSIDE)));
            // Under the ledge, cave air is neither support (the skirt still starts right under the ledge) nor
            // filled by the skirt.
            BlockPos ledgeSkirt = anchor.offset(CAVE_UNDER_LEDGE).above();
            helper.assertTrue(level.getBlockState(ledgeSkirt).is(net.minecraft.world.level.block.Blocks.STONE),
                    "no skirt between the ledge and the cave air at " + ledgeSkirt);
            helper.assertTrue(level.getBlockState(anchor.offset(CAVE_UNDER_LEDGE)).is(net.minecraft.world.level.block.Blocks.CAVE_AIR),
                    "the skirt filled the cave-air cell under the ledge");
        } finally {
            com.hogwartsmagic.worldgen.MiniHogwartsSchematic.overrideForTests(null);
            release(level, forced);
        }
        BlockPos placed = anchor;
        helper.succeedWhen(() -> {
            var castle = com.hogwartsmagic.worldgen.MiniHogwartsTracker.get(level).findClosest(placed);
            helper.assertTrue(castle != null && placed.equals(castle.anchor()),
                    "the tracker has not recorded the castle at " + placed);
            for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                    new net.minecraft.world.phys.AABB(placed).inflate(40))) {
                m.discard();
            }
            LOGGER.info("MINIHOGWARTS-GAMETEST PASS structurePlacesWholeCastle");
        });
    }

    /** The "Hogwarts" title card comes once per player per castle, and a second player still gets their own. */
    @GameTest(template = EMPTY, batch = "mh_title", timeoutTicks = 100)
    public static void titleShownOncePerPlayer(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerPlayer p1 = helper.makeMockServerPlayerInLevel();
        ServerPlayer p2 = helper.makeMockServerPlayerInLevel();
        try {
            var stand = helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1.0, 1.5));
            p1.moveTo(stand.x, stand.y, stand.z);
            p2.moveTo(stand.x, stand.y, stand.z);
            // A castle whose title point is in plain sight, two blocks up and away inside the empty test area.
            BlockPos centre = BlockPos.containing(stand).offset(1, 1, 0);
            var castle = com.hogwartsmagic.worldgen.MiniHogwartsTracker.get(level).add(centre, null);
            helper.assertTrue(com.hogwartsmagic.worldgen.MiniHogwartsManager.titlePoint(castle).equals(centre),
                    "the title point is the centre for now");

            helper.assertTrue(com.hogwartsmagic.worldgen.MiniHogwartsManager.maybeShowTitle(p1) == castle,
                    "the first look shows the title");
            helper.assertTrue(castle.titled().contains(p1.getUUID()), "the castle remembers the player");
            helper.assertTrue(com.hogwartsmagic.worldgen.MiniHogwartsManager.maybeShowTitle(p1) == null,
                    "the title is not shown twice");
            helper.assertTrue(com.hogwartsmagic.worldgen.MiniHogwartsManager.maybeShowTitle(p2) == castle,
                    "another player gets their own title");

            // Out of range: nothing, and nothing remembered.
            ServerPlayer p3 = helper.makeMockServerPlayerInLevel();
            try {
                p3.moveTo(stand.x + 200, stand.y, stand.z);
                helper.assertTrue(com.hogwartsmagic.worldgen.MiniHogwartsManager.maybeShowTitle(p3) == null,
                        "no title from 200 blocks away");
                helper.assertTrue(!castle.titled().contains(p3.getUUID()), "a far player is not marked");
            } finally {
                level.getServer().getPlayerList().remove(p3);
            }
        } finally {
            level.getServer().getPlayerList().remove(p1);
            level.getServer().getPlayerList().remove(p2);
        }
        LOGGER.info("MINIHOGWARTS-GAMETEST PASS titleShownOncePerPlayer");
        helper.succeed();
    }
}
