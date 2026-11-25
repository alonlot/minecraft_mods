package com.hogwartsmagic.cosy;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Famous Wizard Card table: twenty cards, three rarities, a weighted roll, and the album's absorb rule. */
class WizardCardsTest {

    @Test
    void twentyCardsWithUniqueIdsAndKeys() {
        assertEquals(20, WizardCards.ALL.size());
        Set<Integer> ids = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (WizardCards.Card c : WizardCards.ALL) {
            assertTrue(ids.add(c.id()), "duplicate id " + c.id());
            assertTrue(keys.add(c.key()), "duplicate key " + c.key());
            assertTrue(c.id() > 0, "ids start above zero so an absent component can never match one");
            assertEquals(c, WizardCards.byId(c.id()));
        }
        assertNull(WizardCards.byId(0));
        assertNull(WizardCards.byId(21));
    }

    @Test
    void rarityCountsAreTwelveSixTwo() {
        int[] counts = new int[3];
        for (WizardCards.Card c : WizardCards.ALL) {
            counts[c.rarity()]++;
        }
        assertEquals(12, counts[0]);
        assertEquals(6, counts[1]);
        assertEquals(2, counts[2]);
    }

    @Test
    void theNamedTiersAreRight() {
        assertEquals(2, WizardCards.byKey("merlin").rarity());
        assertEquals(2, WizardCards.byKey("albus_dumbledore").rarity());
        for (String k : List.of("godric_gryffindor", "helga_hufflepuff", "rowena_ravenclaw", "salazar_slytherin",
                "nicolas_flamel", "morgana")) {
            assertEquals(1, WizardCards.byKey(k).rarity(), k);
        }
    }

    @Test
    void legendaryShareIsAboutFivePercent() {
        RandomSource r = RandomSource.create(1);
        int legendary = 0;
        int rare = 0;
        for (int i = 0; i < 10_000; i++) {
            WizardCards.Card c = WizardCards.roll(r);
            assertNotNull(c);
            if (c.rarity() == 2) {
                legendary++;
            } else if (c.rarity() == 1) {
                rare++;
            }
        }
        assertTrue(legendary >= 300 && legendary <= 700, "legendary share was " + legendary / 100.0 + "%");
        assertTrue(rare >= 2000 && rare <= 3000, "rare share was " + rare / 100.0 + "%");
    }

    @Test
    void absorbAddsNewCardsOnceAndKeepsTheOld() {
        assertEquals(Set.of(1, 2), WizardCards.absorb(Set.of(1), List.of(1, 2, 2)));
        assertEquals(Set.of(3), WizardCards.absorb(Set.of(), List.of(3)));
        assertEquals(Set.of(1), WizardCards.absorb(Set.of(1), List.of()));
        assertEquals(Set.of(1), WizardCards.absorb(Set.of(1), List.of(99)), "an unknown id is not a card");
    }
}
