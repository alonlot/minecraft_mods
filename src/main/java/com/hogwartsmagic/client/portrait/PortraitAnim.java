package com.hogwartsmagic.client.portrait;

import com.hogwartsmagic.portrait.FatLadyLines;
import com.hogwartsmagic.portrait.FatLadyPortraitEntity;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Everything the client remembers about one portrait: the line it is saying, and the paper puppet's springs and
 * expression weights, advanced once per rendered frame ({@link #update}). A port of the prototype's frame loop.
 */
final class PortraitAnim {
    // Expression layers, in the order they are drawn over the base painting.
    static final int BASE = 0, HAPPY = 1, CROSS = 2, SCARED = 3, LOOK_L = 4, LOOK_R = 5, BLINK = 6, AH = 7, OH = 8;
    static final int LAYERS = 9;
    static final String[] LAYER_TEXTURE = {"base", "mood_happy", "mood_cross", "mood_scared", "look_l", "look_r",
            "blink", "talk_ah", "talk_oh"};

    /** Weight of each layer (index = layer; BASE is always 1 and not used). */
    final float[] w = new float[LAYERS];

    // ---- what the tick handler tracks
    boolean haveSerial;
    int serial;
    int ticksSeen;
    long nextSubtitleNs;

    // ---- the line being said
    @Nullable
    FatLadyLines.Line line;
    long lineStartNs;
    @Nullable
    float[] envelope;
    /** Non-null when the line is a video line whose frames are present. */
    @Nullable
    PortraitAssets.Clip clip;
    @Nullable
    SoundInstance sound;
    private float nextBounce;

    // ---- puppet state (painting pixels / radians)
    float breath, rot, hx, hy, flinch;
    private float flinchV, bounce, bounceV;
    private boolean init;
    private long lastNs;
    private float idleGaze;
    private double nextGlance, nextBlink;
    private float blinkT = -1f;
    private float awaySide;

    void begin(FatLadyLines.Line newLine, @Nullable float[] env, @Nullable PortraitAssets.Clip videoClip, long nowNs) {
        this.line = newLine;
        this.lineStartNs = nowNs;
        this.envelope = env;
        this.clip = newLine.isVideo() ? videoClip : null;
        this.nextBounce = 1.4f;
        if (newLine.mood() == FatLadyLines.Mood.SCARED) {
            flinchV += 9f;
        } else if (newLine.mood() == FatLadyLines.Mood.HAPPY) {
            bounceV += 5f;
        }
    }

    /** Seconds since the line started (negative before). */
    float lineTime(long nowNs) {
        return line == null ? Float.MAX_VALUE : (nowNs - lineStartNs) / 1.0e9f;
    }

    /** The video frame to show, or -1 when the puppet is on. */
    int videoFrame(long nowNs) {
        if (line == null || clip == null) {
            return -1;
        }
        float t = lineTime(nowNs);
        if (t < 0f) {
            return -1;
        }
        int frame = (int) (t * clip.fps());
        return frame < clip.frames() ? frame : -1;
    }

    /** Advances the puppet to {@code nowNs}. Called from the renderer, once per frame per portrait. */
    void update(FatLadyPortraitEntity entity, float partialTick, long nowNs) {
        double now = nowNs / 1.0e9;
        if (!init) {
            init = true;
            lastNs = nowNs;
            nextBlink = now + 1.5;
            nextGlance = now + 2.0;
            awaySide = (entity.getId() & 1) == 0 ? 1f : -1f;
        }
        float dt = Math.min(0.05f, Math.max(0f, (nowNs - lastNs) / 1.0e9f));
        lastNs = nowNs;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        float t = lineTime(nowNs);
        boolean active = line != null && t <= line.seconds() + 0.6f;
        FatLadyLines.Mood mood = active ? line.mood() : FatLadyLines.Mood.NEUTRAL;
        boolean scared = mood == FatLadyLines.Mood.SCARED;
        boolean happy = mood == FatLadyLines.Mood.HAPPY;
        boolean cross = mood == FatLadyLines.Mood.CROSS;
        boolean speaking = line != null && t >= 0f && t < line.seconds();

        // mouth from the line's 50 ms loudness envelope; no envelope means a closed mouth
        float env = 0f;
        if (speaking && envelope != null) {
            int k = (int) (t * 20f);
            env = k >= 0 && k < envelope.length ? envelope[k] : 0f;
        }

        // gaze: toward the player she looks at, otherwise idle glances
        boolean present = false;
        float gaze;
        Entity target = entity.getLookId() >= 0 ? entity.level().getEntity(entity.getLookId()) : null;
        if (target != null) {
            Vec3 front = entity.frontVector();
            Vec3 d = target.getPosition(partialTick).subtract(entity.position());
            float rightX = (float) front.z, rightZ = (float) -front.x; // image-right = facing.getCounterClockWise()
            float lateral = (float) (d.x * rightX + d.z * rightZ);
            float depth = (float) (d.x * front.x + d.z * front.z);
            present = depth > -0.5f;
            gaze = Mth.clamp(lateral / (Math.max(depth, 0f) + 0.7f), -1f, 1f);
        } else {
            gaze = 0f;
        }
        if (now > nextGlance) {
            idleGaze = new float[]{-0.7f, 0f, 0f, 0.7f}[rng.nextInt(4)];
            nextGlance = now + 2.0 + rng.nextDouble() * 4.0;
        }
        if (!present) {
            gaze = idleGaze;
        }
        // cross: the head turns slightly away from the player, the eyes still follow (a sideways glare)
        float headGaze = gaze;
        if (present && w[CROSS] > 0.01f) {
            float away = Math.abs(gaze) < 0.15f ? awaySide * 0.5f : gaze;
            headGaze = Mth.lerp(w[CROSS], gaze, -away * 0.8f);
        }

        // blinking
        float blinkTarget = 0f;
        if (now > nextBlink && blinkT < 0f) {
            blinkT = 0f;
            nextBlink = now + 2.5 + rng.nextDouble() * 3.5;
        }
        if (blinkT >= 0f) {
            blinkT += dt;
            blinkTarget = blinkT < 0.07f ? 1f : 0f;
            if (blinkT > 0.22f) {
                blinkT = -1f;
            }
        }

        // body
        breath = (float) Math.sin(now * 2.0 * Math.PI / 4.2);
        float talkNod = env * (0.5f + 0.5f * (float) Math.sin(now * 9.0));
        float tremble = scared && active ? rng.nextFloat() - 0.5f : 0f;
        rot = ease(rot, (present ? headGaze * 0.045f : headGaze * 0.02f) + (float) Math.sin(now * 0.9) * 0.012f
                + tremble * 0.02f, 6f, dt);
        hx = ease(hx, headGaze * 9f + tremble * 3f, 6f, dt);
        // happy: a little bounce now and then while she talks
        if (happy && speaking && t > nextBounce) {
            bounceV += 3.5f;
            nextBounce = t + 1.4f;
        }
        bounceV += (-90f * bounce - 7f * bounceV) * dt;
        bounce += bounceV * dt;
        hy = ease(hy, -talkNod * 5f + (scared ? -8f : 0f) - bounce * 6f, 10f, dt);
        // flinch spring
        flinchV += (-60f * (flinch - (scared ? 0.5f : 0f)) - 9f * flinchV) * dt;
        flinch += flinchV * dt;

        // expression weights: eyes and mouth are separate regions, a scared face overrides both
        w[SCARED] = ease(w[SCARED], scared ? 1f : 0f, 14f, dt);
        w[HAPPY] = ease(w[HAPPY], happy ? 1f : 0f, 10f, dt);
        w[CROSS] = ease(w[CROSS], cross ? 1f : 0f, 10f, dt);
        float free = 1f - w[SCARED];
        w[BLINK] = ease(w[BLINK], blinkTarget * free, 40f, dt);
        float lookFree = free * (1f - w[BLINK]);
        w[LOOK_L] = ease(w[LOOK_L], Math.max(0f, -gaze) * lookFree, 10f, dt);
        w[LOOK_R] = ease(w[LOOK_R], Math.max(0f, gaze) * lookFree, 10f, dt);
        float ah = !scared && env > 0.55f ? 1f : 0f;
        float oh = !scared && env > 0.18f && env <= 0.55f ? 1f : 0f;
        w[AH] = ease(w[AH], ah * free, 30f, dt);
        w[OH] = ease(w[OH], oh * free, 30f, dt);
    }

    private static float ease(float current, float to, float rate, float dt) {
        return current + (to - current) * (1f - (float) Math.exp(-rate * dt));
    }
}
