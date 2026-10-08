package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.boggart.BoggartEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Boggart's own shape, which is to say the lack of one: a hunched column of dark that floats
 * a few inches off the floor, a lumpen head with two faint lights where eyes might be, and six
 * tattered tendrils trailing down to the ground. Everything moves all the time, slowly, because a
 * thing with no shape never settles on one.
 *
 * <p>Hand-built box UV on a 64x64 sheet; build_scripts/generate_boggart_texture.py paints the same
 * packing. Keep the two in step.
 */
public class BoggartModel extends HierarchicalModel<BoggartEntity> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "boggart"), "main");

    private static final int TENDRILS = 6;

    private final ModelPart root;
    private final ModelPart mass;
    private final ModelPart upper;
    private final ModelPart head;
    private final ModelPart[] tendrils = new ModelPart[TENDRILS];

    public BoggartModel(ModelPart root) {
        this.root = root.getChild("root");
        this.mass = this.root.getChild("mass");
        this.upper = this.mass.getChild("upper");
        this.head = this.upper.getChild("head");
        for (int i = 0; i < TENDRILS; i++) {
            this.tendrils[i] = this.mass.getChild("tendril" + i);
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition part = mesh.getRoot();

        // The root floats 6px above the floor; the tendrils reach back down to it.
        PartDefinition root = part.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 18.0F, 0.0F));

        PartDefinition mass = root.addOrReplaceChild("mass", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-5.0F, -14.0F, -4.0F, 10.0F, 14.0F, 8.0F)
                        .texOffs(0, 30).addBox(-7.0F, -12.0F, -1.5F, 3.0F, 3.0F, 3.0F)
                        .texOffs(12, 30).addBox(4.0F, -9.0F, -1.5F, 3.0F, 3.0F, 3.0F),
                PartPose.ZERO);

        PartDefinition upper = mass.addOrReplaceChild("upper", CubeListBuilder.create()
                        .texOffs(36, 0).addBox(-4.0F, -10.0F, -3.0F, 8.0F, 10.0F, 6.0F),
                PartPose.offsetAndRotation(0.0F, -13.0F, 0.0F, 0.15F, 0.0F, 0.0F));

        upper.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(36, 16).addBox(-3.5F, -7.0F, -3.5F, 7.0F, 7.0F, 7.0F),
                PartPose.offsetAndRotation(0.0F, -9.0F, -1.0F, 0.2F, 0.0F, 0.0F));

        for (int i = 0; i < TENDRILS; i++) {
            double angle = (Math.PI * 2.0 * i) / TENDRILS;
            float x = (float) (Math.cos(angle) * 3.5);
            float z = (float) (Math.sin(angle) * 2.8);
            mass.addOrReplaceChild("tendril" + i, CubeListBuilder.create()
                            .texOffs(i * 6, 22).addBox(-1.0F, 0.0F, -0.5F, 2.0F, 6.0F, 1.0F),
                    PartPose.offsetAndRotation(x, 0.0F, z, 0.0F, (float) -angle, 0.0F));
        }

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(BoggartEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        float t = ageInTicks;
        // The whole column leans and turns a little, never still.
        this.root.zRot = Mth.sin(t * 0.07F) * 0.08F;
        this.root.yRot = Mth.sin(t * 0.045F) * 0.12F;
        // Breathing that is too slow and too deep to be breathing.
        this.mass.xScale = 1.0F + Mth.sin(t * 0.09F) * 0.04F;
        this.mass.zScale = 1.0F + Mth.cos(t * 0.09F) * 0.04F;
        this.upper.xRot = 0.15F + Mth.sin(t * 0.11F + 1.0F) * 0.06F;
        this.upper.zRot = Mth.sin(t * 0.08F) * 0.05F;
        // The head follows whoever it is looking at, with a lag, and nods.
        this.head.yRot = netHeadYaw * ((float) Math.PI / 180F) * 0.6F;
        this.head.xRot = 0.2F + headPitch * ((float) Math.PI / 180F) * 0.5F + Mth.sin(t * 0.13F) * 0.05F;
        for (int i = 0; i < TENDRILS; i++) {
            ModelPart tendril = this.tendrils[i];
            tendril.xRot = Mth.sin(t * 0.16F + i * 1.1F) * 0.28F;
            tendril.zRot = Mth.cos(t * 0.12F + i * 0.7F) * 0.22F;
        }
    }

    @Override
    public ModelPart root() {
        return this.root;
    }
}
