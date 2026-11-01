package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.LivingStatueEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A stone knight with a halberd, animated as a statue rather than as a man.
 *
 * <p>The mesh between the {@code generated} markers comes from
 * build_scripts/generate_living_statue_model.py -- edit that and re-run it. The animation is
 * {@link LivingStatuePoses}, pure arithmetic this class only copies onto its parts.
 *
 * <h2>What it does in each state</h2>
 * <ul>
 *   <li><b>Dormant</b> -- at attention, halberd planted, fist on the sword. Not one part moves:
 *       no breathing, no sway, no head tracking, no red flash when struck. It is a statue.</li>
 *   <li><b>Waking</b> -- one joint at a time, each grinding loose with a catch half-way: the head
 *       turns, the left hand lets go of the sword, the halberd comes up, then a single heavy step
 *       down off its post.</li>
 *   <li><b>Awake</b> -- a guard stance it marches in: flat-footed, stiff-kneed, the body dropping
 *       onto each foot. Nothing idles; a statue standing its ground stands perfectly still.</li>
 *   <li><b>Swinging</b> -- a slow wind-up that hangs at the top, a strike that accelerates, then
 *       it stays stuck in the follow-through before it grinds back to guard.</li>
 *   <li><b>Destroyed</b> -- the knees give, it drops onto them, the halberd falls and the head
 *       topples off onto the floor. A bound sentry later reassembles itself at its post.</li>
 * </ul>
 *
 * <p>The rig is a real hierarchy -- torso carries head and arms, the halberd rides the right
 * forearm -- which is the fix for the old flat rig, where leaning the torso tore it away from its
 * own shoulders.
 */
public class LivingStatueModel<T extends LivingStatueEntity> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "living_statue"), "main");

    private final ModelPart root;
    /** In {@link LivingStatuePoses#BONES} order. */
    private final ModelPart[] bones;
    private final List<ModelPart> allParts;
    private final float[] values = new float[LivingStatuePoses.OUT_SIZE];
    /** False while it is stone (dormant, rising, falling): no red hurt tint on a statue. */
    private boolean tint;

    public LivingStatueModel(ModelPart part) {
        this.root = part.getChild("root");
        ModelPart hips = this.root.getChild("hips");
        ModelPart torso = hips.getChild("torso");
        ModelPart rightArm = torso.getChild("right_arm");
        ModelPart rightForearm = rightArm.getChild("right_forearm");
        ModelPart leftArm = torso.getChild("left_arm");
        ModelPart rightLeg = this.root.getChild("right_leg");
        ModelPart rightShin = rightLeg.getChild("right_shin");
        ModelPart leftLeg = this.root.getChild("left_leg");
        ModelPart leftShin = leftLeg.getChild("left_shin");
        this.bones = new ModelPart[]{this.root, hips, torso, torso.getChild("head"), rightArm,
                rightForearm, rightForearm.getChild("halberd"), leftArm,
                leftArm.getChild("left_forearm"), rightLeg, rightShin,
                rightShin.getChild("right_foot"), leftLeg, leftShin,
                leftShin.getChild("left_foot")};
        this.allParts = this.root.getAllParts().toList();
    }

    // <generated:mesh>
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition hips = root.addOrReplaceChild("hips", CubeListBuilder.create()
                .texOffs(20, 89).addBox(-6.0F, -4.0F, -4.0F, 12.0F, 2.0F, 8.0F)
                .texOffs(0, 65).addBox(-6.5F, -2.0F, -4.5F, 13.0F, 3.0F, 9.0F)
                , PartPose.offset(0.0F, 4.0F, 0.0F));
        PartDefinition torso = hips.addOrReplaceChild("torso", CubeListBuilder.create()
                .texOffs(8, 0).addBox(-6.5F, -17.0F, -4.5F, 13.0F, 9.0F, 9.0F)
                .texOffs(52, 99).addBox(-0.5F, -16.0F, -5.5F, 1.0F, 7.0F, 1.0F)
                .texOffs(92, 50).addBox(-5.5F, -8.0F, -3.5F, 11.0F, 5.0F, 7.0F)
                , PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition head = torso.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(52, 0).addBox(-4.5F, -8.0F, -4.5F, 9.0F, 8.0F, 9.0F)
                .texOffs(56, 99).addBox(-3.5F, -6.5F, -5.5F, 7.0F, 6.0F, 1.0F)
                .texOffs(0, 89).addBox(-1.0F, -10.0F, -3.5F, 2.0F, 2.0F, 8.0F)
                .texOffs(72, 99).addBox(-1.0F, -8.0F, 4.5F, 2.0F, 5.0F, 1.0F)
                , PartPose.offset(0.0F, -17.0F, 0.0F));
        PartDefinition rightArm = torso.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(24, 50).addBox(-4.0F, -2.5F, -4.5F, 7.0F, 5.0F, 9.0F)
                .texOffs(60, 89).addBox(-3.5F, 2.0F, -4.0F, 6.0F, 2.0F, 8.0F)
                .texOffs(44, 65).addBox(-2.5F, 3.0F, -2.5F, 5.0F, 7.0F, 5.0F)
                , PartPose.offset(-9.0F, -15.0F, 0.0F));
        PartDefinition rightForearm = rightArm.addOrReplaceChild("right_forearm", CubeListBuilder.create()
                .texOffs(12, 77).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 6.0F, 5.0F)
                .texOffs(88, 89).addBox(-3.0F, 6.0F, -3.0F, 6.0F, 4.0F, 6.0F)
                , PartPose.offset(0.0F, 9.5F, 0.0F));
        PartDefinition halberd = rightForearm.addOrReplaceChild("halberd", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1.0F, -24.0F, -1.0F, 2.0F, 48.0F, 2.0F)
                .texOffs(52, 109).addBox(-1.5F, 24.0F, -1.5F, 3.0F, 1.0F, 3.0F)
                .texOffs(16, 109).addBox(-1.5F, -26.0F, -1.5F, 3.0F, 2.0F, 3.0F)
                .texOffs(0, 77).addBox(-0.5F, -32.5F, -6.0F, 1.0F, 7.0F, 5.0F)
                .texOffs(108, 77).addBox(-0.5F, -33.5F, -8.0F, 1.0F, 9.0F, 2.0F)
                .texOffs(78, 99).addBox(-0.5F, -30.5F, 1.0F, 1.0F, 2.0F, 4.0F)
                .texOffs(88, 50).addBox(-0.5F, -37.5F, -0.5F, 1.0F, 12.0F, 1.0F)
                , PartPose.offset(0.0F, 8.0F, 0.0F));
        PartDefinition leftArm = torso.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(56, 50).addBox(-3.0F, -2.5F, -4.5F, 7.0F, 5.0F, 9.0F)
                .texOffs(0, 99).addBox(-2.5F, 2.0F, -4.0F, 6.0F, 2.0F, 8.0F)
                .texOffs(84, 65).addBox(-2.5F, 3.0F, -2.5F, 5.0F, 7.0F, 5.0F)
                , PartPose.offset(9.0F, -15.0F, 0.0F));
        PartDefinition leftForearm = leftArm.addOrReplaceChild("left_forearm", CubeListBuilder.create()
                .texOffs(60, 77).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 6.0F, 5.0F)
                .texOffs(28, 99).addBox(-3.0F, 6.0F, -3.0F, 6.0F, 4.0F, 6.0F)
                , PartPose.offset(0.0F, 9.5F, 0.0F));
        PartDefinition sword = hips.addOrReplaceChild("sword", CubeListBuilder.create()
                .texOffs(64, 109).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 1.0F, 2.0F)
                .texOffs(28, 109).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 3.0F, 1.0F)
                .texOffs(88, 99).addBox(-1.0F, -1.0F, -2.0F, 2.0F, 1.0F, 4.0F)
                .texOffs(88, 0).addBox(-0.5F, 0.0F, -1.0F, 1.0F, 13.0F, 2.0F)
                , PartPose.offsetAndRotation(7.5F, -3.0F, -0.5F, 0.3491F, 0.0F, 0.0F));
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(94, 0).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 9.0F, 6.0F)
                .texOffs(100, 99).addBox(-3.5F, 0.5F, -4.0F, 7.0F, 4.0F, 1.0F)
                , PartPose.offset(-3.5F, 5.0F, 0.0F));
        PartDefinition rightShin = rightLeg.addOrReplaceChild("right_shin", CubeListBuilder.create()
                .texOffs(32, 109).addBox(-2.0F, -1.5F, -4.0F, 4.0F, 3.0F, 1.0F)
                .texOffs(64, 65).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 7.0F, 5.0F)
                , PartPose.offset(0.0F, 9.0F, 0.0F));
        PartDefinition rightFoot = rightShin.addOrReplaceChild("right_foot", CubeListBuilder.create()
                .texOffs(32, 77).addBox(-3.0F, -0.5F, -5.5F, 6.0F, 3.0F, 8.0F)
                , PartPose.offset(0.0F, 7.5F, 0.0F));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(0, 50).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 9.0F, 6.0F)
                .texOffs(0, 109).addBox(-3.5F, 0.5F, -4.0F, 7.0F, 4.0F, 1.0F)
                , PartPose.offset(3.5F, 5.0F, 0.0F));
        PartDefinition leftShin = leftLeg.addOrReplaceChild("left_shin", CubeListBuilder.create()
                .texOffs(42, 109).addBox(-2.0F, -1.5F, -4.0F, 4.0F, 3.0F, 1.0F)
                .texOffs(104, 65).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 7.0F, 5.0F)
                , PartPose.offset(0.0F, 9.0F, 0.0F));
        PartDefinition leftFoot = leftShin.addOrReplaceChild("left_foot", CubeListBuilder.create()
                .texOffs(80, 77).addBox(-3.0F, -0.5F, -5.5F, 6.0F, 3.0F, 8.0F)
                , PartPose.offset(0.0F, 7.5F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    // </generated:mesh>

    @Override
    public void setupAnim(T e, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        float partial = ageInTicks - (float) Math.floor(ageInTicks);
        this.tint = LivingStatuePoses.evaluate(e.getWakeTime(partial), e.getComboIndex(),
                e.getSwingProgress(partial), e.getCrumbleProgress(partial),
                e.getReformProgress(partial), netHeadYaw, headPitch, limbSwing, limbSwingAmount,
                this.values);
        // Back to the generated rest offsets (the sword keeps its fixed tilt this way), then
        // every animated joint gets its offset added and its rotation set outright.
        for (ModelPart p : this.allParts) {
            p.resetPose();
        }
        float[] v = this.values;
        for (int i = 0; i < this.bones.length; i++) {
            ModelPart p = this.bones[i];
            p.x += v[i * 6];
            p.y += v[i * 6 + 1];
            p.z += v[i * 6 + 2];
            p.xRot = v[i * 6 + 3];
            p.yRot = v[i * 6 + 4];
            p.zRot = v[i * 6 + 5];
        }
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buf, int light, int overlay,
                               int colour) {
        this.root.render(pose, buf, light, this.tint ? overlay : OverlayTexture.NO_OVERLAY, colour);
    }
}
