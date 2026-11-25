package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.decor.FloatingCandleBlock;
import com.hogwartsmagic.block.decor.ModDecorations;
import com.hogwartsmagic.cosy.CosyHome;
import com.hogwartsmagic.cosy.CosyHomeEvents;
import com.hogwartsmagic.cosy.FloatingCandleBlockEntity;
import com.hogwartsmagic.cosy.HearthBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * The cosy home pack: the hearth and the Warmed effect, the bobbing candles and the furniture. Block entities
 * are ticked by hand, as the Gnome tests do, so the numbers do not depend on how the test server schedules a batch.
 */
@GameTestHolder(ExampleMod.MODID)
public final class CosyHomeGameTests {
    private static final String EMPTY9 = "hogwartsmagic:gametest/empty9";

    private CosyHomeGameTests() {
    }

    private static void stonePad(GameTestHelper h) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                h.setBlock(x, 1, z, Blocks.STONE);
            }
        }
    }

    private static ServerPlayer playerAt(GameTestHelper h, double x, double y, double z) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        Vec3 at = h.absoluteVec(new Vec3(x, y, z));
        player.setPos(at.x, at.y, at.z);
        return player;
    }

    /** Places a hearth at (4, 2, 4) and runs its block entity for {@code ticks} ticks. */
    private static void runHearth(GameTestHelper h, boolean lit, int ticks) {
        BlockPos rel = new BlockPos(4, 2, 4);
        h.setBlock(rel, CosyHome.HEARTH.get().defaultBlockState().setValue(CampfireBlock.LIT, lit));
        BlockPos abs = h.absolutePos(rel);
        BlockState state = h.getLevel().getBlockState(abs);
        HearthBlockEntity hearth = (HearthBlockEntity) h.getLevel().getBlockEntity(abs);
        h.assertTrue(hearth != null, "the hearth should have a block entity");
        for (int i = 0; i < ticks; i++) {
            HearthBlockEntity.serverTick(h.getLevel(), abs, state, hearth);
        }
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void litHearthWarmsNearbyPlayer(GameTestHelper h) {
        stonePad(h);
        ServerPlayer player = playerAt(h, 7.5, 2, 4.5);
        runHearth(h, true, 41);
        h.assertTrue(player.hasEffect(CosyHome.WARMED.getHolder().get()), "a player three blocks away should be warmed");
        MobEffectInstance warmed = player.getEffect(CosyHome.WARMED.getHolder().get());
        h.assertTrue(warmed != null && warmed.getDuration() > 100, "the warmth should last a while");
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void farPlayerIsNotWarmed(GameTestHelper h) {
        stonePad(h);
        // The pad is only 9 wide, so stand 7 blocks off along the diagonal.
        ServerPlayer player = playerAt(h, 4.5 + 7, 2, 4.5);
        runHearth(h, true, 41);
        h.assertFalse(player.hasEffect(CosyHome.WARMED.getHolder().get()), "a player seven blocks away should stay cold");
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void unlitHearthDoesNotWarm(GameTestHelper h) {
        stonePad(h);
        ServerPlayer player = playerAt(h, 7.5, 2, 4.5);
        runHearth(h, false, 41);
        h.assertFalse(player.hasEffect(CosyHome.WARMED.getHolder().get()), "an unlit hearth gives no warmth");
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void warmedClearsGlaciusChill(GameTestHelper h) {
        stonePad(h);
        ServerPlayer player = playerAt(h, 4.5, 2, 4.5);
        player.addEffect(new MobEffectInstance(ExampleMod.GLACIUS_CHILL.getHolder().get(), 200, 0));
        player.addEffect(new MobEffectInstance(CosyHome.WARMED.getHolder().get(), 200, 0));
        player.setTicksFrozen(100);
        for (int i = 0; i < 2; i++) {
            player.doTick();
        }
        h.assertFalse(player.hasEffect(ExampleMod.GLACIUS_CHILL.getHolder().get()),
                "the chill should be gone; effects=" + player.getActiveEffects() + " tickCount=" + player.tickCount
                        + " alive=" + player.isAlive() + " frozen=" + player.getTicksFrozen());
        h.assertTrue(player.getTicksFrozen() == 0, "ticks frozen should be zero, was " + player.getTicksFrozen());
        h.succeed();
    }

    // ---------------------------------------------------------------------------- floating candles

    private static BlockHitResult clickOn(GameTestHelper h, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        return new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void defaultCandleIsLitWithEntity(GameTestHelper h) {
        stonePad(h);
        BlockPos rel = new BlockPos(4, 3, 4);
        // The plain default state is what a candle placed before the LIT property loads as.
        h.setBlock(rel, ModDecorations.FLOATING_CANDLE.get().defaultBlockState());
        BlockPos abs = h.absolutePos(rel);
        BlockState state = h.getLevel().getBlockState(abs);
        h.assertTrue(state.getValue(FloatingCandleBlock.LIT), "a candle should be lit by default");
        h.assertTrue(state.getLightEmission(h.getLevel(), abs) == 14, "a lit candle gives light 14");
        h.assertTrue(h.getLevel().getBlockEntity(abs) instanceof FloatingCandleBlockEntity, "it should have its block entity");
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void oldCandleGetsItsEntityBack(GameTestHelper h) {
        stonePad(h);
        BlockPos rel = new BlockPos(4, 3, 4);
        h.setBlock(rel, ModDecorations.FLOATING_CANDLE.get().defaultBlockState());
        BlockPos abs = h.absolutePos(rel);
        // A chunk saved before the block entity existed holds the block and nothing else.
        h.getLevel().removeBlockEntity(abs);
        h.assertTrue(h.getLevel().getChunkAt(abs).getBlockEntity(abs, LevelChunk.EntityCreationType.CHECK) == null,
                "the entity should be gone for the test");
        int made = CosyHomeEvents.adoptCandles(h.getLevel().getChunkAt(abs));
        h.assertTrue(made >= 1, "adoption should create the entity, made " + made);
        h.assertTrue(h.getLevel().getBlockEntity(abs) instanceof FloatingCandleBlockEntity, "the candle should have an entity now");
        h.assertTrue(h.getLevel().getBlockState(abs).getValue(FloatingCandleBlock.LIT), "and still be lit");
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void clickTogglesCandle(GameTestHelper h) {
        stonePad(h);
        BlockPos rel = new BlockPos(4, 3, 4);
        h.setBlock(rel, ModDecorations.FLOATING_CANDLE.get().defaultBlockState());
        BlockPos abs = h.absolutePos(rel);
        ServerPlayer player = playerAt(h, 4.5, 2, 5.5);
        h.getLevel().getBlockState(abs).useWithoutItem(h.getLevel(), player, clickOn(h, rel));
        BlockState snuffed = h.getLevel().getBlockState(abs);
        h.assertFalse(snuffed.getValue(FloatingCandleBlock.LIT), "a click should snuff the candles");
        h.assertTrue(snuffed.getLightEmission(h.getLevel(), abs) == 0, "snuffed candles give no light");
        h.getLevel().getBlockState(abs).useWithoutItem(h.getLevel(), player, clickOn(h, rel));
        BlockState relit = h.getLevel().getBlockState(abs);
        h.assertTrue(relit.getValue(FloatingCandleBlock.LIT), "a second click lights them again");
        h.assertTrue(relit.getLightEmission(h.getLevel(), abs) == 14, "relit candles give light 14");
        h.succeed();
    }
}
