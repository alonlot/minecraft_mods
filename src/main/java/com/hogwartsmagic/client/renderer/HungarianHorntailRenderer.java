package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.HungarianHorntailModel;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;

public class HungarianHorntailRenderer extends MobRenderer<HungarianHorntailEntity, HungarianHorntailModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/hungarian_horntail.png");

    public HungarianHorntailRenderer(EntityRendererProvider.Context context) {
        this(context, new HungarianHorntailModel(context.bakeLayer(HungarianHorntailModel.LAYER_LOCATION)));
    }

    /** With a model of its own (the memory dragon's blends its poses: client/MemoryHorntailRenderer). */
    protected HungarianHorntailRenderer(EntityRendererProvider.Context context, HungarianHorntailModel model) {
        super(context, model, 1.6F);
        this.addLayer(new EyesLayer(this));
        this.addLayer(new FireMouthLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(HungarianHorntailEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(HungarianHorntailEntity entity, PoseStack poseStack, float partialTickTime) {
        // The model's ground plane sits at the renderer's scale pivot, so the feet stay planted at
        // any scale — this only controls how big the dragon reads next to the player.
        poseStack.scale(1.15F, 1.15F, 1.15F);
    }

    /** Burning amber eyes that stay lit in the dark, the way the film dragon's do. */
    private static class EyesLayer extends RenderLayer<HungarianHorntailEntity, HungarianHorntailModel> {
        private static final RenderType EYES = RenderType.eyes(
                ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/hungarian_horntail_eyes.png"));

        EyesLayer(RenderLayerParent<HungarianHorntailEntity, HungarianHorntailModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                           HungarianHorntailEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            VertexConsumer consumer = buffer.getBuffer(EYES);
            this.getParentModel().renderToBuffer(poseStack, consumer, 15728640,
                    OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        }
    }

    /**
     * Hands the drawn mouth and the head direction of a wild dragon to the fire (DragonFireRenderer) each frame, read
     * off the posed model: the pose stack at a layer is the model root, camera-relative. The memory dragon has its own.
     */
    private static class FireMouthLayer extends RenderLayer<HungarianHorntailEntity, HungarianHorntailModel> {
        FireMouthLayer(RenderLayerParent<HungarianHorntailEntity, HungarianHorntailModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                           HungarianHorntailEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity instanceof com.hogwartsmagic.entity.MemoryHorntailEntity) return;
            PoseStack head = new PoseStack();
            head.last().pose().set(poseStack.last().pose());
            head.last().normal().set(poseStack.last().normal());
            getParentModel().translateToHead(head);
            Vector4f p = new Vector4f(0f, HungarianHorntailModel.SNOUT_Y / 16f, HungarianHorntailModel.SNOUT_Z / 16f, 1f)
                    .mul(head.last().pose());
            Vector4f d = new Vector4f(0f, 0f, -1f, 0f).mul(head.last().pose());
            double len = Math.sqrt(d.x * d.x + d.y * d.y + d.z * d.z);
            if (len < 1e-9) return;
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            com.hogwartsmagic.client.pensieve.DragonFireRenderer.stampWild(entity,
                    new double[]{p.x + cam.x, p.y + cam.y, p.z + cam.z},
                    new double[]{d.x / len, d.y / len, d.z / len});
        }
    }
}
