package com.hogwartsmagic.block;

import com.hogwartsmagic.entity.DementorEntity;
import com.hogwartsmagic.entity.PatronusEntity;
import com.hogwartsmagic.spell.PatronusShield;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * The invisible light of a corporeal Patronus and of the Patronus shield. Its placers remove it, but a
 * chunk unload or a server stop discards the stag without calling its cleanup, and a dimension change
 * strands the shield's lights. So every light checks itself on a scheduled tick (saved with the chunk)
 * and removes itself when no stag and no shield-holding player is near.
 */
public class PatronusLightBlock extends Block {
    private static final int RECHECK_TICKS = 40;
    /** The stag's pulse rings reach 30 blocks; the shield reaches {@link PatronusShield#RANGE}. */
    private static final double OWNER_SEARCH = 32.0D;

    public PatronusLightBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty(); // Not clickable/Targetable
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty(); // No collision
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, RECHECK_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!hasOwnerNear(level, pos)) {
            level.removeBlock(pos, false);
            return;
        }
        level.scheduleTick(pos, this, RECHECK_TICKS);
    }

    private static boolean hasOwnerNear(ServerLevel level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(OWNER_SEARCH);
        if (!level.getEntitiesOfClass(PatronusEntity.class, area, PatronusEntity::isAlive).isEmpty()) {
            return true;
        }
        double reach = PatronusShield.RANGE + 2.0D;
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= reach * reach
                    && player.getPersistentData().getBoolean(DementorEntity.TAG_PATRONUS_SHIELD_CHANNELING)) {
                return true;
            }
        }
        return false;
    }
}
