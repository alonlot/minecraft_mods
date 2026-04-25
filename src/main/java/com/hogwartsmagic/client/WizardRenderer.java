package com.hogwartsmagic.client;

import com.hogwartsmagic.entity.WizardEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public class WizardRenderer<T extends WizardEntity> extends MobRenderer<T, WizardModel<T>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "textures/entity/texture.png");
    private static final ResourceLocation VOLDEMORT_TEXTURE = ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "textures/entity/voldemort.png");

    public WizardRenderer(EntityRendererProvider.Context context) {
        super(context, new WizardModel<>(context.bakeLayer(WizardModel.LAYER_LOCATION)), 0.5f);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public void render(T p_114485_, float p_114486_, float p_114487_, com.mojang.blaze3d.vertex.PoseStack p_114488_, net.minecraft.client.renderer.MultiBufferSource p_114489_, int p_114490_) {
        if (p_114485_.isFlying()) {
            return;
        }
        super.render(p_114485_, p_114486_, p_114487_, p_114488_, p_114489_, p_114490_);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        if (entity instanceof com.hogwartsmagic.entity.VoldemortEntity) {
            return VOLDEMORT_TEXTURE;
        }
        return TEXTURE;
    }
}
