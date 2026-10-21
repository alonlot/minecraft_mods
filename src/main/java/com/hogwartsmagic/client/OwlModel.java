package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.OwlEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.WeakHashMap;

public class OwlModel<T extends OwlEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "owl"), "main");

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart leftWing;
    private final ModelPart rightWing;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;

    public OwlModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.leftWing = body.getChild("left_wing");
        this.rightWing = body.getChild("right_wing");
        this.leftLeg = body.getChild("left_leg");
        this.rightLeg = body.getChild("right_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-4.0F, -4.0F, -3.0F, 8.0F, 8.0F, 6.0F),
                PartPose.offset(0.0F, 18.0F, 0.0F));

        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -7.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                PartPose.offset(0.0F, -3.0F, 0.0F));

        PartDefinition left_wing = body.addOrReplaceChild("left_wing", CubeListBuilder.create()
                .texOffs(32, 32).addBox(0.0F, -3.0F, -3.0F, 1.0F, 8.0F, 6.0F),
                PartPose.offset(4.0F, -1.0F, 0.0F));

        PartDefinition right_wing = body.addOrReplaceChild("right_wing", CubeListBuilder.create()
                .texOffs(32, 32).mirror().addBox(-1.0F, -3.0F, -3.0F, 1.0F, 8.0F, 6.0F),
                PartPose.offset(-4.0F, -1.0F, 0.0F));

        body.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(32, 48).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F),
                PartPose.offset(1.5F, 4.0F, 0.0F));

        body.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(40, 48).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F),
                PartPose.offset(-1.5F, 4.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    public ModelPart body() {
        return this.body;
    }

    public ModelPart head() {
        return this.head;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        this.head.xRot = headPitch * ((float)Math.PI / 180F);
        this.head.yRot = netHeadYaw * ((float)Math.PI / 180F);

        // Ease between flight and perch instead of snapping: the flap dies away and the wings fold over
        // FOLD_TICKS after a landing (and open over UNFOLD_TICKS on take-off). Client-only render state.
        float b = flightBlend(entity, ageInTicks);
        float flap = (float) Math.sin(ageInTicks * 1.35F) * 0.9F;
        this.leftWing.zRot = -0.1F + (-0.25F + flap) * b;
        this.rightWing.zRot = 0.1F + (0.25F - flap) * b;
        this.body.xRot = 0.1F * b;
        this.leftLeg.xRot = 0.75F * b;
        this.rightLeg.xRot = 0.75F * b;
        this.body.y = 18.0F - 0.5F * b;
    }

    private static final float FOLD_TICKS = 5F;
    private static final float UNFOLD_TICKS = 2F;
    /** Per owl: {blend 0 perched .. 1 flying, the ageInTicks it was last advanced at}. */
    private final Map<OwlEntity, float[]> blends = new WeakHashMap<>();

    private float flightBlend(T entity, float age) {
        float target = entity.isFlying() ? 1F : 0F;
        float[] st = blends.get(entity);
        if (st == null) {
            blends.put(entity, new float[]{target, age});
            return target;
        }
        float dt = age - st[1];
        if (dt < 0F || dt > 10F) {
            st[0] = target;              // a jump in time (a seek, a respawn): no easing across it
        } else if (st[0] < target) {
            st[0] = Math.min(target, st[0] + dt / UNFOLD_TICKS);
        } else if (st[0] > target) {
            st[0] = Math.max(target, st[0] - dt / FOLD_TICKS);
        }
        st[1] = age;
        // ease-in-out on the blend so the wings settle rather than stop
        float x = st[0];
        return x * x * (3F - 2F * x);
    }
}
