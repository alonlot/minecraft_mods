package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.TrollEntity;
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
 * The mountain troll: a small blunt head with tufted ears sunk between narrow
 * sloping shoulders, an enormous sagging belly over a hide loincloth, long ropey
 * arms that hang past the knee into clawed hands, short lumpy legs and broad flat
 * feet. A gnarled club is carried in one fist, angled out from the body.
 *
 * <p>Geometry is generated in blockbench_models/mountain_troll_v2.bbmodel and
 * exported through the Modded Entity codec; the keyframe clips live in
 * {@link TrollAnimations}. Part names here must stay in sync with the bone names
 * those clips address.
 */
public class TrollModel extends HierarchicalModel<TrollEntity> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "troll"), "main");

    /** The permanent forward hunch, applied before the clips so they layer on top. */
    private static final float REST_BODY_X = -0.11F;
    /** Counter-tilt so the hunch does not bury the face in the chest. */
    private static final float REST_HEAD_X = 0.08F;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;

    public TrollModel(ModelPart root) {
        this.root = root.getChild("root");
        this.body = this.root.getChild("body");
        this.head = this.body.getChild("head");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(94, 51).addBox(-7.5F, 1.0F, -6.0F, 15.0F, 6.5F, 12.0F, new CubeDeformation(0.0F))
        .texOffs(0, 0).addBox(-9.0F, -7.0F, -8.8F, 18.0F, 11.0F, 16.8F, new CubeDeformation(0.0F))
        .texOffs(70, 0).addBox(-8.2F, -14.0F, -7.4F, 16.4F, 9.0F, 14.6F, new CubeDeformation(0.0F))
        .texOffs(134, 0).addBox(-7.0F, -22.5F, -5.6F, 14.0F, 10.5F, 11.2F, new CubeDeformation(0.0F))
        .texOffs(48, 71).addBox(-10.5F, -23.5F, -5.0F, 4.5F, 6.5F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(78, 71).addBox(6.0F, -23.5F, -5.0F, 4.5F, 6.5F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(36, 105).addBox(-6.0F, -25.0F, 3.6F, 12.0F, 6.0F, 3.9F, new CubeDeformation(0.0F))
        .texOffs(108, 71).addBox(-7.4F, -23.5F, -6.6F, 5.6F, 13.5F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(124, 71).addBox(1.8F, -23.5F, -6.6F, 5.6F, 13.5F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(140, 71).addBox(-7.6F, -23.5F, 4.6F, 15.2F, 13.5F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(128, 28).addBox(-9.0F, -22.0F, -4.8F, 1.8F, 10.0F, 9.6F, new CubeDeformation(0.0F))
        .texOffs(152, 28).addBox(7.2F, -22.0F, -4.8F, 1.8F, 10.0F, 9.6F, new CubeDeformation(0.0F))
        .texOffs(22, 89).addBox(-11.2F, -25.4F, -5.6F, 5.8F, 3.0F, 11.2F, new CubeDeformation(0.0F))
        .texOffs(58, 89).addBox(5.4F, -25.4F, -5.6F, 5.8F, 3.0F, 11.2F, new CubeDeformation(0.0F))
        .texOffs(142, 89).addBox(-8.0F, 2.0F, -9.0F, 16.0F, 10.0F, 2.4F, new CubeDeformation(0.0F))
        .texOffs(180, 89).addBox(-8.0F, 2.0F, 6.6F, 16.0F, 9.0F, 2.4F, new CubeDeformation(0.0F))
        .texOffs(186, 0).addBox(-9.8F, 1.0F, -7.0F, 2.4F, 9.0F, 14.0F, new CubeDeformation(0.0F))
        .texOffs(220, 0).addBox(7.4F, 1.0F, -7.0F, 2.4F, 9.0F, 14.0F, new CubeDeformation(0.0F))
        .texOffs(126, 105).addBox(2.6F, 11.0F, -9.4F, 3.6F, 5.5F, 2.1F, new CubeDeformation(0.0F))
        .texOffs(68, 105).addBox(-2.6F, 11.5F, -9.6F, 3.8F, 6.5F, 2.1F, new CubeDeformation(0.0F))
        .texOffs(202, 105).addBox(-6.8F, 10.5F, -9.4F, 3.4F, 5.0F, 2.1F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -22.0F, 0.0F));

        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(176, 28).addBox(-6.0F, -9.0F, -6.5F, 12.0F, 9.0F, 11.0F, new CubeDeformation(0.0F))
        .texOffs(0, 105).addBox(-5.0F, -10.6F, -5.0F, 10.0F, 2.1F, 8.0F, new CubeDeformation(0.0F))
        .texOffs(54, 116).addBox(-6.4F, -7.0F, -7.7F, 12.8F, 2.4F, 2.4F, new CubeDeformation(0.0F))
        .texOffs(140, 105).addBox(-3.8F, -4.8F, -9.1F, 7.6F, 4.4F, 3.2F, new CubeDeformation(0.0F))
        .texOffs(218, 89).addBox(-7.0F, -5.0F, -5.1F, 2.0F, 4.6F, 6.6F, new CubeDeformation(0.0F))
        .texOffs(236, 89).addBox(5.0F, -5.0F, -5.1F, 2.0F, 4.6F, 6.6F, new CubeDeformation(0.0F))
        .texOffs(194, 116).addBox(-4.8F, -9.2F, -5.9F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -21.0F, -1.5F));

        head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(82, 105).addBox(-3.8F, -0.1F, -5.7F, 7.6F, 4.0F, 5.8F, new CubeDeformation(0.0F))
        .texOffs(178, 116).addBox(-2.9F, -2.3F, -4.9F, 1.3F, 2.6F, 1.3F, new CubeDeformation(0.0F))
        .texOffs(186, 116).addBox(1.6F, -2.3F, -4.9F, 1.3F, 2.6F, 1.3F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.5F, -3.0F));

        PartDefinition ear_left = head.addOrReplaceChild("ear_left", CubeListBuilder.create(), PartPose.offset(-5.5F, -6.0F, 1.5F));

        ear_left.addOrReplaceChild("ear_l_r1", CubeListBuilder.create().texOffs(164, 105).addBox(-1.5F, -4.5F, -2.0F, 1.6F, 4.5F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.5585F));

        PartDefinition ear_right = head.addOrReplaceChild("ear_right", CubeListBuilder.create(), PartPose.offset(5.5F, -6.0F, 1.5F));

        ear_right.addOrReplaceChild("ear_r_r1", CubeListBuilder.create().texOffs(176, 105).addBox(-0.1F, -4.5F, -2.0F, 1.6F, 4.5F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.5585F));

        PartDefinition arm_left = body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(80, 28).addBox(-2.7F, -1.0F, -3.2F, 5.0F, 13.5F, 6.4F, new CubeDeformation(0.0F)), PartPose.offset(-11.5F, -21.0F, 0.0F));

        PartDefinition forearm_left = arm_left.addOrReplaceChild("forearm_left", CubeListBuilder.create().texOffs(222, 28).addBox(-2.2F, -0.5F, -3.0F, 4.4F, 13.5F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.2F, 11.5F, 0.0F));

        PartDefinition hand_left = forearm_left.addOrReplaceChild("hand_left", CubeListBuilder.create().texOffs(176, 71).addBox(-3.2F, -0.3F, -3.8F, 6.2F, 7.8F, 7.6F, new CubeDeformation(0.0F))
        .texOffs(216, 105).addBox(0.8F, 7.1F, -3.5F, 2.0F, 4.2F, 2.9F, new CubeDeformation(0.0F))
        .texOffs(226, 105).addBox(-1.4F, 7.1F, -3.5F, 2.0F, 4.8F, 2.9F, new CubeDeformation(0.0F))
        .texOffs(14, 116).addBox(-3.2F, 7.1F, -3.5F, 1.6F, 3.8F, 2.9F, new CubeDeformation(0.0F))
        .texOffs(24, 116).addBox(1.3F, 5.1F, 0.9F, 1.7F, 4.0F, 2.6F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 12.0F, 0.0F));

        hand_left.addOrReplaceChild("club", CubeListBuilder.create().texOffs(0, 116).addBox(-1.4F, 4.4F, -1.9F, 2.8F, 3.1F, 3.8F, new CubeDeformation(0.0F))
        .texOffs(94, 89).addBox(-1.7F, -4.0F, -2.3F, 3.4F, 9.5F, 4.6F, new CubeDeformation(0.0F))
        .texOffs(0, 89).addBox(-2.1F, -13.0F, -2.8F, 4.2F, 10.0F, 5.6F, new CubeDeformation(0.0F))
        .texOffs(148, 51).addBox(-2.8F, -22.0F, -3.9F, 5.6F, 10.5F, 7.8F, new CubeDeformation(0.0F))
        .texOffs(110, 105).addBox(2.4F, -17.5F, -2.6F, 1.6F, 4.0F, 5.2F, new CubeDeformation(0.0F))
        .texOffs(188, 105).addBox(-4.1F, -19.0F, -2.3F, 1.6F, 4.0F, 4.6F, new CubeDeformation(0.0F))
        .texOffs(112, 89).addBox(-2.1F, -24.5F, -4.6F, 4.2F, 4.0F, 9.2F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 2.5F, 0.0F, 0.1047F, 0.0F, -0.3142F));

        PartDefinition arm_right = body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(104, 28).addBox(-2.3F, -1.0F, -3.2F, 5.0F, 13.5F, 6.4F, new CubeDeformation(0.0F)), PartPose.offset(11.5F, -21.0F, 0.0F));

        PartDefinition forearm_right = arm_right.addOrReplaceChild("forearm_right", CubeListBuilder.create().texOffs(0, 51).addBox(-2.2F, -0.5F, -3.0F, 4.4F, 13.5F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.2F, 11.5F, 0.0F));

        forearm_right.addOrReplaceChild("hand_right", CubeListBuilder.create().texOffs(206, 71).addBox(-3.0F, -0.3F, -3.8F, 6.2F, 7.8F, 7.6F, new CubeDeformation(0.0F))
        .texOffs(236, 105).addBox(-2.8F, 7.1F, -3.5F, 2.0F, 4.2F, 2.9F, new CubeDeformation(0.0F))
        .texOffs(246, 105).addBox(-0.6F, 7.1F, -3.5F, 2.0F, 4.8F, 2.9F, new CubeDeformation(0.0F))
        .texOffs(34, 116).addBox(1.6F, 7.1F, -3.5F, 1.6F, 3.8F, 2.9F, new CubeDeformation(0.0F))
        .texOffs(44, 116).addBox(-3.0F, 5.1F, 0.9F, 1.7F, 4.0F, 2.6F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 12.0F, 0.0F));

        PartDefinition leg_left = root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(0, 28).addBox(-4.4F, -1.5F, -5.2F, 8.6F, 11.5F, 10.8F, new CubeDeformation(0.0F)), PartPose.offset(-5.0F, -20.0F, 0.0F));

        PartDefinition shin_left = leg_left.addOrReplaceChild("shin_left", CubeListBuilder.create().texOffs(22, 51).addBox(-4.0F, -0.5F, -4.6F, 7.6F, 9.1F, 9.6F, new CubeDeformation(0.0F))
        .texOffs(86, 116).addBox(-5.1F, 3.8F, -2.0F, 1.5F, 2.2F, 2.6F, new CubeDeformation(0.0F))
        .texOffs(202, 116).addBox(3.1F, 1.8F, -3.0F, 1.3F, 1.8F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 8.0F, 0.0F));

        shin_left.addOrReplaceChild("foot_left", CubeListBuilder.create().texOffs(176, 51).addBox(-4.6F, -0.5F, -7.6F, 9.2F, 4.0F, 13.6F, new CubeDeformation(0.0F))
        .texOffs(96, 116).addBox(1.8F, 0.9F, -9.6F, 2.3F, 2.6F, 2.4F, new CubeDeformation(0.0F))
        .texOffs(108, 116).addBox(-0.9F, 0.9F, -10.1F, 2.3F, 2.6F, 2.9F, new CubeDeformation(0.0F))
        .texOffs(120, 116).addBox(-3.5F, 0.9F, -9.6F, 2.2F, 2.6F, 2.4F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 8.5F, -1.0F));

        PartDefinition leg_right = root.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(40, 28).addBox(-4.2F, -1.5F, -5.2F, 8.6F, 11.5F, 10.8F, new CubeDeformation(0.0F)), PartPose.offset(5.0F, -20.0F, 0.0F));

        PartDefinition shin_right = leg_right.addOrReplaceChild("shin_right", CubeListBuilder.create().texOffs(58, 51).addBox(-3.6F, -0.5F, -4.6F, 7.6F, 9.1F, 9.6F, new CubeDeformation(0.0F))
        .texOffs(132, 116).addBox(3.6F, 3.8F, -2.0F, 1.5F, 2.2F, 2.6F, new CubeDeformation(0.0F))
        .texOffs(210, 116).addBox(-4.4F, 1.8F, -3.0F, 1.3F, 1.8F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 8.0F, 0.0F));

        shin_right.addOrReplaceChild("foot_right", CubeListBuilder.create().texOffs(0, 71).addBox(-4.6F, -0.5F, -7.6F, 9.2F, 4.0F, 13.6F, new CubeDeformation(0.0F))
        .texOffs(142, 116).addBox(-4.1F, 0.9F, -9.6F, 2.3F, 2.6F, 2.4F, new CubeDeformation(0.0F))
        .texOffs(154, 116).addBox(-1.4F, 0.9F, -10.1F, 2.3F, 2.6F, 2.9F, new CubeDeformation(0.0F))
        .texOffs(166, 116).addBox(1.3F, 0.9F, -9.6F, 2.2F, 2.6F, 2.4F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 8.5F, -1.0F));

        return LayerDefinition.create(meshdefinition, 256, 256);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(TrollEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // Keyframe clips are additive, so every part has to start from its rest
        // pose or last frame's offsets accumulate.
        this.root().getAllParts().forEach(ModelPart::resetPose);

        this.body.xRot = REST_BODY_X;
        this.head.xRot = REST_HEAD_X;

        // A troll that has worked itself into a rage drops into a hunched charge;
        // otherwise it lumbers. Only one gait clip may drive the legs, so this is
        // a choice, not a blend.
        if (entity.isRaging()) {
            this.animateWalk(TrollAnimations.CHARGE, limbSwing, limbSwingAmount, 1.5F, 2.6F);
        } else {
            this.animateWalk(TrollAnimations.WALK, limbSwing, limbSwingAmount, 1.7F, 2.2F);
        }

        this.animate(entity.idleAnimationState, TrollAnimations.IDLE, ageInTicks);
        this.animate(entity.roarAnimationState, TrollAnimations.ROAR, ageInTicks);
        this.animate(entity.slamWindupAnimationState, TrollAnimations.SLAM_WINDUP, ageInTicks);
        this.animate(entity.slamStrikeAnimationState, TrollAnimations.SLAM_STRIKE, ageInTicks);
        this.animate(entity.sweepAnimationState, TrollAnimations.SWEEP, ageInTicks);
        this.animate(entity.staggerAnimationState, TrollAnimations.STAGGER, ageInTicks);

        // Head tracking last, and damped: the clips already throw the head around
        // during a roar or a slam, and full tracking on top of that reads as a
        // twitch rather than a look.
        float track = entity.isPerformingAction() ? 0.2F : 0.7F;
        this.head.yRot += Mth.clamp(netHeadYaw, -50.0F, 50.0F) * Mth.DEG_TO_RAD * track;
        this.head.xRot += Mth.clamp(headPitch, -30.0F, 30.0F) * Mth.DEG_TO_RAD * track;
    }
}
