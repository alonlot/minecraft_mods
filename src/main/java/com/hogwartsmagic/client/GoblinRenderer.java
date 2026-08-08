package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.GoblinModel;
import com.hogwartsmagic.entity.GoblinAnimated;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/**
 * Renders both goblin variants. The geometry is shared; only the texture
 * differs (frock coat for tellers, leather and iron for vault guards).
 */
public class GoblinRenderer<T extends Mob & GoblinAnimated> extends MobRenderer<T, GoblinModel<T>> {

    public static final ResourceLocation TELLER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/goblin.png");
    public static final ResourceLocation GUARD_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/goblin_guard.png");

    /**
     * The model is built at roughly 24px tall so the face has room for detail;
     * this brings the rendered goblin down to about 1.25 blocks, which is the
     * height the hitbox is registered at.
     */
    private static final float MODEL_SCALE = 0.82F;

    private final ResourceLocation texture;

    public GoblinRenderer(EntityRendererProvider.Context context, ResourceLocation texture) {
        super(context, new GoblinModel<>(context.bakeLayer(GoblinModel.LAYER_LOCATION)), 0.35F);
        this.texture = texture;
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    protected void scale(T entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return this.texture;
    }
}
