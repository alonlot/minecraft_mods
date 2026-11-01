package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ambience.AmbienceNet;
import com.hogwartsmagic.ambience.AmbienceRegistry;
import com.hogwartsmagic.ambience.AmbientEffect;
import com.hogwartsmagic.spell.support.ceiling.CaelumFascinoSpell;
import com.hogwartsmagic.spell.support.ceiling.CaelumFascinoSpell.Result;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.List;
import java.util.UUID;

/**
 * Caelum Fascino: two casts make a ceiling in the ambience registry, a sneak-cast removes it (for its maker only), a
 * lone mark lapses, and a made ceiling comes back from a saved world. The spell is driven through its public entry,
 * {@link CaelumFascinoSpell#onBlockHit}, which is what the beam calls on a block hit.
 */
@GameTestHolder(ExampleMod.MODID)
public final class CeilingSpellGameTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";

    private CeilingSpellGameTests() {
    }

    /** Every ceiling this player owns is gone again, and their pending mark too, so no test leaks into the next. */
    private static void cleanUp(ServerLevel level, ServerPlayer... players) {
        AmbienceRegistry registry = AmbienceRegistry.get(level);
        for (ServerPlayer p : players) {
            for (AmbientEffect e : registry.ceilingsOwnedBy(p.getUUID())) {
                registry.remove(level, e.id());
            }
            CaelumFascinoSpell.clearMark(p.getUUID());
        }
        AmbienceNet.setSendObserverForTests(null);
    }

    /** Marks one corner then the other as this player, standing still; returns the second cast's result. */
    private static Result castTwo(ServerLevel level, ServerPlayer p, BlockPos a, BlockPos b) {
        Result first = CaelumFascinoSpell.onBlockHit(level, a, p);
        if (first != Result.MARKED) {
            return first;
        }
        return CaelumFascinoSpell.onBlockHit(level, b, p);
    }

    @GameTest(template = EMPTY, batch = "ceiling_spell_create", timeoutTicks = 100)
    public static void twoCornersCreateCeiling(GameTestHelper helper) {
        AmbienceNet.setSendObserverForTests((player, packet) -> { });
        ServerLevel level = helper.getLevel();
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        AmbienceRegistry registry = AmbienceRegistry.get(level);
        try {
            int before = registry.all().size();
            BlockPos a = helper.absolutePos(new BlockPos(0, 4, 0));
            BlockPos b = helper.absolutePos(new BlockPos(5, 6, 3));
            helper.assertTrue(CaelumFascinoSpell.onBlockHit(level, a, p) == Result.MARKED, "the first cast marks a corner");
            helper.assertTrue(registry.all().size() == before, "a lone mark makes no ceiling");
            helper.assertTrue(CaelumFascinoSpell.markOf(p.getUUID()) != null, "the mark is pending");
            helper.assertTrue(CaelumFascinoSpell.onBlockHit(level, b, p) == Result.CREATED, "the second cast creates it");
            helper.assertTrue(CaelumFascinoSpell.markOf(p.getUUID()) == null, "the mark is spent");
            List<AmbientEffect> mine = registry.ceilingsOwnedBy(p.getUUID());
            helper.assertTrue(registry.all().size() == before + 1 && mine.size() == 1, "the registry gained one ceiling");
            AmbientEffect e = mine.get(0);
            // x runs from the lower corner to one past the higher: a.x .. b.x + 1 (6 wide), z a.z .. b.z + 1 (4 deep).
            double cx = a.getX() + 3.0;
            double cz = a.getZ() + 2.0;
            helper.assertTrue(e.kind() == AmbientEffect.KIND_CEILING, "it is a ceiling");
            helper.assertTrue(e.x() == cx && e.z() == cz, "centred on the rectangle, got " + e.x() + "," + e.z());
            helper.assertTrue(e.hx() == 3f && e.hz() == 2f, "half extents 3 by 2, got " + e.hx() + "," + e.hz());
            helper.assertTrue(e.y() == a.getY() && e.hy() == 0f, "the plane is the lower corner's lower face, no roof clearance");
            helper.assertTrue(p.getUUID().equals(e.owner()), "owned by the caster");
            helper.assertTrue(e.createdTick() == level.getGameTime(), "the reveal starts now");

            helper.assertTrue(registry.ceilingAt(new net.minecraft.world.phys.Vec3(cx, a.getY() + 0.5, cz), 2) == e,
                    "a point in the plane finds it");
        } finally {
            cleanUp(level, p);
        }
        LOGGER.info("CEILING-GAMETEST PASS twoCornersCreateCeiling");
        helper.succeed();
    }

    /** In the Nether (no sky light) the first cast is refused with a hint, and no corner is marked. */
    @GameTest(template = EMPTY, batch = "ceiling_spell_no_sky", timeoutTicks = 100)
    public static void noSkyDimensionRefuses(GameTestHelper helper) {
        AmbienceNet.setSendObserverForTests((player, packet) -> { });
        ServerLevel level = helper.getLevel();
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        try {
            helper.assertTrue(nether != null && !nether.dimensionType().hasSkyLight(), "the Nether is loaded, skyless");
            int before = AmbienceRegistry.get(nether).all().size();
            Result result = CaelumFascinoSpell.onBlockHit(nether, new BlockPos(0, 64, 0), p);
            helper.assertTrue(result == Result.REFUSED, "a cast in the Nether is refused, got " + result);
            helper.assertTrue(CaelumFascinoSpell.markOf(p.getUUID()) == null, "no corner is marked there");
            helper.assertTrue(AmbienceRegistry.get(nether).all().size() == before, "nothing is registered");
            // The overworld still takes a mark.
            helper.assertTrue(CaelumFascinoSpell.onBlockHit(level, helper.absolutePos(new BlockPos(0, 4, 0)), p)
                    == Result.MARKED, "a cast under a sky marks a corner");
        } finally {
            cleanUp(level, p);
        }
        LOGGER.info("CEILING-GAMETEST PASS noSkyDimensionRefuses");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "ceiling_spell_sneak", timeoutTicks = 100)
    public static void sneakCastRemovesOwnCeiling(GameTestHelper helper) {
        AmbienceNet.setSendObserverForTests((player, packet) -> { });
        ServerLevel level = helper.getLevel();
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        AmbienceRegistry registry = AmbienceRegistry.get(level);
        try {
            BlockPos a = helper.absolutePos(new BlockPos(0, 4, 0));
            BlockPos b = helper.absolutePos(new BlockPos(4, 4, 4));
            helper.assertTrue(castTwo(level, p, a, b) == Result.CREATED, "the ceiling is made");
            helper.assertTrue(registry.ceilingsOwnedBy(p.getUUID()).size() == 1, "it is registered");

            // Sneaking elsewhere (no ceiling there) does nothing and keeps the ceiling.
            p.setShiftKeyDown(true);
            BlockPos far = helper.absolutePos(new BlockPos(0, 40, 0));
            helper.assertTrue(CaelumFascinoSpell.onBlockHit(level, far, p) == Result.NOTHING, "no ceiling there");
            helper.assertTrue(registry.ceilingsOwnedBy(p.getUUID()).size() == 1, "still there");

            // A sneak-cast at a roof block (the plane's own block row) inside the rectangle removes it.
            helper.assertTrue(CaelumFascinoSpell.onBlockHit(level, helper.absolutePos(new BlockPos(2, 4, 2)), p) == Result.REMOVED,
                    "the sneak-cast removes the caster's own ceiling");
            helper.assertTrue(registry.ceilingsOwnedBy(p.getUUID()).isEmpty(), "it is gone");

            // With the sneak released, a cast marks again rather than removing anything.
            p.setShiftKeyDown(false);
            helper.assertTrue(CaelumFascinoSpell.onBlockHit(level, a, p) == Result.MARKED, "a normal cast marks");
        } finally {
            p.setShiftKeyDown(false);
            cleanUp(level, p);
        }
        LOGGER.info("CEILING-GAMETEST PASS sneakCastRemovesOwnCeiling");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "ceiling_spell_perm", timeoutTicks = 100)
    public static void otherPlayerCannotRemove(GameTestHelper helper) {
        AmbienceNet.setSendObserverForTests((player, packet) -> { });
        ServerLevel level = helper.getLevel();
        ServerPlayer maker = helper.makeMockServerPlayerInLevel();
        ServerPlayer other = helper.makeMockServerPlayerInLevel();
        AmbienceRegistry registry = AmbienceRegistry.get(level);
        try {
            BlockPos a = helper.absolutePos(new BlockPos(0, 4, 0));
            BlockPos b = helper.absolutePos(new BlockPos(4, 4, 4));
            helper.assertTrue(castTwo(level, maker, a, b) == Result.CREATED, "the maker's ceiling is made");
            helper.assertTrue(!maker.getUUID().equals(other.getUUID()), "two distinct players");
            helper.assertTrue(!other.hasPermissions(2), "the other player is not an operator");

            other.setShiftKeyDown(true);
            Result result = CaelumFascinoSpell.onBlockHit(level, helper.absolutePos(new BlockPos(2, 4, 2)), other);
            helper.assertTrue(result == Result.REFUSED, "another player's sneak-cast is refused, got " + result);
            helper.assertTrue(registry.ceilingsOwnedBy(maker.getUUID()).size() == 1, "the ceiling is still there");

            maker.setShiftKeyDown(true);
            helper.assertTrue(CaelumFascinoSpell.onBlockHit(level, helper.absolutePos(new BlockPos(2, 4, 2)), maker) == Result.REMOVED,
                    "its maker can remove it");
        } finally {
            other.setShiftKeyDown(false);
            maker.setShiftKeyDown(false);
            cleanUp(level, maker, other);
        }
        LOGGER.info("CEILING-GAMETEST PASS otherPlayerCannotRemove");
        helper.succeed();
    }

    /** Makes an op of this player for the test (the ops list is restored by {@link #deop}). */
    private static void op(ServerLevel level, ServerPlayer p) {
        // Level 4 explicitly: PlayerList.op would use the test server's configured level, which is 0 here.
        level.getServer().getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(p.getGameProfile(), 4, false));
    }

    private static void deop(ServerLevel level, ServerPlayer p) {
        level.getServer().getPlayerList().deop(p.getGameProfile());
    }

    @GameTest(template = EMPTY, batch = "ceiling_spell_op", timeoutTicks = 100)
    public static void opCanRemoveAnyCeiling(GameTestHelper helper) {
        AmbienceNet.setSendObserverForTests((player, packet) -> { });
        ServerLevel level = helper.getLevel();
        ServerPlayer maker = helper.makeMockServerPlayerInLevel();
        ServerPlayer admin = helper.makeMockServerPlayerInLevel();
        AmbienceRegistry registry = AmbienceRegistry.get(level);
        BlockPos c = helper.absolutePos(new BlockPos(20, 4, 20));
        // An owner-less ceiling, like the castle's own, somewhere else in the test area.
        final AmbientEffect castle = new AmbientEffect(UUID.randomUUID(), AmbientEffect.KIND_CEILING, c.getX() + 3.0,
                c.getY(), c.getZ() + 3.0, 3f, 0f, 3f, 0f, 0, null, level.getGameTime());
        try {
            BlockPos a = helper.absolutePos(new BlockPos(0, 4, 0));
            BlockPos b = helper.absolutePos(new BlockPos(4, 4, 4));
            helper.assertTrue(castTwo(level, maker, a, b) == Result.CREATED, "the maker's ceiling is made");
            registry.add(level, castle);

            admin.setShiftKeyDown(true);
            BlockPos inside = helper.absolutePos(new BlockPos(2, 4, 2));
            BlockPos insideCastle = helper.absolutePos(new BlockPos(21, 4, 21));
            helper.assertTrue(!admin.hasPermissions(2), "not yet an operator");
            helper.assertTrue(CaelumFascinoSpell.onBlockHit(level, inside, admin) == Result.REFUSED, "a non-op is refused");
            helper.assertTrue(CaelumFascinoSpell.onBlockHit(level, insideCastle, admin) == Result.REFUSED,
                    "a non-op cannot remove an owner-less ceiling either");

            op(level, admin);
            helper.assertTrue(admin.hasPermissions(2), "now an operator");
            helper.assertTrue(CaelumFascinoSpell.onBlockHit(level, inside, admin) == Result.REMOVED,
                    "an op removes another player's ceiling");
            helper.assertTrue(registry.ceilingsOwnedBy(maker.getUUID()).isEmpty(), "it is gone");
            helper.assertTrue(CaelumFascinoSpell.onBlockHit(level, insideCastle, admin) == Result.REMOVED,
                    "an op removes an owner-less ceiling");
            helper.assertTrue(registry.all().stream().noneMatch(e -> e.id().equals(castle.id())), "the castle's is gone");
        } finally {
            deop(level, admin);
            admin.setShiftKeyDown(false);
            registry.remove(level, castle.id());
            cleanUp(level, maker, admin);
        }
        LOGGER.info("CEILING-GAMETEST PASS opCanRemoveAnyCeiling");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "ceiling_spell_limit", timeoutTicks = 100)
    public static void ceilingLimitCountsEveryDimension(GameTestHelper helper) {
        AmbienceNet.setSendObserverForTests((player, packet) -> { });
        ServerLevel level = helper.getLevel();
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        ServerLevel other = level.getServer().getLevel(level.dimension() == Level.NETHER ? Level.END : Level.NETHER);
        helper.assertTrue(other != null, "the test server has a second dimension");
        AmbienceRegistry otherRegistry = AmbienceRegistry.get(other);
        AmbienceRegistry registry = AmbienceRegistry.get(level);
        try {
            // The player already keeps the most allowed, all of them in the other dimension.
            for (int i = 0; i < CaelumFascinoSpell.MAX_PER_PLAYER; i++) {
                otherRegistry.add(other, new AmbientEffect(UUID.randomUUID(), AmbientEffect.KIND_CEILING, 1000.5 + i * 10, 70,
                        1000.5, 2f, 0f, 2f, 0f, 0, p.getUUID(), 0L));
            }
            helper.assertTrue(registry.ceilingsOwnedBy(p.getUUID()).isEmpty(), "none of them is in this level");
            helper.assertTrue(CaelumFascinoSpell.ownedAnywhere(level.getServer(), p.getUUID()) == CaelumFascinoSpell.MAX_PER_PLAYER,
                    "they all count");
            BlockPos a = helper.absolutePos(new BlockPos(0, 4, 0));
            BlockPos b = helper.absolutePos(new BlockPos(4, 4, 4));
            helper.assertTrue(castTwo(level, p, a, b) == Result.REFUSED, "a ninth ceiling is refused here");
            helper.assertTrue(registry.ceilingsOwnedBy(p.getUUID()).isEmpty(), "and none was made");
        } finally {
            cleanUp(level, p);
            cleanUp(other, p);
        }
        LOGGER.info("CEILING-GAMETEST PASS ceilingLimitCountsEveryDimension");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "ceiling_spell_mark", timeoutTicks = 100)
    public static void markExpires(GameTestHelper helper) {
        AmbienceNet.setSendObserverForTests((player, packet) -> { });
        ServerLevel level = helper.getLevel();
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        AmbienceRegistry registry = AmbienceRegistry.get(level);
        try {
            int before = registry.all().size();
            BlockPos a = helper.absolutePos(new BlockPos(0, 4, 0));
            helper.assertTrue(CaelumFascinoSpell.onBlockHit(level, a, p) == Result.MARKED, "marked");
            long marked = CaelumFascinoSpell.markOf(p.getUUID()).tick();
            helper.assertTrue(marked == level.getGameTime(), "the mark remembers when it was made");

            CaelumFascinoSpell.tickMarks(level.getServer(), marked + CaelumFascinoSpell.MARK_TTL_TICKS - 1);
            helper.assertTrue(CaelumFascinoSpell.markOf(p.getUUID()) != null, "a mark one tick short of the limit lives");
            CaelumFascinoSpell.tickMarks(level.getServer(), marked + CaelumFascinoSpell.MARK_TTL_TICKS);
            helper.assertTrue(CaelumFascinoSpell.markOf(p.getUUID()) == null, "the mark lapses after MARK_TTL_TICKS");
            helper.assertTrue(registry.all().size() == before, "a lapsed mark makes nothing");

        } finally {
            cleanUp(level, p);
        }
        LOGGER.info("CEILING-GAMETEST PASS markExpires");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "ceiling_spell_save", timeoutTicks = 100)
    public static void ceilingSurvivesRestart(GameTestHelper helper) {
        AmbienceNet.setSendObserverForTests((player, packet) -> { });
        ServerLevel level = helper.getLevel();
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        AmbienceRegistry registry = AmbienceRegistry.get(level);
        try {
            BlockPos a = helper.absolutePos(new BlockPos(0, 4, 0));
            BlockPos b = helper.absolutePos(new BlockPos(7, 5, 2));
            helper.assertTrue(castTwo(level, p, a, b) == Result.CREATED, "the ceiling is made");
            AmbientEffect made = registry.ceilingsOwnedBy(p.getUUID()).get(0);

            // What the world does on a save and the next load: the registry written to NBT and read back.
            CompoundTag saved = registry.save(new CompoundTag(), level.registryAccess());
            AmbienceRegistry reloaded = AmbienceRegistry.load(saved, level.registryAccess());
            AmbientEffect back = reloaded.all().stream().filter(e -> e.id().equals(made.id())).findFirst().orElse(null);
            helper.assertTrue(back != null, "the ceiling is in the reloaded registry");
            helper.assertTrue(back.equals(made), "and it is exactly the one made: " + back + " vs " + made);
            UUID owner = back.owner();
            helper.assertTrue(p.getUUID().equals(owner), "its owner survives, so its maker can still remove it");
            helper.assertTrue(reloaded.ceilingsOwnedBy(p.getUUID()).size() == 1, "and it still counts toward their limit");
        } finally {
            cleanUp(level, p);
        }
        LOGGER.info("CEILING-GAMETEST PASS ceilingSurvivesRestart");
        helper.succeed();
    }
}
