package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.MerpersonEntity;
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

/**
 * A merperson of the Black Lake - the selkie kind: grey-green, wiry, wild green
 * hair, webbed ear fins and a long eel-like tail ending in a vertical fluke.
 *
 * <p>Geometry comes from {@code blockbench_models/merperson.bbmodel} through the
 * Modded Entity codec. The keyframe clips in {@link MerpersonAnimations} drive
 * the head, jaw, arms, hair and ear fins only.
 *
 * <p><b>The tail is solved here, not keyframed.</b> A fixed clip cannot make a
 * tail beat harder when the animal is sprinting and go slack when it is hanging
 * still in the water, so the six-part chain gets a travelling sine wave whose
 * amplitude and rate both scale with how hard the merperson is actually
 * swimming. Each segment lags the one before it, which is what turns a row of
 * boxes into something that looks like it is pushing water.
 */
public class MerpersonModel extends HierarchicalModel<MerpersonEntity> implements ArmedModel {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "merperson"), "main");

    /** How far the body pitches over when swimming flat out, in radians. */
    private static final float SWIM_PITCH = 1.22F;
    /** Phase lag between one tail segment and the next, in radians. */
    private static final float WAVE_LAG = 0.86F;
    /** Per-segment share of the wave. The whip is at the tip, not the hips. */
    private static final float[] WAVE_GAIN = {0.32F, 0.55F, 0.80F, 1.00F, 1.15F, 0.70F};
    /**
     * Resting S-curl, front to back. Without it the tail hangs like a plumb line
     * and the merperson is three and a half blocks of dead weight; with it the
     * tail sweeps back and hooks, which both looks alive and keeps the silhouette
     * inside something close to the hitbox.
     */
    private static final float[] REST_CURL = {0.30F, 0.16F, -0.06F, -0.18F, -0.16F, -0.05F};

    private final ModelPart root;
    private final ModelPart waist;
    private final ModelPart torso;
    private final ModelPart head;
    private final ModelPart crown;
    private final ModelPart armRight;
    private final ModelPart forearmRight;
    private final ModelPart[] tail;

    public MerpersonModel(ModelPart root) {
        this.root = root.getChild("root");
        this.waist = this.root.getChild("waist");
        this.torso = this.waist.getChild("torso");
        this.head = this.torso.getChild("head");
        this.crown = this.head.getChild("crown");
        this.armRight = this.torso.getChild("arm_right");
        this.forearmRight = this.armRight.getChild("forearm_right");

        ModelPart t1 = this.waist.getChild("tail01");
        ModelPart t2 = t1.getChild("tail02");
        ModelPart t3 = t2.getChild("tail03");
        ModelPart t4 = t3.getChild("tail04");
        ModelPart t5 = t4.getChild("tail05");
        this.tail = new ModelPart[]{t1, t2, t3, t4, t5, t5.getChild("fluke")};
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition waist = root.addOrReplaceChild("waist", CubeListBuilder.create(),
                PartPose.offset(0.0F, -35.0F, 0.0F));

        // Chest, ribs, belly, neck, the bony clavicle ridge and the rope of lake
        // pebbles every merperson wears.
        PartDefinition torso = waist.addOrReplaceChild("torso", CubeListBuilder.create()
                        .texOffs(160, 0).addBox(-5.0F, -15.0F, -3.0F, 10.0F, 7.0F, 6.0F, new CubeDeformation(0.0F))
                        .texOffs(87, 22).addBox(-4.5F, -8.0F, -2.6F, 9.0F, 5.0F, 5.2F, new CubeDeformation(0.0F))
                        .texOffs(206, 22).addBox(-3.6F, -3.0F, -2.2F, 7.2F, 4.5F, 4.4F, new CubeDeformation(0.0F))
                        .texOffs(0, 46).addBox(-2.4F, -16.5F, -1.5F, 4.8F, 2.5F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(32, 35).addBox(-5.6F, -15.0F, -3.3F, 11.2F, 2.0F, 6.6F, new CubeDeformation(0.0F))
                        .texOffs(159, 35).addBox(-4.0F, -12.6F, -3.5F, 8.0F, 1.2F, 4.5F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -1.0F, 0.0F));

        // Wedge skull, scowling brow, short muzzle, upper fangs, gill slits and
        // the two bulging eye cubes the emissive layer lights up.
        PartDefinition head = torso.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(91, 0).addBox(-3.5F, -7.0F, -5.0F, 7.0F, 7.0F, 8.5F, new CubeDeformation(0.0F))
                        .texOffs(17, 46).addBox(-4.0F, -6.6F, -6.6F, 8.0F, 1.6F, 2.4F, new CubeDeformation(0.0F))
                        .texOffs(186, 35).addBox(-1.9F, -3.4F, -7.2F, 3.8F, 2.9F, 2.2F, new CubeDeformation(0.0F))
                        .texOffs(40, 46).addBox(-1.6F, -0.6F, -7.0F, 3.2F, 1.2F, 1.7F, new CubeDeformation(0.0F))
                        .texOffs(141, 35).addBox(3.2F, -5.0F, -1.0F, 1.0F, 4.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(150, 35).addBox(-4.2F, -5.0F, -1.0F, 1.0F, 4.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(54, 22).addBox(-4.0F, -8.0F, -3.5F, 8.0F, 2.5F, 7.5F, new CubeDeformation(0.0F))
                        .texOffs(66, 46).addBox(2.1F, -4.6F, -6.0F, 2.1F, 2.0F, 1.4F, new CubeDeformation(0.0F))
                        .texOffs(77, 46).addBox(-4.2F, -4.6F, -6.0F, 2.1F, 2.0F, 1.4F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -15.0F, 0.0F));

        head.addOrReplaceChild("jaw", CubeListBuilder.create()
                        .texOffs(0, 22).addBox(-2.6F, 0.0F, -8.0F, 5.2F, 3.6F, 8.0F, new CubeDeformation(0.0F))
                        .texOffs(53, 46).addBox(-1.6F, -1.1F, -7.9F, 3.2F, 1.1F, 1.6F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -0.6F, 1.0F));

        head.addOrReplaceChild("hair_back", CubeListBuilder.create()
                        .texOffs(124, 0).addBox(0.2F, -0.5F, -1.0F, 4.3F, 12.5F, 2.5F, new CubeDeformation(0.0F))
                        .texOffs(57, 0).addBox(-4.5F, -0.5F, -1.0F, 4.3F, 14.5F, 2.7F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -7.0F, 3.5F));

        head.addOrReplaceChild("hair_left", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(0.3F, -0.5F, -3.0F, 2.6F, 13.5F, 7.0F, new CubeDeformation(0.0F)),
                PartPose.offset(3.5F, -7.0F, 0.0F));

        head.addOrReplaceChild("hair_right", CubeListBuilder.create()
                        .texOffs(36, 0).addBox(-2.9F, -0.5F, -3.0F, 2.6F, 11.5F, 7.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-3.5F, -7.0F, 0.0F));

        head.addOrReplaceChild("fin_ear_left", CubeListBuilder.create()
                        .texOffs(201, 35).addBox(0.0F, -2.5F, -0.4F, 6.9F, 5.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(3.6F, -3.0F, 1.0F));

        head.addOrReplaceChild("fin_ear_right", CubeListBuilder.create()
                        .texOffs(218, 35).addBox(-6.9F, -2.5F, -0.4F, 6.9F, 5.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-3.6F, -3.0F, 1.0F));

        // Only the merchieftainess wears this; setupAnim hides it for everyone else.
        head.addOrReplaceChild("crown", CubeListBuilder.create()
                        .texOffs(171, 22).addBox(-4.4F, -1.8F, -4.0F, 8.8F, 1.8F, 7.6F, new CubeDeformation(0.0F))
                        .texOffs(110, 35).addBox(-1.0F, -7.0F, -3.2F, 2.0F, 5.2F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(235, 35).addBox(2.0F, -5.8F, -1.4F, 1.8F, 4.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(244, 35).addBox(-3.8F, -5.8F, -1.4F, 1.8F, 4.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -8.0F, 0.0F));

        PartDefinition armLeft = torso.addOrReplaceChild("arm_left", CubeListBuilder.create()
                        .texOffs(193, 0).addBox(0.0F, -0.7F, -2.0F, 3.2F, 8.2F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(5.0F, -13.5F, 0.0F));

        armLeft.addOrReplaceChild("forearm_left", CubeListBuilder.create()
                        .texOffs(118, 22).addBox(-1.5F, -0.2F, -1.6F, 3.0F, 6.7F, 3.2F, new CubeDeformation(0.0F))
                        .texOffs(233, 22).addBox(-2.1F, 6.2F, -2.2F, 4.2F, 4.3F, 4.4F, new CubeDeformation(0.0F))
                        .texOffs(71, 35).addBox(1.6F, -0.5F, 0.5F, 1.5F, 6.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(1.5F, 7.5F, 0.0F));

        PartDefinition armRight = torso.addOrReplaceChild("arm_right", CubeListBuilder.create()
                        .texOffs(210, 0).addBox(-3.2F, -0.7F, -2.0F, 3.2F, 8.2F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-5.0F, -13.5F, 0.0F));

        armRight.addOrReplaceChild("forearm_right", CubeListBuilder.create()
                        .texOffs(133, 22).addBox(-1.5F, -0.2F, -1.6F, 3.0F, 6.7F, 3.2F, new CubeDeformation(0.0F))
                        .texOffs(0, 35).addBox(-2.1F, 6.2F, -2.2F, 4.2F, 4.3F, 4.4F, new CubeDeformation(0.0F))
                        .texOffs(82, 35).addBox(-3.1F, -0.5F, 0.5F, 1.5F, 6.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-1.5F, 7.5F, 0.0F));

        torso.addOrReplaceChild("fin_dorsal", CubeListBuilder.create()
                        .texOffs(21, 0).addBox(-0.6F, -8.0F, -0.2F, 1.2F, 15.0F, 4.7F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -8.0F, 3.0F));

        PartDefinition tail01 = waist.addOrReplaceChild("tail01", CubeListBuilder.create()
                        .texOffs(227, 0).addBox(-3.6F, -0.2F, -2.7F, 7.2F, 6.2F, 5.4F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition tail02 = tail01.addOrReplaceChild("tail02", CubeListBuilder.create()
                        .texOffs(29, 22).addBox(-3.1F, -0.2F, -2.5F, 6.2F, 6.2F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(21, 35).addBox(-0.5F, 0.0F, 2.3F, 1.0F, 6.0F, 3.2F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 6.0F, 0.0F));

        PartDefinition tail03 = tail02.addOrReplaceChild("tail03", CubeListBuilder.create()
                        .texOffs(148, 22).addBox(-2.6F, -0.2F, -2.1F, 5.2F, 5.2F, 4.2F, new CubeDeformation(0.0F))
                        .texOffs(119, 35).addBox(-0.45F, 0.0F, 1.9F, 0.9F, 5.0F, 2.7F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 6.0F, 0.0F));

        PartDefinition tail04 = tail03.addOrReplaceChild("tail04", CubeListBuilder.create()
                        .texOffs(93, 35).addBox(-2.0F, -0.2F, -1.6F, 4.0F, 4.2F, 3.2F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 5.0F, 0.0F));

        PartDefinition tail05 = tail04.addOrReplaceChild("tail05", CubeListBuilder.create()
                        .texOffs(128, 35).addBox(-1.4F, -0.2F, -1.1F, 2.8F, 4.2F, 2.2F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 4.0F, 0.0F));

        tail05.addOrReplaceChild("fluke", CubeListBuilder.create()
                        .texOffs(74, 0).addBox(-0.5F, -0.2F, -6.5F, 1.0F, 9.7F, 6.7F, new CubeDeformation(0.0F))
                        .texOffs(141, 0).addBox(-0.5F, -0.2F, 0.2F, 1.0F, 7.2F, 7.3F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 4.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 256, 64);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(MerpersonEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // Clips are deltas from the rest pose, so every part has to be reset or
        // last frame's offsets accumulate.
        this.root().getAllParts().forEach(ModelPart::resetPose);

        float partialTick = Mth.clamp(ageInTicks - entity.tickCount, 0.0F, 1.0F);
        this.crown.visible = entity.isChieftainess();

        float drive = entity.getTailDrive(partialTick);
        float swim = entity.getSwimPose(partialTick);
        boolean flopping = entity.isFloundering();

        // Body attitude: upright and treading water at rest, streamlined and
        // nearly flat at speed, with the chest lifted back up so the merperson
        // still looks like it is going somewhere on purpose.
        if (flopping) {
            // Beached. It cannot hold itself up at all.
            this.waist.xRot += 1.45F;
            this.torso.xRot -= 0.55F;
            this.head.xRot -= 0.35F;
        } else {
            this.waist.xRot += swim * SWIM_PITCH;
            this.torso.xRot -= swim * SWIM_PITCH * 0.45F;
            this.head.xRot -= swim * SWIM_PITCH * 0.22F;
        }

        solveTail(entity, ageInTicks, drive, flopping);

        this.animate(entity.hoverAnimation, MerpersonAnimations.HOVER, ageInTicks);
        this.animate(entity.swimAnimation, MerpersonAnimations.SWIM, ageInTicks);
        this.animate(entity.singAnimation, MerpersonAnimations.SING, ageInTicks);
        this.animate(entity.warnAnimation, MerpersonAnimations.WARN, ageInTicks);
        this.animate(entity.thrustAnimation, MerpersonAnimations.THRUST, ageInTicks);
        this.animate(entity.hurtAnimation, MerpersonAnimations.HURT, ageInTicks);
        this.animate(entity.deathAnimation, MerpersonAnimations.DEATH, ageInTicks);

        // Head tracking last, and damped: a merperson watching an intruder turns
        // its whole upper body a little rather than swivelling its skull.
        float track = entity.isBusy() ? 0.25F : 0.85F;
        this.head.yRot += Mth.clamp(netHeadYaw, -60.0F, 60.0F) * (Mth.PI / 180.0F) * track;
        this.head.xRot += Mth.clamp(headPitch, -40.0F, 40.0F) * (Mth.PI / 180.0F) * track * 0.7F;
        this.torso.yRot += Mth.clamp(netHeadYaw, -60.0F, 60.0F) * (Mth.PI / 180.0F) * track * 0.25F;
    }

    /**
     * Runs a travelling wave down the tail.
     *
     * <p>The wave beats sideways rather than up and down, because these are fish
     * tails with a vertical fluke, not dolphin flukes. Each segment extends along
     * its own +Y, so a lateral sweep is a rotation about Z; the resting S-curl,
     * which is front-to-back, is a rotation about X.
     */
    private void solveTail(MerpersonEntity entity, float ageInTicks, float drive, boolean flopping) {
        float amplitude;
        float rate;
        if (flopping) {
            // Out of water it is not swimming, it is thrashing: hard, fast, and
            // going nowhere.
            amplitude = 0.55F;
            rate = 0.85F;
        } else {
            amplitude = 0.14F + drive * 0.30F;
            rate = 0.17F + drive * 0.42F;
        }

        float restBlend = flopping ? 0.0F : (1.0F - drive * 0.75F);
        for (int i = 0; i < this.tail.length; i++) {
            ModelPart segment = this.tail[i];
            float phase = ageInTicks * rate - i * WAVE_LAG;
            segment.zRot += Mth.sin(phase) * amplitude * WAVE_GAIN[i];
            segment.xRot += REST_CURL[i] * restBlend;
        }

        // Dying: the wave dies out from the hips down, so the body goes slack
        // head-first and the tail keeps twitching a moment longer.
        if (entity.deathTime > 0) {
            float slack = Mth.clamp(entity.deathTime / 30.0F, 0.0F, 1.0F);
            for (int i = 0; i < this.tail.length; i++) {
                float keep = Mth.clamp(1.0F - slack + i * 0.12F, 0.0F, 1.0F);
                this.tail[i].zRot *= keep;
            }
        }
    }

    /**
     * Anchors the spear in the fist.
     *
     * <p>{@code arm_right} bakes to x = -5, which is where a vanilla humanoid's
     * right arm sits, so the mapping is the ordinary one - no left-handed flag
     * needed here. The whole chain from the model root has to be walked because
     * the arms hang off {@code torso} rather than sitting at the top level.
     */
    @Override
    public void translateToHand(HumanoidArm arm, PoseStack poseStack) {
        this.root.translateAndRotate(poseStack);
        this.waist.translateAndRotate(poseStack);
        this.torso.translateAndRotate(poseStack);
        this.armRight.translateAndRotate(poseStack);
        this.forearmRight.translateAndRotate(poseStack);

        // ItemInHandLayer adds its own 0.625-block translate after this call,
        // which unwinds to 10px down the arm; at 0.85 scale that is 8.5px, and
        // the fist cube is centred 8.35px below the forearm pivot. Only the
        // remainder is worth correcting.
        poseStack.translate(0.0F, -0.15F / 16.0F, 0.0F);
        poseStack.scale(0.85F, 0.85F, 0.85F);
    }
}
