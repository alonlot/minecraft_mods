package com.hogwartsmagic.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
    private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.INT);

    public BroomEntity(EntityType<?> type, Level level) {
        super(type, level);
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
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        if (tag.contains("BroomType")) {
            this.setBroomType(tag.getInt("BroomType"));
        }
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        tag.putInt("BroomType", this.getBroomType());
    }

    public int getBroomType() {
        return this.entityData.get(DATA_TYPE);
    }

    public void setBroomType(int type) {
        this.entityData.set(DATA_TYPE, type);
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
            this.setDeltaMovement(this.getDeltaMovement().add(0, -0.04, 0));
            if (this.onGround()) {
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.8, 0, 0.8));
            }
        }

        super.tick();

        this.move(MoverType.SELF, this.getDeltaMovement());

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

        // Particles for effect
        if (this.level().isClientSide && this.getDeltaMovement().lengthSqr() > 0.01) {
            this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
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
        boolean isTurbo = this.isBoosting;

        // Base speed and smoothing 
        // 0 = Basic, 1 = Nimbus, 2 = Firebolt
        int type = this.getBroomType();
        double baseSpeed = type == 2 ? 3.0 : (type == 1 ? 2.5 : 2.0);
        double boostSpeed = type == 2 ? 10.0 : (type == 1 ? 8.0 : 6.5);
        double accelPhase = type == 2 ? 0.15 : (type == 1 ? 0.12 : 0.08);

        double targetSpeed = isTurbo ? boostSpeed : baseSpeed;
        double speedLerp = isTurbo ? accelPhase : 0.1; // Smoothly ramp up over time, or slow down quickly

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
            return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        // Tenebris / smoke flight sets the broom invisible while the rider is hidden; clear immediately
        // on dismount so a falling or dropped broom stays visible on client and server.
        this.setInvisible(false);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTicks) {
        // Restore the original centered riding feel without the floating gap.
        return new Vec3(0.0, 0.45, 0.0);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < 1;
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
