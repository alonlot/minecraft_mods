package com.hogwartsmagic.block;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.BlockPos;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A small decorative goblet that sits on the Basin of Despair's island. */
public class CrystalGobletBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(5, 0, 5, 11, 1, 11),
            Block.box(7.5, 1, 7.5, 8.5, 5, 8.5),
            Block.box(5, 5, 5, 11, 10, 11));

    public CrystalGobletBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * Lifting the goblet: inside a cave the block becomes the crumbling cave goblet. Anywhere else (Diagon Alley decor)
     * it is just scenery and the click passes.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.PASS;
        }
        if (com.hogwartsmagic.locket.LocketCaves.findBasinFor(server, pos).isEmpty()) {
            return InteractionResult.PASS;
        }
        ItemStack goblet = new ItemStack(ExampleMod.CRYSTAL_GOBLET_ITEM.get());
        level.removeBlock(pos, false);
        player.getInventory().placeItemBackInInventory(goblet);
        return InteractionResult.SUCCESS;
    }

    /** Breaking it drops the cave goblet inside a cave and the plain decorative goblet elsewhere. */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        Vec3 origin = builder.getOptionalParameter(LootContextParams.ORIGIN);
        if (origin != null && com.hogwartsmagic.locket.LocketCaves.findBasinFor(builder.getLevel(), BlockPos.containing(origin)).isPresent()) {
            return List.of(new ItemStack(ExampleMod.CRYSTAL_GOBLET_ITEM.get()));
        }
        return super.getDrops(state, builder);
    }
}
