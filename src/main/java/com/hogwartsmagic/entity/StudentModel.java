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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.HashMap;
import java.util.Map;

public class StudentModel<T extends Entity> extends EntityModel<T> implements net.minecraft.client.model.ArmedModel {
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "student_main"), "main");

	// Per-entity client-side cast tracking, keyed by entity id (same pattern as WizardModel).
	private static final Map<Integer, Integer> CAST_START_TICK = new HashMap<>();
	private static final Map<Integer, Integer> CAST_END_TICK = new HashMap<>();
	private static final Map<Integer, Boolean> LAST_CASTING = new HashMap<>();
	private static final int RELEASE_RECOIL_DURATION = 8;

	// Rest pose of the arms group (matches the part definition rotation).
	private static final float REST_ARMS_X_ROT = -0.7505F;

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
		// Head + nose track look direction.
		this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
		this.head.xRot = headPitch * Mth.DEG_TO_RAD;
		this.nose.copyFrom(this.head);
		this.headwear.copyFrom(this.head);

		// Walk: legs swing opposite, slight body sway with footfalls.
		float walk = Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
		this.right_leg.xRot = walk;
		this.left_leg.xRot = -walk;
		this.body.yRot = Mth.sin(limbSwing * 0.6662F) * 0.05F * limbSwingAmount;

		// Reset arm group to its rest pose before applying overlays.
		this.arms.xRot = REST_ARMS_X_ROT;
		this.arms.yRot = 0.0F;
		this.arms.zRot = 0.0F;

		if (entity instanceof StudentEntity student) {
			int id = student.getId();
			boolean casting = student.isCasting();
			boolean wasCasting = LAST_CASTING.getOrDefault(id, false);
			if (casting && !wasCasting) {
				CAST_START_TICK.put(id, student.tickCount);
				CAST_END_TICK.remove(id);
			} else if (!casting && wasCasting) {
				CAST_END_TICK.put(id, student.tickCount);
				CAST_START_TICK.remove(id);
			}
			LAST_CASTING.put(id, casting);

			if (casting) {
				int elapsed = Math.max(0, student.tickCount - CAST_START_TICK.getOrDefault(id, student.tickCount));
				applyCastPose(elapsed, ageInTicks);
			} else if (CAST_END_TICK.containsKey(id)) {
				int sinceEnd = student.tickCount - CAST_END_TICK.get(id);
				if (sinceEnd < RELEASE_RECOIL_DURATION) {
					applyReleaseRecoil(sinceEnd);
				} else {
					CAST_END_TICK.remove(id);
				}
			}
		}
	}

	/** Both arms swing up as a unit (single arm group), with sine tremor while channeling. */
	private void applyCastPose(int elapsed, float ageInTicks) {
		float windup = Math.min(1.0F, elapsed / 6.0F);
		float ease = windup * windup * (3.0F - 2.0F * windup);

		// Arms group rotates further forward from rest (REST is already -0.75).
		float castTarget = -2.30F;
		this.arms.xRot = Mth.lerp(ease, REST_ARMS_X_ROT, castTarget);

		// Channeling tremor on top.
		float tremor = Mth.sin(ageInTicks * 0.9F) * 0.05F * ease;
		this.arms.xRot += tremor;
		this.arms.yRot = Mth.cos(ageInTicks * 0.7F) * 0.04F * ease;

		// Body leans forward slightly into the cast.
		this.body.yRot += -0.06F * ease;
	}

	/** Brief snap and settle when the spell launches. */
	private void applyReleaseRecoil(int sinceEnd) {
		float p = Math.min(1.0F, sinceEnd / (float) RELEASE_RECOIL_DURATION);
		float snap = (float) Math.sin(Math.min(1.0F, p * 1.6F) * Mth.PI);
		this.arms.xRot = -2.30F + snap * 0.55F;
		this.body.yRot += -0.06F * (1.0F - p);
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
