package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
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
import net.minecraft.world.entity.Entity;

public class AcromantulaModel<T extends Entity> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "acromantula"), "main");
    
    public final ModelPart root;
    public final ModelPart head;
    public final ModelPart thorax;
    public final ModelPart abdomen;
    public final ModelPart leftFang;
    public final ModelPart rightFang;
    public final ModelPart[][] legs = new ModelPart[8][2];
    
    private float attackProgress = 0F;
    private float webProgress = 0F;
    
    public AcromantulaModel(ModelPart root) {
        this.root = root;
        this.thorax = root.getChild("thorax");
        this.abdomen = root.getChild("abdomen");
        this.head = root.getChild("head");
        this.leftFang = this.head.getChild("left_fang");
        this.rightFang = this.head.getChild("right_fang");
        for (int i = 0; i < 8; i++) {
            legs[i][0] = root.getChild("leg" + i + "_upper");
            legs[i][1] = legs[i][0].getChild("leg" + i + "_lower");
        }
    }
    
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        
        // Thorax (central body)
        root.addOrReplaceChild("thorax", CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-5.0F, -4.0F, -6.0F, 10.0F, 8.0F, 12.0F), 
            PartPose.offset(0.0F, 18.0F, 0.0F)); // Lowered to ground
            
        // Abdomen (large bulbous back)
        root.addOrReplaceChild("abdomen", CubeListBuilder.create()
            .texOffs(0, 20)
            .addBox(-7.0F, -6.0F, 0.0F, 14.0F, 12.0F, 18.0F),
            PartPose.offsetAndRotation(0.0F, 18.0F, 5.0F, -0.2F, 0.0F, 0.0F));
            
        // Head (front)
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
            .texOffs(64, 20)
            .addBox(-4.0F, -4.0F, -6.0F, 8.0F, 8.0F, 6.0F),
            PartPose.offset(0.0F, 18.0F, -6.0F));
            
        // Left Fang
        head.addOrReplaceChild("left_fang", CubeListBuilder.create()
            .texOffs(92, 20)
            .addBox(-1.0F, 0.0F, -1.5F, 2.0F, 6.0F, 3.0F),
            PartPose.offsetAndRotation(2.0F, 4.0F, -5.0F, 0.1F, 0.1F, 0.1F));
            
        // Right Fang
        head.addOrReplaceChild("right_fang", CubeListBuilder.create()
            .texOffs(92, 20).mirror()
            .addBox(-1.0F, 0.0F, -1.5F, 2.0F, 6.0F, 3.0F),
            PartPose.offsetAndRotation(-2.0F, 4.0F, -5.0F, 0.1F, -0.1F, -0.1F));
            
        // Legs 
        for (int i = 0; i < 8; i++) {
            boolean isLeft = i < 4;
            int legPair = i % 4; // 0, 1, 2, 3 (front to back)
            float zOffset = -3.0F + (legPair * 3.0F); // -3, 0, 3, 6
            float xOffset = isLeft ? 5.0F : -5.0F; // Left is +X, Right is -X
            
            float baseRot = isLeft ? (float)Math.PI / 2F : -(float)Math.PI / 2F; 
            
            float sweep = switch (legPair) {
                case 0 ->  (float) Math.PI / 4.0F;  // front points forward
                case 1 ->  (float) Math.PI / 9.0F;
                case 2 -> -(float) Math.PI / 9.0F;
                case 3 -> -(float) Math.PI / 4.0F;  // back points backward
                default -> 0F;
            };
            
            float yRotation = baseRot + (isLeft ? sweep : -sweep);
            
            // Upper leg arches DOWN (positive), Lower leg bends further DOWN (negative relative)
            float xRotUpper = 0.0F; // Less positive so they point more outwards instead of straight down
            float xRotLower = -0.5F; 
            
            if (legPair == 0) {
                xRotUpper = 0.0F; 
                xRotLower = -0.4F; 
            }
            if (legPair == 3) {
                xRotUpper = 0.0F; 
                xRotLower = -0.4F; 
            }
            
            PartDefinition upper = root.addOrReplaceChild("leg" + i + "_upper", CubeListBuilder.create()
                .texOffs(0, 52)
                .addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 16.0F),
                PartPose.offsetAndRotation(xOffset, 18.0F, zOffset, xRotUpper, yRotation, 0.0F)); // Base at Y=18
                
            upper.addOrReplaceChild("leg" + i + "_lower", CubeListBuilder.create()
                .texOffs(40, 52)
                .addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 20.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 16.0F, xRotLower, 0.0F, 0.0F));
        }
        
        return LayerDefinition.create(mesh, 128, 128);
    }
    
    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        
        head.yRot = netHeadYaw * ((float)Math.PI / 180F);
        head.xRot = headPitch * ((float)Math.PI / 180F);
        
        thorax.xRot = Mth.sin(ageInTicks * 0.05F) * 0.02F;
        abdomen.xRot = -0.1F + Mth.sin(ageInTicks * 0.05F) * 0.03F;
        
        leftFang.xRot += Mth.sin(ageInTicks * 0.1F) * 0.05F;
        rightFang.xRot += Mth.sin(ageInTicks * 0.1F) * 0.05F;
        
        for (int i = 0; i < 8; i++) {
            boolean isLeft = i < 4;
            int legPair = i % 4;
            float phase = (legPair * (float)Math.PI / 2F) + (isLeft ? (float)Math.PI : 0F);
            
            float swing = Mth.cos(limbSwing * 0.6662F + phase) * 0.4F * limbSwingAmount;
            legs[i][0].yRot += isLeft ? swing : -swing;
            
            float lift = Math.max(0, Mth.sin(limbSwing * 0.6662F + phase)) * limbSwingAmount;
            legs[i][0].xRot -= lift * 0.6F; // Negative lifts the leg up
            legs[i][1].xRot += lift * 0.3F; // Positive tucks it slightly inwards while lifting
        }
        
        if (attackProgress > 0F) {
            float factor = attackProgress;
            for (int i = 0; i < 8; i++) {
                legs[i][0].xRot -= 0.4F * factor; // Negative lifts up
            }
            thorax.xRot -= 0.3F * factor; 
            head.xRot -= 0.5F * factor; 
            leftFang.xRot += 0.4F * factor; 
            rightFang.xRot += 0.4F * factor;
        }
        
        if (webProgress > 0F) {
            float factor = webProgress;
            thorax.xRot -= 0.1F * factor;
            abdomen.xRot += 0.2F * factor; 
        }
        
        if (entity instanceof com.hogwartsmagic.entity.AcromantulaEntity spider && spider.isClimbing()) {
            thorax.xRot += 0.3F;
            abdomen.xRot += 0.3F;
            for (int i = 0; i < 8; i++) legs[i][0].xRot -= 0.1F;
        }
    }
    
    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
    
    public void setAttackProgress(float progress) {
        this.attackProgress = Mth.clamp(progress, 0F, 1F);
    }
    
    public void setWebProgress(float progress) {
        this.webProgress = Mth.clamp(progress, 0F, 1F);
    }
}
