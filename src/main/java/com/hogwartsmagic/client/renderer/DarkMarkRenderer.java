package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.DarkMarkEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public class DarkMarkRenderer extends EntityRenderer<DarkMarkEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/dark_mark.png");

    public DarkMarkRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(DarkMarkEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float age = entity.tickCount + partialTick;
        float growth = Math.min(1.0f, age / 70.0f);
        float scale = (11.0f + (growth * 18.0f)) * entity.getVisualScale();
        float alpha = 0.25f + (growth * 0.72f);
        float pulse = 1.0f + (float) Math.sin(age * 0.08f) * 0.03f;

        poseStack.pushPose();
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.scale(scale * pulse, scale * pulse, scale * pulse);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE));
        Matrix4f matrix = poseStack.last().pose();
        int light = LightTexture.FULL_BRIGHT;

        // Rear smoky silhouette.
        drawQuad(consumer, matrix, -0.04f, 0.06f, 1.18f, 0.16f, 0.32f, 0.20f, alpha * 0.34f, light);
        // Main mark layer.
        drawQuad(consumer, matrix, 0.0f, 0.0f, 1.0f, 0.72f, 0.88f, 0.76f, alpha, light);
        // Front green cursed glow.
        drawQuad(consumer, matrix, 0.03f, -0.02f, 0.92f, 0.38f, 0.78f, 0.42f, alpha * 0.42f, light);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static void drawQuad(VertexConsumer consumer, Matrix4f matrix, float zOffset, float yOffset, float size,
                                 float red, float green, float blue, float alpha, int light) {
        float half = size * 0.5f;
        consumer.addVertex(matrix, -half, -half + yOffset, zOffset).setColor(red, green, blue, alpha).setUv(0.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        consumer.addVertex(matrix, half, -half + yOffset, zOffset).setColor(red, green, blue, alpha).setUv(1.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        consumer.addVertex(matrix, half, half + yOffset, zOffset).setColor(red, green, blue, alpha).setUv(1.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        consumer.addVertex(matrix, -half, half + yOffset, zOffset).setColor(red, green, blue, alpha).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
    }

    @Override
    public ResourceLocation getTextureLocation(DarkMarkEntity entity) {
        return TEXTURE;
    }
}
