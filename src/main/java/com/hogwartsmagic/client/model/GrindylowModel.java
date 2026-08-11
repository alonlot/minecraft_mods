package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.GrindylowEntity;
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
 * A Grindylow, built to the Fantastic Beasts design: a head far too big for the
 * body, eyes set wide and high, a mouth full of needles, curved spines swept
 * back off the skull, and six curling tentacles where legs ought to be.
 *
 * <p>Geometry comes from {@code blockbench_models/grindylow.bbmodel}; the
 * keyframe clips in {@link GrindylowAnimations} drive the head, jaw, arms and
 * spines only.
 *
 * <p><b>The tentacles are solved here.</b> How tightly they are drawn in is the
 * whole read on what the creature is doing - balled up tight while it lurks in
 * the weed, streaming out behind it while it swims, splayed wide as it lunges,
 * clenched while it has hold of you - and a fixed clip cannot know which of
 * those is happening. Each of the six is a three-link chain given a curl and a
 * travelling wave, phase-offset around the ring so they never move as one.
 */
public class GrindylowModel extends HierarchicalModel<GrindylowEntity> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "grindylow"), "main");

    private static final int TENTACLES = 6;
    private static final int LINKS = 3;
    /** Phase lag from one link to the next, so the wave travels down the limb. */
    private static final float WAVE_LAG = 1.05F;
    /** Each link bends more than the one above it, so the tip does the curling. */
    private static final float[] LINK_GAIN = {0.55F, 0.9F, 1.25F};

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    /** [tentacle][link], rooted in a ring under the body. */
    private final ModelPart[][] tentacles = new ModelPart[TENTACLES][LINKS];
    /** Outward direction of each tentacle in the ring, for curling away from centre. */
    private final float[] ringAngle = new float[TENTACLES];

    public GrindylowModel(ModelPart root) {
        this.root = root.getChild("root");
        this.body = this.root.getChild("body");
        this.head = this.body.getChild("head");
        for (int i = 0; i < TENTACLES; i++) {
            ModelPart a = this.body.getChild("tent" + (i + 1) + "_a");
            ModelPart b = a.getChild("tent" + (i + 1) + "_b");
            this.tentacles[i][0] = a;
            this.tentacles[i][1] = b;
            this.tentacles[i][2] = b.getChild("tent" + (i + 1) + "_c");
            this.ringAngle[i] = (float) (Math.PI * 2.0D * i / TENTACLES);
        }
    }

    public static LayerDefinition createBodyLayer() {
MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(39, 0).addBox(-3.6F, -5.6F, -3.2F, 7.2F, 6.6F, 6.4F, new CubeDeformation(0.0F))
                .texOffs(0, 17).addBox(-3.2F, -1.2F, -3.6F, 6.4F, 3.6F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, 0.0F));

        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -7.0F, -4.6F, 10.0F, 7.0F, 8.6F, new CubeDeformation(0.0F))
                .texOffs(27, 17).addBox(-4.0F, -8.4F, -3.4F, 8.0F, 1.8F, 6.4F, new CubeDeformation(0.0F))
                .texOffs(45, 47).addBox(-4.2F, -1.3F, -4.8F, 8.4F, 1.9F, 3.8F, new CubeDeformation(0.0F))
                .texOffs(72, 47).addBox(2.4F, -6.5F, -5.1F, 3.1F, 3.1F, 1.9F, new CubeDeformation(0.0F))
                .texOffs(85, 47).addBox(-5.5F, -6.5F, -5.1F, 3.1F, 3.1F, 1.9F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -6.0F, 0.0F));

        PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(70, 0).addBox(-4.0F, 0.0F, -7.5F, 8.0F, 4.2F, 7.6F, new CubeDeformation(0.0F))
                .texOffs(98, 47).addBox(-3.6F, -1.9F, -7.3F, 7.2F, 1.9F, 3.8F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.2F, 2.5F));

        PartDefinition spine_l1_g = head.addOrReplaceChild("spine_l1_g", CubeListBuilder.create().texOffs(58, 17).addBox(-0.8F, -0.8F, 0.0F, 1.6F, 1.5F, 6.5F, new CubeDeformation(0.0F)), PartPose.offset(4.4F, -6.2F, 2.0F));

        PartDefinition spine_l2_g = head.addOrReplaceChild("spine_l2_g", CubeListBuilder.create().texOffs(77, 17).addBox(-0.9F, -0.7F, 0.0F, 1.8F, 1.4F, 6.4F, new CubeDeformation(0.0F)), PartPose.offset(4.9F, -3.6F, 2.6F));

        PartDefinition spine_l3_g = head.addOrReplaceChild("spine_l3_g", CubeListBuilder.create().texOffs(30, 28).addBox(-0.8F, -0.6F, 0.0F, 1.6F, 1.2F, 5.4F, new CubeDeformation(0.0F)), PartPose.offset(4.5F, -1.0F, 2.6F));

        PartDefinition spine_r1_g = head.addOrReplaceChild("spine_r1_g", CubeListBuilder.create().texOffs(96, 17).addBox(-0.8F, -0.8F, 0.0F, 1.6F, 1.5F, 6.5F, new CubeDeformation(0.0F)), PartPose.offset(-4.4F, -6.2F, 2.0F));

        PartDefinition spine_r2_g = head.addOrReplaceChild("spine_r2_g", CubeListBuilder.create().texOffs(0, 28).addBox(-0.9F, -0.7F, 0.0F, 1.8F, 1.4F, 6.4F, new CubeDeformation(0.0F)), PartPose.offset(-4.9F, -3.6F, 2.6F));

        PartDefinition spine_r3_g = head.addOrReplaceChild("spine_r3_g", CubeListBuilder.create().texOffs(47, 28).addBox(-0.8F, -0.6F, 0.0F, 1.6F, 1.2F, 5.4F, new CubeDeformation(0.0F)), PartPose.offset(-4.5F, -1.0F, 2.6F));

        PartDefinition spine_top_l_g = head.addOrReplaceChild("spine_top_l_g", CubeListBuilder.create().texOffs(64, 28).addBox(-0.7F, -0.7F, 0.0F, 1.4F, 1.4F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(1.6F, -8.0F, 1.5F));

        PartDefinition spine_top_r_g = head.addOrReplaceChild("spine_top_r_g", CubeListBuilder.create().texOffs(81, 28).addBox(-0.7F, -0.7F, 0.0F, 1.4F, 1.4F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(-1.6F, -8.0F, 1.5F));

        PartDefinition arm_left = body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(103, 0).addBox(-0.5F, -0.5F, -1.3F, 2.0F, 6.2F, 2.6F, new CubeDeformation(0.0F)), PartPose.offset(3.6F, -4.5F, 0.0F));

        PartDefinition forearm_left = arm_left.addOrReplaceChild("forearm_left", CubeListBuilder.create().texOffs(115, 17).addBox(-0.4F, -0.2F, -1.1F, 1.6F, 4.8F, 2.2F, new CubeDeformation(0.0F)), PartPose.offset(0.6F, 5.5F, 0.0F));

        PartDefinition hand_left = forearm_left.addOrReplaceChild("hand_left", CubeListBuilder.create().texOffs(123, 47).addBox(0.9F, -0.2F, -0.9F, 0.8F, 4.6F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(52, 38).addBox(0.0F, -0.2F, -0.9F, 0.8F, 5.8F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 55).addBox(-0.9F, -0.2F, -0.9F, 0.8F, 4.2F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.4F, 4.4F, 0.0F));

        PartDefinition arm_right = body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(114, 0).addBox(-1.5F, -0.5F, -1.3F, 2.0F, 6.2F, 2.6F, new CubeDeformation(0.0F)), PartPose.offset(-3.6F, -4.5F, 0.0F));

        PartDefinition forearm_right = arm_right.addOrReplaceChild("forearm_right", CubeListBuilder.create().texOffs(19, 28).addBox(-1.2F, -0.2F, -1.1F, 1.6F, 4.8F, 2.2F, new CubeDeformation(0.0F)), PartPose.offset(-0.6F, 5.5F, 0.0F));

        PartDefinition hand_right = forearm_right.addOrReplaceChild("hand_right", CubeListBuilder.create().texOffs(5, 55).addBox(-1.7F, -0.2F, -0.9F, 0.8F, 4.6F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(57, 38).addBox(-0.8F, -0.2F, -0.9F, 0.8F, 5.8F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(10, 55).addBox(0.1F, -0.2F, -0.9F, 0.8F, 4.2F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.4F, 4.4F, 0.0F));

        PartDefinition tent1_a = body.addOrReplaceChild("tent1_a", CubeListBuilder.create().texOffs(98, 28).addBox(-1.15F, -0.2F, -1.15F, 2.3F, 4.8F, 2.3F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 1.0F, -2.7F));

        PartDefinition tent1_b = tent1_a.addOrReplaceChild("tent1_b", CubeListBuilder.create().texOffs(62, 38).addBox(-0.85F, -0.2F, -0.85F, 1.7F, 4.2F, 1.7F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 4.6F, 0.0F));

        PartDefinition tent1_c = tent1_b.addOrReplaceChild("tent1_c", CubeListBuilder.create().texOffs(71, 38).addBox(-0.6F, -0.2F, -0.6F, 1.2F, 4.2F, 1.2F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 4.0F, 0.0F));

        PartDefinition tent2_a = body.addOrReplaceChild("tent2_a", CubeListBuilder.create().texOffs(111, 28).addBox(-1.15F, -0.2F, -1.15F, 2.3F, 4.8F, 2.3F, new CubeDeformation(0.0F)), PartPose.offset(2.4F, 1.0F, -1.4F));

        PartDefinition tent2_b = tent2_a.addOrReplaceChild("tent2_b", CubeListBuilder.create().texOffs(80, 38).addBox(-0.85F, -0.2F, -0.85F, 1.7F, 4.2F, 1.7F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 4.6F, 0.0F));

        PartDefinition tent2_c = tent2_b.addOrReplaceChild("tent2_c", CubeListBuilder.create().texOffs(89, 38).addBox(-0.6F, -0.2F, -0.6F, 1.2F, 4.2F, 1.2F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 4.0F, 0.0F));

        PartDefinition tent3_a = body.addOrReplaceChild("tent3_a", CubeListBuilder.create().texOffs(0, 38).addBox(-1.15F, -0.2F, -1.15F, 2.3F, 4.8F, 2.3F, new CubeDeformation(0.0F)), PartPose.offset(2.4F, 1.0F, 1.4F));

        PartDefinition tent3_b = tent3_a.addOrReplaceChild("tent3_b", CubeListBuilder.create().texOffs(98, 38).addBox(-0.85F, -0.2F, -0.85F, 1.7F, 4.2F, 1.7F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 4.6F, 0.0F));

        PartDefinition tent3_c = tent3_b.addOrReplaceChild("tent3_c", CubeListBuilder.create().texOffs(107, 38).addBox(-0.6F, -0.2F, -0.6F, 1.2F, 4.2F, 1.2F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 4.0F, 0.0F));

        PartDefinition tent4_a = body.addOrReplaceChild("tent4_a", CubeListBuilder.create().texOffs(13, 38).addBox(-1.15F, -0.2F, -1.15F, 2.3F, 4.8F, 2.3F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 1.0F, 2.7F));

        PartDefinition tent4_b = tent4_a.addOrReplaceChild("tent4_b", CubeListBuilder.create().texOffs(116, 38).addBox(-0.85F, -0.2F, -0.85F, 1.7F, 4.2F, 1.7F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 4.6F, 0.0F));

        PartDefinition tent4_c = tent4_b.addOrReplaceChild("tent4_c", CubeListBuilder.create().texOffs(0, 47).addBox(-0.6F, -0.2F, -0.6F, 1.2F, 4.2F, 1.2F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 4.0F, 0.0F));

        PartDefinition tent5_a = body.addOrReplaceChild("tent5_a", CubeListBuilder.create().texOffs(26, 38).addBox(-1.15F, -0.2F, -1.15F, 2.3F, 4.8F, 2.3F, new CubeDeformation(0.0F)), PartPose.offset(-2.4F, 1.0F, 1.4F));

        PartDefinition tent5_b = tent5_a.addOrReplaceChild("tent5_b", CubeListBuilder.create().texOffs(9, 47).addBox(-0.85F, -0.2F, -0.85F, 1.7F, 4.2F, 1.7F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 4.6F, 0.0F));

        PartDefinition tent5_c = tent5_b.addOrReplaceChild("tent5_c", CubeListBuilder.create().texOffs(18, 47).addBox(-0.6F, -0.2F, -0.6F, 1.2F, 4.2F, 1.2F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 4.0F, 0.0F));

        PartDefinition tent6_a = body.addOrReplaceChild("tent6_a", CubeListBuilder.create().texOffs(39, 38).addBox(-1.15F, -0.2F, -1.15F, 2.3F, 4.8F, 2.3F, new CubeDeformation(0.0F)), PartPose.offset(-2.4F, 1.0F, -1.4F));

        PartDefinition tent6_b = tent6_a.addOrReplaceChild("tent6_b", CubeListBuilder.create().texOffs(27, 47).addBox(-0.85F, -0.2F, -0.85F, 1.7F, 4.2F, 1.7F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 4.6F, 0.0F));

        PartDefinition tent6_c = tent6_b.addOrReplaceChild("tent6_c", CubeListBuilder.create().texOffs(36, 47).addBox(-0.6F, -0.2F, -0.6F, 1.2F, 4.2F, 1.2F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 4.0F, 0.0F));
        return LayerDefinition.create(meshdefinition, 128, 64);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(GrindylowEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        float partialTick = Mth.clamp(ageInTicks - entity.tickCount, 0.0F, 1.0F);
        solveTentacles(entity, ageInTicks, partialTick);

        this.animate(entity.lurkAnimation, GrindylowAnimations.LURK, ageInTicks);
        this.animate(entity.driftAnimation, GrindylowAnimations.DRIFT, ageInTicks);
        this.animate(entity.lungeAnimation, GrindylowAnimations.LUNGE, ageInTicks);
        this.animate(entity.clutchAnimation, GrindylowAnimations.CLUTCH, ageInTicks);
        this.animate(entity.hurtAnimation, GrindylowAnimations.HURT, ageInTicks);
        this.animate(entity.deathAnimation, GrindylowAnimations.DEATH, ageInTicks);

        // A lurking grindylow does not look around; it has already decided where
        // you are and is waiting. Everything else tracks hard - the head is most
        // of the animal, so turning it reads from a long way off.
        float track = entity.isLurking() ? 0.0F : (entity.hasVictim() ? 0.3F : 1.0F);
        this.head.yRot += Mth.clamp(netHeadYaw, -75.0F, 75.0F) * (Mth.PI / 180.0F) * track;
        this.head.xRot += Mth.clamp(headPitch, -50.0F, 50.0F) * (Mth.PI / 180.0F) * track * 0.8F;
    }

    /**
     * Curls the six tentacle chains.
     *
     * <p>Each link extends along its own +Y, so bending it away from the body's
     * centre is a rotation about X and Z weighted by where that tentacle sits in
     * the ring. On top of that goes a travelling wave with a per-tentacle phase
     * offset, which is what stops six identical limbs moving like one comb.
     */
    private void solveTentacles(GrindylowEntity entity, float ageInTicks, float partialTick) {
        float curl = entity.getTentacleCurl(partialTick);
        float wave = entity.getTentacleWave(partialTick);
        float rate = 0.12F + wave * 0.28F;
        float amplitude = 0.10F + wave * 0.26F;

        for (int i = 0; i < TENTACLES; i++) {
            float angle = this.ringAngle[i];
            float outX = Mth.sin(angle);
            float outZ = Mth.cos(angle);
            for (int link = 0; link < LINKS; link++) {
                ModelPart part = this.tentacles[i][link];
                float gain = LINK_GAIN[link];
                // Curl outward and up when splayed, inward and tight when balled.
                part.xRot += curl * gain * outZ * 0.85F;
                part.zRot += curl * gain * -outX * 0.85F;
                float phase = ageInTicks * rate - link * WAVE_LAG + angle * 1.7F;
                part.xRot += Mth.sin(phase) * amplitude * gain;
                part.zRot += Mth.cos(phase * 0.8F) * amplitude * gain * 0.6F;
            }
        }
    }
}
