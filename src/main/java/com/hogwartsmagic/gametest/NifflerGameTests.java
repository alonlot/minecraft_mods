package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.NifflerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * The Niffler: it robs, it buries, and nothing it takes is ever lost. Ticked by hand: the test
 * server stops ticking entities partway through a long batch.
 */
@GameTestHolder(ExampleMod.MODID)
public final class NifflerGameTests {
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";

    private NifflerGameTests() {
    }

    /** Two layers of earth under the whole template, so it can walk, dig and bury. */
    private static void earth(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(x, 0, z, Blocks.DIRT);
                helper.setBlock(x, 1, z, Blocks.DIRT);
            }
        }
    }

    private static NifflerEntity niffler(GameTestHelper helper) {
        earth(helper);
        return helper.spawn(ModEntities.NIFFLER.get(), new BlockPos(1, 2, 1));
    }

    private static int count(Iterable<ItemStack> stacks, Item item) {
        int n = 0;
        for (ItemStack s : stacks) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static int inPouch(NifflerEntity n, Item item) {
        return count(n.pouch().getItems(), item);
    }

    private static int dropped(GameTestHelper helper, Item item) {
        AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8.0D);
        int c = 0;
        for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
            if (e.getItem().is(item)) {
                c += e.getItem().getCount();
            }
        }
        return c;
    }

    private static void tick(NifflerEntity n, int ticks) {
        for (int i = 0; i < ticks && !n.isRemoved(); i++) {
            n.tick();
        }
    }

    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void stealTakesPartOfOneShinyStackAndRuns(GameTestHelper helper) {
        NifflerEntity n = niffler(helper);
        n.setNoAi(true);
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        p.getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 10));
        p.getInventory().setItem(1, new ItemStack(Items.DIRT, 5));

        helper.assertTrue(n.steal(p), "a player with gold should be robbed");
        int taken = inPouch(n, Items.GOLD_INGOT);
        int left = count(p.getInventory().items, Items.GOLD_INGOT);
        helper.assertTrue(taken >= 1 && taken <= 5, "it should take one to half the stack, took " + taken);
        helper.assertTrue(taken + left == 10, "no gold should vanish: " + taken + " + " + left);
        helper.assertTrue(count(p.getInventory().items, Items.DIRT) == 5, "dirt is not shiny");
        helper.assertTrue(n.isFleeing(), "a thief should run");

        p.getInventory().clearContent();
        p.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        helper.assertFalse(n.steal(p), "nothing shiny, nothing taken");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void itPicksUpGoldLyingOnTheGround(GameTestHelper helper) {
        NifflerEntity n = niffler(helper);
        ItemEntity nugget = helper.spawnItem(Items.GOLD_NUGGET, 2.5F, 2.2F, 2.5F);
        nugget.setNoPickUpDelay();
        ItemEntity stone = helper.spawnItem(Items.STONE, 0.5F, 2.2F, 0.5F);
        stone.setNoPickUpDelay();
        for (int i = 0; i < 200 && inPouch(n, Items.GOLD_NUGGET) == 0; i++) {
            n.tick();
            nugget.tick();
        }
        helper.assertTrue(inPouch(n, Items.GOLD_NUGGET) == 1, "the nugget should be in its pouch");
        helper.assertTrue(stone.isAlive(), "stone is not worth its time");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void aBurrowingNifflerBuriesItsPouchForABrush(GameTestHelper helper) {
        NifflerEntity n = niffler(helper);
        n.setNoAi(true);
        n.setOnGround(true);
        n.addToPouch(new ItemStack(Items.GOLD_INGOT, 7));
        n.addToPouch(new ItemStack(Items.DIAMOND, 2));
        n.startAction(NifflerEntity.Action.BURROW);
        tick(n, NifflerEntity.Action.BURROW.ticks + 2);
        helper.assertTrue(n.isRemoved(), "it should have gone into the ground");

        int gold = 0;
        int diamonds = 0;
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 3; z++) {
                    if (helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(x, y, z)))
                            instanceof BrushableBlockEntity b) {
                        gold += b.getItem().is(Items.GOLD_INGOT) ? b.getItem().getCount() : 0;
                        diamonds += b.getItem().is(Items.DIAMOND) ? b.getItem().getCount() : 0;
                    }
                }
            }
        }
        gold += dropped(helper, Items.GOLD_INGOT);
        diamonds += dropped(helper, Items.DIAMOND);
        helper.assertTrue(gold == 7 && diamonds == 2, "every item should be buried, found " + gold + " gold, "
                + diamonds + " diamonds");
        helper.assertBlockPresent(Blocks.SUSPICIOUS_GRAVEL, new BlockPos(1, 1, 1));
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void tickledItEmptiesItsPouchForItsOwner(GameTestHelper helper) {
        NifflerEntity n = niffler(helper);
        n.setNoAi(true);
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        n.tame(p);
        n.addToPouch(new ItemStack(Items.GOLD_NUGGET, 20));
        n.addToPouch(new ItemStack(Items.EMERALD, 3));
        n.addToPouch(new ItemStack(Items.RAW_GOLD, 4));
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        n.mobInteract(p, InteractionHand.MAIN_HAND);
        helper.assertTrue(n.action() == NifflerEntity.Action.TICKLED, "an empty hand should tickle it");
        tick(n, NifflerEntity.Action.TICKLED.ticks + 2);
        helper.assertTrue(n.pouch().isEmpty(), "the tickle should empty the pouch");
        helper.assertTrue(dropped(helper, Items.GOLD_NUGGET) == 20 && dropped(helper, Items.EMERALD) == 3
                && dropped(helper, Items.RAW_GOLD) == 4, "everything should come out");
        helper.assertTrue(n.action() == NifflerEntity.Action.NONE, "and then it rolls back over");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void itRaidsChestsButNotUnopenedTombChests(GameTestHelper helper) {
        NifflerEntity n = niffler(helper);
        n.setNoAi(true);
        BlockPos open = new BlockPos(0, 2, 0);
        BlockPos sealed = new BlockPos(2, 2, 2);
        helper.setBlock(open, Blocks.CHEST);
        helper.setBlock(sealed, Blocks.CHEST);
        ChestBlockEntity a = (ChestBlockEntity) helper.getBlockEntity(open);
        ChestBlockEntity b = (ChestBlockEntity) helper.getBlockEntity(sealed);
        a.setItem(3, new ItemStack(Items.BREAD, 5));
        a.setItem(7, new ItemStack(Items.GOLD_INGOT, 12));
        b.setLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "chests/cursed_tomb")), 1L);

        helper.assertTrue(NifflerEntity.raidable(a), "a chest with gold is fair game");
        helper.assertFalse(NifflerEntity.raidable(b), "an unopened tomb chest must be left for the player");
        helper.assertTrue(n.raid(helper.absolutePos(open)), "it should raid the open chest");
        helper.assertTrue(inPouch(n, Items.GOLD_INGOT) == 12, "the whole gold stack should be in the pouch");
        helper.assertTrue(a.getItem(7).isEmpty() && a.getItem(3).getCount() == 5, "only the gold should go");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void hitsShakeLootOutAndDeathDropsTheRest(GameTestHelper helper) {
        NifflerEntity n = niffler(helper);
        n.setNoAi(true);
        Item[] loot = {Items.GOLD_NUGGET, Items.GOLD_INGOT, Items.EMERALD, Items.DIAMOND, Items.RAW_GOLD};
        for (Item item : loot) {
            n.addToPouch(new ItemStack(item, 2));
        }
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        n.hurt(helper.getLevel().damageSources().playerAttack(p), 1.0F);
        helper.assertTrue(n.fullness() < loot.length, "a knock should shake something out, still " + n.fullness());
        helper.assertTrue(n.isFleeing(), "and it should run");
        n.kill();
        for (Item item : loot) {
            helper.assertTrue(dropped(helper, item) == 2, "all of " + item + " should be on the ground");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void aTameNifflerSmellsGoldUnderground(GameTestHelper helper) {
        NifflerEntity n = niffler(helper);
        n.setNoAi(true);
        helper.setBlock(new BlockPos(2, 0, 0), Blocks.DEEPSLATE_GOLD_ORE);
        BlockPos found = n.senseTreasure(helper.getLevel());
        helper.assertTrue(helper.absolutePos(new BlockPos(2, 0, 0)).equals(found), "it should find the ore, found " + found);
        helper.succeed();
    }
}
