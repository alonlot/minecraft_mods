package com.hogwartsmagic.cosy.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.cosy.ChocolateFrogEntity;
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
import net.minecraft.util.Mth;

/**
 * The chocolate frog: a few boxes (body, head, two eyes, two hind legs, two forelegs) on a texture of 64x32.
 * It squashes and stretches around each hop. The pose is chosen with if/else on the time since the hop began, never
 * a switch (a switch on an enum has failed to load in this mod's dev environment).
 */
public class ChocolateFrogModel extends EntityModel<ChocolateFrogEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "chocolate_frog"), "main");

    /** Ticks the squash-and-stretch lasts after a hop starts. */
    private static final float HOP_ANIM_TICKS = 14.0F;

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart hindLeft;
    private final ModelPart hindRight;
    private final ModelPart foreLeft;
    private final ModelPart foreRight;

    public ChocolateFrogModel(ModelPart model) {
        this.root = model.getChild("root");
        this.head = this.root.getChild("head");
        this.hindLeft = this.root.getChild("hind_left");
        this.hindRight = this.root.getChild("hind_right");
        this.foreLeft = this.root.getChild("fore_left");
        this.foreRight = this.root.getChild("fore_right");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition top = mesh.getRoot();
        // Origin at the feet; the renderer flips y, so negative y is up.
        PartDefinition root = top.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-2.5F, -1.5F, -3.0F, 5.0F, 3.0F, 6.0F), PartPose.offset(0.0F, -2.5F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 9)
                .addBox(-2.5F, -1.0F, -3.0F, 5.0F, 2.0F, 3.0F), PartPose.offset(0.0F, -3.2F, -2.6F));
        head.addOrReplaceChild("eye_left", CubeListBuilder.create().texOffs(24, 0)
                .addBox(-0.5F, -1.2F, -1.6F, 1.0F, 1.0F, 1.0F), PartPose.offset(1.6F, 0.0F, 0.0F));
        head.addOrReplaceChild("eye_right", CubeListBuilder.create().texOffs(24, 0)
                .addBox(-0.5F, -1.2F, -1.6F, 1.0F, 1.0F, 1.0F), PartPose.offset(-1.6F, 0.0F, 0.0F));
        root.addOrReplaceChild("hind_left", CubeListBuilder.create().texOffs(0, 16)
                .addBox(-1.0F, -0.5F, -0.5F, 2.0F, 1.0F, 4.0F), PartPose.offset(2.6F, -1.0F, 1.2F));
        root.addOrReplaceChild("hind_right", CubeListBuilder.create().texOffs(0, 16).mirror()
                .addBox(-1.0F, -0.5F, -0.5F, 2.0F, 1.0F, 4.0F), PartPose.offset(-2.6F, -1.0F, 1.2F));
        root.addOrReplaceChild("fore_left", CubeListBuilder.create().texOffs(14, 16)
                .addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offset(2.0F, -2.0F, -2.2F));
        root.addOrReplaceChild("fore_right", CubeListBuilder.create().texOffs(14, 16)
                .addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offset(-2.0F, -2.0F, -2.2F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(ChocolateFrogEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        this.root.getAllParts().forEach(ModelPart::resetPose);
        float since = ageInTicks - entity.hopStartTick();
        float squashY = 1.0F;
        float squashZ = 1.0F;
        float hind = 0.15F;
        float headTilt = 0.0F;
        if (since < 0.0F || since > HOP_ANIM_TICKS) {
            // Sitting: a slow breath, and a look about.
            squashY = 1.0F + Mth.sin(ageInTicks * 0.2F) * 0.03F;
            headTilt = Mth.sin(ageInTicks * 0.13F) * 0.12F;
        } else if (since < 2.5F) {
            // Crouch: down and long, legs folded under.
            float p = since / 2.5F;
            squashY = Mth.lerp(p, 1.0F, 0.76F);
            squashZ = Mth.lerp(p, 1.0F, 1.12F);
            hind = Mth.lerp(p, 0.15F, 0.7F);
            headTilt = -0.1F * p;
        } else if (since < 7.0F) {
            // Launch: up and thin, back legs thrown out behind.
            float p = (since - 2.5F) / 4.5F;
            squashY = Mth.lerp(p, 0.76F, 1.2F);
            squashZ = Mth.lerp(p, 1.12F, 0.86F);
            hind = Mth.lerp(p, 0.7F, -1.0F);
            headTilt = Mth.lerp(p, -0.1F, -0.35F);
        } else {
            // Land: settle back to rest.
            float p = (since - 7.0F) / (HOP_ANIM_TICKS - 7.0F);
            squashY = Mth.lerp(p, 1.2F, 1.0F);
            squashZ = Mth.lerp(p, 0.86F, 1.0F);
            hind = Mth.lerp(p, -1.0F, 0.15F);
            headTilt = Mth.lerp(p, -0.35F, 0.0F);
        }
        this.root.yScale = squashY;
        this.root.zScale = squashZ;
        this.hindLeft.xRot = hind;
        this.hindRight.xRot = hind;
        this.foreLeft.xRot = -hind * 0.5F;
        this.foreRight.xRot = -hind * 0.5F;
        this.head.xRot = headTilt;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               int color) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
