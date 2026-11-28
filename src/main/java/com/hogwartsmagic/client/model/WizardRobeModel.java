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
 * The coat of the Auror and Death Eater robes: an ankle-length robe whose skirt hangs from the waist as
 * five panels, the way {@link HouseRobeModel} does it (see there for how the panels follow the legs).
 * The Auror's greatcoat adds a shoulder mantle and a stand collar; the Death Eater's robe adds a cloak
 * hung from the shoulders that streams out behind a running wearer.
 *
 * <p>The cloak is drawn after the skirt panels have been posed: its angle is the larger of the wearer's
 * running flare and the least angle that keeps every swinging panel corner in front of it, so a leg
 * kicking back never pushes cloth through the cloak.
 *
 * <p>The texture is drawn at twice the box-UV density (128x192 for a 64x96 layout).
 */
@OnlyIn(Dist.CLIENT)
public class WizardRobeModel<T extends LivingEntity> extends HumanoidModel<T> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "wizard_robe"), "main");

    private static final float WAIST = 12.0F;
    private static final float FRONT_X = 2.65F;
    private static final float FRONT_Z = -2.15F;
    private static final float BACK_Z = 2.15F;
    private static final float SIDE_X = 4.15F;
    private static final float SKIRT = 10.0F;
    private static final float CLOAK_Z = 2.8F;

    private final ModelPart frontRight;
    private final ModelPart frontLeft;
    private final ModelPart back;
    private final ModelPart sideRight;
    private final ModelPart sideLeft;
    private final ModelPart mantle;
    private final ModelPart collar;
    private final ModelPart cloak;
    private float flare;

    public WizardRobeModel(ModelPart root) {
        super(root);
        this.frontRight = this.body.getChild("skirt_front_right");
        this.frontLeft = this.body.getChild("skirt_front_left");
        this.back = this.body.getChild("skirt_back");
        this.sideRight = this.body.getChild("skirt_side_right");
        this.sideLeft = this.body.getChild("skirt_side_left");
        this.mantle = this.body.getChild("mantle");
        this.collar = this.body.getChild("collar");
        this.cloak = this.body.getChild("cloak");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        CubeDeformation robe = new CubeDeformation(0.6F);

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, robe), PartPose.ZERO);

        CubeDeformation half = new CubeDeformation(-0.1F, 0.0F, 0.0F);
        body.addOrReplaceChild("skirt_front_right", CubeListBuilder.create()
                .texOffs(0, 40).addBox(-2.0F, 0.0F, -0.5F, 4.0F, SKIRT, 1.0F, half),
                PartPose.offset(-FRONT_X, WAIST, FRONT_Z));
        body.addOrReplaceChild("skirt_front_left", CubeListBuilder.create()
                .texOffs(10, 40).addBox(-2.0F, 0.0F, -0.5F, 4.0F, SKIRT, 1.0F, half),
                PartPose.offset(FRONT_X, WAIST, FRONT_Z));
        body.addOrReplaceChild("skirt_back", CubeListBuilder.create()
                .texOffs(20, 40).addBox(-4.0F, 0.0F, -0.5F, 8.0F, SKIRT, 1.0F, new CubeDeformation(0.6F, 0.0F, 0.0F)),
                PartPose.offset(0.0F, WAIST, BACK_Z));
        CubeDeformation side = new CubeDeformation(0.0F, 0.0F, 0.6F);
        body.addOrReplaceChild("skirt_side_right", CubeListBuilder.create()
                .texOffs(38, 40).addBox(-0.5F, 0.0F, -2.0F, 1.0F, SKIRT, 4.0F, side),
                PartPose.offset(-SIDE_X, WAIST, 0.0F));
        body.addOrReplaceChild("skirt_side_left", CubeListBuilder.create()
                .texOffs(48, 40).addBox(-0.5F, 0.0F, -2.0F, 1.0F, SKIRT, 4.0F, side),
                PartPose.offset(SIDE_X, WAIST, 0.0F));

        // Auror: a short mantle over the shoulders and upper arms, and a stand collar open at the throat.
        body.addOrReplaceChild("mantle", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8.5F, -1.0F, -3.0F, 17.0F, 3.0F, 6.0F, new CubeDeformation(0.15F)),
                PartPose.ZERO);
        body.addOrReplaceChild("collar", CubeListBuilder.create()
                .texOffs(0, 52).addBox(-5.5F, -2.5F, -5.0F, 11.0F, 3.0F, 10.0F),
                PartPose.ZERO);
        // Death Eater: a cloak from the shoulder blades to the ankles.
        body.addOrReplaceChild("cloak", CubeListBuilder.create()
                .texOffs(0, 66).addBox(-4.5F, 0.0F, 0.0F, 9.0F, 21.0F, 1.0F, new CubeDeformation(0.3F, 0.0F, 0.0F)),
                PartPose.offset(0.0F, -0.2F, CLOAK_Z));

        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F, robe)
                .texOffs(0, 32).addBox(-3.0F, 4.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.9F)),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(40, 16).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F, robe)
                .texOffs(0, 32).mirror().addBox(-1.0F, 4.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.9F)),
                PartPose.offset(5.0F, 2.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 96);
    }

    /** One model draws every wearer, so this is set for each wearer just before their coat is drawn. */
    public void prepare(LivingEntity wearer, boolean deathEater) {
        this.mantle.visible = !deathEater;
        this.collar.visible = !deathEater;
        this.cloak.visible = deathEater;
        float speed = Mth.clamp(wearer.walkAnimation.speed(), 0.0F, 1.0F);
        this.flare = 0.06F + 0.55F * speed + (wearer.isSprinting() ? 0.25F : 0.0F);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        drape();
        super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, color);
    }

    private void drape() {
        float lean = this.body.xRot;
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

        if (this.cloak.visible) {
            float clear = cloakClearing(this.back, 0.5F);
            clear = Math.max(clear, cloakClearing(this.sideRight, 2.6F));
            clear = Math.max(clear, cloakClearing(this.sideLeft, 2.6F));
            this.cloak.xRot = Math.max(this.flare - 0.5F * lean, clear);
        }
    }

    /**
     * The least cloak angle (body space, radians, positive swings the hem back) that keeps the back-bottom
     * corner of {@code panel} in front of the cloak. {@code backZ} is how far the corner sits behind the
     * panel's hinge before it turns.
     */
    private float cloakClearing(ModelPart panel, float backZ) {
        float cos = Mth.cos(panel.xRot);
        float sin = Mth.sin(panel.xRot);
        // the panel turns about x like the cloak: +xRot carries its hem toward +z
        float cornerY = panel.y + SKIRT * cos - backZ * sin;
        float cornerZ = panel.z + SKIRT * sin + backZ * cos;
        float hingeY = this.cloak.y;
        float drop = cornerY - hingeY;
        if (drop <= 0.5F) {
            return 0.0F;
        }
        float lead = cornerZ + 0.35F - CLOAK_Z;
        return lead <= 0.0F ? 0.0F : (float) Math.atan2(lead, drop);
    }
}
