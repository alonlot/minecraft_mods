package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class DarkMarkEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_DURATION = SynchedEntityData.defineId(DarkMarkEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_VISUAL_SCALE = SynchedEntityData.defineId(DarkMarkEntity.class, EntityDataSerializers.FLOAT);
    private static final int STORM_DURATION_TICKS = 20 * 90;
    private static final int STORM_REFRESH_INTERVAL_TICKS = 20;

    public DarkMarkEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public DarkMarkEntity(Level level, double x, double y, double z, int duration) {
        this(level, x, y, z, duration, 1.0f);
    }

    public DarkMarkEntity(Level level, double x, double y, double z, int duration, float visualScale) {
        this(ModEntities.DARK_MARK.get(), level);
        this.setPos(x, y, z);
        this.entityData.set(DATA_DURATION, duration);
        this.entityData.set(DATA_VISUAL_SCALE, Math.max(0.5f, visualScale));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_DURATION, 1200);
        builder.define(DATA_VISUAL_SCALE, 1.0f);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(DATA_DURATION, tag.getInt("Duration"));
        if (tag.contains("VisualScale")) {
            this.entityData.set(DATA_VISUAL_SCALE, tag.getFloat("VisualScale"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Duration", this.entityData.get(DATA_DURATION));
        tag.putFloat("VisualScale", this.entityData.get(DATA_VISUAL_SCALE));
    }

    /**
     * Pensieve memories: a mark that exists only on one client has no server to count its synched duration down,
     * so the memory does it (the mark fades over its last 80 ticks, as a real one does).
     */
    public void setRemainingTicks(int ticks) {
        this.entityData.set(DATA_DURATION, ticks);
    }

    public float getVisualScale() {
        return this.entityData.get(DATA_VISUAL_SCALE);
    }

    /** Ticks left before the mark is gone; the renderer fades it over the last 80. */
    public int getRemainingTicks() {
        return this.entityData.get(DATA_DURATION);
    }

    /**
     * The 1x1 hitbox would cull the mark at ~64 blocks and whenever its centre left the screen,
     * though it hangs tens of blocks across the sky. DarkMarkRenderer draws it all.
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSq) {
        return distanceSq < 512.0D * 512.0D;
    }

    @Override
    public void tick() {
        super.tick();
        int duration = this.entityData.get(DATA_DURATION);
        if (duration <= 0) {
            if (!this.level().isClientSide) {
                this.discard();
            }
            return;
        }

        if (!this.level().isClientSide) {
            // Only the server counts down the synched duration; the client receives it.
            this.entityData.set(DATA_DURATION, duration - 1);
            if (this.level() instanceof ServerLevel serverLevel && (this.tickCount == 1 || this.tickCount % STORM_REFRESH_INTERVAL_TICKS == 0)) {
                startStorm(serverLevel);
            }
            // Build up drama: only start effects after 40 ticks
            if (this.tickCount > 40) {
                if (this.tickCount % 60 == 0 && this.level() instanceof ServerLevel serverLevel) {
                    spawnStormLightning(serverLevel);
                }
                if (this.tickCount % 100 == 0) {
                    applyBuffs();
                }
                if (this.tickCount % 200 == 0) {
                    summonMonsters();
                }
            }
        }
    }

    private void startStorm(ServerLevel serverLevel) {
        int remainingDuration = Math.max(this.entityData.get(DATA_DURATION), STORM_DURATION_TICKS);
        serverLevel.setWeatherParameters(0, remainingDuration, true, true);
    }

    private void spawnStormLightning(ServerLevel serverLevel) {
        net.minecraft.world.entity.LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
        if (lightning == null) {
            return;
        }

        double offsetX = (this.random.nextDouble() - 0.5D) * 30.0D;
        double offsetZ = (this.random.nextDouble() - 0.5D) * 30.0D;
        net.minecraft.core.BlockPos strikePos = serverLevel.getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.core.BlockPos.containing(this.getX() + offsetX, 0.0D, this.getZ() + offsetZ)
        );
        lightning.moveTo(Vec3.atBottomCenterOf(strikePos));
        lightning.setVisualOnly(true);
        serverLevel.addFreshEntity(lightning);
    }

    private void applyBuffs() {
        AABB area = new AABB(this.getX() - 50, this.getY() - 100, this.getZ() - 50, this.getX() + 50, this.getY() + 20, this.getZ() + 50);
        List<WizardEntity> wizards = this.level().getEntitiesOfClass(WizardEntity.class, area);
        for (WizardEntity wizard : wizards) {
            giveMarkBuffs(wizard);
        }
        // Dark Magic: the Mark answers a Corrupted or Lost wizard as it answers a Death Eater.
        List<net.minecraft.world.entity.player.Player> darkPlayers = this.level().getEntitiesOfClass(
                net.minecraft.world.entity.player.Player.class, area,
                player -> !player.isSpectator() && com.hogwartsmagic.spell.dark.DarkRewards.darkMarkBuffs(player));
        for (net.minecraft.world.entity.player.Player player : darkPlayers) {
            giveMarkBuffs(player);
        }
    }

    /** Strength II and Regeneration I for 10 seconds, refreshed every 5. */
    private static void giveMarkBuffs(LivingEntity entity) {
        entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 200, 1));
        entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 200, 0));
    }

    private void summonMonsters() {
        if (this.level() instanceof ServerLevel serverLevel) {
            AABB area = new AABB(this.getX() - 30, this.getY() - 100, this.getZ() - 30, this.getX() + 30, this.getY() + 20, this.getZ() + 30);
            long monsterCount = serverLevel.getEntitiesOfClass(Mob.class, area, m -> m.getType().getCategory() == MobCategory.MONSTER).size();
            
            if (monsterCount < 15) {
                double rx = this.getX() + (this.random.nextDouble() - 0.5) * 40.0;
                double rz = this.getZ() + (this.random.nextDouble() - 0.5) * 40.0;
                double ry = this.getY() - 50; // Try to spawn near ground level below the mark
                
                // Find ground
                net.minecraft.core.BlockPos spawnPos = new net.minecraft.core.BlockPos((int)rx, (int)ry, (int)rz);
                while (spawnPos.getY() > 0 && serverLevel.isEmptyBlock(spawnPos)) {
                    spawnPos = spawnPos.below();
                }
                spawnPos = spawnPos.above();

                EntityType<?>[] types = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER};
                EntityType<?> type = types[this.random.nextInt(types.length)];
                Entity monster = type.create(serverLevel);
                if (monster != null) {
                    monster.setPos(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
                    serverLevel.addFreshEntity(monster);
                    serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, monster.getX(), monster.getY(), monster.getZ(), 20, 0.5, 1.0, 0.5, 0.05);
                }
            }
        }
    }
}
