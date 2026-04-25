package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.WizardEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class WizardModel<T extends WizardEntity> extends EntityModel<T> implements net.minecraft.client.model.ArmedModel {
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "wizard"), "main");
	private final ModelPart head;
	private final ModelPart nose;
	private final ModelPart body;
	private final ModelPart arms;
	private final ModelPart left_arm;
	private final ModelPart right_arm;
	private final ModelPart left_leg;
	private final ModelPart right_leg;
	private final ModelPart hat; // Renamed from 'bone' in the export as it looks like a hat
	private boolean isFlying;

	public WizardModel(ModelPart root) {
		this.head = root.getChild("head");
		this.nose = root.getChild("nose");
		this.body = root.getChild("body");
		this.arms = root.getChild("arms");
		this.left_arm = root.getChild("left_arm");
		this.right_arm = root.getChild("right_arm");
		this.left_leg = root.getChild("left_leg");
		this.right_leg = root.getChild("right_leg");
		this.hat = this.head.getChild("bone");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition head = partdefinition.addOrReplaceChild("head", CubeListBuilder.create().texOffs(28, 26).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition nose = partdefinition.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(0, 59).addBox(-1.0F, -3.0F, -6.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -2.0F, 0.0F));

		PartDefinition hat = head.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(28, 15).addBox(-5.0F, -12.0F, -5.0F, 10.0F, 1.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-6.0F, -11.0F, -6.0F, 12.0F, 3.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(28, 44).addBox(-4.0F, -16.0F, -4.0F, 8.0F, 5.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(48, 0).addBox(-3.0F, -19.0F, -3.0F, 6.0F, 8.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 41).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(0, 15).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 20.0F, 6.0F, new CubeDeformation(0.5F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition arms = partdefinition.addOrReplaceChild("arms", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 2.95F, -1.05F, -0.7505F, 0.0F, 0.0F));
		arms.addOrReplaceChild("mirrored", CubeListBuilder.create(), PartPose.offset(0.0F, 21.05F, 1.05F));

		PartDefinition left_arm = partdefinition.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(28, 57).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(5.0F, 2.0F, 0.0F));

		PartDefinition right_arm = partdefinition.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(28, 57).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.0F, 2.0F, 0.0F));

		PartDefinition left_leg = partdefinition.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(44, 57).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(2.0F, 12.0F, 0.0F));

		PartDefinition right_leg = partdefinition.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(44, 57).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.0F, 12.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		this.isFlying = entity.isFlying();
		this.head.yRot = netHeadYaw * ((float)Math.PI / 180F);
		this.head.xRot = headPitch * ((float)Math.PI / 180F);
		this.nose.copyFrom(this.head);

		this.left_leg.xRot = Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
		this.right_leg.xRot = Mth.cos(limbSwing * 0.6662F + (float)Math.PI) * 1.4F * limbSwingAmount;
		
		if (entity.isCasting()) {
			this.right_arm.xRot = -1.5F;
			this.left_arm.xRot = -1.5F;
			this.left_arm.visible = true;
			this.right_arm.visible = true;
		} else if (entity.isAggressive()) {
			this.right_arm.xRot = -0.8F;
			this.left_arm.xRot = -0.8F;
			this.left_arm.visible = true;
			this.right_arm.visible = true;
		} else {
			this.left_arm.visible = true;
			this.right_arm.visible = true;
			this.left_arm.xRot = 0.0F;
			this.right_arm.xRot = 0.0F;
		}
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
		if (this.isFlying) return;
		head.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		nose.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		body.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		left_arm.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		right_arm.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		left_leg.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		right_leg.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
	}

	@Override
	public void translateToHand(net.minecraft.world.entity.HumanoidArm arm, PoseStack poseStack) {
		if (arm == net.minecraft.world.entity.HumanoidArm.RIGHT) {
			this.right_arm.translateAndRotate(poseStack);
		} else {
			this.left_arm.translateAndRotate(poseStack);
		}
		// Move the item to the end of the long arm (12 pixels down)
		poseStack.translate(0.0D, 0.7D, 0.0D);
		// Rotate it to point forward (Harry Potter style)
		poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90.0F));
	}
}
