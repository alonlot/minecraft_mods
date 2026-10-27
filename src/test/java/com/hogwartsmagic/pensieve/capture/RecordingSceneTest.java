package com.hogwartsmagic.pensieve.capture;

import com.hogwartsmagic.pensieve.scene.SceneDef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingSceneTest {
    private static final UUID OWNER = UUID.randomUUID();

    private static Recording.Track track(int actor, String type, String name, boolean player,
                                         List<Recording.Act> acts) {
        float[] a = new float[3];
        return new Recording.Track(actor, type, UUID.randomUUID(), name, player, null, null, 0, 2, 0,
                a, a, a, a, a, a, a, List.of(), List.of(), acts);
    }

    private static Recording recording(List<Recording.Track> tracks, List<Recording.Beam> beams,
                                       List<Recording.Cast> casts, List<Recording.Chat> chat) {
        return new Recording(UUID.randomUUID(), OWNER, "Alon", "A memory", 3, "minecraft:overworld",
                new int[] {0, 64, 0}, 32, 1200, 30000, null, tracks, beams, casts, List.of(), List.of(),
                List.of(), chat);
    }

    private static Recording basic() {
        return recording(List.of(track(0, "minecraft:player", "Alon", true, List.of()),
                track(1, "minecraft:player", "Bea", true, List.of()),
                track(2, "minecraft:zombie", "Zombie", false, List.of())),
                List.of(), List.of(), List.of());
    }

    @Test
    void ownerIsNamedOwnerAndPlayersArePlayers() {
        SceneDef s = RecordingScene.toScene(basic());
        assertEquals(List.of("owner", "p_Bea", "e2"), s.actors().stream().map(SceneDef.ActorDef::id).toList());
        assertEquals(List.of("player", "player", "minecraft:zombie"),
                s.actors().stream().map(SceneDef.ActorDef::type).toList());
        assertEquals("Bea", s.actors().get(1).skin().username());
        assertEquals(6000, s.set().timeOfDay());
        assertEquals(1200, s.length());
    }

    @Test
    void repeatedPlayerNamesGetTheActorNumber() {
        Recording r = recording(List.of(track(0, "minecraft:player", "Alon", true, List.of()),
                track(1, "minecraft:player", "Bea", true, List.of()),
                track(4, "minecraft:player", "Bea", true, List.of())), List.of(), List.of(), List.of());
        assertEquals(List.of("owner", "p_Bea_1", "p_Bea_4"),
                RecordingScene.toScene(r).actors().stream().map(SceneDef.ActorDef::id).toList());
    }

    @Test
    void actsBecomeActions() {
        Recording r = recording(List.of(track(0, "minecraft:player", "Alon", true,
                List.of(new Recording.Act(10, Recording.ACT_SWING_MAIN), new Recording.Act(12, Recording.ACT_HURT),
                        new Recording.Act(14, Recording.ACT_SWING_OFF), new Recording.Act(40, Recording.ACT_DIE)))),
                List.of(), List.of(new Recording.Cast(20, 0, "hogwartsmagic:stupefy", new float[] {1, 2, 3})),
                List.of());
        List<SceneDef.ActionCue> a = RecordingScene.toScene(r).actors().get(0).actions();
        assertEquals(List.of("swing", "hurt", "swing", "cast", "die"), a.stream().map(SceneDef.ActionCue::action).toList());
        assertEquals(List.of(10f, 12f, 14f, 20f, 40f), a.stream().map(SceneDef.ActionCue::t).toList());
        assertEquals("offhand", a.get(2).slot());
        assertEquals("hogwartsmagic:stupefy", a.get(3).spell());
        assertArrayEquals(new float[] {1, 2, 3}, a.get(3).target().point());
    }

    @Test
    void beamBecomesRecordedBeam() {
        float[] start = {0, 1, 2};
        float[] end = {5, 6, 7};
        Recording r = recording(List.of(), List.of(new Recording.Beam(5, 17, "hogwartsmagic:stupefy", start, end, -1)),
                List.of(), List.of());
        SceneDef.EventDef e = RecordingScene.toScene(r).events().get(0);
        assertEquals("recorded_beam", e.kind());
        assertEquals(5f, e.t());
        assertEquals(12, e.duration());
        assertArrayEquals(end, e.box()[1]);
    }

    @Test
    void chatEscapesPlaceholders() {
        Recording r = recording(List.of(), List.of(), List.of(),
                List.of(new Recording.Chat(7, "Bea", "hi {viewer}")));
        String text = RecordingScene.toScene(r).words().get(0).text();
        assertTrue(text.contains("{​viewer}"));
    }

    @Test
    void sceneIdRoundTrips() {
        UUID id = UUID.randomUUID();
        assertEquals(id, RecordingScene.idOf(RecordingScene.sceneId(id)).orElseThrow());
        assertTrue(RecordingScene.idOf("story/x").isEmpty());
        assertTrue(RecordingScene.idOf("recorded/nope").isEmpty());
    }
}
