package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.PatronusEntity;
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

public class PatronusModel extends EntityModel<PatronusEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "patronus"), "main");
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart frontLegs;
    private final ModelPart backLegs;

    public PatronusModel(ModelPart root) {
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        this.frontLegs = root.getChild("frontLegs");
        this.backLegs = root.getChild("backLegs");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition root = meshdefinition.getRoot();

        // Majestic Stag Body
        root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-4.0F, -5.0F, -8.0F, 8.0F, 10.0F, 18.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 9.0F, 0.0F));

        // Head and Antlers
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 28)
                .addBox(-3.0F, -5.0F, -6.0F, 6.0F, 7.0F, 10.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 4.0F, -9.0F));
        
        // Antlers
        head.addOrReplaceChild("antlers", CubeListBuilder.create()
                .texOffs(32, 28)
                .addBox(-8.0F, -14.0F, -2.0F, 16.0F, 14.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -5.0F, -2.0F));

        // Front Legs
        root.addOrReplaceChild("frontLegs", CubeListBuilder.create()
                .texOffs(0, 45)
                .addBox(-4.0F, 0.0F, -2.0F, 8.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 14.0F, -5.0F));

        // Back Legs
        root.addOrReplaceChild("backLegs", CubeListBuilder.create()
                .texOffs(24, 45)
                .addBox(-4.0F, 0.0F, -2.0F, 8.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 14.0F, 7.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public void setupAnim(PatronusEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.yRot = netHeadYaw * ((float)Math.PI / 180F);
        this.head.xRot = headPitch * ((float)Math.PI / 180F);

        // Hovering gently
        float floatAnim = (float) Math.sin(ageInTicks * 0.1F) * 0.1F;
        this.body.y = 9.0F + floatAnim * 5.0F;
        this.head.y = 4.0F + floatAnim * 5.0F;
        this.frontLegs.y = 14.0F + floatAnim * 5.0F;
        this.backLegs.y = 14.0F + floatAnim * 5.0F;

        // "Galloping" mid-air animation based on movement
        this.frontLegs.xRot = net.minecraft.util.Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
        this.backLegs.xRot = net.minecraft.util.Mth.cos(limbSwing * 0.6662F + (float)Math.PI) * 1.4F * limbSwingAmount;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
        // Glowing magic aura (makes it render transparently if mixed properly in the renderer)
        this.head.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
        this.body.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
        this.frontLegs.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
        this.backLegs.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }
}
