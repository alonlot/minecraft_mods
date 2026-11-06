package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.VoldemortModel;
import com.hogwartsmagic.entity.VoldemortEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Voldemort on his own model ({@link VoldemortModel}): the painted body, the wand in his hand, and the red slit
 * eyes as a glow layer that shows in the dark. While Tenebris Flight turns him to smoke nothing is drawn, as for
 * every wizard.
 */
public class VoldemortRenderer extends MobRenderer<VoldemortEntity, VoldemortModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/voldemort.png");
    private static final RenderType EYES =
            RenderType.eyes(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/voldemort_eyes.png"));

    public VoldemortRenderer(EntityRendererProvider.Context context) {
        super(context, new VoldemortModel(context.bakeLayer(VoldemortModel.LAYER_LOCATION)), 0.45F);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
    }

    @Override
    public void render(VoldemortEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        if (entity.isFlying()) {
            return;
        }
        super.render(entity, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(VoldemortEntity entity) {
        return TEXTURE;
    }
}
