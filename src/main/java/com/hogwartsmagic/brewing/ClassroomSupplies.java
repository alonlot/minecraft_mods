package com.hogwartsmagic.brewing;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;

/**
 * What each ingredient chest of the Mini Hogwarts Potions classroom holds. Pure: items are registry names.
 * The minimums brew each recipe that needs no rare ingredient once (Wiggenweld, Pepperup, Essence of Dittany, the
 * Mandrake Restorative Draught with its splash tail, the Draught of Living Death), with bottles, water and a
 * stirring rod. Polyjuice (a sample, Acromantula venom) and Felix Felicis (an Ashwinder egg, venom, gold) stay
 * earned; now and then a chest holds one Ashwinder egg.
 */
public final class ClassroomSupplies {
    public static final String GLASS_BOTTLE = "minecraft:glass_bottle";
    public static final String WATER_BUCKET = "minecraft:water_bucket";
    /** The Fine Wood Stick is the stirring rod. */
    public static final String STIRRING_ROD = "hogwartsmagic:fine_wood_stick";
    /** Each chest holds one Ashwinder egg with this chance. */
    public static final double ASHWINDER_CHANCE = 0.10;

    /** One kind of supply: between min and max of item, both inclusive. */
    public record Supply(String item, int min, int max) {
    }

    /** One stack for a chest. */
    public record Stack(String item, int count) {
    }

    public static final List<Supply> CHEST = List.of(
            new Supply(GLASS_BOTTLE, 6, 9),
            new Supply(WATER_BUCKET, 1, 1),
            new Supply(STIRRING_ROD, 1, 1),
            new Supply(BrewRules.HONEY_BOTTLE, 1, 3),
            new Supply(BrewRules.GLOW_BERRIES, 2, 6),
            new Supply(BrewRules.BLAZE_POWDER, 1, 3),
            new Supply(BrewRules.MANDRAKE_ROOT, 3, 5),
            new Supply(BrewRules.DITTANY, 3, 6),
            new Supply(BrewRules.ASPHODEL, 2, 3),
            new Supply(BrewRules.WORMWOOD, 1, 2),
            new Supply(BrewRules.VALERIAN_SPRIGS, 2, 4),
            new Supply(BrewRules.ARCANE_DUST, 1, 2),
            new Supply(BrewRules.GUNPOWDER, 2, 3));

    private ClassroomSupplies() {
    }

    /**
     * One chest, in CHEST order. between(min, max) picks a count (clamped to the range); eggRoll is a roll in
     * [0, 1): below ASHWINDER_CHANCE adds one Ashwinder egg at the end.
     */
    public static List<Stack> roll(IntBinaryOperator between, double eggRoll) {
        List<Stack> out = new ArrayList<>();
        for (Supply supply : CHEST) {
            int count = between.applyAsInt(supply.min(), supply.max());
            out.add(new Stack(supply.item(), Math.max(supply.min(), Math.min(supply.max(), count))));
        }
        if (eggRoll < ASHWINDER_CHANCE) {
            out.add(new Stack(BrewRules.ASHWINDER_EGG, 1));
        }
        return out;
    }
}
