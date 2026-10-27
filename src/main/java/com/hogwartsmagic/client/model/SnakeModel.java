package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.SnakeEntity;
import net.minecraft.client.model.HierarchicalModel;
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

/**
 * A small snake written as code: a head and six tapering body segments. As in BasiliskModel the
 * segments are siblings under root, not a nested chain, so each is placed directly. A body this short
 * doesn't need the Basilisk's path ring buffer: a sine wave travelling down the body sets each
 * segment's sideways offset (locked to the limb swing while moving, a slow idle sway at rest), and each
 * segment is aimed at the next one so the body stays joined.
 */
public class SnakeModel extends HierarchicalModel<SnakeEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "snake"), "main");
    public static final int SEGMENTS = 6;
    /** Every segment's cube is 4 model units long (16 units is one block). */
    private static final float SEGMENT_LENGTH = 4.0F;
    private static final int[][] SEGMENT_UV = {{0, 8}, {16, 8}, {32, 8}, {48, 8}, {0, 16}, {16, 16}};
    private static final float[] SEGMENT_WIDTH = {3.0F, 3.0F, 3.0F, 2.0F, 2.0F, 1.0F};
    private static final float[] SEGMENT_HEIGHT = {2.0F, 2.0F, 2.0F, 2.0F, 1.0F, 1.0F};

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart tongue;
    private final ModelPart[] segments = new ModelPart[SEGMENTS];
    /** Scratch buffer reused every frame. */
    private final float[] offsets = new float[SEGMENTS + 1];

    public SnakeModel(ModelPart root) {
        this.root = root.getChild("root");
        this.head = this.root.getChild("head");
        this.tongue = this.head.getChild("tongue");
        for (int i = 0; i < SEGMENTS; i++) {
            this.segments[i] = this.root.getChild("seg" + i);
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();
        PartDefinition root = parts.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-1.5F, -2.0F, -4.0F, 3.0F, 2.0F, 4.0F, CubeDeformation.NONE),
                PartPose.offset(0.0F, 0.0F, -2.0F));
        head.addOrReplaceChild("tongue", CubeListBuilder.create()
                        .texOffs(16, 0).addBox(-0.5F, 0.0F, -3.0F, 1.0F, 0.0F, 3.0F, CubeDeformation.NONE),
                PartPose.offset(0.0F, -0.5F, -4.0F));

        for (int i = 0; i < SEGMENTS; i++) {
            float width = SEGMENT_WIDTH[i];
            float height = SEGMENT_HEIGHT[i];
            root.addOrReplaceChild("seg" + i, CubeListBuilder.create()
                            .texOffs(SEGMENT_UV[i][0], SEGMENT_UV[i][1])
                            .addBox(-width / 2.0F, -height, 0.0F, width, height, SEGMENT_LENGTH, CubeDeformation.NONE),
                    PartPose.offset(0.0F, 0.0F, -2.0F + i * SEGMENT_LENGTH));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(SnakeEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        boolean moving = limbSwingAmount > 0.05F;
        float time = moving ? limbSwing * 1.4F : ageInTicks * 0.08F;
        float amplitude = moving ? 1.4F + Math.min(1.0F, limbSwingAmount) * 1.6F : 0.5F;
        for (int i = 0; i <= SEGMENTS; i++) {
            // The wave grows toward the tail, the way a slithering body whips.
            float taper = 0.35F + 0.65F * (i / (float) SEGMENTS);
            this.offsets[i] = Mth.sin(time - i * 0.9F) * amplitude * taper;
        }
        for (int i = 0; i < SEGMENTS; i++) {
            ModelPart segment = this.segments[i];
            segment.x = this.offsets[i];
            // Each cube runs from its own origin toward +Z; aim it at where the next segment starts.
            segment.yRot = (float) Math.atan2(this.offsets[i + 1] - this.offsets[i], SEGMENT_LENGTH);
        }

        // The head runs toward -Z, so it faces away from the first segment's lean, plus damped tracking.
        this.head.x = this.offsets[0];
        this.head.yRot = (float) Math.atan2(this.offsets[1] - this.offsets[0], SEGMENT_LENGTH)
                + Mth.clamp(netHeadYaw, -40.0F, 40.0F) * Mth.DEG_TO_RAD * 0.5F;
        this.head.xRot = Mth.clamp(headPitch, -30.0F, 30.0F) * Mth.DEG_TO_RAD * 0.5F;

        // Tasting the air: a quick flick every two seconds.
        float cycle = (ageInTicks % 40.0F) / 40.0F;
        this.tongue.visible = cycle < 0.15F;
        this.tongue.yRot = Mth.sin(ageInTicks * 0.9F) * 0.25F;
    }
}
