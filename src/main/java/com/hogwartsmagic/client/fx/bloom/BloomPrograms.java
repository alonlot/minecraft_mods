package com.hogwartsmagic.client.fx.bloom;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.IOException;

/**
 * The bloom's four full-screen programs ({@code assets/hogwartsmagic/shaders/program/bloom_*.json}): the
 * dual-filter downsample and upsample, the composite onto the frame and the heat haze. Each pass draws one quad
 * into a framebuffer of ours or the main target's; the blend is set by hand after the program is applied (vanilla's
 * BlendMode caches what it last set and can skip a needed change).
 */
final class BloomPrograms implements AutoCloseable {
    static final int OPAQUE = 0;
    /** dst + src: the upsample adding a wider level onto a tighter one. */
    static final int ADD = 1;
    /** dst + src * (1 - dst): the screen blend the glow is laid over the frame with, so it never clips harshly. */
    static final int SCREEN = 2;

    final EffectInstance down;
    final EffectInstance up;
    final EffectInstance composite;
    final EffectInstance haze;

    private BloomPrograms(EffectInstance down, EffectInstance up, EffectInstance composite, EffectInstance haze) {
        this.down = down;
        this.up = up;
        this.composite = composite;
        this.haze = haze;
    }

    static BloomPrograms load(ResourceProvider resources) throws IOException {
        EffectInstance down = null;
        EffectInstance up = null;
        EffectInstance composite = null;
        try {
            down = new EffectInstance(resources, "hogwartsmagic:bloom_down");
            up = new EffectInstance(resources, "hogwartsmagic:bloom_up");
            composite = new EffectInstance(resources, "hogwartsmagic:bloom_composite");
            EffectInstance haze = new EffectInstance(resources, "hogwartsmagic:heat_haze");
            BloomPrograms programs = new BloomPrograms(down, up, composite, haze);
            // A program that fails to link only logs a warning; drawing with it would leave garbage in the chain.
            for (EffectInstance effect : new EffectInstance[]{down, up, composite, haze}) {
                if (GlStateManager.glGetProgrami(effect.getId(), GL20.GL_LINK_STATUS) == 0) {
                    closeQuietly(haze); // the catch below closes the other three
                    throw new IOException("bloom program " + effect.getName() + " did not link");
                }
            }
            return programs;
        } catch (IOException | RuntimeException e) {
            closeQuietly(down);
            closeQuietly(up);
            closeQuietly(composite);
            throw e;
        }
    }

    /** Draw {@code effect} over the whole of framebuffer {@code fbo} ({@code w} x {@code h}). */
    static void pass(EffectInstance effect, int fbo, int w, int h, int blend) {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        RenderSystem.viewport(0, 0, w, h);
        effect.apply();
        if (blend == OPAQUE) {
            RenderSystem.disableBlend();
        } else {
            RenderSystem.enableBlend();
            if (blend == ADD) {
                RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
            } else {
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR, GlStateManager.SourceFactor.ZERO,
                        GlStateManager.DestFactor.ONE);
            }
        }
        BufferBuilder quad = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        quad.addVertex(0.0f, 0.0f, 0.0f);
        quad.addVertex(1.0f, 0.0f, 0.0f);
        quad.addVertex(1.0f, 1.0f, 0.0f);
        quad.addVertex(0.0f, 1.0f, 0.0f);
        BufferUploader.draw(quad.buildOrThrow());
        effect.clear();
    }

    @Override
    public void close() {
        closeQuietly(down);
        closeQuietly(up);
        closeQuietly(composite);
        closeQuietly(haze);
    }

    private static void closeQuietly(EffectInstance effect) {
        if (effect == null) {
            return;
        }
        try {
            effect.close();
        } catch (RuntimeException ignored) {
            // a half-built program may not close cleanly
        }
    }
}
