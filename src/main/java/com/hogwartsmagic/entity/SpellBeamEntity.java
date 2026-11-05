package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.block.entity.CaveInimicumWardBlockEntity;
import com.hogwartsmagic.block.entity.BroomWorkbenchBlockEntity;
import com.hogwartsmagic.block.entity.HorcruxBlockEntity;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.sorting.HousePerks;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.polyjuice.PolyjuiceData;
import com.hogwartsmagic.util.HorcruxData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.network.PacketDistributor;
import java.util.UUID;

/**
 * Colored spell beam projectile. Each spell type has its own color and particles.
 */
public class SpellBeamEntity extends ThrowableProjectile {
    /**
     * The player possessing the wand user that fired this beam (Spell Mastery Phase 6b), or null. In memory only:
     * a beam lives 200 ticks and does not outlive a reload. It never hurts players that player may not harm.
     */
    @javax.annotation.Nullable
    private UUID puppeteer;

    private static final int CAVE_INIMICUM_REVELIO_BREAK_TICKS = 20 * 10;
    private static final int REVELIO_LIGHT_TICKS = 20 * 8;
    /** Most ores plus chests one Revelio cast highlights (a dense ore mass must not flood the server). */
    private static final int REVELIO_MAX_REVEALS = 384;
    /** Of those, how many also get particles; the rest get only their light. */
    private static final int REVELIO_MAX_PARTICLE_REVEALS = 128;
    private static final double MORSMORDRE_MIN_SKY_HEIGHT = 20.0D;
    private static final double MORSMORDRE_MARK_MIN_HEIGHT_ABOVE_GROUND = -3.0D;
    private static final double MORSMORDRE_MARK_BELOW_PEAK_OFFSET = 31.0D;
    private static final double ARRESTO_MOMENTUM_PROXIMITY_TRACK_RANGE = 10.0D;
    private static final double ARRESTO_MOMENTUM_DIRECTION_DOT_MIN = 0.35D;
    private static final double ARRESTO_MOMENTUM_MIN_TRACK_SPEED = 1.2D;
    private static final double ARRESTO_MOMENTUM_MAX_TRACK_SPEED = 3.8D;
    private static final double ARRESTO_MOMENTUM_LOCKED_ACCELERATION = 0.18D;
    private static final double ARRESTO_MOMENTUM_FALL_CATCHUP_BONUS = 0.45D;
    private static final double REVELIO_PROXIMITY_TRACK_RANGE = 12.0D;
    private static final double REVELIO_DIRECTION_DOT_MIN = 0.2D;
    private static final double REVELIO_MIN_TRACK_SPEED = 1.2D;
    private static final double REVELIO_MAX_TRACK_SPEED = 2.75D;
    private static final double REVELIO_LOCKED_ACCELERATION = 0.12D;
    private static final double AQUA_ERUCTO_MAX_RANGE = 80.0D;
    private static final double AQUA_ERUCTO_MAX_RANGE_SQR = AQUA_ERUCTO_MAX_RANGE * AQUA_ERUCTO_MAX_RANGE;

    private static final EntityDataAccessor<String> DATA_SPELL_ID = SynchedEntityData.defineId(SpellBeamEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_CHARGE = SynchedEntityData.defineId(SpellBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_ELDER_WAND = SynchedEntityData.defineId(SpellBeamEntity.class, EntityDataSerializers.BOOLEAN);
    private static final double NEAR_MISS_RADIUS = 1.9D;
    /** Two beam paths this close clash: the old touch test (0.25 boxes, one inflated by 0.45) reached 0.7 too. */
    private static final double CLASH_DISTANCE = 0.7D;
    /** Search margin around this beam's path: the clash distance plus the longest move a beam makes in a tick. */
    private static final double CLASH_SEARCH_REACH = 8.0D;
    private boolean nearMissWhooshPlayed = false;
    private double highestYReached = Double.NEGATIVE_INFINITY;
    private UUID homingTargetUuid;
    private double castOriginX = Double.NaN;
    private double castOriginY = Double.NaN;
    private double castOriginZ = Double.NaN;
    /** Sent back by a Protego parry: never reflected again, and its new owner earns no mastery XP from it. */
    private boolean reflected;
    /** Depulso: the owner's rank, read once on the first deflect tick (-1 = not read yet). Not persisted. */
    private int cachedDepulsoRank = -1;

    /**
     * Pensieve memories: a beam that exists only to be seen. It is created on a client level (never added on the
     * server), does no server work, never hits a player, and on its first hit, or when its life runs out, only
     * shows its impact particles and discards itself. Plain data: nothing client-only is referenced here.
     */
    private boolean visualOnly;
    /** The strike packet went out (a dodge sends its own); the impact that follows must not send another. */
    private boolean impactSent;
    /** Stopped by a shield: its fizzle ripples across the shield instead. */
    private boolean shieldBlocked;
    /**
     * Where the beam struck: on an entity, where its line enters the body (an entity hit reports the feet). The
     * strike is drawn here and the beam's drawn path ends here, so it never bends off to the side.
     */
    @javax.annotation.Nullable
    private Vec3 hitAt;
    /** Ticks a visual-only beam lives before it ends as if it had hit something. */
    private int visualLife = 60;

    /** Make this a visual-only beam (see {@link #visualOnly}). */
    public void setVisualOnly() {
        this.visualOnly = true;
    }

    /** Make this a visual-only beam that ends after {@code lifeTicks}. */
    public void setVisualOnly(int lifeTicks) {
        this.visualOnly = true;
        this.visualLife = Math.max(1, lifeTicks);
    }

    public boolean isVisualOnly() {
        return this.visualOnly;
    }

    /** A visual-only beam has no thrower, so its spell is set directly (a real beam sets it in its constructor). */
    public void setVisualSpell(SpellType spellType) {
        getEntityData().set(DATA_SPELL_ID, spellType.getId());
    }

    /** The impact of a visual-only beam: just gone. SpellBeamClientState plays the strike where it vanished. */
    private void endVisualBeam() {
        this.discard();
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (this.visualOnly && target instanceof net.minecraft.world.entity.player.Player) {
            return false; // a memory's beams never touch a viewer
        }
        return super.canHitEntity(target);
    }

    public int getCachedDepulsoRank() {
        return this.cachedDepulsoRank;
    }

    public void setCachedDepulsoRank(int rank) {
        this.cachedDepulsoRank = rank;
    }

    public SpellBeamEntity(EntityType<? extends SpellBeamEntity> type, Level level) {
        super(type, level);
    }

    public SpellBeamEntity(Level level, LivingEntity thrower, SpellType spellType) {
        super(ModEntities.SPELL_BEAM.get(), thrower, level);
        getEntityData().set(DATA_SPELL_ID, spellType.getId());
        rememberCastOrigin();
        this.puppeteer = com.hogwartsmagic.spell.mind.PossessionManager.castingPuppeteer; // set only inside a puppet cast
    }

    public SpellType getSpellType() {
        return SpellType.fromId(getEntityData().get(DATA_SPELL_ID));
    }

    public void setCharge(float charge) {
        this.getEntityData().set(DATA_CHARGE, Math.max(0.35f, Math.min(1.6f, charge)));
    }

    public float getCharge() {
        return this.getEntityData().get(DATA_CHARGE);
    }

    public void setElderWandCast(boolean elderWandCast) {
        this.getEntityData().set(DATA_ELDER_WAND, elderWandCast);
    }

    public boolean isElderWandCast() {
        return this.getEntityData().get(DATA_ELDER_WAND);
    }

    public void markReflected() {
        this.reflected = true;
    }

    public boolean isReflected() {
        return this.reflected;
    }

    public void setHomingTarget(Entity target) {
        this.homingTargetUuid = target != null ? target.getUUID() : null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SPELL_ID, SpellType.WINGARDIUM_LEVIOSA.getId());
        builder.define(DATA_CHARGE, 1.0f);
        builder.define(DATA_ELDER_WAND, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SpellId", getSpellType().getId());
        if (this.puppeteer != null) {
            tag.putUUID("Puppeteer", this.puppeteer);
        }
        tag.putFloat("Charge", getCharge());
        tag.putBoolean("ElderWandCast", isElderWandCast());
        tag.putBoolean("Reflected", this.reflected);
        tag.putDouble("HighestYReached", this.highestYReached);
        if (!Double.isNaN(this.castOriginX)) {
            tag.putDouble("CastOriginX", this.castOriginX);
            tag.putDouble("CastOriginY", this.castOriginY);
            tag.putDouble("CastOriginZ", this.castOriginZ);
        }
        if (this.homingTargetUuid != null) {
            tag.putUUID("HomingTarget", this.homingTargetUuid);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        getEntityData().set(DATA_SPELL_ID, tag.getString("SpellId"));
        this.puppeteer = tag.hasUUID("Puppeteer") ? tag.getUUID("Puppeteer") : null;
        if (tag.contains("Charge")) {
            setCharge(tag.getFloat("Charge"));
        }
        if (tag.contains("ElderWandCast")) {
            setElderWandCast(tag.getBoolean("ElderWandCast"));
        }
        this.reflected = tag.getBoolean("Reflected");
        if (tag.contains("HighestYReached")) {
            this.highestYReached = tag.getDouble("HighestYReached");
        }
        if (tag.contains("CastOriginX")) {
            this.castOriginX = tag.getDouble("CastOriginX");
            this.castOriginY = tag.getDouble("CastOriginY");
            this.castOriginZ = tag.getDouble("CastOriginZ");
        }
        if (tag.hasUUID("HomingTarget")) {
            this.homingTargetUuid = tag.getUUID("HomingTarget");
        }
    }

    @Override
    public void tick() {
        if (this.visualOnly) {
            if (!this.level().isClientSide || this.tickCount >= this.visualLife) {
                if (this.level().isClientSide) {
                    endVisualBeam();
                } else {
                    this.discard(); // never meant to exist on a server
                }
                return;
            }
        }
        if (Double.isNaN(this.castOriginX)) {
            rememberCastOrigin();
        }
        if (!this.level().isClientSide) {
        updateTrackedSpellHoming();
        }
        if (!this.level().isClientSide && getSpellType() == SpellType.DIFFINDO && tryCutNonCollisionBlock()) {
            return;
        }
        if (!this.level().isClientSide && getSpellType() == SpellType.GLACIUS && tryHitWaterSurface()) {
            return;
        }
        super.tick();

        if (this.highestYReached == Double.NEGATIVE_INFINITY) {
            this.highestYReached = this.getY();
        } else {
            this.highestYReached = Math.max(this.highestYReached, this.getY());
        }

        if (!this.level().isClientSide) {
            if (tryResolveBeamClash()) {
                return;
            }
            if (getSpellType() == SpellType.ACCIO && tryPullNearbyMandrakeWithAccio()) {
                this.discard();
                return;
            }
            if (getSpellType() == SpellType.DEPULSO) {
                com.hogwartsmagic.spell.support.DepulsoSpell.deflectProjectiles(this);
            }
            if (getSpellType() == SpellType.AQUA_ERUCTO && hasExceededAquaRange()) {
                this.discard();
                return;
            }
        }

        if (!this.level().isClientSide && this.tickCount > 200) {
            if (getSpellType() == SpellType.MORSMORDRE && tryStartMorsmordreSkyRaid()) {
                if (this.level() instanceof ServerLevel) {
                    com.hogwartsmagic.spell.SpellBeamFx.impact(this, SpellType.MORSMORDRE, this.position(), com.hogwartsmagic.spell.SpellBeamFx.HIT);
                    this.impactSent = true;
                    playLayeredImpactSound(SpellType.MORSMORDRE);
                }
            } else {
                spawnFizzleEffects();
            }
            this.discard();
            return;
        }

        SpellType spell = getSpellType();
        if (!this.level().isClientSide && spell == SpellType.AQUA_ERUCTO) {
            sculptAquaStream();
        }
        if (this.level().isClientSide) {
            // A memory's beam is scenery: it never whooshes past the viewer as if it were a real near miss.
            if (!this.visualOnly) playNearMissFeedback();
            // Every beam is drawn by hand on the client (SpellBeamFxRenderer, per-spell BeamStyles): no particle trail.
        }
    }

    private void updateTrackedSpellHoming() {
        SpellType spellType = getSpellType();
        if (spellType != SpellType.ARRESTO_MOMENTUM && spellType != SpellType.REVELIO) {
            return;
        }

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Entity target = this.homingTargetUuid != null ? serverLevel.getEntity(this.homingTargetUuid) : null;
        if (!(target instanceof LivingEntity livingTarget) || !livingTarget.isAlive() || target.isRemoved() || !isValidTrackedTarget(livingTarget, spellType)) {
            target = spellType == SpellType.REVELIO
                    ? findNearbyRevelioTarget(serverLevel)
                    : findNearbyArrestoMomentumTarget(serverLevel);
            if (target != null) {
                this.homingTargetUuid = target.getUUID();
            } else {
                this.homingTargetUuid = null;
                return;
            }
        }

        if (!(target instanceof LivingEntity livingTarget)) {
            this.homingTargetUuid = null;
            return;
        }

        if (target == getOwner()) {
            this.homingTargetUuid = null;
            return;
        }

        Vec3 toTarget = livingTarget.getBoundingBox().getCenter().subtract(this.position());
        double distanceSqr = toTarget.lengthSqr();
        if (distanceSqr < 1.0E-6D) {
            return;
        }

        Vec3 currentVelocity = this.getDeltaMovement();
        Vec3 targetVelocity = livingTarget.getDeltaMovement();
        double minTrackSpeed = spellType == SpellType.REVELIO ? REVELIO_MIN_TRACK_SPEED : ARRESTO_MOMENTUM_MIN_TRACK_SPEED;
        double maxTrackSpeed = spellType == SpellType.REVELIO ? REVELIO_MAX_TRACK_SPEED : ARRESTO_MOMENTUM_MAX_TRACK_SPEED;
        double lockedAcceleration = spellType == SpellType.REVELIO ? REVELIO_LOCKED_ACCELERATION : ARRESTO_MOMENTUM_LOCKED_ACCELERATION;
        double currentSpeed = Math.max(currentVelocity.length(), minTrackSpeed);
        double targetFallSpeed = Math.max(0.0D, -targetVelocity.y);
        double requiredCatchSpeed = minTrackSpeed;
        if (targetFallSpeed > 0.0D) {
            requiredCatchSpeed = Math.max(requiredCatchSpeed, targetFallSpeed + ARRESTO_MOMENTUM_FALL_CATCHUP_BONUS);
        }

        double boostedSpeed = currentSpeed + lockedAcceleration;
        double speed = Math.max(boostedSpeed, requiredCatchSpeed);
        speed = Math.min(speed, maxTrackSpeed);
        Vec3 desiredVelocity = toTarget.normalize().scale(speed);
        float steering = spellType == SpellType.REVELIO
                ? (distanceSqr > 64.0D ? 0.18F : 0.28F)
                : (distanceSqr > 64.0D ? 0.22F : 0.38F);
        Vec3 adjustedVelocity = currentVelocity.lerp(desiredVelocity, steering);
        if (adjustedVelocity.lengthSqr() < 1.0E-6D) {
            adjustedVelocity = desiredVelocity;
        } else {
            adjustedVelocity = adjustedVelocity.normalize().scale(speed);
        }

        this.setDeltaMovement(adjustedVelocity);
        this.hasImpulse = true;
        double horizontal = Math.sqrt(adjustedVelocity.x * adjustedVelocity.x + adjustedVelocity.z * adjustedVelocity.z);
        this.setYRot((float) (Mth.atan2(adjustedVelocity.x, adjustedVelocity.z) * (180.0F / (float) Math.PI)));
        this.setXRot((float) (Mth.atan2(adjustedVelocity.y, horizontal) * (180.0F / (float) Math.PI)));
        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
    }

    private LivingEntity findNearbyArrestoMomentumTarget(ServerLevel serverLevel) {
        Entity owner = getOwner();
        AABB searchBox = this.getBoundingBox().inflate(ARRESTO_MOMENTUM_PROXIMITY_TRACK_RANGE);
        Vec3 travel = this.getDeltaMovement();
        Vec3 forward = travel.lengthSqr() > 1.0E-6D ? travel.normalize() : this.getLookAngle().normalize();
        return serverLevel.getEntitiesOfClass(LivingEntity.class, searchBox, candidate ->
                        candidate.isAlive()
                                && candidate.isPickable()
                                && !candidate.isSpectator()
                                && candidate != owner
                                && isInArrestoMomentumTrackDirection(candidate, forward))
                .stream()
                .min((left, right) -> Double.compare(left.distanceToSqr(this), right.distanceToSqr(this)))
                .orElse(null);
    }

    private LivingEntity findNearbyRevelioTarget(ServerLevel serverLevel) {
        Entity owner = getOwner();
        AABB searchBox = this.getBoundingBox().inflate(REVELIO_PROXIMITY_TRACK_RANGE);
        Vec3 travel = this.getDeltaMovement();
        Vec3 forward = travel.lengthSqr() > 1.0E-6D ? travel.normalize() : this.getLookAngle().normalize();
        return serverLevel.getEntitiesOfClass(LivingEntity.class, searchBox, candidate ->
                        candidate.isAlive()
                                && candidate.isPickable()
                                && !candidate.isSpectator()
                                && candidate != owner
                                && isRevealableInvisibleTarget(candidate)
                                && isInTrackDirection(candidate, forward, REVELIO_DIRECTION_DOT_MIN))
                .stream()
                .min((left, right) -> Double.compare(left.distanceToSqr(this), right.distanceToSqr(this)))
                .orElse(null);
    }

    private boolean isInArrestoMomentumTrackDirection(LivingEntity candidate, Vec3 forward) {
        return isInTrackDirection(candidate, forward, ARRESTO_MOMENTUM_DIRECTION_DOT_MIN);
    }

    private boolean isInTrackDirection(LivingEntity candidate, Vec3 forward, double minDot) {
        Vec3 toCandidate = candidate.getBoundingBox().getCenter().subtract(this.position());
        if (toCandidate.lengthSqr() < 1.0E-6D) {
            return true;
        }
        return toCandidate.normalize().dot(forward) >= minDot;
    }

    private boolean isValidTrackedTarget(LivingEntity target, SpellType spellType) {
        return spellType != SpellType.REVELIO || isRevealableInvisibleTarget(target);
    }

    private boolean isRevealableInvisibleTarget(LivingEntity candidate) {
        return candidate.isInvisible() || candidate.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
    }

    private void rememberCastOrigin() {
        this.castOriginX = this.getX();
        this.castOriginY = this.getY();
        this.castOriginZ = this.getZ();
    }

    private boolean hasExceededAquaRange() {
        if (Double.isNaN(this.castOriginX)) {
            return false;
        }
        double dx = this.getX() - this.castOriginX;
        double dy = this.getY() - this.castOriginY;
        double dz = this.getZ() - this.castOriginZ;
        return (dx * dx) + (dy * dy) + (dz * dz) >= AQUA_ERUCTO_MAX_RANGE_SQR;
    }

    /**
     * Vanilla projectile hit detection only sees collision shapes, so grass, vines, webs, tripwire and
     * lichen would let a Diffindo beam pass straight through. Clip this tick's path against block
     * outlines and, when it first meets a cuttable block with no entity closer, resolve it as a block hit.
     */
    private boolean tryCutNonCollisionBlock() {
        Vec3 from = this.position();
        Vec3 to = from.add(this.getDeltaMovement());
        BlockHitResult clip = this.level().clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, this));
        if (clip.getType() != HitResult.Type.BLOCK
                || !this.level().getBlockState(clip.getBlockPos())
                        .is(com.hogwartsmagic.spell.damage.DamageSupport.DIFFINDO_CUTTABLE)) {
            return false;
        }
        EntityHitResult entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                this.level(), this, from, clip.getLocation(),
                this.getBoundingBox().expandTowards(this.getDeltaMovement()).inflate(1.0), this::canHitEntity);
        if (entityHit != null) {
            return false; // an entity is closer than the block: the normal path hits it
        }
        this.setPos(clip.getLocation());
        this.onHit(clip);
        return true;
    }

    /**
     * Beams fly through fluids (vanilla hit detection ignores them), so Glacius would never meet the water
     * its block hit freezes. Clip this tick's path against water sources and, when it first enters one
     * from above with no entity closer, resolve it as the Glacius block hit (frosted ice on that source).
     */
    private boolean tryHitWaterSurface() {
        Vec3 from = this.position();
        Vec3 to = from.add(this.getDeltaMovement());
        BlockHitResult clip = this.level().clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.SOURCE_ONLY, this));
        if (clip.getType() != HitResult.Type.BLOCK || clip.isInside()
                || !this.level().getBlockState(clip.getBlockPos()).is(Blocks.WATER)
                || !this.level().getBlockState(clip.getBlockPos().above()).isAir()) {
            return false; // missed, not the surface, or the beam started inside water (cast from below)
        }
        EntityHitResult entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                this.level(), this, from, clip.getLocation(),
                this.getBoundingBox().expandTowards(this.getDeltaMovement()).inflate(1.0), this::canHitEntity);
        if (entityHit != null) {
            return false; // an entity is closer than the water: the normal path hits it
        }
        this.setPos(clip.getLocation());
        this.onHit(clip);
        return true;
    }

    /** Mark this beam as fired through a possessed body by {@code caster}; reusable by any puppet cast. */
    public void setPuppeteer(@javax.annotation.Nullable UUID caster) {
        this.puppeteer = caster;
    }

    @Override
    protected void onHit(HitResult result) {
        if (this.visualOnly) {
            if (this.level().isClientSide) {
                endVisualBeam();
            } else {
                this.discard();
            }
            return;
        }
        if (this.puppeteer == null || this.level().isClientSide) {
            onHitInner(result);
            return;
        }
        // A puppet beam resolves under its puppeteer's context, so splash and chain never pick a player they may not harm.
        UUID previous = com.hogwartsmagic.spell.mind.MindControl.swapHitPuppeteer(this.puppeteer);
        try {
            onHitInner(result);
        } finally {
            com.hogwartsmagic.spell.mind.MindControl.swapHitPuppeteer(previous);
        }
    }

    private void onHitInner(HitResult result) {
        this.hitAt = result instanceof EntityHitResult entityHit
                ? com.hogwartsmagic.spell.SpellBeamFx.entryPoint(this.position(), this.getDeltaMovement(), entityHit.getEntity())
                : result.getLocation();
        if (!this.level().isClientSide && this.puppeteer != null && result instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof net.minecraft.world.entity.player.Player hitPlayer
                && this.level() instanceof ServerLevel puppetLevel
                && com.hogwartsmagic.spell.mind.MindControl.puppeteerSpares(puppetLevel, this.puppeteer, hitPlayer)) {
            this.discard(); // a puppet beam never touches a player its caster may not harm
            return;
        }
        if (!this.level().isClientSide && this.puppeteer != null && result instanceof EntityHitResult thrallHit
                && !(thrallHit.getEntity() instanceof net.minecraft.world.entity.player.Player)
                && com.hogwartsmagic.spell.mind.MindControl.sparesThrallOrAlly(this.puppeteer, thrallHit.getEntity())) {
            this.discard(); // nor its puppeteer's own thralls and allies
            return;
        }
        super.onHit(result);
        if (!this.level().isClientSide) {
            SpellType spell = getSpellType();
            boolean hitSuccessful = false;
            boolean masteryEligible = true;
            LivingEntity masteryTarget = null;

            if (result instanceof BlockHitResult blockHit) {
                BlockPos pos = blockHit.getBlockPos();
                BlockState state = this.level().getBlockState(pos);
                if (spell == SpellType.ACCIO && state.is(ExampleMod.MANDRAKE_PLANT.get())) {
                    hitSuccessful = pullMandrakeWithAccio(pos);
                } else if (spell == SpellType.SERPENSORTIA
                        && state.getBlock() instanceof com.hogwartsmagic.block.ChamberDoorBlock) {
                    // Parseltongue, as far as this mod has one. Serpensortia is the snake spell, and
                    // the Chamber door is the one lock in the game that answers to being spoken to
                    // rather than to being opened.
                    hitSuccessful = this.level() instanceof ServerLevel serverLevel
                            && com.hogwartsmagic.block.ChamberDoorBlock.speakTo(serverLevel, pos);
                } else if (spell == SpellType.SERPENSORTIA) {
                    // Snakes rise from the face the beam struck and wait at the caster's side for a fight.
                    if (this.level() instanceof ServerLevel serpentLevel) {
                        hitSuccessful = com.hogwartsmagic.spell.summon.SerpensortiaSpell.conjureAt(serpentLevel,
                                Vec3.atBottomCenterOf(pos.relative(blockHit.getDirection())), null, getOwner()) > 0;
                    }
                } else if (spell == SpellType.ACCIO) {
                    // Summons loose items near the impact; from Expert, the first stack of a container
                    // the caster could open. (A mandrake is handled by the branch above.)
                    if (this.level() instanceof ServerLevel accioLevel) {
                        com.hogwartsmagic.spell.support.AccioSpell.BlockResult accio =
                                com.hogwartsmagic.spell.support.AccioSpell.onBlockHit(
                                        accioLevel, pos, blockHit.getDirection(), this.position(), getOwner(), this.reflected);
                        hitSuccessful = accio.pulled();
                        masteryEligible = accio.paysMastery(); // XP once per item, never for a reflected beam
                    }
                } else if (spell == SpellType.ALOHOMORA) {
                    // Doors, trapdoors and gates; iron from Adept; all around from Expert; at Master a
                    // Gringotts vault door or a hostile Ward. See AlohomoraSpell.
                    if (this.level() instanceof ServerLevel alohomoraLevel) {
                        hitSuccessful = com.hogwartsmagic.spell.support.AlohomoraSpell.onBlockHit(
                                alohomoraLevel, pos, state, this.position(), getOwner());
                    }
                } else if (spell == SpellType.GLACIUS) {
                    // Frosted ice on still water only (it melts on its own), through TerrainRules:
                    // wards, spawn protection, claims and (for NPC casters) mobGriefing.
                    if (this.level() instanceof ServerLevel glaciusLevel
                            && state.is(Blocks.WATER) && state.getFluidState().isSource()
                            && com.hogwartsmagic.spell.TerrainRules.canPlace(glaciusLevel, pos, getOwner())) {
                        glaciusLevel.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
                        glaciusLevel.scheduleTick(pos, Blocks.FROSTED_ICE,
                                net.minecraft.util.Mth.nextInt(glaciusLevel.getRandom(), 60, 120));
                        hitSuccessful = true;
                    }
                } else if (spell == SpellType.CONFRINGO) {
                    // Confringo is the combat blast: it burns creatures, never terrain. Bombarda is the one that breaks blocks.
                    if (this.level() instanceof ServerLevel confringoLevel) {
                        com.hogwartsmagic.spell.damage.ConfringoSpell.detonateAt(confringoLevel, this.position(), getOwner());
                    }
                    hitSuccessful = true;
                } else if (spell == SpellType.INCENDIO) {
                    // Campfires, candles, TNT, furnaces, flint-and-steel fire; a fire burst from Adept.
                    if (this.level() instanceof ServerLevel incendioLevel) {
                        hitSuccessful = com.hogwartsmagic.spell.damage.IncendioSpell.onBlockHit(
                                incendioLevel, pos, blockHit.getDirection(), getOwner());
                    }
                } else if (spell == SpellType.BOMBARDA) {
                    // The terrain breaker: blast now, blocks over the next few ticks (BombardaBlasts).
                    if (this.level() instanceof ServerLevel bombardaLevel) {
                        com.hogwartsmagic.spell.damage.BombardaSpell.detonateAtBlock(
                                bombardaLevel, pos, this.position(), getOwner(), !this.reflected);
                        hitSuccessful = true;
                        masteryEligible = false; // block XP is granted by BombardaBlasts once a block actually breaks
                    }
                } else if (spell == SpellType.DIFFINDO) {
                    // The magic shears: leaves, vines, webs, wool and grass come free as items.
                    if (this.level() instanceof ServerLevel diffindoLevel) {
                        hitSuccessful = com.hogwartsmagic.spell.damage.DiffindoSpell.cutBlocks(diffindoLevel, pos, getOwner()) > 0;
                    }
                } else if (spell == SpellType.REVELIO) {
                    if (state.getBlock() instanceof com.hogwartsmagic.block.DiagonArchwayBlock
                            && this.level() instanceof ServerLevel archLevel) {
                        // Revelio arms the wall too. The books never say which charm opens it, and a
                        // player who reaches for revealing rather than unlocking has not guessed wrong.
                        hitSuccessful = com.hogwartsmagic.block.DiagonArchwayBlock.enchant(archLevel, pos,
                                getOwner() instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null);
                    }
                    if (this.level() instanceof ServerLevel sl) {
                        net.minecraft.world.level.block.entity.BlockEntity be = this.level().getBlockEntity(pos);
                        if (state.is(ExampleMod.WARD.get()) && be instanceof CaveInimicumWardBlockEntity ward) {
                            LivingEntity caster = getOwner() instanceof LivingEntity livingCaster ? livingCaster : null;
                            if (ward.prepareRevelio(caster)) {
                                com.hogwartsmagic.spell.SpellBeamFx.effectAt(sl, spell, Vec3.atCenterOf(pos).add(0.0, 0.3, 0.0), com.hogwartsmagic.spell.SpellBeamFx.GLOW);
                                this.level().playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.65f, 1.55f);
                                hitSuccessful = true;
                            }
                        }
                    }
                    if (this.level() instanceof ServerLevel serverLevel
                            && com.hogwartsmagic.caveinimicum.CaveInimicumManager.get(serverLevel)
                            .disruptWardsAt(pos, CAVE_INIMICUM_REVELIO_BREAK_TICKS)) {
                        com.hogwartsmagic.spell.SpellBeamFx.effectAt(serverLevel, spell, Vec3.atCenterOf(pos), com.hogwartsmagic.spell.SpellBeamFx.HIT);
                        this.level().playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.85f, 1.4f);
                        hitSuccessful = true;
                    }
                    if (this.level() instanceof ServerLevel sl) {
                        int revelioRank = com.hogwartsmagic.spell.disable.DisableSupport.casterRank(getOwner(), SpellType.REVELIO);
                        revealNearbySecrets(sl, pos, revelioRank);
                        com.hogwartsmagic.spell.support.RevelioSpell.revealHidden(sl, this.position(), getOwner());
                        // A Colloportus seal over this lock is shown (any rank) instead of how the lock opens.
                        boolean sealShown = getOwner() instanceof ServerPlayer sealViewer
                                && com.hogwartsmagic.spell.mind.MindControl.hitPuppeteer() == null
                                && com.hogwartsmagic.spell.support.seal.ColloportusSpell.revealSeals(sl, pos, sealViewer);
                        if (!sealShown && getOwner() instanceof ServerPlayer revealer
                                && com.hogwartsmagic.spell.support.lock.LockRules.readsLocks(revelioRank)
                                && com.hogwartsmagic.spell.mind.MindControl.hitPuppeteer() == null) {
                            // From Expert: show how the piston door here opens (see LockHints).
                            com.hogwartsmagic.spell.support.lock.PistonLockSpell.begin(sl, pos, revealer, revelioRank,
                                    com.hogwartsmagic.spell.support.lock.PistonLockSpell.Use.REVEAL);
                        }
                    }
                    hitSuccessful = true;
                } else if (spell == SpellType.AQUA_ERUCTO) {
                    applyAquaWaveAt(this.position(), getOwner());
                    hitSuccessful = true;
                } else if (spell == SpellType.MORSMORDRE) {
                    if (this.level() instanceof ServerLevel sl
                            && state.is(ExampleMod.WARD.get())
                            && getOwner() instanceof LivingEntity livingCaster
                            && VoldemortSummoningEntity.tryStart(sl, pos, livingCaster)) {
                        com.hogwartsmagic.spell.SpellBeamFx.effectAt(sl, spell, Vec3.atCenterOf(pos).add(0.0, 0.5, 0.0), com.hogwartsmagic.spell.SpellBeamFx.EVADE);
                        this.level().playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.6f, 0.55f);
                        hitSuccessful = true;
                    } else {
                        hitSuccessful = false;
                    }
                } else if (spell == SpellType.FIENDFYRE) {
                    // Spawn Fiendfyre management entity
                    FiendfyreEntity fiendfyre = new FiendfyreEntity(ModEntities.FIENDFYRE.get(), this.level());
                    fiendfyre.moveTo(this.getX(), this.getY(), this.getZ());
                    fiendfyre.setCaster(getOwner());
                    this.level().addFreshEntity(fiendfyre);
                    hitSuccessful = true;
                } else if (spell == SpellType.CAVE_INIMICUM) {
                    if (this.level() instanceof ServerLevel sl) {
                        net.minecraft.world.level.block.entity.BlockEntity be = this.level().getBlockEntity(pos);
                        if (state.is(ExampleMod.WARD.get()) && be instanceof CaveInimicumWardBlockEntity ward) {
                            LivingEntity caster = getOwner() instanceof LivingEntity livingCaster ? livingCaster : null;
                            if (ward.prepareCaveInimicum(caster)) {
                                hitSuccessful = true;
                            }
                        }
                    }
                } else if (spell == SpellType.PROTEGO_MAXIMA) {
                    if (this.level() instanceof ServerLevel sl) {
                        net.minecraft.world.level.block.entity.BlockEntity be = this.level().getBlockEntity(pos);
                        if (state.is(ExampleMod.WARD.get()) && be instanceof CaveInimicumWardBlockEntity ward) {
                            LivingEntity caster = getOwner() instanceof LivingEntity livingCaster ? livingCaster : null;
                            if (ward.prepareProtegoMaxima(caster)) {
                                com.hogwartsmagic.spell.SpellBeamFx.effectAt(sl, spell, Vec3.atCenterOf(pos).add(0.0, 0.3, 0.0), com.hogwartsmagic.spell.SpellBeamFx.GLOW);
                                this.level().playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.75f, 1.35f);
                                hitSuccessful = true;
                            }
                        }
                    }
                } else if (spell == SpellType.ALATUS) {
                    if (this.level() instanceof ServerLevel sl) {
                        net.minecraft.world.level.block.entity.BlockEntity be = this.level().getBlockEntity(pos);
                        if (state.is(ExampleMod.BROOM_WORKBENCH.get()) && be instanceof BroomWorkbenchBlockEntity bbe) {
                            double hx = pos.getX() + 0.5;
                            double hy = pos.getY() + 0.65;
                            double hz = pos.getZ() + 0.5;
                            if (bbe.beginInfusionFromSpell(sl, getOwner())) {
                                hitSuccessful = true;
                            } else {
                                com.hogwartsmagic.spell.SpellBeamFx.effectAt(sl, spell, new Vec3(hx, hy, hz), com.hogwartsmagic.spell.SpellBeamFx.FIZZLE);
                                this.level().playSound(null, hx, hy, hz, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.22f, 0.55f);
                                hitSuccessful = true;
                                masteryEligible = false; // failed infusion earns no mastery
                            }
                        }
                    }
                } else if (spell == SpellType.LUMOS) {
                    // Lumos Maxima (Expert and up): a floating light orb where the beam lands.
                    if (this.level() instanceof ServerLevel lumosLevel) {
                        hitSuccessful = com.hogwartsmagic.spell.support.LumosSpell.placeOrb(
                                lumosLevel, pos.relative(blockHit.getDirection()), getOwner());
                    }
                } else if (spell == SpellType.DEPULSO) {
                    // Expert and up: the banishing blast still throws back enemies around the impact.
                    if (this.level() instanceof ServerLevel depulsoLevel) {
                        hitSuccessful = com.hogwartsmagic.spell.support.DepulsoSpell.onBlockHit(
                                depulsoLevel, this.position(), getOwner(), this.reflected);
                    }
                } else if (spell == SpellType.REPARO) {
                    if (this.level() instanceof ServerLevel sl) {
                        hitSuccessful = com.hogwartsmagic.util.ReparoManager.castReparo(sl,
                                getOwner() instanceof LivingEntity livingCaster ? livingCaster : null,
                                pos);
                    }
                } else if (spell == SpellType.WINGARDIUM_LEVIOSA) {
                    // Expert and up: the struck block rises into a carried block the channel can hold.
                    if (this.level() instanceof ServerLevel wingardiumLevel) {
                        hitSuccessful = com.hogwartsmagic.spell.motion.WingardiumSpell.liftBlock(wingardiumLevel, pos, getOwner());
                    }
                } else if (spell == SpellType.ARRESTO_MOMENTUM) {
                    // Expert and up: projectiles around the impact stop in midair (nothing else happens to a block).
                    if (this.level() instanceof ServerLevel arrestoLevel) {
                        hitSuccessful = com.hogwartsmagic.spell.motion.ArrestoFreeze.freezeAround(arrestoLevel, this.position(), getOwner()) > 0;
                    }
                } else if (spell == SpellType.PIERTOTUM_LOCOMOTOR) {
                    if (this.level() instanceof ServerLevel sl) {
                        // Owner narrowed to ServerPlayer, not LivingEntity: the statue is bound to a
                        // player and LivingStatueEntity.bindTo takes one, so a mob caster has nobody
                        // to bind to and the rite is a no-op for it.
                        hitSuccessful = com.hogwartsmagic.util.StatueMultiblock.tryRaise(sl, pos,
                                getOwner() instanceof ServerPlayer caster ? caster : null);
                    }
                } else if (spell == SpellType.COLLOPORTUS) {
                    // Seals the lock here against Alohomora; inside the caster's own seal it lifts it instead (no XP).
                    if (this.level() instanceof ServerLevel sealLevel) {
                        com.hogwartsmagic.spell.support.seal.ColloportusSpell.Cast cast =
                                com.hogwartsmagic.spell.support.seal.ColloportusSpell.onBlockHit(
                                        sealLevel, pos, state, getOwner(), this.reflected);
                        hitSuccessful = cast != com.hogwartsmagic.spell.support.seal.ColloportusSpell.Cast.NOTHING;
                        masteryEligible = cast == com.hogwartsmagic.spell.support.seal.ColloportusSpell.Cast.SEALED;
                    }
                }
            } else if (result instanceof EntityHitResult entityHit && livingBehind(entityHit.getEntity()) instanceof LivingEntity living) {
                // A player's ranked Protego first: parry window (dissolve or reflect), or the shield breaks.
                com.hogwartsmagic.spell.support.ProtegoSpell.BeamOutcome parry =
                        com.hogwartsmagic.spell.support.ProtegoSpell.onBeamHit(living, this);
                if (parry != com.hogwartsmagic.spell.support.ProtegoSpell.BeamOutcome.NOT_SHIELDED) {
                    hitSuccessful = false; // stopped at the shield; a reflected copy is already in flight
                    this.shieldBlocked = true;
                } else if (!com.hogwartsmagic.spell.support.ProtegoSpell.isRankedShield(living)
                        && com.hogwartsmagic.spell.support.ProtegoShield.has(living) && spell != SpellType.AVADA_KEDAVRA && spell != SpellType.HEAL
                        && spell != SpellType.LUMOS) {
                    // NPC layer shields and Protego Maxima keep the old rule: the shield eats the beam.
                    this.level().playSound(null, living.getX(), living.getY(), living.getZ(),
                            net.minecraft.sounds.SoundEvents.SHIELD_BLOCK, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.5f);
                    if (com.hogwartsmagic.spell.support.ProtegoShield.absorbHit(living)) {
                        this.level().playSound(null, living.getX(), living.getY(), living.getZ(),
                                net.minecraft.sounds.SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.0f);
                    }
                    // Treat the hit as blocked/fizzled
                    hitSuccessful = false;
                    this.shieldBlocked = true;
                } else {
                    // Normal hit
                    if (spell != SpellType.ALOHOMORA) {
                        // Both of these only ever act on a block. Fizzling rather than falling through
                        // to applyHitEffect keeps a living target from eating a "successful" impact
                        // burst for a spell that did nothing to it.
                        // Reparo and Alatus are block-only too: on a creature they did nothing yet paid hit XP.
                        if (spell == SpellType.MORSMORDRE || spell == SpellType.PIERTOTUM_LOCOMOTOR
                                || spell == SpellType.COLLOPORTUS || spell == SpellType.REPARO
                                || spell == SpellType.ALATUS) {
                            hitSuccessful = false;
                        } else if (spell == SpellType.AVADA_KEDAVRA && living instanceof VoldemortEntity voldemort && voldemort.tryEvadeAvada(getOwner())) {
                            com.hogwartsmagic.spell.SpellBeamFx.impact(this, spell, com.hogwartsmagic.spell.SpellBeamFx.bodyCentre(living), com.hogwartsmagic.spell.SpellBeamFx.EVADE);
                            this.impactSent = true;
                            hitSuccessful = true;
                        } else {
                            hitSuccessful = true;
                            masteryTarget = living;
                            boolean chipLanded = true;
                            net.minecraft.world.damagesource.DamageSource chipSource = null;
                            float chipDamage = 0.0f;

                            // Apply nominal magic damage FIRST to trigger standard HURT events for physical shield shattering algorithms if needed
                            // AVADA deals max damage so it doesn't need nominal damage
                            boolean shearableSheep = spell == SpellType.DIFFINDO
                                    && living instanceof net.minecraft.world.entity.animal.Sheep woolly && woolly.readyForShearing();
                            if (spell != SpellType.HEAL && spell != SpellType.AVADA_KEDAVRA && spell != SpellType.PROTEGO
                                    && spell != SpellType.LUMOS && spell != SpellType.RIDDIKULUS && !shearableSheep) {
                               chipDamage = 0.1f * HousePerks.getSpellDamageMultiplier(getOwner());
                               chipSource = this.damageSources().magic();
                               chipLanded = living.hurt(chipSource, chipDamage);
                               // hurt() is also false inside the victim's i-frames: that is not a veto.
                               if (!chipLanded && spell == SpellType.ACCIO && living.invulnerableTime > 10
                                       && !com.hogwartsmagic.spell.support.AccioSpell.attackVetoed(living, chipSource, chipDamage)) {
                                   chipLanded = true;
                               }
                            }

                            if (spell == SpellType.ACCIO) {
                                // A creature too heavy for the caster is refused: no impact burst, no mastery.
                                // A vetoed chip hit (claim or safe-zone mod) also means no snatch.
                                if (!com.hogwartsmagic.spell.support.AccioSpell.onLivingHit(
                                        living, getOwner(), !this.reflected && chipLanded)) {
                                    hitSuccessful = false;
                                    masteryTarget = null;
                                }
                            } else {
                                // Judged before the hit: applying it must not change whether the target counts as controlled.
                                boolean crucioResisted = spell == SpellType.CRUCIO
                                        && com.hogwartsmagic.spell.mind.MindControl.isControlled(living);
                                spell.applyHitEffect(living, getOwner(), this.getDeltaMovement(), !this.reflected);
                                com.hogwartsmagic.spell.SpellBeamFx.react(living, spell, this.getDeltaMovement(),
                                        com.hogwartsmagic.spell.SpellBeamFx.REACT_HIT);
                                if (crucioResisted) {
                                    masteryTarget = null; // a controlled target resists Crucio: no XP
                                }
                            }
                            if (spell == SpellType.WINGARDIUM_LEVIOSA && getOwner() instanceof ServerPlayer caster) {
                                com.hogwartsmagic.event.ModEvents.markRecentWingardiumTarget(caster, living);
                                com.hogwartsmagic.spell.motion.WingardiumSpell.noteLivingHit(caster, living);
                            }
                            if (spell == SpellType.REVELIO) {
                                com.hogwartsmagic.event.ModEvents.dispelIllusionsAndInvisibility(living);
                                if (this.level() instanceof ServerLevel revelioLevel) {
                                    com.hogwartsmagic.spell.support.RevelioSpell.revealHidden(revelioLevel, living.position(), getOwner());
                                }
                            }
                            if (spell == SpellType.AQUA_ERUCTO) {
                                applyAquaWaveAt(living.position(), getOwner());
                            } else if (spell == SpellType.AVADA_KEDAVRA) {
                                tryPrimeHorcruxFromAvada(living);
                                // Dark Magic: killing an innocent stains the soul (+3).
                                com.hogwartsmagic.spell.dark.DarkDeeds.onAvadaHit(living, darkDeedPayer());
                            }
                        }
                    }
                }
            }

            if (!hitSuccessful) {
                spawnFizzleEffects();
            } else {
                if (getOwner() instanceof ServerPlayer masteryCaster && !this.reflected && this.puppeteer == null) {
                    if (masteryTarget != null) {
                        com.hogwartsmagic.spell.mastery.MasteryAwards.onLivingHit(masteryCaster, spell, masteryTarget);
                    } else if (result instanceof BlockHitResult && masteryEligible) {
                        com.hogwartsmagic.spell.mastery.MasteryAwards.onBlockEffect(masteryCaster, spell);
                    }
                }
                if (!this.impactSent) {
                    com.hogwartsmagic.spell.SpellBeamFx.impact(this, spell, this.hitAt != null ? this.hitAt : this.position(), com.hogwartsmagic.spell.SpellBeamFx.HIT);
                }
                if (this.level() instanceof net.minecraft.server.level.ServerLevel) {
                    playLayeredImpactSound(spell);
                    if (spell == SpellType.BOMBARDA || spell == SpellType.CONFRINGO || spell == SpellType.FIENDFYRE) {
                        applyHeavyImpactImpulse();
                    }
                }
            }
            this.discard();
        }
    }

    /**
     * The living thing standing behind a hit box.
     *
     * <p>A mob too long or too tall for one box — the basilisk — is covered by
     * {@link PartEntity} hit volumes instead, and a part is a plain entity, not
     * a {@link LivingEntity}. A beam that asked the struck entity directly found
     * no living target, fell through the whole hit branch and was discarded a
     * few lines later: every spell aimed at a basilisk simply vanished. Asking
     * the part who it belongs to puts the spell back on the animal.
     *
     * <p>This hands the parent over rather than the part, so the part's own
     * damage multiplier is skipped: the payload is delivered by
     * {@code SpellType.applyHitEffect}, which needs a LivingEntity and cannot be
     * pointed at a hit box. Aiming at the head still counts for something —
     * the basilisk credits blows landed from in front toward its blinding
     * whether they arrived through a part or not.
     */
    private static Entity livingBehind(Entity struck) {
        return struck instanceof PartEntity<?> part ? part.getParent() : struck;
    }

    private void revealNearbySecrets(ServerLevel level, BlockPos origin, int rank) {
        int oreRadius = com.hogwartsmagic.spell.support.RevelioRules.oreRadius(rank);
        int chestRadius = com.hogwartsmagic.spell.support.RevelioRules.chestRadius(rank);
        int oreSq = oreRadius * oreRadius;
        int chestSq = chestRadius * chestRadius;
        int minY = Math.max(level.getMinBuildHeight(), origin.getY() - oreRadius);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, origin.getY() + oreRadius);
        int revealCount = 0;
        java.util.function.Predicate<BlockState> interesting = st -> isRevealableOre(st) || isRevealableChest(st);
        // Walk loaded chunks section by section: an unloaded chunk is never loaded for a highlight, and a
        // section whose palette holds no ore or chest (all air, or plain stone) is skipped without a block read.
        scan:
        for (int cx = (origin.getX() - oreRadius) >> 4; cx <= (origin.getX() + oreRadius) >> 4; cx++) {
            for (int cz = (origin.getZ() - oreRadius) >> 4; cz <= (origin.getZ() + oreRadius) >> 4; cz++) {
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                    int sectionIndex = chunk.getSectionIndexFromSectionY(sy);
                    if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) {
                        continue;
                    }
                    net.minecraft.world.level.chunk.LevelChunkSection section = chunk.getSection(sectionIndex);
                    if (section.hasOnlyAir() || !section.maybeHas(interesting)) {
                        continue;
                    }
                    int x0 = Math.max(cx << 4, origin.getX() - oreRadius);
                    int x1 = Math.min((cx << 4) + 15, origin.getX() + oreRadius);
                    int z0 = Math.max(cz << 4, origin.getZ() - oreRadius);
                    int z1 = Math.min((cz << 4) + 15, origin.getZ() + oreRadius);
                    int y0 = Math.max(sy << 4, minY);
                    int y1 = Math.min((sy << 4) + 15, maxY);
                    for (int x = x0; x <= x1; x++) {
                        int dx = x - origin.getX();
                        for (int y = y0; y <= y1; y++) {
                            int dy = y - origin.getY();
                            for (int z = z0; z <= z1; z++) {
                                int dz = z - origin.getZ();
                                int distSq = dx * dx + dy * dy + dz * dz;
                                if (distSq > oreSq) {
                                    continue;
                                }
                                BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                                if (isRevealableOre(state)) {
                                    revealOre(level, origin, new BlockPos(x, y, z), revealCount < REVELIO_MAX_PARTICLE_REVEALS);
                                    revealCount++;
                                } else if (distSq <= chestSq && isRevealableChest(state)) {
                                    revealChest(level, origin, new BlockPos(x, y, z), revealCount < REVELIO_MAX_PARTICLE_REVEALS);
                                    revealCount++;
                                } else {
                                    continue;
                                }
                                if (revealCount >= REVELIO_MAX_REVEALS) {
                                    break scan;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void revealOre(ServerLevel level, BlockPos origin, BlockPos orePos, boolean particles) {
        double x = orePos.getX() + 0.5D;
        double y = orePos.getY() + 0.5D;
        double z = orePos.getZ() + 0.5D;
        if (particles) {
            com.hogwartsmagic.spell.SpellBeamFx.effectAt(level, SpellType.REVELIO, new Vec3(x, y, z), com.hogwartsmagic.spell.SpellBeamFx.GLOW);
        }

        BlockPos lightPos = findNearbyAirForReveal(level, orePos, origin);
        if (lightPos != null) {
            com.hogwartsmagic.event.ModEvents.addTemporaryRevelioLight(level, lightPos, REVELIO_LIGHT_TICKS);
        }
    }

    private void revealChest(ServerLevel level, BlockPos origin, BlockPos chestPos, boolean particles) {
        double chestX = chestPos.getX() + 0.5D;
        double chestY = chestPos.getY() + 0.6D;
        double chestZ = chestPos.getZ() + 0.5D;
        if (particles) {
            com.hogwartsmagic.spell.SpellBeamFx.effectAt(level, SpellType.REVELIO, new Vec3(chestX, chestY, chestZ), com.hogwartsmagic.spell.SpellBeamFx.GLOW);
        }

        BlockPos lightPos = findNearbyAirForReveal(level, chestPos, origin);
        if (lightPos != null) {
            com.hogwartsmagic.event.ModEvents.addTemporaryRevelioLight(level, lightPos, REVELIO_LIGHT_TICKS);
        }
        if (particles) {
            level.playSound(null, chestPos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.45F, 1.65F);
        }
    }

    private static boolean isRevealableOre(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.GOLD_ORES)
                || state.is(net.minecraft.tags.BlockTags.IRON_ORES)
                || state.is(net.minecraft.tags.BlockTags.DIAMOND_ORES)
                || state.is(net.minecraft.tags.BlockTags.REDSTONE_ORES)
                || state.is(net.minecraft.tags.BlockTags.LAPIS_ORES)
                || state.is(net.minecraft.tags.BlockTags.COAL_ORES)
                || state.is(net.minecraft.tags.BlockTags.COPPER_ORES)
                || state.is(net.minecraft.tags.BlockTags.EMERALD_ORES)
                || state.is(Blocks.ANCIENT_DEBRIS)
                || state.is(Blocks.NETHER_QUARTZ_ORE)
                || state.is(Blocks.NETHER_GOLD_ORE);
    }

    private static boolean isRevealableChest(BlockState state) {
        return state.is(Blocks.CHEST)
                || state.is(Blocks.TRAPPED_CHEST)
                || state.is(Blocks.BARREL)
                || state.getBlock() instanceof EnderChestBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock;
    }

    private static BlockPos findNearbyAirForReveal(Level level, BlockPos anchor, BlockPos origin) {
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            BlockPos candidate = anchor.relative(direction);
            if (!level.isLoaded(candidate)) {
                continue; // never force-load a chunk just to place a reveal light
            }
            BlockState candidateState = level.getBlockState(candidate);
            if (!(level.isEmptyBlock(candidate) || candidateState.is(Blocks.LIGHT)
                    || com.hogwartsmagic.block.LumosLightBlock.isTimedLight(candidateState))) {
                continue;
            }

            double distance = candidate.distSqr(origin);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPos = candidate.immutable();
            }
        }
        return bestPos;
    }

    /**
     * Dark Magic: who answers for this beam's deed. The caster; the puppeteer of a puppet beam (if online); nobody
     * for a reflected beam or another forced body. The same rule as a paid cast (DarkRules.payer).
     */
    @javax.annotation.Nullable
    private ServerPlayer darkDeedPayer() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Entity owner = getOwner();
        boolean ownerForced = owner instanceof ServerPlayer body
                && com.hogwartsmagic.spell.mind.PossessionManager.isForcedCaster(body);
        com.hogwartsmagic.spell.dark.DarkRules.Payer who =
                com.hogwartsmagic.spell.dark.DarkRewardRules.deedPayer(this.reflected, this.puppeteer != null, ownerForced);
        if (who == com.hogwartsmagic.spell.dark.DarkRules.Payer.PUPPETEER) {
            return serverLevel.getServer().getPlayerList().getPlayer(this.puppeteer);
        }
        return who == com.hogwartsmagic.spell.dark.DarkRules.Payer.BODY && owner instanceof ServerPlayer caster
                ? caster : null;
    }

    private void tryPrimeHorcruxFromAvada(LivingEntity victim) {
        if (!(this.level() instanceof ServerLevel serverLevel)
                || !(getOwner() instanceof net.minecraft.server.level.ServerPlayer player)
                || victim.isAlive()) {
            return;
        }
        // Dark Magic: a puppet beam or a forced body never forges a Horcrux (a possessed body is never given one).
        if (this.puppeteer != null || com.hogwartsmagic.spell.mind.PossessionManager.isForcedCaster(player)) {
            return;
        }
        HorcruxBlockEntity existing = HorcruxData.getOwnedHorcrux(player);
        if (existing != null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Your soul is already tied to another forbidden vessel.")
                    .withStyle(net.minecraft.ChatFormatting.DARK_RED), true);
            return;
        }

        BlockPos[] candidates = new BlockPos[] {
                victim.blockPosition().below(),
                victim.blockPosition(),
                victim.blockPosition().below(2)
        };
        for (BlockPos candidate : candidates) {
            if (!(serverLevel.getBlockEntity(candidate) instanceof HorcruxBlockEntity horcrux)) {
                continue;
            }
            if ((horcrux.isActive() || horcrux.isPrimed()) && !player.getUUID().equals(horcrux.getOwnerUuid())) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("Another soul already festers inside this altar.")
                        .withStyle(net.minecraft.ChatFormatting.RED), true);
                return;
            }
            if (horcrux.beginRitual(serverLevel, player)) {
                HorcruxData.startRitual(player, serverLevel, candidate);
                com.hogwartsmagic.spell.SpellBeamFx.effectAt(serverLevel, SpellType.AVADA_KEDAVRA, Vec3.atCenterOf(candidate).add(0.0, 0.6, 0.0),
                        com.hogwartsmagic.spell.SpellBeamFx.EVADE);
                serverLevel.playSound(null, candidate, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 1.0F, 0.55F);
                serverLevel.playSound(null, candidate, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.0F, 0.5F);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("The Horcrux has tasted death. Now it will feed on you.")
                        .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE), false);
            }
            return;
        }
    }

    private boolean tryStartMorsmordreSkyRaid() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!canTriggerMorsmordreSkyRaid()) {
            return false;
        }

        BlockPos raidCenter = serverLevel.getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(this.getX(), 0.0D, this.getZ())
        );

        double markY = Math.max(
                raidCenter.getY() + MORSMORDRE_MARK_MIN_HEIGHT_ABOVE_GROUND,
                this.highestYReached - MORSMORDRE_MARK_BELOW_PEAK_OFFSET
        );
        DarkMarkEntity mark = new DarkMarkEntity(serverLevel, raidCenter.getX() + 0.5D, markY, raidCenter.getZ() + 0.5D, 4800);
        serverLevel.addFreshEntity(mark);

        net.minecraft.world.entity.LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
        if (lightning != null) {
            lightning.moveTo(Vec3.atBottomCenterOf(raidCenter));
            lightning.setVisualOnly(true);
            serverLevel.addFreshEntity(lightning);
        }

        // Dark Magic: a Lost caster's Mark calls an escort to their side instead of a raid near them.
        if (!this.reflected && this.puppeteer == null && getOwner() instanceof ServerPlayer escortOwner
                && !com.hogwartsmagic.spell.mind.PossessionManager.isForcedCaster(escortOwner)
                && com.hogwartsmagic.spell.dark.DarkRewards.earnsEscort(escortOwner)) {
            return DeathEaterEscort.summon(serverLevel, escortOwner) > 0;
        }

        java.util.List<WizardEntity> raidMembers = new java.util.ArrayList<>();
        int count = 5 + this.random.nextInt(5);
        for (int i = 0; i < count; i++) {
            double rx = raidCenter.getX() + (this.random.nextDouble() - 0.5D) * 40.0D;
            double rz = raidCenter.getZ() + (this.random.nextDouble() - 0.5D) * 40.0D;
            if (!serverLevel.hasChunkAt(BlockPos.containing(rx, 0.0D, rz))) {
                continue; // never load a chunk for a raider
            }
            BlockPos spawnPos = serverLevel.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(rx, raidCenter.getY(), rz)
            );

            WizardEntity raidWizard = ModEntities.WIZARD.get().create(serverLevel);
            if (raidWizard == null) {
                continue;
            }

            raidWizard.setPos(spawnPos.getX() + 0.5D, spawnPos.getY() + 2.0D, spawnPos.getZ() + 0.5D);
            raidWizard.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(spawnPos), net.minecraft.world.entity.MobSpawnType.EVENT, null);
            raidWizard.addEffect(new net.minecraft.world.effect.MobEffectInstance(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get(), 40, 0));
            if (this.random.nextFloat() < 0.1f) {
                BroomEntity broom = new BroomEntity(ModEntities.BROOM.get(), serverLevel);
                broom.setPos(raidWizard.getX(), raidWizard.getY(), raidWizard.getZ());
                if (serverLevel.addFreshEntity(broom)) {
                    raidWizard.startRiding(broom, true);
                }
            }

            serverLevel.addFreshEntity(raidWizard);
            raidMembers.add(raidWizard);
        }

        if (raidMembers.isEmpty()) {
            return false;
        }

        com.hogwartsmagic.event.ModEvents.startWizardRaid(serverLevel, raidCenter, raidMembers);
        return true;
    }

    private boolean canTriggerMorsmordreSkyRaid() {
        Entity owner = this.getOwner();
        if (!(owner instanceof LivingEntity livingOwner)) {
            return false;
        }

        BlockPos projectilePos = this.blockPosition();
        if (!this.level().canSeeSky(projectilePos)) {
            return false;
        }

        double minRequiredHeight = livingOwner.getY() + MORSMORDRE_MIN_SKY_HEIGHT;
        return this.highestYReached >= minRequiredHeight;
    }

    private void playNearMissFeedback() {
        com.hogwartsmagic.util.ClientSideBridge.playNearMissFeedback(this);
    }

    public static double getNearMissRadius() {
        return NEAR_MISS_RADIUS;
    }

    public boolean hogwartsmagic$hasNearMissWhooshPlayed() {
        return nearMissWhooshPlayed;
    }

    public void hogwartsmagic$markNearMissWhooshPlayed() {
        nearMissWhooshPlayed = true;
    }

    private boolean tryResolveBeamClash() {
        if (this.tickCount < 2 || this.level().isClientSide || this.isRemoved()) {
            return false;
        }
        // Swept test: two beams closing head-on cover 3+ blocks a tick between them, so a check on where
        // they stand now let about half of all clashes pass straight through. Compare the paths each beam
        // took on its last move instead (xo..x). Every beam checks, so whichever of the pair ticks second
        // compares both moves of the same tick.
        Vec3 from = new Vec3(this.xo, this.yo, this.zo);
        Vec3 to = this.position();
        java.util.List<SpellBeamEntity> nearby = this.level().getEntitiesOfClass(
                SpellBeamEntity.class,
                new net.minecraft.world.phys.AABB(from, to).inflate(CLASH_SEARCH_REACH),
                // tickCount > 0: a beam spawned this tick has not moved yet, and its xo is not set until it does.
                e -> e != this && e.isAlive() && e.tickCount > 0
                        && segmentDistanceSqr(from, to, new Vec3(e.xo, e.yo, e.zo), e.position())
                        <= CLASH_DISTANCE * CLASH_DISTANCE
        );
        for (SpellBeamEntity other : nearby) {
            Entity ownerA = this.getOwner();
            Entity ownerB = other.getOwner();
            if (ownerA != null && ownerB != null && ownerA == ownerB) {
                continue;
            }
            if (this.isElderWandCast() != other.isElderWandCast()) {
                SpellBeamEntity winner = this.isElderWandCast() ? this : other;
                SpellBeamEntity loser = winner == this ? other : this;
                Vec3 clashPos = this.position().add(other.position()).scale(0.5);
                com.hogwartsmagic.spell.SpellBeamFx.clash(this, this.getSpellType(), other, other.getSpellType(), clashPos,
                        winner == this ? com.hogwartsmagic.spell.SpellBeamFx.CLASH_A_WINS : com.hogwartsmagic.spell.SpellBeamFx.CLASH_B_WINS);
                loser.impactSent = true;
                loser.discard();
                winner.setDeltaMovement(winner.getDeltaMovement().scale(1.04D));
                return winner != this;
            }
            // Two duellists casting at each other at the same moment: the beams lock (Priori Incantatem).
            if (com.hogwartsmagic.spell.PrioriIncantatem.tryLock(this, this.getSpellType(), other, other.getSpellType(),
                    this.tickCount, other.tickCount, ownerA, ownerB)) {
                this.impactSent = true;
                other.impactSent = true;
                this.discard();
                other.discard();
                return true;
            }
            Vec3 clashPos = this.position().add(other.position()).scale(0.5);
            com.hogwartsmagic.spell.SpellBeamFx.clash(this, this.getSpellType(), other, other.getSpellType(), clashPos, com.hogwartsmagic.spell.SpellBeamFx.CLASH_MUTUAL);
            applyClashShockwave(clashPos);
            this.impactSent = true;
            other.impactSent = true;
            this.discard();
            other.discard();
            return true;
        }
        return false;
    }

    /** Squared distance between the closest points of segments p1-q1 and p2-q2 (either may be a point). */
    private static double segmentDistanceSqr(Vec3 p1, Vec3 q1, Vec3 p2, Vec3 q2) {
        Vec3 d1 = q1.subtract(p1);
        Vec3 d2 = q2.subtract(p2);
        Vec3 r = p1.subtract(p2);
        double a = d1.lengthSqr();
        double e = d2.lengthSqr();
        double f = d2.dot(r);
        double s;
        double t;
        if (a < 1.0E-9 && e < 1.0E-9) {
            return r.lengthSqr();
        }
        if (a < 1.0E-9) {
            s = 0.0;
            t = net.minecraft.util.Mth.clamp(f / e, 0.0, 1.0);
        } else {
            double c = d1.dot(r);
            if (e < 1.0E-9) {
                t = 0.0;
                s = net.minecraft.util.Mth.clamp(-c / a, 0.0, 1.0);
            } else {
                double b = d1.dot(d2);
                double denom = a * e - b * b;
                s = denom > 1.0E-9 ? net.minecraft.util.Mth.clamp((b * f - c * e) / denom, 0.0, 1.0) : 0.0;
                t = (b * s + f) / e;
                if (t < 0.0) {
                    t = 0.0;
                    s = net.minecraft.util.Mth.clamp(-c / a, 0.0, 1.0);
                } else if (t > 1.0) {
                    t = 1.0;
                    s = net.minecraft.util.Mth.clamp((b - c) / a, 0.0, 1.0);
                }
            }
        }
        Vec3 c1 = p1.add(d1.scale(s));
        Vec3 c2 = p2.add(d2.scale(t));
        return c1.distanceToSqr(c2);
    }

    private void applyClashShockwave(Vec3 pos) {
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(
                pos.x - 3.5, pos.y - 2.0, pos.z - 3.5,
                pos.x + 3.5, pos.y + 2.0, pos.z + 3.5
        );
        java.util.List<LivingEntity> affected = this.level().getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive);
        for (LivingEntity entity : affected) {
            Vec3 away = entity.position().subtract(pos);
            if (away.lengthSqr() < 1.0E-6) {
                away = new Vec3(0.0, 0.1, 0.0);
            }
            Vec3 push = away.normalize().scale(0.45);
            entity.push(push.x, 0.2, push.z);
        }
    }

    private void playLayeredImpactSound(SpellType spell) {
        switch (spell) {
            case BOMBARDA, CONFRINGO, FIENDFYRE -> {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.65f, 1.15f);
            }
            case AQUA_ERUCTO -> {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        net.minecraft.sounds.SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.8f, 1.0f);
            }
            case OBLIVIATE, CONFUNDO -> {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.55f, 1.4f);
            }
            default -> {
            }
        }
    }

    private boolean pullMandrakeWithAccio(BlockPos pos) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        Entity owner = getOwner();
        Vec3 target = owner != null
                ? owner.getEyePosition()
                : this.position().subtract(this.getDeltaMovement().normalize().scale(3.0D));
        Vec3 spawn = Vec3.atCenterOf(pos).add(0.0D, -0.2D, 0.0D);
        Vec3 pull = spawn.vectorTo(target);
        if (pull.lengthSqr() < 1.0E-4D) {
            pull = new Vec3(0.0D, 0.25D, 0.0D);
        } else {
            pull = pull.normalize().scale(0.75D).add(0.0D, 0.18D, 0.0D);
        }

        serverLevel.removeBlock(pos, false);
        serverLevel.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(ExampleMod.MANDRAKE_PLANT.get().defaultBlockState()));
        serverLevel.playSound(null, pos, ModSounds.MANDRAKE_SCREAM.get(), SoundSource.BLOCKS, 1.2F, 1.15F);
        com.hogwartsmagic.spell.SpellBeamFx.effectAt(serverLevel, SpellType.ACCIO, Vec3.atCenterOf(pos).add(0.0, 0.25, 0.0), com.hogwartsmagic.spell.SpellBeamFx.GLOW);

        ItemEntity root = new ItemEntity(serverLevel,
                spawn.x,
                spawn.y,
                spawn.z,
                new ItemStack(ExampleMod.MANDRAKE_ROOT.get()));
        root.setDeltaMovement(pull);
        root.hurtMarked = true;
        root.setPickUpDelay(8);
        serverLevel.addFreshEntity(root);
        return true;
    }

    private boolean tryPullNearbyMandrakeWithAccio() {
        BlockPos center = this.blockPosition();
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;

        for (BlockPos candidate : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            if (!this.level().getBlockState(candidate).is(ExampleMod.MANDRAKE_PLANT.get())) {
                continue;
            }

            double distance = Vec3.atCenterOf(candidate).distanceToSqr(this.position());
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPos = candidate.immutable();
            }
        }

        return bestPos != null && bestDistance <= 2.25D && pullMandrakeWithAccio(bestPos);
    }

    private void applyHeavyImpactImpulse() {
        net.minecraft.world.phys.AABB area = this.getBoundingBox().inflate(4.0);
        java.util.List<net.minecraft.server.level.ServerPlayer> players = this.level().getEntitiesOfClass(
                net.minecraft.server.level.ServerPlayer.class,
                area,
                p -> p.isAlive() && !p.isSpectator()
        );
        for (net.minecraft.server.level.ServerPlayer player : players) {
            Vec3 away = player.position().subtract(this.position());
            if (away.lengthSqr() < 1.0E-5) {
                away = new Vec3(0.0, 0.1, 0.0);
            }
            Vec3 push = away.normalize().scale(0.38);
            player.push(push.x, 0.13, push.z);
            player.hurtMarked = true; // player motion is client-side: without this the jolt is never sent
        }
    }

    private void applyAquaWaveAt(Vec3 origin, Entity caster) {
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) {
            return;
        }
        Vec3 forward = this.getDeltaMovement().normalize();
        if (forward.lengthSqr() < 1.0E-6) {
            if (caster != null) {
                forward = caster.getLookAngle();
            } else {
                forward = new Vec3(0.0, 0.0, 1.0);
            }
        }

        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                origin.x - 5.0, origin.y - 1.5, origin.z - 5.0,
                origin.x + 5.0, origin.y + 2.5, origin.z + 5.0
        );
        java.util.List<LivingEntity> targets = sl.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e != caster);

        for (LivingEntity target : targets) {
            Vec3 toTarget = target.position().subtract(origin);
            double dist = toTarget.length();
            if (dist > 6.0 || dist < 0.001) {
                continue;
            }
            Vec3 dirToTarget = toTarget.normalize();
            double dot = dirToTarget.dot(forward);
            if (dot < 0.35) {
                continue; // cone wave in front only
            }

            double strength = (1.0 - (dist / 6.0)) * 1.4;
            Vec3 push = dirToTarget.scale(Math.max(0.15, strength));
            target.push(push.x, 0.22 + strength * 0.08, push.z);
            target.hurtMarked = true; // a player target only feels the wave once its motion is sent
            target.clearFire();
            target.setRemainingFireTicks(0);
            if (target instanceof net.minecraft.world.entity.monster.Blaze || target instanceof net.minecraft.world.entity.monster.MagmaCube) {
                target.hurt(target.damageSources().drown(), 5.0f);
            }
        }

        placeAquaImpactWater(sl, origin, forward);

        // Foam-like wave visuals in a widening front arc.
        for (int ring = 1; ring <= 4; ring++) {
            double radius = ring * 1.4;
            for (int i = -6; i <= 6; i++) {
                double angle = Math.atan2(forward.z, forward.x) + (i / 12.0) * 1.2;
                double x = origin.x + Math.cos(angle) * radius;
                double z = origin.z + Math.sin(angle) * radius;
                double y = origin.y + 0.1 + (ring * 0.05);
                sl.sendParticles(ParticleTypes.SPLASH, x, y, z, 1, 0.0, 0.05, 0.0, 0.0);
                if ((i + ring) % 2 == 0) {
                    sl.sendParticles(ParticleTypes.BUBBLE, x, y + 0.1, z, 1, 0.0, 0.02, 0.0, 0.0);
                }
            }
        }
    }

    private void placeAquaImpactWater(ServerLevel serverLevel, Vec3 origin, Vec3 forward) {
        Vec3 flattened = new Vec3(forward.x, 0.0D, forward.z);
        if (flattened.lengthSqr() < 1.0E-6D) {
            flattened = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            flattened = flattened.normalize();
        }
        Vec3 side = new Vec3(-flattened.z, 0.0D, flattened.x);

        for (int ring = 0; ring < 4; ring++) {
            double distance = 0.85D + ring * 0.9D;
            Vec3 center = origin.add(flattened.scale(distance));
            placeAquaSegment(serverLevel, center, 1 + Math.min(ring, 2));
            placeAquaSegment(serverLevel, center.add(side.scale(0.75D + ring * 0.2D)), 2 + Math.min(ring, 1));
            placeAquaSegment(serverLevel, center.add(side.scale(-(0.75D + ring * 0.2D))), 2 + Math.min(ring, 1));
        }
    }

    private void sculptAquaStream() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 velocity = this.getDeltaMovement();
        double speed = velocity.length();
        if (speed < 1.0E-4D) {
            return;
        }

        Vec3 direction = velocity.normalize();
        Vec3 right = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-5D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }

        Vec3 headOffset = direction.scale(0.9D);
        for (int step = 0; step < 4; step++) {
            Vec3 sample = this.position().subtract(headOffset).subtract(direction.scale(step * 0.95D));
            placeAquaSegment(serverLevel, sample, 1 + Math.min(step, 2));

            if (step < 3) {
                double sideOffset = 0.32D + (step * 0.06D);
                placeAquaSegment(serverLevel, sample.add(right.scale(sideOffset)), 2 + Math.min(step, 1));
                placeAquaSegment(serverLevel, sample.add(right.scale(-sideOffset)), 2 + Math.min(step, 1));
            }
        }
    }

    private void placeAquaSegment(ServerLevel serverLevel, Vec3 sample, int flowLevel) {
        BlockPos centerPos = BlockPos.containing(sample.x, sample.y + 0.05D, sample.z);
        if (placeTransientWater(serverLevel, centerPos, flowLevel)) {
            serverLevel.sendParticles(ParticleTypes.SPLASH, sample.x, sample.y + 0.1D, sample.z, 2, 0.08D, 0.04D, 0.08D, 0.0D);
            if (this.tickCount % 2 == 0) {
                serverLevel.sendParticles(ParticleTypes.FALLING_WATER, sample.x, sample.y + 0.2D, sample.z, 1, 0.04D, 0.02D, 0.04D, 0.0D);
            }
        }
    }

    private boolean placeTransientWater(ServerLevel serverLevel, BlockPos pos, int flowLevel) {
        BlockState state = serverLevel.getBlockState(pos);
        if (state.is(Blocks.WATER)) {
            com.hogwartsmagic.event.ModEvents.addTemporaryAquaWater(
                    serverLevel,
                    pos,
                    3,
                    Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, Math.max(1, Math.min(3, flowLevel)))
            );
            return true;
        }
        if (!canReplaceWithTransientWater(serverLevel, pos)) {
            return false;
        }

        BlockState waterState = Blocks.WATER.defaultBlockState()
                .setValue(LiquidBlock.LEVEL, Math.max(1, Math.min(3, flowLevel)));
        com.hogwartsmagic.event.ModEvents.addTemporaryAquaWater(serverLevel, pos, 3, waterState);
        return true;
    }

    private boolean canReplaceWithTransientWater(ServerLevel serverLevel, BlockPos pos) {
        BlockState state = serverLevel.getBlockState(pos);
        if (state.hasBlockEntity()) {
            return false;
        }
        if (state.isAir() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
            return true;
        }
        if (state.is(Blocks.TALL_GRASS) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN) || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS)
                || state.is(Blocks.KELP_PLANT) || state.is(Blocks.KELP)
                || state.is(Blocks.SNOW)) {
            return true;
        }
        return state.canBeReplaced();
    }

    private void breakPolyjuiceDisguise(LivingEntity target) {
        if (!(target instanceof ServerPlayer disguisedPlayer)) {
            return;
        }
        com.hogwartsmagic.event.ModEvents.breakPolyjuiceDisguise(disguisedPlayer);
    }

    private void spawnFizzleEffects() {
        SpellType spell = getSpellType();
        if (!this.level().isClientSide && !this.impactSent) {
            this.impactSent = true;
            // The hit point, not the beam's position: a projectile is a whole tick's travel short of it in onHit.
            com.hogwartsmagic.spell.SpellBeamFx.impact(this, spell, this.hitAt != null ? this.hitAt : this.position(),
                    this.tickCount > 200 ? com.hogwartsmagic.spell.SpellBeamFx.FADE
                            : this.shieldBlocked ? com.hogwartsmagic.spell.SpellBeamFx.SHIELD
                            : com.hogwartsmagic.spell.SpellBeamFx.FIZZLE);
        }
        if (spell != SpellType.AVADA_KEDAVRA) { // the Killing Curse's strike has its own sound
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.SPELL_FIZZLE.get(), SoundSource.NEUTRAL, 1.0f, 1.5f);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        // Handled in onHit to distinguish between successful and failed hits
        super.onHitEntity(result);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }
}
