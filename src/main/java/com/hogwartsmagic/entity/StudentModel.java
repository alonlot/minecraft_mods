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
import com.mojang.math.Axis;

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

	// Wand hold in translateToHand: tilt at rest per hand, plus how much it grows per radian the arms rise.
	private static final float WAND_TILT_RIGHT = 20.3F * Mth.DEG_TO_RAD;
	private static final float WAND_TILT_LEFT = 105.7F * Mth.DEG_TO_RAD;
	private static final float WAND_TILT_PER_RAISE = 1.5232F;

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

		// The hat. Blockbench exported it pivoting at the feet (y=24); it now pivots at the neck like
		// the head (boxes shifted up by 24, same world placement) so setupAnim can copy the head's
		// rotation onto it — otherwise the head turned and nodded while the hat stayed put.
		PartDefinition bone = partdefinition.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(28, 17).addBox(-5.0F, -12.0F, -5.0F, 10.0F, 3.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-6.0F, -11.0F, -6.0F, 12.0F, 5.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(48, 0).addBox(-4.0F, -16.0F, -4.0F, 8.0F, 7.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(60, 30).addBox(-3.0F, -19.0F, -3.0F, 6.0F, 10.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}

	@Override
	public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		// Head + nose track look direction. The nose is a SIBLING of head (not a child like in
		// vanilla VillagerModel), so we have to sync its rotation manually. Don't use
		// ModelPart.copyFrom — that also copies the position fields and overwrites the nose's
		// own (0, -2, 0) offset, which would shift it 2 units onto the chin instead of the face.
		this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
		this.head.xRot = headPitch * Mth.DEG_TO_RAD;
		this.nose.xRot = this.head.xRot;
		this.nose.yRot = this.head.yRot;
		this.nose.zRot = this.head.zRot;
		this.headwear.xRot = this.head.xRot;
		this.headwear.yRot = this.head.yRot;
		this.headwear.zRot = this.head.zRot;
		this.bone.xRot = this.head.xRot;
		this.bone.yRot = this.head.yRot;
		this.bone.zRot = this.head.zRot;

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
			// Prune entries of students that no longer exist (same fix as WizardModel): entity ids are
			// never reused within a session, so without this the static maps grow for as long as the client runs.
			if (student.tickCount % 200 == 0) {
				java.util.function.Predicate<Integer> gone = key -> student.level().getEntity(key) == null;
				CAST_START_TICK.keySet().removeIf(gone);
				CAST_END_TICK.keySet().removeIf(gone);
				LAST_CASTING.keySet().removeIf(gone);
			}

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

		// Arms-group local +Y points down (the linked-arm cubes extend in +Y from the arms-group
		// origin), so rotating by -π/2 about X swings them to point straight forward. We aim a
		// touch above horizontal (-1.65) so the wand reads as a level aim, not a downward jab.
		// REST_ARMS_X_ROT = -0.7505 (arms hang slightly down-forward) is the resting baseline.
		// Going past -π (e.g., the previous -2.30) flips the arms backward over the head, which
		// is why the wand looked like it was floating above the student.
		float castTarget = -1.65F;
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
		this.arms.xRot = -1.65F + snap * 0.35F;
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
		// ItemInHandLayer follows this with the humanoid hand offset (10px down a 12px arm) plus a
		// fixed -90X/180Y turn. Applied to the crossed-arms group that left the wand at thigh height
		// 8px in front of the hands, and pointing up-and-backward mid-cast. Instead: move to the
		// front of the folded hands, tilt so the wand reads ~35° up at rest and level at full cast
		// (tilt grows with the arm raise), then cancel the layer's own offset so the wand's grip
		// (15% up from the butt) is what lands on the hands. Constants are solved for
		// harry_potter_wand.json's third-person display; left and right display transforms differ.
		boolean right = arm == HumanoidArm.RIGHT;
		this.arms.translateAndRotate(poseStack);
		poseStack.translate((right ? -1.0F : 1.0F) / 16.0F, 4.0F / 16.0F, -3.5F / 16.0F);
		float tilt = (right ? WAND_TILT_RIGHT : WAND_TILT_LEFT) + WAND_TILT_PER_RAISE * (REST_ARMS_X_ROT - this.arms.xRot);
		poseStack.mulPose(Axis.XP.rotation(tilt));
		if (right) {
			poseStack.translate(1.0F / 16.0F, -10.86F / 16.0F, 1.21F / 16.0F);
		} else {
			poseStack.translate(-1.0F / 16.0F, -14.47F / 16.0F, 6.36F / 16.0F);
		}
	}
}
