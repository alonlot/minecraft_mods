package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.spell.dark.DarkRewardRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Dark Magic Phase 3: the Morsmordre escort. A Lost caster's sky raid calls 4 to 6 Death Eaters to the caster's side
 * for 5 minutes instead of a raid: no raid bar, no raid record. They follow (a 20-block leash), fight whatever hurts
 * their owner or whatever the owner strikes, Apparate to the owner past 32 blocks, and Disapparate when the time is
 * up, the owner leaves the dimension, logs out or dies (even with an immediate respawn), or a new escort replaces
 * them. One the owner strikes breaks off and fights back as any Death Eater would. The numbers live in
 * DarkRewardRules.
 */
public final class DeathEaterEscort {
    private static final String KEY_ARRIVES = "gui.hogwartsmagic.dark.escort_arrives";

    private DeathEaterEscort() {
    }

    /** Summons the escort around the owner. Returns how many came (0 means nothing happened). */
    public static int summon(ServerLevel level, ServerPlayer owner) {
        for (WizardEntity old : level.getEntitiesOfClass(WizardEntity.class,
                owner.getBoundingBox().inflate(DarkRewardRules.ESCORT_REPLACE_RADIUS),
                wizard -> owner.getUUID().equals(wizard.getEscortOwnerId()))) {
            dismiss(old);
        }
        long until = DarkRewardRules.escortUntil(level.getGameTime());
        int size = DarkRewardRules.escortSize(owner.getRandom().nextInt(
                DarkRewardRules.ESCORT_MAX_SIZE - DarkRewardRules.ESCORT_MIN_SIZE + 1));
        int deaths = deathCount(owner);
        int came = 0;
        for (int i = 0; i < size; i++) {
            BlockPos pos = landingNear(level, owner);
            if (pos == null) {
                pos = ownBlockIfSafe(level, owner);
                if (pos == null) {
                    continue; // no safe spot for this one
                }
            }
            WizardEntity wizard = ModEntities.WIZARD.get().create(level);
            if (wizard == null) {
                continue;
            }
            wizard.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
            wizard.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            wizard.setEscort(owner.getUUID(), until);
            wizard.setEscortOwnerDeaths(deaths);
            wizard.addEffect(new MobEffectInstance(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get(), 40, 0));
            if (level.addFreshEntity(wizard)) {
                came++;
                puff(level, wizard);
            }
        }
        if (came > 0) {
            owner.sendSystemMessage(Component.translatable(KEY_ARRIVES).withStyle(ChatFormatting.DARK_GREEN));
        }
        return came;
    }

    /** From WizardEntity.tick (server) while it is an escort. */
    public static void tick(WizardEntity wizard) {
        UUID ownerId = wizard.getEscortOwnerId();
        if (ownerId == null) {
            return;
        }
        LivingEntity hurtBy = wizard.getLastHurtByMob();
        if (hurtBy != null && ownerId.equals(hurtBy.getUUID())) {
            wizard.releaseEscort(); // struck by its owner: an ordinary Death Eater now, and the hurt-by goal answers
            return;
        }
        if (!DarkRewardRules.dueToEscortCheck(wizard.tickCount, wizard.getId())) {
            return;
        }
        ServerPlayer owner = wizard.getEscortOwner();
        if (owner == null || !DarkRewardRules.escortActive(wizard.level().getGameTime(), wizard.getEscortUntil())
                || DarkRewardRules.ownerDiedSince(wizard.getEscortOwnerDeaths(), deathCount(owner))) {
            dismiss(wizard);
            return;
        }
        if (wizard.getEscortOwnerDeaths() < 0) {
            wizard.setEscortOwnerDeaths(deathCount(owner)); // an older save: start counting now
        }
        if (DarkRewardRules.needsCatchUp(DarkRewardRules.horizontalDistanceSqr(owner.getX() - wizard.getX(),
                owner.getZ() - wizard.getZ())) && wizard.level() instanceof ServerLevel level) {
            BlockPos pos = landingNear(level, owner);
            if (pos == null) {
                pos = ownBlockIfSafe(level, owner);
            }
            if (pos == null || !DarkRewardRules.catchUpLandingInReach(
                    owner.distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D))) {
                return; // no safe spot near the owner yet: try again next second
            }
            puff(level, wizard);
            wizard.stopRiding();
            wizard.teleportTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            wizard.getNavigation().stop();
            puff(level, wizard);
        }
    }

    /** Disapparates: smoke, a crack, gone. Drops nothing. */
    public static void dismiss(WizardEntity wizard) {
        if (wizard.level() instanceof ServerLevel level) {
            puff(level, wizard);
        }
        wizard.discard();
    }

    private static void puff(ServerLevel level, WizardEntity wizard) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, wizard.getX(), wizard.getY() + 1.0D, wizard.getZ(),
                20, 0.3D, 0.6D, 0.3D, 0.02D);
        level.playSound(null, wizard.getX(), wizard.getY(), wizard.getZ(), ModSounds.HOUSE_ELF_CRACK.get(),
                SoundSource.HOSTILE, 0.8F, 0.8F);
    }

    /**
     * A spot 3 to 8 blocks from the owner on the surface, or null when that column is not safe
     * (DarkRewardRules.escortLandingOk: an empty column, the owner underground, no sturdy ground, lava below, or no
     * two free blocks). The caller then tries the owner's own block under the same test (ownBlockIfSafe), else skips
     * that member or the catch-up. Near the owner, so the chunk is loaded.
     */
    @Nullable
    private static BlockPos landingNear(ServerLevel level, ServerPlayer owner) {
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        double distance = DarkRewardRules.ESCORT_SPAWN_MIN_DISTANCE + level.random.nextDouble()
                * (DarkRewardRules.ESCORT_SPAWN_MAX_DISTANCE - DarkRewardRules.ESCORT_SPAWN_MIN_DISTANCE);
        BlockPos column = BlockPos.containing(owner.getX() + Math.cos(angle) * distance, owner.getY(),
                owner.getZ() + Math.sin(angle) * distance);
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        return safeLanding(level, surface, owner.getBlockY()) ? surface : null;
    }

    /**
     * The owner's own block, only when it passes the same full test as a column (sturdy ground with no lava below, two
     * free blocks), else null: a flying owner over the void or a lava sea, or one swimming in lava, gets nobody.
     */
    @Nullable
    private static BlockPos ownBlockIfSafe(ServerLevel level, ServerPlayer owner) {
        BlockPos own = owner.blockPosition();
        return safeLanding(level, own, own.getY()) ? own : null;
    }

    /** DarkRewardRules.escortLandingOk read from the world at this landing block. */
    private static boolean safeLanding(ServerLevel level, BlockPos landing, int ownerBlockY) {
        BlockPos below = landing.below();
        return DarkRewardRules.escortLandingOk(landing.getY(), level.getMinBuildHeight(), ownerBlockY,
                level.getBlockState(below).isFaceSturdy(level, below, Direction.UP),
                level.getFluidState(below).is(FluidTags.LAVA), free(level, landing), free(level, landing.above()));
    }

    /** No collision at this block (a Death Eater standing in it does not suffocate). */
    private static boolean free(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** The owner's death count (the vanilla deaths stat), recorded at summon time. */
    private static int deathCount(ServerPlayer owner) {
        return owner.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS));
    }

    /** Walks back to the owner: past the leash always (dropping its target), else when idle and 8 blocks away. */
    public static final class FollowOwnerGoal extends Goal {
        private final WizardEntity wizard;
        @Nullable
        private ServerPlayer owner;
        private int repathTicks;

        public FollowOwnerGoal(WizardEntity wizard) {
            this.wizard = wizard;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!this.wizard.isEscort()) {
                return false;
            }
            ServerPlayer candidate = this.wizard.getEscortOwner();
            if (candidate == null || !DarkRewardRules.escortStartsFollowing(this.wizard.distanceToSqr(candidate),
                    this.wizard.getTarget() != null)) {
                return false;
            }
            this.owner = candidate;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return this.owner != null && this.wizard.isEscort() && this.owner.isAlive()
                    && this.owner.level() == this.wizard.level()
                    && DarkRewardRules.escortKeepsFollowing(this.wizard.distanceToSqr(this.owner),
                    this.wizard.getTarget() != null);
        }

        @Override
        public void start() {
            this.repathTicks = 0;
            if (this.owner != null && DarkRewardRules.beyondLeash(this.wizard.distanceToSqr(this.owner))) {
                this.wizard.setTarget(null);
            }
        }

        @Override
        public void stop() {
            this.owner = null;
            this.wizard.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (this.owner == null) {
                return;
            }
            this.wizard.getLookControl().setLookAt(this.owner, 10.0F, (float) this.wizard.getMaxHeadXRot());
            if (--this.repathTicks <= 0) {
                this.repathTicks = 10;
                this.wizard.getNavigation().moveTo(this.owner, 1.15D);
            }
        }
    }

    /** Targets whatever last hurt the owner, or whatever the owner last struck (never another Death Eater). */
    public static final class DefendOwnerGoal extends TargetGoal {
        private final WizardEntity wizard;
        @Nullable
        private LivingEntity pick;
        private boolean primed;
        private int seenHurtBy;
        private int seenStruck;

        public DefendOwnerGoal(WizardEntity wizard) {
            super(wizard, false);
            this.wizard = wizard;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!this.wizard.isEscort()) {
                return false;
            }
            ServerPlayer owner = this.wizard.getEscortOwner();
            if (owner == null) {
                return false;
            }
            if (!this.primed) {
                // What the owner fought before the escort came is not its business.
                this.primed = true;
                this.seenHurtBy = owner.getLastHurtByMobTimestamp();
                this.seenStruck = owner.getLastHurtMobTimestamp();
                return false;
            }
            LivingEntity attacker = owner.getLastHurtByMob();
            if (attacker != null && attacker != this.wizard && owner.getLastHurtByMobTimestamp() != this.seenHurtBy
                    && this.canAttack(attacker, TargetingConditions.DEFAULT)) {
                this.pick = attacker;
                return true;
            }
            LivingEntity struck = owner.getLastHurtMob();
            if (struck != null && struck != owner && owner.getLastHurtMobTimestamp() != this.seenStruck
                    && this.canAttack(struck, TargetingConditions.DEFAULT)) {
                this.pick = struck;
                return true;
            }
            return false;
        }

        @Override
        public void start() {
            ServerPlayer owner = this.wizard.getEscortOwner();
            if (owner != null) {
                this.seenHurtBy = owner.getLastHurtByMobTimestamp();
                this.seenStruck = owner.getLastHurtMobTimestamp();
            }
            this.mob.setTarget(this.pick);
            super.start();
        }

        @Override
        public boolean canContinueToUse() {
            return this.wizard.isEscort() && super.canContinueToUse(); // released or dismissed: the goal ends
        }
    }
}
