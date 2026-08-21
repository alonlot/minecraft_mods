package com.hogwartsmagic.client.model;

import com.hogwartsmagic.entity.GringottsCartEntity;
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
import net.minecraft.resources.ResourceLocation;

/**
 * An iron tub on four wheels.
 *
 * <p>Authored upright and un-mirrored on purpose. The broom renderer draws inside a
 * {@code scale(-1, -1, 1)}, which is why the code that rotates its rider has to negate every sign
 * and carries a comment explaining it. Skipping the mirror here means the rider uses the same signs
 * as the cart and that whole class of bug never arrives.
 */
public class GringottsCartModel extends EntityModel<GringottsCartEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "gringotts_cart"), "main");

    private final ModelPart tub;
    private final ModelPart wheels;

    public GringottsCartModel(ModelPart root) {
        this.tub = root.getChild("tub");
        this.wheels = root.getChild("wheels");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition tub = root.addOrReplaceChild("tub", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-9.0F, -4.0F, -7.0F, 18.0F, 2.0F, 14.0F), PartPose.ZERO);
        // Four walls, low enough to see over -- this is a mine tub, not a bathtub.
        tub.addOrReplaceChild("left", CubeListBuilder.create()
                .texOffs(0, 16).addBox(-9.0F, -9.0F, -7.0F, 1.0F, 5.0F, 14.0F), PartPose.ZERO);
        tub.addOrReplaceChild("right", CubeListBuilder.create()
                .texOffs(0, 16).addBox(8.0F, -9.0F, -7.0F, 1.0F, 5.0F, 14.0F), PartPose.ZERO);
        tub.addOrReplaceChild("front", CubeListBuilder.create()
                .texOffs(30, 16).addBox(-8.0F, -9.0F, -7.0F, 16.0F, 5.0F, 1.0F), PartPose.ZERO);
        tub.addOrReplaceChild("back", CubeListBuilder.create()
                .texOffs(30, 16).addBox(-8.0F, -9.0F, 6.0F, 16.0F, 5.0F, 1.0F), PartPose.ZERO);

        PartDefinition wheels = root.addOrReplaceChild("wheels", CubeListBuilder.create(),
                PartPose.ZERO);
        for (int i = 0; i < 4; i++) {
            float x = (i % 2 == 0) ? -9.5F : 8.5F;
            float z = (i < 2) ? -5.0F : 3.0F;
            wheels.addOrReplaceChild("wheel" + i, CubeListBuilder.create()
                    .texOffs(0, 36).addBox(x, -3.0F, z, 1.0F, 4.0F, 4.0F), PartPose.ZERO);
        }

        return LayerDefinition.create(mesh, 64, 48);
    }

    @Override
    public void setupAnim(GringottsCartEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // Nothing articulated: the whole cart is posed by the renderer from the track's frame.
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay,
                               int colour) {
        this.tub.render(pose, buffer, light, overlay, colour);
        this.wheels.render(pose, buffer, light, overlay, colour);
    }
}
