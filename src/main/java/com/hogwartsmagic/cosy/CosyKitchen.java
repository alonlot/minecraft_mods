package com.hogwartsmagic.cosy;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * The kitchen: the enchanted stove, the self-stirring cooking pot, and the {@code hogwartsmagic:pot_cooking}
 * recipe type. Everything queues on the mod's deferred registers from {@link #init()}, which the {@code ExampleMod}
 * constructor calls before they attach; the recipe type and serializer have registers of their own, attached here.
 */
public final class CosyKitchen {
    private CosyKitchen() {
    }

    private static final List<RegistryObject<Item>> ITEMS = new ArrayList<>();
    /** Kitchen items in creative-tab order. */
    public static final List<RegistryObject<Item>> CREATIVE_ORDER = Collections.unmodifiableList(ITEMS);

    /** Blocks that heat a pot standing on them (the stove and campfires only while LIT). */
    public static final TagKey<Block> HEAT_SOURCES =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "heat_sources"));

    private static RegistryObject<Block> block(String id, Supplier<Block> factory) {
        RegistryObject<Block> block = ExampleMod.BLOCKS.register(id, factory);
        ITEMS.add(ExampleMod.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties())));
        return block;
    }

    // ---- Recipe type ------------------------------------------------------------------------------

    private static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, ExampleMod.MODID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ExampleMod.MODID);

    public static final RegistryObject<RecipeType<PotCookingRecipe>> POT_COOKING_TYPE = RECIPE_TYPES.register("pot_cooking",
            () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "pot_cooking")));

    public static final RegistryObject<RecipeSerializer<PotCookingRecipe>> POT_COOKING_SERIALIZER =
            RECIPE_SERIALIZERS.register("pot_cooking", PotCookingRecipe.Serializer::new);

    // ---- Blocks -----------------------------------------------------------------------------------

    /** A cast-iron range that runs on fuel: right-click furnace fuel in (up to eight coals' worth). */
    public static final RegistryObject<Block> ENCHANTED_STOVE = block("enchanted_stove",
            () -> new EnchantedStoveBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BLAST_FURNACE).strength(3.5f)
                    .sound(SoundType.METAL).lightLevel(s -> s.getValue(EnchantedStoveBlock.LIT) ? 13 : 0)));

    /** A pot that cooks what you put in it, while it is hot and stirred. */
    public static final RegistryObject<Block> COOKING_POT = block("cooking_pot",
            () -> new CookingPotBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CAULDRON).strength(2.0f)
                    .sound(SoundType.METAL)));

    public static final RegistryObject<BlockEntityType<EnchantedStoveBlockEntity>> ENCHANTED_STOVE_BLOCK_ENTITY =
            ExampleMod.BLOCK_ENTITY_TYPES.register("enchanted_stove",
                    () -> BlockEntityType.Builder.of(EnchantedStoveBlockEntity::new, ENCHANTED_STOVE.get()).build(null));

    public static final RegistryObject<BlockEntityType<CookingPotBlockEntity>> COOKING_POT_BLOCK_ENTITY =
            ExampleMod.BLOCK_ENTITY_TYPES.register("cooking_pot",
                    () -> BlockEntityType.Builder.of(CookingPotBlockEntity::new, COOKING_POT.get()).build(null));

    // ---- Heat -------------------------------------------------------------------------------------

    /** True when this state heats a pot above it: in the heat_sources tag, and lit if it has a LIT property. */
    public static boolean isHeat(BlockState state) {
        return state.is(HEAT_SOURCES) && (!state.hasProperty(BlockStateProperties.LIT) || state.getValue(BlockStateProperties.LIT));
    }

    /** True when the block under {@code pos} is a heat source. */
    public static boolean isHeatedBelow(BlockGetter level, BlockPos pos) {
        return isHeat(level.getBlockState(pos.below()));
    }

    /** Loads this class so every entry is queued on the deferred registers before they attach. */
    public static void init() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        RECIPE_TYPES.register(bus);
        RECIPE_SERIALIZERS.register(bus);
    }

    /** Adds the kitchen items to the main creative tab. */
    public static void fillCreativeTab(CreativeModeTab.Output output) {
        CREATIVE_ORDER.forEach(item -> output.accept(item.get()));
    }
}
