package com.hogwartsmagic.block;

import com.hogwartsmagic.spell.combat.CombatDamage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The blood-sealed cave entrance. A connected group of seals acts as one arch: touching it from the
 * outside costs blood (damage that bypasses armor), touching it from the inside is free, and either
 * way the whole arch opens for a minute and then reseals once nobody is standing in it.
 */
public class BloodSealBlock extends Block {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** Horizontal direction pointing to the outside of the cave. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final int OPEN_TICKS = 1200;
    private static final int MAX_ARCH = 64;
    private static final float TOLL = 6.0F;

    public BloodSealBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN, FACING);
    }

    /** The placer stands on the outside, facing in, so the outside is the opposite of where they look. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(OPEN) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(OPEN)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Direction out = state.getValue(FACING);
        Vec3 rel = player.position().subtract(Vec3.atCenterOf(pos));
        boolean outside = rel.x * out.getStepX() + rel.z * out.getStepZ() > 0;
        if (outside) {
            player.hurt(CombatDamage.bloodToll(level), TOLL);
            ((ServerLevel) level).sendParticles(new DustParticleOptions(new Vector3f(0.6f, 0f, 0f), 1.2f),
                    hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, 20, 0.2, 0.2, 0.2, 0);
        }
        setArch(level, pos, true);
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 1f, 0.5f);
        return InteractionResult.CONSUME;
    }

    /** Flood-fills connected Blood Seal blocks (max 64) and sets OPEN; schedules the reseal on the clicked block. */
    static void setArch(Level level, BlockPos start, boolean open) {
        Deque<BlockPos> todo = new ArrayDeque<>(List.of(start));
        Set<BlockPos> seen = new HashSet<>();
        while (!todo.isEmpty() && seen.size() < MAX_ARCH) {
            BlockPos p = todo.pop();
            if (!seen.add(p)) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof BloodSealBlock)) {
                continue;
            }
            level.setBlock(p, s.setValue(OPEN, open), Block.UPDATE_CLIENTS);
            for (Direction d : Direction.values()) {
                todo.push(p.relative(d));
            }
        }
        if (open) {
            level.scheduleTick(start, level.getBlockState(start).getBlock(), OPEN_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(OPEN)) {
            return;
        }
        AABB arch = new AABB(pos).inflate(3);
        boolean someoneInside = !level.getEntitiesOfClass(LivingEntity.class, arch,
                e -> level.getBlockState(e.blockPosition()).getBlock() instanceof BloodSealBlock
                        || level.getBlockState(e.blockPosition().above()).getBlock() instanceof BloodSealBlock).isEmpty();
        if (someoneInside) {
            level.scheduleTick(pos, this, 40);
            return;
        }
        setArch(level, pos, false);
    }
}
