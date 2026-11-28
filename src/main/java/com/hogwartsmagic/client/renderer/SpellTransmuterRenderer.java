package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.block.entity.SpellTransmuterBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The transmuter's top. Idle, what is laid on it hovers at three points over the slab, bobbing. Through the rite the
 * three rise and circle ever faster and tighter until they fold into one point; then the finished book turns there.
 */
public class SpellTransmuterRenderer implements BlockEntityRenderer<SpellTransmuterBlockEntity> {
    public SpellTransmuterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SpellTransmuterBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        float time = be.getLevel().getGameTime() + partialTick;
        int glowLight = LightTexture.pack(15, 15);
        ItemStack result = be.result();
        if (!result.isEmpty()) {
            pose.pushPose();
            pose.translate(0.5, SpellTransmuterBlockEntity.TOP + 0.45 + Mth.sin(time * 0.08f) * 0.05, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(time * 2.5f));
            pose.scale(0.7f, 0.7f, 0.7f);
            render(be, result, pose, buffers, glowLight);
            pose.popPose();
            return;
        }
        int rite = be.rite();
        float p = rite < 0 ? 0.0f : Mth.clamp((rite + partialTick) / SpellTransmuterBlockEntity.RITE_TICKS, 0.0f, 1.0f);
        // Ease in: slow at first, rushing at the end.
        float e = p * p;
        double radius = 0.3 * (1.0 - e);
        double height = SpellTransmuterBlockEntity.TOP + 0.3 + e * 0.35;
        float spin = rite < 0 ? time * 1.0f : time * 1.0f + p * p * 900.0f;
        float scale = rite < 0 ? 0.38f : 0.38f * (1.0f - Mth.clamp((p - 0.85f) / 0.15f, 0.0f, 1.0f) * 0.8f);
        for (int i = 0; i < 3; i++) {
            ItemStack stack = be.slot(i);
            if (stack.isEmpty()) {
                continue;
            }
            double a = Math.toRadians(spin + i * 120.0);
            pose.pushPose();
            pose.translate(0.5 + Math.cos(a) * radius,
                    height + Mth.sin(time * 0.1f + i * 2.1f) * 0.035 * (1.0f - p), 0.5 + Math.sin(a) * radius);
            pose.mulPose(Axis.YP.rotationDegrees(-spin - i * 120.0f + 90.0f));
            pose.scale(scale, scale, scale);
            render(be, stack, pose, buffers, rite < 0 ? light : glowLight);
            pose.popPose();
        }
    }

    private static void render(SpellTransmuterBlockEntity be, ItemStack stack, PoseStack pose, MultiBufferSource buffers,
                               int light) {
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light,
                OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(), (int) be.getBlockPos().asLong());
    }

    @Override
    public boolean shouldRenderOffScreen(SpellTransmuterBlockEntity be) {
        return true;
    }
}
