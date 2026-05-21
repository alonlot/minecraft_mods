package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.AcromantulaEntity;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class AcromantulaModel<T extends Entity> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "acromantula"), "main");

    public final ModelPart root;
    public final ModelPart head;
    public final ModelPart thorax;
    public final ModelPart abdomen;
    public final ModelPart leftFang;
    public final ModelPart rightFang;
    public final ModelPart[][] legs = new ModelPart[8][2];

    public AcromantulaModel(ModelPart root) {
        this.root = root;
        this.thorax = root.getChild("thorax");
        this.abdomen = root.getChild("abdomen");
        this.head = root.getChild("head");
        this.leftFang = this.head.getChild("left_fang");
        this.rightFang = this.head.getChild("right_fang");
        for (int i = 0; i < 8; i++) {
            legs[i][0] = root.getChild("leg" + i + "_upper");
            legs[i][1] = legs[i][0].getChild("leg" + i + "_lower");
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("thorax", CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-5.0F, -4.0F, -6.0F, 10.0F, 8.0F, 12.0F),
                PartPose.offset(0.0F, 18.0F, 0.0F));

        root.addOrReplaceChild("abdomen", CubeListBuilder.create()
                        .texOffs(0, 20)
                        .addBox(-7.0F, -6.0F, 0.0F, 14.0F, 12.0F, 18.0F),
                PartPose.offsetAndRotation(0.0F, 18.0F, 5.0F, -0.2F, 0.0F, 0.0F));

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(64, 20)
                        .addBox(-4.0F, -4.0F, -6.0F, 8.0F, 8.0F, 6.0F),
                PartPose.offset(0.0F, 18.0F, -6.0F));

        head.addOrReplaceChild("left_fang", CubeListBuilder.create()
                        .texOffs(92, 20)
                        .addBox(-1.0F, 0.0F, -1.5F, 2.0F, 6.0F, 3.0F),
                PartPose.offsetAndRotation(2.0F, 4.0F, -5.0F, 0.1F, 0.1F, 0.1F));

        head.addOrReplaceChild("right_fang", CubeListBuilder.create()
                        .texOffs(92, 20).mirror()
                        .addBox(-1.0F, 0.0F, -1.5F, 2.0F, 6.0F, 3.0F),
                PartPose.offsetAndRotation(-2.0F, 4.0F, -5.0F, 0.1F, -0.1F, -0.1F));

        for (int i = 0; i < 8; i++) {
            boolean isLeft = i < 4;
            int legPair = i % 4;
            float zOffset = -3.0F + (legPair * 3.0F);
            float xOffset = isLeft ? 5.0F : -5.0F;

            float baseRot = isLeft ? (float) Math.PI / 2F : -(float) Math.PI / 2F;
            float sweep = switch (legPair) {
                case 0 ->  (float) Math.PI / 4.0F;
                case 1 ->  (float) Math.PI / 9.0F;
                case 2 -> -(float) Math.PI / 9.0F;
                case 3 -> -(float) Math.PI / 4.0F;
                default -> 0F;
            };
            float yRotation = baseRot + (isLeft ? sweep : -sweep);

            float xRotUpper = 0.0F;
            float xRotLower = -0.5F;
            if (legPair == 0 || legPair == 3) {
                xRotLower = -0.4F;
            }

            PartDefinition upper = root.addOrReplaceChild("leg" + i + "_upper", CubeListBuilder.create()
                            .texOffs(0, 52)
                            .addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 16.0F),
                    PartPose.offsetAndRotation(xOffset, 18.0F, zOffset, xRotUpper, yRotation, 0.0F));

            upper.addOrReplaceChild("leg" + i + "_lower", CubeListBuilder.create()
                            .texOffs(40, 52)
                            .addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 20.0F),
                    PartPose.offsetAndRotation(0.0F, 0.0F, 16.0F, xRotLower, 0.0F, 0.0F));
        }

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);

        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;

        // Idle breathing / sway.
        thorax.xRot = Mth.sin(ageInTicks * 0.05F) * 0.02F;
        abdomen.xRot = -0.1F + Mth.sin(ageInTicks * 0.05F) * 0.03F;
        leftFang.xRot += Mth.sin(ageInTicks * 0.1F) * 0.05F;
        rightFang.xRot += Mth.sin(ageInTicks * 0.1F) * 0.05F;

        // 4-beat tarantula gait: diagonal pairs move in unison (L0+R1+L2+R3 vs R0+L1+R2+L3).
        applyWalkGait(limbSwing, limbSwingAmount);

        // State-driven overlays.
        // Use if/else (not switch-on-enum) to avoid the synthetic $1 helper class that the
        // Java compiler generates for enum switches — Forge/Mixin dev environments have been
        // observed to fail to load that helper, producing a NoClassDefFoundError at render time.
        if (entity instanceof AcromantulaEntity spider) {
            AcromantulaEntity.AttackState state = spider.getAttackState();
            int t = spider.getClientStateTicks();

            if (state == AcromantulaEntity.AttackState.ALERT) {
                applyAlert(t);
            } else if (state == AcromantulaEntity.AttackState.LUNGE_WINDUP) {
                applyLungeWindup(t);
            } else if (state == AcromantulaEntity.AttackState.LUNGE_STRIKE) {
                applyLungeStrike(t);
            } else if (state == AcromantulaEntity.AttackState.WEB_SPIT) {
                applyWebSpit(t);
            }
            // AttackState.NONE: idle pose already applied above.

            if (spider.isClimbing()) {
                thorax.xRot += 0.3F;
                abdomen.xRot += 0.3F;
                for (int i = 0; i < 8; i++) legs[i][0].xRot -= 0.1F;
            }

            if (spider instanceof LivingEntity living && living.isDeadOrDying()) {
                applyDeathCurl(living.deathTime);
            }
        }
    }

    private void applyWalkGait(float limbSwing, float limbSwingAmount) {
        for (int i = 0; i < 8; i++) {
            boolean isLeft = i < 4;
            int legPair = i % 4;

            // Group A: L0, R1, L2, R3   Group B: R0, L1, R2, L3.
            boolean groupA = (isLeft && (legPair % 2 == 0)) || (!isLeft && (legPair % 2 == 1));
            float phase = groupA ? 0.0F : Mth.PI;

            float swingSpeed = 0.55F;
            float swing = Mth.cos(limbSwing * swingSpeed + phase) * 0.45F * limbSwingAmount;
            legs[i][0].yRot += isLeft ? swing : -swing;

            float lift = Math.max(0F, Mth.sin(limbSwing * swingSpeed + phase)) * limbSwingAmount;
            legs[i][0].xRot -= lift * 0.55F;
            legs[i][1].xRot += lift * 0.30F;
        }
    }

    /** Brief warning: front legs lift, body rears slightly, fangs flare. */
    private void applyAlert(int t) {
        float p = clamp01((float) t / (float) AcromantulaEntity.ALERT_DURATION);
        float peak = Mth.sin(p * Mth.PI);
        thorax.xRot -= 0.20F * peak;
        head.xRot -= 0.35F * peak;
        abdomen.xRot += 0.15F * peak;
        leftFang.xRot += 0.45F * peak;
        rightFang.xRot += 0.45F * peak;
        // Front pair (legPair == 0): lift high.
        for (int i = 0; i < 8; i++) {
            if (i % 4 == 0) {
                legs[i][0].xRot -= 0.85F * peak;
                legs[i][1].xRot += 0.35F * peak;
            }
        }
    }

    /** Pull back, fangs open wider, body tilts back. */
    private void applyLungeWindup(int t) {
        float p = clamp01((float) t / (float) AcromantulaEntity.LUNGE_WINDUP_DURATION);
        float eased = easeOut(p);
        thorax.xRot -= 0.25F * eased;
        head.xRot -= 0.30F * eased;
        abdomen.xRot += 0.20F * eased;
        leftFang.xRot += 0.55F * eased;
        rightFang.xRot += 0.55F * eased;
        for (int i = 0; i < 8; i++) {
            if (i % 4 == 0) {
                legs[i][0].xRot -= 1.10F * eased;
                legs[i][1].xRot += 0.45F * eased;
            }
        }
    }

    /** Snap forward; sharp leading edge then settle. */
    private void applyLungeStrike(int t) {
        float p = clamp01((float) t / (float) AcromantulaEntity.LUNGE_STRIKE_DURATION);
        float strike = p < 0.25F ? p / 0.25F : 1.0F - (p - 0.25F) / 0.75F;
        thorax.xRot += 0.45F * strike;
        head.xRot += 0.35F * strike;
        abdomen.xRot -= 0.15F * strike;
        // Fangs slam together: pull them back together (close them) on impact, then relax.
        float fangBite = Mth.sin(p * Mth.PI);
        leftFang.xRot += 0.6F - 0.5F * fangBite;
        rightFang.xRot += 0.6F - 0.5F * fangBite;
        // Front legs sweep forward.
        for (int i = 0; i < 8; i++) {
            if (i % 4 == 0) {
                legs[i][0].xRot += 0.55F * strike;
                legs[i][1].xRot -= 0.25F * strike;
            }
        }
    }

    /** Rear abdomen up, head dips forward, brief pulse on fire tick. */
    private void applyWebSpit(int t) {
        float p = clamp01((float) t / (float) AcromantulaEntity.WEB_SPIT_DURATION);
        float build = Mth.clamp(p / 0.55F, 0.0F, 1.0F); // ramp up
        float fire = Mth.clamp((p - 0.58F) / 0.10F, 0.0F, 1.0F);
        float fireBurst = fire * (1.0F - fire) * 4.0F; // bell at fire tick
        abdomen.xRot -= 0.55F * build;
        thorax.xRot += 0.18F * build;
        head.xRot += 0.20F * build + 0.10F * fireBurst;
        // Whole body pulses forward on fire.
        thorax.z += fireBurst * 0.3F;
        abdomen.z += fireBurst * 0.3F;
        head.z += fireBurst * 0.3F;
    }

    /** Death: legs curl inward, body sinks. Driven by living.deathTime (0..20 vanilla). */
    private void applyDeathCurl(int deathTime) {
        float p = clamp01(deathTime / 20.0F);
        for (int i = 0; i < 8; i++) {
            legs[i][0].xRot -= 1.0F * p;
            legs[i][1].xRot += 0.8F * p;
        }
        thorax.y += 1.5F * p;
        abdomen.y += 1.5F * p;
        head.y += 1.5F * p;
    }

    private static float clamp01(float v) {
        return Mth.clamp(v, 0.0F, 1.0F);
    }

    private static float easeOut(float t) {
        return 1.0F - (1.0F - t) * (1.0F - t);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
