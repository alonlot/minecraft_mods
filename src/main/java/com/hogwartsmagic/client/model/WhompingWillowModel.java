package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.WhompingWillowEntity;
import com.hogwartsmagic.entity.WhompingWillowEntity.AttackState;
import com.hogwartsmagic.entity.WhompingWillowRig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The Whomping Willow.
 *
 * <p><b>Geometry between the GENERATED GEOMETRY markers is written by
 * {@code build_scripts/generate_whomping_willow_model.py} -- do not hand-edit it.</b>
 *
 * <p>This class does not decide where any branch goes. {@link WhompingWillowRig#pose} does, from the
 * same synched attack state the server reads, and the server hit-tests against the rig's forward
 * kinematics of that same pose. What this class adds is strictly cosmetic and never touches a
 * striking limb while it is committed: the streamers kept plumb under gravity and shortened where
 * they would sink into the ground, a shudder when hit, the maw, and the fall.
 */
public class WhompingWillowModel extends HierarchicalModel<WhompingWillowEntity> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "whomping_willow"), "main");

    private final ModelPart root;
    private final ModelPart[] parts = new ModelPart[WhompingWillowRig.BONES];
    private final float[] rot = new float[WhompingWillowRig.BONES * 3];
    private final double[] world = new double[WhompingWillowRig.BONES * 12];
    private final WhompingWillowRig.Input input = new WhompingWillowRig.Input();

    public WhompingWillowModel(ModelPart top) {
        for (int b = 0; b < WhompingWillowRig.BONES; b++) {
            int p = WhompingWillowRig.PARENT[b];
            this.parts[b] = (p < 0 ? top : this.parts[p]).getChild(WhompingWillowRig.NAME[b]);
        }
        this.root = this.parts[0];
    }

    // <<< GENERATED GEOMETRY
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition top = mesh.getRoot();

        PartDefinition b0 = top.addOrReplaceChild("root", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition b1 = b0.addOrReplaceChild("willow", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        b1.addOrReplaceChild("flare", CubeListBuilder.create()
                .texOffs(216, 0).addBox(-31.0F, -5.0F, -31.0F, 62.0F, 6.0F, 62.0F)
                .texOffs(494, 174).addBox(-6.3887F, -18.3632F, -34.248F, 19.0F, 19.0F, 19.0F)
                .texOffs(196, 215).addBox(-25.5505F, -17.0188F, -29.9752F, 17.0F, 18.0F, 17.0F)
                .texOffs(62, 251).addBox(-32.6552F, -15.3104F, -12.842F, 15.0F, 16.0F, 15.0F)
                .texOffs(880, 174).addBox(-28.5941F, -16.5601F, 7.7878F, 18.0F, 18.0F, 18.0F)
                .texOffs(82, 174).addBox(-10.0984F, -20.2173F, 14.7683F, 19.0F, 21.0F, 19.0F)
                .texOffs(494, 174).addBox(8.125F, -17.7069F, 7.6911F, 19.0F, 19.0F, 19.0F)
                .texOffs(300, 174).addBox(16.8265F, -19.7839F, -10.2775F, 18.0F, 21.0F, 18.0F)
                .texOffs(150, 251).addBox(10.1156F, -12.6354F, -31.1501F, 16.0F, 14.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition b3 = b1.addOrReplaceChild("root1_s1", CubeListBuilder.create()
                .texOffs(56, 215).addBox(-6.5F, -6.5F, -1.0F, 13.0F, 13.0F, 22.0F),
                PartPose.offsetAndRotation(-9.5317F, -5.0F, -19.8279F, -0.16F, 3.5897F, 0.0F));
        b3.addOrReplaceChild("root1_s2", CubeListBuilder.create()
                .texOffs(94, 282).addBox(-4.0F, -4.0F, -1.0F, 8.0F, 8.0F, 19.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 20.0F, -0.22F, 0.0F, 0.0F));
        PartDefinition b5 = b1.addOrReplaceChild("root2_s1", CubeListBuilder.create()
                .texOffs(56, 215).addBox(-6.5F, -6.5F, -1.0F, 13.0F, 13.0F, 22.0F),
                PartPose.offsetAndRotation(-21.3401F, -5.0F, -5.3479F, -0.16F, 4.4668F, 0.0F));
        b5.addOrReplaceChild("root2_s2", CubeListBuilder.create()
                .texOffs(94, 282).addBox(-4.0F, -4.0F, -1.0F, 8.0F, 8.0F, 19.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 20.0F, -0.22F, 0.0F, 0.0F));
        PartDefinition b7 = b1.addOrReplaceChild("root3_s1", CubeListBuilder.create()
                .texOffs(56, 215).addBox(-6.5F, -6.5F, -1.0F, 13.0F, 13.0F, 22.0F),
                PartPose.offsetAndRotation(-19.9884F, -5.0F, 9.1903F, -0.16F, 5.1433F, 0.0F));
        b7.addOrReplaceChild("root3_s2", CubeListBuilder.create()
                .texOffs(94, 282).addBox(-4.0F, -4.0F, -1.0F, 8.0F, 8.0F, 19.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 20.0F, -0.22F, 0.0F, 0.0F));
        PartDefinition b9 = b1.addOrReplaceChild("root4_s1", CubeListBuilder.create()
                .texOffs(56, 215).addBox(-6.5F, -6.5F, -1.0F, 13.0F, 13.0F, 22.0F),
                PartPose.offsetAndRotation(-1.5615F, -5.0F, 21.9445F, -0.16F, 6.2122F, 0.0F));
        b9.addOrReplaceChild("root4_s2", CubeListBuilder.create()
                .texOffs(94, 282).addBox(-4.0F, -4.0F, -1.0F, 8.0F, 8.0F, 19.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 20.0F, -0.22F, 0.0F, 0.0F));
        PartDefinition b11 = b1.addOrReplaceChild("root5_s1", CubeListBuilder.create()
                .texOffs(56, 215).addBox(-6.5F, -6.5F, -1.0F, 13.0F, 13.0F, 22.0F),
                PartPose.offsetAndRotation(16.1677F, -5.0F, 14.92F, -0.16F, 7.1087F, 0.0F));
        b11.addOrReplaceChild("root5_s2", CubeListBuilder.create()
                .texOffs(94, 282).addBox(-4.0F, -4.0F, -1.0F, 8.0F, 8.0F, 19.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 20.0F, -0.22F, 0.0F, 0.0F));
        PartDefinition b13 = b1.addOrReplaceChild("root6_s1", CubeListBuilder.create()
                .texOffs(56, 215).addBox(-6.5F, -6.5F, -1.0F, 13.0F, 13.0F, 22.0F),
                PartPose.offsetAndRotation(21.839F, -5.0F, -2.6564F, -0.16F, 7.975F, 0.0F));
        b13.addOrReplaceChild("root6_s2", CubeListBuilder.create()
                .texOffs(94, 282).addBox(-4.0F, -4.0F, -1.0F, 8.0F, 8.0F, 19.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 20.0F, -0.22F, 0.0F, 0.0F));
        PartDefinition b15 = b1.addOrReplaceChild("root7_s1", CubeListBuilder.create()
                .texOffs(56, 215).addBox(-6.5F, -6.5F, -1.0F, 13.0F, 13.0F, 22.0F),
                PartPose.offsetAndRotation(7.4827F, -5.0F, -20.6884F, -0.16F, 9.0777F, 0.0F));
        b15.addOrReplaceChild("root7_s2", CubeListBuilder.create()
                .texOffs(94, 282).addBox(-4.0F, -4.0F, -1.0F, 8.0F, 8.0F, 19.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 20.0F, -0.22F, 0.0F, 0.0F));
        PartDefinition b17 = b1.addOrReplaceChild("trunk1", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-27.0F, -21.0F, -27.0F, 54.0F, 22.0F, 54.0F)
                .texOffs(700, 76).addBox(26.0F, -23.0F, -15.1034F, 4.0F, 28.0F, 22.0F)
                .texOffs(246, 76).addBox(-30.0F, -23.0F, -18.3472F, 4.0F, 28.0F, 23.0F)
                .texOffs(716, 215).addBox(-4.5001F, -23.0F, 26.0F, 21.0F, 28.0F, 5.0F)
                .texOffs(768, 215).addBox(-3.6522F, -23.0F, -31.0F, 17.0F, 28.0F, 5.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition b18 = b17.addOrReplaceChild("trunk2", CubeListBuilder.create()
                .texOffs(464, 0).addBox(-24.0F, -19.0F, -24.0F, 48.0F, 20.0F, 48.0F)
                .texOffs(152, 129).addBox(23.0F, -21.0F, -8.048F, 4.0F, 26.0F, 18.0F)
                .texOffs(104, 129).addBox(-28.0F, -21.0F, -4.6323F, 5.0F, 26.0F, 19.0F)
                .texOffs(334, 251).addBox(-9.4387F, -21.0F, 23.0F, 18.0F, 26.0F, 4.0F)
                .texOffs(378, 251).addBox(-14.4562F, -21.0F, -27.0F, 17.0F, 26.0F, 4.0F)
                .texOffs(46, 310).addBox(17.6293F, -16.3F, 3.9887F, 11.0F, 11.0F, 11.0F),
                PartPose.offsetAndRotation(2.0F, -20.0F, -1.0F, 0.0F, 0.11F, 0.0F));
        PartDefinition b19 = b18.addOrReplaceChild("trunk3", CubeListBuilder.create()
                .texOffs(656, 0).addBox(-22.0F, -18.0F, -22.0F, 44.0F, 19.0F, 44.0F)
                .texOffs(850, 174).addBox(21.0F, -20.0F, 1.5012F, 3.0F, 25.0F, 12.0F)
                .texOffs(372, 174).addBox(-24.0F, -20.0F, -2.1152F, 3.0F, 25.0F, 14.0F)
                .texOffs(468, 251).addBox(-2.0151F, -20.0F, 21.0F, 17.0F, 25.0F, 4.0F)
                .texOffs(910, 251).addBox(-4.2079F, -20.0F, -24.0F, 19.0F, 25.0F, 3.0F),
                PartPose.offsetAndRotation(2.0977F, -18.0F, -0.7744F, 0.0F, 0.11F, 0.0F));
        PartDefinition b20 = b19.addOrReplaceChild("trunk4", CubeListBuilder.create()
                .texOffs(832, 0).addBox(-20.0F, -17.0F, -20.0F, 40.0F, 18.0F, 40.0F)
                .texOffs(158, 174).addBox(19.0F, -19.0F, -13.6249F, 4.0F, 24.0F, 16.0F)
                .texOffs(0, 215).addBox(-23.0F, -19.0F, -6.811F, 4.0F, 24.0F, 12.0F)
                .texOffs(954, 251).addBox(-9.2931F, -19.0F, 19.0F, 17.0F, 24.0F, 4.0F)
                .texOffs(0, 282).addBox(-2.9888F, -19.0F, -23.0F, 14.0F, 24.0F, 4.0F)
                .texOffs(458, 282).addBox(-17.5438F, -15.6F, 11.5426F, 12.0F, 12.0F, 12.0F),
                PartPose.offsetAndRotation(0.5394F, -17.0F, 2.17F, 0.0F, 0.11F, 0.0F));
        PartDefinition b21 = b20.addOrReplaceChild("trunk5", CubeListBuilder.create()
                .texOffs(0, 76).addBox(-18.0F, -16.0F, -18.0F, 36.0F, 17.0F, 36.0F)
                .texOffs(612, 215).addBox(17.0F, -18.0F, -0.9523F, 3.0F, 23.0F, 11.0F)
                .texOffs(548, 215).addBox(-22.0F, -18.0F, -8.1207F, 5.0F, 23.0F, 11.0F)
                .texOffs(320, 282).addBox(-6.1867F, -18.0F, 17.0F, 10.0F, 23.0F, 3.0F)
                .texOffs(36, 282).addBox(-11.7822F, -18.0F, -22.0F, 12.0F, 23.0F, 5.0F)
                .texOffs(740, 310).addBox(-10.6624F, -13.0F, 13.7936F, 8.0F, 8.0F, 8.0F),
                PartPose.offsetAndRotation(-1.5941F, -16.0F, 1.568F, 0.0F, 0.11F, 0.0F));
        PartDefinition b22 = b21.addOrReplaceChild("trunk6", CubeListBuilder.create()
                .texOffs(752, 76).addBox(-16.0F, -15.0F, -16.0F, 32.0F, 16.0F, 32.0F)
                .texOffs(876, 215).addBox(15.0F, -17.0F, -4.2553F, 4.0F, 22.0F, 10.0F)
                .texOffs(122, 251).addBox(-20.0F, -17.0F, -5.1107F, 5.0F, 22.0F, 9.0F)
                .texOffs(370, 282).addBox(-1.8123F, -17.0F, 15.0F, 13.0F, 22.0F, 3.0F)
                .texOffs(254, 282).addBox(-3.2451F, -17.0F, -19.0F, 13.0F, 22.0F, 4.0F),
                PartPose.offsetAndRotation(-2.6614F, -15.0F, 0.9576F, 0.0F, 0.11F, 0.0F));
        PartDefinition b23 = b22.addOrReplaceChild("bough1_s1", CubeListBuilder.create()
                .texOffs(300, 76).addBox(-7.0F, -7.0F, -1.0F, 14.0F, 14.0F, 36.0F)
                .texOffs(406, 174).addBox(-9.0396F, -18.92F, 7.7F, 22.0F, 16.0F, 22.0F),
                PartPose.offsetAndRotation(-3.122F, -12.0F, -6.2652F, 0.9816F, 3.5956F, 0.0F));
        PartDefinition b24 = b23.addOrReplaceChild("bough1_s2", CubeListBuilder.create()
                .texOffs(224, 129).addBox(-5.5F, -5.5F, -1.0F, 11.0F, 11.0F, 32.0F)
                .texOffs(506, 282).addBox(-6.5F, -7.0372F, -5.5F, 13.0F, 13.0F, 11.0F)
                .texOffs(0, 129).addBox(-11.8116F, -18.86F, 3.5F, 26.0F, 19.0F, 26.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 34.0F, -0.2389F, 0.0F, 0.0F));
        PartDefinition b25 = b24.addOrReplaceChild("bough1_s3", CubeListBuilder.create()
                .texOffs(344, 215).addBox(-4.0F, -4.0F, -1.0F, 8.0F, 8.0F, 26.0F)
                .texOffs(626, 310).addBox(-5.0F, -4.101F, -4.0F, 10.0F, 10.0F, 8.0F)
                .texOffs(0, 129).addBox(-15.724F, -17.36F, 0.2F, 26.0F, 19.0F, 26.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 30.0F, -0.3757F, 0.0F, 0.0F));
        PartDefinition b26 = b25.addOrReplaceChild("bough1_x31a", CubeListBuilder.create()
                .texOffs(90, 310).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 16.0F)
                .texOffs(510, 251).addBox(-8.0F, -9.76F, 3.2F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 10.8F, -0.7132F, -0.7882F, 0.0F));
        PartDefinition b27 = b26.addOrReplaceChild("bough1_x31b", CubeListBuilder.create()
                .texOffs(662, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 13.0F)
                .texOffs(402, 282).addBox(-7.0F, -8.04F, 0.8F, 14.0F, 10.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 16.0F, -0.7781F, 0.0F, 0.0F));
        b27.addOrReplaceChild("bough1_x31b_cur", CubeListBuilder.create()
                .texOffs(640, 215).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 34.0F, 0.0F)
                .texOffs(196, 129).addBox(0.0F, 0.0F, -7.0F, 0.0F, 30.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 13.0F, 1.3973F, -0.3918F, -0.4783F));
        b26.addOrReplaceChild("bough1_x31a_cur", CubeListBuilder.create()
                .texOffs(288, 282).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 26.0F, 0.0F)
                .texOffs(646, 174).addBox(0.0F, 0.0F, -8.0F, 0.0F, 22.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 13.6F, 2.7089F, 0.0161F, -2.865F));
        PartDefinition b30 = b25.addOrReplaceChild("bough1_x32a", CubeListBuilder.create()
                .texOffs(90, 310).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 16.0F)
                .texOffs(510, 251).addBox(-8.0F, -9.76F, 3.2F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 18.0F, -0.7049F, 1.1787F, 0.0F));
        PartDefinition b31 = b30.addOrReplaceChild("bough1_x32b", CubeListBuilder.create()
                .texOffs(662, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 13.0F)
                .texOffs(402, 282).addBox(-7.0F, -8.04F, 0.8F, 14.0F, 10.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 16.0F, -0.7262F, 0.0F, 0.0F));
        b31.addOrReplaceChild("bough1_x32b_cur", CubeListBuilder.create()
                .texOffs(640, 215).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 34.0F, 0.0F)
                .texOffs(196, 129).addBox(0.0F, 0.0F, -7.0F, 0.0F, 30.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 13.0F, 1.9603F, 0.2078F, 1.7806F));
        b30.addOrReplaceChild("bough1_x32a_cur", CubeListBuilder.create()
                .texOffs(288, 282).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 26.0F, 0.0F)
                .texOffs(646, 174).addBox(0.0F, 0.0F, -8.0F, 0.0F, 22.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 13.6F, 2.4718F, -0.6335F, -3.0963F));
        PartDefinition b34 = b24.addOrReplaceChild("bough1_x21a", CubeListBuilder.create()
                .texOffs(574, 251).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 22.0F)
                .texOffs(264, 215).addBox(-10.0F, -11.2F, 5.4F, 20.0F, 14.0F, 20.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 13.5F, -0.521F, -0.8748F, 0.0F));
        PartDefinition b35 = b34.addOrReplaceChild("bough1_x21b", CubeListBuilder.create()
                .texOffs(134, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 18.0F)
                .texOffs(510, 251).addBox(-8.0F, -8.76F, 2.8F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.8095F, 0.0F, 0.0F));
        b35.addOrReplaceChild("bough1_x21b_cur", CubeListBuilder.create()
                .texOffs(912, 76).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 46.0F, 0.0F)
                .texOffs(992, 0).addBox(0.0F, 0.0F, -7.0F, 0.0F, 42.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 18.0F, 2.4666F, 0.2013F, -2.5842F));
        b34.addOrReplaceChild("bough1_x21a_cur", CubeListBuilder.create()
                .texOffs(580, 215).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 34.0F, 0.0F)
                .texOffs(880, 76).addBox(0.0F, 0.0F, -8.0F, 0.0F, 30.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 18.7F, -0.2065F, -1.5251F, 0.752F));
        PartDefinition b38 = b24.addOrReplaceChild("bough1_x22a", CubeListBuilder.create()
                .texOffs(574, 251).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 22.0F)
                .texOffs(264, 215).addBox(-10.0F, -11.2F, 5.4F, 20.0F, 14.0F, 20.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.5F, -0.5092F, 1.1957F, 0.0F));
        PartDefinition b39 = b38.addOrReplaceChild("bough1_x22b", CubeListBuilder.create()
                .texOffs(134, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 18.0F)
                .texOffs(510, 251).addBox(-8.0F, -8.76F, 2.8F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.5514F, 0.0F, 0.0F));
        b39.addOrReplaceChild("bough1_x22b_cur", CubeListBuilder.create()
                .texOffs(912, 76).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 46.0F, 0.0F)
                .texOffs(992, 0).addBox(0.0F, 0.0F, -7.0F, 0.0F, 42.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 18.0F, 2.3804F, -0.7128F, 2.8692F));
        b38.addOrReplaceChild("bough1_x22a_cur", CubeListBuilder.create()
                .texOffs(580, 215).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 34.0F, 0.0F)
                .texOffs(880, 76).addBox(0.0F, 0.0F, -8.0F, 0.0F, 30.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 18.7F, 0.5254F, 1.2821F, -0.1818F));
        PartDefinition b42 = b23.addOrReplaceChild("bough1_x12a", CubeListBuilder.create()
                .texOffs(574, 251).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 22.0F)
                .texOffs(264, 215).addBox(-10.0F, -11.2F, 5.4F, 20.0F, 14.0F, 20.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 25.5F, -0.405F, 1.2883F, 0.0F));
        PartDefinition b43 = b42.addOrReplaceChild("bough1_x12b", CubeListBuilder.create()
                .texOffs(134, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 18.0F)
                .texOffs(510, 251).addBox(-8.0F, -8.76F, 2.8F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.8759F, 0.0F, 0.0F));
        b43.addOrReplaceChild("bough1_x12b_cur", CubeListBuilder.create()
                .texOffs(912, 76).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 46.0F, 0.0F)
                .texOffs(992, 0).addBox(0.0F, 0.0F, -7.0F, 0.0F, 42.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 18.0F, 1.3671F, -1.0746F, -2.4658F));
        b42.addOrReplaceChild("bough1_x12a_cur", CubeListBuilder.create()
                .texOffs(580, 215).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 34.0F, 0.0F)
                .texOffs(880, 76).addBox(0.0F, 0.0F, -8.0F, 0.0F, 30.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 18.7F, 3.1356F, -0.1075F, 2.2175F));
        PartDefinition b46 = b22.addOrReplaceChild("bough2_s1", CubeListBuilder.create()
                .texOffs(400, 76).addBox(-7.0F, -7.0F, -1.0F, 14.0F, 14.0F, 36.0F)
                .texOffs(0, 129).addBox(-15.377F, -20.36F, 5.7F, 26.0F, 19.0F, 26.0F),
                PartPose.offsetAndRotation(-6.3188F, -12.0F, 3.0122F, 0.9766F, 5.0548F, 0.0F));
        PartDefinition b47 = b46.addOrReplaceChild("bough2_s2", CubeListBuilder.create()
                .texOffs(310, 129).addBox(-5.5F, -5.5F, -1.0F, 11.0F, 11.0F, 32.0F)
                .texOffs(554, 282).addBox(-6.5F, -6.0394F, -5.5F, 13.0F, 13.0F, 11.0F)
                .texOffs(0, 129).addBox(-15.7185F, -18.86F, 3.5F, 26.0F, 19.0F, 26.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 34.0F, -0.2211F, 0.0F, 0.0F));
        PartDefinition b48 = b47.addOrReplaceChild("bough2_s3", CubeListBuilder.create()
                .texOffs(344, 215).addBox(-4.0F, -4.0F, -1.0F, 8.0F, 8.0F, 26.0F)
                .texOffs(626, 310).addBox(-5.0F, -4.7079F, -4.0F, 10.0F, 10.0F, 8.0F)
                .texOffs(0, 129).addBox(-12.3513F, -17.36F, 0.2F, 26.0F, 19.0F, 26.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 30.0F, -0.3436F, 0.0F, 0.0F));
        PartDefinition b49 = b48.addOrReplaceChild("bough2_x31a", CubeListBuilder.create()
                .texOffs(90, 310).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 16.0F)
                .texOffs(510, 251).addBox(-8.0F, -9.76F, 3.2F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 10.8F, -0.4074F, -0.8494F, 0.0F));
        PartDefinition b50 = b49.addOrReplaceChild("bough2_x31b", CubeListBuilder.create()
                .texOffs(662, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 13.0F)
                .texOffs(402, 282).addBox(-7.0F, -8.04F, 0.8F, 14.0F, 10.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 16.0F, -0.7306F, 0.0F, 0.0F));
        b50.addOrReplaceChild("bough2_x31b_cur", CubeListBuilder.create()
                .texOffs(640, 215).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 34.0F, 0.0F)
                .texOffs(196, 129).addBox(0.0F, 0.0F, -7.0F, 0.0F, 30.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 13.0F, 2.1957F, -0.4757F, -2.1272F));
        b49.addOrReplaceChild("bough2_x31a_cur", CubeListBuilder.create()
                .texOffs(288, 282).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 26.0F, 0.0F)
                .texOffs(646, 174).addBox(0.0F, 0.0F, -8.0F, 0.0F, 22.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 13.6F, 2.9962F, 0.59F, -2.9152F));
        PartDefinition b53 = b48.addOrReplaceChild("bough2_x32a", CubeListBuilder.create()
                .texOffs(90, 310).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 16.0F)
                .texOffs(510, 251).addBox(-8.0F, -9.76F, 3.2F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 18.0F, -0.6099F, 0.953F, 0.0F));
        PartDefinition b54 = b53.addOrReplaceChild("bough2_x32b", CubeListBuilder.create()
                .texOffs(662, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 13.0F)
                .texOffs(402, 282).addBox(-7.0F, -8.04F, 0.8F, 14.0F, 10.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 16.0F, -0.7888F, 0.0F, 0.0F));
        b54.addOrReplaceChild("bough2_x32b_cur", CubeListBuilder.create()
                .texOffs(640, 215).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 34.0F, 0.0F)
                .texOffs(196, 129).addBox(0.0F, 0.0F, -7.0F, 0.0F, 30.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 13.0F, 1.134F, 0.3093F, -0.1248F));
        b53.addOrReplaceChild("bough2_x32a_cur", CubeListBuilder.create()
                .texOffs(288, 282).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 26.0F, 0.0F)
                .texOffs(646, 174).addBox(0.0F, 0.0F, -8.0F, 0.0F, 22.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 13.6F, 0.3427F, -0.0867F, -0.3846F));
        PartDefinition b57 = b47.addOrReplaceChild("bough2_x21a", CubeListBuilder.create()
                .texOffs(574, 251).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 22.0F)
                .texOffs(264, 215).addBox(-10.0F, -11.2F, 5.4F, 20.0F, 14.0F, 20.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 13.5F, -0.4959F, -0.9989F, 0.0F));
        PartDefinition b58 = b57.addOrReplaceChild("bough2_x21b", CubeListBuilder.create()
                .texOffs(134, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 18.0F)
                .texOffs(510, 251).addBox(-8.0F, -8.76F, 2.8F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.6511F, 0.0F, 0.0F));
        b58.addOrReplaceChild("bough2_x21b_cur", CubeListBuilder.create()
                .texOffs(912, 76).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 46.0F, 0.0F)
                .texOffs(992, 0).addBox(0.0F, 0.0F, -7.0F, 0.0F, 42.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 18.0F, 2.5103F, 0.5242F, -2.7573F));
        b57.addOrReplaceChild("bough2_x21a_cur", CubeListBuilder.create()
                .texOffs(580, 215).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 34.0F, 0.0F)
                .texOffs(880, 76).addBox(0.0F, 0.0F, -8.0F, 0.0F, 30.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 18.7F, 3.0502F, 1.3495F, -2.6161F));
        PartDefinition b61 = b47.addOrReplaceChild("bough2_x22a", CubeListBuilder.create()
                .texOffs(574, 251).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 22.0F)
                .texOffs(264, 215).addBox(-10.0F, -11.2F, 5.4F, 20.0F, 14.0F, 20.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.5F, -0.7143F, 1.0421F, 0.0F));
        PartDefinition b62 = b61.addOrReplaceChild("bough2_x22b", CubeListBuilder.create()
                .texOffs(134, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 18.0F)
                .texOffs(510, 251).addBox(-8.0F, -8.76F, 2.8F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.6352F, 0.0F, 0.0F));
        b62.addOrReplaceChild("bough2_x22b_cur", CubeListBuilder.create()
                .texOffs(912, 76).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 46.0F, 0.0F)
                .texOffs(992, 0).addBox(0.0F, 0.0F, -7.0F, 0.0F, 42.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 18.0F, 2.4542F, 0.0288F, 2.2457F));
        b61.addOrReplaceChild("bough2_x22a_cur", CubeListBuilder.create()
                .texOffs(580, 215).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 34.0F, 0.0F)
                .texOffs(880, 76).addBox(0.0F, 0.0F, -8.0F, 0.0F, 30.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 18.7F, 2.4783F, 1.2131F, 1.8584F));
        PartDefinition b65 = b46.addOrReplaceChild("bough2_x12a", CubeListBuilder.create()
                .texOffs(574, 251).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 22.0F)
                .texOffs(264, 215).addBox(-10.0F, -11.2F, 5.4F, 20.0F, 14.0F, 20.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 25.5F, -0.4363F, 1.147F, 0.0F));
        PartDefinition b66 = b65.addOrReplaceChild("bough2_x12b", CubeListBuilder.create()
                .texOffs(134, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 18.0F)
                .texOffs(510, 251).addBox(-8.0F, -8.76F, 2.8F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.781F, 0.0F, 0.0F));
        b66.addOrReplaceChild("bough2_x12b_cur", CubeListBuilder.create()
                .texOffs(912, 76).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 46.0F, 0.0F)
                .texOffs(992, 0).addBox(0.0F, 0.0F, -7.0F, 0.0F, 42.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 18.0F, 2.6703F, 0.4581F, 1.9462F));
        b65.addOrReplaceChild("bough2_x12a_cur", CubeListBuilder.create()
                .texOffs(580, 215).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 34.0F, 0.0F)
                .texOffs(880, 76).addBox(0.0F, 0.0F, -8.0F, 0.0F, 30.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 18.7F, -0.0731F, -0.1499F, -0.8482F));
        PartDefinition b69 = b22.addOrReplaceChild("bough3_s1", CubeListBuilder.create()
                .texOffs(500, 76).addBox(-7.0F, -7.0F, -1.0F, 14.0F, 14.0F, 36.0F)
                .texOffs(406, 174).addBox(-13.369F, -18.92F, 7.7F, 22.0F, 16.0F, 22.0F),
                PartPose.offsetAndRotation(2.9014F, -12.0F, 6.3704F, 0.9331F, 6.8245F, 0.0F));
        PartDefinition b70 = b69.addOrReplaceChild("bough3_s2", CubeListBuilder.create()
                .texOffs(396, 129).addBox(-5.5F, -5.5F, -1.0F, 11.0F, 11.0F, 32.0F)
                .texOffs(602, 282).addBox(-6.5F, -6.2F, -5.5F, 13.0F, 13.0F, 11.0F)
                .texOffs(0, 129).addBox(-12.321F, -18.86F, 3.5F, 26.0F, 19.0F, 26.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 34.0F, -0.2527F, 0.0F, 0.0F));
        PartDefinition b71 = b70.addOrReplaceChild("bough3_s3", CubeListBuilder.create()
                .texOffs(344, 215).addBox(-4.0F, -4.0F, -1.0F, 8.0F, 8.0F, 26.0F)
                .texOffs(626, 310).addBox(-5.0F, -5.1874F, -4.0F, 10.0F, 10.0F, 8.0F)
                .texOffs(846, 129).addBox(-11.0607F, -16.64F, 1.2F, 24.0F, 17.0F, 24.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 30.0F, -0.3158F, 0.0F, 0.0F));
        PartDefinition b72 = b71.addOrReplaceChild("bough3_x31a", CubeListBuilder.create()
                .texOffs(90, 310).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 16.0F)
                .texOffs(510, 251).addBox(-8.0F, -9.76F, 3.2F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 10.8F, -0.6636F, -1.0816F, 0.0F));
        PartDefinition b73 = b72.addOrReplaceChild("bough3_x31b", CubeListBuilder.create()
                .texOffs(662, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 13.0F)
                .texOffs(402, 282).addBox(-7.0F, -8.04F, 0.8F, 14.0F, 10.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 16.0F, -0.5623F, 0.0F, 0.0F));
        b73.addOrReplaceChild("bough3_x31b_cur", CubeListBuilder.create()
                .texOffs(640, 215).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 34.0F, 0.0F)
                .texOffs(196, 129).addBox(0.0F, 0.0F, -7.0F, 0.0F, 30.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 13.0F, 0.9659F, -0.0048F, 0.5796F));
        b72.addOrReplaceChild("bough3_x31a_cur", CubeListBuilder.create()
                .texOffs(288, 282).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 26.0F, 0.0F)
                .texOffs(646, 174).addBox(0.0F, 0.0F, -8.0F, 0.0F, 22.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 13.6F, 0.4616F, 0.0944F, 0.4057F));
        PartDefinition b76 = b71.addOrReplaceChild("bough3_x32a", CubeListBuilder.create()
                .texOffs(90, 310).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 16.0F)
                .texOffs(510, 251).addBox(-8.0F, -9.76F, 3.2F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 18.0F, -0.7107F, 0.8585F, 0.0F));
        PartDefinition b77 = b76.addOrReplaceChild("bough3_x32b", CubeListBuilder.create()
                .texOffs(662, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 13.0F)
                .texOffs(402, 282).addBox(-7.0F, -8.04F, 0.8F, 14.0F, 10.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 16.0F, -0.8864F, 0.0F, 0.0F));
        b77.addOrReplaceChild("bough3_x32b_cur", CubeListBuilder.create()
                .texOffs(640, 215).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 34.0F, 0.0F)
                .texOffs(196, 129).addBox(0.0F, 0.0F, -7.0F, 0.0F, 30.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 13.0F, 1.3487F, -0.2701F, -1.7816F));
        b76.addOrReplaceChild("bough3_x32a_cur", CubeListBuilder.create()
                .texOffs(288, 282).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 26.0F, 0.0F)
                .texOffs(646, 174).addBox(0.0F, 0.0F, -8.0F, 0.0F, 22.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 13.6F, 0.6024F, -0.7013F, -0.7216F));
        PartDefinition b80 = b70.addOrReplaceChild("bough3_x21a", CubeListBuilder.create()
                .texOffs(574, 251).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 22.0F)
                .texOffs(264, 215).addBox(-10.0F, -11.2F, 5.4F, 20.0F, 14.0F, 20.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 13.5F, -0.3692F, -0.9761F, 0.0F));
        PartDefinition b81 = b80.addOrReplaceChild("bough3_x21b", CubeListBuilder.create()
                .texOffs(134, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 18.0F)
                .texOffs(510, 251).addBox(-8.0F, -8.76F, 2.8F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.7023F, 0.0F, 0.0F));
        b81.addOrReplaceChild("bough3_x21b_cur", CubeListBuilder.create()
                .texOffs(912, 76).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 46.0F, 0.0F)
                .texOffs(992, 0).addBox(0.0F, 0.0F, -7.0F, 0.0F, 42.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 18.0F, 0.9978F, 0.9131F, 1.5395F));
        b80.addOrReplaceChild("bough3_x21a_cur", CubeListBuilder.create()
                .texOffs(580, 215).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 34.0F, 0.0F)
                .texOffs(880, 76).addBox(0.0F, 0.0F, -8.0F, 0.0F, 30.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 18.7F, -3.0839F, 0.5805F, -2.561F));
        PartDefinition b84 = b70.addOrReplaceChild("bough3_x22a", CubeListBuilder.create()
                .texOffs(574, 251).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 22.0F)
                .texOffs(264, 215).addBox(-10.0F, -11.2F, 5.4F, 20.0F, 14.0F, 20.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.5F, -0.3976F, 0.9885F, 0.0F));
        PartDefinition b85 = b84.addOrReplaceChild("bough3_x22b", CubeListBuilder.create()
                .texOffs(134, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 18.0F)
                .texOffs(510, 251).addBox(-8.0F, -8.76F, 2.8F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.9308F, 0.0F, 0.0F));
        b85.addOrReplaceChild("bough3_x22b_cur", CubeListBuilder.create()
                .texOffs(912, 76).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 46.0F, 0.0F)
                .texOffs(992, 0).addBox(0.0F, 0.0F, -7.0F, 0.0F, 42.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 18.0F, 0.7907F, 0.334F, -0.4684F));
        b84.addOrReplaceChild("bough3_x22a_cur", CubeListBuilder.create()
                .texOffs(580, 215).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 34.0F, 0.0F)
                .texOffs(880, 76).addBox(0.0F, 0.0F, -8.0F, 0.0F, 30.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 18.7F, -0.0181F, 0.0922F, -0.555F));
        PartDefinition b88 = b69.addOrReplaceChild("bough3_x12a", CubeListBuilder.create()
                .texOffs(574, 251).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 22.0F)
                .texOffs(264, 215).addBox(-10.0F, -11.2F, 5.4F, 20.0F, 14.0F, 20.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 25.5F, -0.3509F, 0.71F, 0.0F));
        PartDefinition b89 = b88.addOrReplaceChild("bough3_x12b", CubeListBuilder.create()
                .texOffs(134, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 18.0F)
                .texOffs(510, 251).addBox(-8.0F, -8.76F, 2.8F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.9027F, 0.0F, 0.0F));
        b89.addOrReplaceChild("bough3_x12b_cur", CubeListBuilder.create()
                .texOffs(912, 76).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 46.0F, 0.0F)
                .texOffs(992, 0).addBox(0.0F, 0.0F, -7.0F, 0.0F, 42.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 18.0F, 1.1886F, 1.1541F, 0.5561F));
        b88.addOrReplaceChild("bough3_x12a_cur", CubeListBuilder.create()
                .texOffs(580, 215).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 34.0F, 0.0F)
                .texOffs(880, 76).addBox(0.0F, 0.0F, -8.0F, 0.0F, 30.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 18.7F, -0.3787F, 0.1084F, -0.6413F));
        PartDefinition b92 = b22.addOrReplaceChild("bough4_s1", CubeListBuilder.create()
                .texOffs(600, 76).addBox(-7.0F, -7.0F, -1.0F, 14.0F, 14.0F, 36.0F)
                .texOffs(0, 129).addBox(-13.1371F, -20.36F, 5.7F, 26.0F, 19.0F, 26.0F),
                PartPose.offsetAndRotation(6.5572F, -12.0F, -2.45F, 0.9414F, 8.3299F, 0.0F));
        PartDefinition b93 = b92.addOrReplaceChild("bough4_s2", CubeListBuilder.create()
                .texOffs(482, 129).addBox(-5.5F, -5.5F, -1.0F, 11.0F, 11.0F, 32.0F)
                .texOffs(650, 282).addBox(-6.5F, -7.1211F, -5.5F, 13.0F, 13.0F, 11.0F)
                .texOffs(846, 129).addBox(-9.4922F, -18.14F, 4.5F, 24.0F, 17.0F, 24.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 34.0F, -0.1788F, 0.0F, 0.0F));
        PartDefinition b94 = b93.addOrReplaceChild("bough4_s3", CubeListBuilder.create()
                .texOffs(344, 215).addBox(-4.0F, -4.0F, -1.0F, 8.0F, 8.0F, 26.0F)
                .texOffs(626, 310).addBox(-5.0F, -4.9597F, -4.0F, 10.0F, 10.0F, 8.0F)
                .texOffs(0, 129).addBox(-11.6043F, -17.36F, 0.2F, 26.0F, 19.0F, 26.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 30.0F, -0.3439F, 0.0F, 0.0F));
        PartDefinition b95 = b94.addOrReplaceChild("bough4_x31a", CubeListBuilder.create()
                .texOffs(90, 310).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 16.0F)
                .texOffs(510, 251).addBox(-8.0F, -9.76F, 3.2F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 10.8F, -0.4267F, -1.2306F, 0.0F));
        PartDefinition b96 = b95.addOrReplaceChild("bough4_x31b", CubeListBuilder.create()
                .texOffs(662, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 13.0F)
                .texOffs(402, 282).addBox(-7.0F, -8.04F, 0.8F, 14.0F, 10.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 16.0F, -0.6235F, 0.0F, 0.0F));
        b96.addOrReplaceChild("bough4_x31b_cur", CubeListBuilder.create()
                .texOffs(640, 215).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 34.0F, 0.0F)
                .texOffs(196, 129).addBox(0.0F, 0.0F, -7.0F, 0.0F, 30.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 13.0F, 1.0765F, -0.6029F, -0.2205F));
        b95.addOrReplaceChild("bough4_x31a_cur", CubeListBuilder.create()
                .texOffs(288, 282).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 26.0F, 0.0F)
                .texOffs(646, 174).addBox(0.0F, 0.0F, -8.0F, 0.0F, 22.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 13.6F, 0.3549F, -0.7485F, 0.1605F));
        PartDefinition b99 = b94.addOrReplaceChild("bough4_x32a", CubeListBuilder.create()
                .texOffs(90, 310).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 16.0F)
                .texOffs(510, 251).addBox(-8.0F, -9.76F, 3.2F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 18.0F, -0.6578F, 0.9068F, 0.0F));
        PartDefinition b100 = b99.addOrReplaceChild("bough4_x32b", CubeListBuilder.create()
                .texOffs(662, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 13.0F)
                .texOffs(402, 282).addBox(-7.0F, -8.04F, 0.8F, 14.0F, 10.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 16.0F, -0.5598F, 0.0F, 0.0F));
        b100.addOrReplaceChild("bough4_x32b_cur", CubeListBuilder.create()
                .texOffs(640, 215).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 34.0F, 0.0F)
                .texOffs(196, 129).addBox(0.0F, 0.0F, -7.0F, 0.0F, 30.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 13.0F, 1.6956F, -0.6816F, -2.2936F));
        b99.addOrReplaceChild("bough4_x32a_cur", CubeListBuilder.create()
                .texOffs(288, 282).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 26.0F, 0.0F)
                .texOffs(646, 174).addBox(0.0F, 0.0F, -8.0F, 0.0F, 22.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 13.6F, 0.7632F, -1.0226F, -1.035F));
        PartDefinition b103 = b93.addOrReplaceChild("bough4_x21a", CubeListBuilder.create()
                .texOffs(574, 251).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 22.0F)
                .texOffs(264, 215).addBox(-10.0F, -11.2F, 5.4F, 20.0F, 14.0F, 20.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 13.5F, -0.6671F, -0.7911F, 0.0F));
        PartDefinition b104 = b103.addOrReplaceChild("bough4_x21b", CubeListBuilder.create()
                .texOffs(134, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 18.0F)
                .texOffs(510, 251).addBox(-8.0F, -8.76F, 2.8F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.726F, 0.0F, 0.0F));
        b104.addOrReplaceChild("bough4_x21b_cur", CubeListBuilder.create()
                .texOffs(912, 76).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 46.0F, 0.0F)
                .texOffs(992, 0).addBox(0.0F, 0.0F, -7.0F, 0.0F, 42.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 18.0F, 0.775F, -0.4639F, 0.2683F));
        b103.addOrReplaceChild("bough4_x21a_cur", CubeListBuilder.create()
                .texOffs(580, 215).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 34.0F, 0.0F)
                .texOffs(880, 76).addBox(0.0F, 0.0F, -8.0F, 0.0F, 30.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 18.7F, 0.0842F, 0.6729F, 0.5673F));
        PartDefinition b107 = b93.addOrReplaceChild("bough4_x22a", CubeListBuilder.create()
                .texOffs(574, 251).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 22.0F)
                .texOffs(264, 215).addBox(-10.0F, -11.2F, 5.4F, 20.0F, 14.0F, 20.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.5F, -0.4091F, 1.297F, 0.0F));
        PartDefinition b108 = b107.addOrReplaceChild("bough4_x22b", CubeListBuilder.create()
                .texOffs(134, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 18.0F)
                .texOffs(510, 251).addBox(-8.0F, -8.76F, 2.8F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.6945F, 0.0F, 0.0F));
        b108.addOrReplaceChild("bough4_x22b_cur", CubeListBuilder.create()
                .texOffs(912, 76).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 46.0F, 0.0F)
                .texOffs(992, 0).addBox(0.0F, 0.0F, -7.0F, 0.0F, 42.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 18.0F, 0.6007F, -0.1159F, -1.0126F));
        b107.addOrReplaceChild("bough4_x22a_cur", CubeListBuilder.create()
                .texOffs(580, 215).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 34.0F, 0.0F)
                .texOffs(880, 76).addBox(0.0F, 0.0F, -8.0F, 0.0F, 30.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 18.7F, 0.5797F, -1.3569F, -1.3029F));
        PartDefinition b111 = b92.addOrReplaceChild("bough4_x12a", CubeListBuilder.create()
                .texOffs(574, 251).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 22.0F)
                .texOffs(264, 215).addBox(-10.0F, -11.2F, 5.4F, 20.0F, 14.0F, 20.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 25.5F, -0.4909F, 0.8811F, 0.0F));
        PartDefinition b112 = b111.addOrReplaceChild("bough4_x12b", CubeListBuilder.create()
                .texOffs(134, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 18.0F)
                .texOffs(510, 251).addBox(-8.0F, -8.76F, 2.8F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.6371F, 0.0F, 0.0F));
        b112.addOrReplaceChild("bough4_x12b_cur", CubeListBuilder.create()
                .texOffs(912, 76).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 46.0F, 0.0F)
                .texOffs(992, 0).addBox(0.0F, 0.0F, -7.0F, 0.0F, 42.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 18.0F, 0.9059F, -1.1639F, -1.5801F));
        b111.addOrReplaceChild("bough4_x12a_cur", CubeListBuilder.create()
                .texOffs(580, 215).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 34.0F, 0.0F)
                .texOffs(880, 76).addBox(0.0F, 0.0F, -8.0F, 0.0F, 30.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 18.7F, -0.1986F, -0.4679F, -0.5956F));
        PartDefinition b115 = b22.addOrReplaceChild("leader_s1", CubeListBuilder.create()
                .texOffs(144, 76).addBox(-6.5F, -6.5F, -1.0F, 13.0F, 13.0F, 38.0F)
                .texOffs(846, 129).addBox(-14.6922F, -19.14F, 7.8F, 24.0F, 17.0F, 24.0F),
                PartPose.offsetAndRotation(0.5988F, -12.0F, -2.9396F, 1.3975F, 2.9778F, 0.0F));
        PartDefinition b116 = b115.addOrReplaceChild("leader_s2", CubeListBuilder.create()
                .texOffs(654, 129).addBox(-5.0F, -5.0F, -1.0F, 10.0F, 10.0F, 32.0F)
                .texOffs(178, 310).addBox(-6.0F, -5.2356F, -5.0F, 12.0F, 12.0F, 10.0F)
                .texOffs(846, 129).addBox(-9.8383F, -17.64F, 4.5F, 24.0F, 17.0F, 24.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 36.0F, -0.0972F, 0.0F, 0.0F));
        PartDefinition b117 = b116.addOrReplaceChild("leader_s3", CubeListBuilder.create()
                .texOffs(952, 215).addBox(-3.5F, -3.5F, -1.0F, 7.0F, 7.0F, 24.0F)
                .texOffs(772, 310).addBox(-4.5F, -4.0254F, -3.5F, 9.0F, 9.0F, 7.0F)
                .texOffs(406, 174).addBox(-12.551F, -15.42F, 1.1F, 22.0F, 16.0F, 22.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 30.0F, -0.1975F, 0.0F, 0.0F));
        PartDefinition b118 = b117.addOrReplaceChild("leader_x31a", CubeListBuilder.create()
                .texOffs(90, 310).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 16.0F)
                .texOffs(510, 251).addBox(-8.0F, -9.76F, 3.2F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 9.9F, -0.7023F, -1.1739F, 0.0F));
        PartDefinition b119 = b118.addOrReplaceChild("leader_x31b", CubeListBuilder.create()
                .texOffs(662, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 13.0F)
                .texOffs(402, 282).addBox(-7.0F, -8.04F, 0.8F, 14.0F, 10.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 16.0F, -0.6228F, 0.0F, 0.0F));
        b119.addOrReplaceChild("leader_x31b_cur", CubeListBuilder.create()
                .texOffs(640, 215).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 34.0F, 0.0F)
                .texOffs(196, 129).addBox(0.0F, 0.0F, -7.0F, 0.0F, 30.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 13.0F, 2.7475F, -0.4004F, -1.9051F));
        b118.addOrReplaceChild("leader_x31a_cur", CubeListBuilder.create()
                .texOffs(288, 282).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 26.0F, 0.0F)
                .texOffs(646, 174).addBox(0.0F, 0.0F, -8.0F, 0.0F, 22.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 13.6F, 0.1407F, -1.3697F, 0.8295F));
        PartDefinition b122 = b117.addOrReplaceChild("leader_x32a", CubeListBuilder.create()
                .texOffs(90, 310).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 16.0F)
                .texOffs(510, 251).addBox(-8.0F, -9.76F, 3.2F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 16.5F, -0.575F, 0.8005F, 0.0F));
        PartDefinition b123 = b122.addOrReplaceChild("leader_x32b", CubeListBuilder.create()
                .texOffs(662, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 13.0F)
                .texOffs(402, 282).addBox(-7.0F, -8.04F, 0.8F, 14.0F, 10.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 16.0F, -0.8162F, 0.0F, 0.0F));
        b123.addOrReplaceChild("leader_x32b_cur", CubeListBuilder.create()
                .texOffs(640, 215).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 34.0F, 0.0F)
                .texOffs(196, 129).addBox(0.0F, 0.0F, -7.0F, 0.0F, 30.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 13.0F, 0.6994F, -1.0276F, -1.3709F));
        b122.addOrReplaceChild("leader_x32a_cur", CubeListBuilder.create()
                .texOffs(288, 282).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 26.0F, 0.0F)
                .texOffs(646, 174).addBox(0.0F, 0.0F, -8.0F, 0.0F, 22.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 13.6F, -1.6191F, -1.2906F, 0.8916F));
        PartDefinition b126 = b116.addOrReplaceChild("leader_x21a", CubeListBuilder.create()
                .texOffs(574, 251).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 22.0F)
                .texOffs(264, 215).addBox(-10.0F, -11.2F, 5.4F, 20.0F, 14.0F, 20.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 13.5F, -0.4519F, -1.1498F, 0.0F));
        PartDefinition b127 = b126.addOrReplaceChild("leader_x21b", CubeListBuilder.create()
                .texOffs(134, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 18.0F)
                .texOffs(510, 251).addBox(-8.0F, -8.76F, 2.8F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.9235F, 0.0F, 0.0F));
        b127.addOrReplaceChild("leader_x21b_cur", CubeListBuilder.create()
                .texOffs(912, 76).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 46.0F, 0.0F)
                .texOffs(992, 0).addBox(0.0F, 0.0F, -7.0F, 0.0F, 42.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 18.0F, 0.2313F, -0.6274F, 0.9711F));
        b126.addOrReplaceChild("leader_x21a_cur", CubeListBuilder.create()
                .texOffs(580, 215).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 34.0F, 0.0F)
                .texOffs(880, 76).addBox(0.0F, 0.0F, -8.0F, 0.0F, 30.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 18.7F, -0.2405F, 0.0685F, 1.1156F));
        PartDefinition b130 = b116.addOrReplaceChild("leader_x22a", CubeListBuilder.create()
                .texOffs(574, 251).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 22.0F)
                .texOffs(264, 215).addBox(-10.0F, -11.2F, 5.4F, 20.0F, 14.0F, 20.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.5F, -0.4201F, 1.195F, 0.0F));
        PartDefinition b131 = b130.addOrReplaceChild("leader_x22b", CubeListBuilder.create()
                .texOffs(134, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 18.0F)
                .texOffs(510, 251).addBox(-8.0F, -8.76F, 2.8F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.8152F, 0.0F, 0.0F));
        b131.addOrReplaceChild("leader_x22b_cur", CubeListBuilder.create()
                .texOffs(912, 76).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 46.0F, 0.0F)
                .texOffs(992, 0).addBox(0.0F, 0.0F, -7.0F, 0.0F, 42.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 18.0F, 2.9918F, 0.4263F, 1.9484F));
        b130.addOrReplaceChild("leader_x22a_cur", CubeListBuilder.create()
                .texOffs(580, 215).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 34.0F, 0.0F)
                .texOffs(880, 76).addBox(0.0F, 0.0F, -8.0F, 0.0F, 30.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 18.7F, -2.8667F, 0.663F, 2.1513F));
        PartDefinition b134 = b115.addOrReplaceChild("leader_x11a", CubeListBuilder.create()
                .texOffs(574, 251).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 22.0F)
                .texOffs(264, 215).addBox(-10.0F, -11.2F, 5.4F, 20.0F, 14.0F, 20.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 16.2F, -0.5104F, -0.9665F, 0.0F));
        PartDefinition b135 = b134.addOrReplaceChild("leader_x11b", CubeListBuilder.create()
                .texOffs(134, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 18.0F)
                .texOffs(510, 251).addBox(-8.0F, -8.76F, 2.8F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.8028F, 0.0F, 0.0F));
        b135.addOrReplaceChild("leader_x11b_cur", CubeListBuilder.create()
                .texOffs(912, 76).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 46.0F, 0.0F)
                .texOffs(992, 0).addBox(0.0F, 0.0F, -7.0F, 0.0F, 42.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 18.0F, 0.0251F, -0.2676F, 0.9389F));
        b134.addOrReplaceChild("leader_x11a_cur", CubeListBuilder.create()
                .texOffs(580, 215).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 34.0F, 0.0F)
                .texOffs(880, 76).addBox(0.0F, 0.0F, -8.0F, 0.0F, 30.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 18.7F, -0.6976F, -0.8904F, 1.6665F));
        PartDefinition b138 = b115.addOrReplaceChild("leader_x12a", CubeListBuilder.create()
                .texOffs(574, 251).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 22.0F)
                .texOffs(264, 215).addBox(-10.0F, -11.2F, 5.4F, 20.0F, 14.0F, 20.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 27.0F, -0.5451F, 1.2439F, 0.0F));
        PartDefinition b139 = b138.addOrReplaceChild("leader_x12b", CubeListBuilder.create()
                .texOffs(134, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 18.0F)
                .texOffs(510, 251).addBox(-8.0F, -8.76F, 2.8F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.7262F, 0.0F, 0.0F));
        b139.addOrReplaceChild("leader_x12b_cur", CubeListBuilder.create()
                .texOffs(912, 76).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 46.0F, 0.0F)
                .texOffs(992, 0).addBox(0.0F, 0.0F, -7.0F, 0.0F, 42.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 18.0F, 3.0487F, -0.6922F, 1.992F));
        b138.addOrReplaceChild("leader_x12a_cur", CubeListBuilder.create()
                .texOffs(580, 215).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 34.0F, 0.0F)
                .texOffs(880, 76).addBox(0.0F, 0.0F, -8.0F, 0.0F, 30.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 18.7F, -2.4445F, -1.2848F, 1.2162F));
        PartDefinition b142 = b20.addOrReplaceChild("limb3_s1", CubeListBuilder.create()
                .texOffs(568, 129).addBox(-8.0F, -8.0F, -1.0F, 16.0F, 16.0F, 27.0F),
                PartPose.offsetAndRotation(-2.0636F, -6.0F, 14.8574F, 0.3246F, 6.1452F, 0.0F));
        PartDefinition b143 = b142.addOrReplaceChild("limb3_s2", CubeListBuilder.create()
                .texOffs(222, 174).addBox(-6.5F, -6.5F, -1.0F, 13.0F, 13.0F, 26.0F)
                .texOffs(630, 251).addBox(-7.5F, -6.9228F, -6.5F, 15.0F, 15.0F, 13.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 25.0F, -0.0156F, 0.0F, 0.0F));
        PartDefinition b144 = b143.addOrReplaceChild("limb3_s3", CubeListBuilder.create()
                .texOffs(952, 174).addBox(-5.5F, -5.5F, -1.0F, 11.0F, 11.0F, 25.0F)
                .texOffs(698, 282).addBox(-6.5F, -5.6449F, -5.5F, 13.0F, 13.0F, 11.0F)
                .texOffs(402, 282).addBox(-7.0F, -12.54F, 6.8F, 14.0F, 10.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 24.0F, -0.2678F, 0.0F, 0.0F));
        PartDefinition b145 = b144.addOrReplaceChild("limb3_s4", CubeListBuilder.create()
                .texOffs(812, 215).addBox(-4.5F, -4.5F, -1.0F, 9.0F, 9.0F, 23.0F)
                .texOffs(426, 310).addBox(-5.5F, -5.9943F, -4.5F, 11.0F, 11.0F, 9.0F)
                .texOffs(510, 251).addBox(-8.0F, -12.26F, 4.6F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 23.0F, -0.2917F, 0.0F, 0.0F));
        PartDefinition b146 = b145.addOrReplaceChild("limb3_s5", CubeListBuilder.create()
                .texOffs(686, 251).addBox(-3.5F, -3.5F, -1.0F, 7.0F, 7.0F, 21.0F)
                .texOffs(772, 310).addBox(-4.5F, -4.412F, -3.5F, 9.0F, 9.0F, 7.0F)
                .texOffs(402, 282).addBox(-7.0F, -10.54F, 4.4F, 14.0F, 10.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 21.0F, -0.3947F, 0.0F, 0.0F));
        PartDefinition b147 = b146.addOrReplaceChild("limb3_s6", CubeListBuilder.create()
                .texOffs(746, 282).addBox(-2.5F, -2.5F, -1.0F, 5.0F, 5.0F, 19.0F)
                .texOffs(510, 251).addBox(-8.0F, -10.26F, 2.2F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 19.0F, -0.3881F, 0.0F, 0.0F));
        b147.addOrReplaceChild("limb3_cur5", CubeListBuilder.create()
                .texOffs(668, 215).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 34.0F, 0.0F)
                .texOffs(822, 129).addBox(0.0F, 0.0F, -6.0F, 0.0F, 30.0F, 12.0F),
                PartPose.offsetAndRotation(-0.3104F, 2.5F, 5.1F, 1.4074F, 0.5143F, 1.2474F));
        PartDefinition b149 = b146.addOrReplaceChild("limb3_f4", CubeListBuilder.create()
                .texOffs(962, 282).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 20.0F)
                .texOffs(378, 310).addBox(-6.0F, -6.32F, 10.0F, 12.0F, 9.0F, 12.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 10.45F, -0.2288F, -0.7297F, 0.0F));
        b149.addOrReplaceChild("limb3_f4_cur", CubeListBuilder.create()
                .texOffs(904, 215).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 32.0F, 0.0F)
                .texOffs(198, 174).addBox(0.0F, 0.0F, -6.0F, 0.0F, 28.0F, 12.0F),
                PartPose.offsetAndRotation(0.0F, 1.5F, 15.0F, 1.1404F, 0.8232F, 0.4763F));
        b146.addOrReplaceChild("limb3_cur4", CubeListBuilder.create()
                .texOffs(420, 251).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 30.0F, 0.0F)
                .texOffs(678, 174).addBox(0.0F, 0.0F, -6.0F, 0.0F, 26.0F, 12.0F),
                PartPose.offsetAndRotation(-1.1888F, 3.5F, 5.7F, 1.092F, 0.8263F, 0.9562F));
        PartDefinition b152 = b145.addOrReplaceChild("limb3_f3", CubeListBuilder.create()
                .texOffs(134, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 18.0F)
                .texOffs(402, 282).addBox(-7.0F, -7.04F, 7.4F, 14.0F, 10.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 11.55F, -0.2491F, 0.8124F, 0.0F));
        b152.addOrReplaceChild("limb3_f3_cur", CubeListBuilder.create()
                .texOffs(70, 282).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 28.0F, 0.0F)
                .texOffs(32, 215).addBox(0.0F, 0.0F, -6.0F, 0.0F, 24.0F, 12.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 13.5F, 0.4976F, 0.5613F, 0.4795F));
        b145.addOrReplaceChild("limb3_cur3", CubeListBuilder.create()
                .texOffs(346, 282).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 26.0F, 0.0F)
                .texOffs(692, 215).addBox(0.0F, 0.0F, -6.0F, 0.0F, 22.0F, 12.0F),
                PartPose.offsetAndRotation(-0.8508F, 4.5F, 6.3F, 0.2678F, 0.3562F, 0.0954F));
        PartDefinition b155 = b144.addOrReplaceChild("limb3_f2", CubeListBuilder.create()
                .texOffs(90, 310).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 16.0F)
                .texOffs(510, 251).addBox(-8.0F, -7.76F, 4.8F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 12.65F, -0.2937F, -0.7573F, 0.0F));
        b155.addOrReplaceChild("limb3_f2_cur", CubeListBuilder.create()
                .texOffs(938, 282).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 24.0F, 0.0F)
                .texOffs(928, 215).addBox(0.0F, 0.0F, -6.0F, 0.0F, 20.0F, 12.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 12.0F, 2.848F, 0.4492F, 3.0404F));
        b144.addOrReplaceChild("limb3_cur2", CubeListBuilder.create()
                .texOffs(310, 310).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 22.0F, 0.0F)
                .texOffs(444, 251).addBox(0.0F, 0.0F, -6.0F, 0.0F, 18.0F, 12.0F),
                PartPose.offsetAndRotation(1.2514F, 5.5F, 6.9F, -0.0492F, 0.5788F, -0.0269F));
        b143.addOrReplaceChild("limb3_f1", CubeListBuilder.create()
                .texOffs(222, 310).addBox(-4.0F, -4.0F, 0.0F, 8.0F, 8.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 13.2F, -0.2332F, 0.5876F, 0.0F));
        PartDefinition b159 = b19.addOrReplaceChild("maw", CubeListBuilder.create()
                .texOffs(696, 310).addBox(-9.0F, -8.0F, -6.0F, 18.0F, 12.0F, 4.0F)
                .texOffs(868, 310).addBox(-13.0F, -14.0F, -2.0F, 26.0F, 6.0F, 7.0F)
                .texOffs(968, 310).addBox(-10.0F, -17.0F, -1.0F, 20.0F, 4.0F, 5.0F)
                .texOffs(334, 310).addBox(-15.0F, -10.0F, -2.0F, 5.0F, 16.0F, 6.0F)
                .texOffs(356, 310).addBox(10.0F, -10.0F, -2.0F, 5.0F, 16.0F, 6.0F)
                .texOffs(70, 333).addBox(-9.0F, -8.0F, 1.0F, 2.0F, 5.0F, 2.0F)
                .texOffs(958, 310).addBox(-5.0F, -8.0F, 1.0F, 3.0F, 8.0F, 2.0F)
                .texOffs(88, 333).addBox(-1.0F, -8.0F, 1.0F, 2.0F, 4.0F, 2.0F)
                .texOffs(0, 333).addBox(3.0F, -8.0F, 1.0F, 3.0F, 7.0F, 2.0F)
                .texOffs(70, 333).addBox(7.0F, -8.0F, 1.0F, 2.0F, 5.0F, 2.0F),
                PartPose.offsetAndRotation(4.3646F, -9.0F, -19.5179F, 3.1416F, 0.22F, 3.1416F));
        b159.addOrReplaceChild("maw_jaw", CubeListBuilder.create()
                .texOffs(804, 310).addBox(-12.0F, 0.0F, -2.0F, 24.0F, 6.0F, 8.0F)
                .texOffs(62, 333).addBox(-7.0F, -6.0F, 2.0F, 2.0F, 6.0F, 2.0F)
                .texOffs(78, 333).addBox(-3.0F, -4.0F, 2.0F, 3.0F, 4.0F, 2.0F)
                .texOffs(10, 333).addBox(1.0F, -7.0F, 2.0F, 2.0F, 7.0F, 2.0F)
                .texOffs(78, 333).addBox(5.0F, -4.0F, 2.0F, 3.0F, 4.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition b161 = b19.addOrReplaceChild("limb1_s1", CubeListBuilder.create()
                .texOffs(738, 129).addBox(-8.0F, -8.0F, -1.0F, 16.0F, 16.0F, 26.0F),
                PartPose.offsetAndRotation(-0.4138F, -10.0F, -16.995F, 0.2918F, 3.1659F, 0.0F));
        PartDefinition b162 = b161.addOrReplaceChild("limb1_s2", CubeListBuilder.create()
                .texOffs(570, 174).addBox(-6.5F, -6.5F, -1.0F, 13.0F, 13.0F, 25.0F)
                .texOffs(742, 251).addBox(-7.5F, -8.0626F, -6.5F, 15.0F, 15.0F, 13.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 24.0F, -0.0129F, 0.0F, 0.0F));
        PartDefinition b163 = b162.addOrReplaceChild("limb1_s3", CubeListBuilder.create()
                .texOffs(126, 215).addBox(-5.5F, -5.5F, -1.0F, 11.0F, 11.0F, 24.0F)
                .texOffs(794, 282).addBox(-6.5F, -6.8523F, -5.5F, 13.0F, 13.0F, 11.0F)
                .texOffs(402, 282).addBox(-7.0F, -12.54F, 6.2F, 14.0F, 10.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 23.0F, -0.2804F, 0.0F, 0.0F));
        PartDefinition b164 = b163.addOrReplaceChild("limb1_s4", CubeListBuilder.create()
                .texOffs(0, 251).addBox(-4.5F, -4.5F, -1.0F, 9.0F, 9.0F, 22.0F)
                .texOffs(466, 310).addBox(-5.5F, -6.2957F, -4.5F, 11.0F, 11.0F, 9.0F)
                .texOffs(510, 251).addBox(-8.0F, -12.26F, 4.0F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.3292F, 0.0F, 0.0F));
        PartDefinition b165 = b164.addOrReplaceChild("limb1_s5", CubeListBuilder.create()
                .texOffs(148, 282).addBox(-3.5F, -3.5F, -1.0F, 7.0F, 7.0F, 20.0F)
                .texOffs(772, 310).addBox(-4.5F, -4.9623F, -3.5F, 9.0F, 9.0F, 7.0F)
                .texOffs(402, 282).addBox(-7.0F, -10.54F, 3.8F, 14.0F, 10.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 20.0F, -0.3139F, 0.0F, 0.0F));
        PartDefinition b166 = b165.addOrReplaceChild("limb1_s6", CubeListBuilder.create()
                .texOffs(0, 310).addBox(-2.5F, -2.5F, -1.0F, 5.0F, 5.0F, 18.0F)
                .texOffs(510, 251).addBox(-8.0F, -10.26F, 1.6F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 18.0F, -0.3877F, 0.0F, 0.0F));
        b166.addOrReplaceChild("limb1_cur5", CubeListBuilder.create()
                .texOffs(668, 215).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 34.0F, 0.0F)
                .texOffs(822, 129).addBox(0.0F, 0.0F, -6.0F, 0.0F, 30.0F, 12.0F),
                PartPose.offsetAndRotation(-0.268F, 2.5F, 4.8F, 1.6003F, -0.5378F, -1.6283F));
        PartDefinition b168 = b165.addOrReplaceChild("limb1_f4", CubeListBuilder.create()
                .texOffs(962, 282).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 20.0F)
                .texOffs(402, 282).addBox(-7.0F, -7.04F, 9.0F, 14.0F, 10.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 9.9F, -0.147F, -0.5983F, 0.0F));
        b168.addOrReplaceChild("limb1_f4_cur", CubeListBuilder.create()
                .texOffs(904, 215).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 32.0F, 0.0F)
                .texOffs(198, 174).addBox(0.0F, 0.0F, -6.0F, 0.0F, 28.0F, 12.0F),
                PartPose.offsetAndRotation(0.0F, 1.5F, 15.0F, 0.6997F, -0.3348F, -0.7103F));
        b165.addOrReplaceChild("limb1_cur4", CubeListBuilder.create()
                .texOffs(420, 251).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 30.0F, 0.0F)
                .texOffs(678, 174).addBox(0.0F, 0.0F, -6.0F, 0.0F, 26.0F, 12.0F),
                PartPose.offsetAndRotation(0.0232F, 3.5F, 5.4F, 1.1941F, -0.8683F, -1.0926F));
        PartDefinition b171 = b164.addOrReplaceChild("limb1_f3", CubeListBuilder.create()
                .texOffs(134, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 18.0F)
                .texOffs(402, 282).addBox(-7.0F, -7.04F, 7.4F, 14.0F, 10.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 11.0F, 0.0937F, 0.6068F, 0.0F));
        b171.addOrReplaceChild("limb1_f3_cur", CubeListBuilder.create()
                .texOffs(70, 282).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 28.0F, 0.0F)
                .texOffs(32, 215).addBox(0.0F, 0.0F, -6.0F, 0.0F, 24.0F, 12.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 13.5F, 2.9481F, 0.4004F, -3.0286F));
        b164.addOrReplaceChild("limb1_cur3", CubeListBuilder.create()
                .texOffs(346, 282).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 26.0F, 0.0F)
                .texOffs(692, 215).addBox(0.0F, 0.0F, -6.0F, 0.0F, 22.0F, 12.0F),
                PartPose.offsetAndRotation(-0.0551F, 4.5F, 6.0F, 0.7561F, -1.0778F, -0.6932F));
        PartDefinition b174 = b163.addOrReplaceChild("limb1_f2", CubeListBuilder.create()
                .texOffs(90, 310).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 16.0F)
                .texOffs(378, 310).addBox(-6.0F, -6.32F, 6.8F, 12.0F, 9.0F, 12.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 12.1F, -0.3098F, -0.6536F, 0.0F));
        b174.addOrReplaceChild("limb1_f2_cur", CubeListBuilder.create()
                .texOffs(938, 282).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 24.0F, 0.0F)
                .texOffs(928, 215).addBox(0.0F, 0.0F, -6.0F, 0.0F, 20.0F, 12.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 12.0F, 0.9028F, -1.1704F, -0.8634F));
        b163.addOrReplaceChild("limb1_cur2", CubeListBuilder.create()
                .texOffs(310, 310).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 22.0F, 0.0F)
                .texOffs(444, 251).addBox(0.0F, 0.0F, -6.0F, 0.0F, 18.0F, 12.0F),
                PartPose.offsetAndRotation(-1.6476F, 5.5F, 6.6F, 3.1389F, -0.9984F, -3.1394F));
        b162.addOrReplaceChild("limb1_f1", CubeListBuilder.create()
                .texOffs(222, 310).addBox(-4.0F, -4.0F, 0.0F, 8.0F, 8.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 12.65F, -0.1175F, 0.7836F, 0.0F));
        PartDefinition b178 = b19.addOrReplaceChild("limb4_s1", CubeListBuilder.create()
                .texOffs(942, 129).addBox(-8.0F, -8.0F, -1.0F, 16.0F, 16.0F, 25.0F),
                PartPose.offsetAndRotation(16.8086F, -14.0F, 2.5436F, 0.2769F, 7.7038F, 0.0F));
        PartDefinition b179 = b178.addOrReplaceChild("limb4_s2", CubeListBuilder.create()
                .texOffs(702, 174).addBox(-6.5F, -6.5F, -1.0F, 13.0F, 13.0F, 24.0F)
                .texOffs(798, 251).addBox(-7.5F, -7.3298F, -6.5F, 15.0F, 15.0F, 13.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 23.0F, -0.0544F, 0.0F, 0.0F));
        PartDefinition b180 = b179.addOrReplaceChild("limb4_s3", CubeListBuilder.create()
                .texOffs(412, 215).addBox(-5.5F, -5.5F, -1.0F, 11.0F, 11.0F, 23.0F)
                .texOffs(842, 282).addBox(-6.5F, -5.7758F, -5.5F, 13.0F, 13.0F, 11.0F)
                .texOffs(402, 282).addBox(-7.0F, -12.54F, 5.6F, 14.0F, 10.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.2999F, 0.0F, 0.0F));
        PartDefinition b181 = b180.addOrReplaceChild("limb4_s4", CubeListBuilder.create()
                .texOffs(214, 251).addBox(-4.5F, -4.5F, -1.0F, 9.0F, 9.0F, 21.0F)
                .texOffs(506, 310).addBox(-5.5F, -5.1609F, -4.5F, 11.0F, 11.0F, 9.0F)
                .texOffs(510, 251).addBox(-8.0F, -12.26F, 3.4F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 21.0F, -0.3255F, 0.0F, 0.0F));
        PartDefinition b182 = b181.addOrReplaceChild("limb4_s5", CubeListBuilder.create()
                .texOffs(202, 282).addBox(-3.5F, -3.5F, -1.0F, 7.0F, 7.0F, 19.0F)
                .texOffs(772, 310).addBox(-4.5F, -5.047F, -3.5F, 9.0F, 9.0F, 7.0F)
                .texOffs(402, 282).addBox(-7.0F, -10.54F, 3.2F, 14.0F, 10.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 19.0F, -0.3846F, 0.0F, 0.0F));
        PartDefinition b183 = b182.addOrReplaceChild("limb4_s6", CubeListBuilder.create()
                .texOffs(0, 310).addBox(-2.5F, -2.5F, -1.0F, 5.0F, 5.0F, 18.0F)
                .texOffs(510, 251).addBox(-8.0F, -10.26F, 1.6F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 17.0F, -0.3503F, 0.0F, 0.0F));
        b183.addOrReplaceChild("limb4_cur5", CubeListBuilder.create()
                .texOffs(668, 215).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 34.0F, 0.0F)
                .texOffs(822, 129).addBox(0.0F, 0.0F, -6.0F, 0.0F, 30.0F, 12.0F),
                PartPose.offsetAndRotation(0.1424F, 2.5F, 4.8F, 1.2167F, 0.2547F, 0.5982F));
        PartDefinition b185 = b182.addOrReplaceChild("limb4_f4", CubeListBuilder.create()
                .texOffs(962, 282).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 20.0F)
                .texOffs(402, 282).addBox(-7.0F, -7.04F, 9.0F, 14.0F, 10.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 9.35F, -0.075F, 0.8986F, 0.0F));
        b185.addOrReplaceChild("limb4_f4_cur", CubeListBuilder.create()
                .texOffs(904, 215).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 32.0F, 0.0F)
                .texOffs(198, 174).addBox(0.0F, 0.0F, -6.0F, 0.0F, 28.0F, 12.0F),
                PartPose.offsetAndRotation(0.0F, 1.5F, 15.0F, 0.8003F, -0.8137F, 0.0481F));
        b182.addOrReplaceChild("limb4_cur4", CubeListBuilder.create()
                .texOffs(420, 251).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 30.0F, 0.0F)
                .texOffs(678, 174).addBox(0.0F, 0.0F, -6.0F, 0.0F, 26.0F, 12.0F),
                PartPose.offsetAndRotation(-0.3747F, 3.5F, 5.1F, 1.1479F, 0.6809F, 0.9502F));
        PartDefinition b188 = b181.addOrReplaceChild("limb4_f3", CubeListBuilder.create()
                .texOffs(134, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 18.0F)
                .texOffs(402, 282).addBox(-7.0F, -7.04F, 7.4F, 14.0F, 10.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 10.45F, -0.1474F, -0.7705F, 0.0F));
        b188.addOrReplaceChild("limb4_f3_cur", CubeListBuilder.create()
                .texOffs(70, 282).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 28.0F, 0.0F)
                .texOffs(32, 215).addBox(0.0F, 0.0F, -6.0F, 0.0F, 24.0F, 12.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 13.5F, 0.4263F, -0.0273F, -0.317F));
        b181.addOrReplaceChild("limb4_cur3", CubeListBuilder.create()
                .texOffs(346, 282).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 26.0F, 0.0F)
                .texOffs(692, 215).addBox(0.0F, 0.0F, -6.0F, 0.0F, 22.0F, 12.0F),
                PartPose.offsetAndRotation(-0.6887F, 4.5F, 5.7F, 0.4202F, -0.28F, -0.1228F));
        PartDefinition b191 = b180.addOrReplaceChild("limb4_f2", CubeListBuilder.create()
                .texOffs(90, 310).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 16.0F)
                .texOffs(402, 282).addBox(-7.0F, -7.04F, 5.8F, 14.0F, 10.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 11.55F, -0.0348F, 0.7071F, 0.0F));
        b191.addOrReplaceChild("limb4_f2_cur", CubeListBuilder.create()
                .texOffs(938, 282).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 24.0F, 0.0F)
                .texOffs(928, 215).addBox(0.0F, 0.0F, -6.0F, 0.0F, 20.0F, 12.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 12.0F, 0.2799F, -1.2256F, -0.2137F));
        b180.addOrReplaceChild("limb4_cur2", CubeListBuilder.create()
                .texOffs(310, 310).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 22.0F, 0.0F)
                .texOffs(444, 251).addBox(0.0F, 0.0F, -6.0F, 0.0F, 18.0F, 12.0F),
                PartPose.offsetAndRotation(0.5842F, 5.5F, 6.3F, 0.0774F, 0.0034F, 0.0003F));
        b179.addOrReplaceChild("limb4_f1", CubeListBuilder.create()
                .texOffs(222, 310).addBox(-4.0F, -4.0F, 0.0F, 8.0F, 8.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 12.1F, -0.1662F, -0.8451F, 0.0F));
        PartDefinition b195 = b18.addOrReplaceChild("limb2_s1", CubeListBuilder.create()
                .texOffs(0, 174).addBox(-8.0F, -8.0F, -1.0F, 16.0F, 16.0F, 25.0F),
                PartPose.offsetAndRotation(-18.9986F, -14.0F, 0.2313F, 0.3072F, 4.7246F, 0.0F));
        PartDefinition b196 = b195.addOrReplaceChild("limb2_s2", CubeListBuilder.create()
                .texOffs(776, 174).addBox(-6.5F, -6.5F, -1.0F, 13.0F, 13.0F, 24.0F)
                .texOffs(854, 251).addBox(-7.5F, -6.5795F, -6.5F, 15.0F, 15.0F, 13.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 23.0F, -0.0737F, 0.0F, 0.0F));
        PartDefinition b197 = b196.addOrReplaceChild("limb2_s3", CubeListBuilder.create()
                .texOffs(480, 215).addBox(-5.5F, -5.5F, -1.0F, 11.0F, 11.0F, 23.0F)
                .texOffs(890, 282).addBox(-6.5F, -7.2867F, -5.5F, 13.0F, 13.0F, 11.0F)
                .texOffs(402, 282).addBox(-7.0F, -12.54F, 5.6F, 14.0F, 10.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 22.0F, -0.2588F, 0.0F, 0.0F));
        PartDefinition b198 = b197.addOrReplaceChild("limb2_s4", CubeListBuilder.create()
                .texOffs(274, 251).addBox(-4.5F, -4.5F, -1.0F, 9.0F, 9.0F, 21.0F)
                .texOffs(546, 310).addBox(-5.5F, -4.745F, -4.5F, 11.0F, 11.0F, 9.0F)
                .texOffs(510, 251).addBox(-8.0F, -12.26F, 3.4F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 21.0F, -0.3637F, 0.0F, 0.0F));
        PartDefinition b199 = b198.addOrReplaceChild("limb2_s5", CubeListBuilder.create()
                .texOffs(202, 282).addBox(-3.5F, -3.5F, -1.0F, 7.0F, 7.0F, 19.0F)
                .texOffs(772, 310).addBox(-4.5F, -4.2435F, -3.5F, 9.0F, 9.0F, 7.0F)
                .texOffs(402, 282).addBox(-7.0F, -10.54F, 3.2F, 14.0F, 10.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 19.0F, -0.3299F, 0.0F, 0.0F));
        PartDefinition b200 = b199.addOrReplaceChild("limb2_s6", CubeListBuilder.create()
                .texOffs(266, 310).addBox(-2.5F, -2.5F, -1.0F, 5.0F, 5.0F, 17.0F)
                .texOffs(510, 251).addBox(-8.0F, -10.26F, 1.0F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 17.0F, -0.377F, 0.0F, 0.0F));
        b200.addOrReplaceChild("limb2_cur5", CubeListBuilder.create()
                .texOffs(668, 215).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 34.0F, 0.0F)
                .texOffs(822, 129).addBox(0.0F, 0.0F, -6.0F, 0.0F, 30.0F, 12.0F),
                PartPose.offsetAndRotation(1.2512F, 2.5F, 4.5F, 1.6254F, 0.472F, 1.6904F));
        PartDefinition b202 = b199.addOrReplaceChild("limb2_f4", CubeListBuilder.create()
                .texOffs(962, 282).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 20.0F)
                .texOffs(378, 310).addBox(-6.0F, -6.32F, 10.0F, 12.0F, 9.0F, 12.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 9.35F, -0.0833F, 0.8533F, 0.0F));
        b202.addOrReplaceChild("limb2_f4_cur", CubeListBuilder.create()
                .texOffs(904, 215).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 32.0F, 0.0F)
                .texOffs(198, 174).addBox(0.0F, 0.0F, -6.0F, 0.0F, 28.0F, 12.0F),
                PartPose.offsetAndRotation(0.0F, 1.5F, 15.0F, 1.1935F, 1.0105F, 1.7406F));
        b199.addOrReplaceChild("limb2_cur4", CubeListBuilder.create()
                .texOffs(420, 251).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 30.0F, 0.0F)
                .texOffs(678, 174).addBox(0.0F, 0.0F, -6.0F, 0.0F, 26.0F, 12.0F),
                PartPose.offsetAndRotation(-1.3068F, 3.5F, 5.1F, 2.4092F, 0.174F, 2.9871F));
        PartDefinition b205 = b198.addOrReplaceChild("limb2_f3", CubeListBuilder.create()
                .texOffs(134, 310).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 18.0F)
                .texOffs(510, 251).addBox(-8.0F, -7.76F, 6.4F, 16.0F, 12.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 10.45F, -0.1053F, -0.7851F, 0.0F));
        b205.addOrReplaceChild("limb2_f3_cur", CubeListBuilder.create()
                .texOffs(70, 282).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 28.0F, 0.0F)
                .texOffs(32, 215).addBox(0.0F, 0.0F, -6.0F, 0.0F, 24.0F, 12.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 13.5F, 2.7673F, -0.0934F, 2.8863F));
        b198.addOrReplaceChild("limb2_cur3", CubeListBuilder.create()
                .texOffs(346, 282).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 26.0F, 0.0F)
                .texOffs(692, 215).addBox(0.0F, 0.0F, -6.0F, 0.0F, 22.0F, 12.0F),
                PartPose.offsetAndRotation(1.3777F, 4.5F, 5.7F, 2.7062F, -0.4529F, -2.9408F));
        PartDefinition b208 = b197.addOrReplaceChild("limb2_f2", CubeListBuilder.create()
                .texOffs(90, 310).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 16.0F)
                .texOffs(378, 310).addBox(-6.0F, -6.32F, 6.8F, 12.0F, 9.0F, 12.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 11.55F, -0.2047F, 0.7616F, 0.0F));
        b208.addOrReplaceChild("limb2_f2_cur", CubeListBuilder.create()
                .texOffs(938, 282).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 24.0F, 0.0F)
                .texOffs(928, 215).addBox(0.0F, 0.0F, -6.0F, 0.0F, 20.0F, 12.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 12.0F, 1.9324F, 1.3321F, 1.9599F));
        b197.addOrReplaceChild("limb2_cur2", CubeListBuilder.create()
                .texOffs(310, 310).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 22.0F, 0.0F)
                .texOffs(444, 251).addBox(0.0F, 0.0F, -6.0F, 0.0F, 18.0F, 12.0F),
                PartPose.offsetAndRotation(-1.6813F, 5.5F, 6.3F, 3.0982F, 0.9475F, 3.1064F));
        b196.addOrReplaceChild("limb2_f1", CubeListBuilder.create()
                .texOffs(222, 310).addBox(-4.0F, -4.0F, 0.0F, 8.0F, 8.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 12.1F, -0.2376F, -0.5911F, 0.0F));
        b17.addOrReplaceChild("knot", CubeListBuilder.create()
                .texOffs(586, 310).addBox(-7.0F, -7.0F, -4.0F, 14.0F, 14.0F, 6.0F)
                .texOffs(934, 310).addBox(-4.0F, -4.0F, 1.0F, 8.0F, 8.0F, 4.0F)
                .texOffs(18, 333).addBox(-9.0F, -9.0F, -3.0F, 18.0F, 4.0F, 4.0F),
                PartPose.offsetAndRotation(-7.2577F, -12.0F, -29.1089F, 3.1416F, -0.2443F, 3.1416F));

        return LayerDefinition.create(mesh, 1024, 1024);
    }
    // >>> GENERATED GEOMETRY

    @Override
    public ModelPart root() {
        return this.root;
    }

    /**
     * Never vanilla's red damage flash. It tints the whole twelve-block tree at once, which reads
     * as a mob being hurt, not timber being struck. The tree answers a hit with its own effects
     * instead: the crown shudders (see {@link #poseFrom}) and splinters fly from the bole.
     */
    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
        super.renderToBuffer(poseStack, buffer, light, OverlayTexture.NO_OVERLAY, color);
    }

    @Override
    public void setupAnim(WhompingWillowEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        float partial = ageInTicks - entity.tickCount;
        entity.rigInput(this.input, partial, true);
        poseFrom(this.input, entity.hurtTime > 0 ? entity.hurtTime - partial : 0.0F,
                entity.deathTime > 0 ? entity.deathTime + partial : 0.0F);
    }

    /**
     * Poses every bone. Split out of {@link #setupAnim} so the parity harness can drive it without
     * a live entity.
     *
     * @param hurt  remaining hurt ticks (10 down to 0), fractional
     * @param death death ticks so far, fractional; 0 while alive
     */
    public void poseFrom(WhompingWillowRig.Input in, float hurt, float death) {
        WhompingWillowRig.pose(in, this.rot);
        AttackState state = in.state;
        float env = WhompingWillowRig.envelope(state, in.tick);

        // Hurt shudder through the crown. Only the boughs: the striking limbs must stay where the
        // server thinks they are.
        if (hurt > 0.0F) {
            float h = hurt / 10.0F;
            float shake = (float) Math.sin(hurt * 1.9F) * h * h * 0.07F;
            for (int[] bough : WhompingWillowRig.BOUGH) {
                for (int k = 0; k < bough.length; k++) {
                    this.rot[bough[k] * 3 + 2] += shake * (0.6F + k * 0.5F);
                    this.rot[bough[k] * 3] += shake * 0.5F;
                }
            }
        }

        // The maw: gapes on waking, snaps on every strike, hangs when limp.
        float jaw = 0.0F;
        if (state == AttackState.WAKE) {
            jaw = (1.0F - in.tick / WhompingWillowRig.WAKE_DURATION) * 0.55F;
        } else if (state == AttackState.DORMANT || state == AttackState.FROZEN) {
            jaw = 0.0F;
        } else {
            jaw = 0.12F + env * 0.25F + (in.idle ? (float) Math.sin(in.age * 0.09F) * 0.04F : 0.0F);
        }
        this.rot[WhompingWillowRig.MAW_JAW * 3] += jaw;

        if (death > 0.0F) {
            death(death);
        }

        for (int b = 0; b < WhompingWillowRig.BONES; b++) {
            ModelPart p = this.parts[b];
            p.xRot = this.rot[b * 3];
            p.yRot = this.rot[b * 3 + 1];
            p.zRot = this.rot[b * 3 + 2];
        }
        hangCurtains(in);
    }

    /**
     * Keeps every streamer hanging straight down whatever the branch above it is doing, and shortens
     * it where it would otherwise sink into the ground under a slammed limb.
     *
     * <p>Plumb means: the curtain bone's local +Y must be the model's down axis expressed in the
     * parent bone's frame, v = H^T (0, 1, 0). ModelPart composes Rz * Ry * Rx; holding the curtain's
     * own rest yaw y0 fixed, Rz(z) Ry(y0) Rx(x) (0, 1, 0) = v solves in closed form:
     * sin x = v.z / cos y0, then z is the angle that turns (sin y0 sin x, cos x) onto (v.x, v.y).
     */
    private void hangCurtains(WhompingWillowRig.Input in) {
        WhompingWillowRig.fkAll(this.rot, this.world);
        int[] curtains = WhompingWillowRig.CURTAIN;
        for (int i = 0; i < curtains.length; i++) {
            int c = curtains[i];
            int p = WhompingWillowRig.PARENT[c] * 12;
            double vx = this.world[p + 3], vy = this.world[p + 4], vz = this.world[p + 5];
            float y0 = WhompingWillowRig.REST[c * 3 + 1];
            double cy = Math.cos(y0);
            if (Math.abs(cy) < 0.2) cy = Math.copySign(0.2, cy);
            double sx = Math.max(-1.0, Math.min(1.0, vz / cy));
            double x = Math.asin(sx);
            double w0 = Math.sin(y0) * sx, w1 = Math.cos(x);
            double z = Math.atan2(vy, vx) - Math.atan2(w1, w0);

            float sway = 0.0F, swayZ = 0.0F;
            if (in.idle) {
                float a = in.age * 0.06F + i * 1.37F;
                sway = (float) Math.sin(a) * 0.05F;
                swayZ = (float) Math.cos(a * 0.8F + 0.4F) * 0.04F;
            }
            ModelPart part = this.parts[c];
            part.xRot = (float) x + sway;
            part.yRot = y0;
            part.zRot = (float) z + swayZ;

            // Height of the curtain's pivot above the feet, blocks (same transform as the rig).
            double py = this.world[p + 10] + this.world[p + 3] * part.x + this.world[p + 4] * part.y
                    + this.world[p + 5] * part.z;
            double h = (1.501 - py / 16.0) * WhompingWillowRig.MODEL_SCALE;
            float len = WhompingWillowRig.curtainLength(i);
            float room = (float) h - 0.06F;
            part.yScale = room >= len ? 1.0F : Math.max(0.08F, room / len);
        }
    }

    /**
     * Falling. {@code LivingEntityRenderer} already tips a dying mob over; this adds what mass
     * would: every limb goes limp and trails, and the crown lags furthest.
     */
    private void death(float death) {
        float t = Math.min(1.0F, death / 20.0F);
        float fall = t * t;
        for (int[] limb : WhompingWillowRig.LIMB) {
            for (int k = 0; k < limb.length; k++) {
                this.rot[limb[k] * 3] -= fall * (0.15F + k * 0.08F);
            }
        }
        for (int[] bough : WhompingWillowRig.BOUGH) {
            for (int k = 0; k < bough.length; k++) {
                this.rot[bough[k] * 3] -= fall * 0.12F * (k + 1);
            }
        }
        this.rot[WhompingWillowRig.MAW_JAW * 3] += fall * 0.7F;
    }
}
