package com.hogwartsmagic.cosy.client.card;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.cosy.WizardCardItem;
import com.hogwartsmagic.cosy.WizardCards;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A held Famous Wizard Card in first person: it replaces the vanilla hand item and draws the living card large, the way
 * vanilla holds a map. Two-handed (both arms come up, as {@code renderTwoHandedMap}) when the off hand is empty,
 * otherwise one-handed. The geometry is vanilla's own map code, transcribed, because the hand event hands us the bare
 * pose and the arm helpers are private. The view seed is the player's UUID hash (xor 1 for the off hand, so two cards
 * are not in step). Client only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CardHandRender {
    /** How big the card is against vanilla's 0.38-wide map square; tuned by eye (see the report). */
    static final float ONE_HANDED_SCALE = 1.0F;
    static final float TWO_HANDED_SCALE = 0.85F;

    private static final CardCues MAIN_CUES = new CardCues();
    private static final CardCues OFF_CUES = new CardCues();

    private CardHandRender() {
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        WizardCards.Card card = WizardCardItem.cardOf(event.getItemStack());
        if (card == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        event.setCanceled(true);
        if (player.isScoping()) {
            return; // vanilla draws no held item while looking through a spyglass
        }

        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        float partial = event.getPartialTick();
        boolean mainHand = event.getHand() == InteractionHand.MAIN_HAND;
        HumanoidArm arm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
        long seed = player.getUUID().hashCode() ^ (mainHand ? 0L : 1L);

        pose.pushPose();
        if (mainHand && player.getOffhandItem().isEmpty()) {
            twoHanded(pose, buffers, light, event.getInterpolatedPitch(), event.getEquipProgress(),
                    event.getSwingProgress(), player, card, seed, partial);
        } else {
            oneHanded(pose, buffers, light, event.getEquipProgress(), event.getSwingProgress(), arm, player, card, seed, partial);
        }
        pose.popPose();

        (mainHand ? MAIN_CUES : OFF_CUES).update(CardArt.stateOf(card.id(), seed, partial), card,
                CardAssets.forKey(card.key()), 0.35F);
    }

    // ---- vanilla ItemInHandRenderer.renderTwoHandedMap / renderOneHandedMap, with the card in place of the map

    private static void twoHanded(PoseStack pose, MultiBufferSource buffers, int light, float pitch, float equip,
                                  float swing, AbstractClientPlayer player, WizardCards.Card card, long seed, float partial) {
        float sqrtSwing = Mth.sqrt(swing);
        float lift = -0.2F * Mth.sin(swing * Mth.PI);
        float push = -0.4F * Mth.sin(sqrtSwing * Mth.PI);
        pose.translate(0.0F, -lift / 2.0F, push);
        float tilt = mapTilt(pitch);
        pose.translate(0.0F, 0.04F + equip * -1.2F + tilt * -0.5F, -0.72F);
        pose.mulPose(Axis.XP.rotationDegrees(tilt * -85.0F));
        if (!player.isInvisible()) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(90.0F));
            arm(pose, buffers, light, player, HumanoidArm.RIGHT);
            arm(pose, buffers, light, player, HumanoidArm.LEFT);
            pose.popPose();
        }
        float swingTilt = Mth.sin(sqrtSwing * Mth.PI);
        pose.mulPose(Axis.XP.rotationDegrees(swingTilt * 20.0F));
        pose.scale(2.0F, 2.0F, 2.0F);
        cardInHand(pose, buffers, light, card, seed, partial, TWO_HANDED_SCALE);
    }

    private static void oneHanded(PoseStack pose, MultiBufferSource buffers, int light, float equip, float swing,
                                  HumanoidArm side, AbstractClientPlayer player, WizardCards.Card card, long seed, float partial) {
        float f = side == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        pose.translate(f * 0.125F, -0.125F, 0.0F);
        if (!player.isInvisible()) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(f * 10.0F));
            playerArm(pose, buffers, light, equip, swing, side, player);
            pose.popPose();
        }
        pose.pushPose();
        pose.translate(f * 0.51F, -0.08F + equip * -1.2F, -0.75F);
        float s1 = Mth.sqrt(swing);
        float s2 = Mth.sin(s1 * Mth.PI);
        float s3 = -0.5F * s2;
        float s4 = 0.4F * Mth.sin(s1 * Mth.TWO_PI);
        float s5 = -0.3F * Mth.sin(swing * Mth.PI);
        pose.translate(f * s3, s4 - 0.3F * s2, s5);
        pose.mulPose(Axis.XP.rotationDegrees(s2 * -45.0F));
        pose.mulPose(Axis.YP.rotationDegrees(f * s2 * -30.0F));
        cardInHand(pose, buffers, light, card, seed, partial, ONE_HANDED_SCALE);
        pose.popPose();
    }

    /** Vanilla's renderMap transform chain, then the card centred in it. */
    private static void cardInHand(PoseStack pose, MultiBufferSource buffers, int light, WizardCards.Card card, long seed,
                                   float partial, float scale) {
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
        pose.scale(0.38F * scale, 0.38F * scale, 0.38F * scale);
        pose.translate(-0.5F, -0.7F, 0.0F);   // the card quad is 1 wide, 1.4 tall, top-left at the origin
        CardArt.drawCard(pose, buffers, light, card.id(), seed, partial);
    }

    private static float mapTilt(float pitch) {
        float f = 1.0F - pitch / 45.0F + 0.1F;
        f = Mth.clamp(f, 0.0F, 1.0F);
        return -Mth.cos(f * Mth.PI) * 0.5F + 0.5F;
    }

    /** One arm reaching in to hold the sides of the card (vanilla renderMapHand). */
    private static void arm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, HumanoidArm side) {
        PlayerRenderer renderer = (PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        pose.pushPose();
        float f = side == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        pose.mulPose(Axis.YP.rotationDegrees(92.0F));
        pose.mulPose(Axis.XP.rotationDegrees(45.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(f * -41.0F));
        pose.translate(f * 0.3F, -1.1F, 0.45F);
        if (side == HumanoidArm.RIGHT) {
            renderer.renderRightHand(pose, buffers, light, player);
        } else {
            renderer.renderLeftHand(pose, buffers, light, player);
        }
        pose.popPose();
    }

    /** The holding arm of the one-handed pose (vanilla renderPlayerArm). */
    private static void playerArm(PoseStack pose, MultiBufferSource buffers, int light, float equip, float swing,
                                  HumanoidArm side, AbstractClientPlayer player) {
        boolean right = side != HumanoidArm.LEFT;
        float f = right ? 1.0F : -1.0F;
        float s1 = Mth.sqrt(swing);
        float f2 = -0.3F * Mth.sin(s1 * Mth.PI);
        float f3 = 0.4F * Mth.sin(s1 * Mth.TWO_PI);
        float f4 = -0.4F * Mth.sin(swing * Mth.PI);
        pose.translate(f * (f2 + 0.64000005F), f3 + -0.6F + equip * -0.6F, f4 + -0.71999997F);
        pose.mulPose(Axis.YP.rotationDegrees(f * 45.0F));
        float f5 = Mth.sin(swing * swing * Mth.PI);
        float f6 = Mth.sin(s1 * Mth.PI);
        pose.mulPose(Axis.YP.rotationDegrees(f * f6 * 70.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(f * f5 * -20.0F));
        pose.translate(f * -1.0F, 3.6F, 3.5F);
        pose.mulPose(Axis.ZP.rotationDegrees(f * 120.0F));
        pose.mulPose(Axis.XP.rotationDegrees(200.0F));
        pose.mulPose(Axis.YP.rotationDegrees(f * -135.0F));
        pose.translate(f * 5.6F, 0.0F, 0.0F);
        PlayerRenderer renderer = (PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        pose.pushPose();
        if (right) {
            renderer.renderRightHand(pose, buffers, light, player);
        } else {
            renderer.renderLeftHand(pose, buffers, light, player);
        }
        pose.popPose();
    }
}
