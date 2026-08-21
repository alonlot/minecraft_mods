package com.hogwartsmagic.loot;

import com.hogwartsmagic.spell.SpellType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps vanilla chest loot tables to spell pools and weighted spell rolls for magic books.
 */
public final class MagicBookChestLoot {
    private static final Map<ResourceLocation, SpellType[]> TABLE_POOLS = new HashMap<>();
    private static final Set<ResourceLocation> GUARANTEED_MAGIC_BOOK_TABLES = new HashSet<>();

    private static final SpellType[] VILLAGE = {
            SpellType.LUMOS, SpellType.HEAL, SpellType.ACCIO, SpellType.ALOHOMORA,
            SpellType.WINGARDIUM_LEVIOSA, SpellType.PROTEGO, SpellType.DEPULSO, SpellType.DIFFINDO,
            SpellType.ALATUS, SpellType.ARRESTO_MOMENTUM, SpellType.REPARO
    };
    private static final SpellType[] WATER = {
            SpellType.AQUA_ERUCTO, SpellType.GLACIUS, SpellType.REVELIO, SpellType.ASCENDIO
    };
    private static final SpellType[] ANCIENT_CITY = {
            SpellType.AVADA_KEDAVRA, SpellType.CRUCIO, SpellType.IMPERIO, SpellType.MORSMORDRE,
            SpellType.DOLOHOV_CURSE, SpellType.SECTUMSEMPRA, SpellType.TENEBRIS_FLIGHT, SpellType.SERPENSORTIA,
            SpellType.OBLIVIATE, SpellType.LEVICORPUS, SpellType.INCARCEROUS, SpellType.FIENDFYRE,
            SpellType.CAVE_INIMICUM, SpellType.PROTEGO_MAXIMA
    };
    private static final SpellType[] ANCIENT_CITY_ICE = {
            SpellType.GLACIUS, SpellType.AVADA_KEDAVRA, SpellType.CRUCIO, SpellType.IMPERIO, SpellType.MORSMORDRE,
            SpellType.DOLOHOV_CURSE, SpellType.SECTUMSEMPRA, SpellType.TENEBRIS_FLIGHT, SpellType.SERPENSORTIA,
            SpellType.OBLIVIATE, SpellType.LEVICORPUS, SpellType.INCARCEROUS, SpellType.FIENDFYRE,
            SpellType.CAVE_INIMICUM
    };
    private static final SpellType[] CHAMBER = {
            SpellType.AVADA_KEDAVRA, SpellType.CRUCIO, SpellType.IMPERIO, SpellType.MORSMORDRE,
            SpellType.FIENDFYRE, SpellType.SECTUMSEMPRA, SpellType.DOLOHOV_CURSE,
            SpellType.TENEBRIS_FLIGHT, SpellType.OBLIVIATE, SpellType.PROTEGO_MAXIMA
    };
    private static final SpellType[] NETHER_FIRE = {
            SpellType.INCENDIO, SpellType.CONFRINGO, SpellType.FIENDFYRE, SpellType.BOMBARDA,
            SpellType.STUPEFY, SpellType.EXPELLIARMUS, SpellType.DEPULSO
    };
    private static final SpellType[] STRONGHOLD_LIBRARY = {
            SpellType.ALOHOMORA, SpellType.REVELIO, SpellType.CONFUNDO, SpellType.WINGARDIUM_LEVIOSA,
            SpellType.ACCIO, SpellType.DEPULSO, SpellType.DIFFINDO, SpellType.PETRIFICUS_TOTALUS, SpellType.REPARO,
            SpellType.CAVE_INIMICUM, SpellType.PROTEGO_MAXIMA, SpellType.EXPECTO_PATRONUM, SpellType.APPARITION,
            // A castle-defence charm belongs with the other things written down in a fortified library,
            // and keeping it out of the village pool stops a starting player animating a sentry before
            // they have any reason to want one.
            SpellType.PIERTOTUM_LOCOMOTOR
    };
    private static final SpellType[] DUNGEON_MIX = {
            SpellType.STUPEFY, SpellType.REVELIO, SpellType.LUMOS, SpellType.GLACIUS,
            SpellType.INCENDIO, SpellType.EXPELLIARMUS, SpellType.ALOHOMORA, SpellType.ARRESTO_MOMENTUM
    };
    /**
     * Fallback for vanilla loot tables we do not explicitly map.
     * Keep this to broadly useful early/mid-game spells so random vanilla chests
     * do not leak dark arts or late-game progression skips.
     */
    private static final SpellType[] DEFAULT_POOL = {
            SpellType.LUMOS,
            SpellType.ACCIO,
            SpellType.ALOHOMORA,
            SpellType.DIFFINDO,
            SpellType.HEAL,
            SpellType.REVELIO,
            SpellType.ARRESTO_MOMENTUM,
            SpellType.WINGARDIUM_LEVIOSA,
            SpellType.PROTEGO,
            SpellType.EXPELLIARMUS,
            SpellType.STUPEFY,
            SpellType.DEPULSO,
            SpellType.GLACIUS,
            SpellType.INCENDIO,
            SpellType.AQUA_ERUCTO,
            SpellType.REPARO
    };

    static {
        put("minecraft:chests/village/village_plains_house", VILLAGE);
        put("minecraft:chests/village/village_desert_house", VILLAGE);
        put("minecraft:chests/village/village_savanna_house", VILLAGE);
        put("minecraft:chests/village/village_snowy_house", VILLAGE);
        put("minecraft:chests/village/village_taiga_house", VILLAGE);

        put("minecraft:chests/shipwreck_supply", WATER);
        put("minecraft:chests/shipwreck_treasure", WATER);
        put("minecraft:chests/shipwreck_map", WATER);
        put("minecraft:chests/underwater_ruin_big", WATER);
        put("minecraft:chests/underwater_ruin_small", WATER);
        put("minecraft:chests/buried_treasure", WATER);

        put("minecraft:chests/ancient_city", ANCIENT_CITY);
        put("minecraft:chests/ancient_city_ice_box", ANCIENT_CITY_ICE);

        put("minecraft:chests/bastion_bridge", NETHER_FIRE);
        put("minecraft:chests/bastion_hoglin_stable", NETHER_FIRE);
        put("minecraft:chests/bastion_other", NETHER_FIRE);
        put("minecraft:chests/bastion_treasure", NETHER_FIRE);
        put("minecraft:chests/ruined_portal", NETHER_FIRE);
        put("minecraft:chests/nether_bridge", NETHER_FIRE);

        // Salazar's own chamber. The darkest pool in the mod, minus Serpensortia itself -- that is
        // the spell the door answers to, so a book for it inside the room it unlocks would only ever
        // be found by someone who already had one.
        put("hogwartsmagic:chests/chamber_of_secrets", CHAMBER);

        put("minecraft:chests/stronghold_library", STRONGHOLD_LIBRARY);

        put("minecraft:chests/simple_dungeon", DUNGEON_MIX);
        put("minecraft:chests/abandoned_mineshaft", DUNGEON_MIX);
        put("minecraft:chests/desert_pyramid", DUNGEON_MIX);
        put("minecraft:chests/jungle_temple", DUNGEON_MIX);

    }

    /**
     * Friendly "where to find this spell's book" labels, keyed by spell. Built by reverse-indexing
     * the same pools used for chest rolls, so the in-game Grimoire hints stay accurate to the loot.
     * Values are lang-key suffixes under {@code gui.hogwartsmagic.grimoire.source.}.
     */
    private static final String SOURCE_PREFIX = "gui.hogwartsmagic.grimoire.source.";
    private static final Map<SpellType, Set<String>> SOURCE_HINTS = new EnumMap<>(SpellType.class);

    static {
        indexSource("village", VILLAGE);
        indexSource("water", WATER);
        indexSource("ancient_city", ANCIENT_CITY);
        indexSource("ancient_city", ANCIENT_CITY_ICE);
        indexSource("nether", NETHER_FIRE);
        indexSource("stronghold", STRONGHOLD_LIBRARY);
        indexSource("dungeon", DUNGEON_MIX);
        indexSource("common_chests", DEFAULT_POOL);
    }

    private static void indexSource(String labelSuffix, SpellType[] pool) {
        String key = SOURCE_PREFIX + labelSuffix;
        for (SpellType spell : pool) {
            SOURCE_HINTS.computeIfAbsent(spell, s -> new LinkedHashSet<>()).add(key);
        }
    }

    /**
     * Lang keys describing where this spell's book can be found, in stable order.
     * Returns a single "unknown source" key for spells not placed in any chest pool
     * (e.g. those obtained only through other means).
     */
    public static List<String> getSourceHintKeys(SpellType spell) {
        Set<String> hints = SOURCE_HINTS.get(spell);
        if (hints == null || hints.isEmpty()) {
            return List.of(SOURCE_PREFIX + "unknown");
        }
        return new ArrayList<>(hints);
    }

    private static void put(String id, SpellType[] pool) {
        TABLE_POOLS.put(ResourceLocation.parse(id), pool);
    }

    private static void putGuaranteed(String id, SpellType[] pool) {
        ResourceLocation location = ResourceLocation.parse(id);
        TABLE_POOLS.put(location, pool);
        GUARANTEED_MAGIC_BOOK_TABLES.add(location);
    }

    private MagicBookChestLoot() {
    }

    /**
     * Spell pool for this loot table, or a safe vanilla fallback for other {@code minecraft:chests/...} tables.
     */
    public static SpellType[] spellPoolFor(ResourceLocation lootTableId) {
        SpellType[] exact = TABLE_POOLS.get(lootTableId);
        if (exact != null) {
            return exact;
        }
        return DEFAULT_POOL;
    }

    public static boolean isGuaranteedSpellBookChest(ResourceLocation lootTableId) {
        return GUARANTEED_MAGIC_BOOK_TABLES.contains(lootTableId);
    }

    public static SpellType pickWeightedSpell(SpellType[] pool, RandomSource random) {
        if (pool == null || pool.length == 0) {
            return SpellType.LUMOS;
        }
        int total = 0;
        for (SpellType s : pool) {
            total += s.getLootRarity().getLootWeight();
        }
        if (total <= 0) {
            return pool[random.nextInt(pool.length)];
        }
        int roll = random.nextInt(total);
        for (SpellType s : pool) {
            roll -= s.getLootRarity().getLootWeight();
            if (roll < 0) {
                return s;
            }
        }
        return pool[pool.length - 1];
    }
}
