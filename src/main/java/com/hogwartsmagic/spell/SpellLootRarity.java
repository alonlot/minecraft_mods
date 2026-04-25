package com.hogwartsmagic.spell;

/**
 * Weight used when rolling a spell for magic-book chest loot (higher = more common).
 */
public enum SpellLootRarity {
    COMMON(10),
    UNCOMMON(4),
    RARE(2),
    EPIC(1);

    private final int lootWeight;

    SpellLootRarity(int lootWeight) {
        this.lootWeight = lootWeight;
    }

    public int getLootWeight() {
        return lootWeight;
    }
}
