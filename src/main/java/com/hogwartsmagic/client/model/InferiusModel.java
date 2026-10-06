package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.InferiusEntity;
import net.minecraft.client.animation.KeyframeAnimations;
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
import org.joml.Vector3f;

/**
 * An Inferius: a gaunt drowned corpse, hunched forward with its arms hanging
 * long, ribs showing through grey-white skin, and black wet hair hanging in
 * strands over a face with nothing lit in its sockets.
 *
 * <p>Geometry comes from {@code blockbench_models/inferius.bbmodel}; clips are
 * in {@link InferiusAnimations}. The hunch is the rest pose of {@code body}
 * (and the arms are counter-rotated so they still hang plumb), so every clip
 * is authored as a delta from that stoop.
 */
public class InferiusModel extends HierarchicalModel<InferiusEntity> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "inferius"), "main");

    /** RISE length in ms; the idle sway is held off until the corpse is up. */
    private static final long RISE_MS = 1500L;

    private final Vector3f animationCache = new Vector3f();
    private final ModelPart root;
    private final ModelPart head;

    public InferiusModel(ModelPart root) {
        this.root = root.getChild("root");
        this.head = this.root.getChild("body").getChild("head");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(76, 0).addBox(-3.5F, -11.0F, -1.5F, 7.0F, 11.0F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(66, 16).addBox(-4.0F, -2.0F, -2.0F, 8.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
        .texOffs(14, 29).addBox(-0.5F, 2.0F, -2.2F, 3.0F, 4.0F, 1.0F, new CubeDeformation(-0.3F))
        .texOffs(22, 29).addBox(-3.0F, 2.0F, 1.2F, 3.0F, 3.0F, 1.0F, new CubeDeformation(-0.3F))
        .texOffs(30, 29).addBox(-1.0F, -12.5F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -12.0F, 0.0F, 0.3491F, 0.0F, 0.0F));

        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(48, 0).addBox(-3.5F, -7.5F, -3.5F, 7.0F, 7.0F, 7.0F, new CubeDeformation(0.0F))
        .texOffs(96, 16).addBox(-2.5F, -0.5F, -3.3F, 5.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -11.5F, 0.0F, -0.2793F, 0.0F, 0.0F));

        PartDefinition hair = head.addOrReplaceChild("hair", CubeListBuilder.create().texOffs(96, 0).addBox(-4.0F, 1.0F, 2.0F, 8.0F, 11.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(116, 0).addBox(-4.0F, 1.0F, -3.0F, 1.0F, 8.0F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(0, 16).addBox(3.0F, 1.0F, -3.0F, 1.0F, 8.0F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(12, 16).addBox(-4.0F, -1.0F, -4.0F, 8.0F, 3.0F, 8.0F, new CubeDeformation(0.0F))
        .texOffs(60, 16).addBox(-3.7F, 1.5F, -4.2F, 2.0F, 8.0F, 1.0F, new CubeDeformation(-0.25F))
        .texOffs(90, 16).addBox(1.7F, 1.5F, -4.2F, 2.0F, 7.0F, 1.0F, new CubeDeformation(-0.25F))
        .texOffs(38, 29).addBox(-0.4F, 0.0F, -4.1F, 1.0F, 3.0F, 1.0F, new CubeDeformation(-0.3F)), PartPose.offset(0.0F, -7.5F, 0.0F));

        PartDefinition left_arm = body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, 0.0F, -1.5F, 3.0F, 13.0F, 3.0F, new CubeDeformation(-0.35F))
        .texOffs(42, 29).addBox(-0.25F, 12.5F, -1.3F, 1.0F, 2.0F, 1.0F, new CubeDeformation(-0.15F))
        .texOffs(46, 29).addBox(-1.0F, 13.0F, -1.3F, 1.0F, 2.0F, 1.0F, new CubeDeformation(-0.15F))
        .texOffs(50, 29).addBox(-1.75F, 12.5F, -1.3F, 1.0F, 2.0F, 1.0F, new CubeDeformation(-0.15F)), PartPose.offsetAndRotation(-4.5F, -10.0F, 0.0F, -0.4189F, 0.0F, 0.0524F));

        PartDefinition right_arm = body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(12, 0).addBox(-1.0F, 0.0F, -1.5F, 3.0F, 13.0F, 3.0F, new CubeDeformation(-0.35F))
        .texOffs(44, 16).addBox(-1.5F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(-0.4F))
        .texOffs(54, 29).addBox(-0.75F, 12.5F, -1.3F, 1.0F, 2.0F, 1.0F, new CubeDeformation(-0.15F))
        .texOffs(58, 29).addBox(0.0F, 13.0F, -1.3F, 1.0F, 2.0F, 1.0F, new CubeDeformation(-0.15F))
        .texOffs(62, 29).addBox(0.75F, 12.5F, -1.3F, 1.0F, 2.0F, 1.0F, new CubeDeformation(-0.15F)), PartPose.offsetAndRotation(4.5F, -10.0F, 0.0F, -0.4189F, 0.0F, -0.0524F));

        PartDefinition left_leg = root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(24, 0).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 12.0F, 3.0F, new CubeDeformation(-0.2F))
        .texOffs(114, 16).addBox(-1.5F, 11.0F, -2.5F, 3.0F, 1.0F, 4.0F, new CubeDeformation(-0.15F)), PartPose.offset(-2.0F, -12.0F, 0.0F));

        PartDefinition right_leg = root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(36, 0).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 12.0F, 3.0F, new CubeDeformation(-0.2F))
        .texOffs(0, 29).addBox(-1.5F, 11.0F, -2.5F, 3.0F, 1.0F, 4.0F, new CubeDeformation(-0.15F)), PartPose.offset(2.0F, -12.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 128, 64);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(InferiusEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        this.animateWalk(InferiusAnimations.WALK, limbSwing, limbSwingAmount, 1.5F, 2.5F);
        this.animate(entity.riseAnimation, InferiusAnimations.RISE, ageInTicks);
        this.animate(entity.grabAnimation, InferiusAnimations.GRAB, ageInTicks);
        this.animate(entity.recoilAnimation, InferiusAnimations.RECOIL, ageInTicks);

        // The sway fades out as the walk fades in, so a corpse that stops
        // drifts back into its loll instead of snapping to it. It waits for
        // the rise to finish: swaying while still half in the ground looks wrong.
        boolean rising = entity.riseAnimation.isStarted()
                && entity.riseAnimation.getAccumulatedTime() < RISE_MS;
        float sway = 1.0F - Mth.clamp(limbSwingAmount * 2.5F, 0.0F, 1.0F);
        if (!rising && sway > 0.0F) {
            long swayMs = (long) (ageInTicks * 50.0F);
            KeyframeAnimations.animate(this, InferiusAnimations.IDLE_SWAY, swayMs, sway, this.animationCache);
        }

        // It is dead; it turns its head slowly and not far.
        this.head.yRot += Mth.clamp(netHeadYaw, -50.0F, 50.0F) * Mth.DEG_TO_RAD * 0.7F;
        this.head.xRot += Mth.clamp(headPitch, -30.0F, 30.0F) * Mth.DEG_TO_RAD * 0.7F;
    }
}
