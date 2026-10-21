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
     * {@code type} is an entity id or "player"; {@code look} values are [yaw, pitch], or [yaw, pitch, roll] when any
     * key gives a roll. {@code life} (players only, default true) turns on the procedural life layer of
     * {@link HumanMotion}. {@code block} is the block state a {@code memory_block} actor shows (null otherwise).
     * {@code ride} is the id of a vehicle actor ({@link Vehicle}: a {@code memory_rowboat} or a memory broom) this one
     * rides (null otherwise), from {@code rideFrom} until (not including) {@code rideUntil} (infinite when the scene
     * gave no window): inside the window its {@code path} is an offset in the vehicle's frame and its look yaw is
     * relative to the vehicle's (see {@link Rowboat}, {@link BroomFlight}); outside it both are world space.
     */
    public record ActorDef(String id, String type, SkinRef skin, String name, Map<String, String> items,
                           Track path, Track look, Map<String, PartTrack> limbs, List<Cue> poses,
                           List<Cue> anims, List<ActionCue> actions, float scale, boolean life, String block,
                           String ride, float rideFrom, float rideUntil) {
        /** An actor that rides {@code ride} for the whole scene (no window). */
        public ActorDef(String id, String type, SkinRef skin, String name, Map<String, String> items,
                        Track path, Track look, Map<String, PartTrack> limbs, List<Cue> poses,
                        List<Cue> anims, List<ActionCue> actions, float scale, boolean life, String block,
                        String ride) {
            this(id, type, skin, name, items, path, look, limbs, poses, anims, actions, scale, life, block, ride,
                    Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
        }

        /** An actor that rides nothing. */
        public ActorDef(String id, String type, SkinRef skin, String name, Map<String, String> items,
                        Track path, Track look, Map<String, PartTrack> limbs, List<Cue> poses,
                        List<Cue> anims, List<ActionCue> actions, float scale, boolean life, String block) {
            this(id, type, skin, name, items, path, look, limbs, poses, anims, actions, scale, life, block, null);
        }

        /** An actor with no block. */
        public ActorDef(String id, String type, SkinRef skin, String name, Map<String, String> items,
                        Track path, Track look, Map<String, PartTrack> limbs, List<Cue> poses,
                        List<Cue> anims, List<ActionCue> actions, float scale, boolean life) {
            this(id, type, skin, name, items, path, look, limbs, poses, anims, actions, scale, life, null);
        }

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
     * {@code box} is the letter_flood room, two scene-relative corners {@code {{x1,y1,z1},{x2,y2,z2}}}, null when absent.
     * {@code loop} is for {@code sfx} cues only: the sound repeats seamlessly until {@code duration} ticks have passed
     * (or the scene ends when {@code duration} is 0) and then fades out.
     */
    public record EventDef(float t, String kind, String id, float[] at, float volume, float pitch, int count,
                           float[] spread, int duration, float yaw, String style, float intensity, float[][] box,
                           boolean loop, float[][] rows) {
        /**
         * An event with no {@code rows} (a {@code crowd}'s seat-top segments: six numbers each, a seventh 1 for a
         * standing row; see {@link Crowd}).
         */
        public EventDef(float t, String kind, String id, float[] at, float volume, float pitch, int count,
                        float[] spread, int duration, float yaw, String style, float intensity, float[][] box,
                        boolean loop) {
            this(t, kind, id, at, volume, pitch, count, spread, duration, yaw, style, intensity, box, loop, null);
        }

        /** An event that does not loop. */
        public EventDef(float t, String kind, String id, float[] at, float volume, float pitch, int count,
                        float[] spread, int duration, float yaw, String style, float intensity, float[][] box) {
            this(t, kind, id, at, volume, pitch, count, spread, duration, yaw, style, intensity, box, false);
        }

        /** An event with no {@code box}. */
        public EventDef(float t, String kind, String id, float[] at, float volume, float pitch, int count,
                        float[] spread, int duration, float yaw, String style, float intensity) {
            this(t, kind, id, at, volume, pitch, count, spread, duration, yaw, style, intensity, null);
        }
    }

    public record WordDef(float t, String by, String text, boolean narration, int duration, float size,
                          boolean subtitle, String speaker) {
    }
}
