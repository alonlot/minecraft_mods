package com.hogwartsmagic.client;

import com.hogwartsmagic.spell.disable.IncarcerousRopeTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The body inside the Incarcerous ropes: arms pinned to the sides, legs hobbled to a shuffle, and every few
 * seconds a strain against the ropes (arms forcing outward, head thrown back and twisting) in step with the
 * coils stretching and the creak in {@link IncarcerousRopesClientState}. Eases in as the chest rope wraps and
 * out as the ropes fall away.
 *
 * <p>Called right after the renderer's {@code setupAnim} (ActorPoseMixin). Only humanoid models are posed, and
 * only parts {@code HumanoidModel.setupAnim} sets outright every frame, so nothing leaks into the next entity
 * sharing the model; the player model's sleeves, trousers and hat layer are re-copied from their limbs.
 */
public final class IncarcerousBoundPose {
    private static final float PINNED_ARM_X = 0.06f;
    private static final float PINNED_ARM_Z = 0.03f;

    private IncarcerousBoundPose() {
    }

    public static void apply(EntityModel<?> model, Entity entity, float partial) {
        if (IncarcerousRopesClientState.isEmpty() || !(model instanceof HumanoidModel<?> humanoid)
                || !(entity instanceof LivingEntity)) {
            return;
        }
        IncarcerousRopesClientState.Bind bind = IncarcerousRopesClientState.get(entity.getId());
        if (bind == null || Minecraft.getInstance().level == null) {
            return;
        }
        double now = Minecraft.getInstance().level.getGameTime() + partial;
        double t = bind.age(now);
        float hold = (float) IncarcerousRopeTimeline.hold(t, bind.sinceRelease(now));
        if (hold <= 0.0f) {
            return;
        }
        float strain = bind.released() ? 0.0f : (float) IncarcerousRopeTimeline.strain(bind.seed, t);
        float shake = (float) Math.sin(t * 2.9) * strain;

        // Arms: a swing (an attack, a cast) still shows, damped to the forearm room the ropes leave.
        pin(humanoid.rightArm, hold, PINNED_ARM_Z + 0.12f * strain + 0.02f * shake);
        pin(humanoid.leftArm, hold, -(PINNED_ARM_Z + 0.12f * strain - 0.02f * shake));

        // Legs: tied together, so a walk becomes a shuffle.
        hobble(humanoid.rightLeg, hold);
        hobble(humanoid.leftLeg, hold);

        // Head thrown back and twisting while straining.
        humanoid.head.xRot -= hold * strain * 0.32f;
        humanoid.head.yRot += hold * strain * 0.22f * (float) Math.sin(t * 0.9);
        humanoid.hat.copyFrom(humanoid.head);

        if (model instanceof PlayerModel<?> player) {
            player.rightSleeve.copyFrom(humanoid.rightArm);
            player.leftSleeve.copyFrom(humanoid.leftArm);
            player.rightPants.copyFrom(humanoid.rightLeg);
            player.leftPants.copyFrom(humanoid.leftLeg);
        }
    }

    private static void pin(ModelPart arm, float hold, float zRot) {
        float swing = Math.max(-0.35f, Math.min(0.2f, arm.xRot * 0.15f));
        arm.xRot = lerp(hold, arm.xRot, PINNED_ARM_X + swing);
        arm.yRot = lerp(hold, arm.yRot, 0.0f);
        arm.zRot = lerp(hold, arm.zRot, zRot);
    }

    private static void hobble(ModelPart leg, float hold) {
        leg.xRot = lerp(hold, leg.xRot, leg.xRot * 0.22f);
        leg.yRot = lerp(hold, leg.yRot, 0.0f);
    }

    private static float lerp(float f, float a, float b) {
        return a + (b - a) * f;
    }
}
