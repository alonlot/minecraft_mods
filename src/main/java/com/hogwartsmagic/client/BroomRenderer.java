package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.BroomEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

public class BroomRenderer extends EntityRenderer<BroomEntity> {
    private static final ResourceLocation TRAINING_TEXTURE = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/broom_shooting_star_v2.png");
    private static final ResourceLocation NIMBUS_TEXTURE = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/broom_nimbus_2000_v2.png");
    private static final ResourceLocation FIREBOLT_TEXTURE = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/broom_firebolt_v2.png");

    // Each variant is its own mesh now, so there is a baked model per broom
    // rather than one mesh with parts toggled on and off.
    private final BroomModelV2 trainingModel;
    private final BroomModelV2 nimbusModel;
    private final BroomModelV2 fireboltModel;

    public BroomRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.trainingModel = new BroomModelV2(context.bakeLayer(BroomModelV2.TRAINING_LAYER));
        this.nimbusModel = new BroomModelV2(context.bakeLayer(BroomModelV2.NIMBUS_LAYER));
        this.fireboltModel = new BroomModelV2(context.bakeLayer(BroomModelV2.FIREBOLT_LAYER));
    }

    private BroomModelV2 modelFor(BroomEntity entity) {
        return switch (entity.getBroomType()) {
            case 1 -> nimbusModel;
            case 2 -> fireboltModel;
            default -> trainingModel;
        };
    }

    @Override
    public boolean shouldRender(BroomEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        // Broom model uses custom transform offsets and can be falsely culled mid-fall.
        // Always render when in range rather than relying on frustum tests.
        return true;
    }

    @Override
    public void render(BroomEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (BroomEntity.DEBUG_VANISH) { // TEMPORARY DEBUG
            net.minecraft.client.Camera cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
            entity.debugRendered(entity.distanceToSqr(cam.getPosition()));
        }
        poseStack.pushPose();
        
        // Centering the model on the entity
        // The parts in the model are mostly around y=22.
        // In MC models, y=24 is ground (bottom of the hitbox).
        // If we want the broom (y=22) to be slightly above the ground (say at y=0.5 blocks),
        // we need to translate it.
        
        // Use lerp for rotations for smoother visual orientation
        float lerpY = net.minecraft.util.Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
        float lerpX = net.minecraft.util.Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        float lerpRoll = net.minecraft.util.Mth.lerp(partialTicks, entity.prevDisplayRoll, entity.displayRoll);

        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - lerpY));
        poseStack.mulPose(Axis.XP.rotationDegrees(-lerpX));

        // Trick: barrel roll (Z axis) or loop (X axis). Progress = 1 - ticksLeft/duration.
        byte trick = entity.getTrickType();
        if (trick != com.hogwartsmagic.entity.BroomEntity.TRICK_NONE) {
            int duration = com.hogwartsmagic.entity.BroomEntity.trickDurationFor(trick);
            float lerpTicks = net.minecraft.util.Mth.lerp(partialTicks, entity.prevTrickTicks, entity.getTrickTicksSynced());
            float progress = 1.0F - Mth.clamp(lerpTicks / duration, 0.0F, 1.0F);
            float spinDeg = progress * 360.0F;
            if (trick == com.hogwartsmagic.entity.BroomEntity.TRICK_BARREL_LEFT) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(-spinDeg));
            } else if (trick == com.hogwartsmagic.entity.BroomEntity.TRICK_BARREL_RIGHT) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(spinDeg));
            } else if (trick == com.hogwartsmagic.entity.BroomEntity.TRICK_LOOP) {
                poseStack.mulPose(Axis.XP.rotationDegrees(spinDeg));
            }
        }

        // Banking roll: broom local +Z runs along the handle, so roll is about the Z axis.
        poseStack.mulPose(Axis.ZP.rotationDegrees(lerpRoll));
        
        // Flip the baked model, then lift it so the broom body sits around the entity origin
        // instead of rendering mostly below the ground plane.
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0D, -1.501D, 0.0D);
        
        // Remove the extra arbitrary translation so it maps exactly 1:1 with the hitbox
        
        ResourceLocation texture = this.getTextureLocation(entity);
        BroomModelV2 model = this.modelFor(entity);
        model.setupAnim(entity, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        model.renderToBuffer(poseStack, buffer.getBuffer(model.renderType(texture)), packedLight, OverlayTexture.NO_OVERLAY, -1);
        
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(BroomEntity entity) {
        return switch (entity.getBroomType()) {
            case 1 -> NIMBUS_TEXTURE;
            case 2 -> FIREBOLT_TEXTURE;
            default -> TRAINING_TEXTURE;
        };
    }
}
