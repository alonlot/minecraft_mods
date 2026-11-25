package com.hogwartsmagic.cosy;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The cosy home pack: the hearth and the Warmed effect. The furniture lives in
 * {@link com.hogwartsmagic.block.decor.ModDecorations}, the candles in its floating candle block.
 */
public final class CosyHome {
    private CosyHome() {
    }

    private static final List<RegistryObject<Item>> ITEMS = new ArrayList<>();
    /** Items of this pack, in creative-tab order. */
    public static final List<RegistryObject<Item>> CREATIVE_ORDER = Collections.unmodifiableList(ITEMS);

    public static final RegistryObject<MobEffect> WARMED = ExampleMod.MOB_EFFECTS.register("warmed", WarmedEffect::new);

    public static final RegistryObject<Block> HEARTH = ExampleMod.BLOCKS.register("hearth",
            () -> new HearthBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS)
                    .strength(2.5f).sound(SoundType.STONE).noOcclusion()
                    .lightLevel(state -> state.getValue(CampfireBlock.LIT) ? 13 : 0)));

    static {
        ITEMS.add(ExampleMod.ITEMS.register("hearth", () -> new BlockItem(HEARTH.get(), new Item.Properties())));
    }

    public static final RegistryObject<BlockEntityType<HearthBlockEntity>> HEARTH_BLOCK_ENTITY =
            ExampleMod.BLOCK_ENTITY_TYPES.register("hearth",
                    () -> BlockEntityType.Builder.of(HearthBlockEntity::new, HEARTH.get()).build(null));

    /** Loads this class so every entry is queued on the deferred registers before they attach. */
    public static void init() {
    }
}
