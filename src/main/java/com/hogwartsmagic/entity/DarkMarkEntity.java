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
    private static final double FULL_SCALE_TICKS = 80.0D;
    private static final double MAX_SCALE = 5.0D;

    public DarkMarkEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
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

    public float getVisualScale() {
        return this.entityData.get(DATA_VISUAL_SCALE);
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
        this.entityData.set(DATA_DURATION, duration - 1);

        if (this.level().isClientSide) {
            spawnParticles();
        } else {
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

    private void spawnParticles() {
        double growth = Math.min(1.0D, this.tickCount / FULL_SCALE_TICKS);
        double fade = Math.min(1.0D, this.entityData.get(DATA_DURATION) / 80.0D);
        double scale = MAX_SCALE * growth * Math.max(0.45D, fade);
        double time = this.tickCount * 0.075D;
        double pulse = 1.0D + Math.sin(time * 0.6D) * 0.05D;
        scale *= pulse;

        spawnAmbientCloud(scale, time);
        spawnSkullOutline(scale, time);
        spawnFacialFeatures(scale, time);
        spawnJawAndTeeth(scale, time);
        spawnSnake(scale, time);
        spawnAura(scale, time);
    }

    private void spawnAmbientCloud(double scale, double time) {
        for (int i = 0; i < 24; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double radius = (3.5D + this.random.nextDouble() * 3.0D) * scale;
            double x = this.getX() + Math.cos(angle) * radius;
            double y = this.getY() + (this.random.nextDouble() * 9.0D - 2.0D) * scale;
            double z = this.getZ() + Math.sin(angle) * radius;
            this.level().addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, 0.0D, 0.01D, 0.0D);
            if (i % 3 == 0) {
                this.level().addParticle(ParticleTypes.SQUID_INK, x, y + 0.4D * scale, z, 0.0D, 0.015D, 0.0D);
            }
        }
    }

    private void spawnSkullOutline(double scale, double time) {
        for (int ring = 0; ring < 16; ring++) {
            double v = ring / 15.0D;
            double y = this.getY() + (1.6D + v * 6.6D) * scale;
            double horizontal = Math.sin(v * Math.PI) * 4.2D * scale;
            double depth = Math.max(0.9D * scale, horizontal * 0.72D);
            for (int i = 0; i < 34; i++) {
                double angle = (Math.PI * 2.0D * i) / 34.0D;
                double x = this.getX() + Math.cos(angle) * horizontal;
                double z = this.getZ() + Math.sin(angle) * depth;
                this.level().addParticle(ParticleTypes.SQUID_INK, x, y, z, 0.0D, 0.004D, 0.0D);
                if ((i + ring) % 6 == 0) {
                    this.level().addParticle(ParticleTypes.SMOKE, x, y, z, 0.0D, 0.0D, 0.0D);
                }
            }
        }
    }

    private void spawnFacialFeatures(double scale, double time) {
        double eyeY = this.getY() + 5.1D * scale;
        spawnEye(-1.55D * scale, eyeY, scale, time, true);
        spawnEye(1.55D * scale, eyeY, scale, time, false);

        for (int row = 0; row < 8; row++) {
            double progress = row / 7.0D;
            double y = this.getY() + (3.8D - progress * 1.7D) * scale;
            double width = (0.12D + progress * 0.65D) * scale;
            this.level().addParticle(ParticleTypes.SMOKE, this.getX() - width, y, this.getZ(), 0.0D, 0.0D, 0.0D);
            this.level().addParticle(ParticleTypes.SMOKE, this.getX() + width, y, this.getZ(), 0.0D, 0.0D, 0.0D);
            if (row > 1) {
                this.level().addParticle(ParticleTypes.SQUID_INK, this.getX(), y - 0.05D * scale, this.getZ(), 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private void spawnEye(double centerX, double eyeY, double scale, double time, boolean left) {
        for (int i = 0; i < 18; i++) {
            double angle = (Math.PI * 2.0D * i) / 18.0D;
            double x = this.getX() + centerX + Math.cos(angle) * 0.95D * scale;
            double y = eyeY + Math.sin(angle) * 0.75D * scale;
            double z = this.getZ() + Math.sin(angle) * 0.35D * scale;
            this.level().addParticle(ParticleTypes.SMOKE, x, y, z, 0.0D, 0.0D, 0.0D);
        }

        double flicker = 0.12D + 0.08D * Math.sin(time * 1.4D + (left ? 0.0D : Math.PI));
        for (int i = 0; i < 3; i++) {
            this.level().addParticle(ParticleTypes.HAPPY_VILLAGER,
                    this.getX() + centerX + (this.random.nextDouble() - 0.5D) * 0.35D * scale,
                    eyeY + (this.random.nextDouble() - 0.5D) * 0.3D * scale,
                    this.getZ() + (this.random.nextDouble() - 0.5D) * 0.25D * scale,
                    0.0D, flicker, 0.0D);
        }
    }

    private void spawnJawAndTeeth(double scale, double time) {
        for (int i = 0; i <= 24; i++) {
            double progress = i / 24.0D;
            double angle = Math.PI + progress * Math.PI;
            double x = this.getX() + Math.cos(angle) * 3.5D * scale;
            double y = this.getY() + (1.2D - Math.sin(progress * Math.PI) * 2.35D) * scale;
            double z = this.getZ() + Math.sin(angle) * 2.0D * scale;
            this.level().addParticle(ParticleTypes.SQUID_INK, x, y, z, 0.0D, 0.003D, 0.0D);
            if (i > 3 && i < 21 && i % 3 == 0) {
                this.level().addParticle(ParticleTypes.SMOKE, x, y + 0.55D * scale, z, 0.0D, 0.0D, 0.0D);
            }
        }

        for (int tooth = -4; tooth <= 4; tooth++) {
            double x = this.getX() + tooth * 0.42D * scale;
            double topY = this.getY() + 1.05D * scale;
            for (int seg = 0; seg < 3; seg++) {
                double y = topY - seg * 0.42D * scale;
                this.level().addParticle(ParticleTypes.SQUID_INK, x, y, this.getZ() + 1.9D * scale, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private void spawnSnake(double scale, double time) {
        int segments = (int) (110 * Math.max(0.8D, scale / MAX_SCALE));
        for (int i = 0; i < segments; i++) {
            double progress = i / (double) segments;
            double coil = time + progress * 10.0D;
            double radius = (0.5D + (1.0D - progress) * 0.85D) * scale;
            double x = this.getX() + Math.sin(coil) * radius;
            double y = this.getY() + (0.5D - progress * 13.0D) * scale;
            double z = this.getZ() + 2.2D * scale + Math.cos(coil * 0.85D) * radius * 0.85D;

            this.level().addParticle(ParticleTypes.SQUID_INK, x, y, z, 0.0D, 0.0D, 0.0D);
            if (i % 2 == 0) {
                this.level().addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, 0.0D, 0.005D, 0.0D);
            }
            if (i % 10 == 0) {
                this.level().addParticle(ParticleTypes.HAPPY_VILLAGER, x, y, z, 0.0D, 0.02D, 0.0D);
            }
        }

        double headX = this.getX() + Math.sin(time + 10.0D) * 0.55D * scale;
        double headY = this.getY() - 12.2D * scale;
        double headZ = this.getZ() + 2.2D * scale + Math.cos((time + 10.0D) * 0.85D) * 0.45D * scale;
        for (int i = 0; i < 8; i++) {
            this.level().addParticle(ParticleTypes.SQUID_INK,
                    headX + (this.random.nextDouble() - 0.5D) * 0.4D * scale,
                    headY + (this.random.nextDouble() - 0.5D) * 0.25D * scale,
                    headZ + (this.random.nextDouble() - 0.5D) * 0.3D * scale,
                    0.0D, 0.0D, 0.0D);
        }
        this.level().addParticle(ParticleTypes.WITCH, headX, headY, headZ, 0.0D, 0.0D, 0.0D);
        this.level().addParticle(ParticleTypes.WITCH, headX + 0.22D * scale, headY - 0.05D * scale, headZ + 0.18D * scale, 0.0D, 0.0D, 0.0D);
        this.level().addParticle(ParticleTypes.WITCH, headX - 0.22D * scale, headY - 0.05D * scale, headZ + 0.18D * scale, 0.0D, 0.0D, 0.0D);
    }

    private void spawnAura(double scale, double time) {
        for (int i = 0; i < 12; i++) {
            double x = this.getX() + (this.random.nextDouble() - 0.5D) * 6.0D * scale;
            double y = this.getY() + this.random.nextDouble() * 8.5D * scale;
            double z = this.getZ() + (this.random.nextDouble() - 0.5D) * 4.8D * scale;
            this.level().addParticle(ParticleTypes.HAPPY_VILLAGER, x, y, z, 0.0D, 0.02D, 0.0D);
        }

        for (int i = 0; i < 10; i++) {
            double angle = time * 0.45D + (Math.PI * 2.0D * i) / 10.0D;
            double x = this.getX() + Math.cos(angle) * 2.6D * scale;
            double y = this.getY() + 4.0D * scale + Math.sin(angle * 1.3D) * 0.65D * scale;
            double z = this.getZ() + Math.sin(angle) * 2.0D * scale;
            this.level().addParticle(ParticleTypes.SMOKE, x, y, z, 0.0D, 0.01D, 0.0D);
        }
    }

    private void applyBuffs() {
        AABB area = new AABB(this.getX() - 50, this.getY() - 100, this.getZ() - 50, this.getX() + 50, this.getY() + 20, this.getZ() + 50);
        List<WizardEntity> wizards = this.level().getEntitiesOfClass(WizardEntity.class, area);
        for (WizardEntity wizard : wizards) {
            wizard.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 200, 1));
            wizard.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 200, 0));
        }
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
