package com.hogwartsmagic.client.particle;

import com.hogwartsmagic.MagicParticleOptions;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The mod's magic particles as the player sees them (client only): the soft glowing mote, the ember,
 * the stretched spark, the billowing puff and the four point glint. Everything that glows is drawn
 * additively and full-bright, so it reads as light and piles up into a bloom where it crowds; the smoke
 * is an ordinary lit translucent particle.
 *
 * <p>Velocities arrive the way {@code ServerLevel.sendParticles} delivers them: the (xd, yd, zd) given
 * to each constructor is "how hard it was thrown", and each class adds its own drift on top.
 */
final class MagicParticleSet {

    private MagicParticleSet() {
    }

    /** Light-emitting draw mode: depth-tested but not depth-written, source alpha added onto the frame. */
    static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textures) {
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            // the particle engine puts the depth mask and blend switch back after the last batch, and
            // every later vanilla batch sets its own blend function, so nothing else leaks from this
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "HOGWARTSMAGIC_ADDITIVE";
        }
    };

    private static final int FULL_BRIGHT = 0xF000F0;

    // ------------------------------------------------------------------------------------ mote

    /** A glowing dot that drifts on a lazy wobble, swells in, twinkles and fades out. */
    static final class Mote extends TextureSheetParticle {
        private final float baseSize;
        private final float twinkleRate;
        private final float twinklePhase;
        private final float wobblePhase;

        Mote(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
             SpriteSet sprites, MagicParticleOptions o) {
            super(level, x, y, z);
            pickSprite(sprites);
            this.xd = dx;
            this.yd = dy + 0.004D;
            this.zd = dz;
            this.gravity = -0.0015F;               // faintly buoyant
            this.friction = 0.94F;
            this.lifetime = 22 + this.random.nextInt(22);
            this.baseSize = 0.085F * o.scale * (0.8F + this.random.nextFloat() * 0.5F);
            this.quadSize = this.baseSize;
            this.twinkleRate = 0.5F + this.random.nextFloat() * 0.5F;
            this.twinklePhase = this.random.nextFloat() * Mth.TWO_PI;
            this.wobblePhase = this.random.nextFloat() * Mth.TWO_PI;
            this.hasPhysics = false;
            setColor(o.r, o.g, o.b);
        }

        @Override
        public void tick() {
            super.tick();
            if (this.removed) return;
            // a slow sideways meander, so a cloud of motes never moves in lockstep
            this.xd += Mth.sin(this.age * 0.35F + this.wobblePhase) * 0.0016D;
            this.zd += Mth.cos(this.age * 0.31F + this.wobblePhase) * 0.0016D;
        }

        @Override
        public float getQuadSize(float partialTick) {
            float t = (this.age + partialTick) / this.lifetime;
            float swell = Mth.clamp(t * 6.0F, 0.0F, 1.0F);          // pops in
            float fade = Mth.clamp((1.0F - t) * 3.0F, 0.0F, 1.0F); // dwindles out
            float twinkle = 0.78F + 0.32F * Mth.sin((this.age + partialTick) * this.twinkleRate + this.twinklePhase);
            return this.baseSize * swell * fade * twinkle;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return FULL_BRIGHT;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ADDITIVE;
        }
    }

    // ------------------------------------------------------------------------------------ ember

    /** A spark of fire: rises on its own heat, sways, flickers and cools from yellow to deep red. */
    static final class Ember extends TextureSheetParticle {
        private final float baseSize;
        private final float sway;

        Ember(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z);
            pickSprite(sprites);
            this.xd = dx;
            this.yd = dy + 0.02D + this.random.nextDouble() * 0.025D;
            this.zd = dz;
            this.gravity = -0.012F;
            this.friction = 0.95F;
            this.lifetime = 18 + this.random.nextInt(22);
            this.baseSize = 0.07F + this.random.nextFloat() * 0.05F;
            this.quadSize = this.baseSize;
            this.sway = this.random.nextFloat() * Mth.TWO_PI;
            this.hasPhysics = false;
            setColor(1.0F, 0.92F, 0.45F);
        }

        @Override
        public void tick() {
            super.tick();
            if (this.removed) return;
            float t = (float) this.age / this.lifetime;
            // yellow -> orange -> deep red as it cools
            float g = Mth.clamp(0.92F - t * 0.9F, 0.06F, 1.0F);
            float b = Mth.clamp(0.45F - t * 0.7F, 0.0F, 1.0F);
            float r = 1.0F - t * 0.45F;
            setColor(r, g, b);
            this.xd += Mth.sin(this.age * 0.5F + this.sway) * 0.0025D;
            this.zd += Mth.cos(this.age * 0.45F + this.sway) * 0.0025D;
            // flicker: brightness jumps around, fading to nothing at the end
            this.alpha = Mth.clamp((1.0F - t) * 1.6F, 0.0F, 1.0F) * (0.7F + this.random.nextFloat() * 0.3F);
        }

        @Override
        public float getQuadSize(float partialTick) {
            float t = (this.age + partialTick) / this.lifetime;
            return this.baseSize * Mth.clamp(1.25F - t, 0.2F, 1.0F);
        }

        @Override
        protected int getLightColor(float partialTick) {
            return FULL_BRIGHT;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ADDITIVE;
        }
    }

    // ------------------------------------------------------------------------------------ spark

    /** A bright streak stretched along its own velocity: a flung magic spark, or one struck off metal. */
    static final class Spark extends TextureSheetParticle {
        private final float width;

        Spark(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
              SpriteSet sprites, MagicParticleOptions o) {
            super(level, x, y, z);
            pickSprite(sprites);
            // the packet velocity is gentle for most callers; sparks are always flung a bit harder
            this.xd = dx * 1.6D;
            this.yd = dy * 1.6D;
            this.zd = dz * 1.6D;
            if (this.xd * this.xd + this.yd * this.yd + this.zd * this.zd < 1.0E-4D) {
                // thrown with no direction: pick a random one, so it still reads as a streak
                this.xd = (this.random.nextDouble() - 0.5D) * 0.25D;
                this.yd = this.random.nextDouble() * 0.2D;
                this.zd = (this.random.nextDouble() - 0.5D) * 0.25D;
            }
            this.gravity = 0.06F;
            this.friction = 0.9F;
            this.lifetime = 7 + this.random.nextInt(8);
            this.width = 0.035F * o.scale;
            this.hasPhysics = false;
            setColor(o.r, o.g, o.b);
        }

        @Override
        public void render(VertexConsumer buffer, Camera camera, float partialTick) {
            Vec3 cam = camera.getPosition();
            float hx = (float) (Mth.lerp(partialTick, this.xo, this.x) - cam.x);
            float hy = (float) (Mth.lerp(partialTick, this.yo, this.y) - cam.y);
            float hz = (float) (Mth.lerp(partialTick, this.zo, this.z) - cam.z);
            float vx = (float) this.xd;
            float vy = (float) this.yd;
            float vz = (float) this.zd;
            float speed = Mth.sqrt(vx * vx + vy * vy + vz * vz);
            if (speed < 1.0E-4F) return;
            // the streak is as long as the spark is fast, with a floor so a slow one is still a dash
            float len = Mth.clamp(speed * 2.6F, 0.12F, 0.7F) * Math.max(0.6F, this.width / 0.035F * 0.8F);
            float ax = vx / speed;
            float ay = vy / speed;
            float az = vz / speed;
            // the quad's width axis: perpendicular to both the streak and the line of sight
            float sx = ay * hz - az * hy;
            float sy = az * hx - ax * hz;
            float sz = ax * hy - ay * hx;
            float sl = Mth.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1.0E-5F) return;
            float w = this.width / sl;
            sx *= w;
            sy *= w;
            sz *= w;
            float tx = hx - ax * len;
            float ty = hy - ay * len;
            float tz = hz - az * len;
            float u0 = getU0();
            float u1 = getU1();
            float v0 = getV0();
            float v1 = getV1();
            int light = FULL_BRIGHT;
            float t = (this.age + partialTick) / this.lifetime;
            float a = Mth.clamp((1.0F - t) * 1.7F, 0.0F, 1.0F);
            // white-hot at birth, relaxing into its colour
            float hot = Mth.clamp(1.0F - t * 2.5F, 0.0F, 1.0F) * 0.6F;
            float rr = this.rCol + (1.0F - this.rCol) * hot;
            float gg = this.gCol + (1.0F - this.gCol) * hot;
            float bb = this.bCol + (1.0F - this.bCol) * hot;
            buffer.addVertex(tx - sx, ty - sy, tz - sz).setUv(u0, v1).setColor(rr, gg, bb, a).setLight(light);
            buffer.addVertex(tx + sx, ty + sy, tz + sz).setUv(u0, v0).setColor(rr, gg, bb, a).setLight(light);
            buffer.addVertex(hx + sx, hy + sy, hz + sz).setUv(u1, v0).setColor(rr, gg, bb, a).setLight(light);
            buffer.addVertex(hx - sx, hy - sy, hz - sz).setUv(u1, v1).setColor(rr, gg, bb, a).setLight(light);
        }

        @Override
        protected int getLightColor(float partialTick) {
            return FULL_BRIGHT;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ADDITIVE;
        }
    }

    // ------------------------------------------------------------------------------------ smoke

    /** A big soft puff: billows out and up, turns slowly, grows and thins away. Lit like the world. */
    static final class Smoke extends TextureSheetParticle {
        private final float startSize;
        private final float spin;
        private final float peak;

        Smoke(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
              SpriteSet sprites, MagicParticleOptions o) {
            super(level, x, y, z);
            pickSprite(sprites);                   // four painted variants
            this.xd = dx + (this.random.nextDouble() - 0.5D) * 0.01D;
            this.yd = dy + 0.012D + this.random.nextDouble() * 0.01D;
            this.zd = dz + (this.random.nextDouble() - 0.5D) * 0.01D;
            this.gravity = -0.0008F;
            this.friction = 0.93F;
            this.lifetime = 34 + this.random.nextInt(26);
            this.startSize = 0.22F * o.scale * (0.85F + this.random.nextFloat() * 0.35F);
            this.quadSize = this.startSize;
            this.spin = (this.random.nextFloat() - 0.5F) * 0.05F;
            this.roll = this.random.nextFloat() * Mth.TWO_PI;
            this.oRoll = this.roll;
            this.peak = 0.5F + this.random.nextFloat() * 0.15F;
            this.hasPhysics = false;
            float shade = 0.9F + this.random.nextFloat() * 0.1F;
            setColor(o.r * shade, o.g * shade, o.b * shade);
            this.alpha = 0.0F;
        }

        @Override
        public void tick() {
            super.tick();
            if (this.removed) return;
            this.oRoll = this.roll;
            this.roll += this.spin;
            float t = (float) this.age / this.lifetime;
            // fades in over the first tenth, then thins to nothing
            this.alpha = this.peak * Mth.clamp(t * 10.0F, 0.0F, 1.0F) * (1.0F - t) * (1.0F - t * 0.3F);
        }

        @Override
        public float getQuadSize(float partialTick) {
            float t = (this.age + partialTick) / this.lifetime;
            return this.startSize * (1.0F + t * 1.8F);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    // ------------------------------------------------------------------------------------ glint

    /** A four point star catching the light: flares open, hangs, turns a little and is gone. */
    static final class Glint extends TextureSheetParticle {
        private final float baseSize;
        private final float spin;

        Glint(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
              SpriteSet sprites, MagicParticleOptions o) {
            super(level, x, y, z);
            pickSprite(sprites);
            this.xd = dx * 0.3D;
            this.yd = dy * 0.3D + 0.002D;
            this.zd = dz * 0.3D;
            this.gravity = 0.0F;
            this.friction = 0.9F;
            this.lifetime = 10 + this.random.nextInt(14);
            this.baseSize = 0.16F * o.scale * (0.8F + this.random.nextFloat() * 0.45F);
            this.quadSize = this.baseSize;
            this.spin = (this.random.nextFloat() - 0.5F) * 0.1F;
            this.roll = this.random.nextFloat() * 0.8F;
            this.oRoll = this.roll;
            this.hasPhysics = false;
            setColor(o.r, o.g, o.b);
        }

        @Override
        public void tick() {
            super.tick();
            if (this.removed) return;
            this.oRoll = this.roll;
            this.roll += this.spin;
        }

        @Override
        public float getQuadSize(float partialTick) {
            float t = (this.age + partialTick) / this.lifetime;
            // flare open fast, then shrink to a point: the shape of a glint of light
            float s = t < 0.25F ? t / 0.25F : 1.0F - (t - 0.25F) / 0.75F;
            return this.baseSize * Mth.clamp(s, 0.0F, 1.0F);
        }

        @Override
        protected int getLightColor(float partialTick) {
            return FULL_BRIGHT;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ADDITIVE;
        }
    }
}
