package com.hogwartsmagic.client;

import com.hogwartsmagic.spell.dark.DarkRules;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Client only: the player's own Darkness as a thin "soul" meter just above the mana bar. Five bands (Clean to Lost)
 * in the tier colours, dividers at each tier bound, a fill that slides to the value the server last sent, a marker
 * at the current point and the tier name to the left. Shown while a wand is held (like the mana bar), and for a few
 * seconds after the value changes even without one, flashing as it moves so a darkening never goes unseen.
 */
public final class SoulMeterOverlay {
    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = 4;
    /** How long the meter stays up after a change when no wand is held, and the fade at its end. */
    private static final long SHOW_AFTER_CHANGE_MS = 6000L;
    private static final long FADE_MS = 1000L;
    private static final long FLASH_MS = 900L;

    private static float shown = Float.NaN;
    private static float lastValue = Float.NaN;
    private static long changedAt = Long.MIN_VALUE / 2;
    private static long lastFrame;

    private SoulMeterOverlay() {
    }

    public static void render(GuiGraphics g, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator() || mc.options.hideGui) return;
        if (com.hogwartsmagic.client.pensieve.PensieveClientState.hidesHud()) return;

        long now = Util.getMillis();
        float value = DarkMagicClientState.darkness();
        if (Float.isNaN(lastValue)) {
            // First frame of the session: start where we are, no slide and no flash for the login sync of 0.
            lastValue = value;
            shown = value;
            if (value > DarkRules.MIN_DARKNESS) {
                changedAt = now;
            }
        } else if (value != lastValue) {
            lastValue = value;
            changedAt = now;
        }

        // Slide the drawn fill toward the real value (about a quarter-second ease, frame-rate independent).
        float dt = Math.min(0.25f, Math.max(0.0f, (now - lastFrame) / 1000.0f));
        lastFrame = now;
        shown += (value - shown) * (1.0f - (float) Math.exp(-dt * 10.0f));
        if (Math.abs(value - shown) < 0.01f) {
            shown = value;
        }

        boolean holdingWand = com.hogwartsmagic.item.MagicWandItem.isAnyWand(mc.player.getMainHandItem())
                || com.hogwartsmagic.item.MagicWandItem.isAnyWand(mc.player.getOffhandItem());
        long sinceChange = now - changedAt;
        float alpha;
        if (holdingWand) {
            alpha = 1.0f;
        } else if (sinceChange < SHOW_AFTER_CHANGE_MS) {
            long left = SHOW_AFTER_CHANGE_MS - sinceChange;
            alpha = left >= FADE_MS ? 1.0f : left / (float) FADE_MS;
        } else {
            return;
        }
        if (alpha <= 0.02f) return;

        // Same column as the mana bar (x = width - 120), one row above its "mana / max" text.
        int x = width - 120;
        int y = height - 48;
        int tier = DarkRules.tier(value);

        // Frame and the five tier bands, dim.
        g.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, withAlpha(0xCC000000, alpha));
        for (int t = DarkRules.CLEAN; t <= DarkRules.LOST; t++) {
            int bx0 = x + px(DarkRules.tierLowerBound(t));
            int bx1 = t == DarkRules.LOST ? x + BAR_WIDTH : x + px(DarkRules.tierLowerBound(t + 1));
            g.fill(bx0, y, bx1, y + BAR_HEIGHT, withAlpha(scaleRgb(DarkRules.tierColor(t), 0.30f) | 0xFF000000,
                    alpha * 0.9f));
        }

        // The fill: each band it covers in its own tier colour, so the bar visibly darkens as it grows.
        int fillPx = px(shown);
        for (int t = DarkRules.CLEAN; t <= DarkRules.LOST; t++) {
            int bx0 = x + px(DarkRules.tierLowerBound(t));
            int bx1 = t == DarkRules.LOST ? x + BAR_WIDTH : x + px(DarkRules.tierLowerBound(t + 1));
            int end = Math.min(bx1, x + fillPx);
            if (end > bx0) {
                g.fill(bx0, y, end, y + BAR_HEIGHT, withAlpha(DarkRules.tierColor(t), alpha));
            }
        }

        // A white flash over the fill right after a change.
        if (sinceChange < FLASH_MS && fillPx > 0) {
            float flash = 1.0f - sinceChange / (float) FLASH_MS;
            g.fill(x, y, x + fillPx, y + BAR_HEIGHT, withAlpha(0xFFFFFFFF, alpha * flash * 0.6f));
        }

        // Dividers at 20 / 40 / 60 / 80.
        for (int t = DarkRules.SHADOWED; t <= DarkRules.LOST; t++) {
            int dx = x + px(DarkRules.tierLowerBound(t));
            g.fill(dx, y, dx + 1, y + BAR_HEIGHT, withAlpha(0xFF000000, alpha * 0.85f));
        }

        // The marker: where the soul stands now.
        int mx = Math.max(x, Math.min(x + BAR_WIDTH - 1, x + fillPx));
        g.fill(mx - 1, y - 2, mx + 2, y + BAR_HEIGHT + 2, withAlpha(0xFF000000, alpha));
        g.fill(mx, y - 1, mx + 1, y + BAR_HEIGHT + 1, withAlpha(0xFFFFFFFF, alpha));

        // "Soul: <tier>" above the bar, the tier in its colour (Lost's green is lifted a little to stay legible).
        // Above, not beside: left of the bar is where the hunger and air rows sit on narrow screens.
        Font font = mc.font;
        int color = tier == DarkRules.LOST ? 0x4FC25E : DarkRules.tierColor(tier) & 0xFFFFFF;
        Component name = Component.translatable("gui.hogwartsmagic.dark.hud",
                Component.translatable(DarkRules.tierKey(tier)).withStyle(s -> s.withColor(color)));
        g.drawString(font, name, x, y - 12, withAlpha(0xFFAAAAAA, alpha), true);
    }

    private static int px(float darkness) {
        return Math.round(DarkRules.sanitize(darkness) / DarkRules.MAX_DARKNESS * BAR_WIDTH);
    }

    private static int withAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0f, Math.min(1.0f, alpha)));
        return (Math.max(a, 4) << 24) | (argb & 0xFFFFFF);
    }

    private static int scaleRgb(int argb, float k) {
        int r = Math.round(((argb >> 16) & 0xFF) * k);
        int gr = Math.round(((argb >> 8) & 0xFF) * k);
        int b = Math.round((argb & 0xFF) * k);
        return (r << 16) | (gr << 8) | b;
    }
}
