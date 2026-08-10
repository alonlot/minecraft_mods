package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.caveinimicum.CaveInimicumManager;
import com.hogwartsmagic.polyjuice.PolyjuiceData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * A house elf. Never hostile, at any stage - the whole design is an arc rather
 * than a fight.
 *
 * <p>You find it <b>wild</b> in the Forbidden Forest, where it Disapparates with
 * a crack the moment a wizard it notices comes near. Approach it unseen (an
 * Invisibility Cloak, an invisibility potion, Polyjuice into an animal, or a
 * patient crouch) or pin it so it cannot Apparate away (a Cave Inimicum ward,
 * Petrificus Totalus, Incarcerous, Glacius, Stupefy, Arresto Momentum, Imperio),
 * then offer it food and it binds to you as its master.
 *
 * <p>A <b>bound</b> elf follows and serves, and punishes itself when its master
 * comes to harm. Present it with clothing and it is <b>freed</b>. How you took it
 * decides what happens then: an elf taken gently stays on as a friend and will
 * throw your attackers across the clearing, while one taken by force takes the
 * clothes and leaves forever.
 */
public class HouseElfEntity extends PathfinderMob {

    public static final int STATE_WILD = 0;
    public static final int STATE_BOUND = 1;
    public static final int STATE_FREE = 2;

    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(HouseElfEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_WILLING =
            SynchedEntityData.defineId(HouseElfEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_COWERING =
            SynchedEntityData.defineId(HouseElfEntity.class, EntityDataSerializers.BOOLEAN);

    // Entity event bytes must dodge two sets of reservations. ClientPacketListener
    // intercepts 21, 35 and 63 and casts the entity to Guardian, Player and Sniffer
    // respectively BEFORE handleEntityEvent is ever reached, so those three crash
    // the client outright. LivingEntity and Entity additionally consume 3, 29, 30,
    // 46-55, 60 and 65. 61, 62, 64, 66 and 67 are free.
    private static final byte EVENT_PUNISH = 61;
    private static final byte EVENT_JOY = 62;
    private static final byte EVENT_DISAPPARATE = 64;
    private static final byte EVENT_APPARITION_FAILED = 66;

    /** How close a noticed wizard may get before a wild elf bolts. */
    private static final double FLIGHT_RADIUS = 5.0D;
    /**
     * A crouching player has to be at least this far off to go unnoticed. Kept
     * well inside the roughly three-block entity reach, or there would be no
     * range at which a careful player is both unseen and able to offer food.
     */
    private static final double CROUCH_SAFE_DISTANCE = 1.5D;
    /** Ticks between the crack starting and the elf actually leaving. */
    private static final int DISAPPARATE_WINDUP = 8;
    /** How far a Disapparating elf tries to land from where it stood. */
    private static final int REAPPEAR_MIN = 20;
    private static final int REAPPEAR_MAX = 44;
    /** No two house elves within this many blocks - they are solitary. */
    public static final double SOLITUDE_RADIUS = 96.0D;

    /** How long the freed elf capers about before settling down, in milliseconds. */
    private static final long REJOICE_MILLIS = 3200L;
    /** Length of the DISAPPARATE clip. It does not loop, so it has to be cut off by hand. */
    private static final long DISAPPARATE_MILLIS = 500L;

    private static final int PROTECT_COOLDOWN_TICKS = 60;
    private static final double PROTECT_RADIUS = 8.0D;
    private static final float PROTECT_DAMAGE = 4.0F;

    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState cowerAnimationState = new AnimationState();
    public final AnimationState punishAnimationState = new AnimationState();
    public final AnimationState joyAnimationState = new AnimationState();
    public final AnimationState disapparateAnimationState = new AnimationState();

    @Nullable
    private UUID masterUuid;

    /** Counts down while the elf is held by force and cannot Apparate out. */
    private int subduedTicks;
    /** Counts down from {@link #DISAPPARATE_WINDUP} once the crack has begun. */
    private int disapparatingTicks;
    private int protectCooldown;

    public HouseElfEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.36D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, STATE_WILD);
        builder.define(DATA_WILLING, false);
        builder.define(DATA_COWERING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FleeNoticedWizardGoal());
        this.goalSelector.addGoal(2, new FollowMasterGoal());
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.5D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        // No target goal at all. A house elf does not pick fights; the freed one
        // only ever answers an attack on its friend, handled in tick().
    }

    // ------------------------------------------------------------------ state

    public int getElfState() {
        return this.entityData.get(DATA_STATE);
    }

    private void setElfState(int state) {
        this.entityData.set(DATA_STATE, state);
    }

    public boolean isWild() {
        return this.getElfState() == STATE_WILD;
    }

    public boolean isBound() {
        return this.getElfState() == STATE_BOUND;
    }

    public boolean isFree() {
        return this.getElfState() == STATE_FREE;
    }

    /** True when the elf entered service willingly rather than under duress. */
    public boolean isWilling() {
        return this.entityData.get(DATA_WILLING);
    }

    /** True while a noticed wizard is inside {@link #FLIGHT_RADIUS}. Synched: the cower clip is client-side. */
    public boolean isCowering() {
        return this.entityData.get(DATA_COWERING);
    }

    private void setCowering(boolean cowering) {
        if (this.entityData.get(DATA_COWERING) != cowering) {
            this.entityData.set(DATA_COWERING, cowering);
        }
    }

    @Nullable
    public Player getMaster() {
        return this.masterUuid == null ? null : this.level().getPlayerByUUID(this.masterUuid);
    }

    /**
     * Held by force - Imperio, or anything else that overrides the elf's will.
     * While this is running the elf cannot Apparate away.
     */
    public void markSubdued(int ticks) {
        this.subduedTicks = Math.max(this.subduedTicks, ticks);
    }

    // -------------------------------------------------------------- noticing

    /**
     * Whether this player registers to the elf as a wizard bearing down on it.
     *
     * <p>Everything in the "approach unseen" family lands here: the mod's
     * Invisibility Cloak and the vanilla invisibility potion both go through
     * {@link LivingEntity#getVisibilityPercent}, Polyjuice is read straight off
     * the player's active disguise, and crouching at a distance works with no
     * items at all.
     */
    public boolean noticesWizard(Player player) {
        if (player.isSpectator() || player.isCreative()) {
            return false;
        }
        // Cloak or potion. getVisibilityPercent also honours vanilla's rule that
        // worn armour gives an invisible player away.
        if (player.getVisibilityPercent(this) < 0.35D) {
            return false;
        }
        if (isDisguisedAsCreature(player)) {
            return false;
        }
        return !player.isCrouching()
                || this.distanceToSqr(player) < CROUCH_SAFE_DISTANCE * CROUCH_SAFE_DISTANCE;
    }

    /** True when Polyjuice has the player wearing the shape of something that is not a wizard. */
    private static boolean isDisguisedAsCreature(Player player) {
        if (!PolyjuiceData.hasActive(player)) {
            return false;
        }
        CompoundTag sample = PolyjuiceData.getActive(player);
        if (!PolyjuiceData.isMobSample(sample)) {
            return false;
        }
        return PolyjuiceData.getEntityType(sample)
                .map(type -> type != EntityType.PLAYER
                        && type != ModEntities.WIZARD.get()
                        && type != ModEntities.STUDENT.get()
                        && type != ModEntities.VOLDEMORT.get())
                .orElse(false);
    }

    /** The nearest player this elf has actually noticed, within the given range. */
    @Nullable
    private Player nearestNoticedWizard(double range) {
        Player nearest = null;
        double best = range * range;
        for (Player player : this.level().players()) {
            double distance = this.distanceToSqr(player);
            if (distance < best && this.noticesWizard(player)) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    // --------------------------------------------------------------- pinning

    /**
     * Whether the elf can still crack out of here. Every way of holding one in
     * place funnels through this one check.
     */
    public boolean canDisapparate() {
        if (this.subduedTicks > 0) {
            return false; // Imperio, or anything else that has taken its will
        }
        if (this.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
            return false; // Petrificus Totalus, Incarcerous, Glacius, Stupefy, Arresto Momentum
        }
        if (this.hasEffect(ExampleMod.INCARCEROUS_CHAINS.getHolder().get())) {
            return false;
        }
        if (this.level() instanceof ServerLevel server
                && CaveInimicumManager.get(server).isWardedAt(this.blockPosition())) {
            return false; // the ward's whole purpose: nobody Apparates in or out
        }
        return true;
    }

    // ------------------------------------------------------------ apparition

    private void beginDisapparation() {
        if (this.disapparatingTicks > 0 || !(this.level() instanceof ServerLevel)) {
            return;
        }
        this.disapparatingTicks = DISAPPARATE_WINDUP;
        this.level().broadcastEntityEvent(this, EVENT_DISAPPARATE);
        this.playSound(ModSounds.HOUSE_ELF_CRACK.get(), 1.0F, 1.0F);
    }

    private void finishDisapparation(ServerLevel server) {
        spawnApparitionParticles(server, this.position());

        // It leaves, but it does not die - a rare creature that vanished for good
        // every time a player blundered near would not stay findable for long.
        BlockPos landing = findReappearPos(server);
        if (landing == null) {
            this.discard();
            return;
        }
        this.teleportTo(landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D);
        this.setDeltaMovement(Vec3.ZERO);
        this.disapparatingTicks = 0;
        this.setCowering(false);
        spawnApparitionParticles(server, this.position());
        server.playSound(null, this.blockPosition(), ModSounds.HOUSE_ELF_CRACK.get(),
                SoundSource.NEUTRAL, 0.8F, 1.0F);
    }

    /** The Apparition fails: something is holding the elf here. */
    private void abortDisapparation() {
        this.disapparatingTicks = 0;
        this.level().broadcastEntityEvent(this, EVENT_APPARITION_FAILED);
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE,
                    this.getX(), this.getY() + 0.5D, this.getZ(), 14, 0.3D, 0.4D, 0.3D, 0.01D);
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    this.getX(), this.getY() + 0.5D, this.getZ(), 18, 0.35D, 0.45D, 0.35D, 0.06D);
            server.playSound(null, this.blockPosition(), ModSounds.HOUSE_ELF_CRACK.get(),
                    SoundSource.NEUTRAL, 0.45F, 0.55F);
        }
    }

    @Nullable
    private BlockPos findReappearPos(ServerLevel server) {
        RandomSource random = this.getRandom();
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = Mth.nextDouble(random, REAPPEAR_MIN, REAPPEAR_MAX);
            int x = Mth.floor(this.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(this.getZ() + Math.sin(angle) * distance);
            if (!server.hasChunkAt(new BlockPos(x, 0, z))) {
                continue;
            }
            BlockPos ground = server.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, 0, z));
            if (server.noCollision(this.getType().getSpawnAABB(
                    ground.getX() + 0.5D, ground.getY(), ground.getZ() + 0.5D))) {
                return ground;
            }
        }
        return null;
    }

    private static void spawnApparitionParticles(ServerLevel server, Vec3 at) {
        server.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 0.4D, at.z, 30, 0.3D, 0.5D, 0.3D, 0.35D);
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 0.4D, at.z, 18, 0.2D, 0.4D, 0.2D, 0.1D);
        server.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2D, at.z, 12, 0.25D, 0.15D, 0.25D, 0.02D);
    }

    // ---------------------------------------------------------- interactions

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        if (this.isWild()) {
            return this.tryBind(player, held);
        }
        if (this.isBound() && this.isMaster(player)) {
            return this.tryFree(player, held);
        }
        return super.mobInteract(player, hand);
    }

    private boolean isMaster(Player player) {
        return this.masterUuid != null && this.masterUuid.equals(player.getUUID());
    }

    /**
     * Offering food to a wild elf that is not fleeing binds it. It is not fleeing
     * either because it never noticed you, or because it cannot get away.
     */
    private InteractionResult tryBind(Player player, ItemStack held) {
        if (held.isEmpty() || held.get(DataComponents.FOOD) == null) {
            return InteractionResult.PASS;
        }
        boolean unnoticed = !this.noticesWizard(player);
        if (!unnoticed && this.canDisapparate()) {
            // It has seen you and it is free to go. It will not take your food.
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // Gentle routes leave the elf willing; taking one by force does not.
        boolean gently = unnoticed || this.wardedOnly();
        this.masterUuid = player.getUUID();
        this.entityData.set(DATA_WILLING, gently);
        this.setElfState(STATE_BOUND);
        this.setPersistenceRequired();
        this.setCowering(false);
        this.subduedTicks = 0;
        this.disapparatingTicks = 0;

        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        this.playSound(ModSounds.HOUSE_ELF_BIND.get(), 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable(
                gently ? "message.hogwartsmagic.house_elf.bound_willing"
                        : "message.hogwartsmagic.house_elf.bound_forced"), true);
        return InteractionResult.CONSUME;
    }

    /** True when the only thing holding the elf is a Cave Inimicum ward, not force. */
    private boolean wardedOnly() {
        if (this.subduedTicks > 0
                || this.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                || this.hasEffect(ExampleMod.INCARCEROUS_CHAINS.getHolder().get())) {
            return false;
        }
        return this.level() instanceof ServerLevel server
                && CaveInimicumManager.get(server).isWardedAt(this.blockPosition());
    }

    /** Presenting clothes to a bound elf frees it. What that costs you depends. */
    private InteractionResult tryFree(Player player, ItemStack held) {
        if (held.isEmpty() || !(held.getItem() instanceof Equipable equipable)
                || equipable.getEquipmentSlot() == EquipmentSlot.MAINHAND
                || equipable.getEquipmentSlot() == EquipmentSlot.OFFHAND) {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        this.playSound(ModSounds.HOUSE_ELF_FREE.get(), 1.0F, 1.0F);

        if (this.isWilling()) {
            this.setElfState(STATE_FREE);
            this.level().broadcastEntityEvent(this, EVENT_JOY);
            player.displayClientMessage(Component.translatable(
                    "message.hogwartsmagic.house_elf.freed_friend"), true);
        } else {
            // It has what it wanted from you. It does not stay.
            player.displayClientMessage(Component.translatable(
                    "message.hogwartsmagic.house_elf.freed_resentful"), true);
            if (this.level() instanceof ServerLevel server) {
                spawnApparitionParticles(server, this.position());
            }
            this.playSound(ModSounds.HOUSE_ELF_CRACK.get(), 1.0F, 1.0F);
            this.discard();
        }
        return InteractionResult.CONSUME;
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.tickClientAnimations();
            return;
        }

        if (this.subduedTicks > 0) {
            this.subduedTicks--;
        }
        if (this.protectCooldown > 0) {
            this.protectCooldown--;
        }

        if (this.disapparatingTicks > 0) {
            // A spell that pins lands a tick or two after the impact damage that
            // startled the elf, so the crack has to be revocable: it strains,
            // sparks, and stays exactly where it is.
            if (!this.canDisapparate()) {
                this.abortDisapparation();
                return;
            }
            this.disapparatingTicks--;
            if (this.disapparatingTicks == 0 && this.level() instanceof ServerLevel server) {
                this.finishDisapparation(server);
            }
            return;
        }

        if (this.isWild()) {
            this.tickWild();
        } else {
            this.tickServing();
        }
    }

    private void tickWild() {
        Player threat = this.nearestNoticedWizard(FLIGHT_RADIUS);
        this.setCowering(threat != null);
        if (threat != null && this.canDisapparate()) {
            this.beginDisapparation();
        }
    }

    private void tickServing() {
        Player master = this.getMaster();
        if (master == null || master.isRemoved()) {
            return;
        }
        // A bound elf blames itself when its master is hurt, and says so with its
        // own fists. A freed one has better things to do.
        if (this.isBound() && master.hurtTime == master.hurtDuration - 1 && master.hurtTime > 0
                && this.distanceToSqr(master) < 256.0D && this.random.nextInt(2) == 0) {
            this.level().broadcastEntityEvent(this, EVENT_PUNISH);
            this.playSound(ModSounds.HOUSE_ELF_PUNISH.get(), 0.9F, 1.0F);
            this.hurt(this.damageSources().generic(), 0.5F);
        }
        if (this.isFree() && this.isWilling()) {
            this.tryProtect(master);
        }
    }

    /**
     * The Lucius Malfoy moment: whoever raised a hand to the elf's friend gets
     * thrown across the clearing.
     */
    private void tryProtect(Player master) {
        if (this.protectCooldown > 0 || !(this.level() instanceof ServerLevel server)) {
            return;
        }
        LivingEntity attacker = master.getLastHurtByMob();
        if (attacker == null || !attacker.isAlive() || attacker == this
                || master.getLastHurtByMobTimestamp() + 40 < master.tickCount) {
            return;
        }
        if (this.distanceToSqr(attacker) > PROTECT_RADIUS * PROTECT_RADIUS) {
            return;
        }

        this.protectCooldown = PROTECT_COOLDOWN_TICKS;
        Vec3 away = attacker.position().subtract(this.position()).normalize();
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class,
                new AABB(attacker.blockPosition()).inflate(2.5D),
                candidate -> candidate != this && candidate != master && candidate.isAlive())) {
            target.hurt(this.damageSources().magic(), PROTECT_DAMAGE);
            target.push(away.x * 1.6D, 0.55D, away.z * 1.6D);
            target.hurtMarked = true;
        }
        server.sendParticles(ParticleTypes.SWEEP_ATTACK,
                attacker.getX(), attacker.getY() + 0.8D, attacker.getZ(), 6, 0.4D, 0.3D, 0.4D, 0.0D);
        server.sendParticles(ParticleTypes.ENCHANT,
                attacker.getX(), attacker.getY() + 0.8D, attacker.getZ(), 24, 0.5D, 0.5D, 0.5D, 0.4D);
        server.playSound(null, this.blockPosition(), ModSounds.HOUSE_ELF_CRACK.get(),
                SoundSource.NEUTRAL, 0.7F, 1.4F);
    }

    private void tickClientAnimations() {
        this.idleAnimationState.startIfStopped(this.tickCount);
        if (this.isCowering()) {
            this.cowerAnimationState.startIfStopped(this.tickCount);
        } else {
            this.cowerAnimationState.stop();
        }
        // JOY loops, so it has to be cut off by hand; left running the elf would
        // celebrate its freedom for the rest of the save.
        if (this.joyAnimationState.isStarted()
                && this.joyAnimationState.getAccumulatedTime() > REJOICE_MILLIS) {
            this.joyAnimationState.stop();
        }
        // DISAPPARATE ends with the root scaled to almost nothing, and a
        // non-looping clip holds its last keyframe forever. Left running, an elf
        // that reappeared elsewhere would stay the size of a mouse.
        if (this.disapparateAnimationState.isStarted()
                && this.disapparateAnimationState.getAccumulatedTime() > DISAPPARATE_MILLIS) {
            this.disapparateAnimationState.stop();
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case EVENT_PUNISH -> this.punishAnimationState.start(this.tickCount);
            case EVENT_JOY -> this.joyAnimationState.start(this.tickCount);
            case EVENT_DISAPPARATE -> this.disapparateAnimationState.start(this.tickCount);
            case EVENT_APPARITION_FAILED -> this.disapparateAnimationState.stop();
            default -> super.handleEntityEvent(id);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        // A frightened wild elf leaves rather than fights - unless it is being held.
        if (hurt && !this.level().isClientSide && this.isWild() && this.canDisapparate()) {
            this.beginDisapparation();
        }
        return hurt;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return this.isWild();
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    // ------------------------------------------------------------------ save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ElfState", this.getElfState());
        tag.putBoolean("Willing", this.isWilling());
        if (this.masterUuid != null) {
            tag.putUUID("Master", this.masterUuid);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setElfState(tag.getInt("ElfState"));
        this.entityData.set(DATA_WILLING, tag.getBoolean("Willing"));
        this.masterUuid = tag.hasUUID("Master") ? tag.getUUID("Master") : null;
    }

    // ----------------------------------------------------------------- sound

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return switch (this.random.nextInt(3)) {
            case 0 -> ModSounds.HOUSE_ELF_AMBIENT1.get();
            case 1 -> ModSounds.HOUSE_ELF_AMBIENT2.get();
            default -> ModSounds.HOUSE_ELF_AMBIENT3.get();
        };
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.HOUSE_ELF_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.HOUSE_ELF_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.7F;
    }

    // -------------------------------------------------------------- spawning

    /**
     * House elves are rare and, above all, alone. Beyond the usual ground and
     * light rules this refuses to place one anywhere near another.
     */
    public static boolean canSpawn(EntityType<HouseElfEntity> type, ServerLevelAccessor level,
                                   MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (reason == MobSpawnType.SPAWNER || reason == MobSpawnType.SPAWN_EGG
                || reason == MobSpawnType.STRUCTURE || reason == MobSpawnType.COMMAND) {
            return true;
        }
        if (!level.getBlockState(pos.below()).isSolidRender(level, pos.below())
                || !level.getFluidState(pos).isEmpty()) {
            return false;
        }
        AABB solitude = new AABB(pos).inflate(SOLITUDE_RADIUS);
        List<HouseElfEntity> neighbours =
                level.getEntitiesOfClass(HouseElfEntity.class, solitude, elf -> true);
        return neighbours.isEmpty();
    }

    // ------------------------------------------------------------------ goals

    /**
     * Backs away from any wizard the elf has noticed. It runs whether or not the
     * elf can Apparate out: a pinned elf still tries, which is what makes the
     * capture read as a capture.
     */
    private class FleeNoticedWizardGoal extends Goal {

        @Nullable
        private Player threat;

        FleeNoticedWizardGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!HouseElfEntity.this.isWild()) {
                return false;
            }
            this.threat = HouseElfEntity.this.nearestNoticedWizard(FLIGHT_RADIUS + 3.0D);
            return this.threat != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.threat != null && this.threat.isAlive()
                    && HouseElfEntity.this.isWild()
                    && HouseElfEntity.this.noticesWizard(this.threat)
                    && HouseElfEntity.this.distanceToSqr(this.threat) < 144.0D;
        }

        @Override
        public void stop() {
            this.threat = null;
        }

        @Override
        public void tick() {
            if (this.threat == null) {
                return;
            }
            HouseElfEntity.this.getLookControl().setLookAt(this.threat, 30.0F, 30.0F);
            Vec3 away = HouseElfEntity.this.position()
                    .subtract(this.threat.position()).normalize().scale(6.0D);
            Vec3 target = HouseElfEntity.this.position().add(away);
            HouseElfEntity.this.getNavigation().moveTo(target.x, target.y, target.z, 1.3D);
        }
    }

    /** A bound elf keeps a servile distance; a freed one simply keeps up. */
    private class FollowMasterGoal extends Goal {

        private static final double START_DISTANCE = 6.0D;
        private static final double STOP_DISTANCE = 3.0D;
        /** Beyond this the elf simply Apparates to its friend instead of walking. */
        private static final double APPARATE_DISTANCE = 18.0D;

        @Nullable
        private Player master;
        private int repathCooldown;

        FollowMasterGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (HouseElfEntity.this.isWild()) {
                return false;
            }
            // A freed elf that was taken by force never got this far, but a freed
            // willing one stays by choice, so both served states follow.
            this.master = HouseElfEntity.this.getMaster();
            return this.master != null && !this.master.isSpectator()
                    && HouseElfEntity.this.distanceToSqr(this.master) > START_DISTANCE * START_DISTANCE;
        }

        @Override
        public boolean canContinueToUse() {
            return this.master != null && this.master.isAlive() && !HouseElfEntity.this.isWild()
                    && HouseElfEntity.this.distanceToSqr(this.master) > STOP_DISTANCE * STOP_DISTANCE;
        }

        @Override
        public void stop() {
            this.master = null;
            HouseElfEntity.this.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (this.master == null) {
                return;
            }
            HouseElfEntity.this.getLookControl().setLookAt(this.master, 30.0F, 30.0F);

            if (HouseElfEntity.this.distanceToSqr(this.master) > APPARATE_DISTANCE * APPARATE_DISTANCE
                    && HouseElfEntity.this.level() instanceof ServerLevel server) {
                spawnApparitionParticles(server, HouseElfEntity.this.position());
                Vec3 beside = this.master.position().add(
                        HouseElfEntity.this.random.nextDouble() * 2.0D - 1.0D, 0.0D,
                        HouseElfEntity.this.random.nextDouble() * 2.0D - 1.0D);
                HouseElfEntity.this.teleportTo(beside.x, beside.y, beside.z);
                spawnApparitionParticles(server, HouseElfEntity.this.position());
                HouseElfEntity.this.playSound(ModSounds.HOUSE_ELF_CRACK.get(), 0.6F, 1.2F);
                return;
            }

            if (--this.repathCooldown <= 0) {
                this.repathCooldown = 10;
                HouseElfEntity.this.getNavigation().moveTo(this.master, 1.15D);
            }
        }
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return !this.isWild();
    }

    @Override
    protected void doPush(Entity other) {
        if (!this.isWild()) {
            super.doPush(other);
        }
    }
}
