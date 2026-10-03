package com.hogwartsmagic.spell.mind;

import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.phys.Vec3;

/**
 * Drives a possessed mob by velocity (the old Imperio channel's movement, kept). Its AI is paused by the
 * possessed marker (LivingEntityStunMixin), so nothing fights the steering; hurtMarked sends the
 * motion to every watcher.
 */
final class PossessionDriver {
    private PossessionDriver() {
    }

    /** Mobs that fly under their own power (bats, bees, parrots, allays, vexes, phantoms, ghasts, blazes, owls, dragons, ...). */
    static boolean canFly(Mob mob) {
        return mob instanceof FlyingAnimal || mob instanceof FlyingMob || mob instanceof Blaze || mob instanceof Vex
                || mob instanceof com.hogwartsmagic.entity.HungarianHorntailEntity
                || mob.getMoveControl() instanceof FlyingMoveControl
                || mob.getNavigation() instanceof FlyingPathNavigation;
    }

    static void driveMob(Mob mob, PossessionSession session, boolean stunned) {
        mob.setYBodyRot(session.yaw);
        if (mob.getTarget() != null) {
            MindControl.setTargetBypassing(mob, null);
        }
        mob.getNavigation().stop();
        Vec3 look = Vec3.directionFromRotation(0.0F, session.yaw).normalize();
        Vec3 left = new Vec3(look.z, 0.0, -look.x); // input strafe is positive to the left
        float forward = stunned ? 0.0F : session.forward;
        float strafe = stunned ? 0.0F : session.strafe;
        Vec3 desired = look.scale(forward).add(left.scale(strafe));
        if (desired.lengthSqr() > 1.0) {
            desired = desired.normalize();
        }
        boolean flyer = canFly(mob);
        boolean airborne = flyer && !mob.onGround();
        if (airborne && !stunned) {
            // A flyer in the air steers in 3D: forward follows where the caster looks (look down to descend),
            // jump climbs, and with no input it hovers. On the ground it takes off with jump like any hop.
            Vec3 look3 = Vec3.directionFromRotation(session.pitch, session.yaw).normalize();
            Vec3 want = look3.scale(forward).add(left.scale(strafe));
            if (want.lengthSqr() > 1.0) {
                want = want.normalize();
            }
            double flySpeed = Math.min(0.4, Math.max(0.1, mob.getAttributeValue(Attributes.MOVEMENT_SPEED))) * 1.05;
            Vec3 current = mob.getDeltaMovement();
            double climb = session.jump ? 0.3 : 0.0;
            boolean still = Math.abs(forward) < 0.01F && Math.abs(strafe) < 0.01F;
            double y = still ? current.y * 0.2 + climb : want.y * flySpeed + climb;
            Vec3 flying = still
                    ? new Vec3(current.x * 0.35, y, current.z * 0.35)
                    : new Vec3(want.x * flySpeed + current.x * 0.15, y, want.z * flySpeed + current.z * 0.15);
            mob.setDeltaMovement(flying);
            mob.hurtMarked = true;
            mob.hasImpulse = true;
            mob.fallDistance = 0.0F;
            return;
        }
        double speed = Math.max(0.1, mob.getAttributeValue(Attributes.MOVEMENT_SPEED)) * 1.05;
        Vec3 motion = mob.getDeltaMovement();
        boolean idle = Math.abs(forward) < 0.01F && Math.abs(strafe) < 0.01F;
        Vec3 next = idle
                ? new Vec3(motion.x * 0.35, motion.y, motion.z * 0.35)
                : new Vec3(desired.x * speed + motion.x * 0.15, motion.y, desired.z * speed + motion.z * 0.15);
        if (!stunned && session.jump && mob.onGround()) {
            next = new Vec3(next.x, 0.42, next.z);
        }
        mob.setDeltaMovement(next);
        mob.hurtMarked = true;
        mob.hasImpulse = true;
    }
}
