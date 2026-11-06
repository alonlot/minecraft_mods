package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.VoldemortEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ArmedModel;
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
import net.minecraft.world.entity.HumanoidArm;

import java.util.HashMap;
import java.util.Map;

/**
 * Lord Voldemort's own body (he used to wear the Death Eater's pointed-hat illager rig). A gaunt figure two
 * blocks tall: a bald, narrow head on a thin neck under a standing collar, a slim torso, long thin arms in bell
 * sleeves with long pale fingers, bare feet, and robes that hang to the ground as two skirt panels (which swing
 * with the stride) and a cape from the shoulders (which streams behind him as he moves).
 *
 * <p>Everything above the waist hangs off {@code body} (pivot at the waist), so a lean or a sway carries the
 * head, arms, skirts and cape together; the legs are the root's own. Part names match the player model's where
 * they can ({@code head body right_arm left_arm right_leg left_leg}) so the Pensieve's pose presets work on him.
 * The texture is painted from these boxes by {@code tools/gen_voldemort_texture.py}; change a box and rerun it.
 *
 * <p>Animation is procedural: a stately walk, breathing, robe and cape motion, then the wizard cast, release
 * recoil, aggressive stance and the Killing Curse wind-up (arm straight up over his head, which is where
 * {@code AvadaChargePose.wandTip} puts a boss's wand), and a hover pose for Tenebris Flight.
 */
public class VoldemortModel extends HierarchicalModel<VoldemortEntity> implements ArmedModel {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "voldemort"), "main");

    private static final Map<Integer, Integer> CAST_START_TICK = new HashMap<>();
    private static final Map<Integer, Integer> CAST_END_TICK = new HashMap<>();
    private static final Map<Integer, Boolean> LAST_CASTING = new HashMap<>();
    private static final int RELEASE_RECOIL_DURATION = 8;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart cape;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightSleeve;
    private final ModelPart leftSleeve;
    private final ModelPart rightSkirt;
    private final ModelPart leftSkirt;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    /** Set in setupAnim: while he comes apart, no red death tint (vanilla tints every frame deathTime is set). */
    private boolean dying;

    public VoldemortModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.cape = body.getChild("cape");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
        this.rightSleeve = rightArm.getChild("right_sleeve");
        this.leftSleeve = leftArm.getChild("left_sleeve");
        this.rightSkirt = body.getChild("right_skirt");
        this.leftSkirt = body.getChild("left_skirt");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation none = CubeDeformation.NONE;

        // Waist at y 11 (13 px above the ground); the head's crown reaches y -8, so he stands 32 px tall.
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(48, 0).addBox(-1.0F, -11.0F, -1.0F, 2.0F, 2.0F, 2.0F, none)      // neck
                .texOffs(0, 14).addBox(-4.0F, -9.0F, -2.0F, 8.0F, 10.0F, 4.0F, none)      // torso
                .texOffs(56, 0).addBox(-4.5F, -10.0F, -2.5F, 9.0F, 2.0F, 5.0F, none),     // standing collar
                PartPose.offset(0.0F, 11.0F, 0.0F));

        body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.0F, -7.0F, -3.0F, 6.0F, 7.0F, 6.0F, none)
                .texOffs(28, 0).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 1.0F, 4.0F, none),     // the crown of the skull
                PartPose.offset(0.0F, -11.0F, 0.0F));

        body.addOrReplaceChild("cape", CubeListBuilder.create()
                .texOffs(84, 0).addBox(-5.0F, 0.0F, 0.0F, 10.0F, 22.0F, 1.0F, none),
                PartPose.offsetAndRotation(0.0F, -9.5F, 2.0F, 0.05F, 0.0F, 0.0F));

        PartDefinition rightArm = body.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(24, 14).addBox(-2.5F, -1.0F, -1.5F, 3.0F, 9.0F, 3.0F, none)
                .texOffs(56, 14).addBox(-2.0F, 8.0F, -1.0F, 2.0F, 2.0F, 2.0F, none)       // hand
                .texOffs(64, 14).addBox(-2.0F, 10.0F, -1.0F, 2.0F, 3.0F, 1.0F, none),     // long fingers
                PartPose.offset(-4.5F, -8.0F, 0.0F));
        rightArm.addOrReplaceChild("right_sleeve", CubeListBuilder.create()
                .texOffs(36, 14).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 4.0F, 4.0F, none),
                PartPose.offset(-1.0F, 5.0F, 0.0F));

        PartDefinition leftArm = body.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(24, 14).mirror().addBox(-0.5F, -1.0F, -1.5F, 3.0F, 9.0F, 3.0F, none)
                .texOffs(56, 14).addBox(0.0F, 8.0F, -1.0F, 2.0F, 2.0F, 2.0F, none)
                .texOffs(64, 14).addBox(0.0F, 10.0F, -1.0F, 2.0F, 3.0F, 1.0F, none).mirror(false),
                PartPose.offset(4.5F, -8.0F, 0.0F));
        leftArm.addOrReplaceChild("left_sleeve", CubeListBuilder.create()
                .texOffs(36, 14).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 4.0F, 4.0F, none).mirror(false),
                PartPose.offset(1.0F, 5.0F, 0.0F));

        body.addOrReplaceChild("right_skirt", CubeListBuilder.create()
                .texOffs(24, 28).addBox(-2.0F, 0.0F, -3.0F, 4.0F, 10.0F, 6.0F, none)
                .texOffs(44, 28).addBox(-3.0F, 9.0F, -3.5F, 5.0F, 3.0F, 7.0F, none),     // flared hem
                PartPose.offset(-2.0F, 0.0F, 0.0F));
        body.addOrReplaceChild("left_skirt", CubeListBuilder.create()
                .texOffs(24, 28).mirror().addBox(-2.0F, 0.0F, -3.0F, 4.0F, 10.0F, 6.0F, none)
                .texOffs(44, 28).addBox(-2.0F, 9.0F, -3.5F, 5.0F, 3.0F, 7.0F, none).mirror(false),
                PartPose.offset(2.0F, 0.0F, 0.0F));

        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 28).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 12.0F, 3.0F, none)
                .texOffs(12, 28).addBox(-1.5F, 11.0F, -3.5F, 3.0F, 1.0F, 2.0F, none),     // bare toes
                PartPose.offset(-2.0F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(0, 28).mirror().addBox(-1.5F, 0.0F, -1.5F, 3.0F, 12.0F, 3.0F, none)
                .texOffs(12, 28).addBox(-1.5F, 11.0F, -3.5F, 3.0F, 1.0F, 2.0F, none).mirror(false),
                PartPose.offset(2.0F, 12.0F, 0.0F));

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(VoldemortEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float stride = Mth.clamp(limbSwingAmount, 0.0F, 1.0F);

        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;

        // A long, unhurried stride: the legs swing, the skirt panels follow at half, the arms barely move.
        float swing = Mth.cos(limbSwing * 0.6662F) * 1.1F * limbSwingAmount;
        rightLeg.xRot = swing;
        leftLeg.xRot = -swing;
        rightSkirt.xRot = swing * 0.45F;
        leftSkirt.xRot = -swing * 0.45F;
        rightSkirt.zRot = 0.04F + stride * 0.04F;      // the panels hang flared a little outward
        leftSkirt.zRot = -0.04F - stride * 0.04F;
        rightArm.xRot = -swing * 0.22F;
        leftArm.xRot = swing * 0.22F;
        rightArm.zRot = 0.05F;
        leftArm.zRot = -0.05F;
        body.y -= Math.abs(Mth.sin(limbSwing * 0.6662F)) * 0.35F * stride;

        // Breathing, and the head held a touch high.
        float breath = Mth.sin(ageInTicks * 0.055F);
        body.xRot = breath * 0.012F;
        head.xRot += -0.04F + breath * 0.015F;

        // The cape streams back with speed and stirs on its own.
        cape.xRot = 0.05F + stride * 0.55F + Mth.sin(ageInTicks * 0.09F) * 0.025F + Mth.sin(ageInTicks * 0.23F) * 0.01F;
        cape.zRot = Mth.sin(ageInTicks * 0.06F) * 0.02F;

        int action = entity.clientAction();
        boolean acting = action != 0 && entity.actionAnim.isStarted();
        this.dying = entity.deathTime > 0;
        if (!acting && !this.dying) {
            applyCombat(entity, ageInTicks, headPitch, netHeadYaw);
            applyFlourish(entity, limbSwingAmount, ageInTicks);
        }
        if (acting) {
            net.minecraft.client.animation.AnimationDefinition clip = VoldemortAnimations.forAction(action);
            if (clip != null) this.animate(entity.actionAnim, clip, ageInTicks);
        }
        if (this.dying) {
            this.animate(entity.deathAnim, VoldemortAnimations.DEATH, ageInTicks);
        }

        // The bell sleeves hang from the forearm: they swing back toward vertical as the arm lifts.
        rightSleeve.xRot = -rightArm.xRot * 0.3F;
        leftSleeve.xRot = -leftArm.xRot * 0.3F;
        rightSleeve.zRot = -rightArm.zRot * 0.3F;
        leftSleeve.zRot = -leftArm.zRot * 0.3F;
    }

    /** The wizard cast, release recoil and aggressive stance, tracked per entity as WizardModel does. */
    private void applyCombat(VoldemortEntity entity, float ageInTicks, float headPitch, float netHeadYaw) {
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
        if (entity.tickCount % 200 == 0) {
            java.util.function.Predicate<Integer> gone = key -> entity.level().getEntity(key) == null;
            CAST_START_TICK.keySet().removeIf(gone);
            CAST_END_TICK.keySet().removeIf(gone);
            LAST_CASTING.keySet().removeIf(gone);
        }

        float aimX = -Mth.HALF_PI + headPitch * Mth.DEG_TO_RAD;
        float yaw = netHeadYaw * Mth.DEG_TO_RAD;
        if (casting) {
            int elapsed = Math.max(0, entity.tickCount - CAST_START_TICK.getOrDefault(id, entity.tickCount));
            float w = Math.min(1.0F, elapsed / 6.0F);
            float e = w * w * (3.0F - 2.0F * w);
            rightArm.xRot = Mth.lerp(e, rightArm.xRot, aimX + Mth.sin(ageInTicks * 0.9F) * 0.05F * e);
            rightArm.yRot = yaw * 0.6F * e + Mth.cos(ageInTicks * 0.7F) * 0.03F * e;
            rightArm.zRot = Mth.lerp(e, rightArm.zRot, -0.1F);
            // The off hand comes up open, fingers spread, as if pressing on the air.
            leftArm.xRot = Mth.lerp(e, leftArm.xRot, -0.75F);
            leftArm.zRot = Mth.lerp(e, leftArm.zRot, -0.35F);
            body.yRot = -0.12F * e;
        } else if (CAST_END_TICK.containsKey(id)) {
            int since = entity.tickCount - CAST_END_TICK.get(id);
            if (since < RELEASE_RECOIL_DURATION) {
                float p = since / (float) RELEASE_RECOIL_DURATION;
                float snap = Mth.sin(Math.min(1.0F, p * 1.6F) * Mth.PI);
                rightArm.xRot = aimX + snap * 0.55F;
                rightArm.yRot = yaw * 0.6F * (1.0F - p);
                leftArm.xRot = -0.75F * (1.0F - p);
                leftArm.zRot = -0.35F * (1.0F - p);
                body.yRot = -0.12F * (1.0F - p);
            } else {
                CAST_END_TICK.remove(id);
            }
        } else if (entity.isAggressive()) {
            // Wand held low and forward, the off hand loose at his side: contempt rather than a guard.
            rightArm.xRot = -0.65F;
            rightArm.zRot = 0.12F;
            leftArm.xRot = -0.15F;
        }
    }

    /** The boss's own beats: the hover of Tenebris Flight and the Killing Curse wind-up. */
    private void applyFlourish(VoldemortEntity v, float limbSwingAmount, float ageInTicks) {
        if (v.isHovering()) {
            rightLeg.xRot = 0.15F;
            leftLeg.xRot = 0.1F;
            rightSkirt.xRot = 0.2F;
            leftSkirt.xRot = 0.2F;
            rightSkirt.zRot = 0.18F;
            leftSkirt.zRot = -0.18F;
            body.xRot = 0.16F + Mth.sin(ageInTicks * 0.1F) * 0.04F;
            cape.xRot = 0.7F + Mth.sin(ageInTicks * 0.2F) * 0.1F;
            if (!v.isCasting()) {
                rightArm.xRot = -0.3F + Mth.sin(ageInTicks * 0.12F) * 0.05F;
                leftArm.xRot = -0.3F + Mth.cos(ageInTicks * 0.12F) * 0.05F;
                rightArm.zRot = 0.55F;
                leftArm.zRot = -0.55F;
            }
        }

        if (v.isChargingAvadaKedavra()) {
            // The wand goes straight up over his head and back, trembling harder as the curse gathers; the
            // off hand opens out to the side and the head tips back to look up the wand.
            float charge = Mth.clamp((ageInTicks % 60.0F) / 60.0F, 0.0F, 1.0F);
            float tremble = Mth.sin(ageInTicks * 1.3F) * (0.02F + 0.05F * charge);
            rightArm.xRot = -2.85F - charge * 0.2F + tremble;
            rightArm.yRot = 0.0F;
            rightArm.zRot = 0.18F + tremble * 0.5F;
            leftArm.xRot = -0.5F;
            leftArm.zRot = -1.0F - charge * 0.2F;
            head.xRot = Math.min(head.xRot - 0.3F, -0.25F);
            body.xRot = -0.1F * charge;
        } else if (!v.isHovering() && !v.isCasting() && limbSwingAmount < 0.05F) {
            // Standing still he is never quite still: a slow turn at the waist, the hands drifting.
            float sway = Mth.sin(ageInTicks * 0.07F) * 0.07F;
            body.yRot += sway;
            rightArm.zRot += Math.max(0.0F, sway) * 0.4F;
            leftArm.zRot += Math.min(0.0F, sway) * 0.4F;
        }
    }

    @Override
    public void renderToBuffer(PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, int light, int overlay, int color) {
        super.renderToBuffer(pose, buffer, light,
                this.dying ? net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY : overlay, color);
    }

    @Override
    public void translateToHand(HumanoidArm arm, PoseStack poseStack) {
        // The arms hang off the body, so the item layer has to follow the body's lean and sway first. Each arm
        // box is centred one pixel out from its pivot and the hand sits ten pixels down, as on the player
        // model, so the vanilla ItemInHandLayer offset puts the wand in the fingers without any extra shift.
        root.translateAndRotate(poseStack);
        body.translateAndRotate(poseStack);
        (arm == HumanoidArm.RIGHT ? rightArm : leftArm).translateAndRotate(poseStack);
    }
}
