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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

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
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;

        // ---- Hover + drift ----
        // Multi-frequency vertical bob (large slow wave + small flutter) and a side-to-side pendulum.
        float bigBob = Mth.sin(ageInTicks * 0.10F) * 0.55F;
        float flutter = Mth.sin(ageInTicks * 0.35F + 1.2F) * 0.12F;
        float vertical = bigBob + flutter;
        float pendulum = Mth.sin(ageInTicks * 0.07F) * 0.06F;  // body sway (radians)

        this.body.y = -12.0F + vertical;
        this.head.y = -12.0F + vertical;
        this.rightArm.y = -10.0F + vertical;
        this.leftArm.y = -10.0F + vertical;
        this.lowerCloak.y = 4.0F + vertical * 0.8F;

        // Wraith sway — slight zRot pendulum on everything except the head (head pivots independently).
        this.body.zRot = pendulum;
        this.lowerCloak.zRot = pendulum * 1.4F + Mth.sin(ageInTicks * 0.18F) * 0.04F;

        // ---- Target awareness intensity ----
        // 0 = no target/far away, 1 = right on top of victim. Drives sucking arm reach + tremor speed.
        float intensity = 0.0F;
        LivingEntity target = entity.getTarget();
        if (target != null) {
            double distSq = entity.distanceToSqr(target);
            if (distSq < 256.0D) {
                intensity = (float) (1.0 - Math.sqrt(distSq) / 16.0);
                intensity = Mth.clamp(intensity, 0.0F, 1.0F);
            }
        }

        // ---- Arms ----
        // Idle: hang loose with subtle drift.
        // Hunting: claw forward and spread, with rising tremor frequency as intensity climbs.
        float idleX = -0.35F;  // base droop
        float reachX = -1.55F; // hands-forward-and-up
        float armX = Mth.lerp(intensity, idleX, reachX);
        float tremorSpeed = 0.18F + intensity * 0.45F;
        float tremor = Mth.sin(ageInTicks * tremorSpeed) * (0.05F + intensity * 0.10F);

        this.rightArm.xRot = armX + tremor;
        this.leftArm.xRot = armX + Mth.sin(ageInTicks * tremorSpeed + 1.3F) * (0.05F + intensity * 0.10F);

        // Spread the arms outward (zRot) as the dementor reaches.
        this.rightArm.zRot = Mth.lerp(intensity, 0.10F, 0.45F);
        this.leftArm.zRot = Mth.lerp(intensity, -0.10F, -0.45F);

        // And forward yaw — arms come around to embrace.
        this.rightArm.yRot = Mth.lerp(intensity, 0.0F, -0.30F);
        this.leftArm.yRot = Mth.lerp(intensity, 0.0F, 0.30F);

        // ---- Torso lean toward target ----
        // When closing on a victim, body pitches forward; relaxes when idle.
        this.body.xRot = Mth.lerp(intensity, 0.0F, 0.22F);

        // ---- Cloak ----
        // Multi-frequency ripple on the trailing hem + intensity-driven backward flare during the rush.
        float baseTrail = 0.26F;  // ~15 degrees rest tilt
        float ripple = Mth.sin(ageInTicks * 0.08F) * 0.10F + Mth.sin(ageInTicks * 0.22F + 0.7F) * 0.05F;
        this.lowerCloak.xRot = baseTrail + ripple + Mth.lerp(intensity, 0.0F, 0.18F);
        this.lowerCloak.yRot = Mth.sin(ageInTicks * 0.11F) * 0.06F;
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
