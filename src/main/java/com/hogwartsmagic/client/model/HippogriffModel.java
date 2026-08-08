package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.HippogriffEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
 * Buckbeak. Eagle in front, horse behind: hooked beak with a heavy brow, swept feather
 * crest, a shaggy ruff over the shoulders, layered wings that fold down along the flanks,
 * scaled talons at the front and hooves behind.
 *
 * <p>Geometry is baked from blockbench_models/hippogriff_v2.bbmodel. The rest pose has the
 * wings <em>folded</em>, which is what the creature spends most of its time in; flight
 * unfurls them by interpolating the three wing joints back toward the spread pose.
 */
public class HippogriffModel extends HierarchicalModel<HippogriffEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "hippogriff"), "main");

    private static final float DEG = (float) Math.PI / 180.0F;

    // Folded-wing rest rotations, mirrored on the far wing. Must match createBodyLayer.
    private static final float REST_WING_Y = 1.0472F;
    private static final float REST_WING_Z = 0.384F;
    private static final float REST_FORE_Y = -0.5236F;
    private static final float REST_FORE_Z = -0.6981F;
    private static final float REST_HAND_Y = -0.5934F;
    private static final float REST_HAND_Z = -0.4189F;

    // Spread-wing targets: a shallow dihedral rather than a dead-flat plank.
    private static final float OPEN_WING_Z = 0.10F;
    private static final float OPEN_FORE_Z = -0.06F;
    private static final float OPEN_HAND_Z = -0.12F;

    private static float smoothstep(float value) {
        value = Mth.clamp(value, 0.0F, 1.0F);
        return value * value * (3.0F - 2.0F * value);
    }

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart chest;
    private final ModelPart ruff;
    private final ModelPart neck;
    private final ModelPart neckUpper;
    private final ModelPart head;
    private final ModelPart beakUpper;
    private final ModelPart beakLower;
    private final ModelPart crest;
    private final ModelPart leftWing;
    private final ModelPart leftWingFore;
    private final ModelPart leftWingHand;
    private final ModelPart rightWing;
    private final ModelPart rightWingFore;
    private final ModelPart rightWingHand;
    private final ModelPart frontLeftLeg;
    private final ModelPart frontLeftShank;
    private final ModelPart frontLeftFoot;
    private final ModelPart frontRightLeg;
    private final ModelPart frontRightShank;
    private final ModelPart frontRightFoot;
    private final ModelPart backLeftLeg;
    private final ModelPart backLeftShin;
    private final ModelPart backLeftCannon;
    private final ModelPart backRightLeg;
    private final ModelPart backRightShin;
    private final ModelPart backRightCannon;
    private final ModelPart tail;
    private final ModelPart tailHair;

    public HippogriffModel(ModelPart root) {
        this.root = root.getChild("root");
        this.body = this.root.getChild("body");
        this.chest = this.body.getChild("chest");
        this.ruff = this.chest.getChild("ruff");
        this.neck = this.chest.getChild("neck");
        this.neckUpper = this.neck.getChild("neck_upper");
        this.head = this.neckUpper.getChild("head");
        this.beakUpper = this.head.getChild("beak_upper");
        this.beakLower = this.head.getChild("beak_lower");
        this.crest = this.head.getChild("crest");
        this.leftWing = this.chest.getChild("left_wing");
        this.leftWingFore = this.leftWing.getChild("left_wing_fore");
        this.leftWingHand = this.leftWingFore.getChild("left_wing_hand");
        this.rightWing = this.chest.getChild("right_wing");
        this.rightWingFore = this.rightWing.getChild("right_wing_fore");
        this.rightWingHand = this.rightWingFore.getChild("right_wing_hand");
        this.frontLeftLeg = this.body.getChild("front_left_leg");
        this.frontLeftShank = this.frontLeftLeg.getChild("front_left_shank");
        this.frontLeftFoot = this.frontLeftShank.getChild("front_left_foot");
        this.frontRightLeg = this.body.getChild("front_right_leg");
        this.frontRightShank = this.frontRightLeg.getChild("front_right_shank");
        this.frontRightFoot = this.frontRightShank.getChild("front_right_foot");
        this.backLeftLeg = this.body.getChild("back_left_leg");
        this.backLeftShin = this.backLeftLeg.getChild("back_left_shin");
        this.backLeftCannon = this.backLeftShin.getChild("back_left_cannon");
        this.backRightLeg = this.body.getChild("back_right_leg");
        this.backRightShin = this.backRightLeg.getChild("back_right_shin");
        this.backRightCannon = this.backRightShin.getChild("back_right_cannon");
        this.tail = this.body.getChild("tail");
        this.tailHair = this.tail.getChild("tail_hair");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -4.5F, -6.0F, 10.0F, 9.5F, 15.0F, new CubeDeformation(0.0F))
        .texOffs(38, 26).addBox(-4.5F, -4.0F, 8.5F, 9.0F, 8.5F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -20.0F, 3.0F));

        PartDefinition chest = body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(51, 0).addBox(-5.0F, -4.0F, -8.0F, 10.0F, 11.0F, 8.0F, new CubeDeformation(0.0F))
        .texOffs(172, 26).addBox(-3.8F, 6.0F, -7.5F, 7.6F, 4.5F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.0F, -5.0F));

        PartDefinition ruff = chest.addOrReplaceChild("ruff", CubeListBuilder.create().texOffs(142, 0).addBox(-6.2F, -3.5F, -4.0F, 12.4F, 8.5F, 7.0F, new CubeDeformation(0.0F))
        .texOffs(182, 0).addBox(-7.2F, -2.0F, -3.5F, 1.4F, 8.5F, 6.0F, new CubeDeformation(0.0F))
        .texOffs(198, 0).addBox(5.8F, -2.0F, -3.5F, 1.4F, 8.5F, 6.0F, new CubeDeformation(0.0F))
        .texOffs(51, 53).addBox(-4.4F, 2.5F, -4.6F, 8.8F, 7.0F, 1.4F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.0F, -6.0F));

        PartDefinition neck = chest.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(65, 26).addBox(-3.6F, -5.6F, -3.5F, 7.2F, 5.6F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -2.0F, -5.5F, 0.3491F, 0.0F, 0.0F));

        PartDefinition neck_upper = neck.addOrReplaceChild("neck_upper", CubeListBuilder.create().texOffs(203, 26).addBox(-3.2F, -4.8F, -3.3F, 6.4F, 4.8F, 6.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -5.2F, 0.0F, 0.2443F, 0.0F, 0.0F));

        PartDefinition head = neck_upper.addOrReplaceChild("head", CubeListBuilder.create().texOffs(214, 0).addBox(-3.4F, -5.0F, -5.9F, 6.8F, 6.6F, 7.8F, new CubeDeformation(0.0F))
        .texOffs(138, 53).addBox(-3.7F, -5.7F, -6.7F, 7.4F, 1.9F, 5.8F, new CubeDeformation(0.0F))
        .texOffs(166, 53).addBox(-2.8F, -4.8F, 1.3F, 5.6F, 5.4F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(26, 53).addBox(-2.6F, -0.6F, -5.7F, 5.2F, 2.6F, 6.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -4.4F, 0.0F, -0.5934F, 0.0F, 0.0F));

        head.addOrReplaceChild("cheek_r_r1", CubeListBuilder.create().texOffs(16, 41).addBox(-0.1F, 0.0F, -3.8F, 1.1F, 4.6F, 6.2F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.4F, -3.6F, -1.5F, 0.0F, 0.0F, 0.1222F));

        head.addOrReplaceChild("cheek_l_r1", CubeListBuilder.create().texOffs(0, 41).addBox(-1.0F, 0.0F, -3.8F, 1.1F, 4.6F, 6.2F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.4F, -3.6F, -1.5F, 0.0F, 0.0F, -0.1222F));

        head.addOrReplaceChild("beak_upper", CubeListBuilder.create().texOffs(183, 53).addBox(-2.1F, -1.8F, -4.2F, 4.2F, 3.2F, 4.2F, new CubeDeformation(0.0F))
        .texOffs(230, 53).addBox(-1.6F, -0.4F, -5.4F, 3.2F, 3.4F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(190, 71).addBox(-1.25F, 2.7F, -5.2F, 2.5F, 1.9F, 1.4F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -2.2F, -5.5F));

        head.addOrReplaceChild("beak_lower", CubeListBuilder.create().texOffs(0, 64).addBox(-1.7F, -0.2F, -3.9F, 3.4F, 1.7F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -0.8F, -5.5F));

        PartDefinition crest = head.addOrReplaceChild("crest", CubeListBuilder.create(), PartPose.offset(0.0F, -4.9F, -3.5F));

        crest.addOrReplaceChild("crest_r_r1", CubeListBuilder.create().texOffs(181, 41).addBox(-0.7F, -1.8F, -1.0F, 1.4F, 2.0F, 7.2F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.8F, 0.5F, 0.0F, 0.2443F, 0.0F, -0.1222F));

        crest.addOrReplaceChild("crest_l_r1", CubeListBuilder.create().texOffs(162, 41).addBox(-0.7F, -1.8F, -1.0F, 1.4F, 2.0F, 7.2F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.8F, 0.5F, 0.0F, 0.2443F, 0.0F, 0.1222F));

        crest.addOrReplaceChild("crest_mid_r1", CubeListBuilder.create().texOffs(73, 53).addBox(-1.2F, -1.8F, -1.0F, 2.4F, 2.0F, 6.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.1396F, 0.0F, 0.0F));

        PartDefinition left_wing = chest.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(95, 26).addBox(-4.5F, -1.6F, -4.0F, 4.5F, 3.2F, 9.0F, new CubeDeformation(0.0F))
        .texOffs(92, 53).addBox(-4.2F, -0.6F, 4.0F, 3.9F, 1.8F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.0F, -3.0F, -1.0F, 0.0F, 1.0472F, 0.384F));

        PartDefinition left_wing_fore = left_wing.addOrReplaceChild("left_wing_fore", CubeListBuilder.create().texOffs(32, 41).addBox(-3.5F, -1.3F, -4.0F, 3.5F, 2.6F, 8.0F, new CubeDeformation(0.0F))
        .texOffs(56, 41).addBox(-3.3F, -0.4F, 3.5F, 3.1F, 1.4F, 9.5F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.5F, 0.0F, -1.0F, 0.0F, -0.5236F, -0.6981F));

        PartDefinition left_wing_hand = left_wing_fore.addOrReplaceChild("left_wing_hand", CubeListBuilder.create().texOffs(200, 41).addBox(-2.5F, -1.1F, -3.0F, 2.5F, 2.2F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.5F, 0.0F, -2.0F, 0.0F, -0.5934F, -0.4189F));

        left_wing_hand.addOrReplaceChild("l_prim5_r1", CubeListBuilder.create().texOffs(132, 71).addBox(-11.0F, -0.65F, -1.5F, 11.0F, 1.3F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.5F, 0.45F, 8.0F, 0.0F, 0.3142F, 0.0F));

        left_wing_hand.addOrReplaceChild("l_prim4_r1", CubeListBuilder.create().texOffs(68, 71).addBox(-12.5F, -0.65F, -1.5F, 12.5F, 1.3F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.5F, 0.35F, 5.5F, 0.0F, 0.2443F, 0.0F));

        left_wing_hand.addOrReplaceChild("l_prim3_r1", CubeListBuilder.create().texOffs(0, 71).addBox(-13.5F, -0.65F, -1.5F, 13.5F, 1.3F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.5F, 0.25F, 3.0F, 0.0F, 0.1745F, 0.0F));

        left_wing_hand.addOrReplaceChild("l_prim2_r1", CubeListBuilder.create().texOffs(174, 64).addBox(-14.0F, -0.65F, -1.5F, 14.0F, 1.3F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.5F, 0.15F, 0.5F, 0.0F, 0.1047F, 0.0F));

        left_wing_hand.addOrReplaceChild("l_prim1_r1", CubeListBuilder.create().texOffs(106, 64).addBox(-13.5F, -0.65F, -1.5F, 13.5F, 1.3F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.5F, 0.05F, -2.0F, 0.0F, 0.0349F, 0.0F));

        PartDefinition right_wing = chest.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(123, 26).addBox(0.0F, -1.6F, -4.0F, 4.5F, 3.2F, 9.0F, new CubeDeformation(0.0F))
        .texOffs(115, 53).addBox(0.3F, -0.6F, 4.0F, 3.9F, 1.8F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.0F, -3.0F, -1.0F, 0.0F, -1.0472F, -0.384F));

        PartDefinition right_wing_fore = right_wing.addOrReplaceChild("right_wing_fore", CubeListBuilder.create().texOffs(83, 41).addBox(0.0F, -1.3F, -4.0F, 3.5F, 2.6F, 8.0F, new CubeDeformation(0.0F))
        .texOffs(107, 41).addBox(0.2F, -0.4F, 3.5F, 3.1F, 1.4F, 9.5F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.5F, 0.0F, -1.0F, 0.0F, 0.5236F, 0.6981F));

        PartDefinition right_wing_hand = right_wing_fore.addOrReplaceChild("right_wing_hand", CubeListBuilder.create().texOffs(220, 41).addBox(0.0F, -1.1F, -3.0F, 2.5F, 2.2F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.5F, 0.0F, -2.0F, 0.0F, 0.5934F, 0.4189F));

        right_wing_hand.addOrReplaceChild("r_prim5_r1", CubeListBuilder.create().texOffs(161, 71).addBox(0.0F, -0.65F, -1.5F, 11.0F, 1.3F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.5F, 0.45F, 8.0F, 0.0F, -0.3142F, 0.0F));

        right_wing_hand.addOrReplaceChild("r_prim4_r1", CubeListBuilder.create().texOffs(100, 71).addBox(0.0F, -0.65F, -1.5F, 12.5F, 1.3F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.5F, 0.35F, 5.5F, 0.0F, -0.2443F, 0.0F));

        right_wing_hand.addOrReplaceChild("r_prim3_r1", CubeListBuilder.create().texOffs(34, 71).addBox(0.0F, -0.65F, -1.5F, 13.5F, 1.3F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.5F, 0.25F, 3.0F, 0.0F, -0.1745F, 0.0F));

        right_wing_hand.addOrReplaceChild("r_prim2_r1", CubeListBuilder.create().texOffs(209, 64).addBox(0.0F, -0.65F, -1.5F, 14.0F, 1.3F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.5F, 0.15F, 0.5F, 0.0F, -0.1047F, 0.0F));

        right_wing_hand.addOrReplaceChild("r_prim1_r1", CubeListBuilder.create().texOffs(140, 64).addBox(0.0F, -0.65F, -1.5F, 13.5F, 1.3F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.5F, 0.05F, -2.0F, 0.0F, -0.0349F, 0.0F));

        PartDefinition front_left_leg = body.addOrReplaceChild("front_left_leg", CubeListBuilder.create().texOffs(0, 26).addBox(-1.8F, -0.7F, -2.8F, 3.6F, 7.7F, 5.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, 4.5F, -10.0F, -0.1047F, 0.0F, 0.0F));

        PartDefinition front_left_shank = front_left_leg.addOrReplaceChild("front_left_shank", CubeListBuilder.create().texOffs(240, 41).addBox(-1.0F, -0.4F, -1.3F, 2.0F, 7.2F, 2.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 6.5F, 0.0F, 0.1396F, 0.0F, 0.0F));

        PartDefinition front_left_foot = front_left_shank.addOrReplaceChild("front_left_foot", CubeListBuilder.create().texOffs(34, 64).addBox(-1.3F, 0.0F, -1.5F, 2.6F, 1.7F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(47, 64).addBox(-0.7F, 1.0F, -4.5F, 1.4F, 1.2F, 3.2F, new CubeDeformation(0.0F))
        .texOffs(217, 71).addBox(-0.4F, 1.1F, 1.2F, 0.8F, 1.1F, 2.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 6.8F, 0.0F, -0.0349F, 0.0F, 0.0F));

        front_left_foot.addOrReplaceChild("fl_toe_i_r1", CubeListBuilder.create().texOffs(208, 71).addBox(-0.2F, -0.55F, -2.5F, 1.2F, 1.1F, 2.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.7F, 1.65F, -1.3F, 0.0F, -0.4189F, 0.0F));

        front_left_foot.addOrReplaceChild("fl_toe_o_r1", CubeListBuilder.create().texOffs(199, 71).addBox(-1.0F, -0.55F, -2.5F, 1.2F, 1.1F, 2.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.7F, 1.65F, -1.3F, 0.0F, 0.4189F, 0.0F));

        PartDefinition front_right_leg = body.addOrReplaceChild("front_right_leg", CubeListBuilder.create().texOffs(19, 26).addBox(-1.8F, -0.7F, -2.8F, 3.6F, 7.7F, 5.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, 4.5F, -10.0F, -0.1047F, 0.0F, 0.0F));

        PartDefinition front_right_shank = front_right_leg.addOrReplaceChild("front_right_shank", CubeListBuilder.create().texOffs(0, 53).addBox(-1.0F, -0.4F, -1.3F, 2.0F, 7.2F, 2.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 6.5F, 0.0F, 0.1396F, 0.0F, 0.0F));

        PartDefinition front_right_foot = front_right_shank.addOrReplaceChild("front_right_foot", CubeListBuilder.create().texOffs(58, 64).addBox(-1.3F, 0.0F, -1.5F, 2.6F, 1.7F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(71, 64).addBox(-0.7F, 1.0F, -4.5F, 1.4F, 1.2F, 3.2F, new CubeDeformation(0.0F))
        .texOffs(243, 71).addBox(-0.4F, 1.1F, 1.2F, 0.8F, 1.1F, 2.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 6.8F, 0.0F, -0.0349F, 0.0F, 0.0F));

        front_right_foot.addOrReplaceChild("fr_toe_i_r1", CubeListBuilder.create().texOffs(234, 71).addBox(-1.0F, -0.55F, -2.5F, 1.2F, 1.1F, 2.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.7F, 1.65F, -1.3F, 0.0F, 0.4189F, 0.0F));

        front_right_foot.addOrReplaceChild("fr_toe_o_r1", CubeListBuilder.create().texOffs(225, 71).addBox(-0.2F, -0.55F, -2.5F, 1.2F, 1.1F, 2.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.7F, 1.65F, -1.3F, 0.0F, -0.4189F, 0.0F));

        PartDefinition back_left_leg = body.addOrReplaceChild("back_left_leg", CubeListBuilder.create().texOffs(88, 0).addBox(-2.2F, -1.2F, -4.2F, 4.8F, 9.2F, 7.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.4F, 2.0F, 7.5F, -0.1745F, 0.0F, 0.0F));

        PartDefinition back_left_shin = back_left_leg.addOrReplaceChild("back_left_shin", CubeListBuilder.create().texOffs(134, 41).addBox(-1.5F, -0.5F, -1.7F, 3.0F, 7.3F, 3.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 7.6F, 0.0F, 0.3491F, 0.0F, 0.0F));

        back_left_shin.addOrReplaceChild("back_left_cannon", CubeListBuilder.create().texOffs(16, 64).addBox(-1.0F, -0.4F, -1.0F, 2.0F, 3.2F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(82, 64).addBox(-1.3F, 1.1F, -1.4F, 2.6F, 1.9F, 2.9F, new CubeDeformation(0.0F))
        .texOffs(0, 77).addBox(-1.2F, 2.8F, -1.2F, 2.4F, 1.2F, 2.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 6.4F, -0.1F, -0.1745F, 0.0F, 0.0F));

        PartDefinition back_right_leg = body.addOrReplaceChild("back_right_leg", CubeListBuilder.create().texOffs(115, 0).addBox(-2.6F, -1.2F, -4.2F, 4.8F, 9.2F, 7.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.4F, 2.0F, 7.5F, -0.1745F, 0.0F, 0.0F));

        PartDefinition back_right_shin = back_right_leg.addOrReplaceChild("back_right_shin", CubeListBuilder.create().texOffs(148, 41).addBox(-1.5F, -0.5F, -1.7F, 3.0F, 7.3F, 3.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 7.6F, 0.0F, 0.3491F, 0.0F, 0.0F));

        back_right_shin.addOrReplaceChild("back_right_cannon", CubeListBuilder.create().texOffs(25, 64).addBox(-1.0F, -0.4F, -1.0F, 2.0F, 3.2F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(94, 64).addBox(-1.3F, 1.1F, -1.4F, 2.6F, 1.9F, 2.9F, new CubeDeformation(0.0F))
        .texOffs(12, 77).addBox(-1.2F, 2.8F, -1.2F, 2.4F, 1.2F, 2.6F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 6.4F, -0.1F, -0.1745F, 0.0F, 0.0F));

        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(201, 53).addBox(-2.2F, -0.3F, -0.7F, 4.4F, 3.8F, 4.2F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -2.5F, 11.5F, -0.2443F, 0.0F, 0.0F));

        PartDefinition tail_hair = tail.addOrReplaceChild("tail_hair", CubeListBuilder.create().texOffs(151, 26).addBox(-2.8F, -0.5F, -1.8F, 5.6F, 8.0F, 4.2F, new CubeDeformation(0.0F))
        .texOffs(11, 53).addBox(-2.0F, 6.5F, -1.2F, 4.0F, 6.5F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(220, 53).addBox(-1.2F, 12.0F, -0.7F, 2.4F, 4.5F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 2.0F, 2.5F, -0.0698F, 0.0F, 0.0F));

        tail_hair.addOrReplaceChild("hair_r_r1", CubeListBuilder.create().texOffs(240, 26).addBox(0.0F, 1.0F, -1.4F, 1.1F, 8.5F, 3.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.4F, 0.0F, 0.0F, 0.0F, 0.0F, 0.2269F));

        tail_hair.addOrReplaceChild("hair_l_r1", CubeListBuilder.create().texOffs(230, 26).addBox(-1.1F, 1.0F, -1.4F, 1.1F, 8.5F, 3.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.4F, 0.0F, 0.0F, 0.0F, 0.0F, -0.2269F));

        return LayerDefinition.create(meshdefinition, 256, 256);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(HippogriffEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        float partialTick = ageInTicks - (float) entity.tickCount;
        float walkAmount = Mth.clamp(limbSwingAmount, 0.0F, 1.0F);
        float walkCycle = limbSwing * 0.62F;
        float flight = entity.getFlightProgress(partialTick);
        float sit = entity.getSitProgress(partialTick);
        float landing = entity.getLandingImpulse(partialTick);
        float attack = entity.getAttackAnim(partialTick);
        boolean angry = entity.isAggressive();

        float headYaw = netHeadYaw * DEG;
        float headPitchRad = headPitch * DEG;
        float breathe = Mth.sin(ageInTicks * 0.07F) * 0.02F;

        // --- wings ------------------------------------------------------------------------
        // Folded at rest; flight unfurls them, and a grounded threat display half-mantles them.
        float mantle = (1.0F - flight) * (angry ? 0.5F : 0.0F);
        float open = Math.max(flight, mantle);
        float flap = Mth.sin(entity.getWingFlap(partialTick)) * (0.12F + flight * 0.88F);
        // The forearm and hand trail the shoulder, so the wing whips rather than moving rigidly.
        float flapFore = Mth.sin(entity.getWingFlap(partialTick) - 0.45F) * (0.08F + flight * 0.62F);
        float flapHand = Mth.sin(entity.getWingFlap(partialTick) - 0.9F) * (0.06F + flight * 0.70F);

        float wingY = Mth.lerp(open, REST_WING_Y, 0.0F);
        float wingZ = Mth.lerp(open, REST_WING_Z, OPEN_WING_Z) + flap * 0.55F;
        float foreY = Mth.lerp(open, REST_FORE_Y, 0.0F);
        float foreZ = Mth.lerp(open, REST_FORE_Z, OPEN_FORE_Z) + flapFore * 0.35F;
        float handY = Mth.lerp(open, REST_HAND_Y, 0.0F);
        float handZ = Mth.lerp(open, REST_HAND_Z, OPEN_HAND_Z) + flapHand * 0.45F;

        this.leftWing.yRot = wingY;
        this.leftWing.zRot = wingZ;
        this.leftWingFore.yRot = foreY;
        this.leftWingFore.zRot = foreZ;
        this.leftWingHand.yRot = handY;
        this.leftWingHand.zRot = handZ;
        this.rightWing.yRot = -wingY;
        this.rightWing.zRot = -wingZ;
        this.rightWingFore.yRot = -foreY;
        this.rightWingFore.zRot = -foreZ;
        this.rightWingHand.yRot = -handY;
        this.rightWingHand.zRot = -handZ;

        // A grounded hippogriff never holds perfectly still: the folded wings shudder faintly.
        float settle = (1.0F - open) * (Mth.sin(ageInTicks * 0.17F) * 0.02F
                + Mth.sin(ageInTicks * 0.41F + 1.7F) * 0.012F);
        this.leftWing.zRot += settle;
        this.rightWing.zRot -= settle;

        // --- head, neck and beak ----------------------------------------------------------
        this.neck.yRot += headYaw * 0.30F;
        this.neckUpper.yRot += headYaw * 0.28F;
        this.head.yRot += headYaw * 0.42F;
        this.neck.xRot += headPitchRad * 0.16F - flight * 0.20F + breathe;
        this.neckUpper.xRot += headPitchRad * 0.20F;
        this.head.xRot += headPitchRad * 0.34F + breathe * 1.5F;
        this.ruff.xRot += breathe * 2.0F;

        // The crest flares back when the creature is alarmed, the way a raptor raises its hackles.
        this.crest.xRot += angry ? 0.34F : 0.06F * Mth.sin(ageInTicks * 0.05F);

        // Beak: brief idle chirrups, plus a wide-open scream through the attack lunge.
        float chirp = Math.max(0.0F, Mth.sin(ageInTicks * 0.09F) - 0.94F) * 16.0F;
        float scream = attack > 0.0F ? Mth.sin(attack * (float) Math.PI) : 0.0F;
        float beakOpen = Mth.clamp(Math.max(chirp, scream * (angry ? 1.0F : 0.6F)), 0.0F, 1.0F);
        this.beakLower.xRot += beakOpen * 0.50F;
        this.beakUpper.xRot -= beakOpen * 0.14F;

        // --- gait -------------------------------------------------------------------------
        // Diagonal pairs, the way a horse trots: front-left travels with back-right.
        float swing = Mth.cos(walkCycle) * 0.85F * walkAmount;
        float frontTuck = Math.max(0.0F, -Mth.cos(walkCycle)) * 0.45F * walkAmount;
        float frontTuckAlt = Math.max(0.0F, Mth.cos(walkCycle)) * 0.45F * walkAmount;

        this.frontLeftLeg.xRot += Mth.lerp(flight, swing, -0.60F);
        this.frontRightLeg.xRot += Mth.lerp(flight, -swing, -0.60F);
        this.frontLeftShank.xRot += Mth.lerp(flight, frontTuck, 1.45F);
        this.frontRightShank.xRot += Mth.lerp(flight, frontTuckAlt, 1.45F);
        this.frontLeftFoot.xRot += Mth.lerp(flight, -frontTuck * 0.5F, -0.75F);
        this.frontRightFoot.xRot += Mth.lerp(flight, -frontTuckAlt * 0.5F, -0.75F);

        this.backLeftLeg.xRot += Mth.lerp(flight, -swing * 0.9F, 0.55F);
        this.backRightLeg.xRot += Mth.lerp(flight, swing * 0.9F, 0.55F);
        this.backLeftShin.xRot += Mth.lerp(flight, frontTuckAlt * 0.7F, -0.25F);
        this.backRightShin.xRot += Mth.lerp(flight, frontTuck * 0.7F, -0.25F);
        this.backLeftCannon.xRot += Mth.lerp(flight, -frontTuckAlt * 0.4F, 0.30F);
        this.backRightCannon.xRot += Mth.lerp(flight, -frontTuck * 0.4F, 0.30F);

        // Body rocks gently with the stride and pitches nose-down into flight.
        this.body.xRot += Mth.sin(walkCycle * 2.0F) * 0.03F * walkAmount - flight * 0.22F;
        this.body.zRot += Mth.cos(walkCycle) * 0.04F * walkAmount;
        this.chest.xRot += flight * 0.14F;

        // --- tail -------------------------------------------------------------------------
        this.tail.xRot += -flight * 0.30F + Mth.sin(ageInTicks * 0.06F) * 0.05F;
        this.tail.yRot += Mth.cos(walkCycle) * 0.09F * walkAmount;
        this.tailHair.xRot += Mth.sin(ageInTicks * 0.05F + 1.0F) * 0.06F;
        this.tailHair.zRot += Mth.sin(ageInTicks * 0.043F) * 0.06F + Mth.cos(walkCycle) * 0.05F * walkAmount;

        // --- sitting ----------------------------------------------------------------------
        if (sit > 0.001F) {
            this.body.y += 5.5F * sit;
            this.body.xRot += 0.12F * sit;
            this.backLeftLeg.xRot += 0.90F * sit;
            this.backRightLeg.xRot += 0.90F * sit;
            this.backLeftShin.xRot -= 1.50F * sit;
            this.backRightShin.xRot -= 1.50F * sit;
            this.backLeftCannon.xRot += 0.85F * sit;
            this.backRightCannon.xRot += 0.85F * sit;
            this.frontLeftLeg.xRot += 0.55F * sit;
            this.frontRightLeg.xRot += 0.55F * sit;
            this.frontLeftShank.xRot -= 0.75F * sit;
            this.frontRightShank.xRot -= 0.75F * sit;
            this.tail.xRot += 0.30F * sit;
        }

        // --- landing absorption -----------------------------------------------------------
        // Spikes for ~10 ticks after touchdown so the model compresses instead of snapping
        // straight to a stiff standing pose.
        if (landing > 0.0F) {
            float absorb = landing * 0.55F;
            this.frontLeftLeg.xRot += absorb * 0.5F;
            this.frontRightLeg.xRot += absorb * 0.5F;
            this.frontLeftShank.xRot += absorb;
            this.frontRightShank.xRot += absorb;
            this.backLeftLeg.xRot += absorb * 0.4F;
            this.backRightLeg.xRot += absorb * 0.4F;
            this.backLeftShin.xRot -= absorb * 0.8F;
            this.backRightShin.xRot -= absorb * 0.8F;
            this.body.y += landing * 1.8F;
            this.body.xRot += landing * 0.10F;
        }

        // --- the bow ----------------------------------------------------------------------
        // The lore-critical greeting: head sweeps low, front legs bend, the whole front end dips.
        if (entity.getInteractionState() == HippogriffEntity.InteractionState.BOWING) {
            float p = Mth.clamp(entity.getInteractionTicks() / (float) HippogriffEntity.BOW_DURATION_TICKS, 0.0F, 1.0F);
            float bow;
            if (p < 0.25F) {
                bow = smoothstep(p / 0.25F);
            } else if (p > 0.75F) {
                bow = 1.0F - smoothstep((p - 0.75F) / 0.25F);
            } else {
                bow = 1.0F;
            }
            this.body.xRot += bow * 0.30F;
            this.body.y += bow * 2.5F;
            this.chest.xRot += bow * 0.22F;
            this.neck.xRot += bow * 0.62F;
            this.neckUpper.xRot += bow * 0.30F;
            this.head.xRot += bow * 0.20F;
            this.crest.xRot -= bow * 0.18F;
            this.frontLeftLeg.xRot += bow * 0.75F;
            this.frontRightLeg.xRot += bow * 0.75F;
            this.frontLeftShank.xRot -= bow * 0.45F;
            this.frontRightShank.xRot -= bow * 0.45F;
            this.backLeftLeg.xRot -= bow * 0.18F;
            this.backRightLeg.xRot -= bow * 0.18F;
            this.tail.xRot -= bow * 0.22F;
            // Wings part slightly, as if bracing.
            this.leftWing.zRot += bow * 0.20F;
            this.rightWing.zRot -= bow * 0.20F;
        }

        // --- rear and strike --------------------------------------------------------------
        if (attack > 0.0F && flight < 0.7F) {
            float rear;
            float slam;
            if (attack < 0.45F) {
                rear = smoothstep(attack / 0.45F);
                slam = 0.0F;
            } else {
                rear = 1.0F - smoothstep((attack - 0.45F) / 0.55F);
                slam = smoothstep((attack - 0.45F) / 0.2F);
            }

            this.body.xRot -= rear * 0.42F;
            this.chest.xRot -= rear * 0.30F;
            this.neck.xRot -= rear * 0.22F;
            this.head.xRot += rear * 0.26F;
            this.crest.xRot += rear * 0.25F;
            this.frontLeftLeg.xRot += -1.85F * rear + 1.25F * slam;
            this.frontRightLeg.xRot += -1.85F * rear + 1.25F * slam;
            this.frontLeftShank.xRot += 1.05F * rear - 0.80F * slam;
            this.frontRightShank.xRot += 1.05F * rear - 0.80F * slam;
            this.frontLeftFoot.xRot -= 0.55F * rear;
            this.frontRightFoot.xRot -= 0.55F * rear;
            this.backLeftLeg.xRot += rear * 0.30F;
            this.backRightLeg.xRot += rear * 0.30F;
            // Wings snap out for balance on the rear-up.
            this.leftWing.zRot += rear * 0.55F;
            this.rightWing.zRot -= rear * 0.55F;
            this.leftWing.yRot -= rear * 0.45F;
            this.rightWing.yRot += rear * 0.45F;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
        this.root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }
}
