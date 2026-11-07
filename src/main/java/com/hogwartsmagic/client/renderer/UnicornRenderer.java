package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.UnicornModel;
import com.hogwartsmagic.entity.UnicornEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** White adult, gold foal (canon: foals are born gold and whiten as they grow); the adult's horn glows. */
public class UnicornRenderer extends MobRenderer<UnicornEntity, UnicornModel> {
    private static final ResourceLocation ADULT =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/unicorn.png");
    private static final ResourceLocation FOAL =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/unicorn_foal.png");
    private static final RenderType HORN_GLOW =
            RenderType.eyes(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/unicorn_glow.png"));

    public UnicornRenderer(EntityRendererProvider.Context context) {
        super(context, new UnicornModel(context.bakeLayer(UnicornModel.LAYER_LOCATION)), 0.75F);
        this.addLayer(new HornGlowLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(UnicornEntity entity) {
        return entity.isBaby() ? FOAL : ADULT;
    }

    private static final class HornGlowLayer extends EyesLayer<UnicornEntity, UnicornModel> {
        HornGlowLayer(RenderLayerParent<UnicornEntity, UnicornModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, UnicornEntity entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (entity.isBaby() || entity.isInvisible()) {
                return;
            }
            super.render(poseStack, buffer, packedLight, entity, limbSwing, limbSwingAmount, partialTick, ageInTicks,
                    netHeadYaw, headPitch);
        }

        @Override
        public RenderType renderType() {
            return HORN_GLOW;
        }
    }
}
