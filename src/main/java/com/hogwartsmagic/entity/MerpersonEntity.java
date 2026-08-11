package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * A merperson of the Black Lake.
 *
 * <p>These are selkies - the Scottish variety, and the ones that actually live
 * under Hogwarts: grey, wiry, fierce-looking, armed with a crude spear, nothing
 * like the pretty siren of the Mediterranean. They are Beings, not beasts, and
 * they behave like it.
 *
 * <p><b>They are territorial, not hostile.</b> Swim past and they will watch you
 * go. Linger and one of them will rear up, flare its ear fins and screech a
 * warning in Mermish. Ignore that warning for five seconds and the pod attacks.
 * Strike one and every merperson within twenty blocks turns on you at once. This
 * is the Goblet of Fire behaviour: they let Harry take his hostage and go, and
 * levelled spears at him the moment he tried to take more.
 *
 * <p>A pod is led by a single merchieftainess - larger, paler, white-green
 * haired and wearing a shell crown.
 */
public class MerpersonEntity extends PathfinderMob implements NeutralMob {

    private static final EntityDataAccessor<Byte> DATA_ACTION =
            SynchedEntityData.defineId(MerpersonEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_CHIEFTAINESS =
            SynchedEntityData.defineId(MerpersonEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * Plain int constants rather than an enum: a {@code switch} on an enum
     * compiles to a synthetic switch-map helper class that has been observed to
     * fail to load in this mod's dev environment, and this value is read from
     * render code.
     */
    public static final byte ACTION_NONE = 0;
    public static final byte ACTION_WARN = 1;
    public static final byte ACTION_THRUST = 2;
    public static final byte ACTION_SING = 3;

    private static final int WARN_TICKS = 40;
    private static final int THRUST_TICKS = 15;
    private static final int SING_TICKS = 100;

    /** Inside this, and in the water, you get warned. */
    private static final double TERRITORY_RADIUS = 11.0D;
    /** Inside this, the warning has a clock on it. */
    private static final double INTRUSION_RADIUS = 5.5D;
    /** How long you may stand your ground before the spears come down. */
    private static final int PATIENCE_TICKS = 100;
    /** How far a scream for help carries through the pod. */
    private static final double POD_RADIUS = 20.0D;

    /** Out of water they suffocate, but slowly enough to be dragged ashore. */
    private static final int AIR_GRACE_TICKS = 300;

    /** Long enough for the death clip to play out. Vanilla's is twenty. */
    private static final int DEATH_TICKS = 46;

    private static final byte EVENT_HURT = 62;

    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);

    public final AnimationState hoverAnimation = new AnimationState();
    public final AnimationState swimAnimation = new AnimationState();
    public final AnimationState singAnimation = new AnimationState();
    public final AnimationState warnAnimation = new AnimationState();
    public final AnimationState thrustAnimation = new AnimationState();
    public final AnimationState hurtAnimation = new AnimationState();
    public final AnimationState deathAnimation = new AnimationState();

    private int actionTicks;
    private int warnCooldown;
    private int trespassTicks;
    @Nullable
    private UUID trespasser;
    private int dryTicks;
    private int songCooldown;

    /** Smoothed on both sides from synced motion, so no extra packet is needed. */
    private float tailDrive;
    private float tailDriveO;
    private float swimPose;
    private float swimPoseO;

    private int remainingPersistentAngerTime;
    @Nullable
    private UUID persistentAngerTarget;

    public MerpersonEntity(EntityType<? extends MerpersonEntity> type, Level level) {
        super(type, level);
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.12F, true);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
        this.setPathfindingMalus(PathType.WATER, 0.0F);
        this.songCooldown = 200 + this.random.nextInt(400);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 1.05D)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTION, ACTION_NONE);
        builder.define(DATA_CHIEFTAINESS, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new SpearThrustGoal(this));
        this.goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0D, 12));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(MerpersonEntity.class));
        this.targetSelector.addGoal(3, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    // ------------------------------------------------------------------ state

    public boolean isChieftainess() {
        return this.entityData.get(DATA_CHIEFTAINESS);
    }

    public void setChieftainess(boolean chieftainess) {
        this.entityData.set(DATA_CHIEFTAINESS, chieftainess);
        if (chieftainess) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(52.0D);
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10.0D);
            this.getAttribute(Attributes.ARMOR).setBaseValue(5.0D);
            this.setHealth(this.getMaxHealth());
        }
        this.refreshDimensions();
    }

    public byte getAction() {
        return this.entityData.get(DATA_ACTION);
    }

    private void setAction(byte action, int ticks) {
        this.entityData.set(DATA_ACTION, action);
        this.actionTicks = ticks;
    }

    /** True while a committed animation is playing, so head tracking backs off. */
    public boolean isBusy() {
        return getAction() != ACTION_NONE;
    }

    public boolean isFloundering() {
        return !this.isInWater() && this.onGround();
    }

    public float getTailDrive(float partialTick) {
        return Mth.lerp(partialTick, this.tailDriveO, this.tailDrive);
    }

    public float getSwimPose(float partialTick) {
        return Mth.lerp(partialTick, this.swimPoseO, this.swimPose);
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        EntityDimensions base = super.getDefaultDimensions(pose);
        return this.isChieftainess() ? base.scale(1.15F) : base;
    }

    // ------------------------------------------------------------------- tick

    @Override
    public void tick() {
        super.tick();

        this.tailDriveO = this.tailDrive;
        this.swimPoseO = this.swimPose;
        // Measured from how far it actually moved this tick, not from
        // getDeltaMovement(). A remote entity's delta-movement vector is only
        // resent when the server thinks it changed enough to bother, so on the
        // client it goes stale and the tail would stop beating mid-swim.
        double dx = this.getX() - this.xo;
        double dy = this.getY() - this.yo;
        double dz = this.getZ() - this.zo;
        double step = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double flat = Math.sqrt(dx * dx + dz * dz);
        float driveTarget = (float) Mth.clamp(step / 0.32D, 0.0D, 1.0D);
        float poseTarget = this.isInWater()
                ? (float) Mth.clamp(flat / 0.20D, 0.0D, 1.0D)
                : 0.0F;
        this.tailDrive += (driveTarget - this.tailDrive) * 0.16F;
        this.swimPose += (poseTarget - this.swimPose) * 0.10F;

        if (this.level().isClientSide) {
            tickClientAnimations();
            return;
        }

        if (this.actionTicks > 0 && --this.actionTicks == 0) {
            this.entityData.set(DATA_ACTION, ACTION_NONE);
        }
        if (this.warnCooldown > 0) {
            this.warnCooldown--;
        }
        if (this.songCooldown > 0) {
            this.songCooldown--;
        }

        tickBeaching();
    }

    private void tickClientAnimations() {
        if (this.deathTime > 0) {
            this.hoverAnimation.stop();
            this.swimAnimation.stop();
            this.deathAnimation.startIfStopped(this.tickCount);
            return;
        }
        // Hover and swim cross-fade on actual speed rather than on a state flag,
        // so a merperson drifting to a halt eases into treading water.
        if (this.swimPose > 0.35F) {
            this.hoverAnimation.stop();
            this.swimAnimation.startIfStopped(this.tickCount);
        } else {
            this.swimAnimation.stop();
            this.hoverAnimation.startIfStopped(this.tickCount);
        }
    }

    /**
     * Merpeople cannot live in air. This is deliberately gentle - five minutes of
     * grace - so hauling one out of the lake is a viable, and grim, tactic rather
     * than an instant kill.
     */
    private void tickBeaching() {
        if (this.isInWater()) {
            this.dryTicks = 0;
            return;
        }
        this.dryTicks++;
        if (this.dryTicks > AIR_GRACE_TICKS && this.dryTicks % 40 == 0) {
            this.hurt(this.damageSources().dryOut(), 1.0F);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        this.updatePersistentAnger((ServerLevel) this.level(), true);
        tickTerritory();
        tickSong();
    }

    /**
     * The escalation ladder: ignore, warn, attack.
     *
     * <p>Deliberately forgiving. A player who swims past at a distance is never
     * touched; a player who parks on top of the pod gets one loud, unmistakable
     * warning and five seconds to take it.
     */
    private void tickTerritory() {
        if (this.getTarget() != null || this.isAngry() || this.tickCount % 5 != 0) {
            return;
        }

        Player nearest = this.level().getNearestPlayer(this, TERRITORY_RADIUS);
        if (nearest == null || nearest.isSpectator() || nearest.isCreative() || !nearest.isInWater()) {
            this.trespassTicks = Math.max(0, this.trespassTicks - 10);
            if (this.trespassTicks == 0) {
                this.trespasser = null;
            }
            return;
        }

        if (!nearest.getUUID().equals(this.trespasser)) {
            this.trespasser = nearest.getUUID();
            this.trespassTicks = 0;
        }

        double distance = this.distanceTo(nearest);
        if (this.warnCooldown <= 0 && !isBusy()) {
            warnOff(nearest);
        }

        if (distance <= INTRUSION_RADIUS) {
            this.trespassTicks += 5;
            if (this.trespassTicks >= PATIENCE_TICKS) {
                this.setTarget(nearest);
                this.setPersistentAngerTarget(nearest.getUUID());
                this.startPersistentAngerTimer();
                this.trespassTicks = 0;
            }
        } else {
            this.trespassTicks = Math.max(0, this.trespassTicks - 3);
        }
    }

    /** Rear up, flare, and cry a warning in Mermish. */
    private void warnOff(Player at) {
        setAction(ACTION_WARN, WARN_TICKS);
        this.warnCooldown = 90 + this.random.nextInt(60);
        this.getLookControl().setLookAt(at, 60.0F, 40.0F);
        speak(ModSounds.MERPERSON_CALL.get(), ModSounds.MERPERSON_SCREECH.get(), 1.2F, 26.0D);
    }

    /**
     * Says something in Mermish, and lets every listener hear it the way their
     * own ears would.
     *
     * <p>This is the joke the books make and it only works per-listener:
     * Mermish is a real language with a real song in it, but above the surface
     * it is unbearable screeching - Harry cannot make out a word of the golden
     * egg until he puts his head under the bathwater. A broadcast sound would
     * pick one version for everybody, so each player gets their own packet based
     * on whether <em>their</em> head is submerged.
     */
    private void speak(SoundEvent submerged, SoundEvent inAir, float volume, double range) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        float pitch = voicePitch();
        long seed = this.random.nextLong();
        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(this) > range * range) {
                continue;
            }
            SoundEvent heard = player.isUnderWater() ? submerged : inAir;
            player.connection.send(new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(heard), SoundSource.HOSTILE,
                    this.getX(), this.getY(), this.getZ(), volume, pitch, seed));
        }
    }

    /**
     * The chorus from the golden egg. Only sung when the pod is calm, and only
     * when a player is close enough to hear it, so it stays an event rather than
     * a loop running unheard at the bottom of the lake.
     *
     * <p>Only the chieftainess starts it. Otherwise four merpeople in one pod
     * each roll their own cooldown and the square turns into a round.
     */
    private void tickSong() {
        if (this.songCooldown > 0 || isBusy() || this.getTarget() != null || !this.isInWater()) {
            return;
        }
        if (!isChieftainess() && !this.level()
                .getEntitiesOfClass(MerpersonEntity.class, this.getBoundingBox().inflate(POD_RADIUS),
                        MerpersonEntity::isChieftainess).isEmpty()) {
            this.songCooldown = 300;
            return;
        }
        if (this.level().getNearestPlayer(this, 30.0D) == null) {
            this.songCooldown = 200;
            return;
        }
        setAction(ACTION_SING, SING_TICKS);
        this.songCooldown = 500 + this.random.nextInt(700);
        speak(ModSounds.MERPERSON_SONG.get(), ModSounds.MERPERSON_SCREECH.get(), 1.6F, 34.0D);

        // The rest of the pod joins in behind her.
        for (MerpersonEntity other : this.level().getEntitiesOfClass(MerpersonEntity.class,
                this.getBoundingBox().inflate(POD_RADIUS),
                mer -> mer != this && mer.isAlive() && !mer.isBusy() && mer.getTarget() == null)) {
            other.setAction(ACTION_SING, SING_TICKS);
            other.songCooldown = 500 + other.random.nextInt(700);
        }
    }

    private float voicePitch() {
        float base = this.isChieftainess() ? 0.82F : 1.0F;
        return base + (this.random.nextFloat() - 0.5F) * 0.12F;
    }

    // ----------------------------------------------------------------- combat

    /** Starts the swing: the clip runs now, the point lands a few ticks later. */
    void beginThrust() {
        setAction(ACTION_THRUST, THRUST_TICKS);
        this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    /** Called by the thrust goal on the frame the spear is actually out in front. */
    void landThrust(LivingEntity target) {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                ModSounds.MERPERSON_SPEAR.get(), SoundSource.HOSTILE, 1.0F, voicePitch());
        this.doHurtTarget(target);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !this.level().isClientSide) {
            this.level().broadcastEntityEvent(this, EVENT_HURT);
            if (source.getEntity() instanceof LivingEntity attacker) {
                rallyPod(attacker);
            }
        }
        return hit;
    }

    /**
     * Strike one merperson and you have struck all of them.
     *
     * <p>{@link HurtByTargetGoal#setAlertOthers} only reaches mobs the victim can
     * see; a pod scattered round a lakebed ruin mostly cannot see each other, and
     * the whole point of these creatures is that they answer a cry for help.
     */
    private void rallyPod(LivingEntity attacker) {
        if (attacker instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }
        AABB range = this.getBoundingBox().inflate(POD_RADIUS);
        List<MerpersonEntity> pod = this.level().getEntitiesOfClass(MerpersonEntity.class, range,
                other -> other != this && other.isAlive());
        for (MerpersonEntity other : pod) {
            if (other.getTarget() == null) {
                other.setTarget(attacker);
                other.setPersistentAngerTarget(attacker.getUUID());
                other.startPersistentAngerTimer();
            }
        }
    }

    /**
     * Death runs long enough for the clip to finish, and the body sinks.
     *
     * <p>Vanilla removes a corpse after twenty ticks, which would cut the
     * two-and-a-bit second death animation off less than halfway through - the
     * merperson would vanish while it was still going limp.
     */
    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (!this.level().isClientSide()) {
            if (this.isInWater()) {
                this.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0.0D, -0.03D, 0.0D));
            }
            if (this.deathTime >= DEATH_TICKS && !this.isRemoved()) {
                this.level().broadcastEntityEvent(this, (byte) 60);   // the usual poof
                this.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
            }
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_HURT) {
            this.hurtAnimation.start(this.tickCount);
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (!this.level().isClientSide || !DATA_ACTION.equals(key)) {
            return;
        }
        // Restart whichever clip matches the new state and stop the rest, so the
        // client stays in sync without needing a one-shot entity event.
        byte action = getAction();
        this.warnAnimation.stop();
        this.thrustAnimation.stop();
        this.singAnimation.stop();
        if (action == ACTION_WARN) {
            this.warnAnimation.start(this.tickCount);
        } else if (action == ACTION_THRUST) {
            this.thrustAnimation.start(this.tickCount);
        } else if (action == ACTION_SING) {
            this.singAnimation.start(this.tickCount);
        }
    }

    // ---------------------------------------------------------------- spawning

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData);
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ExampleMod.MERPERSON_SPEAR.get()));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.06F);
        // One leader per naturally-spawned pod: the first of the group, never a
        // straggler, and never for spawn eggs unless the player asks for one.
        if (data == null && (reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION
                || reason == MobSpawnType.STRUCTURE)) {
            setChieftainess(true);
            data = new PodLeaderMarker();
        }
        return data;
    }

    /** Presence of this in the group data means the pod already has its leader. */
    private static class PodLeaderMarker implements SpawnGroupData {
    }

    public static boolean checkMerpersonSpawnRules(EntityType<MerpersonEntity> type, ServerLevelAccessor level,
                                                   MobSpawnType reason, BlockPos pos,
                                                   net.minecraft.util.RandomSource random) {
        // Enough water to hold a two-and-a-bit-block animal, below the surface,
        // and near the bottom. Measuring against the floor rather than against a
        // fixed depth below sea level is what lets them live in an inland loch as
        // well as in the deep arm - a flat "must be four blocks under sea level"
        // test excludes every shallow lake in the world.
        if (!level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)
                || !level.getFluidState(pos.above()).is(net.minecraft.tags.FluidTags.WATER)
                || !level.getFluidState(pos.above(2)).is(net.minecraft.tags.FluidTags.WATER)) {
            return false;
        }
        int floor = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR,
                pos.getX(), pos.getZ());
        return pos.getY() < level.getSeaLevel() && pos.getY() - floor <= 10;
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    // ---------------------------------------------------------------- plumbing

    // Breathing underwater is a data tag in 1.21, not an override: see
    // data/minecraft/tags/entity_type/can_breathe_under_water.json.

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public void travel(Vec3 travelVector) {
        // In water the swim move control drives it; on land it can only flop.
        if (this.isEffectiveAi() && this.isInWater()) {
            this.moveRelative(this.getSpeed(), travelVector);
            this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9D));
            if (this.getTarget() == null) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.005D, 0.0D));
            }
        } else {
            super.travel(travelVector);
        }
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return this.isInWater() ? ModSounds.MERPERSON_AMBIENT.get() : ModSounds.MERPERSON_SCREECH.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.MERPERSON_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.MERPERSON_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 1.1F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.addPersistentAngerSaveData(tag);
        tag.putBoolean("Chieftainess", isChieftainess());
        tag.putInt("DryTicks", this.dryTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.readPersistentAngerSaveData(this.level(), tag);
        if (tag.getBoolean("Chieftainess")) {
            setChieftainess(true);
        }
        this.dryTicks = tag.getInt("DryTicks");
    }

    // ------------------------------------------------------------- NeutralMob

    @Override
    public int getRemainingPersistentAngerTime() {
        return this.remainingPersistentAngerTime;
    }

    @Override
    public void setRemainingPersistentAngerTime(int time) {
        this.remainingPersistentAngerTime = time;
    }

    @Nullable
    @Override
    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable UUID target) {
        this.persistentAngerTarget = target;
    }

    @Override
    public void startPersistentAngerTimer() {
        setRemainingPersistentAngerTime(PERSISTENT_ANGER_TIME.sample(this.random));
    }

    /**
     * A spear thrust, timed to the clip.
     *
     * <p>The base goal lands its hit the instant the cooldown expires, which would
     * put the damage on the frame the arm is still cocked. Here the swing starts
     * the clip and the hit is applied a few ticks later, when the point is
     * actually out in front.
     */
    private static class SpearThrustGoal extends MeleeAttackGoal {

        private static final int WINDUP_TICKS = 5;

        private final MerpersonEntity merperson;
        private int windup;
        @Nullable
        private LivingEntity pending;

        SpearThrustGoal(MerpersonEntity merperson) {
            super(merperson, 1.15D, true);
            this.merperson = merperson;
        }

        @Override
        public void stop() {
            super.stop();
            this.windup = 0;
            this.pending = null;
        }

        @Override
        public void tick() {
            super.tick();
            if (this.windup > 0 && --this.windup == 0) {
                LivingEntity target = this.pending;
                this.pending = null;
                if (target != null && target.isAlive()
                        && this.merperson.isWithinMeleeAttackRange(target)) {
                    this.merperson.landThrust(target);
                }
            }
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target) {
            if (!canPerformAttack(target)) {
                return;
            }
            resetAttackCooldown();
            this.merperson.beginThrust();
            this.pending = target;
            this.windup = WINDUP_TICKS;
        }
    }
}
