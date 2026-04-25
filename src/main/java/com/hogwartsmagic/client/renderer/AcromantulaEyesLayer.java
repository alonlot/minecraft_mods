package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.AcromantulaModel;
import com.hogwartsmagic.entity.AcromantulaEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

public class AcromantulaEyesLayer<T extends AcromantulaEntity, M extends AcromantulaModel<T>> extends EyesLayer<T, M> {
    private static final RenderType SPIDER_EYES = RenderType.eyes(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/acromantula_eyes.png"));

    public AcromantulaEyesLayer(RenderLayerParent<T, M> renderer) {
        super(renderer);
    }

    @Override
    public RenderType renderType() {
        return SPIDER_EYES;
    }
}
