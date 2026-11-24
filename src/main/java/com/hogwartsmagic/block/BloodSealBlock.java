package com.hogwartsmagic.block;

import com.hogwartsmagic.spell.combat.CombatDamage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The blood-sealed cave entrance. A connected group of seals acts as one arch: touching it from the
 * outside costs blood (damage that bypasses armor), touching it from the inside is free, and either
 * way the whole arch opens for a minute and then reseals once nobody is standing in it.
 */
public class BloodSealBlock extends Block implements LiquidBlockContainer {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** Horizontal direction pointing to the outside of the cave. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** The look: 0 bare rock with a hairline, 1 smeared, 2 melting, 3 open. OPEN stays the collision flag (true at 3). */
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 3);

    /** Ticks between melt stages: 0 to 1 on the click, 1 to 2, then 2 to 3, so the arch is open after 14 ticks. */
    private static final int MELT_STEP = 7;
    private static final int OPEN_TICKS = 1200;
    private static final int MAX_ARCH = 64;
    private static final float TOLL = 6.0F;

    public BloodSealBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false).setValue(STAGE, 0).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN, STAGE, FACING);
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
        if (state.getValue(STAGE) != 0) {
            // Already melting: no second toll. But if the melt tick was lost (a /setblock, a re-placed
            // structure), nothing would ever finish it, so re-arm it from the current stage.
            if (!level.isClientSide && !meltPending((ServerLevel) level, pos)) {
                meltArch(level, pos, state.getValue(STAGE));
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Direction out = state.getValue(FACING);
        Vec3 rel = player.position().subtract(Vec3.atCenterOf(pos));
        boolean outside = rel.x * out.getStepX() + rel.z * out.getStepZ() > 0;
        if (outside) {
            if (!player.hasInfiniteMaterials()) {
                player.invulnerableTime = 0; // i-frames must not make the toll free
                player.hurt(CombatDamage.bloodToll(level), TOLL);
            }
            ((ServerLevel) level).sendParticles(com.hogwartsmagic.spell.vfx.SpellVfx.swap(new DustParticleOptions(new Vector3f(0.6f, 0f, 0f), 1.2f)),
                    hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, 20, 0.2, 0.2, 0.2, 0);
        }
        meltArch(level, pos, 1);
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 1f, 0.5f);
        return InteractionResult.CONSUME;
    }

    /** Connected seals (max 64 seals, never revisiting a position), read-only. */
    static List<BlockPos> arch(Level level, BlockPos start) {
        List<BlockPos> seals = new ArrayList<>();
        Deque<BlockPos> todo = new ArrayDeque<>(List.of(start));
        Set<BlockPos> seen = new HashSet<>();
        while (!todo.isEmpty() && seals.size() < MAX_ARCH) {
            BlockPos p = todo.pop();
            if (!seen.add(p) || !(level.getBlockState(p).getBlock() instanceof BloodSealBlock)) {
                continue;
            }
            seals.add(p);
            for (Direction d : Direction.values()) {
                todo.push(p.relative(d));
            }
        }
        return seals;
    }

    /** True when any seal of the arch still has a scheduled tick to carry the melt on. */
    private boolean meltPending(ServerLevel level, BlockPos pos) {
        for (BlockPos p : arch(level, pos)) {
            if (level.getBlockTicks().hasScheduledTick(p, this)) {
                return true;
            }
        }
        return false;
    }

    /** Puts the whole connected arch at one stage; 3 also opens it. The clicked block carries the schedule. */
    static void meltArch(Level level, BlockPos start, int stage) {
        for (BlockPos p : arch(level, start)) {
            BlockState s = level.getBlockState(p);
            level.setBlock(p, s.setValue(STAGE, stage).setValue(OPEN, stage == 3), Block.UPDATE_CLIENTS);
        }
        level.scheduleTick(start, level.getBlockState(start).getBlock(), stage == 3 ? OPEN_TICKS : MELT_STEP);
    }

    /** Seals the whole arch back to bare rock. */
    static void sealArch(Level level, BlockPos start) {
        for (BlockPos p : arch(level, start)) {
            BlockState s = level.getBlockState(p);
            level.setBlock(p, s.setValue(STAGE, 0).setValue(OPEN, false), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int stage = state.getValue(STAGE);
        if (!state.getValue(OPEN) && stage != 0) {
            meltArch(level, pos, stage + 1); // melting: stage 1 -> 2 -> 3
            return;
        }
        if (!state.getValue(OPEN)) {
            return;
        }
        for (BlockPos p : arch(level, pos)) {
            AABB cube = new AABB(p);
            if (!level.getEntitiesOfClass(LivingEntity.class, cube, e -> !e.isSpectator()).isEmpty()) {
                level.scheduleTick(pos, this, 40);
                return;
            }
        }
        sealArch(level, pos);
    }

    /** A slow red drip from the top of the arch's outer face, only when someone is close enough to see it. */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(OPEN) || state.getValue(STAGE) == 3 || random.nextInt(10) != 0) {
            return;
        }
        if (level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4.0, false) == null) {
            return;
        }
        Direction out = state.getValue(FACING);
        boolean top = !(level.getBlockState(pos.above()).getBlock() instanceof BloodSealBlock);
        double x = pos.getX() + 0.5 + out.getStepX() * 0.52 + (out.getStepX() == 0 ? random.nextDouble() - 0.5 : 0);
        double z = pos.getZ() + 0.5 + out.getStepZ() * 0.52 + (out.getStepZ() == 0 ? random.nextDouble() - 0.5 : 0);
        double y = top ? pos.getY() + 1.0 : pos.getY() + random.nextDouble();
        level.addParticle(net.minecraft.core.particles.ParticleTypes.DRIPPING_DRIPSTONE_LAVA, x, y, z, 0, 0, 0);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Water must never spread into (and break) this block while its shape is empty. */
    @Override
    public boolean canPlaceLiquid(@Nullable Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        return false;
    }
}
