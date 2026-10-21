package com.hogwartsmagic.entity;

import com.hogwartsmagic.pensieve.scene.MemoryBall;
import com.hogwartsmagic.pensieve.scene.SceneNames;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * A Quidditch ball or the Remembrall used as a Pensieve memory actor ({@code memory_quaffle}, {@code memory_bludger},
 * {@code memory_remembrall}; see {@code pensieve/scene/MemoryBall}). It has no AI, gravity, collision or save data.
 * The actor's position is the ball's centre (its eye height is 0, so a camera looking at it aims at the centre).
 * The memory rolls it by the distance it moved each tick ({@link #spin}, with the previous tick's for interpolation),
 * and sets {@link #speed} and {@link #seed}.
 */
public abstract class MemoryBallEntity extends Entity {
    /** The orientation this tick and the last (MemoryBall.Spin: rolled by distance / radius). */
    public final MemoryBall.Spin spin = new MemoryBall.Spin(), spinO = new MemoryBall.Spin();
    /** Blocks moved in the last tick, for the Bludger's shake. */
    public double speed;
    /** The smoke and pattern seed from the actor id (set by the memory; the same every replay). */
    public int seed;

    protected MemoryBallEntity(EntityType<? extends MemoryBallEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    /** The scene's entity type id of this ball. */
    public abstract String ballType();

    /** Moves the ball's roll on by a tick's travel (blocks), as the memory places it. */
    public void rollBy(double dx, double dy, double dz, float scale) {
        spinO.set(spin);
        spin.roll(dx, dy, dz, MemoryBall.radius(ballType(), scale));
        speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Starts a tick that did not move (a first placement or a cut): no roll, and the interpolation holds still. */
    public void holdRoll() {
        spinO.set(spin);
        speed = 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** The ball at any scale up to 4 is well inside a 3-block box about its centre. */
    @Override
    public AABB getBoundingBoxForCulling() {
        double x = getX(), y = getY(), z = getZ();
        return new AABB(x - 1.5, y - 1.5, z - 1.5, x + 1.5, y + 1.5, z + 1.5);
    }

    public static final class Quaffle extends MemoryBallEntity {
        public Quaffle(EntityType<? extends MemoryBallEntity> type, Level level) {
            super(type, level);
        }

        @Override
        public String ballType() {
            return SceneNames.QUAFFLE_ACTOR;
        }
    }

    public static final class Bludger extends MemoryBallEntity {
        public Bludger(EntityType<? extends MemoryBallEntity> type, Level level) {
            super(type, level);
        }

        @Override
        public String ballType() {
            return SceneNames.BLUDGER_ACTOR;
        }
    }

    public static final class Remembrall extends MemoryBallEntity {
        public Remembrall(EntityType<? extends MemoryBallEntity> type, Level level) {
            super(type, level);
        }

        @Override
        public String ballType() {
            return SceneNames.REMEMBRALL_ACTOR;
        }
    }
}
