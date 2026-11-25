package com.hogwartsmagic.cosy.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.decor.FloatingCandleBlock;
import com.hogwartsmagic.cosy.CandleBob;
import com.hogwartsmagic.cosy.FloatingCandleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.ModelEvent;

/**
 * Draws the three floating candles of a cell, each bobbing and swaying on its own. The candles are three
 * small models (lit with a flame, unlit without) that the model event loads; the block itself bakes nothing.
 * Client only: referenced from ClientModEvents.
 */
public class FloatingCandleRenderer implements BlockEntityRenderer<FloatingCandleBlockEntity> {
    private static final ModelResourceLocation[] LIT = new ModelResourceLocation[3];
    private static final ModelResourceLocation[] UNLIT = new ModelResourceLocation[3];

    static {
        for (int i = 0; i < 3; i++) {
            LIT[i] = standalone("block/floating_candle_" + i);
            UNLIT[i] = standalone("block/floating_candle_" + i + "_unlit");
        }
    }

    /** A model loaded only so the renderer can fetch it by this key; the variant is a label, not a blockstate. */
    private static ModelResourceLocation standalone(String path) {
        return new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, path), "standalone");
    }

    public FloatingCandleRenderer(BlockEntityRendererProvider.Context context) {
    }

    /** Asks the model loader for the six candle models; called from the mod-bus model event. */
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        for (int i = 0; i < 3; i++) {
            event.register(LIT[i]);
            event.register(UNLIT[i]);
        }
    }

    @Override
    public void render(FloatingCandleBlockEntity candle, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        Level level = candle.getLevel();
        if (level == null) {
            return;
        }
        boolean lit = candle.getBlockState().getValue(FloatingCandleBlock.LIT);
        long time = level.getGameTime();
        long seed = candle.getBlockPos().asLong();
        VertexConsumer consumer = buffers.getBuffer(RenderType.cutout());
        var modelManager = Minecraft.getInstance().getModelManager();
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        for (int i = 0; i < 3; i++) {
            BakedModel model = modelManager.getModel(lit ? LIT[i] : UNLIT[i]);
            double[] pivot = FloatingCandleBlock.PIVOTS[i];
            float sway = CandleBob.swayDeg(time, partialTick, i, seed);
            pose.pushPose();
            pose.translate(0.0, CandleBob.offsetY(time, partialTick, i, seed), 0.0);
            pose.translate(pivot[0] / 16.0, pivot[1] / 16.0, pivot[2] / 16.0);
            pose.mulPose(Axis.ZP.rotationDegrees(sway));
            pose.mulPose(Axis.XP.rotationDegrees(-0.6f * sway * ((i & 1) == 0 ? 1f : -1f)));
            pose.translate(-pivot[0] / 16.0, -pivot[1] / 16.0, -pivot[2] / 16.0);
            renderer.renderModel(pose.last(), consumer, null, model, 1.0f, 1.0f, 1.0f, light, overlay);
            pose.popPose();
        }
    }
}
