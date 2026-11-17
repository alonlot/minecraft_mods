package com.hogwartsmagic.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A chunk of a broken block: a small textured cube that tumbles out of a blast, bounces and lies on the
 * ground as rubble for a while. Reparo can call it home ({@link #flyHome}): it lifts off, arcs back into the
 * block it came from with a cyan glow trail, and vanishes as the block reappears. Client-only and visual;
 * the real block and its drops are the server's.
 */
public class BlockDebrisParticle extends Particle {

    /** Reparo's light colour for the trail and the arrival flash. */
    static final float[] REPARO_GLOW = {0.55F, 0.95F, 0.84F};

    final BlockState state;
    final BlockPos origin;
    private final float size;
    private final float u0;
    private final float u1;
    private final float v0;
    private final float v1;
    private final Vector3f spinAxis;
    private float angle;
    private float angleO;
    private float spin;

    private boolean returning;
    private Vec3 returnFrom;
    private Vec3 returnControl;
    private Vec3 returnTo;
    private int returnTicks;
    private int returnAge;

    BlockDebrisParticle(ClientLevel level, double x, double y, double z, BlockState state, BlockPos origin, float size) {
        super(level, x, y, z);
        this.state = state;
        this.origin = origin.immutable();
        this.size = size;
        TextureAtlasSprite sprite = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(state);
        // A random window of the block texture, like vanilla's break particles, so pieces are not all alike.
        float span = 0.25F + this.random.nextFloat() * 0.25F;
        float uo = this.random.nextFloat() * (1.0F - span);
        float vo = this.random.nextFloat() * (1.0F - span);
        this.u0 = sprite.getU(uo);
        this.u1 = sprite.getU(uo + span);
        this.v0 = sprite.getV(vo);
        this.v1 = sprite.getV(vo + span);
        if (!state.is(Blocks.GRASS_BLOCK)) {
            int tint = Minecraft.getInstance().getBlockColors().getColor(state, level, origin, 0);
            if (tint != -1) {
                this.rCol = (tint >> 16 & 255) / 255.0F;
                this.gCol = (tint >> 8 & 255) / 255.0F;
                this.bCol = (tint & 255) / 255.0F;
            }
        }
        this.spinAxis = new Vector3f(this.random.nextFloat() - 0.5F, this.random.nextFloat() - 0.5F,
                this.random.nextFloat() - 0.5F).normalize();
        this.angle = this.random.nextFloat() * Mth.TWO_PI;
        this.angleO = this.angle;
        this.spin = (this.random.nextFloat() - 0.5F) * 0.9F;
        this.gravity = 1.0F;
        this.hasPhysics = true;
        this.lifetime = 300 + this.random.nextInt(60);
        this.setSize(size, size);
    }

    /**
     * A short-lived chip of a block, standing in for a vanilla block crumb: a tiny textured cube thrown with
     * the crumb's motion that tumbles, bounces and shrinks away within about {@code lifetime} ticks.
     */
    static Particle chip(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                         BlockState state, float size, int lifetime) {
        if (state.isAir()) {
            state = Blocks.STONE.defaultBlockState();
        }
        java.util.Random random = new java.util.Random();
        float s = size * (0.6F + random.nextFloat() * 0.8F);
        BlockDebrisParticle chip = new BlockDebrisParticle(level, x, y, z, state, BlockPos.containing(x, y, z), s);
        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        double k = speed > 0.3 ? 0.3 / speed : 1.0;
        chip.launch(vx * k + (random.nextDouble() - 0.5) * 0.08, vy * k + random.nextDouble() * 0.12,
                vz * k + (random.nextDouble() - 0.5) * 0.08);
        chip.lifetime = Math.max(8, lifetime + random.nextInt(Math.max(1, lifetime / 2)));
        return chip;
    }

    void launch(double vx, double vy, double vz) {
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
    }

    boolean isReturning() {
        return this.returning;
    }

    /** Lift off and fly into {@code target} (a point inside the block) over {@code ticks} ticks. */
    void flyHome(Vec3 target, int ticks) {
        this.returning = true;
        this.hasPhysics = false;
        this.returnFrom = new Vec3(this.x, this.y, this.z);
        this.returnTo = target;
        Vec3 mid = this.returnFrom.add(target).scale(0.5);
        this.returnControl = mid.add(0.0, 1.2 + this.returnFrom.distanceTo(target) * 0.15, 0.0);
        this.returnTicks = Math.max(4, ticks);
        this.returnAge = 0;
        this.spin = (this.random.nextBoolean() ? 1.0F : -1.0F) * (0.5F + this.random.nextFloat() * 0.4F);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.angleO = this.angle;
        this.angle += this.spin;
        if (this.returning) {
            tickReturn();
            return;
        }
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        this.yd -= 0.04 * this.gravity;
        double fall = this.yd;
        collide(this.xd, this.yd, this.zd);
        if (this.onGround) {
            if (fall < -0.12) {
                this.yd = -fall * 0.3; // a small bounce
                this.xd *= 0.6;
                this.zd *= 0.6;
            } else {
                this.yd = 0.0;
                this.xd *= 0.5;
                this.zd *= 0.5;
            }
            this.spin *= 0.5F;
        }
        this.xd *= 0.98;
        this.yd *= 0.98;
        this.zd *= 0.98;
    }

    /**
     * Moves with block collision. Vanilla's Particle.move freezes a particle for good the first time a vertical
     * move is blocked, which leaves pieces stuck to ceilings; this one bounces off walls and drops off ceilings.
     */
    private void collide(double dx, double dy, double dz) {
        Vec3 want = new Vec3(dx, dy, dz);
        Vec3 got = net.minecraft.world.entity.Entity.collideBoundingBox(null, want, this.getBoundingBox(), this.level,
                java.util.List.of());
        if (got.lengthSqr() > 0.0) {
            this.setBoundingBox(this.getBoundingBox().move(got));
            this.setLocationFromBoundingbox();
        }
        this.onGround = want.y != got.y && want.y < 0.0;
        if (want.y != got.y && want.y > 0.0) {
            this.yd = 0.0; // hit a ceiling: fall from here
        }
        if (want.x != got.x) {
            this.xd = -this.xd * 0.3;
        }
        if (want.z != got.z) {
            this.zd = -this.zd * 0.3;
        }
    }

    private void tickReturn() {
        this.returnAge++;
        float t = Mth.clamp(this.returnAge / (float) this.returnTicks, 0.0F, 1.0F);
        float k = t * t * (3.0F - 2.0F * t);
        double r = 1.0 - k;
        Vec3 p = this.returnFrom.scale(r * r).add(this.returnControl.scale(2.0 * r * k)).add(this.returnTo.scale(k * k));
        this.setPos(p.x, p.y, p.z);
        float[] c = REPARO_GLOW;
        SpellGlowParticle.spawn(this.xo, this.yo, this.zo, 0, 0.005, 0, c[0], c[1], c[2], 0.7F,
                0.16F, 0.03F, 10, 0.0F, 1.0F, false);
        if (this.returnAge >= this.returnTicks) {
            SpellGlowParticle.spawn(p.x, p.y, p.z, 0, 0, 0, c[0], c[1], c[2], 0.9F, 0.35F, 0.7F, 6, 0.0F, 1.0F, false);
            this.remove();
        }
    }

    /** Pieces shrink away at the end of their life, and into the wall at the end of a Reparo flight. */
    private float shrink(float partialTick) {
        if (this.returning) {
            float t = (this.returnAge + partialTick) / (float) this.returnTicks;
            return t < 0.8F ? 1.0F : Mth.clamp((1.0F - t) / 0.2F, 0.0F, 1.0F);
        }
        float left = this.lifetime - (this.age + partialTick);
        return Mth.clamp(left / Math.min(20.0F, this.lifetime * 0.4F), 0.0F, 1.0F);
    }

    @Override
    public void render(VertexConsumer consumer, Camera camera, float partialTick) {
        float scale = shrink(partialTick);
        if (scale <= 0.0F) {
            return;
        }
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTick, this.xo, this.x) - cam.x);
        float py = (float) (Mth.lerp(partialTick, this.yo, this.y) - cam.y) + this.size * 0.5F;
        float pz = (float) (Mth.lerp(partialTick, this.zo, this.z) - cam.z);
        Quaternionf rotation = new Quaternionf().rotationAxis(Mth.lerp(partialTick, this.angleO, this.angle), this.spinAxis);
        float h = this.size * 0.5F * scale;
        int light = this.getLightColor(partialTick);
        // Six faces; each corner list is in the cube's own space, wound outwards.
        face(consumer, rotation, px, py, pz, h, light, 1.0F, -1, 1, -1, -1, 1, 1, 1, 1, 1, 1, 1, -1);   // up
        face(consumer, rotation, px, py, pz, h, light, 0.5F, -1, -1, 1, -1, -1, -1, 1, -1, -1, 1, -1, 1); // down
        face(consumer, rotation, px, py, pz, h, light, 0.8F, -1, -1, -1, -1, 1, -1, 1, 1, -1, 1, -1, -1); // north
        face(consumer, rotation, px, py, pz, h, light, 0.8F, 1, -1, 1, 1, 1, 1, -1, 1, 1, -1, -1, 1);     // south
        face(consumer, rotation, px, py, pz, h, light, 0.6F, -1, -1, 1, -1, 1, 1, -1, 1, -1, -1, -1, -1); // west
        face(consumer, rotation, px, py, pz, h, light, 0.6F, 1, -1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1);     // east
    }

    private void face(VertexConsumer consumer, Quaternionf rotation, float px, float py, float pz, float h, int light,
                      float shade, float... corners) {
        float[] us = {this.u0, this.u0, this.u1, this.u1};
        float[] vs = {this.v1, this.v0, this.v0, this.v1};
        Vector3f v = new Vector3f();
        // Both windings, so the cube is solid whether or not face culling is on for the particle pass.
        for (int n = 0; n < 8; n++) {
            int i = n < 4 ? n : 7 - n;
            v.set(corners[i * 3] * h, corners[i * 3 + 1] * h, corners[i * 3 + 2] * h);
            rotation.transform(v);
            consumer.addVertex(px + v.x, py + v.y, pz + v.z)
                    .setUv(us[i], vs[i])
                    .setColor(this.rCol * shade, this.gCol * shade, this.bCol * shade, this.alpha)
                    .setLight(light);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.TERRAIN_SHEET;
    }
}
