package com.hogwartsmagic.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;

/**
 * A client-only light particle for spell casts: a soft glow (or a shockwave ring) that is added onto the scene,
 * so overlapping glows bloom towards white. It is never registered or sent over the network; client code makes
 * it directly through {@link #spawn}. Every spell effect in the cast pipeline (sparkles, flashes, impact
 * bursts, rings) is one of these with different numbers.
 */
public class SpellGlowParticle extends SingleQuadParticle {

    /** Additive glow sheet. Custom types sort after the vanilla ones, so glows draw over normal particles. */
    public static final ParticleRenderType GLOW_SHEET = new LightSheet(ModRenderTypes.SPELL_GLOW_TEXTURE, "SPELL_GLOW");
    public static final ParticleRenderType RING_SHEET = new LightSheet(ModRenderTypes.SPELL_RING_TEXTURE, "SPELL_RING");

    private final float startSize;
    private final float endSize;
    private final float startAlpha;
    private final boolean ring;

    private SpellGlowParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                              float r, float g, float b, float alpha, float startSize, float endSize,
                              int lifetime, float gravity, float friction, boolean ring) {
        super(level, x, y, z); // the 3-arg constructor: no random kick added to the velocity
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.rCol = r;
        this.gCol = g;
        this.bCol = b;
        this.startAlpha = alpha;
        this.alpha = alpha;
        this.startSize = startSize;
        this.endSize = endSize;
        this.quadSize = startSize;
        this.lifetime = Math.max(1, lifetime);
        this.gravity = gravity;
        this.friction = friction;
        this.hasPhysics = false;
        this.ring = ring;
    }

    /**
     * Adds one glow to the local particle engine. Safe to call from any client code; does nothing when no
     * world is loaded or the particle setting is "minimal" (the setting already thins vanilla particles).
     */
    public static void spawn(double x, double y, double z, double vx, double vy, double vz,
                             float r, float g, float b, float alpha, float startSize, float endSize,
                             int lifetime, float gravity, float friction, boolean ring) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        minecraft.particleEngine.add(new SpellGlowParticle(minecraft.level, x, y, z, vx, vy, vz,
                r, g, b, alpha, startSize, endSize, lifetime, gravity, friction, ring));
    }

    private float progress(float partialTick) {
        return Mth.clamp((this.age + partialTick) / (float) this.lifetime, 0.0F, 1.0F);
    }

    @Override
    public float getQuadSize(float partialTick) {
        float t = progress(partialTick);
        float eased = 1.0F - (1.0F - t) * (1.0F - t); // fast start, slow finish
        return Mth.lerp(eased, this.startSize, this.endSize);
    }

    @Override
    public void render(VertexConsumer consumer, Camera camera, float partialTick) {
        float t = progress(partialTick);
        // Rings fade linearly as they grow; glows hold their light, then die quickly.
        this.alpha = this.ring ? this.startAlpha * (1.0F - t) : this.startAlpha * (1.0F - t * t);
        super.render(consumer, camera, partialTick);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0; // full bright: it is a light source
    }

    @Override
    protected float getU0() {
        return 0.0F;
    }

    @Override
    protected float getU1() {
        return 1.0F;
    }

    @Override
    protected float getV0() {
        return 0.0F;
    }

    @Override
    protected float getV1() {
        return 1.0F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return this.ring ? RING_SHEET : GLOW_SHEET;
    }

    /** Binds one of the spell light sprites with additive blending and no depth write. */
    private static final class LightSheet implements ParticleRenderType {
        private final net.minecraft.resources.ResourceLocation texture;
        private final String name;

        LightSheet(net.minecraft.resources.ResourceLocation texture, String name) {
            this.texture = texture;
            this.name = name;
        }

        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, this.texture);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return this.name;
        }
    }
}
