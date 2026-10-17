package com.hogwartsmagic.pensieve.scene;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks a parsed scene against the names that exist, so a typo gets a message naming its JSON path instead of
 * a crash or a silent no-op. Pure; one message per problem.
 */
public final class SceneValidator {
    private SceneValidator() {
    }

    public static List<String> validate(SceneDef scene, SceneNames names) {
        List<String> out = new ArrayList<>();
        float length = scene.length();
        if (length <= 0) out.add("length: must be greater than 0, got " + scene.length());

        if (scene.radius() > 200) out.add("radius: must be at most 200, got " + scene.radius());
        if (scene.fogFar() < 0 || scene.fogFar() > SceneNames.MAX_FOG_FAR) {
            out.add("fog_far: must be 1 to " + SceneNames.MAX_FOG_FAR + " blocks (or left out), got " + scene.fogFar());
        }

        Set<String> actorIds = new HashSet<>();
        for (int i = 0; i < scene.actors().size(); i++) {
            String id = scene.actors().get(i).id();
            if (!actorIds.add(id)) out.add("actors[" + i + "].id: duplicate actor id '" + id + "'");
        }

        SceneDef.SetDef set = scene.set();
        for (int i = 0; i < set.templates().size(); i++) {
            known(out, "set.templates[" + i + "].id", "template", set.templates().get(i).id(), names.templates());
        }
        for (int i = 0; i < set.schematics().size(); i++) {
            SceneDef.SchematicRef ref = set.schematics().get(i);
            if (!SceneNames.SCHEMATICS.contains(ref.id())) {
                out.add("set.schematics[" + i + "].id: unknown schematic '" + ref.id() + "' (expected one of "
                        + sorted(SceneNames.SCHEMATICS) + ")");
            }
            if (!SceneNames.SCHEMATIC_ROTATIONS.contains(ref.rotation())) {
                out.add("set.schematics[" + i + "].rotation: must be 0, 90, 180 or 270, got " + ref.rotation());
            }
        }
        for (int i = 0; i < set.fills().size(); i++) {
            known(out, "set.fills[" + i + "].block", "block", set.fills().get(i).block(), names.blocks());
        }
        for (int i = 0; i < set.disks().size(); i++) {
            known(out, "set.disks[" + i + "].block", "block", set.disks().get(i).block(), names.blocks());
        }
        for (int i = 0; i < set.features().size(); i++) {
            known(out, "set.features[" + i + "].id", "feature", set.features().get(i).id(), names.features());
        }

        extent(out, scene.radius(), set);

        for (int i = 0; i < scene.actors().size(); i++) {
            actor(out, scene, scene.actors().get(i), "actors[" + i + "]", actorIds, names, length);
        }

        for (int i = 0; i < scene.events().size(); i++) {
            event(out, scene.events().get(i), "events[" + i + "]", names, length);
        }

        for (int i = 0; i < scene.words().size(); i++) {
            SceneDef.WordDef w = scene.words().get(i);
            String p = "words[" + i + "]";
            time(out, p + ".t", w.t(), length);
            if (w.by() != null && !actorIds.contains(w.by())) {
                out.add(p + ".by: unknown actor '" + w.by() + "'");
            }
        }

        List<CameraKey> keys = scene.camera().keys();
        for (int i = 0; i < keys.size(); i++) {
            CameraKey k = keys.get(i);
            String p = "camera[" + i + "]";
            time(out, p + ".t", k.t(), length);
            if (k.lookAtActor() != null && !actorIds.contains(k.lookAtActor())) {
                out.add(p + ".look_at: unknown actor '" + k.lookAtActor() + "'");
            }
        }

        if (scene.endAction() != null && !SceneNames.END_ACTIONS.contains(scene.endAction())) {
            out.add("end_action: unknown end action '" + scene.endAction() + "' (expected one of "
                    + sorted(SceneNames.END_ACTIONS) + ")");
        }
        return out;
    }

    private static void actor(List<String> out, SceneDef scene, SceneDef.ActorDef a, String p, Set<String> actorIds,
                              SceneNames names, float length) {
        known(out, p + ".type", "entity", a.type(), names.entityTypes());
        if (a.skin() != null && a.skin().texture() != null) {
            known(out, p + ".skin.texture", "skin", a.skin().texture(), names.skins());
        }
        for (var item : a.items().entrySet()) {
            known(out, p + ".items." + item.getKey(), "item", item.getValue(), names.items());
        }
        track(out, p + ".path", a.path(), length);
        track(out, p + ".look", a.look(), length);
        for (var limb : a.limbs().entrySet()) {
            track(out, p + ".limbs." + limb.getKey() + ".rot", limb.getValue().rot(), length);
            track(out, p + ".limbs." + limb.getKey() + ".offset", limb.getValue().offset(), length);
        }
        for (int i = 0; i < a.poses().size(); i++) {
            SceneDef.Cue c = a.poses().get(i);
            String cp = p + ".poses[" + i + "]";
            time(out, cp + ".t", c.t(), length);
            if (!scene.poses().containsKey(c.name()) && !SceneNames.POSE_PRESETS.contains(c.name())) {
                out.add(cp + ".name: unknown pose '" + c.name() + "' (define it under poses, or use a preset: "
                        + sorted(SceneNames.POSE_PRESETS) + ")");
            }
        }
        for (int i = 0; i < a.anims().size(); i++) {
            time(out, p + ".anims[" + i + "].t", a.anims().get(i).t(), length);
        }
        for (int i = 0; i < a.actions().size(); i++) {
            SceneDef.ActionCue c = a.actions().get(i);
            String cp = p + ".actions[" + i + "]";
            time(out, cp + ".t", c.t(), length);
            if (!SceneNames.ACTIONS.contains(c.action())) {
                out.add(cp + ".do: unknown action '" + c.action() + "' (expected one of "
                        + sorted(SceneNames.ACTIONS) + ")");
            }
            if ("cast".equals(c.action())) {
                if (c.spell() == null) {
                    out.add(cp + ".spell: a cast action needs a spell");
                } else if (!names.spells().contains(c.spell()) && !SceneNames.MEMORY_ONLY_SPELLS.contains(c.spell())) {
                    out.add(cp + ".spell: unknown spell '" + c.spell() + "'");
                } else if (!SceneNames.SPELL_VISUALS.contains(c.spell())) {
                    out.add(cp + ".spell: no memory visual for '" + c.spell() + "'");
                }
            }
            if (c.item() != null) known(out, cp + ".item", "item", c.item(), names.items());
            if (c.target() != null && c.target().actor() != null && !actorIds.contains(c.target().actor())) {
                out.add(cp + ".target: unknown actor '" + c.target().actor() + "'");
            }
        }
    }

    private static void event(List<String> out, SceneDef.EventDef e, String p, SceneNames names, float length) {
        time(out, p + ".t", e.t(), length);
        if (!SceneNames.EVENT_KINDS.contains(e.kind())) {
            out.add(p + ".kind: unknown event kind '" + e.kind() + "' (expected one of "
                    + sorted(SceneNames.EVENT_KINDS) + ")");
            return;
        }
        if (e.id() == null) {
            out.add(p + ".id: missing required key for a " + e.kind() + " event");
            return;
        }
        switch (e.kind()) {
            case "block" -> known(out, p + ".id", "block", e.id(), names.blocks());
            case "sound" -> known(out, p + ".id", "sound", e.id(), names.sounds());
            case "music", "voice" -> {
                known(out, p + ".id", "sound", e.id(), names.sounds());
                float v = e.volume();
                if (Float.isNaN(v) || v < 0 || v > SceneNames.MAX_CUE_VOLUME) {
                    out.add(p + ".volume: " + v + " is outside 0 to " + SceneNames.MAX_CUE_VOLUME);
                }
            }
            case "particles" -> known(out, p + ".id", "particle", e.id(), names.particles());
            default -> {
                if (!SceneNames.EFFECTS.contains(e.id())) {
                    out.add(p + ".id: unknown effect '" + e.id() + "' (expected one of " + sorted(SceneNames.EFFECTS) + ")");
                }
            }
        }
    }

    private static void track(List<String> out, String p, Track t, float length) {
        for (int i = 0; i < t.keys().size(); i++) {
            // Keys are stored sorted by time, so the index may differ from the JSON order; the time value says which.
            time(out, p + "[" + i + " (sorted)].t", t.keys().get(i).t(), length);
        }
    }

    private static void time(List<String> out, String p, float t, float length) {
        if (Float.isNaN(t) || Float.isInfinite(t)) {
            out.add(p + ": time is not a finite number");
        } else if (t < 0 || t > length) {
            out.add(p + ": time " + t + " is outside the scene (0 to " + length + ")");
        }
    }

    /** Largest single fill, in blocks. */
    public static final long MAX_FILL_BLOCKS = 2_000_000L;

    /** Everything in the set must sit within the scene radius (horizontally) of the region origin. */
    private static void extent(List<String> out, int radius, SceneDef.SetDef set) {
        for (int i = 0; i < set.templates().size(); i++) {
            int[] at = set.templates().get(i).at();
            if (beyond(radius, at[0], at[0], at[2], at[2])) {
                out.add("set.templates[" + i + "]: anchor extends beyond radius " + radius);
            }
        }
        for (int i = 0; i < set.schematics().size(); i++) {
            int[] at = set.schematics().get(i).at();
            if (beyond(radius, at[0], at[0], at[2], at[2])) {
                out.add("set.schematics[" + i + "]: anchor extends beyond radius " + radius);
            }
        }
        for (int i = 0; i < set.fills().size(); i++) {
            SceneDef.Fill f = set.fills().get(i);
            int x0 = Math.min(f.from()[0], f.to()[0]), x1 = Math.max(f.from()[0], f.to()[0]);
            int z0 = Math.min(f.from()[2], f.to()[2]), z1 = Math.max(f.from()[2], f.to()[2]);
            int y0 = Math.min(f.from()[1], f.to()[1]), y1 = Math.max(f.from()[1], f.to()[1]);
            if (beyond(radius, x0, x1, z0, z1)) {
                out.add("set.fills[" + i + "]: extends beyond radius " + radius);
            }
            long blocks = (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
            if (blocks > MAX_FILL_BLOCKS) {
                out.add("set.fills[" + i + "]: fill too large (" + blocks + " blocks, max " + MAX_FILL_BLOCKS + ")");
            }
        }
        for (int i = 0; i < set.disks().size(); i++) {
            SceneDef.Disk d = set.disks().get(i);
            int cx = d.center()[0], cz = d.center()[2], r = Math.abs(d.radius());
            if (beyond(radius, cx - r, cx + r, cz - r, cz + r)) {
                out.add("set.disks[" + i + "]: extends beyond radius " + radius);
            }
        }
        for (int i = 0; i < set.features().size(); i++) {
            int[] at = set.features().get(i).at();
            if (beyond(radius, at[0], at[0], at[2], at[2])) {
                out.add("set.features[" + i + "]: anchor extends beyond radius " + radius);
            }
        }
    }

    private static boolean beyond(int radius, int x0, int x1, int z0, int z1) {
        return x0 < -radius || x1 > radius || z0 < -radius || z1 > radius;
    }

    private static void known(List<String> out, String p, String what, String id, Set<String> valid) {
        if (id == null || !valid.contains(id)) out.add(p + ": unknown " + what + " '" + id + "'");
    }

    private static List<String> sorted(Set<String> s) {
        return s.stream().sorted().toList();
    }
}
