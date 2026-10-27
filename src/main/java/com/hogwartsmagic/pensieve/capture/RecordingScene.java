package com.hogwartsmagic.pensieve.capture;

import com.hogwartsmagic.pensieve.scene.CameraTrack;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.Track;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    private static boolean isOwner(Recording r, Recording.Track t) {
        return t.uuid() != null && t.uuid().equals(r.owner());
    }

    /**
     * The actor id of every track, in track order: {@code "owner"} for the track whose uuid is the recording's owner,
     * {@code "p_" + name} for other players ({@code "p_" + name + "_" + actor} when a player name repeats), and
     * {@code "e" + actor} for everyone else.
     */
    public static Map<String, Recording.Track> actorIds(Recording r) {
        Map<String, Integer> nameCounts = new HashMap<>();
        for (Recording.Track t : r.tracks()) {
            if (t.player() && !isOwner(r, t)) nameCounts.merge(t.name(), 1, Integer::sum);
        }
        Map<String, Recording.Track> ids = new LinkedHashMap<>();
        for (Recording.Track t : r.tracks()) {
            String id;
            if (isOwner(r, t)) {
                id = "owner";
            } else if (t.player()) {
                id = "p_" + t.name() + (nameCounts.get(t.name()) > 1 ? "_" + t.actor() : "");
            } else {
                id = "e" + t.actor();
            }
            ids.put(id, t);
        }
        return ids;
    }

    public static SceneDef toScene(Recording r) {
        List<SceneDef.ActorDef> actors = new ArrayList<>();
        actorIds(r).forEach((id, t) -> actors.add(actor(r, t, id)));

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
        return new SceneDef.ActorDef(id, t.player() ? "player" : t.type(), skin,
                // The scene shows a name tag for any non-blank name, so mobs stay unnamed: a Track does not say
                // whether a mob had a custom name (Task 4 would have to store it).
                t.player() ? escape(t.name()) : "", Map.of(),
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
