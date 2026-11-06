package com.hogwartsmagic.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;

public class BroomEntity extends Entity {

    public boolean isBoosting = false;
    private float currentVisualOffset = 0.0F;
    public float displayRoll = 0.0F;
    public float prevDisplayRoll = 0.0F;
    private boolean wasBoostingLast = false;
    // Trick state: 0 = none, 1 = barrel roll left, 2 = barrel roll right, 3 = forward loop.
    // Both sides tick down trickTicks deterministically once tryStartTrick syncs the start.
    public static final byte TRICK_NONE = 0;
    public static final byte TRICK_BARREL_LEFT = 1;
    public static final byte TRICK_BARREL_RIGHT = 2;
    public static final byte TRICK_LOOP = 3;
    public static final int TRICK_BARREL_DURATION = 16;
    public static final int TRICK_LOOP_DURATION = 28;
    public static final int TRICK_COOLDOWN = 25;
    private int trickCooldown = 0;
    public int prevTrickTicks = 0; // client-side cache for render lerp
    // Stamina: 0..100. Drains while boosting, recovers when not. Below 0 = boost locked until > 30.
    public static final int STAMINA_MAX = 100;
    public static final int STAMINA_RECOVERY_THRESHOLD = 30;
    private float staminaF = STAMINA_MAX; // server-authoritative; the synced byte mirrors floor(staminaF).
    private boolean boostLocked = false;
    private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_BOOSTING = SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> DATA_TRICK = SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_TRICK_TICKS = SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_STAMINA = SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.BYTE);
    /**
     * The stamina lock, synced: boostLocked is server-only, and the rider's client decides when to send a boost
     * packet from canBoost(). Without this the client never saw the lock and re-sent boost the moment the stamina
     * byte read 1%, which the server accepted, so the "locked until 30%" rule never held.
     */
    private static final EntityDataAccessor<Boolean> DATA_BOOST_LOCKED = SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.BOOLEAN);
    /** Alatus quality (AlatusRules.QUALITY_*). Synced: while ridden, the rider's client runs the flight physics. */
    private static final EntityDataAccessor<Integer> DATA_QUALITY = SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.INT);

    public BroomEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    /**
     * Server only, never saved: the player whose Flying memory has this broom parked. A held broom stays exactly
     * where it is (no gravity, no motion) and nobody can mount it, until {@link #releaseHold}.
     */
    @javax.annotation.Nullable
    private java.util.UUID heldFor;

    public void holdFor(java.util.UUID player) {
        this.heldFor = player;
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
    }

    public void releaseHold() {
        this.heldFor = null;
    }

    public boolean isHeld() {
        return this.heldFor != null;
    }
    
    @Override
    public EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        // Broom logic: 1.5 blocks long, 0.5 blocks high
        return EntityDimensions.scalable(1.5f, 0.5f);
    }
    
    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TYPE, 0); // 0 = Basic, 1 = Nimbus 2000, 2 = Firebolt
        builder.define(DATA_BOOSTING, false);
        builder.define(DATA_TRICK, TRICK_NONE);
        builder.define(DATA_TRICK_TICKS, 0);
        builder.define(DATA_STAMINA, (byte) STAMINA_MAX);
        builder.define(DATA_BOOST_LOCKED, false);
        builder.define(DATA_QUALITY, 0);
    }

    public boolean isBoostingSynced() {
        return this.entityData.get(DATA_BOOSTING);
    }

    public void setBoostingSynced(boolean v) {
        this.entityData.set(DATA_BOOSTING, v);
    }

    public byte getTrickType() {
        return this.entityData.get(DATA_TRICK);
    }

    public int getTrickTicksSynced() {
        return this.entityData.get(DATA_TRICK_TICKS);
    }

    public int getStaminaPercent() {
        return this.entityData.get(DATA_STAMINA) & 0xFF;
    }

    /** Returns true when there is enough stamina available to engage boost. */
    public boolean canBoost() {
        return !this.entityData.get(DATA_BOOST_LOCKED) && this.getStaminaPercent() > 0;
    }

    public void tryStartTrick(byte trickType) {
        if (trickType <= TRICK_NONE || trickType > TRICK_LOOP) return;
        if (this.trickCooldown > 0 || this.getTrickTicksSynced() > 0) return;
        if (!this.isVehicle()) return;
        int dur = trickDurationFor(trickType);
        this.entityData.set(DATA_TRICK, trickType);
        this.entityData.set(DATA_TRICK_TICKS, dur);
        // Whoosh on trick start.
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    public static int trickDurationFor(byte trickType) {
        return trickType == TRICK_LOOP ? TRICK_LOOP_DURATION : TRICK_BARREL_DURATION;
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        if (tag.contains("BroomType")) {
            this.setBroomType(tag.getInt("BroomType"));
        }
        this.setQuality(tag.getInt("Quality"));
        // Absent (brooms saved before the flag, /summon, NPC brooms): not a pickup, so punching is no broom farm.
        this.dropsItem = tag.getBoolean("DropsItem");
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        tag.putInt("BroomType", this.getBroomType());
        tag.putInt("Quality", this.getQuality());
        tag.putBoolean("DropsItem", this.dropsItem);
    }

    /**
     * True for a broom a player placed from its item. Only those turn back into an item when
     * punched; an NPC wizard broom just breaks, so wizards are not a free broom farm.
     */
    private boolean dropsItem;

    public void setDropsItem(boolean dropsItem) {
        this.dropsItem = dropsItem;
    }

    /** Punching an empty broom picks it up again (as its item, keeping its Alatus quality). */
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved() || this.isVehicle() || this.heldFor != null
                || this.isInvulnerableTo(source)) {
            return false;
        }
        if (!(source.getEntity() instanceof Player player) || source.getDirectEntity() != player) {
            return false;
        }
        if (!this.dropsItem) {
            return false; // an NPC or summoned broom is not anyone's to take apart
        }
        if (this.dropsItem && !player.getAbilities().instabuild
                && this.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOENTITYDROPS)) {
            net.minecraft.world.item.Item item;
            if (this.getBroomType() == 1) {
                item = com.hogwartsmagic.ExampleMod.NIMBUS_2000.get();
            } else if (this.getBroomType() == 2) {
                item = com.hogwartsmagic.ExampleMod.FIREBOLT.get();
            } else {
                item = com.hogwartsmagic.ExampleMod.BROOM.get();
            }
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
            com.hogwartsmagic.item.BroomItem.setQuality(stack, this.getQuality());
            this.spawnAtLocation(stack);
        }
        this.discard();
        return true;
    }

    public int getBroomType() {
        return this.entityData.get(DATA_TYPE);
    }

    public void setBroomType(int type) {
        this.entityData.set(DATA_TYPE, type);
    }

    public int getQuality() {
        return this.entityData.get(DATA_QUALITY);
    }

    public void setQuality(int quality) {
        this.entityData.set(DATA_QUALITY, com.hogwartsmagic.spell.motion.AlatusRules.clampQuality(quality));
    }

    @Override
    public Component getName() {
        return switch (this.getBroomType()) {
            case 1 -> Component.translatable("entity.hogwartsmagic.broom.nimbus_2000");
            case 2 -> Component.translatable("entity.hogwartsmagic.broom.firebolt");
            default -> Component.translatable("entity.hogwartsmagic.broom.training");
        };
    }

    @Override
    @javax.annotation.Nullable
    public LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof LivingEntity e ? e : null;
    }

    @Override
    public void tick() {
        this.debugTick(); // TEMPORARY DEBUG
        this.prevDisplayRoll = this.displayRoll;
        if (this.heldFor != null && !this.level().isClientSide) {
            // Parked for a memory: not a step of flight, gravity or damping, so nothing can move it.
            this.setNoGravity(true);
            this.setDeltaMovement(Vec3.ZERO);
            this.fallDistance = 0.0f;
            super.tick();
            return;
        }
        float yawBefore = this.getYRot();

        if (this.isVehicle()) {
            this.setNoGravity(true);
            Entity passenger = this.getFirstPassenger();
            if (passenger instanceof LivingEntity living) {
                // Smoothly pull the broom's yaw towards the player's body yaw,
                // avoiding instant snapping to prevent camera flickering.
                this.yRotO = this.getYRot();
                this.xRotO = this.getXRot();
                
                float targetYaw = living.getYHeadRot();
                if (living instanceof WizardEntity) {
                    // For AI riders, orient the broom with movement direction to prevent awkward body-only aiming.
                    Vec3 horizontalMotion = this.getDeltaMovement();
                    if (horizontalMotion.horizontalDistanceSqr() > 1.0E-3) {
                        targetYaw = (float) (net.minecraft.util.Mth.atan2(horizontalMotion.z, horizontalMotion.x) * (180.0 / Math.PI)) - 90.0F;
                    }
                    this.currentVisualOffset += net.minecraft.util.Mth.wrapDegrees(0.0F - this.currentVisualOffset) * 0.2F;
                } else {
                    float targetOffset = 0.0F;
                    if (Math.abs(living.xxa) > 0.01f || Math.abs(living.zza) > 0.01f) {
                        // Calculate visual turn angle based on movement keys (A, D, S, W)
                        targetOffset = (float) Math.toDegrees(Math.atan2(-living.xxa, living.zza));
                    }

                    // Smoothly lerp our current visual offset towards the target offset
                    this.currentVisualOffset += net.minecraft.util.Mth.wrapDegrees(targetOffset - this.currentVisualOffset) * 0.15F;
                    targetYaw += this.currentVisualOffset;
                }
                
                this.setYRot(this.getYRot() + net.minecraft.util.Mth.wrapDegrees(targetYaw - this.getYRot()) * 0.2F);
                this.setXRot(living.getXRot() * 0.5F);

                // Reset fall distance to prevent massive damage on landing
                living.fallDistance = 0.0f;
                this.fallDistance = 0.0f;

                // Run flight logic on both sides to maintain smooth momentum
                handleServerFlight(living);
            }
        } else {
            // Reset fall distance while falling empty to avoid breaking
            this.fallDistance = 0.0f;
            this.setInvisible(false);
            
            // Apply gravity when no one is riding
            this.setNoGravity(false);
            this.xRotO = this.getXRot();
            this.setXRot(this.getXRot() + (0.0F - this.getXRot()) * 0.2F);
            // Server-authoritative once riderless: with nobody driving, a client that keeps
            // integrating its own gravity just re-diverges from the server between sync packets.
            if (!this.level().isClientSide) {
                this.setDeltaMovement(this.getDeltaMovement().add(0, -0.04, 0));
                if (this.onGround()) {
                    this.setDeltaMovement(this.getDeltaMovement().multiply(0.8, 0, 0.8));
                }
            }
        }

        super.tick();

        // Only integrate movement on the side that owns it: the client while it is being ridden
        // (it drives the vehicle), the server otherwise.
        if (this.isVehicle() || !this.level().isClientSide) {
            this.move(MoverType.SELF, this.getDeltaMovement());
        }

        if (!this.isVehicle() && this.onGround()) {
            Vec3 landedMotion = this.getDeltaMovement();
            this.setDeltaMovement(landedMotion.x * 0.8, 0.0, landedMotion.z * 0.8);
            this.setXRot(0.0F);
        }
        
        // Slightly higher damping for "standing still" feel
        if (this.getDeltaMovement().lengthSqr() < 1.0E-4) {
            this.setDeltaMovement(Vec3.ZERO);
        } else {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.94));
        }

        // Banking roll: lean into turns proportional to yaw-delta scaled by horizontal speed.
        // Computed on both sides (deterministic from yaw + velocity) so client visuals match server.
        float yawDelta = Mth.wrapDegrees(this.getYRot() - yawBefore);
        float horizSpeed = (float) this.getDeltaMovement().horizontalDistance();
        float targetRoll = Mth.clamp(-yawDelta * (1.2F + horizSpeed * 0.6F), -45.0F, 45.0F);
        // No passenger → relax to level.
        if (!this.isVehicle()) {
            targetRoll = 0.0F;
        }
        this.displayRoll += (targetRoll - this.displayRoll) * 0.22F;

        // Snapshot trickTicks for client-side render lerp (must happen before server decrement).
        this.prevTrickTicks = this.getTrickTicksSynced();
        if (!this.level().isClientSide) {
            int t = this.getTrickTicksSynced();
            if (t > 0) {
                t--;
                this.entityData.set(DATA_TRICK_TICKS, t);
                if (t == 0) {
                    this.entityData.set(DATA_TRICK, TRICK_NONE);
                    this.trickCooldown = TRICK_COOLDOWN;
                }
            } else if (this.trickCooldown > 0) {
                this.trickCooldown--;
            }
        }

        // Server-side: sync boost state, manage stamina, fire transition effects, weather hazards.
        if (!this.level().isClientSide) {
            // Stamina: drain while actually moving under boost; recover otherwise.
            // The tank is sized by the drain rate, not by STAMINA_MAX: the synced value is a byte
            // the HUD reads as a percentage, so it has to stay 0..100 and a bigger tank means each
            // tick costs less. 0.347/tick → 100 stamina lasts ~288 ticks (~14.4s) of boost,
            // 60% longer than the 0.555/tick (~9s) it held before.
            // The boost packet is client input: while the tank is locked it is refused here, not just not sent.
            if (this.boostLocked) {
                this.isBoosting = false;
            }
            boolean activelyBoosting = this.isBoosting && this.isVehicle() && horizSpeed > 0.5F;
            if (activelyBoosting) {
                // A Masterwork broom's boost lasts 15% longer (Spell Mastery Phase 7b).
                this.staminaF = Math.max(0.0F, this.staminaF - 0.347F
                        * (float) com.hogwartsmagic.spell.motion.AlatusRules.staminaDrainMultiplier(this.getQuality()));
                if (this.staminaF <= 0.0F) {
                    this.boostLocked = true;
                    this.isBoosting = false; // force-release boost when empty
                }
            } else {
                // Faster recovery while not boosting at all; slow recovery while still holding boost but stationary.
                float rate = this.isBoosting ? 0.35F : 0.7F;
                this.staminaF = Math.min(STAMINA_MAX, this.staminaF + rate);
                if (this.boostLocked && this.staminaF >= STAMINA_RECOVERY_THRESHOLD) {
                    this.boostLocked = false;
                }
            }
            byte staminaByte = (byte) Mth.clamp((int) this.staminaF, 0, STAMINA_MAX);
            if (this.entityData.get(DATA_STAMINA) != staminaByte) {
                this.entityData.set(DATA_STAMINA, staminaByte);
            }
            if (this.entityData.get(DATA_BOOST_LOCKED) != this.boostLocked) {
                this.entityData.set(DATA_BOOST_LOCKED, this.boostLocked);
            }

            if (this.isBoostingSynced() != this.isBoosting) {
                this.setBoostingSynced(this.isBoosting);
            }
            if (this.isBoosting && !this.wasBoostingLast) {
                // Boost-on whoosh — custom magical broom acceleration surge.
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        com.hogwartsmagic.ModSounds.BROOM_BOOST.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
            }
            if (!this.isBoosting && this.wasBoostingLast && this.boostLocked) {
                // Stamina-out sound: the broom's boost sputters and winds down.
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        com.hogwartsmagic.ModSounds.BROOM_STALL.get(), SoundSource.PLAYERS, 0.7F, 1.0F);
            }
            this.wasBoostingLast = this.isBoosting;

            // Weather hazard: at high altitude during a thunderstorm, low chance of lightning strike.
            if (this.isVehicle()
                    && this.level().isThundering()
                    && this.getY() > 180.0D
                    && this.random.nextFloat() < 0.0006F
                    && this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(serverLevel);
                if (bolt != null) {
                    bolt.moveTo(this.getX(), this.getY(), this.getZ());
                    serverLevel.addFreshEntity(bolt);
                }
            }
        }

        // Client-side particle effects.
        if (this.level().isClientSide) {
            spawnFlightParticles(horizSpeed);
        }
    }

    private void spawnFlightParticles(float horizSpeed) {
        if (horizSpeed < 0.06F) {
            return;
        }
        int type = this.getBroomType();
        boolean boosting = this.isBoostingSynced();

        // Trail emitter sits at the twigs end (behind the broom in entity-local +Z).
        float yawRad = this.getYRot() * ((float) Math.PI / 180F);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        // Local +Z (twigs end) in world: when broom yaw=0 the model points -Z, so +Z local is behind.
        double trailX = this.getX() + sin * 1.3D;
        double trailZ = this.getZ() - cos * 1.3D;
        double trailY = this.getY() + 0.1D;

        // Backward velocity to streak particles behind us.
        Vec3 vel = this.getDeltaMovement();
        double backX = -vel.x * 0.55D;
        double backZ = -vel.z * 0.55D;
        double backY = -vel.y * 0.25D;

        // Density scales with speed; capped so we don't flood at boost.
        float intensity = Mth.clamp(horizSpeed * 0.55F + (boosting ? 0.6F : 0.0F), 0.0F, 1.6F);
        // With the speed effects on, the bristle contrail ribbon (client.fx.broom) carries the trail and the wind
        // streaks carry the speed, so the particles shrink to a few sparks and the cheap smoke and pixels go.
        boolean ribbon = com.hogwartsmagic.fx.FxConfig.get(com.hogwartsmagic.fx.FxConfig.BROOM_SPEED_FX);
        int trailEmits = ribbon ? (this.random.nextFloat() < 0.5F ? 1 : 0) + (int) (intensity * 0.8F)
                : 1 + (int) (intensity * 2.5F);

        ParticleOptions trail;
        if (type == 2) {
            trail = ParticleTypes.FLAME; // Firebolt — orange flame.
        } else if (type == 1) {
            trail = ParticleTypes.SOUL_FIRE_FLAME; // Nimbus 2000 — blue magical wisp.
        } else {
            trail = ribbon ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.CAMPFIRE_COSY_SMOKE; // Training — smoke / pale sparks.
        }

        for (int i = 0; i < trailEmits; i++) {
            double jx = (this.random.nextDouble() - 0.5D) * 0.25D;
            double jy = (this.random.nextDouble() - 0.5D) * 0.18D;
            double jz = (this.random.nextDouble() - 0.5D) * 0.25D;
            this.level().addParticle(trail, trailX + jx, trailY + jy, trailZ + jz, backX, backY, backZ);
        }

        // Speed-line streaks while boosting — bright end-rod pixels shooting straight backward.
        if (boosting && !ribbon) {
            int streakEmits = 2 + (int) (horizSpeed * 0.6F);
            for (int i = 0; i < streakEmits; i++) {
                double offX = (this.random.nextDouble() - 0.5D) * 1.4D;
                double offY = (this.random.nextDouble() - 0.5D) * 0.6D + 0.15D;
                double offZ = (this.random.nextDouble() - 0.5D) * 1.4D;
                this.level().addParticle(
                        ParticleTypes.END_ROD,
                        trailX + offX, trailY + offY, trailZ + offZ,
                        backX * 2.6D, backY, backZ * 2.6D);
            }
            // Crit sparkles around the rider for extra punch.
            if (this.random.nextFloat() < 0.4F) {
                this.level().addParticle(
                        ParticleTypes.CRIT,
                        this.getX() + (this.random.nextDouble() - 0.5D) * 0.6D,
                        this.getY() + 0.5D + (this.random.nextDouble() - 0.5D) * 0.4D,
                        this.getZ() + (this.random.nextDouble() - 0.5D) * 0.6D,
                        backX, backY, backZ);
            }
        }
    }

    private void handleClientFlight(LivingEntity pilot) {
        // We rely on the pilot's input (handled on server via setDeltaMovement if we sync it)
        // In simple mods, we can just check pilot's move forward/sideways
    }

    private void handleServerFlight(LivingEntity pilot) {
        if (pilot instanceof WizardEntity wizard) {
            handleWizardFlight(wizard);
            return;
        }

        handlePlayerFlight(pilot);
    }

    private void handlePlayerFlight(LivingEntity pilot) {
        // Desired velocity from input
        Vec3 look = pilot.getLookAngle().normalize();
        
        // Since players don't naturally sync their xxa/zza natively to custom entities without packets in some versions,
        // we can estimate forward movement purely based on if they are looking and holding shift, or use the client inputs directly.
        float forward = pilot.zza; // -1..1
        float strafe = pilot.xxa;  // -1..1
        
        // Use the custom packet's state for exclusively boosting! (Shift is now back to purely dismount)
        // The rider's client drives the broom, but only the server receives BroomBoostPacket, so on
        // the client the plain field is always false. Read the synced flag there.
        boolean isTurbo = this.level().isClientSide ? this.isBoostingSynced() : this.isBoosting;

        // Base speed and smoothing 
        // 0 = Basic, 1 = Nimbus, 2 = Firebolt
        int type = this.getBroomType();
        // Spell Mastery Phase 7b: a Fine broom is 5% faster, a Masterwork 10%.
        double qualitySpeed = com.hogwartsmagic.spell.motion.AlatusRules.speedMultiplier(this.getQuality());
        double baseSpeed = (type == 2 ? 3.0 : (type == 1 ? 2.5 : 2.0)) * qualitySpeed;
        double boostSpeed = (type == 2 ? 10.0 : (type == 1 ? 8.0 : 6.5)) * qualitySpeed;
        double accelPhase = type == 2 ? 0.15 : (type == 1 ? 0.12 : 0.08);

        double targetSpeed = isTurbo ? boostSpeed : baseSpeed;
        double speedLerp = isTurbo ? accelPhase : 0.1; // Smoothly ramp up over time, or slow down quickly

        // Weather: rain saps top speed and dampens acceleration. Thunderstorm makes it worse.
        if (this.level().isRaining()) {
            double weatherMul = this.level().isThundering() ? 0.7 : 0.82;
            targetSpeed *= weatherMul;
            speedLerp *= 0.85;
        }

        double currentLen = this.getDeltaMovement().horizontalDistance();
        // Approach the target speed if moving input
        if (Math.abs(forward) > 0.01f || Math.abs(strafe) > 0.01f) {
            currentLen += (targetSpeed - currentLen) * speedLerp;
        } else {
            currentLen *= 0.9;
        }

        Vec3 desired = Vec3.ZERO;
        if (Math.abs(forward) > 0.01f) {
            desired = desired.add(look.scale(Math.signum(forward)));
        }

        // Strafe horizontally relative to LOOK direction
        if (Math.abs(strafe) > 0.01f) {
            Vec3 horiz = new Vec3(look.z, 0, -look.x).normalize();
            desired = desired.add(horiz.scale(Math.signum(strafe)));
        }

        // Normalize and scale by our target speed length to prevent diagonal boosting!
        if (desired.lengthSqr() > 0.01) {
            desired = desired.normalize().scale(currentLen);
        }

        Vec3 current = this.getDeltaMovement();
        
        // DRIFT PHYSICS:
        // Instead of instantly changing our velocity to `desired`, we blend it over time.
        // `momentumRetention` defines how "slippery" the broom is. High = drifty, Low = snappy.
        double momentumRetention = 0.85;
        // Rain pushes steering toward sluggish: more momentum retained → less responsive.
        if (this.level().isRaining()) {
            momentumRetention = this.level().isThundering() ? 0.93 : 0.90;
        }

        Vec3 next = current.scale(momentumRetention).add(desired.scale(1.0 - momentumRetention));

        // Cap absolute maximum speed
        double len = next.length();
        if (len > targetSpeed + 2.0) {
            next = next.scale((targetSpeed + 2.0) / len);
        }

        // Small damping when no input at all to eventually stop
        if (Math.abs(forward) < 0.01f && Math.abs(strafe) < 0.01f) {
            next = next.scale(0.9);
        }

        this.setDeltaMovement(next);
    }

    private void handleWizardFlight(WizardEntity wizard) {
        LivingEntity target = wizard.getTarget();
        Vec3 current = this.getDeltaMovement();

        int type = this.getBroomType();
        double cruiseSpeed = type == 2 ? 3.6 : (type == 1 ? 3.1 : 2.6);
        double combatSpeed = type == 2 ? 4.8 : (type == 1 ? 4.1 : 3.5);
        double chaseSpeed = type == 2 ? 6.2 : (type == 1 ? 5.4 : 4.6);
        double escapeSpeed = type == 2 ? 7.2 : (type == 1 ? 6.3 : 5.4);

        Vec3 desiredDir = Vec3.ZERO;
        double desiredY = this.getY();
        double targetSpeed = cruiseSpeed;

        boolean lowHealth = wizard.getHealth() < wizard.getMaxHealth() * 0.45f;

        if (target != null && target.isAlive()) {
            Vec3 toTarget = target.position().subtract(this.position());
            Vec3 toTargetHoriz = new Vec3(toTarget.x, 0, toTarget.z);
            double horizDist = toTargetHoriz.length();
            Vec3 toward = horizDist > 1.0E-4 ? toTargetHoriz.scale(1.0 / horizDist) : Vec3.ZERO;
            Vec3 away = toward.scale(-1.0);
            Vec3 strafe = new Vec3(-toward.z, 0, toward.x);
            if ((wizard.getUUID().hashCode() & 1) == 0) {
                strafe = strafe.scale(-1.0);
            }

            double preferredRange = 11.0;

            if (lowHealth) {
                desiredDir = away.add(strafe.scale(0.45));
                targetSpeed = escapeSpeed;
                desiredY = target.getY() + 8.0;
            } else if (horizDist > preferredRange + 5.0) {
                desiredDir = toward;
                targetSpeed = chaseSpeed;
                desiredY = target.getY() + 5.5;
            } else if (horizDist < preferredRange - 3.0) {
                desiredDir = away.add(strafe.scale(0.65));
                targetSpeed = combatSpeed;
                desiredY = target.getY() + 6.0;
            } else {
                desiredDir = strafe.add(toward.scale(0.22));
                targetSpeed = combatSpeed;
                desiredY = target.getY() + 5.5;
            }
        } else {
            // Patrol/roam in look direction if no target is active.
            Vec3 look = wizard.getLookAngle();
            desiredDir = new Vec3(look.x, 0, look.z);
            desiredY = this.getY() + (this.tickCount % 80 < 40 ? 0.35 : -0.2);
            targetSpeed = cruiseSpeed;
        }

        if (desiredDir.lengthSqr() > 1.0E-4) {
            desiredDir = desiredDir.normalize();
        } else {
            desiredDir = Vec3.ZERO;
        }

        // Basic obstacle avoidance: if we're about to hit something, bias upward and side-step.
        if (desiredDir.lengthSqr() > 1.0E-4) {
            Vec3 from = this.position().add(0, 0.5, 0);
            Vec3 to = from.add(desiredDir.scale(6.0));
            var hit = this.level().clip(new net.minecraft.world.level.ClipContext(
                    from,
                    to,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    this
            ));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                desiredDir = desiredDir.add(0, 0.0, 0).add(new Vec3(-desiredDir.z, 0, desiredDir.x).scale(0.45)).normalize();
                desiredY += 2.5;
            }
        }

        // Vertical stabilization with light damping to avoid jitter.
        double yError = desiredY - this.getY();
        double vertical = net.minecraft.util.Mth.clamp((yError * 0.09) - (current.y * 0.18), -0.34, 0.34);

        Vec3 desiredVel = desiredDir.scale(targetSpeed).add(0, vertical, 0);
        double momentumRetention = 0.86;
        Vec3 next = current.scale(momentumRetention).add(desiredVel.scale(1.0 - momentumRetention));

        double maxSpeed = targetSpeed + 1.6;
        if (next.length() > maxSpeed) {
            next = next.normalize().scale(maxSpeed);
        }

        this.setDeltaMovement(next);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.level().isClientSide) {
            boolean mounted = player.startRiding(this);
            // The Flying memory: a real player's first mount (a mob mounts with startRiding(broom, true), never here).
            if (mounted && player instanceof net.minecraft.server.level.ServerPlayer sp
                    && com.hogwartsmagic.pensieve.FlyingMemory.isRealPlayer(sp)) {
                com.hogwartsmagic.pensieve.FlyingMemory.scheduleStart(sp, this);
            }
            return mounted ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
    }

    // ---- TEMPORARY DEBUG: tracking down the broom vanishing after a mid-air dismount.
    // Delete this block (and the two call sites in tick()) once the cause is found.
    public static final boolean DEBUG_VANISH = false;
    private static final org.slf4j.Logger DBG = com.mojang.logging.LogUtils.getLogger();
    private int riderlessTicks = -1;

    private String side() {
        return this.level().isClientSide ? "CLIENT" : "SERVER";
    }

    /** The single most useful signal: if the broom disappears because it was removed,
     *  this prints which side removed it and why. */
    @Override
    public void remove(RemovalReason reason) {
        if (DEBUG_VANISH) {
            DBG.warn("[BROOM-DBG] REMOVE side={} id={} reason={} pos=({}, {}, {}) riderless={} vehicle={}",
                    side(), this.getId(), reason,
                    String.format("%.2f", this.getX()), String.format("%.2f", this.getY()),
                    String.format("%.2f", this.getZ()), this.riderlessTicks, this.isVehicle());
        }
        super.remove(reason);
    }

    private void debugTick() {
        if (!DEBUG_VANISH) return;
        if (this.isVehicle()) {
            this.riderlessTicks = -1;
            return;
        }
        this.riderlessTicks++;
        // log the first ~8 seconds after a dismount, every other tick
        if (this.riderlessTicks > 160 || (this.riderlessTicks % 2) != 0) return;
        boolean tracked = !this.level().isClientSide
                || this.level().getEntity(this.getId()) != null;
        DBG.warn("[BROOM-DBG] TICK side={} id={} t={} pos=({}, {}, {}) vel={} onGround={} invis={} removed={} inClientLevel={}",
                side(), this.getId(), this.riderlessTicks,
                String.format("%.2f", this.getX()), String.format("%.2f", this.getY()),
                String.format("%.2f", this.getZ()),
                String.format("%.3f", this.getDeltaMovement().y), this.onGround(),
                this.isInvisible(), this.isRemoved(), tracked);
    }

    /** Called by BroomRenderer so we can tell "not drawn" apart from "drawn in the wrong place". */
    public void debugRendered(double camDistSqr) {
        if (!DEBUG_VANISH || this.isVehicle()) return;
        if (this.riderlessTicks < 0 || this.riderlessTicks > 160 || (this.riderlessTicks % 10) != 0) return;
        DBG.warn("[BROOM-DBG] RENDER id={} t={} pos=({}, {}, {}) camDist={}",
                this.getId(), this.riderlessTicks,
                String.format("%.2f", this.getX()), String.format("%.2f", this.getY()),
                String.format("%.2f", this.getZ()), String.format("%.1f", Math.sqrt(camDistSqr)));
    }
    // ---- end TEMPORARY DEBUG

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        // Tenebris / smoke flight sets the broom invisible while the rider is hidden; clear immediately
        // on dismount so a falling or dropped broom stays visible on client and server.
        this.setInvisible(false);

        // While a player rides, getControllingPassenger() makes the broom client-authoritative:
        // the client ignores incoming position packets and drives via ServerboundMoveVehiclePacket.
        // But the server ALSO integrates its own flight physics, so the two positions drift apart
        // over a long flight -- measured at 153 blocks. The moment the rider leaves, authority flips
        // back to the server while the client is still sitting at its stale position, so the broom
        // appears to vanish until an ordinary sync packet happens to arrive seconds later.
        // Force the correction through immediately instead of waiting for one.
        if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.getChunkSource().broadcast(this,
                    new net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket(this));
            serverLevel.getChunkSource().broadcast(this,
                    new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(this));
        }
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTicks) {
        // Restore the original centered riding feel without the floating gap.
        return new Vec3(0.0, 0.45, 0.0);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.heldFor == null && this.getPassengers().size() < 1;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(2.5D, 1.5D, 2.5D);
    }
}
