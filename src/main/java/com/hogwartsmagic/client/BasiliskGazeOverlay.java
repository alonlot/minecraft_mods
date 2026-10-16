package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * Draws the basilisk's stare closing in.
 *
 * <p>The whole state of this overlay is the amplifier of the player's
 * {@code basilisk_gaze} effect — tier 0 is a faint grey at the corners of the
 * eye, tier 4 is a screen almost gone to stone. Nothing is tracked client side,
 * so there is nothing to desync: look away and the basilisk stops refreshing
 * the effect, it lapses, and the overlay drains with it.
 */
public final class BasiliskGazeOverlay {

    /** Number of strips used to fake a horizontal gradient down each side. */
    private static final int STRIPS = 24;
    private static final int STONE = 0x8A8F86;
    private static final int STONE_DEEP = 0x4A4F48;

    private BasiliskGazeOverlay() {
    }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        MobEffectInstance gaze = player.getEffect(ExampleMod.BASILISK_GAZE.getHolder().get());
        if (gaze == null) {
            return;
        }

        int tier = Mth.clamp(gaze.getAmplifier(), 0, 4);
        float intensity = (tier + 1) / 5.0F;
        // A slow swell, so the edges breathe rather than sit still.
        float time = (player.tickCount % 200) / 200.0F * Mth.TWO_PI;
        intensity = Mth.clamp(intensity + Mth.sin(time * (1.0F + tier)) * 0.025F, 0.0F, 1.0F);
        // From tier two the colour starts draining out of the whole view.
        int wash = tier >= 2 ? 28 * (tier - 1) : 0;
        drawEdges(graphics, intensity, wash);
    }

    /**
     * The stone closing in from the edges: {@code intensity} 0 to 1 sets how deep and how opaque, {@code wash} is
     * the alpha (0 to 255) of a grey laid over the whole view. Shared with the Pensieve's {@code grey_edges} effect.
     */
    public static void drawEdges(GuiGraphics graphics, float intensity, int wash) {
        if (intensity <= 0.0F) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int peak = (int) (245 * intensity);

        int sideDepth = (int) (width * (0.15F + 0.30F * intensity));
        int topDepth = (int) (height * (0.13F + 0.30F * intensity));

        int stripW = Math.max(1, sideDepth / STRIPS);
        for (int i = 0; i < STRIPS; i++) {
            float t = 1.0F - i / (float) STRIPS;
            int alpha = (int) (peak * t * t);
            if (alpha <= 2) {
                continue;
            }
            int colour = (alpha << 24) | (i < STRIPS / 3 ? STONE_DEEP : STONE);
            int x = i * stripW;
            graphics.fill(x, 0, x + stripW, height, colour);
            graphics.fill(width - x - stripW, 0, width - x, height, colour);
        }

        int stripH = Math.max(1, topDepth / STRIPS);
        for (int i = 0; i < STRIPS; i++) {
            float t = 1.0F - i / (float) STRIPS;
            int alpha = (int) (peak * t * t);
            if (alpha <= 2) {
                continue;
            }
            int colour = (alpha << 24) | (i < STRIPS / 3 ? STONE_DEEP : STONE);
            int y = i * stripH;
            graphics.fill(0, y, width, y + stripH, colour);
            graphics.fill(0, height - y - stripH, width, height - y, colour);
        }

        if (wash > 0) {
            graphics.fill(0, 0, width, height, (Mth.clamp(wash, 0, 255) << 24) | STONE);
        }
    }
}
