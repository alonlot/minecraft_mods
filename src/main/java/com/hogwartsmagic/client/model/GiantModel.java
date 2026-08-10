package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.GiantEntity;
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

/**
 * Grawp: a heavy sloping skull under matted hair, small close-set eyes beneath a
 * brow shelf, a squashed upturned snout, an enormous pale belly, a fur pelt slung
 * across one shoulder and a hide wrap at the hips. Arms hang to the knees.
 *
 * <p>Geometry is generated in Blockbench (blockbench_models/giant.bbmodel) and
 * exported through the Modded Entity codec; keyframe clips live in
 * {@link GiantAnimations}. Part names here must stay in sync with the bone names
 * those clips address.
 */
public class GiantModel extends HierarchicalModel<GiantEntity> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "giant"), "main");

    /** The permanent forward hunch, applied before the clips so they layer on top. */
    private static final float REST_BODY_X = -0.09F;
    /** Counter-tilt so the hunch does not bury the face in the chest. */
    private static final float REST_HEAD_X = 0.07F;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;

    public GiantModel(ModelPart root) {
        this.root = root.getChild("root");
        this.body = this.root.getChild("body");
        this.head = this.body.getChild("head");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(65, 102).addBox(-6.0F, -40.0F, -5.0F, 12.0F, 8.0F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(0, 42).addBox(-13.0F, -34.0F, -8.5F, 26.0F, 16.0F, 17.0F, new CubeDeformation(0.0F))
        .texOffs(0, 76).addBox(12.0F, -36.0F, -7.0F, 6.2F, 10.5F, 14.0F, new CubeDeformation(0.0F))
        .texOffs(43, 76).addBox(-18.2F, -36.0F, -7.0F, 6.2F, 10.5F, 14.0F, new CubeDeformation(0.0F))
        .texOffs(0, 0).addBox(-13.2F, -22.0F, -10.2F, 26.4F, 22.0F, 18.2F, new CubeDeformation(0.0F))
        .texOffs(333, 76).addBox(-11.5F, -2.0F, -9.0F, 23.0F, 7.0F, 16.0F, new CubeDeformation(0.0F))
        .texOffs(435, 76).addBox(-11.0F, 3.0F, -7.5F, 22.0F, 6.0F, 14.5F, new CubeDeformation(0.0F))
        .texOffs(287, 42).addBox(-12.5F, -37.0F, -9.2F, 8.5F, 11.0F, 17.2F, new CubeDeformation(0.0F))
        .texOffs(152, 102).addBox(-9.0F, -28.0F, -10.4F, 7.5F, 12.0F, 4.2F, new CubeDeformation(0.0F))
        .texOffs(289, 102).addBox(-5.5F, -18.0F, -11.0F, 8.0F, 11.0F, 3.8F, new CubeDeformation(0.0F))
        .texOffs(314, 102).addBox(-1.5F, -9.0F, -10.8F, 8.0F, 10.0F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(412, 76).addBox(-10.5F, -37.0F, 7.8F, 8.5F, 21.0F, 1.8F, new CubeDeformation(0.0F))
        .texOffs(179, 102).addBox(-11.5F, 2.0F, -9.6F, 23.0F, 12.0F, 3.2F, new CubeDeformation(0.0F))
        .texOffs(234, 102).addBox(-11.5F, 2.0F, 6.4F, 23.0F, 12.0F, 3.2F, new CubeDeformation(0.0F))
        .texOffs(263, 76).addBox(10.0F, 2.0F, -7.0F, 2.5F, 10.0F, 14.0F, new CubeDeformation(0.0F))
        .texOffs(298, 76).addBox(-12.5F, 2.0F, -7.0F, 2.5F, 10.0F, 14.0F, new CubeDeformation(0.0F))
        .texOffs(454, 102).addBox(5.5F, 12.5F, -9.9F, 3.5F, 8.5F, 2.6F, new CubeDeformation(0.0F))
        .texOffs(339, 102).addBox(-1.0F, 12.5F, -10.1F, 3.5F, 11.0F, 2.7F, new CubeDeformation(0.0F))
        .texOffs(53, 122).addBox(-7.5F, 12.5F, -9.9F, 3.5F, 8.0F, 2.6F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -48.0F, 0.0F));

        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(313, 0).addBox(-10.0F, -22.0F, -9.0F, 20.0F, 18.0F, 18.0F, new CubeDeformation(0.0F))
        .texOffs(0, 102).addBox(-8.5F, -25.0F, -7.5F, 17.0F, 4.0F, 15.0F, new CubeDeformation(0.0F))
        .texOffs(229, 122).addBox(-10.5F, -16.0F, -10.0F, 21.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(276, 122).addBox(3.0F, -13.6F, -9.7F, 4.2F, 2.4F, 1.1F, new CubeDeformation(0.0F))
        .texOffs(291, 122).addBox(-7.2F, -13.6F, -9.7F, 4.2F, 2.4F, 1.1F, new CubeDeformation(0.0F))
        .texOffs(469, 102).addBox(4.0F, -11.5F, -9.6F, 6.6F, 6.5F, 3.1F, new CubeDeformation(0.0F))
        .texOffs(0, 122).addBox(-10.6F, -11.5F, -9.6F, 6.6F, 6.5F, 3.1F, new CubeDeformation(0.0F))
        .texOffs(99, 122).addBox(-4.0F, -12.0F, -11.6F, 8.0F, 5.0F, 3.1F, new CubeDeformation(0.0F))
        .texOffs(333, 122).addBox(-3.0F, -11.6F, -12.4F, 6.0F, 2.8F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(427, 42).addBox(-8.5F, -7.5F, -9.6F, 17.0F, 7.5F, 16.6F, new CubeDeformation(0.0F))
        .texOffs(306, 122).addBox(-5.5F, -5.6F, -10.3F, 11.0F, 2.2F, 1.5F, new CubeDeformation(0.0F))
        .texOffs(68, 122).addBox(-4.6F, -3.0F, -9.3F, 9.2F, 5.0F, 4.8F, new CubeDeformation(0.0F))
        .texOffs(23, 122).addBox(10.0F, -15.0F, -2.0F, 1.6F, 6.0F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(38, 122).addBox(-11.6F, -15.0F, -2.0F, 1.6F, 6.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -34.0F, 0.0F));

        PartDefinition hair = head.addOrReplaceChild("hair", CubeListBuilder.create().texOffs(342, 42).addBox(-10.7F, -13.5F, -9.7F, 21.4F, 7.5F, 19.4F, new CubeDeformation(0.0F))
        .texOffs(124, 122).addBox(-10.5F, -7.5F, -10.5F, 21.0F, 6.0F, 1.9F, new CubeDeformation(0.0F))
        .texOffs(212, 76).addBox(-10.3F, -9.0F, 7.4F, 20.6F, 20.0F, 3.2F, new CubeDeformation(0.0F))
        .texOffs(396, 102).addBox(2.5F, -16.5F, -4.0F, 6.0F, 3.5F, 8.0F, new CubeDeformation(0.0F))
        .texOffs(425, 102).addBox(-8.5F, -16.5F, -4.0F, 6.0F, 3.5F, 8.0F, new CubeDeformation(0.0F))
        .texOffs(110, 102).addBox(10.4F, -2.0F, -3.0F, 1.4F, 9.0F, 8.0F, new CubeDeformation(0.0F))
        .texOffs(131, 102).addBox(-11.8F, -2.0F, -3.0F, 1.4F, 9.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -13.0F, 0.0F));

        PartDefinition arm_right = body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(390, 0).addBox(-3.5F, -2.5F, -5.5F, 9.5F, 24.5F, 11.0F, new CubeDeformation(0.0F)), PartPose.offset(18.5F, -32.0F, 0.0F));

        PartDefinition forearm_right = arm_right.addOrReplaceChild("forearm_right", CubeListBuilder.create().texOffs(87, 42).addBox(-5.0F, 0.0F, -6.0F, 11.0F, 19.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(1.0F, 21.0F, 0.0F));

        PartDefinition hand_right = forearm_right.addOrReplaceChild("hand_right", CubeListBuilder.create().texOffs(181, 42).addBox(-5.5F, -0.5F, -6.8F, 12.0F, 15.5F, 13.6F, new CubeDeformation(0.0F))
        .texOffs(354, 102).addBox(-8.5F, 5.0F, -6.0F, 3.3F, 7.0F, 5.5F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 18.0F, 0.0F));

        PartDefinition arm_left = body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(433, 0).addBox(-6.0F, -2.5F, -5.5F, 9.5F, 24.5F, 11.0F, new CubeDeformation(0.0F)), PartPose.offset(-18.5F, -32.0F, 0.0F));

        PartDefinition forearm_left = arm_left.addOrReplaceChild("forearm_left", CubeListBuilder.create().texOffs(134, 42).addBox(-6.0F, 0.0F, -6.0F, 11.0F, 19.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(-1.0F, 21.0F, 0.0F));

        PartDefinition hand_left = forearm_left.addOrReplaceChild("hand_left", CubeListBuilder.create().texOffs(234, 42).addBox(-6.5F, -0.5F, -6.8F, 12.0F, 15.5F, 13.6F, new CubeDeformation(0.0F))
        .texOffs(375, 102).addBox(5.2F, 5.0F, -6.0F, 3.3F, 7.0F, 5.5F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 18.0F, 0.0F));

        PartDefinition leg_right = root.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(93, 0).addBox(-6.0F, -1.0F, -7.8F, 12.3F, 25.0F, 15.6F, new CubeDeformation(0.0F)), PartPose.offset(7.5F, -48.0F, 0.0F));

        PartDefinition shin_right = leg_right.addOrReplaceChild("shin_right", CubeListBuilder.create().texOffs(211, 0).addBox(-5.0F, -0.5F, -7.0F, 10.3F, 22.5F, 14.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 22.0F, 0.0F));

        PartDefinition foot_right = shin_right.addOrReplaceChild("foot_right", CubeListBuilder.create().texOffs(86, 76).addBox(-5.5F, -0.2F, -12.5F, 11.2F, 5.2F, 19.0F, new CubeDeformation(0.0F))
        .texOffs(171, 122).addBox(-5.1F, 1.6F, -15.0F, 10.4F, 3.4F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 21.0F, 0.0F));

        PartDefinition leg_left = root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(152, 0).addBox(-6.3F, -1.0F, -7.8F, 12.3F, 25.0F, 15.6F, new CubeDeformation(0.0F)), PartPose.offset(-7.5F, -48.0F, 0.0F));

        PartDefinition shin_left = leg_left.addOrReplaceChild("shin_left", CubeListBuilder.create().texOffs(262, 0).addBox(-5.3F, -0.5F, -7.0F, 10.3F, 22.5F, 14.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 22.0F, 0.0F));

        PartDefinition foot_left = shin_left.addOrReplaceChild("foot_left", CubeListBuilder.create().texOffs(149, 76).addBox(-5.7F, -0.2F, -12.5F, 11.2F, 5.2F, 19.0F, new CubeDeformation(0.0F))
        .texOffs(200, 122).addBox(-5.3F, 1.6F, -15.0F, 10.4F, 3.4F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 21.0F, 0.0F));
        return LayerDefinition.create(meshdefinition, 512, 256);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(GiantEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // Keyframe clips are additive, so every part has to start from its rest
        // pose or last frame's offsets accumulate.
        this.root().getAllParts().forEach(ModelPart::resetPose);

        this.body.xRot = REST_BODY_X;
        this.head.xRot = REST_HEAD_X;

        // An angry giant drops into a hunched charge; otherwise it lumbers. Only
        // one gait clip may drive the legs, so this is a choice, not a blend.
        if (entity.isAngry()) {
            this.animateWalk(GiantAnimations.CHARGE, limbSwing, limbSwingAmount, 1.6F, 2.4F);
        } else {
            this.animateWalk(GiantAnimations.WALK, limbSwing, limbSwingAmount, 1.9F, 2.0F);
        }

        this.animate(entity.idleAnimationState, GiantAnimations.IDLE, ageInTicks);
        this.animate(entity.roarAnimationState, GiantAnimations.ROAR, ageInTicks);
        this.animate(entity.swipeAnimationState, GiantAnimations.SWIPE, ageInTicks);
        this.animate(entity.kickAnimationState, GiantAnimations.KICK, ageInTicks);
        this.animate(entity.grabAnimationState, GiantAnimations.GRAB, ageInTicks);
        this.animate(entity.throwAnimationState, GiantAnimations.THROW, ageInTicks);
        this.animate(entity.staggerAnimationState, GiantAnimations.STAGGER, ageInTicks);

        // Head tracking last, and damped: the clips already throw the head around
        // during a roar or a throw, and full tracking on top of that reads as a
        // twitch rather than a look.
        float track = entity.isPerformingAction() ? 0.2F : 0.75F;
        this.head.yRot += Mth.clamp(netHeadYaw, -55.0F, 55.0F) * (Mth.PI / 180.0F) * track;
        this.head.xRot += Mth.clamp(headPitch, -35.0F, 35.0F) * (Mth.PI / 180.0F) * track;
    }
}
