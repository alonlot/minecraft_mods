package com.hogwartsmagic.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hogwartsmagic.Config;
import com.hogwartsmagic.client.model.HungarianHorntailModel;
import com.hogwartsmagic.client.model.MemoryHorntailModel;
import com.hogwartsmagic.client.pensieve.ActorRuntime;
import com.hogwartsmagic.client.pensieve.MemoryActors;
import com.hogwartsmagic.client.pensieve.PensieveClientState;
import com.hogwartsmagic.client.renderer.HungarianHorntailRenderer;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.hogwartsmagic.entity.MemoryHorntailEntity;
import com.hogwartsmagic.pensieve.scene.DragonFlight;
import com.hogwartsmagic.pensieve.scene.HorntailCues;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Draws the memory Horntail ({@link MemoryHorntailEntity}): the wild dragon's renderer, model and glowing eyes, at
 * {@code scale} 1 exactly the real dragon's size (its 1.15 render scale; MemoryActors applies the actor scale), with
 * three additions:
 * <ul>
 *   <li>each frame it sets the puppet's {@code frame} from the scene clock: the state and its cross-fade
 *       ({@link HorntailCues#blendAt}) and the wing phase and amplitude ({@link DragonFlight}), so wingbeats follow the
 *       path at any frame rate and a late joiner sees the same stroke, and her head look
 *       ({@link DragonFlight#headLook}, the {@code limbs.head_look} channel) and the neck's counter-roll
 *       ({@link DragonFlight#neckRoll});</li>
 *   <li>it turns the whole dragon by her lerped pitch and roll about her body pivot ({@link DragonFlight#PIVOT_Y}),
 *       Yaw x Pitch x Roll as {@link DragonFlight#place} gives it (the vanilla renderer adds the yaw itself);</li>
 *   <li>a layer stores the drawn mouth and the head's direction on the puppet ({@link MemoryHorntailEntity#snout()})
 *       for the fire, read off the posed model's own part transforms. In a {@code dev_} scene it also logs, every 5 s
 *       of scene time, that point against the Python mirror's {@code horntail_snout} (a side file the dev script writes
 *       next to the scene: {@code <scene>_snout.json}).</li>
 * </ul>
 */
public class MemoryHorntailRenderer extends HungarianHorntailRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();

    public MemoryHorntailRenderer(EntityRendererProvider.Context context) {
        super(context, new MemoryHorntailModel(context.bakeLayer(HungarianHorntailModel.LAYER_LOCATION)));
        this.addLayer(new SnoutLayer(this));
    }

    @Override
    public void render(HungarianHorntailEntity entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        if (!(entity instanceof MemoryHorntailEntity m)) {
            super.render(entity, yaw, partial, pose, buffers, light);
            return;
        }
        float t = PensieveClientState.sceneTime(partial);
        m.frame = HorntailCues.blendAt(m.anims, t);
        m.wingPhase = m.wingClock.phase(t);
        m.wingAmplitude = (float) DragonFlight.wingAmp(DragonFlight.speed(m.path, t), DragonFlight.climbRate(m.path, t));
        float pitch = Mth.lerp(partial, m.pitchO, m.pitch);
        float roll = Mth.lerp(partial, m.rollO, m.roll);
        // Her head look and the neck's counter to her drawn roll, which the model reads off the puppet.
        float[] look = DragonFlight.headLook(m.headLook, t);
        m.headYaw = look[0];
        m.headPitch = look[1];
        m.neckRoll = DragonFlight.neckRoll(roll);
        if (pitch == 0f && roll == 0f) {
            super.render(entity, yaw, partial, pose, buffers, light);
            return;
        }
        // Pitch and roll about the body pivot, in the dragon's own heading: Yaw * Pitch * Roll * Yaw^-1 (the vanilla
        // renderer then applies the yaw, so the whole is Yaw * Pitch * Roll).
        float bodyYaw = Mth.rotLerp(partial, m.yBodyRotO, m.yBodyRot);
        Quaternionf tilt = MemoryBroomRenderer.rotation(bodyYaw, pitch, roll)
                .mul(MemoryBroomRenderer.rotation(bodyYaw, 0f, 0f).conjugate());
        pose.pushPose();
        pose.translate(0, DragonFlight.PIVOT_Y, 0);
        pose.mulPose(tilt);
        pose.translate(0, -DragonFlight.PIVOT_Y, 0);
        super.render(entity, yaw, partial, pose, buffers, light);
        pose.popPose();
    }

    /** Stores the drawn mouth on the puppet: the pose stack at a layer is the posed model's root, camera-relative. */
    private static final class SnoutLayer extends RenderLayer<HungarianHorntailEntity, HungarianHorntailModel> {
        SnoutLayer(RenderLayerParent<HungarianHorntailEntity, HungarianHorntailModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, HungarianHorntailEntity entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                           float headPitch) {
            if (!(entity instanceof MemoryHorntailEntity m)) return;
            PoseStack head = new PoseStack();
            head.last().pose().set(poseStack.last().pose());
            head.last().normal().set(poseStack.last().normal());
            getParentModel().translateToHead(head);
            Vector4f p = new Vector4f(0f, HungarianHorntailModel.SNOUT_Y / 16f, HungarianHorntailModel.SNOUT_Z / 16f, 1f)
                    .mul(head.last().pose());
            Vector4f d = new Vector4f(0f, 0f, -1f, 0f).mul(head.last().pose());
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            double len = Math.sqrt(d.x * d.x + d.y * d.y + d.z * d.z);
            if (len < 1e-9) return;
            m.setSnout(new double[]{p.x + cam.x, p.y + cam.y, p.z + cam.z},
                    new double[]{d.x / len, d.y / len, d.z / len},
                    com.hogwartsmagic.client.pensieve.DragonFireRenderer.frame());
            DevLog.check(m, partialTick);
        }
    }

    /** The dev scenes' snout check: the drawn mouth against the Python mirror's, every 5 s of scene time. */
    private static final class DevLog {
        private static String loadedFor;
        private static Map<String, float[][]> mirror = Map.of();
        private static final Map<String, Integer> LAST_SLOT = new HashMap<>();
        private static final Map<String, Double> WORST = new HashMap<>();

        static void check(MemoryHorntailEntity m, float partial) {
            SceneDef scene = PensieveClientState.scene();
            BlockPos origin = PensieveClientState.origin();
            if (scene == null || origin == null || !scene.id().startsWith("dev_")) return;
            ActorRuntime rt = MemoryActors.runtimeFor(m);
            if (rt == null) return;
            if (!scene.id().equals(loadedFor)) load(scene.id());
            float t = PensieveClientState.sceneTime(partial);
            String id = rt.def.id();
            double[] s = m.snout();
            if (s == null) return;
            double x = s[0] - origin.getX(), y = s[1] - origin.getY(), z = s[2] - origin.getZ();
            float[] want = sample(mirror.get(id), t);
            double gap = want == null ? Double.NaN
                    : Math.sqrt((x - want[0]) * (x - want[0]) + (y - want[1]) * (y - want[1]) + (z - want[2]) * (z - want[2]));
            if (!Double.isNaN(gap)) WORST.merge(id, gap, Math::max);
            int slot = (int) Math.floor(t / 100f);
            Integer last = LAST_SLOT.get(id);
            if (last != null && last == slot) return;
            LAST_SLOT.put(id, slot);
            LOGGER.info("Pensieve horntail: t {} s, actor {}, state {}, rendered snout ({}, {}, {}), mirror {}, gap {}, "
                            + "worst so far {}",
                    f(t / 20f), id, m.frame.now().name(), f(x), f(y), f(z),
                    want == null ? "none (no side file)" : "(" + f(want[0]) + ", " + f(want[1]) + ", " + f(want[2]) + ")",
                    Double.isNaN(gap) ? "-" : f(gap), WORST.containsKey(id) ? f(WORST.get(id)) : "-");
        }

        private static String f(double v) {
            return String.format(Locale.ROOT, "%.3f", v);
        }

        /** Linear between the mirror's samples ({@code [t, x, y, z]}, in time order). */
        private static float[] sample(float[][] rows, float t) {
            if (rows == null || rows.length == 0) return null;
            if (t <= rows[0][0]) return new float[]{rows[0][1], rows[0][2], rows[0][3]};
            for (int i = 1; i < rows.length; i++) {
                if (t <= rows[i][0]) {
                    float[] a = rows[i - 1], b = rows[i];
                    float k = b[0] > a[0] ? (t - a[0]) / (b[0] - a[0]) : 1f;
                    return new float[]{a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k, a[3] + (b[3] - a[3]) * k};
                }
            }
            float[] l = rows[rows.length - 1];
            return new float[]{l[1], l[2], l[3]};
        }

        private static void load(String sceneId) {
            loadedFor = sceneId;
            mirror = Map.of();
            LAST_SLOT.clear();
            WORST.clear();
            String dir = Config.pensieveDevSceneDir;
            if (dir == null || dir.isBlank()) return;
            Path file = Path.of(dir).resolve(sceneId + "_snout.json");
            if (!Files.isRegularFile(file)) return;
            try {
                JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                Map<String, float[][]> out = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    JsonArray rows = e.getValue().getAsJsonArray();
                    float[][] r = new float[rows.size()][];
                    for (int i = 0; i < rows.size(); i++) {
                        JsonArray row = rows.get(i).getAsJsonArray();
                        r[i] = new float[]{row.get(0).getAsFloat(), row.get(1).getAsFloat(), row.get(2).getAsFloat(),
                                row.get(3).getAsFloat()};
                    }
                    out.put(e.getKey(), r);
                }
                mirror = out;
            } catch (Exception ex) {
                LOGGER.warn("Pensieve horntail: cannot read {}: {}", file, ex.toString());
            }
        }
    }
}
