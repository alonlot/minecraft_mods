package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.pensieve.scene.SceneDef;

import java.util.List;
import java.util.Locale;

/** The arithmetic of actor posing, kept free of Minecraft types so it can be unit tested. */
public final class PoseMath {
    /** Ticks a pose cue blends in over when the scene did not say. */
    public static final float DEFAULT_POSE_BLEND = 5f;

    private PoseMath() {
    }

    /** {@code fooBarBaz} to {@code foo_bar_baz}; already-snake names are unchanged. */
    public static String snake(String camel) {
        StringBuilder sb = new StringBuilder(camel.length() + 4);
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0 && camel.charAt(i - 1) != '_') sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else if (Character.isDigit(c) && i > 0 && Character.isLetter(camel.charAt(i - 1))) {
                sb.append('_').append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /** Index of the cue in force at {@code t} (the latest one whose time has come), or -1 before the first. */
    public static int activeCue(List<SceneDef.Cue> sortedCues, float t) {
        int found = -1;
        for (int i = 0; i < sortedCues.size(); i++) {
            if (sortedCues.get(i).t() <= t) found = i;
            else break;
        }
        return found;
    }

    /** How far a cue has blended in at {@code t}, 0..1. A blend of 0 or less is "not given": {@code dflt} ticks. */
    public static float weight(SceneDef.Cue cue, float t, float dflt) {
        float span = cue.blend() > 0 ? cue.blend() : dflt;
        if (span <= 0) return 1f;
        float w = (t - cue.t()) / span;
        return w < 0 ? 0 : Math.min(w, 1f);
    }

    public static float lerp(float a, float b, float w) {
        return a + (b - a) * w;
    }

    /**
     * A pose value for one component. The part sits at {@code base} (what the model's own animation left), the
     * previous pose (if it named this part) at {@code prev}, the new pose (if it names it) at {@code next};
     * {@code NaN} means "this pose does not name the part". The result is the previous state blended to the
     * next one by {@code w}.
     */
    public static float blendPose(float base, float prev, float next, float w) {
        float from = Float.isNaN(prev) ? base : prev;
        float to = Float.isNaN(next) ? base : next;
        return lerp(from, to, w);
    }

    /** A limb track's contribution: added to {@code current}, or, in set mode, replacing it. */
    public static float limb(float current, float value, boolean set) {
        return set ? value : current + value;
    }
}
