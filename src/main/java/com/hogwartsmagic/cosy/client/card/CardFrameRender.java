package com.hogwartsmagic.cosy.client.card;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.cosy.WizardCardItem;
import com.hogwartsmagic.cosy.WizardCards;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderItemInFrameEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A Famous Wizard Card in an item frame draws flat and alive. The event fires after vanilla has turned the pose to the
 * frame's rotation, so the card follows the frame's turn; we then set up the pose the way vanilla does for a map (a
 * half turn about Z, because map space has y pointing down). The view seed is the frame's entity id, so two frames
 * showing the same card are out of step. Client only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CardFrameRender {
    /** The card's width in blocks: the inner opening of an item frame is 0.75 wide. */
    static final float CARD_BLOCKS = 0.52F;

    private CardFrameRender() {
    }

    @SubscribeEvent
    public static void onRenderItemInFrame(RenderItemInFrameEvent event) {
        WizardCards.Card card = WizardCardItem.cardOf(event.getItemStack());
        if (card == null) {
            return;
        }
        event.setCanceled(true);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
        pose.scale(CARD_BLOCKS, CARD_BLOCKS, CARD_BLOCKS);
        pose.translate(-0.5F, -0.7F, 0.0F);   // 1 wide, 1.4 tall, top-left at the origin
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        CardArt.drawCard(pose, event.getMultiBufferSource(), event.getPackedLight(), card.id(),
                event.getItemFrameEntity().getId(), partial);
        pose.popPose();
    }
}
