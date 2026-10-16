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

        Set<String> actorIds = new HashSet<>();
        for (int i = 0; i < scene.actors().size(); i++) {
            String id = scene.actors().get(i).id();
            if (!actorIds.add(id)) out.add("actors[" + i + "].id: duplicate actor id '" + id + "'");
        }

        SceneDef.SetDef set = scene.set();
        for (int i = 0; i < set.templates().size(); i++) {
            known(out, "set.templates[" + i + "].id", "template", set.templates().get(i).id(), names.templates());
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
            if ("cast".equals(c.action()) && c.spell() != null) {
                known(out, cp + ".spell", "spell", c.spell(), names.spells());
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
            time(out, p + "[" + i + "].t", t.keys().get(i).t(), length);
        }
    }

    private static void time(List<String> out, String p, float t, float length) {
        if (Float.isNaN(t) || Float.isInfinite(t)) {
            out.add(p + ": time is not a finite number");
        } else if (t < 0 || t > length) {
            out.add(p + ": time " + t + " is outside the scene (0 to " + length + ")");
        }
    }

    private static void known(List<String> out, String p, String what, String id, Set<String> valid) {
        if (id == null || !valid.contains(id)) out.add(p + ": unknown " + what + " '" + id + "'");
    }

    private static List<String> sorted(Set<String> s) {
        return s.stream().sorted().toList();
    }
}
