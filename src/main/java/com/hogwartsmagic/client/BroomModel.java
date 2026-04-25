package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.BroomEntity;
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

public class BroomModel extends EntityModel<BroomEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "broom"), "main");
    private final ModelPart handle;
    private final ModelPart twigs;
    private final ModelPart noseCap;
    private final ModelPart gripWrap;
    private final ModelPart frontBand;
    private final ModelPart rearBand;
    private final ModelPart trainingTuft;
    private final ModelPart nimbusLeftRail;
    private final ModelPart nimbusRightRail;
    private final ModelPart nimbusTopFin;
    private final ModelPart nimbusBottomFin;
    private final ModelPart fireboltSpine;
    private final ModelPart fireboltLeftFin;
    private final ModelPart fireboltRightFin;

    public BroomModel(ModelPart root) {
        this.handle = root.getChild("handle");
        this.twigs = root.getChild("twigs");
        this.noseCap = root.getChild("nose_cap");
        this.gripWrap = root.getChild("grip_wrap");
        this.frontBand = root.getChild("front_band");
        this.rearBand = root.getChild("rear_band");
        this.trainingTuft = root.getChild("training_tuft");
        this.nimbusLeftRail = root.getChild("nimbus_left_rail");
        this.nimbusRightRail = root.getChild("nimbus_right_rail");
        this.nimbusTopFin = root.getChild("nimbus_top_fin");
        this.nimbusBottomFin = root.getChild("nimbus_bottom_fin");
        this.fireboltSpine = root.getChild("firebolt_spine");
        this.fireboltLeftFin = root.getChild("firebolt_left_fin");
        this.fireboltRightFin = root.getChild("firebolt_right_fin");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition root = meshdefinition.getRoot();

        // The main wooden handle (long pole)
        root.addOrReplaceChild("handle", CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-1.0F, -1.0F, -14.0F, 2.0F, 2.0F, 28.0F),
                PartPose.offset(0.0F, 18.0F, 0.0F));

        // The thick bundle of twigs at the back
        root.addOrReplaceChild("twigs", CubeListBuilder.create()
                .texOffs(0, 32)
                .addBox(-2.5F, -2.5F, 12.0F, 5.0F, 5.0F, 14.0F)
                // Symmetric side flaring to avoid one-sided protrusions
                .texOffs(38, 32)
                .addBox(-2.0F, -2.0F, 17.0F, 1.0F, 4.0F, 10.0F)
                .texOffs(38, 32)
                .mirror()
                .addBox(1.0F, -2.0F, 17.0F, 1.0F, 4.0F, 10.0F),
                PartPose.offset(0.0F, 18.0F, 0.0F));

        root.addOrReplaceChild("nose_cap", CubeListBuilder.create()
                .texOffs(64, 0)
                .addBox(-1.5F, -1.5F, -16.5F, 3.0F, 3.0F, 3.0F),
                PartPose.offset(0.0F, 18.0F, 0.0F));

        root.addOrReplaceChild("grip_wrap", CubeListBuilder.create()
                .texOffs(64, 8)
                .addBox(-1.5F, -1.5F, -10.0F, 3.0F, 3.0F, 7.0F),
                PartPose.offset(0.0F, 18.0F, 0.0F));

        root.addOrReplaceChild("front_band", CubeListBuilder.create()
                .texOffs(80, 0)
                .addBox(-2.0F, -2.0F, 10.5F, 4.0F, 4.0F, 2.0F),
                PartPose.offset(0.0F, 18.0F, 0.0F));

        root.addOrReplaceChild("rear_band", CubeListBuilder.create()
                .texOffs(80, 6)
                .addBox(-2.5F, -2.5F, 14.5F, 5.0F, 5.0F, 2.0F),
                PartPose.offset(0.0F, 18.0F, 0.0F));

        root.addOrReplaceChild("training_tuft", CubeListBuilder.create()
                .texOffs(80, 14)
                .addBox(-3.8F, -1.0F, 18.0F, 1.0F, 2.0F, 10.0F)
                .texOffs(80, 14)
                .mirror()
                .addBox(2.8F, -1.0F, 18.0F, 1.0F, 2.0F, 10.0F),
                PartPose.offset(0.0F, 18.0F, 0.0F));

        root.addOrReplaceChild("nimbus_left_rail", CubeListBuilder.create()
                .texOffs(64, 20)
                .addBox(-2.3F, -0.5F, -8.0F, 1.0F, 1.0F, 11.0F),
                PartPose.offset(0.0F, 18.0F, 0.0F));

        // Right rail: already on +X — do not mirror() or the cube can reflect to the wrong side vs. the left rail.
        root.addOrReplaceChild("nimbus_right_rail", CubeListBuilder.create()
                .texOffs(64, 20)
                .addBox(1.3F, -0.5F, -8.0F, 1.0F, 1.0F, 11.0F),
                PartPose.offset(0.0F, 18.0F, 0.0F));

        root.addOrReplaceChild("nimbus_top_fin", CubeListBuilder.create()
                .texOffs(96, 0)
                .addBox(-1.0F, -4.1F, 16.0F, 2.0F, 1.0F, 11.0F),
                PartPose.offset(0.0F, 18.0F, 0.0F));

        root.addOrReplaceChild("nimbus_bottom_fin", CubeListBuilder.create()
                .texOffs(96, 13)
                .addBox(-1.0F, 3.1F, 16.0F, 2.0F, 1.0F, 11.0F),
                PartPose.offset(0.0F, 18.0F, 0.0F));

        root.addOrReplaceChild("firebolt_spine", CubeListBuilder.create()
                .texOffs(96, 26)
                .addBox(-1.0F, -1.0F, 14.0F, 2.0F, 2.0F, 15.0F),
                PartPose.offset(0.0F, 18.0F, 0.0F));

        root.addOrReplaceChild("firebolt_left_fin", CubeListBuilder.create()
                .texOffs(80, 39)
                .addBox(-4.0F, -0.8F, 18.0F, 1.0F, 2.0F, 10.0F),
                PartPose.offset(0.0F, 18.0F, 0.0F));

        root.addOrReplaceChild("firebolt_right_fin", CubeListBuilder.create()
                .texOffs(80, 39)
                .addBox(3.0F, -0.8F, 18.0F, 1.0F, 2.0F, 10.0F),
                PartPose.offset(0.0F, 18.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 128, 64);
    }

    public void setVariant(int broomType) {
        boolean isNimbus = broomType == 1;
        boolean isFirebolt = broomType == 2;
        boolean isTraining = !isNimbus && !isFirebolt;

        this.noseCap.visible = true;
        this.frontBand.visible = true;
        this.rearBand.visible = isTraining || isFirebolt;
        this.gripWrap.visible = !isTraining;
        this.trainingTuft.visible = isTraining;
        this.nimbusLeftRail.visible = isNimbus;
        this.nimbusRightRail.visible = isNimbus;
        this.nimbusTopFin.visible = isNimbus;
        this.nimbusBottomFin.visible = isNimbus;
        this.fireboltSpine.visible = isFirebolt;
        this.fireboltLeftFin.visible = isFirebolt;
        this.fireboltRightFin.visible = isFirebolt;
    }

    @Override
    public void setupAnim(BroomEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int loadedLight, int loadedOverlay, int color) {
        handle.render(poseStack, vertexConsumer, loadedLight, loadedOverlay, color);
        twigs.render(poseStack, vertexConsumer, loadedLight, loadedOverlay, color);
        noseCap.render(poseStack, vertexConsumer, loadedLight, loadedOverlay, color);
        gripWrap.render(poseStack, vertexConsumer, loadedLight, loadedOverlay, color);
        frontBand.render(poseStack, vertexConsumer, loadedLight, loadedOverlay, color);
        rearBand.render(poseStack, vertexConsumer, loadedLight, loadedOverlay, color);
        trainingTuft.render(poseStack, vertexConsumer, loadedLight, loadedOverlay, color);
        nimbusLeftRail.render(poseStack, vertexConsumer, loadedLight, loadedOverlay, color);
        nimbusRightRail.render(poseStack, vertexConsumer, loadedLight, loadedOverlay, color);
        nimbusTopFin.render(poseStack, vertexConsumer, loadedLight, loadedOverlay, color);
        nimbusBottomFin.render(poseStack, vertexConsumer, loadedLight, loadedOverlay, color);
        fireboltSpine.render(poseStack, vertexConsumer, loadedLight, loadedOverlay, color);
        fireboltLeftFin.render(poseStack, vertexConsumer, loadedLight, loadedOverlay, color);
        fireboltRightFin.render(poseStack, vertexConsumer, loadedLight, loadedOverlay, color);
    }
}
