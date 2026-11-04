package com.hogwartsmagic.entity;

import com.hogwartsmagic.item.MagicWandItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;

/**
 * Top-priority goal for a {@link DisarmableWandUser}: when the entity has been disarmed by
 * Expelliarmus, scan the surrounding area for any wand-class item lying on the ground, navigate
 * to the closest one, and pick it up — restoring the entity to combat.
 *
 * <p>The goal ignores its normal idle/attack work while running: an Expelliarmus victim has one
 * objective and one objective only. If no wand can be found in range, the goal yields and the
 * entity stays disarmed (and unable to cast) — by design, a disarmed wizard is finished until
 * they recover a wand.</p>
 */
public class RetrieveDroppedWandGoal extends Goal {
    private static final double SEARCH_RADIUS = 16.0D;
    private static final double SEARCH_RADIUS_SQR = SEARCH_RADIUS * SEARCH_RADIUS;
    private static final double PICKUP_RADIUS_SQR = 1.6D * 1.6D;
    private static final int RESCAN_INTERVAL_TICKS = 20;

    private final DisarmableWandUser user;
    private final Mob mob;
    private final double speed;
    private ItemEntity target;
    private int rescanCooldown;

    public RetrieveDroppedWandGoal(DisarmableWandUser user, double speed) {
        this.user = user;
        this.mob = user.asMob();
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!user.isDisarmed()) return false;
        ItemEntity nearest = findNearestWand();
        if (nearest == null) return false;
        this.target = nearest;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!user.isDisarmed()) return false;
        if (target == null || !target.isAlive() || target.isRemoved()) return false;
        if (mob.distanceToSqr(target) > SEARCH_RADIUS_SQR * 4.0D) return false;
        return true;
    }

    @Override
    public void start() {
        rescanCooldown = 0;
        if (target != null) {
            mob.getNavigation().moveTo(target, speed);
        }
    }

    @Override
    public void stop() {
        target = null;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (target == null || !target.isAlive()) {
            target = findNearestWand();
            if (target == null) return;
        }

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (mob.distanceToSqr(target) <= PICKUP_RADIUS_SQR) {
            pickup();
            return;
        }

        // Re-issue the path occasionally to track the item if something nudged it (water flow, etc).
        if (--rescanCooldown <= 0) {
            rescanCooldown = RESCAN_INTERVAL_TICKS;
            if (mob.getNavigation().isDone()) {
                mob.getNavigation().moveTo(target, speed);
            }
        }
    }

    private void pickup() {
        if (target == null) return;
        ItemStack stack = target.getItem().copy();
        if (stack.isEmpty() || !MagicWandItem.isAnyWand(stack)) {
            target = null;
            return;
        }

        // Equip in main hand; preserve any selected-spell NBT from the dropped stack.
        mob.setItemInHand(InteractionHand.MAIN_HAND, stack);
        // Any wand on the ground qualifies, including one a player was just disarmed of. A drop
        // chance of 0 here destroyed that wand for good when the NPC died; a guaranteed drop gives
        // back exactly the item that was picked up and creates nothing new.
        mob.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        target.discard();
        user.setDisarmed(false);
        target = null;
    }

    private ItemEntity findNearestWand() {
        AABB area = mob.getBoundingBox().inflate(SEARCH_RADIUS);
        List<ItemEntity> items = mob.level().getEntitiesOfClass(
                ItemEntity.class, area,
                e -> e.isAlive() && !e.isRemoved() && !e.hasPickUpDelay() && MagicWandItem.isAnyWand(e.getItem()));
        if (items.isEmpty()) return null;
        ItemEntity nearest = null;
        double bestSq = Double.POSITIVE_INFINITY;
        for (ItemEntity item : items) {
            double d = mob.distanceToSqr(item);
            if (d < bestSq) {
                bestSq = d;
                nearest = item;
            }
        }
        return nearest;
    }
}
