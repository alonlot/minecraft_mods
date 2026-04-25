package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.HippogriffEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class HippogriffModel extends HierarchicalModel<HippogriffEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "hippogriff"), "main");

    private static float smoothstep(float value) {
        value = Mth.clamp(value, 0.0F, 1.0F);
        return value * value * (3.0F - 2.0F * value);
    }

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart chest;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart lowerBeak;
    private final ModelPart crest;
    private final ModelPart mane;
    private final ModelPart leftWing;
    private final ModelPart leftWingTip;
    private final ModelPart rightWing;
    private final ModelPart rightWingTip;
    private final ModelPart frontLeftLeg;
    private final ModelPart frontLeftShin;
    private final ModelPart frontRightLeg;
    private final ModelPart frontRightShin;
    private final ModelPart backLeftLeg;
    private final ModelPart backLeftShin;
    private final ModelPart backRightLeg;
    private final ModelPart backRightShin;
    private final ModelPart tail;
    private final ModelPart tailFan;

    public HippogriffModel(ModelPart root) {
        this.root = root.getChild("root");
        this.body = this.root.getChild("body");
        this.chest = this.body.getChild("chest");
        this.neck = this.chest.getChild("neck");
        this.head = this.neck.getChild("head");
        this.lowerBeak = this.head.getChild("lower_beak");
        this.crest = this.head.getChild("crest");
        this.mane = this.chest.getChild("mane");
        this.leftWing = this.chest.getChild("left_wing");
        this.leftWingTip = this.leftWing.getChild("left_wing_tip");
        this.rightWing = this.chest.getChild("right_wing");
        this.rightWingTip = this.rightWing.getChild("right_wing_tip");
        this.frontLeftLeg = this.body.getChild("front_left_leg");
        this.frontLeftShin = this.frontLeftLeg.getChild("front_left_shin");
        this.frontRightLeg = this.body.getChild("front_right_leg");
        this.frontRightShin = this.frontRightLeg.getChild("front_right_shin");
        this.backLeftLeg = this.body.getChild("back_left_leg");
        this.backLeftShin = this.backLeftLeg.getChild("back_left_shin");
        this.backRightLeg = this.body.getChild("back_right_leg");
        this.backRightShin = this.backRightLeg.getChild("back_right_shin");
        this.tail = this.body.getChild("tail");
        this.tailFan = this.tail.getChild("tail_fan");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition hippogriff = root.addOrReplaceChild("root", CubeListBuilder.create(),
                PartPose.offset(0.0F, 10.5F, 0.0F));

        PartDefinition body = hippogriff.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 56)
                        .addBox(-4.5F, -4.0F, -2.0F, 9.0F, 9.0F, 16.0F, new CubeDeformation(0.0F))
                        .texOffs(50, 56)
                        .addBox(-4.0F, -3.5F, 12.5F, 8.0F, 8.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 1.0F, 0.0F));

        PartDefinition chest = body.addOrReplaceChild("chest", CubeListBuilder.create()
                        .texOffs(0, 31)
                        .addBox(-5.0F, -5.0F, -8.0F, 10.0F, 10.0F, 8.0F, new CubeDeformation(0.15F)),
                PartPose.offset(0.0F, -1.5F, -2.0F));

        chest.addOrReplaceChild("mane", CubeListBuilder.create()
                        .texOffs(90, 0)
                        .addBox(-3.5F, 0.0F, -0.5F, 7.0F, 9.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -1.0F, -7.9F));

        PartDefinition neck = chest.addOrReplaceChild("neck", CubeListBuilder.create()
                        .texOffs(40, 31)
                        .addBox(-2.5F, -7.0F, -2.5F, 5.0F, 8.0F, 5.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -1.5F, -6.5F));

        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(64, 0)
                        .addBox(-3.0F, -3.0F, -5.0F, 6.0F, 5.0F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(88, 0)
                        .addBox(-1.5F, -1.3F, -8.0F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -6.5F, -0.5F));

        head.addOrReplaceChild("lower_beak", CubeListBuilder.create()
                        .texOffs(88, 8)
                        .addBox(-1.4F, 0.0F, -3.0F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 1.0F, -5.0F));

        head.addOrReplaceChild("crest", CubeListBuilder.create()
                        .texOffs(80, 14)
                        .addBox(-2.0F, -2.0F, -1.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(80, 18)
                        .addBox(-1.0F, -4.0F, -0.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -2.5F, -0.2F));

        PartDefinition leftWing = chest.addOrReplaceChild("left_wing", CubeListBuilder.create()
                        .texOffs(64, 29)
                        .addBox(0.0F, -1.0F, -2.0F, 3.0F, 8.0F, 12.0F, new CubeDeformation(0.0F)),
                PartPose.offset(4.5F, -3.5F, -2.5F));

        leftWing.addOrReplaceChild("left_wing_tip", CubeListBuilder.create()
                        .texOffs(64, 49)
                        .addBox(0.0F, -0.5F, -1.5F, 2.0F, 7.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offset(2.5F, 1.5F, 2.5F));

        PartDefinition rightWing = chest.addOrReplaceChild("right_wing", CubeListBuilder.create()
                        .texOffs(64, 29)
                        .mirror()
                        .addBox(-3.0F, -1.0F, -2.0F, 3.0F, 8.0F, 12.0F, new CubeDeformation(0.0F))
                        .mirror(false),
                PartPose.offset(-4.5F, -3.5F, -2.5F));

        rightWing.addOrReplaceChild("right_wing_tip", CubeListBuilder.create()
                        .texOffs(64, 49)
                        .mirror()
                        .addBox(-2.0F, -0.5F, -1.5F, 2.0F, 7.0F, 14.0F, new CubeDeformation(0.0F))
                        .mirror(false),
                PartPose.offset(-2.5F, 1.5F, 2.5F));

        PartDefinition frontLeftLeg = body.addOrReplaceChild("front_left_leg", CubeListBuilder.create()
                        .texOffs(0, 82)
                        .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(2.5F, 4.0F, -6.5F));

        frontLeftLeg.addOrReplaceChild("front_left_shin", CubeListBuilder.create()
                        .texOffs(10, 82)
                        .addBox(-0.8F, 0.0F, -0.8F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(20, 82)
                        .addBox(-2.0F, 6.4F, -2.7F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 7.5F, 0.0F));

        PartDefinition frontRightLeg = body.addOrReplaceChild("front_right_leg", CubeListBuilder.create()
                        .texOffs(0, 82)
                        .mirror()
                        .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
                        .mirror(false),
                PartPose.offset(-2.5F, 4.0F, -6.5F));

        frontRightLeg.addOrReplaceChild("front_right_shin", CubeListBuilder.create()
                        .texOffs(10, 82)
                        .mirror()
                        .addBox(-1.2F, 0.0F, -0.8F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F))
                        .mirror(false)
                        .texOffs(20, 82)
                        .mirror()
                        .addBox(-2.0F, 6.4F, -2.7F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                        .mirror(false),
                PartPose.offset(0.0F, 7.5F, 0.0F));

        PartDefinition backLeftLeg = body.addOrReplaceChild("back_left_leg", CubeListBuilder.create()
                        .texOffs(36, 82)
                        .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 8.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(3.0F, 4.0F, 9.0F));

        backLeftLeg.addOrReplaceChild("back_left_shin", CubeListBuilder.create()
                        .texOffs(50, 82)
                        .addBox(-1.2F, 0.0F, -1.2F, 3.0F, 7.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(64, 82)
                        .addBox(-1.2F, 6.0F, -1.2F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 7.0F, 0.0F));

        PartDefinition backRightLeg = body.addOrReplaceChild("back_right_leg", CubeListBuilder.create()
                        .texOffs(36, 82)
                        .mirror()
                        .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 8.0F, 3.0F, new CubeDeformation(0.0F))
                        .mirror(false),
                PartPose.offset(-3.0F, 4.0F, 9.0F));

        backRightLeg.addOrReplaceChild("back_right_shin", CubeListBuilder.create()
                        .texOffs(50, 82)
                        .mirror()
                        .addBox(-1.8F, 0.0F, -1.2F, 3.0F, 7.0F, 3.0F, new CubeDeformation(0.0F))
                        .mirror(false)
                        .texOffs(64, 82)
                        .mirror()
                        .addBox(-1.8F, 6.0F, -1.2F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                        .mirror(false),
                PartPose.offset(0.0F, 7.0F, 0.0F));

        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                        .texOffs(94, 52)
                        .addBox(-1.5F, -1.0F, 0.0F, 3.0F, 7.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -1.0F, 14.5F));

        tail.addOrReplaceChild("tail_fan", CubeListBuilder.create()
                        .texOffs(106, 52)
                        .addBox(-4.0F, -1.0F, 0.0F, 8.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 5.0F, 1.5F));

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(HippogriffEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        float partialTick = ageInTicks - (float) entity.tickCount;
        float walkAmount = Mth.clamp(limbSwingAmount, 0.0F, 1.0F);
        float walkCycle = limbSwing * 0.6F;
        float idle = Mth.sin(ageInTicks * 0.08F) * 0.03F;
        float headYawRad = netHeadYaw * ((float) Math.PI / 180F);
        float headPitchRad = headPitch * ((float) Math.PI / 180F);
        float flight = entity.getFlightProgress(1.0F);
        float flap = Mth.sin(entity.getWingFlap(1.0F)) * (0.2F + flight * 0.8F);
        float attackSwing = entity.getAttackAnim(partialTick);

        this.body.xRot = 0.03F - walkAmount * 0.04F - flight * 0.24F;
        this.chest.xRot = -0.03F + flight * 0.14F;
        this.neck.yRot = headYawRad * 0.32F;
        this.head.yRot = headYawRad * 0.48F;
        this.neck.xRot = -0.2F - flight * 0.16F + headPitchRad * 0.12F + idle;
        this.head.xRot = 0.02F + headPitchRad * 0.28F + idle;
        this.lowerBeak.xRot = 0.04F;
        this.crest.xRot = -0.04F;
        this.mane.xRot = 0.04F + idle;

        float wingBaseFold = Mth.lerp(flight, 0.5F, 1.25F);
        float wingTipFold = Mth.lerp(flight, 0.7F, 1.4F);
        this.leftWing.zRot = -(wingBaseFold + flap * 0.85F);
        this.rightWing.zRot = wingBaseFold + flap * 0.85F;
        this.leftWing.xRot = -0.08F + flight * 0.18F;
        this.rightWing.xRot = -0.08F + flight * 0.18F;
        this.leftWingTip.zRot = -(wingTipFold + flap * 0.45F);
        this.rightWingTip.zRot = wingTipFold + flap * 0.45F;

        float frontWalk = Mth.cos(walkCycle) * 0.85F * walkAmount;
        float backWalk = Mth.cos(walkCycle + (float) Math.PI) * 0.75F * walkAmount;
        this.frontLeftLeg.xRot = Mth.lerp(flight, frontWalk, -1.1F);
        this.frontRightLeg.xRot = Mth.lerp(flight, -frontWalk, -1.1F);
        this.frontLeftShin.xRot = Mth.lerp(flight, 0.18F + Mth.cos(walkCycle + 0.4F) * 0.18F * walkAmount, 0.95F);
        this.frontRightShin.xRot = Mth.lerp(flight, 0.18F + Mth.cos(walkCycle + (float) Math.PI + 0.4F) * 0.18F * walkAmount, 0.95F);
        this.backLeftLeg.xRot = Mth.lerp(flight, backWalk, 0.85F);
        this.backRightLeg.xRot = Mth.lerp(flight, -backWalk, 0.85F);
        this.backLeftShin.xRot = Mth.lerp(flight, 0.25F + Mth.cos(walkCycle + (float) Math.PI) * 0.12F * walkAmount, -0.4F);
        this.backRightShin.xRot = Mth.lerp(flight, 0.25F + Mth.cos(walkCycle) * 0.12F * walkAmount, -0.4F);

        this.tail.xRot = 0.25F - flight * 0.12F + Mth.sin(ageInTicks * 0.1F) * 0.05F;
        this.tail.yRot = Mth.cos(walkCycle) * 0.05F * walkAmount;
        this.tailFan.xRot = 0.08F;

        if (attackSwing > 0.0F && flight < 0.7F) {
            float rear;
            float slam;
            if (attackSwing < 0.45F) {
                rear = smoothstep(attackSwing / 0.45F);
                slam = 0.0F;
            } else {
                rear = 1.0F - smoothstep((attackSwing - 0.45F) / 0.55F);
                slam = smoothstep((attackSwing - 0.45F) / 0.2F);
            }

            this.body.xRot -= rear * 0.35F;
            this.chest.xRot -= rear * 0.45F;
            this.neck.xRot -= rear * 0.12F;
            this.head.xRot += rear * 0.24F;
            this.frontLeftLeg.xRot = -1.95F * rear + 1.3F * slam;
            this.frontRightLeg.xRot = -1.95F * rear + 1.3F * slam;
            this.frontLeftShin.xRot = 1.1F * rear - 0.85F * slam;
            this.frontRightShin.xRot = 1.1F * rear - 0.85F * slam;
            this.backLeftLeg.xRot += rear * 0.25F;
            this.backRightLeg.xRot += rear * 0.25F;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
        this.root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }
}
