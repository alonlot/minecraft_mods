package com.hogwartsmagic.pensieve.scene;

import java.util.Set;

/**
 * The closed vocabularies a scene may name, handed to {@link SceneValidator}. Pure: the command side fills the
 * sets from the live registries, the tests fill them from source files.
 *
 * <p>{@code spells} is the set of spell ids a {@code cast} action may use (the ids that have a beam visual).
 * Ids are written the way scenes write them: namespaced for blocks, items, sounds, particles, entity types,
 * templates and features; bare for spells; skins as listed in {@code _skins.json}.
 */
public record SceneNames(Set<String> entityTypes, Set<String> spells, Set<String> sounds, Set<String> blocks,
                         Set<String> items, Set<String> templates, Set<String> features, Set<String> particles,
                         Set<String> skins) {
    /** Actor actions a scene may use. */
    public static final Set<String> ACTIONS = Set.of("swing", "sneak", "stand", "hurt", "die", "vanish", "appear",
            "stone", "unstone", "cast", "use_item", "hold", "ignite", "equip");
    /** Slots an {@code equip} action may fill. */
    public static final Set<String> EQUIP_SLOTS = Set.of("head", "mainhand", "offhand", "chest", "legs", "feet");
    /** Range of a narration word's {@code size}. */
    public static final float MIN_WORD_SIZE = 1f, MAX_WORD_SIZE = 4f;
    /** Largest {@code volume} a {@code music}, {@code voice} or {@code sfx} cue may ask for. */
    public static final float MAX_CUE_VOLUME = 4f;
    /** Event kinds. */
    public static final Set<String> EVENT_KINDS = Set.of("block", "sound", "particles", "effect", "music", "voice", "sfx");
    /** The actor type that shows one block state (its {@code block} key), and the only one a look {@code roll} turns. */
    public static final String BLOCK_ACTOR = "hogwartsmagic:memory_block";
    /** The rowing boat actor: the only type an actor may {@code ride} (see {@link Rowboat}). */
    public static final String ROWBOAT_ACTOR = "hogwartsmagic:memory_rowboat";
    /** A rain box's half-size range, each axis, in blocks. */
    public static final float MIN_RAIN_SPREAD = 1f, MAX_RAIN_SPREAD = 100f;
    /** The letter_flood effect's letter count (1 to this) and each room half-size (blocks). */
    public static final int MAX_FLOOD_LETTERS = 400;
    public static final float MIN_FLOOD_SPREAD = 0.5f, MAX_FLOOD_SPREAD = 12f;
    /** An enchanted_ceiling's half-size along x and z, in blocks. */
    public static final float MIN_CEILING_SPREAD = 1f, MAX_CEILING_SPREAD = 100f;
    /** A light_rays' doorway half-width, height and throw length, in blocks. */
    public static final float MIN_RAYS_HALF_WIDTH = 0.5f, MAX_RAYS_HALF_WIDTH = 12f;
    public static final float MIN_RAYS_HEIGHT = 1f, MAX_RAYS_HEIGHT = 24f;
    public static final float MIN_RAYS_LENGTH = 1f, MAX_RAYS_LENGTH = 60f;
    /** A floating_candles half-box, each axis, in blocks, and the most candles. */
    public static final float MIN_CANDLES_SPREAD = 0.5f, MAX_CANDLES_SPREAD = 100f;
    public static final int MAX_CANDLES = 1500;
    /** Scripted effects an {@code effect} event may name. */
    public static final Set<String> EFFECTS = Set.of("summoning_ritual", "lightning", "dark_mark", "grey_edges",
            "patronus_light", "fiendfyre_rings", "eyes_open", "eyes_close", "eyes_peek", "letter_flood",
            "enchanted_ceiling", "light_rays", "floating_candles", "wand_choice", "rough_sea", "rain", "sky");
    /** The looks an enchanted_ceiling may take; the first is the default. */
    public static final java.util.List<String> CEILING_STYLES = java.util.List.of("storm", "night");
    /**
     * Bundled schematics a set's {@code schematics} may place, by bare id. Each one is loaded by its own class
     * ({@code mini_hogwarts}: {@code worldgen/MiniHogwartsSchematic}, 96 x 109 x 145).
     */
    public static final Set<String> SCHEMATICS = Set.of("mini_hogwarts");
    /** The rotations a schematic may be placed at, in degrees clockwise seen from above. */
    public static final Set<Integer> SCHEMATIC_ROTATIONS = Set.of(0, 90, 180, 270);
    /** Largest {@code fog_far}, in blocks. */
    public static final int MAX_FOG_FAR = 512;
    /**
     * Things a scene's {@code end_action} may do. {@code wake}: back home, the viewer opens their eyes on the real
     * world (an eyes_open drawn outside the memory); unlike {@code cave_map} it also plays for a viewer who leaves.
     */
    public static final Set<String> END_ACTIONS = Set.of("cave_map", "wake");
    /** Reusable poses every scene may cue without defining them. */
    public static final Set<String> POSE_PRESETS = Set.of("wand_raised", "wand_point", "kneel", "cower", "arms_out",
            "bent_over_basin", "hands_up", "look_down");
    /**
     * Spells a {@code cast} action may use: exactly those {@code client/pensieve/SpellVisuals} can draw inside a
     * memory, the beam spells plus the hand-built adapters. Kept as plain string sets because this class must stay
     * free of Minecraft imports; SceneDataTest asserts every id is a real SpellType id and that SpellVisuals
     * dispatches on these sets. A spell outside this set is named by the validator ("no memory visual for ...").
     */
    public static final Set<String> SPELL_BEAMS = Set.of(
            "expelliarmus", "stupefy", "glacius", "confringo", "incendio", "avada_kedavra", "crucio", "imperio",
            "sectumsempra", "dolohov_curse", "incarcerous", "bombarda", "depulso", "levicorpus",
            "petrificus_totalus", "obliviate", "confundo");
    /** Spells that are not a beam and have a hand-written drawing in SpellVisuals. */
    public static final Set<String> SPELL_ADAPTERS = Set.of("revelio", "lumos", "protego", "morsmordre",
            "expecto_patronum", "patronus_wisp", "fiendfyre");
    /** Adapter spell ids that are memory-only visuals, not a {@code SpellType} (the wisp: a failing Patronus). */
    public static final Set<String> MEMORY_ONLY_SPELLS = Set.of("patronus_wisp");
    public static final Set<String> SPELL_VISUALS = unionOf(SPELL_BEAMS, SPELL_ADAPTERS);

    private static Set<String> unionOf(Set<String> a, Set<String> b) {
        java.util.HashSet<String> all = new java.util.HashSet<>(a);
        all.addAll(b);
        return Set.copyOf(all);
    }
}
