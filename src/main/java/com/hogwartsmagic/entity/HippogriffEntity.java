package com.hogwartsmagic.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

public class HippogriffEntity extends TamableAnimal implements FlyingAnimal, PlayerRideableJumping {
    private static final double CALM_APPROACH_SPEED_SQR = 0.015D;
    private static final double THREAT_APPROACH_SPEED_SQR = 0.09D;
    private static final int PLAYER_JUMP_COOLDOWN_TICKS = 10;
    private static final float BASE_MOUNT_JUMP = 0.42F;
    private static final float MAX_MOUNT_JUMP_BONUS = 0.38F;

    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState flyingAnimationState = new AnimationState();

    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> DATA_INTERACTION_STATE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(HippogriffEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BYTE);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_INTERACTION_TICKS =
            net.minecraft.network.syncher.SynchedEntityData.defineId(HippogriffEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    public enum InteractionState {
        NONE, BOWING;

        private static final InteractionState[] VALUES = values();

        public static InteractionState fromByte(byte b) {
            if (b < 0 || b >= VALUES.length) return NONE;
            return VALUES[b];
        }
    }

    public static final int BOW_DURATION_TICKS = 50;
    private static final int BOW_COOLDOWN_TICKS = 100;
    private static final double BOW_TRIGGER_RANGE_SQR = 16.0D;

    private int bowCooldown;
    /** Player she has bowed back to. Cleared if they attack her. */
    @Nullable
    private java.util.UUID greetedPlayer;
    private float wingFlap;
    private float wingFlapO;
    private float flightProgress;
    private float flightProgressO;
    private float lastFlightProgress;
    private float landingImpulse;     // 0..1, briefly spikes when transitioning flight→ground
    private float landingImpulseO;
    private float sitProgress;        // 0..1, eases the model in and out of the folded sitting pose
    private float sitProgressO;
    private int wingBeatCooldown;
    private int snortCooldown;
    private float playerJumpStrength;
    private int warningSoundCooldown;
    private int playerJumpCooldown;

    private final net.minecraft.world.entity.ai.navigation.GroundPathNavigation groundNavigation;
    private final FlyingPathNavigation airNavigation;
    private final net.minecraft.world.entity.ai.control.MoveControl groundMoveControl;
    private final net.minecraft.world.entity.ai.control.MoveControl airMoveControl;

    /** True while she is actively driving herself through the air under her own power. */
    private boolean poweredFlight;
    private float bank;            // -1 hard left .. +1 hard right, eased slowly
    private float bankO;
    private float flapPower;       // 0 pure glide .. 1 hauling herself upward
    private float flapPowerO;
    private int groundStuckTicks;
    private static final int GROUND_STUCK_TICKS = 30;

    // Flight feel. Descending trades height for speed like an elytra; climbing is work.
    private static final double FLIGHT_CRUISE = 2.2D;
    /** Height traded for airspeed per tick when pointed straight down, and back on the climb. */
    private static final double FLIGHT_GRAVITY = 0.045D;
    /** Grows with the square of speed, so it sets her terminal dive speed. */
    private static final double FLIGHT_DRAG = 0.013D;
    /**
     * How hard her wings pull, per tick, when she is below cruise. This has to beat
     * FLIGHT_GRAVITY or she can never sustain a climb at all - she just decays to the
     * minimum speed and crawls upward, which is exactly what it used to do.
     */
    private static final double FLAP_THRUST = 0.058D;
    private static final double MIN_FLIGHT_SPEED = 0.30D;
    /** Hard ceiling on airspeed, so a long dive cannot run away with her. */
    private static final double MAX_FLIGHT_SPEED = 2.0D;
    /** She wanders at an easier pace than she carries a rider. */
    private static final double AI_CRUISE_SCALE = 0.55D;
    /** Low value = heavy inertia, so she banks through wide arcs instead of turning on a pin. */
    private static final double FLIGHT_RESPONSE = 0.11D;
    private static final float MAX_YAW_RATE = 5.5F;
    /** Degrees of yaw change per tick that counts as a full-commitment bank. */
    private static final float FULL_BANK_YAW_RATE = 3.2F;
    /** Deliberately slow, so the roll reads as a decision rather than a twitch. */
    private static final float BANK_EASE = 0.035F;
    /** A shade snappier than her own flying, so rider input does not feel sluggish. */
    private static final double RIDDEN_FLIGHT_RESPONSE = 0.24D;
    /** Degrees of body roll at full bank. Lives here, not in the model: common code needs it. */
    public static final float BANK_BODY_ROLL_DEGREES = 35.0F;
    /** Carrying somebody, she keeps it flatter - a rider does not lean with her. */
    private static final float RIDDEN_TILT_FRACTION = 0.45F;
    private static final double SEAT_HEIGHT = 1.55D;
    private static final double SEAT_OFFSET = -0.25D;

    public HippogriffEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        // Two movement rigs. On foot she walks and jumps like any land animal; only real
        // flight swaps in the flying pair. Driving ground movement through the flying rig
        // is what made her drift over obstacles instead of stepping and jumping over them.
        this.groundNavigation = (net.minecraft.world.entity.ai.navigation.GroundPathNavigation) this.navigation;
        this.groundMoveControl = this.moveControl;
        this.airNavigation = new FlyingPathNavigation(this, level);
        this.airNavigation.setCanOpenDoors(false);
        this.airNavigation.setCanFloat(true);
        this.airNavigation.setCanPassDoors(true);
        this.airMoveControl = new FlyingMoveControl(this, 20, true);
        // The default look control zeroes xRot every tick, which would wipe out the body
        // pitch that makes flight read as flight. Leave her pitch alone while she is flying.
        this.lookControl = new net.minecraft.world.entity.ai.control.LookControl(this) {
            @Override
            protected boolean resetXRotOnTick() {
                return !HippogriffEntity.this.isFlightPosture();
            }
        };
    }

    /** True when she is holding a flying attitude and her pitch/roll should be left alone. */
    public boolean isFlightPosture() {
        return this.poweredFlight || !this.onGround();
    }

    /**
     * Whether she is free to glance about. While she is being ridden or is in the air she
     * is not: LookAtPlayerGoal picks the nearest player, which when mounted is the rider at
     * zero range, so she spent the whole flight craning her head round to stare backwards.
     */
    private boolean canLookAround() {
        return !this.isVehicle() && !this.poweredFlight && this.onGround();
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_INTERACTION_STATE, (byte) InteractionState.NONE.ordinal());
        builder.define(DATA_INTERACTION_TICKS, 0);
    }

    public InteractionState getInteractionState() {
        return InteractionState.fromByte(this.entityData.get(DATA_INTERACTION_STATE));
    }

    public int getInteractionTicks() {
        return this.entityData.get(DATA_INTERACTION_TICKS);
    }

    private void setInteractionState(InteractionState state) {
        this.entityData.set(DATA_INTERACTION_STATE, (byte) state.ordinal());
        this.entityData.set(DATA_INTERACTION_TICKS, 0);
    }

    /** Lore-accurate respect bow: triggers on a tame hippogriff when a player approaches calmly within range. */
    private void tryBowToCalmPlayer() {
        if (this.bowCooldown > 0 || this.getInteractionState() != InteractionState.NONE) return;
        if (this.isOrderedToSit() || this.isVehicle() || !this.onGround()) return;
        Player nearestPlayer = this.level().getNearestPlayer(this, 4.0D);
        if (nearestPlayer == null) return;
        if (this.distanceToSqr(nearestPlayer) > BOW_TRIGGER_RANGE_SQR) return;
        if (!this.isCalmApproach(nearestPlayer)) return;
        if (!this.hasLineOfSight(nearestPlayer)) return;
        // Tamed hippogriffs always bow back to their owner's calm approach; wild ones bow once
        // when a player is calm-approaching for tameing (signaling acceptance).
        if (this.isTame() && !this.isOwnedBy(nearestPlayer)) return;
        setInteractionState(InteractionState.BOWING);
        this.bowCooldown = BOW_COOLDOWN_TICKS + BOW_DURATION_TICKS;
        // Returning the bow *is* acceptance: she will not round on this player again unless
        // they draw blood. Without this she would go for them the moment they stood up,
        // because standing counts as a threatening approach.
        this.greetedPlayer = nearestPlayer.getUUID();
        if (this.getTarget() == nearestPlayer) {
            this.setTarget(null);
        }
        this.setLastHurtByMob(null);
        this.playSound(com.hogwartsmagic.ModSounds.HIPPOGRIFF_BOW.get(), 0.9F, 0.95F + this.random.nextFloat() * 0.15F);
    }

    /** True once she has bowed back to this player and they have not attacked her since. */
    public boolean hasGreeted(Player player) {
        return this.greetedPlayer != null && this.greetedPlayer.equals(player.getUUID());
    }

    private void tickInteractionState() {
        if (this.bowCooldown > 0) this.bowCooldown--;
        InteractionState state = getInteractionState();
        int t = getInteractionTicks();
        if (state == InteractionState.BOWING) {
            if (t >= BOW_DURATION_TICKS) {
                setInteractionState(InteractionState.NONE);
            } else {
                this.entityData.set(DATA_INTERACTION_TICKS, t + 1);
            }
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 34.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FLYING_SPEED, 0.50D)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.FOLLOW_RANGE, 28.0D)
                // Long-legged enough to walk straight up a block, like a horse. Without this
                // she has to jump every kerb and wedges herself against them.
                .add(Attributes.STEP_HEIGHT, 1.1D);
    }

    public static boolean canSpawn(EntityType<HippogriffEntity> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return checkMobSpawnRules(type, level, spawnType, pos, random);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new PanicGoal(this, 1.25D));
        this.goalSelector.addGoal(3, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new TemptGoal(this, 1.1D, Ingredient.of(Items.RABBIT), false));
        // Intentionally no FollowOwnerGoal: tamed hippogriffs stay where you leave them.
        this.goalSelector.addGoal(6, new MeleeAttackGoal(this, 1.2D, true));
        // Flight outranks strolling: when the flight goal decides to launch it wins the
        // MOVE slot, and when it declines the stroll goal takes over as usual.
        this.goalSelector.addGoal(7, new HippogriffFreeFlightGoal(this));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F) {
            @Override
            public boolean canUse() {
                return HippogriffEntity.this.canLookAround() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return HippogriffEntity.this.canLookAround() && super.canContinueToUse();
            }
        });
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return HippogriffEntity.this.canLookAround() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return HippogriffEntity.this.canLookAround() && super.canContinueToUse();
            }
        });

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(4, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, Monster.class, false));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        // Ground rig by default: this is what lets her step and jump over a block rather
        // than lifting off to clear it.
        net.minecraft.world.entity.ai.navigation.GroundPathNavigation navigation =
                new net.minecraft.world.entity.ai.navigation.GroundPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    /**
     * Swaps her between the walking rig and the flying rig. Only real flight gets zero
     * gravity; while she is on foot she is an ordinary heavy animal that has to jump.
     */
    public void setPoweredFlight(boolean value) {
        if (this.poweredFlight == value) {
            return;
        }
        this.poweredFlight = value;
        this.navigation.stop();
        this.navigation = value ? this.airNavigation : this.groundNavigation;
        this.moveControl = value ? this.airMoveControl : this.groundMoveControl;
        this.setNoGravity(value);
    }

    public boolean isPoweredFlight() {
        return this.poweredFlight;
    }

    /**
     * Bird-like steering toward a point in the air.
     *
     * <p>Speed is not constant: pointing downhill she accelerates the way an elytra does,
     * and pointing uphill she bleeds speed and has to pay for the climb with wingbeats.
     * The low blend factor gives her real mass, so she sweeps through turns.
     */
    public void steerFlight(Vec3 target, double speedScale) {
        Vec3 delta = target.subtract(this.position());
        double distance = delta.length();
        if (distance < 1.0E-4D) {
            return;
        }
        Vec3 direction = delta.scale(1.0D / distance);
        applyFlightPhysics(direction,
                this.getAttributeValue(Attributes.FLYING_SPEED) * FLIGHT_CRUISE * speedScale,
                FLIGHT_RESPONSE);
        faceFlightPath();
    }

    /**
     * Energy-based flight, the way an elytra behaves.
     *
     * <p>Airspeed is carried from tick to tick rather than recomputed from the current
     * heading, so a dive banks real momentum and pulling up afterwards spends it climbing
     * instead of throwing it away. Steering only turns the velocity vector; it never
     * rewrites its length.
     */
    private void applyFlightPhysics(Vec3 heading, double cruise, double turnResponse) {
        Vec3 motion = this.getDeltaMovement();
        double speed = motion.length();
        Vec3 currentDirection = speed > 1.0E-4D ? motion.scale(1.0D / speed) : heading;

        // Nose down converts height into speed; nose up spends it again.
        speed += -heading.y * FLIGHT_GRAVITY;
        speed -= speed * speed * FLIGHT_DRAG;
        if (speed < cruise) {
            speed = Math.min(cruise, speed + FLAP_THRUST);
        }
        speed = Mth.clamp(speed, MIN_FLIGHT_SPEED, MAX_FLIGHT_SPEED);

        Vec3 blended = currentDirection.scale(1.0D - turnResponse).add(heading.scale(turnResponse));
        if (blended.lengthSqr() < 1.0E-6D) {
            blended = heading;
        }
        this.setDeltaMovement(blended.normalize().scale(speed));
    }

    /** Points her along her own velocity, turning at a limited rate so the bank has time to build. */
    private void faceFlightPath() {
        Vec3 motion = this.getDeltaMovement();
        double horizontal = motion.horizontalDistance();
        if (horizontal < 1.0E-4D) {
            return;
        }
        float wantedYaw = (float) (Mth.atan2(motion.z, motion.x) * (180.0D / Math.PI)) - 90.0F;
        this.setYRot(approachDegrees(this.getYRot(), wantedYaw, MAX_YAW_RATE));
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
        float wantedPitch = (float) (-(Mth.atan2(motion.y, horizontal) * (180.0D / Math.PI)));
        this.setXRot(approachDegrees(this.getXRot(), Mth.clamp(wantedPitch, -60.0F, 60.0F), 4.0F));
    }

    private static float approachDegrees(float current, float target, float maxStep) {
        float difference = Mth.degreesDifference(current, target);
        return current + Mth.clamp(difference, -maxStep, maxStep);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.RABBIT);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (this.isFood(stack) && !this.isTame()) {
            // A player she has already bowed to may feed her standing; everyone else must crouch.
            if (!this.isCalmApproach(player) && !hasGreeted(player)) {
                if (!this.level().isClientSide) {
                    this.reactToRudeApproach(player);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }

            if (!this.level().isClientSide) {
                if (this.random.nextInt(3) == 0) {
                    this.tame(player);
                    this.navigation.stop();
                    this.setTarget(null);
                    this.setOrderedToSit(true);
                    this.level().broadcastEntityEvent(this, (byte) 7);
                } else {
                    this.level().broadcastEntityEvent(this, (byte) 6);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        if (!this.isTame() && hand == InteractionHand.MAIN_HAND) {
            if (!this.level().isClientSide) {
                this.reactToRudeApproach(player);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        if (this.isTame() && this.isOwnedBy(player)) {
            if (player.isShiftKeyDown()) {
                // Server-authoritative, like vanilla tameables: the client must not flip synched data itself.
                if (!this.level().isClientSide) {
                    this.setOrderedToSit(!this.isOrderedToSit());
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }

            if (!this.level().isClientSide && !this.isBaby()) {
                this.setOrderedToSit(false);
                player.startRiding(this);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    @Override
    public void tick() {
        super.tick();

        this.wingFlapO = this.wingFlap;
        this.flightProgressO = this.flightProgress;
        this.landingImpulseO = this.landingImpulse;
        this.sitProgressO = this.sitProgress;
        this.bankO = this.bank;
        this.flapPowerO = this.flapPower;
        this.lastFlightProgress = this.flightProgress;

        boolean airborne = this.isAirbornePose();
        this.sitProgress = Mth.approach(this.sitProgress, this.isInSittingPose() ? 1.0F : 0.0F, 0.10F);
        this.flightProgress = Mth.approach(this.flightProgress, airborne ? 1.0F : 0.0F, airborne ? 0.12F : 0.16F);
        // Landing impulse: spikes briefly when we transition from airborne to grounded so the model
        // can compress its legs as if absorbing impact.
        if (this.lastFlightProgress > 0.6F && this.flightProgress < 0.4F) {
            this.landingImpulse = 1.0F;
        } else {
            this.landingImpulse = Math.max(0.0F, this.landingImpulse - 0.10F);
        }
        // The beat only advances while she is actually working the air. On the ground it
        // stops dead, so walking no longer looks like flapping on the spot.
        this.wingFlap += airborne ? 0.22F + this.flapPower * 0.62F : 0.0F;

        if (!this.level().isClientSide) {
            tickInteractionState();
            // Wild OR tame: bow on calm approach (lore: respect the hippogriff and it respects you back).
            // Throttle the check to every 10 ticks for efficiency.
            if (this.tickCount % 10 == 0) {
                tryBowToCalmPlayer();
            }
        }
        if (this.warningSoundCooldown > 0) {
            this.warningSoundCooldown--;
        }
        if (this.playerJumpCooldown > 0) {
            this.playerJumpCooldown--;
        }
        if (!this.level().isClientSide) {
            tickFlightSounds(airborne);
        }

        if (!this.isVehicle() && !this.poweredFlight && !this.onGround() && !this.isOrderedToSit()) {
            // Not under power but off the ground: spread wings and glide down instead of
            // dropping like a stone.
            Vec3 motion = this.getDeltaMovement();
            this.setDeltaMovement(motion.x * 0.96D, Math.max(motion.y, -0.22D), motion.z * 0.96D);
        }

        if (!this.level().isClientSide) {
            tickGroundStuck();
        }
        tickFlightPosture(airborne);

        if (this.level().isClientSide) {
            this.idleAnimationState.startIfStopped(this.tickCount);
            if (airborne) {
                this.flyingAnimationState.startIfStopped(this.tickCount);
            } else {
                this.flyingAnimationState.stop();
            }
        } else if (!this.isTame() && !this.isOrderedToSit() && this.tickCount % 10 == 0) {
            Player nearestPlayer = this.level().getNearestPlayer(this, 3.0D);
            if (nearestPlayer != null && this.hasLineOfSight(nearestPlayer) && this.isThreateningApproach(nearestPlayer)) {
                this.reactToRudeApproach(nearestPlayer);
            }
        }
    }

    /**
     * Wing-beat whumps while airborne, a launch cue as it leaves the ground, and the odd
     * equine snort while it is standing about. Server side only so the sound is broadcast.
     */
    private void tickFlightSounds(boolean airborne) {
        if (this.wingBeatCooldown > 0) this.wingBeatCooldown--;
        if (this.snortCooldown > 0) this.snortCooldown--;

        if (airborne && this.lastFlightProgress < 0.25F && this.flightProgress >= 0.25F) {
            this.playSound(com.hogwartsmagic.ModSounds.HIPPOGRIFF_TAKEOFF.get(), 1.0F,
                    0.95F + this.random.nextFloat() * 0.12F);
            this.wingBeatCooldown = 30;
            return;
        }

        if (airborne) {
            if (this.wingBeatCooldown <= 0) {
                this.playSound(com.hogwartsmagic.ModSounds.HIPPOGRIFF_WINGS.get(), 0.7F,
                        0.9F + this.random.nextFloat() * 0.2F);
                this.wingBeatCooldown = 16 + this.random.nextInt(6);
            }
        } else if (!this.isInSittingPose() && this.snortCooldown <= 0 && this.random.nextInt(400) == 0) {
            this.playSound(com.hogwartsmagic.ModSounds.HIPPOGRIFF_SNORT.get(), 0.75F,
                    0.9F + this.random.nextFloat() * 0.2F);
            this.snortCooldown = 200;
        }
    }

    public float getWingFlap(float partialTick) {
        return Mth.lerp(partialTick, this.wingFlapO, this.wingFlap);
    }

    public float getFlightProgress(float partialTick) {
        return Mth.lerp(partialTick, this.flightProgressO, this.flightProgress);
    }

    public float getLandingImpulse(float partialTick) {
        return Mth.lerp(partialTick, this.landingImpulseO, this.landingImpulse);
    }

    public float getSitProgress(float partialTick) {
        return Mth.lerp(partialTick, this.sitProgressO, this.sitProgress);
    }

    /** -1 hard bank left, +1 hard bank right. Eased slowly so the roll reads as deliberate. */
    public float getBank(float partialTick) {
        return Mth.lerp(partialTick, this.bankO, this.bank);
    }

    /** 0 while gliding on held wings, 1 while beating hard to climb. */
    public float getFlapPower(float partialTick) {
        return Mth.lerp(partialTick, this.flapPowerO, this.flapPower);
    }

    /** Degrees of body pitch for the renderer; positive is nose-up. */
    public float getFlightPitch(float partialTick) {
        return -Mth.rotLerp(partialTick, this.xRotO, this.getXRot());
    }

    /**
     * Degrees of body roll the renderer should apply; positive banks to her right.
     *
     * <p>The saddle position is derived from exactly this, so the two can never disagree
     * and leave the rider hanging in space beside her.
     */
    public float getVisualRoll(float partialTick) {
        return this.getBank(partialTick) * this.getFlightProgress(partialTick)
                * BANK_BODY_ROLL_DEGREES * tiltFraction();
    }

    /** Degrees of body pitch the renderer should apply; positive is nose-up. */
    public float getVisualPitch(float partialTick) {
        return Mth.clamp(getFlightPitch(partialTick), -55.0F, 55.0F)
                * this.getFlightProgress(partialTick) * tiltFraction();
    }

    private float tiltFraction() {
        return this.isVehicle() ? RIDDEN_TILT_FRACTION : 1.0F;
    }

    /**
     * Turns her actual motion into the two numbers the model animates from: how hard she is
     * beating her wings, and how far she is rolled into a turn.
     *
     * <p>Both are heavily smoothed. A bird does not snap between gliding and flapping, and
     * the roll in particular is meant to swing across slowly rather than twitch.
     */
    /**
     * Notices when she is shoving into something she cannot get round on foot.
     *
     * <p>She is wide and tall, and the ground pathfinder will happily route her at gaps she
     * does not fit through, leaving her grinding against a tree trunk. Rather than let her
     * stand there shuffling, being stuck earns her a flight over the top of it.
     */
    private void tickGroundStuck() {
        if (!this.onGround() || this.isVehicle() || this.isInSittingPose() || this.poweredFlight) {
            this.groundStuckTicks = 0;
            return;
        }
        boolean tryingToMove = !this.navigation.isDone();
        if (tryingToMove && this.horizontalCollision
                && this.getDeltaMovement().horizontalDistanceSqr() < 0.0016D) {
            this.groundStuckTicks++;
        } else {
            this.groundStuckTicks = Math.max(0, this.groundStuckTicks - 2);
        }
    }

    public boolean isGroundStuck() {
        return this.groundStuckTicks > GROUND_STUCK_TICKS;
    }

    private void tickFlightPosture(boolean airborne) {
        if (!airborne) {
            this.flapPower = Mth.approach(this.flapPower, 0.0F, 0.12F);
            this.bank = Mth.approach(this.bank, 0.0F, BANK_EASE * 2.0F);
            return;
        }

        // Climbing costs effort, level flight ticks over, a descent is a free glide.
        // Derived only from synced state, so the client works this out identically to the
        // server rather than needing it sent over the wire.
        float climbRate = (float) this.getDeltaMovement().y;
        float wantedFlap = Mth.clamp(0.18F + climbRate * 3.4F, 0.0F, 1.0F);
        // Hovering is as much work as climbing: with no forward speed to hold her up, her
        // whole weight is on her wings.
        if (this.getDeltaMovement().horizontalDistance() < 0.12D) {
            wantedFlap = Math.max(wantedFlap, 0.75F);
        }
        this.flapPower = Mth.approach(this.flapPower, wantedFlap, 0.06F);

        // Bank follows how sharply she is turning. Positive yaw change is a turn to the right.
        float yawRate = Mth.degreesDifference(this.yRotO, this.getYRot());
        float wantedBank = Mth.clamp(yawRate / FULL_BANK_YAW_RATE, -1.0F, 1.0F);
        this.bank = Mth.approach(this.bank, wantedBank, BANK_EASE);
    }

    public boolean isAirbornePose() {
        // Purely a question of whether her feet are on the ground. Including isVehicle()
        // here meant a hippogriff standing still with a rider on her back was animated as
        // though she were in flight.
        return !this.onGround();
    }

    private boolean isCalmApproach(Player player) {
        return player.isShiftKeyDown()
                && !player.isSprinting()
                && player.getDeltaMovement().horizontalDistanceSqr() <= CALM_APPROACH_SPEED_SQR;
    }

    private boolean isThreateningApproach(Player player) {
        return !player.isShiftKeyDown()
                || player.isSprinting()
                || player.getDeltaMovement().horizontalDistanceSqr() >= THREAT_APPROACH_SPEED_SQR;
    }

    private void reactToRudeApproach(Player player) {
        // Once she has bowed back, the courtesy holds: no screech, no charge.
        if (hasGreeted(player)) {
            return;
        }
        // Nor does she break off a bow she is already halfway through.
        if (this.getInteractionState() == InteractionState.BOWING) {
            return;
        }
        this.setOrderedToSit(false);
        this.setTarget(player);
        if (this.warningSoundCooldown <= 0) {
            this.playSound(com.hogwartsmagic.ModSounds.HIPPOGRIFF_SCREECH.get(), 1.0F, 0.95F + this.random.nextFloat() * 0.15F);
            this.warningSoundCooldown = 50;
        }
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        // Draw blood and the bow no longer counts for anything.
        if (source.getEntity() instanceof Player attacker && hasGreeted(attacker)) {
            this.greetedPlayer = null;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.greetedPlayer != null) {
            tag.putUUID("GreetedPlayer", this.greetedPlayer);
        }
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.greetedPlayer = tag.hasUUID("GreetedPlayer") ? tag.getUUID("GreetedPlayer") : null;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        this.swing(InteractionHand.MAIN_HAND);
        this.playSound(com.hogwartsmagic.ModSounds.HIPPOGRIFF_SCREECH.get(), 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
        return super.doHurtTarget(target);
    }

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        Entity passenger = this.getFirstPassenger();
        return passenger instanceof LivingEntity living ? living : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        // The saddle is a point on her back, so it has to travel with her: rolled by her
        // bank, pitched along her flight path, then swung round by her yaw. This used to be
        // a fixed world-space offset, which is why the rider hung in the air beside her the
        // moment she tilted.
        final float toRadians = (float) Math.PI / 180.0F;
        float roll = getVisualRoll(partialTick) * toRadians;
        float pitch = getVisualPitch(partialTick) * toRadians;
        float yaw = Mth.rotLerp(partialTick, this.yRotO, this.getYRot()) * toRadians;
        // zRot is negated: a positive Vec3.zRot tips the +X side down, which at yaw 0 is her
        // left, the opposite of the renderer's convention that positive roll banks right.
        return new Vec3(0.0D, SEAT_HEIGHT, SEAT_OFFSET)
                .zRot(-roll)
                .xRot(pitch)
                .yRot(-yaw);
    }

    @Override
    public void onPlayerJump(int jumpPower) {
        // The actual launch is handled in travel() so rider movement and lift are applied together.
    }

    @Override
    public boolean canJump() {
        return this.isAlive()
                && this.isTame()
                && !this.isBaby()
                && this.isVehicle()
                && this.playerJumpCooldown == 0
                && (this.onGround() || this.horizontalCollision);
    }

    @Override
    public void handleStartJump(int jumpPower) {
        if (!this.canJump()) {
            return;
        }

        float normalizedPower = Mth.clamp(jumpPower, 0, 90) / 90.0F;
        this.playerJumpStrength = BASE_MOUNT_JUMP + normalizedPower * MAX_MOUNT_JUMP_BONUS;
        this.hasImpulse = true;
    }

    @Override
    public void handleStopJump() {
        // Nothing to do. Hippogriffs use a single takeoff impulse instead of a charge release state.
    }

    @Override
    public void travel(Vec3 travelVector) {
        LivingEntity rider = this.getControllingPassenger();
        if (this.isAlive() && rider instanceof Player player && this.isVehicle() && this.isTame() && this.isOwnedBy(player)) {
            // Keep the previous yaw. This used to overwrite yRotO with the new yaw, which
            // zeroed the per-tick turn rate and so flattened her out through every turn -
            // the bank is measured from exactly that difference.
            float previousYaw = this.getYRot();
            this.setYRot(rider.getYRot());
            this.yRotO = previousYaw;
            this.setXRot(rider.getXRot() * 0.55F);
            this.yBodyRot = this.getYRot();
            this.yHeadRot = this.yBodyRot;

            float forward = rider.zza;
            float strafe = rider.xxa * 0.75F;
            Vec3 look = rider.getLookAngle();

            boolean launching = this.playerJumpStrength > 0.0F && (this.onGround() || this.horizontalCollision);
            if (launching) {
                Vec3 motion = this.getDeltaMovement();
                this.setDeltaMovement(motion.x, this.playerJumpStrength, motion.z);
                this.playerJumpStrength = 0.0F;
                this.playerJumpCooldown = PLAYER_JUMP_COOLDOWN_TICKS;
                this.hasImpulse = true;
            }

            if (this.onGround() && !launching) {
                // On her feet she moves at her own walking speed under normal physics, so
                // she steps and jumps over obstacles rather than levitating over them.
                this.setNoGravity(false);
                this.setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED));
                super.travel(new Vec3(strafe, travelVector.y, forward));
                return;
            }

            // Airborne. Heading comes from where the rider is looking, and speed from the
            // angle of that heading: nose down and she accelerates like an elytra, nose up
            // and the climb costs her.
            Vec3 forwardDir = look.normalize();
            Vec3 rightDir = new Vec3(look.z, 0.0D, -look.x).normalize();
            Vec3 desired = Vec3.ZERO;
            if (Math.abs(forward) > 0.01F) {
                desired = desired.add(forwardDir.scale(forward));
            }
            if (Math.abs(strafe) > 0.01F) {
                desired = desired.add(rightDir.scale(strafe));
            }

            if (desired.lengthSqr() > 1.0E-4D) {
                applyFlightPhysics(desired.normalize(),
                        this.getAttributeValue(Attributes.FLYING_SPEED) * FLIGHT_CRUISE,
                        RIDDEN_FLIGHT_RESPONSE);
            } else {
                // Hands off the reins: she holds station on beating wings. She used to keep
                // coasting forward here, so letting go of the controls never stopped her.
                Vec3 motion = this.getDeltaMovement();
                this.setDeltaMovement(motion.x * 0.78D, motion.y * 0.70D, motion.z * 0.78D);
            }

            this.setNoGravity(true);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.calculateEntityAnimation(false);
            return;
        }

        this.playerJumpStrength = 0.0F;
        if (this.poweredFlight) {
            // Her own flight is entirely velocity-driven by the flight goal. Routing it
            // through the normal travel path would stack vanilla's air friction on top and
            // bleed away the momentum a dive just earned.
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.calculateEntityAnimation(false);
            return;
        }
        this.setNoGravity(false);
        super.travel(travelVector);
    }

    @Override
    public boolean isFlying() {
        return !this.onGround();
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, net.minecraft.core.BlockPos pos) {
        // Hippogriffs do not take fall damage.
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return ModEntities.HIPPOGRIFF.get().create(level);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return com.hogwartsmagic.ModSounds.HIPPOGRIFF_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return com.hogwartsmagic.ModSounds.HIPPOGRIFF_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return com.hogwartsmagic.ModSounds.HIPPOGRIFF_DEATH.get();
    }

    /**
     * Takes the hippogriff into the air of her own accord and keeps her there.
     *
     * <p>The old goal only inherited vanilla's random-flying behaviour, which picked a
     * single nearby point and gave up as soon as it was reached, so she almost never left
     * the ground. This one launches deliberately, then chains aerial waypoints so she
     * cruises for a stretch before settling back down and resting a while.
     */
    private static class HippogriffFreeFlightGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private static final int MAX_FLIGHT_TICKS = 500;      // ~25s aloft before landing
        private static final int MIN_REST_TICKS = 400;
        private static final int LAUNCH_CHANCE = 240;         // rolled per goal tick when grounded
        private static final double KEEP_GROUNDED_RANGE = 5.0D;
        private static final double ARRIVE_RANGE = 4.0D;

        private final HippogriffEntity hippogriff;
        private Vec3 destination;
        private int airborneTicks;
        private int restTicks;

        private HippogriffFreeFlightGoal(HippogriffEntity hippogriff) {
            this.hippogriff = hippogriff;
            this.setFlags(java.util.EnumSet.of(Flag.MOVE));
        }

        private boolean canFlyFreely() {
            return !this.hippogriff.isOrderedToSit()
                    && !this.hippogriff.isVehicle()
                    && !this.hippogriff.isLeashed()
                    && !this.hippogriff.isInWater()
                    && this.hippogriff.getTarget() == null;
        }

        @Override
        public boolean canUse() {
            if (!canFlyFreely()) {
                return false;
            }
            // Wedged against terrain she cannot walk round: fly over it, rest timer or not.
            if (this.hippogriff.isGroundStuck()) {
                return pickDestination();
            }
            if (this.restTicks > 0) {
                // Must not re-trigger here just because she is still airborne, or she would
                // relaunch on the tick after landing approach and never touch down again.
                this.restTicks--;
                return false;
            }
            if (!this.hippogriff.onGround()) {
                // Already in the air - walked off a ledge, knocked up - so fly properly
                // rather than drifting.
                return pickDestination();
            }
            // Stay put while somebody is right next to her, so she can still be bowed to,
            // fed and mounted instead of bolting into the sky every time you walk up.
            if (this.hippogriff.level().getNearestPlayer(this.hippogriff, KEEP_GROUNDED_RANGE) != null) {
                return false;
            }
            return this.hippogriff.getRandom().nextInt(LAUNCH_CHANCE) == 0 && pickDestination();
        }

        @Override
        public boolean canContinueToUse() {
            return canFlyFreely() && this.destination != null && this.airborneTicks < MAX_FLIGHT_TICKS;
        }

        @Override
        public void start() {
            this.airborneTicks = 0;
            this.hippogriff.setPoweredFlight(true);
            // A real shove off the ground, not a hop.
            Vec3 motion = this.hippogriff.getDeltaMovement();
            this.hippogriff.setDeltaMovement(motion.x, Math.max(motion.y, 0.62D), motion.z);
            this.hippogriff.hasImpulse = true;
        }

        @Override
        public void stop() {
            this.destination = null;
            this.hippogriff.setPoweredFlight(false);
            this.restTicks = MIN_REST_TICKS + this.hippogriff.getRandom().nextInt(400);
        }

        @Override
        public void tick() {
            this.airborneTicks++;
            if (this.destination == null) {
                return;
            }
            // Steered directly rather than pathfound. Asking the flying navigator for a
            // path to an empty point of sky mostly failed, which is why she never actually
            // left the ground; this drives her velocity toward it instead.
            this.hippogriff.steerFlight(this.destination, AI_CRUISE_SCALE);
            if (this.hippogriff.position().distanceTo(this.destination) < ARRIVE_RANGE
                    && !pickDestination()) {
                this.destination = null;
            }
        }

        /** Looks for an open point of sky a little way off and several blocks up. */
        private boolean pickDestination() {
            RandomSource random = this.hippogriff.getRandom();
            for (int attempt = 0; attempt < 8; attempt++) {
                double x = this.hippogriff.getX() + (random.nextDouble() - 0.5D) * 28.0D;
                double z = this.hippogriff.getZ() + (random.nextDouble() - 0.5D) * 28.0D;
                BlockPos ground = this.hippogriff.level().getHeightmapPos(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        BlockPos.containing(x, this.hippogriff.getY(), z));
                double y = ground.getY() + 6.0D + random.nextDouble() * 10.0D;
                if (y - this.hippogriff.getY() > 22.0D) {
                    continue;   // don't ask for a climb she cannot make in one hop
                }
                Vec3 candidate = new Vec3(x, y, z);
                if (this.hippogriff.level().noCollision(this.hippogriff,
                        this.hippogriff.getBoundingBox().move(candidate.subtract(this.hippogriff.position())))) {
                    this.destination = candidate;
                    return true;
                }
            }
            return false;
        }
    }
}
