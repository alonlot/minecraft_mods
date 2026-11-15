package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.spell.SpellType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * First-person wand casting. While a wand is in the main hand the arm is drawn holding it (vanilla draws
 * the wand alone), and every cast swing plays a wand flick instead of the vanilla punch: a short wind-up,
 * a fast slash towards the crosshair, then a settle back. At the moment of release the wand tip flares in
 * the spell colour with a white core and a few crackling arcs; the tip also glows while a cast is held.
 * Other casters (players in third person, wizards) get the flare as world particles at their hand instead.
 * Visual only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class WandCastAnimation {

    /** Length of the flick in ticks. */
    private static final float FLICK_TICKS = 9.0F;
    /** Flick progress at which the spell leaves the wand. */
    private static final float RELEASE = 0.36F;
    /** Wand tip in the centred item model space (16x16 handheld sprite, tip at pixel 15, 1). */
    private static final Vector3f TIP = new Vector3f(0.40F, 0.38F, 0.0F);

    private static int flickStart = Integer.MIN_VALUE / 2;
    private static SpellType flickSpell;
    private static boolean wasSwinging;
    private static int lastSwingTime;

    private WandCastAnimation() {
    }

    /** Flick progress 0..1, or -1 when no flick is playing. */
    private static float flickProgress(LocalPlayer player, float partialTick) {
        float t = (player.tickCount - flickStart + partialTick) / FLICK_TICKS;
        return t >= 0.0F && t < 1.0F ? t : -1.0F;
    }

    /** Every wand swing is a cast (all cast paths swing the main hand), so a new swing starts a flick. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            wasSwinging = false;
            return;
        }
        boolean swinging = player.swinging;
        if (swinging && (!wasSwinging || player.swingTime < lastSwingTime)
                && MagicWandItem.isAnyWand(player.getMainHandItem())) {
            flickStart = player.tickCount;
            flickSpell = MagicWandItem.getSelectedSpell(player.getMainHandItem());
        }
        wasSwinging = swinging;
        lastSwingTime = player.swingTime;
    }

    // ---------------------------------------------------------------- the flick curve

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * Pose offsets of the flick at progress t: {pitch, yaw, roll, forward}. Key poses: rest (0), wind-up
     * (0.2: raised and drawn back), strike (0.4: slashed down and across towards the crosshair), rest (1).
     */
    private static float[] flickPose(float t) {
        float[] rest = {0.0F, 0.0F, 0.0F, 0.0F};
        float[] windUp = {-34.0F, -22.0F, -14.0F, 0.10F};
        float[] strike = {-8.0F, 34.0F, 14.0F, -0.36F};
        if (t < 0.0F) {
            return rest;
        }
        if (t < 0.2F) {
            return mix(rest, windUp, smooth(t / 0.2F));
        }
        if (t < 0.4F) {
            float k = (t - 0.2F) / 0.2F;
            return mix(windUp, strike, k * k * (3.0F - 2.0F * k));
        }
        return mix(strike, rest, smooth((t - 0.4F) / 0.6F));
    }

    private static float[] mix(float[] a, float[] b, float k) {
        float[] out = new float[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = Mth.lerp(k, a[i], b[i]);
        }
        return out;
    }

    // ---------------------------------------------------------------- first person drawing

    /**
     * Draws arm and wand. LOWEST priority: the cloak (hides the hand), Possession and LockPickClient (its own
     * picking pose) cancel first, and a cancelled event never reaches this handler.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || PossessionClient.isCaster()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!MagicWandItem.isAnyWand(stack)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        EntityRenderer<? super LocalPlayer> renderer = minecraft.getEntityRenderDispatcher().getRenderer(player);
        if (!(renderer instanceof PlayerRenderer playerRenderer)) {
            return;
        }
        event.setCanceled(true);

        float partialTick = event.getPartialTick();
        HumanoidArm arm = player.getMainArm();
        boolean right = arm == HumanoidArm.RIGHT;
        float side = right ? 1.0F : -1.0F;
        float t = flickProgress(player, partialTick);
        float[] f = flickPose(t);
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();
        int light = event.getPackedLight();

        pose.pushPose();
        // The flick turns the whole arm about the shoulder, low and to the side, off screen.
        pose.translate(side * 0.75F, -0.95F, -0.35F + f[3]);
        pose.mulPose(Axis.YP.rotationDegrees(side * f[1]));
        pose.mulPose(Axis.XP.rotationDegrees(-f[0]));
        pose.mulPose(Axis.ZP.rotationDegrees(side * f[2]));
        pose.translate(side * -0.75F, 0.95F, 0.35F);
        applyArmTransform(pose, side, event.getEquipProgress());
        if (right) {
            playerRenderer.renderRightHand(pose, buffer, light, player);
        } else {
            playerRenderer.renderLeftHand(pose, buffer, light, player);
        }
        // Into the hand, the same way a held item is placed in third person.
        playerRenderer.getModel().translateToHand(arm, pose);
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.translate(side / 16.0F, 0.125F, -0.625F);
        ItemDisplayContext context = right ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        minecraft.gameRenderer.itemInHandRenderer.renderItem(player, stack, context, !right, pose, buffer, light);

        Vector3f tip = tipPosition(minecraft, player, stack, context, !right, pose);
        pose.popPose();

        drawTipLight(minecraft, player, stack, t, tip, buffer);
    }

    /** Vanilla's first-person bare arm transform (ItemInHandRenderer.renderPlayerArm) with no attack swing. */
    private static void applyArmTransform(PoseStack pose, float side, float equipProgress) {
        pose.translate(side * 0.64000005F, -0.6F + equipProgress * -0.6F, -0.71999997F);
        pose.mulPose(Axis.YP.rotationDegrees(side * 45.0F));
        pose.translate(side * -1.0F, 3.6F, 3.5F);
        pose.mulPose(Axis.ZP.rotationDegrees(side * 120.0F));
        pose.mulPose(Axis.XP.rotationDegrees(200.0F));
        pose.mulPose(Axis.YP.rotationDegrees(side * -135.0F));
        pose.translate(side * 5.6F, 0.0F, 0.0F);
    }

    /** The wand tip in view space, found by applying the wand's own display transform to the tip pixel. */
    private static Vector3f tipPosition(Minecraft minecraft, LivingEntity holder, ItemStack stack,
                                        ItemDisplayContext context, boolean leftHand, PoseStack pose) {
        BakedModel model = minecraft.getItemRenderer().getModel(stack, holder.level(), holder, 0);
        pose.pushPose();
        model.applyTransform(context, pose, leftHand);
        Vector3f tip = pose.last().pose().transformPosition(new Vector3f(TIP));
        pose.popPose();
        return tip;
    }

    /** The tip light: a flare with crackling arcs at release, and a steady glow while a cast is held. */
    private static void drawTipLight(Minecraft minecraft, LocalPlayer player, ItemStack stack, float t,
                                     Vector3f tip, MultiBufferSource buffer) {
        SpellType spell = t >= 0.0F && flickSpell != null ? flickSpell : MagicWandItem.getSelectedSpell(stack);
        float[] color = spell != null ? SpellFx.lightColor(spell) : new float[]{1.0F, 1.0F, 1.0F};
        float[] hot = SpellFx.whiten(color, 0.6F);

        float flare = 0.0F;
        if (t >= RELEASE) {
            float k = (t - RELEASE) / 0.3F;
            flare = k < 1.0F ? 1.0F - k : 0.0F;
        }
        float charge = t >= 0.0F && t < RELEASE ? t / RELEASE : 0.0F; // gathers light during the wind-up
        float held = minecraft.options.keyUse.isDown() ? 1.0F : 0.0F;
        float glow = Math.max(Math.max(charge * 0.7F, held * 0.8F), flare);
        if (glow <= 0.01F) {
            return;
        }
        Matrix4f matrix = new Matrix4f().translate(tip);
        VertexConsumer light = buffer.getBuffer(ModRenderTypes.SPELL_GLOW);
        SpellFx.glowQuad(light, matrix, 0, 0, 0, 0.07F + 0.05F * glow, 1.0F, 1.0F, 1.0F, glow);
        SpellFx.glowQuad(light, matrix, 0, 0, 0, 0.14F + 0.10F * glow, hot[0], hot[1], hot[2], 0.75F * glow);
        if (flare > 0.0F) {
            SpellFx.glowQuad(light, matrix, 0, 0, 0, 0.2F + 0.45F * flare, color[0], color[1], color[2], 0.9F * flare);
            VertexConsumer bolts = buffer.getBuffer(RenderType.lightning());
            long seed = player.tickCount * 13L + flickStart;
            for (int i = 0; i < 4; i++) {
                SpellFx.crackle(bolts, matrix, seed + i * 101L, 0.25F + 0.2F * flare, 0.005F,
                        hot[0], hot[1], hot[2], flare);
            }
        }
    }

    // ---------------------------------------------------------------- everyone else's casts

    /**
     * A beam appearing is the cue for its caster's wand flare as seen by others. The local player in first
     * person is skipped: the hand flare above already shows it, in the right place on screen.
     */
    @SubscribeEvent
    public static void onBeamJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() || !(event.getEntity() instanceof SpellBeamEntity beam)) {
            return;
        }
        if (SpellFx.NO_ORB.contains(beam.getSpellType())) {
            return;
        }
        Entity owner = beam.getOwner();
        if (!(owner instanceof LivingEntity caster) || beam.distanceToSqr(caster.getEyePosition()) > 9.0) {
            return; // a beam far from its caster is one coming into tracking range, not a fresh cast
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (caster == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        Vec3 look = caster.getViewVector(1.0F);
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        float side = caster.getMainArm() == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        Vec3 hand = caster.getEyePosition().add(look.scale(0.75)).add(right.scale(0.32 * side)).add(0.0, -0.32, 0.0);
        SpellFx.castFlash(hand, look, beam.getSpellType());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        flickStart = Integer.MIN_VALUE / 2;
        flickSpell = null;
        wasSwinging = false;
    }
}
