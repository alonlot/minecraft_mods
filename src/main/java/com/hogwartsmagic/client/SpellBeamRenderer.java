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
        Vec3 orb = origin.add(0.0, ORB_HEIGHT, 0.0);
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
        poseStack.translate(0.0F, ORB_HEIGHT, 0.0F);

        drawTail(entity, poseStack, buffer, orb, camera, age, color, hot, near);
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
     * A tapered ribbon from the orb back along the flight path, turned to face the camera. It never reaches
     * further back than the beam has flown, so it does not poke out of the caster's wand.
     */
    private void drawTail(SpellBeamEntity entity, PoseStack poseStack, MultiBufferSource buffer, Vec3 orb, Vec3 camera,
                          float age, float[] color, float[] hot, float near) {
        Vec3 vel = entity.getDeltaMovement();
        double speed = vel.length();
        if (speed < 1.0E-3) {
            return;
        }
        float length = (float) Math.min(TAIL_LENGTH, age * speed);
        if (length < 0.05F) {
            return;
        }
        Vec3 dir = vel.scale(1.0 / speed);
        Vec3 toCamera = camera.subtract(orb);
        Vec3 side = dir.cross(toCamera);
        if (side.lengthSqr() < 1.0E-6) {
            return; // looking straight down the path: the orb covers the tail anyway
        }
        side = side.normalize();
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer glow = buffer.getBuffer(ModRenderTypes.SPELL_GLOW);
        ribbon(glow, matrix, dir, side, length, 0.2F, color, 0.55F * near);
        ribbon(glow, matrix, dir, side, length * 0.75F, 0.07F, hot, 0.8F * near);
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
