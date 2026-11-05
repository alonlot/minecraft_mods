package com.hogwartsmagic.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Flat-fill painter shared by the mod's container and menu screens (benches, spell menu), so they read
 * as one family: a bevelled themed panel, recessed wells with gold hairlines, vanilla-shaped slots, and
 * themed buttons. Client only.
 */
public final class ThemedGui {
    private ThemedGui() {}

    /** Panel palette, all ARGB. */
    public record Theme(int border, int fill, int bevelLight, int bevelDark,
                        int well, int wellShadow, int wellLight, int slotFill, int arrowBase,
                        int trim, int trimBright, int title, int text, int textDim) {}

    /** Midnight violet with old gold: wands, spells. */
    public static final Theme VIOLET = new Theme(
            0xFF0A0614, 0xFF2B1F4B, 0xFF4B3C7A, 0xFF150E29,
            0xFF1B1332, 0xFF0C0819, 0xFF3D3063, 0xFF120C22, 0xFF44366E,
            0xFFB8932E, 0xFFF0CF5A, 0xFFF5E6BE, 0xFFE2D6F5, 0xFF9C8FBF);

    /** Oiled oak and brass: brooms. */
    public static final Theme OAK = new Theme(
            0xFF140B04, 0xFF4A2E17, 0xFF6E4826, 0xFF26160A,
            0xFF2E1C0D, 0xFF170D05, 0xFF5A3B1E, 0xFF1E1209, 0xFF6A4A2A,
            0xFFC9A227, 0xFFF2D36B, 0xFFF5E6BE, 0xFFFFE9CC, 0xFFB89B78);

    /** Mixes {@code a} toward {@code b} on a slow sine, for "ready"/"selected" glows. */
    public static int pulse(int a, int b) {
        return lerpColor(a, b, pulse01());
    }

    public static float pulse01() {
        return 0.5f + 0.5f * Mth.sin(Util.getMillis() / 260f);
    }

    public static int lerpColor(int a, int b, float t) {
        int ra = (a >> 16) & 0xFF, ga = (a >> 8) & 0xFF, ba = a & 0xFF;
        int rb = (b >> 16) & 0xFF, gb = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ra + (rb - ra) * t);
        int gg = Math.round(ga + (gb - ga) * t);
        int bl = Math.round(ba + (bb - ba) * t);
        return 0xFF000000 | (r << 16) | (gg << 8) | bl;
    }

    /** Raised panel: dark outline, 2 px light top-left / dark bottom-right bevel, gold brackets in three corners. */
    public static void drawPanel(GuiGraphics g, int x, int y, int w, int h, Theme th) {
        g.fill(x, y, x + w, y + h, th.border());
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, th.bevelDark());
        g.fill(x + 1, y + 1, x + w - 3, y + h - 3, th.bevelLight());
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, th.fill());
        int c = th.trim();
        int len = 5;
        int in = 3;
        // The top-right corner is left clear: that is where screens put their corner button.
        g.fill(x + in, y + in, x + in + len, y + in + 1, c);
        g.fill(x + in, y + in, x + in + 1, y + in + len, c);
        g.fill(x + in, y + h - in - 1, x + in + len, y + h - in, c);
        g.fill(x + in, y + h - in - len, x + in + 1, y + h - in, c);
        g.fill(x + w - in - len, y + h - in - 1, x + w - in, y + h - in, c);
        g.fill(x + w - in - 1, y + h - in - len, x + w - in, y + h - in, c);
    }

    /** Recessed area with a gold hairline frame just outside it. */
    public static void drawWell(GuiGraphics g, int x0, int y0, int x1, int y1, Theme th) {
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, th.trim());
        g.fill(x0, y0, x1, y1, th.wellLight());
        g.fill(x0, y0, x1 - 1, y1 - 1, th.wellShadow());
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, th.well());
    }

    /** Vanilla-shaped slot frame around the 16 px item cell at (x, y): dark top-left, light bottom-right. */
    public static void drawSlot(GuiGraphics g, int x, int y, Theme th) {
        g.fill(x - 1, y - 1, x + 17, y + 17, th.wellShadow());
        g.fill(x, y, x + 17, y + 17, th.wellLight());
        g.fill(x, y, x + 16, y + 16, th.slotFill());
    }

    /** Smooth filled disc (one translucent pass, so alpha colours do not stack like scanline circles did). */
    public static void drawFilledCircle(GuiGraphics g, float cx, float cy, float radius, int color) {
        fillAnnulus(g, cx, cy, 0f, radius, 0f, (float) (Math.PI * 2.0), color);
    }

    /**
     * Ring sector between radii {@code r0..r1} and angles {@code a0..a1} (radians, 0 = +X, clockwise on
     * screen since Y points down). {@code r0 = 0} gives a pie slice / disc.
     */
    public static void fillAnnulus(GuiGraphics g, float cx, float cy, float r0, float r1, float a0, float a1, int color) {
        int segments = Math.max(6, (int) Math.ceil(Math.abs(a1 - a0) * Math.max(r1, 4f) / 3f));
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        for (int i = 0; i < segments; i++) {
            float ta = a0 + (a1 - a0) * i / segments;
            float tb = a0 + (a1 - a0) * (i + 1) / segments;
            float ca = Mth.cos(ta), sa = Mth.sin(ta), cb = Mth.cos(tb), sb = Mth.sin(tb);
            emitQuad(vc, m,
                    cx + ca * r0, cy + sa * r0,
                    cx + ca * r1, cy + sa * r1,
                    cx + cb * r1, cy + sb * r1,
                    cx + cb * r0, cy + sb * r0, color);
        }
        g.flush();
    }

    /** Arbitrary convex quad (repeat a corner for a triangle). */
    public static void fillQuad(GuiGraphics g, float x1, float y1, float x2, float y2, float x3, float y3,
                                float x4, float y4, int color) {
        emitQuad(g.bufferSource().getBuffer(RenderType.gui()), g.pose().last().pose(), x1, y1, x2, y2, x3, y3, x4, y4, color);
        g.flush();
    }

    /**
     * RenderType.gui() culls back faces, so every quad is emitted with the winding GuiGraphics#fill uses
     * (negative shoelace area in screen space), whichever order the caller listed the corners in.
     */
    private static void emitQuad(VertexConsumer vc, Matrix4f m, float x1, float y1, float x2, float y2,
                                 float x3, float y3, float x4, float y4, int color) {
        float area = (x1 * y2 - x2 * y1) + (x2 * y3 - x3 * y2) + (x3 * y4 - x4 * y3) + (x4 * y1 - x1 * y4);
        if (area > 0) {
            vc.addVertex(m, x4, y4, 0f).setColor(color);
            vc.addVertex(m, x3, y3, 0f).setColor(color);
            vc.addVertex(m, x2, y2, 0f).setColor(color);
            vc.addVertex(m, x1, y1, 0f).setColor(color);
        } else {
            vc.addVertex(m, x1, y1, 0f).setColor(color);
            vc.addVertex(m, x2, y2, 0f).setColor(color);
            vc.addVertex(m, x3, y3, 0f).setColor(color);
            vc.addVertex(m, x4, y4, 0f).setColor(color);
        }
    }

    /**
     * Glyphs of the enchanting-table alphabet (minecraft:alt font; lowercase letters become runes) set
     * round a circle, each turned to face outward.
     */
    public static void drawRuneRing(GuiGraphics g, Font font, float cx, float cy, float r, float rotation,
                                    String runes, int color, float scale) {
        int count = runes.length();
        float full = (float) (Math.PI * 2.0);
        for (int i = 0; i < count; i++) {
            float angle = rotation + full * i / count;
            Component glyph = Component.literal(String.valueOf(runes.charAt(i)))
                    .withStyle(s -> s.withFont(Minecraft.ALT_FONT));
            g.pose().pushPose();
            g.pose().translate(cx + Mth.cos(angle) * r, cy + Mth.sin(angle) * r, 0f);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotation(angle + (float) Math.PI / 2f));
            g.pose().scale(scale, scale, 1f);
            g.drawString(font, glyph, -font.width(glyph) / 2, -4, color, false);
            g.pose().popPose();
        }
    }

    public static int withAlpha(int color, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    /** Draws {@code text} centred on {@code cx}, shrunk only if it would overflow {@code maxWidth}. */
    public static void drawFittedCentered(GuiGraphics g, Font font, Component text,
                                          float cx, float y, int color, int maxWidth) {
        int w = font.width(text);
        float scale = w > maxWidth ? maxWidth / (float) w : 1f;
        GuiRenderUtil.drawScaledStringCentered(g, font, text, cx, y + (1f - scale) * font.lineHeight / 2f, color, scale);
    }

    /** Text button painted in a {@link Theme}: raised when idle, gold-rimmed on hover/focus, sunk while disabled. */
    public static class ThemedButton extends Button {
        private final Theme theme;

        public ThemedButton(int x, int y, int w, int h, Component message, OnPress onPress, Theme theme) {
            super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
            this.theme = theme;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Theme th = this.theme;
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();
            boolean lit = this.active && this.isHoveredOrFocused();
            g.fill(x, y, x + w, y + h, lit ? th.trimBright() : th.border());
            if (this.active) {
                g.fill(x + 1, y + 1, x + w - 1, y + h - 1, th.bevelDark());
                g.fill(x + 1, y + 1, x + w - 2, y + h - 2, lit ? th.wellLight() : th.bevelLight());
                g.fill(x + 2, y + 2, x + w - 2, y + h - 2, lit ? th.bevelLight() : th.fill());
            } else {
                g.fill(x + 1, y + 1, x + w - 1, y + h - 1, th.well());
            }
            int color = !this.active ? th.textDim() : lit ? th.trimBright() : th.text();
            Minecraft mc = Minecraft.getInstance();
            drawFittedCentered(g, mc.font, this.getMessage(), x + w / 2f, y + (h - 8) / 2f, color, w - 6);
        }
    }
}
