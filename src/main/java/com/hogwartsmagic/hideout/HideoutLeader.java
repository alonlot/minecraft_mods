package com.hogwartsmagic.hideout;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.WizardEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumSet;

/**
 * The named dark wizard who guards a hideout's ritual room. The template bakes in a plain wizard with
 * a tag; when he joins the world he is toughened once (80 health, armour, a name like "Corvin
 * Mortlake") and from then on stays within a few blocks of the altar: he will cast at anyone in the
 * room but never chases a player up the stair.
 *
 * <p>Server thread only: the join event is ignored on the client.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class HideoutLeader {
    private HideoutLeader() {
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof WizardEntity wizard && wizard.getTags().contains(HideoutRules.LEADER_TAG)) {
            prepare(wizard);
        }
    }

    /**
     * Arms the leader the first time (a persistent flag stops a chunk reload healing him to full), and every
     * time re-applies the leash and makes sure there is exactly one guard goal.
     */
    public static void prepare(WizardEntity wizard) {
        CompoundTag data = wizard.getPersistentData();
        if (!data.contains(HideoutRules.ARMED_KEY)) {
            wizard.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HideoutRules.LEADER_MAX_HEALTH);
            wizard.getAttribute(Attributes.ARMOR).setBaseValue(HideoutRules.LEADER_ARMOR);
            wizard.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(HideoutRules.LEADER_KNOCKBACK_RESISTANCE);
            wizard.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(HideoutRules.LEADER_FOLLOW_RANGE);
            wizard.setHealth(wizard.getMaxHealth());
            long home = wizard.blockPosition().asLong();
            data.putLong(HideoutRules.HOME_KEY, home);
            wizard.setCustomName(Component.literal(HideoutRules.leaderName(home)));
            wizard.setCustomNameVisible(false);
            wizard.setRaidLeader(false);
            if (wizard.getItemBySlot(EquipmentSlot.HEAD).is(Items.WHITE_BANNER)) {
                wizard.setItemSlot(EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
            }
            wizard.setPersistenceRequired();
            data.putBoolean(HideoutRules.ARMED_KEY, true);
        }
        BlockPos home = BlockPos.of(data.getLong(HideoutRules.HOME_KEY));
        wizard.restrictTo(home, (int) HideoutRules.LEASH_RADIUS);
        boolean hasGoal = wizard.goalSelector.getAvailableGoals().stream()
                .anyMatch(g -> g.getGoal() instanceof GuardAltarGoal);
        if (!hasGoal) {
            wizard.goalSelector.addGoal(1, new GuardAltarGoal(wizard));
        }
    }

    /** Walks the leader home whenever he has strayed beyond the leash (priority 1, over his spell casting at 2). */
    public static final class GuardAltarGoal extends Goal {
        private final WizardEntity wizard;
        private int repath;

        public GuardAltarGoal(WizardEntity wizard) {
            this.wizard = wizard;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        private Vec3 home() {
            return Vec3.atBottomCenterOf(BlockPos.of(wizard.getPersistentData().getLong(HideoutRules.HOME_KEY)));
        }

        private double distSq() {
            Vec3 h = home();
            return wizard.distanceToSqr(h.x, h.y, h.z);
        }

        @Override
        public boolean canUse() {
            return wizard.getPersistentData().contains(HideoutRules.HOME_KEY)
                    && HideoutRules.shouldHeadHome(distSq(), false);
        }

        @Override
        public boolean canContinueToUse() {
            return HideoutRules.shouldHeadHome(distSq(), true);
        }

        @Override
        public void start() {
            repath = 0;
            go();
        }

        @Override
        public void tick() {
            if (++repath >= HideoutRules.LEASH_REPATH_TICKS) {
                repath = 0;
                go();
            }
        }

        private void go() {
            Vec3 h = home();
            wizard.getNavigation().moveTo(h.x, h.y, h.z, 1.15);
        }

        @Override
        public void stop() {
            wizard.getNavigation().stop();
        }
    }
}
