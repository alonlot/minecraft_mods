package com.hogwartsmagic.client.fx;

import com.mojang.logging.LogUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Screen-space layers drawn over the finished HUD by the cinematic effects (the letterbox bars, impact flashes, the
 * wind streaking past a fast broom). Forge 51 has no HUD event, so the HUD mixin
 * ({@code mixin.client.BasiliskGazeGuiMixin}) calls {@link #render} at the tail of {@code Gui#render}; each feature
 * registers its layer here from its own client setup, with an order (lower draws first, so higher sits on top).
 */
public final class FxOverlays {

    /** A layer: draw into {@code graphics}; {@code delta} gives the partial tick. */
    @FunctionalInterface
    public interface Layer {
        void render(GuiGraphics graphics, DeltaTracker delta);
    }

    /** Suggested orders. Wind and tints sit low; flashes above them; the letterbox bars on top of everything. */
    public static final int ORDER_WORLD_TINT = 100;
    public static final int ORDER_FLASH = 500;
    public static final int ORDER_LETTERBOX = 900;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Layer> LAYERS = new ConcurrentSkipListMap<>();

    private FxOverlays() {
    }

    /** Register (or replace) a layer under a unique name, drawn in {@code order} (lower first). */
    public static void register(int order, String name, Layer layer) {
        LAYERS.put(String.format("%06d:%s", Math.max(0, order), name), layer);
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (LAYERS.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Layer> entry : LAYERS.entrySet()) {
            try {
                entry.getValue().render(graphics, delta);
            } catch (RuntimeException e) {
                LOGGER.error("Cinematic overlay {} failed; dropping it", entry.getKey(), e);
                LAYERS.remove(entry.getKey());
            }
        }
    }
}
