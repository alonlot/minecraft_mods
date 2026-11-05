package com.hogwartsmagic.block;

import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.entity.GoblinGuardEntity;
import com.hogwartsmagic.gringotts.GringottsAlarm;
import com.hogwartsmagic.gringotts.GringottsDisguise;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The screen across the back of the banking hall, and the gate in it.
 *
 * <p>The imported schematic already draws this as an octagon of iron bars with a doorway on the
 * hall's centre line, and until now it was a doorway - you walked through it. Under a break-in it
 * has to be a <b>gate</b>, because "the goblins do not escort you down" only means anything if
 * there is something between you and the carts.
 *
 * <h2>Two ways past, and they cost different things</h2>
 *
 * <ul>
 *   <li><b>Be a goblin.</b> Polyjuiced into one, a bare hand on the gate swings it open for eight
 *       seconds and nobody looks up. This is what the mod's whole Polyjuice chain was built for and
 *       never had a destination for.</li>
 *   <li><b>Force it.</b> The bars are goblin-forged and take about twelve seconds a block, and the
 *       first swing brings every guard in the hall. That is not a punishment, it is the price:
 *       twelve seconds of standing in the open swinging a pickaxe at a bank is exactly the moment
 *       a heist is supposed to have.</li>
 * </ul>
 *
 * <p>Deliberately no third way. Alohomora does not work on it, which is canon and also the hint -
 * a lock that shrugs off the unlocking charm is telling you the answer is not a lock at all.
 *
 * <p>The whole screen is this block, not just the gap, because a tough gate beside ordinary iron
 * bars is not a gate: you would break the railing next to it in four seconds and walk round.
 */
public class GringottsGateBlock extends IronBarsBlock {

    /** True while the gate stands open: no model, no collision, and it shuts itself. */
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    /** How long a goblin's hand holds it. Long enough to walk through, short enough to matter. */
    private static final int OPEN_TICKS = 160;
    /** How far the swing carries, so one touch opens the doorway and not one bar of it. */
    private static final int SPREAD = 4;

    public GringottsGateBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(OPEN, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OPEN);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : super.getShape(state, level, pos, context);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty()
                : super.getCollisionShape(state, level, pos, context);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (state.getValue(OPEN)) {
            return InteractionResult.PASS;
        }

        if (!GringottsDisguise.isGoblin(player)) {
            server.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.9F, 0.6F);
            server.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5D, pos.getY() + 1.0D,
                    pos.getZ() + 0.5D, 6, 0.2D, 0.2D, 0.2D, 0.01D);
            player.displayClientMessage(
                    Component.translatable("block.hogwartsmagic.gringotts_gate.locked")
                            .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }

        swing(server, pos);
        return InteractionResult.CONSUME;
    }

    /**
     * Opens this bar and everything connected to it, out to {@link #SPREAD}.
     *
     * <p>Bar by bar would mean clicking a doorway open nine times. Spreading along the screen is
     * also why it is capped: run it over the whole octagon and one touch opens the entire back wall
     * of the bank, which is a rather different feature.
     */
    private void swing(ServerLevel level, BlockPos from) {
        level.playSound(null, from, ModSounds.VAULT_DOOR_BOLTS.get(), SoundSource.BLOCKS,
                0.8F, 1.4F);
        for (BlockPos pos : BlockPos.betweenClosed(from.offset(-SPREAD, -SPREAD, -SPREAD),
                from.offset(SPREAD, SPREAD, SPREAD))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof GringottsGateBlock) || state.getValue(OPEN)) {
                continue;
            }
            level.setBlock(pos, state.setValue(OPEN, Boolean.TRUE), 3);
            level.scheduleTick(pos.immutable(), this, OPEN_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(OPEN)) {
            return;
        }
        // Never shut on somebody standing in the doorway; come back in a second instead.
        if (!level.getEntities((net.minecraft.world.entity.Entity) null,
                new net.minecraft.world.phys.AABB(pos), e -> !e.isSpectator()).isEmpty()) {
            level.scheduleTick(pos, this, 20);
            return;
        }
        level.setBlock(pos, state.setValue(OPEN, Boolean.FALSE), 3);
        level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.7F, 1.2F);
    }

    /**
     * The first swing is what the hall hears, not the twelfth second: guards turn on the thief the
     * moment a non-goblin starts hitting the screen. Heat is only raised when a bar actually goes.
     */
    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel && player instanceof ServerPlayer thief
                && !thief.isSpectator() && !GringottsDisguise.isGoblin(player)) {
            GoblinGuardEntity.alertGuards(thief, thief, 48.0D);
        }
        super.attack(state, level, pos, player);
    }

    /**
     * Breaking one is the loud way in, so it is loud: every guard in the hall, and two steps of
     * heat rather than one.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer thief
                && !GringottsDisguise.isGoblin(player)) {
            GringottsAlarm.get(server).raise(server, thief, pos, 2, "gringotts.alarm.gate");
            GoblinGuardEntity.alertGuards(thief, thief, 48.0D);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
