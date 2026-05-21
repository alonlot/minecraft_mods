package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.BasiliskEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
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

public class BasiliskModel extends EntityModel<BasiliskEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "basilisk"), "main");

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart tongue;
    private final ModelPart neck;
    private final ModelPart body1;
    private final ModelPart body2;
    private final ModelPart tail1;
    private final ModelPart tail2;

    public BasiliskModel(ModelPart root) {
        this.root = root.getChild("root");
        this.head = this.root.getChild("head");
        this.jaw = this.head.getChild("jaw");
        this.tongue = this.jaw.getChild("tongue");
        this.neck = this.root.getChild("neck");
        this.body1 = this.root.getChild("body1");
        this.body2 = this.root.getChild("body2");
        this.tail1 = this.root.getChild("tail1");
        this.tail2 = this.root.getChild("tail2");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition basilisk = root.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 20.0F, 0.0F));

        PartDefinition head = basilisk.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-4.5F, -3.5F, -13.0F, 9.0F, 6.0F, 13.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 19)
                        .addBox(-1.5F, -1.0F, -18.0F, 3.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(36, 0)
                        .addBox(-2.5F, -4.5F, -9.0F, 5.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -1.5F, -14.0F));

        PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create()
                        .texOffs(36, 8)
                        .addBox(-4.0F, 0.0F, -12.5F, 8.0F, 2.0F, 12.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.7F, -0.2F));

        // Forked tongue inside the mouth — animates flicking in/out.
        jaw.addOrReplaceChild("tongue", CubeListBuilder.create()
                        .texOffs(110, 0)
                        .addBox(-0.5F, -0.1F, -4.0F, 1.0F, 0.2F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, -10.0F));

        head.addOrReplaceChild("left_fang", CubeListBuilder.create()
                        .texOffs(0, 27)
                        .addBox(-0.4F, 0.0F, -2.0F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(2.2F, 1.0F, -14.2F));

        head.addOrReplaceChild("right_fang", CubeListBuilder.create()
                        .texOffs(4, 27)
                        .addBox(-0.6F, 0.0F, -2.0F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-2.2F, 1.0F, -14.2F));

        basilisk.addOrReplaceChild("neck", CubeListBuilder.create()
                        .texOffs(0, 24)
                        .addBox(-3.5F, -3.5F, -7.0F, 7.0F, 7.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -1.0F, -6.5F));

        basilisk.addOrReplaceChild("body1", CubeListBuilder.create()
                        .texOffs(0, 42)
                        .addBox(-5.0F, -4.0F, -8.0F, 10.0F, 8.0F, 18.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -0.3F, 4.0F));

        basilisk.addOrReplaceChild("body2", CubeListBuilder.create()
                        .texOffs(50, 42)
                        .addBox(-4.0F, -3.0F, -3.0F, 8.0F, 6.0F, 17.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.1F, 18.5F));

        basilisk.addOrReplaceChild("tail1", CubeListBuilder.create()
                        .texOffs(36, 13)
                        .addBox(-3.0F, -2.5F, -2.0F, 6.0F, 5.0F, 17.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.2F, 32.5F));

        basilisk.addOrReplaceChild("tail2", CubeListBuilder.create()
                        .texOffs(24, 24)
                        .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 18.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.2F, 45.5F));

        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public void setupAnim(BasiliskEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float moveBlend = Mth.clamp(limbSwingAmount * 1.6F, 0.15F, 1.0F);
        float waveTime = limbSwingAmount > 0.08F ? limbSwing * 0.65F : ageInTicks * 0.08F;
        float headYawRad = netHeadYaw * ((float) Math.PI / 180F);
        float headPitchRad = headPitch * ((float) Math.PI / 180F);
        float idle = Mth.sin(ageInTicks * 0.09F) * 0.04F;

        float neckWave = Mth.sin(waveTime) * 0.12F * moveBlend;
        float bodyWave1 = Mth.sin(waveTime - 0.75F) * 0.16F * moveBlend;
        float bodyWave2 = Mth.sin(waveTime - 1.45F) * 0.2F * moveBlend;
        float tailWave1 = Mth.sin(waveTime - 2.15F) * 0.25F * moveBlend;
        float tailWave2 = Mth.sin(waveTime - 2.85F) * 0.31F * moveBlend;

        this.head.yRot = headYawRad * 0.65F + neckWave * 0.55F;
        this.head.xRot = headPitchRad * 0.4F + idle;

        // Jaw opens wider when the basilisk has a target (aggressive); subtle drift when idle.
        // A second slow wave at 0.04Hz creates an occasional menacing maw-gape rather than constant motion.
        float jawBase = 0.10F + Mth.sin(ageInTicks * 0.18F) * 0.04F;
        float jawAgro = entity.isAggressive() ? (0.25F + Math.max(0.0F, Mth.sin(ageInTicks * 0.13F)) * 0.35F) : 0.0F;
        this.jaw.xRot = jawBase + jawAgro;

        // Tongue flick: extends and retracts on a sine cycle, visible mostly when jaw is open.
        float tongueCycle = Mth.sin(ageInTicks * 0.5F);
        float extend = tongueCycle > 0.3F ? (tongueCycle - 0.3F) / 0.7F : 0.0F;
        this.tongue.z = -10.0F + -3.0F * extend; // slides forward out of the mouth
        this.tongue.xRot = -0.10F + extend * 0.15F;

        this.neck.yRot = neckWave;
        this.neck.xRot = idle * 0.8F;

        this.body1.yRot = bodyWave1;
        this.body1.xRot = Mth.sin(waveTime - 0.4F) * 0.03F * moveBlend;

        this.body2.yRot = bodyWave2;
        this.body2.xRot = Mth.sin(waveTime - 1.1F) * 0.04F * moveBlend;

        this.tail1.yRot = tailWave1;
        this.tail1.xRot = Mth.sin(waveTime - 1.8F) * 0.05F * moveBlend;

        this.tail2.yRot = tailWave2;
        this.tail2.xRot = Mth.sin(waveTime - 2.6F) * 0.07F * moveBlend;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
