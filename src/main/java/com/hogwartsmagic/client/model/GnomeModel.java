package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.GnomeEntity;
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
 * The garden gnome. The bone tree and its texture come from {@code tools/gnome/build_gnome.py} via
 * {@link GnomeGeometry}; this class only moves the bones, adding to the rest pose each frame.
 *
 * <ul>
 *   <li><b>Walking</b>: a quick waddle, body rolling, arms swinging against the legs, the ears twitching.</li>
 *   <li><b>Pulling a crop</b>: leaning back, both arms out in front, jerking. <b>Eating</b>: both hands to the
 *       mouth, the jaw chomping. <b>Digging</b>: bent double, arms scrabbling.</li>
 *   <li><b>Carried</b> (drawn upside down by the renderer): one leg straight, the other kicking, arms flailing,
 *       head shaking. <b>Whirled</b>: limbs splayed. <b>Flung</b>: limbs flailing. <b>Dazed</b>: sitting, legs out
 *       in front, head lolling in a slow circle.</li>
 *   <li><b>Leaving for good</b>: running flat out with both arms up.</li>
 * </ul>
 *
 * No {@code switch} on the state: the synthetic switch-map class has failed to load in this mod's dev
 * environment, so every branch is an {@code if}.
 */
public class GnomeModel extends EntityModel<GnomeEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "gnome"), "main");

    private static final float PI = (float) Math.PI;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart[] ear = new ModelPart[2];
    private final ModelPart[] arm = new ModelPart[2];
    private final ModelPart[] leg = new ModelPart[2];

    private GnomeEntity.State state = GnomeEntity.State.NONE;
    private float stateTime;
    private float spinTicks;
    private boolean leaving;

    public GnomeModel(ModelPart model) {
        Map<String, ModelPart> b = GnomeGeometry.bones(model);
        root = b.get("root");
        body = b.get("body");
        head = b.get("head");
        jaw = b.get("jaw");
        String[] sides = {"left", "right"};
        for (int i = 0; i < 2; i++) {
            ear[i] = b.get("ear_" + sides[i]);
            arm[i] = b.get("arm_" + sides[i]);
            leg[i] = b.get("leg_" + sides[i]);
        }
    }

    public static LayerDefinition createBodyLayer() {
        return GnomeGeometry.createBodyLayer();
    }

    @Override
    public void prepareMobModel(GnomeEntity entity, float limbSwing, float limbSwingAmount, float partialTick) {
        pose(entity.state(), entity.stateTime(partialTick), entity.clientSpinTicks(partialTick), entity.isLeaving());
    }

    /** Sets the inputs the animation reads; a preview harness can call this directly. */
    public void pose(GnomeEntity.State s, float stateTime, float spinTicks, boolean leaving) {
        this.state = s;
        this.stateTime = stateTime;
        this.spinTicks = spinTicks;
        this.leaving = leaving;
    }

    @Override
    public void setupAnim(GnomeEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float t = stateTime;
        boolean none = state == GnomeEntity.State.NONE;
        // Ears never quite stop: a twitch each, out of step.
        ear[0].zRot += Mth.sin(ageInTicks * 0.31F) * 0.06F + twitch(ageInTicks, 0.0F) * 0.25F;
        ear[1].zRot -= Mth.sin(ageInTicks * 0.27F + 1.0F) * 0.06F + twitch(ageInTicks, 2.1F) * 0.25F;

        if (none && leaving) {
            running(ageInTicks, limbSwing);
        } else if (none) {
            waddle(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        } else if (state == GnomeEntity.State.PULL) {
            pull(t);
        } else if (state == GnomeEntity.State.EAT) {
            eat(t);
        } else if (state == GnomeEntity.State.DIG) {
            dig(t);
        } else if (state == GnomeEntity.State.CARRIED) {
            carried(t);
        } else if (state == GnomeEntity.State.SPUN) {
            spun(spinTicks);
        } else if (state == GnomeEntity.State.THROWN) {
            thrown(t);
        } else if (state == GnomeEntity.State.DAZED) {
            dazed(t);
        }
    }

    /** A brief flick: mostly zero, now and then a quick positive pulse. */
    private static float twitch(float age, float phase) {
        float s = Mth.sin(age * 0.09F + phase);
        s = s > 0.85F ? (s - 0.85F) / 0.15F : 0.0F;
        return s * Mth.sin(age * 1.6F);
    }

    private void waddle(float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        float amt = Math.min(1.0F, limbSwingAmount * 1.8F);
        float p = limbSwing * 1.5F;
        float[] ph = {p, p + PI};
        for (int i = 0; i < 2; i++) {
            leg[i].xRot += Mth.sin(ph[i]) * 1.0F * amt;
            arm[i].xRot += Mth.sin(ph[1 - i]) * 0.9F * amt;
            arm[i].zRot += (i == 0 ? -1.0F : 1.0F) * 0.12F * amt;
        }
        body.zRot += Mth.sin(p) * 0.15F * amt;
        body.y -= Math.abs(Mth.sin(p)) * 0.7F * amt;
        head.zRot -= Mth.sin(p) * 0.08F * amt;
        head.yRot += Mth.clamp(netHeadYaw, -50.0F, 50.0F) * Mth.DEG_TO_RAD * 0.8F;
        head.xRot += Mth.clamp(headPitch, -30.0F, 30.0F) * Mth.DEG_TO_RAD * 0.8F;
        // Idle: it breathes, rocks, and now and then giggles with its jaw.
        float idle = 1.0F - amt;
        body.y += Mth.sin(age * 0.12F) * 0.15F * idle;
        float giggle = Mth.sin(age * 0.05F);
        giggle = giggle > 0.9F ? (giggle - 0.9F) / 0.1F : 0.0F;
        jaw.xRot += giggle * (0.25F + 0.25F * Mth.sin(age * 1.9F)) * idle;
        head.zRot += giggle * Mth.sin(age * 1.9F) * 0.08F;
    }

    /** Away at a flat-out run, both arms in the air, mouth open. */
    private void running(float age, float limbSwing) {
        float p = limbSwing * 1.9F + age * 0.1F;
        for (int i = 0; i < 2; i++) {
            float off = i == 0 ? 0.0F : PI;
            leg[i].xRot += Mth.sin(p + off) * 1.2F;
            arm[i].xRot += -2.9F + Mth.sin(p * 1.3F + off) * 0.35F;
            arm[i].zRot += (i == 0 ? -1.0F : 1.0F) * (0.2F + Mth.sin(p + off) * 0.15F);
        }
        body.xRot += 0.18F;
        body.y -= Math.abs(Mth.sin(p)) * 0.9F;
        body.zRot += Mth.sin(p) * 0.1F;
        head.xRot -= 0.2F;
        jaw.xRot += 0.5F + Mth.sin(p * 2.0F) * 0.1F;
    }

    /** Leaning back, hauling a crop out of the ground. */
    private void pull(float t) {
        float in = Math.min(1.0F, t / 4.0F);
        float jerk = Mth.sin(t * 1.8F);
        body.xRot += (-0.4F + jerk * 0.1F) * in;
        body.y += 0.4F * in;
        head.xRot += (0.3F + jerk * 0.05F) * in;
        for (int i = 0; i < 2; i++) {
            arm[i].xRot += (-1.4F + jerk * 0.4F) * in;
            arm[i].zRot += (i == 0 ? 1.0F : -1.0F) * 0.12F * in;
            leg[i].xRot += (i == 0 ? 0.35F : -0.35F) * in;
        }
        jaw.xRot += 0.15F * in;
    }

    /** Both hands up to the mouth, chomping. */
    private void eat(float t) {
        float in = Math.min(1.0F, t / 4.0F);
        float chomp = Math.max(0.0F, Mth.sin(t * 1.5F));
        for (int i = 0; i < 2; i++) {
            arm[i].xRot += -1.9F * in;
            arm[i].zRot += (i == 0 ? -1.0F : 1.0F) * 0.3F * in;
        }
        head.xRot += 0.12F * in;
        jaw.xRot += chomp * 0.45F * in;
        body.y += Mth.sin(t * 1.5F + 1.0F) * 0.2F * in;
        head.yRot += Mth.sin(t * 0.4F) * 0.1F;
    }

    /** Bent double, arms scrabbling at the turf. */
    private void dig(float t) {
        float in = Math.min(1.0F, t / 5.0F);
        body.xRot += 0.9F * in;
        body.y += 1.0F * in;
        head.xRot += -0.55F * in;
        for (int i = 0; i < 2; i++) {
            float off = i == 0 ? 0.0F : PI;
            arm[i].xRot += (-1.0F + Mth.sin(t * 1.7F + off) * 0.8F) * in;
            leg[i].xRot += -0.2F * in;
        }
        body.zRot += Mth.sin(t * 0.85F) * 0.08F * in;
        jaw.xRot += 0.1F * in;
    }

    /** Hanging by one ankle: one leg straight (the held one), the other kicking, arms flailing, wailing. */
    private void carried(float t) {
        leg[0].zRot += 0.05F;
        leg[1].xRot += Mth.sin(t * 0.9F) * 0.8F;
        leg[1].zRot += 0.35F + Mth.sin(t * 0.7F + 1.0F) * 0.25F;
        for (int i = 0; i < 2; i++) {
            float off = i == 0 ? 0.0F : 2.1F;
            float s = i == 0 ? -1.0F : 1.0F;
            arm[i].xRot += Mth.sin(t * 1.3F + off) * 1.1F - 0.4F;
            arm[i].zRot += s * (0.9F + Mth.sin(t * 1.7F + off) * 0.5F);
        }
        head.yRot += Mth.sin(t * 1.9F) * 0.5F;
        head.zRot += Mth.sin(t * 1.4F) * 0.15F;
        jaw.xRot += 0.35F + Mth.sin(t * 2.3F) * 0.2F;
        body.zRot += Mth.sin(t * 0.8F) * 0.1F;
        ear[0].zRot += 0.5F;
        ear[1].zRot -= 0.5F;
    }

    /** Whirled round: limbs flung out by the spin, ears streaming. */
    private void spun(float spin) {
        float f = Mth.clamp(spin / 30.0F, 0.0F, 1.0F);
        for (int i = 0; i < 2; i++) {
            float s = i == 0 ? -1.0F : 1.0F;
            arm[i].zRot += s * (1.3F + 0.2F * f + Mth.sin(spin * 0.9F + i) * 0.12F);
            arm[i].xRot += -0.3F;
            leg[i].zRot += s * (0.4F + 0.3F * f);
            leg[i].xRot += Mth.sin(spin * 0.8F + i * 2.0F) * 0.15F;
        }
        head.xRot += -0.25F;
        jaw.xRot += 0.5F;
        ear[0].zRot += 0.9F * f;
        ear[1].zRot -= 0.9F * f;
        ear[0].yRot += 0.5F * f;
        ear[1].yRot -= 0.5F * f;
    }

    /** In the air: everything flailing at once. */
    private void thrown(float t) {
        for (int i = 0; i < 2; i++) {
            float off = i == 0 ? 0.0F : 1.7F;
            float s = i == 0 ? -1.0F : 1.0F;
            arm[i].xRot += Mth.sin(t * 0.9F + off) * 1.2F;
            arm[i].zRot += s * (0.8F + Mth.sin(t * 1.1F + off) * 0.5F);
            leg[i].xRot += Mth.sin(t * 1.0F + off + 1.0F) * 0.9F;
            leg[i].zRot += s * 0.4F;
        }
        head.yRot += Mth.sin(t * 1.3F) * 0.4F;
        jaw.xRot += 0.6F;
        ear[0].zRot += 0.8F;
        ear[1].zRot -= 0.8F;
    }

    /** Sitting in a heap, legs stuck out in front, head lolling round and round. */
    private void dazed(float t) {
        float in = Math.min(1.0F, t / 4.0F);
        body.y += 2.0F * in;
        body.xRot += -0.15F * in;
        for (int i = 0; i < 2; i++) {
            float s = i == 0 ? -1.0F : 1.0F;
            leg[i].xRot += -1.5F * in;
            leg[i].zRot += s * 0.2F * in;
            arm[i].xRot += -0.2F * in;
            arm[i].zRot += s * 0.25F * in;
        }
        float a = t * 0.13F;
        head.yRot += Mth.sin(a) * 0.45F;
        head.xRot += (0.3F + Mth.cos(a) * 0.3F) * in;
        head.zRot += Mth.cos(a) * 0.2F;
        jaw.xRot += 0.2F + Mth.sin(a * 2.0F) * 0.1F;
        ear[0].zRot += 0.6F * in;
        ear[1].zRot -= 0.6F * in;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
