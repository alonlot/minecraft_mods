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
 * The mountain troll: a small blunt head thrust low in front of a great hump, a pear of a torso with
 * a belly that hangs over the belt, long thick arms and short bowed legs.
 *
 * <p>The club is held properly. {@code fingers_r} and {@code club} share a pivot and a rotation, so the
 * three finger rings are built around the handle in the club's own frame and the shaft runs through
 * them from any angle. The grip leans the club 56 degrees forward of the forearm with the head on the
 * thumb side, which is what puts it behind the troll's head on the backswing and out in front at the
 * end of the slam. At rest its tip clears the ground by less than a pixel.
 *
 * <p>{@code club_loose} repeats the club's cubes directly under {@code root}: that is the club once
 * Wingardium Leviosa has taken it, and exactly one of the two is visible. The rest of the troll hangs off
 * {@code hull}, which is what the clips move, so it can fall over without taking the dropped club with it.
 *
 * <p>Geometry comes from tools/troll/build_model.js (blockbench_models/mountain_troll_v3.bbmodel) and is
 * spliced in by tools/troll/merge_model.py. The clips live in {@link TrollAnimations}; the rest pose's
 * hunch and arm bends are baked into the part poses, so the clips are plain deltas from it.
 */
public class TrollModel extends HierarchicalModel<TrollEntity> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "troll"), "main");

    /** limbSwing advances about four units per block, so this sets the stride: a 1.6 s clip over 2 blocks. */
    private static final float WALK_RATE = 4.0F;
    private static final float CHARGE_RATE = 2.1F;

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart club;
    private final ModelPart clubLoose;

    public TrollModel(ModelPart root) {
        this.root = root.getChild("root");
        ModelPart body = this.root.getChild("hull").getChild("body");
        this.head = body.getChild("head");
        this.club = body.getChild("arm_r").getChild("forearm_r").getChild("hand_r").getChild("club");
        this.clubLoose = this.root.getChild("club_loose");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition hull = root.addOrReplaceChild("hull", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition body = hull.addOrReplaceChild("body", CubeListBuilder.create().texOffs(160, 0).addBox(-11.0F, -2.0F, -9.0F, 22.0F, 7.0F, 17.0F, new CubeDeformation(0.0F))
        .texOffs(81, 0).addBox(-12.0F, -13.0F, -6.0F, 24.0F, 12.0F, 15.0F, new CubeDeformation(0.0F))
        .texOffs(0, 0).addBox(-11.0F, -23.0F, -9.0F, 22.0F, 11.0F, 18.0F, new CubeDeformation(0.0F))
        .texOffs(94, 30).addBox(-15.0F, -28.0F, -7.0F, 30.0F, 7.0F, 15.0F, new CubeDeformation(0.0F))
        .texOffs(69, 116).addBox(-9.0F, -31.0F, -5.0F, 18.0F, 4.0F, 12.0F, new CubeDeformation(0.0F))
        .texOffs(185, 133).addBox(-7.0F, -33.0F, 0.0F, 14.0F, 4.0F, 8.0F, new CubeDeformation(0.0F))
        .texOffs(164, 97).addBox(-6.0F, -31.0F, -11.0F, 12.0F, 8.0F, 9.0F, new CubeDeformation(0.0F))
        .texOffs(31, 163).addBox(1.0F, -22.0F, -11.0F, 9.0F, 7.0F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(56, 163).addBox(-10.0F, -22.0F, -11.0F, 9.0F, 7.0F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(0, 54).addBox(-12.0F, 1.0F, -10.0F, 24.0F, 2.0F, 19.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -18.0F, 0.0F, 0.1047F, 0.0F, 0.0F));

        PartDefinition belly = body.addOrReplaceChild("belly", CubeListBuilder.create().texOffs(50, 133).addBox(-12.0F, -9.0F, -5.0F, 24.0F, 6.0F, 8.0F, new CubeDeformation(0.0F))
        .texOffs(35, 76).addBox(-14.0F, -4.0F, -8.0F, 28.0F, 8.0F, 11.0F, new CubeDeformation(0.0F))
        .texOffs(0, 116).addBox(-12.0F, 3.0F, -7.0F, 24.0F, 6.0F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(136, 133).addBox(-8.0F, 8.0F, -5.0F, 16.0F, 4.0F, 8.0F, new CubeDeformation(0.0F))
        .texOffs(128, 150).addBox(-9.0F, -3.0F, -10.0F, 18.0F, 9.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -6.0F, -8.0F));

        PartDefinition loin_front = body.addOrReplaceChild("loin_front", CubeListBuilder.create().texOffs(101, 150).addBox(-6.0F, 0.0F, -0.5F, 12.0F, 11.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 3.0F, -7.5F));

        PartDefinition loin_back = body.addOrReplaceChild("loin_back", CubeListBuilder.create().texOffs(0, 150).addBox(-8.0F, 0.0F, -0.5F, 16.0F, 11.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 2.0F, 9.5F));

        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(169, 54).addBox(-6.0F, -9.0F, -11.0F, 12.0F, 9.0F, 11.0F, new CubeDeformation(0.0F))
        .texOffs(96, 163).addBox(-4.0F, -11.0F, -9.0F, 8.0F, 2.0F, 7.0F, new CubeDeformation(0.0F))
        .texOffs(127, 163).addBox(-5.0F, -8.0F, 0.0F, 10.0F, 7.0F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(214, 175).addBox(-7.0F, -7.0F, -13.0F, 14.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(22, 175).addBox(-2.5F, -5.0F, -14.0F, 5.0F, 4.0F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(199, 163).addBox(5.0F, -3.0F, -9.0F, 2.0F, 3.0F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(214, 163).addBox(-7.0F, -3.0F, -9.0F, 2.0F, 3.0F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(69, 175).addBox(-5.0F, -1.0F, -13.0F, 10.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -29.0F, -8.0F, -0.1047F, 0.0F, 0.0F));

        PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(130, 116).addBox(-6.0F, 0.0F, -10.0F, 12.0F, 5.0F, 11.0F, new CubeDeformation(0.0F))
        .texOffs(98, 175).addBox(-3.5F, 5.0F, -10.0F, 7.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(196, 175).addBox(3.0F, -3.0F, -10.5F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(205, 175).addBox(-5.0F, -3.0F, -10.5F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(66, 184).addBox(0.5F, -1.0F, -10.2F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(71, 184).addBox(-1.5F, -1.0F, -10.2F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -4.0F));

        PartDefinition ear_r = head.addOrReplaceChild("ear_r", CubeListBuilder.create().texOffs(0, 184).addBox(-5.0F, -2.0F, -0.5F, 5.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(52, 184).addBox(-7.0F, 0.0F, -0.5F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.0F, -4.0F, -4.0F, 0.0F, -0.4189F, -0.2443F));

        PartDefinition ear_l = head.addOrReplaceChild("ear_l", CubeListBuilder.create().texOffs(13, 184).addBox(0.0F, -2.0F, -0.5F, 5.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(59, 184).addBox(5.0F, 0.0F, -0.5F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.0F, -4.0F, -4.0F, 0.0F, 0.4189F, 0.2443F));

        PartDefinition arm_r = body.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(0, 97).addBox(-5.0F, -4.0F, -6.0F, 10.0F, 7.0F, 11.0F, new CubeDeformation(0.0F))
        .texOffs(216, 54).addBox(-4.0F, 2.0F, -5.0F, 8.0F, 11.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-17.0F, -24.0F, 0.0F, 0.0349F, 0.0F, 0.0873F));

        PartDefinition forearm_r = arm_r.addOrReplaceChild("forearm_r", CubeListBuilder.create().texOffs(87, 54).addBox(-5.0F, -1.0F, -5.0F, 10.0F, 11.0F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(35, 150).addBox(-4.0F, 9.0F, -4.0F, 8.0F, 4.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, -0.4189F, 0.0F, 0.0F));

        PartDefinition hand_r = forearm_r.addOrReplaceChild("hand_r", CubeListBuilder.create().texOffs(17, 133).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 7.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 11.0F, 0.0F, 0.2443F, 0.0F, 0.0F));

        PartDefinition fingers_r = hand_r.addOrReplaceChild("fingers_r", CubeListBuilder.create().texOffs(169, 150).addBox(-3.5F, -4.5F, -3.5F, 7.0F, 3.0F, 8.0F, new CubeDeformation(0.0F))
        .texOffs(200, 150).addBox(-3.5F, -1.5F, -3.5F, 7.0F, 3.0F, 8.0F, new CubeDeformation(0.0F))
        .texOffs(0, 163).addBox(-3.5F, 1.5F, -3.5F, 7.0F, 3.0F, 8.0F, new CubeDeformation(0.0F))
        .texOffs(81, 163).addBox(3.0F, -3.5F, -1.5F, 2.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.5F, -1.0F, -0.9774F, 0.0F, 0.2793F));

        PartDefinition club = hand_r.addOrReplaceChild("club", CubeListBuilder.create().texOffs(123, 175).addBox(-2.5F, -6.5F, -2.5F, 5.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(0, 133).addBox(-2.0F, -5.5F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
        .texOffs(115, 133).addBox(-2.5F, 6.5F, -2.5F, 5.0F, 8.0F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(212, 116).addBox(-4.0F, 14.5F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
        .texOffs(174, 163).addBox(-3.0F, 22.5F, -3.0F, 6.0F, 2.0F, 6.0F, new CubeDeformation(0.0F))
        .texOffs(229, 163).addBox(4.0F, 16.5F, -2.0F, 2.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
        .texOffs(242, 163).addBox(-6.0F, 17.5F, -2.0F, 2.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
        .texOffs(26, 184).addBox(-2.0F, 16.5F, -6.0F, 4.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(39, 184).addBox(-2.0F, 9.5F, 2.5F, 4.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.5F, -1.0F, -0.9774F, 0.0F, 0.2793F));

        PartDefinition arm_l = body.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(43, 97).addBox(-5.0F, -4.0F, -6.0F, 10.0F, 7.0F, 11.0F, new CubeDeformation(0.0F))
        .texOffs(0, 76).addBox(-4.0F, 2.0F, -5.0F, 8.0F, 11.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(17.0F, -24.0F, 0.0F, -0.0698F, 0.0F, -0.0873F));

        PartDefinition forearm_l = arm_l.addOrReplaceChild("forearm_l", CubeListBuilder.create().texOffs(128, 54).addBox(-5.0F, -1.0F, -5.0F, 10.0F, 11.0F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(68, 150).addBox(-4.0F, 9.0F, -4.0F, 8.0F, 4.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, -0.1745F, 0.0F, 0.0F));

        PartDefinition hand_l = forearm_l.addOrReplaceChild("hand_l", CubeListBuilder.create().texOffs(177, 116).addBox(-4.0F, 0.0F, -4.0F, 8.0F, 7.0F, 9.0F, new CubeDeformation(0.0F))
        .texOffs(0, 175).addBox(2.0F, 7.0F, -4.0F, 2.0F, 5.0F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(152, 163).addBox(-1.0F, 7.0F, -4.0F, 2.0F, 6.0F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(11, 175).addBox(-4.0F, 7.0F, -4.0F, 2.0F, 5.0F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(163, 163).addBox(-6.0F, 3.0F, 0.0F, 2.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 11.0F, 0.0F));

        PartDefinition leg_r = hull.addOrReplaceChild("leg_r", CubeListBuilder.create().texOffs(0, 30).addBox(-6.0F, -2.0F, -6.0F, 11.0F, 11.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(-8.0F, -18.0F, 0.0F));

        PartDefinition shin_r = leg_r.addOrReplaceChild("shin_r", CubeListBuilder.create().texOffs(86, 97).addBox(-4.5F, -1.0F, -5.0F, 9.0F, 8.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.5F, 8.0F, 0.0F));

        PartDefinition foot_r = shin_r.addOrReplaceChild("foot_r", CubeListBuilder.create().texOffs(114, 76).addBox(-5.5F, 0.0F, -10.0F, 11.0F, 4.0F, 15.0F, new CubeDeformation(0.0F))
        .texOffs(144, 175).addBox(2.5F, 1.0F, -13.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(39, 175).addBox(-1.5F, 1.0F, -14.0F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
        .texOffs(157, 175).addBox(-5.5F, 1.0F, -13.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 6.0F, 0.0F));

        PartDefinition leg_l = hull.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(47, 30).addBox(-5.0F, -2.0F, -6.0F, 11.0F, 11.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(8.0F, -18.0F, 0.0F));

        PartDefinition shin_l = leg_l.addOrReplaceChild("shin_l", CubeListBuilder.create().texOffs(125, 97).addBox(-4.5F, -1.0F, -5.0F, 9.0F, 8.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, 8.0F, 0.0F));

        PartDefinition foot_l = shin_l.addOrReplaceChild("foot_l", CubeListBuilder.create().texOffs(167, 76).addBox(-5.5F, 0.0F, -10.0F, 11.0F, 4.0F, 15.0F, new CubeDeformation(0.0F))
        .texOffs(170, 175).addBox(2.5F, 1.0F, -13.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(54, 175).addBox(-1.5F, 1.0F, -14.0F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
        .texOffs(183, 175).addBox(-5.5F, 1.0F, -13.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 6.0F, 0.0F));

        root.addOrReplaceChild("club_loose", CubeListBuilder.create().texOffs(123, 175).addBox(-2.5F, -6.5F, -2.5F, 5.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(0, 133).addBox(-2.0F, -5.5F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
        .texOffs(115, 133).addBox(-2.5F, 6.5F, -2.5F, 5.0F, 8.0F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(212, 116).addBox(-4.0F, 14.5F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
        .texOffs(174, 163).addBox(-3.0F, 22.5F, -3.0F, 6.0F, 2.0F, 6.0F, new CubeDeformation(0.0F))
        .texOffs(229, 163).addBox(4.0F, 16.5F, -2.0F, 2.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
        .texOffs(242, 163).addBox(-6.0F, 17.5F, -2.0F, 2.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
        .texOffs(26, 184).addBox(-2.0F, 16.5F, -6.0F, 4.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(39, 184).addBox(-2.0F, 9.5F, 2.5F, 4.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-19.6F, -16.0F, -4.9F, -1.05F, 0.0F, 0.28F));

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

        boolean holding = entity.isHoldingClub();
        this.club.visible = holding;
        this.clubLoose.visible = !holding;

        // Only one gait clip may drive the legs, so this is a choice, not a blend.
        if (entity.isRaging()) {
            this.animateWalk(TrollAnimations.CHARGE, limbSwing, limbSwingAmount, CHARGE_RATE, 2.0F);
        } else {
            this.animateWalk(TrollAnimations.WALK, limbSwing, limbSwingAmount, WALK_RATE, 3.0F);
        }

        this.animate(entity.idleAnimationState, TrollAnimations.IDLE, ageInTicks);
        this.animate(entity.roarAnimationState, TrollAnimations.ROAR, ageInTicks);
        this.animate(entity.slamWindupAnimationState, TrollAnimations.SLAM_WINDUP, ageInTicks);
        this.animate(entity.slamStrikeAnimationState, TrollAnimations.SLAM_STRIKE, ageInTicks);
        this.animate(entity.sweepAnimationState, TrollAnimations.SWEEP, ageInTicks);
        this.animate(entity.backhandAnimationState, TrollAnimations.BACKHAND, ageInTicks);
        this.animate(entity.staggerAnimationState, TrollAnimations.STAGGER, ageInTicks);
        this.animate(entity.disarmedAnimationState, TrollAnimations.DISARMED, ageInTicks);
        this.animate(entity.knockedOutAnimationState, TrollAnimations.KNOCKED_OUT, ageInTicks);
        this.animate(entity.getUpAnimationState, TrollAnimations.GET_UP, ageInTicks);

        // Head tracking last, and damped: the clips already throw the head around, and full tracking on
        // top of that reads as a twitch rather than a look.
        float track = entity.headTrackingWeight();
        this.head.yRot += Mth.clamp(netHeadYaw, -50.0F, 50.0F) * Mth.DEG_TO_RAD * track;
        this.head.xRot += Mth.clamp(headPitch, -30.0F, 30.0F) * Mth.DEG_TO_RAD * track;
    }
}
