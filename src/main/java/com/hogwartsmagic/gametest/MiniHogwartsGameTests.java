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

    /** The title card looks at the anchors' {@code title_point} when the castle has one, else at the centre. */
    @GameTest(template = EMPTY, batch = "mh_title_point", timeoutTicks = 100)
    public static void titlePointUsesTheAnchor(GameTestHelper helper) {
        BlockPos anchor = new BlockPos(-5000, 70, -5000);
        BlockPos centre = anchor.offset(10, 3, 10);
        var castle = new com.hogwartsmagic.worldgen.MiniHogwartsTracker.Castle(centre, anchor,
                new java.util.HashSet<>(), new java.util.HashSet<>());
        var without = com.hogwartsmagic.worldgen.MiniHogwartsAnchors.parse(anchorsJson(null));
        var with = com.hogwartsmagic.worldgen.MiniHogwartsAnchors.parse(anchorsJson("[3, 4, 5]"));
        try {
            com.hogwartsmagic.worldgen.MiniHogwartsAnchors.overrideForTests(without);
            helper.assertTrue(com.hogwartsmagic.worldgen.MiniHogwartsManager.titlePoint(castle).equals(centre),
                    "no title_point: the centre");
            com.hogwartsmagic.worldgen.MiniHogwartsAnchors.overrideForTests(with);
            helper.assertTrue(com.hogwartsmagic.worldgen.MiniHogwartsManager.titlePoint(castle).equals(anchor.offset(3, 4, 5)),
                    "title_point: the anchor plus the local point, got "
                            + com.hogwartsmagic.worldgen.MiniHogwartsManager.titlePoint(castle));
            var oldSave = new com.hogwartsmagic.worldgen.MiniHogwartsTracker.Castle(centre, null,
                    new java.util.HashSet<>(), new java.util.HashSet<>());
            helper.assertTrue(com.hogwartsmagic.worldgen.MiniHogwartsManager.titlePoint(oldSave).equals(centre),
                    "a castle with no recorded anchor keeps its centre");
        } finally {
            com.hogwartsmagic.worldgen.MiniHogwartsAnchors.overrideForTests(null);
        }
        LOGGER.info("MINIHOGWARTS-GAMETEST PASS titlePointUsesTheAnchor");
        helper.succeed();
    }

    private static com.google.gson.JsonObject anchorsJson(String titlePoint) {
        return com.google.gson.JsonParser.parseString("{\"hall_ceiling\": {\"min\": [0, 0], \"max\": [1, 1], \"y\": 5},"
                + " \"hall_box\": {\"min\": [0, 0, 0], \"max\": [1, 1, 1]}, \"main_entrance\": [0, 1, 0]"
                + (titlePoint == null ? "" : ", \"title_point\": " + titlePoint) + "}").getAsJsonObject();
    }

    // ---------------------------------------------------------------- castle guardian statues

    /** A real survival player (the helper's mock is creative, which every statue ignores). */
    private static ServerPlayer survivalPlayer(GameTestHelper helper, net.minecraft.world.phys.Vec3 at) {
        var server = helper.getLevel().getServer();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "mh-survivor"), false);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var conn = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(conn);
        server.getPlayerList().placeNewPlayer(conn, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.moveTo(at.x, at.y, at.z);
        return player;
    }

    private static com.hogwartsmagic.entity.LivingStatueEntity castleStatue(GameTestHelper helper, BlockPos relative) {
        var statue = helper.spawn(com.hogwartsmagic.entity.ModEntities.LIVING_STATUE.get(), relative);
        statue.guardCastle(helper.absolutePos(relative));
        return statue;
    }

    /** A castle statue next to a survival player stays stone, while a ruin guardian beside it rises for the same player. */
    @GameTest(template = EMPTY, batch = "mh_castle_statue_castleStatueIgnoresPlayers", timeoutTicks = 120)
    public static void castleStatueIgnoresPlayers(GameTestHelper helper) {
        var castle = castleStatue(helper, new BlockPos(0, 2, 1));
        var ruin = helper.spawn(com.hogwartsmagic.entity.ModEntities.LIVING_STATUE.get(), new BlockPos(2, 2, 1));
        ServerPlayer player = survivalPlayer(helper, helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 1.5)));
        helper.runAtTickTime(70, () -> {
            try {
                helper.assertTrue(castle.isCastle(), "the statue is a castle statue");
                helper.assertTrue(!ruin.isDormant(), "the control ruin guardian rose for the survival player");
                helper.assertTrue(castle.isDormant(), "the castle statue stayed dormant beside a survival player");
                helper.assertTrue(castle.getTarget() == null, "the castle statue never targets a player");
                helper.assertTrue(player.getHealth() > 0, "the player lives");
                LOGGER.info("MINIHOGWARTS-GAMETEST PASS castleStatueIgnoresPlayers");
                helper.succeed();
            } finally {
                helper.getLevel().getServer().getPlayerList().remove(player);
                ruin.discard();
                castle.discard();
            }
        });
    }

    /** A zombie inside five blocks wakes it. */
    @GameTest(template = EMPTY, batch = "mh_castle_statue_castleStatueWakesForZombie", timeoutTicks = 100)
    public static void castleStatueWakesForZombie(GameTestHelper helper) {
        var statue = castleStatue(helper, new BlockPos(0, 2, 1));
        var zombie = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(2, 2, 1));
        zombie.setPersistenceRequired();
        helper.runAtTickTime(40, () -> {
            try {
                helper.assertTrue(!statue.isDormant(), "a zombie five blocks away did not wake the castle statue");
                LOGGER.info("MINIHOGWARTS-GAMETEST PASS castleStatueWakesForZombie");
                helper.succeed();
            } finally {
                zombie.discard();
                statue.discard();
            }
        });
    }

    /** Opening a chest in earshot wakes a ruin guardian but not a castle statue. */
    @GameTest(template = EMPTY, batch = "mh_castle_statue_castleStatueIgnoresChestAlarm", timeoutTicks = 100)
    public static void castleStatueIgnoresChestAlarm(GameTestHelper helper) {
        var castle = castleStatue(helper, new BlockPos(0, 2, 12));
        var ruin = helper.spawn(com.hogwartsmagic.entity.ModEntities.LIVING_STATUE.get(), new BlockPos(2, 2, 12));
        BlockPos chest = new BlockPos(1, 2, 1);
        helper.setBlock(chest, net.minecraft.world.level.block.Blocks.CHEST);
        ServerPlayer player = survivalPlayer(helper, helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 2.5)));
        try {
            var hit = new net.minecraft.world.phys.BlockHitResult(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 3, 1.5)),
                    net.minecraft.core.Direction.UP, helper.absolutePos(chest), false);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(
                            player, net.minecraft.world.InteractionHand.MAIN_HAND, helper.absolutePos(chest), hit));
            helper.assertTrue(!ruin.isDormant(), "the control ruin guardian heard the chest");
            helper.assertTrue(castle.isDormant(), "the castle statue did not wake for a castle chest");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
            ruin.discard();
            castle.discard();
        }
        LOGGER.info("MINIHOGWARTS-GAMETEST PASS castleStatueIgnoresChestAlarm");
        helper.succeed();
    }

    /**
     * A player's hit makes it crumble instead of fighting back, it reforms dormant at its post, and a mob's
     * killing blow does the same instead of destroying it. Nothing it does drops loot.
     */
    @GameTest(template = EMPTY, batch = "mh_castle_statue_castleStatueCrumblesAndReformsAtItsPost", timeoutTicks = 240)
    public static void castleStatueCrumblesAndReformsAtItsPost(GameTestHelper helper) {
        var statue = castleStatue(helper, new BlockPos(0, 2, 1));
        BlockPos post = helper.absolutePos(new BlockPos(0, 2, 1));
        // Walked off its post, as after a fight.
        net.minecraft.world.phys.Vec3 away = helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2, 1.5));
        statue.teleportTo(away.x, away.y, away.z);
        ServerPlayer player = survivalPlayer(helper, helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 3.5)));
        // Far enough that it never wakes the statue once it has reformed; it is only the source of a blow later.
        var zombie = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(10, 2, 2));
        zombie.setPersistenceRequired();
        try {
            statue.hurt(statue.damageSources().playerAttack(player), 4.0F);
            helper.assertTrue(statue.isCrumbling(), "a player's hit makes it crumble");
            helper.assertTrue(statue.getTarget() == null, "it does not fight back");
            helper.assertTrue(statue.getHealth() == statue.getMaxHealth(), "the hit cost it nothing");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(!statue.isCrumbling() && statue.isDormant(), "it is dormant again after the fall");
            helper.assertTrue(statue.blockPosition().equals(post), "it reformed at its post, not at " + statue.blockPosition());
            // A killing blow from a mob is a fall too, never a death.
            statue.hurt(statue.damageSources().mobAttack(zombie), 1000.0F);
            helper.assertTrue(statue.isAlive() && statue.isCrumbling() && statue.getHealth() >= 1.0F,
                    "a killing blow from a mob makes it crumble instead of dying");
        });
        helper.runAtTickTime(130, () -> {
            try {
                helper.assertTrue(statue.isAlive() && statue.isDormant() && statue.blockPosition().equals(post),
                        "it stands dormant at its post again");
                // Even a command kill (which bypasses the sentry rule) leaves nothing to farm.
                statue.hurt(statue.damageSources().genericKill(), 1000.0F);
            } catch (RuntimeException e) {
                zombie.discard();
                statue.discard();
                throw e;
            }
        });
        helper.runAtTickTime(220, () -> {
            try {
                helper.assertTrue(!statue.isAlive(), "a command kill still removes it");
                var items = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(post).inflate(8));
                helper.assertTrue(items.isEmpty(), "a castle statue dropped " + items);
                LOGGER.info("MINIHOGWARTS-GAMETEST PASS castleStatueCrumblesAndReformsAtItsPost");
                helper.succeed();
            } finally {
                zombie.discard();
                statue.discard();
            }
        });
    }

    /** A castle statue survives a save: the flag, the post and the facing it returns to. */
    @GameTest(template = EMPTY, batch = "mh_castle_statue_castleStatueSurvivesASave", timeoutTicks = 40)
    public static void castleStatueSurvivesASave(GameTestHelper helper) {
        var statue = helper.spawn(com.hogwartsmagic.entity.ModEntities.LIVING_STATUE.get(), new BlockPos(0, 2, 1));
        BlockPos post = helper.absolutePos(new BlockPos(0, 2, 1));
        statue.guardCastle(post, 90.0F);
        var tag = new net.minecraft.nbt.CompoundTag();
        statue.addAdditionalSaveData(tag);
        helper.assertTrue(tag.getBoolean("Castle"), "the save carries Castle");
        var reborn = helper.spawn(com.hogwartsmagic.entity.ModEntities.LIVING_STATUE.get(), new BlockPos(2, 2, 1));
        reborn.readAdditionalSaveData(tag);
        try {
            helper.assertTrue(reborn.isCastle() && !reborn.isBound(), "it loads as a castle statue, not a sentry");
            helper.assertTrue(post.equals(reborn.getHomePos()), "it keeps its post");
        } finally {
            statue.discard();
            reborn.discard();
        }
        LOGGER.info("MINIHOGWARTS-GAMETEST PASS castleStatueSurvivesASave");
        helper.succeed();
    }

    // ---------------------------------------------------------------- the anchors furnish the castle

    /**
     * A 20 x 8 x 20 stand-in with its inside cleared (cave air, as the generator writes it): a perimeter wall of
     * cobblestone four high, a stone-brick floor, and a spruce-slab stool at local (5, 1, 15).
     */
    private static com.hogwartsmagic.worldgen.MiniHogwartsSchematic furnishedCastle() {
        net.minecraft.world.level.block.state.BlockState[] palette = {
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(),
                net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState(),
                net.minecraft.world.level.block.Blocks.CAVE_AIR.defaultBlockState(),
                net.minecraft.world.level.block.Blocks.SPRUCE_SLAB.defaultBlockState()
        };
        short[] cells = new short[TW * TH * TL];
        for (int y = 0; y < TH; y++) {
            for (int z = 0; z < TL; z++) {
                for (int x = 0; x < 16; x++) {
                    short id = 0;
                    boolean rim = x == 0 || x == 15 || z == 0 || z == TL - 1;
                    if (y == 0) {
                        id = 1;
                    } else if (rim && y <= 4) {
                        id = 2;
                    } else if (!rim && y <= 6) {
                        id = 3;
                    }
                    if (x == 5 && y == 1 && z == 15) {
                        id = 4;
                    }
                    cells[x + z * TW + y * TW * TL] = id;
                }
            }
        }
        return com.hogwartsmagic.worldgen.MiniHogwartsSchematic.forTests(TW, TH, TL, palette, cells);
    }

    /** The stand-in castle's anchors: one of each furnishing, spread over all four chunks, and one bogus chest. */
    private static com.hogwartsmagic.worldgen.MiniHogwartsAnchors furnishedAnchors() {
        return com.hogwartsmagic.worldgen.MiniHogwartsAnchors.parse(com.google.gson.JsonParser.parseString("""
                {"chests": [{"at": [3, 1, 3], "facing": "east", "theme": "GREAT_HALL"},
                            {"at": [4, 1, 3], "facing": "east", "theme": "NO_SUCH_THEME"}],
                 "student_spots": [[3, 1, 10]],
                 "elf_spots": [[3, 1, 14]],
                 "statues": [{"at": [10, 1, 10], "yaw": 90}],
                 "pensieve": [8, 1, 3],
                 "fat_lady": {"wall": [0, 2, 12], "facing": "east"},
                 "paintings": [{"at": [0, 2, 6], "facing": "east"}],
                 "banners": [{"at": [14, 3, 6], "facing": "west", "house": "gryffindor"}],
                 "hall_ceiling": {"min": [1, 1], "max": [14, 18], "y": 6},
                 "hall_box": {"min": [1, 1, 1], "max": [14, 5, 18]},
                 "main_entrance": [1, 1, 1],
                 "stool": [5.5, 1.5, 15.5]}
                """).getAsJsonObject());
    }

    /**
     * The anchors place each furnishing in the chunk that holds it: chest, Pensieve, banner, painting, Fat Lady,
     * statue, student, elf and the Sorting Hat all stand where the anchors say, the painting and the Fat Lady
     * pass the game's own {@code survives()}, and an unknown chest theme is skipped.
     */
    @GameTest(template = EMPTY, batch = "mh_furnish", timeoutTicks = 200)
    public static void anchorsFurnishTheirChunks(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos far = helper.absolutePos(BlockPos.ZERO).offset(-3072, 0, -3072);
        var chunk = new net.minecraft.world.level.ChunkPos(far);
        List<net.minecraft.world.level.ChunkPos> forced = forceChunks(level, chunk, 2);
        // A freshly forced chunk only lets entity queries see its entities once the server has ticked it up to
        // entity-ticking, which takes a few ticks and is not the same tick for every chunk; wait for the four
        // the castle covers, then furnish.
        whenEntityTicking(helper, level, chunk, 40, () -> furnishAndCheck(helper, level, chunk, forced));
    }

    private static void whenEntityTicking(GameTestHelper helper, net.minecraft.server.level.ServerLevel level,
                                          net.minecraft.world.level.ChunkPos chunk, int triesLeft, Runnable then) {
        boolean ready = true;
        for (int cx = 0; cx <= 1; cx++) {
            for (int cz = 0; cz <= 1; cz++) {
                ready &= level.isPositionEntityTicking(new net.minecraft.world.level.ChunkPos(chunk.x + cx, chunk.z + cz)
                        .getMiddleBlockPosition(0));
            }
        }
        if (ready) {
            then.run();
        } else {
            helper.assertTrue(triesLeft > 0, "the chunks never became entity-ticking");
            helper.runAfterDelay(2, () -> whenEntityTicking(helper, level, chunk, triesLeft - 1, then));
        }
    }

    private static void furnishAndCheck(GameTestHelper helper, net.minecraft.server.level.ServerLevel level,
                                        net.minecraft.world.level.ChunkPos chunk,
                                        List<net.minecraft.world.level.ChunkPos> forced) {
        com.hogwartsmagic.worldgen.MiniHogwartsSchematic.overrideForTests(furnishedCastle());
        com.hogwartsmagic.worldgen.MiniHogwartsAnchors.overrideForTests(furnishedAnchors());
        net.minecraft.world.phys.AABB area = null;
        try {
            int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                    chunk.getMinBlockX() + 8, chunk.getMinBlockZ() + 8);
            BlockPos anchor = new BlockPos(chunk.getMinBlockX() + 8, ground - 2, chunk.getMinBlockZ() + 8);
            var piece = new com.hogwartsmagic.worldgen.structure.MiniHogwartsPiece(anchor);
            area = net.minecraft.world.phys.AABB.of(piece.getBoundingBox()).inflate(2);
            for (int cx = 0; cx <= 1; cx++) {
                for (int cz = 0; cz <= 1; cz++) {
                    var c = new net.minecraft.world.level.ChunkPos(chunk.x + cx, chunk.z + cz);
                    var box = new net.minecraft.world.level.levelgen.structure.BoundingBox(
                            c.getMinBlockX(), level.getMinBuildHeight(), c.getMinBlockZ(),
                            c.getMaxBlockX(), level.getMaxBuildHeight(), c.getMaxBlockZ());
                    piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(),
                            level.getRandom(), box, c, anchor);
                }
            }

            var chestState = level.getBlockState(anchor.offset(3, 1, 3));
            helper.assertTrue(chestState.is(net.minecraft.world.level.block.Blocks.CHEST), "no chest at the anchor");
            helper.assertTrue(chestState.getValue(net.minecraft.world.level.block.ChestBlock.FACING)
                    == net.minecraft.core.Direction.EAST, "the chest faces the way the anchor says");
            var chestBe = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(anchor.offset(3, 1, 3));
            helper.assertTrue(chestBe != null && !chestBe.isEmpty(), "the Great Hall chest is stocked");
            helper.assertTrue(!level.getBlockState(anchor.offset(4, 1, 3)).is(net.minecraft.world.level.block.Blocks.CHEST),
                    "a chest with an unknown theme is skipped");
            helper.assertTrue(level.getBlockState(anchor.offset(8, 1, 3)).is(ExampleMod.PENSIEVE.get()),
                    "no Pensieve at the anchor");
            var banner = level.getBlockState(anchor.offset(14, 3, 6));
            helper.assertTrue(banner.is(net.minecraft.world.level.block.Blocks.RED_WALL_BANNER)
                            && banner.getValue(net.minecraft.world.level.block.WallBannerBlock.FACING) == net.minecraft.core.Direction.WEST,
                    "no red wall banner at the anchor, found " + banner);

            var paintings = level.getEntitiesOfClass(net.minecraft.world.entity.decoration.Painting.class, area);
            helper.assertTrue(paintings.size() == 1, "expected one painting, found " + paintings.size() + "; entities in the area: "
                    + level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, area).stream()
                    .map(e -> e.getType().toShortString() + "@" + e.blockPosition().toShortString()).toList());
            helper.assertTrue(paintings.get(0).survives(), "the painting does not survive where it hangs");
            helper.assertTrue(paintings.get(0).getDirection() == net.minecraft.core.Direction.EAST,
                    "the painting faces the way the anchor says");
            var ladies = level.getEntitiesOfClass(com.hogwartsmagic.portrait.FatLadyPortraitEntity.class, area);
            helper.assertTrue(ladies.size() == 1, "expected one Fat Lady, found " + ladies.size());
            helper.assertTrue(ladies.get(0).survives(), "the Fat Lady does not survive where she hangs");
            helper.assertTrue(ladies.get(0).getPos().equals(anchor.offset(1, 2, 12)),
                    "the Fat Lady hangs in front of the wall block, found " + ladies.get(0).getPos());

            var statues = level.getEntitiesOfClass(com.hogwartsmagic.entity.LivingStatueEntity.class, area);
            helper.assertTrue(statues.size() == 1, "expected one statue, found " + statues.size() + "; entities in the area: "
                    + level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, area).stream()
                    .map(e -> e.getType().toShortString() + "@" + e.blockPosition().toShortString()).toList()
                    + ", block at post " + level.getBlockState(anchor.offset(10, 1, 10)) + " / "
                    + level.getBlockState(anchor.offset(10, 2, 10)));
            helper.assertTrue(statues.get(0).isCastle() && statues.get(0).isDormant()
                            && anchor.offset(10, 1, 10).equals(statues.get(0).getHomePos())
                            && statues.get(0).getYRot() == 90.0F,
                    "the statue is a dormant castle statue posted at its anchor, facing its yaw");
            helper.assertTrue(level.getEntitiesOfClass(com.hogwartsmagic.entity.StudentEntity.class, area).size() == 1,
                    "expected one student at the student spot");
            var elves = level.getEntitiesOfClass(com.hogwartsmagic.entity.HouseElfEntity.class, area);
            helper.assertTrue(elves.size() == 1 && elves.get(0).blockPosition().equals(anchor.offset(3, 1, 14)),
                    "expected one house elf at the elf spot");
            var hats = level.getEntitiesOfClass(net.minecraft.world.entity.Display.ItemDisplay.class, area);
            helper.assertTrue(hats.size() == 1, "expected one Sorting Hat display, found " + hats.size());
            helper.assertTrue(hats.get(0).blockPosition().equals(anchor.offset(5, 1, 15)),
                    "the Sorting Hat sits on the stool, found " + hats.get(0).blockPosition());
            helper.assertTrue("hogwartsmagic:sorting_hat".equals(
                            hats.get(0).saveWithoutId(new net.minecraft.nbt.CompoundTag()).getCompound("item").getString("id")),
                    "the display shows the Sorting Hat");
        } finally {
            com.hogwartsmagic.worldgen.MiniHogwartsSchematic.overrideForTests(null);
            com.hogwartsmagic.worldgen.MiniHogwartsAnchors.overrideForTests(null);
            if (area != null) {
                for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, area,
                        e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
                    e.discard();
                }
            }
            release(level, forced);
        }
        LOGGER.info("MINIHOGWARTS-GAMETEST PASS anchorsFurnishTheirChunks");
        helper.succeed();
    }

    /**
     * The shipped anchors against the shipped castle: every chest, Pensieve, statue and student cell is free
     * floor, the stool is a slab, the Fat Lady and every painting find a spot where the game's own survival rules
     * hold, and banners have a wall behind them. Reads the schematic only, so nothing is placed.
     */
    @GameTest(template = EMPTY, batch = "mh_anchor_fit", timeoutTicks = 600)
    public static void shippedAnchorsFitTheShippedCastle(GameTestHelper helper) {
        var schem = com.hogwartsmagic.worldgen.MiniHogwartsSchematic.get();
        var anchors = com.hogwartsmagic.worldgen.MiniHogwartsAnchors.get();
        helper.assertTrue(schem != null && anchors != null, "the shipped castle and its anchors load");
        List<String> problems = com.hogwartsmagic.worldgen.MiniHogwartsManager.anchorProblems(helper.getLevel(), schem, anchors);
        for (String p : problems) {
            LOGGER.error("anchor problem: {}", p);
        }
        helper.assertTrue(problems.isEmpty(), problems.size() + " anchor problems, first: "
                + (problems.isEmpty() ? "" : problems.get(0)));
        LOGGER.info("MINIHOGWARTS-GAMETEST PASS shippedAnchorsFitTheShippedCastle");
        helper.succeed();
    }
}
