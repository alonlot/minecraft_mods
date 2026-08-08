package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.GoblinAnimated;
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
import net.minecraft.world.entity.Mob;

/**
 * Gringotts goblin: oversized bald cranium, heavy brow, hooked drooping nose,
 * pince-nez spectacles, enormous swept ears and a black Victorian frock coat.
 *
 * <p>Geometry is generated in Blockbench (blockbench_models/goblin.bbmodel) and
 * exported through the Modded Entity codec; keyframe clips live in
 * {@link GoblinAnimations}. Part names here must stay in sync with the bone
 * names those clips address.
 */
public class GoblinModel<T extends Mob & GoblinAnimated> extends HierarchicalModel<T> implements ArmedModel {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "goblin"), "main");

    /**
     * Forward hunch baked into the torso every frame. Positive xRot pitches the
     * top of the part toward -Z, which is the direction the entity faces.
     */
    private static final float REST_BODY_X = 0.14F;
    /** Counter-tilt so the hunch does not leave the goblin staring at its own shoes. */
    private static final float REST_HEAD_X = -0.07F;

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart earLeft;
    private final ModelPart earRight;
    private final ModelPart body;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;

    public GoblinModel(ModelPart root) {
        this.root = root.getChild("root");
        this.head = this.root.getChild("head");
        this.earLeft = this.head.getChild("ear_left");
        this.earRight = this.head.getChild("ear_right");
        this.body = this.root.getChild("body");
        this.leftArm = this.root.getChild("left_arm");
        this.rightArm = this.root.getChild("right_arm");
        this.leftLeg = this.root.getChild("left_leg");
        this.rightLeg = this.root.getChild("right_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        // Skull, brow shelf, jaw, three-segment hooked nose, spectacle bar, and
        // the ring of long white hair around an otherwise bald crown.
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0F, -8.5F, -4.5F, 8.0F, 6.0F, 7.0F, new CubeDeformation(0.0F))
                        .texOffs(7, 39).addBox(-4.0F, -6.5F, -5.2F, 8.0F, 1.0F, 0.7F, new CubeDeformation(0.0F))
                        .texOffs(0, 14).addBox(-3.0F, -3.0F, -4.5F, 6.0F, 4.0F, 5.5F, new CubeDeformation(0.0F))
                        .texOffs(0, 39).addBox(-1.0F, -6.0F, -5.5F, 2.0F, 1.5F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 33).addBox(-1.0F, -4.5F, -7.5F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(89, 33).addBox(-1.0F, -2.5F, -7.5F, 2.0F, 1.7F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(26, 39).addBox(-4.5F, -4.2F, -6.3F, 9.0F, 1.0F, 0.5F, new CubeDeformation(0.0F))
                        .texOffs(31, 0).addBox(-4.0F, -8.5F, 2.0F, 8.0F, 10.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(52, 0).addBox(-5.5F, -6.5F, 0.0F, 1.5F, 8.0F, 3.5F, new CubeDeformation(0.0F))
                        .texOffs(65, 0).addBox(4.0F, -6.5F, 0.0F, 1.5F, 8.0F, 3.5F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -16.0F, -1.0F));

        head.addOrReplaceChild("ear_left", CubeListBuilder.create()
                        .texOffs(79, 25).addBox(-3.5F, -2.0F, -1.0F, 4.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(98, 33).addBox(-6.0F, -1.5F, -1.0F, 2.5F, 2.5F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(47, 39).addBox(-8.5F, -0.5F, -1.0F, 2.5F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-3.5F, -4.0F, -1.5F, 0.0F, -0.2443F, 0.1047F));

        head.addOrReplaceChild("ear_right", CubeListBuilder.create()
                        .texOffs(92, 25).addBox(-0.5F, -2.0F, -1.0F, 4.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(107, 33).addBox(3.5F, -1.5F, -1.0F, 2.5F, 2.5F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(56, 39).addBox(6.0F, -0.5F, -1.0F, 2.5F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(3.5F, -4.0F, -1.5F, 0.0F, 0.2443F, -0.1047F));

        // Torso, flared coat skirt, hanging coat tails, wing collar and bow tie.
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(78, 0).addBox(-4.5F, -7.0F, -2.5F, 9.0F, 7.0F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(25, 14).addBox(-5.0F, -1.0F, -3.0F, 10.0F, 3.5F, 6.0F, new CubeDeformation(0.0F))
                        .texOffs(105, 25).addBox(-4.5F, 1.0F, 1.5F, 9.0F, 3.5F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(88, 14).addBox(-2.5F, -7.8F, -3.2F, 5.0F, 2.3F, 4.7F, new CubeDeformation(0.0F))
                        .texOffs(116, 33).addBox(-1.5F, -7.2F, -4.2F, 3.0F, 1.6F, 1.2F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -8.0F, 0.0F));

        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                        .texOffs(58, 14).addBox(-3.0F, -0.5F, -2.0F, 3.0F, 5.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(109, 14).addBox(-2.75F, 4.5F, -1.75F, 2.5F, 3.0F, 3.5F, new CubeDeformation(0.0F))
                        .texOffs(11, 33).addBox(-3.0F, 7.5F, -2.0F, 3.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(26, 33).addBox(-2.5F, 8.5F, -1.5F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-4.5F, -15.0F, -1.0F));

        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                        .texOffs(73, 14).addBox(0.0F, -0.5F, -2.0F, 3.0F, 5.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 25).addBox(0.25F, 4.5F, -1.75F, 2.5F, 3.0F, 3.5F, new CubeDeformation(0.0F))
                        .texOffs(37, 33).addBox(0.0F, 7.5F, -2.0F, 3.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(52, 33).addBox(0.5F, 8.5F, -1.5F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(4.5F, -15.0F, -1.0F));

        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                        .texOffs(15, 25).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(63, 33).addBox(-1.25F, 4.0F, -1.5F, 2.5F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(28, 25).addBox(-1.75F, 6.0F, -3.5F, 3.5F, 2.0F, 5.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-2.0F, -8.0F, 0.0F));

        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                        .texOffs(47, 25).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(76, 33).addBox(-1.25F, 4.0F, -1.5F, 2.5F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(60, 25).addBox(-1.75F, 6.0F, -3.5F, 3.5F, 2.0F, 5.0F, new CubeDeformation(0.0F)),
                PartPose.offset(2.0F, -8.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // Keyframe clips are additive, so every part has to start from its rest
        // pose or last frame's offsets accumulate.
        this.root().getAllParts().forEach(ModelPart::resetPose);

        // The permanent stoop, applied before the clips so they layer on top.
        this.body.xRot = REST_BODY_X;
        this.head.xRot = REST_HEAD_X;

        // Waddle is driven by actual movement rather than a free-running timer so
        // the feet stay planted at whatever speed the goblin happens to be going.
        this.animateWalk(GoblinAnimations.WALK, limbSwing, limbSwingAmount, 2.0F, 2.5F);

        this.animate(entity.goblinIdleAnimationState(), GoblinAnimations.IDLE, ageInTicks);
        if (entity.goblinCountsCoins()) {
            this.animate(entity.goblinBusyAnimationState(), GoblinAnimations.COUNT_COINS, ageInTicks);
        }
        this.animate(entity.goblinAttackAnimationState(), GoblinAnimations.ATTACK, ageInTicks);

        // Head tracking last, damped: an old goblin turns its head grudgingly and
        // peers over the top of its spectacles rather than swivelling freely.
        this.head.yRot += Mth.clamp(netHeadYaw, -55.0F, 55.0F) * (Mth.PI / 180.0F) * 0.7F;
        this.head.xRot += Mth.clamp(headPitch, -35.0F, 35.0F) * (Mth.PI / 180.0F) * 0.6F;
    }

    /**
     * Anchors a held item to the correct fist.
     *
     * <p>Blockbench mirrors X on export, so the part named {@code left_arm} sits
     * at -X - which is the entity's <em>right</em> side in Minecraft's model
     * space - and {@code right_arm} sits at +X, the entity's left. Guards are
     * flagged left-handed so their blade lands in {@code right_arm}, the arm the
     * attack clip actually swings.
     */
    @Override
    public void translateToHand(HumanoidArm arm, PoseStack poseStack) {
        // Walk the whole chain from the model root down to the fist. Vanilla
        // humanoids can skip this because their arms sit at the top level; ours
        // hang off "root", so leaving that transform out floats the held item a
        // block and a half up, level with the goblin's face.
        this.root.translateAndRotate(poseStack);

        boolean rightSide = arm == HumanoidArm.RIGHT;
        ModelPart limb = rightSide ? this.leftArm : this.rightArm;
        limb.translateAndRotate(poseStack);

        // ItemInHandLayer applies its own translate of 0.625 blocks AFTER this
        // call, and once its -90/180 rotations are unwound that works out as
        // 10px straight down the arm - vanilla's way of reaching a humanoid's
        // fist. Scaled by 0.8 it covers 8px here. Our hand cube is centred 9.5px
        // below the shoulder pivot, so only the remaining 1.5px is added.
        // Adding the full 9.5px double-counted it and left the dagger hanging
        // in front of the goblin's knees.
        poseStack.translate(rightSide ? -0.044F : 0.044F, 1.5F / 16.0F, 0.0F);
        poseStack.scale(0.8F, 0.8F, 0.8F);
    }
}
