package com.hogwartsmagic.entity;

import com.hogwartsmagic.spell.dark.DarkCostRules;
import com.hogwartsmagic.spell.dark.DarkCosts;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * NPC reactions to a dark player (Dark Magic Phase 2). Each goal is tied to one DarkCostRules.Cost and first checks
 * the cached darkest tier online (DarkCosts.darkestOnlineTier), so while nobody online is dark enough it searches
 * nothing. Creative and spectator players are never chosen (vanilla combat targeting).
 */
public final class DarkPlayerGoals {

    private DarkPlayerGoals() {
    }

    /**
     * Targets the nearest player in follow range, with line of sight, whose tier carries the cost. Give it a lower
     * priority number than the mob's plain player goal and it takes over from a target already chosen.
     */
    public static class TargetDarkPlayer extends NearestAttackableTargetGoal<Player> {
        private final DarkCostRules.Cost cost;

        public TargetDarkPlayer(Mob mob, DarkCostRules.Cost cost) {
            super(mob, Player.class, 10, true, false, target -> {
                double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
                // Distance first: the tier is read only for players the mob could choose.
                return mob.distanceToSqr(target) <= range * range
                        && !com.hogwartsmagic.petrification.Petrification.isPetrified(target)
                        && cost.appliesAt(DarkCosts.tierOf(target));
            });
            this.cost = cost;
        }

        @Override
        public boolean canUse() {
            return DarkCosts.anyoneOnlineAt(this.mob.level(), this.cost) && super.canUse();
        }

        /**
         * Re-checks the tier every tick: once the player leaves it (or Dark Magic is turned off) the goal fails and
         * the inherited stop() clears the target, so the mob drops the player at once.
         */
        @Override
        public boolean canContinueToUse() {
            LivingEntity t = this.mob.getTarget();
            return t != null && DarkCosts.anyoneOnlineAt(this.mob.level(), this.cost)
                    && this.cost.appliesAt(DarkCosts.tierOf(t)) && super.canContinueToUse();
        }
    }

    /** Keeps away from a player whose tier carries the cost. Searches once per NPC_CHECK_EVERY_CALLS evaluations. */
    public static class AvoidDarkPlayer extends AvoidEntityGoal<Player> {
        private final DarkCostRules.Cost cost;
        private int checkCountdown;

        public AvoidDarkPlayer(PathfinderMob mob, DarkCostRules.Cost cost, float distance, double walkSpeed,
                double sprintSpeed) {
            super(mob, Player.class, distance, walkSpeed, sprintSpeed,
                    target -> cost.appliesAt(DarkCosts.tierOf(target)));
            this.cost = cost;
        }

        @Override
        public boolean canUse() {
            if (--this.checkCountdown > 0) {
                return false;
            }
            this.checkCountdown = DarkCostRules.NPC_CHECK_EVERY_CALLS;
            return DarkCosts.anyoneOnlineAt(this.mob.level(), this.cost) && super.canUse();
        }
    }
}
