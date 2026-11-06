package com.hogwartsmagic.client.fx.bloom;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.BeamStyle;
import com.hogwartsmagic.client.BeamStyles;
import com.hogwartsmagic.spell.SpellType;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * The air shimmers over fire. Fiendfyre and dragon fire mark it themselves (their light's alpha goes into the glow
 * buffer, see {@link GlowRouting#FIRE}); an Incendio or Fiendfyre bolt, which draws with the shared spell light, says
 * where it is through {@link #beam} and {@link #strike}, and each of those points is laid into the same mask as a
 * soft camera-facing disc, depth-tested so a wall in front hides it. {@link Bloom} then bends the finished frame
 * inside the mask with rising, wavering noise, reaching further up than down, the way hot air climbs.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class HeatHaze {
    /** At most this many hot points a frame; the rest go without. */
    private static final int MAX_POINTS = 64;
    private static final int DISC_SEGMENTS = 10;
    /** A bolt marks its path every this many path samples back from the head, at most this many times. */
    private static final int BEAM_STRIDE = 3;
    private static final int BEAM_POINTS = 5;
    /** The disc sits a little above its source: the heat rises off it. */
    private static final float RISE = 0.45f;

    private static final float[] POINTS = new float[MAX_POINTS * 5];
    private static int count;

    private static BeamStyle incendio;
    private static BeamStyle fiendfyre;

    private HeatHaze() {
    }

    /** A hot point this frame, camera-relative, {@code radius} in blocks, {@code strength} 0..1. */
    public static void emit(double x, double y, double z, float radius, float strength) {
        if (count >= MAX_POINTS || strength <= 0.01f || !Bloom.wantsHaze()) {
            return;
        }
        int i = count++ * 5;
        POINTS[i] = (float) x;
        POINTS[i + 1] = (float) y;
        POINTS[i + 2] = (float) z;
        POINTS[i + 3] = radius;
        POINTS[i + 4] = Math.min(1.0f, strength);
    }

    /** A spell bolt in flight: if it is a fire spell, the air along its last stretch shimmers. */
    public static void beam(BeamStyle style, Vec3[] path, float glow) {
        if (!hot(style) || path == null || path.length == 0 || !Bloom.wantsHaze()) {
            return;
        }
        float strength = Mth.clamp(glow, 0.0f, 1.0f) * 0.85f;
        for (int k = 0; k < BEAM_POINTS; k++) {
            int index = path.length - 1 - k * BEAM_STRIDE;
            if (index < 0) {
                break;
            }
            float fade = 1.0f - k / (float) BEAM_POINTS;
            Vec3 p = path[index];
            emit(p.x, p.y, p.z, 0.45f + 0.35f * fade, strength * fade);
        }
    }

    /** A spell landing, {@code age} 0..1 through its strike: a fire spell's leaves a hot spot that cools. */
    public static void strike(BeamStyle style, Vec3 pos, float age) {
        if (age < 0.0f || age > 1.0f || !hot(style)) {
            return;
        }
        float cool = 1.0f - age;
        emit(pos.x, pos.y, pos.z, 0.9f + 0.6f * age, cool * cool);
    }

    private static boolean hot(BeamStyle style) {
        if (incendio == null) {
            incendio = BeamStyles.of(SpellType.INCENDIO);
            fiendfyre = BeamStyles.of(SpellType.FIENDFYRE);
        }
        return style != null && (style == incendio || style == fiendfyre);
    }

    /**
     * The last stage inside the level (the camera rotation is still on the model-view stack, so identity plus
     * camera-relative points, like the Dark Mark): lay this frame's hot points into the haze mask.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        int n = count;
        count = 0;
        if (n == 0 || !Bloom.wantsHaze() || !Bloom.readyCapture()) {
            return;
        }
        Camera camera = event.getCamera();
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        BufferBuilder out = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int p = 0; p < n; p++) {
            int i = p * 5;
            float r = POINTS[i + 3];
            float cx = POINTS[i];
            float cy = POINTS[i + 1] + RISE * r;
            float cz = POINTS[i + 2];
            int alpha = (int) (POINTS[i + 4] * 255.0f);
            for (int s = 0; s < DISC_SEGMENTS; s++) {
                double a0 = s * Math.PI * 2.0 / DISC_SEGMENTS;
                double a1 = (s + 1) * Math.PI * 2.0 / DISC_SEGMENTS;
                float c0 = (float) Math.cos(a0) * r;
                float s0 = (float) Math.sin(a0) * r * 1.35f;
                float c1 = (float) Math.cos(a1) * r;
                float s1 = (float) Math.sin(a1) * r * 1.35f;
                out.addVertex(cx, cy, cz).setColor(255, 255, 255, alpha);
                out.addVertex(cx + left.x() * c0 + up.x() * s0, cy + left.y() * c0 + up.y() * s0,
                        cz + left.z() * c0 + up.z() * s0).setColor(255, 255, 255, 0);
                out.addVertex(cx + left.x() * c1 + up.x() * s1, cy + left.y() * c1 + up.y() * s1,
                        cz + left.z() * c1 + up.z() * s1).setColor(255, 255, 255, 0);
            }
        }
        MeshData mesh = out.build();
        if (mesh != null) {
            Types.SOURCE.draw(mesh);
            Bloom.markHaze();
        }
    }

    /** The hot points' render type; subclassing {@link RenderType} is the only way to reach its shards. */
    private static final class Types extends RenderType {
        private static int previousFbo;

        private Types(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                      boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }

        /** Adds only to the glow buffer's alpha (the haze mask); its colour is left alone. */
        private static final RenderStateShard.TransparencyStateShard ALPHA_ADD =
                new RenderStateShard.TransparencyStateShard("hogwartsmagic_haze_alpha_add", () -> {
                    RenderSystem.enableBlend();
                    RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE,
                            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
                }, () -> {
                    RenderSystem.disableBlend();
                    RenderSystem.defaultBlendFunc();
                });

        /** Into the glow capture, whatever target was bound (Fabulous binds its weather target here). */
        private static final RenderStateShard.OutputStateShard GLOW_TARGET =
                new RenderStateShard.OutputStateShard("hogwartsmagic_glow_target", () -> {
                    previousFbo = GlStateManager.getBoundFramebuffer();
                    Bloom.bindCapture();
                }, () -> GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, previousFbo));

        static final RenderType SOURCE = create("hogwartsmagic_heat_source", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.TRIANGLES, 4096, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                        .setTransparencyState(ALPHA_ADD)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .setOutputState(GLOW_TARGET)
                        .createCompositeState(false));
    }
}
