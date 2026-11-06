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
import com.hogwartsmagic.item.CosmeticArmorItem;
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
    public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.minecraftforge.common.loot.IGlobalLootModifier>> LOOT_MODIFIER_SERIALIZERS = DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MODID);
    public static final DeferredRegister<net.minecraft.core.component.DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);
    /** Broom quality from an Alatus infusion: 0 normal, 1 Fine, 2 Masterwork (Spell Mastery Phase 7b). */
    public static final RegistryObject<net.minecraft.core.component.DataComponentType<Integer>> BROOM_QUALITY =
            DATA_COMPONENTS.register("broom_quality", () -> net.minecraft.core.component.DataComponentType.<Integer>builder()
                    .persistent(com.mojang.serialization.Codec.intRange(0, 2))
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT.map(
                            com.hogwartsmagic.spell.motion.AlatusRules::clampQuality, q -> q))
                    .build());
    /** Brew quality of a bottled potion: 0 standard, 1 fine, 2 exceptional. Absent reads as standard. */
    public static final RegistryObject<net.minecraft.core.component.DataComponentType<Integer>> BREW_QUALITY =
            DATA_COMPONENTS.register("brew_quality", () -> net.minecraft.core.component.DataComponentType.<Integer>builder()
                    .persistent(com.mojang.serialization.Codec.intRange(0, 2))
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT.map(
                            com.hogwartsmagic.brewing.BrewQuality::clampGrade, q -> q))
                    .build());
    /** True on a fine variant of a rare brewing ingredient; it lifts the brew it goes into. */
    public static final RegistryObject<net.minecraft.core.component.DataComponentType<Boolean>> FINE_INGREDIENT =
            DATA_COMPONENTS.register("fine_ingredient", () -> net.minecraft.core.component.DataComponentType.<Boolean>builder()
                    .persistent(com.mojang.serialization.Codec.BOOL)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL)
                    .build());
    /** Sneakoscope: true while a carried Sneakoscope detects something. Synced to clients, never saved (transient). */
    public static final RegistryObject<net.minecraft.core.component.DataComponentType<Boolean>> SNEAKOSCOPE_SPINNING =
            DATA_COMPONENTS.register("sneakoscope_spinning", () -> net.minecraft.core.component.DataComponentType.<Boolean>builder()
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL)
                    .build());

    public static final RegistryObject<com.mojang.serialization.MapCodec<com.hogwartsmagic.loot.MagicBookLootModifier>> MAGIC_BOOK_LOOT_MODIFIER =
            LOOT_MODIFIER_SERIALIZERS.register("magic_books", () -> com.hogwartsmagic.loot.MagicBookLootModifier.CODEC);
    public static final RegistryObject<com.mojang.serialization.MapCodec<com.hogwartsmagic.loot.SortingHatLootModifier>> SORTING_HAT_LOOT_MODIFIER =
            LOOT_MODIFIER_SERIALIZERS.register("sorting_hat_chest", () -> com.hogwartsmagic.loot.SortingHatLootModifier.CODEC);
    public static final RegistryObject<com.mojang.serialization.MapCodec<com.hogwartsmagic.loot.InvisibilityCloakLootModifier>> INVISIBILITY_CLOAK_LOOT_MODIFIER =
            LOOT_MODIFIER_SERIALIZERS.register("invisibility_cloak_chest", () -> com.hogwartsmagic.loot.InvisibilityCloakLootModifier.CODEC);
    public static final RegistryObject<com.mojang.serialization.MapCodec<com.hogwartsmagic.loot.MagicalSilkLootModifier>> MAGICAL_SILK_LOOT_MODIFIER =
            LOOT_MODIFIER_SERIALIZERS.register("magical_silk_chest", () -> com.hogwartsmagic.loot.MagicalSilkLootModifier.CODEC);
    public static final RegistryObject<com.mojang.serialization.MapCodec<com.hogwartsmagic.loot.CosmeticLootModifier>> COSMETIC_LOOT_MODIFIER =
            LOOT_MODIFIER_SERIALIZERS.register("cosmetic_chest", () -> com.hogwartsmagic.loot.CosmeticLootModifier.CODEC);
    public static final RegistryObject<com.mojang.serialization.MapCodec<com.hogwartsmagic.loot.TomeOfMasteryLootModifier>> TOME_OF_MASTERY_LOOT_MODIFIER =
            LOOT_MODIFIER_SERIALIZERS.register("tomes_of_mastery", () -> com.hogwartsmagic.loot.TomeOfMasteryLootModifier.CODEC);
    public static final RegistryObject<com.mojang.serialization.MapCodec<com.hogwartsmagic.loot.BrewingIngredientLootModifier>> BREWING_INGREDIENT_LOOT_MODIFIER =
            LOOT_MODIFIER_SERIALIZERS.register("brewing_ingredients_chest", () -> com.hogwartsmagic.loot.BrewingIngredientLootModifier.CODEC);
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
    public static final RegistryObject<net.minecraft.world.level.levelgen.feature.Feature<net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration>> DITTANY_PATCH_FEATURE =
            FEATURES.register("dittany_patch", () -> new com.hogwartsmagic.worldgen.DittanyPatchFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));
    // Diagon Alley is not a worldgen feature. It is eleven chunks long, and vanilla forbids a
    // feature from writing outside the 3x3 chunk window around the chunk being generated, so the
    // old one spammed "Detected setBlock in a far chunk" and could wedge chunk loading. It is now a
    // structure template placed across ticks by DiagonAlleyManager, via /spawnalley.
    // Lumos Effect
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> LUMOS_EFFECT = MOB_EFFECTS.register("lumos",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.BENEFICIAL, 0xFFFFAA) {
                @Override
                public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
                    return true;
                }
            });

    // Retired: Protego is ProtegoShield state now. Kept registered so old worlds load cleanly; do not apply it.
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

    public static final RegistryObject<net.minecraft.world.effect.MobEffect> BASILISK_GAZE = MOB_EFFECTS.register("basilisk_gaze",
            com.hogwartsmagic.spell.BasiliskGazeEffect::new);

    // Shared combat effects (Spell Mastery Phase 2). Later phases apply them; hard crowd control
    // goes through com.hogwartsmagic.spell.combat.CrowdControl so diminishing returns, the boss
    // rule and the PvP rule always run.
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> STUNNED = MOB_EFFECTS.register("stunned",
            com.hogwartsmagic.spell.StunnedEffect::new);
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> BLEEDING = MOB_EFFECTS.register("bleeding",
            com.hogwartsmagic.spell.BleedingEffect::new);
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> CURSED_WOUND = MOB_EFFECTS.register("cursed_wound",
            com.hogwartsmagic.spell.CursedWoundEffect::new);
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> UNQUENCHABLE_THIRST = MOB_EFFECTS.register("unquenchable_thirst",
            com.hogwartsmagic.spell.UnquenchableThirstEffect::new);
    /** Marker: CombatEffects consumes it on the next physical or explosion hit for bonus damage. */
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> BRITTLE = MOB_EFFECTS.register("brittle",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0x9FD8F0) {});
    /** Marker: the cast handlers refuse while it is active and the spell wheel greys out. */
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> SILENCED = MOB_EFFECTS.register("silenced",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0x6E6E8F) {});

    // Disabling-spell markers (Spell Mastery Phase 3). Always added hidden (no icon, no particles); the
    // amplifier stores the caster's mastery rank - 1, so the hooks know the rank without extra state.
    /** Petrificus Totalus: drives the topple render (synced by VisibleEffects) and the break-on-damage rule. */
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> PETRIFIED = MOB_EFFECTS.register("petrified",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0xAAAAAA) {});
    /** Glacius: the window in which a second hit freezes the target solid. */
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> GLACIUS_CHILL = MOB_EFFECTS.register("glacius_chill",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0xBFEFFF) {});
    /** Glacius: frozen solid (alongside stunned and brittle). Fire thaws it; at Master a death shatters it. */
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> GLACIUS_FROZEN = MOB_EFFECTS.register("glacius_frozen",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0x88FFFF) {});

    // Mind spells and Unforgivables (Spell Mastery Phase 6). All three are applied hidden.
    /** Imperio thrall marker. The thrall player's own client zeroes their input while it lasts; ImperioThralls owns the state. */
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> IMPERIUS = MOB_EFFECTS.register("imperius",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0x88FF88) {});
    /** Confundo marker: the mob attacks the nearest other creature. Amplifier 1 marks a contagious (Master) confusion. */
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> CONFUNDED = MOB_EFFECTS.register("confunded",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0xFFAAAA) {});
    /**
     * Rooted: movement speed and jump strength drop to zero. Attributes sync to the player's own client, so a
     * rooted player can't walk or jump there either. Worn by a channelling Crucio caster (and, in 6b, a
     * possessing caster's body).
     */
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> ROOTED = MOB_EFFECTS.register("rooted",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0x5A3D7A) {}
                    .addAttributeModifier(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "rooted_speed"), -1.0,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH,
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "rooted_jump"), -1.0,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    /** Imperio Master: the possessed body (hidden). A possessed mob's AI is paused; a possessed player's own keys do nothing. */
    /** The werewolf curse at its worst: the beast drives the player's body (Lycanthropy). Counts as mind control. */
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> FERAL = MOB_EFFECTS.register("feral",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0x8B1A1A) {});
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> POSSESSED = MOB_EFFECTS.register("possessed",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0x55CC66) {});
    /** Imperio Master: the caster's frozen body (hidden). It makes the body immobile on both sides through LivingEntityStunMixin. */
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> POSSESSING = MOB_EFFECTS.register("possessing",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0x55CC66) {});

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
    public static final RegistryObject<BlockEntityType<com.hogwartsmagic.block.entity.WizardCauldronBlockEntity>> WIZARD_CAULDRON_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("wizard_cauldron", () -> BlockEntityType.Builder.of(
                    com.hogwartsmagic.block.entity.WizardCauldronBlockEntity::new,
                    WIZARD_CAULDRON.get()
            ).build(null));
    public static final RegistryObject<Block> SNEAKOSCOPE = BLOCKS.register("sneakoscope",
            () -> new com.hogwartsmagic.block.SneakoscopeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .strength(0.5f)
                    .sound(net.minecraft.world.level.block.SoundType.GLASS)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    public static final RegistryObject<BlockEntityType<com.hogwartsmagic.block.entity.SneakoscopeBlockEntity>> SNEAKOSCOPE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("sneakoscope", () -> BlockEntityType.Builder.of(
                    com.hogwartsmagic.block.entity.SneakoscopeBlockEntity::new,
                    SNEAKOSCOPE.get()
            ).build(null));
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
    // Deepslate bricks, not obsidian: this is quarried out of a ruin with an ordinary pickaxe, and
    // an unbreakable-feeling core would make the ruin guardians' drop feel like a trophy you are not
    // allowed to use. Light 7 matches ARCANE_CLUSTER -- enough to find one across a dark hall,
    // not enough to light the hall for you.
    public static final RegistryObject<Block> STATUE_HEART = BLOCKS.register("statue_heart",
            () -> new com.hogwartsmagic.block.StatueHeartBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_BRICKS)
                    // Both numbers, not the one-argument form: that sets blast resistance to the
                    // hardness as well, and softening the dig would have quietly made the core
                    // easier to blow up than the deepslate it is copied from.
                    .strength(3.0f, 6.0f)
                    .lightLevel(state -> 7)));
    // The Chamber of Secrets is meant to have exactly one way in, and a room you can tunnel into
    // through its own wall has none. Hardness -1 with bedrock's blast resistance makes the shell and
    // the door leaf genuinely unminable, so the Parseltongue lock is the only route past them. Both
    // are plain blocks with no block entity: they only ever need to sit there and refuse.
    /**
     * The wall behind the Leaky Cauldron. Ordinary brick strength on purpose -- a determined
     * player with a pickaxe can always get through, and should be able to; the tap is the way in,
     * not the only way in.
     */
    public static final RegistryObject<Block> DIAGON_ARCHWAY_BRICK = BLOCKS.register("diagon_archway_brick",
            () -> new com.hogwartsmagic.block.DiagonArchwayBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.BRICKS)
                            .strength(2.0f, 6.0f)
                            .lightLevel(state -> state.getValue(
                                    com.hogwartsmagic.block.DiagonArchwayBlock.LIT) ? 10 : 0)));
    public static final RegistryObject<Block> CHAMBER_STONE = BLOCKS.register("chamber_stone",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_DEEPSLATE)
                    .strength(-1.0f, 3600000.0f)
                    .noLootTable()));
    public static final RegistryObject<Block> CHAMBER_DOOR_LEAF = BLOCKS.register("chamber_door_leaf",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.CHISELED_TUFF)
                    .strength(-1.0f, 3600000.0f)
                    .noLootTable()));

    // The lock at the centre of the Chamber of Secrets' round door. Strength is deliberately absurd:
    // the whole point is that it opens to Parseltongue and to nothing else, and a door you can take
    // out with a diamond pickaxe in four seconds is not a lock, it is a decoration.
    public static final RegistryObject<Block> CHAMBER_DOOR = BLOCKS.register("chamber_door",
            () -> new com.hogwartsmagic.block.ChamberDoorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CHISELED_DEEPSLATE)
                    .strength(50.0f, 1200.0f)
                    .lightLevel(state -> state.hasProperty(com.hogwartsmagic.block.ChamberDoorBlock.OPEN)
                            && state.getValue(com.hogwartsmagic.block.ChamberDoorBlock.OPEN) ? 6 : 0)));
    /**
     * The plinth in the bank's rear rotunda, and its opposite number on the platform below it.
     * Strong but not unbreakable: it is somewhere you stand, not part of the shell.
     */
    /**
     * The leaf of a vault door. Unminable on purpose: a vault you can take out with a pickaxe is
     * not a vault, and the rest of the chamber is sealed rock either way.
     */
    public static final RegistryObject<Block> GRINGOTTS_VAULT_DOOR = BLOCKS.register("gringotts_vault_door",
            () -> new com.hogwartsmagic.block.GringottsVaultDoorBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                            .strength(-1.0f, 3600000.0f)
                            .noLootTable()
                            // The parted state is invisible and passable, so occlusion has to be
                            // decided per state rather than baked in as "iron block, always solid".
                            .noOcclusion()
                            .lightLevel(state -> state.hasProperty(
                                    com.hogwartsmagic.block.GringottsVaultDoorBlock.PHASE)
                                    && state.getValue(com.hogwartsmagic.block.GringottsVaultDoorBlock.PHASE)
                                            == com.hogwartsmagic.block.GringottsVaultDoorBlock.PARTED ? 7 : 0)));

    /**
     * The screen across the back of the banking hall. Goblin-forged, so about twelve seconds a
     * block with a pickaxe - long enough that breaking in is a decision rather than a reflex - and
     * it drops nothing, so nobody carries a bank away one bar at a time.
     */
    public static final RegistryObject<Block> GRINGOTTS_GATE = BLOCKS.register("gringotts_gate",
            () -> new com.hogwartsmagic.block.GringottsGateBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BARS)
                            .strength(50.0f, 1200.0f)
                            .noLootTable()
                            .noOcclusion()));

    /**
     * The Thief's Downfall. No collision - you ride and walk straight through it, and it takes
     * what you were hiding behind on the way past.
     */
    /** A heap of cursed coin. Touch it and there is more of it. */
    public static final RegistryObject<Block> GEMINO_PILE = BLOCKS.register("gemino_pile",
            () -> new com.hogwartsmagic.block.GeminoPileBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK)
                            .strength(0.6f)
                            .noOcclusion()
                            .lightLevel(state -> 3)));

    public static final RegistryObject<Block> THIEFS_DOWNFALL = BLOCKS.register("thiefs_downfall",
            () -> new com.hogwartsmagic.block.ThiefsDownfallBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(net.minecraft.world.level.material.MapColor.WATER)
                            .noCollission()
                            .noOcclusion()
                            .strength(-1.0f, 3600000.0f)
                            .noLootTable()
                            .lightLevel(state -> 7)));

    public static final RegistryObject<Block> GRINGOTTS_LIFT = BLOCKS.register("gringotts_lift",
            () -> new com.hogwartsmagic.block.GringottsLiftBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_DEEPSLATE)
                            .strength(25.0f, 1200.0f)
                            .lightLevel(state -> 4)
                            .noLootTable()));

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
    public static final RegistryObject<BlockEntityType<com.hogwartsmagic.block.entity.ChamberDoorBlockEntity>> CHAMBER_DOOR_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("chamber_door", () -> BlockEntityType.Builder.of(
                    com.hogwartsmagic.block.entity.ChamberDoorBlockEntity::new,
                    CHAMBER_DOOR.get()
            ).build(null));
    public static final RegistryObject<BlockEntityType<com.hogwartsmagic.block.entity.DiagonArchwayBlockEntity>> DIAGON_ARCHWAY_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("diagon_archway_brick", () -> BlockEntityType.Builder.of(
                    com.hogwartsmagic.block.entity.DiagonArchwayBlockEntity::new,
                    DIAGON_ARCHWAY_BRICK.get()
            ).build(null));
    public static final RegistryObject<BlockEntityType<com.hogwartsmagic.block.entity.GringottsVaultDoorBlockEntity>> GRINGOTTS_VAULT_DOOR_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("gringotts_vault_door", () -> BlockEntityType.Builder.of(
                    com.hogwartsmagic.block.entity.GringottsVaultDoorBlockEntity::new,
                    GRINGOTTS_VAULT_DOOR.get()
            ).build(null));
    public static final RegistryObject<BlockEntityType<com.hogwartsmagic.block.entity.GringottsLiftBlockEntity>> GRINGOTTS_LIFT_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("gringotts_lift", () -> BlockEntityType.Builder.of(
                    com.hogwartsmagic.block.entity.GringottsLiftBlockEntity::new,
                    GRINGOTTS_LIFT.get()
            ).build(null));
    public static final RegistryObject<BlockEntityType<com.hogwartsmagic.block.entity.HorcruxBlockEntity>> HORCRUX_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("horcrux", () -> BlockEntityType.Builder.of(
                    com.hogwartsmagic.block.entity.HorcruxBlockEntity::new,
                    HORCRUX.get()
            ).build(null));

    // Locket cave dressing (hidden until Revelio, plus the blood-sealed entrance).
    public static final RegistryObject<Block> CONCEALED_ROCK = BLOCKS.register("concealed_rock",
            () -> new com.hogwartsmagic.block.ConcealedRockBlock(BlockBehaviour.Properties.of()
                    .mapColor(net.minecraft.world.level.material.MapColor.DEEPSLATE)
                    .sound(net.minecraft.world.level.block.SoundType.DEEPSLATE_TILES)
                    .strength(3.0f, 6.0f).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> HIDDEN_CHAIN = BLOCKS.register("hidden_chain",
            () -> new com.hogwartsmagic.block.HiddenChainBlock(BlockBehaviour.Properties.of()
                    .sound(net.minecraft.world.level.block.SoundType.CHAIN)
                    .strength(-1.0f, 3600000.0f).noLootTable().noOcclusion()));
    public static final RegistryObject<Block> BLOOD_SEAL = BLOCKS.register("blood_seal",
            () -> new com.hogwartsmagic.block.BloodSealBlock(BlockBehaviour.Properties.of()
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK)
                    .sound(net.minecraft.world.level.block.SoundType.DEEPSLATE)
                    .strength(-1.0f, 3600000.0f).noLootTable()));
    public static final RegistryObject<BlockEntityType<com.hogwartsmagic.block.entity.CaveMarkerBlockEntity>> CAVE_MARKER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("cave_marker", () -> BlockEntityType.Builder.of(
                    com.hogwartsmagic.block.entity.CaveMarkerBlockEntity::new,
                    CONCEALED_ROCK.get(), HIDDEN_CHAIN.get()
            ).build(null));

    public static final RegistryObject<Block> DESPAIR_BASIN = BLOCKS.register("despair_basin",
            () -> new com.hogwartsmagic.block.DespairBasinBlock(BlockBehaviour.Properties.of()
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK)
                    .sound(net.minecraft.world.level.block.SoundType.DEEPSLATE)
                    .strength(-1.0f, 3600000.0f).noLootTable().noOcclusion()
                    .lightLevel(state -> state.getValue(com.hogwartsmagic.block.DespairBasinBlock.STAGE)
                            != com.hogwartsmagic.locket.BasinStage.LOOTED ? 9 : 0)));
    public static final RegistryObject<Block> CRYSTAL_GOBLET = BLOCKS.register("crystal_goblet",
            () -> new com.hogwartsmagic.block.CrystalGobletBlock(BlockBehaviour.Properties.of()
                    .mapColor(net.minecraft.world.level.material.MapColor.ICE)
                    .sound(net.minecraft.world.level.block.SoundType.GLASS)
                    .strength(0.3f).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    public static final RegistryObject<BlockEntityType<com.hogwartsmagic.block.entity.DespairBasinBlockEntity>> DESPAIR_BASIN_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("despair_basin", () -> BlockEntityType.Builder.of(
                    com.hogwartsmagic.block.entity.DespairBasinBlockEntity::new,
                    DESPAIR_BASIN.get()
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
    public static final RegistryObject<Block> DITTANY_PLANT = BLOCKS.register("dittany_plant",
            () -> new com.hogwartsmagic.block.DittanyPlantBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.POPPY).noCollission().instabreak()));
    public static final RegistryObject<Block> WILLOW_SAPLING = BLOCKS.register("willow_sapling",
            () -> new com.hogwartsmagic.block.WillowSaplingBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SAPLING)));

    // Lumos light (Spell Mastery Phase 5): removes itself, so it can never be left behind.
    public static final RegistryObject<Block> LUMOS_LIGHT = BLOCKS.register("lumos_light",
            () -> new com.hogwartsmagic.block.LumosLightBlock(
                    BlockBehaviour.Properties.of()
                            .replaceable()
                            .noCollission()
                            .noOcclusion()
                            .noLootTable()
                            .instabreak()
                            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                            .lightLevel(state -> state.getValue(com.hogwartsmagic.block.LumosLightBlock.LIGHT))));

    // Custom Invisible Light Block for Patronus
    public static final RegistryObject<Block> PATRONUS_LIGHT_BLOCK = BLOCKS.register("patronus_light_block", 
            () -> new com.hogwartsmagic.block.PatronusLightBlock(
                    BlockBehaviour.Properties.of()
                            .replaceable() // a stray light must never stop a player building there
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
    public static final RegistryObject<Item> WILLOW_SAPLING_ITEM = ITEMS.register("willow_sapling", () -> new BlockItem(WILLOW_SAPLING.get(), new Item.Properties()));
    public static final RegistryObject<Item> MOVING_PORTRAIT_ITEM = ITEMS.register("moving_portrait", () -> new BlockItem(MOVING_PORTRAIT.get(), new Item.Properties()));
    public static final RegistryObject<Item> CANDLE_CLUSTER_ITEM = ITEMS.register("candle_cluster", () -> new BlockItem(CANDLE_CLUSTER.get(), new Item.Properties()));
    public static final RegistryObject<Item> ENCHANTED_BOOK_PILE_ITEM = ITEMS.register("enchanted_book_pile", () -> new BlockItem(ENCHANTED_BOOK_PILE.get(), new Item.Properties()));
    public static final RegistryObject<Item> DIAGON_ARCHWAY_BRICK_ITEM = ITEMS.register("diagon_archway_brick", () -> new BlockItem(DIAGON_ARCHWAY_BRICK.get(), new Item.Properties()));
    public static final RegistryObject<Item> WIZARD_CAULDRON_ITEM = ITEMS.register("wizard_cauldron", () -> new BlockItem(WIZARD_CAULDRON.get(), new Item.Properties()));
    public static final RegistryObject<Item> SNEAKOSCOPE_ITEM = ITEMS.register("sneakoscope",
            () -> new com.hogwartsmagic.item.SneakoscopeItem(SNEAKOSCOPE.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> OWL_PERCH_ITEM = ITEMS.register("owl_perch", () -> new BlockItem(OWL_PERCH.get(), new Item.Properties()));
    public static final RegistryObject<Item> FLOO_FIREPLACE_ITEM = ITEMS.register("floo_fireplace", () -> new BlockItem(FLOO_FIREPLACE.get(), new Item.Properties()));
    public static final RegistryObject<Item> WARD_ITEM = ITEMS.register("ward", () -> new BlockItem(WARD.get(), new Item.Properties()));
    public static final RegistryObject<Item> HORCRUX_ITEM = ITEMS.register("horcrux", () -> new BlockItem(HORCRUX.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> CONCEALED_ROCK_ITEM = ITEMS.register("concealed_rock", () -> new BlockItem(CONCEALED_ROCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> HIDDEN_CHAIN_ITEM = ITEMS.register("hidden_chain", () -> new BlockItem(HIDDEN_CHAIN.get(), new Item.Properties()));
    public static final RegistryObject<Item> BLOOD_SEAL_ITEM = ITEMS.register("blood_seal", () -> new BlockItem(BLOOD_SEAL.get(), new Item.Properties()));
    public static final RegistryObject<Item> DESPAIR_BASIN_ITEM = ITEMS.register("despair_basin", () -> new BlockItem(DESPAIR_BASIN.get(), new Item.Properties()));
    public static final RegistryObject<Item> CRYSTAL_GOBLET_ITEM = ITEMS.register("crystal_goblet", () -> new BlockItem(CRYSTAL_GOBLET.get(), new Item.Properties()));
    public static final RegistryObject<Item> STATUE_HEART_ITEM = ITEMS.register("statue_heart", () -> new BlockItem(STATUE_HEART.get(), new Item.Properties()));
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
            () -> new com.hogwartsmagic.item.StirringRodItem(new Item.Properties()));
    public static final RegistryObject<Item> ARCANE_DUST = ITEMS.register("arcane_dust",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> WIZARD_HAT_PURPLE = ITEMS.register("wizard_hat_purple",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WIZARD_HAT_GREEN = ITEMS.register("wizard_hat_green",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WIZARD_HAT_NAVY = ITEMS.register("wizard_hat_navy",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WIZARD_HAT_BLACK = ITEMS.register("wizard_hat_black",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WIZARD_HAT_BROWN = ITEMS.register("wizard_hat_brown",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WIZARD_HAT_BURGUNDY = ITEMS.register("wizard_hat_burgundy",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WITCH_HAT_PURPLE = ITEMS.register("witch_hat_purple",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WITCH_HAT_GREEN = ITEMS.register("witch_hat_green",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WITCH_HAT_NAVY = ITEMS.register("witch_hat_navy",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WITCH_HAT_BLACK = ITEMS.register("witch_hat_black",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WITCH_HAT_BROWN = ITEMS.register("witch_hat_brown",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WITCH_HAT_BURGUNDY = ITEMS.register("witch_hat_burgundy",
            () -> new Item(new Item.Properties().stacksTo(1)));
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
    // Cauldron Brewing Phase 2 ingredients. The dittany item also plants dittany.
    public static final RegistryObject<Item> DITTANY = ITEMS.register("dittany",
            () -> new net.minecraft.world.item.ItemNameBlockItem(DITTANY_PLANT.get(), new Item.Properties()));
    public static final RegistryObject<Item> ASPHODEL = ITEMS.register("asphodel",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> WORMWOOD = ITEMS.register("wormwood",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> VALERIAN_SPRIGS = ITEMS.register("valerian_sprigs",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ASHWINDER_EGG = ITEMS.register("ashwinder_egg",
            () -> new Item(new Item.Properties().fireResistant()));
    public static final RegistryObject<Item> POLYJUICE_POTION = ITEMS.register("polyjuice_potion",
            () -> new PolyjuicePotionItem(new Item.Properties()
                    .craftRemainder(net.minecraft.world.item.Items.GLASS_BOTTLE)
                    .component(DataComponents.POTION_CONTENTS, PolyjuicePotionItem.defaultPotionContents())));
    // Wizard Cauldron potions (brewing/BrewRules).
    public static final RegistryObject<Item> WIGGENWELD_POTION = ITEMS.register("wiggenweld_potion",
            () -> new com.hogwartsmagic.item.BrewedPotionItem(new Item.Properties(),
                    com.hogwartsmagic.item.BrewedPotionItem.Kind.WIGGENWELD));
    public static final RegistryObject<Item> PEPPERUP_POTION = ITEMS.register("pepperup_potion",
            () -> new com.hogwartsmagic.item.BrewedPotionItem(new Item.Properties(),
                    com.hogwartsmagic.item.BrewedPotionItem.Kind.PEPPERUP));
    public static final RegistryObject<Item> FAILED_POTION = ITEMS.register("failed_potion",
            () -> new com.hogwartsmagic.item.BrewedPotionItem(new Item.Properties(),
                    com.hogwartsmagic.item.BrewedPotionItem.Kind.FAILED));
    public static final RegistryObject<Item> ESSENCE_OF_DITTANY = ITEMS.register("essence_of_dittany",
            () -> new com.hogwartsmagic.item.BrewedPotionItem(new Item.Properties(),
                    com.hogwartsmagic.item.BrewedPotionItem.Kind.DITTANY));
    public static final RegistryObject<Item> FELIX_FELICIS = ITEMS.register("felix_felicis",
            () -> new com.hogwartsmagic.item.BrewedPotionItem(new Item.Properties()
                    .rarity(net.minecraft.world.item.Rarity.EPIC)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true),
                    com.hogwartsmagic.item.BrewedPotionItem.Kind.FELIX));
    public static final RegistryObject<Item> POTION_OF_REMORSE = ITEMS.register("potion_of_remorse",
            () -> new com.hogwartsmagic.item.BrewedPotionItem(new Item.Properties()
                    .rarity(net.minecraft.world.item.Rarity.UNCOMMON),
                    com.hogwartsmagic.item.BrewedPotionItem.Kind.REMORSE));
    public static final RegistryObject<Item> VERITASERUM = ITEMS.register("veritaserum",
            () -> new com.hogwartsmagic.item.BrewedPotionItem(new Item.Properties()
                    .rarity(net.minecraft.world.item.Rarity.RARE),
                    com.hogwartsmagic.item.BrewedPotionItem.Kind.VERITASERUM));
    /** Lifts the werewolf's curse (crafted from a wolf fang and moon-touched herbs). */
    public static final RegistryObject<Item> WOLFSBANE_POTION = ITEMS.register("wolfsbane_potion",
            () -> new com.hogwartsmagic.item.BrewedPotionItem(new Item.Properties()
                    .rarity(net.minecraft.world.item.Rarity.UNCOMMON),
                    com.hogwartsmagic.item.BrewedPotionItem.Kind.WOLFSBANE));
    public static final RegistryObject<Item> MANDRAKE_DRAUGHT = ITEMS.register("mandrake_draught",
            () -> new com.hogwartsmagic.item.BrewedPotionItem(new Item.Properties()
                    .rarity(net.minecraft.world.item.Rarity.UNCOMMON),
                    com.hogwartsmagic.item.BrewedPotionItem.Kind.MANDRAKE));
    public static final RegistryObject<Item> SPLASH_MANDRAKE_DRAUGHT = ITEMS.register("splash_mandrake_draught",
            () -> new com.hogwartsmagic.item.SplashBrewItem(new Item.Properties()
                    .rarity(net.minecraft.world.item.Rarity.UNCOMMON),
                    com.hogwartsmagic.item.SplashBrewItem.Kind.MANDRAKE));
    public static final RegistryObject<Item> DRAUGHT_OF_LIVING_DEATH = ITEMS.register("draught_of_living_death",
            () -> new com.hogwartsmagic.item.SplashBrewItem(new Item.Properties(),
                    com.hogwartsmagic.item.SplashBrewItem.Kind.LIVING_DEATH));
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
    public static final RegistryObject<Item> ADVANCED_POTION_MAKING = ITEMS.register("advanced_potion_making",
            () -> new com.hogwartsmagic.item.AdvancedPotionMakingItem(new Item.Properties()));
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

    /** The film school robes: black, open over the uniform, hood lined in the house colour (see HouseRobeModel). */
    public static final RegistryObject<Item> GRYFFINDOR_ROBES = ITEMS.register("gryffindor_robes",
            () -> schoolRobe(GRYFFINDOR_ROBES_MATERIAL, "gryffindor_robes"));
    public static final RegistryObject<Item> RAVENCLAW_ROBES = ITEMS.register("ravenclaw_robes",
            () -> schoolRobe(RAVENCLAW_ROBES_MATERIAL, "ravenclaw_robes"));
    public static final RegistryObject<Item> HUFFLEPUFF_ROBES = ITEMS.register("hufflepuff_robes",
            () -> schoolRobe(HUFFLEPUFF_ROBES_MATERIAL, "hufflepuff_robes"));
    public static final RegistryObject<Item> SLYTHERIN_ROBES = ITEMS.register("slytherin_robes",
            () -> schoolRobe(SLYTHERIN_ROBES_MATERIAL, "slytherin_robes"));

    /** Striped house scarves and the first-year pointed hat: head-slot cosmetics on their own models. */
    public static final RegistryObject<ArmorMaterial> WOOL_COSMETIC_MATERIAL = ARMOR_MATERIALS.register("wool_cosmetic",
            ExampleMod::createWoolCosmeticMaterial);
    public static final RegistryObject<Item> GRYFFINDOR_SCARF = ITEMS.register("gryffindor_scarf",
            () -> woolCosmetic(CosmeticArmorItem.SCARF, "gryffindor_scarf"));
    public static final RegistryObject<Item> RAVENCLAW_SCARF = ITEMS.register("ravenclaw_scarf",
            () -> woolCosmetic(CosmeticArmorItem.SCARF, "ravenclaw_scarf"));
    public static final RegistryObject<Item> HUFFLEPUFF_SCARF = ITEMS.register("hufflepuff_scarf",
            () -> woolCosmetic(CosmeticArmorItem.SCARF, "hufflepuff_scarf"));
    public static final RegistryObject<Item> SLYTHERIN_SCARF = ITEMS.register("slytherin_scarf",
            () -> woolCosmetic(CosmeticArmorItem.SCARF, "slytherin_scarf"));
    public static final RegistryObject<Item> POINTED_HAT = ITEMS.register("pointed_hat",
            () -> woolCosmetic(CosmeticArmorItem.POINTED_HAT, "pointed_hat"));
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
    // Hogwarts Assignments: a task that turns into its spell's book (see AssignmentItem)
    public static final RegistryObject<Item> ASSIGNMENT = ITEMS.register("assignment",
            () -> new com.hogwartsmagic.item.AssignmentItem(new Item.Properties().stacksTo(1)));

    // Tomes of Mastery: loot-only, one rank per tome, tier-gated (see TomeOfMasteryItem)
    public static final RegistryObject<Item> COMMON_TOME_OF_MASTERY = ITEMS.register("common_tome_of_mastery",
            () -> new com.hogwartsmagic.item.TomeOfMasteryItem(com.hogwartsmagic.spell.SpellLootRarity.COMMON, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> UNCOMMON_TOME_OF_MASTERY = ITEMS.register("uncommon_tome_of_mastery",
            () -> new com.hogwartsmagic.item.TomeOfMasteryItem(com.hogwartsmagic.spell.SpellLootRarity.UNCOMMON, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> RARE_TOME_OF_MASTERY = ITEMS.register("rare_tome_of_mastery",
            () -> new com.hogwartsmagic.item.TomeOfMasteryItem(com.hogwartsmagic.spell.SpellLootRarity.RARE, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> EPIC_TOME_OF_MASTERY = ITEMS.register("epic_tome_of_mastery",
            () -> new com.hogwartsmagic.item.TomeOfMasteryItem(com.hogwartsmagic.spell.SpellLootRarity.EPIC, new Item.Properties().stacksTo(16)));

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

    public static final RegistryObject<Item> SLYTHERINS_LOCKET = ITEMS.register("slytherins_locket",
            () -> new com.hogwartsmagic.item.SlytherinsLocketItem(new Item.Properties()));

    public static final RegistryObject<Item> CAVE_MEMORY = ITEMS.register("cave_memory",
            () -> new com.hogwartsmagic.item.CaveMemoryItem(new Item.Properties()));

    public static final RegistryObject<Item> EMPTY_MEMORY_PHIAL = ITEMS.register("empty_memory_phial",
            () -> new com.hogwartsmagic.pensieve.EmptyMemoryPhialItem(new Item.Properties()));

    public static final RegistryObject<Item> MEMORY = ITEMS.register("memory",
            () -> new com.hogwartsmagic.pensieve.RecordedMemoryItem(new Item.Properties()));

    public static final RegistryObject<Item> CHAMBER_MEMORY = ITEMS.register("chamber_memory",
            () -> new com.hogwartsmagic.pensieve.StoryMemoryItem(new Item.Properties(), "chamber"));

    public static final RegistryObject<Item> SUMMONING_MEMORY = ITEMS.register("summoning_memory",
            () -> new com.hogwartsmagic.pensieve.StoryMemoryItem(new Item.Properties(), "summoning"));

    public static final RegistryObject<Item> PATRONUS_MEMORY = ITEMS.register("patronus_memory",
            () -> new com.hogwartsmagic.pensieve.StoryMemoryItem(new Item.Properties(), "lake_patronus"));

    /** "The Dark Lord" (dark_lord.json), the cinematic of Voldemort; he drops it when he falls. */
    public static final RegistryObject<Item> DARK_LORD_MEMORY = ITEMS.register("dark_lord_memory",
            () -> new com.hogwartsmagic.pensieve.StoryMemoryItem(new Item.Properties(), "dark_lord"));

    public static final RegistryObject<Block> PENSIEVE = BLOCKS.register("pensieve",
            () -> new com.hogwartsmagic.pensieve.PensieveBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .sound(net.minecraft.world.level.block.SoundType.STONE)
                    .strength(3.0f).lightLevel(state -> 6).noOcclusion()));
    public static final RegistryObject<Item> PENSIEVE_ITEM = ITEMS.register("pensieve",
            () -> new BlockItem(PENSIEVE.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<com.hogwartsmagic.pensieve.PensieveBlockEntity>> PENSIEVE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("pensieve", () -> BlockEntityType.Builder.of(
                    com.hogwartsmagic.pensieve.PensieveBlockEntity::new,
                    PENSIEVE.get()
            ).build(null));

    public static final RegistryObject<Item> TROLL_SPAWN_EGG = ITEMS.register("troll_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.TROLL, 0x777365, 0x3F6A2E, new Item.Properties()));

    public static final RegistryObject<Item> GIANT_SPAWN_EGG = ITEMS.register("giant_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.GIANT, 0xC6A18E, 0x3A2B22, new Item.Properties()));

    public static final RegistryObject<Item> HUNGARIAN_HORNTAIL_SPAWN_EGG = ITEMS.register("hungarian_horntail_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.HUNGARIAN_HORNTAIL, 0x12100E, 0xC79A3A, new Item.Properties()));

    // --- Gringotts ---------------------------------------------------------

    /** The goblins' noisemakers. Durability, not stack size: enough for a careful heist. */
    public static final RegistryObject<Item> CLANKERS = ITEMS.register("clankers",
            () -> new com.hogwartsmagic.item.ClankersItem(
                    new Item.Properties().stacksTo(1).durability(24)));

    /**
     * Display-only props for Pensieve memories (the Flying memory): the Remembrall a puppet or the viewer holds, and
     * Madam Hooch's whistle. They do nothing, are in no creative tab and have no recipe: a scene puts them in a hand
     * with an {@code items} / {@code equip} entry.
     */
    public static final RegistryObject<Item> MEMORY_REMEMBRALL = ITEMS.register("memory_remembrall",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WHISTLE = ITEMS.register("whistle",
            () -> new Item(new Item.Properties().stacksTo(1)));

    /**
     * Display-only props for the First Task memory: the golden egg the champion holds at the grab (its in-hand size is
     * the memory_golden_egg actor's), and the champion's robes, charcoal with bronze trim and house-neutral. The egg
     * has no recipe and is in no creative tab: a scene puts it in a hand with an {@code items} / {@code equip} entry.
     * The robes are also a rare find in chests (CosmeticLootModifier).
     */
    public static final RegistryObject<Item> GOLDEN_EGG_PROP = ITEMS.register("golden_egg_prop",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<ArmorMaterial> TRIWIZARD_ROBES_MATERIAL = ARMOR_MATERIALS.register("triwizard_robes",
            () -> createRobeMaterial("triwizard_robes"));
    public static final RegistryObject<Item> TRIWIZARD_ROBES = ITEMS.register("triwizard_robes",
            () -> new ArmorItem(TRIWIZARD_ROBES_MATERIAL.getHolder().orElseThrow(), ArmorItem.Type.CHESTPLATE, new Item.Properties().stacksTo(1)));
    /**
     * Quidditch kits for the Flying memory: a house-coloured tunic (chest) with leather arm guards and gloves per
     * house, and one pair of leather shin guards (legs) for all. Worn by the Flying memory's puppets, and craftable:
     * house wool round a leather tunic, as the school robes once were.
     */
    public static final RegistryObject<ArmorMaterial> GRYFFINDOR_QUIDDITCH_ROBES_MATERIAL = ARMOR_MATERIALS.register("gryffindor_quidditch_robes",
            () -> createRobeMaterial("gryffindor_quidditch_robes"));
    public static final RegistryObject<Item> GRYFFINDOR_QUIDDITCH_ROBES = ITEMS.register("gryffindor_quidditch_robes",
            () -> new ArmorItem(GRYFFINDOR_QUIDDITCH_ROBES_MATERIAL.getHolder().orElseThrow(), ArmorItem.Type.CHESTPLATE, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<ArmorMaterial> HUFFLEPUFF_QUIDDITCH_ROBES_MATERIAL = ARMOR_MATERIALS.register("hufflepuff_quidditch_robes",
            () -> createRobeMaterial("hufflepuff_quidditch_robes"));
    public static final RegistryObject<Item> HUFFLEPUFF_QUIDDITCH_ROBES = ITEMS.register("hufflepuff_quidditch_robes",
            () -> new ArmorItem(HUFFLEPUFF_QUIDDITCH_ROBES_MATERIAL.getHolder().orElseThrow(), ArmorItem.Type.CHESTPLATE, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<ArmorMaterial> RAVENCLAW_QUIDDITCH_ROBES_MATERIAL = ARMOR_MATERIALS.register("ravenclaw_quidditch_robes",
            () -> createRobeMaterial("ravenclaw_quidditch_robes"));
    public static final RegistryObject<Item> RAVENCLAW_QUIDDITCH_ROBES = ITEMS.register("ravenclaw_quidditch_robes",
            () -> new ArmorItem(RAVENCLAW_QUIDDITCH_ROBES_MATERIAL.getHolder().orElseThrow(), ArmorItem.Type.CHESTPLATE, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<ArmorMaterial> SLYTHERIN_QUIDDITCH_ROBES_MATERIAL = ARMOR_MATERIALS.register("slytherin_quidditch_robes",
            () -> createRobeMaterial("slytherin_quidditch_robes"));
    public static final RegistryObject<Item> SLYTHERIN_QUIDDITCH_ROBES = ITEMS.register("slytherin_quidditch_robes",
            () -> new ArmorItem(SLYTHERIN_QUIDDITCH_ROBES_MATERIAL.getHolder().orElseThrow(), ArmorItem.Type.CHESTPLATE, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<ArmorMaterial> QUIDDITCH_SHIN_GUARDS_MATERIAL = ARMOR_MATERIALS.register("quidditch_shin_guards",
            () -> createRobeMaterial("quidditch_shin_guards"));
    public static final RegistryObject<Item> QUIDDITCH_SHIN_GUARDS = ITEMS.register("quidditch_shin_guards",
            () -> new ArmorItem(QUIDDITCH_SHIN_GUARDS_MATERIAL.getHolder().orElseThrow(), ArmorItem.Type.LEGGINGS, new Item.Properties().stacksTo(1)));

    /**
     * Gold off a cursed hoard. It burns, and there is always more of it than there was.
     * Deliberately NOT a Galleon: no goblin will take it at face value.
     */
    public static final RegistryObject<Item> CURSED_GALLEON = ITEMS.register("cursed_galleon",
            () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC)
                    .fireResistant()));

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

    public static final RegistryObject<Item> GRINDYLOW_SPAWN_EGG = ITEMS.register("grindylow_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.GRINDYLOW, 0x74744E, 0xB0D64E, new Item.Properties()));
    public static final RegistryObject<Item> INFERIUS_SPAWN_EGG = ITEMS.register("inferius_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.INFERIUS, 0xB9BDB4, 0x1B1B1E, new Item.Properties()));

    public static final RegistryObject<Item> BOGGART_SPAWN_EGG = ITEMS.register("boggart_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.BOGGART, 0x1B1426, 0x6E4A8C, new Item.Properties()));

    public static final RegistryObject<Item> MERPERSON_SPAWN_EGG = ITEMS.register("merperson_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.MERPERSON, 0x5E6B5A, 0x223A26, new Item.Properties()));

    // Bark brown with a sickly leaf-green spot.
    public static final RegistryObject<Item> WHOMPING_WILLOW_SPAWN_EGG = ITEMS.register("whomping_willow_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.WHOMPING_WILLOW, 0x42372A, 0x4A5C30, new Item.Properties()));

    public static final RegistryObject<Item> SNAKE_SPAWN_EGG = ITEMS.register("snake_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.SNAKE, 0x3A6B2E, 0xC9B458, new Item.Properties()));

    // A barbed lakebed-stone spear. Unremarkable in air, nasty underwater --
    // see MerpersonSpearItem for where the bonus is applied.
    // Nacre and lake pearl: soft as armour, which is not what it is for.
    public static final RegistryObject<ArmorMaterial> MERCHIEFTAINESS_CROWN_MATERIAL =
            ARMOR_MATERIALS.register("merchieftainess_crown",
                    () -> new ArmorMaterial(
                            Util.make(new java.util.EnumMap<>(ArmorItem.Type.class), map -> {
                                map.put(ArmorItem.Type.BOOTS, 0);
                                map.put(ArmorItem.Type.LEGGINGS, 0);
                                map.put(ArmorItem.Type.CHESTPLATE, 0);
                                map.put(ArmorItem.Type.HELMET, 2);
                                map.put(ArmorItem.Type.BODY, 0);
                            }),
                            18,
                            net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_TURTLE,
                            () -> net.minecraft.world.item.crafting.Ingredient.of(
                                    net.minecraft.world.item.Items.NAUTILUS_SHELL),
                            java.util.List.of(new ArmorMaterial.Layer(
                                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                            MODID, "merchieftainess_crown"))),
                            0.0f,
                            0.0f));

    public static final RegistryObject<Item> MERCHIEFTAINESS_CROWN = ITEMS.register("merchieftainess_crown",
            () -> new com.hogwartsmagic.item.MerchieftainessCrownItem(
                    MERCHIEFTAINESS_CROWN_MATERIAL.getHolder().orElseThrow(),
                    ArmorItem.Type.HELMET,
                    // Fire-resistant on purpose: the Black Lake seeds lava springs, and
                    // losing a one-per-village artefact to a stray pocket is not a fair
                    // way to lose it. ArmorItem sets the real durability from the material.
                    new Item.Properties().stacksTo(1)
                            .rarity(net.minecraft.world.item.Rarity.RARE).fireResistant()));

    public static final RegistryObject<Item> MERPERSON_SPEAR = ITEMS.register("merperson_spear",
            () -> new com.hogwartsmagic.item.MerpersonSpearItem(
                    net.minecraft.world.item.Tiers.IRON,
                    com.hogwartsmagic.item.MerpersonSpearItem.properties(net.minecraft.world.item.Tiers.IRON)));

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register("main_tab", () -> CreativeModeTab.builder()
            .title(net.minecraft.network.chat.Component.translatable("itemGroup.hogwartsmagic.main_tab"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .title(net.minecraft.network.chat.Component.translatable("itemGroup.hogwartsmagic.main_tab"))
            .icon(() -> HARRY_POTTER_WAND.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(HARRY_POTTER_WAND.get());
                output.accept(SLYTHERINS_LOCKET.get());
                output.accept(CAVE_MEMORY.get());
                output.accept(EMPTY_MEMORY_PHIAL.get());
                output.accept(MEMORY.get());
                output.accept(CHAMBER_MEMORY.get());
                output.accept(SUMMONING_MEMORY.get());
                output.accept(PATRONUS_MEMORY.get());
                output.accept(DARK_LORD_MEMORY.get());
                output.accept(PENSIEVE_ITEM.get());
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
                output.accept(WILLOW_SAPLING_ITEM.get());
                output.accept(MOVING_PORTRAIT_ITEM.get());
                output.accept(CANDLE_CLUSTER_ITEM.get());
                output.accept(ENCHANTED_BOOK_PILE_ITEM.get());
                output.accept(WIZARD_CAULDRON_ITEM.get());
                output.accept(SNEAKOSCOPE_ITEM.get());
                output.accept(DIAGON_ARCHWAY_BRICK_ITEM.get());
                output.accept(OWL_PERCH_ITEM.get());
                output.accept(FLOO_FIREPLACE_ITEM.get());
                output.accept(WARD_ITEM.get());
                output.accept(HORCRUX_ITEM.get());
                output.accept(CONCEALED_ROCK_ITEM.get());
                output.accept(HIDDEN_CHAIN_ITEM.get());
                output.accept(BLOOD_SEAL_ITEM.get());
                output.accept(DESPAIR_BASIN_ITEM.get());
                output.accept(CRYSTAL_GOBLET_ITEM.get());
                output.accept(STATUE_HEART_ITEM.get());
                output.accept(GRYFFINDOR_BANNER_BLOCK_ITEM.get());
                output.accept(RAVENCLAW_BANNER_BLOCK_ITEM.get());
                output.accept(HUFFLEPUFF_BANNER_BLOCK_ITEM.get());
                output.accept(SLYTHERIN_BANNER_BLOCK_ITEM.get());
                output.accept(ARCANE_DUST.get());
                output.accept(ARCANE_CORE.get());
                output.accept(WIZARD_HAT_PURPLE.get());
                output.accept(WIZARD_HAT_GREEN.get());
                output.accept(WIZARD_HAT_NAVY.get());
                output.accept(WIZARD_HAT_BLACK.get());
                output.accept(WIZARD_HAT_BROWN.get());
                output.accept(WIZARD_HAT_BURGUNDY.get());
                output.accept(WITCH_HAT_PURPLE.get());
                output.accept(WITCH_HAT_GREEN.get());
                output.accept(WITCH_HAT_NAVY.get());
                output.accept(WITCH_HAT_BLACK.get());
                output.accept(WITCH_HAT_BROWN.get());
                output.accept(WITCH_HAT_BURGUNDY.get());
                output.accept(OWL_ROUTE_TAG.get());
                output.accept(HOGWARTS_INVITE.get());
                output.accept(HOGWARTS_GUIDE.get());
                output.accept(ADVANCED_POTION_MAKING.get());
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
                output.accept(DITTANY.get());
                output.accept(ASPHODEL.get());
                output.accept(WORMWOOD.get());
                output.accept(VALERIAN_SPRIGS.get());
                output.accept(ASHWINDER_EGG.get());
                output.accept(POLYJUICE_POTION.get());
                output.accept(WIGGENWELD_POTION.get());
                output.accept(PEPPERUP_POTION.get());
                output.accept(FAILED_POTION.get());
                output.accept(ESSENCE_OF_DITTANY.get());
                output.accept(FELIX_FELICIS.get());
                output.accept(POTION_OF_REMORSE.get());
                output.accept(VERITASERUM.get());
                output.accept(WOLFSBANE_POTION.get());
                output.accept(MANDRAKE_DRAUGHT.get());
                output.accept(SPLASH_MANDRAKE_DRAUGHT.get());
                output.accept(DRAUGHT_OF_LIVING_DEATH.get());
                output.accept(PHOENIX_FEATHER_CORE.get());
                output.accept(UNICORN_HAIR_CORE.get());
                output.accept(DRAGON_HEARTSTRING_CORE.get());
                output.accept(SORTING_HAT.get()); // Sorting Hat
                output.accept(GRYFFINDOR_ROBES.get());
                output.accept(RAVENCLAW_ROBES.get());
                output.accept(HUFFLEPUFF_ROBES.get());
                output.accept(SLYTHERIN_ROBES.get());
                output.accept(GRYFFINDOR_SCARF.get());
                output.accept(RAVENCLAW_SCARF.get());
                output.accept(HUFFLEPUFF_SCARF.get());
                output.accept(SLYTHERIN_SCARF.get());
                output.accept(POINTED_HAT.get());
                output.accept(GRYFFINDOR_QUIDDITCH_ROBES.get());
                output.accept(RAVENCLAW_QUIDDITCH_ROBES.get());
                output.accept(HUFFLEPUFF_QUIDDITCH_ROBES.get());
                output.accept(SLYTHERIN_QUIDDITCH_ROBES.get());
                output.accept(QUIDDITCH_SHIN_GUARDS.get());
                output.accept(TRIWIZARD_ROBES.get());
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
                output.accept(MERPERSON_SPAWN_EGG.get()); // Merperson Spawn Egg
                output.accept(GRINDYLOW_SPAWN_EGG.get()); // Grindylow Spawn Egg
                output.accept(INFERIUS_SPAWN_EGG.get()); // Inferius Spawn Egg
                output.accept(BOGGART_SPAWN_EGG.get()); // Boggart Spawn Egg
                output.accept(WHOMPING_WILLOW_SPAWN_EGG.get()); // Whomping Willow Spawn Egg
                output.accept(SNAKE_SPAWN_EGG.get()); // Wild Snake Spawn Egg
                output.accept(GALLEON.get());
                output.accept(CURSED_GALLEON.get());
                output.accept(CLANKERS.get()); // Galleon
                output.accept(GOBLIN_SILVER_DAGGER.get()); // Goblin-Forged Silver Dagger
                output.accept(MERPERSON_SPEAR.get()); // Merperson's Spear
                output.accept(MERCHIEFTAINESS_CROWN.get()); // Merchieftainess's Crown
                output.accept(COMMON_TOME_OF_MASTERY.get());
                output.accept(UNCOMMON_TOME_OF_MASTERY.get());
                output.accept(RARE_TOME_OF_MASTERY.get());
                output.accept(EPIC_TOME_OF_MASTERY.get());
                // Add books for all spells
                for (com.hogwartsmagic.spell.SpellType spell : com.hogwartsmagic.spell.SpellType.values()) {
                    output.accept(com.hogwartsmagic.item.MagicBookItem.createBook(spell));
                }
                for (com.hogwartsmagic.spell.SpellType spell : com.hogwartsmagic.spell.SpellType.values()) {
                    output.accept(com.hogwartsmagic.item.AssignmentItem.create(spell));
                }
            }).build());

    public ExampleMod()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Dev launches with --quickPlaySingleplayer open their world before Forge starts the game event bus
        // (Minecraft#onResourceLoadFinished runs ahead of ClientModLoader#completeModLoading), which silently drops
        // that world's AddReloadListenerEvent: Forge's LootModifierManager and our own reload listeners never get
        // created, and the first block or mob drop crashes the server. Starting the bus here, once every mod has
        // loaded, is harmless (start() is idempotent) and closes the window. Production never launches that way.
        if (!net.minecraftforge.fml.loading.FMLLoader.isProduction()) {
            modEventBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent e) ->
                    MinecraftForge.EVENT_BUS.start());
        }

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
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
        // Code-defined structures (Mini Hogwarts): structure and piece types.
        com.hogwartsmagic.worldgen.structure.ModStructures.register(modEventBus);
        // Register loot modifiers
        LOOT_MODIFIER_SERIALIZERS.register(modEventBus);
        // Register sounds
        ModSounds.SOUND_EVENTS.register(modEventBus);
        ModParticles.register(modEventBus);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        // BrewingRecipeRegisterEvent is NOT an IModBusEvent — must use the game event bus.
        MinecraftForge.EVENT_BUS.addListener(ExampleMod::onRegisterBrewingRecipes);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        // How strong the cinematic effects are (shake, bloom, moments...): each player's own choice.
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, com.hogwartsmagic.fx.FxConfig.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        event.enqueueWork(SpellPackets::register);
        // Both dists: the Sorting memory's home callback runs on the dedicated and the integrated server alike.
        event.enqueueWork(com.hogwartsmagic.sorting.SortingHatManager.get()::init);
        event.enqueueWork(com.hogwartsmagic.pensieve.WandMemory::register);
        event.enqueueWork(com.hogwartsmagic.pensieve.LetterMemory::register);
        event.enqueueWork(com.hogwartsmagic.pensieve.FlyingMemory::register);
        event.enqueueWork(com.hogwartsmagic.pensieve.FirstTaskMemory::register);
    }

    /**
     * Fired on {@link MinecraftForge#EVENT_BUS} while {@link net.minecraft.world.item.alchemy.PotionBrewing} is built.
     * Not a mod-bus event ({@code IModBusEvent}).
     */
    private static void onRegisterBrewingRecipes(BrewingRecipeRegisterEvent event) {
        event.addRecipe(new com.hogwartsmagic.polyjuice.WizardPotionBrewingRecipe());
        // Polyjuice is brewed only in the Wizard Cauldron (BrewRules.POLYJUICE). The stabilized_polyjuice_base potion
        // stays registered so bottles already in worlds still load, but nothing makes or uses it any more.
        LOGGER.info("Registered the mana regeneration brewing recipe (awkward + acromantula venom); Polyjuice is brewed in the Wizard Cauldron");
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");

    }

    /** Give every Ollivanders camera back before the server goes down, so nobody logs in disembodied. */
    @SubscribeEvent
    public void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event)
    {
        com.hogwartsmagic.event.OllivandersCeremony.endAll();
        // And pull everyone out of their Pensieve memories, so nobody is saved in the memory dimension.
        com.hogwartsmagic.pensieve.PensieveSessions.endAll();
    }

    @SubscribeEvent
    public void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
        com.hogwartsmagic.command.WizardRaidCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.FindDiagonAlleyCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.SpawnDiagonAlleyCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.OllivandersCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.FindMiniHogwartsCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.FindGringottsCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.BuildBankCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.GringottsLandmarksCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.CartTrackCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.UndercroftDumpCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.BuildUndercroftCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.SpawnMiniHogwartsCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.SummonHorntailCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.SpellMasteryCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.DarkMagicCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.LycanthropyCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.CrowdControlCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.PatronusCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.ImperioCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.LockInfoCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.ColloportusCommand.register(event.getDispatcher());
        com.hogwartsmagic.pensieve.PensieveCommand.register(event.getDispatcher());
    }

    private static Item schoolRobe(RegistryObject<ArmorMaterial> material, String textureName) {
        return new CosmeticArmorItem(material.getHolder().orElseThrow(), ArmorItem.Type.CHESTPLATE,
                CosmeticArmorItem.ROBE, textureName, new Item.Properties().stacksTo(1));
    }

    private static Item woolCosmetic(int modelKind, String textureName) {
        return new CosmeticArmorItem(WOOL_COSMETIC_MATERIAL.getHolder().orElseThrow(), ArmorItem.Type.HELMET,
                modelKind, textureName, new Item.Properties().stacksTo(1));
    }

    /** Knitted wool: a leather cap's worth of protection, mended with any wool. */
    private static ArmorMaterial createWoolCosmeticMaterial() {
        return new ArmorMaterial(
                Util.make(new java.util.EnumMap<>(ArmorItem.Type.class), map -> {
                    map.put(ArmorItem.Type.BOOTS, 0);
                    map.put(ArmorItem.Type.LEGGINGS, 0);
                    map.put(ArmorItem.Type.CHESTPLATE, 0);
                    map.put(ArmorItem.Type.HELMET, 1);
                    map.put(ArmorItem.Type.BODY, 0);
                }),
                18,
                net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER,
                () -> net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.tags.ItemTags.WOOL),
                java.util.List.of(new ArmorMaterial.Layer(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "wool_cosmetic"))),
                0.0f,
                0.0f
        );
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
