package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.entity.HungarianHorntailEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * The Ironbelly, drawn with the Horntail's own model and a different hide.
 *
 * <p>This is the whole of what "a variant for now" buys: same mesh, same animations, same renderer,
 * one texture. The Ironbelly is canonically the larger breed and deserves its own sculpt eventually,
 * but a pale scarred recolour of a dragon that already moves correctly reads far better than a
 * placeholder, and it can be swapped out later without touching anything else.
 */
public class UkrainianIronbellyRenderer extends HungarianHorntailRenderer {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "hogwartsmagic", "textures/entity/ukrainian_ironbelly.png");

    public UkrainianIronbellyRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(HungarianHorntailEntity entity) {
        return TEXTURE;
    }
}
