package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.HouseElfEntity;
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
 * House elf: an oversized bald cranium, tennis-ball eyes under a heavy brow, a
 * long thin nose, enormous drooping bat-ears and a filthy pillowcase worn as a
 * tunic. Spindly limbs, knobbly knees and flat oversized feet.
 *
 * <p>Geometry is generated in Blockbench (blockbench_models/house_elf.bbmodel)
 * and exported through the Modded Entity codec; keyframe clips live in
 * {@link HouseElfAnimations}. Part names here must stay in sync with the bone
 * names those clips address.
 */
public class HouseElfModel extends HierarchicalModel<HouseElfEntity> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "house_elf"), "main");

    /** The permanent servile stoop, applied before the clips so they layer on top. */
    private static final float REST_BODY_X = 0.10F;
    /** Counter-tilt so the stoop does not leave the elf staring at its own feet. */
    private static final float REST_HEAD_X = -0.05F;

    private final ModelPart root;
    private final ModelPart head;

    public HouseElfModel(ModelPart root) {
        this.root = root.getChild("root");
        this.head = this.root.getChild("head");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        // Cranium and domed crown, brow shelf, the two bulging eyes, a three-piece
        // nose, and the narrow pointed jaw and chin.
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.8F, -6.4F, -3.4F, 7.6F, 5.6F, 6.9F, new CubeDeformation(0.0F))
                        .texOffs(20, 14).addBox(-3.2F, -7.2F, -2.8F, 6.4F, 0.8F, 5.7F, new CubeDeformation(0.0F))
                        .texOffs(109, 30).addBox(-3.9F, -4.0F, -4.1F, 7.8F, 0.9F, 0.8F, new CubeDeformation(0.0F))
                        .texOffs(84, 30).addBox(-3.05F, -3.5F, -4.1F, 2.5F, 1.9F, 0.75F, new CubeDeformation(0.0F))
                        .texOffs(93, 30).addBox(0.55F, -3.5F, -4.1F, 2.5F, 1.9F, 0.75F, new CubeDeformation(0.0F))
                        .texOffs(102, 30).addBox(-0.6F, -3.4F, -4.1F, 1.2F, 1.4F, 0.7F, new CubeDeformation(0.0F))
                        .texOffs(43, 23).addBox(-0.7F, -2.3F, -4.8F, 1.4F, 2.1F, 1.4F, new CubeDeformation(0.0F))
                        .texOffs(35, 30).addBox(-0.5F, -1.1F, -5.8F, 1.0F, 1.1F, 1.3F, new CubeDeformation(0.0F))
                        .texOffs(75, 0).addBox(-2.6F, -1.0F, -3.5F, 5.2F, 2.3F, 5.5F, new CubeDeformation(0.0F))
                        .texOffs(52, 23).addBox(-1.3F, 0.8F, -3.3F, 2.6F, 1.3F, 2.4F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -19.2F, 0.0F));

        // Ears: two hinged segments each, swept outward and back with the tips
        // drooping. Both joints are animated, so they flap and lag behind the head.
        PartDefinition ear_left = head.addOrReplaceChild("ear_left", CubeListBuilder.create()
                        .texOffs(65, 23).addBox(-2.4F, -1.1F, -1.3F, 2.4F, 2.8F, 1.9F, new CubeDeformation(0.0F))
                        .texOffs(76, 23).addBox(-4.8F, -0.3F, -1.0F, 2.6F, 3.0F, 1.5F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-3.9F, -3.1F, -0.2F, 0.0F, 0.2443F, -0.1745F));

        ear_left.addOrReplaceChild("ear_left_tip", CubeListBuilder.create()
                        .texOffs(99, 14).addBox(-2.1F, -0.5F, -0.8F, 2.1F, 3.8F, 1.2F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-4.6F, 1.2F, 0.0F, 0.0F, 0.0F, -0.5236F));

        PartDefinition ear_right = head.addOrReplaceChild("ear_right", CubeListBuilder.create()
                        .texOffs(87, 23).addBox(0.0F, -1.1F, -1.3F, 2.4F, 2.8F, 1.9F, new CubeDeformation(0.0F))
                        .texOffs(98, 23).addBox(2.2F, -0.3F, -1.0F, 2.6F, 3.0F, 1.5F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(3.9F, -3.1F, -0.2F, 0.0F, -0.2443F, 0.1745F));

        ear_right.addOrReplaceChild("ear_right_tip", CubeListBuilder.create()
                        .texOffs(110, 14).addBox(0.0F, -0.5F, -0.8F, 2.1F, 3.8F, 1.2F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(4.6F, 1.2F, 0.0F, 0.0F, 0.0F, 0.5236F));

        // Bony chest and thin neck left bare above the pillowcase, which is knotted
        // over both shoulders and hangs to mid-thigh with a torn strip trailing.
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(54, 0).addBox(-2.6F, -5.2F, -1.6F, 5.2F, 5.4F, 3.2F, new CubeDeformation(0.0F))
                        .texOffs(109, 23).addBox(-1.0F, -7.0F, -0.9F, 2.0F, 2.4F, 1.8F, new CubeDeformation(0.0F))
                        .texOffs(42, 30).addBox(-2.4F, -5.5F, -1.4F, 4.8F, 1.0F, 2.8F, new CubeDeformation(0.0F))
                        .texOffs(31, 0).addBox(-3.0F, -3.8F, -2.1F, 6.0F, 5.8F, 4.2F, new CubeDeformation(0.0F))
                        .texOffs(0, 23).addBox(-3.2F, -4.4F, -2.3F, 6.4F, 1.0F, 4.6F, new CubeDeformation(0.0F))
                        .texOffs(59, 30).addBox(-2.5F, -5.7F, -0.9F, 1.1F, 1.5F, 1.6F, new CubeDeformation(0.0F))
                        .texOffs(68, 30).addBox(1.4F, -5.7F, -0.9F, 1.1F, 1.5F, 1.6F, new CubeDeformation(0.0F))
                        .texOffs(77, 30).addBox(-0.2F, 1.8F, -2.3F, 1.2F, 2.2F, 0.5F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -11.4F, 0.0F));

        PartDefinition left_arm = root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                        .texOffs(47, 14).addBox(-0.8F, -0.2F, -0.85F, 1.5F, 4.1F, 1.7F, new CubeDeformation(0.0F)),
                PartPose.offset(-3.7F, -16.4F, 0.0F));

        left_arm.addOrReplaceChild("left_forearm", CubeListBuilder.create()
                        .texOffs(25, 23).addBox(-0.65F, -0.1F, -0.8F, 1.3F, 3.7F, 1.6F, new CubeDeformation(0.0F))
                        .texOffs(118, 23).addBox(-0.75F, 3.4F, -0.85F, 1.5F, 2.1F, 1.7F, new CubeDeformation(0.0F)),
                PartPose.offset(-0.05F, 3.7F, 0.0F));

        PartDefinition right_arm = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                        .texOffs(56, 14).addBox(-0.7F, -0.2F, -0.85F, 1.5F, 4.1F, 1.7F, new CubeDeformation(0.0F)),
                PartPose.offset(3.7F, -16.4F, 0.0F));

        right_arm.addOrReplaceChild("right_forearm", CubeListBuilder.create()
                        .texOffs(34, 23).addBox(-0.65F, -0.1F, -0.8F, 1.3F, 3.7F, 1.6F, new CubeDeformation(0.0F))
                        .texOffs(0, 30).addBox(-0.75F, 3.4F, -0.85F, 1.5F, 2.1F, 1.7F, new CubeDeformation(0.0F)),
                PartPose.offset(0.05F, 3.7F, 0.0F));

        PartDefinition left_leg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                        .texOffs(100, 0).addBox(-1.0F, -0.1F, -1.1F, 1.9F, 4.5F, 2.2F, new CubeDeformation(0.0F))
                        .texOffs(9, 30).addBox(-1.15F, 3.8F, -1.2F, 2.2F, 1.2F, 2.4F, new CubeDeformation(0.0F)),
                PartPose.offset(-1.9F, -11.4F, 0.0F));

        left_leg.addOrReplaceChild("left_shin", CubeListBuilder.create()
                        .texOffs(111, 0).addBox(-0.8F, 0.0F, -1.0F, 1.6F, 5.7F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(65, 14).addBox(-1.45F, 5.6F, -3.4F, 2.8F, 1.4F, 5.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-0.05F, 4.4F, 0.0F));

        PartDefinition right_leg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                        .texOffs(0, 14).addBox(-0.9F, -0.1F, -1.1F, 1.9F, 4.5F, 2.2F, new CubeDeformation(0.0F))
                        .texOffs(22, 30).addBox(-1.05F, 3.8F, -1.2F, 2.2F, 1.2F, 2.4F, new CubeDeformation(0.0F)),
                PartPose.offset(1.9F, -11.4F, 0.0F));

        right_leg.addOrReplaceChild("right_shin", CubeListBuilder.create()
                        .texOffs(11, 14).addBox(-0.8F, 0.0F, -1.0F, 1.6F, 5.7F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(82, 14).addBox(-1.35F, 5.6F, -3.4F, 2.8F, 1.4F, 5.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.05F, 4.4F, 0.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(HouseElfEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // Keyframe clips are additive, so every part has to start from its rest
        // pose or last frame's offsets accumulate.
        this.root().getAllParts().forEach(ModelPart::resetPose);

        this.root.getChild("body").xRot = REST_BODY_X;
        this.head.xRot = REST_HEAD_X;

        // Driven by actual movement rather than a free-running timer, so the feet
        // stay planted whatever speed the elf happens to be scurrying at.
        this.animateWalk(HouseElfAnimations.WALK, limbSwing, limbSwingAmount, 2.0F, 2.8F);

        this.animate(entity.idleAnimationState, HouseElfAnimations.IDLE, ageInTicks);
        this.animate(entity.cowerAnimationState, HouseElfAnimations.COWER, ageInTicks);
        this.animate(entity.punishAnimationState, HouseElfAnimations.PUNISH, ageInTicks);
        this.animate(entity.joyAnimationState, HouseElfAnimations.JOY, ageInTicks);
        this.animate(entity.disapparateAnimationState, HouseElfAnimations.DISAPPARATE, ageInTicks);

        // Head tracking last. A cowering elf will not meet your eye, so tracking is
        // scaled away while it is hiding.
        float track = entity.cowerAnimationState.isStarted() ? 0.15F : 0.8F;
        this.head.yRot += Mth.clamp(netHeadYaw, -60.0F, 60.0F) * (Mth.PI / 180.0F) * track;
        this.head.xRot += Mth.clamp(headPitch, -40.0F, 40.0F) * (Mth.PI / 180.0F) * track;
    }
}
