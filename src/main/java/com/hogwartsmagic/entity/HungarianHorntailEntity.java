package com.hogwartsmagic.entity;

import com.hogwartsmagic.event.ModEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * The Hungarian Horntail — a boss-tier wyvern dragon (two hind legs, two wings) modelled on the
 * Triwizard Tournament dragon. It fights primarily on the ground with bites, a spiked-tail whip and
 * cone fire-breath, occasionally taking flight to hover and dive-bomb its target with fire.
 *
 * <p>Everything authoritative (movement, damage, fire, flight, the boss bar) lives server-side; the
 * client only reads the synced attack state + tick to drive animation. See the project's
 * server/client-safety rule.
 */
public class HungarianHorntailEntity extends Monster {

    public enum AttackState {
        NONE, ROAR, BITE, TAIL_WHIP, BREATH_WINDUP, BREATH, WING_GUST, TAKEOFF, FLY, DIVE, LAND, STAGGER;

        private static final AttackState[] VALUES = values();

        public static AttackState fromByte(byte b) {
            if (b < 0 || b >= VALUES.length) return NONE;
            return VALUES[b];
        }
    }

    private static final EntityDataAccessor<Byte> DATA_ATTACK_STATE =
            SynchedEntityData.defineId(HungarianHorntailEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_STATE_TICK =
            SynchedEntityData.defineId(HungarianHorntailEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_RAGING =
            SynchedEntityData.defineId(HungarianHorntailEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_FLYING =
            SynchedEntityData.defineId(HungarianHorntailEntity.class, EntityDataSerializers.BOOLEAN);

    // --- State timings (ticks). "strike" frames are noted per-state. ---
    public static final int ROAR_DURATION = 50;
    public static final int BITE_DURATION = 18;
    public static final int BITE_STRIKE_TICK = 8;
    public static final int TAIL_WHIP_DURATION = 24;
    public static final int TAIL_WHIP_STRIKE_TICK = 12;
    public static final int BREATH_WINDUP_DURATION = 20;
    public static final int BREATH_DURATION = 40;
    public static final int WING_GUST_DURATION = 26;
    public static final int WING_GUST_STRIKE_TICK = 11;
    public static final int TAKEOFF_DURATION = 26;
    public static final int FLY_DURATION = 90;
    public static final int DIVE_DURATION = 34;
    public static final int LAND_DURATION = 18;
    public static final int STAGGER_DURATION = 16;

    private static final double BITE_RANGE = 5.5D;
    private static final double TAIL_RANGE = 6.5D;
    private static final double WING_GUST_RANGE = 6.0D;
    private static final double BREATH_RANGE = 16.0D;
    private static final double BREATH_HALF_ANGLE = 0.55D; // cosine threshold for the cone
    private static final float RAGE_HP_FRACTION = 0.45F;
    private static final double HOVER_HEIGHT = 11.0D;

    private boolean hasRoared;
    private int actionCooldown;
    private int struckThisState;
    private double hoverTargetY;

    private final ServerBossEvent bossEvent =
            new ServerBossEvent(this.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);

    public HungarianHorntailEntity(EntityType<? extends HungarianHorntailEntity> type, Level level) {
        super(type, level);
        this.xpReward = 120;
        this.setPersistenceRequired();
        this.moveControl = new net.minecraft.world.entity.ai.control.MoveControl(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 320.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.ATTACK_DAMAGE, 18.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.STEP_HEIGHT, 1.2D)
                .add(Attributes.ATTACK_KNOCKBACK, 2.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ATTACK_STATE, (byte) AttackState.NONE.ordinal());
        builder.define(DATA_STATE_TICK, 0);
        builder.define(DATA_RAGING, false);
        builder.define(DATA_FLYING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new HorntailCombatGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 24.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // -------------------------------------------------------------------------------------------
    // Synced state accessors
    // -------------------------------------------------------------------------------------------

    public AttackState getAttackState() {
        return AttackState.fromByte(this.entityData.get(DATA_ATTACK_STATE));
    }

    public int getStateTick() {
        return this.entityData.get(DATA_STATE_TICK);
    }

    public boolean isRaging() {
        return this.entityData.get(DATA_RAGING);
    }

    public boolean isFlying() {
        return this.entityData.get(DATA_FLYING);
    }

    private void setAttackState(AttackState state) {
        this.entityData.set(DATA_ATTACK_STATE, (byte) state.ordinal());
        this.entityData.set(DATA_STATE_TICK, 0);
        this.struckThisState = 0;
    }

    private boolean isAerial() {
        AttackState s = getAttackState();
        return s == AttackState.TAKEOFF || s == AttackState.FLY || s == AttackState.DIVE || s == AttackState.LAND;
    }

    private boolean isBusy() {
        AttackState s = getAttackState();
        return s != AttackState.NONE && s != AttackState.STAGGER;
    }

    /** Committed ground actions lock movement; aerial states drive their own movement. */
    private boolean groundLocked() {
        AttackState s = getAttackState();
        return s == AttackState.ROAR || s == AttackState.BITE || s == AttackState.TAIL_WHIP
                || s == AttackState.BREATH_WINDUP || s == AttackState.BREATH || s == AttackState.WING_GUST
                || s == AttackState.STAGGER;
    }

    // -------------------------------------------------------------------------------------------
    // Tick / state machine
    // -------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        // While airborne we drive velocity ourselves; clear any ground path each tick first so the
        // stroll/move-control goals can't fight the flight (this runs before super's AI step).
        if (!this.level().isClientSide && isAerial()) {
            this.getNavigation().stop();
        }
        super.tick();
        if (this.level().isClientSide) {
            return;
        }

        AttackState state = getAttackState();
        int t = getStateTick();
        this.entityData.set(DATA_STATE_TICK, t + 1);

        if (this.actionCooldown > 0) {
            this.actionCooldown--;
        }

        // Rage threshold (one-way latch): faster, more aggressive, more fire.
        if (!isRaging() && this.getHealth() <= this.getMaxHealth() * RAGE_HP_FRACTION) {
            this.entityData.set(DATA_RAGING, true);
            this.playSound(SoundEvents.ENDER_DRAGON_GROWL, 4.0F, 0.85F);
            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.FLAME,
                        this.getX(), this.getY() + this.getBbHeight() * 0.8D, this.getZ(),
                        40, 1.2D, 0.8D, 1.2D, 0.05D);
            }
        }

        driveState(state, t);

        if (groundLocked()) {
            this.getNavigation().stop();
            Vec3 v = this.getDeltaMovement();
            this.setDeltaMovement(v.x * 0.2D, v.y, v.z * 0.2D);
        }
    }

    private void driveState(AttackState state, int t) {
        if (state == AttackState.ROAR) {
            if (t == 6) this.playSound(SoundEvents.ENDER_DRAGON_GROWL, 5.0F, 0.75F);
            if (t >= ROAR_DURATION) setAttackState(AttackState.NONE);
        } else if (state == AttackState.BITE) {
            if (t == 2) this.playSound(SoundEvents.ENDER_DRAGON_HURT, 1.2F, 1.4F);
            if (t == BITE_STRIKE_TICK && struckThisState == 0) {
                struckThisState = 1;
                doBiteImpact();
            }
            if (t >= BITE_DURATION) setAttackState(AttackState.NONE);
        } else if (state == AttackState.TAIL_WHIP) {
            if (t == 3) this.playSound(SoundEvents.RAVAGER_ATTACK, 1.4F, 0.7F);
            if (t == TAIL_WHIP_STRIKE_TICK && struckThisState == 0) {
                struckThisState = 1;
                doTailImpact();
            }
            if (t >= TAIL_WHIP_DURATION) setAttackState(AttackState.NONE);
        } else if (state == AttackState.BREATH_WINDUP) {
            if (t == 2) this.playSound(SoundEvents.ENDER_DRAGON_GROWL, 2.0F, 1.2F);
            if (this.level() instanceof ServerLevel sl) {
                Vec3 mouth = mouthPos();
                sl.sendParticles(ParticleTypes.SMALL_FLAME, mouth.x, mouth.y, mouth.z, 4, 0.15D, 0.15D, 0.15D, 0.01D);
            }
            if (t >= BREATH_WINDUP_DURATION) setAttackState(AttackState.BREATH);
        } else if (state == AttackState.BREATH) {
            if (t == 1) this.playSound(SoundEvents.GHAST_SHOOT, 3.0F, 0.6F);
            if (t % 3 == 0) doBreathTick();
            if (t >= BREATH_DURATION) {
                setAttackState(AttackState.NONE);
                this.actionCooldown = isRaging() ? 14 : 26;
            }
        } else if (state == AttackState.WING_GUST) {
            if (t == 4) this.playSound(SoundEvents.ENDER_DRAGON_FLAP, 3.0F, 0.8F);
            if (t == WING_GUST_STRIKE_TICK && struckThisState == 0) {
                struckThisState = 1;
                doWingGust();
            }
            if (t >= WING_GUST_DURATION) setAttackState(AttackState.NONE);
        } else if (state == AttackState.TAKEOFF) {
            tickTakeoff(t);
        } else if (state == AttackState.FLY) {
            tickFly(t);
        } else if (state == AttackState.DIVE) {
            tickDive(t);
        } else if (state == AttackState.LAND) {
            tickLand(t);
        } else if (state == AttackState.STAGGER) {
            if (t >= STAGGER_DURATION) setAttackState(AttackState.NONE);
        }
    }

    // -------------------------------------------------------------------------------------------
    // Ground attacks
    // -------------------------------------------------------------------------------------------

    private void doBiteImpact() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
        double cx = this.getX() + forward.x * 3.0D;
        double cy = this.getY() + 1.2D;
        double cz = this.getZ() + forward.z * 3.0D;
        float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * (isRaging() ? 1.2F : 1.0F);
        for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(cx, cy, cz, cx, cy, cz).inflate(BITE_RANGE * 0.5D), e -> e != this && e.isAlive())) {
            if (victim.distanceToSqr(cx, cy, cz) > BITE_RANGE * BITE_RANGE) continue;
            victim.hurt(this.damageSources().mobAttack(this), dmg);
            Vec3 away = victim.position().subtract(this.position()).normalize();
            victim.push(away.x * 0.8D, 0.35D, away.z * 0.8D);
            victim.hurtMarked = true;
            interruptIfCaster(victim);
        }
        this.playSound(SoundEvents.ENDER_DRAGON_HURT, 1.5F, 1.3F);
        sl.sendParticles(ParticleTypes.CRIT, cx, cy, cz, 12, 0.4D, 0.4D, 0.4D, 0.1D);
    }

    private void doTailImpact() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        // The spiked tail sweeps a wide arc all around the dragon — its signature "horntail" attack.
        float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * (isRaging() ? 1.35F : 1.15F);
        AABB area = this.getBoundingBox().inflate(TAIL_RANGE, 2.0D, TAIL_RANGE);
        for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.isAlive())) {
            Vec3 to = victim.position().subtract(this.position());
            if (to.horizontalDistanceSqr() > TAIL_RANGE * TAIL_RANGE) continue;
            victim.hurt(this.damageSources().mobAttack(this), dmg);
            Vec3 push = new Vec3(to.x, 0.0D, to.z).normalize();
            victim.push(push.x * 2.2D, 0.6D, push.z * 2.2D);
            victim.hurtMarked = true;
            interruptIfCaster(victim);
        }
        this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, 0.5F);
        Vec3 c = this.position();
        sl.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y + 0.6D, c.z, 6, TAIL_RANGE * 0.4D, 0.2D, TAIL_RANGE * 0.4D, 0.0D);
        sl.sendParticles(ParticleTypes.POOF, c.x, c.y + 0.2D, c.z, 40, TAIL_RANGE * 0.5D, 0.2D, TAIL_RANGE * 0.5D, 0.05D);
    }

    private void doWingGust() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
        AABB area = this.getBoundingBox().inflate(WING_GUST_RANGE, 2.5D, WING_GUST_RANGE);
        for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.isAlive())) {
            Vec3 to = victim.position().subtract(this.position());
            Vec3 toFlat = new Vec3(to.x, 0.0D, to.z);
            if (toFlat.lengthSqr() < 1.0E-4D || toFlat.lengthSqr() > WING_GUST_RANGE * WING_GUST_RANGE) continue;
            if (toFlat.normalize().dot(new Vec3(forward.x, 0.0D, forward.z).normalize()) < 0.0D) continue;
            victim.hurt(this.damageSources().mobAttack(this), 6.0F);
            Vec3 push = toFlat.normalize();
            victim.push(push.x * 2.6D, 0.85D, push.z * 2.6D);
            victim.hurtMarked = true;
            interruptIfCaster(victim);
        }
        this.playSound(SoundEvents.ENDER_DRAGON_FLAP, 4.0F, 1.0F);
        Vec3 c = this.position();
        sl.sendParticles(ParticleTypes.CLOUD, c.x + forward.x * 2.0D, c.y + 1.0D, c.z + forward.z * 2.0D,
                60, 2.0D, 0.6D, 2.0D, 0.25D);
    }

    private void doBreathTick() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        Vec3 mouth = mouthPos();
        Vec3 dir = lookDir();
        float dmg = isRaging() ? 6.0F : 4.0F;
        // Cone damage out to BREATH_RANGE.
        AABB scan = new AABB(mouth, mouth).inflate(BREATH_RANGE);
        for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class, scan, e -> e != this && e.isAlive())) {
            Vec3 to = victim.getBoundingBox().getCenter().subtract(mouth);
            double dist = to.length();
            if (dist > BREATH_RANGE || dist < 0.1D) continue;
            if (to.normalize().dot(dir) < BREATH_HALF_ANGLE) continue;
            victim.hurt(this.damageSources().onFire(), dmg);
            victim.igniteForSeconds(isRaging() ? 8 : 5);
            interruptIfCaster(victim);
        }
        // Visual flame jet — a line of flame particles from the mouth along the look direction.
        int steps = 14;
        for (int i = 1; i <= steps; i++) {
            double f = (double) i / steps;
            double px = mouth.x + dir.x * BREATH_RANGE * f;
            double py = mouth.y + dir.y * BREATH_RANGE * f;
            double pz = mouth.z + dir.z * BREATH_RANGE * f;
            double spread = 0.15D + f * 1.1D;
            sl.sendParticles(ParticleTypes.FLAME, px, py, pz, 3, spread, spread, spread, 0.02D);
            if (i % 3 == 0) {
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, px, py, pz, 1, spread, spread, spread, 0.01D);
            }
        }
        // Light grass fires sparsely at the cone's end for drama (server-only, guarded to air over solid).
        if (this.random.nextFloat() < 0.5F) {
            double f = 0.6D + this.random.nextDouble() * 0.4D;
            BlockPos hit = BlockPos.containing(mouth.x + dir.x * BREATH_RANGE * f,
                    mouth.y + dir.y * BREATH_RANGE * f, mouth.z + dir.z * BREATH_RANGE * f);
            if (sl.isEmptyBlock(hit) && !sl.isEmptyBlock(hit.below())
                    && sl.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)) {
                sl.setBlock(hit, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(), 11);
            }
        }
    }

    // -------------------------------------------------------------------------------------------
    // Flight
    // -------------------------------------------------------------------------------------------

    private void beginTakeoff() {
        setAttackState(AttackState.TAKEOFF);
        this.entityData.set(DATA_FLYING, true);
        this.setNoGravity(true);
        this.getNavigation().stop();
        double ground = groundBelowY();
        this.hoverTargetY = ground + HOVER_HEIGHT;
        this.playSound(SoundEvents.ENDER_DRAGON_FLAP, 5.0F, 0.7F);
    }

    private void tickTakeoff(int t) {
        Vec3 v = this.getDeltaMovement();
        double dy = Math.min(0.55D, (this.hoverTargetY - this.getY()) * 0.25D + 0.2D);
        this.setDeltaMovement(v.x * 0.8D, dy, v.z * 0.8D);
        if (t % 6 == 0) this.playSound(SoundEvents.ENDER_DRAGON_FLAP, 3.0F, 0.9F);
        if (this.getY() >= this.hoverTargetY - 1.0D || t >= TAKEOFF_DURATION) {
            setAttackState(AttackState.FLY);
        }
    }

    private void tickFly(int t) {
        LivingEntity target = this.getTarget();
        if (target == null) {
            if (t >= 20) beginLand();
            return;
        }
        // Hover above and circle toward a point over the target, facing it.
        double overX = target.getX();
        double overZ = target.getZ();
        double desiredY = Math.max(this.hoverTargetY, target.getY() + HOVER_HEIGHT);
        Vec3 toOver = new Vec3(overX - this.getX(), 0.0D, overZ - this.getZ());
        Vec3 horiz = toOver.lengthSqr() > 9.0D ? toOver.normalize().scale(isRaging() ? 0.42D : 0.32D) : toOver.scale(0.1D);
        double dy = MthClamp((desiredY - this.getY()) * 0.1D, -0.25D, 0.25D);
        this.setDeltaMovement(horiz.x, dy, horiz.z);
        faceTowards(target, 12.0F);
        if (t % 8 == 0) this.playSound(SoundEvents.ENDER_DRAGON_FLAP, 2.5F, 1.0F);

        // Mid-flight strafing fire on the target if roughly above and within range.
        if (isRaging() && t > 20 && t % 12 == 0) {
            faceTowards(target, 30.0F);
        }
        if (t >= FLY_DURATION) {
            beginDive(target);
        }
    }

    private void beginDive(LivingEntity target) {
        setAttackState(AttackState.DIVE);
        this.playSound(SoundEvents.ENDER_DRAGON_GROWL, 5.0F, 1.0F);
    }

    private void tickDive(int t) {
        LivingEntity target = this.getTarget();
        if (target == null) {
            beginLand();
            return;
        }
        Vec3 to = target.getBoundingBox().getCenter().subtract(this.position());
        Vec3 dir = to.lengthSqr() > 1.0E-3D ? to.normalize() : lookDir();
        double speed = isRaging() ? 1.15D : 0.9D;
        this.setDeltaMovement(dir.scale(speed));
        faceTowards(target, 25.0F);
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 1.0D, this.getZ(), 6, 0.6D, 0.6D, 0.6D, 0.02D);
        }
        // Impact when close to the target or the ground.
        boolean closeToTarget = this.distanceToSqr(target) < 9.0D;
        boolean nearGround = this.getY() <= groundBelowY() + 1.5D;
        if (closeToTarget || nearGround || t >= DIVE_DURATION) {
            doDiveImpact();
            beginLand();
        }
    }

    private void doDiveImpact() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        Vec3 c = this.position();
        float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * (isRaging() ? 1.5F : 1.25F);
        for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(5.0D, 3.0D, 5.0D), e -> e != this && e.isAlive())) {
            victim.hurt(this.damageSources().mobAttack(this), dmg);
            victim.igniteForSeconds(5);
            Vec3 away = victim.position().subtract(c).normalize();
            victim.push(away.x * 1.8D, 0.7D, away.z * 1.8D);
            victim.hurtMarked = true;
            interruptIfCaster(victim);
        }
        this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 3.0F, 0.6F);
        sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5D, c.z, 4, 1.5D, 0.5D, 1.5D, 0.0D);
        sl.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.3D, c.z, 60, 2.0D, 0.4D, 2.0D, 0.1D);
    }

    private void beginLand() {
        setAttackState(AttackState.LAND);
    }

    private void tickLand(int t) {
        Vec3 v = this.getDeltaMovement();
        this.setDeltaMovement(v.x * 0.7D, -0.35D, v.z * 0.7D);
        if (this.onGround() || this.getY() <= groundBelowY() + 0.5D || t >= LAND_DURATION) {
            this.setNoGravity(false);
            this.entityData.set(DATA_FLYING, false);
            setAttackState(AttackState.NONE);
            this.actionCooldown = isRaging() ? 20 : 40;
            this.playSound(SoundEvents.RAVAGER_STEP, 3.0F, 0.5F);
        }
    }

    // -------------------------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------------------------

    private void faceTowards(Entity target, float speed) {
        this.getLookControl().setLookAt(target, speed, speed);
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        float wanted = (float) (Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        this.setYRot(rotateTowards(this.getYRot(), wanted, speed));
        this.yBodyRot = this.getYRot();
    }

    private static float rotateTowards(float from, float to, float max) {
        float delta = net.minecraft.util.Mth.wrapDegrees(to - from);
        delta = net.minecraft.util.Mth.clamp(delta, -max, max);
        return from + delta;
    }

    private static double MthClamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private double groundBelowY() {
        BlockPos pos = this.blockPosition();
        if (this.level() instanceof ServerLevel sl) {
            return sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        }
        return this.getY();
    }

    private Vec3 lookDir() {
        return this.getLookAngle();
    }

    private Vec3 mouthPos() {
        Vec3 look = this.getLookAngle();
        return new Vec3(this.getX() + look.x * 3.2D,
                this.getEyeY() + 0.4D + look.y * 2.0D,
                this.getZ() + look.z * 3.2D);
    }

    private void interruptIfCaster(LivingEntity victim) {
        if (victim instanceof ServerPlayer sp) {
            ModEvents.interruptActiveCasting(sp);
        }
    }

    // Action triggers used by the combat goal.
    void beginRoar() { setAttackState(AttackState.ROAR); hasRoared = true; }
    void beginBite() { setAttackState(AttackState.BITE); }
    void beginTailWhip() { setAttackState(AttackState.TAIL_WHIP); }
    void beginBreath() { setAttackState(AttackState.BREATH_WINDUP); }
    void beginWingGust() { setAttackState(AttackState.WING_GUST); }
    void beginFlight() { beginTakeoff(); }

    boolean canStartAction() {
        return getAttackState() == AttackState.NONE && this.actionCooldown <= 0;
    }

    boolean inAerialSequence() {
        return isAerial();
    }

    // -------------------------------------------------------------------------------------------
    // Damage / hooks
    // -------------------------------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Stagger on heavy ground hits when not committed; never stagger out of flight.
        boolean wasBusy = isBusy() || isAerial();
        boolean result = super.hurt(source, amount);
        if (result && !this.level().isClientSide && !wasBusy && amount >= 14.0F && this.isAlive()) {
            setAttackState(AttackState.STAGGER);
        }
        return result;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            living.igniteForSeconds(3);
            interruptIfCaster(living);
        }
        return hit;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    protected void tickDeath() {
        super.tickDeath();
        if (this.level() instanceof ServerLevel sl && this.deathTime % 4 == 0) {
            sl.sendParticles(ParticleTypes.EXPLOSION,
                    this.getX() + (this.random.nextDouble() - 0.5D) * this.getBbWidth(),
                    this.getY() + this.random.nextDouble() * this.getBbHeight(),
                    this.getZ() + (this.random.nextDouble() - 0.5D) * this.getBbWidth(),
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public void setCustomName(@javax.annotation.Nullable net.minecraft.network.chat.Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(this.getDisplayName());
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false; // a flying dragon doesn't take fall damage
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Raging", isRaging());
        tag.putBoolean("HasRoared", hasRoared);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Raging")) this.entityData.set(DATA_RAGING, tag.getBoolean("Raging"));
        if (tag.contains("HasRoared")) this.hasRoared = tag.getBoolean("HasRoared");
        if (this.hasCustomName()) this.bossEvent.setName(this.getDisplayName());
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENDER_DRAGON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENDER_DRAGON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDER_DRAGON_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 2.0F;
    }

    // -------------------------------------------------------------------------------------------
    // Combat goal — picks attacks by range / rage / situation, mirroring the Troll's brain.
    // -------------------------------------------------------------------------------------------

    static class HorntailCombatGoal extends Goal {
        private final HungarianHorntailEntity dragon;
        private int decisionCooldown;

        HorntailCombatGoal(HungarianHorntailEntity dragon) {
            this.dragon = dragon;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = dragon.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void stop() {
            dragon.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = dragon.getTarget();
            if (target == null) return;

            // Aerial sequence is self-driving in the entity tick; the goal just waits it out.
            if (dragon.inAerialSequence()) {
                return;
            }

            dragon.getLookControl().setLookAt(target, 30.0F, 30.0F);

            if (!dragon.canStartAction()) {
                return; // committed to an action or on cooldown
            }

            // Intro roar before the first engagement.
            if (!dragon.hasRoared && dragon.distanceToSqr(target) < 400.0D) {
                dragon.beginRoar();
                return;
            }

            double distSq = dragon.distanceToSqr(target);
            double biteReach = BITE_RANGE * BITE_RANGE;

            if (decisionCooldown > 0) {
                decisionCooldown--;
            }

            // Close range: bite / tail whip / wing gust.
            if (distSq <= biteReach) {
                dragon.getNavigation().stop();
                if (decisionCooldown > 0) return;
                int roll = dragon.random.nextInt(100);
                int nearby = countNearby(target);
                if (nearby >= 2 || roll < 30) {
                    dragon.beginTailWhip();
                } else if (roll < 50) {
                    dragon.beginWingGust();
                } else if (roll < 78) {
                    dragon.beginBite();
                } else {
                    dragon.beginBreath();
                }
                decisionCooldown = dragon.isRaging() ? 8 : 14;
                return;
            }

            // Mid range: fire breath if the target is roughly in front, else occasionally take flight.
            if (distSq <= BREATH_RANGE * BREATH_RANGE) {
                if (decisionCooldown <= 0 && facingTarget(target)) {
                    int roll = dragon.random.nextInt(100);
                    int flyChance = dragon.isRaging() ? 28 : 16;
                    if (roll < flyChance) {
                        dragon.beginFlight();
                    } else {
                        dragon.beginBreath();
                    }
                    decisionCooldown = dragon.isRaging() ? 16 : 28;
                    return;
                }
                // Close the gap while keeping the target in view.
                dragon.getNavigation().moveTo(target, dragon.isRaging() ? 1.15D : 0.95D);
                return;
            }

            // Far: stalk toward the target, or launch into a dive-bomb run sometimes.
            if (dragon.canStartAction() && decisionCooldown <= 0
                    && dragon.random.nextInt(100) < (dragon.isRaging() ? 24 : 12)) {
                dragon.beginFlight();
                decisionCooldown = 30;
                return;
            }
            dragon.getNavigation().moveTo(target, dragon.isRaging() ? 1.2D : 1.0D);
        }

        private boolean facingTarget(LivingEntity target) {
            Vec3 forward = Vec3.directionFromRotation(0.0F, dragon.getYRot());
            Vec3 to = target.position().subtract(dragon.position());
            Vec3 toFlat = new Vec3(to.x, 0.0D, to.z);
            if (toFlat.lengthSqr() < 1.0E-4D) return true;
            return toFlat.normalize().dot(new Vec3(forward.x, 0.0D, forward.z).normalize()) > 0.6D;
        }

        private int countNearby(LivingEntity around) {
            AABB box = around.getBoundingBox().inflate(4.0D);
            return dragon.level().getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != dragon && e instanceof Player).size();
        }
    }
}
