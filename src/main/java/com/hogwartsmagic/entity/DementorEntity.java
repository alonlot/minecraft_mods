package com.hogwartsmagic.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class DementorEntity extends Monster {

    /** Persistent-data flag set on a player channelling the non-corporeal Patronus shield. */
    public static final String TAG_PATRONUS_SHIELD_CHANNELING = "hogwartsmagic:patronus_shield_channeling";

    /** Game time until which the Patronus shield keeps this Dementor cowed. Server-side only. */
    private long shieldRepelledUntil = 0L;

    // Entity-event ids must stay clear of vanilla's. ClientPacketListener.handleEntityEvent
    // intercepts 21, 35 and 63 before per-entity dispatch and casts the entity to Guardian,
    // Player and Sniffer respectively -- a Dementor sent id 63 crashed the client with a
    // ClassCastException instead of ever reaching handleEntityEvent below. Vanilla's own
    // EntityEvent table currently ends at 65 (BODY_BREAK), so 80+ is free with headroom.

    /** Broadcast to clients to start the Kiss - the only clip that uncovers the face. */
    private static final byte EVENT_KISS = 80;
    /** Broadcast to clients when a Patronus drives it back. */
    private static final byte EVENT_RECOIL = 81;

    /** Length of {@code DementorAnimations.KISS} in ticks (4.0s). */
    private static final int KISS_TICKS = 80;
    /** Length of {@code DementorAnimations.RECOIL} in ticks (1.4s), plus a breath. */
    private static final int RECOIL_TICKS = 34;

    /**
     * Animation states live here rather than in the renderer so nothing
     * client-only leaks into common code; {@link AnimationState} itself is a
     * common class and is safe on a dedicated server.
     */
    private final AnimationState floatState = new AnimationState();
    private final AnimationState kissState = new AnimationState();
    private final AnimationState recoilState = new AnimationState();

    /** Server-side lockouts so a clip cannot be retriggered while it is still playing. */
    private int kissCooldown;
    private int recoilCooldown;

    public DementorEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new FlyingMoveControl(this, 10, false);
    }

    /** Ambient hover. Runs for the entity's whole life. */
    public AnimationState dementorFloatState() {
        return this.floatState;
    }

    /** The soul-drinking lunge, during which the hood opens. */
    public AnimationState dementorKissState() {
        return this.kissState;
    }

    /** The shrieking flinch away from a Patronus. */
    public AnimationState dementorRecoilState() {
        return this.recoilState;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_KISS) {
            this.kissState.start(this.tickCount);
        } else if (id == EVENT_RECOIL) {
            this.recoilState.start(this.tickCount);
        } else {
            super.handleEntityEvent(id);
        }
    }

    /**
     * Called by the Patronus shield each tick a Dementor sits inside the cone. A non-corporeal
     * Patronus can't hurt a Dementor, but it holds it off: the soul-suck breaks for a second.
     */
    public void markShieldRepelled(long gameTime) {
        this.shieldRepelledUntil = gameTime + 20L;
    }

    public boolean isShieldRepelled() {
        return this.level().getGameTime() < this.shieldRepelledUntil;
    }

    /** True while a corporeal Patronus is close enough to panic it. Server-side. */
    private boolean isPatronusNear() {
        return !this.level().getEntitiesOfClass(PatronusEntity.class, this.getBoundingBox().inflate(16.0D)).isEmpty();
    }

    /** A caster holding the shield up is behind it, so the aura of dread can't reach them. */
    private static boolean isShieldedFromAura(LivingEntity entity) {
        return entity.getTags().contains("patronus")
                || entity.getPersistentData().getBoolean(TAG_PATRONUS_SHIELD_CHANNELING);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, PatronusEntity.class, 24.0F, 1.5D, 2.0D)); // Runs from Patronus IMPERATIVELY
        this.goalSelector.addGoal(2, new DementorSuckGoal(this)); // Custom soul-sucking attack
        this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // Dark Magic: a Corrupted or Lost player in range comes first (the nearest such one), even over a target
        // already chosen. With none in range the goals below choose exactly as before (same order, renumbered).
        this.targetSelector.addGoal(2, new DarkPlayerGoals.TargetDarkPlayer(this,
                com.hogwartsmagic.spell.dark.DarkCostRules.Cost.DEMENTORS_PREFER));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, WizardEntity.class, true));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, StudentEntity.class, true));
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return com.hogwartsmagic.ModSounds.DEMENTOR_AMBIENT.get();
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return com.hogwartsmagic.ModSounds.DEMENTOR_HURT.get();
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return com.hogwartsmagic.ModSounds.DEMENTOR_DEATH.get();
    }

    /**
     * Natural-spawn rule for the Dementor Fen.
     *
     * <p>Daylight is no protection from a Dementor — the whole point of the fen is that it stays
     * oppressive at noon — so the usual darkness check is skipped and density is held down by the
     * biome's {@code spawn_costs} instead of by light level.
     *
     * <p>Open sky <i>is</i> required, though. The fen owns the rock beneath it as well as the surface,
     * so without this check the caves under the marsh would fill with Dementors: a dark enclosed space
     * they can phase through the walls of, with no room to back away from and no daylight to escape
     * into. They are meant to drift over the water, not lurk under it.
     *
     * <p>This only gates <i>natural</i> spawning. The insomnia event constructs Dementors directly and
     * never consults this, so being indoors still will not save a sleepless player.
     */
    public static boolean canSpawn(EntityType<DementorEntity> type, ServerLevelAccessor level,
                                   MobSpawnType spawnType, BlockPos pos,
                                   net.minecraft.util.RandomSource random) {
        return level.canSeeSky(pos)
                && checkAnyLightMonsterSpawnRules(type, level, spawnType, pos, random);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 120.0D)
                .add(Attributes.FLYING_SPEED, 0.52D) // Still threatening, but slightly easier to shake off
                .add(Attributes.MOVEMENT_SPEED, 0.34D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level) {
            @Override
            public boolean isStableDestination(net.minecraft.core.BlockPos pos) {
                return true; // We don't care about the destination blocks because we clip through them
            }
        };
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, net.minecraft.core.BlockPos pos) {
    }

    @Override
    public void tick() {
        super.tick();

        // Disable gravity entirely
        this.setNoGravity(true);

        if (this.level().isClientSide) {
            this.floatState.startIfStopped(this.tickCount);
        } else {
            if (this.kissCooldown > 0) {
                this.kissCooldown--;
            }
            if (this.recoilCooldown > 0) {
                this.recoilCooldown--;
            }
            // A Patronus in view, or a shield held in its face, makes it recoil and
            // shriek. Driven from tick rather than the flee goal so it fires even
            // when the Dementor has no target to abandon.
            if (this.recoilCooldown == 0 && (this.isShieldRepelled() || (this.tickCount % 5 == 0 && this.isPatronusNear()))) {
                this.recoilCooldown = RECOIL_TICKS;
                this.level().broadcastEntityEvent(this, EVENT_RECOIL);
                this.playSound(com.hogwartsmagic.ModSounds.DEMENTOR_SHRIEK.get(), 1.4F, 1.0F);
            }
        }

        // Ambient Dementor Aura of Dread and Cold Space
        if (!this.level().isClientSide && this.tickCount % 20 == 0) {
            // Apply blindness and slowness to all living things nearby
            List<LivingEntity> nearby = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(9.0D));
            for (LivingEntity e : nearby) {
                if (!(e instanceof DementorEntity) && !isShieldedFromAura(e)) { // Exclude other Dementors, Patronuses and shielded casters
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false));
                }
            }
        }

        // Frost/Soul particles flowing off them
        if (this.level().isClientSide) {
            for (int i = 0; i < 2; i++) {
                this.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                        this.getRandomX(0.5D),
                        this.getRandomY(),
                        this.getRandomZ(0.5D),
                        0.0D, 0.05D, 0.0D);
            }
            this.level().addParticle(ParticleTypes.SNOWFLAKE,
                    this.getRandomX(1.5D),
                    this.getRandomY() + 0.5D,
                    this.getRandomZ(1.5D),
                    0.0D, -0.05D, 0.0D);
        }
    }

    class DementorSuckGoal extends Goal {
        private final DementorEntity dementor;
        private int suckTime = 0;
        private boolean patronusNear;

        public DementorSuckGoal(DementorEntity dementor) {
            this.dementor = dementor;
        }

        @Override
        public boolean canUse() {
            // Immediately stop trying to suck souls if a Patronus is nearby (Panic mode)
            // The 24-block scan is throttled to every 5 ticks and cached in between (same cadence as
            // isPatronusNear in tick()); canContinueToUse defaults to canUse, so no hard tick gate here.
            if (this.dementor.tickCount % 5 == 0) {
                this.patronusNear = !this.dementor.level().getEntitiesOfClass(PatronusEntity.class, this.dementor.getBoundingBox().inflate(24.0D)).isEmpty();
            }
            if (this.patronusNear) {
                return false;
            }
            // Held off by a non-corporeal shield: it can't hurt us, but we can't feed through it either.
            if (this.dementor.isShieldRepelled()) {
                return false;
            }
            return this.dementor.getTarget() != null && this.dementor.distanceToSqr(this.dementor.getTarget()) < 2304.0D; // 48 blocks
        }

        @Override
        public void start() {
            this.suckTime = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = this.dementor.getTarget();
            if (target != null) {
                this.dementor.getLookControl().setLookAt(target, 30.0F, 30.0F);
                
                double distSq = this.dementor.distanceToSqr(target);

                if (distSq < 9.0D && !this.dementor.level().isClientSide
                        && this.dementor.kissCooldown == 0) {
                    // Close enough to feed: throw the hood open and lunge. This is
                    // the only thing that ever exposes the face, so it is gated on
                    // its own cooldown rather than the damage tick.
                    this.dementor.kissCooldown = KISS_TICKS;
                    this.dementor.level().broadcastEntityEvent(this.dementor, EVENT_KISS);
                    this.dementor.playSound(com.hogwartsmagic.ModSounds.DEMENTOR_KISS.get(), 1.5F, 1.0F);
                }

                if (distSq < 4.0D) {
                    // Kiss of Death: Huge damage
                    if (this.suckTime % 10 == 0) {
                        target.hurt(this.dementor.damageSources().magic(), 3.0F);
                        target.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0));
                    }
                } else {
                    // Apply blindness as they get sucked in
                    if (distSq < 196.0D && this.suckTime % 20 == 0) {
                        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
                    }
                    
                    // Move towards the target while sucking them in
                    this.dementor.getNavigation().moveTo(target, 1.25D);
                    
                    // Pull the target towards the Dementor (Soul Sucking Wind)
                    Vec3 pull = new Vec3(this.dementor.getX() - target.getX(), 
                                         this.dementor.getY() + this.dementor.getEyeHeight() - target.getY(), 
                                         this.dementor.getZ() - target.getZ()).normalize().scale(0.03);
                    target.setDeltaMovement(target.getDeltaMovement().add(pull));
                    target.hurtMarked = true;
                }
                
                // The dragging inhale that runs under the whole approach.
                if (this.suckTime % 60 == 0 && !this.dementor.level().isClientSide) {
                    this.dementor.playSound(com.hogwartsmagic.ModSounds.DEMENTOR_SUCK.get(), 1.2F, 1.0F);
                }

                this.suckTime++;
            }
        }
    }
}
