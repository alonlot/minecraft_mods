package com.hogwartsmagic.brewing;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassroomSuppliesTest {

    private static ClassroomSupplies.Supply supply(String item) {
        for (ClassroomSupplies.Supply supply : ClassroomSupplies.CHEST) {
            if (supply.item().equals(item)) {
                return supply;
            }
        }
        return null;
    }

    private static boolean hasEgg(List<ClassroomSupplies.Stack> stacks) {
        for (ClassroomSupplies.Stack stack : stacks) {
            if (stack.item().equals(BrewRules.ASHWINDER_EGG)) {
                return true;
            }
        }
        return false;
    }

    private static void addNeeds(Map<String, Integer> needed, List<BrewRules.Step> steps) {
        for (BrewRules.Step step : steps) {
            if (step.kind() == BrewRules.Kind.ADD) {
                needed.merge(step.item(), step.amount(), Integer::sum);
            }
        }
    }

    @Test
    void oneChestBrewsEveryRecipeWithoutARareIngredientOnce() {
        Map<String, Integer> needed = new HashMap<>();
        for (BrewRules.Recipe recipe : List.of(BrewRules.WIGGENWELD, BrewRules.PEPPERUP, BrewRules.DITTANY_ESSENCE,
                BrewRules.MANDRAKE_DRAUGHT, BrewRules.LIVING_DEATH)) {
            addNeeds(needed, recipe.steps());
            addNeeds(needed, recipe.tail());
        }
        for (Map.Entry<String, Integer> need : needed.entrySet()) {
            ClassroomSupplies.Supply supply = supply(need.getKey());
            assertNotNull(supply, need.getKey() + " is not supplied");
            assertTrue(supply.min() >= need.getValue(),
                    need.getKey() + ": at least " + supply.min() + " but a brew of each needs " + need.getValue());
        }
        assertTrue(supply(ClassroomSupplies.GLASS_BOTTLE).min() >= BrewRules.MAX_DOSES);
        assertNotNull(supply(ClassroomSupplies.WATER_BUCKET));
        assertNotNull(supply(ClassroomSupplies.STIRRING_ROD));
    }

    @Test
    void countsStayInsideTheirRange() {
        List<ClassroomSupplies.Stack> low = ClassroomSupplies.roll((min, max) -> min, 0.99, 0.99);
        List<ClassroomSupplies.Stack> high = ClassroomSupplies.roll((min, max) -> max, 0.99, 0.99);
        List<ClassroomSupplies.Stack> tooHigh = ClassroomSupplies.roll((min, max) -> 1000, 0.99, 0.99);
        List<ClassroomSupplies.Stack> tooLow = ClassroomSupplies.roll((min, max) -> -5, 0.99, 0.99);
        assertEquals(ClassroomSupplies.CHEST.size(), low.size());
        for (int i = 0; i < ClassroomSupplies.CHEST.size(); i++) {
            ClassroomSupplies.Supply supply = ClassroomSupplies.CHEST.get(i);
            assertEquals(supply.item(), low.get(i).item());
            assertEquals(supply.min(), low.get(i).count());
            assertEquals(supply.max(), high.get(i).count());
            assertEquals(supply.max(), tooHigh.get(i).count());
            assertEquals(supply.min(), tooLow.get(i).count());
        }
    }

    @Test
    void anAshwinderEggOnlyBelowTheChance() {
        List<ClassroomSupplies.Stack> lucky = ClassroomSupplies.roll((min, max) -> min, 0.05, 0.99);
        assertTrue(lucky.contains(new ClassroomSupplies.Stack(BrewRules.ASHWINDER_EGG, 1)));
        assertEquals(ClassroomSupplies.CHEST.size() + 1, lucky.size());
        assertFalse(hasEgg(ClassroomSupplies.roll((min, max) -> min, ClassroomSupplies.ASHWINDER_CHANCE, 0.99)));
        assertFalse(hasEgg(ClassroomSupplies.roll((min, max) -> min, 0.5, 0.99)));
    }

    @Test
    void theAdvancedBookOnlyBelowItsChance() {
        List<ClassroomSupplies.Stack> book = ClassroomSupplies.roll((min, max) -> min, 0.99, 0.24);
        assertEquals(new ClassroomSupplies.Stack(ClassroomSupplies.ADVANCED_BOOK, 1), book.get(book.size() - 1));
        assertEquals(ClassroomSupplies.CHEST.size() + 1, book.size());
        List<ClassroomSupplies.Stack> none = ClassroomSupplies.roll((min, max) -> min, 0.99, 0.25);
        assertEquals(ClassroomSupplies.CHEST.size(), none.size());
        List<ClassroomSupplies.Stack> both = ClassroomSupplies.roll((min, max) -> min, 0.05, 0.1);
        assertEquals(new ClassroomSupplies.Stack(BrewRules.ASHWINDER_EGG, 1), both.get(both.size() - 2));
        assertEquals(new ClassroomSupplies.Stack(ClassroomSupplies.ADVANCED_BOOK, 1), both.get(both.size() - 1));
    }

    @Test
    void suppliesAreSaneAndRareThingsStayEarned() {
        Set<String> items = new HashSet<>();
        for (ClassroomSupplies.Supply supply : ClassroomSupplies.CHEST) {
            assertTrue(items.add(supply.item()), "listed twice: " + supply.item());
            assertTrue(supply.min() >= 1 && supply.min() <= supply.max() && supply.max() <= 16, supply.item());
        }
        assertFalse(items.contains(BrewRules.ASHWINDER_EGG));
        assertFalse(items.contains(BrewRules.ACROMANTULA_VENOM));
        assertFalse(items.contains(BrewRules.POLYJUICE_SAMPLE));
        assertTrue(ClassroomSupplies.CHEST.size() + 2 <= 27, "everything fits one chest");
    }
}
