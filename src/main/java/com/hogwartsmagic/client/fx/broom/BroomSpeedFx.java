package com.hogwartsmagic.client.fx.broom;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.fx.FxOverlays;
import com.hogwartsmagic.entity.BroomEntity;
import com.hogwartsmagic.fx.FxConfig;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Matrix4f;

/**
 * What flying fast on a broom does to the view. As the speed climbs the field of view eases wider (and kicks a
 * little further the moment a boost lights), and thin pale streaks of wind stream outward past the edges of the
 * screen from the centre: more of them, and longer, the faster you go, and never across the middle third. All of
 * it eases in and out, so a boost is felt arriving rather than switched on. Off with {@code broomSpeedFx}.
 *
 * <p>The FOV is a plain multiplier on the configured field of view, so it stacks with the Killing Curse's
 * tightening and kick ({@code AvadaChargeOverlay}) instead of fighting it.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class BroomSpeedFx {
    /** Speeds are measured against the broom's own cruising speed, so Firebolt and Training read alike. */
    private static final float FAST_FROM = 0.6f;
    private static final float FASTEST_AT = 3.2f;
    /** How far the view widens at full speed (a fraction of the field of view). */
    private static final float FOV_WIDEN = 0.30f;
    /** The extra kick as a boost lights, and how long it takes to come and go. */
    private static final float FOV_KICK = 0.07f;
    private static final int KICK_TICKS = 14;
    /** Never widen the view past this many degrees, whatever the player's FOV slider says. */
    private static final double FOV_CEILING = 140.0;

    private static final int STREAKS = 72;
    /** Streaks live between this fraction of the way to the screen edge and just past it (the middle third is 1/3). */
    private static final float INNER = 0.42f;

    private static float prevSpeed;
    private static float speed;
    private static int kickTicks;
    private static boolean wasBoosting;

    private BroomSpeedFx() {
    }

    /** 0 (cruising or slower) to 1 (flat out) for a broom moving {@code blocksPerTick}. */
    static float fraction(BroomEntity broom, double blocksPerTick) {
        int type = broom.getBroomType();
        float base = type == 2 ? 3.0f : type == 1 ? 2.5f : 2.0f;
        return smoothstep(FAST_FROM * base, FASTEST_AT * base, (float) blocksPerTick);
    }

    /** How far a broom moved over the last tick (position, not velocity: remote brooms have no usable velocity). */
    static double movedLastTick(BroomEntity broom) {
        double dx = broom.getX() - broom.xo;
        double dy = broom.getY() - broom.yo;
        double dz = broom.getZ() - broom.zo;
        return Math.max(Math.sqrt(dx * dx + dy * dy + dz * dz), broom.getDeltaMovement().length());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.isPaused()) {
            return;
        }
        prevSpeed = speed;
        float target = 0.0f;
        boolean boosting = false;
        if (FxConfig.get(FxConfig.BROOM_SPEED_FX) && minecraft.player != null
                && minecraft.player.getVehicle() instanceof BroomEntity broom) {
            target = fraction(broom, movedLastTick(broom));
            boosting = broom.isBoostingSynced() && target > 0.3f;
        }
        // Ease towards the speed: quicker to build than to let go, so a boost is felt and a lull is gentle.
        speed += (target - speed) * (target > speed ? 0.07f : 0.045f);
        if (speed < 0.002f && target == 0.0f) {
            speed = 0.0f;
        }
        if (boosting && !wasBoosting) {
            kickTicks = KICK_TICKS;
        } else if (kickTicks > 0) {
            kickTicks--;
        }
        wasBoosting = boosting;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        prevSpeed = 0.0f;
        speed = 0.0f;
        kickTicks = 0;
        wasBoosting = false;
    }

    private static float eased(float partial) {
        float s = Mth.lerp(partial, prevSpeed, speed);
        return s * s * (3.0f - 2.0f * s);
    }

    @SubscribeEvent
    public static void onViewportFov(ViewportEvent.ComputeFov event) {
        if (speed <= 0.0f && prevSpeed <= 0.0f && kickTicks == 0 || !event.usedConfiguredFov()) {
            return;
        }
        float partial = (float) event.getPartialTick();
        float widen = FOV_WIDEN * eased(partial);
        if (kickTicks > 0) {
            float progress = (KICK_TICKS - kickTicks + partial) / KICK_TICKS;
            widen += FOV_KICK * Mth.sin((float) Math.PI * Mth.clamp(progress, 0.0f, 1.0f));
        }
        if (widen <= 0.0005f) {
            return;
        }
        double fov = event.getFOV();
        event.setFOV(Math.min(fov * (1.0 + widen), Math.max(fov, FOV_CEILING)));
    }

    @Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            FxOverlays.register(FxOverlays.ORDER_WORLD_TINT, "broom_wind", BroomSpeedFx::renderWind);
        }
    }

    // ---- wind --------------------------------------------------------------------------------------------

    private static void renderWind(GuiGraphics graphics, DeltaTracker delta) {
        if (speed <= 0.004f) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null
                || com.hogwartsmagic.client.pensieve.PensieveClientState.hidesHud()) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        float s = eased(partial);
        if (s <= 0.03f || minecraft.level == null) {
            return;
        }
        float width = graphics.guiWidth();
        float height = graphics.guiHeight();
        double seconds = (minecraft.level.getGameTime() + partial) / 20.0;
        // More streaks the faster you go.
        float density = (float) Math.pow(s, 0.7);

        Matrix4f m = graphics.pose().last().pose();
        VertexConsumer out = graphics.bufferSource().getBuffer(RenderType.gui());
        for (int i = 0; i < STREAKS; i++) {
            if (hash(i, 3) > density) {
                continue;
            }
            float angle = hash(i, 0) * (float) (Math.PI * 2.0);
            double cycles = hash(i, 1) + seconds * (0.9 + 0.9 * hash(i, 2));
            float p = (float) (cycles - Math.floor(cycles));
            float cos = Mth.cos(angle);
            float sin = Mth.sin(angle);
            // Distance along this ray from the centre to the screen edge.
            float edge = Math.min(width * 0.5f / Math.max(Math.abs(cos), 1.0E-3f),
                    height * 0.5f / Math.max(Math.abs(sin), 1.0E-3f));
            float head = INNER + 0.80f * p * p;
            float length = (0.07f + 0.30f * s) * (0.6f + 0.8f * hash(i, 4)) * (0.4f + 0.6f * p);
            float tail = Math.max(INNER, head - length);
            if (head - tail < 0.01f) {
                continue;
            }
            float fade = smoothstep(0.0f, 0.18f, p) * (1.0f - smoothstep(0.82f, 1.0f, p));
            float alpha = 0.50f * (0.35f + 0.65f * s) * fade;
            if (alpha < 0.02f) {
                continue;
            }
            float thick = (hash(i, 5) < 0.3f ? 1.6f : 0.9f) * (0.8f + 0.5f * s);
            streak(out, m, width * 0.5f + cos * edge * tail, height * 0.5f + sin * edge * tail,
                    width * 0.5f + cos * edge * head, height * 0.5f + sin * edge * head, thick, alpha);
        }
        graphics.flush();
    }

    /** A thin quad from a transparent tail to a pale head. */
    private static void streak(VertexConsumer out, Matrix4f m, float x0, float y0, float x1, float y1, float thick,
                               float alpha) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 1.0f) {
            return;
        }
        float nx = -dy / len * thick * 0.5f;
        float ny = dx / len * thick * 0.5f;
        int head = argb(alpha);
        int tail = argb(0.0f);
        // Wound the way GuiGraphics#fill winds its quads (RenderType.gui culls back faces); flip if needed.
        float ax = x0 + nx, ay = y0 + ny, bx = x0 - nx, by = y0 - ny;
        float cx = x1 - nx * 0.4f, cy = y1 - ny * 0.4f, dx2 = x1 + nx * 0.4f, dy2 = y1 + ny * 0.4f;
        float shoelace = (ax * by - bx * ay) + (bx * cy - cx * by) + (cx * dy2 - dx2 * cy) + (dx2 * ay - ax * dy2);
        if (shoelace > 0) {
            out.addVertex(m, dx2, dy2, 0.0f).setColor(head);
            out.addVertex(m, cx, cy, 0.0f).setColor(head);
            out.addVertex(m, bx, by, 0.0f).setColor(tail);
            out.addVertex(m, ax, ay, 0.0f).setColor(tail);
        } else {
            out.addVertex(m, ax, ay, 0.0f).setColor(tail);
            out.addVertex(m, bx, by, 0.0f).setColor(tail);
            out.addVertex(m, cx, cy, 0.0f).setColor(head);
            out.addVertex(m, dx2, dy2, 0.0f).setColor(head);
        }
    }

    private static int argb(float alpha) {
        return (Mth.clamp(Math.round(alpha * 255.0f), 0, 255) << 24) | 0xE6F0FF;
    }

    /** A stable pseudo-random 0..1 for (streak, channel): the streaks are the same every frame. */
    private static float hash(int i, int channel) {
        double v = Math.sin(i * 12.9898 + channel * 78.233) * 43758.5453;
        return (float) (v - Math.floor(v));
    }

    static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
