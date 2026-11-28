package com.hogwartsmagic.client.model;

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
 * The raised hood of the Auror and Death Eater robes: a deep cowl over the head with its face cut open,
 * a brow that juts forward to shadow the eyes, a peak falling back off the crown and cloth gathered at
 * the nape. A Death Eater's hood frames the silver mask (a plate set back inside the opening and a nose
 * ridge standing proud of it); an Auror's leaves the face bare.
 *
 * <p>Every part hangs off {@code head}, the only part the armour layer draws for a helmet, so it all turns
 * with the head. The texture is drawn at twice the box-UV density (128x64 for a 64x32 layout).
 */
@OnlyIn(Dist.CLIENT)
public class WizardHoodModel<T extends LivingEntity> extends HumanoidModel<T> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "wizard_hood"), "main");

    private final ModelPart mask;
    private final ModelPart nose;

    public WizardHoodModel(ModelPart root) {
        super(root);
        this.mask = this.head.getChild("mask");
        this.nose = this.head.getChild("nose");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.75F)),
                PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);

        // The brow: the hood's front edge standing a little proud of the face. Its sides sit just inside
        // the cowl's so the two never fight over the same plane.
        head.addOrReplaceChild("brow", CubeListBuilder.create()
                .texOffs(32, 0).addBox(-4.5F, -8.5F, -5.5F, 9.0F, 2.0F, 2.0F, new CubeDeformation(0.2F)),
                PartPose.ZERO);
        // Cloth gathered at the nape, falling below the jaw at the back.
        head.addOrReplaceChild("nape", CubeListBuilder.create()
                .texOffs(32, 4).addBox(-4.0F, -1.0F, 3.0F, 8.0F, 4.0F, 2.0F, new CubeDeformation(0.5F)),
                PartPose.ZERO);
        // The peak: the cowl's point lying back and down off the crown.
        head.addOrReplaceChild("peak", CubeListBuilder.create()
                .texOffs(32, 10).addBox(-2.5F, -1.0F, 0.0F, 5.0F, 2.0F, 4.0F, new CubeDeformation(0.2F)),
                PartPose.offsetAndRotation(0.0F, -8.3F, 2.0F, -0.45F, 0.0F, 0.0F));

        // The mask sits back inside the opening, behind the cowl's front face, so the rim frames it.
        head.addOrReplaceChild("mask", CubeListBuilder.create()
                .texOffs(0, 16).addBox(-3.5F, -7.5F, -4.6F, 7.0F, 7.0F, 1.0F),
                PartPose.ZERO);
        head.addOrReplaceChild("nose", CubeListBuilder.create()
                .texOffs(16, 16).addBox(-1.0F, -5.0F, -5.3F, 2.0F, 3.0F, 1.0F, new CubeDeformation(-0.1F, 0.0F, 0.0F)),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 32);
    }

    /** One model draws every wearer, so this is set for each wearer just before their hood is drawn. */
    public void setMasked(boolean masked) {
        this.mask.visible = masked;
        this.nose.visible = masked;
    }
}
