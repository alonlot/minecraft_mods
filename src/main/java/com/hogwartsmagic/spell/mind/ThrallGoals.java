package com.hogwartsmagic.spell.mind;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Tame-dog goals that work on any Mob, not only TamableAnimal (vanilla's FollowOwnerGoal and the owner
 * target goals need a TamableAnimal). ImperioThralls adds them on enthrall and removes them on release.
 */
final class ThrallGoals {
    private ThrallGoals() {
    }

    /** Walk after the owner like a wolf; beyond 12 blocks, teleport beside them. Only while not fighting. */
    static final class Follow extends Goal {
        private final Mob mob;
        private final UUID ownerId;
        @Nullable
        private LivingEntity owner;
        private int recalc;

        Follow(Mob mob, UUID ownerId) {
            this.mob = mob;
            this.ownerId = ownerId;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.mob.getTarget() != null || ImperioThralls.pinned(this.mob)) {
                return false;
            }
            LivingEntity found = ImperioThralls.ownerOf(this.mob, this.ownerId);
            double start = ImperioRules.FOLLOW_START;
            if (found == null || this.mob.distanceToSqr(found) < start * start) {
                return false;
            }
            this.owner = found;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            double stop = ImperioRules.FOLLOW_STOP;
            return this.owner != null && this.owner.isAlive() && this.mob.getTarget() == null && !ImperioThralls.pinned(this.mob)
                    && this.mob.distanceToSqr(this.owner) > stop * stop;
        }

        @Override
        public void start() {
            this.recalc = 0;
        }

        @Override
        public void stop() {
            this.owner = null;
            this.mob.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.owner == null) {
                return;
            }
            this.mob.getLookControl().setLookAt(this.owner, 10.0F, (float) this.mob.getMaxHeadXRot());
            if (--this.recalc > 0) {
                return;
            }
            this.recalc = this.adjustedTickDelay(10);
            double teleport = ImperioRules.TELEPORT_DISTANCE;
            if (this.mob.distanceToSqr(this.owner) > teleport * teleport) {
                ImperioThralls.teleportNear(this.mob, this.owner);
            } else {
                this.mob.getNavigation().moveTo(this.owner, 1.2);
            }
        }
    }

    /** Take up the owner's fight: whoever hurt the owner, else whatever the owner hurt (last 5s). */
    static final class Assist extends Goal {
        private final Mob mob;
        private final UUID ownerId;
        @Nullable
        private LivingEntity pick;

        Assist(Mob mob, UUID ownerId) {
            this.mob = mob;
            this.ownerId = ownerId;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = ImperioThralls.ownerOf(this.mob, this.ownerId);
            if (owner == null) {
                return false;
            }
            LivingEntity candidate = ImperioThralls.pickTarget(owner, this.mob);
            if (candidate == null || candidate == this.mob.getTarget()) {
                return false;
            }
            this.pick = candidate;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            MindControl.setTargetBypassing(this.mob, this.pick);
            this.pick = null;
        }
    }

    /** A creature with no attack of its own (a villager, a cow) still throws a 2-damage punch for its owner. */
    static final class Brawl extends Goal {
        private final PathfinderMob mob;
        private int cooldown;
        private int recalc;

        Brawl(PathfinderMob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.mob.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void start() {
            this.cooldown = 0;
            this.recalc = 0;
        }

        @Override
        public void stop() {
            this.mob.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = this.mob.getTarget();
            if (target == null) {
                return;
            }
            this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (this.mob.isWithinMeleeAttackRange(target)) {
                this.mob.getNavigation().stop();
                if (--this.cooldown <= 0) {
                    this.cooldown = ImperioRules.BRAWL_INTERVAL;
                    this.mob.swing(InteractionHand.MAIN_HAND);
                    target.hurt(this.mob.damageSources().mobAttack(this.mob), ImperioRules.BRAWL_DAMAGE);
                }
            } else if (--this.recalc <= 0) {
                this.recalc = 10;
                this.mob.getNavigation().moveTo(target, 1.2);
            }
        }
    }
}
