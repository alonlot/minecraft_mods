package com.hogwartsmagic.client.model;

import com.hogwartsmagic.entity.CaveBoatEntity;
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
 * The Locket cave skiff: an old, narrow boat about 1.2 blocks wide and 3.5 long with raked sides, a pointed prow, a low
 * stern, one thwart and a lantern on a short post at the prow. The prow points to +Z (the renderer turns it by the
 * entity yaw only) and y=0 is the bottom of the hull, with +Y down as in every entity model.
 *
 * <p>Three groups so the renderer can treat them differently: {@code hull} (wood and brass, lit by the world),
 * {@code lantern} (the glass, drawn full-bright so the light reads in the dark cave) and {@code water_patch} (a thin
 * slab written only to the depth buffer so water does not show inside the open hull).
 *
 * <p>Texture is 128x64: every wooden box samples the one plank region at the top left, the lantern and brass sit
 * to its right. A placeholder, painted by {@code build_scripts/generate_cave_skiff_texture.py}.
 */
public class CaveSkiffModel extends EntityModel<CaveBoatEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "cave_skiff"), "main");

    private static final float RAKE = 0.2F;
    private static final float BOW_TURN = 0.48F;

    private final ModelPart hull;
    private final ModelPart lantern;
    private final ModelPart waterPatch;

    public CaveSkiffModel(ModelPart root) {
        this.hull = root.getChild("hull");
        this.lantern = root.getChild("lantern");
        this.waterPatch = root.getChild("water_patch");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition hull = root.addOrReplaceChild("hull", CubeListBuilder.create(), PartPose.ZERO);
        hull.addOrReplaceChild("floor", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5.0F, -2.0F, -22.0F, 10.0F, 2.0F, 42.0F), PartPose.ZERO);
        hull.addOrReplaceChild("side_left", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1.0F, -12.0F, -16.0F, 2.0F, 12.0F, 36.0F),
                PartPose.offsetAndRotation(-5.0F, 0.0F, 0.0F, 0.0F, 0.0F, -RAKE));
        hull.addOrReplaceChild("side_right", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1.0F, -12.0F, -16.0F, 2.0F, 12.0F, 36.0F),
                PartPose.offsetAndRotation(5.0F, 0.0F, 0.0F, 0.0F, 0.0F, RAKE));
        hull.addOrReplaceChild("bow_left", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1.0F, -12.0F, 0.0F, 2.0F, 12.0F, 12.0F),
                PartPose.offsetAndRotation(-5.5F, 0.0F, 20.0F, 0.0F, BOW_TURN, 0.0F));
        hull.addOrReplaceChild("bow_right", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1.0F, -12.0F, 0.0F, 2.0F, 12.0F, 12.0F),
                PartPose.offsetAndRotation(5.5F, 0.0F, 20.0F, 0.0F, -BOW_TURN, 0.0F));
        hull.addOrReplaceChild("prow_deck", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.0F, -2.0F, 20.0F, 6.0F, 2.0F, 9.0F), PartPose.ZERO);
        hull.addOrReplaceChild("transom", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5.0F, -6.0F, -22.0F, 10.0F, 6.0F, 2.0F), PartPose.ZERO);
        hull.addOrReplaceChild("thwart", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6.0F, -11.0F, -3.0F, 12.0F, 1.5F, 5.0F), PartPose.ZERO);
        hull.addOrReplaceChild("post", CubeListBuilder.create()
                .texOffs(104, 12).addBox(-1.0F, -16.0F, 22.5F, 2.0F, 14.0F, 2.0F), PartPose.ZERO);
        hull.addOrReplaceChild("lantern_cap", CubeListBuilder.create()
                .texOffs(104, 30).addBox(-3.0F, -24.0F, 20.5F, 6.0F, 1.0F, 6.0F), PartPose.ZERO);

        root.addOrReplaceChild("lantern", CubeListBuilder.create()
                .texOffs(104, 0).addBox(-2.5F, -23.0F, 21.0F, 5.0F, 6.0F, 5.0F), PartPose.ZERO);

        PartDefinition patch = root.addOrReplaceChild("water_patch", CubeListBuilder.create(), PartPose.ZERO);
        patch.addOrReplaceChild("patch_mid", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6.0F, -8.5F, -22.0F, 12.0F, 1.0F, 42.0F), PartPose.ZERO);
        patch.addOrReplaceChild("patch_bow", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.0F, -8.5F, 20.0F, 6.0F, 1.0F, 9.0F), PartPose.ZERO);

        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public void setupAnim(CaveBoatEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
    }

    public void renderHull(PoseStack pose, VertexConsumer buffer, int light, int overlay) {
        this.hull.render(pose, buffer, light, overlay);
    }

    public void renderLantern(PoseStack pose, VertexConsumer buffer, int light, int overlay) {
        this.lantern.render(pose, buffer, light, overlay);
    }

    public void renderWaterPatch(PoseStack pose, VertexConsumer maskBuffer, int light, int overlay) {
        this.waterPatch.render(pose, maskBuffer, light, overlay);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        this.hull.render(pose, buffer, light, overlay, color);
        this.lantern.render(pose, buffer, light, overlay, color);
    }
}
