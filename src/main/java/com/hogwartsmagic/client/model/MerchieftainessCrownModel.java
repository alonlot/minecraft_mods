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
 * The merchieftainess's crown, worn on a player's head.
 *
 * <p>All geometry hangs off {@code head} so it follows the vanilla head transform
 * chain — the same rule {@link SortingHatArmorModel} documents. {@code hat} is
 * left out of {@code bodyParts()} for the same reason: vanilla renders it in the
 * torso pass, and anything added there follows the wrong chain.
 *
 * <p>The band is a head-sized box with a deformation rather than a hand-built
 * ring, so it sits flush at any player skin proportion instead of clipping into
 * a slim-armed model's skull.
 */
@OnlyIn(Dist.CLIENT)
public class MerchieftainessCrownModel<T extends LivingEntity> extends HumanoidModel<T> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "merchieftainess_crown"), "main");

    public MerchieftainessCrownModel(ModelPart root) {
        super(root);
    }

    @Override
    protected Iterable<ModelPart> bodyParts() {
        return ImmutableList.of(this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();

        // A circlet round the upper skull, with five points rising off it: one
        // tall at the brow, two at the temples and two low at the back.
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0F, -7.6F, -4.0F, 8.0F, 2.0F, 8.0F, new CubeDeformation(0.55F))
                        .texOffs(33, 0).addBox(-1.0F, -13.0F, -5.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(42, 0).addBox(-5.0F, -11.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(51, 0).addBox(3.0F, -11.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 11).addBox(-3.0F, -10.0F, 3.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(9, 11).addBox(1.0F, -10.0F, 3.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 32);
    }
}
