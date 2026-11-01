package com.hogwartsmagic.client.model;

import com.google.common.collect.ImmutableList;
import com.hogwartsmagic.ExampleMod;
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
 * The plain black pointed hat of a first year's uniform: a wide brim and a cone of seven tiers, each
 * a child of the one below so the felt bends back further toward the tip.
 *
 * <p>All of it hangs off {@code head}, and {@code hat} is kept out of {@link #bodyParts()}, for the
 * reason {@link SortingHatArmorModel} gives. The texture is drawn at twice the box-UV density
 * (128x64 for a 64x32 layout).
 */
@OnlyIn(Dist.CLIENT)
public class PointedHatModel<T extends LivingEntity> extends HumanoidModel<T> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "pointed_hat"), "main");

    private static final int[] TIER_WIDTHS = {8, 7, 6, 5, 4, 3, 2};
    private static final int[][] TIER_UV = {{0, 11}, {32, 11}, {0, 21}, {24, 21}, {44, 21}, {40, 0}, {52, 0}};
    /** Sideways growth per tier: the base clears the skin's hair layer and the cone tapers smoothly to the tip. */
    private static final float[] TIER_SPREAD = {0.75F, 0.6F, 0.5F, 0.4F, 0.3F, 0.2F, 0.1F};
    private static final float[] TIER_BEND = {0.0F, -0.04F, -0.05F, -0.07F, -0.1F, -0.14F, -0.2F};
    /** The upper tiers are squashed from 2 to 1.4 tall so the cone stands about one head above the brim. */
    private static final float TIER_SQUASH = 0.3F;

    public PointedHatModel(ModelPart root) {
        super(root);
    }

    @Override
    protected Iterable<ModelPart> bodyParts() {
        return ImmutableList.of(this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        // head's own pose is overwritten with the wearer's every frame, so the offset lives on the brim.
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition tier = head.addOrReplaceChild("brim", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5.0F, -0.5F, -5.0F, 10.0F, 1.0F, 10.0F, new CubeDeformation(1.0F, 0.0F, 1.0F)),
                PartPose.offset(0.0F, -7.0F, 0.0F));

        // The brim rests on the forehead a pixel below the hairline and the base tier sits down over the
        // whole top of the head, wide and tall enough to swallow the skin's hair layer (4.5 out, 8.5 up),
        // its bottom flush with the brim's. Each tier above starts where the last one ends and bends back
        // a little more than the one below it.
        for (int i = 0; i < TIER_WIDTHS.length; i++) {
            int w = TIER_WIDTHS[i];
            boolean base = i == 0;
            float spread = TIER_SPREAD[i];
            float y = base ? 0.0F : (i == 1 ? -3.5F + TIER_SQUASH : -2.0F + 2.0F * TIER_SQUASH);
            tier = tier.addOrReplaceChild("crown_" + i, CubeListBuilder.create()
                    .texOffs(TIER_UV[i][0], TIER_UV[i][1])
                    .addBox(-w / 2.0F, base ? -3.0F : -2.0F, -w / 2.0F, w, base ? 3.0F : 2.0F, w,
                            new CubeDeformation(spread, base ? 0.5F : -TIER_SQUASH, spread)),
                    PartPose.offsetAndRotation(0.0F, y, 0.0F, TIER_BEND[i], 0.0F, i >= 4 ? 0.04F : 0.0F));
        }

        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }
}
