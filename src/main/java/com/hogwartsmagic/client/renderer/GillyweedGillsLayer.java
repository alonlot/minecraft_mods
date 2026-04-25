package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.GillyweedGillsModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class GillyweedGillsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation GILLYWEED_GILLS_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/gillyweed_gills.png");

    private final GillyweedGillsModel<AbstractClientPlayer> model;

    public GillyweedGillsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer,
                               net.minecraft.client.model.geom.EntityModelSet entityModelSet) {
        super(renderer);
        this.model = new GillyweedGillsModel<>(entityModelSet.bakeLayer(GillyweedGillsModel.LAYER_LOCATION));
    }

    @Override
    public void render(PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight,
                       AbstractClientPlayer player,
                       float limbSwing,
                       float limbSwingAmount,
                       float partialTick,
                       float ageInTicks,
                       float netHeadYaw,
                       float headPitch) {
        if (player.isInvisible() || !player.hasEffect(ExampleMod.GILLYWEED_EFFECT.getHolder().get())) {
            return;
        }

        model.copyStateFrom(getParentModel());
        model.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        model.renderToBuffer(
                poseStack,
                bufferSource.getBuffer(RenderType.entityCutoutNoCull(GILLYWEED_GILLS_TEXTURE)),
                packedLight,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF
        );
    }
}
