package com.hogwartsmagic.client.fx;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.fx.FxConfig;
import com.hogwartsmagic.fx.FxKinds;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The view shaking under something big: a Giant's fist, a Bombarda blast, a dragon landing, a willow limb slamming
 * down. Every source is a jolt at a place (or on the camera itself), with a strength, a reach and a length; what the
 * camera feels is each source's strength, fading with distance and with time, added up the way energies add (so two
 * thuds feel a little stronger than one, never twice as wild) and capped.
 *
 * <p>The shake is smooth noise, not random per frame: each source has its own phases, so overlapping sources don't
 * beat in step, and it is driven by level time so it slows down with the world in slow motion. A sharp attack (the
 * first tick or two) then an eased decay. Scaled by the player's {@link FxConfig#CAMERA_SHAKE}.
 *
 * <p>Low priority on ComputeCameraAngles, so it adds to whatever a cinematic camera (the Pensieve's) has already set.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class CameraShake {

    /** No more than this many sources are remembered; the weakest go first. */
    private static final int MAX_SOURCES = 24;
    /** The combined strength never goes past this, however much lands at once. */
    private static final float MAX_STRENGTH = 4.0f;
    /** Degrees of pitch/yaw per unit of strength at the peak of the noise. */
    private static final float DEGREES_PER_STRENGTH = 0.7f;

    private static final List<Shake> SHAKES = new ArrayList<>();
    private static long seedCounter;

    private CameraShake() {
    }

    private record Shake(Vec3 at, float strength, float radius, double start, float duration, float frequency,
                         float phase) {
        /** This source's strength as felt from {@code camera} at {@code now}, 0 when gone or out of reach. */
        float felt(Vec3 camera, double now) {
            float age = (float) (now - start);
            if (age < 0.0f || age >= duration) {
                return 0.0f;
            }
            float near = 1.0f;
            if (at != null) {
                double distance = at.distanceTo(camera);
                if (distance >= radius) {
                    return 0.0f;
                }
                float f = (float) (1.0 - distance / radius);
                near = f * f;
            }
            float attack = Math.min(1.0f, age / 1.5f + 0.35f);
            float decay = 1.0f - age / duration;
            return strength * near * attack * decay * decay;
        }
    }

    /**
     * A jolt at {@code at}: {@code strength} about 0.3 (a thud) to 3 (the ground splitting), felt out to
     * {@code radius} blocks, gone after {@code durationTicks}. {@code frequency} 1 is a normal rattle, lower rolls,
     * higher buzzes.
     */
    public static void add(Vec3 at, float strength, float radius, float durationTicks, float frequency) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || strength <= 0.0f || durationTicks <= 0.0f) {
            return;
        }
        float scale = (float) FxConfig.get(FxConfig.CAMERA_SHAKE);
        if (scale <= 0.0f) {
            return;
        }
        double now = minecraft.level.getGameTime() + minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        float phase = (float) ((seedCounter++ * 2.399963) % (Math.PI * 2.0));
        SHAKES.add(new Shake(at, strength * scale, Math.max(1.0f, radius), now, durationTicks,
                Mth.clamp(frequency, 0.2f, 4.0f), phase));
        if (SHAKES.size() > MAX_SOURCES) {
            Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
            SHAKES.sort((a, b) -> Float.compare(a.felt(camera, now), b.felt(camera, now)));
            SHAKES.remove(0);
        }
    }

    /** A jolt felt the same wherever the camera is (the player's own body taking a blow). */
    public static void addLocal(float strength, float durationTicks, float frequency) {
        add(null, strength, 1.0f, durationTicks, frequency);
    }

    /** How hard the view is shaking right now, 0 when still (for effects that want to follow it). */
    public static float current() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || SHAKES.isEmpty()) {
            return 0.0f;
        }
        double now = minecraft.level.getGameTime() + minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        float energy = 0.0f;
        for (Shake shake : SHAKES) {
            float f = shake.felt(camera, now);
            energy += f * f;
        }
        return Math.min(MAX_STRENGTH, Mth.sqrt(energy));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (SHAKES.isEmpty() || minecraft.level == null || minecraft.isPaused()) {
            return; // a paused world does not shake
        }
        double now = minecraft.level.getGameTime() + event.getPartialTick();
        Vec3 camera = event.getCamera().getPosition();
        float pitch = 0.0f;
        float yaw = 0.0f;
        float roll = 0.0f;
        float energy = 0.0f;
        // The noise runs on real time (in 50 ms units) so /tick freeze cannot hold a constant tilt; game time only
        // drives the envelope. Wrapped so a long session cannot saturate Mth.sin's table.
        float t = (float) ((System.nanoTime() / 50_000_000.0) % 20000.0);
        for (Shake shake : SHAKES) {
            float f = shake.felt(camera, now);
            if (f <= 0.0f) {
                continue;
            }
            energy += f * f;
            float w = t * shake.frequency;
            float p = shake.phase;
            pitch += f * (Mth.sin(w * 2.9f + p) + 0.5f * Mth.sin(w * 7.1f + p * 1.7f));
            yaw += f * (Mth.sin(w * 3.7f + 1.3f + p * 2.3f) + 0.5f * Mth.sin(w * 6.3f + p));
            roll += f * Mth.sin(w * 4.3f + 2.1f + p * 0.7f);
        }
        if (energy <= 1.0e-6f) {
            return;
        }
        // Each source's own share adds linearly; rescale so the total follows the energy sum, capped.
        float linear = Math.abs(pitch) + Math.abs(yaw) + Math.abs(roll);
        float total = Math.min(MAX_STRENGTH, Mth.sqrt(energy));
        float norm = linear > 1.0e-6f ? Math.min(1.0f, total * 2.5f / linear) : 0.0f;
        event.setPitch(event.getPitch() + pitch * norm * DEGREES_PER_STRENGTH);
        event.setYaw(event.getYaw() + yaw * norm * DEGREES_PER_STRENGTH);
        event.setRoll(event.getRoll() + roll * norm * DEGREES_PER_STRENGTH * 0.6f);
    }

    /** Spent sources are dropped once a tick, not every frame. */
    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || SHAKES.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            SHAKES.clear();
            return;
        }
        double now = minecraft.level.getGameTime();
        SHAKES.removeIf(shake -> now - shake.start > shake.duration + 1.0);
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        SHAKES.clear();
    }

    @Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            FxClient.register(FxKinds.SHAKE, e -> add(e.pos(), e.param(0, 1.0f), e.param(1, 16.0f),
                    e.param(2, 12.0f), e.param(3, 1.0f)));
        }
    }
}
