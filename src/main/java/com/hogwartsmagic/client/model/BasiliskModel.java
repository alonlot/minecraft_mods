package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.BasiliskEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A wedge-skulled serpent on a thirteen-segment spine.
 *
 * <p>Geometry comes from blockbench_models/basilisk.bbmodel through the Modded
 * Entity codec, and the head assembly is driven by the clips in
 * {@link BasiliskAnimations}. The spine is not: it is solved here every frame.
 *
 * <p>The segments are <em>siblings</em> under {@code root}, not a nested chain,
 * and each one is positioned absolutely from a ring buffer of the entity's
 * recent world positions. That is what makes the body retrace the path the head
 * actually took — round a corner, up a stair, down a burrow — which no fixed
 * keyframe clip can express for a body this long. A lateral undulation and the
 * per-state lift and coil ride on top of that path.
 */
public class BasiliskModel extends HierarchicalModel<BasiliskEntity> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "basilisk"), "main");

    /** Segments in the spine. Each cube is 16 px deep, so one segment is one block. */
    public static final int SEGMENTS = 13;

    /**
     * Resting height of each segment's axis above the entity's feet, in model
     * units (negative is up). The body sits high through the swell and settles
     * toward the ground down the tail.
     */
    private static final float[] REST_Y = {
            -12.0F, -12.0F, -12.0F, -12.0F, -12.0F, -12.0F, -11.0F,
            -10.0F, -9.0F, -8.0F, -7.0F, -6.0F, -4.0F
    };

    /** Rest Z of the tongue part, so the flick can slide out from it. */
    private static final float TONGUE_REST_Z = -10.0F;

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart tongue;
    private final ModelPart[] segments = new ModelPart[SEGMENTS];

    /** Scratch buffers, reused every frame so the solver allocates nothing. */
    private final double[] sampleDistances = new double[SEGMENTS + 1];
    private final Vec3[] samplePoints = new Vec3[SEGMENTS + 1];
    private final float[] localX = new float[SEGMENTS + 1];
    private final float[] localY = new float[SEGMENTS + 1];
    private final float[] localZ = new float[SEGMENTS + 1];

    public BasiliskModel(ModelPart root) {
        this.root = root.getChild("root");
        this.head = this.root.getChild("head");
        this.jaw = this.head.getChild("jaw");
        this.tongue = this.jaw.getChild("tongue");
        for (int i = 0; i < SEGMENTS; i++) {
            this.segments[i] = this.root.getChild(segmentName(i));
            this.sampleDistances[i] = i;
        }
        this.sampleDistances[SEGMENTS] = SEGMENTS;
    }

    private static String segmentName(int index) {
        return String.format("seg%02d", index + 1);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(322, 0).addBox(-11.0F, -10.0F, -14.0F, 22.0F, 18.0F, 16.0F, new CubeDeformation(0.0F))
        .texOffs(323, 37).addBox(-8.5F, -8.0F, -26.0F, 17.0F, 15.0F, 12.0F, new CubeDeformation(0.0F))
        .texOffs(94, 71).addBox(-5.5F, -4.0F, -36.0F, 11.0F, 10.5F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(141, 95).addBox(-3.5F, -0.5F, -41.0F, 7.0F, 6.5F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(307, 71).addBox(-1.5F, -14.0F, -12.0F, 3.0F, 4.0F, 14.0F, new CubeDeformation(0.0F))
        .texOffs(200, 95).addBox(-5.0F, 4.0F, -32.0F, 2.0F, 8.0F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(211, 95).addBox(3.0F, 4.0F, -32.0F, 2.0F, 8.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -13.0F, 0.0F));

        head.addOrReplaceChild("horn_r_r1", CubeListBuilder.create().texOffs(183, 95).addBox(-1.0F, -6.0F, -5.0F, 2.5F, 6.0F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(166, 95).addBox(-17.5F, -6.0F, -5.0F, 2.5F, 6.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(8.0F, -9.0F, 3.0F, 0.4538F, 0.0F, 0.0F));

        head.addOrReplaceChild("brow_r_r1", CubeListBuilder.create().texOffs(182, 71).addBox(0.0F, -4.0F, -15.0F, 6.5F, 4.0F, 15.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.0F, -7.0F, -14.0F, 0.0F, 0.0F, 0.1745F));

        head.addOrReplaceChild("brow_l_r1", CubeListBuilder.create().texOffs(137, 71).addBox(-6.5F, -4.0F, -15.0F, 6.5F, 4.0F, 15.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.0F, -7.0F, -14.0F, 0.0F, 0.0F, -0.1745F));

        PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(229, 0).addBox(-9.0F, 0.0F, -28.0F, 18.0F, 6.0F, 28.0F, new CubeDeformation(0.0F))
        .texOffs(58, 95).addBox(-6.0F, 0.0F, -36.0F, 12.0F, 5.5F, 8.0F, new CubeDeformation(0.0F))
        .texOffs(260, 95).addBox(-5.0F, -5.0F, -28.0F, 2.0F, 5.0F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(271, 95).addBox(3.0F, -5.0F, -28.0F, 2.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 6.0F, -2.0F));

        PartDefinition tongue = jaw.addOrReplaceChild("tongue", CubeListBuilder.create().texOffs(227, 71).addBox(-1.0F, -0.5F, -18.0F, 2.0F, 1.0F, 18.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 2.0F, -10.0F));

        tongue.addOrReplaceChild("tongue_fr_r1", CubeListBuilder.create().texOffs(241, 95).addBox(-0.5F, -0.5F, -8.0F, 1.0F, 1.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.7F, 0.0F, -17.0F, 0.0F, -0.2618F, 0.0F));

        tongue.addOrReplaceChild("tongue_fl_r1", CubeListBuilder.create().texOffs(222, 95).addBox(-0.5F, -0.5F, -8.0F, 1.0F, 1.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.7F, 0.0F, -17.0F, 0.0F, 0.2618F, 0.0F));

        head.addOrReplaceChild("hood_left", CubeListBuilder.create().texOffs(437, 37).addBox(-3.5F, -7.0F, -8.0F, 3.5F, 13.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(-11.0F, 0.0F, -2.0F));

        head.addOrReplaceChild("hood_right", CubeListBuilder.create().texOffs(470, 37).addBox(0.0F, -7.0F, -8.0F, 3.5F, 13.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(11.0F, 0.0F, -2.0F));

        head.addOrReplaceChild("eye_left", CubeListBuilder.create().texOffs(99, 95).addBox(-1.5F, -3.0F, -4.0F, 2.0F, 6.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(-9.0F, -5.0F, -23.0F));

        head.addOrReplaceChild("eye_right", CubeListBuilder.create().texOffs(120, 95).addBox(-0.5F, -3.0F, -4.0F, 2.0F, 6.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(9.0F, -5.0F, -23.0F));

        root.addOrReplaceChild("seg01", CubeListBuilder.create().texOffs(264, 37).addBox(-6.5F, -6.0F, 0.0F, 13.0F, 12.0F, 16.0F, new CubeDeformation(0.0F))
        .texOffs(0, 95).addBox(-1.0F, -9.0F, 2.0F, 2.0F, 3.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, 0.0F));

        root.addOrReplaceChild("seg02", CubeListBuilder.create().texOffs(138, 37).addBox(-8.0F, -7.5F, 0.0F, 16.0F, 15.0F, 16.0F, new CubeDeformation(0.0F))
        .texOffs(29, 95).addBox(-1.0F, -10.5F, 2.0F, 2.0F, 3.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, 16.0F));

        root.addOrReplaceChild("seg03", CubeListBuilder.create().texOffs(399, 0).addBox(-9.5F, -9.0F, 0.0F, 19.0F, 18.0F, 16.0F, new CubeDeformation(0.0F))
        .texOffs(342, 71).addBox(-1.5F, -12.0F, 2.0F, 3.0F, 3.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, 32.0F));

        root.addOrReplaceChild("seg04", CubeListBuilder.create().texOffs(0, 0).addBox(-11.0F, -10.0F, 0.0F, 22.0F, 20.0F, 16.0F, new CubeDeformation(0.0F))
        .texOffs(373, 71).addBox(-1.5F, -13.0F, 2.0F, 3.0F, 3.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, 48.0F));

        root.addOrReplaceChild("seg05", CubeListBuilder.create().texOffs(77, 0).addBox(-11.0F, -10.0F, 0.0F, 22.0F, 20.0F, 16.0F, new CubeDeformation(0.0F))
        .texOffs(404, 71).addBox(-1.5F, -13.0F, 2.0F, 3.0F, 3.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, 64.0F));

        root.addOrReplaceChild("seg06", CubeListBuilder.create().texOffs(154, 0).addBox(-10.5F, -9.5F, 0.0F, 21.0F, 19.0F, 16.0F, new CubeDeformation(0.0F))
        .texOffs(435, 71).addBox(-1.5F, -12.5F, 2.0F, 3.0F, 3.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, 80.0F));

        root.addOrReplaceChild("seg07", CubeListBuilder.create().texOffs(0, 37).addBox(-9.5F, -8.5F, 0.0F, 19.0F, 17.0F, 16.0F, new CubeDeformation(0.0F))
        .texOffs(466, 71).addBox(-1.5F, -11.5F, 2.0F, 3.0F, 3.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -11.0F, 96.0F));

        root.addOrReplaceChild("seg08", CubeListBuilder.create().texOffs(71, 37).addBox(-8.5F, -7.5F, 0.0F, 17.0F, 15.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -10.0F, 112.0F));

        root.addOrReplaceChild("seg09", CubeListBuilder.create().texOffs(203, 37).addBox(-7.0F, -6.0F, 0.0F, 14.0F, 12.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -9.0F, 128.0F));

        root.addOrReplaceChild("seg10", CubeListBuilder.create().texOffs(382, 37).addBox(-5.5F, -4.5F, 0.0F, 11.0F, 9.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -8.0F, 144.0F));

        root.addOrReplaceChild("seg11", CubeListBuilder.create().texOffs(0, 71).addBox(-4.0F, -3.5F, 0.0F, 8.0F, 7.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -7.0F, 160.0F));

        root.addOrReplaceChild("seg12", CubeListBuilder.create().texOffs(49, 71).addBox(-3.0F, -2.5F, 0.0F, 6.0F, 5.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -6.0F, 176.0F));

        root.addOrReplaceChild("seg13", CubeListBuilder.create().texOffs(268, 71).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 192.0F));

        return LayerDefinition.create(meshdefinition, 512, 128);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(BasiliskEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // Clips are additive, so every part has to start from its rest pose or
        // last frame's offsets accumulate.
        this.root().getAllParts().forEach(ModelPart::resetPose);

        // ageInTicks is tickCount + partialTick, so this recovers the fraction
        // the renderer is currently between ticks.
        float partialTick = Mth.clamp(ageInTicks - entity.tickCount, 0.0F, 1.0F);

        solveSpine(entity, partialTick, ageInTicks, limbSwingAmount);

        this.animate(entity.idleAnimationState, BasiliskAnimations.IDLE, ageInTicks);
        this.animateWalk(BasiliskAnimations.SLITHER, limbSwing, limbSwingAmount, 2.2F, 3.0F);
        this.animate(entity.rearAnimationState, BasiliskAnimations.REAR, ageInTicks);
        this.animate(entity.strikeAnimationState, BasiliskAnimations.STRIKE, ageInTicks);
        this.animate(entity.spitAnimationState, BasiliskAnimations.SPIT, ageInTicks);
        this.animate(entity.biteAnimationState, BasiliskAnimations.BITE, ageInTicks);
        this.animate(entity.burrowAnimationState, BasiliskAnimations.BURROW, ageInTicks);
        this.animate(entity.eruptAnimationState, BasiliskAnimations.ERUPT, ageInTicks);
        this.animate(entity.blindedAnimationState, BasiliskAnimations.BLINDED, ageInTicks);
        this.animate(entity.deathAnimationState, BasiliskAnimations.DEATH, ageInTicks);

        applyTongueFlick(entity, ageInTicks);

        // Head tracking last, and damped mid-action: the clips already throw the
        // head around during a strike, and full tracking on top reads as a twitch.
        float track = entity.isPerformingAction() ? 0.2F : 0.8F;
        this.head.yRot += Mth.clamp(netHeadYaw, -50.0F, 50.0F) * Mth.DEG_TO_RAD * track;
        this.head.xRot += Mth.clamp(headPitch, -32.0F, 32.0F) * Mth.DEG_TO_RAD * track;
    }

    /**
     * Places every spine segment on the path the head walked, then layers the
     * undulation and the current action's shaping over it.
     */
    private void solveSpine(BasiliskEntity entity, float partialTick, float ageInTicks, float limbSwingAmount) {
        double bodyYaw = Mth.lerp(partialTick, entity.yBodyRotO, entity.yBodyRot) * Mth.DEG_TO_RAD;
        double cos = Math.cos(bodyYaw);
        double sin = Math.sin(bodyYaw);

        Vec3 origin = new Vec3(
                Mth.lerp(partialTick, entity.xo, entity.getX()),
                Mth.lerp(partialTick, entity.yo, entity.getY()),
                Mth.lerp(partialTick, entity.zo, entity.getZ()));

        entity.samplePath(origin, this.sampleDistances, this.samplePoints);

        // World offsets -> the model's local frame. Model +Z runs back down the
        // body and model +Y runs down, hence the two sign flips.
        for (int i = 0; i <= SEGMENTS; i++) {
            Vec3 point = this.samplePoints[i];
            double dx = point.x - origin.x;
            double dy = point.y - origin.y;
            double dz = point.z - origin.z;
            double along = dx * cos + dz * sin;
            double across = -dx * sin + dz * cos;
            this.localX[i] = (float) (along * 16.0D);
            this.localY[i] = (float) (-dy * 16.0D);
            this.localZ[i] = (float) (-across * 16.0D);
        }

        // Undulation. Locked to the limb swing while moving so the wave travels
        // at the speed the body does, and to a slow clock when at rest.
        boolean moving = limbSwingAmount > 0.06F;
        float waveTime = moving ? ageInTicks * 0.34F : ageInTicks * 0.055F;
        float waveAmp = (moving ? 3.4F + limbSwingAmount * 7.0F : 1.5F);

        BasiliskEntity.ActionState state = entity.getActionState();
        int stateTick = entity.getStateTick();
        float rearAmount = entity.rearProgress(partialTick);
        float coilAmount = entity.coilProgress(partialTick);
        float burrowAmount = entity.burrowProgress(partialTick);

        boolean headHidden = burrowAmount > 0.04F && burrowAmount < 0.96F && entity.isBurrowing();
        this.head.visible = !entity.isFullyBurrowed() && !headHidden;

        for (int i = 0; i < SEGMENTS; i++) {
            ModelPart segment = this.segments[i];
            segment.visible = true;

            float x = this.localX[i];
            float y = this.localY[i] + REST_Y[i];
            float z = this.localZ[i];

            // Serpentine undulation, growing toward the whip tail.
            float taper = 0.35F + 0.65F * (i / (float) (SEGMENTS - 1));
            float phase = waveTime - i * 0.62F;
            x += Mth.sin(phase) * waveAmp * taper;
            y += Mth.sin(phase * 0.5F + 1.1F) * waveAmp * 0.22F * taper;

            // Rearing lifts and gathers the front of the body into an S.
            if (rearAmount > 0.0F) {
                float front = Math.max(0.0F, 1.0F - i / 5.0F);
                y -= rearAmount * front * 26.0F;
                z += rearAmount * front * 7.0F;
                x += rearAmount * front * Mth.sin(i * 1.15F) * 5.0F;
            }

            // The tail sweep coils the back half hard around the body.
            if (coilAmount > 0.0F) {
                float back = Mth.clamp((i - 4) / 6.0F, 0.0F, 1.0F);
                x += coilAmount * back * Mth.sin(i * 0.85F + stateTick * 0.42F) * 26.0F;
                z -= coilAmount * back * 5.0F;
            }

            // Burrowing swallows the body front to back, and spits it out back
            // to front. Hidden rather than merely sunk, so it cannot poke
            // through a floor from below.
            if (burrowAmount > 0.0F) {
                float threshold = (i + 1) / (float) (SEGMENTS + 1);
                float sink = Mth.clamp((burrowAmount - threshold) * 4.0F, 0.0F, 1.0F);
                y += sink * 30.0F;
                if (sink >= 1.0F) {
                    segment.visible = false;
                }
            }
            if (entity.isFullyBurrowed()) {
                segment.visible = false;
            }

            segment.x = x;
            segment.y = y;
            segment.z = z;

            // Aim the segment down the body: its cube runs from its own origin
            // toward the next sample.
            float dirX = this.localX[i + 1] - this.localX[i];
            float dirY = this.localY[i + 1] - this.localY[i];
            float dirZ = this.localZ[i + 1] - this.localZ[i];
            float length = Mth.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
            if (length < 1.0E-4F) {
                segment.yRot = 0.0F;
                segment.xRot = 0.0F;
            } else {
                segment.yRot = (float) Math.atan2(dirX, dirZ);
                segment.xRot = (float) -Math.asin(Mth.clamp(dirY / length, -1.0F, 1.0F));
            }
            // Bank the segment into the undulation so the belly rolls.
            segment.zRot = Mth.cos(phase) * 0.10F * taper;
        }
    }

    /**
     * Snakes taste the air on a cycle rather than continuously. The tongue is
     * translated rather than keyframed because the animation codec's sign
     * handling for position channels is easy to get backwards.
     */
    private void applyTongueFlick(BasiliskEntity entity, float ageInTicks) {
        float cycle = (ageInTicks % 55.0F) / 55.0F;
        float extend = 0.0F;
        if (cycle < 0.22F) {
            // Two quick darts per flick, which is what makes it read as a tongue.
            float t = cycle / 0.22F;
            extend = Mth.sin(t * Mth.PI * 2.0F);
            extend = Math.max(0.0F, extend);
        }
        if (entity.isFullyBurrowed()) {
            extend = 0.0F;
        }
        this.tongue.z = TONGUE_REST_Z - extend * 10.0F;
        this.tongue.xRot = -0.08F + extend * 0.22F;
        this.tongue.yRot = Mth.sin(ageInTicks * 0.7F) * 0.12F * extend;
        // A flicking tongue needs a gap to leave through.
        this.jaw.xRot += extend * 0.10F;
    }
}
