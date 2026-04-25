package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public class GillyweedGillsModel<T extends LivingEntity> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "gillyweed_gills"),
            "main"
    );

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;
    private final ModelPart leftGill;
    private final ModelPart rightGill;
    private final ModelPart leftHandFin;
    private final ModelPart rightHandFin;
    private final ModelPart leftLegFin;
    private final ModelPart rightLegFin;
    private final ModelPart leftFootFin;
    private final ModelPart rightFootFin;

    public GillyweedGillsModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.leftArm = root.getChild("left_arm");
        this.rightArm = root.getChild("right_arm");
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");
        this.leftGill = this.head.getChild("left_gill");
        this.rightGill = this.head.getChild("right_gill");
        this.leftHandFin = this.leftArm.getChild("left_hand_fin");
        this.rightHandFin = this.rightArm.getChild("right_hand_fin");
        this.leftLegFin = this.leftLeg.getChild("left_leg_fin");
        this.rightLegFin = this.rightLeg.getChild("right_leg_fin");
        this.leftFootFin = this.leftLeg.getChild("left_foot_fin");
        this.rightFootFin = this.rightLeg.getChild("right_foot_fin");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition root = meshDefinition.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);

        head.addOrReplaceChild(
                "left_gill",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-0.45F, -1.3F, 0.0F, 0.8F, 2.2F, 0.0F)
                        .texOffs(0, 0)
                        .addBox(-0.4F, 1.05F, 0.0F, 0.75F, 2.0F, 0.0F),
                PartPose.offsetAndRotation(2.9F, -0.55F, -3.95F, 0.0F, -0.16F, -0.08F)
        );
        head.addOrReplaceChild(
                "right_gill",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .mirror()
                        .addBox(-0.35F, -1.3F, 0.0F, 0.8F, 2.2F, 0.0F)
                        .texOffs(0, 0)
                        .addBox(-0.35F, 1.05F, 0.0F, 0.75F, 2.0F, 0.0F)
                        .mirror(false),
                PartPose.offsetAndRotation(-2.9F, -0.55F, -3.95F, 0.0F, 0.16F, 0.08F)
        );

        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.ZERO);
        leftArm.addOrReplaceChild(
                "left_hand_fin",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-1.4F, -0.4F, 0.0F, 2.8F, 4.8F, 0.0F),
                PartPose.offsetAndRotation(0.0F, 7.0F, 2.05F, 0.0F, 0.0F, 0.0F)
        );

        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.ZERO);
        rightArm.addOrReplaceChild(
                "right_hand_fin",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .mirror()
                        .addBox(-1.4F, -0.4F, 0.0F, 2.8F, 4.8F, 0.0F)
                        .mirror(false),
                PartPose.offsetAndRotation(0.0F, 7.0F, 2.05F, 0.0F, 0.0F, 0.0F)
        );

        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.ZERO);
        leftLeg.addOrReplaceChild(
                "left_leg_fin",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-1.5F, -0.2F, 0.0F, 3.0F, 4.8F, 0.0F),
                PartPose.offsetAndRotation(0.0F, 8.4F, 2.2F, 0.16F, 0.0F, 0.0F)
        );
        leftLeg.addOrReplaceChild(
                "left_foot_fin",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-1.7F, -0.1F, -2.6F, 4.2F, 0.0F, 5.2F),
                PartPose.offsetAndRotation(0.9F, 11.0F, -0.8F, 0.0F, 0.0F, -0.08F)
        );

        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.ZERO);
        rightLeg.addOrReplaceChild(
                "right_leg_fin",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .mirror()
                        .addBox(-1.5F, -0.2F, 0.0F, 3.0F, 4.8F, 0.0F)
                        .mirror(false),
                PartPose.offsetAndRotation(0.0F, 8.4F, 2.2F, 0.16F, 0.0F, 0.0F)
        );
        rightLeg.addOrReplaceChild(
                "right_foot_fin",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .mirror()
                        .addBox(-2.5F, -0.1F, -2.6F, 4.2F, 0.0F, 5.2F)
                        .mirror(false),
                PartPose.offsetAndRotation(-0.9F, 11.0F, -0.8F, 0.0F, 0.0F, 0.08F)
        );

        return LayerDefinition.create(meshDefinition, 16, 16);
    }

    public void copyStateFrom(HumanoidModel<?> humanoidModel) {
        this.head.copyFrom(humanoidModel.head);
        this.leftArm.copyFrom(humanoidModel.leftArm);
        this.rightArm.copyFrom(humanoidModel.rightArm);
        this.leftLeg.copyFrom(humanoidModel.leftLeg);
        this.rightLeg.copyFrom(humanoidModel.rightLeg);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float swimBlend = entity.isInWaterOrBubble() ? 1.0F : 0.25F;
        float idleWave = Mth.sin(ageInTicks * 0.18F) * 0.05F;
        float strideWave = Mth.sin(limbSwing * 0.8F) * limbSwingAmount * 0.28F;
        float sway = idleWave + (strideWave * swimBlend);

        leftGill.yRot = 0.02F + (sway * 0.25F);
        rightGill.yRot = -0.02F - (sway * 0.25F);

        leftHandFin.xRot = 0.18F + (sway * 0.35F);
        rightHandFin.xRot = 0.18F + (sway * 0.35F);
        leftHandFin.zRot = 0.0F;
        rightHandFin.zRot = 0.0F;

        leftLegFin.xRot = 0.12F + (sway * 0.25F);
        rightLegFin.xRot = 0.12F + (sway * 0.25F);
        leftLegFin.zRot = 0.0F;
        rightLegFin.zRot = 0.0F;

        leftFootFin.xRot = 0.08F + (Mth.sin(ageInTicks * 0.2F) * 0.04F);
        rightFootFin.xRot = 0.08F - (Mth.sin(ageInTicks * 0.2F) * 0.04F);
    }

    @Override
    public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack poseStack,
                               com.mojang.blaze3d.vertex.VertexConsumer buffer,
                               int packedLight,
                               int packedOverlay,
                               int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
