package com.hogwartsmagic.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ScreenEvent;

public final class HorcruxClientState {
    private static final int FALLBACK_FLIGHT_TICKS = 140;
    private static int activeSoulId = -1;
    private static boolean respawnUnlocked = true;
    private static int missingSoulTicks = 0;
    private static int fallbackFlightTick = 0;
    private static Vec3 fallbackStartPos = Vec3.ZERO;
    private static Vec3 fallbackTargetPos = Vec3.ZERO;
    private static HorcruxCameraAnchor fallbackAnchor;

    private HorcruxClientState() {
    }

    public static void start(int soulEntityId, Vec3 startPos, BlockPos targetPos) {
        activeSoulId = soulEntityId;
        respawnUnlocked = false;
        missingSoulTicks = 0;
        fallbackFlightTick = 0;
        fallbackStartPos = startPos == null ? Vec3.ZERO : startPos;
        fallbackTargetPos = Vec3.atCenterOf(targetPos).add(0.0D, 0.6D, 0.0D);
        ensureFallbackAnchor(Minecraft.getInstance());
    }

    public static void finish() {
        respawnUnlocked = true;
        missingSoulTicks = 0;
    }

    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
        activeSoulId = -1;
        respawnUnlocked = true;
        missingSoulTicks = 0;
        fallbackFlightTick = 0;
        fallbackAnchor = null;
    }

    public static void tick(Minecraft mc) {
        if (activeSoulId == -1) {
            syncDeathScreenButtons(mc);
            return;
        }
        if (mc.level == null) {
            clear();
            return;
        }

        Entity soul = mc.level.getEntity(activeSoulId);
        if (soul != null) {
            missingSoulTicks = 0;
            fallbackStartPos = soul.position();
            fallbackFlightTick = 0;
            if (fallbackAnchor != null) {
                fallbackAnchor.setPos(fallbackStartPos.x, fallbackStartPos.y, fallbackStartPos.z);
            }
            syncCameraToSoul(mc, soul);
            syncDeathScreenButtons(mc);
            return;
        }

        missingSoulTicks++;
        if (!respawnUnlocked) {
            syncCameraToSoul(mc, getFallbackAnchor(mc));
        } else if (mc.player != null) {
            mc.setCameraEntity(mc.player);
            activeSoulId = -1;
        }
        syncDeathScreenButtons(mc);
    }

    public static boolean isRespawnUnlocked() {
        return respawnUnlocked;
    }

    public static boolean isDeathSequenceActive() {
        return !respawnUnlocked;
    }

    public static void applyButtonLock(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof DeathScreen)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Entity soul = getActiveSoul(mc);
        if (soul != null) {
            syncCameraToSoul(mc, soul);
        } else if (!respawnUnlocked) {
            syncCameraToSoul(mc, getFallbackAnchor(mc));
        }
        boolean active = respawnUnlocked;
        for (var child : event.getScreen().children()) {
            if (child instanceof AbstractWidget widget) {
                widget.active = active;
            }
        }
    }

    public static void renderOverlay(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof DeathScreen) || respawnUnlocked) {
            return;
        }
        GuiGraphics guiGraphics = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        Entity soul = getActiveSoul(mc);
        if (soul != null) {
            syncCameraToSoul(mc, soul);
        } else {
            syncCameraToSoul(mc, getFallbackAnchor(mc));
        }
        Component line1 = Component.literal("Your soul is flying to the Horcrux...");
        Component line2 = Component.literal("Respawn will return when the vessel is reached.");
        int centerX = event.getScreen().width / 2;
        int y = Math.min(event.getScreen().height - 68, event.getScreen().height / 2 + 38);
        guiGraphics.drawCenteredString(mc.font, line1, centerX, y, 0x8A2BE2);
        guiGraphics.drawCenteredString(mc.font, line2, centerX, y + 12, 0xC8B6D8);
    }

    private static void syncDeathScreenButtons(Minecraft mc) {
        if (!(mc.screen instanceof DeathScreen)) {
            return;
        }
        boolean active = respawnUnlocked;
        for (var child : mc.screen.children()) {
            if (child instanceof AbstractWidget widget) {
                widget.active = active;
            }
        }
    }

    private static Entity getActiveSoul(Minecraft mc) {
        if (activeSoulId == -1 || mc.level == null) {
            return null;
        }
        return mc.level.getEntity(activeSoulId);
    }

    private static void syncCameraToSoul(Minecraft mc, Entity soul) {
        if (soul != null && mc.screen instanceof DeathScreen && mc.getCameraEntity() != soul) {
            mc.setCameraEntity(soul);
        }
    }

    private static Entity getFallbackAnchor(Minecraft mc) {
        if (respawnUnlocked || mc.level == null) {
            return null;
        }
        ensureFallbackAnchor(mc);
        if (fallbackAnchor == null) {
            return null;
        }
        double progress = Math.min(1.0D, fallbackFlightTick / (double) FALLBACK_FLIGHT_TICKS);
        Vec3 base = fallbackStartPos.lerp(fallbackTargetPos, progress);
        double arc = Math.sin(progress * Math.PI) * 1.2D;
        fallbackAnchor.setPos(base.x, base.y + arc, base.z);
        if (fallbackFlightTick < FALLBACK_FLIGHT_TICKS) {
            fallbackFlightTick++;
        } else {
            finish();
        }
        return fallbackAnchor;
    }

    private static void ensureFallbackAnchor(Minecraft mc) {
        if (mc.level == null || fallbackAnchor != null) {
            return;
        }
        fallbackAnchor = new HorcruxCameraAnchor(mc.level);
        fallbackAnchor.setPos(fallbackStartPos.x, fallbackStartPos.y, fallbackStartPos.z);
    }
}

final class HorcruxCameraAnchor extends Entity {
    HorcruxCameraAnchor(Level level) {
        super(EntityType.MARKER, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(0.0F, 0.0F);
    }
}
