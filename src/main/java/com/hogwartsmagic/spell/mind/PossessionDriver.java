package com.hogwartsmagic.spell.mind;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Drives a possessed mob by velocity (the old Imperio channel's movement, kept). Its AI is paused by the
 * possessed marker (LivingEntityStunMixin), so nothing fights the steering; hurtMarked sends the
 * motion to every watcher.
 */
final class PossessionDriver {
    private PossessionDriver() {
    }

    static void driveMob(Mob mob, PossessionSession session) {
        mob.setYBodyRot(session.yaw);
        if (mob.getTarget() != null) {
            MindControl.setTargetBypassing(mob, null);
        }
        mob.getNavigation().stop();
        Vec3 look = Vec3.directionFromRotation(0.0F, session.yaw).normalize();
        Vec3 left = new Vec3(look.z, 0.0, -look.x); // input strafe is positive to the left
        Vec3 desired = look.scale(session.forward).add(left.scale(session.strafe));
        if (desired.lengthSqr() > 1.0) {
            desired = desired.normalize();
        }
        double speed = Math.max(0.1, mob.getAttributeValue(Attributes.MOVEMENT_SPEED)) * 1.05;
        Vec3 motion = mob.getDeltaMovement();
        boolean idle = Math.abs(session.forward) < 0.01F && Math.abs(session.strafe) < 0.01F;
        Vec3 next = idle
                ? new Vec3(motion.x * 0.35, motion.y, motion.z * 0.35)
                : new Vec3(desired.x * speed + motion.x * 0.15, motion.y, desired.z * speed + motion.z * 0.15);
        if (session.jump && mob.onGround()) {
            next = new Vec3(next.x, 0.42, next.z);
        }
        mob.setDeltaMovement(next);
        mob.hurtMarked = true;
        mob.hasImpulse = true;
    }
}
