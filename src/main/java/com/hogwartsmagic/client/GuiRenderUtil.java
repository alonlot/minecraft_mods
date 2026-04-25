package com.hogwartsmagic.client;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class GuiRenderUtil {
    private GuiRenderUtil() {}

    /** Draw string scaled from top-left (x, y). */
    public static void drawScaledString(GuiGraphics g, Font font, Component text, float x, float y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    /** Horizontally center scaled text above a point (used for slot column centers). */
    public static void drawScaledStringCentered(GuiGraphics g, Font font, Component text, float centerX, float y, int color, float scale) {
        float halfWidth = font.width(text) * scale * 0.5f;
        g.pose().pushPose();
        g.pose().translate(centerX - halfWidth, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    /**
     * Word-wrapped block at the given scale. {@code maxPixelWidth} is the on-screen width to fit.
     *
     * @return total height drawn in screen pixels
     */
    public static float drawScaledMultiline(GuiGraphics g, Font font, Component text, float x, float y, int color, float scale, int maxPixelWidth) {
        int splitAt = Math.max(8, (int) Math.ceil(maxPixelWidth / scale));
        float lineH = font.lineHeight * scale;
        float used = 0;
        List<FormattedCharSequence> lines = font.split(text, splitAt);
        for (FormattedCharSequence line : lines) {
            g.pose().pushPose();
            g.pose().translate(x, y + used, 0);
            g.pose().scale(scale, scale, 1f);
            g.drawString(font, line, 0, 0, color, false);
            g.pose().popPose();
            used += lineH;
        }
        return used;
    }
}
