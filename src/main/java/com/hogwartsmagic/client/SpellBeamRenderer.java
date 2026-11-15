package com.hogwartsmagic.client;

import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.spell.SpellType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws a spell in flight as a ball of light: a soft coloured halo, a brighter inner glow and a white-hot
 * core, with a short comet tail behind it along its path. The violent curses also crackle with small
 * lightning arcs. The sparkle trail and the impact burst are particles (see {@link SpellFx}).
 */
public class SpellBeamRenderer extends EntityRenderer<SpellBeamEntity> {

    /** Height of the orb above the entity position: the middle of its 0.25 block hitbox. */
    public static final float ORB_HEIGHT = 0.125F;
    /** Spells that fly as a swarm of comets around the orb. */
    public static final java.util.Set<SpellType> WISPS = java.util.EnumSet.of(
            SpellType.AVADA_KEDAVRA, SpellType.IMPERIO, SpellType.HEAL);
    private static final float TAIL_LENGTH = 2.4F;

    public SpellBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SpellBeamEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        SpellType spell = entity.getSpellType();
        if (SpellFx.NO_ORB.contains(spell) || entity.hasFreshClientImpact()) {
            return;
        }
        float age = entity.tickCount + partialTick;
        Vec3 camera = this.entityRenderDispatcher.camera.getPosition();
        Vec3 origin = entity.getPosition(partialTick);
        Vec3 orb = SpellPath.orb(entity, partialTick); // curved and wobbling, not the straight real line
        SpellPath.record(entity, orb);
        if (SpellRibbon.RIBBON.contains(spell)) {
            // Drawn before the near-camera fade: the ribbon starts at the caster's wand.
            SpellRibbon.draw(entity, poseStack.last().pose(), buffer, partialTick, this.entityRenderDispatcher.camera,
                    origin, orb);
        }
        // Fade in over the first blocks so a beam leaving the caster's own eyes never fills the screen.
        float near = Mth.clamp((float) ((orb.distanceTo(camera) - 1.2) / 3.0), 0.0F, 1.0F);
        if (near <= 0.0F) {
            return;
        }
        float[] color = SpellFx.lightColor(spell);
        float[] hot = SpellFx.whiten(color, 0.45F);
        float size = Mth.clamp(0.8F + entity.getCharge() * 0.15F, 0.85F, 1.1F);
        float pulse = 1.0F + 0.08F * Mth.sin(age * 1.3F);

        poseStack.pushPose();
        drawCurvedTail(entity, poseStack.last().pose(), buffer, origin, orb, camera, color, hot, near);
        Vec3 rel = orb.subtract(origin);
        poseStack.translate(rel.x, rel.y, rel.z);
        if (WISPS.contains(spell)) {
            drawWisps(entity, poseStack, buffer, camera, orb, age, color, hot, near);
        }

        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer glow = buffer.getBuffer(ModRenderTypes.SPELL_GLOW);
        SpellFx.glowQuad(glow, matrix, 0, 0, 0, 0.9F * size * pulse, color[0], color[1], color[2], 0.6F * near);
        SpellFx.glowQuad(glow, matrix, 0, 0, 0, 0.36F * size * pulse, hot[0], hot[1], hot[2], 0.85F * near);
        SpellFx.glowQuad(glow, matrix, 0, 0, 0, 0.15F * size, 1.0F, 1.0F, 1.0F, near);

        if (SpellFx.CRACKLING.contains(spell)) {
            VertexConsumer bolts = buffer.getBuffer(RenderType.lightning());
            long seed = entity.getId() * 31L + entity.tickCount;
            for (int i = 0; i < 3; i++) {
                SpellFx.crackle(bolts, matrix, seed * 7L + i, 0.55F * size, 0.012F,
                        hot[0], hot[1], hot[2], 0.8F * near);
            }
        }
        poseStack.popPose();
    }

    /**
     * A swarm of small comets weaving around the main orb, each with its own streak: the green spell from the
     * reference video. They circle the flight path at different distances and lag behind the orb.
     */
    private void drawWisps(SpellBeamEntity entity, PoseStack poseStack, MultiBufferSource buffer, Vec3 camera, Vec3 orb,
                           float age, float[] color, float[] hot, float near) {
        Vec3 vel = entity.getDeltaMovement();
        double speed = vel.length();
        if (speed < 1.0E-3) {
            return;
        }
        Vec3 dir = vel.scale(1.0 / speed);
        Vec3 upRef = Math.abs(dir.y) > 0.99 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 right = dir.cross(upRef).normalize();
        Vec3 up = right.cross(dir).normalize();
        VertexConsumer glow = buffer.getBuffer(ModRenderTypes.SPELL_GLOW);
        int count = 5;
        for (int i = 0; i < count; i++) {
            float phase = age * 0.45F + i * Mth.TWO_PI / count;
            float radius = 0.5F + 0.25F * Mth.sin(age * 0.7F + i * 1.3F);
            float lag = 0.3F + 0.9F * (((i * 0.37F) + age * 0.03F) % 1.0F);
            Vec3 offset = right.scale(Mth.cos(phase) * radius).add(up.scale(Mth.sin(phase) * radius)).subtract(dir.scale(lag));
            // The wisp moves forward with the orb and round the path, so its streak trails against that.
            Vec3 motion = dir.add(right.scale(-Mth.sin(phase) * 0.6).add(up.scale(Mth.cos(phase) * 0.6))).normalize();
            Vec3 toCamera = camera.subtract(orb.add(offset));
            Vec3 side = motion.cross(toCamera);
            if (side.lengthSqr() > 1.0E-6) {
                side = side.normalize();
                ribbon(glow, poseStack.last().pose(), offset, motion, side, 1.1F, 0.09F, color, 0.7F * near);
            }
            poseStack.pushPose();
            poseStack.translate(offset.x, offset.y, offset.z);
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            Matrix4f m = poseStack.last().pose();
            SpellFx.glowQuad(glow, m, 0, 0, 0, 0.3F, color[0], color[1], color[2], 0.7F * near);
            SpellFx.glowQuad(glow, m, 0, 0, 0, 0.09F, hot[0], hot[1], hot[2], 0.95F * near);
            poseStack.popPose();
        }
    }

    @Override
    public boolean shouldRender(SpellBeamEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double camX, double camY, double camZ) {
        if (super.shouldRender(entity, frustum, camX, camY, camZ)) {
            return true;
        }
        // A ribbon reaches back to the caster, so it can be on screen while the orb itself is not.
        return SpellRibbon.RIBBON.contains(entity.getSpellType()) && entity.distanceToSqr(camX, camY, camZ) < 64.0 * 64.0;
    }

    /**
     * The comet tail: a tapered band through the points the orb was drawn at, so it follows the curve and the
     * wobble instead of pointing straight back. Only drawn points are used, so it never pokes out of the wand.
     */
    private static void drawCurvedTail(SpellBeamEntity entity, Matrix4f matrix, MultiBufferSource buffer, Vec3 origin,
                                       Vec3 orb, Vec3 camera, float[] color, float[] hot, float near) {
        Vec3[] points = SpellPath.tail(entity, orb, TAIL_LENGTH);
        if (points.length < 2) {
            return;
        }
        VertexConsumer glow = buffer.getBuffer(ModRenderTypes.SPELL_GLOW);
        curvedBand(glow, matrix, points, origin, camera, 0.2F, color, 0.55F * near);
        curvedBand(glow, matrix, points, origin, camera, 0.07F, hot, 0.8F * near);
    }

    private static void curvedBand(VertexConsumer consumer, Matrix4f matrix, Vec3[] points, Vec3 origin, Vec3 camera,
                                   float width, float[] c, float alpha) {
        int n = points.length;
        Vec3 prevSide = null;
        for (int i = 0; i < n - 1; i++) {
            Vec3 a = points[i];
            Vec3 b = points[i + 1];
            Vec3 side = b.subtract(a).cross(camera.subtract(a));
            if (side.lengthSqr() < 1.0E-10) {
                continue;
            }
            side = side.normalize();
            if (prevSide == null) {
                prevSide = side;
            }
            float ka = 1.0F - i / (float) (n - 1);
            float kb = 1.0F - (i + 1) / (float) (n - 1);
            float wa = width * (0.15F + 0.85F * ka);
            float wb = width * (0.15F + 0.85F * kb);
            Vec3 ra = a.subtract(origin);
            Vec3 rb = b.subtract(origin);
            SpellFx.vertex(consumer, matrix, (float) (ra.x + prevSide.x * wa), (float) (ra.y + prevSide.y * wa),
                    (float) (ra.z + prevSide.z * wa), 0.0F, 0.5F, c[0], c[1], c[2], alpha * ka);
            SpellFx.vertex(consumer, matrix, (float) (ra.x - prevSide.x * wa), (float) (ra.y - prevSide.y * wa),
                    (float) (ra.z - prevSide.z * wa), 1.0F, 0.5F, c[0], c[1], c[2], alpha * ka);
            SpellFx.vertex(consumer, matrix, (float) (rb.x - side.x * wb), (float) (rb.y - side.y * wb),
                    (float) (rb.z - side.z * wb), 1.0F, 0.5F, c[0], c[1], c[2], alpha * kb);
            SpellFx.vertex(consumer, matrix, (float) (rb.x + side.x * wb), (float) (rb.y + side.y * wb),
                    (float) (rb.z + side.z * wb), 0.0F, 0.5F, c[0], c[1], c[2], alpha * kb);
            prevSide = side;
        }
    }

    /** One ribbon layer: full width at the orb, narrowing to a point and fading out at the end of the tail. */
    private static void ribbon(VertexConsumer consumer, Matrix4f matrix, Vec3 dir, Vec3 side, float length,
                               float width, float[] c, float alpha) {
        ribbon(consumer, matrix, Vec3.ZERO, dir, side, length, width, c, alpha);
    }

    /** The same ribbon starting at {@code at} instead of the orb. */
    private static void ribbon(VertexConsumer consumer, Matrix4f matrix, Vec3 at, Vec3 dir, Vec3 side, float length,
                               float width, float[] c, float alpha) {
        float ox = (float) at.x;
        float oy = (float) at.y;
        float oz = (float) at.z;
        float hx = (float) side.x * width;
        float hy = (float) side.y * width;
        float hz = (float) side.z * width;
        float ex = ox + (float) -dir.x * length;
        float ey = oy + (float) -dir.y * length;
        float ez = oz + (float) -dir.z * length;
        float tip = 0.15F;
        // Across the ribbon u runs over the middle row of the glow sprite: bright centre, soft edges.
        SpellFx.vertex(consumer, matrix, ox + hx, oy + hy, oz + hz, 0.0F, 0.5F, c[0], c[1], c[2], alpha);
        SpellFx.vertex(consumer, matrix, ox - hx, oy - hy, oz - hz, 1.0F, 0.5F, c[0], c[1], c[2], alpha);
        SpellFx.vertex(consumer, matrix, ex - hx * tip, ey - hy * tip, ez - hz * tip, 1.0F, 0.5F, c[0], c[1], c[2], 0.0F);
        SpellFx.vertex(consumer, matrix, ex + hx * tip, ey + hy * tip, ez + hz * tip, 0.0F, 0.5F, c[0], c[1], c[2], 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(SpellBeamEntity entity) {
        return ModRenderTypes.SPELL_GLOW_TEXTURE;
    }
}
