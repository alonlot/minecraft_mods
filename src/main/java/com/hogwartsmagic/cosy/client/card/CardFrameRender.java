package com.hogwartsmagic.cosy.client.card;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.cosy.WizardCardItem;
import com.hogwartsmagic.cosy.WizardCards;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.EntityType;
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
    /**
     * The card's width in blocks. The inner opening of an item frame is 10 px (0.625) across, and the card is 1.4 times
     * taller than wide, so 0.446 wide makes it 0.625 tall: it fits the opening, in visible and invisible frames alike.
     */
    static final float CARD_BLOCKS = 0.446F;

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
        // vanilla lifts a map 1/128 toward the viewer so it clears the surface behind it (the wall, in an invisible frame)
        pose.translate(0.0F, 0.0F, -1.0F / 128.0F);
        pose.scale(CARD_BLOCKS, CARD_BLOCKS, CARD_BLOCKS);
        pose.translate(-0.5F, -0.7F, 0.0F);   // 1 wide, 1.4 tall, top-left at the origin
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        int light = event.getItemFrameEntity().getType() == EntityType.GLOW_ITEM_FRAME
                ? LightTexture.FULL_BRIGHT : event.getPackedLight();
        CardArt.drawCard(pose, event.getMultiBufferSource(), light, card.id(),
                event.getItemFrameEntity().getId(), partial);
        pose.popPose();
    }
}
