package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.spell.SpellType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Your own wand arm in first person: the whole arm drawn holding the 3D wand through its fist, posed by
 * FirstPersonWandRig (rest pose that breathes and trails a turn, each spell's gesture, the channel hold, the
 * Killing Curse held high while it gathers). While the wand cuts through a stroke its tip leaves a thin streak
 * of the spell's light; the tip glows faintly at rest, flares with arcs as the spell leaves, and sparkles on a
 * change of spell. Where the tip is drawn is also where the spell beams start ({@link #localTipInWorld}).
 * Visual only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class FirstPersonWand {

    /** The wand tip in the centred item model space: the end of the 3D wand (tools/gen_wand_models.py). */
    private static final Vector3f TIP = new Vector3f(0.40F, 0.40F, 0.0F);
    /** Where the hand grips the wand, in the same space: on the handle, 6.5 px down the diagonal. */
    private static final Vector3f GRIP = new Vector3f(-0.287F, -0.287F, 0.0F);
    /**
     * The strike lands this many ticks after the cast starts, at most: in first person the wind-up is played
     * faster when a gesture's own is longer, so the strike meets the beam leaving the wand.
     */
    private static final float WINDUP = 1.2F;

    private static final RenderType GLOW = ModRenderTypes.SPELL_GLOW;

    /** The arm's pose last frame (before the turn sway), and where the current cast set off from. */
    private static FirstPersonWandRig.Key lastPose;
    private static FirstPersonWandRig.Key castFrom;
    private static int fromCastStart = Integer.MIN_VALUE;

    /** Where the tip was as the current cast let its spell go (view space): the flash stays there. */
    private static final Vector3f FLARE_AT = new Vector3f();
    private static int flareCast = Integer.MIN_VALUE;

    /** The wand tip in view space as last drawn, and when (nanos). */
    private static final Vector3f LAST_TIP = new Vector3f();
    private static long lastTipFrame = Long.MIN_VALUE;

    private FirstPersonWand() {
    }

    /**
     * Where your wand tip is in the world, as drawn in first person, or null when it was not drawn last frame
     * (third person, no wand). The hand is drawn with its own field of view, so the point is rescaled to the
     * world's.
     */
    @javax.annotation.Nullable
    public static Vec3 localTipInWorld(Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (net.minecraft.Util.getNanos() - lastTipFrame > 200_000_000L) {
            return null;
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

    /**
     * Where a spell of yours leaves the wand: the drawn tip, or {@code fallback} when it was not drawn. While the
     * arm is still winding up (the beam can be on its way before the strike lands), the point the strike lands
     * on instead, a little below and right of the crosshair, so the beam never starts from the raised wand.
     */
    public static Vec3 localTipOr(Vec3 fallback) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 tip = localTipInWorld(camera);
        if (tip == null) {
            return fallback;
        }
        if (inWindup()) {
            Vector3f strike = strikeTip();
            if (strike != null) {
                return viewToWorld(camera, strike);
            }
            Vector3f look = camera.getLookVector();
            Vector3f up = camera.getUpVector();
            Vector3f left = camera.getLeftVector();
            return camera.getPosition().add(look.x * 0.9, look.y * 0.9, look.z * 0.9)
                    .add(up.x * -0.08, up.y * -0.08, up.z * -0.08)
                    .add(left.x * -0.06, left.y * -0.06, left.z * -0.06);
        }
        return tip;
    }

    /** Where the wand tip will be when your current cast strikes, in view space, or null. */
    @javax.annotation.Nullable
    private static Vector3f strikeTip() {
        Minecraft minecraft = Minecraft.getInstance();
        FirstPersonCast.Cast cast = FirstPersonCast.current();
        if (cast == null || minecraft.player == null) {
            return null;
        }
        FirstPersonWandRig.Motion motion = FirstPersonWandRig.motionOf(cast);
        FirstPersonWandRig.Key rest = FirstPersonWandRig.baseRest();
        FirstPersonWandRig.Key key = FirstPersonWandRig.pose(motion, cast.variant(), rest, motion.release(),
                FirstPersonWandRig.fromOf(cast, rest));
        float side = minecraft.player.getMainArm() == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        return new Vector3f(key.wand()).normalize().mul(0.7F).add(key.hand()).mul(side, 1.0F, 1.0F);
    }

    /** A point drawn in the hand's view space, in the world (the hand is drawn with its own field of view). */
    private static Vec3 viewToWorld(Camera camera, Vector3f p) {
        double worldFov = Minecraft.getInstance().options.fov().get();
        double scale = Math.tan(Math.toRadians(worldFov) / 2.0) / Math.tan(Math.toRadians(70.0) / 2.0);
        Vector3f look = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        double depth = -p.z;
        double x = p.x * scale;
        double y = p.y * scale;
        return camera.getPosition()
                .add(look.x * depth, look.y * depth, look.z * depth)
                .add(up.x * y, up.y * y, up.z * y)
                .add(-left.x * x, -left.y * x, -left.z * x);
    }

    /** Whether your arm is winding up a cast and has not struck yet. */
    private static boolean inWindup() {
        Minecraft minecraft = Minecraft.getInstance();
        FirstPersonCast.Cast cast = FirstPersonCast.current();
        if (cast == null || minecraft.player == null) {
            return false;
        }
        FirstPersonWandRig.Motion motion = FirstPersonWandRig.motionOf(cast);
        float since = minecraft.player.tickCount + minecraft.getTimer().getGameTimeDeltaPartialTick(false) - cast.start();
        return since >= 0.0F && since < releaseAfter(motion);
    }

    /** Gesture time: the cast's own clock, with the wind-up played faster so the strike lands by WINDUP. */
    static float gestureTime(FirstPersonWandRig.Motion motion, float since) {
        float release = motion.release();
        float windup = releaseAfter(motion);
        return since < windup ? since * release / Math.max(1.0E-3F, windup) : since - windup + release;
    }

    /** Ticks into a cast at which the spell leaves the wand, on the cast's own clock. */
    static float releaseAfter(FirstPersonWandRig.Motion motion) {
        // A pointing strike keeps its own sweep in (its arm visibly moving across), never sped up.
        return motion.point() ? motion.release() : Math.min(motion.release(), WINDUP);
    }

    /**
     * LOWEST priority: the cloak, the Pensieve, a beast's form, Possession and LockPickClient (its own picking
     * pose) cancel first, and a cancelled event never reaches this handler.
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
        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        float side = right ? 1.0F : -1.0F;
        FirstPersonCast.Cast cast = FirstPersonCast.current();
        float since = cast == null ? -1.0F : player.tickCount + partialTick - cast.start();
        FirstPersonWandRig.Motion motion = cast == null ? null : FirstPersonWandRig.motionOf(cast);
        float time = motion == null ? -1.0F : gestureTime(motion, since);
        boolean casting = motion != null && since >= 0.0F && time < motion.length();

        FirstPersonWandRig.Key rest = FirstPersonWandRig.rest(player, partialTick, cast);
        if (cast != null && cast.start() != fromCastStart) {
            // A new cast moves on from where the arm is now, not from rest.
            fromCastStart = cast.start();
            castFrom = lastPose != null ? lastPose : rest;
            FirstPersonWandRig.castFrom(cast.start(), castFrom);
        }
        FirstPersonWandRig.Key now = casting
                ? FirstPersonWandRig.pose(motion, cast.variant(), rest, time, castFrom != null ? castFrom : rest) : rest;
        float hold = FirstPersonCast.holdWeight(partialTick);
        if (hold > 0.0F) {
            FirstPersonWandRig.Motion held = casting ? motion
                    : FirstPersonWandRig.motionOf(FirstPersonCast.heldSpell(),
                            FirstPersonCast.forSpell(FirstPersonCast.heldSpell()));
            now = FirstPersonWandRig.mix(now, held.holdKey(), hold * hold * (3.0F - 2.0F * hold));
        }
        // The Killing Curse gathering: the wand held high over the shoulder until it strikes.
        AvadaChargeClientState.Charge charge = AvadaChargeClientState.get(player.getId());
        if (charge != null && !casting) {
            float c = charge.charge(AvadaChargeClientState.now(partialTick));
            FirstPersonWandRig.Key raised = FirstPersonWandRig.raisedKey();
            now = FirstPersonWandRig.mix(now, raised, c * c * (3.0F - 2.0F * c));
        }
        lastPose = now.copy(now.at()); // a copy: the equip drop below changes the drawn pose in place
        now = FirstPersonWandRig.sway(now);
        // Crucio: the hand shakes from the release on and while the curse is held.
        float shake = 0.0F;
        if (casting && motion.tremor() > 0.0F && time >= motion.release()) {
            shake = motion.tremor() * Mth.clamp((motion.length() - time) / 4.0F, 0.0F, 1.0F);
        }
        if (hold > 0.0F) {
            FirstPersonWandRig.Motion held = casting ? motion : FirstPersonWandRig.motionOf(
                    FirstPersonCast.heldSpell(), FirstPersonCast.forSpell(FirstPersonCast.heldSpell()));
            shake = Math.max(shake, held.tremor() * hold);
        }
        if (shake > 0.0F) {
            float age = player.tickCount + partialTick;
            float dx = 0.5F * (Mth.sin(age * 7.3F) + Mth.sin(age * 12.9F + 1.3F));
            float dy = 0.5F * (Mth.sin(age * 8.7F + 2.1F) + Mth.sin(age * 15.1F));
            now.hand().add(dx * 0.007F * shake, dy * 0.007F * shake, 0.0F);
            now.wand().add(dy * 0.035F * shake, dx * 0.035F * shake, 0.0F);
        }
        FirstPersonWandRig.keepWandInView(now.wand());
        now.hand().y -= event.getEquipProgress() * 0.7F; // lowered while the wand is being taken out

        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();
        int light = event.getPackedLight();

        pose.pushPose();
        float roll = casting ? motion.rollAt(time, cast.variant()) : 0.0F;
        Vector3f fist = FirstPersonWandRig.placeArm(pose, now, side, roll);
        if (right) {
            playerRenderer.renderRightHand(pose, buffer, light, player);
        } else {
            playerRenderer.renderLeftHand(pose, buffer, light, player);
        }
        pose.popPose();

        // The wand through the fist, pointing where the pose wants it (no hand item transform).
        pose.pushPose();
        FirstPersonWandRig.placeWand(pose, fist, new Vector3f(now.wand()).mul(side, 1.0F, 1.0F), GRIP);
        minecraft.gameRenderer.itemInHandRenderer.renderItem(player, stack, ItemDisplayContext.NONE, false, pose,
                buffer, light);
        Vector3f tip = pose.last().pose().transformPosition(new Vector3f(TIP));
        pose.popPose();
        LAST_TIP.set(tip);
        lastTipFrame = net.minecraft.Util.getNanos();

        float release = casting ? motion.release() : 0.0F;
        float age = player.tickCount + partialTick;
        if (casting) {
            swipeLife = motion.trailLife();
        }
        recordSwipe(casting && time <= motion.trailUntil(), tip, age);
        SpellType spell = cast != null && cast.spell() != null ? cast.spell() : MagicWandItem.getSelectedSpell(stack);
        drawSwipe(spell, buffer, age);
        if (casting && time >= release && cast.start() != flareCast) {
            flareCast = cast.start();
            FLARE_AT.set(tip);
        }
        // A sweep carries the wand on across the view: its flash stays where the spell left, as in the reference.
        Vector3f flareAt = casting && motion.anchorFlash() && flareCast == cast.start() ? FLARE_AT : tip;
        drawTipLight(player, spell, casting, time, release, tip, flareAt, buffer, age);
    }

    // ---------------------------------------------------------------- colour

    /** The spell's light colour: its beam's glow colour, lifted so it reads as light. */
    static float[] lightColor(@javax.annotation.Nullable SpellType spell) {
        if (spell == null) {
            return new float[]{0.9F, 0.9F, 1.0F};
        }
        BeamStyle style = BeamStyles.of(spell);
        float r = style.glowR;
        float g = style.glowG;
        float b = style.glowB;
        float max = Math.max(r, Math.max(g, b));
        if (max < 0.05F) {
            return new float[]{0.6F, 0.6F, 0.65F};
        }
        if (max < 0.7F) {
            float lift = 0.7F / max;
            r *= lift;
            g *= lift;
            b *= lift;
        }
        return new float[]{r, g, b};
    }

    private static float[] whiten(float[] c, float amount) {
        return new float[]{Mth.lerp(amount, c[0], 1.0F), Mth.lerp(amount, c[1], 1.0F), Mth.lerp(amount, c[2], 1.0F)};
    }

    // ---------------------------------------------------------------- the swipe streak

    /** How long a point of the streak lasts, in ticks (90 ms), on the gesture's own clock. */
    private static final float SWIPE_LIFE = 1.8F;
    /** The current gesture's streak life (a circle keeps a whole ring of light). */
    private static float swipeLife = SWIPE_LIFE;
    private static final int SWIPE_MAX = 48;
    private static final java.util.ArrayDeque<Vector3f> SWIPE = new java.util.ArrayDeque<>();
    private static final java.util.ArrayDeque<Float> SWIPE_TIME = new java.util.ArrayDeque<>();

    private static void recordSwipe(boolean stroking, Vector3f tip, float now) {
        // The clock is the player's tick count, which starts again after a respawn: points "from the future" go too.
        while (!SWIPE_TIME.isEmpty() && (now - SWIPE_TIME.peekFirst() > swipeLife || SWIPE_TIME.peekLast() > now)) {
            SWIPE_TIME.pollFirst();
            SWIPE.pollFirst();
        }
        // Only while the tip is on screen (and in front of the eye): off the edge the streak would be drawn as a
        // long straight line across the view.
        if (!stroking || tip.z > -0.05F || Math.abs(tip.x / -tip.z) > 0.85F || Math.abs(tip.y / -tip.z) > 0.55F) {
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

    private static void drawSwipe(@javax.annotation.Nullable SpellType spell, MultiBufferSource buffer, float now) {
        if (SWIPE.size() < 2) {
            return;
        }
        float[] color = lightColor(spell);
        float[] hot = whiten(color, 0.5F);
        Vector3f[] points = SWIPE.toArray(new Vector3f[0]);
        Float[] times = SWIPE_TIME.toArray(new Float[0]);
        Matrix4f matrix = new Matrix4f();
        VertexConsumer light = buffer.getBuffer(GLOW);
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
            float fa = 1.0F - Mth.clamp((now - times[i]) / swipeLife, 0.0F, 1.0F);
            float fb = 1.0F - Mth.clamp((now - times[i + 1]) / swipeLife, 0.0F, 1.0F);
            float wa = 0.004F + 0.012F * fa;
            float wb = 0.004F + 0.012F * fb;
            ribbonQuad(light, matrix, a, prevSide, wa * 3.0F, b, side, wb * 3.0F, color, 0.45F * fa, 0.45F * fb);
            ribbonQuad(light, matrix, a, prevSide, wa, b, side, wb, hot, fa, fb);
            prevSide.set(side);
        }
    }

    private static void ribbonQuad(VertexConsumer consumer, Matrix4f matrix, Vector3f a, Vector3f sideA, float wa,
                                   Vector3f b, Vector3f sideB, float wb, float[] c, float alphaA, float alphaB) {
        vertex(consumer, matrix, a.x + sideA.x * wa, a.y + sideA.y * wa, a.z + sideA.z * wa, 0.0F, 0.5F, c, alphaA);
        vertex(consumer, matrix, a.x - sideA.x * wa, a.y - sideA.y * wa, a.z - sideA.z * wa, 1.0F, 0.5F, c, alphaA);
        vertex(consumer, matrix, b.x - sideB.x * wb, b.y - sideB.y * wb, b.z - sideB.z * wb, 1.0F, 0.5F, c, alphaB);
        vertex(consumer, matrix, b.x + sideB.x * wb, b.y + sideB.y * wb, b.z + sideB.z * wb, 0.0F, 0.5F, c, alphaB);
    }

    // ---------------------------------------------------------------- the tip light

    /**
     * The tip: a faint breathing glow of the selected spell at rest, a sparkle on a change of spell, light
     * gathering through the wind-up, and a wide flare with arcs lashing out as the spell leaves.
     */
    private static void drawTipLight(LocalPlayer player, @javax.annotation.Nullable SpellType spell, boolean casting,
                                     float time, float release, Vector3f tip, Vector3f flareAt,
                                     MultiBufferSource buffer, float age) {
        float[] color = lightColor(spell);
        float[] hot = whiten(color, 0.6F);
        float flare = 0.0F;
        if (casting && time >= release) {
            float k = (time - release) / 4.0F;
            flare = k < 1.0F ? (1.0F - k) * (1.0F - k) : 0.0F;
        }
        float charge = casting && time < release ? time / Math.max(0.5F, release) : 0.0F;
        float held = Minecraft.getInstance().options.keyUse.isDown() ? 1.0F : 0.0F;
        float idle = 0.16F + 0.05F * Mth.sin(age * 0.12F);
        float sparkle = FirstPersonWandRig.switchGlow(age);
        float glow = Math.max(Math.max(Math.max(charge * 0.7F, held * 0.8F), flare), Math.max(idle, sparkle * 0.9F));
        Matrix4f matrix = new Matrix4f().translate(tip);
        VertexConsumer light = buffer.getBuffer(GLOW);
        glowQuad(light, matrix, 0.05F + 0.07F * glow, new float[]{1.0F, 1.0F, 1.0F}, glow);
        glowQuad(light, matrix, 0.12F + 0.12F * glow, hot, 0.75F * glow);
        if (flare > 0.0F) {
            matrix = new Matrix4f().translate(flareAt);
            // A hot white core in a halo about a hand across, as in the reference: not a wash over the view.
            glowQuad(light, matrix, 0.18F + 0.32F * flare, color, 0.8F * flare);
            glowQuad(light, matrix, 0.08F + 0.16F * flare, hot, flare);
            glowQuad(light, matrix, 0.04F + 0.07F * flare, new float[]{1.0F, 1.0F, 1.0F}, flare);
            VertexConsumer bolts = buffer.getBuffer(RenderType.lightning());
            long seed = player.tickCount * 13L;
            for (int i = 0; i < 7; i++) {
                crackle(bolts, matrix, seed + i * 101L, 0.3F + 0.4F * flare, 0.006F, hot, flare);
            }
        }
    }

    /** One camera-facing glow quad (view space, so the plane faces the eye). */
    private static void glowQuad(VertexConsumer consumer, Matrix4f matrix, float half, float[] c, float a) {
        vertex(consumer, matrix, -half, -half, 0.0F, 0.0F, 1.0F, c, a);
        vertex(consumer, matrix, half, -half, 0.0F, 1.0F, 1.0F, c, a);
        vertex(consumer, matrix, half, half, 0.0F, 1.0F, 0.0F, c, a);
        vertex(consumer, matrix, -half, half, 0.0F, 0.0F, 0.0F, c, a);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float u, float v,
                               float[] c, float a) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(c[0], c[1], c[2], Mth.clamp(a, 0.0F, 1.0F))
                .setUv(u, v)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 0.0F, 1.0F);
    }

    /** A small jagged arc of lightning from the origin, in the plane of the screen. */
    private static void crackle(VertexConsumer consumer, Matrix4f matrix, long seed, float length, float width,
                                float[] c, float a) {
        RandomSource random = RandomSource.create(seed);
        float angle = random.nextFloat() * Mth.TWO_PI;
        float x = 0.0F;
        float y = 0.0F;
        int segments = 5;
        float step = length / segments;
        for (int i = 0; i < segments; i++) {
            angle += (random.nextFloat() - 0.5F) * 1.6F;
            float nx = x + Mth.cos(angle) * step;
            float ny = y + Mth.sin(angle) * step;
            float w = width * (1.0F - i / (float) segments * 0.7F);
            float dx = nx - x;
            float dy = ny - y;
            float len = Mth.sqrt(dx * dx + dy * dy);
            float px = -dy / len * w;
            float py = dx / len * w;
            consumer.addVertex(matrix, x - px, y - py, 0.0F).setColor(c[0], c[1], c[2], a);
            consumer.addVertex(matrix, nx - px, ny - py, 0.0F).setColor(c[0], c[1], c[2], a);
            consumer.addVertex(matrix, nx + px, ny + py, 0.0F).setColor(c[0], c[1], c[2], a);
            consumer.addVertex(matrix, x + px, y + py, 0.0F).setColor(c[0], c[1], c[2], a);
            x = nx;
            y = ny;
        }
    }
}
