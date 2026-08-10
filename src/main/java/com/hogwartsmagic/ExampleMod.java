package com.hogwartsmagic;

import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.item.HogwartsInviteItem;
import com.hogwartsmagic.item.HogwartsGuideItem;
import com.hogwartsmagic.item.MagicalSilkItem;
import com.hogwartsmagic.item.PolyjuicePotionItem;
import com.hogwartsmagic.item.PolyjuiceSampleItem;
import com.hogwartsmagic.item.SampleCollectorItem;
import com.hogwartsmagic.item.GillyweedItem;
import com.hogwartsmagic.item.InvisibilityCloakItem;
import com.hogwartsmagic.item.SortingHatItem;
import com.hogwartsmagic.block.GillyweedPlantBlock;
import com.hogwartsmagic.block.MandrakePlantBlock;
import com.hogwartsmagic.network.SpellPackets;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.event.brewing.BrewingRecipeRegisterEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.util.Optional;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(ExampleMod.MODID)
public class ExampleMod
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "hogwartsmagic";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "hogwartsmagic" namespace
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "hogwartsmagic" namespace
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "hogwartsmagic" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<net.minecraft.world.effect.MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);
    public static final DeferredRegister<net.minecraft.world.level.levelgen.feature.Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, MODID);
    public static final DeferredRegister<net.minecraft.world.level.levelgen.structure.StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, MODID);
    public static final DeferredRegister<net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType> STRUCTURE_PIECE_TYPES = DeferredRegister.create(Registries.STRUCTURE_PIECE, MODID);
    public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.minecraftforge.common.loot.IGlobalLootModifier>> LOOT_MODIFIER_SERIALIZERS = DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MODID);

    public static final RegistryObject<com.mojang.serialization.MapCodec<com.hogwartsmagic.loot.MagicBookLootModifier>> MAGIC_BOOK_LOOT_MODIFIER =
            LOOT_MODIFIER_SERIALIZERS.register("magic_books", () -> com.hogwartsmagic.loot.MagicBookLootModifier.CODEC);
    public static final RegistryObject<com.mojang.serialization.MapCodec<com.hogwartsmagic.loot.SortingHatLootModifier>> SORTING_HAT_LOOT_MODIFIER =
            LOOT_MODIFIER_SERIALIZERS.register("sorting_hat_chest", () -> com.hogwartsmagic.loot.SortingHatLootModifier.CODEC);
    public static final RegistryObject<com.mojang.serialization.MapCodec<com.hogwartsmagic.loot.InvisibilityCloakLootModifier>> INVISIBILITY_CLOAK_LOOT_MODIFIER =
            LOOT_MODIFIER_SERIALIZERS.register("invisibility_cloak_chest", () -> com.hogwartsmagic.loot.InvisibilityCloakLootModifier.CODEC);
    public static final RegistryObject<com.mojang.serialization.MapCodec<com.hogwartsmagic.loot.MagicalSilkLootModifier>> MAGICAL_SILK_LOOT_MODIFIER =
            LOOT_MODIFIER_SERIALIZERS.register("magical_silk_chest", () -> com.hogwartsmagic.loot.MagicalSilkLootModifier.CODEC);
    public static final RegistryObject<MenuType<com.hogwartsmagic.menu.WandWorkbenchMenu>> WAND_WORKBENCH_MENU =
            MENUS.register("wand_workbench", () -> IForgeMenuType.create((windowId, inv, buf) -> {
                net.minecraft.core.BlockPos pos = buf.readBlockPos();
                net.minecraft.world.level.block.entity.BlockEntity be = inv.player.level().getBlockEntity(pos);
                if (be instanceof com.hogwartsmagic.block.entity.WandWorkbenchBlockEntity wb) {
                    return new com.hogwartsmagic.menu.WandWorkbenchMenu(windowId, inv, wb);
                }
                return com.hogwartsmagic.menu.WandWorkbenchMenu.createInvalid(windowId, inv, pos);
            }));
    public static final RegistryObject<MenuType<com.hogwartsmagic.menu.BroomWorkbenchMenu>> BROOM_WORKBENCH_MENU =
            MENUS.register("broom_workbench", () -> IForgeMenuType.create((windowId, inv, buf) -> {
                net.minecraft.core.BlockPos pos = buf.readBlockPos();
                net.minecraft.world.level.block.entity.BlockEntity be = inv.player.level().getBlockEntity(pos);
                if (be instanceof com.hogwartsmagic.block.entity.BroomWorkbenchBlockEntity bb) {
                    return new com.hogwartsmagic.menu.BroomWorkbenchMenu(windowId, inv, bb);
                }
                return com.hogwartsmagic.menu.BroomWorkbenchMenu.createInvalid(windowId, inv, pos);
            }));

    public static final RegistryObject<net.minecraft.world.level.levelgen.feature.Feature<net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration>> COBWEB_FEATURE = 
            FEATURES.register("cobwebs", () -> new com.hogwartsmagic.worldgen.CobwebFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<net.minecraft.world.level.levelgen.feature.Feature<net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration>> ARCANE_CLUSTER_FEATURE =
            FEATURES.register("arcane_cluster_patch", () -> new com.hogwartsmagic.worldgen.ArcaneClusterFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<net.minecraft.world.level.levelgen.feature.Feature<net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration>> GILLYWEED_SWAMP_FEATURE =
            FEATURES.register("gillyweed_swamp", () -> new com.hogwartsmagic.worldgen.GillyweedSwampFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<net.minecraft.world.level.levelgen.feature.Feature<net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration>> MANDRAKE_PATCH_FEATURE =
            FEATURES.register("mandrake_patch", () -> new com.hogwartsmagic.worldgen.MandrakePatchFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<net.minecraft.world.level.levelgen.feature.Feature<net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration>> DIAGON_ALLEY_FEATURE =
            FEATURES.register("diagon_alley", () -> new com.hogwartsmagic.worldgen.DiagonAlleyFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));
    // Lumos Effect
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> LUMOS_EFFECT = MOB_EFFECTS.register("lumos",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.BENEFICIAL, 0xFFFFAA) {
                @Override
                public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
                    return true;
                }
            });

    // Protego Shield Effect
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> PROTEGO_EFFECT = MOB_EFFECTS.register("protego",
            com.hogwartsmagic.spell.ProtegoEffect::new);

    public static final RegistryObject<net.minecraft.world.effect.MobEffect> TENEBRIS_FLIGHT_EFFECT = MOB_EFFECTS.register("tenebris_flight",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.BENEFICIAL, 0x111111) {
                @Override
                public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
                    return true;
                }
            });

    /** Marker for Incarcerous chain visuals (no attributes); duration matches bind. */
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> INCARCEROUS_CHAINS = MOB_EFFECTS.register("incarcerous_chains",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0x3A3A44) {});

    /** Marker for Levicorpus upside-down body pose (client render); paired with vanilla levitation for motion. */
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> LEVICORPUS_EFFECT = MOB_EFFECTS.register("levicorpus",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0x8844FF) {});

    public static final RegistryObject<net.minecraft.world.effect.MobEffect> POLYJUICE_EFFECT = MOB_EFFECTS.register("polyjuice",
            com.hogwartsmagic.spell.PolyjuiceEffect::new);

    public static final RegistryObject<net.minecraft.world.effect.MobEffect> GILLYWEED_EFFECT = MOB_EFFECTS.register("gillyweed",
            com.hogwartsmagic.spell.GillyweedEffect::new);

    /** Intermediate brew: Awkward + acromantula venom for the mana regeneration potion (vanilla potion bottle). */
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> WIZARD_ELIXIR_EFFECT = MOB_EFFECTS.register("wizard_elixir",
            com.hogwartsmagic.spell.WizardElixirEffect::new);

    public static final DeferredRegister<net.minecraft.world.item.alchemy.Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, MODID);

    public static final RegistryObject<net.minecraft.world.item.alchemy.Potion> WIZARD_POTION = POTIONS.register("wizard",
            () -> new net.minecraft.world.item.alchemy.Potion(new net.minecraft.world.effect.MobEffectInstance(
                    WIZARD_ELIXIR_EFFECT.getHolder().get(), 20 * 60 * 3, 0)));
    public static final RegistryObject<net.minecraft.world.item.alchemy.Potion> STABILIZED_POLYJUICE_BASE = POTIONS.register("stabilized_polyjuice_base",
            net.minecraft.world.item.alchemy.Potion::new);

    public static final RegistryObject<net.minecraft.world.effect.MobEffect> BASILISK_PETRIFICATION = MOB_EFFECTS.register("basilisk_petrification",
            com.hogwartsmagic.spell.BasiliskPetrificationEffect::new);

    public static final RegistryObject<Block> WAND_WORKBENCH = BLOCKS.register("wand_workbench",
            () -> new com.hogwartsmagic.block.WandWorkbenchBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SMITHING_TABLE)));

    public static final RegistryObject<BlockEntityType<com.hogwartsmagic.block.entity.WandWorkbenchBlockEntity>> WAND_WORKBENCH_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("wand_workbench", () -> BlockEntityType.Builder.of(
                    com.hogwartsmagic.block.entity.WandWorkbenchBlockEntity::new,
                    WAND_WORKBENCH.get()
            ).build(null));

    public static final RegistryObject<Block> BROOM_WORKBENCH = BLOCKS.register("broom_workbench",
            () -> new com.hogwartsmagic.block.BroomWorkbenchBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE)));

    public static final RegistryObject<BlockEntityType<com.hogwartsmagic.block.entity.BroomWorkbenchBlockEntity>> BROOM_WORKBENCH_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("broom_workbench", () -> BlockEntityType.Builder.of(
                    com.hogwartsmagic.block.entity.BroomWorkbenchBlockEntity::new,
                    BROOM_WORKBENCH.get()
            ).build(null));

    public static final RegistryObject<Block> MOVING_PORTRAIT = BLOCKS.register("moving_portrait",
            () -> new com.hogwartsmagic.block.MovingPortraitBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(1.0f)));
    public static final RegistryObject<Block> CANDLE_CLUSTER = BLOCKS.register("candle_cluster",
            () -> new com.hogwartsmagic.block.CandleClusterBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CANDLE).strength(0.8f)));
    public static final RegistryObject<Block> ENCHANTED_BOOK_PILE = BLOCKS.register("enchanted_book_pile",
            () -> new com.hogwartsmagic.block.EnchantedBookPileBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BOOKSHELF).strength(1.2f)));
    public static final RegistryObject<Block> WIZARD_CAULDRON = BLOCKS.register("wizard_cauldron",
            () -> new com.hogwartsmagic.block.WizardCauldronBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CAULDRON).strength(1.5f)));
    public static final RegistryObject<Block> OWL_PERCH = BLOCKS.register("owl_perch",
            () -> new com.hogwartsmagic.block.OwlPerchBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE).strength(1.0f)));
    public static final RegistryObject<Block> FLOO_FIREPLACE = BLOCKS.register("floo_fireplace",
            () -> new com.hogwartsmagic.block.FlooFireplaceBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SOUL_CAMPFIRE).strength(2.5f).lightLevel(state -> 12)));
    public static final RegistryObject<Block> WARD = BLOCKS.register("ward",
            () -> new com.hogwartsmagic.block.WardBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK)
                    .strength(2.0f)
                    .noOcclusion()
                    .lightLevel(state -> state.hasProperty(com.hogwartsmagic.block.WardBlock.LIT)
                            && state.getValue(com.hogwartsmagic.block.WardBlock.LIT) ? 7 : 0)));
    public static final RegistryObject<Block> HORCRUX = BLOCKS.register("horcrux",
            () -> new com.hogwartsmagic.block.HorcruxBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CRYING_OBSIDIAN)
                    .strength(4.0f)
                    .noOcclusion()
                    .lightLevel(state -> state.hasProperty(com.hogwartsmagic.block.HorcruxBlock.LIT)
                            && state.getValue(com.hogwartsmagic.block.HorcruxBlock.LIT) ? 8 : 1)));
    public static final RegistryObject<Block> GRYFFINDOR_BANNER_BLOCK = BLOCKS.register("gryffindor_banner_block",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.RED_WOOL)));
    public static final RegistryObject<Block> RAVENCLAW_BANNER_BLOCK = BLOCKS.register("ravenclaw_banner_block",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.BLUE_WOOL)));
    public static final RegistryObject<Block> HUFFLEPUFF_BANNER_BLOCK = BLOCKS.register("hufflepuff_banner_block",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.YELLOW_WOOL)));
    public static final RegistryObject<Block> SLYTHERIN_BANNER_BLOCK = BLOCKS.register("slytherin_banner_block",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.GREEN_WOOL)));

    public static final RegistryObject<BlockEntityType<com.hogwartsmagic.block.entity.OwlPerchBlockEntity>> OWL_PERCH_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("owl_perch", () -> BlockEntityType.Builder.of(
                    com.hogwartsmagic.block.entity.OwlPerchBlockEntity::new,
                    OWL_PERCH.get()
            ).build(null));
    public static final RegistryObject<BlockEntityType<com.hogwartsmagic.block.entity.FlooFireplaceBlockEntity>> FLOO_FIREPLACE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("floo_fireplace", () -> BlockEntityType.Builder.of(
                    com.hogwartsmagic.block.entity.FlooFireplaceBlockEntity::new,
                    FLOO_FIREPLACE.get()
            ).build(null));
    public static final RegistryObject<BlockEntityType<com.hogwartsmagic.block.entity.CaveInimicumWardBlockEntity>> CAVE_INIMICUM_WARD_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("ward", () -> BlockEntityType.Builder.of(
                    com.hogwartsmagic.block.entity.CaveInimicumWardBlockEntity::new,
                    WARD.get()
            ).build(null));
    public static final RegistryObject<BlockEntityType<com.hogwartsmagic.block.entity.HorcruxBlockEntity>> HORCRUX_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("horcrux", () -> BlockEntityType.Builder.of(
                    com.hogwartsmagic.block.entity.HorcruxBlockEntity::new,
                    HORCRUX.get()
            ).build(null));

    public static final ResourceKey<ConfiguredFeature<?, ?>> FINE_WOOD_TREE_CONFIGURED =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath(MODID, "fine_wood"));

    public static final TreeGrower FINE_WOOD_TREE_GROWER = new TreeGrower(
            "hogwartsmagic_fine_wood",
            Optional.empty(),
            Optional.of(FINE_WOOD_TREE_CONFIGURED),
            Optional.empty());

    public static final RegistryObject<Block> FINE_WOOD_LOG = BLOCKS.register("fine_wood_log",
            () -> new com.hogwartsmagic.block.FineWoodLogBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CHERRY_LOG)));
    public static final RegistryObject<Block> STRIPPED_FINE_WOOD_LOG = BLOCKS.register("stripped_fine_wood_log",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STRIPPED_CHERRY_LOG)));
    public static final RegistryObject<Block> FINE_WOOD_LEAVES = BLOCKS.register("fine_wood_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CHERRY_LEAVES)));
    public static final RegistryObject<Block> FINE_WOOD_SAPLING = BLOCKS.register("fine_wood_sapling",
            () -> new SaplingBlock(FINE_WOOD_TREE_GROWER, BlockBehaviour.Properties.ofFullCopy(Blocks.CHERRY_SAPLING)));
    public static final RegistryObject<Block> FINE_WOOD_PLANKS = BLOCKS.register("fine_wood_planks",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.CHERRY_PLANKS)));
    public static final RegistryObject<Block> ARCANE_CLUSTER = BLOCKS.register("arcane_cluster",
            () -> new AmethystClusterBlock(
                    7,
                    3,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_CLUSTER)
                            .strength(1.5f)
                            .lightLevel(state -> 7)));

    public static final RegistryObject<Block> GILLYWEED_PLANT = BLOCKS.register("gillyweed_plant",
            () -> new GillyweedPlantBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SEAGRASS)));
    public static final RegistryObject<Block> MANDRAKE_PLANT = BLOCKS.register("mandrake_plant",
            () -> new MandrakePlantBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.POPPY).noCollission().instabreak()));

    // Custom Invisible Light Block for Patronus
    public static final RegistryObject<Block> PATRONUS_LIGHT_BLOCK = BLOCKS.register("patronus_light_block", 
            () -> new com.hogwartsmagic.block.PatronusLightBlock(
                    BlockBehaviour.Properties.of()
                            .noCollission()
                            .noLootTable()
                            .instabreak()
                            .lightLevel(state -> 15)
                            .air()
            ));

    public static final RegistryObject<Item> WAND_WORKBENCH_ITEM = ITEMS.register("wand_workbench", () -> new BlockItem(WAND_WORKBENCH.get(), new Item.Properties()));
    public static final RegistryObject<Item> BROOM_WORKBENCH_ITEM = ITEMS.register("broom_workbench", () -> new BlockItem(BROOM_WORKBENCH.get(), new Item.Properties()));
    public static final RegistryObject<Item> FINE_WOOD_LOG_ITEM = ITEMS.register("fine_wood_log", () -> new BlockItem(FINE_WOOD_LOG.get(), new Item.Properties()));
    public static final RegistryObject<Item> STRIPPED_FINE_WOOD_LOG_ITEM = ITEMS.register("stripped_fine_wood_log", () -> new BlockItem(STRIPPED_FINE_WOOD_LOG.get(), new Item.Properties()));
    public static final RegistryObject<Item> FINE_WOOD_LEAVES_ITEM = ITEMS.register("fine_wood_leaves", () -> new BlockItem(FINE_WOOD_LEAVES.get(), new Item.Properties()));
    public static final RegistryObject<Item> FINE_WOOD_SAPLING_ITEM = ITEMS.register("fine_wood_sapling", () -> new BlockItem(FINE_WOOD_SAPLING.get(), new Item.Properties()));
    public static final RegistryObject<Item> FINE_WOOD_PLANKS_ITEM = ITEMS.register("fine_wood_planks", () -> new BlockItem(FINE_WOOD_PLANKS.get(), new Item.Properties()));
    public static final RegistryObject<Item> ARCANE_CLUSTER_ITEM = ITEMS.register("arcane_cluster", () -> new BlockItem(ARCANE_CLUSTER.get(), new Item.Properties()));
    public static final RegistryObject<Item> GILLYWEED_PLANT_ITEM = ITEMS.register("gillyweed_plant", () -> new BlockItem(GILLYWEED_PLANT.get(), new Item.Properties()));
    public static final RegistryObject<Item> MANDRAKE_PLANT_ITEM = ITEMS.register("mandrake_plant", () -> new BlockItem(MANDRAKE_PLANT.get(), new Item.Properties()));
    public static final RegistryObject<Item> MOVING_PORTRAIT_ITEM = ITEMS.register("moving_portrait", () -> new BlockItem(MOVING_PORTRAIT.get(), new Item.Properties()));
    public static final RegistryObject<Item> CANDLE_CLUSTER_ITEM = ITEMS.register("candle_cluster", () -> new BlockItem(CANDLE_CLUSTER.get(), new Item.Properties()));
    public static final RegistryObject<Item> ENCHANTED_BOOK_PILE_ITEM = ITEMS.register("enchanted_book_pile", () -> new BlockItem(ENCHANTED_BOOK_PILE.get(), new Item.Properties()));
    public static final RegistryObject<Item> WIZARD_CAULDRON_ITEM = ITEMS.register("wizard_cauldron", () -> new BlockItem(WIZARD_CAULDRON.get(), new Item.Properties()));
    public static final RegistryObject<Item> OWL_PERCH_ITEM = ITEMS.register("owl_perch", () -> new BlockItem(OWL_PERCH.get(), new Item.Properties()));
    public static final RegistryObject<Item> FLOO_FIREPLACE_ITEM = ITEMS.register("floo_fireplace", () -> new BlockItem(FLOO_FIREPLACE.get(), new Item.Properties()));
    public static final RegistryObject<Item> WARD_ITEM = ITEMS.register("ward", () -> new BlockItem(WARD.get(), new Item.Properties()));
    public static final RegistryObject<Item> HORCRUX_ITEM = ITEMS.register("horcrux", () -> new BlockItem(HORCRUX.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> GRYFFINDOR_BANNER_BLOCK_ITEM = ITEMS.register("gryffindor_banner_block", () -> new BlockItem(GRYFFINDOR_BANNER_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> RAVENCLAW_BANNER_BLOCK_ITEM = ITEMS.register("ravenclaw_banner_block", () -> new BlockItem(RAVENCLAW_BANNER_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> HUFFLEPUFF_BANNER_BLOCK_ITEM = ITEMS.register("hufflepuff_banner_block", () -> new BlockItem(HUFFLEPUFF_BANNER_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> SLYTHERIN_BANNER_BLOCK_ITEM = ITEMS.register("slytherin_banner_block", () -> new BlockItem(SLYTHERIN_BANNER_BLOCK.get(), new Item.Properties()));

    // Harry Potter wand - opens spell menu with Shift + Right Click
    public static final RegistryObject<Item> HARRY_POTTER_WAND = ITEMS.register("harry_potter_wand",
            () -> new MagicWandItem(new Item.Properties()));
    public static final RegistryObject<Item> PHOENIX_CORE_WAND = ITEMS.register("phoenix_core_wand",
            () -> new MagicWandItem(new Item.Properties()));
    public static final RegistryObject<Item> UNICORN_CORE_WAND = ITEMS.register("unicorn_core_wand",
            () -> new MagicWandItem(new Item.Properties()));
    public static final RegistryObject<Item> DRAGON_CORE_WAND = ITEMS.register("dragon_core_wand",
            () -> new MagicWandItem(new Item.Properties()));
    public static final RegistryObject<Item> ELDER_WAND = ITEMS.register("elder_wand",
            () -> new MagicWandItem(new Item.Properties()));

    // Broom Items
    public static final RegistryObject<Item> BROOM = ITEMS.register("broom",
            () -> new com.hogwartsmagic.item.BroomItem(new Item.Properties().stacksTo(1), 0));
    public static final RegistryObject<Item> NIMBUS_2000 = ITEMS.register("nimbus_2000",
            () -> new com.hogwartsmagic.item.BroomItem(new Item.Properties().stacksTo(1), 1));
    public static final RegistryObject<Item> FIREBOLT = ITEMS.register("firebolt",
            () -> new com.hogwartsmagic.item.BroomItem(new Item.Properties().stacksTo(1), 2));
    public static final RegistryObject<Item> FINE_WOOD_STICK = ITEMS.register("fine_wood_stick",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ARCANE_DUST = ITEMS.register("arcane_dust",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ARCANE_CORE = ITEMS.register("arcane_core",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SAMPLE_COLLECTOR = ITEMS.register("sample_collector",
            () -> new SampleCollectorItem(new Item.Properties()));
    public static final RegistryObject<Item> POLYJUICE_SAMPLE = ITEMS.register("polyjuice_sample",
            () -> new PolyjuiceSampleItem(new Item.Properties()));
    public static final RegistryObject<Item> WOLF_FANG = ITEMS.register("wolf_fang",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CENTAUR_TALISMAN = ITEMS.register("centaur_talisman",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ACROMANTULA_VENOM = ITEMS.register("acromantula_venom",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> MANDRAKE_ROOT = ITEMS.register("mandrake_root",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> GILLYWEED = ITEMS.register("gillyweed",
            () -> new GillyweedItem(new Item.Properties()));
    public static final RegistryObject<Item> POLYJUICE_POTION = ITEMS.register("polyjuice_potion",
            () -> new PolyjuicePotionItem(new Item.Properties()
                    .craftRemainder(net.minecraft.world.item.Items.GLASS_BOTTLE)
                    .component(DataComponents.POTION_CONTENTS, PolyjuicePotionItem.defaultPotionContents())));
    public static final RegistryObject<Item> PHOENIX_FEATHER_CORE = ITEMS.register("phoenix_feather_core",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> UNICORN_HAIR_CORE = ITEMS.register("unicorn_hair_core",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> DRAGON_HEARTSTRING_CORE = ITEMS.register("dragon_heartstring_core",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> OWL_ROUTE_TAG = ITEMS.register("owl_route_tag",
            () -> new com.hogwartsmagic.item.OwlRouteTagItem(new Item.Properties()));
    public static final RegistryObject<Item> HOGWARTS_INVITE = ITEMS.register("hogwarts_invite",
            () -> new HogwartsInviteItem(new Item.Properties()));
    public static final RegistryObject<Item> HOGWARTS_GUIDE = ITEMS.register("hogwarts_guide",
            () -> new HogwartsGuideItem(new Item.Properties()));
    public static final RegistryObject<Item> FLOO_BINDING_TOKEN = ITEMS.register("floo_binding_token",
            () -> new com.hogwartsmagic.item.FlooBindingTokenItem(new Item.Properties()));
    public static final RegistryObject<Item> CAVE_INIMICUM_TOTEM = ITEMS.register("cave_inimicum_totem",
            () -> new com.hogwartsmagic.item.CaveInimicumTotemItem(new Item.Properties()));
    public static final RegistryObject<Item> FLOO_POWDER = ITEMS.register("floo_powder",
            () -> new com.hogwartsmagic.item.FlooPowderItem(new Item.Properties()));
    public static final RegistryObject<Item> MAGICAL_SILK = ITEMS.register("magical_silk",
            () -> new MagicalSilkItem(new Item.Properties()));
    public static final RegistryObject<Item> INVISIBILITY_CLOAK = ITEMS.register("invisibility_cloak",
            () -> new InvisibilityCloakItem(new Item.Properties()));
    public static final RegistryObject<Item> MINOR_MANA_CHARM = ITEMS.register("minor_mana_charm",
            () -> new com.hogwartsmagic.relic.RelicItem(com.hogwartsmagic.relic.RelicType.MINOR_MANA_CHARM, new Item.Properties()));
    public static final RegistryObject<Item> REFINED_MANA_CHARM = ITEMS.register("refined_mana_charm",
            () -> new com.hogwartsmagic.relic.RelicItem(com.hogwartsmagic.relic.RelicType.REFINED_MANA_CHARM, new Item.Properties()));
    public static final RegistryObject<Item> ARCANE_FOCUS = ITEMS.register("arcane_focus",
            () -> new com.hogwartsmagic.relic.RelicItem(com.hogwartsmagic.relic.RelicType.ARCANE_FOCUS, new Item.Properties()));
    public static final RegistryObject<Item> RAVENCLAW_DIADEM_FRAGMENT = ITEMS.register("ravenclaw_diadem_fragment",
            () -> new com.hogwartsmagic.relic.RelicItem(com.hogwartsmagic.relic.RelicType.RAVENCLAW_DIADEM_FRAGMENT, new Item.Properties()));
    public static final RegistryObject<Item> ENCHANTED_INKWELL = ITEMS.register("enchanted_inkwell",
            () -> new com.hogwartsmagic.relic.RelicItem(com.hogwartsmagic.relic.RelicType.ENCHANTED_INKWELL, new Item.Properties()));
    public static final RegistryObject<Item> MOONLIT_PHIAL = ITEMS.register("moonlit_phial",
            () -> new com.hogwartsmagic.relic.RelicItem(com.hogwartsmagic.relic.RelicType.MOONLIT_PHIAL, new Item.Properties()));
    public static final RegistryObject<Item> GOLDEN_SNITCH = ITEMS.register("golden_snitch",
            () -> new com.hogwartsmagic.item.GoldenSnitchItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));

    public static final RegistryObject<ArmorMaterial> SORTING_HAT_MATERIAL = ARMOR_MATERIALS.register("sorting_hat",
            () -> new ArmorMaterial(
                    Util.make(new java.util.EnumMap<>(ArmorItem.Type.class), map -> {
                        map.put(ArmorItem.Type.BOOTS, 0);
                        map.put(ArmorItem.Type.LEGGINGS, 0);
                        map.put(ArmorItem.Type.CHESTPLATE, 0);
                        map.put(ArmorItem.Type.HELMET, 2);
                        map.put(ArmorItem.Type.BODY, 0);
                    }),
                    22,
                    net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER,
                    () -> net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.LEATHER),
                    java.util.List.of(new ArmorMaterial.Layer(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "sorting_hat"))),
                    0.5f,
                    0.0f
            ));

    public static final RegistryObject<Item> SORTING_HAT = ITEMS.register("sorting_hat",
            () -> new SortingHatItem(SORTING_HAT_MATERIAL.getHolder().orElseThrow(), ArmorItem.Type.HELMET, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<ArmorMaterial> GRYFFINDOR_ROBES_MATERIAL = ARMOR_MATERIALS.register("gryffindor_robes",
            () -> createRobeMaterial("gryffindor_robes"));
    public static final RegistryObject<ArmorMaterial> RAVENCLAW_ROBES_MATERIAL = ARMOR_MATERIALS.register("ravenclaw_robes",
            () -> createRobeMaterial("ravenclaw_robes"));
    public static final RegistryObject<ArmorMaterial> HUFFLEPUFF_ROBES_MATERIAL = ARMOR_MATERIALS.register("hufflepuff_robes",
            () -> createRobeMaterial("hufflepuff_robes"));
    public static final RegistryObject<ArmorMaterial> SLYTHERIN_ROBES_MATERIAL = ARMOR_MATERIALS.register("slytherin_robes",
            () -> createRobeMaterial("slytherin_robes"));

    public static final RegistryObject<Item> GRYFFINDOR_ROBES = ITEMS.register("gryffindor_robes",
            () -> new ArmorItem(GRYFFINDOR_ROBES_MATERIAL.getHolder().orElseThrow(), ArmorItem.Type.CHESTPLATE, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> RAVENCLAW_ROBES = ITEMS.register("ravenclaw_robes",
            () -> new ArmorItem(RAVENCLAW_ROBES_MATERIAL.getHolder().orElseThrow(), ArmorItem.Type.CHESTPLATE, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> HUFFLEPUFF_ROBES = ITEMS.register("hufflepuff_robes",
            () -> new ArmorItem(HUFFLEPUFF_ROBES_MATERIAL.getHolder().orElseThrow(), ArmorItem.Type.CHESTPLATE, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SLYTHERIN_ROBES = ITEMS.register("slytherin_robes",
            () -> new ArmorItem(SLYTHERIN_ROBES_MATERIAL.getHolder().orElseThrow(), ArmorItem.Type.CHESTPLATE, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> GRYFFINDOR_HOUSE_REVEAL = ITEMS.register("gryffindor_house_reveal",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> RAVENCLAW_HOUSE_REVEAL = ITEMS.register("ravenclaw_house_reveal",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> HUFFLEPUFF_HOUSE_REVEAL = ITEMS.register("hufflepuff_house_reveal",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SLYTHERIN_HOUSE_REVEAL = ITEMS.register("slytherin_house_reveal",
            () -> new Item(new Item.Properties()));

    // Magic Book Item
    public static final RegistryObject<Item> MAGIC_BOOK = ITEMS.register("magic_book",
            () -> new com.hogwartsmagic.item.MagicBookItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> WIZARD_SPAWN_EGG = ITEMS.register("wizard_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.WIZARD, 0x341906, 0xFFDD44, new Item.Properties()));

    public static final RegistryObject<Item> STUDENT_SPAWN_EGG = ITEMS.register("student_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.STUDENT, 0x8B4513, 0xFFDD44, new Item.Properties()));

    public static final RegistryObject<Item> OWL_SPAWN_EGG = ITEMS.register("owl_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.OWL, 0xC19E67, 0xFFFFFF, new Item.Properties()));

    public static final RegistryObject<Item> HIPPOGRIFF_SPAWN_EGG = ITEMS.register("hippogriff_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.HIPPOGRIFF, 0x7A6A54, 0xD3C8B8, new Item.Properties()));

    public static final RegistryObject<Item> ACROMANTULA_SPAWN_EGG = ITEMS.register("acromantula_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.ACROMANTULA, 0x1A1A1A, 0x4D0000, new Item.Properties()));

    public static final RegistryObject<Item> DEMENTOR_SPAWN_EGG = ITEMS.register("dementor_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.DEMENTOR, 0x111111, 0x333333, new Item.Properties()));

    public static final RegistryObject<Item> MINATOR_SPAWN_EGG = ITEMS.register("minator_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.MINATOR, 0x8B8B83, 0x5C4033, new Item.Properties()));

    public static final RegistryObject<Item> BASILISK_SPAWN_EGG = ITEMS.register("basilisk_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.BASILISK, 0x23361B, 0xA5D05D, new Item.Properties()));

    public static final RegistryObject<Item> TROLL_SPAWN_EGG = ITEMS.register("troll_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.TROLL, 0x777365, 0x3F6A2E, new Item.Properties()));

    public static final RegistryObject<Item> GIANT_SPAWN_EGG = ITEMS.register("giant_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.GIANT, 0xC6A18E, 0x3A2B22, new Item.Properties()));

    public static final RegistryObject<Item> HUNGARIAN_HORNTAIL_SPAWN_EGG = ITEMS.register("hungarian_horntail_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.HUNGARIAN_HORNTAIL, 0x12100E, 0xC79A3A, new Item.Properties()));

    // --- Gringotts ---------------------------------------------------------

    /** Wizarding currency. Goblins exchange it against gold and mod goods. */
    public static final RegistryObject<Item> GALLEON = ITEMS.register("galleon",
            () -> new Item(new Item.Properties()));

    /**
     * Goblin-forged silver: a short blade that trades reach and burst damage for
     * speed. 4 damage at 2.4 swings/second beats an iron sword's DPS but punishes
     * a missed hit far less kindly.
     */
    public static final RegistryObject<Item> GOBLIN_SILVER_DAGGER = ITEMS.register("goblin_silver_dagger",
            () -> new net.minecraft.world.item.SwordItem(net.minecraft.world.item.Tiers.IRON,
                    new Item.Properties().attributes(
                            net.minecraft.world.item.SwordItem.createAttributes(
                                    net.minecraft.world.item.Tiers.IRON, 1, -1.6F))));

    public static final RegistryObject<Item> GOBLIN_SPAWN_EGG = ITEMS.register("goblin_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.GOBLIN, 0x1A1A1F, 0xC4907A, new Item.Properties()));

    public static final RegistryObject<Item> GOBLIN_GUARD_SPAWN_EGG = ITEMS.register("goblin_guard_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.GOBLIN_GUARD, 0x563A24, 0xC4907A, new Item.Properties()));

    public static final RegistryObject<Item> HOUSE_ELF_SPAWN_EGG = ITEMS.register("house_elf_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.HOUSE_ELF, 0x9E8E76, 0xCEC9BA, new Item.Properties()));

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register("main_tab", () -> CreativeModeTab.builder()
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> HARRY_POTTER_WAND.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(HARRY_POTTER_WAND.get());
        output.accept(PHOENIX_CORE_WAND.get());
        output.accept(UNICORN_CORE_WAND.get());
        output.accept(DRAGON_CORE_WAND.get());
        output.accept(ELDER_WAND.get());
                output.accept(BROOM.get()); // Basic Broom
                output.accept(NIMBUS_2000.get()); // Nimbus 2000
                output.accept(FIREBOLT.get()); // Firebolt
                output.accept(WAND_WORKBENCH_ITEM.get());
                output.accept(BROOM_WORKBENCH_ITEM.get());
                output.accept(FINE_WOOD_STICK.get());
                output.accept(FINE_WOOD_LOG_ITEM.get());
                output.accept(STRIPPED_FINE_WOOD_LOG_ITEM.get());
                output.accept(FINE_WOOD_LEAVES_ITEM.get());
                output.accept(FINE_WOOD_SAPLING_ITEM.get());
                output.accept(FINE_WOOD_PLANKS_ITEM.get());
                output.accept(ARCANE_CLUSTER_ITEM.get());
                output.accept(GILLYWEED_PLANT_ITEM.get());
                output.accept(MANDRAKE_PLANT_ITEM.get());
                output.accept(MOVING_PORTRAIT_ITEM.get());
                output.accept(CANDLE_CLUSTER_ITEM.get());
                output.accept(ENCHANTED_BOOK_PILE_ITEM.get());
                output.accept(WIZARD_CAULDRON_ITEM.get());
                output.accept(OWL_PERCH_ITEM.get());
                output.accept(FLOO_FIREPLACE_ITEM.get());
                output.accept(WARD_ITEM.get());
                output.accept(HORCRUX_ITEM.get());
                output.accept(GRYFFINDOR_BANNER_BLOCK_ITEM.get());
                output.accept(RAVENCLAW_BANNER_BLOCK_ITEM.get());
                output.accept(HUFFLEPUFF_BANNER_BLOCK_ITEM.get());
                output.accept(SLYTHERIN_BANNER_BLOCK_ITEM.get());
                output.accept(ARCANE_DUST.get());
                output.accept(ARCANE_CORE.get());
                output.accept(OWL_ROUTE_TAG.get());
                output.accept(HOGWARTS_INVITE.get());
                output.accept(HOGWARTS_GUIDE.get());
                output.accept(FLOO_BINDING_TOKEN.get());
                output.accept(CAVE_INIMICUM_TOTEM.get());
                output.accept(FLOO_POWDER.get());
                output.accept(MAGICAL_SILK.get());
                output.accept(INVISIBILITY_CLOAK.get());
                output.accept(GOLDEN_SNITCH.get());
                output.accept(MINOR_MANA_CHARM.get());
                output.accept(REFINED_MANA_CHARM.get());
                output.accept(ARCANE_FOCUS.get());
                output.accept(RAVENCLAW_DIADEM_FRAGMENT.get());
                output.accept(ENCHANTED_INKWELL.get());
                output.accept(MOONLIT_PHIAL.get());
                output.accept(SAMPLE_COLLECTOR.get());
                output.accept(POLYJUICE_SAMPLE.get());
                output.accept(WOLF_FANG.get());
                output.accept(CENTAUR_TALISMAN.get());
                output.accept(ACROMANTULA_VENOM.get());
                output.accept(MANDRAKE_ROOT.get());
                output.accept(GILLYWEED.get());
                output.accept(POLYJUICE_POTION.get());
                output.accept(PHOENIX_FEATHER_CORE.get());
                output.accept(UNICORN_HAIR_CORE.get());
                output.accept(DRAGON_HEARTSTRING_CORE.get());
                output.accept(SORTING_HAT.get()); // Sorting Hat
                output.accept(GRYFFINDOR_ROBES.get());
                output.accept(RAVENCLAW_ROBES.get());
                output.accept(HUFFLEPUFF_ROBES.get());
                output.accept(SLYTHERIN_ROBES.get());
                output.accept(WIZARD_SPAWN_EGG.get()); // Wizard Spawn Egg
                output.accept(STUDENT_SPAWN_EGG.get()); // Student Spawn Egg
                output.accept(OWL_SPAWN_EGG.get()); // Owl Spawn Egg
                output.accept(HIPPOGRIFF_SPAWN_EGG.get()); // Hippogriff Spawn Egg
                output.accept(ACROMANTULA_SPAWN_EGG.get()); // Acromantula Spawn Egg
                output.accept(DEMENTOR_SPAWN_EGG.get()); // Dementor Spawn Egg
                output.accept(MINATOR_SPAWN_EGG.get()); // Minator Spawn Egg
                output.accept(BASILISK_SPAWN_EGG.get()); // Basilisk Spawn Egg
                output.accept(TROLL_SPAWN_EGG.get()); // Troll Spawn Egg
                output.accept(GIANT_SPAWN_EGG.get()); // Giant Spawn Egg
                output.accept(HUNGARIAN_HORNTAIL_SPAWN_EGG.get()); // Hungarian Horntail Spawn Egg
                output.accept(GOBLIN_SPAWN_EGG.get()); // Gringotts Goblin Spawn Egg
                output.accept(GOBLIN_GUARD_SPAWN_EGG.get()); // Goblin Vault Guard Spawn Egg
                output.accept(HOUSE_ELF_SPAWN_EGG.get()); // House Elf Spawn Egg
                output.accept(GALLEON.get()); // Galleon
                output.accept(GOBLIN_SILVER_DAGGER.get()); // Goblin-Forged Silver Dagger
                // Add books for all spells
                for (com.hogwartsmagic.spell.SpellType spell : com.hogwartsmagic.spell.SpellType.values()) {
                    output.accept(com.hogwartsmagic.item.MagicBookItem.createBook(spell));
                }
            }).build());

    public ExampleMod()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);
        ARMOR_MATERIALS.register(modEventBus);
        MENUS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        // Register entity types
        ModEntities.ENTITY_TYPES.register(modEventBus);
        // Register mob effects
        MOB_EFFECTS.register(modEventBus);
        // Register potions (after mob effects — wizard potion references wizard_elixir)
        POTIONS.register(modEventBus);
        // Register worldgen features
        FEATURES.register(modEventBus);
        // Register Villager Professions and POIs
        com.hogwartsmagic.entity.ModVillagers.POI_TYPES.register(modEventBus);
        com.hogwartsmagic.entity.ModVillagers.VILLAGER_PROFESSIONS.register(modEventBus);
        STRUCTURE_TYPES.register(modEventBus);
        STRUCTURE_PIECE_TYPES.register(modEventBus);
        // Register loot modifiers
        LOOT_MODIFIER_SERIALIZERS.register(modEventBus);
        // Register sounds
        ModSounds.SOUND_EVENTS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        // BrewingRecipeRegisterEvent is NOT an IModBusEvent — must use the game event bus.
        MinecraftForge.EVENT_BUS.addListener(ExampleMod::onRegisterBrewingRecipes);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        event.enqueueWork(SpellPackets::register);
    }

    /**
     * Fired on {@link MinecraftForge#EVENT_BUS} while {@link net.minecraft.world.item.alchemy.PotionBrewing} is built.
     * Not a mod-bus event ({@code IModBusEvent}).
     */
    private static void onRegisterBrewingRecipes(BrewingRecipeRegisterEvent event) {
        event.addRecipe(new com.hogwartsmagic.polyjuice.WizardPotionBrewingRecipe());
        event.addRecipe(new com.hogwartsmagic.polyjuice.StabilizedPolyjuiceBaseBrewingRecipe());
        event.addRecipe(new com.hogwartsmagic.polyjuice.PolyjuiceBrewingRecipe());
        event.addRecipe(new com.hogwartsmagic.polyjuice.PolyjuiceRedstoneExtendBrewingRecipe());
        event.addRecipe(new com.hogwartsmagic.polyjuice.PolyjuiceSplashBrewingRecipe());
        LOGGER.info("Registered mana regeneration / Polyjuice brewing recipes (awkward+acromantula venom -> mana regeneration; mana regeneration+mandrake root -> stabilized base; stabilized base+sample -> polyjuice; polyjuice+redstone -> extended; polyjuice+gunpowder -> splash)");
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event)
    {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    @SubscribeEvent
    public void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
        com.hogwartsmagic.command.WizardRaidCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.FindDiagonAlleyCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.SpawnDiagonAlleyCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.FindMiniHogwartsCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.FindGringottsCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.BuildBankCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.SpawnMiniHogwartsCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.SummonHorntailCommand.register(event.getDispatcher());
    }

    private static ArmorMaterial createRobeMaterial(String textureName) {
        return new ArmorMaterial(
                Util.make(new java.util.EnumMap<>(ArmorItem.Type.class), map -> {
                    map.put(ArmorItem.Type.BOOTS, 0);
                    map.put(ArmorItem.Type.LEGGINGS, 0);
                    map.put(ArmorItem.Type.CHESTPLATE, 5);
                    map.put(ArmorItem.Type.HELMET, 0);
                    map.put(ArmorItem.Type.BODY, 5);
                }),
                18,
                net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER,
                () -> net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.LEATHER),
                java.util.List.of(new ArmorMaterial.Layer(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, textureName))),
                0.0f,
                0.0f
        );
    }
}
