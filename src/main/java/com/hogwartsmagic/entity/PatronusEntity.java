package com.hogwartsmagic.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.hogwartsmagic.spell.summon.PatronusRules;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PatronusEntity extends PathfinderMob {
    /** PatronusForm id (spell.summon.PatronusForm); 0 is the generic stag. Synced so clients draw the form. */
    private static final EntityDataAccessor<Integer> DATA_FORM =
            SynchedEntityData.defineId(PatronusEntity.class, EntityDataSerializers.INT);
    private static final String TAG_FORM = "Form";

    private static final String TAG_OWNER = "Owner";
    private static final String TAG_AURA = "Aura";
    private static final String TAG_LIFESPAN = "Lifespan";

    private int lifespan = 600; // 30 seconds
    @javax.annotation.Nullable
    private java.util.UUID owner;
    private boolean masterAura;
    private net.minecraft.core.BlockPos lastLightPos = null;
    private final Map<net.minecraft.core.BlockPos, Integer> pulseLightBlocks = new HashMap<>();

    public PatronusEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new FlyingMoveControl(this, 10, false);
        this.getTags().add("patronus");
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FORM, 0);
    }

    public int getFormId() {
        return this.entityData.get(DATA_FORM);
    }

    public void setFormId(int formId) {
        this.entityData.set(DATA_FORM, formId);
    }

    public void setOwner(@javax.annotation.Nullable java.util.UUID owner) {
        this.owner = owner;
    }

    @javax.annotation.Nullable
    public java.util.UUID getOwnerUuid() {
        return this.owner;
    }

    public void setMasterAura(boolean masterAura) {
        this.masterAura = masterAura;
    }

    public boolean hasMasterAura() {
        return this.masterAura;
    }

    public void setLifespan(int ticks) {
        this.lifespan = ticks;
    }

    /** The owner as a live player in this level, or null. */
    @javax.annotation.Nullable
    private net.minecraft.server.level.ServerPlayer resolveOwner() {
        if (this.owner == null || !(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) {
            return null;
        }
        net.minecraft.world.entity.player.Player p = sl.getPlayerByUUID(this.owner);
        return p instanceof net.minecraft.server.level.ServerPlayer sp && sp.isAlive() ? sp : null;
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_FORM, this.getFormId());
        if (this.owner != null) {
            tag.putUUID(TAG_OWNER, this.owner);
        }
        tag.putBoolean(TAG_AURA, this.masterAura);
        tag.putInt(TAG_LIFESPAN, this.lifespan);
        // The light blocks it placed are only removed by this entity, and an unload never calls remove():
        // saved with it so a reloaded Patronus still clears them, instead of leaving permanent invisible lights.
        if (this.lastLightPos != null) {
            tag.putLong("LightPos", this.lastLightPos.asLong());
        }
        if (!this.pulseLightBlocks.isEmpty()) {
            tag.putLongArray("PulseLights", this.pulseLightBlocks.keySet().stream()
                    .mapToLong(net.minecraft.core.BlockPos::asLong).toArray());
        }
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setFormId(tag.getInt(TAG_FORM));
        this.owner = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        this.masterAura = tag.getBoolean(TAG_AURA);
        if (tag.contains(TAG_LIFESPAN)) {
            this.lifespan = tag.getInt(TAG_LIFESPAN);
        }
        this.lastLightPos = tag.contains("LightPos") ? net.minecraft.core.BlockPos.of(tag.getLong("LightPos")) : null;
        this.pulseLightBlocks.clear();
        for (long packed : tag.getLongArray("PulseLights")) {
            this.pulseLightBlocks.put(net.minecraft.core.BlockPos.of(packed), 1);
        }
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return com.hogwartsmagic.ModSounds.PATRONUS_AMBIENT.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.5F;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.FLYING_SPEED, 0.8D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .add(Attributes.ATTACK_DAMAGE, 20.0D) // High damage to Dementors
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        // Hunt down Dementors!
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25D, true));
        this.goalSelector.addGoal(4, new FollowOwnerGoal());
        this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 1.0D));

        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, DementorEntity.class, true));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level) {
            @Override
            public boolean isStableDestination(net.minecraft.core.BlockPos pos) {
                return true;
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
        this.setNoGravity(true);

        if (!this.level().isClientSide) {
            this.lifespan--;
            if (this.lifespan <= 0) {
                this.cleanupLight();
                this.cleanupPulseLights();
                this.discard();
            }
            
            this.updateLightBlock();
            this.tickPulseLights();
            if (this.masterAura) {
                this.tickMasterAura();
            }
            
            // Radiate light/damage to nearby dementors. The damage radius grows over time.
            double auraRadius = Math.min(32.0D, 8.0D + (this.tickCount * 0.15D));
            
            // Continuous burn
            if (this.tickCount % 10 == 0) {
                List<DementorEntity> dementors = this.level().getEntitiesOfClass(DementorEntity.class, this.getBoundingBox().inflate(auraRadius));
                for (DementorEntity d : dementors) {
                    d.hurt(this.damageSources().magic(), 10.0F); // Burns dementors
                }
            }
            
            // Pulse ring extends farther every pulse.
            if (this.tickCount % 40 == 0) {
                int pulseRadius = (int) Math.ceil(Math.min(30.0D, 8.0D + (this.tickCount * 0.15D)));
                createPulseRingAtRadius(pulseRadius);
                pushDementorsNearRing(pulseRadius);
                
                // Auditory pulse cue.
                if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    sl.playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, net.minecraft.sounds.SoundSource.NEUTRAL, 2.0F, 1.5F);
                }
            }
        }
    }
    
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    public void remove(net.minecraft.world.entity.Entity.RemovalReason reason) {
        if (!this.level().isClientSide) {
            this.cleanupLight();
            this.cleanupPulseLights();
        }
        super.remove(reason);
    }

    /** Night Vision for the caster and every player nearby; undead and Inferi are pushed out and forget the players. */
    private void tickMasterAura() {
        if (this.tickCount % PatronusRules.NIGHT_VISION_INTERVAL == 0) {
            net.minecraft.server.level.ServerPlayer ownerPlayer = this.resolveOwner();
            if (ownerPlayer != null) {
                this.giveNightVision(ownerPlayer);
            }
            for (net.minecraft.world.entity.player.Player p : this.level().getEntitiesOfClass(
                    net.minecraft.world.entity.player.Player.class, this.getBoundingBox().inflate(PatronusRules.LIGHT_RADIUS))) {
                if (!p.isSpectator() && p != ownerPlayer && p.distanceToSqr(this) <= PatronusRules.LIGHT_RADIUS * PatronusRules.LIGHT_RADIUS) {
                    this.giveNightVision(p);
                }
            }
        }
        if (this.tickCount % PatronusRules.UNDEAD_INTERVAL == 0) {
            for (net.minecraft.world.entity.Mob mob : this.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                    this.getBoundingBox().inflate(PatronusRules.UNDEAD_RADIUS))) {
                boolean undead = mob.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD) || mob instanceof InferiusEntity;
                if (!PatronusRules.repels(undead, com.hogwartsmagic.spell.combat.CombatTags.isBoss(mob), mob.distanceToSqr(this))) {
                    continue;
                }
                double dx = mob.getX() - this.getX();
                double dz = mob.getZ() - this.getZ();
                double len = Math.sqrt(dx * dx + dz * dz);
                if (len < 1.0E-3) {
                    dx = 1.0;
                    dz = 0.0;
                    len = 1.0;
                }
                mob.setDeltaMovement(mob.getDeltaMovement().add(dx / len * PatronusRules.UNDEAD_PUSH, 0.0, dz / len * PatronusRules.UNDEAD_PUSH));
                mob.hurtMarked = true;
                if (mob.getTarget() instanceof net.minecraft.world.entity.player.Player) {
                    mob.setTarget(null);
                }
            }
        }
    }

    private void giveNightVision(net.minecraft.world.entity.player.Player p) {
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,
                PatronusRules.NIGHT_VISION_TICKS, 0, true, false));
    }

    /** A Master Patronus keeps close to its caster so its light goes with them. */
    private final class FollowOwnerGoal extends Goal {
        private static final int REPATH_INTERVAL = 10;
        private net.minecraft.server.level.ServerPlayer target;
        private int repathIn;

        FollowOwnerGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!PatronusEntity.this.masterAura || PatronusEntity.this.getTarget() != null) {
                return false;
            }
            net.minecraft.server.level.ServerPlayer p = PatronusEntity.this.resolveOwner();
            if (p == null) {
                return false;
            }
            this.target = p;
            this.repathIn = 0;
            return PatronusEntity.this.distanceToSqr(p) > PatronusRules.FOLLOW_START * PatronusRules.FOLLOW_START;
        }

        @Override
        public boolean canContinueToUse() {
            return PatronusEntity.this.masterAura && PatronusEntity.this.getTarget() == null && this.target != null
                    && this.target.isAlive() && this.target.level() == PatronusEntity.this.level()
                    && PatronusEntity.this.distanceToSqr(this.target) > PatronusRules.FOLLOW_STOP * PatronusRules.FOLLOW_STOP;
        }

        @Override
        public void stop() {
            this.target = null;
            PatronusEntity.this.getNavigation().stop();
        }

        @Override
        public void tick() {
            PatronusEntity self = PatronusEntity.this;
            if (self.distanceToSqr(this.target) > PatronusRules.TELEPORT_DISTANCE * PatronusRules.TELEPORT_DISTANCE) {
                for (int i = 0; i < 10; i++) {
                    double x = this.target.getX() + (self.getRandom().nextDouble() - 0.5) * 3.0;
                    double y = this.target.getY() + self.getRandom().nextInt(2);
                    double z = this.target.getZ() + (self.getRandom().nextDouble() - 0.5) * 3.0;
                    net.minecraft.world.phys.AABB box = self.getBoundingBox().move(x - self.getX(), y - self.getY(), z - self.getZ());
                    if (self.level().noCollision(self, box)) {
                        self.moveTo(x, y, z, self.getYRot(), self.getXRot());
                        self.getNavigation().stop();
                        return;
                    }
                }
                return;
            }
            // Re-path every 10 ticks like vanilla FollowOwnerGoal, not every tick.
            if (--this.repathIn <= 0) {
                this.repathIn = REPATH_INTERVAL;
                self.getNavigation().moveTo(this.target, 1.2D);
            }
        }
    }

    private void updateLightBlock() {
        net.minecraft.core.BlockPos currentPos = this.blockPosition();
        
        // If we moved to a new block
        if (lastLightPos == null || !lastLightPos.equals(currentPos)) {
            // Remove old light
            cleanupLight();
            
            // Try to place new light if the block is air
            if (this.level().getBlockState(currentPos).isAir()) {
                this.level().setBlock(currentPos, com.hogwartsmagic.ExampleMod.PATRONUS_LIGHT_BLOCK.get().defaultBlockState(), 3);
                this.lastLightPos = currentPos;
            }
        }
    }

    private void cleanupLight() {
        if (this.lastLightPos != null) {
            if (this.level().getBlockState(this.lastLightPos).is(com.hogwartsmagic.ExampleMod.PATRONUS_LIGHT_BLOCK.get())) {
                // Restore air
                this.level().removeBlock(this.lastLightPos, false);
            }
            this.lastLightPos = null;
        }
    }

    private void createPulseRingAtRadius(int radius) {
        net.minecraft.core.BlockPos center = this.blockPosition();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double distance = Math.sqrt((x * x) + (z * z));
                    if (distance > radius - 0.6D && distance < radius + 0.6D) {
                        net.minecraft.core.BlockPos p = center.offset(x, y, z);
                        if (this.level().getBlockState(p).isAir()) {
                            this.level().setBlock(p, com.hogwartsmagic.ExampleMod.PATRONUS_LIGHT_BLOCK.get().defaultBlockState(), 3);
                            pulseLightBlocks.put(p, 6); // brief flash on moving ring only
                        }
                    }
                }
            }
        }
    }

    private void pushDementorsNearRing(int radius) {
        List<DementorEntity> dementors = this.level().getEntitiesOfClass(DementorEntity.class, this.getBoundingBox().inflate(radius + 2.0D));
        for (DementorEntity d : dementors) {
            double dx = d.getX() - this.getX();
            double dz = d.getZ() - this.getZ();
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);
            if (horizontalDist < radius - 1.0D || horizontalDist > radius + 1.0D) {
                continue; // only affect the outer wavefront ring
            }
            if (horizontalDist > 0.1D) {
                double force = Math.min(2.8D, 1.0D + (this.tickCount * 0.008D));
                d.setDeltaMovement(d.getDeltaMovement().add((dx / horizontalDist) * force, 0.28D, (dz / horizontalDist) * force));
                d.hurtMarked = true;
            }
        }
    }

    private void tickPulseLights() {
        Iterator<Map.Entry<net.minecraft.core.BlockPos, Integer>> it = pulseLightBlocks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<net.minecraft.core.BlockPos, Integer> entry = it.next();
            int ttl = entry.getValue() - 1;
            if (ttl <= 0) {
                net.minecraft.core.BlockPos pos = entry.getKey();
                if (this.level().getBlockState(pos).is(com.hogwartsmagic.ExampleMod.PATRONUS_LIGHT_BLOCK.get())) {
                    this.level().removeBlock(pos, false);
                }
                it.remove();
            } else {
                entry.setValue(ttl);
            }
        }
    }

    private void cleanupPulseLights() {
        for (net.minecraft.core.BlockPos pos : pulseLightBlocks.keySet()) {
            if (this.level().getBlockState(pos).is(com.hogwartsmagic.ExampleMod.PATRONUS_LIGHT_BLOCK.get())) {
                this.level().removeBlock(pos, false);
            }
        }
        pulseLightBlocks.clear();
    }
}
