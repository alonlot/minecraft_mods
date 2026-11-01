package com.hogwartsmagic.client;

import com.hogwartsmagic.spell.mind.AvadaChargeFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The caster's body while the Killing Curse gathers: the wand arm lifts toward the target and keeps rising, high
 * over the line of aim, trembling harder as the charge builds, the off hand drawn back; on the cast it whips down
 * onto the line of aim and holds there a beat. Also where that wand tip is in the world, so the renderer puts the
 * gathering curse exactly on it.
 *
 * <p>Applied right after the renderer's {@code setupAnim} (ActorPoseMixin), to humanoid models only, and only to
 * parts {@code HumanoidModel.setupAnim} sets outright every frame, so nothing leaks into the next entity.
 */
public final class AvadaChargePose {
    /** How high above the line of aim the wand is held at full charge, in radians. */
    private static final float MAX_RAISE = 0.95f;
    private static final float MIN_RAISE = 0.15f;
    /** Player models are drawn at 15/16 scale. */
    private static final double PLAYER_SCALE = 0.9375;
    /** Shoulder to wand tip along the arm, blocks: ten pixels of arm then the wand. */
    private static final double ARM_REACH = 1.12;

    private AvadaChargePose() {
    }

    public static void apply(EntityModel<?> model, Entity entity, float partial) {
        if (AvadaChargeClientState.isEmpty() || !(model instanceof HumanoidModel<?> humanoid)
                || !(entity instanceof Player)) {
            return;
        }
        AvadaChargeClientState.Charge charge = AvadaChargeClientState.get(entity.getId());
        if (charge == null) {
            return;
        }
        double now = AvadaChargeClientState.now(partial);
        float weight = weight(charge, now);
        if (weight <= 0.0f) {
            return;
        }
        float raise = raise(charge, now);
        float tremble = tremble(charge, now);

        // Pointing along the gaze is the vanilla bow pose; lifted from there by the raise.
        float armX = humanoid.head.xRot - Mth.HALF_PI - raise + tremble;
        float armY = humanoid.head.yRot - 0.06f + tremble * 0.6f;
        humanoid.rightArm.xRot = Mth.lerp(weight, humanoid.rightArm.xRot, armX);
        humanoid.rightArm.yRot = Mth.lerp(weight, humanoid.rightArm.yRot, armY);
        humanoid.rightArm.zRot = Mth.lerp(weight, humanoid.rightArm.zRot, 0.0f);

        // The off hand drawn back and out, braced, a counterweight to the wand.
        float brace = weight * (charge.ended() ? 1.0f : 0.4f + 0.6f * charge.charge(now));
        humanoid.leftArm.xRot = Mth.lerp(brace, humanoid.leftArm.xRot, 0.35f);
        humanoid.leftArm.yRot = Mth.lerp(brace, humanoid.leftArm.yRot, 0.0f);
        humanoid.leftArm.zRot = Mth.lerp(brace, humanoid.leftArm.zRot, 0.42f);

        if (model instanceof PlayerModel<?> player) {
            player.rightSleeve.copyFrom(humanoid.rightArm);
            player.leftSleeve.copyFrom(humanoid.leftArm);
        }
    }

    /** How much of the pose is showing: eases in over a few ticks, holds through the cast, eases out after. */
    private static float weight(AvadaChargeClientState.Charge charge, double now) {
        if (!charge.ended()) {
            return smoothstep(0.0f, 4.0f, (float) charge.elapsed(now));
        }
        float since = (float) charge.sinceEnd(now);
        if (charge.endPhase() == AvadaChargeFx.FIRED) {
            return 1.0f - smoothstep(4.0f, AvadaChargeClientState.FIRED_TICKS, since);
        }
        return 1.0f - smoothstep(0.0f, 5.0f, since);
    }

    /** Radians above the line of aim. Rises with the charge; snaps to the aim when it fires. */
    private static float raise(AvadaChargeClientState.Charge charge, double now) {
        float held = MIN_RAISE + (MAX_RAISE - MIN_RAISE) * ease(charge.charge(now));
        if (charge.ended() && charge.endPhase() == AvadaChargeFx.FIRED) {
            // The strike: down past the line in two ticks, settling just on it.
            float since = (float) charge.sinceEnd(now);
            float strike = smoothstep(0.0f, 2.0f, since);
            return Mth.lerp(strike, held, -0.06f);
        }
        return held;
    }

    /** A shake that builds with the charge, as if the curse were fighting the hand that holds it. */
    private static float tremble(AvadaChargeClientState.Charge charge, double now) {
        if (charge.ended()) {
            return 0.0f;
        }
        float c = charge.charge(now);
        float t = (float) charge.elapsed(now);
        return c * c * 0.05f * (Mth.sin(t * 2.7f + charge.phase) + 0.6f * Mth.sin(t * 5.3f + charge.phase * 2.0f));
    }

    /**
     * Where the wand tip is this frame, in world space. Your own first-person view puts it where the held wand
     * shows on screen; everyone else (and you in third person) at the end of the posed arm.
     */
    public static Vec3 wandTip(LivingEntity caster, AvadaChargeClientState.Charge charge, float partial, boolean firstPerson) {
        double x = Mth.lerp(partial, caster.xo, caster.getX());
        double y = Mth.lerp(partial, caster.yo, caster.getY());
        double z = Mth.lerp(partial, caster.zo, caster.getZ());
        Vec3 look = caster.getViewVector(partial);
        if (firstPerson) {
            Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
            right = right.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
            Vec3 up = right.cross(look).normalize();
            return new Vec3(x, y + caster.getEyeHeight(), z).add(look.scale(0.62)).add(right.scale(0.30))
                    .add(up.scale(-0.20));
        }
        float bodyYaw = Mth.rotLerp(partial, caster.yBodyRotO, caster.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 bodyRight = new Vec3(-Mth.cos(bodyYaw), 0.0, -Mth.sin(bodyYaw));
        if (!(caster instanceof Player)) {
            // A boss's own wind-up pose holds the wand high over its head, pulled back.
            Vec3 bodyForward = new Vec3(-Mth.sin(bodyYaw), 0.0, Mth.cos(bodyYaw));
            return new Vec3(x, y + caster.getBbHeight() * 1.22, z).add(bodyRight.scale(0.32))
                    .add(bodyForward.scale(-0.12));
        }
        double now = AvadaChargeClientState.now(partial);
        double shoulderHeight = (caster.isCrouching() ? 18.8 : 22.0) / 16.0 * PLAYER_SCALE;
        Vec3 shoulder = new Vec3(x, y + shoulderHeight, z).add(bodyRight.scale(5.0 / 16.0 * PLAYER_SCALE));
        float pitch = caster.getViewXRot(partial) - raise(charge, now) * Mth.RAD_TO_DEG;
        float yaw = Mth.rotLerp(partial, caster.yHeadRotO, caster.yHeadRot);
        return shoulder.add(Vec3.directionFromRotation(pitch, yaw).scale(ARM_REACH));
    }

    /** True when {@code caster} is this client's own player seen from their own eyes. */
    public static boolean isOwnFirstPerson(Entity caster) {
        Minecraft minecraft = Minecraft.getInstance();
        return caster == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson();
    }

    static float ease(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
