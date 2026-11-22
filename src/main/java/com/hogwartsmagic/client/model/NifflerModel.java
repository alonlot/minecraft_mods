package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.NifflerEntity;
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
 * The Niffler. The bone tree and its texture come from {@code tools/niffler/build_niffler.py} via
 * {@link NifflerGeometry}; this class only moves the bones, adding to the rest pose each frame.
 *
 * <ul>
 *   <li><b>Walking</b>: a quick scurry in diagonal pairs, the body bobbing and rolling, the tail
 *       swinging. A full pouch makes it waddle.</li>
 *   <li><b>Idle</b>: the bill never stops; it twitches, and every few seconds it sniffs hard.</li>
 *   <li><b>Sitting</b> (told to): up on its haunches like a meerkat, fore paws held at the chest.</li>
 *   <li><b>Sniff, dig, burrow</b>: nose down, the fore paws scrabbling; burrowing sinks it into the
 *       ground nose first.</li>
 *   <li><b>Tickled</b>: it rolls on its back and kicks while its pouch empties.</li>
 *   <li>The pouch swells with what is in it.</li>
 * </ul>
 */
public class NifflerModel extends EntityModel<NifflerEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "niffler"), "main");

    private static final float PI = (float) Math.PI;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart pouch;
    private final ModelPart head;
    private final ModelPart snout;
    private final ModelPart tail;
    private final ModelPart[] arm = new ModelPart[2];
    private final ModelPart[] paw = new ModelPart[2];
    private final ModelPart[] leg = new ModelPart[2];
    private final ModelPart[] foot = new ModelPart[2];

    private NifflerEntity.Action action = NifflerEntity.Action.NONE;
    private float actionTime;
    private float fill;
    private boolean sitting;

    public NifflerModel(ModelPart model) {
        Map<String, ModelPart> b = NifflerGeometry.bones(model);
        root = b.get("root");
        body = b.get("body");
        pouch = b.get("pouch");
        head = b.get("head");
        snout = b.get("snout");
        tail = b.get("tail");
        String[] sides = {"left", "right"};
        for (int i = 0; i < 2; i++) {
            arm[i] = b.get("arm_" + sides[i]);
            paw[i] = b.get("paw_" + sides[i]);
            leg[i] = b.get("leg_" + sides[i]);
            foot[i] = b.get("foot_" + sides[i]);
        }
    }

    public static LayerDefinition createBodyLayer() {
        return NifflerGeometry.createBodyLayer();
    }

    @Override
    public void prepareMobModel(NifflerEntity entity, float limbSwing, float limbSwingAmount, float partialTick) {
        pose(entity.action(), entity.actionTime(partialTick), entity.pouchFill(), entity.isInSittingPose());
    }

    /** Sets the inputs the animation reads; the preview harness calls this directly. */
    public void pose(NifflerEntity.Action action, float actionTime, float fill, boolean sitting) {
        this.action = action;
        this.actionTime = actionTime;
        this.fill = fill;
        this.sitting = sitting;
    }

    @Override
    public void setupAnim(NifflerEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float t = actionTime;
        boolean busy = action != NifflerEntity.Action.NONE;

        // --- the pouch ------------------------------------------------------------------------------
        // Its pivot is at its top: it swells down to the ground (one unit of room) and out.
        pouch.yScale = 1.0F + fill;
        pouch.xScale = 1.0F + fill * 0.3F;
        pouch.zScale = 1.0F + fill * 0.35F;

        // --- the walk: a scurry in diagonal pairs ------------------------------------------------------
        float amt = busy || sitting ? 0.0F : Math.min(1.0F, limbSwingAmount * 1.6F);
        float p = limbSwing * 1.3F;
        float[] phase = {p, p + PI};
        for (int i = 0; i < 2; i++) {
            arm[i].xRot += Mth.sin(phase[i]) * 0.9F * amt;
            paw[i].xRot -= Math.max(0.0F, Mth.sin(phase[i] + 1.3F)) * 0.6F * amt;
            leg[i].xRot += Mth.sin(phase[1 - i]) * 0.8F * amt;
            foot[i].xRot -= Mth.sin(phase[1 - i] + 0.8F) * 0.4F * amt;
        }
        float waddle = 0.07F + 0.08F * fill;
        body.y -= Math.abs(Mth.sin(p)) * 0.8F * amt;
        body.zRot += Mth.sin(p) * waddle * amt;
        tail.yRot += Mth.sin(p) * 0.35F * amt;

        // --- the bill: it never stops ---------------------------------------------------------------------
        float burst = Mth.sin(ageInTicks * 0.11F);
        burst = burst > 0.0F ? burst * burst * burst * burst : 0.0F;
        snout.xRot += Mth.sin(ageInTicks * 0.9F) * 0.03F + Mth.sin(ageInTicks * 2.3F) * 0.07F * burst;
        snout.yRot += Mth.sin(ageInTicks * 1.7F) * 0.05F * burst;
        if (!busy) {
            head.yRot += Mth.clamp(netHeadYaw, -50.0F, 50.0F) * Mth.DEG_TO_RAD * 0.8F;
            head.xRot += Mth.clamp(headPitch, -30.0F, 30.0F) * Mth.DEG_TO_RAD * 0.8F;
            tail.yRot += Mth.sin(ageInTicks * 0.08F) * 0.08F;
        }

        // --- sitting up -------------------------------------------------------------------------------------------
        if (sitting && !busy) {
            body.xRot += -1.0F;
            body.y += -2.6F;
            for (int i = 0; i < 2; i++) {
                float s = i == 0 ? -1.0F : 1.0F;
                arm[i].xRot += 0.35F;               // fore paws held up at the chest
                arm[i].zRot += s * 0.15F;
                paw[i].xRot += -0.5F;
                leg[i].xRot += 1.0F;                // hind legs flat on the ground in front
                foot[i].xRot += 0.45F;
            }
            head.xRot += 0.85F;
            tail.xRot += 1.0F;
            pouch.xRot += -0.1F;
        }

        switch (action) {
            case SNIFF -> {
                body.xRot += 0.15F;
                head.xRot += 0.25F + Mth.sin(t * 0.9F) * 0.06F;
                head.yRot += Mth.sin(t * 0.35F) * 0.55F;
                snout.xRot += Mth.sin(t * 2.6F) * 0.12F;
            }
            case DIG -> dig(t, 0.0F);
            case BURROW -> dig(t, Math.min(1.0F, t / NifflerEntity.Action.BURROW.ticks));
            case TICKLED -> tickled(t);
            default -> {
            }
        }
    }

    /** Nose down, the fore paws throwing earth back. sink 0..1 takes it into the ground. */
    private void dig(float t, float sink) {
        float in = Math.min(1.0F, t / 5.0F);
        body.xRot += (0.35F + 0.35F * sink) * in;
        body.y += (1.0F + 15.0F * sink * sink) * in;
        head.xRot += 0.3F * in;
        snout.xRot += 0.2F * in + Mth.sin(t * 2.2F) * 0.08F;
        for (int i = 0; i < 2; i++) {
            float off = i == 0 ? 0.0F : PI;
            arm[i].xRot += (-0.7F + Mth.sin(t * 1.6F + off) * 0.9F) * in;
            paw[i].xRot += Math.max(0.0F, Mth.sin(t * 1.6F + off + 1.0F)) * 0.8F * in;
            leg[i].xRot += -0.25F * in;
        }
        tail.xRot += -0.5F * in;
        tail.yRot += Mth.sin(t * 0.9F) * 0.4F * in;
        body.zRot += Mth.sin(t * 0.8F) * 0.05F * in;
    }

    /** On its back, kicking and wriggling. */
    private void tickled(float t) {
        float total = NifflerEntity.Action.TICKLED.ticks;
        float roll = Math.min(1.0F, Math.min(t / 8.0F, (total - t) / 8.0F));
        roll = roll * roll * (3.0F - 2.0F * roll);
        body.zRot += PI * roll;
        body.y += 1.5F * roll;
        body.yRot += Mth.sin(t * 0.7F) * 0.2F * roll;
        for (int i = 0; i < 2; i++) {
            float off = i == 0 ? 0.0F : PI;
            arm[i].xRot += Mth.sin(t * 1.4F + off) * 0.7F * roll;
            leg[i].xRot += Mth.sin(t * 1.2F + off + 1.0F) * 0.7F * roll;
        }
        head.yRot += Mth.sin(t * 0.9F) * 0.4F * roll;
        tail.yRot += Mth.sin(t * 1.1F) * 0.5F * roll;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
