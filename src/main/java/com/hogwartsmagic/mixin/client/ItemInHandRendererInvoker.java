package com.hogwartsmagic.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Lets a first-person hand be drawn under our own transform: the cast gesture cancels Forge's RenderHandEvent for
 * the wand hand and redraws it here inside a pushed pose, so the transform can't leak into the other hand.
 */
@Mixin(ItemInHandRenderer.class)
public interface ItemInHandRendererInvoker {
    @Invoker("renderArmWithItem")
    void hogwartsmagic$renderArmWithItem(AbstractClientPlayer player, float partialTick, float pitch,
                                         InteractionHand hand, float swingProgress, ItemStack stack,
                                         float equipProgress, PoseStack poseStack, MultiBufferSource buffer,
                                         int packedLight);
}
