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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * A Hogwarts school robe as the films cut it: open down the front over the uniform, sleeves that
 * flare at the wrist, a hood folded down the back and a skirt to the shins.
 *
 * <p>The armour layer only shows body and arms for a chest piece, so the skirt hangs off
 * {@code body} as five waist-hinged panels: a front panel over each leg, one at the back and one on
 * each side. The layer never runs {@code setupAnim} on an armour model, it only copies the wearer's
 * pose in, so {@link #drape()} turns those copied leg angles into panel angles just before drawing.
 * A front panel follows its own leg only when that leg swings forward and the back panel follows
 * whichever leg is further back, which keeps every leg behind cloth without a panel ever cutting
 * through it. Each angle is taken relative to the body's own tilt so the skirt keeps hanging when
 * the wearer crouches, and the hinges slide forward with the crouch to stay in front of the legs.
 *
 * <p>The texture is drawn at twice the box-UV density (128x128 for a 64x64 layout).
 */
@OnlyIn(Dist.CLIENT)
public class HouseRobeModel<T extends LivingEntity> extends HumanoidModel<T> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "house_robe"), "main");

    private static final float WAIST = 12.0F;
    private static final float FRONT_X = 2.65F;
    private static final float FRONT_Z = -2.15F;
    private static final float BACK_Z = 2.15F;
    private static final float SIDE_X = 4.15F;

    private final ModelPart hood;
    private final ModelPart frontRight;
    private final ModelPart frontLeft;
    private final ModelPart back;
    private final ModelPart sideRight;
    private final ModelPart sideLeft;

    public HouseRobeModel(ModelPart root) {
        super(root);
        this.hood = this.body.getChild("hood");
        this.frontRight = this.body.getChild("skirt_front_right");
        this.frontLeft = this.body.getChild("skirt_front_left");
        this.back = this.body.getChild("skirt_back");
        this.sideRight = this.body.getChild("skirt_side_right");
        this.sideLeft = this.body.getChild("skirt_side_left");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        CubeDeformation robe = new CubeDeformation(0.6F);

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, robe), PartPose.ZERO);

        // The hood lies folded on the upper back, its lining showing at the top.
        body.addOrReplaceChild("hood", CubeListBuilder.create()
                .texOffs(0, 52).addBox(-3.5F, 0.0F, 0.0F, 7.0F, 5.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, -0.4F, 2.4F, -0.12F, 0.0F, 0.0F));

        // Trimmed sideways only, so the panels stay 1 thick and their UVs stay whole texels; the 1.5 gap
        // between them is the robe hanging open over the trousers.
        CubeDeformation half = new CubeDeformation(-0.1F, 0.0F, 0.0F);
        body.addOrReplaceChild("skirt_front_right", CubeListBuilder.create()
                .texOffs(0, 40).addBox(-2.0F, 0.0F, -0.5F, 4.0F, 10.0F, 1.0F, half),
                PartPose.offset(-FRONT_X, WAIST, FRONT_Z));
        body.addOrReplaceChild("skirt_front_left", CubeListBuilder.create()
                .texOffs(10, 40).addBox(-2.0F, 0.0F, -0.5F, 4.0F, 10.0F, 1.0F, half),
                PartPose.offset(FRONT_X, WAIST, FRONT_Z));
        body.addOrReplaceChild("skirt_back", CubeListBuilder.create()
                .texOffs(20, 40).addBox(-4.0F, 0.0F, -0.5F, 8.0F, 10.0F, 1.0F, new CubeDeformation(0.6F, 0.0F, 0.0F)),
                PartPose.offset(0.0F, WAIST, BACK_Z));
        CubeDeformation side = new CubeDeformation(0.0F, 0.0F, 0.6F);
        body.addOrReplaceChild("skirt_side_right", CubeListBuilder.create()
                .texOffs(38, 40).addBox(-0.5F, 0.0F, -2.0F, 1.0F, 10.0F, 4.0F, side),
                PartPose.offset(-SIDE_X, WAIST, 0.0F));
        body.addOrReplaceChild("skirt_side_left", CubeListBuilder.create()
                .texOffs(48, 40).addBox(-0.5F, 0.0F, -2.0F, 1.0F, 10.0F, 4.0F, side),
                PartPose.offset(SIDE_X, WAIST, 0.0F));

        // Sleeves stop above the hand; the bell cuff overlaps their end and leaves the hand bare.
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F, robe)
                .texOffs(0, 32).addBox(-3.0F, 4.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.9F)),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(40, 16).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F, robe)
                .texOffs(0, 32).mirror().addBox(-1.0F, 4.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.9F)),
                PartPose.offset(5.0F, 2.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    /** One model draws every wearer, so this is set for each wearer just before their robe is drawn. */
    public void setHoodVisible(boolean visible) {
        this.hood.visible = visible;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        drape();
        super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, color);
    }

    private void drape() {
        float lean = this.body.xRot;
        // 0 standing, 1 at the vanilla crouch tilt; a fall-flying or swimming body stays near 0.
        float crouch = Mth.clamp(lean / 0.5F, 0.0F, 1.0F);
        float right = this.rightLeg.xRot;
        float left = this.leftLeg.xRot;

        float frontY = WAIST - 1.5F * crouch;
        float frontZ = FRONT_Z - 2.25F * crouch;
        this.frontRight.setPos(-FRONT_X, frontY, frontZ);
        this.frontLeft.setPos(FRONT_X, frontY, frontZ);
        this.frontRight.xRot = Math.min(right, 0.0F) * 1.05F - lean;
        this.frontLeft.xRot = Math.min(left, 0.0F) * 1.05F - lean;

        this.back.xRot = Math.max(Math.max(right, left), 0.0F) * 1.05F - 0.6F * lean;

        float sideY = WAIST - 1.0F * crouch;
        float sideZ = -2.0F * crouch;
        this.sideRight.setPos(-SIDE_X, sideY, sideZ);
        this.sideLeft.setPos(SIDE_X, sideY, sideZ);
        this.sideRight.xRot = right - lean;
        this.sideLeft.xRot = left - lean;
    }
}
