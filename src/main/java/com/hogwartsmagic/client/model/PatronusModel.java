package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.PatronusEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * The stag Patronus: a slim red-deer stag built by hand (no Blockbench). Model space is the usual one,
 * y down, feet at y = 24, head toward -Z.
 *
 * <p>Hierarchy: {@code body} (waist + haunch) carries the {@code chest}, the {@code neck} -> {@code head}
 * (muzzle, ears, two branching antlers of 1x1 tines), the {@code tail}, and four legs, each an upper part
 * with a lower part (knee at the front, hock at the back) ending in a hoof. The gallop drives the legs
 * from limbSwing with the diagonal pairs half a cycle apart; the float bob fades out as the gallop
 * builds, so the two never fight. Client only.
 */
public class PatronusModel extends EntityModel<PatronusEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "patronus"), "main");

    private static final float BODY_Y = 11.0F;
    private static final float NECK_TILT = 0.55F;
    private static final float HEAD_TILT = -0.40F;
    private static final float TAIL_TILT = 0.60F;
    private static final float BACK_UPPER_TILT = 0.30F;
    private static final float HOCK_TILT = -0.30F;
    private static final float EAR_SPLAY = 0.50F;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart chest;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart leftEar;
    private final ModelPart rightEar;
    private final ModelPart leftAntler;
    private final ModelPart rightAntler;
    private final ModelPart tail;
    private final ModelPart leftFrontLeg;
    private final ModelPart rightFrontLeg;
    private final ModelPart leftHindLeg;
    private final ModelPart rightHindLeg;
    private final ModelPart leftFrontShin;
    private final ModelPart rightFrontShin;
    private final ModelPart leftHindShin;
    private final ModelPart rightHindShin;

    /** Brightness multiplier for this frame (the gentle pulse), set in setupAnim. */
    private float pulse = 1.0F;

    public PatronusModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.chest = this.body.getChild("chest");
        this.neck = this.body.getChild("neck");
        this.head = this.neck.getChild("head");
        this.leftEar = this.head.getChild("left_ear");
        this.rightEar = this.head.getChild("right_ear");
        this.leftAntler = this.head.getChild("left_antler");
        this.rightAntler = this.head.getChild("right_antler");
        this.tail = this.body.getChild("tail");
        this.leftFrontLeg = this.body.getChild("left_front_leg");
        this.rightFrontLeg = this.body.getChild("right_front_leg");
        this.leftHindLeg = this.body.getChild("left_hind_leg");
        this.rightHindLeg = this.body.getChild("right_hind_leg");
        this.leftFrontShin = this.leftFrontLeg.getChild("shin");
        this.rightFrontShin = this.rightFrontLeg.getChild("shin");
        this.leftHindShin = this.leftHindLeg.getChild("shin");
        this.rightHindShin = this.rightHindLeg.getChild("shin");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Waist (tucked: narrower and shallower than chest and haunch) and the rounded haunch.
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(28, 0).addBox(-2.75F, -3.5F, -2.0F, 5.5F, 6.0F, 5.0F)
                        .texOffs(0, 15).addBox(-3.25F, -4.0F, 3.0F, 6.5F, 7.0F, 6.0F),
                PartPose.offset(0.0F, BODY_Y, 0.0F));
        // Deep chest, its own part so it can breathe without scaling the legs.
        body.addOrReplaceChild("chest", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.5F, -4.0F, -7.0F, 7.0F, 8.0F, 7.0F),
                PartPose.offset(0.0F, 0.0F, -2.0F));

        // Long neck from the top of the chest, angled up and forward.
        PartDefinition neck = body.addOrReplaceChild("neck", CubeListBuilder.create()
                        .texOffs(26, 15).addBox(-1.75F, -9.0F, -2.0F, 3.5F, 9.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, -2.0F, -7.5F, NECK_TILT, 0.0F, 0.0F));

        // Narrow head with a lower, narrower muzzle (the step).
        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(42, 15).addBox(-2.0F, -2.5F, -4.0F, 4.0F, 4.0F, 5.0F)
                        .texOffs(0, 28).addBox(-1.25F, -1.0F, -7.5F, 2.5F, 2.5F, 3.5F),
                PartPose.offsetAndRotation(0.0F, -9.0F, 0.0F, HEAD_TILT, 0.0F, 0.0F));
        head.addOrReplaceChild("left_ear", CubeListBuilder.create()
                        .texOffs(14, 28).addBox(0.0F, -0.75F, -0.5F, 3.0F, 1.5F, 1.0F),
                PartPose.offsetAndRotation(1.6F, -2.0F, 0.2F, 0.0F, 0.2F, -EAR_SPLAY));
        head.addOrReplaceChild("right_ear", CubeListBuilder.create()
                        .texOffs(14, 28).mirror().addBox(-3.0F, -0.75F, -0.5F, 3.0F, 1.5F, 1.0F),
                PartPose.offsetAndRotation(-1.6F, -2.0F, 0.2F, 0.0F, -0.2F, EAR_SPLAY));
        addAntler(head, "left_antler", 1.0F);
        addAntler(head, "right_antler", -1.0F);

        // Short upturned tail.
        body.addOrReplaceChild("tail", CubeListBuilder.create()
                        .texOffs(22, 28).addBox(-1.0F, -0.75F, 0.0F, 2.0F, 1.5F, 2.5F),
                PartPose.offsetAndRotation(0.0F, -3.0F, 8.75F, TAIL_TILT, 0.0F, 0.0F));

        // Front legs: upper + knee -> slender shin and hoof. Hip at body y +2.5, feet land on y 24.
        addFrontLeg(body, "left_front_leg", 2.1F);
        addFrontLeg(body, "right_front_leg", -2.1F);
        // Hind legs: thigh angled back to the hock, then a vertical cannon and hoof.
        addHindLeg(body, "left_hind_leg", 2.1F);
        addHindLeg(body, "right_hind_leg", -2.1F);

        return LayerDefinition.create(mesh, 64, 64);
    }

    /** One branching antler: three beam segments curving out, up and back, with five tines. */
    private static void addAntler(PartDefinition head, String name, float s) {
        CubeListBuilder seg5 = CubeListBuilder.create().texOffs(56, 0).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 5.0F, 1.0F);
        CubeListBuilder seg4 = CubeListBuilder.create().texOffs(56, 0).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F);
        CubeListBuilder seg3 = CubeListBuilder.create().texOffs(56, 0).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F);
        CubeListBuilder seg2 = CubeListBuilder.create().texOffs(56, 0).addBox(-0.5F, -2.5F, -0.5F, 1.0F, 2.5F, 1.0F);

        // Base beam: leans outward and a little back.
        PartDefinition beam1 = head.addOrReplaceChild(name, seg5,
                PartPose.offsetAndRotation(s * 1.3F, -2.3F, -0.2F, -0.30F, 0.0F, s * 0.50F));
        // Brow tine: forward over the face.
        beam1.addOrReplaceChild("brow_tine", seg4,
                PartPose.offsetAndRotation(0.0F, -1.2F, 0.0F, 1.25F, 0.0F, s * -0.15F));
        // Second beam: curves back toward upright.
        PartDefinition beam2 = beam1.addOrReplaceChild("beam2", seg5,
                PartPose.offsetAndRotation(0.0F, -4.6F, 0.0F, -0.25F, 0.0F, s * -0.30F));
        // Bez tine: forward and a touch out.
        beam2.addOrReplaceChild("bez_tine", seg4,
                PartPose.offsetAndRotation(0.0F, -1.8F, 0.0F, 1.00F, 0.0F, s * 0.25F));
        // Top beam.
        PartDefinition beam3 = beam2.addOrReplaceChild("beam3", seg4,
                PartPose.offsetAndRotation(0.0F, -4.6F, 0.0F, 0.15F, 0.0F, s * -0.15F));
        // Trez tine: out to the side.
        beam3.addOrReplaceChild("trez_tine", seg3,
                PartPose.offsetAndRotation(0.0F, -1.2F, 0.0F, 0.55F, 0.0F, s * 0.75F));
        // Crown: two short tines forking at the top.
        beam3.addOrReplaceChild("crown_back", seg3,
                PartPose.offsetAndRotation(0.0F, -3.7F, 0.0F, -0.45F, 0.0F, s * 0.40F));
        beam3.addOrReplaceChild("crown_front", seg2,
                PartPose.offsetAndRotation(0.0F, -3.7F, 0.0F, 0.55F, 0.0F, s * -0.30F));
    }

    private static void addFrontLeg(PartDefinition body, String name, float x) {
        PartDefinition upper = body.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(32, 28).addBox(-1.1F, 0.0F, -1.25F, 2.2F, 5.5F, 2.5F),
                PartPose.offset(x, 2.5F, -6.5F));
        upper.addOrReplaceChild("shin", CubeListBuilder.create()
                        .texOffs(44, 28).addBox(-0.6F, 0.0F, -0.6F, 1.2F, 4.0F, 1.2F)
                        .texOffs(52, 28).addBox(-0.85F, 4.0F, -1.0F, 1.7F, 1.0F, 1.9F),
                PartPose.offset(0.0F, 5.5F, 0.0F));
    }

    private static void addHindLeg(PartDefinition body, String name, float x) {
        PartDefinition upper = body.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(0, 40).addBox(-1.25F, 0.0F, -1.5F, 2.5F, 6.0F, 3.0F),
                PartPose.offsetAndRotation(x, 2.0F, 5.5F, BACK_UPPER_TILT, 0.0F, 0.0F));
        upper.addOrReplaceChild("shin", CubeListBuilder.create()
                        .texOffs(12, 40).addBox(-0.6F, 0.0F, -0.6F, 1.2F, 4.3F, 1.2F)
                        .texOffs(52, 28).addBox(-0.85F, 4.3F, -1.0F, 1.7F, 1.0F, 1.9F),
                PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, HOCK_TILT, 0.0F, 0.0F));
    }

    /** The Doe form reuses the stag with its antlers hidden; the renderer restores them after drawing. */
    public void setAntlersVisible(boolean visible) {
        this.leftAntler.visible = visible;
        this.rightAntler.visible = visible;
    }

    @Override
    public void setupAnim(PatronusEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root.getAllParts().forEach(ModelPart::resetPose);

        float amount = Mth.clamp(limbSwingAmount, 0.0F, 1.0F);
        float still = 1.0F - amount;
        float phase = limbSwing * 0.6662F;
        float a = Mth.cos(phase);           // left front + right hind
        float b = Mth.cos(phase + Mth.PI);  // right front + left hind
        float sa = Mth.sin(phase);
        float sb = Mth.sin(phase + Mth.PI);

        // Float bob only when still; the gallop's own bob takes over as it builds.
        float floatBob = Mth.sin(ageInTicks * 0.1F) * 0.5F * still;
        float gallopBob = -Math.abs(Mth.sin(phase)) * 1.1F * amount;
        this.body.y = BODY_Y + floatBob + gallopBob;
        this.body.xRot = sa * 0.05F * amount;

        // Breathing when still: the chest swells slightly.
        float breath = Mth.sin(ageInTicks * 0.08F);
        float swell = 1.0F + breath * 0.025F * still;
        this.chest.xScale = swell;
        this.chest.yScale = swell;

        // Neck sway in time with the stride, and the head's look.
        float yaw = Mth.clamp(netHeadYaw, -60.0F, 60.0F) * Mth.DEG_TO_RAD;
        float pitch = Mth.clamp(headPitch, -40.0F, 40.0F) * Mth.DEG_TO_RAD;
        this.neck.xRot = NECK_TILT + Mth.cos(phase * 2.0F) * 0.10F * amount - breath * 0.02F * still;
        this.neck.yRot = yaw * 0.4F + sa * 0.06F * amount;
        this.head.xRot = HEAD_TILT + pitch - Mth.cos(phase * 2.0F) * 0.06F * amount;
        this.head.yRot = yaw * 0.6F;

        // Ear flick: a quick twitch now and then while standing, each ear on its own beat.
        int seed = entity.getId() * 37;
        this.leftEar.zRot = -EAR_SPLAY - earFlick(ageInTicks + seed) * 0.6F * still - amount * 0.25F;
        this.rightEar.zRot = EAR_SPLAY + earFlick(ageInTicks + seed + 47.0F) * 0.6F * still + amount * 0.25F;

        // Tail flicks up a little with each stride.
        this.tail.xRot = TAIL_TILT + Math.abs(sa) * 0.35F * amount;

        // Gallop: diagonal pairs half a cycle apart; knees fold back, hocks fold forward as a leg lifts.
        float swing = 0.95F * amount;
        this.leftFrontLeg.xRot = a * swing;
        this.rightFrontLeg.xRot = b * swing;
        this.leftHindLeg.xRot = BACK_UPPER_TILT + b * swing;
        this.rightHindLeg.xRot = BACK_UPPER_TILT + a * swing;
        this.leftFrontShin.xRot = Math.max(0.0F, sa) * 1.2F * amount;
        this.rightFrontShin.xRot = Math.max(0.0F, sb) * 1.2F * amount;
        this.leftHindShin.xRot = HOCK_TILT - Math.max(0.0F, -sb) * 0.9F * amount;
        this.rightHindShin.xRot = HOCK_TILT - Math.max(0.0F, -sa) * 0.9F * amount;

        // A gentle brightness pulse.
        this.pulse = 0.86F + 0.14F * (0.5F + 0.5F * Mth.sin(ageInTicks * 0.12F));
    }

    /** 0 most of the time, a short 0 -> 1 -> 0 twitch for 6 ticks out of every 90. */
    private static float earFlick(float t) {
        float p = t % 90.0F;
        if (p < 0.0F) {
            p += 90.0F;
        }
        if (p >= 6.0F) {
            return 0.0F;
        }
        return Mth.sin(p / 6.0F * Mth.PI);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
        int r = (int) (FastColor.ARGB32.red(color) * this.pulse);
        int g = (int) (FastColor.ARGB32.green(color) * this.pulse);
        int bl = (int) (FastColor.ARGB32.blue(color) * this.pulse);
        int pulsed = FastColor.ARGB32.color(FastColor.ARGB32.alpha(color), r, g, bl);
        this.body.render(poseStack, vertexConsumer, packedLight, packedOverlay, pulsed);
    }
}
