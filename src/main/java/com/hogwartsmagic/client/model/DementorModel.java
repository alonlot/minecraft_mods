package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.DementorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

public class DementorModel extends EntityModel<DementorEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "dementor"), "main");
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart lowerCloak;

    public DementorModel(ModelPart root) {
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.leftArm = root.getChild("leftArm");
        this.rightArm = root.getChild("rightArm");
        this.lowerCloak = root.getChild("lowerCloak");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition root = meshdefinition.getRoot();

        // Hood and face (Hood extends slightly over the face)
        root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.5F)),
                PartPose.offset(0.0F, -12.0F, 0.0F));

        // Long tall body
        root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(16, 16)
                .addBox(-4.0F, 0.0F, -2.0F, 8.0F, 16.0F, 4.0F),
                PartPose.offset(0.0F, -12.0F, 0.0F));

        // Skeletal scary arms raised up slightly
        root.addOrReplaceChild("rightArm", CubeListBuilder.create()
                .texOffs(40, 16)
                .addBox(-2.0F, -2.0F, -2.0F, 3.0F, 18.0F, 3.0F),
                PartPose.offsetAndRotation(-5.0F, -10.0F, 0.0F, -0.2F, 0.0F, 0.1F));

        root.addOrReplaceChild("leftArm", CubeListBuilder.create()
                .texOffs(40, 16)
                .mirror()
                .addBox(-1.0F, -2.0F, -2.0F, 3.0F, 18.0F, 3.0F),
                PartPose.offsetAndRotation(5.0F, -10.0F, 0.0F, -0.2F, 0.0F, -0.1F));

        // Tattered lower cloak trailing beneath
        root.addOrReplaceChild("lowerCloak", CubeListBuilder.create()
                .texOffs(0, 36)
                .addBox(-5.0F, 0.0F, -3.0F, 10.0F, 16.0F, 6.0F, new CubeDeformation(0.25F)),
                PartPose.offset(0.0F, 4.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public void setupAnim(DementorEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.yRot = netHeadYaw * ((float)Math.PI / 180F);
        this.head.xRot = headPitch * ((float)Math.PI / 180F);

        // Hovering gently
        float floatAnim = (float) Math.sin(ageInTicks * 0.1F) * 0.1F;
        this.body.y = -12.0F + floatAnim * 5.0F;
        this.head.y = -12.0F + floatAnim * 5.0F;
        this.rightArm.y = -10.0F + floatAnim * 5.0F;
        this.leftArm.y = -10.0F + floatAnim * 5.0F;
        this.lowerCloak.y = 4.0F + floatAnim * 5.0F;

        // "Sucking" animation raises the arms more towards the player
        if (entity.getTarget() != null && entity.distanceToSqr(entity.getTarget()) < 256.0D) {
            this.rightArm.xRot = (float) Math.toRadians(-80) + (float)Math.sin(ageInTicks * 0.3) * 0.1F;
            this.leftArm.xRot = (float) Math.toRadians(-80) + (float)Math.sin(ageInTicks * 0.3 + 1.0) * 0.1F;
        } else {
            this.rightArm.xRot = (float) Math.toRadians(-20);
            this.leftArm.xRot = (float) Math.toRadians(-20);
        }

        // Cloak trails backwards slightly based on flying
        this.lowerCloak.xRot = (float) Math.toRadians(15) + (float) Math.sin(ageInTicks * 0.05F) * 0.1F;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
        this.head.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
        this.body.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
        this.rightArm.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
        this.leftArm.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
        this.lowerCloak.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }
}
