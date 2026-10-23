package com.hogwartsmagic.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hogwartsmagic.entity.MemoryHorntailPose;
import com.hogwartsmagic.pensieve.scene.DragonFlight;
import com.hogwartsmagic.pensieve.scene.HorntailCues;
import com.hogwartsmagic.pensieve.scene.PartTrack;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real model, baked from createBodyLayer and posed as MemoryHorntailModel poses a memory dragon, against
 * the Python mirror's model pose (horntail_cases.json), for every case (perched with a head look, a roar and its
 * cross-fades, quick cues with nested fades, a takeoff, a hover with a head look, a cruise, a climb, a glide, a banked
 * turn, an air breath, the stagger):
 * <ul>
 *   <li>the mouth the fire leaves from, through the whole posed neck chain, in model space and through the
 *       renderer's whole matrix chain;</li>
 *   <li>both ends of every capsule dragon_engine builds (each wing, tail, leg, neck and body part, and each spike and
 *       horn on its own child offset), and the left wingtip: the poses behind every clearance assert;</li>
 * </ul>
 * and that a positive beat lowers the real model's wingtip (model y down), the sign the downstroke cues rest on.
 */
class HorntailModelMirrorTest {
    @Test
    void modelMatchesTheMirror() throws Exception {
        ModelPart root = HungarianHorntailModel.createBodyLayer().bakeRoot();
        MemoryHorntailModel model = new MemoryHorntailModel(root);
        JsonObject all = JsonParser.parseString(Files.readString(
                Path.of("build_scripts/pensieve_scenes/horntail_cases.json"))).getAsJsonObject();
        Map<String, SceneDef> scenes = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : all.getAsJsonObject("scenes").entrySet()) {
            scenes.put(e.getKey(), SceneParser.parse(e.getKey(), e.getValue().getAsJsonObject()));
        }
        JsonObject k = all.getAsJsonObject("constants");
        JsonObject tree = k.getAsJsonObject("TREE");
        JsonArray specs = k.getAsJsonArray("CAPSULES");
        double[] tip = doubles(k.get("WINGTIP"));
        double worst = 0, worstDir = 0, worstWorld = 0, worstPart = 0;
        String worstName = "";
        int n = 0;
        for (JsonElement ce : all.getAsJsonArray("cases")) {
            JsonObject c = ce.getAsJsonObject();
            SceneDef.ActorDef h = scenes.get(c.get("scene").getAsString()).actors().get(0);
            float t = c.get("t").getAsFloat();
            String at = c.get("scene").getAsString() + " t " + t;
            pose(model, h, t);
            PoseStack ps = new PoseStack();
            model.translateToHead(ps);
            Vector4f p = new Vector4f(0f, HungarianHorntailModel.SNOUT_Y / 16f, HungarianHorntailModel.SNOUT_Z / 16f, 1f)
                    .mul(ps.last().pose());
            Vector4f d = new Vector4f(0f, 0f, -1f, 0f).mul(ps.last().pose());
            JsonArray want = c.getAsJsonArray("model_snout"), wantDir = c.getAsJsonArray("model_snout_dir");
            double gap = dist(p, doubles(want));
            double dirGap = dist(d, doubles(wantDir));
            assertTrue(gap < 0.01, at + ": the model's mouth is " + gap + " (model blocks) from the mirror's");
            assertTrue(dirGap < 0.01, at + ": head direction off by " + dirGap);
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
            double worldGap = dist(wp, doubles(c.get("snout")));
            assertTrue(worldGap < 0.01, at + ": the drawn mouth is " + worldGap + " blocks from the mirror's horntail_snout");
            // Every capsule's two ends, and the wingtip, on the real posed parts.
            JsonArray points = c.getAsJsonArray("part_points");
            assertEquals(specs.size(), points.size(), at + " capsule count");
            for (int i = 0; i < specs.size(); i++) {
                JsonObject spec = specs.get(i).getAsJsonObject();
                JsonArray seg = spec.getAsJsonArray("seg");
                for (int e = 0; e < 2; e++) {
                    Vector4f got = specPoint(root, tree, spec, doubles(seg.get(e)));
                    double g = dist(got, doubles(points.get(i).getAsJsonArray().get(e)));
                    String name = spec.get("name").getAsString();
                    assertTrue(g < 0.01, at + ": capsule " + name + " end " + e + " is " + g + " from the mirror's");
                    if (g > worstPart) {
                        worstPart = g;
                        worstName = at + " " + name;
                    }
                }
            }
            Vector4f wingtip = partPoint(root, tree, "left_finger", null, tip);
            double tipGap = dist(wingtip, doubles(c.get("wingtip")));
            assertTrue(tipGap < 0.01, at + ": the wingtip is " + tipGap + " from the mirror's");
            worstWorld = Math.max(worstWorld, worldGap);
            worst = Math.max(worst, gap);
            worstDir = Math.max(worstDir, dirGap);
            n++;
        }
        assertEquals(44, n);
        System.out.println("HorntailModelMirrorTest: worst mouth gap " + worst + " model blocks, direction " + worstDir
                + ", in the world " + worstWorld + " blocks; worst capsule end " + worstPart + " (" + worstName + ")");
    }

    @Test
    void aPositiveBeatLowersTheWingtip() {
        ModelPart root = HungarianHorntailModel.createBodyLayer().bakeRoot();
        MemoryHorntailModel model = new MemoryHorntailModel(root);
        Map<String, String> chain = Map.of("left_finger", "left_forearm", "left_forearm", "left_humerus",
                "left_humerus", "body", "body", "");
        JsonObject tree = new JsonObject();
        for (Map.Entry<String, String> e : chain.entrySet()) {
            if (e.getValue().isEmpty()) tree.add(e.getKey(), com.google.gson.JsonNull.INSTANCE);
            else tree.addProperty(e.getKey(), e.getValue());
        }
        HorntailCues.State fly = new HorntailCues.State("FLY", 0, true, 0);
        double[] tip = {24, 0, 0};
        float amp = (float) DragonFlight.HOVER_AMP;
        model.poseBlend(new HorntailCues.Blend(fly, null, 1), s -> new MemoryHorntailPose(s, Math.PI / 2, amp),
                0f, 0f, 0f, 0f, 0f);
        float down = partPoint(root, tree, "left_finger", null, tip).y;
        model.poseBlend(new HorntailCues.Blend(fly, null, 1), s -> new MemoryHorntailPose(s, -Math.PI / 2, amp),
                0f, 0f, 0f, 0f, 0f);
        float up = partPoint(root, tree, "left_finger", null, tip).y;
        assertTrue(down > up + 0.5, "a positive beat must lower the wingtip (model y down): " + down + " vs " + up);
        // And the mid-downstroke the sound cues land on is halfway between: the wing on its way down.
        model.poseBlend(new HorntailCues.Blend(fly, null, 1),
                s -> new MemoryHorntailPose(s, DragonFlight.MID_DOWNSTROKE, amp), 0f, 0f, 0f, 0f, 0f);
        float mid = partPoint(root, tree, "left_finger", null, tip).y;
        assertTrue(mid > up && mid < down, "mid-downstroke " + mid + " between " + up + " and " + down);
    }

    /** Poses the model as MemoryHorntailRenderer sets the frame and MemoryHorntailModel poses it. */
    private static void pose(MemoryHorntailModel model, SceneDef.ActorDef h, float t) {
        HorntailCues.Blend b = HorntailCues.blendAt(h.anims(), t);
        double phase = DragonFlight.wingPhase(h.path(), t);
        float amp = (float) DragonFlight.wingAmp(DragonFlight.speed(h.path(), t), DragonFlight.climbRate(h.path(), t));
        float roll = DragonFlight.neckRoll(DragonFlight.place(h, t).roll());
        PartTrack hl = h.limbs().get(DragonFlight.HEAD_LOOK);
        float[] look = DragonFlight.headLook(hl == null ? null : hl.rot(), t);
        model.poseBlend(b, s -> new MemoryHorntailPose(s, phase, amp, roll), 0f, 0f, t, look[0], look[1]);
    }

    /** A capsule end: its part's posed chain, then a spike's own child offset and rotation, then the point (px). */
    private static Vector4f specPoint(ModelPart root, JsonObject tree, JsonObject spec, double[] px) {
        JsonElement child = spec.get("child");
        return partPoint(root, tree, spec.get("part").getAsString(),
                child == null || child.isJsonNull() ? null : doubles(child), px);
    }

    private static Vector4f partPoint(ModelPart root, JsonObject tree, String part, double[] child, double[] px) {
        List<String> chain = new ArrayList<>();
        for (String p = part; p != null; ) {
            chain.add(p);
            JsonElement up = tree.get(p);
            p = up == null || up.isJsonNull() ? null : up.getAsString();
        }
        Collections.reverse(chain);
        PoseStack ps = new PoseStack();
        root.translateAndRotate(ps);
        ModelPart cur = root;
        for (String name : chain) {
            cur = cur.getChild(name);
            cur.translateAndRotate(ps);
        }
        if (child != null) {
            ps.translate(child[0] / 16f, child[1] / 16f, child[2] / 16f);
            ps.mulPose(new Quaternionf().rotationZYX((float) child[4], 0f, (float) child[3]));
        }
        return new Vector4f((float) px[0] / 16f, (float) px[1] / 16f, (float) px[2] / 16f, 1f).mul(ps.last().pose());
    }

    /** MemoryBroomRenderer.rotation: BroomFlight.frame as a quaternion. */
    private static Quaternionf rotation(float yaw, float pitch, float roll) {
        double[] r = com.hogwartsmagic.pensieve.scene.BroomFlight.frame(yaw, pitch, roll);
        org.joml.Matrix3f m3 = new org.joml.Matrix3f((float) r[0], (float) r[3], (float) r[6], (float) r[1], (float) r[4],
                (float) r[7], (float) r[2], (float) r[5], (float) r[8]);
        return new Quaternionf().setFromNormalized(m3);
    }

    private static double[] doubles(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        double[] out = new double[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsDouble();
        return out;
    }

    private static double dist(Vector4f v, double[] w) {
        return Math.sqrt(sq(v.x - w[0]) + sq(v.y - w[1]) + sq(v.z - w[2]));
    }

    private static double sq(double v) {
        return v * v;
    }
}
