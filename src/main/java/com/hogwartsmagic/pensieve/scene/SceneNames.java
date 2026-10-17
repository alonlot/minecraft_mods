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
            "stone", "unstone", "cast", "use_item", "hold", "ignite");
    /** Largest {@code volume} a {@code music} or {@code voice} cue may ask for. */
    public static final float MAX_CUE_VOLUME = 4f;
    /** Event kinds. */
    public static final Set<String> EVENT_KINDS = Set.of("block", "sound", "particles", "effect", "music", "voice");
    /** Scripted effects an {@code effect} event may name. */
    public static final Set<String> EFFECTS = Set.of("summoning_ritual", "lightning", "dark_mark", "grey_edges",
            "patronus_light", "fiendfyre_rings");
    /**
     * Bundled schematics a set's {@code schematics} may place, by bare id. Each one is loaded by its own class
     * ({@code mini_hogwarts}: {@code worldgen/MiniHogwartsSchematic}, 96 x 109 x 145).
     */
    public static final Set<String> SCHEMATICS = Set.of("mini_hogwarts");
    /** The rotations a schematic may be placed at, in degrees clockwise seen from above. */
    public static final Set<Integer> SCHEMATIC_ROTATIONS = Set.of(0, 90, 180, 270);
    /** Largest {@code fog_far}, in blocks. */
    public static final int MAX_FOG_FAR = 512;
    /** Things a scene's {@code end_action} may do. */
    public static final Set<String> END_ACTIONS = Set.of("cave_map");
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
