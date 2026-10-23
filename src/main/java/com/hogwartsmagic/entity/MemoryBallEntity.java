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
 * The memory gives it its {@link #roll} and {@link #seed}.
 */
public abstract class MemoryBallEntity extends Entity {
    /**
     * The ball's roll, a pure function of its path and the scene clock (MemoryBall.Roll; set by the memory from the
     * actor's path, so a late joiner turns exactly as a watcher does).
     */
    public MemoryBall.Roll roll;
    /** The smoke and pattern seed from the actor id (set by the memory; the same every replay). */
    public int seed;

    protected MemoryBallEntity(EntityType<? extends MemoryBallEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    /** The scene's entity type id of this ball. */
    public abstract String ballType();

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

    /**
     * The golden egg of the First Task (see {@code pensieve/scene/GoldenEgg}). It rides this class for the seed the
     * memory gives every ball (the glint's clock); it never rolls, and its renderer ignores {@link #roll}.
     */
    public static final class GoldenEgg extends MemoryBallEntity {
        public GoldenEgg(EntityType<? extends MemoryBallEntity> type, Level level) {
            super(type, level);
        }

        @Override
        public String ballType() {
            return SceneNames.GOLDEN_EGG_ACTOR;
        }
    }
}
