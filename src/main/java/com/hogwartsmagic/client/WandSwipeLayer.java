package com.hogwartsmagic.client;

import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.spell.SpellType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The wand swipe streak as seen from outside: while a caster's (player's or NPC's) gesture cuts through its stroke, the tip
 * of the wand in their hand leaves a thin fading streak of the spell's light in the world. This is what other
 * players (and you, in third person) see; the first-person streak is drawn by WandCastAnimation. Visual only.
 *
 * The tip is found the way the held item is placed (ItemInHandLayer, then the wand's own display transform),
 * kept as world points for a short time, and drawn as a camera-facing band through them.
 */
public class WandSwipeLayer<T extends LivingEntity, M extends EntityModel<T> & ArmedModel> extends RenderLayer<T, M> {

    /** Wand tip in the centred item model space, the same point the first-person streak uses. */
    private static final Vector3f TIP = new Vector3f(0.40F, 0.38F, 0.0F);
    /** How long a point of the streak lasts, in ticks (280 ms) of the caster's age, the gesture's own clock. */
    private static final float LIFE = 5.6F;
    private static final int MAX_POINTS = 48;

    private record Point(Vec3 at, float time) {
    }

    private record Tip(Vec3 at, long nanos) {
    }

    /** The latest wand tip of each caster mid-stroke, for the orb held on the tip (SpellPath). */
    private static final Map<Integer, Tip> LATEST = new HashMap<>();

    /** Where this caster's wand tip was drawn in the last few frames, or null. */
    @javax.annotation.Nullable
    static Vec3 recentTip(LivingEntity caster) {
        Tip tip = LATEST.get(caster.getId());
        return tip != null && net.minecraft.Util.getNanos() - tip.nanos() < 150_000_000L ? tip.at() : null;
    }

    /** Recent tip points of each player, oldest first. */
    private static final Map<Integer, ArrayDeque<Point>> TRAILS = new HashMap<>();

    public WandSwipeLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight, T player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        ItemStack stack = player.getMainHandItem();
        if (!MagicWandItem.isAnyWand(stack) || player.isInvisible()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        float now = ageInTicks;
        ArrayDeque<Point> trail = TRAILS.computeIfAbsent(player.getId(), id -> new ArrayDeque<>());
        // Age starts again when an entity is re-created under the same id: points "from the future" go too.
        while (!trail.isEmpty() && (now - trail.peekFirst().time() > LIFE || trail.peekLast().time() > now)) {
            trail.pollFirst();
        }

        CastGestures.Cast cast = CastGestures.castOf(player);
        float t = cast == null ? -1.0F : cast.progress(ageInTicks);
        if (t >= 0.0F && t <= cast.gesture().release + 0.25F) {
            Vec3 tip = tipInWorld(minecraft, pose, player, stack, camera);
            // The same layer also draws the player in inventory screens; those points are nowhere near the
            // player in the world and are dropped.
            if (tip != null && tip.distanceToSqr(player.getPosition(partialTick)) < 9.0) {
                LATEST.put(player.getId(), new Tip(tip, net.minecraft.Util.getNanos()));
                Point last = trail.peekLast();
                if (last == null || last.at().distanceToSqr(tip) > 1.0E-6) {
                    trail.addLast(new Point(tip, now));
                    while (trail.size() > MAX_POINTS) {
                        trail.pollFirst();
                    }
                }
            }
        }
        if (trail.size() < 2) {
            return;
        }
        SpellType spell = cast != null && cast.spell() != null ? cast.spell() : MagicWandItem.getSelectedSpell(stack);
        float[] color = spell != null ? SpellFx.lightColor(spell) : new float[]{1.0F, 1.0F, 1.0F};
        drawTrail(pose, buffer, trail, camera, now, color);
    }

    /** The wand tip of this player in the world, from the pose the layer is rendered with (camera-relative). */
    @javax.annotation.Nullable
    private Vec3 tipInWorld(Minecraft minecraft, PoseStack pose, T player, ItemStack stack, Vec3 camera) {
        HumanoidArm arm = player.getMainArm();
        boolean left = arm == HumanoidArm.LEFT;
        pose.pushPose();
        this.getParentModel().translateToHand(arm, pose);
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.translate((left ? -1.0F : 1.0F) / 16.0F, 0.125F, -0.625F);
        BakedModel model = minecraft.getItemRenderer().getModel(stack, player.level(), player, 0);
        model.applyTransform(left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                pose, left);
        Vector3f p = pose.last().pose().transformPosition(new Vector3f(TIP));
        pose.popPose();
        if (!Float.isFinite(p.x) || !Float.isFinite(p.y) || !Float.isFinite(p.z)) {
            return null;
        }
        return camera.add(p.x, p.y, p.z);
    }

    /**
     * A tapered band through the trail. Vertices are worked out camera-relative (where the camera is the
     * origin, so the line of sight to a point is the point itself), then taken into the layer's own space with
     * the inverse of its matrix, so the layer's matrix puts them back where they belong.
     */
    private static void drawTrail(PoseStack pose, MultiBufferSource buffer, ArrayDeque<Point> trail, Vec3 camera,
                                  float now, float[] color) {
        Matrix4f matrix = pose.last().pose();
        Matrix4f inverse = new Matrix4f(matrix);
        if (Math.abs(inverse.determinant()) < 1.0E-12F) {
            return;
        }
        inverse.invert();
        float[] hot = SpellFx.whiten(color, 0.5F);
        VertexConsumer light = buffer.getBuffer(ModRenderTypes.SPELL_GLOW);
        Point[] points = trail.toArray(new Point[0]);
        Vector3f prevSide = null;
        for (int i = 0; i < points.length - 1; i++) {
            Vector3f a = rel(points[i].at(), camera);
            Vector3f b = rel(points[i + 1].at(), camera);
            Vector3f side = new Vector3f(b).sub(a).cross(b);
            if (side.lengthSquared() < 1.0E-12F) {
                continue;
            }
            side.normalize();
            if (prevSide == null) {
                prevSide = new Vector3f(side);
            }
            float fa = 1.0F - Mth.clamp((now - points[i].time()) / LIFE, 0.0F, 1.0F);
            float fb = 1.0F - Mth.clamp((now - points[i + 1].time()) / LIFE, 0.0F, 1.0F);
            float wa = 0.018F + 0.04F * fa;
            float wb = 0.018F + 0.04F * fb;
            band(light, matrix, inverse, a, prevSide, wa * 2.6F, b, side, wb * 2.6F, color, 0.45F * fa, 0.45F * fb);
            band(light, matrix, inverse, a, prevSide, wa, b, side, wb, hot, fa, fb);
            prevSide.set(side);
        }
    }

    private static Vector3f rel(Vec3 world, Vec3 camera) {
        return new Vector3f((float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z));
    }

    private static void band(VertexConsumer consumer, Matrix4f matrix, Matrix4f inverse, Vector3f a, Vector3f sideA,
                             float wa, Vector3f b, Vector3f sideB, float wb, float[] c, float alphaA, float alphaB) {
        corner(consumer, matrix, inverse, new Vector3f(sideA).mul(wa).add(a), 0.0F, c, alphaA);
        corner(consumer, matrix, inverse, new Vector3f(sideA).mul(-wa).add(a), 1.0F, c, alphaA);
        corner(consumer, matrix, inverse, new Vector3f(sideB).mul(-wb).add(b), 1.0F, c, alphaB);
        corner(consumer, matrix, inverse, new Vector3f(sideB).mul(wb).add(b), 0.0F, c, alphaB);
    }

    private static void corner(VertexConsumer consumer, Matrix4f matrix, Matrix4f inverse, Vector3f cameraRelative,
                               float u, float[] c, float alpha) {
        Vector3f local = inverse.transformPosition(cameraRelative);
        SpellFx.vertex(consumer, matrix, local.x, local.y, local.z, u, 0.5F, c[0], c[1], c[2], Mth.clamp(alpha, 0.0F, 1.0F));
    }

    /** Drops the trails of players who left; called from a client tick. */
    static void prune(java.util.function.IntPredicate stillHere) {
        for (Iterator<Integer> it = TRAILS.keySet().iterator(); it.hasNext(); ) {
            if (!stillHere.test(it.next())) {
                it.remove();
            }
        }
        LATEST.keySet().removeIf(id -> !stillHere.test(id));
    }
}
