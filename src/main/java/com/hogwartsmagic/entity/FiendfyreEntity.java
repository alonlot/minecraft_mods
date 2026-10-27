package com.hogwartsmagic.entity;

import com.hogwartsmagic.spell.TerrainRules;
import com.hogwartsmagic.spell.combat.CombatEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Entity that manages the spreading of Fiendfyre.
 * It starts small and spreads to nearby blocks (even non-flammable ones) for 15 seconds. Every fire block
 * goes through TerrainRules.canPlace as its caster (an NPC, an unknown or an offline caster falls back to
 * the mobGriefing rule), so claims, spawn protection and Cave Inimicum wards hold.
 * The clients draw the dragon's billows and smoke over the fire it lights, and scorch under it
 * (client/pensieve/DragonFireRenderer).
 */
public class FiendfyreEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_LIFE_TICKS = SynchedEntityData.defineId(FiendfyreEntity.class, EntityDataSerializers.INT);
    private static final int MAX_LIFE = 300; // 15 seconds (20 ticks per second)

    @Nullable
    private UUID casterUuid;

    public FiendfyreEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    /** Whoever cast the beam (a projectile's owner is resolved to the caster). Call before adding it. */
    public void setCaster(@Nullable Entity caster) {
        Entity responsible = CombatEffects.responsible(caster);
        this.casterUuid = responsible == null ? null : responsible.getUUID();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_LIFE_TICKS, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setLifeTicks(tag.getInt("LifeTicks"));
        this.casterUuid = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("LifeTicks", getLifeTicks());
        if (this.casterUuid != null) {
            tag.putUUID("Caster", this.casterUuid);
        }
    }

    public int getLifeTicks() {
        return this.entityData.get(DATA_LIFE_TICKS);
    }

    public void setLifeTicks(int ticks) {
        this.entityData.set(DATA_LIFE_TICKS, ticks);
    }

    @Override
    public void tick() {
        super.tick();

        int ticks = getLifeTicks();
        if (ticks >= MAX_LIFE) {
            if (!this.level().isClientSide) {
                this.discard();
            }
            return;
        }

        if (!this.level().isClientSide) {
            // Only the server advances the synched counter; the client receives it.
            setLifeTicks(ticks + 1);
            // Spreading logic every 10 ticks
            if (ticks % 10 == 0 && this.level() instanceof ServerLevel serverLevel) {
                spreadFire(serverLevel);
            }
        }
    }

    /** How far from its centre it is lighting fires now (blocks): from 1 to 8 over its 15 seconds. */
    public int spreadRadius() {
        return 1 + (getLifeTicks() / 40);
    }

    private void spreadFire(ServerLevel level) {
        // Randomly pick blocks around the center to ignite
        BlockPos center = this.blockPosition();
        int radius = spreadRadius();
        Entity caster = this.casterUuid == null ? null : level.getEntity(this.casterUuid);

        for (int i = 0; i < 5; i++) {
            int rx = this.random.nextInt(radius * 2 + 1) - radius;
            int ry = this.random.nextInt(3) - 1;
            int rz = this.random.nextInt(radius * 2 + 1) - radius;
            
            BlockPos target = center.offset(rx, ry, rz);
            if (!level.isLoaded(target) || !level.isEmptyBlock(target)) {
                continue;
            }
            if (TerrainRules.canPlace(level, target, caster)) {
                level.setBlockAndUpdate(target, Blocks.FIRE.defaultBlockState());
            }
        }
    }
}
