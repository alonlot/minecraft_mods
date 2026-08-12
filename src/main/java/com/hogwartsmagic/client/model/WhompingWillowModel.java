package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.WhompingWillowEntity;
import com.hogwartsmagic.entity.WhompingWillowEntity.AttackState;
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
 * The Whomping Willow.
 *
 * <p><b>Geometry between the GENERATED GEOMETRY markers is written by
 * {@code build_scripts/generate_whomping_willow_model.py} — do not hand-edit it.</b> Change the
 * constants in that script and re-run it. Everything outside the markers is hand-written and the
 * splice will not touch it.
 *
 * <p><b>There are no keyframe clips.</b> Unlike most mobs in this mod there is no
 * {@code WhompingWillowAnimations} class at all, and that is deliberate: a tree's motion is entirely
 * wave-and-bend, which is what sinusoids do well and keyframes do badly, and a fixed clip cannot know
 * which of four branches is currently swinging. {@code HungarianHorntailModel} and
 * {@code GrindylowModel} solve their appendages the same way.
 *
 * <p>The branches are solved from the same {@code public static} functions the server uses to
 * compute the damage arc — {@link WhompingWillowEntity#strikeReach} and
 * {@link WhompingWillowEntity#branchTrail} — so what you see a branch sweep through is what it hits.
 * Reading {@code getActiveBranch()} and {@code getSweepSign()} fresh every frame (rather than
 * latching them on a state change) is what keeps them correct even if the parameter and the state
 * arrive in the same packet.
 */
public class WhompingWillowModel extends HierarchicalModel<WhompingWillowEntity> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "whomping_willow"), "main");

    private static final int RINGS = 8;
    private static final int BRANCHES = 4;
    private static final int SEGS = 5;

    /** How much of a branch's motion each segment carries. Spreading the bend down the chain instead
     *  of pivoting at the shoulder is what makes it read as a whip rather than a plank. */
    private static final float[] SEG_WEIGHT = {0.22F, 0.26F, 0.30F, 0.36F, 0.44F};
    /** Ticks of lag per segment, so the tip trails the base through a swing. */
    private static final float[] SEG_LAG = {0.0F, 0.06F, 0.13F, 0.21F, 0.30F};

    private final ModelPart root;
    private final ModelPart willow;
    private final ModelPart[] trunk = new ModelPart[RINGS];
    private final ModelPart[][] branch = new ModelPart[BRANCHES][SEGS];
    private final ModelPart crown;
    private final ModelPart mawJaw;

    public WhompingWillowModel(ModelPart root) {
        this.root = root.getChild("root");
        this.willow = this.root.getChild("willow");

        ModelPart part = this.willow;
        for (int i = 0; i < RINGS; i++) {
            part = part.getChild("trunk" + (i + 1));
            this.trunk[i] = part;
        }
        this.crown = this.trunk[RINGS - 1].getChild("crown");
        this.mawJaw = this.trunk[2].getChild("maw").getChild("maw_jaw");

        // Hosts mirror BRANCH_HOST in the generator: branches 0 and 2 on ring 5, 1 and 3 on ring 7.
        for (int b = 0; b < BRANCHES; b++) {
            ModelPart host = this.trunk[(b % 2 == 0) ? 4 : 6];
            ModelPart seg = host.getChild("branch" + (b + 1) + "_s1");
            this.branch[b][0] = seg;
            for (int s = 1; s < SEGS; s++) {
                seg = seg.getChild("branch" + (b + 1) + "_s" + (s + 1));
                this.branch[b][s] = seg;
            }
        }
    }

    // <<< GENERATED GEOMETRY
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition partdefinition = mesh.getRoot();

        PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        PartDefinition willow = root.addOrReplaceChild("willow", CubeListBuilder.create(),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition roots = willow.addOrReplaceChild("roots", CubeListBuilder.create()
            .texOffs(402, 126).addBox(20.7464F, -7.8325F, -7.0334F, 13.6973F, 7.8325F, 14.0667F, new CubeDeformation(0.0F))
            .texOffs(0, 159).addBox(14.3958F, -7.2274F, 12.6923F, 12.8263F, 7.2274F, 16.2333F, new CubeDeformation(0.0F))
            .texOffs(60, 159).addBox(-8.4524F, -9.5927F, 20.6779F, 16.9047F, 9.5927F, 13.2472F, new CubeDeformation(0.0F))
            .texOffs(122, 159).addBox(-24.1048F, -8.9531F, 8.4556F, 16.539F, 8.9531F, 14.7593F, new CubeDeformation(0.0F))
            .texOffs(186, 159).addBox(-33.3996F, -7.8482F, -7.539F, 15.34F, 7.8482F, 15.078F, new CubeDeformation(0.0F))
            .texOffs(250, 159).addBox(-27.8943F, -9.2667F, -28.0424F, 16.7385F, 9.2667F, 17.0347F, new CubeDeformation(0.0F))
            .texOffs(320, 159).addBox(-7.4217F, -7.4108F, -34.6725F, 14.8433F, 7.4108F, 15.9997F, new CubeDeformation(0.0F))
            .texOffs(382, 159).addBox(11.415F, -7.57F, -26.5194F, 12.8728F, 7.57F, 17.3359F, new CubeDeformation(0.0F))
            .texOffs(0, 187).addBox(-26.0F, -4.0F, -26.0F, 52.0F, 4.0F, 52.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition trunk1 = willow.addOrReplaceChild("trunk1", CubeListBuilder.create()
            .texOffs(208, 187).addBox(-24.1802F, -16.0F, -21.9921F, 46.0F, 16.0F, 46.0F, new CubeDeformation(0.0F))
            .texOffs(392, 187).addBox(17.0F, -11.0F, -7.599F, 8.0F, 7.0F, 10.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.0233F, 0.0F));
        PartDefinition trunk2 = trunk1.addOrReplaceChild("trunk2", CubeListBuilder.create()
            .texOffs(0, 249).addBox(-20.8415F, -16.0F, -21.2453F, 42.0F, 16.0F, 42.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -16.0F, 0.0F, 0.0F, 0.0126F, 0.0F));
        PartDefinition trunk3 = trunk2.addOrReplaceChild("trunk3", CubeListBuilder.create()
            .texOffs(168, 249).addBox(-19.8659F, -16.0F, -17.552F, 38.0F, 16.0F, 38.0F, new CubeDeformation(0.0F))
            .texOffs(320, 249).addBox(-21.0F, -11.0F, -2.1395F, 8.0F, 7.0F, 10.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -16.0F, 0.0F, 0.0F, 0.0437F, 0.0F));
        PartDefinition trunk4 = trunk3.addOrReplaceChild("trunk4", CubeListBuilder.create()
            .texOffs(356, 249).addBox(-17.2741F, -16.0F, -17.5847F, 34.0F, 16.0F, 34.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -16.0F, 0.0F, 0.0F, 0.0272F, 0.0F));
        PartDefinition trunk5 = trunk4.addOrReplaceChild("trunk5", CubeListBuilder.create()
            .texOffs(0, 307).addBox(-13.6245F, -16.0F, -15.7966F, 30.0F, 16.0F, 30.0F, new CubeDeformation(0.0F))
            .texOffs(120, 307).addBox(9.0F, -11.0F, -6.8904F, 8.0F, 7.0F, 10.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -16.0F, 0.0F, 0.0F, -0.0346F, 0.0F));
        PartDefinition trunk6 = trunk5.addOrReplaceChild("trunk6", CubeListBuilder.create()
            .texOffs(156, 307).addBox(-13.5385F, -16.0F, -14.2713F, 26.0F, 16.0F, 26.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -16.0F, 0.0F, 0.0F, 0.0244F, 0.0F));
        PartDefinition trunk7 = trunk6.addOrReplaceChild("trunk7", CubeListBuilder.create()
            .texOffs(260, 307).addBox(-10.7086F, -16.0F, -10.8554F, 22.0F, 16.0F, 22.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -16.0F, 0.0F, 0.0F, -0.0012F, 0.0F));
        PartDefinition trunk8 = trunk7.addOrReplaceChild("trunk8", CubeListBuilder.create()
            .texOffs(348, 307).addBox(-7.9401F, -14.0F, -8.7587F, 18.0F, 14.0F, 18.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -16.0F, 0.0F, 0.0F, -0.0445F, 0.0F));
        PartDefinition crown = trunk8.addOrReplaceChild("crown", CubeListBuilder.create()
            .texOffs(310, 88).addBox(-1.2203F, -24.6094F, -4.1271F, 21.1997F, 20.4002F, 16.1853F, new CubeDeformation(0.0F))
            .texOffs(388, 88).addBox(-5.5861F, -27.6968F, 9.3365F, 15.5303F, 17.6017F, 16.4081F, new CubeDeformation(0.0F))
            .texOffs(0, 126).addBox(-22.4316F, -22.1722F, 0.2277F, 17.821F, 12.0796F, 19.9828F, new CubeDeformation(0.0F))
            .texOffs(76, 126).addBox(-20.4977F, -28.362F, -13.4213F, 14.3881F, 15.5799F, 15.5933F, new CubeDeformation(0.0F))
            .texOffs(138, 126).addBox(-12.0979F, -26.6887F, -21.1936F, 21.0428F, 13.5809F, 17.0071F, new CubeDeformation(0.0F))
            .texOffs(218, 126).addBox(4.5028F, -19.5802F, -16.4781F, 14.9397F, 17.0226F, 14.8584F, new CubeDeformation(0.0F))
            .texOffs(222, 88).addBox(3.6762F, -30.7423F, 1.3616F, 22.0F, 3.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(222, 88).addBox(-6.968F, -27.9425F, 5.3012F, 22.0F, 3.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(222, 88).addBox(-16.5846F, -49.4857F, 2.7484F, 22.0F, 3.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(222, 88).addBox(-26.3392F, -23.0609F, -1.4548F, 22.0F, 3.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(222, 88).addBox(-24.8458F, -26.3577F, -11.9942F, 22.0F, 3.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(222, 88).addBox(-25.4837F, -46.2946F, -23.1995F, 22.0F, 3.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(222, 88).addBox(-14.0184F, -35.0275F, -23.2033F, 22.0F, 3.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(222, 88).addBox(-4.2584F, -33.3017F, -27.5966F, 22.0F, 3.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(222, 88).addBox(4.7975F, -35.5127F, -20.8304F, 22.0F, 3.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(222, 88).addBox(7.8177F, -50.5702F, -9.6488F, 22.0F, 3.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(142, 88).addBox(-10.0F, -54.0F, -10.0F, 20.0F, 8.0F, 20.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -14.0F, 0.0F));
        PartDefinition branch2_s1 = trunk7.addOrReplaceChild("branch2_s1", CubeListBuilder.create()
            .texOffs(412, 0).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 10.0F, 34.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -4.0F, 9.0F, 0.25F, 1.5708F, 0.0F));
        PartDefinition branch2_s2 = branch2_s1.addOrReplaceChild("branch2_s2", CubeListBuilder.create()
            .texOffs(0, 44).addBox(-4.0F, -4.0F, 0.0F, 8.0F, 8.0F, 30.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 34.0F, -0.15F, 0.0F, 0.0F));
        PartDefinition branch2_s3 = branch2_s2.addOrReplaceChild("branch2_s3", CubeListBuilder.create()
            .texOffs(76, 44).addBox(-3.5F, -3.5F, 0.0F, 7.0F, 7.0F, 26.0F, new CubeDeformation(0.0F))
            .texOffs(0, 0).addBox(-10.0F, -5.0F, 8.2F, 20.0F, 3.0F, 20.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 30.0F, -0.2F, 0.0F, 0.0F));
        PartDefinition branch2_s4 = branch2_s3.addOrReplaceChild("branch2_s4", CubeListBuilder.create()
            .texOffs(310, 0).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(0, 0).addBox(-10.0F, -5.0F, 5.4F, 20.0F, 3.0F, 20.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 26.0F, -0.3F, 0.0F, 0.0F));
        PartDefinition branch2_s5 = branch2_s4.addOrReplaceChild("branch2_s5", CubeListBuilder.create()
            .texOffs(366, 0).addBox(-2.5F, -2.5F, 0.0F, 5.0F, 5.0F, 18.0F, new CubeDeformation(0.0F))
            .texOffs(0, 0).addBox(-10.0F, -5.0F, 2.6F, 20.0F, 3.0F, 20.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.45F, 0.0F, 0.0F));
        PartDefinition branch4_s1 = trunk7.addOrReplaceChild("branch4_s1", CubeListBuilder.create()
            .texOffs(372, 44).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 10.0F, 34.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -4.0F, 9.0F, 0.25F, 4.7124F, 0.0F));
        PartDefinition branch4_s2 = branch4_s1.addOrReplaceChild("branch4_s2", CubeListBuilder.create()
            .texOffs(0, 88).addBox(-4.0F, -4.0F, 0.0F, 8.0F, 8.0F, 30.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 34.0F, -0.15F, 0.0F, 0.0F));
        PartDefinition branch4_s3 = branch4_s2.addOrReplaceChild("branch4_s3", CubeListBuilder.create()
            .texOffs(76, 88).addBox(-3.5F, -3.5F, 0.0F, 7.0F, 7.0F, 26.0F, new CubeDeformation(0.0F))
            .texOffs(0, 0).addBox(-10.0F, -5.0F, 8.2F, 20.0F, 3.0F, 20.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 30.0F, -0.2F, 0.0F, 0.0F));
        PartDefinition branch4_s4 = branch4_s3.addOrReplaceChild("branch4_s4", CubeListBuilder.create()
            .texOffs(310, 0).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(0, 0).addBox(-10.0F, -5.0F, 5.4F, 20.0F, 3.0F, 20.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 26.0F, -0.3F, 0.0F, 0.0F));
        PartDefinition branch4_s5 = branch4_s4.addOrReplaceChild("branch4_s5", CubeListBuilder.create()
            .texOffs(366, 0).addBox(-2.5F, -2.5F, 0.0F, 5.0F, 5.0F, 18.0F, new CubeDeformation(0.0F))
            .texOffs(0, 0).addBox(-10.0F, -5.0F, 2.6F, 20.0F, 3.0F, 20.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.45F, 0.0F, 0.0F));
        PartDefinition branch1_s1 = trunk5.addOrReplaceChild("branch1_s1", CubeListBuilder.create()
            .texOffs(80, 0).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 10.0F, 34.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -6.0F, 13.0F, 0.25F, 0.0F, 0.0F));
        PartDefinition branch1_s2 = branch1_s1.addOrReplaceChild("branch1_s2", CubeListBuilder.create()
            .texOffs(168, 0).addBox(-4.0F, -4.0F, 0.0F, 8.0F, 8.0F, 30.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 34.0F, -0.15F, 0.0F, 0.0F));
        PartDefinition branch1_s3 = branch1_s2.addOrReplaceChild("branch1_s3", CubeListBuilder.create()
            .texOffs(244, 0).addBox(-3.5F, -3.5F, 0.0F, 7.0F, 7.0F, 26.0F, new CubeDeformation(0.0F))
            .texOffs(0, 0).addBox(-10.0F, -5.0F, 8.2F, 20.0F, 3.0F, 20.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 30.0F, -0.2F, 0.0F, 0.0F));
        PartDefinition branch1_s4 = branch1_s3.addOrReplaceChild("branch1_s4", CubeListBuilder.create()
            .texOffs(310, 0).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(0, 0).addBox(-10.0F, -5.0F, 5.4F, 20.0F, 3.0F, 20.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 26.0F, -0.3F, 0.0F, 0.0F));
        PartDefinition branch1_s5 = branch1_s4.addOrReplaceChild("branch1_s5", CubeListBuilder.create()
            .texOffs(366, 0).addBox(-2.5F, -2.5F, 0.0F, 5.0F, 5.0F, 18.0F, new CubeDeformation(0.0F))
            .texOffs(0, 0).addBox(-10.0F, -5.0F, 2.6F, 20.0F, 3.0F, 20.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.45F, 0.0F, 0.0F));
        PartDefinition branch3_s1 = trunk5.addOrReplaceChild("branch3_s1", CubeListBuilder.create()
            .texOffs(142, 44).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 10.0F, 34.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -6.0F, 13.0F, 0.25F, 3.1416F, 0.0F));
        PartDefinition branch3_s2 = branch3_s1.addOrReplaceChild("branch3_s2", CubeListBuilder.create()
            .texOffs(230, 44).addBox(-4.0F, -4.0F, 0.0F, 8.0F, 8.0F, 30.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 34.0F, -0.15F, 0.0F, 0.0F));
        PartDefinition branch3_s3 = branch3_s2.addOrReplaceChild("branch3_s3", CubeListBuilder.create()
            .texOffs(306, 44).addBox(-3.5F, -3.5F, 0.0F, 7.0F, 7.0F, 26.0F, new CubeDeformation(0.0F))
            .texOffs(0, 0).addBox(-10.0F, -5.0F, 8.2F, 20.0F, 3.0F, 20.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 30.0F, -0.2F, 0.0F, 0.0F));
        PartDefinition branch3_s4 = branch3_s3.addOrReplaceChild("branch3_s4", CubeListBuilder.create()
            .texOffs(310, 0).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(0, 0).addBox(-10.0F, -5.0F, 5.4F, 20.0F, 3.0F, 20.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 26.0F, -0.3F, 0.0F, 0.0F));
        PartDefinition branch3_s5 = branch3_s4.addOrReplaceChild("branch3_s5", CubeListBuilder.create()
            .texOffs(366, 0).addBox(-2.5F, -2.5F, 0.0F, 5.0F, 5.0F, 18.0F, new CubeDeformation(0.0F))
            .texOffs(0, 0).addBox(-10.0F, -5.0F, 2.6F, 20.0F, 3.0F, 20.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.45F, 0.0F, 0.0F));
        PartDefinition maw = trunk3.addOrReplaceChild("maw", CubeListBuilder.create()
            .texOffs(358, 126).addBox(-8.0F, -10.0F, -4.0F, 16.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
            .texOffs(278, 126).addBox(-7.0F, -6.0F, -1.0F, 14.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -6.0F, -19.0F));
        PartDefinition maw_jaw = maw.addOrReplaceChild("maw_jaw", CubeListBuilder.create()
            .texOffs(314, 126).addBox(-8.0F, -1.0F, -4.0F, 16.0F, 5.0F, 6.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 2.0F, 0.0F));

        return LayerDefinition.create(mesh, 512, 512);
    }
    // >>> GENERATED GEOMETRY

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(WhompingWillowEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // Every pose below is additive, so the rest pose -- including the willow droop baked into
        // each branch segment's PartPose -- has to be restored first.
        this.root().getAllParts().forEach(ModelPart::resetPose);

        AttackState state = entity.getAttackState();
        boolean dormant = state == AttackState.DORMANT || state == AttackState.FROZEN;

        sway(entity, ageInTicks, dormant);

        int active = entity.getActiveBranch();
        float sign = entity.getSweepSign();
        for (int b = 0; b < BRANCHES; b++) {
            idleBranch(b, ageInTicks, dormant);
            if (!dormant) {
                poseBranch(entity, b, state, b == active, sign, ageInTicks);
            }
        }

        if (state == AttackState.WAKE) {
            // A shudder that runs up the whole tree, decaying as it goes.
            float p = entity.stateProgress(WhompingWillowEntity.WAKE_DURATION);
            float shake = (1.0F - p) * 0.09F * Mth.sin(ageInTicks * 2.1F);
            for (int i = 0; i < RINGS; i++) {
                this.trunk[i].zRot += shake * (0.4F + i * 0.1F);
            }
            this.mawJaw.xRot += (1.0F - p) * 0.5F;
        }
    }

    /** Trunk sway. Each ring adds a little, and because the rings are a nested chain the crown moves
     *  far more than the base -- which is how a real tree behaves and why the trunk is eight bones. */
    private void sway(WhompingWillowEntity entity, float age, boolean dormant) {
        float amp = dormant ? 0.010F : 0.026F;
        float wind = Mth.sin(age * 0.021F);
        float creak = Mth.sin(age * 0.037F + 1.1F);
        for (int i = 0; i < RINGS; i++) {
            float gain = 0.35F + i * 0.16F;
            this.trunk[i].xRot += wind * amp * gain;
            this.trunk[i].zRot += creak * amp * 0.7F * gain;
        }
        this.crown.xRot += wind * amp * 1.4F;
        if (!dormant) {
            // Roused, it holds itself up and open rather than hanging.
            this.willow.xRot -= 0.04F;
        }
    }

    private void idleBranch(int b, float age, boolean dormant) {
        float amp = dormant ? 0.02F : 0.06F;
        float phase = b * 1.7F;
        for (int s = 0; s < SEGS; s++) {
            float w = Mth.sin(age * 0.043F + phase + s * 0.5F);
            this.branch[b][s].yRot += w * amp * SEG_WEIGHT[s];
            this.branch[b][s].xRot += Mth.cos(age * 0.031F + phase + s * 0.4F) * amp * 0.5F;
        }
    }

    /**
     * The committed poses. if/else on the state rather than switch: the synthetic switch-map helper
     * class has been observed to fail to load in this mod's dev environment, which crashes on summon.
     */
    private void poseBranch(WhompingWillowEntity entity, int b, AttackState state,
                            boolean active, float sign, float age) {
        if (state == AttackState.SWEEP && active) {
            float p = entity.stateProgress(WhompingWillowEntity.SWEEP_DURATION);
            for (int s = 0; s < SEGS; s++) {
                float lagged = Mth.clamp(p - SEG_LAG[s], 0.0F, 1.0F);
                this.branch[b][s].yRot += WhompingWillowEntity.branchTrail(lagged) * sign * SEG_WEIGHT[s];
                // A striking branch straightens: the droop is cancelled in proportion to reach.
                this.branch[b][s].xRot -= WhompingWillowEntity.strikeReach(lagged) * SEG_WEIGHT[s] * 1.6F;
            }
        } else if (state == AttackState.SLAM && active) {
            float p = entity.stateProgress(WhompingWillowEntity.SLAM_DURATION);
            float rear = arc(p, WhompingWillowEntity.SLAM_STRIKE_TICK / (float) WhompingWillowEntity.SLAM_DURATION);
            for (int s = 0; s < SEGS; s++) {
                this.branch[b][s].xRot += rear * SEG_WEIGHT[s] * 3.2F;
            }
        } else if (state == AttackState.ROOT_STOMP) {
            // Every branch rears together -- the one tell that is visible from any angle.
            float p = entity.stateProgress(WhompingWillowEntity.STOMP_DURATION);
            float rear = arc(p, WhompingWillowEntity.STOMP_STRIKE_TICK / (float) WhompingWillowEntity.STOMP_DURATION);
            for (int s = 0; s < SEGS; s++) {
                this.branch[b][s].xRot += rear * SEG_WEIGHT[s] * 2.6F;
            }
        } else if (state == AttackState.SWAT && active) {
            float p = entity.stateProgress(WhompingWillowEntity.SWAT_DURATION);
            float up = arc(p, WhompingWillowEntity.SWAT_STRIKE_TICK / (float) WhompingWillowEntity.SWAT_DURATION);
            for (int s = 0; s < SEGS; s++) {
                this.branch[b][s].xRot -= up * SEG_WEIGHT[s] * 3.6F;
            }
        } else if ((state == AttackState.GRAB || state == AttackState.SMASH) && active) {
            int duration = state == AttackState.GRAB
                    ? WhompingWillowEntity.GRAB_DURATION : WhompingWillowEntity.SMASH_DURATION;
            float p = entity.stateProgress(duration);
            float lift = state == AttackState.GRAB ? p : WhompingWillowEntity.holdLift(p);
            for (int s = 0; s < SEGS; s++) {
                this.branch[b][s].xRot -= lift * SEG_WEIGHT[s] * 2.4F;
            }
        } else if (state == AttackState.STAGGER) {
            float p = entity.stateProgress(WhompingWillowEntity.STAGGER_DURATION);
            float flop = (1.0F - p) * Mth.sin(age * 1.6F + b) * 0.22F;
            for (int s = 0; s < SEGS; s++) {
                this.branch[b][s].zRot += flop * SEG_WEIGHT[s] * 2.0F;
            }
        }
    }

    /** Rise to 1 at {@code peak}, then fall away. The wind-up eases, the strike does not — which is
     *  what makes the tell readable and the blow feel like it lands. */
    private static float arc(float p, float peak) {
        if (p <= peak) {
            float w = peak <= 0.0F ? 1.0F : p / peak;
            return Mth.sin(w * Mth.PI * 0.5F);
        }
        float r = (p - peak) / Math.max(1.0E-4F, 1.0F - peak);
        return (1.0F - r) * (1.0F - r);
    }
}
