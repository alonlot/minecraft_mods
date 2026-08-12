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
            .texOffs(340, 524).addBox(-4.1828F, -7.3989F, -38.9772F, 12.2631F, 7.3989F, 12.7557F, new CubeDeformation(0.0F))
            .texOffs(64, 566).addBox(23.79F, -6.6728F, -18.8409F, 11.1017F, 6.6728F, 15.6444F, new CubeDeformation(0.0F))
            .texOffs(120, 566).addBox(-11.9212F, -9.5113F, -34.9474F, 16.5397F, 9.5113F, 11.6629F, new CubeDeformation(0.0F))
            .texOffs(178, 566).addBox(-6.5562F, -8.7437F, 15.2888F, 16.052F, 8.7437F, 13.6791F, new CubeDeformation(0.0F))
            .texOffs(240, 566).addBox(-31.3994F, -7.4179F, -18.9811F, 14.4533F, 7.4179F, 14.1041F, new CubeDeformation(0.0F))
            .texOffs(300, 566).addBox(-5.9421F, -9.1201F, -39.646F, 16.318F, 9.1201F, 16.713F, new CubeDeformation(0.0F))
            .texOffs(368, 566).addBox(-24.18F, -6.893F, -37.8643F, 13.7911F, 6.893F, 15.333F, new CubeDeformation(0.0F))
            .texOffs(428, 566).addBox(-39.1098F, -7.084F, -13.7757F, 11.1637F, 7.084F, 17.1145F, new CubeDeformation(0.0F))
            .texOffs(0, 593).addBox(15.3685F, -5.7961F, -5.6622F, 17.8967F, 5.7961F, 15.1981F, new CubeDeformation(0.0F))
            .texOffs(392, 524).addBox(-29.2788F, -5.3664F, 18.7071F, 16.5376F, 5.3664F, 17.4707F, new CubeDeformation(0.0F))
            .texOffs(0, 566).addBox(-22.7707F, -7.6279F, -32.134F, 17.3688F, 7.6279F, 13.6728F, new CubeDeformation(0.0F))
            .texOffs(220, 483).addBox(-27.4815F, -19.1247F, -16.5226F, 20.1845F, 19.1247F, 17.5268F, new CubeDeformation(0.0F))
            .texOffs(298, 483).addBox(-6.2606F, -14.6348F, 3.5546F, 24.7111F, 14.6348F, 18.1868F, new CubeDeformation(0.0F))
            .texOffs(386, 483).addBox(-20.1645F, -20.0079F, -24.028F, 20.9603F, 20.0079F, 19.2384F, new CubeDeformation(0.0F))
            .texOffs(0, 524).addBox(6.6918F, -15.2978F, -7.3684F, 22.0768F, 15.2978F, 17.0213F, new CubeDeformation(0.0F))
            .texOffs(82, 524).addBox(-28.2885F, -15.0297F, -7.6867F, 25.6133F, 15.0297F, 18.6886F, new CubeDeformation(0.0F))
            .texOffs(172, 524).addBox(-1.5892F, -20.2518F, -26.6811F, 18.2272F, 20.2518F, 20.5635F, new CubeDeformation(0.0F))
            .texOffs(252, 524).addBox(-29.9359F, -19.46F, -14.1413F, 23.3538F, 19.46F, 19.0066F, new CubeDeformation(0.0F))
            .texOffs(68, 593).addBox(-30.0F, -4.0F, -30.0F, 60.0F, 4.0F, 60.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition trunk1 = willow.addOrReplaceChild("trunk1", CubeListBuilder.create()
            .texOffs(0, 657).addBox(-31.1802F, -18.0F, -28.9921F, 60.0F, 18.0F, 60.0F, new CubeDeformation(0.0F))
            .texOffs(240, 657).addBox(24.0F, -11.0F, -7.599F, 8.0F, 7.0F, 10.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.0233F, 0.0F));
        PartDefinition trunk2 = trunk1.addOrReplaceChild("trunk2", CubeListBuilder.create()
            .texOffs(276, 657).addBox(-26.8415F, -17.0F, -27.2453F, 54.0F, 17.0F, 54.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -18.0F, 0.0F, 0.0F, 0.0126F, 0.0F));
        PartDefinition trunk3 = trunk2.addOrReplaceChild("trunk3", CubeListBuilder.create()
            .texOffs(0, 735).addBox(-24.8659F, -16.0F, -22.552F, 48.0F, 16.0F, 48.0F, new CubeDeformation(0.0F))
            .texOffs(192, 735).addBox(-26.0F, -11.0F, -2.1395F, 8.0F, 7.0F, 10.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -17.0F, 0.0F, 0.0F, 0.0437F, 0.0F));
        PartDefinition trunk4 = trunk3.addOrReplaceChild("trunk4", CubeListBuilder.create()
            .texOffs(228, 735).addBox(-21.2741F, -15.0F, -21.5847F, 42.0F, 15.0F, 42.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -16.0F, 0.0F, 0.0F, 0.0272F, 0.0F));
        PartDefinition trunk5 = trunk4.addOrReplaceChild("trunk5", CubeListBuilder.create()
            .texOffs(0, 799).addBox(-16.6245F, -14.0F, -18.7966F, 36.0F, 14.0F, 36.0F, new CubeDeformation(0.0F))
            .texOffs(144, 799).addBox(12.0F, -11.0F, -6.8904F, 8.0F, 7.0F, 10.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -15.0F, 0.0F, 0.0F, -0.0346F, 0.0F));
        PartDefinition trunk6 = trunk5.addOrReplaceChild("trunk6", CubeListBuilder.create()
            .texOffs(180, 799).addBox(-16.5385F, -13.0F, -17.2713F, 32.0F, 13.0F, 32.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -14.0F, 0.0F, 0.0F, 0.0244F, 0.0F));
        PartDefinition trunk7 = trunk6.addOrReplaceChild("trunk7", CubeListBuilder.create()
            .texOffs(308, 799).addBox(-13.7086F, -12.0F, -13.8554F, 28.0F, 12.0F, 28.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -13.0F, 0.0F, 0.0F, -0.0012F, 0.0F));
        PartDefinition trunk8 = trunk7.addOrReplaceChild("trunk8", CubeListBuilder.create()
            .texOffs(0, 849).addBox(-10.9401F, -11.0F, -11.7587F, 24.0F, 11.0F, 24.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -12.0F, 0.0F, 0.0F, -0.0445F, 0.0F));
        PartDefinition crown = trunk8.addOrReplaceChild("crown", CubeListBuilder.create()
            .texOffs(416, 372).addBox(-3.1727F, -30.8F, -0.034F, 21.1997F, 27.8669F, 16.1853F, new CubeDeformation(0.0F))
            .texOffs(0, 417).addBox(1.7873F, -32.2189F, -10.407F, 15.5303F, 24.1357F, 16.4081F, new CubeDeformation(0.0F))
            .texOffs(66, 417).addBox(-2.317F, -24.8538F, -15.9411F, 17.821F, 16.7727F, 19.9828F, new CubeDeformation(0.0F))
            .texOffs(142, 417).addBox(-16.6471F, -31.8742F, -15.1166F, 14.3881F, 21.4398F, 15.5933F, new CubeDeformation(0.0F))
            .texOffs(204, 417).addBox(-21.5181F, -29.4938F, -2.5338F, 21.0428F, 18.7745F, 17.0071F, new CubeDeformation(0.0F))
            .texOffs(284, 200).addBox(10.861F, -17.769F, -4.27F, 24.1149F, 5.0F, 20.5734F, new CubeDeformation(0.0F))
            .texOffs(212, 267).addBox(2.4309F, -14.326F, 10.5698F, 23.0071F, 5.0F, 29.6278F, new CubeDeformation(0.0F))
            .texOffs(320, 337).addBox(-19.89F, -16.6412F, 14.242F, 22.2522F, 5.0F, 23.3976F, new CubeDeformation(0.0F))
            .texOffs(0, 372).addBox(-35.0832F, -13.7626F, -8.8467F, 21.3404F, 5.0F, 29.2358F, new CubeDeformation(0.0F))
            .texOffs(104, 372).addBox(-32.814F, -18.1424F, -22.955F, 27.4879F, 5.0F, 24.275F, new CubeDeformation(0.0F))
            .texOffs(210, 372).addBox(-11.258F, -15.4364F, -40.2191F, 23.7804F, 5.0F, 26.2904F, new CubeDeformation(0.0F))
            .texOffs(110, 302).addBox(2.4228F, -19.6152F, -33.434F, 26.7959F, 5.0F, 28.8096F, new CubeDeformation(0.0F))
            .texOffs(312, 372).addBox(-3.3343F, -31.7219F, 3.7301F, 28.4698F, 5.0F, 22.7202F, new CubeDeformation(0.0F))
            .texOffs(222, 302).addBox(-18.5541F, -26.57F, 17.14F, 20.5734F, 5.0F, 27.4064F, new CubeDeformation(0.0F))
            .texOffs(376, 200).addBox(-36.4306F, -31.1755F, 0.7885F, 29.6278F, 5.0F, 26.7366F, new CubeDeformation(0.0F))
            .texOffs(0, 235).addBox(-28.1101F, -30.1611F, -26.2228F, 23.3976F, 5.0F, 24.5084F, new CubeDeformation(0.0F))
            .texOffs(98, 235).addBox(-13.4281F, -26.9459F, -39.8472F, 29.2358F, 5.0F, 20.213F, new CubeDeformation(0.0F))
            .texOffs(284, 200).addBox(5.1484F, -31.9494F, -27.1453F, 24.275F, 5.0F, 20.1784F, new CubeDeformation(0.0F))
            .texOffs(200, 235).addBox(15.5904F, -31.074F, -2.7648F, 26.2904F, 5.0F, 21.7388F, new CubeDeformation(0.0F))
            .texOffs(298, 235).addBox(-14.478F, -36.5583F, 9.2447F, 20.7442F, 5.0F, 26.6688F, new CubeDeformation(0.0F))
            .texOffs(394, 235).addBox(-26.1962F, -38.502F, 0.2981F, 22.7202F, 5.0F, 20.9014F, new CubeDeformation(0.0F))
            .texOffs(0, 267).addBox(-33.0802F, -38.8804F, -25.1043F, 27.4064F, 5.0F, 28.2673F, new CubeDeformation(0.0F))
            .texOffs(200, 235).addBox(-14.1921F, -38.105F, -25.4494F, 26.7366F, 5.0F, 21.0182F, new CubeDeformation(0.0F))
            .texOffs(114, 267).addBox(7.1428F, -37.5766F, -16.0065F, 24.5084F, 5.0F, 23.3529F, new CubeDeformation(0.0F))
            .texOffs(320, 267).addBox(2.6424F, -36.9382F, -9.5003F, 20.213F, 5.0F, 29.3968F, new CubeDeformation(0.0F))
            .texOffs(0, 302).addBox(-21.8722F, -50.1129F, -4.4945F, 24.9563F, 5.0F, 29.8053F, new CubeDeformation(0.0F))
            .texOffs(110, 302).addBox(-22.3809F, -48.894F, -18.7596F, 26.6688F, 5.0F, 28.6244F, new CubeDeformation(0.0F))
            .texOffs(222, 302).addBox(-2.5706F, -51.9289F, -32.7833F, 20.9014F, 5.0F, 27.6277F, new CubeDeformation(0.0F))
            .texOffs(320, 302).addBox(-2.3071F, -46.4014F, -13.1858F, 28.2673F, 5.0F, 21.8518F, new CubeDeformation(0.0F))
            .texOffs(422, 302).addBox(-3.5488F, -52.7395F, 7.3907F, 21.0182F, 5.0F, 21.9483F, new CubeDeformation(0.0F))
            .texOffs(0, 337).addBox(-22.7166F, -61.1668F, -11.7325F, 20.1154F, 5.0F, 25.7243F, new CubeDeformation(0.0F))
            .texOffs(94, 337).addBox(-16.5649F, -58.2819F, -24.1756F, 29.8053F, 5.0F, 29.8379F, new CubeDeformation(0.0F))
            .texOffs(214, 337).addBox(-7.4555F, -55.5209F, -12.6463F, 28.6244F, 5.0F, 23.5132F, new CubeDeformation(0.0F))
            .texOffs(0, 267).addBox(-11.8222F, -56.9042F, -5.3005F, 27.6277F, 5.0F, 28.2617F, new CubeDeformation(0.0F))
            .texOffs(196, 200).addBox(-11.0F, -64.0F, -11.0F, 22.0F, 10.0F, 22.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -11.0F, 0.0F));
        PartDefinition branch4_s1 = trunk7.addOrReplaceChild("branch4_s1", CubeListBuilder.create()
            .texOffs(306, 151).addBox(-8.28F, -8.28F, 0.0F, 16.56F, 16.56F, 30.36F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -5.0F, 12.0F, 0.1763F, -7.9238F, 0.0175F));
        PartDefinition branch4_s2 = branch4_s1.addOrReplaceChild("branch4_s2", CubeListBuilder.create()
            .texOffs(402, 151).addBox(-6.9F, -6.9F, 0.0F, 13.8F, 13.8F, 26.68F, new CubeDeformation(0.0F))
            .texOffs(0, 0).addBox(-12.0F, -5.0F, 5.342F, 24.0F, 4.0F, 24.0F, new CubeDeformation(0.0F))
            .texOffs(278, 51).addBox(-7.5F, 0.0F, 6.504F, 3.0F, 18.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(290, 51).addBox(3.5F, 0.0F, 13.174F, 3.0F, 23.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(182, 200).addBox(-1.5F, 0.0F, 19.844F, 3.0F, 20.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 30.36F, -0.1496F, 0.0F, -0.069F));
        PartDefinition branch4_s3 = branch4_s2.addOrReplaceChild("branch4_s3", CubeListBuilder.create()
            .texOffs(0, 200).addBox(-5.52F, -5.52F, 0.0F, 11.04F, 11.04F, 23.0F, new CubeDeformation(0.0F))
            .texOffs(96, 0).addBox(-11.0F, -5.0F, 3.95F, 22.0F, 4.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-6.5F, 0.0F, 5.4F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(302, 51).addBox(2.5F, 0.0F, 11.15F, 3.0F, 20.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(278, 51).addBox(-1.5F, 0.0F, 16.9F, 3.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 26.68F, -0.227F, 0.0F, 0.089F));
        PartDefinition branch4_s4 = branch4_s3.addOrReplaceChild("branch4_s4", CubeListBuilder.create()
            .texOffs(70, 200).addBox(-4.6F, -4.6F, 0.0F, 9.2F, 9.2F, 20.24F, new CubeDeformation(0.0F))
            .texOffs(362, 51).addBox(-10.0F, -5.0F, 3.156F, 20.0F, 4.0F, 20.0F, new CubeDeformation(0.0F))
            .texOffs(326, 51).addBox(-5.5F, 0.0F, 4.572F, 3.0F, 14.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(338, 51).addBox(1.5F, 0.0F, 9.632F, 3.0F, 17.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-1.5F, 0.0F, 14.692F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 23.0F, -0.32F, 0.0F, 0.0758F));
        PartDefinition branch4_s5 = branch4_s4.addOrReplaceChild("branch4_s5", CubeListBuilder.create()
            .texOffs(132, 200).addBox(-3.68F, -3.68F, 0.0F, 7.36F, 7.36F, 16.56F, new CubeDeformation(0.0F))
            .texOffs(266, 0).addBox(-9.0F, -5.0F, 1.764F, 18.0F, 4.0F, 18.0F, new CubeDeformation(0.0F))
            .texOffs(350, 51).addBox(-4.5F, 0.0F, 3.468F, 3.0F, 12.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(326, 51).addBox(0.5F, 0.0F, 7.608F, 3.0F, 14.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(326, 51).addBox(-1.5F, 0.0F, 11.748F, 3.0F, 14.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 20.24F, -0.4995F, 0.0F, -0.0099F));
        PartDefinition limb6 = trunk7.addOrReplaceChild("limb6", CubeListBuilder.create()
            .texOffs(448, 459).addBox(-2.86F, -2.86F, 12.0F, 5.72F, 5.72F, 13.52F, new CubeDeformation(0.0F))
            .texOffs(0, 483).addBox(-2.08F, -2.08F, 25.52F, 4.16F, 4.16F, 9.88F, new CubeDeformation(0.0F))
            .texOffs(444, 417).addBox(-8.0F, -2.0F, 24.436F, 16.0F, 3.0F, 16.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-1.5F, 0.0F, 29.948F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(132, 459).addBox(-1.3F, -1.3F, 35.4F, 2.6F, 2.6F, 6.24F, new CubeDeformation(0.0F))
            .texOffs(0, 459).addBox(-7.0F, -2.0F, 32.768F, 14.0F, 3.0F, 14.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-1.5F, 0.0F, 37.644F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -4.9143F, 0.0F, 0.3729F, -7.4176F, 0.0F));
        PartDefinition limb7 = trunk7.addOrReplaceChild("limb7", CubeListBuilder.create()
            .texOffs(258, 459).addBox(-2.64F, -2.64F, 12.0F, 5.28F, 5.28F, 12.48F, new CubeDeformation(0.0F))
            .texOffs(296, 459).addBox(-1.92F, -1.92F, 24.48F, 3.84F, 3.84F, 9.12F, new CubeDeformation(0.0F))
            .texOffs(30, 483).addBox(-8.0F, -2.0F, 22.864F, 16.0F, 3.0F, 16.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-1.5F, 0.0F, 28.452F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(324, 459).addBox(-1.2F, -1.2F, 33.6F, 2.4F, 2.4F, 5.76F, new CubeDeformation(0.0F))
            .texOffs(0, 459).addBox(-7.0F, -2.0F, 30.632F, 14.0F, 3.0F, 14.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-1.5F, 0.0F, 35.556F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -4.0692F, 0.0F, 0.4431F, -5.2011F, 0.0F));
        PartDefinition branch2_s1 = trunk6.addOrReplaceChild("branch2_s1", CubeListBuilder.create()
            .texOffs(0, 95).addBox(-7.74F, -7.74F, 0.0F, 15.48F, 15.48F, 28.38F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -3.0F, 14.0F, 0.1972F, -4.8346F, -0.0445F));
        PartDefinition branch2_s2 = branch2_s1.addOrReplaceChild("branch2_s2", CubeListBuilder.create()
            .texOffs(90, 95).addBox(-6.45F, -6.45F, 0.0F, 12.9F, 12.9F, 24.94F, new CubeDeformation(0.0F))
            .texOffs(0, 0).addBox(-12.0F, -5.0F, 4.211F, 24.0F, 4.0F, 24.0F, new CubeDeformation(0.0F))
            .texOffs(278, 51).addBox(-7.5F, 0.0F, 5.982F, 3.0F, 18.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(290, 51).addBox(3.5F, 0.0F, 12.217F, 3.0F, 23.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(302, 51).addBox(-1.5F, 0.0F, 18.452F, 3.0F, 20.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 28.38F, -0.2016F, 0.0F, -0.0126F));
        PartDefinition branch2_s3 = branch2_s2.addOrReplaceChild("branch2_s3", CubeListBuilder.create()
            .texOffs(166, 95).addBox(-5.16F, -5.16F, 0.0F, 10.32F, 10.32F, 21.5F, new CubeDeformation(0.0F))
            .texOffs(96, 0).addBox(-11.0F, -5.0F, 2.975F, 22.0F, 4.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-6.5F, 0.0F, 4.95F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(302, 51).addBox(2.5F, 0.0F, 10.325F, 3.0F, 20.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(278, 51).addBox(-1.5F, 0.0F, 15.7F, 3.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 24.94F, -0.1208F, 0.0F, 0.074F));
        PartDefinition branch2_s4 = branch2_s3.addOrReplaceChild("branch2_s4", CubeListBuilder.create()
            .texOffs(232, 95).addBox(-4.3F, -4.3F, 0.0F, 8.6F, 8.6F, 18.92F, new CubeDeformation(0.0F))
            .texOffs(362, 51).addBox(-10.0F, -5.0F, 2.298F, 20.0F, 4.0F, 20.0F, new CubeDeformation(0.0F))
            .texOffs(326, 51).addBox(-5.5F, 0.0F, 4.176F, 3.0F, 14.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(338, 51).addBox(1.5F, 0.0F, 8.906F, 3.0F, 17.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-1.5F, 0.0F, 13.636F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 21.5F, -0.3413F, 0.0F, 0.0082F));
        PartDefinition branch2_s5 = branch2_s4.addOrReplaceChild("branch2_s5", CubeListBuilder.create()
            .texOffs(288, 95).addBox(-3.44F, -3.44F, 0.0F, 6.88F, 6.88F, 15.48F, new CubeDeformation(0.0F))
            .texOffs(266, 0).addBox(-9.0F, -5.0F, 1.062F, 18.0F, 4.0F, 18.0F, new CubeDeformation(0.0F))
            .texOffs(350, 51).addBox(-4.5F, 0.0F, 3.144F, 3.0F, 12.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(326, 51).addBox(0.5F, 0.0F, 7.014F, 3.0F, 14.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(326, 51).addBox(-1.5F, 0.0F, 10.884F, 3.0F, 14.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 18.92F, -0.3936F, 0.0F, -0.0517F));
        PartDefinition limb4 = trunk6.addOrReplaceChild("limb4", CubeListBuilder.create()
            .texOffs(342, 459).addBox(-3.19F, -3.19F, 14.0F, 6.38F, 6.38F, 15.08F, new CubeDeformation(0.0F))
            .texOffs(200, 459).addBox(-2.32F, -2.32F, 29.08F, 4.64F, 4.64F, 11.02F, new CubeDeformation(0.0F))
            .texOffs(444, 417).addBox(-8.0F, -2.0F, 28.794F, 16.0F, 3.0F, 16.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-1.5F, 0.0F, 34.192F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(132, 459).addBox(-1.45F, -1.45F, 40.1F, 2.9F, 2.9F, 6.96F, new CubeDeformation(0.0F))
            .texOffs(0, 459).addBox(-7.0F, -2.0F, 37.972F, 14.0F, 3.0F, 14.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-1.5F, 0.0F, 42.776F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -9.6381F, 0.0F, 0.5387F, -5.7945F, 0.0F));
        PartDefinition limb5 = trunk6.addOrReplaceChild("limb5", CubeListBuilder.create()
            .texOffs(388, 459).addBox(-2.475F, -2.475F, 14.0F, 4.95F, 4.95F, 11.7F, new CubeDeformation(0.0F))
            .texOffs(422, 459).addBox(-1.8F, -1.8F, 25.7F, 3.6F, 3.6F, 8.55F, new CubeDeformation(0.0F))
            .texOffs(444, 417).addBox(-8.0F, -2.0F, 23.685F, 16.0F, 3.0F, 16.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-1.5F, 0.0F, 29.33F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(324, 459).addBox(-1.125F, -1.125F, 34.25F, 2.25F, 2.25F, 5.4F, new CubeDeformation(0.0F))
            .texOffs(0, 459).addBox(-7.0F, -2.0F, 31.03F, 14.0F, 3.0F, 14.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-1.5F, 0.0F, 35.99F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -8.9389F, 0.0F, 0.4632F, -3.4907F, 0.0F));
        PartDefinition branch3_s1 = trunk5.addOrReplaceChild("branch3_s1", CubeListBuilder.create()
            .texOffs(334, 95).addBox(-9.72F, -9.72F, 0.0F, 19.44F, 19.44F, 35.64F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -9.0F, 16.0F, 0.2778F, -6.4752F, -0.0293F));
        PartDefinition branch3_s2 = branch3_s1.addOrReplaceChild("branch3_s2", CubeListBuilder.create()
            .texOffs(0, 151).addBox(-8.1F, -8.1F, 0.0F, 16.2F, 16.2F, 31.32F, new CubeDeformation(0.0F))
            .texOffs(0, 0).addBox(-12.0F, -5.0F, 8.358F, 24.0F, 4.0F, 24.0F, new CubeDeformation(0.0F))
            .texOffs(278, 51).addBox(-7.5F, 0.0F, 7.896F, 3.0F, 18.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(290, 51).addBox(3.5F, 0.0F, 15.726F, 3.0F, 23.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(302, 51).addBox(-1.5F, 0.0F, 23.556F, 3.0F, 20.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 35.64F, -0.199F, 0.0F, 0.0452F));
        PartDefinition branch3_s3 = branch3_s2.addOrReplaceChild("branch3_s3", CubeListBuilder.create()
            .texOffs(98, 151).addBox(-6.48F, -6.48F, 0.0F, 12.96F, 12.96F, 27.0F, new CubeDeformation(0.0F))
            .texOffs(96, 0).addBox(-11.0F, -5.0F, 6.55F, 22.0F, 4.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-6.5F, 0.0F, 6.6F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(302, 51).addBox(2.5F, 0.0F, 13.35F, 3.0F, 20.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(278, 51).addBox(-1.5F, 0.0F, 20.1F, 3.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 31.32F, -0.2568F, 0.0F, -0.0642F));
        PartDefinition branch3_s4 = branch3_s3.addOrReplaceChild("branch3_s4", CubeListBuilder.create()
            .texOffs(178, 151).addBox(-5.4F, -5.4F, 0.0F, 10.8F, 10.8F, 23.76F, new CubeDeformation(0.0F))
            .texOffs(362, 51).addBox(-10.0F, -5.0F, 5.444F, 20.0F, 4.0F, 20.0F, new CubeDeformation(0.0F))
            .texOffs(326, 51).addBox(-5.5F, 0.0F, 5.628F, 3.0F, 14.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(338, 51).addBox(1.5F, 0.0F, 11.568F, 3.0F, 17.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-1.5F, 0.0F, 17.508F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 27.0F, -0.2818F, 0.0F, -0.0002F));
        PartDefinition branch3_s5 = branch3_s4.addOrReplaceChild("branch3_s5", CubeListBuilder.create()
            .texOffs(248, 151).addBox(-4.32F, -4.32F, 0.0F, 8.64F, 8.64F, 19.44F, new CubeDeformation(0.0F))
            .texOffs(266, 0).addBox(-9.0F, -5.0F, 3.636F, 18.0F, 4.0F, 18.0F, new CubeDeformation(0.0F))
            .texOffs(350, 51).addBox(-4.5F, 0.0F, 4.332F, 3.0F, 12.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(326, 51).addBox(0.5F, 0.0F, 9.192F, 3.0F, 14.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(326, 51).addBox(-1.5F, 0.0F, 14.052F, 3.0F, 14.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 23.76F, -0.4345F, 0.0F, -0.0087F));
        PartDefinition limb3 = trunk5.addOrReplaceChild("limb3", CubeListBuilder.create()
            .texOffs(258, 459).addBox(-2.75F, -2.75F, 16.0F, 5.5F, 5.5F, 13.0F, new CubeDeformation(0.0F))
            .texOffs(296, 459).addBox(-2.0F, -2.0F, 29.0F, 4.0F, 4.0F, 9.5F, new CubeDeformation(0.0F))
            .texOffs(444, 417).addBox(-8.0F, -2.0F, 27.65F, 16.0F, 3.0F, 16.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-1.5F, 0.0F, 33.2F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(324, 459).addBox(-1.25F, -1.25F, 38.5F, 2.5F, 2.5F, 6.0F, new CubeDeformation(0.0F))
            .texOffs(0, 459).addBox(-7.0F, -2.0F, 35.7F, 14.0F, 3.0F, 14.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-1.5F, 0.0F, 40.6F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -6.0117F, 0.0F, 0.4745F, -8.7266F, 0.0F));
        PartDefinition branch1_s1 = trunk4.addOrReplaceChild("branch1_s1", CubeListBuilder.create()
            .texOffs(338, 0).addBox(-9.0F, -9.0F, 0.0F, 18.0F, 18.0F, 33.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -7.0F, 19.0F, 0.2653F, -3.3859F, 0.014F));
        PartDefinition branch1_s2 = branch1_s1.addOrReplaceChild("branch1_s2", CubeListBuilder.create()
            .texOffs(0, 51).addBox(-7.5F, -7.5F, 0.0F, 15.0F, 15.0F, 29.0F, new CubeDeformation(0.0F))
            .texOffs(0, 0).addBox(-12.0F, -5.0F, 6.85F, 24.0F, 4.0F, 24.0F, new CubeDeformation(0.0F))
            .texOffs(278, 51).addBox(-7.5F, 0.0F, 7.2F, 3.0F, 18.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(290, 51).addBox(3.5F, 0.0F, 14.45F, 3.0F, 23.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(302, 51).addBox(-1.5F, 0.0F, 21.7F, 3.0F, 20.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 33.0F, -0.1901F, 0.0F, -0.0105F));
        PartDefinition branch1_s3 = branch1_s2.addOrReplaceChild("branch1_s3", CubeListBuilder.create()
            .texOffs(88, 51).addBox(-6.0F, -6.0F, 0.0F, 12.0F, 12.0F, 25.0F, new CubeDeformation(0.0F))
            .texOffs(96, 0).addBox(-11.0F, -5.0F, 5.25F, 22.0F, 4.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-6.5F, 0.0F, 6.0F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(302, 51).addBox(2.5F, 0.0F, 12.25F, 3.0F, 20.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(278, 51).addBox(-1.5F, 0.0F, 18.5F, 3.0F, 18.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 29.0F, -0.2964F, 0.0F, 0.0816F));
        PartDefinition branch1_s4 = branch1_s3.addOrReplaceChild("branch1_s4", CubeListBuilder.create()
            .texOffs(162, 51).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 10.0F, 22.0F, new CubeDeformation(0.0F))
            .texOffs(184, 0).addBox(-10.0F, -5.0F, 4.3F, 20.0F, 4.0F, 20.0F, new CubeDeformation(0.0F))
            .texOffs(326, 51).addBox(-5.5F, 0.0F, 5.1F, 3.0F, 14.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(338, 51).addBox(1.5F, 0.0F, 10.6F, 3.0F, 17.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-1.5F, 0.0F, 16.1F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 25.0F, -0.3575F, 0.0F, -0.0424F));
        PartDefinition branch1_s5 = branch1_s4.addOrReplaceChild("branch1_s5", CubeListBuilder.create()
            .texOffs(226, 51).addBox(-4.0F, -4.0F, 0.0F, 8.0F, 8.0F, 18.0F, new CubeDeformation(0.0F))
            .texOffs(266, 0).addBox(-9.0F, -5.0F, 2.7F, 18.0F, 4.0F, 18.0F, new CubeDeformation(0.0F))
            .texOffs(350, 51).addBox(-4.5F, 0.0F, 3.9F, 3.0F, 12.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(326, 51).addBox(0.5F, 0.0F, 8.4F, 3.0F, 14.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(326, 51).addBox(-1.5F, 0.0F, 12.9F, 3.0F, 14.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.368F, 0.0F, -0.0663F));
        PartDefinition limb2 = trunk4.addOrReplaceChild("limb2", CubeListBuilder.create()
            .texOffs(152, 459).addBox(-3.41F, -3.41F, 19.0F, 6.82F, 6.82F, 16.12F, new CubeDeformation(0.0F))
            .texOffs(200, 459).addBox(-2.48F, -2.48F, 35.12F, 4.96F, 4.96F, 11.78F, new CubeDeformation(0.0F))
            .texOffs(444, 417).addBox(-8.0F, -2.0F, 35.366F, 16.0F, 3.0F, 16.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-1.5F, 0.0F, 40.688F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(234, 459).addBox(-1.55F, -1.55F, 46.9F, 3.1F, 3.1F, 7.44F, new CubeDeformation(0.0F))
            .texOffs(0, 459).addBox(-7.0F, -2.0F, 45.108F, 14.0F, 3.0F, 14.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-1.5F, 0.0F, 49.864F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -4.6109F, 0.0F, 0.3487F, -7.1908F, 0.0F));
        PartDefinition maw = trunk3.addOrReplaceChild("maw", CubeListBuilder.create()
            .texOffs(176, 483).addBox(-8.0F, -10.0F, -4.0F, 16.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
            .texOffs(96, 483).addBox(-7.0F, -6.0F, -1.0F, 14.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -6.0F, -24.0F));
        PartDefinition maw_jaw = maw.addOrReplaceChild("maw_jaw", CubeListBuilder.create()
            .texOffs(132, 483).addBox(-8.0F, -1.0F, -4.0F, 16.0F, 5.0F, 6.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 2.0F, 0.0F));
        PartDefinition limb1 = trunk3.addOrReplaceChild("limb1", CubeListBuilder.create()
            .texOffs(56, 459).addBox(-3.025F, -3.025F, 22.0F, 6.05F, 6.05F, 14.3F, new CubeDeformation(0.0F))
            .texOffs(100, 459).addBox(-2.2F, -2.2F, 36.3F, 4.4F, 4.4F, 10.45F, new CubeDeformation(0.0F))
            .texOffs(444, 417).addBox(-8.0F, -2.0F, 35.615F, 16.0F, 3.0F, 16.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-1.5F, 0.0F, 41.07F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(132, 459).addBox(-1.375F, -1.375F, 46.75F, 2.75F, 2.75F, 6.6F, new CubeDeformation(0.0F))
            .texOffs(0, 459).addBox(-7.0F, -2.0F, 44.37F, 14.0F, 3.0F, 14.0F, new CubeDeformation(0.0F))
            .texOffs(314, 51).addBox(-1.5F, 0.0F, 49.21F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -8.9604F, 0.0F, 0.3463F, -4.1015F, 0.0F));
        PartDefinition knot = trunk2.addOrReplaceChild("knot", CubeListBuilder.create()
            .texOffs(284, 417).addBox(-7.2419F, -9.0F, -6.0297F, 14.0F, 9.0F, 14.0F, new CubeDeformation(0.0F))
            .texOffs(340, 417).addBox(-5.2096F, -7.0F, 0.8515F, 8.0F, 4.0F, 8.0F, new CubeDeformation(0.0F))
            .texOffs(372, 417).addBox(-9.2419F, 0.0F, -5.0297F, 6.0F, 4.0F, 6.0F, new CubeDeformation(0.0F))
            .texOffs(396, 417).addBox(-3.2419F, -5.0F, -2.0297F, 6.0F, 4.0F, 6.0F, new CubeDeformation(0.0F))
            .texOffs(420, 417).addBox(2.7581F, -10.0F, 0.9703F, 6.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-6.5319F, -5.0F, 26.198F));

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
