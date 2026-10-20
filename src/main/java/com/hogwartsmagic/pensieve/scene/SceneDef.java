package com.hogwartsmagic.pensieve.scene;

import java.util.List;
import java.util.Map;

/** A parsed Pensieve memory scene. Pure data; no Minecraft types. */
public record SceneDef(String id, String title, int length, int radius, SetDef set,
                       Map<String, Map<String, float[]>> poses, List<ActorDef> actors,
                       List<EventDef> events, List<WordDef> words, CameraTrack camera, String endAction,
                       int fogFar, String next) {

    /** A scene with no follow-on scene ({@code next} null). */
    public SceneDef(String id, String title, int length, int radius, SetDef set,
                    Map<String, Map<String, float[]>> poses, List<ActorDef> actors,
                    List<EventDef> events, List<WordDef> words, CameraTrack camera, String endAction,
                    int fogFar) {
        this(id, title, length, radius, set, poses, actors, events, words, camera, endAction, fogFar, null);
    }

    /** A scene with the default fog ({@code fogFar} 0: the fog sphere follows {@code radius}). */
    public SceneDef(String id, String title, int length, int radius, SetDef set,
                    Map<String, Map<String, float[]>> poses, List<ActorDef> actors,
                    List<EventDef> events, List<WordDef> words, CameraTrack camera, String endAction) {
        this(id, title, length, radius, set, poses, actors, events, words, camera, endAction, 0, null);
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

    /**
     * {@code type} is an entity id or "player"; {@code look} values are [yaw, pitch]. {@code life} (players only,
     * default true) turns on the procedural life layer of {@link HumanMotion}.
     */
    public record ActorDef(String id, String type, SkinRef skin, String name, Map<String, String> items,
                           Track path, Track look, Map<String, PartTrack> limbs, List<Cue> poses,
                           List<Cue> anims, List<ActionCue> actions, float scale, boolean life) {
        /** A living actor (life on). */
        public ActorDef(String id, String type, SkinRef skin, String name, Map<String, String> items,
                        Track path, Track look, Map<String, PartTrack> limbs, List<Cue> poses,
                        List<Cue> anims, List<ActionCue> actions, float scale) {
            this(id, type, skin, name, items, path, look, limbs, poses, anims, actions, scale, true);
        }

        /** An actor at normal size (scale 1). */
        public ActorDef(String id, String type, SkinRef skin, String name, Map<String, String> items,
                        Track path, Track look, Map<String, PartTrack> limbs, List<Cue> poses,
                        List<Cue> anims, List<ActionCue> actions) {
            this(id, type, skin, name, items, path, look, limbs, poses, anims, actions, 1f);
        }
    }

    /** {@code viewer} means the watching player's own skin (excludes texture and username). */
    public record SkinRef(String texture, String username, boolean slim, boolean viewer) {
        public SkinRef(String texture, String username, boolean slim) {
            this(texture, username, slim, false);
        }
    }

    public record Cue(float t, String name, float blend) {
    }

    /** Actions: swing, sneak, stand, hurt, die, vanish, appear, stone, unstone, cast, use_item, hold, ignite, equip ({@code slot} + {@code item}). */
    public record ActionCue(float t, String action, String spell, Target target, String item, String slot) {
    }

    public record Target(String actor, float[] point) {
    }

    /**
     * Kinds: block, sound, particles, effect, music, voice, sfx. {@code yaw} is for effects only ({@code Float.NaN} when absent);
     * {@code count} is -1 and {@code spread} null when the scene did not give them.
     * {@code at} may be fractional (an effect centred on a half block); a {@code block} event's must be whole, which the
     * validator checks. {@code style} is the enchanted_ceiling look ({@code "storm"} or {@code "night"}), null when absent.
     * {@code intensity} is the light_rays brightness multiplier, NaN when absent (default 1).
     */
    public record EventDef(float t, String kind, String id, float[] at, float volume, float pitch, int count,
                           float[] spread, int duration, float yaw, String style, float intensity) {
    }

    public record WordDef(float t, String by, String text, boolean narration, int duration, float size) {
    }
}
