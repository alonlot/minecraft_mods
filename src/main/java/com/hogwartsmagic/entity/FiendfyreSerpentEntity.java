package com.hogwartsmagic.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

public class FiendfyreSerpentEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_LIFE_TICKS = SynchedEntityData.defineId(FiendfyreSerpentEntity.class, EntityDataSerializers.INT);
    private static final String TAG_FIENDFYRE_CHANNELING = "hogwartsmagic:fiendfyre_channeling";
    private static final String TAG_FIENDFYRE_ADVANCE = "hogwartsmagic:fiendfyre_advance";
    private static final double CONTROL_DISTANCE = 6.2D;
    private static final double ADVANCE_BONUS_DISTANCE = 4.8D;
    private static final double MAX_STEP = 0.72D;
    private static final double ADVANCE_MAX_STEP = 1.08D;
    private static final double TURN_RESPONSE = 0.18D;
    private static final double ADVANCE_TURN_RESPONSE = 0.12D;
    private static final int TRAIL_LENGTH = 18;

    private UUID casterUuid;
    private final Deque<Vec3> trailPoints = new ArrayDeque<>();

    public FiendfyreSerpentEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_LIFE_TICKS, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setLifeTicks(tag.getInt("LifeTicks"));
        if (tag.hasUUID("Caster")) {
            this.casterUuid = tag.getUUID("Caster");
        }
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

    public void setCaster(ServerPlayer caster) {
        this.casterUuid = caster.getUUID();
    }

    @Override
    public void tick() {
        super.tick();

        int ticks = getLifeTicks() + 1;
        setLifeTicks(ticks);
        this.noPhysics = true;
        this.setNoGravity(true);

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ServerPlayer caster = getCaster(serverLevel);
        if (caster == null || !caster.isAlive() || !caster.getPersistentData().getBoolean(TAG_FIENDFYRE_CHANNELING)) {
            this.discard();
            return;
        }

        steerToCasterAim(caster, ticks);
        recordTrail();
        scorchNearby(caster);

        if (ticks % 2 == 0) {
            spreadFire();
        }
        spawnTrailParticles(serverLevel, ticks);
        spawnBurstParticles(serverLevel, ticks);
    }

    private ServerPlayer getCaster(ServerLevel level) {
        return this.casterUuid == null ? null : level.getServer().getPlayerList().getPlayer(this.casterUuid);
    }

    private void steerToCasterAim(ServerPlayer caster, int ticks) {
        boolean advancing = caster.getPersistentData().getBoolean(TAG_FIENDFYRE_ADVANCE);
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        if (look.lengthSqr() < 1.0E-6D) {
            look = new Vec3(0.0D, 0.0D, 1.0D);
        }

        Vec3 direction = look.normalize();
        double distanceScale = CONTROL_DISTANCE + (advancing ? ADVANCE_BONUS_DISTANCE : 0.0D);
        Vec3 desired = eye.add(direction.scale(distanceScale));
        desired = desired.add(0.0D, Math.sin(ticks * 0.3D) * (advancing ? 0.25D : 0.45D), 0.0D);

        Vec3 current = this.position();
        Vec3 desiredMovement = desired.subtract(current);
        double distance = desiredMovement.length();
        double maxStep = advancing ? ADVANCE_MAX_STEP : MAX_STEP;
        if (distance > maxStep) {
            desiredMovement = desiredMovement.normalize().scale(maxStep);
        }

        Vec3 currentVelocity = this.getDeltaMovement();
        double turnResponse = advancing ? ADVANCE_TURN_RESPONSE : TURN_RESPONSE;
        Vec3 movement = currentVelocity.scale(1.0D - turnResponse).add(desiredMovement.scale(turnResponse));
        if (movement.lengthSqr() > (maxStep * maxStep)) {
            movement = movement.normalize().scale(maxStep);
        }

        Vec3 next = current.add(movement);
        this.setPos(next.x, next.y, next.z);
        this.setDeltaMovement(movement);
        if (movement.horizontalDistanceSqr() > 1.0E-4D) {
            this.setYRot((float) (net.minecraft.util.Mth.atan2(movement.z, movement.x) * (180F / Math.PI)) - 90.0F);
        }
    }

    private void recordTrail() {
        this.trailPoints.addFirst(this.position());
        while (this.trailPoints.size() > TRAIL_LENGTH) {
            this.trailPoints.removeLast();
        }
    }

    private void scorchNearby(ServerPlayer caster) {
        AABB area = this.getBoundingBox().inflate(2.6D, 1.4D, 2.6D);
        for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class, area, entity -> entity.isAlive() && entity != caster)) {
            target.igniteForSeconds(5);
            target.hurt(target.damageSources().onFire(), 4.0f);
            Vec3 lift = target.position().subtract(this.position());
            if (lift.lengthSqr() > 1.0E-4D) {
                Vec3 push = lift.normalize().scale(0.32D);
                target.push(push.x, 0.12D, push.z);
            }
        }
    }

    private void spawnTrailParticles(ServerLevel serverLevel, int ticks) {
        Vec3 previous = null;
        int index = 0;
        for (Vec3 point : this.trailPoints) {
            serverLevel.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 3, 0.18D, 0.18D, 0.18D, 0.01D);
            if (index % 2 == 0) {
                serverLevel.sendParticles(ParticleTypes.LAVA, point.x, point.y, point.z, 1, 0.08D, 0.08D, 0.08D, 0.0D);
            }
            if (previous != null) {
                Vec3 segment = previous.subtract(point);
                int steps = Math.max(2, (int) Math.ceil(segment.length() * 5.0D));
                for (int i = 1; i < steps; i++) {
                    Vec3 mid = point.add(segment.scale(i / (double) steps));
                    serverLevel.sendParticles(ParticleTypes.SMALL_FLAME, mid.x, mid.y, mid.z, 1, 0.03D, 0.03D, 0.03D, 0.0D);
                }
            }
            previous = point;
            index++;
        }

        if (ticks % 3 == 0) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(), this.getZ(), 2, 0.2D, 0.25D, 0.2D, 0.01D);
        }
    }

    private void spawnBurstParticles(ServerLevel serverLevel, int ticks) {
        if (ticks % 4 != 0) {
            return;
        }
        for (int i = 0; i < 12; i++) {
            double angle = (Math.PI * 2.0D * i) / 12.0D + ticks * 0.08D;
            double radius = 1.4D + Math.sin(ticks * 0.22D + i) * 0.25D;
            double x = this.getX() + Math.cos(angle) * radius;
            double y = this.getY() + 0.2D + (i % 3) * 0.18D;
            double z = this.getZ() + Math.sin(angle) * radius;
            serverLevel.sendParticles(ParticleTypes.FLAME, x, y, z, 1, 0.04D, 0.04D, 0.04D, 0.01D);
            if (i % 2 == 0) {
                serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 1, 0.01D, 0.05D, 0.01D, 0.0D);
            }
        }
    }

    private void spreadFire() {
        BlockPos center = this.blockPosition();
        int radius = 2 + Math.min(5, getLifeTicks() / 20);

        for (int i = 0; i < 8; i++) {
            int rx = this.random.nextInt(radius * 2 + 1) - radius;
            int ry = this.random.nextInt(5) - 2;
            int rz = this.random.nextInt(radius * 2 + 1) - radius;
            BlockPos target = center.offset(rx, ry, rz);
            if (this.level().isEmptyBlock(target)) {
                this.level().setBlockAndUpdate(target, Blocks.FIRE.defaultBlockState());
            }
        }
    }
}
