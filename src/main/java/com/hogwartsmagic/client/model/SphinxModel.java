package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.SphinxEntity;
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

/**
 * The Sphinx: a lion's body and a woman's head in a striped nemes headdress, with the cobra on the
 * brow. 128x64 box UV; the layout is mirrored in {@code tools/sphinx/paint_sphinx.py}, which paints
 * the texture, so a box moved here has to be moved there too.
 *
 * <p>Two poses, blended by {@link SphinxEntity#lieAmount}: lying as the statues lie, forepaws out in
 * front and haunches folded, and standing, which she only does to fight or to walk home.
 */
public class SphinxModel extends EntityModel<SphinxEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "sphinx"), "main");

    // A lion is long and low: legs nine high under a body twenty-six long. The first draft stood on
    // twelve-high legs under a twenty-two-long body and read as a llama.
    private static final float STAND_BODY_Y = -9.0F;
    private static final float LIE_BODY_Y = -1.0F;
    private static final float STAND_LEG_Y = -9.0F;
    private static final float LIE_LEG_Y = -2.5F;
    private static final float HALF_PI = (float) (Math.PI / 2.0);

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart tail;
    private final ModelPart tailTip;
    private final ModelPart legFrontLeft;
    private final ModelPart legFrontRight;
    private final ModelPart legBackLeft;
    private final ModelPart legBackRight;

    public SphinxModel(ModelPart model) {
        this.root = model.getChild("root");
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.tail = body.getChild("tail");
        this.tailTip = tail.getChild("tail_tip");
        this.legFrontLeft = root.getChild("leg_front_left");
        this.legFrontRight = root.getChild("leg_front_right");
        this.legBackLeft = root.getChild("leg_back_left");
        this.legBackRight = root.getChild("leg_back_right");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        CubeDeformation none = CubeDeformation.NONE;
        PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6.0F, -10.0F, -13.0F, 12.0F, 10.0F, 26.0F, none),
                PartPose.offset(0.0F, STAND_BODY_Y, 0.0F));

        body.addOrReplaceChild("neck", CubeListBuilder.create()
                .texOffs(0, 50).addBox(-3.0F, -5.0F, -3.0F, 6.0F, 5.0F, 6.0F, none),
                // Leaning forward: a head set straight up over the chest read as a llama from the front.
                PartPose.offsetAndRotation(0.0F, -8.0F, -12.0F, 0.5F, 0.0F, 0.0F));

        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(76, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, none),
                PartPose.offset(0.0F, -12.0F, -15.0F));
        head.addOrReplaceChild("nemes", CubeListBuilder.create()
                .texOffs(76, 16).addBox(-5.0F, -9.0F, -4.5F, 10.0F, 4.0F, 10.0F, none)
                .texOffs(116, 16).addBox(-2.0F, -5.0F, 4.0F, 4.0F, 5.0F, 2.0F, none),
                PartPose.ZERO);
        head.addOrReplaceChild("lappet_left", CubeListBuilder.create()
                .texOffs(108, 0).addBox(-7.0F, -5.0F, -3.0F, 3.0F, 11.0F, 2.0F, none),
                PartPose.ZERO);
        head.addOrReplaceChild("lappet_right", CubeListBuilder.create()
                .texOffs(118, 0).addBox(4.0F, -5.0F, -3.0F, 3.0F, 11.0F, 2.0F, none),
                PartPose.ZERO);
        head.addOrReplaceChild("uraeus", CubeListBuilder.create()
                .texOffs(108, 13).addBox(-0.5F, -11.0F, -5.0F, 1.0F, 2.0F, 1.0F, none),
                PartPose.ZERO);

        // Two segments, so the tail can curve: one straight stick read as a stick.
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(80, 36).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 8.0F, none),
                PartPose.offsetAndRotation(0.0F, -6.0F, 13.0F, -1.1F, 0.0F, 0.0F));
        PartDefinition tailTip = tail.addOrReplaceChild("tail_tip", CubeListBuilder.create()
                .texOffs(100, 36).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 7.0F, none),
                PartPose.offset(0.0F, 0.0F, 7.5F));
        tailTip.addOrReplaceChild("tuft", CubeListBuilder.create()
                .texOffs(100, 46).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 3.0F, none),
                PartPose.offset(0.0F, 0.0F, 6.5F));

        leg(root, "leg_front_left", 0, -3.5F, -9.5F);
        leg(root, "leg_front_right", 20, 3.5F, -9.5F);
        leg(root, "leg_back_left", 40, -3.5F, 9.5F);
        leg(root, "leg_back_right", 60, 3.5F, 9.5F);
        return LayerDefinition.create(mesh, 128, 64);
    }

    private static void leg(PartDefinition root, String name, int u, float x, float z) {
        root.addOrReplaceChild(name, CubeListBuilder.create()
                .texOffs(u, 36).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 9.0F, 5.0F, CubeDeformation.NONE),
                PartPose.offset(x, STAND_LEG_Y, z));
    }

    @Override
    public void prepareMobModel(SphinxEntity entity, float limbSwing, float limbSwingAmount, float partialTick) {
        this.lie = entity.lieAmount(partialTick);
        this.angry = entity.isAngry();
    }

    private float lie;
    private boolean angry;

    @Override
    public void setupAnim(SphinxEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        float l = lie;
        float stand = 1.0F - l;

        body.y = Mth.lerp(l, STAND_BODY_Y, LIE_BODY_Y);
        float legY = Mth.lerp(l, STAND_LEG_Y, LIE_LEG_Y);

        // Standing gait: diagonal pairs, as a big cat walks.
        float swing = Mth.cos(limbSwing * 0.6662F) * 0.9F * limbSwingAmount * stand;
        float swingB = Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 0.9F * limbSwingAmount * stand;

        // Lying: forelegs straight out in front, hind legs folded under with the haunches splayed.
        for (ModelPart leg : new ModelPart[] {legFrontLeft, legFrontRight, legBackLeft, legBackRight}) {
            leg.y = legY;
        }
        legFrontLeft.xRot = Mth.lerp(l, swing, -HALF_PI);
        legFrontRight.xRot = Mth.lerp(l, swingB, -HALF_PI);
        legBackLeft.xRot = Mth.lerp(l, swingB, -HALF_PI);
        legBackRight.xRot = Mth.lerp(l, swing, -HALF_PI);
        legBackLeft.zRot = l * 0.35F;
        legBackRight.zRot = -l * 0.35F;
        legFrontLeft.zRot = 0.0F;
        legFrontRight.zRot = 0.0F;

        // The swipe: the right forepaw strikes.
        if (attackTime > 0.0F) {
            float strike = Mth.sin(attackTime * (float) Math.PI);
            legFrontRight.xRot = -1.7F * strike;
            legFrontRight.zRot = 0.2F * strike;
        }

        head.yRot = Mth.clamp(netHeadYaw, -60.0F, 60.0F) * Mth.DEG_TO_RAD;
        head.xRot = Mth.clamp(headPitch, -30.0F, 30.0F) * Mth.DEG_TO_RAD;

        float lash = angry ? 0.55F : 0.18F;
        float rate = angry ? 0.35F : 0.06F;
        // Lying, the tail runs back along the ground and curls to one side; it must not dip under it.
        tail.xRot = Mth.lerp(l, -1.1F, -0.45F) + Mth.cos(ageInTicks * rate * 0.7F) * 0.08F;
        tail.yRot = Mth.lerp(l, 0.0F, 0.7F) + Mth.sin(ageInTicks * rate) * lash;
        // The tip curls: up and out when she stands, round toward her flank when she lies.
        tailTip.xRot = Mth.lerp(l, 0.75F, 0.15F) + Mth.cos(ageInTicks * rate * 0.9F) * 0.12F;
        tailTip.yRot = Mth.lerp(l, 0.0F, 0.8F) + Mth.sin(ageInTicks * rate - 0.8F) * lash * 0.8F;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
