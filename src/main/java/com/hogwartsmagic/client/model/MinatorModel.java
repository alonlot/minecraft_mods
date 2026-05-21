package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.MinatorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.ArmedModel;
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
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Items;

/**
 * Half-pillager (upper) and half-horse (body, legs, tail). Uses a 128×64 atlas:
 * pillager UVs in x=[0,64), horse UVs in x=[64,128).
 */
public class MinatorModel extends EntityModel<MinatorEntity> implements ArmedModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "minator"), "main");

    private static float smoothstep(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * Front-leg stomp: 0–~40% wind-up (hooves lift), ~40–52% fast slam down, then recover.
     * Returns a "hoof height" curve: 0 = neutral, 1 = max lifted, slam drives past toward extension.
     */
    private static void frontLegStompAngles(float attackSwing, float[] outLeftRight) {
        final float WIND = 0.40F;
        final float STRIKE = 0.14F;
        final float RECOVER = 1.0F - WIND - STRIKE;

        float fl;
        float fr;
        if (attackSwing < WIND) {
            float u = smoothstep(attackSwing / WIND);
            // Lift knees / hooves up and slightly back
            fl = Mth.lerp(u, 0.0F, -1.05F);
            fr = Mth.lerp(u, 0.0F, -0.92F);
        } else if (attackSwing < WIND + STRIKE) {
            float u = (attackSwing - WIND) / STRIKE;
            // Ease-in for a sharp downward strike
            float k = u * u;
            fl = Mth.lerp(k, -1.05F, 0.72F);
            fr = Mth.lerp(k, -0.92F, 0.65F);
        } else {
            float u = (attackSwing - WIND - STRIKE) / Math.max(RECOVER, 0.01F);
            u = smoothstep(u);
            fl = Mth.lerp(u, 0.72F, 0.08F);
            fr = Mth.lerp(u, 0.65F, 0.06F);
        }
        outLeftRight[0] = fl;
        outLeftRight[1] = fr;
    }

    private final ModelPart pillager;
    private final ModelPart head2;
    private final ModelPart body2;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart horse;
    private final ModelPart body;
    private final ModelPart tail;
    private final ModelPart frontLeftLeg;
    private final ModelPart frontRightLeg;
    private final ModelPart backLeftLeg;
    private final ModelPart backRightLeg;

    public MinatorModel(ModelPart root) {
        this.pillager = root.getChild("pillager");
        this.head2 = this.pillager.getChild("head2");
        this.body2 = this.pillager.getChild("body2");
        this.leftArm = this.pillager.getChild("left_arm");
        this.rightArm = this.pillager.getChild("right_arm");
        this.horse = root.getChild("horse");
        this.body = this.horse.getChild("body");
        this.tail = this.horse.getChild("tail");
        this.frontLeftLeg = this.horse.getChild("front_left_leg");
        this.frontRightLeg = this.horse.getChild("front_right_leg");
        this.backLeftLeg = this.horse.getChild("back_left_leg");
        this.backRightLeg = this.horse.getChild("back_right_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition pillager = partdefinition.addOrReplaceChild("pillager", CubeListBuilder.create(),
                PartPose.offset(0.0F, -6.0F, -8.0F));

        PartDefinition head2 = pillager.addOrReplaceChild("head2", CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        // Nose is parented to the head so it rotates with the face; nudged up vs. old sibling pivot.
        head2.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(24, 0)
                        .addBox(-1.0F, -1.0F, -6.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -3.25F, 0.35F));

        pillager.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        pillager.addOrReplaceChild("body2", CubeListBuilder.create().texOffs(16, 20)
                        .addBox(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        pillager.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 46)
                        .addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(5.0F, 2.0F, 0.0F));

        pillager.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 46)
                        .addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-5.0F, 2.0F, 0.0F));

        PartDefinition horse = partdefinition.addOrReplaceChild("horse", CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        horse.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0.0F, -22.0F, -9.0F));
        horse.addOrReplaceChild("mouth", CubeListBuilder.create(), PartPose.offset(0.0F, -22.0F, -9.0F));
        horse.addOrReplaceChild("left_ear", CubeListBuilder.create(), PartPose.offset(0.0F, -23.0F, -9.01F));
        horse.addOrReplaceChild("right_ear", CubeListBuilder.create(), PartPose.offset(0.0F, -23.0F, -9.01F));
        horse.addOrReplaceChild("neck", CubeListBuilder.create(), PartPose.offset(0.0F, -22.0F, -9.0F));
        horse.addOrReplaceChild("mane", CubeListBuilder.create(), PartPose.offset(0.0F, -22.0F, -9.01F));

        horse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(64, 32)
                        .addBox(-5.0F, -8.0F, -17.0F, 10.0F, 10.0F, 22.0F, new CubeDeformation(0.05F)),
                PartPose.offset(0.0F, -13.0F, 6.0F));

        horse.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(106, 36)
                        .addBox(-1.5F, 0.0F, 0.0F, 3.0F, 14.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -21.0F, 11.0F));

        horse.addOrReplaceChild("front_left_leg", CubeListBuilder.create().texOffs(112, 21).mirror()
                        .addBox(-3.0F, -1.0F, -1.9F, 4.0F, 11.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offset(4.0F, -10.0F, -9.0F));

        horse.addOrReplaceChild("front_right_leg", CubeListBuilder.create().texOffs(112, 21)
                        .addBox(-1.0F, -1.0F, -1.9F, 4.0F, 11.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-4.0F, -10.0F, -9.0F));

        horse.addOrReplaceChild("back_left_leg", CubeListBuilder.create().texOffs(112, 21).mirror()
                        .addBox(-3.0F, -1.0F, -1.0F, 4.0F, 11.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offset(4.0F, -10.0F, 8.0F));

        horse.addOrReplaceChild("back_right_leg", CubeListBuilder.create().texOffs(112, 21)
                        .addBox(-1.0F, -1.0F, -1.0F, 4.0F, 11.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-4.0F, -10.0F, 8.0F));

        return LayerDefinition.create(meshdefinition, 128, 64);
    }

    @Override
    public void setupAnim(MinatorEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        this.head2.yRot = netHeadYaw * ((float) Math.PI / 180F);
        this.head2.xRot = headPitch * ((float) Math.PI / 180F);

        float partialTick = ageInTicks - (float) entity.tickCount;
        float attackSwing = entity.getAttackAnim(partialTick);

        // Horse-half locomotion: 4-beat trot at walk speed, broadens at higher limbSwingAmount.
        float walk = Mth.cos(limbSwing * 0.6662F) * 1.2F * limbSwingAmount;
        this.frontLeftLeg.xRot = walk;
        this.frontRightLeg.xRot = -walk;
        this.backLeftLeg.xRot = -walk;
        this.backRightLeg.xRot = walk;

        // Pillager torso bobs vertically with footfalls so the humanoid half doesn't look pasted on
        // top of a moving horse. Idle breathing on top.
        float bob = Mth.sin(limbSwing * 1.3324F) * 0.6F * limbSwingAmount;
        this.pillager.y = -6.0F + bob;
        this.pillager.xRot = bob * 0.04F + Mth.sin(ageInTicks * 0.06F) * 0.02F;

        // Tail: subtle idle sway + amplified swish when galloping.
        this.tail.xRot = 0.25F + Mth.sin(ageInTicks * 0.12F) * 0.07F;
        this.tail.yRot = Mth.cos(limbSwing * 0.6662F) * 0.30F * limbSwingAmount;

        if (attackSwing > 0.0F) {
            float[] fr = new float[2];
            frontLegStompAngles(attackSwing, fr);
            this.frontLeftLeg.xRot = fr[0];
            this.frontRightLeg.xRot = fr[1];

            float br = 0.22F * Mth.sin(attackSwing * Mth.PI);
            this.backLeftLeg.xRot = br;
            this.backRightLeg.xRot = -br;

            float coil = attackSwing < 0.38F ? smoothstep(attackSwing / 0.38F) : (1.0F - smoothstep((attackSwing - 0.38F) / 0.62F));
            this.rightArm.xRot = -0.35F * coil;
            this.leftArm.xRot = -0.55F * coil;
            this.rightArm.yRot = 0.0F;
            this.leftArm.yRot = 0.0F;
            this.rightArm.zRot = 0.0F;
            this.leftArm.zRot = 0.0F;
            return;
        }

        boolean bowMain = entity.getMainHandItem().is(Items.BOW);
        if (!bowMain) {
            // No bow: arms swing opposite to legs.
            this.rightArm.yRot = 0.0F;
            this.leftArm.yRot = 0.0F;
            this.rightArm.zRot = 0.0F;
            this.leftArm.zRot = 0.0F;
            this.rightArm.xRot = Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 0.6F * limbSwingAmount;
            this.leftArm.xRot = Mth.cos(limbSwing * 0.6662F) * 0.6F * limbSwingAmount;
            return;
        }

        // Bow path. Two states:
        //   1) isUsingItem()  -> drawing the bowstring (the actual aim/charge)
        //   2) isAggressive() -> nocking / ready (between draws)
        //   3) otherwise      -> casual carry, wobbles with gait
        if (entity.isUsingItem()) {
            int drawTicks = entity.getTicksUsingItem();
            float draw = Math.min(1.0F, drawTicks / 18.0F);
            float ease = smoothstep(draw);

            // Right arm holds the bow up and forward (the entity is left-handed=false → main hand right).
            // Bow pose: arm raised horizontally toward target, slight outward yaw.
            this.rightArm.xRot = -1.45F;
            this.rightArm.yRot = -0.20F;
            this.rightArm.zRot = -0.05F;

            // Left arm pulls the string back: starts at the bow grip and retreats toward the shoulder.
            // xRot rotates further forward as the draw deepens; yRot pulls across the chest.
            this.leftArm.xRot = Mth.lerp(ease, -1.10F, -1.55F);
            this.leftArm.yRot = Mth.lerp(ease, -0.10F, -0.55F);
            this.leftArm.zRot = Mth.lerp(ease, 0.05F, 0.18F);

            // Brace: torso rotates slightly toward the bow side.
            this.pillager.yRot = -0.10F * ease;

            // Tiny aim-jitter at full draw, like holding tension on the string.
            if (ease > 0.85F) {
                float jitter = Mth.sin(ageInTicks * 1.5F) * 0.015F;
                this.rightArm.xRot += jitter;
                this.leftArm.xRot += jitter * 0.5F;
            }
        } else if (entity.isAggressive()) {
            // Ready stance: bow up but string relaxed.
            this.rightArm.xRot = -1.18F;
            this.leftArm.xRot = -1.02F;
            this.rightArm.yRot = -0.12F;
            this.leftArm.yRot = 0.18F;
            this.rightArm.zRot = 0.0F;
            this.leftArm.zRot = 0.0F;
            this.pillager.yRot = 0.0F;
        } else {
            // Casual carry with subtle gait wobble.
            float wobble = Mth.cos(limbSwing * 0.6662F) * 0.12F * limbSwingAmount;
            this.rightArm.xRot = -0.58F + wobble;
            this.leftArm.xRot = -0.44F - wobble;
            this.rightArm.yRot = 0.0F;
            this.leftArm.yRot = 0.0F;
            this.rightArm.zRot = 0.0F;
            this.leftArm.zRot = 0.0F;
            this.pillager.yRot = 0.0F;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
                               int color) {
        this.pillager.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
        this.horse.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }

    @Override
    public void translateToHand(HumanoidArm arm, PoseStack poseStack) {
        this.pillager.translateAndRotate(poseStack);
        if (arm == HumanoidArm.RIGHT) {
            this.rightArm.translateAndRotate(poseStack);
        } else {
            this.leftArm.translateAndRotate(poseStack);
        }
        poseStack.translate(0.0D, 0.95D / 16.0D, 1.0D / 16.0D);
    }
}
