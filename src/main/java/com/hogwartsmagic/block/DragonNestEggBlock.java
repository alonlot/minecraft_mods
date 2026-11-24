package com.hogwartsmagic.block;

import java.util.List;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A dragon's egg: a cold, heavy, egg-shaped lump (speckled grey for the Horntail, pale metal for
 * the Ironbelly) sitting in a nest. Mined with a pickaxe it comes away as an item, and every
 * dragon within sixty-four blocks turns on whoever took it. It never ticks, never hatches and
 * never grows anything: it is only a trophy, or something to sell in Knockturn Alley.
 */
public class DragonNestEggBlock extends Block {
    /** Every dragon egg block, for the theft check. */
    public static final TagKey<Block> DRAGON_EGGS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "dragon_eggs"));
    /** Every dragon egg item, for "is the thief still carrying one". */
    public static final TagKey<Item> DRAGON_EGG_ITEMS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "dragon_eggs"));

    /** Foot, belly, shoulder and crown, matching the boxes of the block model. */
    public static final VoxelShape SHAPE = Shapes.or(
            Block.box(3, 0, 3, 13, 2, 13),
            Block.box(2, 2, 2, 14, 10, 14),
            Block.box(3, 10, 3, 13, 13, 13),
            Block.box(5, 13, 5, 11, 15, 11));

    public DragonNestEggBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.hogwartsmagic.dragon_egg.tooltip")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
