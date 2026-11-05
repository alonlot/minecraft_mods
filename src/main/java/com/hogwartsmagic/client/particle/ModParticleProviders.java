package com.hogwartsmagic.client.particle;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * How the mod's own particles look and move. Client only.
 *
 * <p>Velocities arrive from {@code ServerLevel.sendParticles}: with a count above zero the client
 * gives every particle a gaussian velocity scaled by the packet's speed, so each class below treats
 * the incoming (xd, yd, zd) as "how hard it was thrown" and adds its own physics on top.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModParticleProviders {

    private ModParticleProviders() {
    }

    @SubscribeEvent
    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.WILLOW_LEAF.get(), sprites -> provider(sprites, Leaf::new));
        event.registerSpriteSet(ModParticles.BARK_CHIP.get(), sprites -> provider(sprites, Chip::new));
        event.registerSpriteSet(ModParticles.ROOT_DUST.get(), sprites -> provider(sprites, Dust::new));
        event.registerSpriteSet(ModParticles.WILLOW_GLINT.get(), sprites -> provider(sprites, Glint::new));
    }

    @FunctionalInterface
    private interface Factory {
        Particle make(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites);
    }

    private static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites, Factory f) {
        return (type, level, x, y, z, dx, dy, dz) -> f.make(level, x, y, z, dx, dy, dz, sprites);
    }

    /** A willow leaf: drifts down slowly with a side-to-side flutter, spinning, and lies where it
     *  lands for a moment before fading. Cutout, so it reads crisp against the sky. */
    static final class Leaf extends TextureSheetParticle {
        private final float flutterPhase;
        private final float flutterRate;
        private final float spin;

        Leaf(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z);
            pickSprite(sprites);
            this.xd = dx + (this.random.nextDouble() - 0.5D) * 0.04D;
            this.yd = dy + 0.02D + this.random.nextDouble() * 0.03D;
            this.zd = dz + (this.random.nextDouble() - 0.5D) * 0.04D;
            this.gravity = 0.035F;
            this.friction = 0.93F;
            this.lifetime = 55 + this.random.nextInt(35);
            this.quadSize = 0.11F + this.random.nextFloat() * 0.05F;
            this.flutterPhase = this.random.nextFloat() * Mth.TWO_PI;
            this.flutterRate = 0.18F + this.random.nextFloat() * 0.12F;
            this.spin = (this.random.nextFloat() - 0.5F) * 0.25F;
            this.roll = this.random.nextFloat() * Mth.TWO_PI;
            this.oRoll = this.roll;
            this.hasPhysics = true;
            float tint = 0.85F + this.random.nextFloat() * 0.15F;
            setColor(tint, tint, tint);
        }

        @Override
        public void tick() {
            super.tick();
            if (this.removed) return;
            this.oRoll = this.roll;
            if (!this.onGround) {
                float f = Mth.sin(this.age * this.flutterRate + this.flutterPhase);
                // a falling leaf sideslips back and forth and rocks as it goes
                this.xd += f * 0.0035D;
                this.zd += Mth.cos(this.age * this.flutterRate * 0.8F + this.flutterPhase) * 0.0035D;
                if (this.yd < -0.06D) this.yd = -0.06D;    // drag: leaves never plummet
                this.roll += this.spin + f * 0.06F;
            } else {
                this.xd *= 0.5D;
                this.zd *= 0.5D;
            }
            int left = this.lifetime - this.age;
            if (left < 12) this.alpha = left / 12.0F;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return this.alpha < 1.0F ? ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT
                    : ParticleRenderType.PARTICLE_SHEET_OPAQUE;
        }
    }

    /** A bark splinter: thrown hard, heavy, spinning fast; it bounces once off the ground and dies. */
    static final class Chip extends TextureSheetParticle {
        private float spin;
        private boolean bounced;

        Chip(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z);
            pickSprite(sprites);
            this.xd = dx * 2.2D + (this.random.nextDouble() - 0.5D) * 0.15D;
            this.yd = Math.abs(dy) * 2.0D + 0.12D + this.random.nextDouble() * 0.12D;
            this.zd = dz * 2.2D + (this.random.nextDouble() - 0.5D) * 0.15D;
            this.gravity = 0.9F;
            this.friction = 0.97F;
            this.lifetime = 24 + this.random.nextInt(16);
            this.quadSize = 0.07F + this.random.nextFloat() * 0.05F;
            this.spin = (this.random.nextFloat() - 0.5F) * 0.9F;
            this.roll = this.random.nextFloat() * Mth.TWO_PI;
            this.oRoll = this.roll;
            this.hasPhysics = true;
        }

        @Override
        public void tick() {
            double before = this.yd;
            super.tick();
            if (this.removed) return;
            this.oRoll = this.roll;
            this.roll += this.spin;
            if (this.onGround && !this.bounced && before < -0.08D) {
                this.bounced = true;
                this.yd = -before * 0.35D;
                this.xd *= 0.6D;
                this.zd *= 0.6D;
                this.spin *= 0.5F;
            } else if (this.onGround) {
                this.spin *= 0.6F;
            }
            int left = this.lifetime - this.age;
            if (left < 6) this.alpha = left / 6.0F;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return this.alpha < 1.0F ? ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT
                    : ParticleRenderType.PARTICLE_SHEET_OPAQUE;
        }
    }

    /** Thrown earth: a soft brown puff that billows out and up, swells, and thins away. The sprite
     *  runs from a dense clod to a sparse haze over the particle's life. */
    static final class Dust extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final float startSize;

        Dust(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z);
            this.sprites = sprites;
            this.xd = dx * 1.4D + (this.random.nextDouble() - 0.5D) * 0.03D;
            this.yd = Math.abs(dy) * 0.8D + 0.015D + this.random.nextDouble() * 0.02D;
            this.zd = dz * 1.4D + (this.random.nextDouble() - 0.5D) * 0.03D;
            this.gravity = -0.004F;               // a little lift: warm, disturbed air
            this.friction = 0.88F;
            this.lifetime = 30 + this.random.nextInt(25);
            this.startSize = 0.32F + this.random.nextFloat() * 0.25F;
            this.quadSize = this.startSize;
            this.roll = this.random.nextFloat() * Mth.TWO_PI;
            this.oRoll = this.roll;
            this.hasPhysics = false;
            float shade = 0.8F + this.random.nextFloat() * 0.2F;
            setColor(shade, shade * 0.97F, shade * 0.92F);
            this.alpha = 0.85F;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (this.removed) return;
            setSpriteFromAge(this.sprites);
            float t = this.age / (float) this.lifetime;
            this.quadSize = this.startSize * (1.0F + t * 1.6F);
            this.alpha = 0.85F * (1.0F - t * t);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    /** The slam's warning: a sickly green-gold glint that rises a little off the ground, pulses and
     *  goes out. Full-bright, so it reads at dusk and under the canopy's shadow. */
    static final class Glint extends TextureSheetParticle {
        private final SpriteSet sprites;

        Glint(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z);
            this.sprites = sprites;
            this.xd = dx * 0.2D;
            this.yd = 0.03D + this.random.nextDouble() * 0.03D;
            this.zd = dz * 0.2D;
            this.gravity = 0.0F;
            this.friction = 0.9F;
            this.lifetime = 14 + this.random.nextInt(8);
            this.quadSize = 0.14F + this.random.nextFloat() * 0.06F;
            this.hasPhysics = false;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (this.removed) return;
            setSpriteFromAge(this.sprites);
            float t = this.age / (float) this.lifetime;
            this.alpha = Math.min(1.0F, (1.0F - t) * 1.6F);
        }

        @Override
        protected int getLightColor(float partialTick) {
            return 0xF000F0;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }
}
