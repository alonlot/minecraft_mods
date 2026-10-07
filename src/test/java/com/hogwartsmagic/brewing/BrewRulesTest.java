package com.hogwartsmagic.brewing;

import com.hogwartsmagic.brewing.BrewRules.Match;
import com.hogwartsmagic.brewing.BrewRules.Recipe;
import com.hogwartsmagic.brewing.BrewRules.Status;
import com.hogwartsmagic.brewing.BrewRules.Step;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrewRulesTest {
    private static final String HONEY = "minecraft:honey_bottle";
    private static final String GLOW = "minecraft:glow_berries";
    private static final String BLAZE = "minecraft:blaze_powder";
    private static final String MANDRAKE = "hogwartsmagic:mandrake_root";
    private static final String VENOM = "hogwartsmagic:acromantula_venom";
    private static final String GILLY = "hogwartsmagic:gillyweed";
    private static final String SAMPLE = "hogwartsmagic:polyjuice_sample";
    private static final String REDSTONE = "minecraft:redstone";
    private static final String GUNPOWDER = "minecraft:gunpowder";

    /** Applies single actions one by one, as the cauldron does. */
    private static List<Step> run(List<Step> start, Step... actions) {
        List<Step> history = start;
        for (Step action : actions) {
            history = BrewRules.append(history, action);
        }
        return history;
    }

    private static List<Step> run(Step... actions) {
        return run(new ArrayList<>(), actions);
    }

    private static List<Step> simmer(List<Step> history, int seconds) {
        List<Step> out = history;
        for (int i = 0; i < seconds; i++) {
            out = BrewRules.append(out, Step.simmer(1));
        }
        return out;
    }

    private static Step add(String item) {
        return Step.add(item, 1);
    }

    private static Step cw() {
        return Step.stirClockwise(1);
    }

    private static Step ccw() {
        return Step.stirAnticlockwise(1);
    }

    private static List<Step> wiggenweldToSimmer() {
        return run(add(HONEY), add(GLOW), add(GLOW), cw(), cw(), cw());
    }

    private static List<Step> polyjuiceToSimmer() {
        return run(add(VENOM), add(MANDRAKE), add(MANDRAKE), add(GILLY), cw(), cw(), cw(), cw());
    }

    @Test
    void emptyHistoryIsOnTrackAndWater() {
        Match match = BrewRules.match(List.of());
        assertEquals(Status.ON_TRACK, match.status());
        assertNull(match.recipe());
        assertEquals(BrewRules.WATER_COLOR, BrewRules.color(List.of()));
        assertFalse(BrewRules.wantsSimmer(List.of()));
        assertFalse(BrewRules.overbrewClock(List.of()));
    }

    @Test
    void appendMergesTheSameAction() {
        List<Step> history = run(add(GLOW), add(GLOW), cw(), cw(), ccw(), add(GLOW));
        assertEquals(List.of(Step.add(GLOW, 2), Step.stirClockwise(2), Step.stirAnticlockwise(1), Step.add(GLOW, 1)), history);
        assertEquals(List.of(Step.simmer(3)), simmer(new ArrayList<>(), 3));
    }

    @Test
    void wiggenweldFinishesAfterItsSimmer() {
        List<Step> almost = simmer(wiggenweldToSimmer(), 19);
        assertEquals(Status.ON_TRACK, BrewRules.match(almost).status());
        assertSame(BrewRules.WIGGENWELD, BrewRules.match(almost).recipe());
        Match done = BrewRules.match(simmer(almost, 1));
        assertEquals(Status.FINISHED, done.status());
        assertSame(BrewRules.WIGGENWELD, done.recipe());
        assertEquals("wiggenweld", done.recipe().id());
        assertEquals(3, done.recipe().doses());
    }

    @Test
    void pepperupFinishesWithAnticlockwiseStirs() {
        List<Step> history = simmer(run(add(BLAZE), add(MANDRAKE), ccw(), ccw()), 15);
        Match match = BrewRules.match(history);
        assertEquals(Status.FINISHED, match.status());
        assertSame(BrewRules.PEPPERUP, match.recipe());
    }

    @Test
    void aWrongFirstIngredientRuins() {
        assertEquals(Status.RUINED, BrewRules.match(run(add(GUNPOWDER))).status());
        assertEquals(Status.RUINED, BrewRules.match(run(add(GLOW))).status());
        assertEquals(Status.RUINED, BrewRules.match(run(cw())).status());
    }

    @Test
    void tooFewThenTheNextStepRuins() {
        assertEquals(Status.ON_TRACK, BrewRules.match(run(add(HONEY), add(GLOW))).status());
        assertEquals(Status.RUINED, BrewRules.match(run(add(HONEY), add(GLOW), cw())).status());
    }

    @Test
    void tooManyRuins() {
        assertEquals(Status.RUINED, BrewRules.match(run(add(HONEY), add(GLOW), add(GLOW), add(GLOW))).status());
        assertEquals(Status.RUINED, BrewRules.match(run(wiggenweldToSimmer(), cw())).status());
    }

    @Test
    void theWrongStirDirectionRuins() {
        assertEquals(Status.RUINED, BrewRules.match(run(add(BLAZE), add(MANDRAKE), cw())).status());
        assertEquals(Status.RUINED, BrewRules.match(run(add(HONEY), add(GLOW), add(GLOW), ccw())).status());
    }

    @Test
    void anythingAfterAFinishedBrewRuins() {
        List<Step> finished = simmer(wiggenweldToSimmer(), 20);
        assertEquals(Status.RUINED, BrewRules.match(run(finished, cw())).status());
        assertEquals(Status.RUINED, BrewRules.match(run(finished, add(REDSTONE))).status());
        assertEquals(Status.RUINED, BrewRules.match(run(finished, add(HONEY))).status());
    }

    @Test
    void simmerCountsOnlyAtASimmerStep() {
        assertFalse(BrewRules.wantsSimmer(run(add(HONEY))));
        assertEquals(0, BrewRules.simmerLeft(run(add(HONEY))));
        List<Step> ready = wiggenweldToSimmer();
        assertTrue(BrewRules.wantsSimmer(ready));
        assertEquals(20, BrewRules.simmerLeft(ready));
        assertEquals(12, BrewRules.simmerLeft(simmer(ready, 8)));
        assertTrue(BrewRules.wantsSimmer(simmer(ready, 19)));
        assertFalse(BrewRules.wantsSimmer(simmer(ready, 20)));
        assertEquals(0, BrewRules.simmerLeft(simmer(ready, 20)));
    }

    @Test
    void overbrewClockStartsAfterTheLastSimmer() {
        assertFalse(BrewRules.overbrewClock(wiggenweldToSimmer()));
        assertFalse(BrewRules.overbrewClock(simmer(wiggenweldToSimmer(), 10)));
        assertTrue(BrewRules.overbrewClock(simmer(wiggenweldToSimmer(), 20)));
        List<Step> waiting = simmer(polyjuiceToSimmer(), 480);
        assertEquals(Status.ON_TRACK, BrewRules.match(waiting).status());
        assertSame(BrewRules.POLYJUICE, BrewRules.match(waiting).recipe());
        assertFalse(BrewRules.wantsSimmer(waiting));
        assertTrue(BrewRules.overbrewClock(waiting));
        assertFalse(BrewRules.overbrewClock(simmer(polyjuiceToSimmer(), 479)));
        assertFalse(BrewRules.overbrewClock(run(add(HONEY), add(GUNPOWDER))));
    }

    @Test
    void overbrewMath() {
        assertFalse(BrewRules.overbrewed(0));
        assertFalse(BrewRules.overbrewed(120));
        assertTrue(BrewRules.overbrewed(121));
        assertEquals(120, BrewRules.overbrewLeft(0));
        assertEquals(16, BrewRules.overbrewLeft(105));
        assertEquals(1, BrewRules.overbrewLeft(120));
        assertEquals(0, BrewRules.overbrewLeft(121));
        assertEquals(0, BrewRules.overbrewLeft(200));
        assertFalse(BrewRules.overbrewWarning(100));
        assertTrue(BrewRules.overbrewWarning(101));
        assertTrue(BrewRules.overbrewWarning(119));
        assertTrue(BrewRules.overbrewWarning(120));
        assertFalse(BrewRules.overbrewWarning(121));
    }

    @Test
    void polyjuiceFinishesWithTheSampleAndTakesItsTail() {
        List<Step> waiting = simmer(polyjuiceToSimmer(), 480);
        List<Step> brewed = run(waiting, add(SAMPLE));
        Match match = BrewRules.match(brewed);
        assertEquals(Status.FINISHED, match.status());
        assertSame(BrewRules.POLYJUICE, match.recipe());
        assertFalse(BrewRules.usedTail(brewed, REDSTONE));
        assertFalse(BrewRules.usedTail(brewed, GUNPOWDER));

        List<Step> extended = run(brewed, add(REDSTONE));
        assertEquals(Status.FINISHED, BrewRules.match(extended).status());
        assertTrue(BrewRules.usedTail(extended, REDSTONE));

        List<Step> both = run(extended, add(GUNPOWDER));
        assertEquals(Status.FINISHED, BrewRules.match(both).status());
        assertTrue(BrewRules.usedTail(both, REDSTONE));
        assertTrue(BrewRules.usedTail(both, GUNPOWDER));

        List<Step> splashOnly = run(brewed, add(GUNPOWDER));
        assertEquals(Status.FINISHED, BrewRules.match(splashOnly).status());
        assertFalse(BrewRules.usedTail(splashOnly, REDSTONE));
        assertTrue(BrewRules.usedTail(splashOnly, GUNPOWDER));
    }

    @Test
    void polyjuiceTailIsInOrderAndOnce() {
        List<Step> brewed = run(simmer(polyjuiceToSimmer(), 480), add(SAMPLE));
        assertEquals(Status.RUINED, BrewRules.match(run(brewed, add(GUNPOWDER), add(REDSTONE))).status());
        assertEquals(Status.RUINED, BrewRules.match(run(brewed, add(REDSTONE), add(REDSTONE))).status());
        assertEquals(Status.RUINED, BrewRules.match(run(brewed, add(REDSTONE), add(GUNPOWDER), add(GUNPOWDER))).status());
        assertEquals(Status.RUINED, BrewRules.match(run(brewed, add(MANDRAKE))).status());
        assertEquals(Status.RUINED, BrewRules.match(run(brewed, add(SAMPLE))).status());
        assertFalse(BrewRules.usedTail(run(brewed, add(GUNPOWDER), add(REDSTONE)), GUNPOWDER));
    }

    @Test
    void theSampleTooEarlyRuins() {
        assertEquals(Status.RUINED, BrewRules.match(run(simmer(polyjuiceToSimmer(), 100), add(SAMPLE))).status());
        assertEquals(Status.RUINED, BrewRules.match(run(polyjuiceToSimmer(), add(SAMPLE))).status());
    }

    @Test
    void anEmptySampleIsRefused() {
        assertTrue(BrewRules.refusesEmptySample(SAMPLE, false));
        assertFalse(BrewRules.refusesEmptySample(SAMPLE, true));
        assertFalse(BrewRules.refusesEmptySample(HONEY, false));
    }

    @Test
    void coloursFollowTheSteps() {
        assertEquals(0xD9A33A, BrewRules.color(run(add(HONEY))));
        assertEquals(BrewRules.lerpColor(0xD9A33A, 0xE8C85A, 0.5f), BrewRules.color(run(add(HONEY), add(GLOW))));
        assertEquals(0xE8C85A, BrewRules.color(run(add(HONEY), add(GLOW), add(GLOW))));
        assertEquals(0x4CC38A, BrewRules.color(simmer(wiggenweldToSimmer(), 20)));
        assertEquals(BrewRules.RUINED_COLOR, BrewRules.color(run(add(GUNPOWDER))));
        List<Step> brewed = run(simmer(polyjuiceToSimmer(), 480), add(SAMPLE));
        assertEquals(0x9C8F4A, BrewRules.color(brewed));
        assertEquals(0xA0563E, BrewRules.color(run(brewed, add(REDSTONE))));
        assertEquals(0x7D7468, BrewRules.color(run(brewed, add(REDSTONE), add(GUNPOWDER))));
        assertEquals(0x000000, BrewRules.lerpColor(0x000000, 0xFFFFFF, 0.0f));
        assertEquals(0xFFFFFF, BrewRules.lerpColor(0x000000, 0xFFFFFF, 1.0f));
        assertEquals(0x808080, BrewRules.lerpColor(0x000000, 0xFFFFFF, 0.5f));
        assertEquals(0xFFFFFF, BrewRules.lerpColor(0x000000, 0xFFFFFF, 3.0f));
    }

    @Test
    void ingredientsAreTheRecipeItems() {
        for (String id : List.of(HONEY, GLOW, BLAZE, MANDRAKE, VENOM, GILLY, SAMPLE, REDSTONE, GUNPOWDER)) {
            assertTrue(BrewRules.isIngredient(id), id);
        }
        assertFalse(BrewRules.isIngredient("minecraft:dirt"));
        assertFalse(BrewRules.isIngredient("hogwartsmagic:fine_wood_stick"));
        assertFalse(BrewRules.isIngredient("minecraft:glass_bottle"));
    }

    @Test
    void theTableIsWellFormed() {
        Set<String> ids = new HashSet<>();
        Set<String> firstItems = new HashSet<>();
        for (Recipe recipe : BrewRules.RECIPES) {
            assertTrue(ids.add(recipe.id()), recipe.id());
            assertEquals(BrewRules.Kind.ADD, recipe.steps().get(0).kind(), recipe.id() + " step 0 is not an ADD");
            assertTrue(firstItems.add(recipe.steps().get(0).item()), recipe.id() + " first ingredient is shared");
            assertEquals(recipe.steps().size(), recipe.colors().size(), recipe.id());
            assertEquals(recipe.tail().size(), recipe.tailColors().size(), recipe.id());
            int doses = recipe == BrewRules.FELIX_FELICIS ? BrewRules.FELIX_DOSES : BrewRules.MAX_DOSES;
            assertEquals(doses, recipe.doses(), recipe.id());
            for (int i = 1; i < recipe.steps().size(); i++) {
                assertFalse(recipe.steps().get(i).sameAction(recipe.steps().get(i - 1)), recipe.id() + " step " + i);
            }
            for (Step step : recipe.steps()) {
                assertTrue(step.amount() > 0, recipe.id());
            }
            if (!recipe.tail().isEmpty()) {
                Step last = recipe.steps().get(recipe.steps().size() - 1);
                assertFalse(last.sameAction(recipe.tail().get(0)), recipe.id() + " last step vs tail");
                for (int i = 1; i < recipe.tail().size(); i++) {
                    assertFalse(recipe.tail().get(i).sameAction(recipe.tail().get(i - 1)), recipe.id() + " tail " + i);
                }
            }
        }
        assertEquals(List.of(BrewRules.WIGGENWELD, BrewRules.PEPPERUP, BrewRules.POLYJUICE, BrewRules.DITTANY_ESSENCE,
                BrewRules.MANDRAKE_DRAUGHT, BrewRules.LIVING_DEATH, BrewRules.FELIX_FELICIS), BrewRules.RECIPES);
    }

    @Test
    void aFirstIngredientNamesItsRecipe() {
        Match blaze = BrewRules.match(run(add(BLAZE)));
        assertEquals(Status.ON_TRACK, blaze.status());
        assertSame(BrewRules.PEPPERUP, blaze.recipe());
        Match venom = BrewRules.match(run(add(VENOM)));
        assertEquals(Status.ON_TRACK, venom.status());
        assertSame(BrewRules.POLYJUICE, venom.recipe());
    }

    @Test
    void kindByName() {
        assertEquals(BrewRules.Kind.SIMMER, BrewRules.Kind.byName("SIMMER"));
        assertEquals(BrewRules.Kind.STIR_ANTICLOCKWISE, BrewRules.Kind.byName("STIR_ANTICLOCKWISE"));
        assertNull(BrewRules.Kind.byName("BOIL"));
        assertNull(BrewRules.Kind.byName(""));
    }
}
