package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.pensieve.capture.IslandBounds;
import com.hogwartsmagic.pensieve.capture.Recording;
import com.hogwartsmagic.pensieve.capture.RecordedTrackSampler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The viewer of a recorded memory is a free ghost: they fly (the server makes them a flier), pass through blocks
 * (the {@code localNoclip} flag read by the common noclip mixin) and are held softly inside the island here, while the
 * server only snaps them back once they are well past it. The snap key swaps the view for the owner's own eyes;
 * the viewer stays where they were, with their input zeroed, and the view comes back to them on release.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class GhostCamera {
    /** Start taking the outward speed away this far inside the radius. */
    private static final double EASE_INSIDE = 1.0;

    private static boolean snap;
    /** The owner's last valid view, held when their track is gone. */
    private static float[] lastView;

    private GhostCamera() {
    }

    /** The viewer is a free ghost: building or playing a recorded memory. */
    public static boolean active() {
        int phase = PensieveClientState.phase();
        return (phase == PensieveClientState.PLAY || phase == PensieveClientState.BUILD)
                && PensieveClientState.recording() != null;
    }

    /** The view is the owner's eyes. Only ever while playing. */
    public static boolean snapped() {
        return snap && active() && PensieveClientState.phase() == PensieveClientState.PLAY;
    }

    /** Swap between flying freely and watching through the owner's eyes. A snap only works in PLAY. */
    public static void toggleSnap() {
        if (snap) {
            snap = false;
        } else if (active() && PensieveClientState.phase() == PensieveClientState.PLAY) {
            snap = true;
        }
    }

    /** Drop the snap (the memory ended or a new one starts). */
    static void release() {
        snap = false;
        lastView = null;
    }

    /** The owner's recorded track, or null. */
    static Recording.Track ownerTrack() {
        return RecordedActors.trackFor("owner");
    }

    /**
     * The owner's view at scene time {@code t}: {@code {x, y, z, yaw, pitch}} with the eye in world space. The puppet's
     * eye when it stands, else the recorded track's; the track is sampled once. Once the owner's track has ended the
     * last view is held rather than a stale puppet. Null if the owner has never been seen.
     */
    static float[] ownerView(float t) {
        BlockPos o = PensieveClientState.origin();
        if (o == null) return null;
        Recording.Track tr = ownerTrack();
        if (tr == null) return lastView;
        if (!RecordedTrackSampler.present(tr, t)) return lastView;
        float[] p = RecordedTrackSampler.pose(tr, t);
        float[] eye = MemoryActors.actorEye("owner");
        float[] v = eye != null
                ? new float[] {o.getX() + eye[0], o.getY() + eye[1], o.getZ() + eye[2], p[4], p[6]}
                : new float[] {o.getX() + p[0], (float) (o.getY() + p[1] + 1.62), o.getZ() + p[2], p[4], p[6]};
        lastView = v;
        return v;
    }

    /** Client tick: the soft island bounds, and the silver ghosts' motes. */
    public static void tick() {
        if (!active()) {
            snap = false;
            return;
        }
        SilverGhosts.tick();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        BlockPos o = PensieveClientState.origin();
        Recording r = PensieveClientState.recording();
        if (me == null || o == null || r == null || mc.isPaused()) return;
        double cx = o.getX() + 0.5;
        double cy = o.getY();
        double cz = o.getZ() + 0.5;
        double radius = r.radius();
        double dx = me.getX() - cx;
        double dy = me.getY() - cy;
        double dz = me.getZ() - cz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > radius - EASE_INSIDE && len > 1.0E-6) {
            // Take away the outward part of the velocity: the stop at the edge of the fray.
            Vec3 v = me.getDeltaMovement();
            double out = (v.x * dx + v.y * dy + v.z * dz) / len;
            if (out > 0) {
                me.setDeltaMovement(v.x - out * dx / len, v.y - out * dy / len, v.z - out * dz / len);
            }
        }
        if (len > radius) {
            // Every tick, so holding into the edge is a soft wall of tiny corrections and not a creep and a snap.
            double[] at = IslandBounds.clamp(me.getX(), me.getY(), me.getZ(), cx, cy, cz, radius);
            me.setPos(at[0], at[1], at[2]);
        }
    }

    /** While snapped the viewer stays where they were: no walking, no flying up or down. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!snapped()) return;
        Input input = event.getInput();
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }
}
