package com.hogwartsmagic.entity;

import com.hogwartsmagic.fire.BreathAim;
import com.hogwartsmagic.fire.FireBreather;
import com.hogwartsmagic.fire.SerpentRoar;
import com.hogwartsmagic.pensieve.scene.DragonFire;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The Fiendfyre serpent, steered by its caster's aim. Its fire is drawn on the clients: the serpent itself
 * (client/FiendfyreSerpentRenderer), the dragon's billows and smoke it sheds, and the dragon's own jet it roars from its
 * jaws on the {@link SerpentRoar} beat (client/pensieve/DragonFireRenderer), along the synched aim the jet's damage is
 * cast on here.
 */
public class FiendfyreSerpentEntity extends Entity implements FireBreather {
    private static final EntityDataAccessor<Integer> DATA_LIFE_TICKS = SynchedEntityData.defineId(FiendfyreSerpentEntity.class, EntityDataSerializers.INT);
    /** The roar's aim (unit): the caster's look, so the jet carries on along it from the jaws. */
    private static final EntityDataAccessor<org.joml.Vector3f> DATA_BREATH_AIM =
            SynchedEntityData.defineId(FiendfyreSerpentEntity.class, EntityDataSerializers.VECTOR3);
    private static final String TAG_FIENDFYRE_CHANNELING = "hogwartsmagic:fiendfyre_channeling";
    private static final String TAG_FIENDFYRE_ADVANCE = "hogwartsmagic:fiendfyre_advance";
    private static final double CONTROL_DISTANCE = 6.2D;
    private static final double ADVANCE_BONUS_DISTANCE = 4.8D;
    private static final double MAX_STEP = 0.72D;
    private static final double ADVANCE_MAX_STEP = 1.08D;
    private static final double TURN_RESPONSE = 0.18D;
    private static final double ADVANCE_TURN_RESPONSE = 0.12D;
    /** How far in front of the serpent's position its jaws are (FiendfyreSerpentRenderer's lip, in blocks). */
    private static final double MOUTH_FORWARD = 1.8D;

    private UUID casterUuid;

    public FiendfyreSerpentEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_LIFE_TICKS, 0);
        builder.define(DATA_BREATH_AIM, new org.joml.Vector3f(BreathAim.DEFAULT));
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

    // ---- The roar: the dragon's fire from its jaws ------------------------------------------------------------------

    @Override
    public double[] breathAimSynced() {
        return BreathAim.decode(this.entityData.get(DATA_BREATH_AIM));
    }

    @Override
    public Vec3 mouthEstimate() {
        return this.position().add(heading().scale(MOUTH_FORWARD));
    }

    @Override
    public int breathAge() {
        return SerpentRoar.breathAge(getLifeTicks());
    }

    @Override
    public float breathDuration() {
        return SerpentRoar.BREATH;
    }

    @Override
    public float breathCharge() {
        return SerpentRoar.charge(getLifeTicks());
    }

    @Override
    public double fireScale() {
        return SerpentRoar.SCALE;
    }

    /** The way the serpent is moving (its head leads), else the way it faces. */
    private Vec3 heading() {
        Vec3 v = this.getDeltaMovement();
        if (v.lengthSqr() > 1.0E-4D) return v.normalize();
        float yaw = this.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
        return new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
    }

    /**
     * One tick of the roar's fire: the aim follows the caster's look; the jet reaches as far as the drawn puffs have
     * flown ({@link DragonFire#front}), stops at the first block, burns what is inside the width the clients draw and
     * sets fire where it lands.
     */
    private void roar(ServerLevel level, ServerPlayer caster) {
        Vec3 look = caster.getLookAngle();
        if (look.lengthSqr() > 1.0E-6D) {
            this.entityData.set(DATA_BREATH_AIM, BreathAim.encode(look));
        }
        int age = breathAge();
        if (age < 0) return;
        if (age == 0) {
            this.playSound(com.hogwartsmagic.ModSounds.HORNTAIL_FIRE_BREATH.get(), 3.0F, 0.9F);
        }
        double[] a = breathAimSynced();
        Vec3 dir = new Vec3(a[0], a[1], a[2]);
        Vec3 mouth = mouthEstimate();
        double reach = DragonFire.front(age + 1);
        HitResult hit = level.clip(new ClipContext(mouth, mouth.add(dir.scale(reach)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        boolean landed = hit.getType() != HitResult.Type.MISS;
        double jet = landed ? hit.getLocation().distanceTo(mouth) : reach;
        if (age % 8 == 2) {
            burnJet(level, caster, mouth, dir, jet);
        }
        if (landed && this.random.nextFloat() < 0.3F) {
            BlockPos at = BlockPos.containing(hit.getLocation().subtract(dir.scale(0.3D)));
            for (BlockPos p : new BlockPos[]{at, at.above(), at.below()}) {
                if (level.isEmptyBlock(p) && !level.isEmptyBlock(p.below())) {
                    level.setBlockAndUpdate(p, Blocks.FIRE.defaultBlockState());
                    break;
                }
            }
        }
    }

    /** Burns everything but the caster inside the drawn jet: along its axis, within its visible width there. */
    private void burnJet(ServerLevel level, ServerPlayer caster, Vec3 mouth, Vec3 dir, double jet) {
        double widest = DragonFire.visibleRadius(jet) * SerpentRoar.SCALE;
        AABB scan = new AABB(mouth, mouth.add(dir.scale(jet))).inflate(widest + 0.5D);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, scan,
                e -> e.isAlive() && e != caster)) {
            Vec3 to = victim.getBoundingBox().getCenter().subtract(mouth);
            double along = to.dot(dir);
            if (along < 0.0D || along > jet) continue;
            double perp = to.subtract(dir.scale(along)).length();
            if (perp > DragonFire.visibleRadius(along) * SerpentRoar.SCALE + victim.getBbWidth() * 0.5D) continue;
            victim.hurt(victim.damageSources().onFire(), 6.0f);
            victim.igniteForSeconds(6);
        }
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
        scorchNearby(caster);
        roar(serverLevel, caster);

        if (ticks % 2 == 0) {
            spreadFire();
        }
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
