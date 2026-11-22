package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.YetiBoulderEntity;
import com.hogwartsmagic.entity.YetiEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * The Yeti: it fears fire and shrugs off cold, sleeps until disturbed, hits hard up close and at
 * range, and its cave arrives with it asleep in the nest. Entities are ticked by hand where it matters.
 */
@GameTestHolder(ExampleMod.MODID)
public final class YetiGameTests {
    private static final String EMPTY9 = "hogwartsmagic:gametest/empty9";
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";

    private YetiGameTests() {
    }

    private static void floor(GameTestHelper helper, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                helper.setBlock(x, 0, z, Blocks.SNOW_BLOCK);
            }
        }
    }

    private static YetiEntity yeti(GameTestHelper helper) {
        floor(helper, 9);
        YetiEntity y = helper.spawn(ModEntities.YETI.get(), new BlockPos(4, 1, 4));
        y.setNoAi(true);
        return y;
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void fireHurtsItDoubleAndMakesItCower(GameTestHelper helper) {
        YetiEntity y = yeti(helper);
        float before = y.getHealth();
        y.hurt(helper.getLevel().damageSources().inFire(), 3.0F);
        float lost = before - y.getHealth();
        helper.assertTrue(lost > 5.0F, "fire should hurt it double, it lost " + lost);
        helper.assertTrue(y.isAfraid(), "fire should frighten it");
        helper.assertTrue(y.action() == YetiEntity.Action.COWER, "it should cower, it is " + y.action());
        helper.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void coldDoesNothingToIt(GameTestHelper helper) {
        YetiEntity y = yeti(helper);
        float before = y.getHealth();
        helper.assertFalse(y.hurt(helper.getLevel().damageSources().freeze(), 5.0F), "freezing should not hurt it");
        helper.assertTrue(y.getHealth() == before, "health should not move");
        helper.assertFalse(y.canFreeze(), "it should not freeze in powder snow");
        helper.assertFalse(y.canBeAffected(new MobEffectInstance(
                net.minecraft.core.Holder.direct(ExampleMod.GLACIUS_FROZEN.get()), 100)), "Glacius should not freeze it");
        helper.assertTrue(y.canBeAffected(new MobEffectInstance(MobEffects.WEAKNESS, 100)), "other effects still work");
        helper.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void sneakingGetsCloserThanWalking(GameTestHelper helper) {
        helper.assertTrue(YetiEntity.disturbs(5.0D, false, false), "walking at 5 blocks wakes it");
        helper.assertFalse(YetiEntity.disturbs(5.0D, true, false), "sneaking at 5 blocks does not");
        helper.assertFalse(YetiEntity.disturbs(3.0D, true, false), "sneaking at 3 blocks does not");
        helper.assertTrue(YetiEntity.disturbs(2.0D, true, false), "sneaking at 2 blocks does");
        helper.assertTrue(YetiEntity.disturbs(9.0D, false, true), "running at 9 blocks does");
        helper.assertFalse(YetiEntity.disturbs(9.0D, false, false), "walking at 9 blocks does not");

        YetiEntity y = yeti(helper);
        y.setAsleep(true);
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        var near = helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5D, 1.0D, 1.5D));
        p.moveTo(near.x, near.y, near.z);      // within its 32-block give-up range
        y.hurt(helper.getLevel().damageSources().playerAttack(p), 1.0F);
        helper.assertFalse(y.isAsleep(), "a blow should wake it");
        helper.assertTrue(y.action() == YetiEntity.Action.ROAR, "and it should wake roaring");
        helper.assertTrue(y.getTarget() == p, "at whoever hit it");
        helper.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void theSlamHurtsLiftsAndSlows(GameTestHelper helper) {
        YetiEntity y = yeti(helper);
        y.setYRot(0.0F);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(4, 1, 6));
        pig.setNoAi(true);
        float before = pig.getHealth();
        y.slamImpact(helper.getLevel());
        helper.assertTrue(pig.getHealth() < before, "the pig in reach should be hurt");
        helper.assertTrue(pig.getDeltaMovement().y > 0.2D, "and thrown up, dy " + pig.getDeltaMovement().y);
        helper.assertTrue(pig.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and slowed");
        helper.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 60)
    public static void aThrownBoulderHitsAndBursts(GameTestHelper helper) {
        YetiEntity y = yeti(helper);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(4, 1, 8));
        pig.setNoAi(true);
        float before = pig.getHealth();
        YetiBoulderEntity boulder = new YetiBoulderEntity(helper.getLevel(), y);
        var from = helper.absoluteVec(new net.minecraft.world.phys.Vec3(4.5D, 1.6D, 5.5D));
        boulder.setPos(from.x, from.y, from.z);
        boulder.shoot(0.0D, 0.0D, 1.0D, 0.8F, 0.0F);
        helper.getLevel().addFreshEntity(boulder);
        for (int i = 0; i < 40 && !boulder.isRemoved(); i++) {
            boulder.tick();
        }
        helper.assertTrue(boulder.isRemoved(), "the boulder should burst on impact");
        helper.assertTrue(before - pig.getHealth() >= YetiBoulderEntity.DAMAGE - 0.01F,
                "the pig should take the boulder's damage, lost " + (before - pig.getHealth()));
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void aHeldFlameIsWhatItFears(GameTestHelper helper) {
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_SWORD));
        helper.assertFalse(YetiEntity.holdsFire(p), "a sword is not fire");
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH));
        helper.assertTrue(YetiEntity.holdsFire(p), "a torch in the off hand is");
        helper.succeed();
    }

    /** The cave template loads with its Yeti asleep in the nest, its two frozen dead and its hoard. */
    @GameTest(template = "hogwartsmagic:yeti_cave", timeoutTicks = 40)
    public static void theCaveHasItsSleepingYeti(GameTestHelper helper) {
        var yetis = helper.getEntities(ModEntities.YETI.get());
        helper.assertTrue(yetis.size() == 1, "one Yeti in the cave, found " + yetis.size());
        YetiEntity y = yetis.get(0);
        helper.assertTrue(y.isAsleep(), "it should be asleep");
        helper.assertTrue(y.home() != null, "its nest should be its home");
        helper.assertTrue(helper.getEntities(EntityType.ARMOR_STAND).size() == 2, "two frozen explorers");
        for (ArmorStand s : helper.getEntities(EntityType.ARMOR_STAND)) {
            helper.assertTrue(helper.getLevel().getBlockState(s.blockPosition()).is(Blocks.ICE),
                    "a frozen explorer should stand in ice");
        }
        int chests = 0;
        for (BlockPos rel : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(36, 24, 36))) {
            if (helper.getLevel().getBlockState(helper.absolutePos(rel)).is(Blocks.CHEST)) {
                chests++;
            }
        }
        helper.assertTrue(chests == 2, "the hoard is two chests, found " + chests);
        var ra = helper.getLevel().registryAccess();
        var id = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "yeti_cave");
        helper.assertTrue(ra.registryOrThrow(Registries.STRUCTURE).containsKey(id), "the yeti_cave structure did not load");
        helper.assertTrue(ra.registryOrThrow(Registries.STRUCTURE_SET).containsKey(id), "the yeti_cave structure set did not load");
        var cold = ra.registryOrThrow(Registries.BIOME).getTag(net.minecraft.tags.TagKey.create(Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "has_structure/yeti_cave")));
        helper.assertTrue(cold.isPresent() && cold.get().size() == 5, "the cave's biome tag should list five snowy biomes");
        var loot = helper.getLevel().getServer().reloadableRegistries();
        for (String path : new String[] {"chests/yeti_cave", "entities/yeti"}) {
            helper.assertTrue(loot.getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                    ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, path))) != LootTable.EMPTY,
                    "loot table " + path + " did not load");
        }
        helper.succeed();
    }
}
