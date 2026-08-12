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

    /**
     * A bone's local yaw is the NEGATED world yaw: Minecraft renders the model rotated by
     * (180 - yaw) and {@code Vec3.directionFromRotation} maps a yaw to a rotation of minus that
     * yaw. Every yRot below that has to agree with a server-side direction is multiplied by this.
     * Getting it wrong sweeps the branch the opposite way from the arc that deals the damage.
     */
    private static final float YAW_TO_MODEL = -1.0F;

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
            .texOffs(212, 184).addBox(20.7464F, -7.8325F, -7.0334F, 13.6973F, 7.8325F, 14.0667F, new CubeDeformation(0.0F))
            .texOffs(270, 184).addBox(14.3958F, -7.2274F, 12.6923F, 12.8263F, 7.2274F, 16.2333F, new CubeDeformation(0.0F))
            .texOffs(330, 184).addBox(-8.4524F, -9.5927F, 20.6779F, 16.9047F, 9.5927F, 13.2472F, new CubeDeformation(0.0F))
            .texOffs(392, 184).addBox(-24.1048F, -8.9531F, 8.4556F, 16.539F, 8.9531F, 14.7593F, new CubeDeformation(0.0F))
            .texOffs(0, 230).addBox(-33.3996F, -7.8482F, -7.539F, 15.34F, 7.8482F, 15.078F, new CubeDeformation(0.0F))
            .texOffs(64, 230).addBox(-27.8943F, -9.2667F, -28.0424F, 16.7385F, 9.2667F, 17.0347F, new CubeDeformation(0.0F))
            .texOffs(134, 230).addBox(-7.4217F, -7.4108F, -34.6725F, 14.8433F, 7.4108F, 15.9997F, new CubeDeformation(0.0F))
            .texOffs(196, 230).addBox(11.415F, -7.57F, -26.5194F, 12.8728F, 7.57F, 17.3359F, new CubeDeformation(0.0F))
            .texOffs(258, 230).addBox(-26.0F, -4.0F, -26.0F, 52.0F, 4.0F, 52.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition trunk1 = willow.addOrReplaceChild("trunk1", CubeListBuilder.create()
            .texOffs(0, 286).addBox(-31.1802F, -18.0F, -28.9921F, 60.0F, 18.0F, 60.0F, new CubeDeformation(0.0F))
            .texOffs(240, 286).addBox(24.0F, -11.0F, -7.599F, 8.0F, 7.0F, 10.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.0233F, 0.0F));
        PartDefinition trunk2 = trunk1.addOrReplaceChild("trunk2", CubeListBuilder.create()
            .texOffs(276, 286).addBox(-26.8415F, -17.0F, -27.2453F, 54.0F, 17.0F, 54.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -18.0F, 0.0F, 0.0F, 0.0126F, 0.0F));
        PartDefinition trunk3 = trunk2.addOrReplaceChild("trunk3", CubeListBuilder.create()
            .texOffs(0, 364).addBox(-24.8659F, -16.0F, -22.552F, 48.0F, 16.0F, 48.0F, new CubeDeformation(0.0F))
            .texOffs(192, 364).addBox(-26.0F, -11.0F, -2.1395F, 8.0F, 7.0F, 10.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -17.0F, 0.0F, 0.0F, 0.0437F, 0.0F));
        PartDefinition trunk4 = trunk3.addOrReplaceChild("trunk4", CubeListBuilder.create()
            .texOffs(228, 364).addBox(-21.2741F, -15.0F, -21.5847F, 42.0F, 15.0F, 42.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -16.0F, 0.0F, 0.0F, 0.0272F, 0.0F));
        PartDefinition trunk5 = trunk4.addOrReplaceChild("trunk5", CubeListBuilder.create()
            .texOffs(0, 428).addBox(-16.6245F, -14.0F, -18.7966F, 36.0F, 14.0F, 36.0F, new CubeDeformation(0.0F))
            .texOffs(144, 428).addBox(12.0F, -11.0F, -6.8904F, 8.0F, 7.0F, 10.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -15.0F, 0.0F, 0.0F, -0.0346F, 0.0F));
        PartDefinition trunk6 = trunk5.addOrReplaceChild("trunk6", CubeListBuilder.create()
            .texOffs(180, 428).addBox(-16.5385F, -13.0F, -17.2713F, 32.0F, 13.0F, 32.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -14.0F, 0.0F, 0.0F, 0.0244F, 0.0F));
        PartDefinition trunk7 = trunk6.addOrReplaceChild("trunk7", CubeListBuilder.create()
            .texOffs(308, 428).addBox(-13.7086F, -12.0F, -13.8554F, 28.0F, 12.0F, 28.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -13.0F, 0.0F, 0.0F, -0.0012F, 0.0F));
        PartDefinition trunk8 = trunk7.addOrReplaceChild("trunk8", CubeListBuilder.create()
            .texOffs(0, 478).addBox(-10.9401F, -11.0F, -11.7587F, 24.0F, 11.0F, 24.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -12.0F, 0.0F, 0.0F, -0.0445F, 0.0F));
        PartDefinition crown = trunk8.addOrReplaceChild("crown", CubeListBuilder.create()
            .texOffs(364, 95).addBox(-1.9148F, -28.619F, -5.4678F, 26.9996F, 24.1335F, 20.7317F, new CubeDeformation(0.0F))
            .texOffs(0, 141).addBox(-3.8675F, -31.9757F, 11.2251F, 19.9129F, 20.8687F, 21.0101F, new CubeDeformation(0.0F))
            .texOffs(84, 141).addBox(-24.0145F, -25.5304F, 4.7835F, 22.7762F, 14.4262F, 25.4784F, new CubeDeformation(0.0F))
            .texOffs(182, 141).addBox(-27.4792F, -32.6397F, -9.1052F, 18.4851F, 18.5098F, 19.9916F, new CubeDeformation(0.0F))
            .texOffs(260, 141).addBox(-24.0091F, -30.674F, -22.9249F, 26.8035F, 16.1777F, 21.7589F, new CubeDeformation(0.0F))
            .texOffs(358, 141).addBox(-6.2663F, -22.8203F, -28.2542F, 19.1746F, 20.193F, 19.073F, new CubeDeformation(0.0F))
            .texOffs(0, 184).addBox(4.9992F, -39.244F, -19.1541F, 21.249F, 23.1192F, 21.335F, new CubeDeformation(0.0F))
            .texOffs(0, 95).addBox(4.3476F, -30.9897F, 1.2963F, 30.0F, 4.0F, 30.0F, new CubeDeformation(0.0F))
            .texOffs(0, 95).addBox(-5.0938F, -27.2567F, 4.1168F, 30.0F, 4.0F, 30.0F, new CubeDeformation(0.0F))
            .texOffs(120, 95).addBox(-13.4253F, -55.9809F, 3.3945F, 30.0F, 4.0F, 30.0F, new CubeDeformation(0.0F))
            .texOffs(120, 95).addBox(-22.1185F, -20.7478F, 7.4307F, 30.0F, 4.0F, 30.0F, new CubeDeformation(0.0F))
            .texOffs(0, 95).addBox(-25.9237F, -25.1437F, -2.031F, 30.0F, 4.0F, 30.0F, new CubeDeformation(0.0F))
            .texOffs(0, 95).addBox(-37.1087F, -51.7261F, -3.5434F, 30.0F, 4.0F, 30.0F, new CubeDeformation(0.0F))
            .texOffs(0, 95).addBox(-29.843F, -36.7033F, -13.7293F, 30.0F, 4.0F, 30.0F, new CubeDeformation(0.0F))
            .texOffs(0, 95).addBox(-37.2016F, -34.4022F, -22.0458F, 30.0F, 4.0F, 30.0F, new CubeDeformation(0.0F))
            .texOffs(0, 95).addBox(-33.648F, -37.3503F, -30.707F, 30.0F, 4.0F, 30.0F, new CubeDeformation(0.0F))
            .texOffs(0, 95).addBox(-26.4055F, -57.4269F, -37.0101F, 30.0F, 4.0F, 30.0F, new CubeDeformation(0.0F))
            .texOffs(120, 95).addBox(-17.0764F, -35.9574F, -39.2552F, 30.0F, 4.0F, 30.0F, new CubeDeformation(0.0F))
            .texOffs(0, 95).addBox(-9.879F, -47.2125F, -31.1365F, 30.0F, 4.0F, 30.0F, new CubeDeformation(0.0F))
            .texOffs(120, 95).addBox(6.8869F, -43.8421F, -40.9851F, 30.0F, 4.0F, 30.0F, new CubeDeformation(0.0F))
            .texOffs(242, 95).addBox(15.4782F, -38.9323F, -30.7936F, 30.0F, 4.0F, 30.0F, new CubeDeformation(0.0F))
            .texOffs(0, 95).addBox(12.8237F, -32.1701F, -17.3819F, 30.0F, 4.0F, 30.0F, new CubeDeformation(0.0F))
            .texOffs(0, 95).addBox(18.16F, -43.2006F, -4.4765F, 30.0F, 4.0F, 30.0F, new CubeDeformation(0.0F))
            .texOffs(362, 51).addBox(-16.0F, -64.0F, -16.0F, 32.0F, 12.0F, 32.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -11.0F, 0.0F));
        PartDefinition branch2_s1 = trunk7.addOrReplaceChild("branch2_s1", CubeListBuilder.create()
            .texOffs(336, 0).addBox(-9.0F, -9.0F, 0.0F, 18.0F, 18.0F, 33.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -4.0F, 12.0F, 0.25F, -4.7124F, 0.0F));
        PartDefinition branch2_s2 = branch2_s1.addOrReplaceChild("branch2_s2", CubeListBuilder.create()
            .texOffs(0, 51).addBox(-7.5F, -7.5F, 0.0F, 15.0F, 15.0F, 29.0F, new CubeDeformation(0.0F))
            .texOffs(0, 0).addBox(-12.0F, -5.0F, 6.85F, 24.0F, 4.0F, 24.0F, new CubeDeformation(0.0F))
            .texOffs(278, 51).addBox(-7.5F, 0.0F, 7.2F, 3.0F, 18.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(290, 51).addBox(3.5F, 0.0F, 14.45F, 3.0F, 23.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(302, 51).addBox(-1.5F, 0.0F, 21.7F, 3.0F, 20.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 33.0F, -0.15F, 0.0F, 0.0F));
        PartDefinition branch2_s3 = branch2_s2.addOrReplaceChild("branch2_s3", CubeListBuilder.create()
            .texOffs(88, 51).addBox(-6.0F, -6.0F, 0.0F, 12.0F, 12.0F, 25.0F, new CubeDeformation(0.0F))
            .texOffs(96, 0).addBox(-11.0F, -5.0F, 5.25F, 22.0F, 4.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-6.5F, 0.0F, 6.0F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(302, 51).addBox(2.5F, 0.0F, 12.25F, 3.0F, 20.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(278, 51).addBox(-1.5F, 0.0F, 18.5F, 3.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 29.0F, -0.2F, 0.0F, 0.0F));
        PartDefinition branch2_s4 = branch2_s3.addOrReplaceChild("branch2_s4", CubeListBuilder.create()
            .texOffs(162, 51).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 10.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(184, 0).addBox(-10.0F, -5.0F, 4.3F, 20.0F, 4.0F, 20.0F, new CubeDeformation(0.0F))
            .texOffs(326, 51).addBox(-5.5F, 0.0F, 5.1F, 3.0F, 14.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(338, 51).addBox(1.5F, 0.0F, 10.6F, 3.0F, 17.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-1.5F, 0.0F, 16.1F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 25.0F, -0.3F, 0.0F, 0.0F));
        PartDefinition branch2_s5 = branch2_s4.addOrReplaceChild("branch2_s5", CubeListBuilder.create()
            .texOffs(226, 51).addBox(-4.0F, -4.0F, 0.0F, 8.0F, 8.0F, 18.0F, new CubeDeformation(0.0F))
            .texOffs(264, 0).addBox(-9.0F, -5.0F, 2.7F, 18.0F, 4.0F, 18.0F, new CubeDeformation(0.0F))
            .texOffs(350, 51).addBox(-4.5F, 0.0F, 3.9F, 3.0F, 12.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(326, 51).addBox(0.5F, 0.0F, 8.4F, 3.0F, 14.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(326, 51).addBox(-1.5F, 0.0F, 12.9F, 3.0F, 14.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.45F, 0.0F, 0.0F));
        PartDefinition branch4_s1 = trunk7.addOrReplaceChild("branch4_s1", CubeListBuilder.create()
            .texOffs(336, 0).addBox(-9.0F, -9.0F, 0.0F, 18.0F, 18.0F, 33.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -4.0F, 12.0F, 0.25F, -7.854F, 0.0F));
        PartDefinition branch4_s2 = branch4_s1.addOrReplaceChild("branch4_s2", CubeListBuilder.create()
            .texOffs(0, 51).addBox(-7.5F, -7.5F, 0.0F, 15.0F, 15.0F, 29.0F, new CubeDeformation(0.0F))
            .texOffs(0, 0).addBox(-12.0F, -5.0F, 6.85F, 24.0F, 4.0F, 24.0F, new CubeDeformation(0.0F))
            .texOffs(278, 51).addBox(-7.5F, 0.0F, 7.2F, 3.0F, 18.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(290, 51).addBox(3.5F, 0.0F, 14.45F, 3.0F, 23.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(302, 51).addBox(-1.5F, 0.0F, 21.7F, 3.0F, 20.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 33.0F, -0.15F, 0.0F, 0.0F));
        PartDefinition branch4_s3 = branch4_s2.addOrReplaceChild("branch4_s3", CubeListBuilder.create()
            .texOffs(88, 51).addBox(-6.0F, -6.0F, 0.0F, 12.0F, 12.0F, 25.0F, new CubeDeformation(0.0F))
            .texOffs(96, 0).addBox(-11.0F, -5.0F, 5.25F, 22.0F, 4.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-6.5F, 0.0F, 6.0F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(302, 51).addBox(2.5F, 0.0F, 12.25F, 3.0F, 20.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(278, 51).addBox(-1.5F, 0.0F, 18.5F, 3.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 29.0F, -0.2F, 0.0F, 0.0F));
        PartDefinition branch4_s4 = branch4_s3.addOrReplaceChild("branch4_s4", CubeListBuilder.create()
            .texOffs(162, 51).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 10.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(184, 0).addBox(-10.0F, -5.0F, 4.3F, 20.0F, 4.0F, 20.0F, new CubeDeformation(0.0F))
            .texOffs(326, 51).addBox(-5.5F, 0.0F, 5.1F, 3.0F, 14.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(338, 51).addBox(1.5F, 0.0F, 10.6F, 3.0F, 17.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-1.5F, 0.0F, 16.1F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 25.0F, -0.3F, 0.0F, 0.0F));
        PartDefinition branch4_s5 = branch4_s4.addOrReplaceChild("branch4_s5", CubeListBuilder.create()
            .texOffs(226, 51).addBox(-4.0F, -4.0F, 0.0F, 8.0F, 8.0F, 18.0F, new CubeDeformation(0.0F))
            .texOffs(264, 0).addBox(-9.0F, -5.0F, 2.7F, 18.0F, 4.0F, 18.0F, new CubeDeformation(0.0F))
            .texOffs(350, 51).addBox(-4.5F, 0.0F, 3.9F, 3.0F, 12.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(326, 51).addBox(0.5F, 0.0F, 8.4F, 3.0F, 14.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(326, 51).addBox(-1.5F, 0.0F, 12.9F, 3.0F, 14.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.45F, 0.0F, 0.0F));
        PartDefinition branch1_s1 = trunk5.addOrReplaceChild("branch1_s1", CubeListBuilder.create()
            .texOffs(336, 0).addBox(-9.0F, -9.0F, 0.0F, 18.0F, 18.0F, 33.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -6.0F, 16.0F, 0.25F, -3.1416F, 0.0F));
        PartDefinition branch1_s2 = branch1_s1.addOrReplaceChild("branch1_s2", CubeListBuilder.create()
            .texOffs(0, 51).addBox(-7.5F, -7.5F, 0.0F, 15.0F, 15.0F, 29.0F, new CubeDeformation(0.0F))
            .texOffs(0, 0).addBox(-12.0F, -5.0F, 6.85F, 24.0F, 4.0F, 24.0F, new CubeDeformation(0.0F))
            .texOffs(278, 51).addBox(-7.5F, 0.0F, 7.2F, 3.0F, 18.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(290, 51).addBox(3.5F, 0.0F, 14.45F, 3.0F, 23.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(302, 51).addBox(-1.5F, 0.0F, 21.7F, 3.0F, 20.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 33.0F, -0.15F, 0.0F, 0.0F));
        PartDefinition branch1_s3 = branch1_s2.addOrReplaceChild("branch1_s3", CubeListBuilder.create()
            .texOffs(88, 51).addBox(-6.0F, -6.0F, 0.0F, 12.0F, 12.0F, 25.0F, new CubeDeformation(0.0F))
            .texOffs(96, 0).addBox(-11.0F, -5.0F, 5.25F, 22.0F, 4.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-6.5F, 0.0F, 6.0F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(302, 51).addBox(2.5F, 0.0F, 12.25F, 3.0F, 20.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(278, 51).addBox(-1.5F, 0.0F, 18.5F, 3.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 29.0F, -0.2F, 0.0F, 0.0F));
        PartDefinition branch1_s4 = branch1_s3.addOrReplaceChild("branch1_s4", CubeListBuilder.create()
            .texOffs(162, 51).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 10.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(184, 0).addBox(-10.0F, -5.0F, 4.3F, 20.0F, 4.0F, 20.0F, new CubeDeformation(0.0F))
            .texOffs(326, 51).addBox(-5.5F, 0.0F, 5.1F, 3.0F, 14.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(338, 51).addBox(1.5F, 0.0F, 10.6F, 3.0F, 17.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-1.5F, 0.0F, 16.1F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 25.0F, -0.3F, 0.0F, 0.0F));
        PartDefinition branch1_s5 = branch1_s4.addOrReplaceChild("branch1_s5", CubeListBuilder.create()
            .texOffs(226, 51).addBox(-4.0F, -4.0F, 0.0F, 8.0F, 8.0F, 18.0F, new CubeDeformation(0.0F))
            .texOffs(264, 0).addBox(-9.0F, -5.0F, 2.7F, 18.0F, 4.0F, 18.0F, new CubeDeformation(0.0F))
            .texOffs(350, 51).addBox(-4.5F, 0.0F, 3.9F, 3.0F, 12.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(326, 51).addBox(0.5F, 0.0F, 8.4F, 3.0F, 14.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(326, 51).addBox(-1.5F, 0.0F, 12.9F, 3.0F, 14.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.45F, 0.0F, 0.0F));
        PartDefinition branch3_s1 = trunk5.addOrReplaceChild("branch3_s1", CubeListBuilder.create()
            .texOffs(336, 0).addBox(-9.0F, -9.0F, 0.0F, 18.0F, 18.0F, 33.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -6.0F, 16.0F, 0.25F, -6.2832F, 0.0F));
        PartDefinition branch3_s2 = branch3_s1.addOrReplaceChild("branch3_s2", CubeListBuilder.create()
            .texOffs(0, 51).addBox(-7.5F, -7.5F, 0.0F, 15.0F, 15.0F, 29.0F, new CubeDeformation(0.0F))
            .texOffs(0, 0).addBox(-12.0F, -5.0F, 6.85F, 24.0F, 4.0F, 24.0F, new CubeDeformation(0.0F))
            .texOffs(278, 51).addBox(-7.5F, 0.0F, 7.2F, 3.0F, 18.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(290, 51).addBox(3.5F, 0.0F, 14.45F, 3.0F, 23.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(302, 51).addBox(-1.5F, 0.0F, 21.7F, 3.0F, 20.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 33.0F, -0.15F, 0.0F, 0.0F));
        PartDefinition branch3_s3 = branch3_s2.addOrReplaceChild("branch3_s3", CubeListBuilder.create()
            .texOffs(88, 51).addBox(-6.0F, -6.0F, 0.0F, 12.0F, 12.0F, 25.0F, new CubeDeformation(0.0F))
            .texOffs(96, 0).addBox(-11.0F, -5.0F, 5.25F, 22.0F, 4.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-6.5F, 0.0F, 6.0F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(302, 51).addBox(2.5F, 0.0F, 12.25F, 3.0F, 20.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(278, 51).addBox(-1.5F, 0.0F, 18.5F, 3.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 29.0F, -0.2F, 0.0F, 0.0F));
        PartDefinition branch3_s4 = branch3_s3.addOrReplaceChild("branch3_s4", CubeListBuilder.create()
            .texOffs(162, 51).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 10.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(184, 0).addBox(-10.0F, -5.0F, 4.3F, 20.0F, 4.0F, 20.0F, new CubeDeformation(0.0F))
            .texOffs(326, 51).addBox(-5.5F, 0.0F, 5.1F, 3.0F, 14.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(338, 51).addBox(1.5F, 0.0F, 10.6F, 3.0F, 17.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-1.5F, 0.0F, 16.1F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 25.0F, -0.3F, 0.0F, 0.0F));
        PartDefinition branch3_s5 = branch3_s4.addOrReplaceChild("branch3_s5", CubeListBuilder.create()
            .texOffs(226, 51).addBox(-4.0F, -4.0F, 0.0F, 8.0F, 8.0F, 18.0F, new CubeDeformation(0.0F))
            .texOffs(264, 0).addBox(-9.0F, -5.0F, 2.7F, 18.0F, 4.0F, 18.0F, new CubeDeformation(0.0F))
            .texOffs(350, 51).addBox(-4.5F, 0.0F, 3.9F, 3.0F, 12.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(326, 51).addBox(0.5F, 0.0F, 8.4F, 3.0F, 14.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(326, 51).addBox(-1.5F, 0.0F, 12.9F, 3.0F, 14.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.45F, 0.0F, 0.0F));
        PartDefinition maw = trunk3.addOrReplaceChild("maw", CubeListBuilder.create()
            .texOffs(168, 184).addBox(-8.0F, -10.0F, -4.0F, 16.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
            .texOffs(88, 184).addBox(-7.0F, -6.0F, -1.0F, 14.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -6.0F, -24.0F));
        PartDefinition maw_jaw = maw.addOrReplaceChild("maw_jaw", CubeListBuilder.create()
            .texOffs(124, 184).addBox(-8.0F, -1.0F, -4.0F, 16.0F, 5.0F, 6.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 2.0F, 0.0F));

        return LayerDefinition.create(mesh, 512, 1024);
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

        lean(entity, state, active, ageInTicks);

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

    /**
     * The trunk commits to the swing.
     *
     * <p>This is most of what separates a tree that is animating from a tree that is attacking. A
     * nine-block trunk that stays bolt upright while a branch flails reads as a prop with a moving
     * part; leaning the whole mass into the blow and letting it rock back afterwards is what sells
     * the weight. The lean is applied to the master bone so the entire crown, every branch and every
     * hanging withe inherits it for free.
     */
    private void lean(WhompingWillowEntity entity, AttackState state, int active, float age) {
        float amount = 0.0F;
        if (state == AttackState.SWEEP) {
            float p = entity.stateProgress(WhompingWillowEntity.SWEEP_DURATION);
            amount = arc(p, 0.55F) * 0.13F;
        } else if (state == AttackState.SLAM) {
            float p = entity.stateProgress(WhompingWillowEntity.SLAM_DURATION);
            amount = arc(p, SLAM_PEAK) * 0.20F;
        } else if (state == AttackState.ROOT_STOMP) {
            float p = entity.stateProgress(WhompingWillowEntity.STOMP_DURATION);
            // Rears back, then drives straight down: the lean inverts at the strike.
            amount = -arc(p, STOMP_PEAK) * 0.16F;
        } else if (state == AttackState.SWAT) {
            float p = entity.stateProgress(WhompingWillowEntity.SWAT_DURATION);
            amount = -arc(p, SWAT_PEAK) * 0.15F;
        } else if (state == AttackState.STAGGER) {
            float p = entity.stateProgress(WhompingWillowEntity.STAGGER_DURATION);
            // Rocking, decaying -- the recoil of having been hit hard enough to interrupt it.
            this.willow.zRot += Mth.sin(p * 22.0F) * (1.0F - p) * 0.16F;
            return;
        }
        if (amount == 0.0F) {
            return;
        }
        // Lean along the active branch's own axis: branches 0 and 2 lie on one axis, 1 and 3 the other.
        if (active % 2 == 0) {
            this.willow.xRot += (active == 0 ? amount : -amount);
        } else {
            this.willow.zRot += (active == 1 ? amount : -amount);
        }
    }

    private static final float SLAM_PEAK =
            WhompingWillowEntity.SLAM_STRIKE_TICK / (float) WhompingWillowEntity.SLAM_DURATION;
    private static final float STOMP_PEAK =
            WhompingWillowEntity.STOMP_STRIKE_TICK / (float) WhompingWillowEntity.STOMP_DURATION;
    private static final float SWAT_PEAK =
            WhompingWillowEntity.SWAT_STRIKE_TICK / (float) WhompingWillowEntity.SWAT_DURATION;

    /** Trunk sway. Each ring adds a little, and because the rings are a nested chain the crown moves
     *  far more than the base -- which is how a real tree behaves and why the trunk is eight bones. */
    private void sway(WhompingWillowEntity entity, float age, boolean dormant) {
        float amp = dormant ? 0.012F : 0.034F;
        // Two incommensurate frequencies, so the sway never repeats on a visible beat.
        float wind = Mth.sin(age * 0.021F) * 0.7F + Mth.sin(age * 0.0083F + 2.3F) * 0.3F;
        float creak = Mth.sin(age * 0.037F + 1.1F) * 0.6F + Mth.sin(age * 0.0134F) * 0.4F;
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
        float amp = dormant ? 0.025F : 0.085F;
        float phase = b * 1.7F;
        for (int s = 0; s < SEGS; s++) {
            // Amplitude grows toward the tip and the phase lags, so the wander travels outward
            // along the branch instead of the whole thing waving as one rigid piece.
            float travel = age * 0.043F + phase - s * 0.55F;
            float grow = 0.5F + s * 0.35F;
            this.branch[b][s].yRot += Mth.sin(travel) * amp * SEG_WEIGHT[s] * grow;
            this.branch[b][s].xRot += Mth.cos(travel * 0.72F) * amp * 0.6F * grow;
            this.branch[b][s].zRot += Mth.sin(travel * 1.3F + 0.8F) * amp * 0.35F * grow;
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
                float trail = WhompingWillowEntity.branchTrail(lagged) * sign;
                // Overshoot past the follow-through, then settle. A swing that simply stops at the
                // end of its arc reads as a machine; the recoil is what makes it feel like weight.
                float recoil = lagged > 0.72F
                        ? Mth.sin((lagged - 0.72F) * 11.0F) * (1.0F - lagged) * 0.9F : 0.0F;
                this.branch[b][s].yRot += (trail + recoil) * YAW_TO_MODEL * SEG_WEIGHT[s];
                // A striking branch straightens: the droop is cancelled in proportion to reach.
                this.branch[b][s].xRot -= WhompingWillowEntity.strikeReach(lagged) * SEG_WEIGHT[s] * 1.9F;
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
