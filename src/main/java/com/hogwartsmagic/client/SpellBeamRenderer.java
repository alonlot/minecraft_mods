package com.hogwartsmagic.client;

import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.spell.SpellType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Renders spell beams as layered jagged bolts so they feel magical instead of hitscan lasers.
 */
public class SpellBeamRenderer extends EntityRenderer<SpellBeamEntity> {

    private static final float BASE_BEAM_LENGTH = 2.8f;
    private static final float BASE_CORE_THICKNESS = 0.05f;
    private static final float BASE_GLOW_THICKNESS = 0.11f;

    public SpellBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SpellBeamEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        SpellType spell = entity.getSpellType();
        if (spell == SpellType.AQUA_ERUCTO) {
            return;
        }
        float r = spell.getR();
        float g = spell.getG();
        float b = spell.getB();
        float age = entity.tickCount + partialTick;
        float pulse = 0.85f + (float) Math.sin(age * 0.65f) * 0.15f;
        float coreAlpha = 0.9f * pulse;
        float glowAlpha = 0.35f * pulse;
        poseStack.pushPose();
        
        // Center the beam vertically based on the entity's apparent offset.
        poseStack.translate(0, 0.25, 0);

        // Calculate velocity direction for beam orientation
        net.minecraft.world.phys.Vec3 vel = entity.getDeltaMovement();
        if (vel.lengthSqr() < 0.001) {
            vel = new net.minecraft.world.phys.Vec3(0, 0, 1); // fallback
        }
        vel = vel.normalize();

        // Calculate yaw and pitch based on velocity vector so the beam points exactly in the direction of travel
        float yaw = (float) (Math.atan2(vel.x, vel.z) * (180F / Math.PI));
        float pitch = (float) (Math.atan2(vel.y, Math.sqrt(vel.x * vel.x + vel.z * vel.z)) * (180F / Math.PI));

        poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(pitch));
        poseStack.mulPose(Axis.XP.rotationDegrees(age * 7.0f));

        float beamLength = BASE_BEAM_LENGTH;
        float coreThickness = BASE_CORE_THICKNESS;
        float glowThickness = BASE_GLOW_THICKNESS;

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        int light = LightTexture.FULL_BRIGHT;

        // if-chain, not an enum switch: the synthetic switch-map class fails to load in client code here.
        if (spell == SpellType.AVADA_KEDAVRA || spell == SpellType.CRUCIO
                || spell == SpellType.DOLOHOV_CURSE || spell == SpellType.MORSMORDRE) {
            // Jagged lightning-like curse beam.
            drawLightningBeam(consumer, matrix, beamLength * 1.05f, glowThickness, age, spell.ordinal(), 1.9f, r, g, b, glowAlpha, light);
            drawLightningBeam(consumer, matrix, beamLength, coreThickness, age, spell.ordinal() + 17, 1.5f, r, g, b, coreAlpha, light);
            drawLightningBeam(consumer, matrix, beamLength * 0.9f, coreThickness * 0.45f, age, spell.ordinal() + 31, 1.15f, 1.0f, 1.0f, 1.0f, 0.3f * pulse, light);
        } else if (spell == SpellType.BOMBARDA || spell == SpellType.CONFRINGO || spell == SpellType.FIENDFYRE) {
            // Heavier, unstable blast profile.
            drawLightningBeam(consumer, matrix, beamLength * 0.98f, glowThickness * 1.35f, age, spell.ordinal(), 1.7f, r, g, b, glowAlpha * 1.1f, light);
            drawLightningBeam(consumer, matrix, beamLength * 0.92f, coreThickness * 1.25f, age, spell.ordinal() + 13, 1.35f, r, g, b, coreAlpha, light);
            drawLightningBeam(consumer, matrix, beamLength * 0.82f, coreThickness * 0.75f, age, spell.ordinal() + 29, 1.0f, 1.0f, 0.95f, 0.9f, 0.28f * pulse, light);
        } else if (spell == SpellType.EXPECTO_PATRONUM || spell == SpellType.LUMOS
                || spell == SpellType.HEAL || spell == SpellType.REVELIO) {
            // Brighter spells still arc, but with softer bends.
            drawLightningBeam(consumer, matrix, beamLength * 1.1f, glowThickness * 0.95f, age, spell.ordinal(), 1.15f, r, g, b, glowAlpha * 1.2f, light);
            drawLightningBeam(consumer, matrix, beamLength * 1.05f, coreThickness * 0.9f, age, spell.ordinal() + 11, 0.9f, r, g, b, coreAlpha, light);
            drawLightningBeam(consumer, matrix, beamLength, coreThickness * 0.5f, age, spell.ordinal() + 23, 0.65f, 1.0f, 1.0f, 1.0f, 0.42f * pulse, light);
        } else {
            // Default magical bolt profile.
            drawLightningBeam(consumer, matrix, beamLength, glowThickness, age, spell.ordinal(), 1.3f, r, g, b, glowAlpha, light);
            drawLightningBeam(consumer, matrix, beamLength, coreThickness, age, spell.ordinal() + 7, 1.05f, r, g, b, coreAlpha, light);
            drawLightningBeam(consumer, matrix, beamLength * 0.92f, coreThickness * 0.55f, age, spell.ordinal() + 19, 0.75f, 1.0f, 1.0f, 1.0f, 0.35f * pulse, light);
        }

        poseStack.popPose();
    }

    private static void drawLightningBeam(VertexConsumer consumer, Matrix4f matrix, float length, float thickness,
                                          float age, int seed, float jaggedness, float r, float g, float b, float a, int light) {
        int segments = 8;
        float segmentLength = length / segments;
        float startX = 0.0f;
        float startY = 0.0f;
        float startZ = 0.0f;

        for (int i = 1; i <= segments; i++) {
            float endX = -segmentLength * i;
            float endY;
            float endZ;

            if (i == segments) {
                endY = 0.0f;
                endZ = 0.0f;
            } else {
                float j = i + (seed * 0.13f);
                endY = (float) Math.sin(age * 1.9f + j * 1.7f) * thickness * 1.8f * jaggedness;
                endZ = (float) Math.cos(age * 1.6f + j * 1.3f) * thickness * 1.8f * jaggedness;
            }

            drawSegmentCross(consumer, matrix, startX, startY, startZ, endX, endY, endZ, thickness, r, g, b, a, light);
            startX = endX;
            startY = endY;
            startZ = endZ;
        }
    }

    private static void drawSegmentCross(VertexConsumer consumer, Matrix4f matrix,
                                         float sx, float sy, float sz, float ex, float ey, float ez,
                                         float thickness, float r, float g, float b, float a, int light) {
        consumer.addVertex(matrix, sx, sy - thickness, sz).setColor(r, g, b, a).setLight(light);
        consumer.addVertex(matrix, ex, ey - thickness, ez).setColor(r, g, b, a).setLight(light);
        consumer.addVertex(matrix, ex, ey + thickness, ez).setColor(r, g, b, a).setLight(light);
        consumer.addVertex(matrix, sx, sy + thickness, sz).setColor(r, g, b, a).setLight(light);

        consumer.addVertex(matrix, sx, sy, sz - thickness).setColor(r, g, b, a).setLight(light);
        consumer.addVertex(matrix, ex, ey, ez - thickness).setColor(r, g, b, a).setLight(light);
        consumer.addVertex(matrix, ex, ey, ez + thickness).setColor(r, g, b, a).setLight(light);
        consumer.addVertex(matrix, sx, sy, sz + thickness).setColor(r, g, b, a).setLight(light);
    }

    @Override
    public ResourceLocation getTextureLocation(SpellBeamEntity entity) {
        return null;
    }
}
