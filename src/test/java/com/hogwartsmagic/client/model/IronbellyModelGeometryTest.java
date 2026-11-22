package com.hogwartsmagic.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Ironbelly's own sculpt keeps the Horntail's skeleton, so in the rest pose its feet and wing
 * claws must land on the same ground plane (model y = 24) and nothing may hang below it. Also
 * writes the posed mesh to build/ironbelly_mesh.json, which build_scripts/preview_ironbelly.py
 * renders, so the sculpt can be looked at without launching the game.
 */
class IronbellyModelGeometryTest {

    @Test
    void restPoseStandsOnTheGround() throws Exception {
        ModelPart root = UkrainianIronbellyModel.createIronbellyLayer().bakeRoot();
        float[] lowest = {-Float.MAX_VALUE};
        StringBuilder json = new StringBuilder("[");
        // Record what the model actually draws: every vertex, posed, with its UV. Four per face.
        java.util.List<float[]> verts = new java.util.ArrayList<>();
        com.mojang.blaze3d.vertex.VertexConsumer recorder = new com.mojang.blaze3d.vertex.VertexConsumer() {
            @Override
            public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x, float y, float z) {
                verts.add(new float[]{x, y, z, 0.0F, 0.0F});
                lowest[0] = Math.max(lowest[0], y);
                return this;
            }

            @Override
            public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r, int g, int b, int a) {
                return this;
            }

            @Override
            public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u, float v) {
                float[] last = verts.get(verts.size() - 1);
                last[3] = u;
                last[4] = v;
                return this;
            }

            @Override
            public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u, int v) {
                return this;
            }

            @Override
            public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u, int v) {
                return this;
            }

            @Override
            public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x, float y, float z) {
                return this;
            }
        };
        root.render(new PoseStack(), recorder, 0xF000F0, 0);
        for (int q = 0; q + 3 < verts.size(); q += 4) {
            json.append(q == 0 ? "" : ",").append("[");
            for (int i = 0; i < 4; i++) {
                float[] v = verts.get(q + i);
                json.append(i == 0 ? "" : ",").append(String.format(Locale.ROOT,
                        "[%.4f,%.4f,%.4f,%.5f,%.5f]", v[0], v[1], v[2], v[3], v[4]));
            }
            json.append("]");
        }
        json.append("]");
        Files.createDirectories(Path.of("build"));
        Files.writeString(Path.of("build/ironbelly_mesh.json"), json.toString());
        // Model y grows downward; 24 px = 1.5 blocks is the ground plane.
        // The claws curl down into the ground by a couple of px, as the Horntail's do: that is
        // the grip, not a float. Anything lower than that is a part through the floor.
        assertTrue(lowest[0] <= 24.0F / 16.0F + 3.0F / 16.0F,
                "something hangs below the ground plane: lowest y " + lowest[0] * 16.0F + " px");
        assertTrue(lowest[0] >= 24.0F / 16.0F - 0.35F,
                "nothing reaches the ground plane: lowest y " + lowest[0] * 16.0F + " px");
    }

    @Test
    void keepsTheHorntailSkeleton() {
        ModelPart iron = UkrainianIronbellyModel.createIronbellyLayer().bakeRoot();
        ModelPart horn = HungarianHorntailModel.createBodyLayer().bakeRoot();
        String[][] chains = {
                {"body", "neck1", "neck2", "head", "jaw"},
                {"body", "tail1", "tail2", "tail3", "tail4"},
                {"body", "left_humerus", "left_forearm", "left_finger"},
                {"body", "right_thigh", "right_shank", "right_foot"},
        };
        for (String[] chain : chains) {
            ModelPart a = iron;
            ModelPart b = horn;
            for (String name : chain) {
                a = a.getChild(name);
                b = b.getChild(name);
                assertEquals(b.x, a.x, 1.0E-4F, name + " pivot x");
                assertEquals(b.y, a.y, 1.0E-4F, name + " pivot y");
                assertEquals(b.xRot, a.xRot, 1.0E-4F, name + " rest xRot");
                assertEquals(b.zRot, a.zRot, 1.0E-4F, name + " rest zRot");
            }
        }
    }
}
