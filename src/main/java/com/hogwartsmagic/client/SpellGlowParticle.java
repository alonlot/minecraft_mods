package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.vfx.ModParticles;
import com.hogwartsmagic.spell.vfx.SpellVfxOptions;
import com.hogwartsmagic.spell.vfx.SpellVfxOptions.Kind;
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
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The particle every custom effect is drawn with (block chips excepted, see BlockDebrisParticle): a soft sprite of light (or smoke) whose behaviour follows
 * its {@link Kind}. Lights are added onto the scene, so overlapping glows bloom towards white; smoke is
 * blended normally so it can be dark. Made two ways: by client code directly ({@link #spawn}), and from
 * {@link SpellVfxOptions} sent by the server or added by common code, through the provider below.
 */
public class SpellGlowParticle extends SingleQuadParticle {

    /** Additive glow sheet. Custom types sort after the vanilla ones, so glows draw over normal particles. */
    public static final ParticleRenderType GLOW_SHEET = new LightSheet(ModRenderTypes.SPELL_GLOW_TEXTURE, "SPELL_GLOW", true);
    public static final ParticleRenderType RING_SHEET = new LightSheet(ModRenderTypes.SPELL_RING_TEXTURE, "SPELL_RING", true);
    /** Soft round sprite with normal blending, for smoke that can be darker than what is behind it. */
    public static final ParticleRenderType SMOKE_SHEET = new LightSheet(ModRenderTypes.SPELL_GLOW_TEXTURE, "SPELL_SMOKE", false);

    private final Kind kind;
    private final float startSize;
    private final float endSize;
    private final float startAlpha;
    private final float baseR;
    private final float baseG;
    private final float baseB;
    private final float wobble;

    private SpellGlowParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                              Kind kind, float r, float g, float b, float alpha, float startSize, float endSize,
                              int lifetime, float gravity, float friction) {
        super(level, x, y, z); // the 3-arg constructor: no random kick added to the velocity
        this.kind = kind;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.rCol = this.baseR = r;
        this.gCol = this.baseG = g;
        this.bCol = this.baseB = b;
        this.startAlpha = alpha;
        this.alpha = alpha;
        this.startSize = startSize;
        this.endSize = endSize;
        this.quadSize = startSize;
        this.lifetime = Math.max(1, lifetime);
        this.gravity = gravity;
        this.friction = friction;
        this.hasPhysics = false;
        this.wobble = this.random.nextFloat() * Mth.TWO_PI;
        this.roll = this.random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
    }

    /**
     * Adds one glow (or ring) to the local particle engine. Safe to call from any client code; does nothing when
     * no world is loaded.
     */
    public static void spawn(double x, double y, double z, double vx, double vy, double vz,
                             float r, float g, float b, float alpha, float startSize, float endSize,
                             int lifetime, float gravity, float friction, boolean ring) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        minecraft.particleEngine.add(new SpellGlowParticle(minecraft.level, x, y, z, vx, vy, vz,
                ring ? Kind.RING : Kind.GLOW, r, g, b, alpha, startSize, endSize, lifetime, gravity, friction));
    }

    /** Builds the particle for a spell effect sent as {@link SpellVfxOptions}. */
    static Particle fromOptions(SpellVfxOptions options, ClientLevel level, double x, double y, double z,
                                double vx, double vy, double vz) {
        Kind kind = options.kindEnum();
        if (kind == Kind.CHUNK) {
            return BlockDebrisParticle.chip(level, x, y, z, vx, vy, vz,
                    net.minecraft.world.level.block.Block.stateById(options.rgb()), options.size(), options.lifetime());
        }
        float r = (options.rgb() >> 16 & 255) / 255.0F;
        float g = (options.rgb() >> 8 & 255) / 255.0F;
        float b = (options.rgb() & 255) / 255.0F;
        float size = options.size();
        int life = Math.max(1, options.lifetime());
        float jitter = 0.8F + level.getRandom().nextFloat() * 0.4F;
        // Vanilla call sites tuned their speeds for vanilla particles (an enchant glyph's "speed" is a pull
        // distance); cap them so a mote never shoots off.
        double cap = speedCap(kind);
        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (speed > cap) {
            double k = cap / speed;
            vx *= k;
            vy *= k;
            vz *= k;
        }
        int lifetime = Math.round(life * jitter);
        SpellGlowParticle particle;
        if (kind == Kind.SPARK) {
            particle = new SpellGlowParticle(level, x, y, z, vx, vy, vz, kind, r, g, b, 1.0F,
                    size * jitter, size * 0.2F, lifetime, 0.35F, 0.9F);
        } else if (kind == Kind.MOTE) {
            particle = new SpellGlowParticle(level, x, y, z, vx, vy + 0.01, vz, kind, r, g, b, 0.85F,
                    size * jitter, size * 0.3F, lifetime, -0.04F, 0.94F);
        } else if (kind == Kind.SMOKE && size < 0.2F) {
            // A small puff (blood, chain links, ash) keeps its place and size so the shape it draws stays clear.
            particle = new SpellGlowParticle(level, x, y, z, vx, vy, vz, kind, r, g, b, 0.85F,
                    size * jitter, size * 1.3F, lifetime, 0.0F, 0.85F);
        } else if (kind == Kind.SMOKE) {
            boolean dark = (r + g + b) < 1.2F;
            particle = new SpellGlowParticle(level, x, y, z, vx, vy + 0.004, vz, kind, r, g, b, dark ? 0.75F : 0.5F,
                    size * jitter, size * 2.4F * jitter, lifetime, -0.02F, 0.92F);
        } else if (kind == Kind.FLAME) {
            particle = new SpellGlowParticle(level, x, y, z, vx, vy, vz, kind, r, g, b, 0.95F,
                    size * jitter, size * 0.3F, lifetime, -0.06F, 0.9F);
        } else if (kind == Kind.RING) {
            particle = new SpellGlowParticle(level, x, y, z, 0, 0, 0, kind, r, g, b, 0.9F,
                    size * 0.3F, size * 4.0F, lifetime, 0.0F, 1.0F);
        } else if (kind == Kind.BLAST) {
            particle = new SpellGlowParticle(level, x, y, z, 0, 0, 0, kind, r, g, b, 1.0F,
                    size * 0.6F, size * 1.6F, lifetime, 0.0F, 1.0F);
        } else {
            boolean flash = size > 0.6F;
            particle = new SpellGlowParticle(level, x, y, z, vx, vy, vz, kind, r, g, b, 0.9F,
                    size * jitter, flash ? size * 1.4F : size * 0.6F, lifetime, 0.0F, 0.9F);
        }
        return particle;
    }

    /**
     * Highest speed a sent effect keeps. Only motes are held back hard: an enchant glyph's "speed" is a pull
     * distance, not a velocity. Fire breath and spark sprays send real velocities, so those stay fast.
     */
    private static double speedCap(Kind kind) {
        if (kind == Kind.MOTE) {
            return 0.08;
        }
        if (kind == Kind.SMOKE) {
            return 0.4;
        }
        return 1.2;
    }

    private float progress(float partialTick) {
        return Mth.clamp((this.age + partialTick) / (float) this.lifetime, 0.0F, 1.0F);
    }

    @Override
    public void tick() {
        if (this.kind == Kind.BLAST && this.age == 0) {
            burst();
        }
        super.tick();
        if (this.kind == Kind.MOTE) {
            // A slow wobble as it rises.
            this.xd += Mth.sin(this.wobble + this.age * 0.35F) * 0.002;
            this.zd += Mth.cos(this.wobble + this.age * 0.35F) * 0.002;
        } else if (this.kind == Kind.SMOKE) {
            this.oRoll = this.roll;
            this.roll += 0.03F;
        }
        if (this.kind == Kind.FLAME) {
            // Cools from its colour towards a deep red as it rises.
            float t = Mth.clamp(this.age / (float) this.lifetime, 0.0F, 1.0F);
            this.rCol = Mth.lerp(t, this.baseR, Math.max(this.baseR * 0.9F, 0.5F));
            this.gCol = Mth.lerp(t, this.baseG, this.baseG * 0.25F);
            this.bCol = Mth.lerp(t, this.baseB, this.baseB * 0.4F);
        }
    }

    /** A blast throws its ring, smoke and sparks once, on its first tick. */
    private void burst() {
        float s = this.startSize / 0.6F; // the size it was sent with
        SpellGlowParticle.spawn(this.x, this.y, this.z, 0, 0, 0, 1.0F, 0.95F, 0.85F, 0.9F, s * 0.3F, s * 3.2F,
                10, 0.0F, 1.0F, true);
        int smoke = Math.round(6 * s);
        for (int i = 0; i < smoke; i++) {
            double vx = (this.random.nextDouble() - 0.5) * 0.12 * s;
            double vy = this.random.nextDouble() * 0.06 * s;
            double vz = (this.random.nextDouble() - 0.5) * 0.12 * s;
            float grey = 0.25F + this.random.nextFloat() * 0.25F;
            Minecraft.getInstance().particleEngine.add(new SpellGlowParticle(this.level,
                    this.x + vx * 4, this.y + vy * 4, this.z + vz * 4, vx, vy, vz, Kind.SMOKE, grey, grey, grey, 0.6F,
                    0.5F * s, 1.4F * s, 30 + this.random.nextInt(20), -0.01F, 0.9F));
        }
        int sparks = Math.round(14 * s);
        for (int i = 0; i < sparks; i++) {
            double vx = (this.random.nextDouble() - 0.5) * 0.6 * s;
            double vy = this.random.nextDouble() * 0.4 * s;
            double vz = (this.random.nextDouble() - 0.5) * 0.6 * s;
            Minecraft.getInstance().particleEngine.add(new SpellGlowParticle(this.level, this.x, this.y, this.z,
                    vx, vy, vz, Kind.SPARK, this.baseR, this.baseG, this.baseB, 1.0F, 0.09F, 0.02F,
                    12 + this.random.nextInt(10), 0.6F, 0.9F));
        }
    }

    @Override
    public float getQuadSize(float partialTick) {
        float t = progress(partialTick);
        float eased = 1.0F - (1.0F - t) * (1.0F - t); // fast start, slow finish
        return Mth.lerp(eased, this.startSize, this.endSize);
    }

    @Override
    public void render(VertexConsumer consumer, Camera camera, float partialTick) {
        // Fade out up close, so an effect around the caster (a wind-up, an aura) never fills their screen.
        net.minecraft.world.phys.Vec3 cam = camera.getPosition();
        double dx = Mth.lerp(partialTick, this.xo, this.x) - cam.x;
        double dy = Mth.lerp(partialTick, this.yo, this.y) - cam.y;
        double dz = Mth.lerp(partialTick, this.zo, this.z) - cam.z;
        float near = Mth.clamp((float) ((Math.sqrt(dx * dx + dy * dy + dz * dz) - 0.6) / 2.0), 0.0F, 1.0F);
        if (near <= 0.0F) {
            return;
        }
        float t = progress(partialTick);
        if (this.kind == Kind.RING) {
            this.alpha = this.startAlpha * (1.0F - t);
        } else if (this.kind == Kind.SMOKE) {
            float in = Mth.clamp(t / 0.15F, 0.0F, 1.0F);
            this.alpha = this.startAlpha * in * (1.0F - t);
        } else if (this.kind == Kind.MOTE) {
            float in = Mth.clamp(t / 0.2F, 0.0F, 1.0F);
            float twinkle = 0.75F + 0.25F * Mth.sin(this.wobble + (this.age + partialTick) * 0.9F);
            this.alpha = this.startAlpha * in * (1.0F - t * t) * twinkle;
        } else {
            this.alpha = this.startAlpha * (1.0F - t * t); // holds its light, then dies quickly
        }
        this.alpha *= near;
        super.render(consumer, camera, partialTick);
    }

    @Override
    protected int getLightColor(float partialTick) {
        if (this.kind == Kind.SMOKE) {
            return super.getLightColor(partialTick); // smoke is lit by the world
        }
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
        if (this.kind == Kind.RING) {
            return RING_SHEET;
        }
        return this.kind == Kind.SMOKE ? SMOKE_SHEET : GLOW_SHEET;
    }

    /** Binds one of the spell sprites with no depth write; additive for light, normal blending for smoke. */
    private static final class LightSheet implements ParticleRenderType {
        private final net.minecraft.resources.ResourceLocation texture;
        private final String name;
        private final boolean additive;

        LightSheet(net.minecraft.resources.ResourceLocation texture, String name, boolean additive) {
            this.texture = texture;
            this.name = name;
            this.additive = additive;
        }

        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, this.texture);
            RenderSystem.enableBlend();
            if (this.additive) {
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            } else {
                RenderSystem.defaultBlendFunc();
            }
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    /** Registers the provider that turns sent spell effects into this particle. */
    @Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Provider implements ParticleProvider<SpellVfxOptions> {
        @Override
        public Particle createParticle(SpellVfxOptions options, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return fromOptions(options, level, x, y, z, vx, vy, vz);
        }

        @SubscribeEvent
        public static void register(RegisterParticleProvidersEvent event) {
            event.registerSpecial(ModParticles.SPELL_VFX.get(), new Provider());
            event.registerSpecial(ModParticles.SPELL_VFX_ALWAYS.get(), new Provider());
        }
    }
}
