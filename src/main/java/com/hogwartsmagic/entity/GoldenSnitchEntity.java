package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * The Golden Snitch — a tiny, blindingly fast winged ball released from its item form.
 * It flutters around its release point, darting erratically, and actively evades players.
 * It can only be caught by touching it while riding a broom, Seeker-style; on foot it
 * always slips away. Catching it awards XP and returns the item.
 *
 * <p>Movement is Bat-style: no gravity, no pathfinding — each server tick nudges the velocity
 * toward a wander target that re-rolls every second or two (instantly when a player closes in).
 * All catch/reward logic is server-side; the client only sees particles.
 */
public class GoldenSnitchEntity extends Mob {

    private static final double WANDER_RADIUS = 24.0;
    private static final double EVADE_RANGE = 9.0;
    private static final double CATCH_RANGE = 1.4;
    private static final double CRUISE_SPEED = 0.5;
    private static final double EVADE_SPEED = 0.95;
    /** Ticks after release before it can be caught. */
    private static final long RELEASE_GRACE_TICKS = 60L;

    private BlockPos anchor;
    private Vec3 wanderTarget;
    private int retargetTicks;
    private long releaseGameTime = -1;

    public GoldenSnitchEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.noCulling = true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.9D)
                .add(Attributes.FLYING_SPEED, 1.2D);
    }

    /** Set at release so the catch message can brag about chase time. */
    public void markReleased(BlockPos releasePos) {
        this.anchor = releasePos;
        this.releaseGameTime = this.level().getGameTime();
    }

    @Override
    protected void registerGoals() {
        // No goals: the snitch is not an AI mob, it's a physics toy steered in customServerAiStep.
    }

    @Override
    public void tick() {
        super.tick();
        this.setNoGravity(true);

        if (this.level().isClientSide) {
            // Golden sparkle trail.
            if (this.random.nextFloat() < 0.6f) {
                this.level().addParticle(new DustParticleOptions(new Vector3f(1.0f, 0.85f, 0.2f), 0.6f),
                        this.getX(), this.getY() + 0.15, this.getZ(),
                        0.0, 0.0, 0.0);
            }
        } else {
            tryCatch();
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.anchor == null) {
            this.anchor = this.blockPosition();
        }

        Player pursuer = nearestPursuer();
        boolean evading = pursuer != null;

        if (--this.retargetTicks <= 0 || this.wanderTarget == null
                || this.position().distanceToSqr(this.wanderTarget) < 4.0) {
            pickWanderTarget(evading ? pursuer : null);
        } else if (evading && this.retargetTicks % 5 == 0) {
            // Mid-flight jinks while being chased — the snitch never flies a straight line.
            pickWanderTarget(pursuer);
        }

        double speed = evading ? EVADE_SPEED : CRUISE_SPEED;
        Vec3 toTarget = this.wanderTarget.subtract(this.position());
        if (toTarget.lengthSqr() > 1.0e-4) {
            Vec3 desired = toTarget.normalize().scale(speed);
            // Nudge velocity toward the desired vector — snappier when evading.
            double agility = evading ? 0.35 : 0.15;
            Vec3 vel = this.getDeltaMovement();
            this.setDeltaMovement(vel.add(desired.subtract(vel).scale(agility)));
        }
        // Gentle hover bob so it never sits perfectly still.
        this.setDeltaMovement(this.getDeltaMovement().add(0.0, Math.sin(this.tickCount * 0.35) * 0.01, 0.0));

        Vec3 vel = this.getDeltaMovement();
        if (vel.horizontalDistanceSqr() > 1.0e-4) {
            this.setYRot((float) (Mth.atan2(vel.z, vel.x) * (180.0 / Math.PI)) - 90.0f);
            this.yBodyRot = this.getYRot();
        }
    }

    private Player nearestPursuer() {
        Player nearest = this.level().getNearestPlayer(this, EVADE_RANGE);
        return (nearest != null && !nearest.isSpectator() && nearest.isAlive()) ? nearest : null;
    }

    private void pickWanderTarget(Player fleeFrom) {
        this.retargetTicks = 10 + this.random.nextInt(20);
        Vec3 anchorCenter = Vec3.atCenterOf(this.anchor);

        Vec3 candidate;
        if (fleeFrom != null) {
            // Away from the pursuer with heavy random jitter — evasion, not a straight run.
            Vec3 away = this.position().subtract(fleeFrom.position());
            away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0.3, 0) : away.normalize();
            candidate = this.position()
                    .add(away.scale(6.0 + this.random.nextDouble() * 6.0))
                    .add((this.random.nextDouble() - 0.5) * 8.0,
                         (this.random.nextDouble() - 0.3) * 5.0,
                         (this.random.nextDouble() - 0.5) * 8.0);
        } else {
            candidate = anchorCenter.add(
                    (this.random.nextDouble() - 0.5) * 2.0 * WANDER_RADIUS,
                    2.0 + this.random.nextDouble() * 12.0,
                    (this.random.nextDouble() - 0.5) * 2.0 * WANDER_RADIUS);
        }

        // Leash: even while evading, curve back toward the pitch instead of escaping forever.
        if (candidate.distanceToSqr(anchorCenter) > WANDER_RADIUS * WANDER_RADIUS * 2.25) {
            candidate = anchorCenter.add(candidate.subtract(anchorCenter).normalize().scale(WANDER_RADIUS));
        }
        // Never burrow: stay above the anchor's height.
        double minY = this.anchor.getY() + 1.0;
        if (candidate.y < minY) {
            candidate = new Vec3(candidate.x, minY + this.random.nextDouble() * 4.0, candidate.z);
        }
        this.wanderTarget = candidate;
    }

    private void tryCatch() {
        // It has to get away before it can be caught. Released from a broom it would otherwise be
        // caught on the next tick, and release-and-catch would be an endless 50 XP a click.
        if (this.releaseGameTime > 0 && this.level().getGameTime() - this.releaseGameTime < RELEASE_GRACE_TICKS) {
            return;
        }
        List<Player> nearby = this.level().getEntitiesOfClass(Player.class,
                this.getBoundingBox().inflate(CATCH_RANGE),
                p -> p.isAlive() && !p.isSpectator() && p.getVehicle() instanceof BroomEntity);
        if (nearby.isEmpty()) return;
        Player catcher = nearby.get(0);

        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK,
                    this.getX(), this.getY(), this.getZ(), 60, 0.5, 0.5, 0.5, 0.15);
            sl.sendParticles(new DustParticleOptions(new Vector3f(1.0f, 0.85f, 0.2f), 1.2f),
                    this.getX(), this.getY(), this.getZ(), 40, 0.8, 0.8, 0.8, 0.0);
        }
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                ModSounds.SNITCH_CATCH.get(), SoundSource.NEUTRAL, 1.2f, 1.0f);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.8f, 1.4f);

        catcher.giveExperiencePoints(50);
        if (!catcher.addItem(new ItemStack(ExampleMod.GOLDEN_SNITCH.get()))) {
            catcher.drop(new ItemStack(ExampleMod.GOLDEN_SNITCH.get()), false);
        }

        if (catcher instanceof ServerPlayer sp) {
            com.hogwartsmagic.util.ModAdvancements.award(sp, "snitch_caught");
            sp.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
            long chaseSeconds = this.releaseGameTime > 0
                    ? Math.max(0, (this.level().getGameTime() - this.releaseGameTime) / 20)
                    : 0;
            sp.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.translatable("hogwartsmagic.snitch.chase_time", chaseSeconds)));
            sp.connection.send(new ClientboundSetTitleTextPacket(
                    Component.translatable("hogwartsmagic.snitch.caught")));
        }

        this.discard();
    }

    // ---- indestructible, unpushable, silent-footed -----------------------------------

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        // Can't be swatted down — it must be caught. Only creative/void-style damage removes it.
        return !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.SNITCH_FLUTTER.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 40;
    }

    @Override
    protected float getSoundVolume() {
        return 0.5f;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.anchor != null) {
            tag.putLong("SnitchAnchor", this.anchor.asLong());
        }
        tag.putLong("SnitchReleaseTime", this.releaseGameTime);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SnitchAnchor")) {
            this.anchor = BlockPos.of(tag.getLong("SnitchAnchor"));
        }
        if (tag.contains("SnitchReleaseTime")) {
            this.releaseGameTime = tag.getLong("SnitchReleaseTime");
        }
    }
}
