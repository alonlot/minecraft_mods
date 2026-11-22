package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.YetiEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.Map;

/**
 * The Yeti. The bone tree and its texture come from {@code tools/yeti/build_yeti.py} via
 * {@link YetiGeometry}; this class only moves the bones, adding to the rest pose each frame.
 *
 * <ul>
 *   <li><b>Walking</b>: a heavy, rolling ape's gait, the long arms swinging against the legs.</li>
 *   <li><b>Swipe</b>: the right arm comes back over the shoulder and down across.</li>
 *   <li><b>Roar</b>: it rears, throws its arms wide, the head goes back and the jaw drops.</li>
 *   <li><b>Slam</b>: both fists up over the head, then down into the ground.</li>
 *   <li><b>Throw</b>: it scoops the ground, lifts the boulder over its head and hurls it.</li>
 *   <li><b>Cower</b>: crouched, arms over its head, away from the fire.</li>
 *   <li><b>Asleep</b>: curled on its side, breathing slowly.</li>
 * </ul>
 */
public class YetiModel extends EntityModel<YetiEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "yeti"), "main");

    private static final float PI = (float) Math.PI;

    private final ModelPart root;
    private final ModelPart hips;
    private final ModelPart torso;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart boulder;
    private final ModelPart[] shoulder = new ModelPart[2];
    private final ModelPart[] elbow = new ModelPart[2];
    private final ModelPart[] hand = new ModelPart[2];
    private final ModelPart[] thigh = new ModelPart[2];
    private final ModelPart[] shin = new ModelPart[2];
    private final ModelPart[] foot = new ModelPart[2];

    private YetiEntity.Action action = YetiEntity.Action.NONE;
    private float actionTime;
    private float sleep;

    public YetiModel(ModelPart model) {
        Map<String, ModelPart> b = YetiGeometry.bones(model);
        root = b.get("root");
        hips = b.get("hips");
        torso = b.get("torso");
        head = b.get("head");
        jaw = b.get("jaw");
        boulder = b.get("boulder");
        String[] sides = {"left", "right"};
        for (int i = 0; i < 2; i++) {
            shoulder[i] = b.get("shoulder_" + sides[i]);
            elbow[i] = b.get("elbow_" + sides[i]);
            hand[i] = b.get("hand_" + sides[i]);
            thigh[i] = b.get("thigh_" + sides[i]);
            shin[i] = b.get("shin_" + sides[i]);
            foot[i] = b.get("foot_" + sides[i]);
        }
    }

    public static LayerDefinition createBodyLayer() {
        return YetiGeometry.createBodyLayer();
    }

    @Override
    public void prepareMobModel(YetiEntity entity, float limbSwing, float limbSwingAmount, float partialTick) {
        pose(entity.action(), entity.actionTime(partialTick), entity.sleepAmount(partialTick));
    }

    /** Sets the inputs the animation reads; the preview harness calls this directly. */
    public void pose(YetiEntity.Action action, float actionTime, float sleep) {
        this.action = action;
        this.actionTime = actionTime;
        this.sleep = sleep;
    }

    private static float ease(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    @Override
    public void setupAnim(YetiEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        boulder.visible = false;
        float t = actionTime;
        float awake = 1.0F - sleep;
        boolean busy = action != YetiEntity.Action.NONE;

        // --- breathing ----------------------------------------------------------------------------------
        float breath = Mth.sin(ageInTicks * (0.08F - 0.04F * sleep));
        torso.xRot += breath * 0.02F;
        for (int i = 0; i < 2; i++) {
            shoulder[i].zRot += (i == 0 ? -1 : 1) * breath * 0.02F;
        }

        // --- the walk: heavy and rolling ---------------------------------------------------------------------
        float amt = busy ? 0.3F : 1.0F;
        amt *= Math.min(1.0F, limbSwingAmount * 1.4F) * awake;
        float p = limbSwing * 0.45F;
        for (int i = 0; i < 2; i++) {
            float ph = i == 0 ? p : p + PI;
            thigh[i].xRot += Mth.sin(ph) * 0.55F * amt;
            shin[i].xRot += Math.max(0.0F, Mth.sin(ph + 1.4F)) * 0.6F * amt;
            foot[i].xRot -= Math.max(0.0F, Mth.sin(ph + 1.4F)) * 0.3F * amt;
            // Arms swing against the legs on the same side, hanging and heavy.
            shoulder[i].xRot += Mth.sin(ph + PI) * 0.45F * amt;
            elbow[i].xRot -= Math.max(0.0F, Mth.sin(ph + PI + 0.6F)) * 0.35F * amt;
        }
        hips.y -= Math.abs(Mth.sin(p)) * 1.2F * amt;
        hips.zRot += Mth.sin(p) * 0.06F * amt;
        torso.zRot -= Mth.sin(p) * 0.05F * amt;
        torso.yRot += Mth.sin(p) * 0.08F * amt;

        // --- head --------------------------------------------------------------------------------------------
        head.yRot += Mth.clamp(netHeadYaw, -45.0F, 45.0F) * Mth.DEG_TO_RAD * awake;
        head.xRot += Mth.clamp(headPitch, -25.0F, 25.0F) * Mth.DEG_TO_RAD * awake;

        // --- the swipe -----------------------------------------------------------------------------------------
        if (attackTime > 0.0F && !busy) {
            float a = attackTime;
            float back = ease(a / 0.35F) * (1.0F - ease((a - 0.35F) / 0.3F));
            float across = ease((a - 0.35F) / 0.3F) * (1.0F - ease((a - 0.75F) / 0.25F));
            shoulder[1].xRot += -2.4F * back - 0.9F * across;
            shoulder[1].zRot += 0.5F * back - 0.9F * across;
            elbow[1].xRot += -0.6F * back;
            torso.yRot += 0.3F * back - 0.45F * across;
            torso.xRot += -0.1F * back + 0.15F * across;
        }

        switch (action) {
            case ROAR -> roar(t);
            case SLAM -> slam(t);
            case THROW -> throwing(t);
            case COWER -> cower(t);
            default -> {
            }
        }

        if (sleep > 0.0F) {
            asleep(sleep, ageInTicks);
        }
    }

    private void roar(float t) {
        float total = YetiEntity.Action.ROAR.ticks;
        float up = ease(t / 8.0F) * (1.0F - ease((t - total + 8.0F) / 8.0F));
        float shake = t > YetiEntity.ROAR_AT && t < total - 8 ? Mth.sin(t * 2.2F) * 0.08F : 0.0F;
        torso.xRot += -0.45F * up;
        hips.y += 0.0F;
        for (int i = 0; i < 2; i++) {
            float s = i == 0 ? -1.0F : 1.0F;
            // Mostly sideways: the arm turns forward first and sideways second (Z*Y*X), so a big
            // forward lift would leave the sideways turn spinning an arm that points ahead.
            shoulder[i].xRot += -0.25F * up;
            shoulder[i].zRot += -s * 1.35F * up;
            elbow[i].xRot += -0.5F * up;
            hand[i].xRot += -0.3F * up;
        }
        head.xRot += -0.45F * up;
        head.yRot += shake;
        jaw.xRot += 0.75F * up;
    }

    private void slam(float t) {
        float impact = YetiEntity.SLAM_IMPACT;
        float raise = ease(t / (impact - 6.0F));
        float down = ease((t - impact + 4.0F) / 4.0F);
        float recover = ease((t - impact - 6.0F) / 8.0F);
        float lift = raise * (1.0F - down);
        float hit = down * (1.0F - recover);
        torso.xRot += -0.3F * lift + 0.55F * hit;
        hips.y += 3.0F * hit;
        for (int i = 0; i < 2; i++) {
            float s = i == 0 ? -1.0F : 1.0F;
            shoulder[i].xRot += -2.9F * lift - 1.1F * hit;
            shoulder[i].zRot += -s * 0.35F * lift;
            elbow[i].xRot += -0.4F * lift;
            thigh[i].xRot += -0.45F * hit;
            shin[i].xRot += 0.7F * hit;
            foot[i].xRot += -0.25F * hit;
        }
        head.xRot += 0.2F * hit - 0.2F * lift;
        jaw.xRot += 0.5F * (lift + hit);
    }

    private void throwing(float t) {
        float release = YetiEntity.THROW_RELEASE;
        float scoop = ease(t / 6.0F) * (1.0F - ease((t - 8.0F) / 4.0F));
        float lift = ease((t - 8.0F) / 8.0F) * (1.0F - ease((t - release + 2.0F) / 3.0F));
        float hurl = ease((t - release + 2.0F) / 3.0F) * (1.0F - ease((t - release - 3.0F) / 5.0F));
        boulder.visible = t >= 7.0F && t < release;
        torso.xRot += 0.5F * scoop - 0.35F * lift + 0.45F * hurl;
        hips.y += 4.0F * scoop;
        for (int i = 0; i < 2; i++) {
            float s = i == 0 ? -1.0F : 1.0F;
            shoulder[i].xRot += -0.6F * scoop - 2.9F * lift - 1.2F * hurl;
            shoulder[i].zRot += -s * 0.25F * lift;
            elbow[i].xRot += -0.7F * lift;
            thigh[i].xRot += -0.5F * scoop;
            shin[i].xRot += 0.8F * scoop;
        }
        head.xRot += -0.25F * lift + 0.15F * hurl;
        jaw.xRot += 0.5F * hurl;
    }

    private void cower(float t) {
        float total = YetiEntity.Action.COWER.ticks;
        float c = ease(t / 6.0F) * (1.0F - ease((t - total + 6.0F) / 6.0F));
        float tremble = Mth.sin(t * 2.6F) * 0.04F * c;
        torso.xRot += 0.45F * c + tremble;
        hips.y += 7.0F * c;
        for (int i = 0; i < 2; i++) {
            float s = i == 0 ? -1.0F : 1.0F;
            shoulder[i].xRot += -2.3F * c;
            shoulder[i].zRot += s * 0.55F * c;
            elbow[i].xRot += -1.5F * c;
            thigh[i].xRot += -0.8F * c;
            shin[i].xRot += 1.3F * c;
            foot[i].xRot += -0.5F * c;
        }
        head.xRot += 0.5F * c;
        head.yRot += tremble * 3.0F;
    }

    /** Curled on its right side in the nest, knees drawn up, arms folded in. */
    private void asleep(float l, float age) {
        float e = ease(l);
        hips.zRot += 1.45F * e;
        hips.y += 14.5F * e;
        hips.x += -4.0F * e;
        torso.xRot += 0.35F * e;
        head.xRot += 0.45F * e;
        head.zRot += -0.2F * e;
        jaw.xRot = 0.0F;
        for (int i = 0; i < 2; i++) {
            shoulder[i].xRot += -1.1F * e;
            elbow[i].xRot += -1.4F * e;
            thigh[i].xRot += -1.3F * e;
            shin[i].xRot += 1.7F * e;
            foot[i].xRot += -0.4F * e;
        }
        torso.xRot += Mth.sin(age * 0.04F) * 0.02F * e;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
