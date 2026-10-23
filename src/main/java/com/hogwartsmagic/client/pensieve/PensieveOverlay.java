package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.BasiliskGazeOverlay;
import com.hogwartsmagic.event.ClientModEvents;
import com.hogwartsmagic.pensieve.scene.Eyelids;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import static com.hogwartsmagic.client.pensieve.PensieveTimeline.*;

/**
 * Screen-space layers of the dive, drawn by {@code PensieveGuiMixin} in place of the vanilla HUD: the silver
 * surface rising over the screen as the basin swallows the viewer, the black fall with silver wisps streaking
 * past in three parallax layers, the dark rise on the way out, the surface draining away on the return, and the
 * red-white flash of a yank. Stands in for {@link PensievePost} (darkness, red edge) if the post chain is off.
 */
public final class PensieveOverlay {
    private static final ResourceLocation SURFACE = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID,
            "textures/gui/pensieve/silver_surface.png");
    private static final ResourceLocation WISP = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID,
            "textures/gui/pensieve/wisp.png");
    private static final int SURFACE_FRAMES = 8;
    private static final int FRAME_W = 256;
    private static final int FRAME_H = 128;
    private static final int SURFACE_TICKS_PER_FRAME = 3;

    private static final int[] WISP_COUNT = {16, 10, 6};
    private static final float[] WISP_WIDTH = {3, 5, 8};
    private static final float[] WISP_LENGTH = {0.22f, 0.36f, 0.55f};
    private static final float[] WISP_SPEED = {0.035f, 0.06f, 0.1f};
    private static final float[] WISP_ALPHA = {0.3f, 0.5f, 0.85f};
    /** The soft gradient edge of an eyelid, in pixels. */
    private static final int EYELID_EDGE = 24;
    private static final int HINT_COLOR = 0xD8E4F2;

    private PensieveOverlay() {
    }

    public static void render(GuiGraphics g, float partial) {
        int phase = PensieveClientState.phase();
        int w = g.guiWidth();
        int h = g.guiHeight();
        if (phase == PensieveClientState.NONE) {
            // The wake: the one lid effect drawn over the real world, after the dive is over.
            if (PensieveClientState.waking()) lids(g, w, h, PensieveClientState.wakeClosed(partial));
            return;
        }
        float pt = PensieveClientState.phaseTime(partial);
        float clock = PensieveClientState.clock(partial);

        if (phase == PensieveClientState.SWALLOW) {
            float rise = surfaceRise(pt);
            if (rise > 0) surface(g, w, h, rise, clock, 1);
        } else if (phase == PensieveClientState.FALL) {
            float k = clamp01(pt / FALL_FADE_TICKS);
            g.fill(0, 0, w, h, 0xFF000000);
            if (k < 1) surface(g, w, h, 1, clock, 1 - k);
            wisps(g, w, h, clock, k, true);
        } else if (phase == PensieveClientState.BUILD || phase == PensieveClientState.PLAY) {
            fallbackDarkness(g, w, h, partial);
            whiteHandover(g, w, h, partial);
            greyEdges(g, partial, clock);
            patronusWash(g, w, h, partial);
            wandBloom(g, w, h, partial);
            float rt = PensieveClientState.revealTime(partial);
            if (rt < 10) wisps(g, w, h, clock, 1 - rt / 10, true);
            hint(g, w, h, phase, pt, rt);
            // Last, so shut eyes hide the wisps and the leave hint too.
            eyelids(g, w, h, partial);
        } else if (phase == PensieveClientState.EXIT) {
            float rt = PensieveClientState.returnTime(partial);
            if (rt >= 0) {
                // Back at the basin: the silver drains down the screen as the camera rises out of it.
                float k = returnMove(rt);
                surface(g, w, h, 1 - k, clock, 1);
                float black = 1 - clamp01(rt / 3);
                if (black > 0) g.fill(0, 0, w, h, argb(black, 0));
            } else if (pt < EXIT_COLLAPSE_TICKS) {
                fallbackDarkness(g, w, h, partial);
                // Leaving during a white chain fade: the white drains into the collapse's black instead of flipping.
                exitWhite(g, w, h, partial, pt);
                // A memory that ended on eyes_close stays black through the collapse: no flash of the scene.
                eyelids(g, w, h, partial);
            } else {
                g.fill(0, 0, w, h, 0xFF000000);
                wisps(g, w, h, clock, clamp01((pt - EXIT_COLLAPSE_TICKS) / 3), false);
            }
            // Going home to a wake: no silver draining away, the eyes stay shut until the wake opens them.
            if (PensieveClientState.wakePending()) g.fill(0, 0, w, h, 0xFF000000);
        } else if (phase == PensieveClientState.YANK) {
            if (pt < FLASH_TICKS) {
                // Red-white flash: white hot at the instant of the tear, reddening as it goes.
                float k = clamp01(pt / FLASH_TICKS);
                int r = 255;
                int gr = (int) (235 - 175 * k);
                int b = (int) (225 - 185 * k);
                g.fill(0, 0, w, h, 0xFF000000 | r << 16 | gr << 8 | b);
            } else {
                g.fill(0, 0, w, h, 0xFF000000);
            }
        }

        if (!(PensievePost.working() && PensievePost.wanted())) {
            redEdge(g, w, h, PensieveClientState.thud(partial));
        }
    }

    /** The swirling silver liquid filling the bottom {@code cover} fraction of the screen. */
    private static void surface(GuiGraphics g, int w, int h, float cover, float clock, float alpha) {
        int sh = Math.round(h * clamp01(cover));
        if (sh <= 0 || alpha <= 0) return;
        int frame = Math.floorMod((int) (clock / SURFACE_TICKS_PER_FRAME), SURFACE_FRAMES);
        // Constant texel scale: the meniscus is the texture's top row, and the liquid does not stretch as it rises.
        int vh = Math.max(1, Math.round(FRAME_H * (float) sh / h));
        RenderSystem.enableBlend();
        g.setColor(1, 1, 1, alpha);
        g.blit(SURFACE, 0, h - sh, w, sh, frame * FRAME_W, 0, FRAME_W, vh, FRAME_W * SURFACE_FRAMES, FRAME_H);
        g.setColor(1, 1, 1, 1);
        // The bright meniscus line catching the light.
        int top = h - sh;
        g.fillGradient(0, top - 4, w, top + 2, argb(0, 0xE0EEFA), argb(0.8f * alpha, 0xE0EEFA));
        RenderSystem.disableBlend();
    }

    /** Three parallax layers of silver wisps; {@code up} = streaking upward (falling), else downward (rising). */
    private static void wisps(GuiGraphics g, int w, int h, float clock, float alpha, boolean up) {
        if (alpha <= 0) return;
        RenderSystem.enableBlend();
        for (int layer = 0; layer < WISP_COUNT.length; layer++) {
            int len = Math.round(h * WISP_LENGTH[layer]);
            int ww = Math.max(1, Math.round(WISP_WIDTH[layer]));
            float span = h + len;
            g.setColor(1, 1, 1, alpha * WISP_ALPHA[layer]);
            for (int i = 0; i < WISP_COUNT[layer]; i++) {
                float hx = hash(i * 7 + layer * 101);
                float hy = hash(i * 13 + layer * 57 + 3);
                float speed = WISP_SPEED[layer] * (0.8f + 0.4f * hash(i * 3 + layer * 31 + 9));
                int x = Math.round(hx * w + Mth.sin(clock * 0.05f + i) * 4);
                float pos = (hy * span + clock * speed * h) % span;
                int y = up ? Math.round(h - pos) : Math.round(pos - len);
                g.blit(WISP, x, y, ww, len, up ? 0 : 16, 0, 16, 128, 32, 128);
            }
        }
        g.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    /** A scene's {@code grey_edges} effect: the basilisk's stare greying the view, as the real gaze overlay does. */
    private static void greyEdges(GuiGraphics g, float partial, float clock) {
        float k = EffectVisuals.greyEdges(partial);
        if (k <= 0) return;
        // The same slow swell as the real overlay, so the edges breathe.
        float swell = Mth.sin(clock / 200f * Mth.TWO_PI * (1 + 4 * k)) * 0.025f;
        float intensity = Mth.clamp(k + swell, 0f, 1f);
        // The whole view starts to drain from the real tier 2 (intensity 0.6), up to its tier-4 wash of 84.
        int wash = Math.round(Mth.clamp((k - 0.4f) / 0.6f, 0f, 1f) * 84);
        RenderSystem.enableBlend();
        BasiliskGazeOverlay.drawEdges(g, intensity, wash);
        RenderSystem.disableBlend();
    }

    /** A scene patronus_light effect: a brief soft white wash over the view for those near the light. */
    private static void patronusWash(GuiGraphics g, int w, int h, float partial) {
        float a = EffectVisuals.patronusWash(partial);
        if (a <= 0.003f) return;
        RenderSystem.enableBlend();
        g.fill(0, 0, w, h, argb(a, 0xE8F2FF));
        RenderSystem.disableBlend();
    }

    /** A scene wand_choice effect: a soft warm bloom over the view for those near it as the wand chooses. */
    private static void wandBloom(GuiGraphics g, int w, int h, float partial) {
        float a = EffectVisuals.wandBloom(partial);
        if (a <= 0.003f) return;
        RenderSystem.enableBlend();
        g.fill(0, 0, w, h, argb(a, 0xFFE2A8));
        RenderSystem.disableBlend();
    }

    /**
     * A scene's {@code eyes_open} / {@code eyes_close}: two black lids, each covering half the screen when shut. A
     * scene that opens its eyes at its start has them held shut while it builds ({@link PensieveClientState#lidsHeldShut}).
     */
    private static void eyelids(GuiGraphics g, int w, int h, float partial) {
        lids(g, w, h, PensieveClientState.lidsClosed(partial));
    }

    /** Two black lids, {@code closed} 0 (open) to 1 (shut), with soft inner edges. */
    private static void lids(GuiGraphics g, int w, int h, float closed) {
        if (closed <= 0f) return;
        int band = Eyelids.band(closed, h, EYELID_EDGE);
        if (band <= 0) return;
        band = Math.min(band, h);
        int soft = Math.min(EYELID_EDGE, band);
        int black = 0xFF000000;
        int clear = 0x00000000;
        RenderSystem.enableBlend();
        g.fill(0, 0, w, band - soft, black);
        g.fillGradient(0, band - soft, w, band, black, clear);
        g.fill(0, h - band + soft, w, h, black);
        g.fillGradient(0, h - band, w, h - band + soft, clear, black);
        RenderSystem.disableBlend();
    }

    /** A chain's white hand-over: the view fades to white at the scene's end, holds, and fades in from white. */
    private static void whiteHandover(GuiGraphics g, int w, int h, float partial) {
        float a = PensieveClientState.handoverCover(partial)[1];
        if (a <= 0f) return;
        RenderSystem.enableBlend();
        g.fill(0, 0, w, h, argb(a, 0xFFFFFF));
        RenderSystem.disableBlend();
    }

    private static void exitWhite(GuiGraphics g, int w, int h, float partial, float pt) {
        float a = PensieveClientState.handoverCover(partial)[1] * (1 - PensieveTimeline.exitDarkness(pt));
        if (a <= 0f) return;
        RenderSystem.enableBlend();
        g.fill(0, 0, w, h, argb(a, 0xFFFFFF));
        RenderSystem.disableBlend();
    }

    private static void fallbackDarkness(GuiGraphics g, int w, int h, float partial) {
        if (PensievePost.working()) return;
        float d = PensievePost.darkness(partial);
        if (d > 0) g.fill(0, 0, w, h, argb(d, 0));
    }

    private static void redEdge(GuiGraphics g, int w, int h, float strength) {
        if (strength <= 0) return;
        int band = Math.max(8, h / 6);
        int edge = argb(0.6f * strength, 0x8C0808);
        int clear = argb(0, 0x8C0808);
        g.fillGradient(0, 0, w, band, edge, clear);
        g.fillGradient(0, h - band, w, h, clear, edge);
    }

    /** The leave key, shown while the memory builds and for the first seconds of play. */
    private static void hint(GuiGraphics g, int w, int h, int phase, float pt, float revealTime) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        float a = clamp01(revealTime / 20f);
        if (phase == PensieveClientState.PLAY) a *= 1 - clamp01((pt - 80) / 40f);
        if (a <= 0.03f) return;
        Component line = Component.translatable("gui.hogwartsmagic.pensieve.leave_hint",
                ClientModEvents.PENSIEVE_LEAVE_KEY.getTranslatedKeyMessage());
        RenderSystem.enableBlend();
        g.drawCenteredString(mc.font, line, w / 2, h - 34, argb(0.75f * a, HINT_COLOR));
        RenderSystem.disableBlend();
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.round(clamp01(alpha) * 255);
        return a << 24 | (rgb & 0xFFFFFF);
    }

    private static float hash(int n) {
        double s = Math.sin(n * 12.9898 + 78.233) * 43758.5453;
        return (float) (s - Math.floor(s));
    }
}
