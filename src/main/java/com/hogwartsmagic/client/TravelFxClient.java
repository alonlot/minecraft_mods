package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.TravelFx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

import static com.hogwartsmagic.client.SpellFxGeometry.*;

/**
 * How travel looks. Apparition: while you focus, a dark violet vortex spirals into you; you implode out of the
 * world with a squeeze, and land with a crack, a flash and a ring of displaced air. For the traveller themself the
 * view slams shut into a twisting black tunnel and opens on the destination, the camera unwinding. Floo: emerald
 * flames roar up round the traveller and take them; at the far hearth they burst out and give them back. For the
 * traveller the flames climb the screen, the view spins through a green tunnel, and the flames sink away.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class TravelFxClient {
    private static final float APPARATE_DEPART_TICKS = 12.0f;
    private static final float APPARATE_ARRIVE_TICKS = 16.0f;
    private static final float FLOO_ARRIVE_TICKS = 16.0f;
    private static final float TUNNEL_TICKS = 14.0f;
    private static final float FLOO_SINK_TICKS = 12.0f;
    // Apparition violet and Floo emerald.
    private static final float VIOLET_R = 0.62f, VIOLET_G = 0.40f, VIOLET_B = 1.0f;
    private static final float PALE_R = 0.92f, PALE_G = 0.86f, PALE_B = 1.0f;
    private static final float EMERALD_R = 0.25f, EMERALD_G = 1.0f, EMERALD_B = 0.45f;
    private static final float EMBER_R = 0.75f, EMBER_G = 1.0f, EMBER_B = 0.7f;

    private record Event(int entityId, byte kind, Vec3 pos, double start, int duration, int seed) {
        float age(double now) {
            return (float) (now - start);
        }
    }

    private static final List<Event> EVENTS = new ArrayList<>();
    /** This client's own trip: when the tunnel (Apparition) or the flames (Floo) began, or -1. */
    private static double tunnelStart = -1.0;
    private static double flooStart = -1.0;
    private static int flooDuration;
    private static double flooArrive = -1.0;

    private TravelFxClient() {
    }

    public static void accept(SpellPackets.TravelFxPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        double now = minecraft.level.getGameTime();
        byte kind = packet.kind();
        boolean self = minecraft.player != null && packet.entityId() == minecraft.player.getId();
        if (kind == TravelFx.APPARATE_CANCEL || kind == TravelFx.FLOO_FIZZLE) {
            EVENTS.removeIf(e -> e.entityId == packet.entityId()
                    && (e.kind == TravelFx.APPARATE_FOCUS || e.kind == TravelFx.FLOO_DEPART));
            if (self && kind == TravelFx.FLOO_FIZZLE) {
                flooStart = -1.0;
            }
            return;
        }
        if (kind == TravelFx.APPARATE_DEPART) {
            EVENTS.removeIf(e -> e.entityId == packet.entityId() && e.kind == TravelFx.APPARATE_FOCUS);
        }
        if (self) {
            if (kind == TravelFx.APPARATE_ARRIVE) {
                tunnelStart = now;
            } else if (kind == TravelFx.FLOO_DEPART) {
                flooStart = now;
                flooDuration = Math.max(1, packet.duration());
            } else if (kind == TravelFx.FLOO_ARRIVE) {
                flooStart = -1.0;
                flooArrive = now;
            }
        }
        EVENTS.add(new Event(packet.entityId(), kind, new Vec3(packet.x(), packet.y(), packet.z()), now,
                packet.duration(), packet.entityId() * 0x45D9F3B + (int) now));
    }

    private static float length(Event e) {
        if (e.kind == TravelFx.APPARATE_FOCUS || e.kind == TravelFx.FLOO_DEPART) {
            return e.duration + 4.0f;
        }
        if (e.kind == TravelFx.APPARATE_DEPART) {
            return APPARATE_DEPART_TICKS;
        }
        return e.kind == TravelFx.APPARATE_ARRIVE ? APPARATE_ARRIVE_TICKS : FLOO_ARRIVE_TICKS;
    }

    // ---- the world ---------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || EVENTS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        double now = minecraft.level.getGameTime() + partial;
        EVENTS.removeIf(e -> e.age(now) > length(e));
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();

        VertexConsumer dark = buffer.getBuffer(ModRenderTypes.AVADA_SHADOW);
        for (Event e : EVENTS) {
            Vec3 at = where(minecraft, e, partial);
            if (at == null || ownFirstPerson(minecraft, e)) {
                continue;
            }
            float t = e.age(now);
            Vec3 pos = at.subtract(camera);
            if (e.kind == TravelFx.APPARATE_FOCUS) {
                float f = Math.min(1.0f, t / Math.max(1, e.duration));
                billboard(dark, m, pos.add(0.0, 1.0, 0.0), 1.0f + 0.6f * f, 0.35f * smoothstep(0.0f, 6.0f, t),
                        SMOKE_R, SMOKE_G, SMOKE_B, false);
            } else if (e.kind == TravelFx.APPARATE_DEPART) {
                // The implosion: a hole of dark collapsing into a point.
                float f = t / APPARATE_DEPART_TICKS;
                float r = 1.6f * (1.0f - smoothstep(0.0f, 0.4f, f));
                billboard(dark, m, pos.add(0.0, 1.0, 0.0), r + 0.05f, 0.7f * (1.0f - f), SMOKE_R, SMOKE_G, SMOKE_B, false);
            } else if (e.kind == TravelFx.APPARATE_ARRIVE) {
                // Displaced air rolling out low along the ground.
                float f = t / APPARATE_ARRIVE_TICKS;
                for (int k = 0; k < 10; k++) {
                    double angle = k * Math.PI * 2.0 / 10.0 + hash(e.seed, k, 1);
                    double reach = 2.6 * (1.0 - (1.0 - f) * (1.0 - f));
                    Vec3 c = pos.add(Math.cos(angle) * reach, 0.25 + 0.2 * f, Math.sin(angle) * reach);
                    billboard(dark, m, c, 0.3f + 0.5f * f, 0.3f * (1.0f - f), 0.55f, 0.55f, 0.58f, false, 3, 10);
                }
            }
        }
        buffer.endBatch(ModRenderTypes.AVADA_SHADOW);

        VertexConsumer light = buffer.getBuffer(ModRenderTypes.AVADA_LIGHT);
        for (Event e : EVENTS) {
            Vec3 at = where(minecraft, e, partial);
            if (at == null || ownFirstPerson(minecraft, e)) {
                continue;
            }
            float t = e.age(now);
            Vec3 pos = at.subtract(camera);
            if (e.kind == TravelFx.APPARATE_FOCUS) {
                drawVortex(light, m, e, pos, t);
            } else if (e.kind == TravelFx.APPARATE_DEPART) {
                float f = t / APPARATE_DEPART_TICKS;
                Vec3 c = pos.add(0.0, 1.0, 0.0);
                float pop = Math.max(0.0f, 1.0f - Math.abs(t - 3.0f) / 2.0f);
                billboard(light, m, c, 0.6f + 0.8f * pop, pop, PALE_R, PALE_G, PALE_B, true);
                ring(light, m, c, new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0), 2.2f * smoothstep(0.0f, 0.6f, f),
                        0.12f, 0.6f * (1.0f - f), VIOLET_R, VIOLET_G, VIOLET_B, 40);
            } else if (e.kind == TravelFx.APPARATE_ARRIVE) {
                drawCrack(light, m, e, pos, t);
            } else if (e.kind == TravelFx.FLOO_DEPART) {
                float f = Math.min(1.0f, t / Math.max(1, e.duration));
                float fade = 1.0f - smoothstep(e.duration, e.duration + 4.0f, t);
                drawFlames(light, m, e, pos, t, (0.4f + 0.8f * f) * fade, 1.0f + 1.4f * f);
            } else if (e.kind == TravelFx.FLOO_ARRIVE) {
                float f = t / FLOO_ARRIVE_TICKS;
                float burst = 1.0f - smoothstep(0.2f, 1.0f, f);
                drawFlames(light, m, e, pos, t, 1.3f * burst, 2.6f * (1.0f - 0.5f * f));
                ring(light, m, pos.add(0.0, 0.1, 0.0), new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0),
                        2.4f * smoothstep(0.0f, 0.5f, f), 0.2f, 0.6f * (1.0f - f), EMERALD_R, EMERALD_G, EMERALD_B, 40);
            }
        }
        buffer.endBatch(ModRenderTypes.AVADA_LIGHT);
    }

    /** The traveller's own effects in first person are on the screen, not round their feet. */
    private static boolean ownFirstPerson(Minecraft minecraft, Event e) {
        return minecraft.player != null && e.entityId == minecraft.player.getId()
                && minecraft.options.getCameraType().isFirstPerson()
                && (e.kind == TravelFx.FLOO_DEPART || e.kind == TravelFx.FLOO_ARRIVE);
    }

    /** Follow the traveller while they are still there (focusing, the flames rising), else stay where it began. */
    private static Vec3 where(Minecraft minecraft, Event e, float partial) {
        if (e.kind == TravelFx.APPARATE_FOCUS || e.kind == TravelFx.FLOO_DEPART) {
            Entity entity = minecraft.level.getEntity(e.entityId);
            if (entity != null && entity.position().distanceToSqr(e.pos) < 16.0) {
                return entity.getPosition(partial);
            }
        }
        return e.pos;
    }

    /** Dark violet streaks spiralling into the caster, faster and thicker as they focus. */
    private static void drawVortex(VertexConsumer out, Matrix4f m, Event e, Vec3 feet, float t) {
        float f = Math.min(1.0f, t / Math.max(1, e.duration));
        float fadeIn = smoothstep(0.0f, 6.0f, t);
        Vec3 centre = feet.add(0.0, 1.0, 0.0);
        int count = 5 + Math.round(9.0f * f);
        Vec3[] path = new Vec3[10];
        float[] alpha = new float[10];
        float[] width = new float[10];
        for (int k = 0; k < count; k++) {
            double run = t * (0.06 + 0.1 * f) + k * 0.618;
            int cycle = (int) Math.floor(run);
            float u = (float) (run - cycle);
            int seed = e.seed + k * 7919 + cycle * 104729;
            double startAngle = hash(seed, 1, 0) * Math.PI * 2.0;
            double height = (hash(seed, 2, 0) - 0.5) * 1.8;
            for (int i = 0; i < 10; i++) {
                float tail = i / 9.0f;
                float p = Mth.clamp(u - tail * 0.3f, 0.0f, 1.0f);
                double r = 2.2 * Math.pow(1.0 - p, 1.4);
                double angle = startAngle + p * 4.0;
                path[i] = centre.add(Math.cos(angle) * r, height * (1.0 - p), Math.sin(angle) * r);
                float head = 1.0f - tail;
                alpha[i] = fadeIn * head * head * smoothstep(0.0f, 0.15f, u) * (1.0f - smoothstep(0.9f, 1.0f, u));
                width[i] = 0.015f + 0.04f * head;
            }
            ribbon(out, m, path, width, alpha, 3.0f, 0.25f, VIOLET_R, VIOLET_G, VIOLET_B);
            ribbon(out, m, path, width, alpha, 1.0f, 0.9f, PALE_R, PALE_G, PALE_B);
        }
    }

    /** Arrival: a flash, a sphere of light snapping out, a ring along the ground and sparks of violet. */
    private static void drawCrack(VertexConsumer out, Matrix4f m, Event e, Vec3 feet, float t) {
        float f = t / APPARATE_ARRIVE_TICKS;
        Vec3 c = feet.add(0.0, 1.0, 0.0);
        float flash = 1.0f - smoothstep(0.0f, 5.0f, t);
        billboard(out, m, c, 0.5f + 2.6f * smoothstep(0.0f, 2.0f, t), flash, PALE_R, PALE_G, PALE_B, true);
        billboard(out, m, c, 1.6f, 0.4f * (1.0f - f) * (1.0f - f), VIOLET_R, VIOLET_G, VIOLET_B, false);
        float shell = Math.min(1.0f, t / 6.0f);
        if (shell < 1.0f) {
            Vec3 toCamera = c.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : c.scale(-1.0).normalize();
            Vec3[] facing = frame(toCamera);
            ring(out, m, c, facing[0], facing[1], 2.2f * (1.0f - (1.0f - shell) * (1.0f - shell)), 0.16f,
                    0.6f * (1.0f - shell), PALE_R, PALE_G, PALE_B, 40);
        }
        ring(out, m, feet.add(0.0, 0.08, 0.0), new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0),
                3.0f * smoothstep(0.0f, 0.6f, f), 0.25f, 0.5f * (1.0f - f), VIOLET_R, VIOLET_G, VIOLET_B, 48);
        for (int k = 0; k < 14; k++) {
            int seed = e.seed + k * 29;
            Vec3 v = randomDirection(seed).scale(0.25 + 0.15 * hash(seed, 1, 1));
            Vec3 p1 = c.add(v.scale(t)).add(0.0, -0.01 * t * t, 0.0);
            float before = Math.max(0.0f, t - 1.2f);
            Vec3 p0 = c.add(v.scale(before)).add(0.0, -0.01 * before * before, 0.0);
            float al = Math.max(0.0f, 1.0f - t / 10.0f);
            ribbon(out, m, new Vec3[]{p0, p1}, new float[]{0.008f, 0.02f}, new float[]{al * 0.3f, al}, 1.0f, 1.0f,
                    PALE_R, PALE_G, PALE_B);
        }
    }

    /** Tongues of emerald flame licking up round a body, wavering, with cinders rising off them. */
    private static void drawFlames(VertexConsumer out, Matrix4f m, Event e, Vec3 feet, float t, float strength,
                                   float height) {
        if (strength <= 0.01f) {
            return;
        }
        int tongues = 12;
        Vec3[] path = new Vec3[8];
        float[] alpha = new float[8];
        float[] width = new float[8];
        for (int k = 0; k < tongues; k++) {
            double angle = k * Math.PI * 2.0 / tongues + hash(e.seed, k, 3) * 0.5;
            double radius = 0.45 + 0.2 * hash(e.seed, k, 4);
            float flick = 0.75f + 0.25f * Mth.sin(t * (1.7f + hash(e.seed, k, 5)) + k);
            float h = height * flick * (0.7f + 0.3f * hash(e.seed, k, 6));
            for (int i = 0; i < 8; i++) {
                float u = i / 7.0f;
                double sway = 0.12 * Math.sin(t * 0.9 + u * 4.0 + k) * u;
                double r = radius * (1.0 - 0.6 * u);
                path[i] = feet.add(Math.cos(angle) * r + sway, h * u, Math.sin(angle) * r + sway * 0.6);
                alpha[i] = strength * (1.0f - u) * smoothstep(0.0f, 0.15f, u + 0.05f);
                width[i] = 0.16f * (1.0f - 0.7f * u);
            }
            ribbon(out, m, path, width, alpha, 1.6f, 0.35f, EMERALD_R, EMERALD_G, EMERALD_B);
            ribbon(out, m, path, width, alpha, 0.5f, 0.9f, EMBER_R, EMBER_G, EMBER_B);
        }
        billboard(out, m, feet.add(0.0, height * 0.4, 0.0), height * 0.7f, 0.25f * strength,
                EMERALD_R, EMERALD_G, EMERALD_B, false);
        for (int k = 0; k < 10; k++) {
            float life = (t + k * 2.3f) % 14.0f;
            Vec3 c = feet.add(randomDirection(e.seed + k * 13 + (int) ((t + k * 2.3f) / 14.0f) * 101).scale(0.4))
                    .add(0.0, 0.1 * life, 0.0);
            billboard(out, m, c, 0.05f, strength * (1.0f - life / 14.0f), EMBER_R, EMBER_G, EMBER_B, true, 2, 8);
        }
    }

    // ---- the traveller's own view ------------------------------------------------------------------------------

    /** HUD: the Apparition tunnel and the Floo flames, for the one travelling. From the HUD mixin. */
    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.options.hideGui
                || tunnelStart < 0.0 && flooStart < 0.0 && flooArrive < 0.0) {
            return;
        }
        float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        double now = minecraft.level.getGameTime() + partial;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        if (tunnelStart >= 0.0) {
            float t = (float) (now - tunnelStart);
            if (t > TUNNEL_TICKS) {
                tunnelStart = -1.0;
            } else {
                // Slammed shut, then opening from the middle with a violet-white flash.
                float black = 1.0f - smoothstep(1.5f, 10.0f, t);
                float open = smoothstep(1.0f, 11.0f, t);
                graphics.fill(0, 0, width, height, argb(black * (1.0f - open * 0.6f), 0x000000));
                edges(graphics, width, height, 0.5f * (1.0f - open) + 0.08f, black, 0x050010);
                float flash = Math.max(0.0f, 1.0f - Math.abs(t - 2.5f) / 2.0f);
                graphics.fill(0, 0, width, height, argb(0.55f * flash, 0xEADCFF));
            }
        }
        if (flooStart >= 0.0) {
            float t = (float) (now - flooStart);
            float climb = smoothstep(0.0f, flooDuration * 0.6f, t);
            float spin = smoothstep(flooDuration * 0.55f, flooDuration, t);
            drawScreenFlames(graphics, width, height, t, climb);
            if (spin > 0.0f) {
                drawFlooTunnel(graphics, width, height, t, spin);
            }
        }
        if (flooArrive >= 0.0) {
            float t = (float) (now - flooArrive);
            if (t > FLOO_SINK_TICKS) {
                flooArrive = -1.0;
            } else {
                float sink = 1.0f - smoothstep(0.0f, FLOO_SINK_TICKS, t);
                drawFlooTunnel(graphics, width, height, t + 20.0f, Math.max(0.0f, sink * 2.0f - 1.0f));
                drawScreenFlames(graphics, width, height, t + 20.0f, sink);
                graphics.fill(0, 0, width, height, argb(0.35f * Math.max(0.0f, 1.0f - t / 4.0f), 0xCCFFD8));
            }
        }
    }

    /** Green flames climbing the screen from the bottom edge, flickering, as high as {@code climb} says. */
    private static void drawScreenFlames(GuiGraphics graphics, int width, int height, float t, float climb) {
        if (climb <= 0.003f) {
            return;
        }
        int columns = 28;
        int column = Math.max(1, width / columns + 1);
        for (int i = 0; i < columns; i++) {
            float flick = 0.7f + 0.3f * Mth.sin(t * (1.3f + (i % 5) * 0.21f) + i * 1.7f);
            int top = height - Math.round(height * 1.15f * climb * flick * (0.75f + 0.25f * ((i * 37) % 11) / 10.0f));
            int x0 = i * column;
            graphics.fillGradient(x0, Math.max(0, top), x0 + column, height, argb(0.0f, 0x30FF70),
                    argb(0.85f * Math.min(1.0f, climb * 1.4f), 0x20C850));
        }
        graphics.fill(0, 0, width, height, argb(0.35f * climb, 0x0A5A28));
    }

    /** The spin through the grates: green streaks wheeling round the middle of the view. */
    private static void drawFlooTunnel(GuiGraphics graphics, int width, int height, float t, float strength) {
        if (strength <= 0.003f) {
            return;
        }
        graphics.fill(0, 0, width, height, argb(0.55f * strength, 0x052812));
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(width * 0.5f, height * 0.5f, 0.0f);
        float reach = (float) Math.hypot(width, height) * 0.55f;
        for (int k = 0; k < 36; k++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(k * 10.0f + t * 22.0f));
            float near = 12.0f + ((k * 53) % 40);
            float length = reach * (0.35f + 0.25f * ((k * 29) % 7) / 6.0f);
            int thickness = 1 + (k % 3);
            graphics.fillGradient((int) near, -thickness, (int) (near + length), thickness,
                    argb(0.0f, 0x7CFFA0), argb(0.7f * strength, 0x7CFFA0));
            pose.popPose();
        }
        pose.popPose();
    }

    private static void edges(GuiGraphics graphics, int width, int height, float depth, float alpha, int rgb) {
        int band = Math.max(1, Math.round(Math.min(width, height) * depth));
        graphics.fillGradient(0, 0, width, band, argb(alpha, rgb), argb(0.0f, rgb));
        graphics.fillGradient(0, height - band, width, height, argb(0.0f, rgb), argb(alpha, rgb));
        for (int i = 0; i < 16; i++) {
            float f = 1.0f - i / 16.0f;
            int x0 = band * i / 16;
            int x1 = band * (i + 1) / 16;
            graphics.fill(x0, 0, x1, height, argb(alpha * f * f, rgb));
            graphics.fill(width - x1, 0, width - x0, height, argb(alpha * f * f, rgb));
        }
    }

    private static int argb(float alpha, int rgb) {
        return (Mth.clamp(Math.round(alpha * 255.0f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** The camera unwinds out of the twist on landing, and turns as the Floo flames take you. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || tunnelStart < 0.0 && flooStart < 0.0) {
            return;
        }
        double now = minecraft.level.getGameTime() + event.getPartialTick();
        if (tunnelStart >= 0.0) {
            float t = (float) (now - tunnelStart);
            float unwind = 1.0f - smoothstep(0.0f, 10.0f, t);
            event.setRoll(event.getRoll() - 120.0f * unwind * unwind);
        }
        if (flooStart >= 0.0) {
            float t = (float) (now - flooStart);
            float spin = smoothstep(flooDuration * 0.5f, flooDuration, t);
            event.setRoll(event.getRoll() + 25.0f * spin * Mth.sin(t * 0.5f));
        }
    }

    /** Squeezed narrow and flung wide on landing; pulled in as the flames take you. */
    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || event.getPlayer() != minecraft.player || tunnelStart < 0.0 && flooStart < 0.0) {
            return;
        }
        double now = minecraft.level.getGameTime();
        float fov = 1.0f;
        if (tunnelStart >= 0.0) {
            float t = (float) (now - tunnelStart);
            fov *= t < 2.0f ? 0.4f : 1.0f + 0.3f * (1.0f - smoothstep(2.0f, 12.0f, t));
        }
        if (flooStart >= 0.0) {
            float t = (float) (now - flooStart);
            fov *= 1.0f - 0.25f * smoothstep(0.0f, flooDuration, t);
        }
        event.setNewFovModifier(event.getNewFovModifier() * fov);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        EVENTS.clear();
        tunnelStart = -1.0;
        flooStart = -1.0;
        flooArrive = -1.0;
    }
}
