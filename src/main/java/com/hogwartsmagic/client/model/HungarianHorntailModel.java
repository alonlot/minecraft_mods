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
 * Hand-coded wyvern model for the Hungarian Horntail: master {@code body} part with a two-segment
 * neck + horned head + jaw, two membranous wings (upper arm + forearm + membrane), small forelimbs,
 * two large hind legs, and a three-segment spiked tail.
 *
 * <p>Animation is pure keyframe math in {@link #setupAnim}. State overlays use if/else (never a
 * switch-on-enum) to avoid the synthetic {@code $1} switch-map helper class that has been observed
 * to fail to load in this mod's dev environment.
 */
public class HungarianHorntailModel extends EntityModel<HungarianHorntailEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "hungarian_horntail"), "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart tail1;
    private final ModelPart tail2;
    private final ModelPart tail3;
    private final ModelPart leftWing;
    private final ModelPart leftWingTip;
    private final ModelPart rightWing;
    private final ModelPart rightWingTip;
    private final ModelPart frontLeftLeg;
    private final ModelPart frontRightLeg;
    private final ModelPart hindLeftLeg;
    private final ModelPart hindRightLeg;

    // Rest-pose constants for the wings (folded back along the spine).
    private static final float WING_FOLD_Z = 1.35F;
    private static final float WING_SPREAD_Z = 0.15F;

    public HungarianHorntailModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.neck = this.body.getChild("neck");
        this.head = this.neck.getChild("head");
        this.jaw = this.head.getChild("jaw");
        this.tail1 = this.body.getChild("tail1");
        this.tail2 = this.tail1.getChild("tail2");
        this.tail3 = this.tail2.getChild("tail3");
        this.leftWing = this.body.getChild("left_wing");
        this.leftWingTip = this.leftWing.getChild("left_wing_tip");
        this.rightWing = this.body.getChild("right_wing");
        this.rightWingTip = this.rightWing.getChild("right_wing_tip");
        this.frontLeftLeg = this.body.getChild("front_left_leg");
        this.frontRightLeg = this.body.getChild("front_right_leg");
        this.hindLeftLeg = this.body.getChild("hind_left_leg");
        this.hindRightLeg = this.body.getChild("hind_right_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // ---- Body (master). Pivot raised off the ground; torso runs along Z (front = -Z). ----
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-7.0F, -9.0F, -15.0F, 14.0F, 14.0F, 28.0F, new CubeDeformation(0.0F))
                        // shoulder hump over the wings
                        .texOffs(0, 44).addBox(-8.0F, -11.0F, -10.0F, 16.0F, 5.0F, 13.0F, new CubeDeformation(0.0F))
                        // back spine ridge
                        .texOffs(60, 0).addBox(-1.0F, -13.0F, -12.0F, 2.0F, 3.0F, 24.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 6.0F, 0.0F));

        // Spine spikes along the back.
        for (int i = 0; i < 6; i++) {
            float z = -10.0F + i * 4.0F;
            body.addOrReplaceChild("spine_" + i, CubeListBuilder.create()
                            .texOffs(64, 30).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)),
                    PartPose.offsetAndRotation(0.0F, -12.0F, z, -0.25F, 0.0F, 0.0F));
        }

        // ---- Neck → head → jaw (front, -Z). ----
        PartDefinition neck = body.addOrReplaceChild("neck", CubeListBuilder.create()
                        .texOffs(70, 44).addBox(-3.5F, -4.0F, -14.0F, 7.0F, 8.0F, 16.0F, new CubeDeformation(0.0F))
                        .texOffs(96, 0).addBox(-1.0F, -7.0F, -12.0F, 2.0F, 3.0F, 12.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -7.0F, -13.0F, -0.55F, 0.0F, 0.0F));

        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create()
                        // skull
                        .texOffs(0, 62).addBox(-4.0F, -5.0F, -12.0F, 8.0F, 7.0F, 13.0F, new CubeDeformation(0.0F))
                        // upper snout
                        .texOffs(40, 62).addBox(-3.0F, -1.0F, -17.0F, 6.0F, 4.0F, 6.0F, new CubeDeformation(0.0F))
                        // brow ridges
                        .texOffs(0, 82).addBox(-5.0F, -6.0F, -7.0F, 3.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(16, 82).addBox(2.0F, -6.0F, -7.0F, 3.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, -13.0F, 0.45F, 0.0F, 0.0F));

        // Crown of horns (the signature spread of horns from the films).
        addHorn(head, "horn_back_l", 4.0F, -6.0F, -4.0F, 0.5F, 0.4F, 9.0F);
        addHorn(head, "horn_back_r", -4.0F, -6.0F, -4.0F, 0.5F, -0.4F, 9.0F);
        addHorn(head, "horn_mid_l", 5.0F, -5.0F, -7.0F, 0.2F, 0.7F, 7.0F);
        addHorn(head, "horn_mid_r", -5.0F, -5.0F, -7.0F, 0.2F, -0.7F, 7.0F);
        addHorn(head, "horn_top_l", 2.0F, -7.0F, -6.0F, 0.7F, 0.15F, 6.0F);
        addHorn(head, "horn_top_r", -2.0F, -7.0F, -6.0F, 0.7F, -0.15F, 6.0F);

        // Jaw hinged at the back of the skull.
        head.addOrReplaceChild("jaw", CubeListBuilder.create()
                        .texOffs(40, 73).addBox(-3.0F, 0.0F, -16.0F, 6.0F, 2.0F, 16.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 2.0F, -1.0F));

        // ---- Tail: three tapering segments + a cluster of deadly spikes at the tip. ----
        PartDefinition tail1 = body.addOrReplaceChild("tail1", CubeListBuilder.create()
                        .texOffs(70, 70).addBox(-3.5F, -3.5F, 0.0F, 7.0F, 7.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -5.0F, 13.0F));
        PartDefinition tail2 = tail1.addOrReplaceChild("tail2", CubeListBuilder.create()
                        .texOffs(70, 92).addBox(-2.5F, -2.5F, 0.0F, 5.0F, 5.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 13.0F));
        PartDefinition tail3 = tail2.addOrReplaceChild("tail3", CubeListBuilder.create()
                        .texOffs(70, 112).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 13.0F));
        // Tail spikes (the "horntail") — splayed at the very end.
        addHorn(tail3, "spike_up", 0.0F, -2.0F, 12.0F, -0.5F, 0.0F, 7.0F);
        addHorn(tail3, "spike_l", 1.5F, 0.0F, 12.0F, 0.0F, 0.6F, 6.0F);
        addHorn(tail3, "spike_r", -1.5F, 0.0F, 12.0F, 0.0F, -0.6F, 6.0F);
        addHorn(tail3, "spike_dn", 0.0F, 2.0F, 12.0F, 0.5F, 0.0F, 6.0F);

        // ---- Wings: upper arm bone + membrane, with a folding forearm tip. ----
        buildWing(body, "left_wing", 1.0F);
        buildWing(body, "right_wing", -1.0F);

        // ---- Legs. Front pair smaller, hind pair large and powerful. ----
        body.addOrReplaceChild("front_left_leg", legCubes(false),
                PartPose.offset(6.0F, -1.0F, -8.0F));
        body.addOrReplaceChild("front_right_leg", legCubes(true),
                PartPose.offset(-6.0F, -1.0F, -8.0F));
        body.addOrReplaceChild("hind_left_leg", hindLegCubes(false),
                PartPose.offset(7.0F, -2.0F, 9.0F));
        body.addOrReplaceChild("hind_right_leg", hindLegCubes(true),
                PartPose.offset(-7.0F, -2.0F, 9.0F));

        return LayerDefinition.create(mesh, 128, 128);
    }

    /** A single tapering horn/spike approximated by a small angled box. */
    private static void addHorn(PartDefinition parent, String name, float x, float y, float z,
                                float xRot, float zRot, float length) {
        parent.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(96, 16).addBox(-1.0F, -length, -1.0F, 2.0F, length, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(x, y, z, xRot, 0.0F, zRot));
    }

    private static void buildWing(PartDefinition body, String name, float side) {
        // side: +1 = left (extends +X), -1 = right (extends -X). Boxes authored for the left wing
        // and mirrored on X for the right via negative origins.
        boolean left = side > 0.0F;
        float pivotX = 6.0F * side;
        CubeListBuilder arm = CubeListBuilder.create();
        if (left) {
            arm.texOffs(0, 96).addBox(0.0F, -1.5F, -2.0F, 18.0F, 3.0F, 5.0F, new CubeDeformation(0.0F))
               // membrane sheet behind the arm
               .texOffs(0, 104).addBox(0.0F, -0.5F, 3.0F, 16.0F, 1.0F, 16.0F, new CubeDeformation(0.0F));
        } else {
            arm.texOffs(0, 96).mirror().addBox(-18.0F, -1.5F, -2.0F, 18.0F, 3.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false)
               .texOffs(0, 104).mirror().addBox(-16.0F, -0.5F, 3.0F, 16.0F, 1.0F, 16.0F, new CubeDeformation(0.0F)).mirror(false);
        }
        PartDefinition wing = body.addOrReplaceChild(name, arm,
                PartPose.offsetAndRotation(pivotX, -8.0F, -4.0F, 0.0F, 0.0F, left ? WING_FOLD_Z : -WING_FOLD_Z));

        CubeListBuilder tip = CubeListBuilder.create();
        if (left) {
            tip.texOffs(40, 96).addBox(0.0F, -1.0F, -1.0F, 16.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
               .texOffs(40, 102).addBox(0.0F, -0.5F, 2.0F, 14.0F, 1.0F, 17.0F, new CubeDeformation(0.0F));
        } else {
            tip.texOffs(40, 96).mirror().addBox(-16.0F, -1.0F, -1.0F, 16.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false)
               .texOffs(40, 102).mirror().addBox(-14.0F, -0.5F, 2.0F, 14.0F, 1.0F, 17.0F, new CubeDeformation(0.0F)).mirror(false);
        }
        wing.addOrReplaceChild(name + "_tip", tip,
                PartPose.offsetAndRotation(18.0F * side, 0.0F, 0.0F, 0.0F, 0.0F, left ? -0.3F : 0.3F));
    }

    private static CubeListBuilder legCubes(boolean mirror) {
        CubeListBuilder b = CubeListBuilder.create();
        if (!mirror) {
            b.texOffs(96, 40).addBox(-2.0F, 0.0F, -2.5F, 5.0F, 12.0F, 5.0F, new CubeDeformation(0.0F))
             .texOffs(96, 58).addBox(-2.5F, 11.0F, -6.0F, 6.0F, 3.0F, 7.0F, new CubeDeformation(0.0F));
        } else {
            b.texOffs(96, 40).mirror().addBox(-3.0F, 0.0F, -2.5F, 5.0F, 12.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false)
             .texOffs(96, 58).mirror().addBox(-3.5F, 11.0F, -6.0F, 6.0F, 3.0F, 7.0F, new CubeDeformation(0.0F)).mirror(false);
        }
        return b;
    }

    private static CubeListBuilder hindLegCubes(boolean mirror) {
        CubeListBuilder b = CubeListBuilder.create();
        if (!mirror) {
            b.texOffs(112, 70).addBox(-3.0F, 0.0F, -3.0F, 7.0F, 16.0F, 8.0F, new CubeDeformation(0.0F))
             .texOffs(96, 94).addBox(-3.5F, 14.0F, -8.0F, 8.0F, 3.0F, 9.0F, new CubeDeformation(0.0F));
        } else {
            b.texOffs(112, 70).mirror().addBox(-4.0F, 0.0F, -3.0F, 7.0F, 16.0F, 8.0F, new CubeDeformation(0.0F)).mirror(false)
             .texOffs(96, 94).mirror().addBox(-4.5F, 14.0F, -8.0F, 8.0F, 3.0F, 9.0F, new CubeDeformation(0.0F)).mirror(false);
        }
        return b;
    }

    @Override
    public void setupAnim(HungarianHorntailEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // -------- Base idle pose. --------
        resetPose();

        boolean flying = entity.isFlying();
        HungarianHorntailEntity.AttackState state = entity.getAttackState();
        int t = entity.getStateTick();

        // Head tracking (damped; overridden during committed actions below).
        this.neck.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.35F;
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.35F;
        this.head.xRot = 0.45F + headPitch * Mth.DEG_TO_RAD * 0.4F;

        // Gentle breathing + idle sway everywhere.
        float breathe = Mth.sin(ageInTicks * 0.05F) * 0.02F;
        this.body.y = 6.0F + Mth.sin(ageInTicks * 0.05F) * 0.3F;
        this.neck.xRot = -0.55F + breathe;
        this.tail1.xRot = 0.05F + Mth.sin(ageInTicks * 0.04F) * 0.04F;
        this.tail1.yRot = Mth.sin(ageInTicks * 0.03F) * 0.10F;
        this.tail2.yRot = Mth.sin(ageInTicks * 0.03F - 0.6F) * 0.14F;
        this.tail3.yRot = Mth.sin(ageInTicks * 0.03F - 1.2F) * 0.18F;

        if (flying) {
            applyFlightBase(ageInTicks, state);
        } else {
            applyGroundWalk(limbSwing, limbSwingAmount, ageInTicks);
        }

        // -------- State overlays (if/else; never switch-on-enum). --------
        if (state == HungarianHorntailEntity.AttackState.ROAR) {
            applyRoar(t);
        } else if (state == HungarianHorntailEntity.AttackState.BITE) {
            applyBite(t);
        } else if (state == HungarianHorntailEntity.AttackState.TAIL_WHIP) {
            applyTailWhip(t);
        } else if (state == HungarianHorntailEntity.AttackState.BREATH_WINDUP) {
            applyBreathWindup(t);
        } else if (state == HungarianHorntailEntity.AttackState.BREATH) {
            applyBreath(t, ageInTicks);
        } else if (state == HungarianHorntailEntity.AttackState.WING_GUST) {
            applyWingGust(t);
        } else if (state == HungarianHorntailEntity.AttackState.TAKEOFF) {
            applyTakeoff(t);
        } else if (state == HungarianHorntailEntity.AttackState.DIVE) {
            applyDive(t);
        } else if (state == HungarianHorntailEntity.AttackState.LAND) {
            applyLand(t);
        } else if (state == HungarianHorntailEntity.AttackState.STAGGER) {
            applyStagger(t);
        }

        if (entity.isRaging() && state == HungarianHorntailEntity.AttackState.NONE) {
            float tremor = Mth.sin(ageInTicks * 0.8F) * 0.02F;
            this.body.xRot += tremor;
            this.neck.xRot += tremor;
        }
    }

    private void resetPose() {
        this.body.xRot = 0.0F;
        this.body.yRot = 0.0F;
        this.body.zRot = 0.0F;
        this.body.y = 6.0F;
        this.neck.xRot = -0.55F;
        this.neck.yRot = 0.0F;
        this.head.xRot = 0.45F;
        this.head.yRot = 0.0F;
        this.jaw.xRot = 0.0F;
        setWing(WING_FOLD_Z, 0.0F, -0.3F);
        this.tail1.xRot = 0.05F;
        this.tail1.yRot = 0.0F;
        this.tail2.xRot = 0.0F;
        this.tail2.yRot = 0.0F;
        this.tail3.xRot = 0.0F;
        this.tail3.yRot = 0.0F;
        this.frontLeftLeg.xRot = 0.0F;
        this.frontRightLeg.xRot = 0.0F;
        this.hindLeftLeg.xRot = 0.0F;
        this.hindRightLeg.xRot = 0.0F;
    }

    /** Sets both wings symmetrically. base = root zRot magnitude, flap adds, tipZ = tip fold. */
    private void setWing(float baseZ, float flap, float tipZ) {
        this.leftWing.zRot = baseZ + flap;
        this.leftWing.xRot = 0.0F;
        this.leftWing.yRot = 0.0F;
        this.leftWingTip.zRot = tipZ;
        this.rightWing.zRot = -(baseZ + flap);
        this.rightWing.xRot = 0.0F;
        this.rightWing.yRot = 0.0F;
        this.rightWingTip.zRot = -tipZ;
    }

    private void applyGroundWalk(float limbSwing, float limbSwingAmount, float ageInTicks) {
        float phase = limbSwing * 0.5F;
        float amp = Math.min(limbSwingAmount, 1.0F);
        float gait = Mth.cos(phase) * amp;
        // Diagonal gait: front-left + hind-right together, opposite pair anti-phase.
        this.frontLeftLeg.xRot = gait * 0.9F;
        this.hindRightLeg.xRot = gait * 0.9F;
        this.frontRightLeg.xRot = -gait * 0.9F;
        this.hindLeftLeg.xRot = -gait * 0.9F;
        // Body rocks subtly with the stride.
        this.body.zRot = Mth.sin(phase) * 0.03F * amp;
        // Wings stay folded; small settle flap when idle.
        float settle = Mth.sin(ageInTicks * 0.06F) * 0.05F;
        setWing(WING_FOLD_Z + settle, 0.0F, -0.3F);
        // Tail counter-sways to the walk.
        this.tail1.yRot += Mth.sin(phase) * 0.15F * amp;
        this.tail2.yRot += Mth.sin(phase - 0.5F) * 0.18F * amp;
        this.tail3.yRot += Mth.sin(phase - 1.0F) * 0.2F * amp;
    }

    private void applyFlightBase(float ageInTicks, HungarianHorntailEntity.AttackState state) {
        // Big slow wingbeat.
        float beat = Mth.sin(ageInTicks * 0.35F);
        setWing(WING_SPREAD_Z, beat * 0.7F, 0.1F + beat * 0.4F);
        // Legs tuck back, neck levels out, tail trails.
        this.frontLeftLeg.xRot = -0.9F;
        this.frontRightLeg.xRot = -0.9F;
        this.hindLeftLeg.xRot = 0.7F;
        this.hindRightLeg.xRot = 0.7F;
        this.neck.xRot = -0.2F;
        this.tail1.xRot = -0.15F + beat * 0.05F;
        // Slight body bob with the beat (unless an aerial sub-state overrides pitch).
        if (state == HungarianHorntailEntity.AttackState.FLY) {
            this.body.xRot = beat * 0.06F;
        }
    }

    private void applyRoar(int t) {
        float p = clamp01((float) t / HungarianHorntailEntity.ROAR_DURATION);
        float peak = Mth.sin(p * Mth.PI);
        this.neck.xRot = -0.55F - 0.9F * peak;     // rear the neck up
        this.head.xRot = 0.45F - 0.7F * peak;       // tilt head back
        this.jaw.xRot = 0.7F * peak;                // open maw
        this.body.xRot = -0.12F * peak;
        // Flare the wings wide.
        float spread = Mth.lerp(peak, WING_FOLD_Z, WING_SPREAD_Z - 0.1F);
        setWing(spread, 0.0F, Mth.lerp(peak, -0.3F, 0.2F));
    }

    private void applyBite(int t) {
        float p = clamp01((float) t / HungarianHorntailEntity.BITE_DURATION);
        // Lunge in over the first ~45%, snap, then recover.
        float lunge = p < 0.45F ? (p / 0.45F) : (1.0F - (p - 0.45F) / 0.55F);
        this.neck.xRot = -0.55F + 0.7F * lunge;
        this.head.xRot = 0.45F + 0.3F * lunge;
        this.body.xRot = 0.12F * lunge;
        // Jaw snaps shut at the strike frame.
        float jawOpen = p < 0.4F ? (p / 0.4F) : Math.max(0.0F, 1.0F - (p - 0.4F) / 0.2F);
        this.jaw.xRot = 0.8F * jawOpen;
    }

    private void applyTailWhip(int t) {
        float p = clamp01((float) t / HungarianHorntailEntity.TAIL_WHIP_DURATION);
        // Wind to one side, then whip across.
        float swing;
        if (p < 0.35F) {
            swing = -0.8F * (p / 0.35F);
        } else if (p < 0.65F) {
            swing = -0.8F + 2.6F * ((p - 0.35F) / 0.30F);
        } else {
            swing = 1.8F * (1.0F - (p - 0.65F) / 0.35F);
        }
        this.body.yRot = swing * 0.12F;
        this.tail1.yRot = swing * 0.5F;
        this.tail2.yRot = swing * 0.7F;
        this.tail3.yRot = swing * 0.9F;
        this.tail1.xRot = 0.05F - 0.2F * Mth.sin(p * Mth.PI);
    }

    private void applyBreathWindup(int t) {
        float p = clamp01((float) t / HungarianHorntailEntity.BREATH_WINDUP_DURATION);
        // Rear the head back and inhale, jaw cracking open.
        this.neck.xRot = -0.55F - 0.5F * p;
        this.head.xRot = 0.45F - 0.4F * p;
        this.jaw.xRot = 0.4F * p;
        setWing(Mth.lerp(p, WING_FOLD_Z, 0.6F), 0.0F, -0.1F);
    }

    private void applyBreath(int t, float ageInTicks) {
        float p = clamp01((float) t / HungarianHorntailEntity.BREATH_DURATION);
        // Head thrusts forward and down, maw wide, slight recoil pulse.
        float thrust = Mth.sin(Math.min(p / 0.2F, 1.0F) * (Mth.PI / 2.0F));
        this.neck.xRot = -0.55F + 0.45F * thrust;
        this.head.xRot = 0.45F + 0.35F * thrust;
        this.jaw.xRot = 0.9F + Mth.sin(ageInTicks * 0.6F) * 0.07F;
        // Recoil jitter from the jet.
        this.body.xRot = Mth.sin(ageInTicks * 0.9F) * 0.02F;
        setWing(0.6F, Mth.sin(ageInTicks * 0.3F) * 0.1F, 0.0F);
    }

    private void applyWingGust(int t) {
        float p = clamp01((float) t / HungarianHorntailEntity.WING_GUST_DURATION);
        // Raise high, then slam down at the strike.
        float beat;
        if (p < 0.4F) {
            beat = -0.9F * (p / 0.4F);          // lift up
        } else if (p < 0.55F) {
            beat = -0.9F + 2.4F * ((p - 0.4F) / 0.15F); // slam down
        } else {
            beat = 1.5F * (1.0F - (p - 0.55F) / 0.45F);
        }
        setWing(WING_SPREAD_Z, beat, 0.2F);
        this.body.xRot = -0.08F * Mth.sin(p * Mth.PI);
        this.neck.xRot = -0.55F - 0.15F * Mth.sin(p * Mth.PI);
    }

    private void applyTakeoff(int t) {
        float p = clamp01((float) t / HungarianHorntailEntity.TAKEOFF_DURATION);
        // Powerful downbeats, body pitches up, legs push off then tuck.
        float beat = Mth.sin(p * Mth.PI * 3.0F);
        setWing(WING_SPREAD_Z, beat * 0.9F, 0.2F + beat * 0.3F);
        this.body.xRot = -0.25F * p;
        this.neck.xRot = -0.4F;
        this.frontLeftLeg.xRot = -0.9F * p;
        this.frontRightLeg.xRot = -0.9F * p;
        this.hindLeftLeg.xRot = 0.7F * p;
        this.hindRightLeg.xRot = 0.7F * p;
    }

    private void applyDive(int t) {
        // Wings swept back, body pitched down, neck stretched forward — a stoop.
        setWing(WING_SPREAD_Z + 0.5F, -0.3F, -0.5F);
        this.body.xRot = 0.5F;
        this.neck.xRot = -0.1F;
        this.head.xRot = 0.25F;
        this.frontLeftLeg.xRot = -0.6F;
        this.frontRightLeg.xRot = -0.6F;
        this.hindLeftLeg.xRot = 0.5F;
        this.hindRightLeg.xRot = 0.5F;
        this.tail1.xRot = -0.3F;
    }

    private void applyLand(int t) {
        float p = clamp01((float) t / HungarianHorntailEntity.LAND_DURATION);
        // Flare wings forward to brake, pitch up, legs reach down.
        setWing(WING_SPREAD_Z, -0.6F * (1.0F - p), -0.3F);
        this.body.xRot = -0.3F * (1.0F - p);
        this.frontLeftLeg.xRot = 0.4F * p;
        this.frontRightLeg.xRot = 0.4F * p;
        this.hindLeftLeg.xRot = -0.2F * (1.0F - p);
        this.hindRightLeg.xRot = -0.2F * (1.0F - p);
    }

    private void applyStagger(int t) {
        float p = clamp01((float) t / HungarianHorntailEntity.STAGGER_DURATION);
        float recoil = Mth.sin(p * Mth.PI);
        this.neck.xRot = -0.55F - 0.4F * recoil;
        this.head.xRot = 0.45F - 0.3F * recoil;
        this.body.xRot = -0.15F * recoil;
        setWing(Mth.lerp(recoil, WING_FOLD_Z, 0.7F), 0.0F, 0.0F);
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
