package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.UnicornEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
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

/**
 * The unicorn, built in Blockbench (blockbench_models/unicorn.bbmodel, texture painted by
 * tools/unicorn/paint_unicorn.py from the same UV layout) and animated procedurally.
 *
 * <p>Gait: walk (four-beat), trot (diagonal pairs) and gallop (transverse) are each solved per
 * leg and blended by speed. The phase is advanced by distance travelled
 * ({@link UnicornEntity#getGaitPhase}), so hooves plant instead of skating whatever the speed.
 * Rear-up pivots the whole body about the hind hooves; the graze and rear poses were posed and
 * checked in Blockbench first.
 *
 * <p>Model space: +Y down, -Z forward. A positive xRot swings a hanging part's lower end back
 * and tips a raised part (neck, ears) forward.
 */
public class UnicornModel extends EntityModel<UnicornEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "unicorn"), "main");

    private static final float PI = Mth.PI;
    private static final float TWO_PI = Mth.TWO_PI;

    /** Root offset at rest, and the hind hoof line the rear-up pivots about. */
    private static final float ROOT_Y = 24.0F;
    private static final float HIND_Z = 7.5F;

    // Leg order everywhere: front left, front right, back left, back right.
    private static final float[] WALK_OFFSET = {0.25F, 0.75F, 0.0F, 0.5F};
    private static final float[] TROT_OFFSET = {0.0F, 0.5F, 0.5F, 0.0F};
    private static final float[] GALLOP_OFFSET = {0.45F, 0.57F, 0.0F, 0.12F};

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart neck;
    private final ModelPart mane;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart earLeft;
    private final ModelPart earRight;
    private final ModelPart horn;
    private final ModelPart tail;
    private final ModelPart tailEnd;
    private final ModelPart[] legs = new ModelPart[4];
    private final ModelPart[] shins = new ModelPart[4];

    public UnicornModel(ModelPart modelRoot) {
        this.root = modelRoot.getChild("root");
        this.body = root.getChild("body");
        this.neck = body.getChild("neck");
        this.mane = neck.getChild("mane");
        this.head = neck.getChild("head");
        this.jaw = head.getChild("jaw");
        this.earLeft = head.getChild("ear_left");
        this.earRight = head.getChild("ear_right");
        this.horn = head.getChild("horn");
        this.tail = body.getChild("tail");
        this.tailEnd = tail.getChild("tail_end");
        String[] names = {"front_left", "front_right", "back_left", "back_right"};
        for (int i = 0; i < 4; i++) {
            legs[i] = body.getChild("leg_" + names[i]);
            shins[i] = legs[i].getChild("shin_" + names[i]);
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition top = mesh.getRoot();
        CubeDeformation none = CubeDeformation.NONE;

        PartDefinition root = top.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.5F, -8.0F, -9.0F, 9.0F, 9.0F, 18.0F, none)
                .texOffs(64, 27).addBox(-4.5F, -7.0F, -12.0F, 9.0F, 7.0F, 3.0F, none)
                .texOffs(88, 27).addBox(-4.5F, -8.0F, 9.0F, 9.0F, 8.0F, 2.0F, none),
                PartPose.offset(0.0F, -15.0F, 0.0F));

        PartDefinition neck = body.addOrReplaceChild("neck", CubeListBuilder.create()
                .texOffs(54, 0).addBox(-2.5F, -14.0F, -3.0F, 5.0F, 15.0F, 6.0F, none),
                PartPose.offsetAndRotation(0.0F, -5.0F, -8.0F, 0.5236F, 0.0F, 0.0F));

        neck.addOrReplaceChild("mane", CubeListBuilder.create()
                .texOffs(76, 0).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 15.0F, 3.0F, none)
                .texOffs(86, 0).addBox(-3.5F, 1.0F, -3.0F, 1.0F, 13.0F, 4.0F, none),
                PartPose.offset(0.0F, -14.0F, 4.0F));

        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(16, 27).addBox(-2.5F, -4.0F, -5.0F, 5.0F, 6.0F, 7.0F, none)
                .texOffs(0, 41).addBox(-2.0F, -2.0F, -11.0F, 4.0F, 4.0F, 6.0F, none)
                .texOffs(28, 51).addBox(-1.0F, -4.0F, -6.0F, 2.0F, 3.0F, 1.0F, none),
                PartPose.offsetAndRotation(0.0F, -13.0F, 0.0F, 0.2094F, 0.0F, 0.0F));

        head.addOrReplaceChild("jaw", CubeListBuilder.create()
                .texOffs(64, 41).addBox(-1.5F, 0.0F, -5.0F, 3.0F, 1.0F, 6.0F, none),
                PartPose.offset(0.0F, 2.0F, -5.0F));

        head.addOrReplaceChild("ear_left", CubeListBuilder.create()
                .texOffs(34, 51).addBox(-1.25F, -3.0F, -0.5F, 2.0F, 3.0F, 1.0F, none),
                PartPose.offset(-1.75F, -4.0F, 0.5F));

        head.addOrReplaceChild("ear_right", CubeListBuilder.create()
                .texOffs(40, 51).addBox(-0.75F, -3.0F, -0.5F, 2.0F, 3.0F, 1.0F, none),
                PartPose.offset(1.75F, -4.0F, 0.5F));

        PartDefinition horn = head.addOrReplaceChild("horn", CubeListBuilder.create()
                .texOffs(12, 51).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F, none)
                .texOffs(46, 51).addBox(-0.5F, -7.6F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.15F))
                .texOffs(54, 51).addBox(-0.5F, -11.2F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(-0.3F)),
                PartPose.offsetAndRotation(0.0F, -4.0F, -3.0F, -0.1396F, 0.0F, 0.0F));

        // The two segments turned 45 degrees about the horn's axis, for the spiral.
        horn.addOrReplaceChild("horn_twist", CubeListBuilder.create()
                .texOffs(50, 51).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(-0.1F))
                .texOffs(20, 51).addBox(-1.0F, 3.1F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(-0.25F)),
                PartPose.offsetAndRotation(0.0F, -8.4F, 0.0F, 0.0F, -0.7854F, 0.0F));

        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(20, 41).addBox(-1.5F, 0.0F, -1.0F, 3.0F, 7.0F, 3.0F, none),
                PartPose.offsetAndRotation(0.0F, -7.0F, 11.0F, 0.5236F, 0.0F, 0.0F));

        tail.addOrReplaceChild("tail_end", CubeListBuilder.create()
                .texOffs(96, 0).addBox(-2.0F, -0.5F, -1.5F, 4.0F, 11.0F, 4.0F, none),
                PartPose.offsetAndRotation(0.0F, 6.5F, 0.5F, -0.4538F, 0.0F, 0.0F));

        PartDefinition legFl = body.addOrReplaceChild("leg_front_left", CubeListBuilder.create()
                .texOffs(40, 27).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 9.0F, 3.0F, none),
                PartPose.offset(-2.75F, -2.0F, -7.0F));
        legFl.addOrReplaceChild("shin_front_left", CubeListBuilder.create()
                .texOffs(48, 41).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F, none)
                .texOffs(82, 41).addBox(-1.5F, 6.0F, -1.5F, 3.0F, 2.0F, 3.0F, none),
                PartPose.offset(0.0F, 9.0F, 0.0F));

        PartDefinition legFr = body.addOrReplaceChild("leg_front_right", CubeListBuilder.create()
                .texOffs(52, 27).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 9.0F, 3.0F, none),
                PartPose.offset(2.75F, -2.0F, -7.0F));
        legFr.addOrReplaceChild("shin_front_right", CubeListBuilder.create()
                .texOffs(56, 41).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F, none)
                .texOffs(94, 41).addBox(-1.5F, 6.0F, -1.5F, 3.0F, 2.0F, 3.0F, none),
                PartPose.offset(0.0F, 9.0F, 0.0F));

        PartDefinition legBl = body.addOrReplaceChild("leg_back_left", CubeListBuilder.create()
                .texOffs(112, 0).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 10.0F, 4.0F, none),
                PartPose.offset(-2.25F, -3.0F, 7.5F));
        legBl.addOrReplaceChild("shin_back_left", CubeListBuilder.create()
                .texOffs(32, 41).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 7.0F, 2.0F, none)
                .texOffs(106, 41).addBox(-1.5F, 7.0F, -1.5F, 3.0F, 2.0F, 3.0F, none),
                PartPose.offset(0.0F, 9.0F, 0.0F));

        PartDefinition legBr = body.addOrReplaceChild("leg_back_right", CubeListBuilder.create()
                .texOffs(0, 27).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 10.0F, 4.0F, none),
                PartPose.offset(2.25F, -3.0F, 7.5F));
        legBr.addOrReplaceChild("shin_back_right", CubeListBuilder.create()
                .texOffs(40, 41).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 7.0F, 2.0F, none)
                .texOffs(0, 51).addBox(-1.5F, 7.0F, -1.5F, 3.0F, 2.0F, 3.0F, none),
                PartPose.offset(0.0F, 9.0F, 0.0F));

        return LayerDefinition.create(mesh, 128, 64);
    }

    // ------------------------------------------------------------------------------ animation

    private static float smoothstep(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float frac(float x) {
        return x - Mth.floor(x);
    }

    /** Hip and knee angle of one leg at phase p: stance sweeps the hoof back, swing folds it and reaches. */
    private static void legPose(float p, float duty, float reach, float fold, float[] out) {
        if (p < duty) {
            float s = p / duty;
            out[0] = -reach + 2.0F * reach * s;
            out[1] = 0.0F;
        } else {
            float s = (p - duty) / (1.0F - duty);
            float e = s * s * (3.0F - 2.0F * s);
            out[0] = reach - 2.0F * reach * e;
            out[1] = fold * Mth.sin(PI * s);
        }
    }

    private final float[] tmp = new float[2];

    @Override
    public void setupAnim(UnicornEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);

        float pt = ageInTicks - entity.tickCount;
        float age = ageInTicks;

        boolean foal = entity.isBaby();
        horn.visible = !foal;
        float headScale = foal ? 1.25F : 1.0F;
        head.xScale = headScale;
        head.yScale = headScale;
        head.zScale = headScale;

        float rear = entity.getRearProgress(pt);
        float rearW = rear < 0.0F ? 0.0F : smoothstep(0.0F, 0.3F, rear) * (1.0F - smoothstep(0.62F, 0.9F, rear));
        float graze = entity.getGrazeProgress(pt);
        float grazeW = graze < 0.0F ? 0.0F : smoothstep(0.0F, 0.22F, graze) * (1.0F - smoothstep(0.8F, 1.0F, graze));
        float toss = entity.getTossProgress(pt);

        // --- gait
        float speed = entity.getGaitSpeed(pt);
        float phase = entity.getGaitPhase(pt);
        float move = smoothstep(0.004F, 0.03F, speed) * (1.0F - rearW);
        float trotK = Mth.clamp((speed - 0.08F) / 0.07F, 0.0F, 1.0F);
        float gal = Mth.clamp((speed - 0.17F) / 0.08F, 0.0F, 1.0F);
        float trot = trotK * (1.0F - gal);
        float walk = (1.0F - trotK) * (1.0F - gal);

        for (int i = 0; i < 4; i++) {
            boolean front = i < 2;
            float hip = 0.0F;
            float knee = 0.0F;
            if (walk > 0.0F) {
                legPose(frac(phase + WALK_OFFSET[i]), 0.62F, front ? 0.32F : 0.28F, front ? 1.0F : 0.6F, tmp);
                hip += walk * tmp[0];
                knee += walk * tmp[1];
            }
            if (trot > 0.0F) {
                legPose(frac(phase + TROT_OFFSET[i]), 0.48F, front ? 0.45F : 0.4F, front ? 1.3F : 0.8F, tmp);
                hip += trot * tmp[0];
                knee += trot * tmp[1];
            }
            if (gal > 0.0F) {
                legPose(frac(phase + GALLOP_OFFSET[i]), 0.38F, front ? 0.7F : 0.62F, front ? 1.6F : 0.9F, tmp);
                hip += gal * tmp[0];
                knee += gal * tmp[1];
            }
            legs[i].xRot = hip * move;
            shins[i].xRot = knee * move;
        }

        float cycle = TWO_PI * phase;
        body.y += -move * (walk * 0.3F * Mth.cos(2.0F * cycle) + trot * 0.8F * Mth.cos(2.0F * cycle)
                + gal * 1.3F * Mth.cos(cycle));
        body.xRot += move * gal * 0.07F * Mth.sin(cycle + 0.3F);
        neck.xRot += move * (walk * 0.07F * Mth.sin(2.0F * cycle) + gal * (0.28F + 0.12F * Mth.sin(cycle + 1.2F)));
        head.xRot += move * gal * -0.12F;
        mane.xRot += move * (trot * 0.06F + gal * 0.2F) + 0.03F * Mth.sin(age * 0.11F)
                + move * gal * 0.05F * Mth.sin(age * 0.9F);
        tail.xRot += move * (trot * 0.3F + gal * 0.8F) + move * gal * 0.08F * Mth.sin(2.0F * cycle);
        tailEnd.xRot += move * (trot * 0.15F + gal * 0.4F);

        // --- idle life: breath, tail swish, ear flicks
        float still = 1.0F - 0.7F * move;
        neck.xRot += 0.015F * Mth.sin(age * 0.1F);
        float swish = 0.22F * Mth.sin(age * 0.08F) + 0.12F * Mth.sin(age * 0.031F);
        tail.yRot += swish * still;
        tailEnd.yRot += 0.5F * swish * still + 0.15F * Mth.sin(age * 0.08F - 0.9F) * still;

        float flickL = (float) Math.pow(Math.max(0.0F, Mth.sin(age * 0.067F + entity.getId())), 14.0D);
        float flickR = (float) Math.pow(Math.max(0.0F, Mth.sin(age * 0.053F + 2.0F + entity.getId() * 0.7F)), 14.0D);
        earLeft.xRot -= 0.5F * flickL;
        earLeft.zRot -= 0.3F * flickL;
        earRight.xRot -= 0.5F * flickR;
        earRight.zRot += 0.3F * flickR;
        float earsBack = Math.max(move * gal * 0.7F, rearW * 0.6F);
        earLeft.xRot -= earsBack;
        earRight.xRot -= earsBack;

        // --- look (faded out while the head is busy)
        float lookW = (1.0F - grazeW) * (1.0F - rearW);
        float yaw = netHeadYaw * Mth.DEG_TO_RAD * lookW;
        float pitch = headPitch * Mth.DEG_TO_RAD * lookW;
        neck.yRot += yaw * 0.35F;
        head.yRot += yaw * 0.45F;
        head.xRot += pitch * 0.6F;

        // --- graze: neck right down until the muzzle is in the grass, chewing
        if (grazeW > 0.0F) {
            neck.xRot += 1.78F * grazeW;
            head.xRot += -0.66F * grazeW;
            jaw.xRot += grazeW * 0.22F * Math.max(0.0F, Mth.sin(age * 0.8F));
            earLeft.zRot -= 0.25F * grazeW;
            earRight.zRot += 0.25F * grazeW;
        }

        // --- head toss: up, a shake of the mane, back down
        if (toss >= 0.0F) {
            float env = Mth.sin(PI * toss);
            neck.xRot += -0.45F * env;
            head.yRot += 0.35F * Mth.sin(3.0F * PI * toss) * env;
            mane.zRot += 0.12F * Mth.sin(4.0F * PI * toss) * env;
            earLeft.xRot += 0.2F * env;
            earRight.xRot += 0.2F * env;
        }

        // --- rear: the whole body swings up about the hind hooves, forelegs paddling
        if (rearW > 0.0F) {
            float theta = -0.87F * rearW;
            root.xRot = theta;
            root.y = ROOT_Y + HIND_Z * Mth.sin(theta);
            root.z = HIND_Z - HIND_Z * Mth.cos(theta);
            // Hind legs stay rigid with the body: the pivot is their hooves, so they stay planted.
            legs[2].xRot = 0.0F;
            legs[3].xRot = 0.0F;
            shins[2].xRot = 0.0F;
            shins[3].xRot = 0.0F;
            float paddle = Mth.sin(rear * TWO_PI * 2.2F);
            legs[0].xRot = rearW * (-0.61F + 0.35F * paddle);
            shins[0].xRot = rearW * (1.745F - 0.4F * paddle);
            legs[1].xRot = rearW * (-0.26F - 0.35F * paddle);
            shins[1].xRot = rearW * (1.4F + 0.4F * paddle);
            neck.xRot += 0.52F * rearW;
            head.xRot += 0.23F * rearW;
            tail.xRot += 0.44F * rearW;
            jaw.xRot += 0.2F * rearW;
            mane.xRot += 0.15F * rearW;
        }

        // --- flinch
        if (entity.hurtTime > 0) {
            float h = (entity.hurtTime - pt) / 10.0F;
            neck.xRot -= 0.18F * h;
            earLeft.xRot -= 0.4F * h;
            earRight.xRot -= 0.4F * h;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
                               int color) {
        root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }
}
