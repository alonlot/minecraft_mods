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
 * The Gringotts cart. <b>Generated - edit {@code build_scripts/generate_gringotts_cart.py}.</b>
 *
 * <p>A Victorian mine tub rather than a minecart, which is what the films and Hogwarts Legacy both
 * draw: dark iron plate, a scooped waist you climb over, flared ends, spoked wheels outboard of the
 * body, a brass headlamp on the prow and a burgundy bench. The waist is the part that does the
 * work - a straight-sided box reads as a crate whatever you paint on it.
 *
 * <p><b>y=0 in the source model is the bottom of the wheels</b>, so the renderer positions the cart
 * by the railhead and nothing else has to know how tall it is. The corridor lays its deck two
 * blocks below the cart line, which is where {@link com.hogwartsmagic.client.GringottsCartRenderer}
 * gets its one offset from.
 *
 * <p>Authored un-mirrored in X on purpose. The broom renderer draws inside a
 * {@code scale(-1, -1, 1)}, which is why the code that rotates its rider has to negate every sign
 * and carries a comment explaining it. Skipping the mirror here means the rider uses the same signs
 * as the cart and that whole class of bug never arrives.
 *
 * <p>The wheels turn. It is one line in {@code setupAnim} and it is the difference between a cart
 * that is moving and a cart that is being dragged.
 */
public class GringottsCartModel extends EntityModel<GringottsCartEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "gringotts_cart"), "main");

    /** Rolling radius in model units, so the wheels turn at the speed the cart is going. */
    private static final float WHEEL_RADIUS = 5.5F;

    private final ModelPart body;
    private final ModelPart wheels;
    private final ModelPart lamp;

    public GringottsCartModel(ModelPart root) {
        this.body = root.getChild("body");
        this.wheels = root.getChild("wheels");
        this.lamp = root.getChild("lamp");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create(), PartPose.ZERO);
        body.addOrReplaceChild("frame_left", CubeListBuilder.create()
                .texOffs(60, 0).addBox(-11.0F, -7.0F, -15.0F, 3.0F, 3.0F, 30.0F), PartPose.ZERO);
        body.addOrReplaceChild("frame_right", CubeListBuilder.create()
                .texOffs(60, 0).addBox(8.0F, -7.0F, -15.0F, 3.0F, 3.0F, 30.0F), PartPose.ZERO);
        body.addOrReplaceChild("axle_front", CubeListBuilder.create()
                .texOffs(0, 77).addBox(-11.0F, -9.0F, -10.0F, 22.0F, 2.0F, 4.0F), PartPose.ZERO);
        body.addOrReplaceChild("axle_back", CubeListBuilder.create()
                .texOffs(0, 77).addBox(-11.0F, -9.0F, 6.0F, 22.0F, 2.0F, 4.0F), PartPose.ZERO);
        body.addOrReplaceChild("floor", CubeListBuilder.create()
                .texOffs(0, 34).addBox(-9.0F, -9.0F, -14.0F, 18.0F, 2.0F, 28.0F), PartPose.ZERO);
        body.addOrReplaceChild("side_left", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-10.0F, -15.0F, -14.0F, 2.0F, 6.0F, 28.0F), PartPose.ZERO);
        body.addOrReplaceChild("side_right", CubeListBuilder.create()
                .texOffs(0, 0).addBox(8.0F, -15.0F, -14.0F, 2.0F, 6.0F, 28.0F), PartPose.ZERO);
        body.addOrReplaceChild("flare_lf", CubeListBuilder.create()
                .texOffs(181, 34).addBox(-10.0F, -21.0F, -14.0F, 2.0F, 6.0F, 8.0F), PartPose.ZERO);
        body.addOrReplaceChild("flare_rf", CubeListBuilder.create()
                .texOffs(181, 34).addBox(8.0F, -21.0F, -14.0F, 2.0F, 6.0F, 8.0F), PartPose.ZERO);
        body.addOrReplaceChild("flare_lb", CubeListBuilder.create()
                .texOffs(201, 34).addBox(-10.0F, -20.0F, 6.0F, 2.0F, 5.0F, 8.0F), PartPose.ZERO);
        body.addOrReplaceChild("flare_rb", CubeListBuilder.create()
                .texOffs(201, 34).addBox(8.0F, -20.0F, 6.0F, 2.0F, 5.0F, 8.0F), PartPose.ZERO);
        body.addOrReplaceChild("prow", CubeListBuilder.create()
                .texOffs(36, 64).addBox(-8.0F, -19.0F, -16.0F, 16.0F, 10.0F, 2.0F), PartPose.ZERO);
        body.addOrReplaceChild("stern", CubeListBuilder.create()
                .texOffs(0, 64).addBox(-8.0F, -20.0F, 14.0F, 16.0F, 11.0F, 2.0F), PartPose.ZERO);
        body.addOrReplaceChild("band_left", CubeListBuilder.create()
                .texOffs(126, 0).addBox(-10.5F, -14.0F, -14.0F, 3.0F, 2.0F, 28.0F), PartPose.ZERO);
        body.addOrReplaceChild("band_right", CubeListBuilder.create()
                .texOffs(126, 0).addBox(7.5F, -14.0F, -14.0F, 3.0F, 2.0F, 28.0F), PartPose.ZERO);
        body.addOrReplaceChild("bench", CubeListBuilder.create()
                .texOffs(72, 64).addBox(-8.0F, -13.0F, -4.0F, 16.0F, 1.0F, 10.0F), PartPose.ZERO);
        body.addOrReplaceChild("cushion", CubeListBuilder.create()
                .texOffs(124, 64).addBox(-8.0F, -14.0F, -4.0F, 16.0F, 1.0F, 10.0F), PartPose.ZERO);
        body.addOrReplaceChild("fender_lf_a", CubeListBuilder.create()
                .texOffs(150, 34).addBox(-12.0F, -15.0F, -15.0F, 1.5F, 2.0F, 14.0F), PartPose.ZERO);
        body.addOrReplaceChild("fender_lf_b", CubeListBuilder.create()
                .texOffs(52, 77).addBox(-12.0F, -13.0F, -16.0F, 1.5F, 4.0F, 2.0F), PartPose.ZERO);
        body.addOrReplaceChild("fender_lf_c", CubeListBuilder.create()
                .texOffs(59, 77).addBox(-12.0F, -13.0F, -2.0F, 1.5F, 4.0F, 2.0F), PartPose.ZERO);
        body.addOrReplaceChild("fender_lb_a", CubeListBuilder.create()
                .texOffs(150, 34).addBox(-12.0F, -15.0F, 1.0F, 1.5F, 2.0F, 14.0F), PartPose.ZERO);
        body.addOrReplaceChild("fender_lb_b", CubeListBuilder.create()
                .texOffs(52, 77).addBox(-12.0F, -13.0F, 0.0F, 1.5F, 4.0F, 2.0F), PartPose.ZERO);
        body.addOrReplaceChild("fender_lb_c", CubeListBuilder.create()
                .texOffs(59, 77).addBox(-12.0F, -13.0F, 14.0F, 1.5F, 4.0F, 2.0F), PartPose.ZERO);
        body.addOrReplaceChild("fender_rf_a", CubeListBuilder.create()
                .texOffs(150, 34).addBox(10.5F, -15.0F, -15.0F, 1.5F, 2.0F, 14.0F), PartPose.ZERO);
        body.addOrReplaceChild("fender_rf_b", CubeListBuilder.create()
                .texOffs(52, 77).addBox(10.5F, -13.0F, -16.0F, 1.5F, 4.0F, 2.0F), PartPose.ZERO);
        body.addOrReplaceChild("fender_rf_c", CubeListBuilder.create()
                .texOffs(59, 77).addBox(10.5F, -13.0F, -2.0F, 1.5F, 4.0F, 2.0F), PartPose.ZERO);
        body.addOrReplaceChild("fender_rb_a", CubeListBuilder.create()
                .texOffs(150, 34).addBox(10.5F, -15.0F, 1.0F, 1.5F, 2.0F, 14.0F), PartPose.ZERO);
        body.addOrReplaceChild("fender_rb_b", CubeListBuilder.create()
                .texOffs(52, 77).addBox(10.5F, -13.0F, 0.0F, 1.5F, 4.0F, 2.0F), PartPose.ZERO);
        body.addOrReplaceChild("fender_rb_c", CubeListBuilder.create()
                .texOffs(59, 77).addBox(10.5F, -13.0F, 14.0F, 1.5F, 4.0F, 2.0F), PartPose.ZERO);
        body.addOrReplaceChild("brake_stand", CubeListBuilder.create()
                .texOffs(80, 77).addBox(8.0F, -17.0F, 8.0F, 2.0F, 2.0F, 3.0F), PartPose.ZERO);
        body.addOrReplaceChild("brake_lever", CubeListBuilder.create()
                .texOffs(216, 64).addBox(8.5F, -22.0F, 8.0F, 1.0F, 5.0F, 4.0F), PartPose.ZERO);

        PartDefinition wheels = root.addOrReplaceChild("wheels",
                CubeListBuilder.create(), PartPose.offset(0.0F, -5.5F, 0.0F));
        wheels.addOrReplaceChild("wheel_0", CubeListBuilder.create()
                .texOffs(92, 34).addBox(-12.0F, -5.5F, -13.5F, 1.0F, 11.0F, 11.0F), PartPose.ZERO);
        wheels.addOrReplaceChild("wheel_1", CubeListBuilder.create()
                .texOffs(92, 34).addBox(11.0F, -5.5F, -13.5F, 1.0F, 11.0F, 11.0F), PartPose.ZERO);
        wheels.addOrReplaceChild("wheel_2", CubeListBuilder.create()
                .texOffs(92, 34).addBox(-12.0F, -5.5F, 2.5F, 1.0F, 11.0F, 11.0F), PartPose.ZERO);
        wheels.addOrReplaceChild("wheel_3", CubeListBuilder.create()
                .texOffs(92, 34).addBox(11.0F, -5.5F, 2.5F, 1.0F, 11.0F, 11.0F), PartPose.ZERO);

        PartDefinition lamp = root.addOrReplaceChild("lamp",
                CubeListBuilder.create(), PartPose.ZERO);
        lamp.addOrReplaceChild("lamp_bracket", CubeListBuilder.create()
                .texOffs(66, 77).addBox(-2.0F, -16.0F, -18.0F, 4.0F, 3.0F, 3.0F), PartPose.ZERO);
        lamp.addOrReplaceChild("lamp_body", CubeListBuilder.create()
                .texOffs(116, 34).addBox(-5.0F, -18.0F, -24.0F, 10.0F, 10.0F, 7.0F), PartPose.ZERO);
        lamp.addOrReplaceChild("lamp_lens", CubeListBuilder.create()
                .texOffs(226, 64).addBox(-4.0F, -17.0F, -25.0F, 8.0F, 8.0F, 1.0F), PartPose.ZERO);
        lamp.addOrReplaceChild("lamp_hood", CubeListBuilder.create()
                .texOffs(176, 64).addBox(-6.0F, -20.0F, -25.0F, 12.0F, 2.0F, 8.0F), PartPose.ZERO);


        return LayerDefinition.create(mesh, 256, 128);
    }

    @Override
    public void setupAnim(GringottsCartEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // Distance travelled over the rolling radius. Each wheel is its own part around a shared
        // pivot, so rotating the group turns all four together without any of them orbiting.
        this.wheels.xRot = (float) (entity.rolledDistance() / WHEEL_RADIUS);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay,
                               int colour) {
        this.body.render(pose, buffer, light, overlay, colour);
        this.wheels.render(pose, buffer, light, overlay, colour);
        this.lamp.render(pose, buffer, light, overlay, colour);
    }
}
