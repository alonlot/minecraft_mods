package com.hogwartsmagic.pensieve.scene;

import java.util.List;
import java.util.Map;

/** A parsed Pensieve memory scene. Pure data; no Minecraft types. */
public record SceneDef(String id, String title, int length, int radius, SetDef set,
                       Map<String, Map<String, float[]>> poses, List<ActorDef> actors,
                       List<EventDef> events, List<WordDef> words, CameraTrack camera, String endAction,
                       int fogFar) {

    /** A scene with the default fog ({@code fogFar} 0: the fog sphere follows {@code radius}). */
    public SceneDef(String id, String title, int length, int radius, SetDef set,
                    Map<String, Map<String, float[]>> poses, List<ActorDef> actors,
                    List<EventDef> events, List<WordDef> words, CameraTrack camera, String endAction) {
        this(id, title, length, radius, set, poses, actors, events, words, camera, endAction, 0);
    }

    public record SetDef(List<TemplateRef> templates, List<SchematicRef> schematics, List<Fill> fills,
                         List<Disk> disks, List<FeatureRef> features, long timeOfDay, int buildTicks) {
        /** A set with no schematics. */
        public SetDef(List<TemplateRef> templates, List<Fill> fills, List<Disk> disks,
                      List<FeatureRef> features, long timeOfDay, int buildTicks) {
            this(templates, List.of(), fills, disks, features, timeOfDay, buildTicks);
        }
    }

    public record TemplateRef(String id, int[] at) {
    }

    /**
     * A bundled schematic (see {@link SceneNames#SCHEMATICS}) whose rotated footprint's min corner is at {@code at};
     * {@code rotation} is 0, 90, 180 or 270 degrees clockwise seen from above.
     */
    public record SchematicRef(String id, int[] at, int rotation) {
    }

    public record Fill(String block, int[] from, int[] to) {
    }

    public record Disk(String block, int[] center, int radius) {
    }

    public record FeatureRef(String id, int[] at) {
    }

    /** {@code type} is an entity id or "player"; {@code look} values are [yaw, pitch]. */
    public record ActorDef(String id, String type, SkinRef skin, String name, Map<String, String> items,
                           Track path, Track look, Map<String, PartTrack> limbs, List<Cue> poses,
                           List<Cue> anims, List<ActionCue> actions) {
    }

    public record SkinRef(String texture, String username, boolean slim) {
    }

    public record Cue(float t, String name, float blend) {
    }

    /** Actions: swing, sneak, stand, hurt, die, vanish, appear, stone, unstone, cast, use_item, hold. */
    public record ActionCue(float t, String action, String spell, Target target, String item) {
    }

    public record Target(String actor, float[] point) {
    }

    /** Kinds: block, sound, particles, effect. */
    public record EventDef(float t, String kind, String id, int[] at, float volume, float pitch, int count,
                           float[] spread, int duration) {
    }

    public record WordDef(float t, String by, String text, boolean narration, int duration) {
    }
}
