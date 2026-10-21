package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.pensieve.scene.SkyDome;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.event.ViewportEvent;
import org.joml.Vector3f;

import static com.hogwartsmagic.client.pensieve.PensieveTimeline.*;

/**
 * The shadow void the memory floats in: black clear and fog colour, a fog sphere whose radius is the visible
 * memory (growing out of the dark in BUILD, fraying at the edge in PLAY, collapsing in EXIT; a scene's
 * {@code fog_far} pushes it out to a soft haze instead), no sky, clouds or
 * weather ({@code PensieveLevelRendererMixin}), and the dark smoke and fragments at the edge.
 */
public final class PensieveVoid {
    private static final DustParticleOptions FRAGMENT = new DustParticleOptions(new Vector3f(0.05f, 0.05f, 0.07f), 1.4f);
    private static final RandomSource RANDOM = RandomSource.create();
    private static final int BUILD_SAMPLES = 40;
    private static final int FRAGMENTS_PER_TICK = 12;

    private PensieveVoid() {
    }

    /** True while the void (not the overworld around the basin) is what the camera shows. */
    public static boolean active() {
        int p = PensieveClientState.phase();
        return p == PensieveClientState.FALL || PensieveClientState.inMemory()
                || (p == PensieveClientState.EXIT && PensieveClientState.returnTime(0) < 0)
                || (p == PensieveClientState.YANK && PensieveClientState.returnTime(0) < 0);
    }

    /** Sky, clouds and weather are not part of a memory. */
    public static boolean hideSky() {
        return active();
    }

    /** The radius of memory currently visible out of the dark. */
    public static float visibleRadius(float partial) {
        int p = PensieveClientState.phase();
        float r = PensieveClientState.radius();
        if (p == PensieveClientState.FALL) return 0;
        if (p == PensieveClientState.BUILD || p == PensieveClientState.PLAY) {
            return revealFogRadius(PensieveClientState.setReveal(partial), r);
        }
        if (p == PensieveClientState.EXIT) {
            return exitFogRadius(PensieveClientState.phaseTime(partial), r);
        }
        return r;
    }

    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        // A running sky effect (a daytime memory) lifts the black to its horizon colour; none, and it stays black.
        float[] sky = SkyRenderer.fogColour((float) event.getPartialTick());
        if (sky != null) {
            event.setRed(sky[0]);
            event.setGreen(sky[1]);
            event.setBlue(sky[2]);
            return;
        }
        event.setRed(0);
        event.setGreen(0);
        event.setBlue(0);
    }

    /** Scratch for the storm haze: the render thread only, so the per-frame fog allocates nothing. */
    private static final float[] HAZE = new float[2];

    public static void renderFog(ViewportEvent.RenderFog event) {
        float r = visibleRadius((float) event.getPartialTick());
        int far = PensieveClientState.fogFar();
        if (far > 0) {
            // fog_far: a scene with a far backdrop sees out to that distance through a soft haze.
            float[] fog = farFog(r, PensieveClientState.radius(), far, event.getFarPlaneDistance());
            event.setNearPlaneDistance(fog[0]);
            event.setFarPlaneDistance(fog[1]);
        } else {
            event.setNearPlaneDistance(fogStart(r));
            event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), fogEnd(r)));
        }
        // A storm sky draws the haze in: far stands go grey and the curtains of rain read as depth.
        float storm = SkyRenderer.stormShare((float) event.getPartialTick());
        if (storm > 0f) {
            SkyDome.stormFog(event.getNearPlaneDistance(), event.getFarPlaneDistance(), storm, HAZE);
            event.setNearPlaneDistance(HAZE[0]);
            event.setFarPlaneDistance(HAZE[1]);
        }
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }

    /** Called every client tick in BUILD and PLAY. */
    static void tickParticles(Minecraft mc) {
        ClientLevel level = mc.level;
        BlockPos o = PensieveClientState.origin();
        if (level == null || o == null) return;
        float r = visibleRadius(0);
        double cx = o.getX() + 0.5;
        double cz = o.getZ() + 0.5;
        if (PensieveClientState.setReveal(0) < 1) {
            // Shadow smoke where blocks are newly coming out of the dark: random samples in the growing ring.
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int i = 0; i < BUILD_SAMPLES; i++) {
                double a = RANDOM.nextDouble() * Mth.TWO_PI;
                double d = Math.max(0, r - 3 + RANDOM.nextDouble() * 3);
                pos.set(cx + Math.cos(a) * d, o.getY() - 6 + RANDOM.nextInt(20), cz + Math.sin(a) * d);
                if (level.getBlockState(pos).isAir()) continue;
                level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE,
                        pos.getX() + RANDOM.nextDouble(), pos.getY() + 1.0, pos.getZ() + RANDOM.nextDouble(),
                        0, 0.02 + RANDOM.nextDouble() * 0.03, 0);
            }
        } else {
            // The frayed edge: dark fragments drifting off the island into the void.
            for (int i = 0; i < FRAGMENTS_PER_TICK; i++) {
                double a = RANDOM.nextDouble() * Mth.TWO_PI;
                double d = r - 2 + RANDOM.nextDouble() * 4;
                double x = cx + Math.cos(a) * d;
                double z = cz + Math.sin(a) * d;
                double y = o.getY() - 4 + RANDOM.nextDouble() * 16;
                double out = 0.02 + RANDOM.nextDouble() * 0.03;
                if (RANDOM.nextInt(3) == 0) {
                    level.addAlwaysVisibleParticle(ParticleTypes.SMOKE, x, y, z,
                            Math.cos(a) * out, 0.01, Math.sin(a) * out);
                } else {
                    level.addAlwaysVisibleParticle(FRAGMENT, x, y, z, Math.cos(a) * out, 0.005, Math.sin(a) * out);
                }
            }
        }
    }
}
