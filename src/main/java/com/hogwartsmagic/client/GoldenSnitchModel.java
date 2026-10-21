package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.GoldenSnitchEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * Golden Snitch: a tiny golden ball with two frantic little wings.
 * The wings beat hummingbird-fast; the whole body rolls slightly with the beat.
 */
public class GoldenSnitchModel<T extends GoldenSnitchEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "golden_snitch"), "main");

    /** The wings' beat: radians per tick of age, the swing either side of the tilt, and the tilt (rad). */
    public static final float FLAP_RATE = 3.2F, FLAP_AMPLITUDE = 1.1F, WING_TILT = 0.4F;
    /** A wing's length and its thickness along z, and where its pivot sits on the body, in px. */
    public static final float WING_LENGTH = 6.0F, WING_THICKNESS = 3.0F, WING_PIVOT_X = 1.5F, WING_PIVOT_Y = -0.5F;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart leftWing;
    private final ModelPart rightWing;

    public GoldenSnitchModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.leftWing = body.getChild("left_wing");
        this.rightWing = body.getChild("right_wing");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F),
                PartPose.offset(0.0F, 21.5F, 0.0F));

        body.addOrReplaceChild("left_wing", CubeListBuilder.create()
                        .texOffs(0, 8).addBox(0.0F, -0.5F, -1.5F, 6.0F, 1.0F, 3.0F),
                PartPose.offset(1.5F, -0.5F, 0.0F));

        body.addOrReplaceChild("right_wing", CubeListBuilder.create()
                        .texOffs(0, 12).mirror().addBox(-6.0F, -0.5F, -1.5F, 6.0F, 1.0F, 3.0F),
                PartPose.offset(-1.5F, -0.5F, 0.0F));

        return LayerDefinition.create(meshdefinition, 32, 32);
    }

    /** The body part, whose pose (with the beat's roll) the wings hang from. */
    public ModelPart bodyPart() {
        return body;
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        // Hummingbird flutter — far faster than any bird wing in the game.
        float flap = (float) Math.sin(ageInTicks * FLAP_RATE) * FLAP_AMPLITUDE;
        this.leftWing.zRot = -WING_TILT + flap;
        this.rightWing.zRot = WING_TILT - flap;

        // The body rocks with the beat and pitches into its motion.
        this.body.zRot = (float) Math.sin(ageInTicks * FLAP_RATE) * 0.08F;
        this.body.xRot = headPitch * ((float) Math.PI / 180F) * 0.4F;
    }
}
