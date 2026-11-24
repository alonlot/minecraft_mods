package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * The Drink of Despair on the drinker screen. All state is the amplifier of the local player own hidden
 * {@code despair} effect (0 to 5), so there is nothing to desync: it fades with the effect.
 *
 * <p>A dark green vignette closes in by stage; faint stacked bands at the top and bottom edges stand in for blur
 * (no blur shader); at the last two stages, while slowness holds the knees down, a dark band rises from the bottom
 * edge so the view sinks.
 */
public final class DespairOverlay {
    private static final int STRIPS = 20;
    private static final int GREEN_DEEP = 0x03100A;
    private static final int GREEN = 0x0B2418;

    private DespairOverlay() {}

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) {
            return;
        }
        MobEffectInstance despair = player.getEffect(ExampleMod.DESPAIR.getHolder().get());
        if (despair == null) {
            return;
        }
        int stage = Mth.clamp(despair.getAmplifier(), 0, 5);
        // Fade out over the last second so it never snaps off.
        float fade = Mth.clamp(despair.getDuration() / 20.0f, 0f, 1f);
        float breath = 0.03f * Mth.sin(player.tickCount * (0.05f + 0.01f * stage));
        float intensity = Mth.clamp((0.18f + 0.16f * stage + breath) * fade, 0f, 1f);
        int w = g.guiWidth();
        int h = g.guiHeight();
        int peak = (int) (235 * intensity);
        int sideDepth = (int) (w * (0.12f + 0.30f * intensity));
        int topDepth = (int) (h * (0.12f + 0.30f * intensity));
        int stripW = Math.max(1, sideDepth / STRIPS);
        int stripH = Math.max(1, topDepth / STRIPS);
        for (int i = 0; i < STRIPS; i++) {
            float t = 1f - i / (float) STRIPS;
            int alpha = (int) (peak * t * t);
            if (alpha <= 2) {
                continue;
            }
            int colour = (alpha << 24) | (i < STRIPS / 3 ? GREEN_DEEP : GREEN);
            int x = i * stripW;
            g.fill(x, 0, x + stripW, h, colour);
            g.fill(w - x - stripW, 0, w - x, h, colour);
            int y = i * stripH;
            g.fill(0, y, w, y + stripH, colour);
            g.fill(0, h - y - stripH, w, h - y, colour);
        }
        // Edge "blur": faint stacked bands so the border reads smeared rather than sharp.
        if (stage >= 2) {
            int smear = (int) (50 * intensity);
            for (int i = 0; i < 6; i++) {
                int a = smear * (6 - i) / 6;
                g.fill(0, i * 3, w, i * 3 + 3, (a << 24) | GREEN_DEEP);
                g.fill(0, h - i * 3 - 3, w, h - i * 3, (a << 24) | GREEN_DEEP);
            }
        }
        // Kneeling: slowness 255 is only ever applied for the kneel window, so it is the cue for the sinking view.
        MobEffectInstance slow = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (stage >= 4 && slow != null && slow.getAmplifier() >= 200) {
            float sink = Mth.clamp(slow.getDuration() / 10f, 0f, 1f);
            int band = (int) (h * 0.14f * sink);
            g.fill(0, h - band, w, h, ((int) (210 * sink) << 24) | GREEN_DEEP);
        }
    }
}
