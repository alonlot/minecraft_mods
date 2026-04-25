package com.hogwartsmagic.block;

import com.hogwartsmagic.ModSounds;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MandrakePlantBlock extends BushBlock {
    public static final BooleanProperty PULLING = BooleanProperty.create("pulling");
    public static final MapCodec<MandrakePlantBlock> CODEC = simpleCodec(MandrakePlantBlock::new);
    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 12.0D, 14.0D);
    private static final double SCREAM_RADIUS = 7.0D;

    public MandrakePlantBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(PULLING, false));
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState floor, BlockGetter level, BlockPos pos) {
        return super.mayPlaceOn(floor, level, pos)
                || floor.is(Blocks.MUD)
                || floor.is(Blocks.ROOTED_DIRT)
                || floor.is(Blocks.COARSE_DIRT)
                || floor.is(Blocks.PODZOL)
                || floor.is(Blocks.MOSS_BLOCK);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PULLING);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative() && !player.isSpectator()) {
            scream(level, pos, player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    private static void scream(Level level, BlockPos pos, Player harvester) {
        level.playSound(null, pos, ModSounds.MANDRAKE_SCREAM.get(), SoundSource.BLOCKS, 1.7F, 1.0F);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SONIC_BOOM,
                    pos.getX() + 0.5D,
                    pos.getY() + 0.7D,
                    pos.getZ() + 0.5D,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D);
        }

        AABB area = new AABB(pos).inflate(SCREAM_RADIUS);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)) {
            if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
                continue;
            }

            int duration = target == harvester ? 20 * 7 : 20 * 5;
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, duration, 0));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 1));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0));
        }
    }
}
