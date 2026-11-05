package com.hogwartsmagic.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class InvisibilityCloakClientState {
    private static final int TRANSITION_TICKS = 10;
    private static final Map<UUID, Transition> TRANSITIONS = new HashMap<>();

    private InvisibilityCloakClientState() {
    }

    public static void start(UUID playerId, boolean hiding) {
        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = minecraft.level != null ? minecraft.level.getGameTime() : 0L;
        TRANSITIONS.put(playerId, new Transition(hiding, gameTime));
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            TRANSITIONS.clear();
            return;
        }
        long gameTime = minecraft.level.getGameTime();
        Iterator<Map.Entry<UUID, Transition>> iterator = TRANSITIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Transition> entry = iterator.next();
            if (gameTime - entry.getValue().startTick > TRANSITION_TICKS + 2L) {
                iterator.remove();
            }
        }
    }

    public static boolean isAnimating(Player player) {
        return player != null && TRANSITIONS.containsKey(player.getUUID());
    }

    public static boolean shouldHidePlayerCompletely(Player player) {
        return player != null && player.isInvisible() && !isAnimating(player);
    }

    public static VisibilityMask getVisibilityMask(Player player, float partialTick) {
        if (player == null) {
            return VisibilityMask.allVisible();
        }
        Transition transition = TRANSITIONS.get(player.getUUID());
        if (transition == null) {
            return VisibilityMask.allVisible();
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return VisibilityMask.allVisible();
        }

        float elapsed = (minecraft.level.getGameTime() - transition.startTick) + partialTick;
        float progress = Math.max(0.0f, Math.min(1.0f, elapsed / (float) TRANSITION_TICKS));
        return transition.hiding ? buildHideMask(progress) : buildShowMask(progress);
    }

    private static VisibilityMask buildHideMask(float progress) {
        if (progress < 0.33f) {
            return new VisibilityMask(false, true, true);
        }
        if (progress < 0.66f) {
            return new VisibilityMask(false, false, true);
        }
        return new VisibilityMask(false, false, false);
    }

    private static VisibilityMask buildShowMask(float progress) {
        if (progress < 0.33f) {
            return new VisibilityMask(true, false, false);
        }
        if (progress < 0.66f) {
            return new VisibilityMask(true, true, false);
        }
        return VisibilityMask.allVisible();
    }

    public static void applyToModel(net.minecraft.client.model.PlayerModel<AbstractClientPlayer> model, VisibilityMask mask) {
        model.head.visible = mask.head();
        model.hat.visible = mask.head();
        model.body.visible = mask.torso();
        model.jacket.visible = mask.torso();
        model.leftArm.visible = mask.torso();
        model.rightArm.visible = mask.torso();
        model.leftSleeve.visible = mask.torso();
        model.rightSleeve.visible = mask.torso();
        model.leftLeg.visible = mask.legs();
        model.rightLeg.visible = mask.legs();
        model.leftPants.visible = mask.legs();
        model.rightPants.visible = mask.legs();
    }

    public static void applyToHumanoidModel(net.minecraft.client.model.HumanoidModel<?> model, VisibilityMask mask) {
        model.head.visible = mask.head();
        model.hat.visible = mask.head();
        model.body.visible = mask.torso();
        model.rightArm.visible = mask.torso();
        model.leftArm.visible = mask.torso();
        model.rightLeg.visible = mask.legs();
        model.leftLeg.visible = mask.legs();
    }

    public static void applyToHumanoidArmorSlot(net.minecraft.client.model.HumanoidModel<?> model,
                                                net.minecraft.world.entity.EquipmentSlot slot,
                                                VisibilityMask mask) {
        // if-chain, not an enum switch: the synthetic switch-map class fails to load in client code here.
        if (slot == net.minecraft.world.entity.EquipmentSlot.HEAD) {
            model.head.visible = model.head.visible && mask.head();
            model.hat.visible = model.hat.visible && mask.head();
        } else if (slot == net.minecraft.world.entity.EquipmentSlot.CHEST) {
            model.body.visible = model.body.visible && mask.torso();
            model.rightArm.visible = model.rightArm.visible && mask.torso();
            model.leftArm.visible = model.leftArm.visible && mask.torso();
        } else if (slot == net.minecraft.world.entity.EquipmentSlot.LEGS) {
            model.body.visible = model.body.visible && mask.torso();
            model.rightLeg.visible = model.rightLeg.visible && mask.legs();
            model.leftLeg.visible = model.leftLeg.visible && mask.legs();
        } else if (slot == net.minecraft.world.entity.EquipmentSlot.FEET) {
            model.rightLeg.visible = model.rightLeg.visible && mask.legs();
            model.leftLeg.visible = model.leftLeg.visible && mask.legs();
        }
    }

    public static void resetModel(net.minecraft.client.model.PlayerModel<AbstractClientPlayer> model) {
        applyToModel(model, VisibilityMask.allVisible());
    }

    private record Transition(boolean hiding, long startTick) {
    }

    public record VisibilityMask(boolean head, boolean torso, boolean legs) {
        public static VisibilityMask allVisible() {
            return new VisibilityMask(true, true, true);
        }
    }
}
