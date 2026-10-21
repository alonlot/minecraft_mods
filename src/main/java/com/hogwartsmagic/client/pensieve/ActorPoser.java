package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.pensieve.scene.PartTrack;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.mojang.logging.LogUtils;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Poses a Pensieve actor (or the body left at the basin) limb by limb, right after the vanilla renderer has run
 * the model own {@code setupAnim}. In order: the named creature animation in force, for player actors the life
 * layer and the player clips ({@link ActorMotion}), the pose cue in force (blended in over its {@code blend} ticks,
 * from the previous pose; ordered against the clip by cue time), then the limb tracks (added to what is there, or
 * replacing it in {@code "mode":"set"}).
 *
 * <p>A model is shared by every entity its renderer draws, and vanilla only resets the rotations it sets, not
 * every offset. So {@link #begin} snapshots every part of the model before {@code setupAnim} and {@link #end}
 * puts the snapshot back, leaving the model exactly as the next (real) entity expects it. {@link #begin} also
 * undoes a snapshot left over by a render that threw, so a failure cannot leak into the next entity.
 *
 * <p>Nothing here throws into the render path.
 */
public final class ActorPoser {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Vector3f SCRATCH = new Vector3f();
    private static final String BODY_POSE = "bent_over_basin";
    /** x, y, z, xRot, yRot, zRot, xScale, yScale, zScale per part. */
    private static final int STRIDE = 9;

    private static List<ModelPart> savedParts;
    private static float[] saved = new float[0];
    private static Entity armedFor;
    private static boolean warned;

    private ActorPoser() {
    }

    /** True for entities this class poses: scene actors and the surrogate that stands for the basin body. */
    public static boolean poses(Entity entity) {
        return entity instanceof PensieveBodyRenderer.Surrogate || MemoryActors.isActor(entity);
    }

    // ---- Snapshot ---------------------------------------------------------------------------------------------

    /** Before {@code setupAnim}: put back a stale snapshot, and take a new one when {@code entity} is posed. */
    public static void begin(EntityModel<?> model, Entity entity) {
        try {
            end();
            if (!poses(entity)) return;
            List<ModelPart> parts = ModelPartIndex.allParts(model);
            if (parts.isEmpty()) return;
            int need = parts.size() * STRIDE;
            if (saved.length < need) saved = new float[need];
            for (int i = 0; i < parts.size(); i++) {
                ModelPart p = parts.get(i);
                int o = i * STRIDE;
                saved[o] = p.x;
                saved[o + 1] = p.y;
                saved[o + 2] = p.z;
                saved[o + 3] = p.xRot;
                saved[o + 4] = p.yRot;
                saved[o + 5] = p.zRot;
                saved[o + 6] = p.xScale;
                saved[o + 7] = p.yScale;
                saved[o + 8] = p.zScale;
            }
            savedParts = parts;
            armedFor = entity;
        } catch (Throwable t) {
            savedParts = null;
            armedFor = null;
            warn(t);
        }
    }

    /** After the render (or before the next one): restore the snapshot, if there is one. */
    public static void end() {
        List<ModelPart> parts = savedParts;
        savedParts = null;
        armedFor = null;
        if (parts == null) return;
        for (int i = 0; i < parts.size(); i++) {
            ModelPart p = parts.get(i);
            int o = i * STRIDE;
            p.x = saved[o];
            p.y = saved[o + 1];
            p.z = saved[o + 2];
            p.xRot = saved[o + 3];
            p.yRot = saved[o + 4];
            p.zRot = saved[o + 5];
            p.xScale = saved[o + 6];
            p.yScale = saved[o + 7];
            p.zScale = saved[o + 8];
        }
    }

    // ---- Posing -----------------------------------------------------------------------------------------------

    /** After {@code setupAnim}: pose the model for {@code entity}, if a snapshot was taken for it. */
    public static void apply(EntityModel<?> model, Entity entity, float partial) {
        if (armedFor != entity) return;
        try {
            Map<String, ModelPart> parts = ModelPartIndex.of(model);
            ActorRuntime rt = MemoryActors.runtimeFor(entity);
            if (rt != null) {
                float t = PensieveClientState.sceneTime(partial);
                applyAnimation(model, rt, t);
                // A broom rider: the rider layer (BroomFlight) is the base the rest builds on.
                rt.broomSeat = model instanceof HumanoidModel<?> ? MemoryActors.broomSeat(rt, t) : null;
                rt.framePartial = partial;
                if (rt.broomSeat != null) ActorMotion.rider(parts, rt.broomSeat);
                if (model instanceof HumanoidModel<?> && ActorMotion.drives(rt)) {
                    ActorMotion.apply(parts, rt, t, () -> applyPose(parts, rt, t));
                } else {
                    applyPose(parts, rt, t);
                }
                applyLimbs(parts, rt, t);
            } else {
                PosePresets.Preset body = PosePresets.get(BODY_POSE);
                if (body != null) blendPreset(parts, body, null, 1f);
            }
            syncLayers(model, rt);
        } catch (Throwable t) {
            warn(t);
        }
    }

    private static void applyAnimation(EntityModel<?> model, ActorRuntime rt, float t) {
        if (!(model instanceof HierarchicalModel<?> hm)) return;
        int i = PoseMath.activeCue(rt.animCues, t);
        if (i < 0) return;
        rt.appliedAnimCue = i;
        AnimationDefinition def = rt.animDefs[i];
        if (def == null) return;
        SceneDef.Cue cue = rt.animCues.get(i);
        long elapsedMs = (long) ((t - cue.t()) * 50f);
        KeyframeAnimations.animate(hm, def, elapsedMs, PoseMath.weight(cue, t, 0f), SCRATCH);
    }

    private static void applyPose(Map<String, ModelPart> parts, ActorRuntime rt, float t) {
        int i = PoseMath.activeCue(rt.poseCues, t);
        if (i < 0 || i >= rt.poseSets.length) return;
        rt.appliedPoseCue = i;
        PosePresets.Preset now = rt.poseSets[i];
        PosePresets.Preset before = i > 0 ? rt.poseSets[i - 1] : null;
        if (now == null && before == null) return;
        float w = PoseMath.weight(rt.poseCues.get(i), t, PoseMath.DEFAULT_POSE_BLEND);
        blendPreset(parts, now, before, w);
    }

    /**
     * Blend {@code before} (null: the model own state) into {@code now} (null: the model own state) by
     * {@code w}. Rotations are absolute, offsets are added to the part position.
     */
    private static void blendPreset(Map<String, ModelPart> parts, PosePresets.Preset now, PosePresets.Preset before,
                                    float w) {
        if (now != null) {
            for (String name : now.rot().keySet()) blendRot(parts, name, now, before, w);
            for (String name : now.offset().keySet()) blendOffset(parts, name, now, before, w);
        }
        if (before != null) {
            for (String name : before.rot().keySet()) {
                if (now == null || !now.rot().containsKey(name)) blendRot(parts, name, now, before, w);
            }
            for (String name : before.offset().keySet()) {
                if (now == null || !now.offset().containsKey(name)) blendOffset(parts, name, now, before, w);
            }
        }
    }

    private static void blendRot(Map<String, ModelPart> parts, String name, PosePresets.Preset now,
                                 PosePresets.Preset before, float w) {
        ModelPart p = parts.get(name);
        if (p == null) return;
        float[] next = now == null ? null : now.rot().get(name);
        float[] prev = before == null ? null : before.rot().get(name);
        for (int c = 0; c < 3; c++) {
            float base = (float) Math.toDegrees(rotation(p, c));
            float deg = PoseMath.blendPose(base, comp(prev, c), comp(next, c), w);
            setRotation(p, c, (float) Math.toRadians(deg));
        }
    }

    private static void blendOffset(Map<String, ModelPart> parts, String name, PosePresets.Preset now,
                                    PosePresets.Preset before, float w) {
        ModelPart p = parts.get(name);
        if (p == null) return;
        float[] next = now == null ? null : now.offset().get(name);
        float[] prev = before == null ? null : before.offset().get(name);
        for (int c = 0; c < 3; c++) {
            float delta = PoseMath.blendPose(0f, comp(prev, c), comp(next, c), w);
            setPosition(p, c, position(p, c) + delta);
        }
    }

    private static void applyLimbs(Map<String, ModelPart> parts, ActorRuntime rt, float t) {
        for (Map.Entry<String, PartTrack> e : rt.def.limbs().entrySet()) {
            ModelPart p = parts.get(e.getKey());
            if (p == null) p = parts.get(PoseMath.snake(e.getKey()));
            if (p == null) continue;
            PartTrack track = e.getValue();
            if (!track.rot().isEmpty()) {
                float[] v = track.rot().sample(t);
                for (int c = 0; c < Math.min(3, v.length); c++) {
                    setRotation(p, c, PoseMath.limb(rotation(p, c), (float) Math.toRadians(v[c]), track.set()));
                }
            }
            if (!track.offset().isEmpty()) {
                float[] v = track.offset().sample(t);
                for (int c = 0; c < Math.min(3, v.length); c++) {
                    setPosition(p, c, PoseMath.limb(position(p, c), v[c], track.set()));
                }
            }
        }
    }

    /**
     * The outer skin layers were copied from their base parts inside {@code setupAnim}, before any of the above;
     * copy them again so a posed arm drags its sleeve along. A layer the scene limbs address by name is left alone.
     */
    private static void syncLayers(EntityModel<?> model, ActorRuntime rt) {
        if (!(model instanceof HumanoidModel<?> hm)) return;
        sync(hm.hat, hm.head, "hat", rt);
        if (model instanceof PlayerModel<?> pm) {
            sync(pm.jacket, pm.body, "jacket", rt);
            sync(pm.leftSleeve, pm.leftArm, "left_sleeve", rt);
            sync(pm.rightSleeve, pm.rightArm, "right_sleeve", rt);
            sync(pm.leftPants, pm.leftLeg, "left_pants", rt);
            sync(pm.rightPants, pm.rightLeg, "right_pants", rt);
        }
    }

    private static void sync(ModelPart layer, ModelPart base, String name, ActorRuntime rt) {
        if (layer == null || base == null) return;
        if (rt != null && rt.def.limbs().containsKey(name)) return;
        layer.copyFrom(base);
    }

    // ---- Component access (0 = x, 1 = y, 2 = z) ---------------------------------------------------------------

    private static float comp(float[] v, int c) {
        return v != null && c < v.length ? v[c] : Float.NaN;
    }

    private static float rotation(ModelPart p, int c) {
        return c == 0 ? p.xRot : c == 1 ? p.yRot : p.zRot;
    }

    private static void setRotation(ModelPart p, int c, float v) {
        if (c == 0) p.xRot = v;
        else if (c == 1) p.yRot = v;
        else p.zRot = v;
    }

    private static float position(ModelPart p, int c) {
        return c == 0 ? p.x : c == 1 ? p.y : p.z;
    }

    private static void setPosition(ModelPart p, int c, float v) {
        if (c == 0) p.x = v;
        else if (c == 1) p.y = v;
        else p.z = v;
    }

    private static void warn(Throwable t) {
        if (warned) return;
        warned = true;
        LOGGER.warn("Pensieve: posing failed, further failures are not logged: {}", t.toString());
    }
}
