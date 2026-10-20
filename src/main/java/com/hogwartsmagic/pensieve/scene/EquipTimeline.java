package com.hogwartsmagic.pensieve.scene;

import java.util.List;
import java.util.Map;

/**
 * What an actor wears in a slot at scene time {@code t}: the last {@code equip} for that slot (or, for the
 * mainhand, {@code hold}) at or before {@code t}, else the actor's base {@code items}. A pure function of time, so
 * a late joiner sees the same item as one who watched from the start. Pure; no Minecraft types.
 */
public final class EquipTimeline {
    private EquipTimeline() {
    }

    /** The item id in {@code slot} at {@code t} ({@code "minecraft:air"} when cleared), or null when empty/unknown. */
    public static String itemAt(List<SceneDef.ActionCue> actions, Map<String, String> base, String slot, float t) {
        String item = base.get(slot);
        float best = Float.NEGATIVE_INFINITY;
        for (SceneDef.ActionCue c : actions) {
            if (c.t() > t || c.t() < best) continue;
            boolean hit = ("equip".equals(c.action()) && slot.equals(c.slot()))
                    || ("hold".equals(c.action()) && "mainhand".equals(slot));
            if (!hit) continue;
            best = c.t();
            item = c.item();
        }
        return item;
    }
}
