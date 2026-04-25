// Made with Blockbench 5.0.7
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports

package com.hogwartsmagic.entity;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public class StudentModel<T extends Entity> extends EntityModel<T> implements net.minecraft.client.model.ArmedModel {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "student_main"), "main");
	private final ModelPart head;
	private final ModelPart nose;
	private final ModelPart headwear;
	private final ModelPart headwear2;
	private final ModelPart body;
	private final ModelPart bodywear;
	private final ModelPart arms;
	private final ModelPart mirrored;
	private final ModelPart right_leg;
	private final ModelPart left_leg;
	private final ModelPart bone;

	public StudentModel(ModelPart root) {
		this.head = root.getChild("head");
		this.nose = root.getChild("nose");
		this.headwear = root.getChild("headwear");
		this.headwear2 = root.getChild("headwear2");
		this.body = root.getChild("body");
		this.bodywear = root.getChild("bodywear");
		this.arms = root.getChild("arms");
		this.mirrored = this.arms.getChild("mirrored");
		this.right_leg = root.getChild("right_leg");
		this.left_leg = root.getChild("left_leg");
		this.bone = root.getChild("bone");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition head = partdefinition.addOrReplaceChild("head", CubeListBuilder.create().texOffs(28, 30).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition nose = partdefinition.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(16, 66).addBox(-1.0F, -1.0F, -6.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -2.0F, 0.0F));

		PartDefinition headwear = partdefinition.addOrReplaceChild("headwear", CubeListBuilder.create().texOffs(0, 48).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F, new CubeDeformation(0.51F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition headwear2 = partdefinition.addOrReplaceChild("headwear2", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -1.5708F, 0.0F, 0.0F));

		PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create().texOffs(32, 48).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition bodywear = partdefinition.addOrReplaceChild("bodywear", CubeListBuilder.create().texOffs(0, 17).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 20.0F, 6.0F, new CubeDeformation(0.5F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition arms = partdefinition.addOrReplaceChild("arms", CubeListBuilder.create().texOffs(60, 62).addBox(-4.0F, 2.0F, -2.0F, 8.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 66).addBox(-8.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 2.95F, -1.05F, -0.7505F, 0.0F, 0.0F));

		PartDefinition mirrored = arms.addOrReplaceChild("mirrored", CubeListBuilder.create().texOffs(0, 66).mirror().addBox(4.0F, -23.05F, -3.05F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 21.05F, 1.05F));

		PartDefinition right_leg = partdefinition.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(60, 46).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.0F, 12.0F, 0.0F));

		PartDefinition left_leg = partdefinition.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(60, 46).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(2.0F, 12.0F, 0.0F));

		PartDefinition bone = partdefinition.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(28, 17).addBox(-5.0F, -36.0F, -5.0F, 10.0F, 3.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-6.0F, -35.0F, -6.0F, 12.0F, 5.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(48, 0).addBox(-4.0F, -40.0F, -4.0F, 8.0F, 7.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(60, 30).addBox(-3.0F, -43.0F, -3.0F, 6.0F, 10.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}

	@Override
	public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
		head.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		nose.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		headwear.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		headwear2.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		body.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		bodywear.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		arms.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		right_leg.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		left_leg.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		bone.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
	}

	@Override
	public void translateToHand(HumanoidArm arm, PoseStack poseStack) {
		this.arms.translateAndRotate(poseStack);
		// Move the item to the hand
		poseStack.translate(0.0D, 0.7D, 0.0D);
		// Rotate it to point forward (like a wand)
		poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90.0F));
	}
}