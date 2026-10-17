package com.hogwartsmagic.client;

import com.hogwartsmagic.entity.DarkMarkEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * Under a Dark Mark the whole sky turns sickly green, as it does over the Quidditch World Cup camp:
 * the fog colour (which the horizon and the sky's lower band are drawn in) is pulled toward a deep
 * emerald, strongest right under the mark and gone by {@link #RANGE} blocks.
 */
public final class DarkMarkSkyTint {
    private static final double RANGE = 192.0D;
    private static final float MAX_BLEND = 0.55f;
    private static final float R = 0.05f, G = 0.20f, B = 0.09f;

    private static long cachedTick = Long.MIN_VALUE;
    private static float cachedTarget;
    private static float current;

    private DarkMarkSkyTint() {
    }

    public static void apply(ViewportEvent.ComputeFogColor event) {
        float k = strength(event.getCamera().getPosition());
        if (k <= 0.001f) {
            return;
        }
        float t = k * MAX_BLEND;
        event.setRed(event.getRed() + (R - event.getRed()) * t);
        event.setGreen(event.getGreen() + (G - event.getGreen()) * t);
        event.setBlue(event.getBlue() + (B - event.getBlue()) * t);
    }

    /** 0..1, eased toward the nearest mark's pull so it rolls in and out instead of popping. */
    private static float strength(Vec3 eye) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            current = 0f;
            return 0f;
        }
        long tick = mc.level.getGameTime();
        if (tick != cachedTick) {
            cachedTick = tick;
            float best = 0f;
            for (DarkMarkEntity mark : mc.level.getEntitiesOfClass(DarkMarkEntity.class, new AABB(eye, eye).inflate(RANGE))) {
                double d = mark.position().distanceTo(eye);
                float near = (float) Math.max(0.0D, 1.0D - d / RANGE);
                float life = Math.min(1f, mark.tickCount / 60f) * Math.min(1f, mark.getRemainingTicks() / 80f);
                best = Math.max(best, (float) Math.sqrt(near) * life);
            }
            cachedTarget = best;
            current += (cachedTarget - current) * 0.08f;
        }
        return current;
    }
}
