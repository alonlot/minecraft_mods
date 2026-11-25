package com.hogwartsmagic.cosy.client;

import com.hogwartsmagic.cosy.CookingPotBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Draws what is in a cooking pot: the ingredients float and bob on the broth, a finished dish rises and turns, and
 * the ladle (a stick) sweeps round the pot while it is stirred, faster while it is actually cooking.
 */
public class CookingPotRenderer implements BlockEntityRenderer<CookingPotBlockEntity> {
    private static final ItemStack LADLE = new ItemStack(Items.STICK);
    /** The broth sits at 8 px; things float just above it. */
    private static final float SURFACE = 8.0F / 16.0F;

    public CookingPotRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CookingPotBlockEntity pot, float partialTick, PoseStack pose, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        if (pot.getLevel() == null) {
            return;
        }
        List<ItemStack> items = pot.renderItems();
        if (items.isEmpty() && !pot.isStirring()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float t = pot.getLevel().getGameTime() + partialTick;
        int seed = pot.getBlockPos().hashCode();

        if (pot.isDone()) {
            // The finished dish: one stack, rising a little and turning slowly.
            pose.pushPose();
            pose.translate(0.5F, SURFACE + 0.22F + Mth.sin(t * 0.08F) * 0.04F, 0.5F);
            pose.mulPose(Axis.YP.rotationDegrees(t * 3.0F));
            pose.scale(0.55F, 0.55F, 0.55F);
            mc.getItemRenderer().renderStatic(items.get(0), ItemDisplayContext.FIXED, packedLight, packedOverlay,
                    pose, buffer, pot.getLevel(), seed);
            pose.popPose();
            return;
        }

        int n = items.size();
        float swirl = pot.isActive() ? 0.09F : pot.isStirring() ? 0.05F : 0.012F;
        for (int i = 0; i < n; i++) {
            float angle = (float) (i * (Math.PI * 2.0) / Math.max(1, n)) + t * swirl;
            float radius = n == 1 ? 0.0F : 0.19F;
            float bob = Mth.sin(t * 0.11F + i * 1.7F) * 0.025F;
            pose.pushPose();
            pose.translate(0.5F + Mth.cos(angle) * radius, SURFACE + 0.06F + bob, 0.5F + Mth.sin(angle) * radius);
            pose.mulPose(Axis.YP.rotationDegrees(t * 2.0F + i * 53.0F));
            pose.mulPose(Axis.XP.rotationDegrees(70.0F));
            pose.scale(0.3F, 0.3F, 0.3F);
            mc.getItemRenderer().renderStatic(items.get(i), ItemDisplayContext.FIXED, packedLight, packedOverlay,
                    pose, buffer, pot.getLevel(), seed + i);
            pose.popPose();
        }

        // The ladle: a stick standing in the pot, swung round the middle while stirring.
        float sweep = pot.isStirring() ? t * (pot.isActive() ? 0.34F : 0.22F) : 0.6F;
        pose.pushPose();
        pose.translate(0.5F + Mth.cos(sweep) * 0.13F, SURFACE + 0.12F, 0.5F + Mth.sin(sweep) * 0.13F);
        pose.mulPose(Axis.YP.rotation(-sweep));
        pose.mulPose(Axis.ZP.rotationDegrees(-22.0F));
        pose.scale(0.85F, 0.85F, 0.85F);
        mc.getItemRenderer().renderStatic(LADLE, ItemDisplayContext.FIXED, packedLight, packedOverlay, pose, buffer,
                pot.getLevel(), seed);
        pose.popPose();
    }
}
