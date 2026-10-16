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
            "stone", "unstone", "cast", "use_item", "hold");
    /** Event kinds. */
    public static final Set<String> EVENT_KINDS = Set.of("block", "sound", "particles", "effect");
    /** Scripted effects an {@code effect} event may name. */
    public static final Set<String> EFFECTS = Set.of("summoning_ritual", "lightning", "dark_mark");
    /** Things a scene's {@code end_action} may do. */
    public static final Set<String> END_ACTIONS = Set.of("cave_map");
    /** Reusable poses every scene may cue without defining them. */
    public static final Set<String> POSE_PRESETS = Set.of("wand_raised", "wand_point", "kneel", "cower", "arms_out",
            "bent_over_basin", "hands_up", "look_down");
    /**
     * Spells a {@code cast} action may use: those with a memory visual. SpellType has no beam flag, so for now
     * this is every SpellType id (kept as a string list because this class must stay free of Minecraft imports;
     * SceneDataTest asserts it is a subset of the ids in SpellType.java). Task 11 narrows it.
     */
    public static final Set<String> SPELL_VISUALS = Set.of(
            "expelliarmus", "glacius", "confringo", "protego", "protego_maxima", "lumos", "accio",
            "wingardium_leviosa", "alohomora", "incendio", "stupefy", "revelio", "avada_kedavra", "crucio",
            "imperio", "fiendfyre", "sectumsempra", "morsmordre", "dolohov_curse", "incarcerous", "heal",
            "tenebris_flight", "expecto_patronum", "bombarda", "depulso", "levicorpus", "diffindo",
            "petrificus_totalus", "aqua_eructo", "obliviate", "ascendio", "confundo", "cave_inimicum",
            "alatus", "serpensortia", "arresto_momentum", "reparo", "apparition", "piertotum_locomotor",
            "colloportus", "riddikulus");
}
