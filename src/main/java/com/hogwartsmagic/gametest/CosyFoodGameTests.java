package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.cosy.CosyFood;
import com.hogwartsmagic.cosy.FoodRules;
import com.hogwartsmagic.entity.DementorEntity;
import com.hogwartsmagic.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * Cosy food: chocolate and its courage, Chocolate Frogs and their cards, beans, the keg, and the stove and pot.
 * Entities are ticked by hand, as {@link GnomeGameTests} does: the test server stops ticking entities partway
 * through a long batch.
 */
@GameTestHolder(ExampleMod.MODID)
public final class CosyFoodGameTests {
    private static final String EMPTY9 = "hogwartsmagic:gametest/empty9";

    private CosyFoodGameTests() {
    }

    private static void stonePad(GameTestHelper h) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                h.setBlock(x, 1, z, Blocks.STONE);
            }
        }
    }

    private static ServerPlayer survivor(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(
                ServerPlayer.class, p, 0, "spawnInvulnerableTime");
        return p;
    }

    private static boolean hasCourage(ServerPlayer p) {
        return FoodRules.hasCourage(p);
    }

    // ---------------------------------------------------------------- B1: chocolate

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void chocolateCuresDementorEffects(GameTestHelper h) {
        stonePad(h);
        ServerPlayer p = survivor(h);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 1));
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 400, 0));
        p.addEffect(new MobEffectInstance(MobEffects.WITHER, 400, 0));
        ItemStack bar = new ItemStack(CosyFood.CHOCOLATE_BAR.get(), 2);
        ItemStack left = bar.finishUsingItem(h.getLevel(), p);
        h.assertTrue(!p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "slowness is gone");
        h.assertTrue(!p.hasEffect(MobEffects.BLINDNESS), "blindness is gone");
        h.assertTrue(!p.hasEffect(MobEffects.WITHER), "wither is gone");
        h.assertTrue(hasCourage(p), "the bar leaves Chocolate Courage");
        h.assertTrue(left.getCount() == 1, "one bar is eaten, got " + left.getCount());
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 60)
    public static void courageBlocksDementorAura(GameTestHelper h) {
        stonePad(h);
        ServerPlayer brave = survivor(h);
        ServerPlayer control = survivor(h);
        brave.setPos(h.absolutePos(new BlockPos(2, 2, 2)).getCenter().add(0, -0.5, 0));
        control.setPos(h.absolutePos(new BlockPos(6, 2, 2)).getCenter().add(0, -0.5, 0));
        FoodRules.applyChocolateCure(brave);
        DementorEntity d = h.spawn(ModEntities.DEMENTOR.get(), new Vec3(4.5, 2, 5.5));
        d.setNoAi(true);
        for (int i = 0; i < 25; i++) {
            d.tick();
        }
        h.assertTrue(!brave.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the courageous player is not slowed");
        h.assertTrue(control.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the control player is slowed");
        d.discard();
        h.succeed();
    }
}
