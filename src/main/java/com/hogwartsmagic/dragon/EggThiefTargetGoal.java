package com.hogwartsmagic.dragon;

import java.util.EnumSet;
import java.util.UUID;

import javax.annotation.Nullable;

import com.hogwartsmagic.block.DragonNestEggBlock;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * The dragon's grudge against whoever took an egg: for as long as it lasts, and while the thief is
 * within reach, the dragon keeps them as its target ahead of anything else. Carrying the egg
 * renews the grudge; dying clears it; a thief who has logged out or left the dimension is simply
 * not found, and the grudge waits out its time.
 */
public final class EggThiefTargetGoal extends Goal {
    /** How often, in ticks, the grudge is renewed while the thief still carries an egg. */
    private static final int RENEW_INTERVAL = 20;

    private final HungarianHorntailEntity dragon;

    public EggThiefTargetGoal(HungarianHorntailEntity dragon) {
        this.dragon = dragon;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    /** The thief as a live player on this dragon's own level, or null (gone, other dimension, dead, creative). */
    @Nullable
    public static ServerPlayer resolveThief(HungarianHorntailEntity dragon) {
        UUID id = dragon.eggThief();
        if (id == null || !(dragon.level() instanceof ServerLevel level)) {
            return null;
        }
        if (!(level.getPlayerByUUID(id) instanceof ServerPlayer player) || !player.isAlive()) {
            return null;
        }
        return DragonEggRules.raisesGrudge(player.isCreative(), player.isSpectator()) ? player : null;
    }

    @Override
    public boolean canUse() {
        ServerPlayer thief = resolveThief(this.dragon);
        return thief != null && DragonEggRules.pursues(this.dragon.level().getGameTime(),
                this.dragon.eggGrudgeUntil(), this.dragon.distanceToSqr(thief));
    }

    @Override
    public boolean canContinueToUse() {
        if (this.dragon.eggThief() != null && this.dragon.level() instanceof ServerLevel level
                && level.getPlayerByUUID(this.dragon.eggThief()) instanceof ServerPlayer dead
                && dead.isDeadOrDying()) {
            this.dragon.forgiveEggThief();
            return false;
        }
        return canUse();
    }

    @Override
    public void start() {
        aim();
    }

    @Override
    public void tick() {
        aim();
        ServerPlayer thief = resolveThief(this.dragon);
        if (thief != null && this.dragon.tickCount % RENEW_INTERVAL == 0) {
            this.dragon.renewEggGrudge(DragonEggRules.renewedExpiry(
                    this.dragon.level().getGameTime(), this.dragon.eggGrudgeUntil(),
                    thief.getInventory().contains(DragonNestEggBlock.DRAGON_EGG_ITEMS),
                    this.dragon.distanceToSqr(thief)));
        }
    }

    private void aim() {
        ServerPlayer thief = resolveThief(this.dragon);
        if (thief != null && this.dragon.getTarget() != thief) {
            this.dragon.setTarget(thief);
        }
    }
}
