package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.pensieve.scene.CameraPose;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.hogwartsmagic.client.pensieve.PensieveTimeline.*;

/**
 * The directed camera for the whole dive. {@code PensieveCameraMixin} calls {@link #apply} at the tail of
 * {@code Camera#setup}, after vanilla has placed the camera at the player's eye; position, yaw and pitch are
 * replaced there. Roll and fov cannot be set on the camera itself, so {@link #roll} and {@link #fov} are read by
 * the existing ComputeCameraAngles and a ComputeFov handler in {@code ClientModEvents}.
 *
 * <p>Scene camera keys are relative to the set's origin (block corner). An empty camera track (or a scene that
 * failed to parse) leaves the viewer's own eye in charge.
 */
public final class PensieveCamera {
    private static float frameRoll;
    private static float fovTarget;
    /** 0 = vanilla fov, 1 = {@link #fovTarget}. */
    private static float fovMix;
    /** The camera's position last frame while the Pensieve drove it; null while it did not. */
    private static Vec3 lastPos;
    /** The camera jumped this frame (see {@link #consumeJump}). */
    private static boolean jumped;

    private PensieveCamera() {
    }

    public static boolean override() {
        return PensieveClientState.active();
    }

    /** Called at {@code Camera#setup} TAIL. */
    public static void apply(Camera camera, float partial) {
        frameRoll = 0;
        fovMix = 0;
        if (!override()) {
            lastPos = null;
            jumped = false;
            return;
        }
        int phase = PensieveClientState.phase();
        float pt = PensieveClientState.phaseTime(partial);
        Vec3 eye = camera.getPosition();
        double x = eye.x;
        double y = eye.y;
        double z = eye.z;
        float yaw = camera.getYRot();
        float pitch = camera.getXRot();
        float roll = 0;
        Vec3 surface = basinSurface();

        if (phase == PensieveClientState.SWALLOW) {
            if (surface != null) {
                Vec3 from = PensieveClientState.enterEye() != null ? PensieveClientState.enterEye() : eye;
                float k = swallowMove(pt);
                x = Mth.lerp(k, from.x, surface.x);
                y = Mth.lerp(k, from.y, surface.y);
                z = Mth.lerp(k, from.z, surface.z);
                yaw = PensieveClientState.enterYaw();
                pitch = lerp(PensieveClientState.enterPitch(), 90f, easeInOutSine(pt / SWALLOW_TICKS));
                // A slight twist as the liquid takes hold.
                roll = 8f * easeInQuad(pt / SWALLOW_TICKS);
                fovTarget = SWALLOW_FOV;
                fovMix = easeInQuad(pt / SWALLOW_TICKS);
            }
        } else if (phase == PensieveClientState.FALL) {
            if (surface != null) {
                x = surface.x;
                y = surface.y;
                z = surface.z;
            }
            yaw = PensieveClientState.enterYaw() + pt * 1.2f;
            pitch = 90f;
            roll = 8f + 4f * Mth.sin(pt * 0.13f) + 2f * Mth.sin(pt * 0.051f);
            fovTarget = SWALLOW_FOV;
            fovMix = 1;
        } else if (phase == PensieveClientState.BUILD || phase == PensieveClientState.PLAY) {
            float t = phase == PensieveClientState.BUILD ? 0 : PensieveClientState.sceneTime(partial);
            CameraPose p = trackPose(t);
            if (p != null) {
                x = p.x();
                y = p.y();
                z = p.z();
                yaw = p.yaw();
                pitch = p.pitch();
                roll = p.roll();
                fovTarget = p.fov();
                fovMix = 1;
            }
            // A recorded memory's viewer who snapped sees through the owner's own eyes, wherever the owner goes.
            if (phase == PensieveClientState.PLAY && GhostCamera.snapped()) {
                Vec3 oe = GhostCamera.ownerEye(t);
                float[] look = GhostCamera.ownerLook(t);
                if (oe != null && look != null) {
                    x = oe.x;
                    y = oe.y;
                    z = oe.z;
                    yaw = look[0];
                    pitch = look[1];
                    roll = 0;
                    fovTarget = 70f;
                    fovMix = 1;
                }
            }
            // Landing in the memory: the camera settles down onto its first shot. Not on a chain's hand-over, which
            // fades in from black on the next shot as it is.
            y += settleHeight(PensieveClientState.revealTime(partial), PensieveClientState.handedOver());
        } else if (phase == PensieveClientState.EXIT) {
            float rt = PensieveClientState.returnTime(partial);
            if (rt >= 0) {
                // Back at the basin: rise out of the silver surface to the eye.
                if (surface != null) {
                    float k = returnMove(rt);
                    x = Mth.lerp(k, surface.x, eye.x);
                    y = Mth.lerp(k, surface.y, eye.y);
                    z = Mth.lerp(k, surface.z, eye.z);
                    pitch = lerp(90f, camera.getXRot(), easeInOutSine(rt / RETURN_TICKS));
                    roll = 8f * (1 - k);
                    fovTarget = SWALLOW_FOV;
                    fovMix = 1 - k;
                }
            } else {
                CameraPose p = trackPose(PensieveClientState.sceneTime(partial));
                if (p != null) {
                    x = p.x();
                    y = p.y();
                    z = p.z();
                    yaw = p.yaw();
                    pitch = p.pitch();
                    roll = p.roll();
                    fovTarget = p.fov();
                    fovMix = 1;
                }
                // The pull upward out of the memory.
                y += 1.5f * easeInQuad(pt / EXIT_TICKS);
            }
        } else if (phase == PensieveClientState.YANK) {
            CameraPose p = trackPose(PensieveClientState.sceneTime(partial));
            if (p != null) {
                x = p.x();
                y = p.y();
                z = p.z();
                yaw = p.yaw();
                pitch = p.pitch();
                roll = p.roll();
                fovTarget = p.fov();
                fovMix = 1;
            }
            float a = 1 - clamp01(pt / YANK_TICKS);
            float c = PensieveClientState.clock(partial);
            x += Mth.sin(c * 5.1f) * 0.25f * a;
            y += Mth.sin(c * 4.3f + 1f) * 0.2f * a;
            z += Mth.sin(c * 5.7f + 2f) * 0.25f * a;
            pitch += Mth.sin(c * 4.7f) * 5f * a;
            roll += Mth.sin(c * 6.3f) * 10f * a;
        }

        float s = PensieveClientState.shudder(partial);
        if (s > 0) {
            float c = PensieveClientState.clock(partial);
            x += Mth.sin(c * 2.9f) * 0.06f * s;
            y += Mth.sin(c * 3.7f + 1f) * 0.05f * s;
            z += Mth.sin(c * 3.3f + 2f) * 0.06f * s;
            roll += Mth.sin(c * 4.1f) * 2f * s;
        }

        frameRoll = roll;
        camera.setRotation(yaw, Mth.clamp(pitch, -90f, 90f), 0f);
        Vec3 at = new Vec3(x, y, z);
        ((PensieveCameraAccess) camera).hogwartsmagic$setPosition(at);
        if (lastPos != null && jumped(lastPos, at)) jumped = true;
        lastPos = at;
    }

    private static boolean jumped(Vec3 from, Vec3 to) {
        return cameraJumped(to.x - from.x, to.y - from.y, to.z - from.z);
    }

    /**
     * True once after a frame whose camera jumped (a cut, the landing, a hand-over). Read by
     * {@code PensieveOcclusionGraphMixin} in that same frame's culling, so it rebuilds the visible sections around
     * the new shot at once instead of drawing a frame or two from the old shot's list.
     */
    public static boolean consumeJump() {
        boolean j = jumped;
        jumped = false;
        return j;
    }

    /** Where the scene camera is at scene time {@code t}, in world space; null for a scene with no camera track. */
    static Vec3 shotPosition(float t) {
        CameraPose p = trackPose(t);
        return p == null ? null : new Vec3(p.x(), p.y(), p.z());
    }

    /** This frame's roll, for the ComputeCameraAngles handler. */
    public static float roll() {
        return frameRoll;
    }

    /** This frame's fov, blended from the fov vanilla would have used. */
    public static double fov(double vanilla) {
        if (fovMix <= 0) return vanilla;
        return vanilla + (fovTarget - vanilla) * fovMix;
    }

    /** 0.2 above the basin's centre: just over the silver surface. */
    private static Vec3 basinSurface() {
        BlockPos b = PensieveClientState.basin();
        return b == null ? null : Vec3.atCenterOf(b).add(0, 0.2, 0);
    }

    /** The scene camera at {@code t} in world space, or null to leave the viewer's eye in charge. */
    private static CameraPose trackPose(float t) {
        SceneDef scene = PensieveClientState.scene();
        BlockPos o = PensieveClientState.origin();
        if (scene == null || o == null || scene.camera() == null || scene.camera().isEmpty()) return null;
        CameraPose p = scene.camera().sample(t, PensieveCamera::actorEye);
        return new CameraPose(o.getX() + p.x(), o.getY() + p.y(), o.getZ() + p.z(), p.yaw(), p.pitch(), p.roll(),
                p.fov());
    }

    /** Origin-relative eye of a scene actor, or null when there is no such puppet. */
    private static float[] actorEye(String actorId) {
        return MemoryActors.actorEye(actorId);
    }
}
