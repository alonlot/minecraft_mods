package com.hogwartsmagic.client;

import com.hogwartsmagic.item.MagicWandItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Where a caster's wand tip is really drawn, as seen from outside. While a caster's (player's or NPC's) gesture
 * plays (CastGestures), this layer finds the tip the way the held item is placed (ItemInHandLayer, then the wand's
 * own display transform) and keeps it, so the spell leaves from the wand you see (SpellBeamClientState, SpellPath)
 * and the wand trail follows it (WandTrailRenderer draws the trail, in the spell's beam colours). Draws nothing.
 */
public class WandSwipeLayer<T extends LivingEntity, M extends EntityModel<T> & ArmedModel> extends RenderLayer<T, M> {

    /** Wand tip in the centred item model space, the same point the first-person arm uses. */
    private static final Vector3f TIP = new Vector3f(0.40F, 0.40F, 0.0F);

    private record Tip(Vec3 at, long nanos) {
    }

    /** The latest drawn wand tip of each caster mid-gesture. */
    private static final Map<Integer, Tip> LATEST = new HashMap<>();

    /** Where this caster's wand tip was drawn in the last few frames, or null. */
    @javax.annotation.Nullable
    static Vec3 recentTip(LivingEntity caster) {
        Tip tip = LATEST.get(caster.getId());
        return tip != null && net.minecraft.Util.getNanos() - tip.nanos() < 150_000_000L ? tip.at() : null;
    }

    public WandSwipeLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight, T caster,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        ItemStack stack = caster.getMainHandItem();
        if (!MagicWandItem.isAnyWand(stack) || caster.isInvisible() || !CastGestures.ownsArm(caster)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 tip = tipInWorld(minecraft, pose, caster, stack, camera);
        // The same layer also draws the player in inventory screens; those points are nowhere near the
        // player in the world and are dropped.
        if (tip != null && tip.distanceToSqr(caster.getPosition(partialTick)) < 9.0) {
            LATEST.put(caster.getId(), new Tip(tip, net.minecraft.Util.getNanos()));
        }
    }

    /** The wand tip of this caster in the world, from the pose the layer is rendered with (camera-relative). */
    @javax.annotation.Nullable
    private Vec3 tipInWorld(Minecraft minecraft, PoseStack pose, T caster, ItemStack stack, Vec3 camera) {
        HumanoidArm arm = caster.getMainArm();
        boolean left = arm == HumanoidArm.LEFT;
        pose.pushPose();
        this.getParentModel().translateToHand(arm, pose);
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.translate((left ? -1.0F : 1.0F) / 16.0F, 0.125F, -0.625F);
        BakedModel model = minecraft.getItemRenderer().getModel(stack, caster.level(), caster, 0);
        model.applyTransform(left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                pose, left);
        Vector3f p = pose.last().pose().transformPosition(new Vector3f(TIP));
        pose.popPose();
        if (!Float.isFinite(p.x) || !Float.isFinite(p.y) || !Float.isFinite(p.z)) {
            return null;
        }
        return camera.add(p.x, p.y, p.z);
    }

    /** Drops the tips of casters who left; called from a client tick. */
    static void prune(java.util.function.IntPredicate stillHere) {
        LATEST.keySet().removeIf(id -> !stillHere.test(id));
    }
}
