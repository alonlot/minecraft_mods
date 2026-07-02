package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.GoldenSnitchEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class GoldenSnitchRenderer extends MobRenderer<GoldenSnitchEntity, GoldenSnitchModel<GoldenSnitchEntity>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/golden_snitch.png");

    public GoldenSnitchRenderer(EntityRendererProvider.Context context) {
        super(context, new GoldenSnitchModel<>(context.bakeLayer(GoldenSnitchModel.LAYER_LOCATION)), 0.15f);
    }

    @Override
    public ResourceLocation getTextureLocation(GoldenSnitchEntity entity) {
        return TEXTURE;
    }

    @Override
    protected int getBlockLightLevel(GoldenSnitchEntity entity, net.minecraft.core.BlockPos pos) {
        // The snitch gleams: always rendered near full brightness so it's spottable at dusk.
        return 15;
    }
}
