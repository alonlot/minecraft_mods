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
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * First-person wand casting. While a wand is in the main hand the arm is drawn holding it (vanilla draws
 * the wand alone), and every cast plays the spell's own wand gesture (CastGestures) instead of the vanilla
 * punch; a channelled spell keeps the wand up while its key is held. At the moment of release the wand tip flares in
 * the spell colour with a white core and a few crackling arcs; the tip also glows while a cast is held.
 * Other casters (players in third person, wizards) get the flare as world particles at their hand instead.
 * Visual only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class WandCastAnimation {

    /** Wand tip in the centred item model space (16x16 handheld sprite, tip at pixel 15, 1). */
    private static final Vector3f TIP = new Vector3f(0.40F, 0.38F, 0.0F);

    /** The wand tip in first-person view space as last drawn, and the frame it was drawn on. */
    private static final Vector3f LAST_TIP = new Vector3f();
    private static long lastTipFrame = Long.MIN_VALUE;

    private WandCastAnimation() {
    }

    /**
     * Where the local player's wand tip is in the world, for effects drawn in the world that must meet the
     * first-person wand (the ribbon beam). Null when the wand was not drawn last frame (third person, no wand).
     * The hand is drawn with its own field of view, so the view-space point is rescaled to the world's.
     */
    @javax.annotation.Nullable
    public static Vec3 localTipInWorld(net.minecraft.client.Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (net.minecraft.Util.getNanos() - lastTipFrame > 200_000_000L) {
            return null; // not drawn for a while: no first-person wand on screen
        }
        double worldFov = minecraft.options.fov().get();
        double scale = Math.tan(Math.toRadians(worldFov) / 2.0) / Math.tan(Math.toRadians(70.0) / 2.0);
        Vector3f look = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        double depth = -LAST_TIP.z;
        double x = LAST_TIP.x * scale;
        double y = LAST_TIP.y * scale;
        return camera.getPosition()
                .add(look.x * depth, look.y * depth, look.z * depth)
                .add(up.x * y, up.y * y, up.z * y)
                .add(-left.x * x, -left.y * x, -left.z * x);
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
        CastGestures.Cast cast = CastGestures.castOf(player);
        float t = cast == null ? -1.0F : cast.progress(player.tickCount + partialTick);
        float[] f = t >= 0.0F
                ? CastGestures.pose(cast, t)
                : CastGestures.mix(new float[4], CastGestures.localHoldPose(), CastGestures.localHoldWeight(partialTick));
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();
        int light = event.getPackedLight();

        pose.pushPose();
        if (t >= 0.0F) {
            float[] shift = cast.variant().screenShift(t); // this cast's own place on screen
            pose.translate(side * shift[0], shift[1], 0.0F);
        }
        // The gesture turns the whole arm about the shoulder, low and to the side, off screen.
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
        LAST_TIP.set(tip);
        lastTipFrame = net.minecraft.Util.getNanos();

        recordSwipe(cast, t, tip);
        drawSwipe(stack, cast, buffer);
        drawTipLight(minecraft, player, stack, cast, t, tip, buffer);
    }

    // ---------------------------------------------------------------- the swipe streak

    /** How long a point of the swipe streak lasts, in nanoseconds. */
    private static final long SWIPE_LIFE = 170_000_000L;
    private static final int SWIPE_MAX = 48;
    /** Recent wand tip positions (view space) during a stroke, oldest first, with the time each was drawn. */
    private static final java.util.ArrayDeque<Vector3f> SWIPE = new java.util.ArrayDeque<>();
    private static final java.util.ArrayDeque<Long> SWIPE_TIME = new java.util.ArrayDeque<>();

    /** While the wand is cutting through its stroke, the tip leaves a thin streak of the spell's light. */
    private static void recordSwipe(@javax.annotation.Nullable CastGestures.Cast cast, float t, Vector3f tip) {
        long now = net.minecraft.Util.getNanos();
        while (!SWIPE_TIME.isEmpty() && now - SWIPE_TIME.peekFirst() > SWIPE_LIFE) {
            SWIPE_TIME.pollFirst();
            SWIPE.pollFirst();
        }
        if (cast == null || t < 0.0F || t > cast.gesture().release + 0.25F) {
            return;
        }
        Vector3f last = SWIPE.peekLast();
        if (last != null && last.distanceSquared(tip) < 1.0E-6F) {
            return;
        }
        SWIPE.addLast(new Vector3f(tip));
        SWIPE_TIME.addLast(now);
        if (SWIPE.size() > SWIPE_MAX) {
            SWIPE.pollFirst();
            SWIPE_TIME.pollFirst();
        }
    }

    private static void drawSwipe(ItemStack stack, @javax.annotation.Nullable CastGestures.Cast cast,
                                  MultiBufferSource buffer) {
        if (SWIPE.size() < 2) {
            return;
        }
        SpellType spell = cast != null && cast.spell() != null ? cast.spell() : MagicWandItem.getSelectedSpell(stack);
        float[] color = spell != null ? SpellFx.lightColor(spell) : new float[]{1.0F, 1.0F, 1.0F};
        float[] hot = SpellFx.whiten(color, 0.5F);
        Vector3f[] points = SWIPE.toArray(new Vector3f[0]);
        Long[] times = SWIPE_TIME.toArray(new Long[0]);
        long now = net.minecraft.Util.getNanos();
        Matrix4f matrix = new Matrix4f();
        VertexConsumer light = buffer.getBuffer(ModRenderTypes.SPELL_GLOW);
        Vector3f side = new Vector3f();
        Vector3f prevSide = null;
        for (int i = 0; i < points.length - 1; i++) {
            Vector3f a = points[i];
            Vector3f b = points[i + 1];
            // Sideways in the screen: across the segment and across the line of sight.
            new Vector3f(b).sub(a).cross(b, side);
            if (side.lengthSquared() < 1.0E-12F) {
                continue;
            }
            side.normalize();
            if (prevSide == null) {
                prevSide = new Vector3f(side);
            }
            float fa = 1.0F - Math.min(1.0F, (now - times[i]) / (float) SWIPE_LIFE);
            float fb = 1.0F - Math.min(1.0F, (now - times[i + 1]) / (float) SWIPE_LIFE);
            float wa = 0.004F + 0.012F * fa;
            float wb = 0.004F + 0.012F * fb;
            ribbonQuad(light, matrix, a, prevSide, wa * 3.0F, b, side, wb * 3.0F, color, 0.45F * fa, 0.45F * fb);
            ribbonQuad(light, matrix, a, prevSide, wa, b, side, wb, hot, fa, fb);
            prevSide.set(side);
        }
    }

    private static void ribbonQuad(VertexConsumer consumer, Matrix4f matrix, Vector3f a, Vector3f sideA, float wa,
                                   Vector3f b, Vector3f sideB, float wb, float[] c, float alphaA, float alphaB) {
        SpellFx.vertex(consumer, matrix, a.x + sideA.x * wa, a.y + sideA.y * wa, a.z + sideA.z * wa, 0.0F, 0.5F, c[0], c[1], c[2], alphaA);
        SpellFx.vertex(consumer, matrix, a.x - sideA.x * wa, a.y - sideA.y * wa, a.z - sideA.z * wa, 1.0F, 0.5F, c[0], c[1], c[2], alphaA);
        SpellFx.vertex(consumer, matrix, b.x - sideB.x * wb, b.y - sideB.y * wb, b.z - sideB.z * wb, 1.0F, 0.5F, c[0], c[1], c[2], alphaB);
        SpellFx.vertex(consumer, matrix, b.x + sideB.x * wb, b.y + sideB.y * wb, b.z + sideB.z * wb, 0.0F, 0.5F, c[0], c[1], c[2], alphaB);
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
    private static void drawTipLight(Minecraft minecraft, LocalPlayer player, ItemStack stack,
                                     @javax.annotation.Nullable CastGestures.Cast cast, float t,
                                     Vector3f tip, MultiBufferSource buffer) {
        SpellType spell = t >= 0.0F && cast.spell() != null ? cast.spell() : MagicWandItem.getSelectedSpell(stack);
        float release = t >= 0.0F ? cast.gesture().release : 1.0F;
        float[] color = spell != null ? SpellFx.lightColor(spell) : new float[]{1.0F, 1.0F, 1.0F};
        float[] hot = SpellFx.whiten(color, 0.6F);

        float flare = 0.0F;
        if (t >= release) {
            float k = (t - release) / 0.3F;
            flare = k < 1.0F ? 1.0F - k : 0.0F;
        }
        float charge = t >= 0.0F && t < release ? t / release : 0.0F; // gathers light during the wind-up
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
            long seed = player.tickCount * 13L + (cast == null ? 0 : cast.start());
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
        Entity owner = beam.getOwner();
        if (!(owner instanceof LivingEntity caster) || beam.distanceToSqr(caster.getEyePosition()) > 9.0) {
            return; // a beam far from its caster is one coming into tracking range, not a fresh cast
        }
        CastGestures.onCasterBeam(caster, beam.getSpellType()); // NPCs: the beam names the gesture
        if (SpellFx.NO_ORB.contains(beam.getSpellType())) {
            return;
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

}
