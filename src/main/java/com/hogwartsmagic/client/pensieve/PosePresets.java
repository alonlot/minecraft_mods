package com.hogwartsmagic.client.pensieve;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The built-in poses a scene may cue by name. Rotations are degrees [x,y,z] and are absolute (they replace the
 * part rotation, blended in); offsets are model pixels and are added to the part position. Written for the
 * player model: another model simply ignores parts it does not have.
 *
 * <p>In model space +y is down and -z is forward, so a negative x rotation lifts an arm forward and a positive
 * one leans a part forward.
 */
public final class PosePresets {
    public record Preset(Map<String, float[]> rot, Map<String, float[]> offset) {
    }

    private static final Map<String, Preset> ALL = new LinkedHashMap<>();

    static {
        add("wand_raised", rot("right_arm", -150, 0, 10));
        add("wand_point", rot("right_arm", -90, -5, 0));
        add("look_down", rot("head", 40, 0, 0));
        add("arms_out", rot("right_arm", 0, 0, 90, "left_arm", 0, 0, -90));
        add("hands_up", rot("right_arm", -170, 0, 15, "left_arm", -170, 0, -15));
        add("cower", rot("head", 35, 0, 0, "body", 20, 0, 0, "right_arm", -140, 20, -25, "left_arm", -140, -20, 25));

        // Knees down: the whole body drops 10 px onto the ground and the legs trail behind it.
        Map<String, float[]> kneelOff = new LinkedHashMap<>();
        for (String p : new String[]{"head", "hat", "body", "jacket", "right_arm", "left_arm", "right_sleeve",
                "left_sleeve", "right_leg", "left_leg", "right_pants", "left_pants"}) {
            kneelOff.put(p, new float[]{0, 10, 0});
        }
        add("kneel", rot("right_leg", 85, 0, 3, "left_leg", 85, 0, -3, "right_pants", 85, 0, 3, "left_pants", 85, 0, -3,
                "right_arm", -25, 0, 0, "left_arm", -25, 0, 0, "head", 10, 0, 0), kneelOff);

        // Bent forward at the hips, about 70 degrees, head down over the water, hands on the rim.
        Map<String, float[]> bendOff = new LinkedHashMap<>();
        for (String p : new String[]{"body", "jacket", "head", "hat"}) bendOff.put(p, new float[]{0, 7.9f, -11.3f});
        for (String p : new String[]{"right_arm", "left_arm", "right_sleeve", "left_sleeve"}) {
            bendOff.put(p, new float[]{0, 6.6f, -9.4f});
        }
        add("bent_over_basin", rot("body", 70, 0, 0, "jacket", 70, 0, 0, "head", 45, 0, 0, "hat", 45, 0, 0,
                "right_arm", -30, 0, 8, "left_arm", -30, 0, -8, "right_sleeve", -30, 0, 8, "left_sleeve", -30, 0, -8),
                bendOff);
    }

    private PosePresets() {
    }

    private static void add(String name, Map<String, float[]> rot) {
        ALL.put(name, new Preset(rot, Map.of()));
    }

    private static void add(String name, Map<String, float[]> rot, Map<String, float[]> offset) {
        ALL.put(name, new Preset(rot, offset));
    }

    /** Pairs of part name then x, y, z degrees. */
    private static Map<String, float[]> rot(Object... nameThenXyz) {
        Map<String, float[]> m = new LinkedHashMap<>();
        for (int i = 0; i < nameThenXyz.length; i += 4) {
            m.put((String) nameThenXyz[i], new float[]{num(nameThenXyz[i + 1]), num(nameThenXyz[i + 2]), num(nameThenXyz[i + 3])});
        }
        return m;
    }

    private static float num(Object o) {
        return ((Number) o).floatValue();
    }

    /** The built-in pose called {@code name}, or null. */
    public static Preset get(String name) {
        return ALL.get(name);
    }

    public static Set<String> names() {
        return Collections.unmodifiableSet(ALL.keySet());
    }
}
