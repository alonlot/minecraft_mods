package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A suit of animated plate: dormant stone until something comes too close, then a slow, heavy
 * fighter that cannot be pushed around.
 *
 * <h2>The four states</h2>
 * The mob is a small state machine and every state is meant to be readable from across the room:
 * <ul>
 *   <li><b>Dormant</b> -- no AI, no idle motion, kneeling with the halberd planted. It is scenery.
 *       Two things end it: something hostile coming inside {@link #WAKE_RADIUS}, or
 *       {@link #wake()} being called from outside (which is how the ruin camp's pair are set off
 *       by the chest).</li>
 *   <li><b>Waking</b> -- {@link #WAKE_TICKS} of rising out of the kneel, during which it does not
 *       move or strike. That delay is the point: it is the window the player gets to realise what
 *       is happening and back off.</li>
 *   <li><b>Active</b> -- fights. Slow, strong, immovable.</li>
 *   <li><b>Crumbling</b> -- folds back down. A player-animated sentry does not actually die here;
 *       see {@link #hurt}.</li>
 * </ul>
 *
 * <h2>Why it fights the way it does</h2>
 * Everything about the combat profile says "several tons of granite". Knockback resistance is a
 * flat 1.0, so nothing shoves it -- not a sword, not an explosion, not Depulso. Its own attack
 * knockback is high, so it throws <i>you</i>. It swings on a {@link #ATTACK_COOLDOWN}-tick cycle
 * rather than the usual 20, and hits about three times as hard to pay for it: the fight is meant
 * to be about spacing and timing, not about trading blows. Movement speed is low for the same
 * reason -- you can outrun it, and that is deliberate, because a sentry you cannot escape is a
 * death sentence rather than a threat.
 */
public class LivingStatueEntity extends Monster {

    /** How long the rise takes. Slow on purpose -- this is the tell, not a formality. */
    public static final int WAKE_TICKS = 50;
    /** How long it takes to fold back down when destroyed. */
    public static final int CRUMBLE_TICKS = 30;
    /** A dormant statue notices anything hostile inside this many blocks. */
    public static final double WAKE_RADIUS = 5.0;
    /**
     * The combo: two diagonal cleaves and then a two-handed overhead split that cracks the floor.
     *
     * <p>Each entry is the whole beat -- wind-up, strike, and the stagger afterwards -- so the
     * statue is committed for that long once it starts. The third is longer because it hits
     * harder and takes longer to haul back up, and the recovery is what gives a player the room
     * to punish it. {@link #STRIKE_AT} is where in the beat the blow actually lands, which is
     * deliberately NOT the moment the AI asks to attack: the damage has to arrive with the blade,
     * or a player who backs off during the wind-up still gets hit by a swing that visibly missed.
     */
    public static final int[] SWING_TICKS = {34, 34, 46};
    /** Fraction into a beat at which the blade arrives. Matches the model's strike window. */
    public static final float STRIKE_AT = 0.45F;
    /** Breather after a full three-hit combo, before it can start another. */
    public static final int COMBO_COOLDOWN = 45;

    private static final EntityDataAccessor<Integer> DATA_WAKE =
            SynchedEntityData.defineId(LivingStatueEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CRUMBLE =
            SynchedEntityData.defineId(LivingStatueEntity.class, EntityDataSerializers.INT);
    /** True for one the player raised with Piertotum Locomotor; false for a ruin guardian. */
    private static final EntityDataAccessor<Boolean> DATA_BOUND =
            SynchedEntityData.defineId(LivingStatueEntity.class, EntityDataSerializers.BOOLEAN);
    /** Which blow of the combo is in flight, or -1 for none. Synched: the model draws from it. */
    private static final EntityDataAccessor<Integer> DATA_COMBO =
            SynchedEntityData.defineId(LivingStatueEntity.class, EntityDataSerializers.INT);
    /** Ticks into the current blow. Synched for the same reason. */
    private static final EntityDataAccessor<Integer> DATA_SWING =
            SynchedEntityData.defineId(LivingStatueEntity.class, EntityDataSerializers.INT);

    @Nullable
    private UUID ownerUUID;
    /** Where it was raised. A bound sentry returns here when it is put down. */
    @Nullable
    private BlockPos homePos;
    /** Ticks left before another combo may start. */
    private int comboCooldown;
    /** Set once the current blow's damage has landed, so one swing cannot hit twice. */
    private boolean struck;

    public LivingStatueEntity(EntityType<? extends LivingStatueEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        // It is made of rock, so it wades rather than walking round water. Fire immunity is on the
        // EntityType in ModEntities, where the builder wants it; drowning is left alone, because
        // canBreatheUnderwater is final in 1.21 and the only way to buy it is a tick override that
        // fights the vanilla air counter every tick for a case that never comes up on land.
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER, 0.0F);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 70.0D)
                .add(Attributes.ARMOR, 14.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0D)
                .add(Attributes.ATTACK_DAMAGE, 13.0D)
                // It throws you; nothing throws it.
                .add(Attributes.ATTACK_KNOCKBACK, 2.2D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.19D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_WAKE, 0);
        builder.define(DATA_CRUMBLE, 0);
        builder.define(DATA_BOUND, false);
        builder.define(DATA_COMBO, -1);
        builder.define(DATA_SWING, 0);
    }

    @Override
    protected void registerGoals() {
        // Every goal is gated on being awake, so a dormant statue runs no AI at all rather than
        // standing still because its goals happen to have nothing to do. That gate has to include
        // the look goal: LookControl drives yHeadRot, BodyRotationControl then drags yBodyRot after
        // it, and a "dormant" statue that swivels its whole body to watch you across ten blocks
        // stops being scenery a long way before it stops being asleep.
        this.goalSelector.addGoal(1, new StatueMeleeGoal(this));
        this.goalSelector.addGoal(8, new StatueLookGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, StudentEntity.class));
        // A ruin guardian hunts players. A bound sentry hunts whatever hunts the player.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this, Player.class, 10, true, false,
                p -> this.isAwake() && !this.isBound()));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(
                this, Mob.class, 10, true, false,
                m -> this.isAwake() && this.isBound() && m instanceof Enemy));
    }

    /**
     * Statues and students are on the same side, always.
     *
     * <p>Enforced here rather than only in the target goals because there are three other ways a
     * mob acquires a target -- retaliation, a goal added later, another mob's revenge chain -- and
     * a rule that only lives in one goal's predicate is a rule that leaks. A bound sentry is
     * knockback-immune with 70 HP and a student cannot meaningfully hurt it back, so a fight
     * between them never resolves; it just runs forever next to the fire.
     *
     * <p>This also covers the ruin guardians, which is deliberate: the camp bakes in four students
     * and two dormant statues a few blocks apart, and the guardians are meant to wake for whoever
     * opens the chest, not to start on the residents.
     */
    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof StudentEntity) && super.canAttack(target);
    }

    // --- state ------------------------------------------------------------------------------

    public int getWakeTicks() {
        return this.entityData.get(DATA_WAKE);
    }

    /** 0 while fully dormant, 1 once risen. Drives the model. */
    public float getWakeProgress() {
        return Math.min(1.0F, this.getWakeTicks() / (float) WAKE_TICKS);
    }

    public boolean isAwake() {
        return this.getWakeTicks() >= WAKE_TICKS;
    }

    public boolean isDormant() {
        return this.getWakeTicks() == 0;
    }

    public boolean isCrumbling() {
        return this.entityData.get(DATA_CRUMBLE) > 0;
    }

    public float getCrumbleProgress() {
        return Math.min(1.0F, this.entityData.get(DATA_CRUMBLE) / (float) CRUMBLE_TICKS);
    }

    public boolean isBound() {
        return this.entityData.get(DATA_BOUND);
    }

    /** Which blow of the combo is in flight, or -1 if it is not swinging. Drives the model. */
    public int getComboIndex() {
        return this.entityData.get(DATA_COMBO);
    }

    public boolean isSwinging() {
        return this.getComboIndex() >= 0;
    }

    /**
     * How far through the current blow, 0..1, across the strike AND the stagger after it.
     *
     * <p>{@code partialTick} is folded in here rather than left to the model because the beat is
     * only 34 ticks: stepping it a whole tick at a time makes a swing that is meant to accelerate
     * look like it judders through four frames.
     */
    public float getSwingProgress(float partialTick) {
        int index = this.getComboIndex();
        if (index < 0) {
            return 0.0F;
        }
        float total = SWING_TICKS[Math.min(index, SWING_TICKS.length - 1)];
        return Math.min(1.0F, (this.entityData.get(DATA_SWING) + partialTick) / total);
    }

    /** Start the rise. Safe to call repeatedly; only the first call does anything. */
    public void wake() {
        if (this.getWakeTicks() == 0 && !this.isCrumbling()) {
            this.entityData.set(DATA_WAKE, 1);
            this.level().playSound(null, this.blockPosition(), ModSounds.STATUE_WAKE.get(),
                    SoundSource.HOSTILE, 1.3F, 1.0F);
        }
    }

    public void bindTo(Player owner, BlockPos home) {
        this.ownerUUID = owner.getUUID();
        this.homePos = home;
        this.entityData.set(DATA_BOUND, true);
    }

    @Nullable
    public BlockPos getHomePos() {
        return this.homePos;
    }

    /**
     * Is {@code entity} the player this was raised by? Compared by UUID rather than by holding a
     * reference: the owner logs out, and the statue outlives them.
     */
    public boolean isOwnedBy(@Nullable net.minecraft.world.entity.Entity entity) {
        return entity != null && this.ownerUUID != null && this.ownerUUID.equals(entity.getUUID());
    }

    // --- ticking ----------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            if (!this.isDormant() && this.random.nextInt(6) == 0) {
                // Grit shakes loose while it moves. Only ever while awake -- dust coming off a
                // dormant statue would give the ambush away before the player is in range.
                this.level().addParticle(new BlockParticleOption(
                                ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                        this.getRandomX(0.7D), this.getY() + this.random.nextDouble() * 1.8D,
                        this.getRandomZ(0.7D), 0.0D, -0.02D, 0.0D);
            }
            return;
        }

        if (this.comboCooldown > 0) {
            this.comboCooldown--;
        }
        this.advanceCombo();

        int crumble = this.entityData.get(DATA_CRUMBLE);
        if (crumble > 0) {
            this.entityData.set(DATA_CRUMBLE, crumble + 1);
            if (crumble + 1 >= CRUMBLE_TICKS) {
                this.finishCrumble();
            }
            return;
        }

        int wake = this.getWakeTicks();
        if (wake == 0) {
            // A statue baked into a structure is placed by StructureTemplate.placeEntities, which
            // calls moveTo -- and moveTo sets yRot only. The saved Rotation tag has already been
            // read into yHeadRot/yBodyRot by Entity.load, so after a rotated jigsaw placement the
            // body still faces the template-local yaw while yRot faces the right way, and the
            // renderer draws yBodyRot. Nothing in vanilla repairs that for a mob that never moves,
            // so the dormant tick does it: the pose it was placed in is the pose it holds.
            this.setYBodyRot(this.getYRot());
            this.setYHeadRot(this.getYRot());
            if (this.tickCount % 10 == 0 && this.senseIntruder()) {
                this.wake();
            }
            return;
        }
        if (wake < WAKE_TICKS) {
            this.entityData.set(DATA_WAKE, wake + 1);
            if (wake % 8 == 0) {
                this.level().playSound(null, this.blockPosition(), ModSounds.STATUE_RISE.get(),
                        SoundSource.HOSTILE, 1.0F, 1.0F);
            }
        }
    }

    /** Is something it should object to standing inside {@link #WAKE_RADIUS}? */
    private boolean senseIntruder() {
        AABB box = this.getBoundingBox().inflate(WAKE_RADIUS);
        if (this.isBound()) {
            return !this.level().getEntitiesOfClass(Mob.class, box,
                    m -> m instanceof Enemy && m.isAlive() && m != this).isEmpty();
        }
        return this.level().getEntitiesOfClass(Player.class, box,
                p -> !p.isSpectator() && !p.isCreative()).stream().findAny().isPresent();
    }

    /**
     * A dormant statue is stone, and stone does not stagger, drift, or fall over. Freezing the
     * whole physics step is the only way to get that: left to the normal path it slides on ice,
     * gets pushed by water, and rocks under attack, all of which break the illusion instantly.
     */
    @Override
    public void travel(net.minecraft.world.phys.Vec3 input) {
        if (!this.isAwake() || this.isCrumbling()) {
            this.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            super.travel(net.minecraft.world.phys.Vec3.ZERO);
            return;
        }
        super.travel(input);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity other) {
        // It pushes through things; nothing pushes it.
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isCrumbling()) {
            return false;
        }
        // Its own caster cannot hurt it, and so cannot start a fight with it. Without this,
        // HurtByTargetGoal makes one stray Confringo or one curious punch turn a 70 HP sentry
        // that cannot be outrun and cannot be killed onto the player who raised it -- and since a
        // bound statue re-forms at full health, that fight has no end. Refusing the damage costs
        // the owner nothing they could otherwise do: lethal damage never destroyed one anyway.
        if (this.isBound() && this.isOwnedBy(source.getEntity())) {
            return false;
        }
        // Hitting a sleeping statue is a perfectly good way to find out what it is.
        boolean wasDormant = this.isDormant();
        if (wasDormant && !source.isCreativePlayer()) {
            this.wake();
        }
        // Stone shrugs off arrows and takes very little from anything thrown at it, but a
        // pickaxe-shaped answer (any player melee) lands normally.
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
            amount *= 0.35F;
        }
        if (!this.level().isClientSide && this.isBound()
                && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                && this.getHealth() - amount <= 0.0F) {
            // A bound sentry is never destroyed by damage. It is put down, and it comes back at
            // the post it was raised on -- which is what "goes back to where it was created"
            // means mechanically, and what stops a player losing the statue heart they spent.
            //
            // BYPASSES_INVULNERABILITY is let through on purpose: /kill and falling out of the
            // world route here too, and a sentry that shrugs those off is an entity an admin
            // cannot remove from their own server.
            //
            // The comparison is against the RAW amount, before armour, so a big enough blow puts
            // it down a hit sooner than its 14 armour says it should. That is the safe direction
            // and the only cheap one: the alternative is to let super.hurt run and revive it
            // afterwards, by which point LivingEntity has already called die() and rolled the
            // entity loot table -- which would hand the player back the heart they spent.
            this.setHealth(1.0F);
            this.beginCrumble();
            return true;
        }
        return super.hurt(source, amount);
    }

    /**
     * The slow swing, enforced here rather than by widening MeleeAttackGoal's interval.
     *
     * <p>MeleeAttackGoal's cooldown hook has moved between versions (it has been both
     * {@code getAttackInterval()} and {@code getAttackInterval(LivingEntity)}), and overriding a
     * signature that does not exist fails at compile time for a reason that reads as unrelated.
     * {@code doHurtTarget} has been stable throughout, so the cooldown lives on this side of the
     * fence: the goal may ask to swing as often as it likes, and only one in
     * {@link #ATTACK_COOLDOWN} ticks actually lands. That also gives the model a clean signal to
     * animate against.
     */
    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        if (this.isSwinging() || this.comboCooldown > 0 || !this.isAwake() || this.isCrumbling()) {
            return false;
        }
        // Deliberately NO damage here. This only opens the combo; every blow lands from
        // advanceCombo at its own strike frame, so the hit arrives with the blade instead of on
        // the frame the AI happened to ask. Returning true keeps MeleeAttackGoal's own cooldown
        // happy -- it never needs to know the swing outlives the call.
        this.beginSwing(0);
        return true;
    }

    /**
     * Runs the three-blow combo on its own clock.
     *
     * <p>Once it starts it finishes, whether or not the target is still standing there. A combo
     * that re-checks its victim between blows stutters to a halt the moment the player steps
     * back -- which is precisely when a committed wind-up should be leaving it open.
     */
    private void advanceCombo() {
        int index = this.getComboIndex();
        if (index < 0) {
            return;
        }
        if (this.isCrumbling() || !this.isAwake()) {
            this.endCombo();
            return;
        }
        int tick = this.entityData.get(DATA_SWING) + 1;
        this.entityData.set(DATA_SWING, tick);
        int total = SWING_TICKS[Math.min(index, SWING_TICKS.length - 1)];
        if (!this.struck && tick >= total * STRIKE_AT) {
            this.struck = true;
            this.performStrike(index);
        }
        if (tick >= total) {
            if (index + 1 < SWING_TICKS.length) {
                this.beginSwing(index + 1);
            } else {
                this.endCombo();
                this.comboCooldown = COMBO_COOLDOWN;
            }
        }
    }

    private void beginSwing(int index) {
        this.entityData.set(DATA_COMBO, index);
        this.entityData.set(DATA_SWING, 0);
        this.struck = false;
        this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        // The wind-up is audible before it is dangerous: stone grinding as it hauls the halberd
        // up. Pitched lower for the heavy third so the player can hear which one is coming.
        this.level().playSound(null, this.blockPosition(), ModSounds.STATUE_SWING.get(),
                SoundSource.HOSTILE, 1.2F, index == 2 ? 0.82F : 1.0F);
    }

    private void endCombo() {
        this.entityData.set(DATA_COMBO, -1);
        this.entityData.set(DATA_SWING, 0);
        this.struck = false;
    }

    /**
     * The blade arrives.
     *
     * <p>The cleaves sweep an arc in FRONT of the statue rather than hitting only the one entity
     * the AI picked -- a halberd that connects with a single target reads as a punch. The
     * overhead split skips the facing test on purpose: it is a shockwave, not a swing.
     */
    private void performStrike(int index) {
        boolean heavy = index == 2;
        double reach = heavy ? 4.5D : 3.8D;
        AABB box = this.getBoundingBox().inflate(reach, heavy ? 2.5D : 1.5D, reach);
        net.minecraft.world.phys.Vec3 facing =
                net.minecraft.world.phys.Vec3.directionFromRotation(0.0F, this.yBodyRot);
        for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class, box,
                this::canStrike)) {
            if (!heavy) {
                net.minecraft.world.phys.Vec3 towards =
                        victim.position().subtract(this.position()).normalize();
                if (facing.dot(towards) < 0.2D) {
                    continue;
                }
            }
            super.doHurtTarget(victim);
        }
        this.level().playSound(null, this.blockPosition(), ModSounds.STATUE_IMPACT.get(),
                SoundSource.HOSTILE, heavy ? 1.7F : 1.1F, heavy ? 0.82F : 1.0F);
        if (heavy) {
            this.groundSlam();
        }
    }

    /** Never the caster, never a student, never another statue. Mirrors {@link #canAttack}. */
    private boolean canStrike(LivingEntity other) {
        return other != this
                && other.isAlive()
                && !(other instanceof LivingStatueEntity)
                && !(other instanceof StudentEntity)
                && !(this.isBound() && this.isOwnedBy(other))
                // A sentry's blows name the statue, not its caster, so PvP-off and teams are checked here
                // for it (as a tamed wolf does): it never cuts down a player its caster may not harm.
                && !(this.isBound() && other instanceof Player player && this.level() instanceof ServerLevel server
                        && com.hogwartsmagic.spell.mind.MindControl.puppeteerSpares(server, this.ownerUUID, player));
    }

    /**
     * The floor takes it.
     *
     * <p>1.21 has no camera-shake hook, so the shake is sold three ways at once: rings of dust
     * torn off whatever it is actually standing on, a low crack that carries further than the
     * swing sounds, and a shove that lifts everything nearby off its feet. The last one is the
     * part a player feels rather than sees, and it is why the push has an upward component --
     * a purely horizontal knockback reads as being hit, not as the ground moving.
     */
    private void groundSlam() {
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        net.minecraft.world.level.block.state.BlockState floor =
                server.getBlockState(this.blockPosition().below());
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK,
                floor.isAir() ? Blocks.STONE.defaultBlockState() : floor);
        for (int ring = 1; ring <= 3; ring++) {
            double radius = ring * 1.4D;
            int points = 10 * ring;
            for (int i = 0; i < points; i++) {
                double a = (Math.PI * 2.0D) * i / points;
                server.sendParticles(dust, this.getX() + Math.cos(a) * radius,
                        this.getY() + 0.15D, this.getZ() + Math.sin(a) * radius,
                        2, 0.1D, 0.05D, 0.1D, 0.12D);
            }
        }
        server.playSound(null, this.blockPosition(), ModSounds.STATUE_SLAM.get(),
                SoundSource.HOSTILE, 1.3F, 1.0F);
        for (LivingEntity shaken : server.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(7.0D), this::canStrike)) {
            double dx = shaken.getX() - this.getX();
            double dz = shaken.getZ() - this.getZ();
            double dist = Math.max(0.4D, Math.sqrt(dx * dx + dz * dz));
            double falloff = 1.0D - Math.min(1.0D, dist / 7.0D);
            shaken.push(dx / dist * falloff * 0.55D, 0.30D * falloff + 0.10D,
                    dz / dist * falloff * 0.55D);
            // Without this the server never tells the client its velocity changed, and a player
            // standing in the blast simply does not move.
            shaken.hurtMarked = true;
        }
    }

    private void beginCrumble() {
        this.entityData.set(DATA_CRUMBLE, 1);
        this.setTarget(null);
        this.level().playSound(null, this.blockPosition(), ModSounds.STATUE_CRUMBLE.get(),
                SoundSource.HOSTILE, 1.5F, 1.0F);
    }

    /** End of the crumble animation: go home, go back to sleep, come back to full strength. */
    private void finishCrumble() {
        this.entityData.set(DATA_CRUMBLE, 0);
        this.entityData.set(DATA_WAKE, 0);
        this.setHealth(this.getMaxHealth());
        this.setTarget(null);
        if (this.homePos != null) {
            this.teleportTo(this.homePos.getX() + 0.5D, this.homePos.getY(),
                    this.homePos.getZ() + 0.5D);
        }
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(new BlockParticleOption(
                            ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                    this.getX(), this.getY() + 1.0D, this.getZ(), 60, 0.6D, 1.0D, 0.6D, 0.05D);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /**
     * Peaceful does not clear these away. {@code Mob.checkDespawn} tests this <i>before</i> it
     * tests {@code isPersistenceRequired}, so the flag the constructor sets is no protection at
     * all: on Peaceful a sentry would be discarded the tick after it was raised, having already
     * eaten the player's statue heart and five blocks of stone, and discarding is not a death, so
     * nothing would drop back.
     */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    /** Dormant statues make no sound at all -- an idle groan gives the ambush away. */
    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return this.isAwake() ? ModSounds.STATUE_AMBIENT.get() : null;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.STATUE_HURT.get();
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return ModSounds.STATUE_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        this.playSound(ModSounds.STATUE_STEP.get(), 0.9F, 1.0F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Wake", this.getWakeTicks());
        tag.putBoolean("Bound", this.isBound());
        if (this.ownerUUID != null) {
            tag.putUUID("Owner", this.ownerUUID);
        }
        if (this.homePos != null) {
            tag.putIntArray("Home", new int[]{
                    this.homePos.getX(), this.homePos.getY(), this.homePos.getZ()});
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_WAKE, tag.getInt("Wake"));
        this.entityData.set(DATA_BOUND, tag.getBoolean("Bound"));
        if (tag.hasUUID("Owner")) {
            this.ownerUUID = tag.getUUID("Owner");
        }
        int[] home = tag.getIntArray("Home");
        if (home.length == 3) {
            this.homePos = new BlockPos(home[0], home[1], home[2]);
        }
    }

    /**
     * Melee on a long cycle, and it will not start swinging until it is fully risen.
     *
     * <p>Extending MeleeAttackGoal only to widen the interval is deliberate: the goal already
     * handles pathing, reach and line of sight correctly, and reimplementing that to change one
     * number is how you end up with a mob that hits through walls.
     */
    private static class StatueMeleeGoal extends MeleeAttackGoal {
        private final LivingStatueEntity statue;

        StatueMeleeGoal(LivingStatueEntity statue) {
            super(statue, 1.0D, true);
            this.statue = statue;
        }

        @Override
        public boolean canUse() {
            return this.statue.isAwake() && !this.statue.isCrumbling() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.statue.isAwake() && !this.statue.isCrumbling() && super.canContinueToUse();
        }

    }

    /**
     * Tracks the player with its head, but only once it is on its feet.
     *
     * <p>The gate cannot be a constructor argument -- LookAtPlayerGoal takes a probability, not a
     * predicate -- so it has to be a subclass, the same way the melee goal is one.
     */
    private static class StatueLookGoal extends LookAtPlayerGoal {
        private final LivingStatueEntity statue;

        StatueLookGoal(LivingStatueEntity statue) {
            super(statue, Player.class, 10.0F);
            this.statue = statue;
        }

        @Override
        public boolean canUse() {
            return this.statue.isAwake() && !this.statue.isCrumbling() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.statue.isAwake() && !this.statue.isCrumbling() && super.canContinueToUse();
        }
    }
}
