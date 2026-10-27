package com.hogwartsmagic.client;

import com.hogwartsmagic.entity.BroomEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** HUD bar shown while riding a broom: stamina (boost fuel). */
public class BroomHudOverlay {
    public static void render(GuiGraphics guiGraphics, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator()) return;
        // A memory plays as a film: no HUD (the Pensieve subtitles still go through the chat overlay event).
        if (com.hogwartsmagic.client.pensieve.PensieveClientState.hidesHud()) return;
        if (!(mc.player.getVehicle() instanceof BroomEntity broom)) return;

        int stamina = broom.getStaminaPercent();
        boolean locked = !broom.canBoost();

        // Position the stamina bar on the left, mirroring the right-side mana bar layout.
        int barWidth = 100;
        int barHeight = 8;
        int x = 20;
        int y = height - 30;

        guiGraphics.fill(x, y, x + barWidth, y + barHeight, 0x88000000);
        int fillWidth = Math.max(0, Math.min(barWidth, (stamina * barWidth) / BroomEntity.STAMINA_MAX));
        // Yellow when high, orange when low, red when locked.
        int color;
        if (locked) color = 0xFFFF4040;
        else if (stamina < 35) color = 0xFFFFAA40;
        else color = 0xFFFFD050;
        guiGraphics.fill(x, y, x + fillWidth, y + barHeight, color);

        String label = locked ? "OVERHEAT" : "BOOST " + stamina + "%";
        guiGraphics.drawString(mc.font, label, x, y - 10, 0xFFFFFF, true);
    }
}
