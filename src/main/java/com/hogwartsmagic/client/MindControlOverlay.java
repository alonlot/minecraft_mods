package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.mind.MindControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The green Imperius vignette over a controlled player's HUD. Its whole state is the imperius effect
 * vanilla syncs to the local player, so there is nothing to desync. Drawn by MindControlGuiMixin.
 */
public final class MindControlOverlay {
    private static final int STRIPS = 20;
    private static final int IMPERIUS_GREEN = 0x2FAF3F;

    private MindControlOverlay() {
    }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        if (MindControl.isControlled(player)) {
            float pulse = 0.85F + 0.15F * Mth.sin(player.tickCount * 0.2F);
            vignette(graphics, IMPERIUS_GREEN, (int) (170 * pulse));
            graphics.drawCenteredString(minecraft.font, Component.translatable("gui." + ExampleMod.MODID + ".imperio.victim"),
                    graphics.guiWidth() / 2, 14, 0xFF9CFF9C);
        }
    }

    /** A soft edge faked with translucent strips down all four sides, strongest at the rim. */
    static void vignette(GuiGraphics graphics, int rgb, int peak) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int stripW = Math.max(1, (width / 5) / STRIPS);
        int stripH = Math.max(1, (height / 5) / STRIPS);
        for (int i = 0; i < STRIPS; i++) {
            float t = 1.0F - i / (float) STRIPS;
            int alpha = (int) (peak * t * t);
            if (alpha <= 2) {
                continue;
            }
            int colour = (alpha << 24) | rgb;
            int x = i * stripW;
            graphics.fill(x, 0, x + stripW, height, colour);
            graphics.fill(width - x - stripW, 0, width - x, height, colour);
            int y = i * stripH;
            graphics.fill(0, y, width, y + stripH, colour);
            graphics.fill(0, height - y - stripH, width, height - y, colour);
        }
    }
}
