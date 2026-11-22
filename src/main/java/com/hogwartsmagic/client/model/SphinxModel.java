package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.SphinxEntity;
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
 * The Sphinx: a lioness's body and a woman's head in the striped nemes headdress.
 *
 * <p>The bone tree and its texture are generated together by {@code tools/sphinx/build_sphinx.py}
 * into {@link SphinxGeometry}; this class only moves the bones. Every frame starts from the rest
 * pose (standing) and adds to it, so the numbers here are changes, not absolute angles.
 *
 * <p>Two poses, blended by {@link SphinxEntity#lieAmount}:
 * <ul>
 *   <li><b>Standing</b>, to fight or to walk home: a big cat's walk, the legs moving in the lateral
 *       sequence (left hind, left fore, right hind, right fore), elbows, knees and hocks bending
 *       through the swing.</li>
 *   <li><b>Lying</b>, as the statues lie: body down, forearms flat on the ground in front with the
 *       paws forward, hind legs folded under the haunch, the tail curled round to one side.</li>
 * </ul>
 */
public class SphinxModel extends EntityModel<SphinxEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "sphinx"), "main");

    private static final float PI = (float) Math.PI;
    private static final float HALF_PI = PI / 2.0F;
    /** How far the body drops to lie down, in model units (32 a block before the renderer's scale). */
    private static final float LIE_DROP = 16.0F;
    private static final float WALK_RATE = 0.55F;

    private final ModelPart root;
    private final ModelPart torso;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart[] tail;
    private final ModelPart[][] fore;   // [side][shoulder, elbow, wrist], side 0 left, 1 right
    private final ModelPart[][] hind;   // [side][hip, stifle, hock, paw]

    private float lie;
    private boolean angry;

    public SphinxModel(ModelPart model) {
        Map<String, ModelPart> b = SphinxGeometry.bones(model);
        this.root = b.get("root");
        this.torso = b.get("torso");
        this.neck = b.get("neck");
        this.head = b.get("head");
        this.tail = new ModelPart[] {b.get("tail1"), b.get("tail2"), b.get("tail3"), b.get("tail4")};
        this.fore = new ModelPart[2][];
        this.hind = new ModelPart[2][];
        String[] sides = {"left", "right"};
        for (int i = 0; i < 2; i++) {
            fore[i] = new ModelPart[] {b.get("shoulder_" + sides[i]), b.get("elbow_" + sides[i]), b.get("wrist_" + sides[i])};
            hind[i] = new ModelPart[] {b.get("hip_" + sides[i]), b.get("stifle_" + sides[i]), b.get("hock_" + sides[i]),
                    b.get("hpaw_" + sides[i])};
        }
    }

    public static LayerDefinition createBodyLayer() {
        return SphinxGeometry.createBodyLayer();
    }

    @Override
    public void prepareMobModel(SphinxEntity entity, float limbSwing, float limbSwingAmount, float partialTick) {
        this.lie = entity.lieAmount(partialTick);
        this.angry = entity.isAngry();
    }

    @Override
    public void setupAnim(SphinxEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float l = lie;
        float stand = 1.0F - l;
        float amt = Math.min(1.0F, limbSwingAmount) * stand;
        float phase = limbSwing * WALK_RATE;

        // --- standing: the walk -------------------------------------------------------------------
        // Lateral sequence: left hind, left fore, right hind, right fore, a quarter cycle apart.
        float[] forePhase = {phase + HALF_PI, phase + 3.0F * HALF_PI};
        float[] hindPhase = {phase, phase + PI};
        for (int i = 0; i < 2; i++) {
            float fp = forePhase[i];
            fore[i][0].xRot += Mth.sin(fp) * 0.5F * amt;
            fore[i][1].xRot += Math.max(0.0F, Mth.sin(fp + 1.2F)) * 0.55F * amt;   // the elbow folds in the swing
            fore[i][2].xRot -= Math.max(0.0F, Mth.sin(fp + 1.6F)) * 0.5F * amt;    // and the paw flicks forward
            float hp = hindPhase[i];
            hind[i][0].xRot += Mth.sin(hp) * 0.45F * amt;
            hind[i][1].xRot += Math.max(0.0F, Mth.sin(hp + 1.0F)) * 0.45F * amt;
            hind[i][2].xRot -= Math.max(0.0F, Mth.sin(hp + 1.0F)) * 0.35F * amt;
        }
        torso.y -= Math.abs(Mth.sin(phase)) * 0.6F * amt;
        torso.zRot += Mth.sin(phase) * 0.03F * amt;

        // --- lying ---------------------------------------------------------------------------------
        torso.y += LIE_DROP * l;
        for (int i = 0; i < 2; i++) {
            float s = i == 0 ? -1.0F : 1.0F;
            // Forearms flat on the ground ahead, paws forward and flat.
            fore[i][0].xRot += -0.66F * l;
            fore[i][1].xRot += -0.85F * l;
            fore[i][2].xRot += 1.57F * l;
            // Hind legs folded: knee forward along the flank, shin back under it, the paw flat.
            // Found by a search over the skeleton in tools/sphinx (the same bones, posed in Python):
            // the paw flat on the ground and tucked forward under the belly, nothing below the
            // ground, the haunch below the line of the back.
            hind[i][0].xRot += -1.0F * l;
            hind[i][0].zRot += -s * 0.1F * l;
            hind[i][1].xRot += 0.5F * l;
            hind[i][2].xRot += -1.0F * l;
            hind[i][3].xRot += 1.5F * l;
        }
        // The tail falls from the rump to the ground, then its end lies along the ground and curls
        // round toward her left flank.
        tail[0].xRot += -0.45F * l;
        tail[2].xRot += 0.4F * l;
        tail[3].xRot += 0.15F * l;
        tail[2].yRot += -0.6F * l;
        tail[3].yRot += -0.7F * l;

        // --- the swipe: the right forepaw strikes ---------------------------------------------------
        if (attackTime > 0.0F) {
            float strike = Mth.sin(attackTime * PI);
            fore[1][0].xRot += -1.5F * strike;
            fore[1][0].zRot += 0.15F * strike;
            fore[1][1].xRot += -0.5F * strike;
            fore[1][2].xRot += 0.6F * strike;
            torso.xRot += -0.1F * strike;
        }

        // --- head and neck -----------------------------------------------------------------------------
        float yaw = Mth.clamp(netHeadYaw, -60.0F, 60.0F) * Mth.DEG_TO_RAD;
        float pitch = Mth.clamp(headPitch, -30.0F, 30.0F) * Mth.DEG_TO_RAD;
        neck.yRot += yaw * 0.35F;
        head.yRot += yaw * 0.65F;
        head.xRot += pitch;
        if (angry) {
            neck.xRot += 0.18F * stand;     // the head goes down to fight
            head.xRot -= 0.1F * stand;
        }

        // --- tail ------------------------------------------------------------------------------------------
        float lash = angry ? 0.35F : 0.12F;
        float rate = angry ? 0.3F : 0.05F;
        for (int i = 0; i < tail.length; i++) {
            tail[i].yRot += Mth.sin(ageInTicks * rate - i * 0.7F) * lash * (0.6F + 0.25F * i);
            tail[i].xRot += Mth.cos(ageInTicks * rate * 0.8F - i * 0.5F) * 0.04F;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
