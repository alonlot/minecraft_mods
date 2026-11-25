package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.cosy.CosyFood;
import com.hogwartsmagic.cosy.ChocolateFrogEntity;
import com.hogwartsmagic.cosy.FoodRules;
import com.hogwartsmagic.cosy.WizardCards;
import com.hogwartsmagic.entity.DementorEntity;
import com.hogwartsmagic.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.List;

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

    // ---------------------------------------------------------------- B2: frogs, cards, album

    private static int cardsInInventory(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(CosyFood.WIZARD_CARD.get())) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static List<ChocolateFrogEntity> frogsAround(GameTestHelper h, ServerPlayer p) {
        return h.getLevel().getEntitiesOfClass(ChocolateFrogEntity.class, p.getBoundingBox().inflate(6.0));
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void openingFrogGivesCardAndFrog(GameTestHelper h) {
        stonePad(h);
        ServerPlayer p = survivor(h);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CosyFood.CHOCOLATE_FROG.get(), 2));
        p.getItemInHand(InteractionHand.MAIN_HAND).use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(cardsInInventory(p) == 1, "one card in the pack, got " + cardsInInventory(p));
        h.assertTrue(frogsAround(h, p).size() == 1, "one frog jumped out");
        h.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1, "one box is used up");
        for (ChocolateFrogEntity f : frogsAround(h, p)) {
            f.discard();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void frogVanishesAfterLife(GameTestHelper h) {
        stonePad(h);
        ChocolateFrogEntity frog = h.spawn(ModEntities.CHOCOLATE_FROG.get(), new Vec3(4.5, 2, 4.5));
        for (int i = 0; i < ChocolateFrogEntity.LIFE_TICKS + 1; i++) {
            frog.tick();
        }
        h.assertTrue(frog.isRemoved(), "the frog is gone after its life");
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void frogHopsAwayFromThePlayer(GameTestHelper h) {
        stonePad(h);
        ServerPlayer p = survivor(h);
        p.setPos(h.absolutePos(new BlockPos(2, 2, 4)).getCenter().add(0, -0.5, 0));
        ChocolateFrogEntity frog = h.spawn(ModEntities.CHOCOLATE_FROG.get(), new Vec3(3.5, 2, 4.5));
        double before = frog.distanceTo(p);
        for (int i = 0; i < 60; i++) {
            frog.tick();
        }
        h.assertTrue(frog.distanceTo(p) > before + 0.5, "the frog got further from the player");
        frog.discard();
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void catchingFrogFeeds(GameTestHelper h) {
        stonePad(h);
        ServerPlayer p = survivor(h);
        p.getFoodData().setFoodLevel(10);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 1));
        ChocolateFrogEntity frog = h.spawn(ModEntities.CHOCOLATE_FROG.get(), new Vec3(4.5, 2, 4.5));
        p.attack(frog);
        h.assertTrue(p.getFoodData().getFoodLevel() >= 13, "food went up by 3, now " + p.getFoodData().getFoodLevel());
        h.assertTrue(frog.isRemoved(), "the caught frog is gone");
        h.assertTrue(!p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "it cures the Dementor chill too");
        h.assertTrue(hasCourage(p), "and gives courage");
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void frogCardDropsWhenInventoryFull(GameTestHelper h) {
        stonePad(h);
        ServerPlayer p = survivor(h);
        for (int i = 0; i < p.getInventory().items.size(); i++) {
            p.getInventory().items.set(i, new ItemStack(Items.DIAMOND_SWORD));
        }
        // Two boxes, so opening one does not free the slot for the card.
        p.getInventory().items.set(0, new ItemStack(CosyFood.CHOCOLATE_FROG.get(), 2));
        p.getInventory().selected = 0;
        p.getItemInHand(InteractionHand.MAIN_HAND).use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(3.0),
                e -> e.getItem().is(CosyFood.WIZARD_CARD.get()));
        h.assertTrue(drops.size() == 1, "the card lay at the player's feet, found " + drops.size());
        h.assertTrue(cardsInInventory(p) == 0, "and none went into the pack");
        drops.forEach(ItemEntity::discard);
        frogsAround(h, p).forEach(ChocolateFrogEntity::discard);
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void albumTakesNewCardsAndKeepsDuplicates(GameTestHelper h) {
        stonePad(h);
        ServerPlayer p = survivor(h);
        WizardCards.Card a = WizardCards.ALL.get(0);
        WizardCards.Card b = WizardCards.ALL.get(5);
        ItemStack cardA = com.hogwartsmagic.cosy.WizardCardItem.create(a);
        cardA.setCount(2);
        p.getInventory().items.set(3, cardA);
        p.getInventory().items.set(4, com.hogwartsmagic.cosy.WizardCardItem.create(b));
        ItemStack album = new ItemStack(CosyFood.CARD_ALBUM.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, album);
        album.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        List<Integer> kept = album.getOrDefault(CosyFood.ALBUM_CARDS.get(), List.of());
        h.assertTrue(kept.size() == 2 && kept.contains(a.id()) && kept.contains(b.id()), "both are in the album: " + kept);
        h.assertTrue(cardsInInventory(p) == 1, "one duplicate stays in the pack, got " + cardsInInventory(p));
        // Using it again with only the duplicate left changes nothing.
        album.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(album.getOrDefault(CosyFood.ALBUM_CARDS.get(), List.of()).size() == 2, "still two");
        h.assertTrue(cardsInInventory(p) == 1, "the duplicate is still there");
        h.succeed();
    }
}
