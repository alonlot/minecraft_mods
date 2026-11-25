package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.cosy.CosyFood;
import com.hogwartsmagic.cosy.ChocolateFrogEntity;
import com.hogwartsmagic.cosy.CookingPotBlockEntity;
import com.hogwartsmagic.cosy.CosyKitchen;
import com.hogwartsmagic.cosy.EnchantedStoveBlock;
import com.hogwartsmagic.cosy.EnchantedStoveBlockEntity;
import com.hogwartsmagic.cosy.PotCookingRecipe;
import com.hogwartsmagic.cosy.FoodRules;
import com.hogwartsmagic.cosy.WizardCards;
import com.hogwartsmagic.entity.DementorEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.cosy.ButterbeerKegBlock;
import com.hogwartsmagic.cosy.ButterbeerKegBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
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
    // Every test here runs in its own batches ("cosy_food", and "cosy_food_dementor" for the aura, which reaches nine
    // blocks), so adding them never moves other tests next to each other or onto each other's leftovers.

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

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
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

    @GameTest(template = EMPTY9, timeoutTicks = 60, batch = "cosy_food_dementor")
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

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
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

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void frogVanishesAfterLife(GameTestHelper h) {
        stonePad(h);
        ChocolateFrogEntity frog = h.spawn(ModEntities.CHOCOLATE_FROG.get(), new Vec3(4.5, 2, 4.5));
        for (int i = 0; i < ChocolateFrogEntity.LIFE_TICKS + 1; i++) {
            frog.tick();
        }
        h.assertTrue(frog.isRemoved(), "the frog is gone after its life");
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
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

    // The window tests get a batch each: glass within eight blocks of ANY frog draws it, and the tests of a batch run
    // side by side, so glass left in "cosy_food" would pull the other frog tests toward it.
    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_frog_glass")
    public static void frogEscapesThroughGlass(GameTestHelper h) {
        stonePad(h);
        h.setBlock(6, 2, 4, Blocks.GLASS); // four blocks away, in the open
        ChocolateFrogEntity frog = h.spawn(ModEntities.CHOCOLATE_FROG.get(), new Vec3(2.5, 2, 4.5));
        int gone = -1;
        boolean sawTarget = false;
        for (int i = 0; i < ChocolateFrogEntity.LIFE_TICKS; i++) {
            frog.tick();
            sawTarget |= frog.windowTarget() != null;
            if (frog.isRemoved()) {
                gone = i;
                break;
            }
        }
        h.assertTrue(sawTarget, "the frog picked the glass as its window");
        h.assertTrue(gone >= 0 && gone < ChocolateFrogEntity.LIFE_TICKS - 20,
                "the frog went out of the window well before its life ran out, gone at " + gone);
        h.assertTrue(frog.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.DISCARDED,
                "removed with a puff (discarded), was " + frog.getRemovalReason());
        h.setBlock(6, 2, 4, Blocks.AIR);
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_frog_wall")
    public static void frogGivesUpOnUnreachableGlass(GameTestHelper h) {
        stonePad(h);
        for (int z = 0; z < 9; z++) {
            for (int y = 2; y <= 4; y++) {
                h.setBlock(5, y, z, Blocks.STONE); // a wall right across the pad
            }
        }
        h.setBlock(7, 2, 4, Blocks.GLASS);          // the window, behind it
        ServerPlayer p = survivor(h);
        // the player stands to the north along the wall, so "away from the player" is south-to-north (-z is toward them)
        p.setPos(h.absolutePos(new BlockPos(4, 2, 7)).getCenter().add(0, -0.5, 0));
        ChocolateFrogEntity frog = h.spawn(ModEntities.CHOCOLATE_FROG.get(), new Vec3(2.5, 2, 4.5));
        boolean sawTarget = false;
        int cleared = -1;
        double distAtClear = 0;
        for (int i = 0; i < ChocolateFrogEntity.LIFE_TICKS - 1 && !frog.isRemoved(); i++) {
            frog.tick();
            if (frog.windowTarget() != null) {
                sawTarget = true;
            } else if (sawTarget && cleared < 0) {
                cleared = i;
                distAtClear = frog.distanceTo(p);
            }
        }
        h.assertTrue(sawTarget, "the frog first made for the glass");
        h.assertTrue(cleared >= ChocolateFrogEntity.GIVE_UP_TICKS,
                "it gave up only after " + ChocolateFrogEntity.GIVE_UP_TICKS + " ticks of no progress, at " + cleared);
        h.assertTrue(!frog.isRemoved(), "and it is still hopping about, not gone");
        h.assertTrue(frog.distanceTo(p) > distAtClear + 0.5,
                "after giving up it hops away from the player: " + distAtClear + " then " + frog.distanceTo(p));
        frog.discard();
        h.setBlock(7, 2, 4, Blocks.AIR);
        for (int z = 0; z < 9; z++) {
            for (int y = 2; y <= 4; y++) {
                h.setBlock(5, y, z, Blocks.AIR);
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
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

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
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

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
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

    // ---------------------------------------------------------------- B3: beans

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void eatingBeanUsesOne(GameTestHelper h) {
        stonePad(h);
        ServerPlayer p = survivor(h);
        ItemStack box = new ItemStack(CosyFood.BERTIE_BOTTS_BEANS.get());
        h.assertTrue(com.hogwartsmagic.cosy.BertieBottsItem.beansLeft(box) == 10, "a fresh box holds ten");
        box.finishUsingItem(h.getLevel(), p);
        h.assertTrue(box.getOrDefault(CosyFood.BEANS_LEFT.get(), -1) == 9, "ten becomes nine");
        h.assertTrue(!box.isEmpty(), "the box is still there");
        for (int i = 0; i < 20; i++) {
            box.set(CosyFood.BEANS_LEFT.get(), 9);
            box.finishUsingItem(h.getLevel(), p);
            h.assertTrue(box.getOrDefault(CosyFood.BEANS_LEFT.get(), -1) == 8, "always exactly one bean");
        }
        p.removeAllEffects();
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void lastBeanEmptiesBox(GameTestHelper h) {
        stonePad(h);
        ServerPlayer p = survivor(h);
        ItemStack box = new ItemStack(CosyFood.BERTIE_BOTTS_BEANS.get());
        box.set(CosyFood.BEANS_LEFT.get(), 1);
        ItemStack after = box.finishUsingItem(h.getLevel(), p);
        h.assertTrue(after.isEmpty(), "the box is gone with its last bean");
        h.assertTrue(box.isEmpty(), "the original stack is empty too");
        p.removeAllEffects();
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void everyBeanEffectResolves(GameTestHelper h) {
        for (com.hogwartsmagic.cosy.BeanTable.Outcome o : com.hogwartsmagic.cosy.BeanTable.ALL) {
            if (o.effect() != null) {
                h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getHolder(o.effect()).isPresent(),
                        o.flavourKey() + " names a missing effect " + o.effect());
            }
        }
        h.succeed();
    }

    // ---------------------------------------------------------------- B4: butterbeer keg

    private static final BlockPos KEG = new BlockPos(4, 2, 4);

    /** Right-click the keg at {@link #KEG} with whatever is in the main hand (the real path: state.useItemOn). */
    private static void click(GameTestHelper h, ServerPlayer p) {
        BlockPos abs = h.absolutePos(KEG);
        BlockState state = h.getLevel().getBlockState(abs);
        state.useItemOn(p.getItemInHand(InteractionHand.MAIN_HAND), h.getLevel(), p, InteractionHand.MAIN_HAND,
                new BlockHitResult(abs.getCenter(), Direction.NORTH, abs, false));
    }

    private static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static int fillAt(GameTestHelper h) {
        return h.getLevel().getBlockState(h.absolutePos(KEG)).getValue(ButterbeerKegBlock.FILL);
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void kegBrewsAndPours(GameTestHelper h) {
        stonePad(h);
        h.setBlock(KEG, CosyFood.BUTTERBEER_KEG.get());
        ServerPlayer p = survivor(h);
        h.assertTrue(fillAt(h) == 0, "an empty keg");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SUGAR));
        click(h, p);
        h.assertTrue(fillAt(h) == 1, "one ingredient in");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.HONEY_BOTTLE));
        click(h, p);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT));
        click(h, p);
        h.assertTrue(fillAt(h) == 2, "all three in: aging, got fill " + fillAt(h));

        // A tankard now does nothing: it is not ready.
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CosyFood.TANKARD.get(), 9));
        click(h, p);
        h.assertTrue(count(p, CosyFood.BUTTERBEER.get()) == 0, "nothing poured while aging");

        ButterbeerKegBlockEntity be = (ButterbeerKegBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(KEG));
        be.setBrewStartForTest(h.getLevel().getGameTime() - 6000);
        for (int i = 0; i < 8; i++) {
            click(h, p);
        }
        h.assertTrue(count(p, CosyFood.BUTTERBEER.get()) == 8, "eight servings poured, got " + count(p, CosyFood.BUTTERBEER.get()));
        h.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1, "one tankard left");
        h.assertTrue(fillAt(h) == 0, "the keg is empty again");
        click(h, p); // the ninth
        h.assertTrue(count(p, CosyFood.BUTTERBEER.get()) == 8, "nothing more to pour");
        h.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1, "the tankard is kept");
        h.assertTrue(fillAt(h) == 0, "still empty");
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void honeyBottleReturned(GameTestHelper h) {
        stonePad(h);
        h.setBlock(KEG, CosyFood.BUTTERBEER_KEG.get());
        ServerPlayer p = survivor(h);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.HONEY_BOTTLE));
        click(h, p);
        h.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).is(Items.GLASS_BOTTLE), "a glass bottle replaces the honey bottle");
        // A stack of two leaves one honey bottle and puts the glass bottle in the pack. A fresh keg first.
        h.setBlock(KEG, Blocks.AIR);
        h.setBlock(KEG, CosyFood.BUTTERBEER_KEG.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.HONEY_BOTTLE, 2));
        click(h, p);
        h.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1, "one honey bottle left");
        h.assertTrue(count(p, Items.GLASS_BOTTLE) >= 1, "the empty bottle went to the pack");
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void butterbeerWarmsAndReturnsTankard(GameTestHelper h) {
        stonePad(h);
        ServerPlayer p = survivor(h);
        ItemStack beer = new ItemStack(CosyFood.BUTTERBEER.get());
        ItemStack left = beer.finishUsingItem(h.getLevel(), p);
        h.assertTrue(left.is(CosyFood.TANKARD.get()), "the tankard comes back");
        // Warmed belongs to the home pack: when it is registered the drink gives it, when it is not nothing breaks.
        var warmed = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getHolder(
                com.hogwartsmagic.cosy.ButterbeerItem.WARMED_ID);
        if (warmed.isPresent()) {
            h.assertTrue(p.hasEffect(warmed.get()), "Warmed from the drink");
            h.assertTrue(p.getEffect(warmed.get()).getDuration() > 3000, "for about three minutes");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void agedKegReadyWithoutAnyone(GameTestHelper h) {
        stonePad(h);
        h.setBlock(KEG, CosyFood.BUTTERBEER_KEG.get());
        ServerPlayer p = survivor(h);
        for (net.minecraft.world.item.Item i : new net.minecraft.world.item.Item[]{Items.SUGAR, Items.HONEY_BOTTLE, Items.WHEAT}) {
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(i));
            click(h, p);
        }
        ButterbeerKegBlockEntity be = (ButterbeerKegBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(KEG));
        be.setBrewStartForTest(h.getLevel().getGameTime() - 6000);
        // The scheduled/random tick path: the block updates its own state, as it does in a loaded chunk.
        BlockState state = h.getLevel().getBlockState(h.absolutePos(KEG));
        state.tick(h.getLevel(), h.absolutePos(KEG), h.getLevel().random);
        h.assertTrue(fillAt(h) == 3, "the aged keg shows ready, got " + fillAt(h));
        h.succeed();
    }

    // ---------------------------------------------------------------- B5: stove and cooking pot

    private static final BlockPos HEAT = new BlockPos(4, 2, 4);
    private static final BlockPos POT = new BlockPos(4, 3, 4);

    private static void useOn(GameTestHelper h, ServerPlayer p, BlockPos rel, ItemStack held) {
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos abs = h.absolutePos(rel);
        h.getLevel().getBlockState(abs).useItemOn(held, h.getLevel(), p, InteractionHand.MAIN_HAND,
                new BlockHitResult(abs.getCenter(), Direction.UP, abs, false));
    }

    private static void emptyHandOn(GameTestHelper h, ServerPlayer p, BlockPos rel) {
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockPos abs = h.absolutePos(rel);
        h.getLevel().getBlockState(abs).useWithoutItem(h.getLevel(), p,
                new BlockHitResult(abs.getCenter(), Direction.UP, abs, false));
    }

    private static CookingPotBlockEntity pot(GameTestHelper h) {
        return (CookingPotBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(POT));
    }

    private static void tickPot(GameTestHelper h, int ticks) {
        BlockPos abs = h.absolutePos(POT);
        for (int i = 0; i < ticks; i++) {
            CookingPotBlockEntity.serverTick(h.getLevel(), abs, h.getLevel().getBlockState(abs), pot(h));
        }
    }

    /** A pot on a lit campfire (or on stone), and the pasty ingredients put in by right-click. */
    private static ServerPlayer potWithPasty(GameTestHelper h, boolean hot) {
        stonePad(h);
        if (hot) {
            h.setBlock(HEAT, Blocks.CAMPFIRE);
        }
        h.setBlock(POT, CosyKitchen.COOKING_POT.get());
        ServerPlayer p = survivor(h);
        for (net.minecraft.world.item.Item i : new net.minecraft.world.item.Item[]{
                Items.PUMPKIN, Items.WHEAT, Items.WHEAT, Items.EGG, Items.SUGAR}) {
            useOn(h, p, POT, new ItemStack(i));
        }
        return p;
    }

    private static void wand(GameTestHelper h, ServerPlayer p) {
        useOn(h, p, POT, new ItemStack(ExampleMod.HARRY_POTTER_WAND.get()));
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void wandPotCooksPasties(GameTestHelper h) {
        ServerPlayer p = potWithPasty(h, true);
        h.assertTrue(pot(h).contents().size() == 5, "five ingredients in, got " + pot(h).contents().size());
        wand(h, p);
        h.assertTrue(pot(h).isEnchanted(), "the wand enchants the ladle");
        tickPot(h, 400);
        h.assertTrue(pot(h).isDone(), "the pasties are done after 400 ticks, progress " + pot(h).progress());
        emptyHandOn(h, p, POT);
        h.assertTrue(count(p, CosyFood.PUMPKIN_PASTY.get()) == 3, "three pasties, got " + count(p, CosyFood.PUMPKIN_PASTY.get()));
        h.assertTrue(pot(h).isEmpty(), "the pot is empty again");
        h.assertTrue(!h.getLevel().getBlockState(h.absolutePos(POT)).getValue(com.hogwartsmagic.cosy.CookingPotBlock.FILLED),
                "and drawn empty");
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void potOnStoneDoesNotCook(GameTestHelper h) {
        ServerPlayer p = potWithPasty(h, false);
        wand(h, p);
        tickPot(h, 400);
        h.assertTrue(!pot(h).isDone(), "a cold pot does not cook");
        h.assertTrue(pot(h).progress() == 0, "and makes no progress");
        h.assertTrue(pot(h).contents().size() == 5, "the ingredients are all still there");
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void potKeepsProgressWhenHeatRemoved(GameTestHelper h) {
        ServerPlayer p = potWithPasty(h, true);
        wand(h, p);
        tickPot(h, 100);
        h.assertTrue(pot(h).progress() == 100, "100 ticks of progress, got " + pot(h).progress());
        h.setBlock(HEAT, Blocks.STONE);
        tickPot(h, 100);
        h.assertTrue(pot(h).progress() == 100, "no progress and none lost without heat, got " + pot(h).progress());
        h.assertTrue(pot(h).contents().size() == 5, "the ingredients stay");
        h.setBlock(HEAT, Blocks.CAMPFIRE);
        tickPot(h, 300);
        h.assertTrue(pot(h).isDone(), "it finishes once the heat is back, progress " + pot(h).progress());
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void handStirringCooksForThreeSeconds(GameTestHelper h) {
        ServerPlayer p = potWithPasty(h, true);
        emptyHandOn(h, p, POT);
        tickPot(h, 100);
        h.assertTrue(pot(h).progress() == 60, "three seconds of progress from one stir, got " + pot(h).progress());
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void sneakingTakesIngredientsBack(GameTestHelper h) {
        ServerPlayer p = potWithPasty(h, true);
        wand(h, p);
        p.setShiftKeyDown(true);
        emptyHandOn(h, p, POT);
        p.setShiftKeyDown(false);
        h.assertTrue(pot(h).isEmpty(), "the pot is empty");
        h.assertTrue(!pot(h).isEnchanted(), "and the ladle is plain again");
        h.assertTrue(count(p, Items.WHEAT) == 2 && count(p, Items.SUGAR) == 1 && count(p, Items.EGG) == 1
                && count(p, Items.PUMPKIN) == 1, "every ingredient came back");
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void brokenPotDropsIngredients(GameTestHelper h) {
        stonePad(h);
        h.setBlock(POT, CosyKitchen.COOKING_POT.get());
        ServerPlayer p = survivor(h);
        useOn(h, p, POT, new ItemStack(Items.SUGAR));
        useOn(h, p, POT, new ItemStack(Items.WHEAT));
        h.getLevel().destroyBlock(h.absolutePos(POT), false);
        List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(h.absolutePos(POT)).inflate(2.0));
        boolean sugar = drops.stream().anyMatch(e -> e.getItem().is(Items.SUGAR));
        boolean wheat = drops.stream().anyMatch(e -> e.getItem().is(Items.WHEAT));
        h.assertTrue(sugar && wheat, "both ingredients dropped: sugar " + sugar + " wheat " + wheat);
        drops.forEach(ItemEntity::discard);
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void containersComeBackFromThePot(GameTestHelper h) {
        stonePad(h);
        h.setBlock(HEAT, Blocks.CAMPFIRE);
        h.setBlock(POT, CosyKitchen.COOKING_POT.get());
        ServerPlayer p = survivor(h);
        for (net.minecraft.world.item.Item i : new net.minecraft.world.item.Item[]{
                Items.COCOA_BEANS, Items.COCOA_BEANS, Items.SUGAR, Items.MILK_BUCKET}) {
            useOn(h, p, POT, new ItemStack(i));
        }
        wand(h, p);
        tickPot(h, 300);
        h.assertTrue(pot(h).isDone(), "the chocolate is done");
        emptyHandOn(h, p, POT);
        h.assertTrue(count(p, CosyFood.CHOCOLATE_BAR.get()) == 3, "three bars");
        h.assertTrue(count(p, Items.BUCKET) == 1, "and the milk bucket comes back as a bucket");
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void allSixPotRecipesLoad(GameTestHelper h) {
        var recipes = h.getLevel().getRecipeManager().getAllRecipesFor(CosyKitchen.POT_COOKING_TYPE.get());
        h.assertTrue(recipes.size() == 6, "six pot recipes parsed, got " + recipes.size());
        for (var holder : recipes) {
            PotCookingRecipe r = holder.value();
            h.assertTrue(!r.result().isEmpty() && r.cookTicks() >= 300 && r.cookTicks() <= 500,
                    holder.id() + " has a result and a cook time of 300 to 500 ticks");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void stoveFuelLights(GameTestHelper h) {
        stonePad(h);
        h.setBlock(HEAT, CosyKitchen.ENCHANTED_STOVE.get());
        ServerPlayer p = survivor(h);
        BlockPos abs = h.absolutePos(HEAT);
        h.assertTrue(!h.getLevel().getBlockState(abs).getValue(EnchantedStoveBlock.LIT), "a new stove is out");
        useOn(h, p, HEAT, new ItemStack(Items.COAL));
        h.assertTrue(h.getLevel().getBlockState(abs).getValue(EnchantedStoveBlock.LIT), "coal lights it");
        EnchantedStoveBlockEntity stove = (EnchantedStoveBlockEntity) h.getLevel().getBlockEntity(abs);
        h.assertTrue(stove.burn() == 1600, "a coal is 1600 ticks, got " + stove.burn());
        h.assertTrue(h.getLevel().getBlockState(abs).getLightEmission(h.getLevel(), abs) == 13, "and it gives light 13");
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void stoveHoldsEightCoalsAndBurnsOut(GameTestHelper h) {
        stonePad(h);
        h.setBlock(HEAT, CosyKitchen.ENCHANTED_STOVE.get());
        ServerPlayer p = survivor(h);
        BlockPos abs = h.absolutePos(HEAT);
        for (int i = 0; i < 8; i++) {
            useOn(h, p, HEAT, new ItemStack(Items.COAL));
        }
        ItemStack ninth = new ItemStack(Items.COAL);
        useOn(h, p, HEAT, ninth);
        h.assertTrue(ninth.getCount() == 1, "the ninth coal is refused");
        EnchantedStoveBlockEntity stove = (EnchantedStoveBlockEntity) h.getLevel().getBlockEntity(abs);
        h.assertTrue(stove.burn() == 8 * 1600, "eight coals worth, got " + stove.burn());
        // A heat source for a pot, only while lit.
        h.setBlock(POT, CosyKitchen.COOKING_POT.get());
        h.assertTrue(CosyKitchen.isHeatedBelow(h.getLevel(), h.absolutePos(POT)), "a lit stove heats the pot");
        for (int i = 0; i < 8 * 1600 + 2; i++) {
            EnchantedStoveBlockEntity.serverTick(h.getLevel(), abs, h.getLevel().getBlockState(abs), stove);
        }
        h.assertTrue(!h.getLevel().getBlockState(abs).getValue(EnchantedStoveBlock.LIT), "it goes out when the fuel is spent");
        h.assertTrue(!CosyKitchen.isHeatedBelow(h.getLevel(), h.absolutePos(POT)), "and no longer heats the pot");
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void heatSourceTagKnowsItsMembers(GameTestHelper h) {
        h.assertTrue(CosyKitchen.isHeat(Blocks.CAMPFIRE.defaultBlockState()), "a lit campfire is heat");
        h.assertTrue(!CosyKitchen.isHeat(Blocks.CAMPFIRE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, false)), "an unlit one is not");
        h.assertTrue(CosyKitchen.isHeat(Blocks.MAGMA_BLOCK.defaultBlockState()), "magma is heat");
        h.assertTrue(CosyKitchen.isHeat(Blocks.LAVA.defaultBlockState()), "lava is heat");
        h.assertTrue(CosyKitchen.isHeat(Blocks.FIRE.defaultBlockState()), "fire is heat");
        h.assertTrue(CosyKitchen.isHeat(ExampleMod.FLOO_FIREPLACE.get().defaultBlockState()), "the Floo hearth is heat");
        h.assertTrue(!CosyKitchen.isHeat(Blocks.STONE.defaultBlockState()), "stone is not");
        h.succeed();
    }

    // ---------------------------------------------------------------- fix round 1

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void lastFrogBoxKeepsItsCardThroughTheRealUsePath(GameTestHelper h) {
        stonePad(h);
        ServerPlayer p = survivor(h);
        for (int i = 0; i < p.getInventory().items.size(); i++) {
            p.getInventory().items.set(i, new ItemStack(Items.DIAMOND_SWORD));
        }
        p.getInventory().items.set(0, new ItemStack(CosyFood.CHOCOLATE_FROG.get()));
        p.getInventory().selected = 0;
        p.gameMode.useItem(p, h.getLevel(), p.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND);
        List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(3.0),
                e -> e.getItem().is(CosyFood.WIZARD_CARD.get()));
        h.assertTrue(cardsInInventory(p) + drops.size() == 1,
                "exactly one card is in the pack or on the ground: pack " + cardsInInventory(p) + ", ground " + drops.size());
        drops.forEach(ItemEntity::discard);
        frogsAround(h, p).forEach(ChocolateFrogEntity::discard);
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void kitchenBlocksDropWhenMinedWithTheRightTool(GameTestHelper h) {
        stonePad(h);
        net.minecraft.world.level.block.Block[] blocks = {CosyKitchen.ENCHANTED_STOVE.get(), CosyKitchen.COOKING_POT.get()};
        net.minecraft.world.item.Item[] items = {CosyKitchen.ENCHANTED_STOVE.get().asItem(), CosyKitchen.COOKING_POT.get().asItem()};
        for (int i = 0; i < blocks.length; i++) {
            h.assertTrue(blocks[i].defaultBlockState().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE),
                    blocks[i] + " is in mineable/pickaxe");
            ServerPlayer p = survivor(h);
            p.getInventory().items.set(0, new ItemStack(Items.IRON_PICKAXE));
            p.getInventory().selected = 0;
            net.minecraft.world.item.Item wanted = items[i];
            BlockPos at = new BlockPos(2 + i * 2, 2, 2);
            h.setBlock(at, blocks[i]);
            h.assertTrue(p.getMainHandItem().isCorrectToolForDrops(h.getLevel().getBlockState(h.absolutePos(at))),
                    "an iron pickaxe is the right tool for " + blocks[i]);
            p.gameMode.destroyBlock(h.absolutePos(at));
            List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new net.minecraft.world.phys.AABB(h.absolutePos(at)).inflate(1.5), e -> e.getItem().is(wanted));
            h.assertTrue(drops.size() == 1, blocks[i] + " dropped itself when mined, found " + drops.size());
            drops.forEach(ItemEntity::discard);
        }
        h.assertTrue(CosyFood.BUTTERBEER_KEG.get().defaultBlockState().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE),
                "the keg is in mineable/axe");
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void brokenKegDropsPartialIngredients(GameTestHelper h) {
        stonePad(h);
        h.setBlock(KEG, CosyFood.BUTTERBEER_KEG.get());
        ServerPlayer p = survivor(h);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SUGAR));
        click(h, p);
        h.getLevel().destroyBlock(h.absolutePos(KEG), false);
        List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(h.absolutePos(KEG)).inflate(2.0), e -> e.getItem().is(Items.SUGAR));
        h.assertTrue(drops.size() == 1, "the sugar comes back, found " + drops.size());
        drops.forEach(ItemEntity::discard);
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void stoveTakesALavaBucketAndGivesBackABucket(GameTestHelper h) {
        stonePad(h);
        h.setBlock(HEAT, CosyKitchen.ENCHANTED_STOVE.get());
        ServerPlayer p = survivor(h);
        useOn(h, p, HEAT, new ItemStack(Items.LAVA_BUCKET));
        EnchantedStoveBlockEntity stove = (EnchantedStoveBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(HEAT));
        h.assertTrue(stove.burn() == 8 * 1600, "clamped to the cap, got " + stove.burn());
        h.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).is(Items.BUCKET), "an empty bucket comes back");
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void albumTakesACardFromTheOffHand(GameTestHelper h) {
        stonePad(h);
        ServerPlayer p = survivor(h);
        p.setItemInHand(InteractionHand.OFF_HAND, com.hogwartsmagic.cosy.WizardCardItem.create(WizardCards.ALL.get(3)));
        ItemStack album = new ItemStack(CosyFood.CARD_ALBUM.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, album);
        album.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(album.getOrDefault(CosyFood.ALBUM_CARDS.get(), List.of()).contains(WizardCards.ALL.get(3).id()),
                "the off-hand card is in the album");
        h.assertTrue(p.getOffhandItem().isEmpty(), "and left the off hand");
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "cosy_food")
    public static void wandGuardLeavesTheServerAlone(GameTestHelper h) {
        stonePad(h);
        h.setBlock(POT, CosyKitchen.COOKING_POT.get());
        ServerPlayer p = survivor(h);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ExampleMod.HARRY_POTTER_WAND.get()));
        BlockPos abs = h.absolutePos(POT);
        var event = new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(p,
                InteractionHand.MAIN_HAND, abs, new BlockHitResult(abs.getCenter(), Direction.UP, abs, false));
        com.hogwartsmagic.cosy.PotWandTapGuard.onRightClickBlock(event);
        h.assertTrue(!event.isCanceled(), "on the server the guard must not cancel, or the pot would never be used");
        h.succeed();
    }
}
