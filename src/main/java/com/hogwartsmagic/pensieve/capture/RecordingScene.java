package com.hogwartsmagic.pensieve.capture;

import com.hogwartsmagic.pensieve.scene.CameraTrack;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.Track;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Turns a {@link Recording} into the {@link SceneDef} a story memory would be. Pure. */
public final class RecordingScene {
    public static final String PREFIX = "recorded/";
    /** U+200B, so a typed {@code {viewer}} is never filled in. */
    private static final String ZWSP = "​";

    private RecordingScene() {
    }

    public static String sceneId(UUID id) {
        return PREFIX + id;
    }

    public static Optional<UUID> idOf(String sceneId) {
        if (sceneId == null || !sceneId.startsWith(PREFIX)) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(sceneId.substring(PREFIX.length())));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * The actor id of one track, ignoring repeated names. The owner's track is the player track with actor 0
     * (the capture always puts the owner first). {@link #toScene} also disambiguates repeated player names.
     */
    public static String actorId(Recording.Track t) {
        if (t.player() && t.actor() == 0) return "owner";
        return t.player() ? "p_" + t.name() : "e" + t.actor();
    }

    private static String actorId(Recording.Track t, Map<String, Integer> nameCounts) {
        String id = actorId(t);
        if (t.player() && t.actor() != 0 && nameCounts.getOrDefault(t.name(), 0) > 1) {
            return id + "_" + t.actor();
        }
        return id;
    }

    public static SceneDef toScene(Recording r) {
        Map<String, Integer> nameCounts = new HashMap<>();
        for (Recording.Track t : r.tracks()) {
            if (t.player() && t.actor() != 0) nameCounts.merge(t.name(), 1, Integer::sum);
        }
        List<SceneDef.ActorDef> actors = new ArrayList<>();
        for (Recording.Track t : r.tracks()) {
            actors.add(actor(r, t, actorId(t, nameCounts)));
        }

        List<SceneDef.EventDef> events = new ArrayList<>();
        for (Recording.Sound s : r.sounds()) {
            events.add(new SceneDef.EventDef(s.tick(), "sound", s.id(), new float[] {s.x(), s.y(), s.z()},
                    s.volume(), s.pitch(), -1, null, 0, Float.NaN, null, Float.NaN));
        }
        for (Recording.Beam b : r.beams()) {
            events.add(new SceneDef.EventDef(b.from(), "recorded_beam", b.spell(), b.start(), 1, 1, -1, null,
                    b.to() - b.from(), Float.NaN, null, Float.NaN, new float[][] {b.start(), b.end()}));
        }
        events.sort(Comparator.comparingDouble(SceneDef.EventDef::t));

        List<SceneDef.WordDef> words = new ArrayList<>();
        for (Recording.Chat c : r.chat()) {
            words.add(new SceneDef.WordDef(c.tick(), c.sender(), escape(c.text()), false, 0, 1f, false, null));
        }

        SceneDef.SetDef set = new SceneDef.SetDef(List.of(), List.of(), List.of(), List.of(),
                r.dayTime() % 24000, 60);
        return new SceneDef(sceneId(r.id()), r.title(), r.length(), r.radius(), set, Map.of(), actors, events,
                words, CameraTrack.EMPTY, null, 0, null);
    }

    private static SceneDef.ActorDef actor(Recording r, Recording.Track t, String id) {
        List<SceneDef.ActionCue> actions = new ArrayList<>();
        for (Recording.Act a : t.acts()) {
            switch (a.kind()) {
                case Recording.ACT_SWING_MAIN -> actions.add(cue(a.tick(), "swing", null, null, null));
                case Recording.ACT_SWING_OFF -> actions.add(cue(a.tick(), "swing", null, null, "offhand"));
                case Recording.ACT_HURT -> actions.add(cue(a.tick(), "hurt", null, null, null));
                case Recording.ACT_DIE -> actions.add(cue(a.tick(), "die", null, null, null));
                default -> {
                }
            }
        }
        for (Recording.Cast c : r.casts()) {
            if (c.actor() == t.actor()) {
                actions.add(cue(c.tick(), "cast", c.spell(), new SceneDef.Target(null, c.target()), null));
            }
        }
        actions.sort(Comparator.comparingDouble(SceneDef.ActionCue::t));
        SceneDef.SkinRef skin = t.player() ? new SceneDef.SkinRef(null, t.name(), false) : null;
        return new SceneDef.ActorDef(id, t.player() ? "player" : t.type(), skin, t.name(), Map.of(),
                Track.EMPTY, Track.EMPTY, Map.of(), List.of(), List.of(), actions, 1f, false);
    }

    private static SceneDef.ActionCue cue(float t, String action, String spell, SceneDef.Target target,
                                          String slot) {
        return new SceneDef.ActionCue(t, action, spell, target, null, slot);
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("{", "{" + ZWSP);
    }
}
