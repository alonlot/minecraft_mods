package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.network.SpellPackets;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client half of hold-to-pick. While the server says the local player is picking a piston lock, the wand is
 * drawn tipped towards the middle of the screen, twisting as if working a lock. Visual only; the server owns
 * the pick and ends it (LockPickStatePacket false), and a logout clears it.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class LockPickClient {
    private static boolean picking;
    /** Game time until which the end-of-pick action bar line stays up (client cooldown line stays quiet). */
    private static long quietUntil;

    private LockPickClient() {
    }

    public static void accept(SpellPackets.LockPickStatePacket packet) {
        picking = packet.picking();
        if (picking) {
            quietUntil = 0;
        } else {
            // Any false reopens the quiet window, also one that arrives after the pick ended (the playback's
            // last line), so the cooldown line does not overwrite it. It never starts the animation.
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                quietUntil = minecraft.level.getGameTime() + 40;
            }
        }
    }

    /**
     * True while the server's lock picking lines own the action bar: while picking, and for two seconds after,
     * so the end-of-pick line is not overwritten by the cooldown line on the next tick.
     */
    public static boolean ownsActionBar() {
        if (isPicking()) {
            return true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.level.getGameTime() < quietUntil;
    }

    /** Picking now, as far as this client can tell: the server said so and a wand is still in the main hand. */
    public static boolean isPicking() {
        LocalPlayer player = Minecraft.getInstance().player;
        return picking && player != null && MagicWandItem.isAnyWand(player.getMainHandItem());
    }

    /**
     * Draws the main-hand wand in its picking pose instead of the vanilla one. A wand is drawn without an arm
     * in first person, so cancelling and calling renderItem with the vanilla hand offset is the whole job.
     * Runs at LOW priority so a handler that already cancelled the hand (cloak, possession) is never delivered
     * to here (cancelled events skip handlers that do not ask for them). A possessing caster is skipped too.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !isPicking() || PossessionClient.isCaster()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!MagicWandItem.isAnyWand(stack)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        event.setCanceled(true);
        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        float side = right ? 1.0F : -1.0F;
        float t = player.tickCount + event.getPartialTick();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(side * 0.56F, -0.52F, -0.72F); // the vanilla first-person item position
        pose.translate(side * -0.16F, 0.10F + 0.012F * Mth.sin(t * 1.7F), -0.10F); // in towards the lock
        pose.mulPose(Axis.YP.rotationDegrees(side * 18.0F));
        pose.mulPose(Axis.XP.rotationDegrees(-28.0F + 5.0F * Mth.sin(t * 0.8F))); // tip down at the keyhole
        pose.mulPose(Axis.ZP.rotationDegrees(side * 14.0F * Mth.sin(t * 2.2F))); // the picking twist
        minecraft.gameRenderer.itemInHandRenderer.renderItem(player, stack,
                right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                !right, pose, event.getMultiBufferSource(), event.getPackedLight());
        pose.popPose();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        picking = false;
        quietUntil = 0;
    }
}
