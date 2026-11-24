package com.hogwartsmagic.client.fx.portkey;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.ModRenderTypes;
import com.hogwartsmagic.client.SpellFx;
import com.hogwartsmagic.portkey.PortkeyItem;
import com.hogwartsmagic.portkey.PortkeyTimeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The boot in the traveller's own hand. While the charge builds it rises from where the hand holds it into the
 * middle of the view and trembles, harder and faster, with a soft blue-white glow swelling behind it; at the hook it
 * flares and jerks forward; in the tunnel it hangs dead still in the middle of the spinning world (the one thing that
 * does not move: the camera's roll never reaches hand space), glowing full; after the landing it sinks back to
 * the hand, and an Old Portkey sinks and shrinks to nothing as it crumbles.
 *
 * <p>Low priority, like the lock-picking hand: earlier listeners (the cloak, the Pensieve) win.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class PortkeyHand {

    /** Where vanilla holds an item (side-signed x), and where the boot rises to. */
    private static final float REST_X = 0.56f, REST_Y = -0.52f, REST_Z = -0.72f;
    private static final float UP_X = 0.18f, UP_Y = -0.30f, UP_Z = -0.62f;
    /** The boot's size in hand space (blocks across). */
    private static final float BOOT_SCALE = 0.36f;
    /** The glow card behind it: size (blocks) and how far behind. */
    private static final float GLOW_SIZE = 0.62f, GLOW_BEHIND = 0.06f;
    /** The boot's tremble in the tunnel: almost still, the anchor. */
    private static final float TRANSIT_TREMBLE = 0.0025f;
    /** It sinks back to the hand between these ticks after the exit began. */
    private static final float SETTLE_FROM = 10.0f, SETTLE_TO = 20.0f;
    /** A crumbling boot sinks this far as it shrinks. */
    private static final float CRUMBLE_SINK = 0.25f;

    private PortkeyHand() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderHand(RenderHandEvent event) {
        if (!PortkeyClient.handActive()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof PortkeyItem)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        // Only one boot: the main hand's, or the off hand's when the main hand holds no Portkey.
        if (event.getHand() == InteractionHand.OFF_HAND
                && player.getMainHandItem().getItem() instanceof PortkeyItem) {
            return;
        }
        event.setCanceled(true);

        boolean rightArm = (player.getMainArm() == HumanoidArm.RIGHT) == (event.getHand() == InteractionHand.MAIN_HAND);
        float side = rightArm ? 1.0f : -1.0f;
        float t = PortkeyClient.ownChargeAge();
        float h = PortkeyClient.ownHookAge();
        float x = PortkeyClient.ownExitAge();
        float crumble = PortkeyClient.crumbleOf(event.getPartialTick());

        float ease;
        float rise;
        float tremble;
        if (h < 0.0f) {
            float fade = PortkeyClient.ownCancelFade();
            ease = t < 0.0f ? 0.0f : PortkeyTimeline.chargeEase(t) * fade;
            rise = t < 0.0f ? 0.0f : PortkeyTimeline.smoothstep(0.0f, PortkeyTimeline.BOOT_RISE_TICKS, t) * fade;
            tremble = PortkeyTimeline.bootTremble(ease);
        } else {
            ease = x < 0.0f ? 1.0f : 1.0f - PortkeyTimeline.smoothstep(0.0f, SETTLE_TO, x);
            rise = x < 0.0f ? 1.0f : 1.0f - PortkeyTimeline.smoothstep(SETTLE_FROM, SETTLE_TO, x);
            tremble = TRANSIT_TREMBLE;
        }
        if (crumble > 0.0f && h < 0.0f && t < 0.0f) {
            rise = 0.0f; // crumbling after the trip has ended: in the hand
        }
        // The hook: a jerk forward and a flare of the glow.
        float pulse = 0.0f;
        if (h >= 0.0f && h < 3.0f) {
            pulse = h < 1.0f ? h : 1.0f - (h - 1.0f) / 2.0f;
        }
        float hz = PortkeyTimeline.bootTrembleHz(ease);
        float s = (float) ((PortkeyClient.now() * 0.05) % 2000.0);
        float w = s * hz * Mth.TWO_PI;
        float tx = tremble * (Mth.sin(w) * 0.7f + Mth.sin(w * 2.3f + 1.1f) * 0.3f);
        float ty = tremble * (Mth.sin(w * 1.3f + 0.4f) * 0.7f + Mth.sin(w * 2.9f + 2.2f) * 0.3f);
        float tilt = tremble * 260.0f * Mth.sin(w * 1.7f + 0.9f); // degrees: it rattles as well as shakes

        float px = Mth.lerp(rise, side * REST_X, side * UP_X) + tx;
        float py = Mth.lerp(rise, REST_Y, UP_Y) + ty - CRUMBLE_SINK * crumble;
        float pz = Mth.lerp(rise, REST_Z, UP_Z) - PortkeyTimeline.HOOK_BOOT_JERK * pulse;
        float scale = BOOT_SCALE * (1.0f - crumble);
        if (scale <= 0.002f) {
            return;
        }

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(px, py, pz);

        // The glow behind it (additive spell light).
        float glowAlpha = (PortkeyTimeline.BOOT_GLOW + PortkeyTimeline.BOOT_GLOW_GAIN * ease) * (1.0f - crumble)
                * Math.min(1.0f, rise * 1.5f + 0.2f);
        float flare = 1.0f + (PortkeyTimeline.HOOK_BOOT_FLARE - 1.0f) * pulse;
        if (glowAlpha > 0.01f) {
            float flick = 0.9f + 0.1f * Mth.sin(w * 0.7f);
            VertexConsumer glow = event.getMultiBufferSource().getBuffer(ModRenderTypes.SPELL_GLOW);
            Matrix4f m = pose.last().pose();
            float half = GLOW_SIZE * 0.5f * flare * (0.8f + 0.2f * ease);
            float a = Math.min(1.0f, glowAlpha * flick);
            float z = -GLOW_BEHIND;
            SpellFx.vertex(glow, m, -half, half, z, 0, 0, PortkeyTimeline.GLOW_R, PortkeyTimeline.GLOW_G,
                    PortkeyTimeline.GLOW_B, a);
            SpellFx.vertex(glow, m, -half, -half, z, 0, 1, PortkeyTimeline.GLOW_R, PortkeyTimeline.GLOW_G,
                    PortkeyTimeline.GLOW_B, a);
            SpellFx.vertex(glow, m, half, -half, z, 1, 1, PortkeyTimeline.GLOW_R, PortkeyTimeline.GLOW_G,
                    PortkeyTimeline.GLOW_B, a);
            SpellFx.vertex(glow, m, half, half, z, 1, 0, PortkeyTimeline.GLOW_R, PortkeyTimeline.GLOW_G,
                    PortkeyTimeline.GLOW_B, a);
            // A smaller white-hot core once it is really going.
            float core = Math.max(0.0f, ease - 0.5f) * 2.0f * a;
            if (core > 0.01f) {
                float c = half * 0.45f;
                SpellFx.vertex(glow, m, -c, c, z + 0.01f, 0, 0, PortkeyTimeline.CORE_R, PortkeyTimeline.CORE_G,
                        PortkeyTimeline.CORE_B, core);
                SpellFx.vertex(glow, m, -c, -c, z + 0.01f, 0, 1, PortkeyTimeline.CORE_R, PortkeyTimeline.CORE_G,
                        PortkeyTimeline.CORE_B, core);
                SpellFx.vertex(glow, m, c, -c, z + 0.01f, 1, 1, PortkeyTimeline.CORE_R, PortkeyTimeline.CORE_G,
                        PortkeyTimeline.CORE_B, core);
                SpellFx.vertex(glow, m, c, c, z + 0.01f, 1, 0, PortkeyTimeline.CORE_R, PortkeyTimeline.CORE_G,
                        PortkeyTimeline.CORE_B, core);
            }
        }

        // The boot: face on, a little turned toward the hand it came from, rattling.
        pose.mulPose(Axis.YP.rotationDegrees(side * -14.0f * (1.0f - 0.5f * rise)));
        pose.mulPose(Axis.ZP.rotationDegrees(side * 6.0f + tilt));
        pose.mulPose(Axis.XP.rotationDegrees(8.0f * (1.0f - rise)));
        pose.scale(scale, scale, scale); // the item renderer centres the model itself
        int light = ease > 0.45f ? LightTexture.FULL_BRIGHT : event.getPackedLight();
        mc.getItemRenderer().renderStatic(player, stack, ItemDisplayContext.NONE, !rightArm, pose,
                event.getMultiBufferSource(), mc.level, light, OverlayTexture.NO_OVERLAY, player.getId());
        pose.popPose();
    }
}
