package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * A striped house scarf, worn in the head slot but wrapped round the neck: one turn round the
 * collar, the long end hanging down the right of the chest and the short end flung back over the
 * left shoulder. The turn stands proud of the robe all round; the robe folds its hood away while a
 * scarf is worn (see {@link HouseRobeModel#setHoodVisible}), or the hood would poke through it.
 *
 * <p>It belongs to the torso, not the head, or it would swivel with every glance. The armour layer
 * only shows head and hat for a helmet, so {@code head} and {@code hat} are left empty and
 * {@link #renderToBuffer} shows {@code body}, which holds nothing but the scarf, whenever the head
 * would have been drawn. The ends swing a little with the stride and keep hanging when the wearer
 * crouches.
 *
 * <p>The texture is drawn at twice the box-UV density (128x64 for a 64x32 layout).
 */
@OnlyIn(Dist.CLIENT)
public class HouseScarfModel<T extends LivingEntity> extends HumanoidModel<T> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "house_scarf"), "main");

    private final ModelPart longEnd;
    private final ModelPart shortEnd;

    public HouseScarfModel(ModelPart root) {
        super(root);
        this.longEnd = this.body.getChild("long_end");
        this.shortEnd = this.body.getChild("short_end");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -0.25F, -2.0F, 8.0F, 2.0F, 4.0F, new CubeDeformation(0.85F, 0.75F, 1.1F)),
                PartPose.ZERO);
        body.addOrReplaceChild("long_end", CubeListBuilder.create()
                .texOffs(24, 0).addBox(-1.0F, 0.0F, -0.5F, 2.0F, 9.0F, 1.0F, new CubeDeformation(0.2F)),
                PartPose.offsetAndRotation(-2.0F, 1.8F, -3.6F, 0.0F, 0.0F, 0.05F));
        body.addOrReplaceChild("short_end", CubeListBuilder.create()
                .texOffs(30, 0).addBox(-1.0F, 0.0F, -0.5F, 2.0F, 7.0F, 1.0F, new CubeDeformation(0.2F)),
                PartPose.offsetAndRotation(2.1F, 1.4F, 3.6F, 0.0F, 0.0F, -0.05F));

        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        this.body.visible = this.head.visible;
        float stride = Math.abs(this.rightLeg.xRot - this.leftLeg.xRot);
        float hang = -0.85F * this.body.xRot;
        this.longEnd.xRot = hang - 0.12F * stride;
        // The short end hangs down the back, so it flaps out behind instead of forward.
        this.shortEnd.xRot = hang + 0.18F * stride;
        super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
