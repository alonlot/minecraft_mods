package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.GringottsLiftBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Carries whoever is standing on the plinth to the other end of the shaft.
 *
 * <p><b>It moves the passenger, not the block.</b> A cage made of real blocks would have to shift
 * several hundred of them every tick down a twenty-six block shaft, and the client would see the
 * floor it is standing on flicker out of existence and reappear a little lower - which is exactly
 * how lifts in Minecraft usually end up looking. Applying velocity instead is smooth for free,
 * because the client integrates it itself and never has to be told where it now is.
 *
 * <p>Which is also why this sets {@code hurtMarked}: a velocity a server sets is not sent to the
 * client unless something asks for it, and without that the passenger stands still on the server
 * and walks around normally on their own screen.
 */
public class GringottsLiftBlockEntity extends BlockEntity {

    /** Blocks per tick. 0.32 puts a 26-block shaft at about four seconds, which reads as a lift. */
    private static final double SPEED_DOWN = 0.32D;
    /** Up is slower. Climbing out of somewhere is meant to take longer than falling into it. */
    private static final double SPEED_UP = 0.26D;

    /** How far out from the plinth counts as standing on it. */
    private static final double PLATFORM = 1.6D;

    /** How far the shaft is searched for the other end before giving up. */
    private static final int SEARCH = 64;

    private static final String TARGET_KEY = "TargetY";
    private static final String TICKS_KEY = "Ticks";

    /** -1 when idle. */
    private int ticks = -1;
    private int targetY;

    public GringottsLiftBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.GRINGOTTS_LIFT_BLOCK_ENTITY.get(), pos, state);
    }

    public boolean isMoving() {
        return this.ticks >= 0;
    }

    /** Finds the other end of the shaft and sets off. False if there is nowhere to go. */
    public boolean begin(ServerLevel level) {
        boolean down = getBlockState().getValue(GringottsLiftBlock.DESCENDS);
        int found = findOtherEnd(level, down);
        if (found == Integer.MIN_VALUE) {
            return false;
        }
        this.targetY = found;
        this.ticks = 0;
        level.playSound(null, this.worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS,
                1.2F, 0.55F);
        level.playSound(null, this.worldPosition, SoundEvents.IRON_TRAPDOOR_OPEN,
                SoundSource.BLOCKS, 0.8F, 0.5F);
        setChanged();
        return true;
    }

    /**
     * Walks the shaft looking for the opposite plinth.
     *
     * <p>Deliberately looks for the block rather than trusting a stored offset: the shaft's depth
     * depends on the terrain the bank landed on, and a hard-coded 26 would drop a passenger into
     * bedrock at one branch and leave them in mid-air at the next.
     */
    private int findOtherEnd(ServerLevel level, boolean down) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int step = 2; step <= SEARCH; step++) {
            int y = this.worldPosition.getY() + (down ? -step : step);
            if (y <= level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                break;
            }
            cursor.set(this.worldPosition.getX(), y, this.worldPosition.getZ());
            if (level.getBlockState(cursor).getBlock() instanceof GringottsLiftBlock) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    public static void tick(Level level, BlockPos pos, BlockState state,
                            GringottsLiftBlockEntity lift) {
        if (lift.ticks < 0 || !(level instanceof ServerLevel server)) {
            return;
        }
        lift.ticks++;

        boolean down = state.getValue(GringottsLiftBlock.DESCENDS);
        double speed = down ? -SPEED_DOWN : SPEED_UP;
        double stopAt = lift.targetY + 1.0D;

        AABB platform = new AABB(
                pos.getX() + 0.5D - PLATFORM, pos.getY() - 1.0D, pos.getZ() + 0.5D - PLATFORM,
                pos.getX() + 0.5D + PLATFORM, pos.getY() + 3.0D, pos.getZ() + 0.5D + PLATFORM)
                .move(0.0D, down ? -(lift.ticks * SPEED_DOWN) : lift.ticks * SPEED_UP, 0.0D);

        List<Player> riders = server.getEntitiesOfClass(Player.class, platform);
        boolean arrived = down
                ? platform.minY <= stopAt
                : platform.maxY >= stopAt + 3.0D;

        for (Player rider : riders) {
            if (arrived) {
                rider.teleportTo(pos.getX() + 0.5D, stopAt, pos.getZ() + 0.5D);
                rider.setDeltaMovement(Vec3.ZERO);
            } else {
                rider.setDeltaMovement(rider.getDeltaMovement().x, speed,
                        rider.getDeltaMovement().z);
            }
            // Without this the server keeps the velocity to itself and the passenger walks about
            // normally on their own screen while standing still here.
            rider.hurtMarked = true;
            rider.resetFallDistance();
        }

        if (lift.ticks % 6 == 0) {
            double y = down ? pos.getY() - lift.ticks * SPEED_DOWN : pos.getY() + lift.ticks * SPEED_UP;
            server.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5D, y, pos.getZ() + 0.5D,
                    3, 0.6D, 0.2D, 0.6D, 0.01D);
            server.playSound(null, pos, SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 0.5F, 0.7F);
        }

        // The ride is over when the cage would have arrived, whether or not anyone was aboard.
        if (arrived || lift.ticks > 20 * 20) {
            lift.ticks = -1;
            server.playSound(null, new BlockPos(pos.getX(), lift.targetY, pos.getZ()),
                    SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.6F);
            lift.setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TICKS_KEY, this.ticks);
        tag.putInt(TARGET_KEY, this.targetY);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // Never resume mid-travel. A passenger who logged out halfway is not standing on it any
        // more, and a cage that carries on without them just makes noise in an empty shaft.
        this.ticks = -1;
        this.targetY = tag.getInt(TARGET_KEY);
    }
}
