package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.TrollEntity;
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

public class TrollModel extends EntityModel<TrollEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "troll"), "main");

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart club;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;

    // Resting/idle pose values — used as base before per-state offsets are layered on.
    private static final float REST_LEFT_ARM_X = 0.0F;
    private static final float REST_LEFT_ARM_Y = 0.0F;
    private static final float REST_LEFT_ARM_Z = -0.12F;
    private static final float REST_RIGHT_ARM_X = 0.0F;
    private static final float REST_RIGHT_ARM_Y = 0.0F;
    private static final float REST_RIGHT_ARM_Z = 0.18F;
    private static final float REST_CLUB_Z = -0.62F;
    private static final float REST_BODY_X = 0.08F;

    public TrollModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.leftArm = root.getChild("left_arm");
        this.rightArm = root.getChild("right_arm");
        this.club = this.leftArm.getChild("club");
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 35).addBox(-10.0F, -25.0F, -6.0F, 20.0F, 19.0F, 12.0F, new CubeDeformation(0.0F))
                        .texOffs(54, 34).addBox(-13.0F, -36.0F, -8.0F, 26.0F, 15.0F, 15.0F, new CubeDeformation(0.1F))
                        .texOffs(0, 62).addBox(-9.0F, -28.0F, -8.0F, 18.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(72, 0).addBox(2.0F, -32.0F, -8.0F, 3.0F, 9.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(80, 0).addBox(-7.0F, -38.0F, -2.0F, 10.0F, 3.0F, 9.0F, new CubeDeformation(0.0F))
                        .texOffs(80, 9).addBox(4.0F, -38.5F, -8.5F, 12.0F, 2.0F, 10.0F, new CubeDeformation(0.0F))
                        .texOffs(80, 21).addBox(-13.0F, -37.5F, -8.5F, 8.0F, 2.0F, 9.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        body.addOrReplaceChild("left_shoulder_moss", CubeListBuilder.create()
                        .texOffs(80, 9).addBox(3.0F, -35.0F, -6.6F, 7.0F, 1.0F, 8.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);

        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-6.0F, -8.0F, -6.0F, 12.0F, 11.0F, 9.0F, new CubeDeformation(0.0F))
                        .texOffs(36, 0).addBox(-7.0F, -3.0F, -8.0F, 14.0F, 5.0F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 18).addBox(-5.0F, 0.0F, -9.0F, 10.0F, 6.0F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(66, 0).addBox(-2.0F, -3.0F, -12.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(36, 8).addBox(-5.0F, -2.0F, -11.0F, 3.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(42, 8).addBox(2.0F, -2.0F, -11.0F, 3.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(48, 8).addBox(-4.0F, -1.0F, -12.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(52, 8).addBox(3.0F, -1.0F, -12.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(56, 8).addBox(-4.0F, 3.0F, -10.0F, 8.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(80, 20).addBox(-6.0F, -7.0F, -9.0F, 2.0F, 9.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(88, 20).addBox(2.0F, -8.0F, -8.5F, 5.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -11.0F, -6.0F));

        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                        .texOffs(0, 68).addBox(-1.0F, -2.0F, -5.0F, 8.0F, 18.0F, 10.0F, new CubeDeformation(0.0F))
                        .texOffs(30, 68).addBox(0.0F, 14.0F, -5.5F, 9.0F, 16.0F, 11.0F, new CubeDeformation(0.0F))
                        .texOffs(64, 68).addBox(-0.5F, 29.0F, -6.0F, 10.0F, 6.0F, 12.0F, new CubeDeformation(0.0F))
                        .texOffs(86, 20).addBox(5.0F, 15.0F, -6.2F, 3.0F, 10.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 112).addBox(0.0F, 34.5F, -7.0F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(12, 112).addBox(3.0F, 34.5F, -7.0F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(9.0F, -9.0F, 0.0F));

        leftArm.addOrReplaceChild("club", CubeListBuilder.create()
                        .texOffs(0, 94).addBox(2.0F, 18.0F, -3.0F, 5.0F, 32.0F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(20, 94).addBox(4.0F, 34.0F, -5.0F, 12.0F, 18.0F, 10.0F, new CubeDeformation(0.0F))
                        .texOffs(64, 94).addBox(12.0F, 38.0F, -6.0F, 6.0F, 10.0F, 12.0F, new CubeDeformation(0.0F))
                        .texOffs(96, 94).addBox(6.0F, 30.0F, -6.0F, 6.0F, 7.0F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(96, 106).addBox(7.0F, 48.0F, -7.0F, 5.0F, 6.0F, 14.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 118).addBox(0.0F, 25.0F, -4.0F, 7.0F, 4.0F, 7.0F, new CubeDeformation(0.0F)),
                PartPose.rotation(0.0F, 0.0F, -0.62F));

        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                        .texOffs(0, 68).mirror().addBox(-7.0F, -2.0F, -5.0F, 8.0F, 18.0F, 10.0F, new CubeDeformation(0.0F)).mirror(false)
                        .texOffs(30, 68).mirror().addBox(-9.0F, 14.0F, -5.5F, 9.0F, 16.0F, 11.0F, new CubeDeformation(0.0F)).mirror(false)
                        .texOffs(64, 68).mirror().addBox(-9.5F, 29.0F, -6.0F, 10.0F, 6.0F, 12.0F, new CubeDeformation(0.0F)).mirror(false)
                        .texOffs(24, 112).addBox(-6.0F, 34.5F, -7.0F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(36, 112).addBox(-3.0F, 34.5F, -7.0F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-9.0F, -9.0F, 0.0F));

        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                        .texOffs(102, 62).addBox(-3.0F, 0.0F, -5.0F, 7.0F, 13.0F, 10.0F, new CubeDeformation(0.0F))
                        .texOffs(102, 84).addBox(-5.0F, 12.0F, -10.0F, 10.0F, 5.0F, 15.0F, new CubeDeformation(0.0F))
                        .texOffs(48, 112).addBox(3.0F, 13.0F, -12.0F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(60, 112).addBox(-1.0F, 13.0F, -12.0F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(4.0F, 7.0F, 0.0F));

        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                        .texOffs(102, 62).mirror().addBox(-4.0F, 0.0F, -5.0F, 7.0F, 13.0F, 10.0F, new CubeDeformation(0.0F)).mirror(false)
                        .texOffs(102, 84).mirror().addBox(-5.0F, 12.0F, -10.0F, 10.0F, 5.0F, 15.0F, new CubeDeformation(0.0F)).mirror(false)
                        .texOffs(94, 9).addBox(-4.5F, 4.0F, -5.8F, 3.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(72, 112).addBox(-6.0F, 13.0F, -12.0F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(84, 112).addBox(-2.0F, 13.0F, -12.0F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-4.0F, 7.0F, 0.0F));

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(TrollEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {

        // -------- 1) Idle base pose: head, breathing body, resting arms/club. --------
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD * 0.65F;

        // Heavy lumber walk: slower frequency, larger amplitude than vanilla.
        float walkPhase = limbSwing * 0.42F;
        float walkAmp = Math.min(limbSwingAmount, 1.0F);
        float leg = Mth.cos(walkPhase) * walkAmp;
        this.leftLeg.xRot = leg * 0.85F;
        this.rightLeg.xRot = -leg * 0.85F;
        // Subtle body bob with footfalls.
        float bodyBobX = REST_BODY_X + Mth.sin(ageInTicks * 0.06F) * 0.025F;
        float bodyBobY = Mth.sin(walkPhase * 2.0F) * 0.05F * walkAmp;
        this.body.xRot = bodyBobX;
        this.body.yRot = bodyBobY;

        // Arms swing opposite to legs, more on the non-club side.
        this.leftArm.xRot = REST_LEFT_ARM_X + (-leg * 0.18F);
        this.leftArm.yRot = REST_LEFT_ARM_Y;
        this.leftArm.zRot = REST_LEFT_ARM_Z;
        this.rightArm.xRot = REST_RIGHT_ARM_X + (leg * 0.45F);
        this.rightArm.yRot = REST_RIGHT_ARM_Y;
        this.rightArm.zRot = REST_RIGHT_ARM_Z;

        this.club.xRot = 0.0F;
        this.club.yRot = 0.0F;
        this.club.zRot = REST_CLUB_Z;

        // -------- 2) State overlays. --------
        TrollEntity.AttackState state = entity.getAttackState();
        int t = entity.getStateTick();

        switch (state) {
            case ROAR -> applyRoar(t);
            case SLAM_WINDUP -> applySlamWindup(t);
            case SLAM_STRIKE -> applySlamStrike(t);
            case SWEEP -> applySweep(t);
            case STAGGER -> applyStagger(t);
            case NONE -> { /* idle pose already applied above */ }
        }

        // Rage adds an idle tremor and lower stance.
        if (entity.isRaging() && state == TrollEntity.AttackState.NONE) {
            float tremor = Mth.sin(ageInTicks * 0.9F) * 0.025F;
            this.body.xRot += tremor;
            this.head.xRot += tremor * 0.6F;
        }
    }

    /** Roar: head back, chest out, arms slightly back, club steady. */
    private void applyRoar(int t) {
        float p = clamp01((float) t / (float) TrollEntity.ROAR_DURATION);
        // Bell curve in/out so the roar peaks and settles.
        float peak = Mth.sin(p * Mth.PI);
        this.body.xRot = REST_BODY_X - 0.30F * peak;
        this.head.xRot = -0.55F * peak;
        this.head.yRot *= (1.0F - peak); // look forward during roar
        this.leftArm.xRot = -0.35F * peak;
        this.leftArm.zRot = REST_LEFT_ARM_Z - 0.30F * peak;
        this.rightArm.xRot = -0.35F * peak;
        this.rightArm.zRot = REST_RIGHT_ARM_Z + 0.30F * peak;
        this.club.zRot = REST_CLUB_Z + 0.15F * peak;
    }

    /** Slam windup: rear back, raise club overhead. */
    private void applySlamWindup(int t) {
        float p = clamp01((float) t / (float) TrollEntity.SLAM_WINDUP_DURATION);
        float eased = easeInOut(p);
        this.body.xRot = REST_BODY_X - 0.45F * eased;
        this.body.yRot = -0.22F * eased;
        this.head.xRot += -0.35F * eased;
        // Raise left arm overhead with club: arm rotates back-up, club pivots forward.
        this.leftArm.xRot = -2.55F * eased;
        this.leftArm.yRot = 0.45F * eased;
        this.leftArm.zRot = REST_LEFT_ARM_Z - 0.50F * eased;
        this.club.xRot = -0.45F * eased;
        this.club.yRot = 0.10F * eased;
        this.club.zRot = REST_CLUB_Z + 0.30F * eased;
        // Right arm steadies for balance.
        this.rightArm.xRot = -0.30F * eased;
        this.rightArm.zRot = REST_RIGHT_ARM_Z - 0.10F * eased;
    }

    /** Slam strike: snap forward and down, then recover. */
    private void applySlamStrike(int t) {
        float p = clamp01((float) t / (float) TrollEntity.SLAM_STRIKE_DURATION);
        // Fast strike (first 30%) then slow recover.
        float strike;
        if (p < 0.30F) {
            strike = p / 0.30F;
        } else {
            strike = 1.0F - (p - 0.30F) / 0.70F;
        }
        // Body pitches forward sharply, then settles.
        this.body.xRot = REST_BODY_X + 0.55F * strike;
        this.body.yRot = -0.10F + 0.20F * (1.0F - strike);
        this.head.xRot += 0.30F * strike;
        // Arm rotates from overhead (~-2.55) toward fully down (~+0.40) at impact.
        float armX = Mth.lerp(strike, -2.55F, 0.40F);
        this.leftArm.xRot = armX;
        this.leftArm.yRot = 0.10F;
        this.leftArm.zRot = REST_LEFT_ARM_Z - 0.20F * (1.0F - strike);
        // Club follows.
        this.club.xRot = Mth.lerp(strike, -0.45F, 0.30F);
        this.club.yRot = 0.0F;
        this.club.zRot = REST_CLUB_Z + 0.25F * (1.0F - strike);
        this.rightArm.xRot = -0.15F;
    }

    /** Sweep: rotate body and arc the club horizontally across the front. */
    private void applySweep(int t) {
        float p = clamp01((float) t / (float) TrollEntity.SWEEP_DURATION);
        // Wind up in first 35%, sweep through to ~70%, recover after.
        float sweep;
        if (p < 0.35F) {
            sweep = -0.6F * (p / 0.35F); // wind to the right
        } else if (p < 0.70F) {
            sweep = -0.6F + 2.0F * ((p - 0.35F) / 0.35F); // arc across
        } else {
            sweep = 1.4F * (1.0F - (p - 0.70F) / 0.30F); // hold then settle
        }
        this.body.yRot = sweep * 0.30F;
        this.head.yRot += sweep * 0.20F;
        // Left arm raises to shoulder height and arcs across.
        this.leftArm.xRot = -1.20F;
        this.leftArm.yRot = -0.20F + sweep * 0.60F;
        this.leftArm.zRot = REST_LEFT_ARM_Z + sweep * 0.40F;
        // Club rotates so it sweeps in a horizontal plane.
        this.club.xRot = -1.10F;
        this.club.yRot = sweep * 0.50F;
        this.club.zRot = REST_CLUB_Z + 0.30F;
        // Right arm counter-balance.
        this.rightArm.xRot = -0.20F;
        this.rightArm.yRot = -sweep * 0.20F;
    }

    /** Stagger: arms up, head back, body recoiled. */
    private void applyStagger(int t) {
        float p = clamp01((float) t / (float) TrollEntity.STAGGER_DURATION);
        float recoil = Mth.sin(p * Mth.PI);
        this.body.xRot = REST_BODY_X - 0.20F * recoil;
        this.head.xRot += -0.30F * recoil;
        this.leftArm.xRot = -0.70F * recoil;
        this.leftArm.zRot = REST_LEFT_ARM_Z - 0.20F * recoil;
        this.rightArm.xRot = -0.70F * recoil;
        this.rightArm.zRot = REST_RIGHT_ARM_Z + 0.20F * recoil;
        // Hold club steady (don't fling it).
        this.club.zRot = REST_CLUB_Z + 0.10F * recoil;
    }

    private static float clamp01(float v) {
        return Mth.clamp(v, 0.0F, 1.0F);
    }

    private static float easeInOut(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
                               int color) {
        this.root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }
}
