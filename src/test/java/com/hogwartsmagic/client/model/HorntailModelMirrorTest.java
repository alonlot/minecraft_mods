package com.hogwartsmagic.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hogwartsmagic.entity.MemoryHorntailPose;
import com.hogwartsmagic.pensieve.scene.DragonFlight;
import com.hogwartsmagic.pensieve.scene.HorntailCues;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real model, baked from createBodyLayer and posed as MemoryHorntailModel poses a memory dragon, against
 * the Python mirror's model pose (dragon_engine.model_snout in horntail_cases.json): the mouth the fire leaves from,
 * through the whole posed neck chain, for every case (perched, a roar and its cross-fades, a takeoff, a hover, a
 * cruise, a climb, a glide, a banked turn, an air breath, the stagger).
 */
class HorntailModelMirrorTest {
    @Test
    void modelSnoutMatchesTheMirror() throws Exception {
        ModelPart root = HungarianHorntailModel.createBodyLayer().bakeRoot();
        MemoryHorntailModel model = new MemoryHorntailModel(root);
        JsonObject all = JsonParser.parseString(Files.readString(
                Path.of("build_scripts/pensieve_scenes/horntail_cases.json"))).getAsJsonObject();
        Map<String, SceneDef> scenes = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : all.getAsJsonObject("scenes").entrySet()) {
            scenes.put(e.getKey(), SceneParser.parse(e.getKey(), e.getValue().getAsJsonObject()));
        }
        double worst = 0, worstDir = 0, worstWorld = 0;
        int n = 0;
        for (JsonElement ce : all.getAsJsonArray("cases")) {
            JsonObject c = ce.getAsJsonObject();
            SceneDef.ActorDef h = scenes.get(c.get("scene").getAsString()).actors().get(0);
            float t = c.get("t").getAsFloat();
            // As MemoryHorntailRenderer sets the frame and MemoryHorntailModel poses it.
            HorntailCues.Blend b = HorntailCues.blendAt(h.anims(), t);
            double phase = DragonFlight.wingPhase(h.path(), t);
            float amp = (float) DragonFlight.wingAmp(DragonFlight.speed(h.path(), t), DragonFlight.climbRate(h.path(), t));
            model.poseBlended(new MemoryHorntailPose(b.now(), phase, amp),
                    b.before() == null ? null : new MemoryHorntailPose(b.before(), phase, amp), (float) b.weight(),
                    0f, 0f, t, 0f, 0f);
            PoseStack ps = new PoseStack();
            model.translateToHead(ps);
            Vector4f p = new Vector4f(0f, HungarianHorntailModel.SNOUT_Y / 16f, HungarianHorntailModel.SNOUT_Z / 16f, 1f)
                    .mul(ps.last().pose());
            Vector4f d = new Vector4f(0f, 0f, -1f, 0f).mul(ps.last().pose());
            JsonArray want = c.getAsJsonArray("model_snout"), wantDir = c.getAsJsonArray("model_snout_dir");
            double gap = Math.sqrt(sq(p.x - want.get(0).getAsDouble()) + sq(p.y - want.get(1).getAsDouble())
                    + sq(p.z - want.get(2).getAsDouble()));
            double dirGap = Math.sqrt(sq(d.x - wantDir.get(0).getAsDouble()) + sq(d.y - wantDir.get(1).getAsDouble())
                    + sq(d.z - wantDir.get(2).getAsDouble()));
            assertTrue(gap < 0.01, c.get("scene").getAsString() + " t " + t + ": the model's mouth is " + gap
                    + " (model blocks) from the mirror's");
            assertTrue(dirGap < 0.01, c.get("scene").getAsString() + " t " + t + ": head direction off by " + dirGap);
            // The renderer's chain (MemoryHorntailRenderer's tilt about the pivot, then LivingEntityRenderer's yaw,
            // flip, 1.15 scale and 1.501 drop) against the mirror's to_world / model_to_local.
            DragonFlight.Pose pose = DragonFlight.place(h, t);
            PoseStack w = new PoseStack();
            w.translate(pose.x(), pose.y(), pose.z());
            w.translate(0, DragonFlight.PIVOT_Y, 0);
            w.mulPose(rotation(pose.yaw(), pose.pitch(), pose.roll()).mul(rotation(pose.yaw(), 0f, 0f).conjugate()));
            w.translate(0, -DragonFlight.PIVOT_Y, 0);
            w.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f - pose.yaw()));
            w.scale(-1f, -1f, 1f);
            w.scale(1.15f, 1.15f, 1.15f);
            w.translate(0f, -1.501f, 0f);
            model.translateToHead(w);
            Vector4f wp = new Vector4f(0f, HungarianHorntailModel.SNOUT_Y / 16f, HungarianHorntailModel.SNOUT_Z / 16f, 1f)
                    .mul(w.last().pose());
            JsonArray ws = c.getAsJsonArray("snout");
            double worldGap = Math.sqrt(sq(wp.x - ws.get(0).getAsDouble()) + sq(wp.y - ws.get(1).getAsDouble())
                    + sq(wp.z - ws.get(2).getAsDouble()));
            assertTrue(worldGap < 0.01, c.get("scene").getAsString() + " t " + t + ": the drawn mouth is " + worldGap
                    + " blocks from the mirror's horntail_snout");
            worstWorld = Math.max(worstWorld, worldGap);
            worst = Math.max(worst, gap);
            worstDir = Math.max(worstDir, dirGap);
            n++;
        }
        assertEquals(40, n);
        System.out.println("HorntailModelMirrorTest: worst mouth gap " + worst + " model blocks, direction " + worstDir
                + ", in the world " + worstWorld + " blocks");
    }

    /** MemoryBroomRenderer.rotation: BroomFlight.frame as a quaternion. */
    private static org.joml.Quaternionf rotation(float yaw, float pitch, float roll) {
        double[] r = com.hogwartsmagic.pensieve.scene.BroomFlight.frame(yaw, pitch, roll);
        org.joml.Matrix3f m3 = new org.joml.Matrix3f((float) r[0], (float) r[3], (float) r[6], (float) r[1], (float) r[4],
                (float) r[7], (float) r[2], (float) r[5], (float) r[8]);
        return new org.joml.Quaternionf().setFromNormalized(m3);
    }

    private static double sq(double v) {
        return v * v;
    }
}
