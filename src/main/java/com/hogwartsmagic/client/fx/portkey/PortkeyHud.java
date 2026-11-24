package com.hogwartsmagic.client.fx.portkey;

import com.hogwartsmagic.client.SpellFxGeometry;
import com.hogwartsmagic.portkey.PortkeyTimeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Screen-space parts of the Portkey: the cold blue-white glow creeping in from the edges of the view as the charge
 * builds, pulsing like a heartbeat that quickens; and, where the tunnel shader cannot run (a shader pack, or the
 * chain failed to load) or over a dimension change's loading screen, a drawn tunnel: a deep blue-black field with
 * coloured streaks wheeling out from the middle.
 */
final class PortkeyHud {

    /** Streaks in the drawn tunnel. */
    private static final int STREAKS = 64;
    private static final int WALL = 0x0B0A1E;
    private static final int WALL_OUTER = 0x1F2A55;
    private static final int CORE = 0xD6ECFF;
    private static final int GOLD = 0xFFD98A;
    private static final int CYAN = 0x8FE3FF;
    private static final int ROSE = 0xFFB3C8;
    private static final int VIOLET = 0xB49CFF;
    private static final int VIGNETTE = 0xBFE0FF;

    private PortkeyHud() {
    }

    /** The overlay layer (FxOverlays, just above the world tints). */
    static void render(GuiGraphics graphics, DeltaTracker delta) {
        PortkeyTimeline.Frame f = PortkeyClient.current();
        if (f == null) {
            return;
        }
        float t = PortkeyClient.ownChargeAge();
        float h = PortkeyClient.ownHookAge();
        if (h < 0.0f && t >= 0.0f) {
            float fade = PortkeyClient.ownCancelFade();
            float e = PortkeyTimeline.chargeEase(t) * fade;
            float beat = PortkeyTimeline.vignetteBeats(t);
            float pulse = 0.62f + 0.38f * Mth.cos(beat * Mth.TWO_PI);
            vignette(graphics, PortkeyTimeline.VIGNETTE_ALPHA * e * pulse, 0.22f + 0.1f * e);
        } else if (h >= 0.0f && h < 3.0f) {
            // The edge glow flares out with the hook and drains into the tunnel.
            vignette(graphics, PortkeyTimeline.VIGNETTE_ALPHA * (1.0f - h / 3.0f) * 1.4f, 0.32f);
        }
        if (f.mix() > 0.002f && PortkeyPost.unavailable()) {
            drawFallbackTunnel(graphics, f.mix());
        }
    }

    private static int argb(float alpha, int rgb) {
        return (Mth.clamp(Math.round(alpha * 255.0f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Blue-white light creeping in from all four edges, {@code depth} of the short side deep. */
    private static void vignette(GuiGraphics graphics, float alpha, float depth) {
        if (alpha <= 0.004f) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int band = Math.max(1, Math.round(Math.min(width, height) * depth));
        graphics.fillGradient(0, 0, width, band, argb(alpha, VIGNETTE), argb(0.0f, VIGNETTE));
        graphics.fillGradient(0, height - band, width, height, argb(0.0f, VIGNETTE), argb(alpha, VIGNETTE));
        int steps = 16;
        for (int i = 0; i < steps; i++) {
            float k = 1.0f - i / (float) steps;
            int x0 = band * i / steps;
            int x1 = band * (i + 1) / steps;
            graphics.fill(x0, 0, x1, height, argb(alpha * k * k, VIGNETTE));
            graphics.fill(width - x1, 0, width - x0, height, argb(alpha * k * k, VIGNETTE));
        }
    }

    /** The drawn tunnel at {@code strength} (0..1): used without the shader, and over the loading screen. */
    static void drawFallbackTunnel(GuiGraphics graphics, float strength) {
        if (strength <= 0.003f) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float time = (float) (PortkeyClient.now() % 100000.0);
        float h = Math.max(0.0f, PortkeyClient.ownHookAge());
        float spin = PortkeyTimeline.spinAngle(h);
        graphics.fillGradient(0, 0, width, height, argb(0.92f * strength, WALL_OUTER), argb(0.92f * strength, WALL));
        graphics.fill(0, 0, width, height, argb(0.55f * strength, WALL));
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(width * 0.5f, height * 0.5f, 0.0f);
        float reach = (float) Math.hypot(width, height) * 0.6f;
        for (int k = 0; k < STREAKS; k++) {
            float pick = SpellFxGeometry.hash(k, 3, 17);
            int colour = pick < 0.70f ? CORE : pick < 0.85f ? GOLD : pick < 0.94f ? CYAN : pick < 0.97f ? ROSE : VIOLET;
            float angle = (float) Math.toDegrees(spin) + k * (360.0f / STREAKS) + SpellFxGeometry.hash(k, 5, 1) * 5.0f;
            // Each streak runs outwards from the middle, faster the further out it is: speed, not static spokes.
            float speed = 0.012f + 0.02f * SpellFxGeometry.hash(k, 7, 2);
            float run = (time * speed + SpellFxGeometry.hash(k, 9, 3)) % 1.0f;
            float near = reach * run * run;
            float length = reach * (0.08f + 0.3f * run) * (0.6f + 0.4f * SpellFxGeometry.hash(k, 11, 4));
            int thickness = run > 0.6f ? 2 : 1;
            float alpha = strength * (0.25f + 0.65f * run) * (colour == CORE ? 0.8f : 1.0f);
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(angle));
            // Drawn along y: fillGradient runs top to bottom, so the streak fades in from its inner end.
            graphics.fillGradient(-thickness, (int) near, thickness, (int) (near + length), argb(0.0f, colour),
                    argb(alpha, colour));
            pose.popPose();
        }
        pose.popPose();
        // The vanishing point glows.
        int glow = Math.max(4, Math.min(width, height) / 10);
        for (int i = 0; i < 6; i++) {
            int r = glow * (6 - i) / 6;
            graphics.fill(width / 2 - r, height / 2 - r, width / 2 + r, height / 2 + r, argb(0.07f * strength, CORE));
        }
    }
}
