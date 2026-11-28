package com.hogwartsmagic.client;

import com.hogwartsmagic.entity.AurorEntity;
import com.hogwartsmagic.entity.StudentModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/** The Auror: the students' robed body, bareheaded, in a crimson Ministry greatcoat (three faces). */
public class AurorRenderer extends MobRenderer<AurorEntity, StudentModel<AurorEntity>> {
    private static final ResourceLocation[] TEXTURES = {
            ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "textures/entity/auror_0.png"),
            ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "textures/entity/auror_1.png"),
            ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "textures/entity/auror_2.png"),
    };

    public AurorRenderer(EntityRendererProvider.Context context) {
        super(context, new StudentModel<>(context.bakeLayer(StudentModel.LAYER_LOCATION)), 0.5f);
        this.getModel().setHatVisible(false);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new WandSwipeLayer<>(this));
    }

    @Override
    public void render(AurorEntity auror, float yaw, float partialTick, com.mojang.blaze3d.vertex.PoseStack pose,
            net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        if (auror.isLightFlying()) {
            return; // TrailFx draws the silver streak in its place (and the wand must not float on its own)
        }
        super.render(auror, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(AurorEntity entity) {
        int v = entity.getVariant();
        return TEXTURES[v >= 0 && v < TEXTURES.length ? v : 0];
    }
}
