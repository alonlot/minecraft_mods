package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.locket.InferiusRules;
import com.hogwartsmagic.spell.combat.CrowdControl;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;

/** A drowned corpse kept by the lake: shrugs off blows, burns easily, drags the living under. */
public class InferiusEntity extends Monster {
    private static final byte EVENT_GRAB = 61;
    private static final byte EVENT_RISE = 62;
    private static final byte EVENT_RECOIL = 64;

    public final AnimationState riseAnimation = new AnimationState();
    public final AnimationState grabAnimation = new AnimationState();
    public final AnimationState recoilAnimation = new AnimationState();

    @Nullable private BlockPos caveBasin;
    private boolean ambush;
    private long lastGrabTick = -1000;
    @Nullable private LivingEntity grabbed;
    private int grabTicksLeft;
    private int idleTicks;

    public InferiusEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, InferiusRules.MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this)); // must swim at the surface to climb onto the island
        this.goalSelector.addGoal(2, new PanicGoal(this, 1.3D) {
            @Override protected boolean shouldPanic() { return mob.isOnFire(); }
        });
        this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Player.class, InferiusRules.FEAR_RADIUS, 1.0D, 1.25D,
                p -> p instanceof Player pl && InferiusEntity.holdsFire(pl)));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    static boolean holdsFire(Player p) {
        return p.getMainHandItem().is(Items.TORCH) || p.getOffhandItem().is(Items.TORCH)
                || p.getMainHandItem().is(Items.FLINT_AND_STEEL) || p.getMainHandItem().is(Items.FIRE_CHARGE)
                || p.getMainHandItem().is(Items.LAVA_BUCKET) || p.getMainHandItem().is(Items.SOUL_TORCH);
    }

    /** Already dead; the lake cannot drown it (canBreatheUnderwater is final in 1.21). */
    @Override public boolean canDrownInFluidType(FluidType type) { return false; }
    @Override protected boolean isSunBurnTick() { return false; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean fire = source.is(DamageTypeTags.IS_FIRE);
        if (fire && !this.level().isClientSide) {
            this.igniteForSeconds(InferiusRules.IGNITE_ON_FIRE_HIT_SECONDS);
            this.level().broadcastEntityEvent(this, EVENT_RECOIL);
        }
        return super.hurt(source, InferiusRules.scaleDamage(amount, fire, this.isOnFire()));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living && !this.level().isClientSide
                && InferiusRules.isGrabReady(this.level().getGameTime(), lastGrabTick)) {
            lastGrabTick = this.level().getGameTime();
            grabbed = living;
            grabTicksLeft = InferiusRules.GRAB_TICKS;
            this.level().broadcastEntityEvent(this, EVENT_GRAB);
            if (!living.isInWater() && !this.isInWater()) {
                CrowdControl.apply(living, ExampleMod.STUNNED.getHolder().orElseThrow(), InferiusRules.GRAB_TICKS, this);
            }
        }
        return hit;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) return;
        if (grabbed != null && grabTicksLeft-- > 0 && grabbed.isAlive() && (grabbed.isInWater() || this.isInWater())) {
            Vec3 pull = this.position().subtract(grabbed.position()).normalize().scale(InferiusRules.WATER_PULL_PER_TICK);
            grabbed.setDeltaMovement(grabbed.getDeltaMovement().add(pull.x, -InferiusRules.WATER_PULL_PER_TICK, pull.z));
            grabbed.hurtMarked = true;
        } else if (grabTicksLeft <= 0) {
            grabbed = null;
        }
        if (isCaveBound()) {
            idleTicks = this.getTarget() == null ? idleTicks + 1 : 0;
            if (idleTicks > InferiusRules.IDLE_DESPAWN_TICKS && this.isInWater()) this.discard();
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_GRAB) grabAnimation.start(this.tickCount);
        else if (id == EVENT_RISE) riseAnimation.start(this.tickCount);
        else if (id == EVENT_RECOIL) recoilAnimation.start(this.tickCount);
        else super.handleEntityEvent(id);
    }

    public void startRise() { this.level().broadcastEntityEvent(this, EVENT_RISE); }
    public void bindToCave(BlockPos basin) { this.caveBasin = basin.immutable(); this.setPersistenceRequired(); }
    @Nullable public BlockPos caveBasin() { return caveBasin; }
    public boolean isCaveBound() { return caveBasin != null; }
    public void setAmbush(boolean ambush) { this.ambush = ambush; }
    public boolean isAmbush() { return ambush; }

    @Override
    protected boolean shouldDropLoot() { return !isCaveBound() && super.shouldDropLoot(); }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (caveBasin != null) tag.put("CaveBasin", NbtUtils.writeBlockPos(caveBasin));
        tag.putBoolean("Ambush", ambush);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        caveBasin = NbtUtils.readBlockPos(tag, "CaveBasin").orElse(null);
        ambush = tag.getBoolean("Ambush");
    }

    public static boolean checkInferiusSpawnRules(EntityType<InferiusEntity> type, ServerLevelAccessor level,
                                                  MobSpawnType reason, BlockPos pos, RandomSource random) {
        return Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
    }

    @Override protected SoundEvent getAmbientSound() { return SoundEvents.DROWNED_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.DROWNED_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.DROWNED_DEATH; }
}
