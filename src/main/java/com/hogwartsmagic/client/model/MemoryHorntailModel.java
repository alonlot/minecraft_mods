package com.hogwartsmagic.client.model;

import com.hogwartsmagic.entity.HorntailPoseSource;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.hogwartsmagic.entity.MemoryHorntailEntity;
import com.hogwartsmagic.entity.MemoryHorntailPose;
import com.hogwartsmagic.pensieve.scene.HorntailCues;
import net.minecraft.client.model.geom.ModelPart;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The memory Horntail's model: the wild dragon's, posed from the scene ({@link MemoryHorntailPose}) and cross-fading
 * between states. The wild dragon snaps from one state's last frame to the next one's first; here, while the frame
 * holds a {@code before} state ({@link HorntailCues#blendAt}), the model is posed for it (or, when that change came
 * inside an earlier fade, for that whole fade, {@link HorntailCues.Blend#older}), the pose is kept, the model is posed
 * for the new state and every animated part is blended from the kept pose by the frame's weight (mirrored by
 * {@code dragon_engine.blended_model_pose}). Her head look and neck counter-roll come from the puppet
 * ({@link MemoryHorntailEntity#headYaw}, {@link MemoryHorntailEntity#neckRoll}).
 */
public class MemoryHorntailModel extends HungarianHorntailModel {
    private static final int STRIDE = 6;
    private final List<ModelPart> parts;
    /** One kept pose per fade depth. */
    private final List<float[]> kept = new ArrayList<>();

    public MemoryHorntailModel(ModelPart root) {
        super(root);
        this.parts = animatedParts();
    }

    @Override
    public void setupAnim(HungarianHorntailEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        if (!(entity instanceof MemoryHorntailEntity m)) {
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            return;
        }
        double phase = m.wingPhase;
        float amp = m.wingAmplitude, roll = m.neckRoll;
        poseBlend(m.frame, s -> new MemoryHorntailPose(s, phase, amp, roll), limbSwing, limbSwingAmount, ageInTicks,
                m.headYaw, m.headPitch);
    }

    /** Poses the blend {@code b}, each of its states through {@code source}. */
    public void poseBlend(HorntailCues.Blend b, Function<HorntailCues.State, HorntailPoseSource> source, float limbSwing,
                          float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        poseBlend(b, source, 0, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }

    private void poseBlend(HorntailCues.Blend b, Function<HorntailCues.State, HorntailPoseSource> source, int depth,
                           float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float w = (float) b.weight();
        if (b.before() == null || w >= 1f) {
            pose(source.apply(b.now()), limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            return;
        }
        if (b.older() != null) {
            poseBlend(b.older(), source, depth + 1, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        } else {
            pose(source.apply(b.before()), limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        }
        while (kept.size() <= depth) kept.add(new float[parts.size() * STRIDE]);
        float[] k = kept.get(depth);
        for (int i = 0; i < parts.size(); i++) {
            ModelPart p = parts.get(i);
            int o = i * STRIDE;
            k[o] = p.x;
            k[o + 1] = p.y;
            k[o + 2] = p.z;
            k[o + 3] = p.xRot;
            k[o + 4] = p.yRot;
            k[o + 5] = p.zRot;
        }
        pose(source.apply(b.now()), limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        for (int i = 0; i < parts.size(); i++) {
            ModelPart p = parts.get(i);
            int o = i * STRIDE;
            p.x = k[o] + (p.x - k[o]) * w;
            p.y = k[o + 1] + (p.y - k[o + 1]) * w;
            p.z = k[o + 2] + (p.z - k[o + 2]) * w;
            p.xRot = k[o + 3] + (p.xRot - k[o + 3]) * w;
            p.yRot = k[o + 4] + (p.yRot - k[o + 4]) * w;
            p.zRot = k[o + 5] + (p.zRot - k[o + 5]) * w;
        }
    }
}
