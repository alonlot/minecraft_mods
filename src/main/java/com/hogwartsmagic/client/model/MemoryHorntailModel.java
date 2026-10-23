package com.hogwartsmagic.client.model;

import com.hogwartsmagic.entity.HorntailPoseSource;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.hogwartsmagic.entity.MemoryHorntailEntity;
import com.hogwartsmagic.entity.MemoryHorntailPose;
import com.hogwartsmagic.pensieve.scene.HorntailCues;
import net.minecraft.client.model.geom.ModelPart;

import java.util.List;

/**
 * The memory Horntail's model: the wild dragon's, posed from the scene ({@link MemoryHorntailPose}) and cross-fading
 * between states. The wild dragon snaps from one state's last frame to the next one's first; here, while the frame
 * holds a {@code before} state ({@link HorntailCues#blendAt}), the model is posed for it, the pose is kept, the model is
 * posed for the new state and every animated part is blended from the old pose by the frame's weight (mirrored by
 * {@code dragon_engine.blended_model_pose}).
 */
public class MemoryHorntailModel extends HungarianHorntailModel {
    private static final int STRIDE = 6;
    private final List<ModelPart> parts;
    private final float[] before;

    public MemoryHorntailModel(ModelPart root) {
        super(root);
        this.parts = animatedParts();
        this.before = new float[parts.size() * STRIDE];
    }

    @Override
    public void setupAnim(HungarianHorntailEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        if (!(entity instanceof MemoryHorntailEntity m)) {
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            return;
        }
        HorntailCues.Blend b = m.frame;
        MemoryHorntailPose now = new MemoryHorntailPose(b.now(), m.wingPhase, m.wingAmplitude);
        MemoryHorntailPose old = b.before() == null ? null : new MemoryHorntailPose(b.before(), m.wingPhase, m.wingAmplitude);
        poseBlended(now, old, (float) b.weight(), limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }

    /** Poses {@code now}, cross-faded from {@code before} (null: none) with {@code now} at weight {@code w}. */
    public void poseBlended(HorntailPoseSource now, HorntailPoseSource before, float w, float limbSwing,
                            float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        if (before == null || w >= 1f) {
            pose(now, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            return;
        }
        pose(before, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        for (int i = 0; i < parts.size(); i++) {
            ModelPart p = parts.get(i);
            int o = i * STRIDE;
            this.before[o] = p.x;
            this.before[o + 1] = p.y;
            this.before[o + 2] = p.z;
            this.before[o + 3] = p.xRot;
            this.before[o + 4] = p.yRot;
            this.before[o + 5] = p.zRot;
        }
        pose(now, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        float[] b = this.before;
        for (int i = 0; i < parts.size(); i++) {
            ModelPart p = parts.get(i);
            int o = i * STRIDE;
            p.x = b[o] + (p.x - b[o]) * w;
            p.y = b[o + 1] + (p.y - b[o + 1]) * w;
            p.z = b[o + 2] + (p.z - b[o + 2]) * w;
            p.xRot = b[o + 3] + (p.xRot - b[o + 3]) * w;
            p.yRot = b[o + 4] + (p.yRot - b[o + 4]) * w;
            p.zRot = b[o + 5] + (p.zRot - b[o + 5]) * w;
        }
    }
}
