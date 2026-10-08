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

import java.util.HashMap;
import java.util.Map;

public class WizardModel<T extends WizardEntity> extends EntityModel<T> implements net.minecraft.client.model.ArmedModel {
	// Per-entity client-side cast tracking. Records the entity.tickCount at which casting started
	// (for the rising windup) and at which casting ended (for the release recoil). Keyed by entity id
	// so multiple wizards on screen animate independently.
	private static final Map<Integer, Integer> CAST_START_TICK = new HashMap<>();
	private static final Map<Integer, Integer> CAST_END_TICK = new HashMap<>();
	private static final Map<Integer, Boolean> LAST_CASTING = new HashMap<>();
	private static final int RELEASE_RECOIL_DURATION = 8;

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
		// Nose is a sibling of head (not a child), so sync rotation only. copyFrom would also
		// copy the position fields and overwrite the nose's own (0, -2, 0) offset.
		this.nose.xRot = this.head.xRot;
		this.nose.yRot = this.head.yRot;
		this.nose.zRot = this.head.zRot;

		this.left_leg.xRot = Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
		this.right_leg.xRot = Mth.cos(limbSwing * 0.6662F + (float)Math.PI) * 1.4F * limbSwingAmount;

		this.left_arm.visible = true;
		this.right_arm.visible = true;
		this.left_arm.xRot = 0.0F;
		this.right_arm.xRot = 0.0F;
		this.left_arm.yRot = 0.0F;
		this.right_arm.yRot = 0.0F;
		this.left_arm.zRot = 0.0F;
		this.right_arm.zRot = 0.0F;
		this.body.yRot = 0.0F;

		int id = entity.getId();
		boolean casting = entity.isCasting();
		boolean wasCasting = LAST_CASTING.getOrDefault(id, false);
		if (casting && !wasCasting) {
			CAST_START_TICK.put(id, entity.tickCount);
			CAST_END_TICK.remove(id);
		} else if (!casting && wasCasting) {
			CAST_END_TICK.put(id, entity.tickCount);
			CAST_START_TICK.remove(id);
		}
		LAST_CASTING.put(id, casting);
		// Prune entries of wizards that no longer exist: entity ids are never reused within a session, so
		// without this the static maps grow for as long as the client runs.
		if (entity.tickCount % 200 == 0) {
			java.util.function.Predicate<Integer> gone = key -> entity.level().getEntity(key) == null;
			CAST_START_TICK.keySet().removeIf(gone);
			CAST_END_TICK.keySet().removeIf(gone);
			LAST_CASTING.keySet().removeIf(gone);
		}

		// Aim the wand at the target by matching the arm pitch to where the head is looking.
		// The AI keeps the wizard's head locked on the target every tick via getLookControl, so
		// headPitch passed into setupAnim reflects current aim; converting to radians and adding
		// to a base-horizontal-forward angle (-π/2) gives an arm direction that tracks the target.
		float headPitchRad = headPitch * ((float) Math.PI / 180F);
		float aimX = -Mth.HALF_PI + headPitchRad;
		float headYawRad = netHeadYaw * ((float) Math.PI / 180F);

		if (casting) {
			int elapsed = Math.max(0, entity.tickCount - CAST_START_TICK.getOrDefault(id, entity.tickCount));
			applyCastPose(elapsed, ageInTicks, aimX, headYawRad);
		} else if (CAST_END_TICK.containsKey(id)) {
			int sinceEnd = entity.tickCount - CAST_END_TICK.get(id);
			if (sinceEnd < RELEASE_RECOIL_DURATION) {
				applyReleaseRecoil(sinceEnd, aimX, headYawRad);
			} else {
				CAST_END_TICK.remove(id);
				if (entity.isAggressive()) applyAggressivePose();
			}
		} else if (entity.isAggressive()) {
			applyAggressivePose();
		}

		// Voldemort gets an extra layer of menace on top of the standard wizard rig:
		// idle robe sway, theatrical AK windup, and a true hover pose during Tenebris Flight.
		// Applied *after* the standard cast/recoil so it can override per-bone where needed.
		if (entity instanceof com.hogwartsmagic.entity.VoldemortEntity v) {
			applyVoldemortFlourish(v, limbSwing, limbSwingAmount, ageInTicks, casting);
		}
	}

	/**
	 * Voldemort-specific animation overrides. Layered on top of the standard wizard
	 * pose so we can keep all the cast/recoil logic intact and only patch the parts
	 * that should feel different for the boss.
	 *
	 * - Hovering (Tenebris Flight active): legs stop walking and hang straight, body
	 *   tilts slightly forward, arms float outward as if held aloft by magic.
	 * - AK windup: head tilts back, both arms slowly raise above the head while
	 *   the right (wand) arm pulls back like a slingshot.
	 * - Idle: subtle body sway around Y when stationary, so he's never fully static.
	 */
	private void applyVoldemortFlourish(com.hogwartsmagic.entity.VoldemortEntity v,
	                                    float limbSwing, float limbSwingAmount,
	                                    float ageInTicks, boolean casting) {
		boolean hovering = v.isHovering();
		boolean chargingAvada = v.isChargingAvadaKedavra();

		if (hovering) {
			// Kill the walk cycle — legs hang together, slightly bent.
			this.left_leg.xRot = 0.12F;
			this.right_leg.xRot = 0.12F;
			this.left_leg.zRot = -0.05F;
			this.right_leg.zRot = 0.05F;
			// Forward body tilt — like flying head-first.
			this.body.xRot = 0.18F + Mth.sin(ageInTicks * 0.1F) * 0.04F;
			// Robes drift sideways during flight if we aren't casting.
			if (!casting) {
				this.left_arm.xRot = -0.35F + Mth.sin(ageInTicks * 0.12F) * 0.05F;
				this.right_arm.xRot = -0.35F + Mth.cos(ageInTicks * 0.12F) * 0.05F;
				this.left_arm.zRot = 0.6F;
				this.right_arm.zRot = -0.6F;
			}
		} else {
			this.body.xRot = 0.0F;
		}

		if (chargingAvada) {
			// Override the standard cast pose with the theatrical AK windup. The base
			// cast logic already pointed the wand arm toward the target; we lift both
			// arms higher and pull the wand back so the spell looks like he's gathering
			// the killing curse before the strike.
			float charge = Mth.clamp((ageInTicks % 60.0F) / 60.0F, 0.0F, 1.0F);
			float lift = -1.4F - charge * 0.35F; // higher than horizontal, rising
			this.right_arm.xRot = lift + Mth.sin(ageInTicks * 1.1F) * 0.04F;
			this.right_arm.zRot = -0.25F - charge * 0.25F;
			this.left_arm.xRot = -1.1F - charge * 0.2F;
			this.left_arm.zRot = 0.45F + charge * 0.2F;
			// Head tilts back to look up the wand.
			this.head.xRot = Math.min(this.head.xRot - 0.35F, -0.25F);
			this.nose.xRot = this.head.xRot;
			// Spine arches back to match.
			this.body.xRot = -0.12F * charge;
		}

		// Idle robe sway — only when basically stationary, not casting, not hovering.
		if (!hovering && !casting && limbSwingAmount < 0.05F) {
			float sway = Mth.sin(ageInTicks * 0.07F) * 0.08F;
			this.body.yRot += sway;
			this.left_arm.zRot += sway * 0.5F;
			this.right_arm.zRot -= sway * 0.5F;
		}
	}

	/** Cast windup: right (wand) arm rises forward over ~6 ticks aiming at the target, then
	 * holds with a channeling tremor. Left arm gives subtle counterbalance. Body leans into the cast. */
	private void applyCastPose(int elapsed, float ageInTicks, float aimX, float headYawRad) {
		float windup = Math.min(1.0F, elapsed / 6.0F);
		float ease = windup * windup * (3.0F - 2.0F * windup); // smoothstep

		// Right arm aims wherever the head is looking — that's the target, since the AI keeps
		// the head locked on it. Eases in from the hanging-down rest pose during windup.
		this.right_arm.xRot = aimX * ease;
		this.right_arm.yRot = headYawRad * 0.6F * ease;
		this.right_arm.zRot = -0.12F * ease;

		// Channeling tremor — visible only after windup completes.
		float tremor = Mth.sin(ageInTicks * 0.9F) * 0.06F * ease;
		this.right_arm.xRot += tremor;
		this.right_arm.yRot += Mth.cos(ageInTicks * 0.7F) * 0.04F * ease;

		// Left arm: small counterbalance lift, rotated to the side.
		this.left_arm.xRot = -0.55F * ease;
		this.left_arm.zRot = 0.25F * ease;

		// Body leans into the cast.
		this.body.yRot = -0.10F * ease;
	}

	/** Release recoil: brief snap forward then quick settle as the spell launches.
	 * Aim is preserved through the recoil so the wand still points at the target during the flourish. */
	private void applyReleaseRecoil(int sinceEnd, float aimX, float headYawRad) {
		float p = Math.min(1.0F, sinceEnd / (float) RELEASE_RECOIL_DURATION);
		// Bell curve peaking early — sharp thrust then fast decay.
		float snap = (float) Math.sin(Math.min(1.0F, p * 1.6F) * Mth.PI);
		this.right_arm.xRot = aimX + snap * 0.55F;   // arm thrusts further forward then relaxes — still aimed
		this.right_arm.yRot = headYawRad * 0.6F * (1.0F - p);
		this.right_arm.zRot = -0.12F * (1.0F - p);
		this.left_arm.xRot = -0.55F * (1.0F - p);
		this.left_arm.zRot = 0.25F * (1.0F - p);
		this.body.yRot = -0.10F * (1.0F - p);
	}

	/** Neutral aggressive pose when not currently casting/recoiling. */
	private void applyAggressivePose() {
		this.right_arm.xRot = -0.8F;
		this.left_arm.xRot = -0.8F;
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
		// Vanilla ItemInHandLayer already applies the standard -90X / 180Y rotation and the
		// (±1/16, 0.125, -0.625) offset to place the item at the hand of a 12-pixel arm. Doing
		// our own extra translate + rotate on top of that flipped the wand upside-down and
		// floated it off the hand. Pillager has the same arm geometry and uses just this call.
		if (arm == net.minecraft.world.entity.HumanoidArm.RIGHT) {
			this.right_arm.translateAndRotate(poseStack);
		} else {
			this.left_arm.translateAndRotate(poseStack);
		}
	}
}
