package com.hogwartsmagic.cosy;

import com.hogwartsmagic.ExampleMod;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Cosy food: chocolate, Chocolate Frogs and Famous Wizard Cards, the card album, Bertie Bott's beans, meals and
 * butterbeer. Everything queues on the mod's own deferred registers from {@link #init()}, called once from the
 * {@code ExampleMod} constructor before those registers attach.
 */
public final class CosyFood {
    private CosyFood() {
    }

    /** How long Chocolate Courage lasts, in ticks. */
    public static final int COURAGE_TICKS = FoodRules.COURAGE_TICKS;

    private static final List<RegistryObject<Item>> ITEMS = new ArrayList<>();
    /** Food items in creative-tab order (a Famous Wizard Card is added separately, one per card). */
    public static final List<RegistryObject<Item>> CREATIVE_ORDER = Collections.unmodifiableList(ITEMS);

    private static RegistryObject<Item> item(String id, Supplier<Item> factory) {
        RegistryObject<Item> item = ExampleMod.ITEMS.register(id, factory);
        ITEMS.add(item);
        return item;
    }

    // ---- Data components --------------------------------------------------------------------------

    /** Which Famous Wizard Card a card stack is (a {@link WizardCards.Card} id). */
    public static final RegistryObject<DataComponentType<Integer>> CARD_ID = ExampleMod.DATA_COMPONENTS.register("card_id",
            () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.intRange(1, 1000))
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /** The card ids an album holds, in the order they were collected. */
    public static final RegistryObject<DataComponentType<List<Integer>>> ALBUM_CARDS = ExampleMod.DATA_COMPONENTS.register("album_cards",
            () -> DataComponentType.<List<Integer>>builder()
                    .persistent(Codec.INT.listOf())
                    .networkSynchronized(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()))
                    .build());

    /** Beans left in a box of Bertie Bott's (absent reads as a full box of ten). */
    public static final RegistryObject<DataComponentType<Integer>> BEANS_LEFT = ExampleMod.DATA_COMPONENTS.register("beans_left",
            () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.intRange(0, BeanTable.BOX_SIZE))
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    // ---- Effects ----------------------------------------------------------------------------------

    /** While it lasts, a Dementor's slowing aura skips you. Eating chocolate gives it. */
    public static final RegistryObject<MobEffect> CHOCOLATE_COURAGE = ExampleMod.MOB_EFFECTS.register("chocolate_courage",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x7A4A28) {});

    // ---- Chocolate and meals ----------------------------------------------------------------------

    public static final RegistryObject<Item> CHOCOLATE_BAR = item("chocolate_bar",
            () -> new ChocolateItem(new Item.Properties().food(
                    new FoodProperties.Builder().nutrition(4).saturationModifier(0.6f).fast().build())));

    public static final RegistryObject<Item> PUMPKIN_PASTY = item("pumpkin_pasty",
            () -> new Item(new Item.Properties().food(
                    new FoodProperties.Builder().nutrition(6).saturationModifier(0.7f).build())));

    public static final RegistryObject<Item> TREACLE_TART = item("treacle_tart",
            () -> new Item(new Item.Properties().food(
                    new FoodProperties.Builder().nutrition(7).saturationModifier(0.6f).build())));

    public static final RegistryObject<Item> CAULDRON_CAKE = item("cauldron_cake",
            () -> new Item(new Item.Properties().food(
                    new FoodProperties.Builder().nutrition(5).saturationModifier(0.8f).build())));

    /** An empty tankard: it takes butterbeer from the keg and comes back after every drink. */
    public static final RegistryObject<Item> TANKARD = item("tankard",
            () -> new Item(new Item.Properties().stacksTo(16)));

    // ---- Chocolate Frogs, cards and the album -----------------------------------------------------

    /** A box of Chocolate Frog: opening it gives a card and a hopping frog. */
    public static final RegistryObject<Item> CHOCOLATE_FROG = item("chocolate_frog",
            () -> new ChocolateFrogItem(new Item.Properties().stacksTo(16)));

    /** A Famous Wizard Card. Not in {@link #CREATIVE_ORDER}: the creative tab lists one stack per card. */
    public static final RegistryObject<Item> WIZARD_CARD =
            ExampleMod.ITEMS.register("wizard_card", () -> new WizardCardItem(new Item.Properties()));

    public static final RegistryObject<Item> CARD_ALBUM = item("card_album",
            () -> new CardAlbumItem(new Item.Properties()));

    // ---- Bertie Bott's Every Flavour Beans --------------------------------------------------------

    /** A box of ten beans; each bite takes one (16 ticks) and rolls a flavour. */
    public static final RegistryObject<Item> BERTIE_BOTTS_BEANS = item("bertie_botts_beans",
            () -> new BertieBottsItem(new Item.Properties().stacksTo(1).food(
                    new FoodProperties.Builder().nutrition(1).saturationModifier(0.1f).alwaysEdible().fast().build())));

    // ---- Butterbeer -------------------------------------------------------------------------------

    /** A barrel on its side with a tap: sugar, honey and wheat age into eight tankards of butterbeer. */
    public static final RegistryObject<Block> BUTTERBEER_KEG = ExampleMod.BLOCKS.register("butterbeer_keg",
            () -> new ButterbeerKegBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL).strength(2.0f)));

    public static final RegistryObject<Item> BUTTERBEER_KEG_ITEM = item("butterbeer_keg",
            () -> new BlockItem(BUTTERBEER_KEG.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<ButterbeerKegBlockEntity>> BUTTERBEER_KEG_BLOCK_ENTITY =
            ExampleMod.BLOCK_ENTITY_TYPES.register("butterbeer_keg",
                    () -> BlockEntityType.Builder.of(ButterbeerKegBlockEntity::new, BUTTERBEER_KEG.get()).build(null));

    /** A drink from the keg: food 4, Warmed for three minutes, and the tankard comes back. */
    public static final RegistryObject<Item> BUTTERBEER = item("tankard_of_butterbeer",
            () -> new ButterbeerItem(new Item.Properties().stacksTo(1).food(
                    new FoodProperties.Builder().nutrition(4).saturationModifier(0.3f).alwaysEdible().build())));

    /** Loads this class so every entry is queued on the deferred registers before they attach. */
    public static void init() {
        // The packet channel registers on common setup, as the owl post channel does.
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
                (net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(CosyNet::register));
    }

    /** Adds the food items to the main creative tab. */
    public static void fillCreativeTab(CreativeModeTab.Output output) {
        CREATIVE_ORDER.forEach(item -> output.accept(item.get()));
        for (WizardCards.Card card : WizardCards.ALL) {
            output.accept(WizardCardItem.create(card));
        }
    }
}
