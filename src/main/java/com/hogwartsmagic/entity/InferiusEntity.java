package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.locket.CaveLayout;
import com.hogwartsmagic.locket.InferiusRules;
import com.hogwartsmagic.spell.combat.CrowdControl;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
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
    /** Hands break the surface first (the ambush rise). 66, never 21, 35 or 63. */
    private static final byte EVENT_RISE_HANDS = 66;

    public final AnimationState riseAnimation = new AnimationState();
    public final AnimationState handsRiseAnimation = new AnimationState();
    public final AnimationState grabAnimation = new AnimationState();
    public final AnimationState recoilAnimation = new AnimationState();

    @Nullable private BlockPos caveBasin;
    private boolean ambush;
    private long lastGrabTick = -1000;
    @Nullable private LivingEntity grabbed;
    private int grabTicksLeft;
    private int idleTicks;
    @Nullable private Vec3 landPullTarget;
    @Nullable private CaveBoatEntity latchedTo;
    private int latchSide = 1;
    private boolean latchHit;
    private int latchHurtTimer;
    private int latchScan;
    private int leashScan;
    @Nullable private CaveLayout cachedLayout;
    @Nullable private BlockPos facingFor;
    @Nullable private net.minecraft.core.Direction archFacing;
    private boolean pastArchNow;
    private boolean returnNow;

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
        this.goalSelector.addGoal(2, new AvoidFireBlocksGoal());
        this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Player.class, InferiusRules.FEAR_RADIUS, 1.0D, 1.25D,
                p -> p instanceof Player pl && InferiusEntity.holdsFire(pl)));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(5, new ReturnToLakeGoal());
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    static boolean holdsFire(Player p) {
        return isFireItem(p.getMainHandItem()) || isFireItem(p.getOffhandItem());
    }

    static boolean isFireItem(ItemStack stack) {
        return stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH) || stack.is(Items.FLINT_AND_STEEL)
                || stack.is(Items.FIRE_CHARGE) || stack.is(Items.LAVA_BUCKET);
    }

    static boolean isFearedBlock(BlockState state) {
        return state.is(BlockTags.FIRE) || CampfireBlock.isLitCampfire(state) || state.getFluidState().is(FluidTags.LAVA);
    }

    /** Every 20 ticks looks for fire or lava within BLOCK_FEAR_SCAN blocks and backs away from the nearest. */
    private class AvoidFireBlocksGoal extends Goal {
        private Vec3 flee;
        private int cooldown;

        AvoidFireBlocksGoal() { this.setFlags(EnumSet.of(Flag.MOVE)); }

        @Override public boolean canUse() {
            if (cooldown-- > 0) return false;
            cooldown = 20;
            int r = InferiusRules.BLOCK_FEAR_SCAN;
            BlockPos origin = InferiusEntity.this.blockPosition();
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
                if (!isFearedBlock(InferiusEntity.this.level().getBlockState(pos))) continue;
                double d = pos.distSqr(origin);
                if (d < bestDist) { bestDist = d; best = pos.immutable(); }
            }
            if (best == null) return false;
            Vec3 away = DefaultRandomPos.getPosAway(InferiusEntity.this, 8, 4, Vec3.atCenterOf(best));
            if (away == null) return false;
            flee = away;
            return true;
        }

        @Override public boolean canContinueToUse() { return !InferiusEntity.this.getNavigation().isDone(); }

        @Override public void start() { InferiusEntity.this.getNavigation().moveTo(flee.x, flee.y, flee.z, 1.25D); }
    }

    /** A cave-bound Inferius with nothing to hunt walks back to nearby lake water instead of standing on the ledge. */
    private class ReturnToLakeGoal extends Goal {
        private int cooldown;

        ReturnToLakeGoal() { this.setFlags(EnumSet.of(Flag.MOVE)); }

        @Override public boolean canUse() {
            InferiusEntity self = InferiusEntity.this;
            if (self.caveBasin == null || self.getTarget() != null || self.isInWater()) return false;
            if (!self.returnNow && cooldown-- > 0) return false;
            self.returnNow = false;
            cooldown = 40 + self.random.nextInt(40);
            CaveLayout layout = self.level().hasChunkAt(self.caveBasin)
                    && self.level().getBlockEntity(self.caveBasin) instanceof com.hogwartsmagic.block.entity.DespairBasinBlockEntity basin
                    ? basin.layout() : CaveLayout.legacy(self.caveBasin);
            for (int t = 0; t < 12; t++) {
                int x = self.getBlockX() + self.random.nextInt(17) - 8;
                int z = self.getBlockZ() + self.random.nextInt(17) - 8;
                for (int y = self.getBlockY() + 2; y >= self.getBlockY() - 6; y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!self.level().getFluidState(p).is(FluidTags.WATER)) continue;
                    if (layout.inLake(p, com.hogwartsmagic.locket.LocketCaves.isCoveredWater(self.level(), p))) {
                        target = Vec3.atBottomCenterOf(p);
                        return true;
                    }
                    break;
                }
            }
            return false;
        }

        private Vec3 target = Vec3.ZERO;

        @Override public boolean canContinueToUse() {
            return InferiusEntity.this.getTarget() == null && !InferiusEntity.this.getNavigation().isDone();
        }

        @Override public void start() {
            InferiusEntity.this.getNavigation().moveTo(target.x, target.y, target.z, 0.9D);
        }
    }

    /** Already dead; the lake cannot drown it (canBreatheUnderwater is final in 1.21). */
    @Override public boolean canDrownInFluidType(FluidType type) { return false; }
    @Override protected boolean isSunBurnTick() { return false; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (latchedTo != null && (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.FALL))) {
            return false; // clinging to a hull beside the rock is no blow
        }
        boolean fire = source.is(DamageTypeTags.IS_FIRE);
        boolean wasBurning = this.isOnFire();
        // Vanilla burn ticks are IS_FIRE too; only a fresh fire hit may (re)light the corpse.
        if (fire && !wasBurning && !source.is(DamageTypes.ON_FIRE) && !this.level().isClientSide) {
            this.igniteForSeconds(InferiusRules.IGNITE_ON_FIRE_HIT_SECONDS);
            this.level().broadcastEntityEvent(this, EVENT_RECOIL);
        }
        boolean hurt = super.hurt(source, InferiusRules.scaleDamage(amount, fire, wasBurning));
        if (hurt && latchedTo != null) {
            latchHit = true;
        }
        return hurt;
    }

    @Override
    protected int getBaseExperienceReward() { return isCaveBound() ? 0 : super.getBaseExperienceReward(); }

    /** A latched Inferius, or one past the arch, takes no target: nothing re-acquires the rider or the escaping player. */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && (latchedTo != null || pastArchNow)) return;
        super.setTarget(target);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (latchedTo != null) return false; // clinging to the hull: the latch damage is the only harm
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living && !this.level().isClientSide
                && InferiusRules.isGrabReady(this.level().getGameTime(), lastGrabTick)) {
            if (living.getVehicle() instanceof CaveBoatEntity) {
                living.stopRiding(); // a boat is no shelter: the water pull drags the rider out
            }
            lastGrabTick = this.level().getGameTime();
            grabbed = living;
            grabTicksLeft = InferiusRules.GRAB_TICKS;
            this.level().broadcastEntityEvent(this, EVENT_GRAB);
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.INFERIUS_GRAB.get(), SoundSource.HOSTILE, 1.0F, 0.95F + this.random.nextFloat() * 0.1F);
            landPullTarget = null;
            if (!living.isInWater() && !this.isInWater()) {
                landPullTarget = findLakeWaterNear(living.blockPosition());
                CrowdControl.apply(living, ExampleMod.STUNNED.getHolder().orElseThrow(), InferiusRules.GRAB_TICKS, this);
            }
        }
        return hit;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) return;
        // A ServerPlayer keeps its instance across a dimension change, and distanceToSqr ignores the level.
        if (grabbed != null && (grabbed.level() != this.level() || grabbed.distanceToSqr(this) > 16.0D)) {
            grabbed = null;
        } else if (grabbed != null && grabTicksLeft-- > 0 && grabbed.isAlive() && (grabbed.isInWater() || this.isInWater())) {
            Vec3 pull = this.position().subtract(grabbed.position()).normalize().scale(InferiusRules.WATER_PULL_PER_TICK);
            grabbed.setDeltaMovement(grabbed.getDeltaMovement().add(pull.x, -InferiusRules.WATER_PULL_PER_TICK, pull.z));
            grabbed.hurtMarked = true;
        } else if (grabbed != null && grabTicksLeft > 0 && grabbed.isAlive()) {
            // On land (the water branch's condition already ticked the counter): a slower drag toward the nearest lake water, so the grab still sends the victim to the shore.
            if (landPullTarget != null) {
                Vec3 to = landPullTarget.subtract(grabbed.position());
                Vec3 flat = new Vec3(to.x, 0.0D, to.z);
                if (flat.lengthSqr() > 0.25D) {
                    Vec3 pull = flat.normalize().scale(InferiusRules.LAND_PULL_PER_TICK);
                    grabbed.setDeltaMovement(grabbed.getDeltaMovement().add(pull.x, 0.0D, pull.z));
                    grabbed.hurtMarked = true;
                }
            }
        } else if (grabTicksLeft <= 0) {
            grabbed = null;
        }
        if (isCaveBound()) {
            tickLatch();
            tickArchLeash();
            idleTicks = this.getTarget() == null ? idleTicks + 1 : 0;
            if (idleTicks > InferiusRules.IDLE_DESPAWN_TICKS
                    && (this.isInWater() || this.level().getNearestPlayer(this, 24.0D) == null)) this.discard();
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_GRAB) grabAnimation.start(this.tickCount);
        else if (id == EVENT_RISE) riseAnimation.start(this.tickCount);
        else if (id == EVENT_RISE_HANDS) handsRiseAnimation.start(this.tickCount);
        else if (id == EVENT_RECOIL) recoilAnimation.start(this.tickCount);
        else super.handleEntityEvent(id);
    }

    public void startRise() {
        this.level().broadcastEntityEvent(this, EVENT_RISE);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.INFERIUS_RISE.get(), SoundSource.HOSTILE, 1.0F, 0.95F + this.random.nextFloat() * 0.1F);
    }
    /** The ambush rise: only the hands break the surface at first. */
    public void startRiseHandsFirst() {
        this.level().broadcastEntityEvent(this, EVENT_RISE_HANDS);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.INFERIUS_RISE.get(), SoundSource.HOSTILE, 1.0F, 0.95F + this.random.nextFloat() * 0.1F);
    }

    /** The layout of the cave this Inferius is bound to, or null for a free one. Cached; refreshed when the basin is loaded. */
    @Nullable
    private CaveLayout layoutOrNull() {
        if (caveBasin == null) return null;
        if (!this.level().hasChunkAt(caveBasin)) return cachedLayout;
        if (this.level().getBlockEntity(caveBasin) instanceof com.hogwartsmagic.block.entity.DespairBasinBlockEntity basin) {
            cachedLayout = basin.layout();
        } else if (cachedLayout == null) {
            cachedLayout = CaveLayout.legacy(caveBasin);
        }
        return cachedLayout;
    }

    /** Nearest lake water (cave-bound: the lake proper; free: any water) within 10 blocks, searched once per grab. */
    @Nullable
    private Vec3 findLakeWaterNear(BlockPos from) {
        CaveLayout layout = layoutOrNull();
        Vec3 best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                double d = dx * dx + dz * dz;
                if (d >= bestD) continue;
                for (int dy = 3; dy >= -5; dy--) {
                    BlockPos p = from.offset(dx, dy, dz);
                    if (!this.level().getFluidState(p).is(FluidTags.WATER)) continue;
                    if (layout == null || layout.inLake(p, com.hogwartsmagic.locket.LocketCaves.isCoveredWater(this.level(), p))) {
                        best = Vec3.atCenterOf(p);
                        bestD = d;
                    }
                    break;
                }
            }
        }
        return best;
    }

    /**
     * Past the arch (the outside of the blood seal's plane, read from the seal's FACING) the lake guardians turn back:
     * drop the target, refuse a new one, and go to lake water. Old caves (no arches) and archless layouts keep their rule.
     */
    private void tickArchLeash() {
        if (leashScan++ % 10 != 0 || caveBasin == null) return;
        CaveLayout layout = layoutOrNull();
        if (layout == null || layout.arches().isEmpty()) { pastArchNow = false; return; }
        BlockPos arch = layout.arches().get(0);
        double best = Double.MAX_VALUE;
        for (BlockPos a : layout.arches()) {
            double d = Math.hypot(a.getX() - caveBasin.getX(), a.getZ() - caveBasin.getZ());
            if (d < best) { best = d; arch = a; }
        }
        if (!arch.equals(facingFor)) {
            if (!this.level().hasChunkAt(arch)) { return; }
            facingFor = arch;
            archFacing = null;
            double seal = Double.MAX_VALUE;
            for (BlockPos p : BlockPos.betweenClosed(arch.offset(-3, -3, -3), arch.offset(3, 3, 3))) {
                BlockState st = this.level().getBlockState(p);
                if (st.is(ExampleMod.BLOOD_SEAL.get()) && p.distSqr(arch) < seal) {
                    seal = p.distSqr(arch);
                    archFacing = st.getValue(com.hogwartsmagic.block.BloodSealBlock.FACING);
                }
            }
        }
        if (archFacing == null) { pastArchNow = false; return; }
        pastArchNow = InferiusRules.pastArchPlane(this.getX() - (arch.getX() + 0.5D), this.getY() - arch.getY(),
                this.getZ() - (arch.getZ() + 0.5D), archFacing.getStepX(), archFacing.getStepZ());
        if (pastArchNow) {
            super.setTarget(null);
            returnNow = true;
            this.getNavigation().stop();
        }
    }

    // ---- clinging to the skiff ----------------------------------------------------------------

    private void tickLatch() {
        if (latchedTo != null) {
            CaveBoatEntity boat = latchedTo;
            boolean rider = boat.isAlive() && !boat.isRemoved() && boat.isSailingWithRider()
                    && boat.level() == this.level() && boat.distanceToSqr(this) < 36.0D;
            if (InferiusRules.latchReleasedWhenRiderGone(rider, latchHit, this.isOnFire())) {
                release(boat);
                return;
            }
            double yaw = Math.toRadians(boat.getYRot());
            double sx = Math.cos(yaw) * 1.1D * latchSide;
            double sz = Math.sin(yaw) * 1.1D * latchSide;
            this.getNavigation().stop();
            super.setTarget(null);
            this.setPos(boat.getX() + sx, boat.getY() - 0.3D, boat.getZ() + sz);
            this.setDeltaMovement(boat.getDeltaMovement());
            this.fallDistance = 0.0F;
            if (boat.getFirstPassenger() instanceof LivingEntity victim) {
                this.getLookControl().setLookAt(victim, 30.0F, 30.0F);
                if (++latchHurtTimer >= InferiusRules.LATCH_DAMAGE_INTERVAL) {
                    latchHurtTimer = 0;
                    victim.hurt(this.damageSources().mobAttack(this), InferiusRules.LATCH_DAMAGE);
                }
            }
            return;
        }
        if (latchScan++ % 5 != 0 || !this.isInWater() || this.isOnFire()) return;
        for (CaveBoatEntity boat : this.level().getEntitiesOfClass(CaveBoatEntity.class,
                this.getBoundingBox().inflate(InferiusRules.LATCH_RANGE))) {
            if (boat.isSailingWithRider() && boat.latch(this)) {
                latchedTo = boat;
                latchSide = boat.latchedCount() % 2 == 1 ? 1 : -1;
                latchHit = false;
                latchHurtTimer = 0;
                this.setTarget(null);
                return;
            }
        }
    }

    private void release(CaveBoatEntity boat) {
        boat.unlatch(this);
        latchedTo = null;
        latchHit = false;
        double yaw = Math.toRadians(boat.getYRot());
        Vec3 away = new Vec3(Math.cos(yaw) * latchSide, 0.0D, Math.sin(yaw) * latchSide).scale(0.35D);
        this.setDeltaMovement(away.x, 0.1D, away.z);
        latchScan = 1; // a few ticks before it may clasp again
    }

    public boolean isLatched() { return latchedTo != null; }

    @Override
    public void remove(RemovalReason reason) {
        if (latchedTo != null) {
            latchedTo.unlatch(this);
            latchedTo = null;
        }
        super.remove(reason);
    }

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

    @Override protected SoundEvent getAmbientSound() { return ModSounds.INFERIUS_AMBIENT.get(); }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.INFERIUS_HURT.get(); }
    @Override protected SoundEvent getDeathSound() { return ModSounds.INFERIUS_DEATH.get(); }
}
