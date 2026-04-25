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
 * Worn Sorting Hat: all visible geometry lives on {@code head}. Vanilla places {@code hat} in
 * {@link HumanoidModel#bodyParts()} (torso pass); extra pieces there follow the wrong transform chain
 * and can stretch into a sky-high column, so we omit {@code hat} from rendering entirely.
 */
@OnlyIn(Dist.CLIENT)
public class SortingHatArmorModel<T extends LivingEntity> extends HumanoidModel<T> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "sorting_hat_armor"), "main");

    public SortingHatArmorModel(ModelPart root) {
        super(root);
    }

    @Override
    protected Iterable<ModelPart> bodyParts() {
        return ImmutableList.of(this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = meshdefinition.getRoot();
        CubeDeformation shell = new CubeDeformation(0.15F);
        CubeDeformation cloth = new CubeDeformation(0.25F);

        // Keep everything under head so the full hat follows the vanilla head transform chain.
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);

        head.addOrReplaceChild(
                "brim",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-5.5F, -8.7F, -5.5F, 11.0F, 1.0F, 11.0F, cloth),
                PartPose.ZERO);

        head.addOrReplaceChild(
                "brim_front_drop",
                CubeListBuilder.create()
                        .texOffs(0, 13).addBox(-4.5F, -0.6F, -1.0F, 9.0F, 1.0F, 4.0F, cloth),
                PartPose.offsetAndRotation(0.0F, -8.1F, -4.6F, 0.42F, 0.0F, 0.0F));

        head.addOrReplaceChild(
                "crown_base",
                CubeListBuilder.create()
                        .texOffs(0, 24).addBox(-4.0F, -6.0F, -4.0F, 8.0F, 6.0F, 8.0F, shell),
                PartPose.offsetAndRotation(0.0F, -8.4F, 0.2F, -0.08F, 0.0F, 0.04F));

        head.addOrReplaceChild(
                "brow_ridge",
                CubeListBuilder.create()
                        .texOffs(40, 0).addBox(-3.0F, -1.0F, -1.0F, 6.0F, 2.0F, 2.0F, shell),
                PartPose.offsetAndRotation(0.0F, -11.8F, -3.9F, 0.12F, 0.0F, 0.0F));

        head.addOrReplaceChild(
                "crown_mid",
                CubeListBuilder.create()
                        .texOffs(32, 24).addBox(-3.0F, -5.0F, -3.0F, 6.0F, 5.0F, 6.0F, shell),
                PartPose.offsetAndRotation(0.2F, -13.7F, -0.2F, -0.18F, 0.0F, 0.1F));

        head.addOrReplaceChild(
                "crown_top",
                CubeListBuilder.create()
                        .texOffs(0, 40).addBox(-2.0F, -4.0F, -2.0F, 4.0F, 4.0F, 4.0F, shell),
                PartPose.offsetAndRotation(0.7F, -18.0F, 0.0F, -0.28F, 0.0F, 0.22F));

        head.addOrReplaceChild(
                "tip",
                CubeListBuilder.create()
                        .texOffs(32, 40).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F, shell),
                PartPose.offsetAndRotation(1.2F, -21.4F, 0.0F, -0.4F, -0.08F, 0.36F));

        head.addOrReplaceChild(
                "crease_left",
                CubeListBuilder.create()
                        .texOffs(48, 40).addBox(-1.0F, -1.0F, -0.6F, 2.0F, 2.0F, 1.0F, CubeDeformation.NONE),
                PartPose.offsetAndRotation(-1.7F, -11.7F, -4.05F, 0.08F, -0.3F, -0.25F));

        head.addOrReplaceChild(
                "crease_right",
                CubeListBuilder.create()
                        .texOffs(48, 45).addBox(-1.0F, -1.0F, -0.6F, 2.0F, 2.0F, 1.0F, CubeDeformation.NONE),
                PartPose.offsetAndRotation(1.7F, -11.7F, -4.05F, 0.08F, 0.3F, 0.25F));

        // Keep vanilla "hat" part so HumanoidModel's constructor still resolves; it is not in bodyParts() so it never draws.
        return LayerDefinition.create(meshdefinition, 64, 64);
    }
}
