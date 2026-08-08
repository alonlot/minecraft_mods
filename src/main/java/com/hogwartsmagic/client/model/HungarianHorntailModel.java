package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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

/**
 * Hand-coded model for the Hungarian Horntail, built as a true wyvern the way the film's dragon is:
 * <b>four limbs, not six</b> — two heavy digitigrade hind legs and two wings whose wrists double as
 * forelimbs. On the ground it knuckle-walks on those wrists with the elbows up high, bat-fashion; in
 * the air the same three wing bones straighten into a full sail.
 *
 * <p>Everything is sized so the hind feet and wing wrists land exactly on the ground plane
 * (model y = {@value #GROUND_Y}), which is what stops the old model floating and clipping.
 *
 * <p>Animation is pure keyframe math in {@link #setupAnim}. State overlays use if/else (never a
 * switch-on-enum) to avoid the synthetic {@code $1} switch-map helper class that has been observed
 * to fail to load in this mod's dev environment.
 */
public class HungarianHorntailModel extends EntityModel<HungarianHorntailEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "hungarian_horntail"), "main");

    /** Model-space Y of the ground plane (the renderer's scale pivots here, so it holds at any scale). */
    public static final float GROUND_Y = 24.0F;
    /** Body pivot height, chosen so the hind feet reach {@link #GROUND_Y} in the rest pose. */
    private static final float BODY_Y = 9.0F;

    // Rest ("knuckle-walking") wing angles — the wrist lands on the ground with the elbow held high.
    private static final float STAND_HUMERUS_Z = 0.90F;
    private static final float STAND_FOREARM_Z = 1.20F;
    /** Folds the wing right back on itself so the sail stands over the flank instead of splaying out. */
    private static final float STAND_FINGER_Z = -3.45F;
    /** Small outward yaw so the folded sail's trailing edge lifts clear of the ground. */
    private static final float STAND_FINGER_Y = 0.35F;

    // Hind leg rest angles — digitigrade: femur forward, shank back, foot flat.
    private static final float STAND_THIGH_X = -0.55F;
    private static final float STAND_SHANK_X = 1.17F;
    private static final float STAND_FOOT_X = -0.62F;

    // Neck rest angles — a low, forward-carried, snaking neck.
    private static final float STAND_NECK1_X = -0.50F;
    private static final float STAND_NECK2_X = 0.55F;
    private static final float STAND_HEAD_X = 0.30F;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart neck1;
    private final ModelPart neck2;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart tail1;
    private final ModelPart tail2;
    private final ModelPart tail3;
    private final ModelPart tail4;

    private final ModelPart leftHumerus;
    private final ModelPart leftForearm;
    private final ModelPart leftFinger;
    private final ModelPart rightHumerus;
    private final ModelPart rightForearm;
    private final ModelPart rightFinger;

    private final ModelPart leftThigh;
    private final ModelPart leftShank;
    private final ModelPart leftFoot;
    private final ModelPart rightThigh;
    private final ModelPart rightShank;
    private final ModelPart rightFoot;

    public HungarianHorntailModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.neck1 = this.body.getChild("neck1");
        this.neck2 = this.neck1.getChild("neck2");
        this.head = this.neck2.getChild("head");
        this.jaw = this.head.getChild("jaw");
        this.tail1 = this.body.getChild("tail1");
        this.tail2 = this.tail1.getChild("tail2");
        this.tail3 = this.tail2.getChild("tail3");
        this.tail4 = this.tail3.getChild("tail4");

        this.leftHumerus = this.body.getChild("left_humerus");
        this.leftForearm = this.leftHumerus.getChild("left_forearm");
        this.leftFinger = this.leftForearm.getChild("left_finger");
        this.rightHumerus = this.body.getChild("right_humerus");
        this.rightForearm = this.rightHumerus.getChild("right_forearm");
        this.rightFinger = this.rightForearm.getChild("right_finger");

        this.leftThigh = this.body.getChild("left_thigh");
        this.leftShank = this.leftThigh.getChild("left_shank");
        this.leftFoot = this.leftShank.getChild("left_foot");
        this.rightThigh = this.body.getChild("right_thigh");
        this.rightShank = this.rightThigh.getChild("right_shank");
        this.rightFoot = this.rightShank.getChild("right_foot");
    }

    // -------------------------------------------------------------------------------------------
    // Geometry
    // -------------------------------------------------------------------------------------------

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // ---- Torso. Runs along Z, nose at -Z. Slung low between the hips and shoulders. ----
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-7.0F, -9.0F, -15.0F, 14.0F, 14.0F, 28.0F, new CubeDeformation(0.0F))
                        // shoulder mass carrying the wings
                        .texOffs(0, 44).addBox(-8.0F, -12.0F, -12.0F, 16.0F, 5.0F, 14.0F, new CubeDeformation(0.0F))
                        // pelvis
                        .texOffs(0, 96).addBox(-6.0F, -9.0F, 5.0F, 12.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
                        // dorsal ridge
                        .texOffs(0, 64).addBox(-1.0F, -14.0F, -12.0F, 2.0F, 3.0F, 26.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, BODY_Y, 0.0F));

        // Dorsal spikes marching down the spine.
        for (int i = 0; i < 7; i++) {
            float z = -11.0F + i * 3.8F;
            float len = 5.0F - Math.abs(i - 2) * 0.4F;
            spike(body, "spine_" + i, 0.0F, -13.5F, z, -0.35F, 0.0F, len);
        }

        // ---- Neck (two segments) -> head -> jaw. ----
        PartDefinition neck1 = body.addOrReplaceChild("neck1", CubeListBuilder.create()
                        .texOffs(88, 0).addBox(-4.0F, -4.0F, -14.0F, 8.0F, 8.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -8.0F, -14.0F, STAND_NECK1_X, 0.0F, 0.0F));
        for (int i = 0; i < 3; i++) {
            spike(neck1, "neck1_spike_" + i, 0.0F, -4.0F, -3.0F - i * 4.0F, -0.5F, 0.0F, 3.5F);
        }

        PartDefinition neck2 = neck1.addOrReplaceChild("neck2", CubeListBuilder.create()
                        .texOffs(88, 24).addBox(-3.0F, -3.0F, -14.0F, 6.0F, 6.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, -13.0F, STAND_NECK2_X, 0.0F, 0.0F));
        for (int i = 0; i < 3; i++) {
            spike(neck2, "neck2_spike_" + i, 0.0F, -3.0F, -3.0F - i * 4.0F, -0.5F, 0.0F, 3.0F);
        }

        PartDefinition head = neck2.addOrReplaceChild("head", CubeListBuilder.create()
                        // skull
                        .texOffs(136, 0).addBox(-4.5F, -4.0F, -13.0F, 9.0F, 8.0F, 14.0F, new CubeDeformation(0.0F))
                        // upper snout
                        .texOffs(136, 24).addBox(-3.0F, -1.0F, -20.0F, 6.0F, 5.0F, 8.0F, new CubeDeformation(0.0F))
                        // brow ridges over the eyes
                        .texOffs(190, 24).addBox(2.5F, -5.5F, -12.0F, 3.0F, 2.0F, 7.0F, new CubeDeformation(0.0F))
                        .texOffs(190, 36).addBox(-5.5F, -5.5F, -12.0F, 3.0F, 2.0F, 7.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, -13.0F, STAND_HEAD_X, 0.0F, 0.0F));

        // Amber eyes, set slightly proud of the skull so the glow layer catches them.
        head.addOrReplaceChild("eye_left", CubeListBuilder.create()
                        .texOffs(200, 0).addBox(-0.5F, -1.5F, -2.0F, 1.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(4.6F, -2.2F, -9.0F));
        head.addOrReplaceChild("eye_right", CubeListBuilder.create()
                        .texOffs(212, 0).addBox(-0.5F, -1.5F, -2.0F, 1.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-4.6F, -2.2F, -9.0F));

        // The crown of bronze horns the film dragon is named for.
        horn(head, "horn_back_l", 4.0F, -4.5F, -2.0F, 0.55F, 0.45F, 12.0F);
        horn(head, "horn_back_r", -4.0F, -4.5F, -2.0F, 0.55F, -0.45F, 12.0F);
        horn(head, "horn_mid_l", 5.0F, -3.5F, -6.0F, 0.25F, 0.85F, 9.0F);
        horn(head, "horn_mid_r", -5.0F, -3.5F, -6.0F, 0.25F, -0.85F, 9.0F);
        horn(head, "horn_top_l", 2.0F, -5.0F, -4.0F, 0.75F, 0.18F, 10.0F);
        horn(head, "horn_top_r", -2.0F, -5.0F, -4.0F, 0.75F, -0.18F, 10.0F);
        spike(head, "cheek_l", 4.5F, -1.0F, -8.0F, 0.1F, 1.3F, 4.0F);
        spike(head, "cheek_r", -4.5F, -1.0F, -8.0F, 0.1F, -1.3F, 4.0F);

        head.addOrReplaceChild("jaw", CubeListBuilder.create()
                        .texOffs(136, 40).addBox(-3.5F, 0.0F, -15.0F, 7.0F, 3.0F, 15.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 3.5F, -6.0F));

        // ---- Tail: four tapering segments, spiked the whole way, ending in the horn cluster. ----
        PartDefinition tail1 = body.addOrReplaceChild("tail1", CubeListBuilder.create()
                        .texOffs(0, 120).addBox(-4.0F, -4.0F, 0.0F, 8.0F, 8.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -3.0F, 13.0F));
        PartDefinition tail2 = tail1.addOrReplaceChild("tail2", CubeListBuilder.create()
                        .texOffs(48, 120).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 13.0F));
        PartDefinition tail3 = tail2.addOrReplaceChild("tail3", CubeListBuilder.create()
                        .texOffs(92, 120).addBox(-2.5F, -2.5F, 0.0F, 5.0F, 5.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 13.0F));
        PartDefinition tail4 = tail3.addOrReplaceChild("tail4", CubeListBuilder.create()
                        .texOffs(134, 120).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 13.0F));
        for (int i = 0; i < 3; i++) {
            spike(tail1, "tail1_spike_" + i, 0.0F, -4.0F, 3.0F + i * 4.0F, -0.4F, 0.0F, 4.0F);
            spike(tail2, "tail2_spike_" + i, 0.0F, -3.0F, 3.0F + i * 4.0F, -0.4F, 0.0F, 3.5F);
            spike(tail3, "tail3_spike_" + i, 0.0F, -2.5F, 3.0F + i * 4.0F, -0.4F, 0.0F, 3.0F);
        }
        // The business end.
        horn(tail4, "sting_up", 0.0F, -2.0F, 12.0F, -0.55F, 0.0F, 10.0F);
        horn(tail4, "sting_l", 2.0F, 0.0F, 12.0F, 0.0F, 0.75F, 9.0F);
        horn(tail4, "sting_r", -2.0F, 0.0F, 12.0F, 0.0F, -0.75F, 9.0F);
        horn(tail4, "sting_dn", 0.0F, 2.0F, 11.0F, 0.55F, 0.0F, 8.0F);
        horn(tail4, "sting_back", 0.0F, 0.0F, 14.0F, -1.57F, 0.0F, 9.0F);

        // ---- Wings: humerus -> forearm -> finger, each carrying part of the sail. ----
        buildWing(body, true);
        buildWing(body, false);

        // ---- Hind legs: thigh -> shank -> foot. ----
        buildLeg(body, true);
        buildLeg(body, false);

        return LayerDefinition.create(mesh, 256, 256);
    }

    /** A bronze horn: a tapering spar growing out of the parent along -Y before rotation. */
    private static void horn(PartDefinition parent, String name, float x, float y, float z,
                             float xRot, float zRot, float length) {
        parent.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(232, 0).addBox(-1.5F, -length, -1.5F, 3.0F, length, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(x, y, z, xRot, 0.0F, zRot));
    }

    /** A smaller ridge spike. */
    private static void spike(PartDefinition parent, String name, float x, float y, float z,
                              float xRot, float zRot, float length) {
        parent.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(232, 20).addBox(-1.0F, -length, -1.0F, 2.0F, length, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(x, y, z, xRot, 0.0F, zRot));
    }

    private static void buildWing(PartDefinition body, boolean left) {
        float s = left ? 1.0F : -1.0F;
        String p = left ? "left_" : "right_";

        // Humerus: shoulder -> elbow. Boxes authored along +X and mirrored for the right side.
        CubeListBuilder humerus = CubeListBuilder.create();
        addMirrored(humerus, left, 112, 146, 0.0F, -2.5F, -2.5F, 13.0F, 5.0F, 5.0F);
        addMirrored(humerus, left, 0, 210, 0.0F, -0.5F, 2.0F, 13.0F, 1.0F, 14.0F); // shoulder sail
        PartDefinition hum = body.addOrReplaceChild(p + "humerus", humerus,
                PartPose.offsetAndRotation(7.0F * s, -7.0F, -6.0F, 0.0F, 0.0F, STAND_HUMERUS_Z * s));

        // Forearm: elbow -> wrist. Length 11 is what puts the wrist just clear of the ground in the
        // rest pose (13*sin(0.90) + 11*sin(2.10) = 21.7), leaving the claw to make contact.
        CubeListBuilder forearm = CubeListBuilder.create();
        addMirrored(forearm, left, 152, 146, 0.0F, -2.0F, -2.0F, 11.0F, 4.0F, 4.0F);
        addMirrored(forearm, left, 0, 180, 0.0F, -0.5F, 1.0F, 11.0F, 1.0F, 20.0F); // inner sail
        PartDefinition fore = hum.addOrReplaceChild(p + "forearm", forearm,
                PartPose.offsetAndRotation(13.0F * s, 0.0F, 0.0F, 0.0F, 0.0F, STAND_FOREARM_Z * s));
        // The thumb claw at the wrist — this is what the dragon actually walks on, so it is angled
        // down-and-forward to reach the ground from the wrist. Only xRot, so wing folding never
        // swings it out of the vertical plane.
        spike(fore, p + "claw", 11.0F * s, 0.0F, 0.0F, 2.05F, 0.0F, 5.0F);

        // Finger: wrist -> wingtip, carrying the main sail.
        CubeListBuilder finger = CubeListBuilder.create();
        addMirrored(finger, left, 0, 170, 0.0F, -1.5F, -1.5F, 24.0F, 3.0F, 3.0F);
        addMirrored(finger, left, 72, 180, 0.0F, -0.5F, 0.0F, 24.0F, 1.0F, 26.0F); // outer sail
        fore.addOrReplaceChild(p + "finger", finger,
                PartPose.offsetAndRotation(11.0F * s, 0.0F, 0.0F,
                        0.0F, STAND_FINGER_Y * s, STAND_FINGER_Z * s));
    }

    /** Adds a box authored for the left side, mirrored onto -X for the right. */
    private static void addMirrored(CubeListBuilder b, boolean left, int u, int v,
                                    float x, float y, float z, float dx, float dy, float dz) {
        if (left) {
            b.texOffs(u, v).addBox(x, y, z, dx, dy, dz, new CubeDeformation(0.0F));
        } else {
            b.texOffs(u, v).mirror().addBox(-x - dx, y, z, dx, dy, dz, new CubeDeformation(0.0F)).mirror(false);
        }
    }

    private static void buildLeg(PartDefinition body, boolean left) {
        float s = left ? 1.0F : -1.0F;
        String p = left ? "left_" : "right_";
        boolean mir = !left;

        CubeListBuilder thigh = CubeListBuilder.create();
        if (!mir) {
            thigh.texOffs(0, 146).addBox(-4.0F, -1.0F, -5.0F, 8.0F, 10.0F, 10.0F, new CubeDeformation(0.0F));
        } else {
            thigh.texOffs(0, 146).mirror().addBox(-4.0F, -1.0F, -5.0F, 8.0F, 10.0F, 10.0F, new CubeDeformation(0.0F)).mirror(false);
        }
        PartDefinition th = body.addOrReplaceChild(p + "thigh", thigh,
                PartPose.offsetAndRotation(7.0F * s, -3.0F, 9.0F, STAND_THIGH_X, 0.0F, 0.0F));

        CubeListBuilder shank = CubeListBuilder.create();
        if (!mir) {
            shank.texOffs(40, 146).addBox(-3.0F, -1.0F, -3.5F, 6.0F, 10.0F, 7.0F, new CubeDeformation(0.0F));
        } else {
            shank.texOffs(40, 146).mirror().addBox(-3.0F, -1.0F, -3.5F, 6.0F, 10.0F, 7.0F, new CubeDeformation(0.0F)).mirror(false);
        }
        PartDefinition sh = th.addOrReplaceChild(p + "shank", shank,
                PartPose.offsetAndRotation(0.0F, 9.0F, 0.0F, STAND_SHANK_X, 0.0F, 0.0F));

        CubeListBuilder foot = CubeListBuilder.create();
        if (!mir) {
            foot.texOffs(70, 146).addBox(-4.0F, 0.0F, -8.0F, 8.0F, 3.0F, 11.0F, new CubeDeformation(0.0F));
        } else {
            foot.texOffs(70, 146).mirror().addBox(-4.0F, 0.0F, -8.0F, 8.0F, 3.0F, 11.0F, new CubeDeformation(0.0F)).mirror(false);
        }
        PartDefinition ft = sh.addOrReplaceChild(p + "foot", foot,
                PartPose.offsetAndRotation(0.0F, 9.0F, 0.0F, STAND_FOOT_X, 0.0F, 0.0F));
        spike(ft, p + "claw_a", -2.5F, 3.0F, -8.0F, 1.9F, 0.25F, 4.0F);
        spike(ft, p + "claw_b", 0.0F, 3.0F, -8.5F, 1.9F, 0.0F, 4.5F);
        spike(ft, p + "claw_c", 2.5F, 3.0F, -8.0F, 1.9F, -0.25F, 4.0F);
    }

    // -------------------------------------------------------------------------------------------
    // Animation
    // -------------------------------------------------------------------------------------------

    @Override
    public void setupAnim(HungarianHorntailEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        resetPose();

        boolean flying = entity.isFlying();
        HungarianHorntailEntity.AttackState state = entity.getAttackState();
        int t = entity.getStateTick();

        // Head tracking, split across both neck segments so the neck snakes rather than hinging.
        float yaw = netHeadYaw * Mth.DEG_TO_RAD;
        float pitch = headPitch * Mth.DEG_TO_RAD;
        this.neck1.yRot = yaw * 0.30F;
        this.neck2.yRot = yaw * 0.40F;
        this.head.yRot = yaw * 0.30F;
        // Pitch is split so the two segments together add up to the entity's actual look angle —
        // that is what keeps the fire jet leaving the snout rather than the air beside it.
        this.neck2.xRot = STAND_NECK2_X + pitch * 0.35F;
        this.head.xRot = STAND_HEAD_X + pitch * 0.65F;

        // Breathing, and a constant low-frequency sway so it never looks frozen.
        float breathe = Mth.sin(ageInTicks * 0.055F);
        this.body.y = BODY_Y + breathe * 0.35F;
        this.neck1.xRot = STAND_NECK1_X + breathe * 0.035F;
        this.jaw.xRot = 0.04F + breathe * 0.02F;

        // Tail hangs and drifts; each segment lags the one before it.
        float drift = ageInTicks * 0.035F;
        this.tail1.xRot = 0.14F + Mth.sin(drift) * 0.04F;
        this.tail2.xRot = 0.10F + Mth.sin(drift - 0.7F) * 0.05F;
        this.tail3.xRot = 0.04F + Mth.sin(drift - 1.4F) * 0.06F;
        this.tail4.xRot = -0.05F + Mth.sin(drift - 2.1F) * 0.07F;
        this.tail1.yRot = Mth.sin(drift * 0.9F) * 0.10F;
        this.tail2.yRot = Mth.sin(drift * 0.9F - 0.6F) * 0.13F;
        this.tail3.yRot = Mth.sin(drift * 0.9F - 1.2F) * 0.16F;
        this.tail4.yRot = Mth.sin(drift * 0.9F - 1.8F) * 0.19F;

        if (flying) {
            applyFlight(entity, ageInTicks, state);
        } else {
            applyKnuckleWalk(limbSwing, limbSwingAmount, ageInTicks);
        }

        // -------- State overlays (if/else; never switch-on-enum). --------
        if (state == HungarianHorntailEntity.AttackState.ROAR) {
            applyRoar(t);
        } else if (state == HungarianHorntailEntity.AttackState.BITE) {
            applyBite(t);
        } else if (state == HungarianHorntailEntity.AttackState.TAIL_WHIP) {
            applyTailWhip(t, entity.isSweepingLeft());
        } else if (state == HungarianHorntailEntity.AttackState.BREATH_WINDUP) {
            applyBreathWindup(t);
        } else if (state == HungarianHorntailEntity.AttackState.BREATH) {
            applyBreath(t, ageInTicks, pitch);
        } else if (state == HungarianHorntailEntity.AttackState.WING_GUST) {
            applyWingGust(t);
        } else if (state == HungarianHorntailEntity.AttackState.TAKEOFF) {
            applyTakeoff(t);
        } else if (state == HungarianHorntailEntity.AttackState.AIR_BREATH) {
            applyAirBreath(t, ageInTicks, pitch);
        } else if (state == HungarianHorntailEntity.AttackState.DIVE) {
            applyDive(ageInTicks);
        } else if (state == HungarianHorntailEntity.AttackState.LAND) {
            applyLand(t);
        } else if (state == HungarianHorntailEntity.AttackState.STAGGER) {
            applyStagger(t);
        }

        if (entity.isRaging() && state == HungarianHorntailEntity.AttackState.NONE) {
            float tremor = Mth.sin(ageInTicks * 0.75F) * 0.018F;
            this.body.xRot += tremor;
            this.neck1.xRot += tremor;
        }
    }

    private void resetPose() {
        this.body.xRot = 0.0F;
        this.body.yRot = 0.0F;
        this.body.zRot = 0.0F;
        this.body.y = BODY_Y;

        this.neck1.xRot = STAND_NECK1_X;
        this.neck1.yRot = 0.0F;
        this.neck1.zRot = 0.0F;
        this.neck2.xRot = STAND_NECK2_X;
        this.neck2.yRot = 0.0F;
        this.neck2.zRot = 0.0F;
        this.head.xRot = STAND_HEAD_X;
        this.head.yRot = 0.0F;
        this.head.zRot = 0.0F;
        this.jaw.xRot = 0.04F;

        this.tail1.xRot = 0.14F; this.tail1.yRot = 0.0F; this.tail1.zRot = 0.0F;
        this.tail2.xRot = 0.10F; this.tail2.yRot = 0.0F; this.tail2.zRot = 0.0F;
        this.tail3.xRot = 0.04F; this.tail3.yRot = 0.0F; this.tail3.zRot = 0.0F;
        this.tail4.xRot = -0.05F; this.tail4.yRot = 0.0F; this.tail4.zRot = 0.0F;

        setWings(STAND_HUMERUS_Z, 0.0F, 0.0F, STAND_FOREARM_Z, 0.0F, STAND_FINGER_Z, STAND_FINGER_Y);
        setLegs(STAND_THIGH_X, STAND_SHANK_X, STAND_FOOT_X, STAND_THIGH_X, STAND_SHANK_X, STAND_FOOT_X);
    }

    /** Sets both wings symmetrically; the right side mirrors the Z and Y rotations. */
    private void setWings(float humZ, float humY, float humX,
                          float foreZ, float foreY, float fingZ, float fingY) {
        this.leftHumerus.zRot = humZ;
        this.leftHumerus.yRot = humY;
        this.leftHumerus.xRot = humX;
        this.leftForearm.zRot = foreZ;
        this.leftForearm.yRot = foreY;
        this.leftForearm.xRot = 0.0F;
        this.leftFinger.zRot = fingZ;
        this.leftFinger.yRot = fingY;
        this.leftFinger.xRot = 0.0F;

        this.rightHumerus.zRot = -humZ;
        this.rightHumerus.yRot = -humY;
        this.rightHumerus.xRot = humX;
        this.rightForearm.zRot = -foreZ;
        this.rightForearm.yRot = -foreY;
        this.rightForearm.xRot = 0.0F;
        this.rightFinger.zRot = -fingZ;
        this.rightFinger.yRot = -fingY;
        this.rightFinger.xRot = 0.0F;
    }

    private void setLegs(float lThigh, float lShank, float lFoot,
                         float rThigh, float rShank, float rFoot) {
        this.leftThigh.xRot = lThigh;
        this.leftShank.xRot = lShank;
        this.leftFoot.xRot = lFoot;
        this.rightThigh.xRot = rThigh;
        this.rightShank.xRot = rShank;
        this.rightFoot.xRot = rFoot;
    }

    /**
     * The ground gait: a four-beat knuckle-walk. Each wing wrist is a front foot, so the wings swing
     * fore-and-aft in diagonal couplets with the hind legs — left wrist with right hind, and so on.
     */
    private void applyKnuckleWalk(float limbSwing, float limbSwingAmount, float ageInTicks) {
        float phase = limbSwing * 0.45F;
        float amp = Math.min(limbSwingAmount, 1.0F);
        float a = Mth.cos(phase) * amp;         // diagonal couplet A
        float b = Mth.cos(phase + Mth.PI) * amp; // couplet B

        // Hind legs: swing the femur, and extend/flex the shank so the foot clears the ground.
        float lLift = Math.max(0.0F, Mth.sin(phase)) * amp;
        float rLift = Math.max(0.0F, Mth.sin(phase + Mth.PI)) * amp;
        setLegs(STAND_THIGH_X + b * 0.55F, STAND_SHANK_X - rLift * 0.35F, STAND_FOOT_X + rLift * 0.30F,
                STAND_THIGH_X + a * 0.55F, STAND_SHANK_X - lLift * 0.35F, STAND_FOOT_X + lLift * 0.30F);

        // Wing wrists: swing the whole arm from the shoulder, tuck the elbow on the recovery stroke.
        this.leftHumerus.zRot = STAND_HUMERUS_Z - Math.max(0.0F, a) * 0.16F * amp;
        this.rightHumerus.zRot = -(STAND_HUMERUS_Z - Math.max(0.0F, b) * 0.16F * amp);
        this.leftHumerus.xRot = a * 0.38F;
        this.rightHumerus.xRot = b * 0.38F;
        this.leftForearm.zRot = STAND_FOREARM_Z + Math.max(0.0F, a) * 0.22F * amp;
        this.rightForearm.zRot = -(STAND_FOREARM_Z + Math.max(0.0F, b) * 0.22F * amp);

        // The whole animal rolls and pitches over its shoulders as the weight shifts.
        this.body.zRot = Mth.sin(phase) * 0.045F * amp;
        this.body.xRot = Mth.cos(phase * 2.0F) * 0.025F * amp;
        this.body.y = BODY_Y + Mth.cos(phase * 2.0F) * 0.5F * amp + Mth.sin(ageInTicks * 0.055F) * 0.35F;

        // The head bobs with the stride and the neck counter-swings — heavy-animal weight.
        this.neck1.xRot += Mth.cos(phase * 2.0F) * 0.05F * amp;
        this.neck2.xRot -= Mth.cos(phase * 2.0F) * 0.04F * amp;
        this.neck1.yRot += Mth.sin(phase) * 0.07F * amp;

        // Tail counter-sways against the gait to balance it.
        this.tail1.yRot += Mth.sin(phase) * 0.16F * amp;
        this.tail2.yRot += Mth.sin(phase - 0.5F) * 0.19F * amp;
        this.tail3.yRot += Mth.sin(phase - 1.0F) * 0.22F * amp;
        this.tail4.yRot += Mth.sin(phase - 1.5F) * 0.25F * amp;
    }

    /** Flight base pose: the three wing bones straighten out into a sail and beat. */
    private void applyFlight(HungarianHorntailEntity entity, float ageInTicks,
                             HungarianHorntailEntity.AttackState state) {
        boolean hard = state == HungarianHorntailEntity.AttackState.TAKEOFF
                || state == HungarianHorntailEntity.AttackState.AIR_BREATH;
        float rate = hard ? 0.42F : 0.26F;
        float beat = Mth.sin(ageInTicks * rate);
        float amp = hard ? 0.85F : 0.60F;

        // Downstroke drives the arm down and the tip trails behind it — a proper flapping wave.
        setWings(0.10F + beat * amp, 0.15F, 0.0F,
                0.14F + beat * amp * 0.55F, -0.10F,
                0.06F + beat * amp * 0.75F, -0.22F);

        // Legs trail back, neck extends, tail streams out behind.
        setLegs(0.75F, 0.55F, 0.25F, 0.75F, 0.55F, 0.25F);
        this.neck1.xRot = -0.28F;
        this.neck2.xRot = 0.22F;
        this.tail1.xRot = -0.12F + beat * 0.05F;
        this.tail2.xRot = -0.08F + beat * 0.05F;
        this.tail3.xRot = -0.04F + beat * 0.06F;
        this.tail4.xRot = 0.0F + beat * 0.07F;

        if (state == HungarianHorntailEntity.AttackState.FLY) {
            // Bank into the turn, and pitch with the climb/descent — this is what sells real flight.
            float turn = Mth.wrapDegrees(entity.getYRot() - entity.yRotO);
            this.body.zRot = Mth.clamp(turn * 0.055F, -0.7F, 0.7F);
            this.body.xRot = Mth.clamp((float) -entity.getDeltaMovement().y * 0.55F, -0.45F, 0.45F)
                    + beat * 0.05F;
            // The head stays level with the horizon while the body banks.
            this.neck1.zRot = -this.body.zRot * 0.55F;
            this.neck2.zRot = -this.body.zRot * 0.35F;
        }
    }

    private void applyRoar(int t) {
        float p = clamp01((float) t / HungarianHorntailEntity.ROAR_DURATION);
        float peak = Mth.sin(p * Mth.PI);
        // Rear up, throw the head back and hold the maw wide open.
        this.neck1.xRot = STAND_NECK1_X - 1.0F * peak;
        this.neck2.xRot = STAND_NECK2_X - 0.55F * peak;
        this.head.xRot = STAND_HEAD_X - 0.75F * peak;
        this.jaw.xRot = 0.04F + 0.85F * peak;
        this.body.xRot = -0.18F * peak;
        this.body.y = BODY_Y - 1.6F * peak;
        // Wings flare wide and high — the threat display.
        float open = Mth.lerp(peak, STAND_HUMERUS_Z, -0.45F);
        setWings(open, 0.2F * peak, 0.0F,
                Mth.lerp(peak, STAND_FOREARM_Z, 0.35F), -0.15F * peak,
                Mth.lerp(peak, STAND_FINGER_Z, 0.15F), Mth.lerp(peak, STAND_FINGER_Y, -0.25F));
        // Hunkers back on the hind legs.
        setLegs(STAND_THIGH_X + 0.25F * peak, STAND_SHANK_X + 0.2F * peak, STAND_FOOT_X - 0.15F * peak,
                STAND_THIGH_X + 0.25F * peak, STAND_SHANK_X + 0.2F * peak, STAND_FOOT_X - 0.15F * peak);
        // Tail lashes with the roar.
        this.tail1.yRot += Mth.sin(p * Mth.PI * 4.0F) * 0.18F;
        this.tail2.yRot += Mth.sin(p * Mth.PI * 4.0F - 0.7F) * 0.22F;
        this.tail3.yRot += Mth.sin(p * Mth.PI * 4.0F - 1.4F) * 0.26F;
    }

    private void applyBite(int t) {
        float p = clamp01((float) t / HungarianHorntailEntity.BITE_DURATION);
        // Coil back, then fire the head forward on a straightening neck.
        float coil = p < 0.35F ? (p / 0.35F) : 0.0F;
        float lunge = p < 0.35F ? 0.0F
                : (p < 0.55F ? (p - 0.35F) / 0.20F : 1.0F - (p - 0.55F) / 0.45F);
        this.neck1.xRot = STAND_NECK1_X - 0.35F * coil + 0.55F * lunge;
        this.neck2.xRot = STAND_NECK2_X + 0.30F * coil + 0.35F * lunge;
        this.head.xRot = STAND_HEAD_X + 0.35F * lunge;
        this.body.xRot = 0.10F * lunge - 0.06F * coil;
        this.body.y = BODY_Y - 1.0F * lunge;
        // Jaw gapes on the way in and snaps shut exactly on the strike frame.
        float jawOpen = p < 0.45F ? (p / 0.45F) : Math.max(0.0F, 1.0F - (p - 0.45F) / 0.12F);
        this.jaw.xRot = 0.04F + 0.95F * jawOpen;
        // Wings brace against the ground for the lunge.
        this.leftHumerus.xRot = -0.25F * lunge;
        this.rightHumerus.xRot = -0.25F * lunge;
        setLegs(STAND_THIGH_X - 0.3F * lunge, STAND_SHANK_X + 0.25F * lunge, STAND_FOOT_X,
                STAND_THIGH_X - 0.3F * lunge, STAND_SHANK_X + 0.25F * lunge, STAND_FOOT_X);
    }

    /**
     * The tail sweep. The entity spins its whole body (server-side); here the tail is dragged behind
     * that spin by exactly {@link HungarianHorntailEntity#tailTrail}, which is also what the damage
     * arc is computed from — so the spikes hit what they visibly pass through.
     */
    private void applyTailWhip(int t, boolean sweepLeft) {
        float p = clamp01((float) t / HungarianHorntailEntity.TAIL_WHIP_DURATION);
        float sign = sweepLeft ? 1.0F : -1.0F;
        float trail = -HungarianHorntailEntity.tailTrail(p) * sign;

        // Spread the trail across the segments so the tail reads as a whip, not a stiff pole.
        this.tail1.yRot = trail * 0.22F;
        this.tail2.yRot = trail * 0.26F;
        this.tail3.yRot = trail * 0.26F;
        this.tail4.yRot = trail * 0.26F;
        // It flattens out level with the ground for the sweep.
        float flat = Mth.sin(p * Mth.PI);
        this.tail1.xRot = 0.14F - 0.16F * flat;
        this.tail2.xRot = 0.10F - 0.12F * flat;
        this.tail3.xRot = 0.04F - 0.08F * flat;
        this.tail4.xRot = -0.05F - 0.05F * flat;

        // The body crouches and leans into the spin; the head whips round to follow it.
        this.body.y = BODY_Y + 1.2F * flat;
        this.body.zRot = -0.16F * flat * sign;
        this.neck1.yRot = 0.45F * flat * sign;
        this.neck2.yRot = 0.35F * flat * sign;
        this.head.yRot = 0.25F * flat * sign;
        this.jaw.xRot = 0.04F + 0.25F * flat;
        // Wings clamp in tight so they are not flailing through the spin.
        setWings(STAND_HUMERUS_Z + 0.18F * flat, 0.0F, 0.0F,
                STAND_FOREARM_Z + 0.10F * flat, 0.0F, STAND_FINGER_Z, STAND_FINGER_Y);
        setLegs(STAND_THIGH_X + 0.2F * flat, STAND_SHANK_X + 0.15F * flat, STAND_FOOT_X,
                STAND_THIGH_X + 0.2F * flat, STAND_SHANK_X + 0.15F * flat, STAND_FOOT_X);
    }

    private void applyBreathWindup(int t) {
        float p = clamp01((float) t / HungarianHorntailEntity.BREATH_WINDUP_DURATION);
        float e = p * p; // accelerating inhale
        // Draws the head back over the shoulders, chest swelling, jaw cracking open.
        this.neck1.xRot = STAND_NECK1_X - 0.55F * e;
        this.neck2.xRot = STAND_NECK2_X + 0.45F * e;
        this.head.xRot = STAND_HEAD_X - 0.30F * e;
        this.jaw.xRot = 0.04F + 0.45F * e;
        this.body.xRot = -0.07F * e;
        // Wings half-open and plant, bracing for the recoil.
        setWings(Mth.lerp(e, STAND_HUMERUS_Z, 0.45F), 0.0F, 0.0F,
                Mth.lerp(e, STAND_FOREARM_Z, 0.65F), 0.0F,
                Mth.lerp(e, STAND_FINGER_Z, -1.4F), STAND_FINGER_Y);
    }

    private void applyBreath(int t, float ageInTicks, float pitch) {
        float p = clamp01((float) t / HungarianHorntailEntity.BREATH_DURATION);
        float thrust = Mth.sin(Math.min(p / 0.15F, 1.0F) * (Mth.PI / 2.0F));
        // Head drives forward and the neck straightens out behind the jet.
        this.neck1.xRot = STAND_NECK1_X + 0.40F * thrust;
        this.neck2.xRot = STAND_NECK2_X - 0.25F * thrust + pitch * 0.35F;
        this.head.xRot = STAND_HEAD_X + 0.25F * thrust + pitch * 0.65F;
        // Maw held wide with the flutter of a pressurised jet.
        this.jaw.xRot = 1.05F + Mth.sin(ageInTicks * 0.9F) * 0.06F;
        // Recoil shudder through the whole body — it is pushing back against the fire.
        float shudder = Mth.sin(ageInTicks * 1.3F) * 0.022F;
        this.body.xRot = -0.05F * thrust + shudder;
        this.body.zRot = Mth.sin(ageInTicks * 0.7F) * 0.02F;
        this.neck1.yRot += Mth.sin(ageInTicks * 1.1F) * 0.03F;
        // Wings stay planted and braced.
        setWings(0.55F + shudder, 0.0F, 0.0F, 0.75F, 0.0F, -1.35F, STAND_FINGER_Y * 0.6F);
        setLegs(STAND_THIGH_X + 0.15F, STAND_SHANK_X + 0.1F, STAND_FOOT_X,
                STAND_THIGH_X + 0.15F, STAND_SHANK_X + 0.1F, STAND_FOOT_X);
        // Tail braced out straight behind as a counterweight.
        this.tail1.xRot = 0.02F;
        this.tail2.xRot = 0.0F;
        this.tail3.xRot = -0.02F;
        this.tail4.xRot = -0.04F;
    }

    private void applyWingGust(int t) {
        float p = clamp01((float) t / HungarianHorntailEntity.WING_GUST_DURATION);
        float beat;
        if (p < 0.42F) {
            beat = -1.0F * (p / 0.42F);                     // haul both wings up high
        } else if (p < 0.55F) {
            beat = -1.0F + 2.6F * ((p - 0.42F) / 0.13F);    // slam them down
        } else {
            beat = 1.6F * (1.0F - (p - 0.55F) / 0.45F);
        }
        setWings(0.05F + beat * 0.85F, 0.25F, 0.0F,
                0.10F + beat * 0.5F, -0.1F,
                0.05F + beat * 0.65F, -0.2F);
        // It rears back on its hind legs to swing them.
        float rear = Mth.sin(p * Mth.PI);
        this.body.xRot = -0.28F * rear;
        this.body.y = BODY_Y - 2.0F * rear;
        setLegs(STAND_THIGH_X + 0.35F * rear, STAND_SHANK_X + 0.25F * rear, STAND_FOOT_X - 0.2F * rear,
                STAND_THIGH_X + 0.35F * rear, STAND_SHANK_X + 0.25F * rear, STAND_FOOT_X - 0.2F * rear);
        this.neck1.xRot = STAND_NECK1_X - 0.25F * rear;
        this.jaw.xRot = 0.04F + 0.4F * rear;
    }

    private void applyTakeoff(int t) {
        float p = clamp01((float) t / HungarianHorntailEntity.TAKEOFF_DURATION);
        // First it compresses, then hammers three downbeats and folds the legs away.
        float crouch = p < 0.2F ? (p / 0.2F) : Math.max(0.0F, 1.0F - (p - 0.2F) / 0.25F);
        float launch = clamp01((p - 0.2F) / 0.8F);
        float beat = Mth.sin(p * Mth.PI * 3.0F);

        setWings(0.08F + beat * 0.95F, 0.2F, 0.0F,
                0.12F + beat * 0.6F, -0.1F,
                0.05F + beat * 0.8F, Mth.lerp(launch, STAND_FINGER_Y, -0.22F));
        this.body.xRot = -0.32F * launch;
        this.body.y = BODY_Y + 2.5F * crouch;
        this.neck1.xRot = STAND_NECK1_X - 0.25F * launch;
        // Legs push off hard, then tuck up.
        float extend = Mth.sin(clamp01(p / 0.35F) * Mth.PI);
        float tuck = clamp01((p - 0.35F) / 0.65F);
        setLegs(Mth.lerp(tuck, STAND_THIGH_X + 0.4F * extend, 0.75F),
                Mth.lerp(tuck, STAND_SHANK_X - 0.9F * extend, 0.55F),
                Mth.lerp(tuck, STAND_FOOT_X + 0.5F * extend, 0.25F),
                Mth.lerp(tuck, STAND_THIGH_X + 0.4F * extend, 0.75F),
                Mth.lerp(tuck, STAND_SHANK_X - 0.9F * extend, 0.55F),
                Mth.lerp(tuck, STAND_FOOT_X + 0.5F * extend, 0.25F));
        this.tail1.xRot = -0.2F * launch;
        this.tail2.xRot = -0.15F * launch;
    }

    private void applyAirBreath(int t, float ageInTicks, float pitch) {
        // Hovering: fast, shallow, hard beats while the neck arcs down over the target.
        float charge = clamp01((float) t / HungarianHorntailEntity.AIR_BREATH_WINDUP);
        boolean firing = t >= HungarianHorntailEntity.AIR_BREATH_WINDUP;
        this.body.xRot = -0.22F + Mth.sin(ageInTicks * 0.42F) * 0.05F;
        this.neck1.xRot = STAND_NECK1_X + 0.25F * charge;
        this.neck2.xRot = STAND_NECK2_X + 0.30F * charge + pitch * 0.4F;
        this.head.xRot = STAND_HEAD_X + pitch * 0.55F;
        this.jaw.xRot = firing ? 1.0F + Mth.sin(ageInTicks * 0.9F) * 0.06F : 0.04F + 0.5F * charge;
        if (firing) {
            this.body.xRot += Mth.sin(ageInTicks * 1.3F) * 0.03F;
        }
        // Legs hang loose beneath it the way a hovering raptor's do.
        setLegs(0.45F, 0.85F, 0.1F, 0.45F, 0.85F, 0.1F);
        this.tail1.xRot = 0.25F;
        this.tail2.xRot = 0.20F;
        this.tail3.xRot = 0.15F;
    }

    private void applyDive(float ageInTicks) {
        // A stoop: wings swept back into a delta, neck stretched out, everything streamlined.
        setWings(0.55F, 0.85F, 0.0F, 0.45F, 0.65F, 0.30F, 0.55F);
        this.body.xRot = 0.62F;
        this.body.zRot = Mth.sin(ageInTicks * 0.25F) * 0.06F;
        this.neck1.xRot = -0.12F;
        this.neck2.xRot = 0.10F;
        this.head.xRot = 0.20F;
        this.jaw.xRot = 0.35F;
        // Legs and talons come forward to rake.
        setLegs(-0.55F, 0.35F, 0.6F, -0.55F, 0.35F, 0.6F);
        this.tail1.xRot = -0.35F;
        this.tail2.xRot = -0.25F;
        this.tail3.xRot = -0.15F;
        this.tail4.xRot = -0.10F;
    }

    private void applyLand(int t) {
        float p = clamp01((float) t / HungarianHorntailEntity.LAND_DURATION);
        // Flare: wings cupped forward to brake, body pitched nose-up, legs reaching for the ground.
        float flare = 1.0F - p;
        setWings(Mth.lerp(p, -0.35F, STAND_HUMERUS_Z), 0.55F * flare, 0.0F,
                Mth.lerp(p, 0.75F, STAND_FOREARM_Z), 0.4F * flare,
                Mth.lerp(p, 0.9F, STAND_FINGER_Z), Mth.lerp(p, 0.5F, STAND_FINGER_Y));
        this.body.xRot = -0.45F * flare;
        this.body.y = BODY_Y - 2.0F * flare;
        this.neck1.xRot = STAND_NECK1_X - 0.2F * flare;
        // Legs stretch down to take the weight, then fold into the standing pose.
        setLegs(Mth.lerp(p, -0.35F, STAND_THIGH_X), Mth.lerp(p, 0.35F, STAND_SHANK_X),
                Mth.lerp(p, 0.45F, STAND_FOOT_X),
                Mth.lerp(p, -0.35F, STAND_THIGH_X), Mth.lerp(p, 0.35F, STAND_SHANK_X),
                Mth.lerp(p, 0.45F, STAND_FOOT_X));
        this.tail1.xRot = -0.25F * flare;
        this.tail2.xRot = -0.20F * flare;
    }

    private void applyStagger(int t) {
        float p = clamp01((float) t / HungarianHorntailEntity.STAGGER_DURATION);
        float recoil = Mth.sin(p * Mth.PI);
        this.neck1.xRot = STAND_NECK1_X - 0.45F * recoil;
        this.neck2.xRot = STAND_NECK2_X + 0.30F * recoil;
        this.head.xRot = STAND_HEAD_X - 0.30F * recoil;
        this.jaw.xRot = 0.04F + 0.45F * recoil;
        this.body.xRot = -0.16F * recoil;
        this.body.zRot = Mth.sin(p * Mth.PI * 2.0F) * 0.10F;
        this.body.y = BODY_Y + 1.0F * recoil;
        setWings(STAND_HUMERUS_Z - 0.35F * recoil, 0.0F, 0.0F,
                STAND_FOREARM_Z + 0.2F * recoil, 0.0F,
                STAND_FINGER_Z + 0.5F * recoil, STAND_FINGER_Y);
    }

    private static float clamp01(float v) {
        return Mth.clamp(v, 0.0F, 1.0F);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
                               int packedOverlay, int color) {
        this.root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }
}
