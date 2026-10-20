package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.pensieve.scene.HumanMotion;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import net.minecraft.client.model.geom.ModelPart;

import java.util.Map;

/**
 * Runs {@link HumanMotion} on a player actor's model, inside {@link ActorPoser#apply}: copies the six humanoid parts
 * out, runs a layer, copies them back. The order, from the bottom:
 * <ol>
 *   <li>the model's own {@code setupAnim} (the head follows {@code look}; the vanilla walk is off for a living
 *   actor, {@link MemoryActors} keeps its walk speed at 0);</li>
 *   <li>life (breathing, weight shifts, gait, seated slouch), damped on parts the scene keys in {@code limbs};</li>
 *   <li>the pose cue and the clip cue: whichever was cued later is applied last, so it wins on the parts both
 *   drive (a clip cued after {@code seated} claps with the arms and keeps the seated legs; a pose cued after a
 *   clip overrides it where the pose names parts);</li>
 *   <li>after {@code STAND_UP}, the legs are handed back to the life layer even if a seated pose is still in force;</li>
 *   <li>the {@code limbs} tracks, last, in {@link ActorPoser}.</li>
 * </ol>
 * Render thread only (shared scratch arrays). Nothing here throws into the render path: ActorPoser catches.
 */
final class ActorMotion {
    private static final float[] ROT = new float[HumanMotion.PARTS * 3];
    private static final float[] POS = new float[HumanMotion.PARTS * 3];
    private static final float[] LIFE_LEGS = new float[6];
    private static final ModelPart[] PARTS = new ModelPart[HumanMotion.PARTS];
    private static final float TO_DEG = (float) (180 / Math.PI);

    private ActorMotion() {
    }

    /** Life, then the pose and the clip in cue order. {@code pose} applies the actor's pose cue. */
    static void apply(Map<String, ModelPart> parts, ActorRuntime rt, float t, Runnable pose) {
        for (int p = 0; p < HumanMotion.PARTS; p++) {
            PARTS[p] = parts.get(HumanMotion.PART_NAMES.get(p));
            if (PARTS[p] == null) {
                pose.run();
                return;
            }
        }
        read();
        float headYaw = ROT[HumanMotion.HEAD * 3 + 1];
        float seat = rt.clips.seat(t, poseSeat(rt, t));
        if (rt.lifeOn()) {
            HumanMotion.Odometer odo = rt.odometer();
            HumanMotion.life(ROT, POS, rt.seed, t, rt.def.scale(), odo.speed(t), odo.phase(t), seat, rt.lifeGain);
            write();
        }
        System.arraycopy(ROT, HumanMotion.RIGHT_LEG * 3, LIFE_LEGS, 0, 6);

        float legs = 0;
        if (!rt.clips.isEmpty()) {
            int pi = PoseMath.activeCue(rt.poseCues, t);
            float poseT = pi < 0 ? Float.NEGATIVE_INFINITY : rt.poseCues.get(pi).t();
            if (rt.clips.cueTime(t) >= poseT) {
                pose.run();
                read();
                legs = rt.clips.apply(ROT, POS, t, rt.def.scale(), headYaw);
                write();
            } else {
                legs = rt.clips.apply(ROT, POS, t, rt.def.scale(), headYaw);
                write();
                pose.run();
            }
        } else {
            pose.run();
        }
        if (legs > 0) {
            read();
            for (int i = 0; i < 6; i++) {
                int k = HumanMotion.RIGHT_LEG * 3 + i;
                ROT[k] += (LIFE_LEGS[i] - ROT[k]) * legs;
            }
            write();
        }
    }

    /** How seated the pose in force says the actor is: a pose that swings a leg up past 60 degrees is a seat. */
    private static float poseSeat(ActorRuntime rt, float t) {
        int i = PoseMath.activeCue(rt.poseCues, t);
        if (i < 0 || i >= rt.poseSets.length) return 0;
        float now = seated(rt.poseSets[i]);
        float before = i > 0 ? seated(rt.poseSets[i - 1]) : 0;
        return PoseMath.lerp(before, now, PoseMath.weight(rt.poseCues.get(i), t, PoseMath.DEFAULT_POSE_BLEND));
    }

    private static float seated(PosePresets.Preset p) {
        if (p == null) return 0;
        float[] r = p.rot().get("right_leg");
        float[] l = p.rot().get("left_leg");
        return (r != null && r[0] <= -60) || (l != null && l[0] <= -60) ? 1 : 0;
    }

    private static void read() {
        for (int p = 0; p < HumanMotion.PARTS; p++) {
            ModelPart m = PARTS[p];
            ROT[p * 3] = m.xRot * TO_DEG;
            ROT[p * 3 + 1] = m.yRot * TO_DEG;
            ROT[p * 3 + 2] = m.zRot * TO_DEG;
            POS[p * 3] = m.x;
            POS[p * 3 + 1] = m.y;
            POS[p * 3 + 2] = m.z;
        }
    }

    private static void write() {
        for (int p = 0; p < HumanMotion.PARTS; p++) {
            ModelPart m = PARTS[p];
            m.xRot = ROT[p * 3] / TO_DEG;
            m.yRot = ROT[p * 3 + 1] / TO_DEG;
            m.zRot = ROT[p * 3 + 2] / TO_DEG;
            m.x = POS[p * 3];
            m.y = POS[p * 3 + 1];
            m.z = POS[p * 3 + 2];
        }
    }

    /** True when {@code rt} is a player actor whose model this layer drives. */
    static boolean drives(ActorRuntime rt) {
        return rt != null && rt.player && (rt.def.life() || !rt.clips.isEmpty());
    }

    /** For PartCheck: the anim cue names a player actor may use. */
    static boolean knownClip(SceneDef.Cue cue) {
        return HumanMotion.clipName(cue.name()) != null;
    }
}
