package com.hogwartsmagic.brewing;

import com.hogwartsmagic.brewing.BrewRules.Match;
import com.hogwartsmagic.brewing.BrewRules.Status;
import com.hogwartsmagic.brewing.BrewRules.Step;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 2 recipes: Essence of Dittany, Mandrake Restorative Draught, Draught of Living Death, Felix Felicis. */
class BrewRulesPhase2Test {
    private static final String DITTANY = "hogwartsmagic:dittany";
    private static final String ASPHODEL = "hogwartsmagic:asphodel";
    private static final String WORMWOOD = "hogwartsmagic:wormwood";
    private static final String VALERIAN = "hogwartsmagic:valerian_sprigs";
    private static final String ASHWINDER = "hogwartsmagic:ashwinder_egg";
    private static final String MANDRAKE = "hogwartsmagic:mandrake_root";
    private static final String VENOM = "hogwartsmagic:acromantula_venom";
    private static final String DUST = "hogwartsmagic:arcane_dust";
    private static final String NUGGET = "minecraft:gold_nugget";
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

    private static List<Step> times(List<Step> start, Step action, int count) {
        List<Step> history = start;
        for (int i = 0; i < count; i++) {
            history = BrewRules.append(history, action);
        }
        return history;
    }

    private static List<Step> simmer(List<Step> history, int seconds) {
        return times(history, Step.simmer(1), seconds);
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

    private static List<Step> dittanyToSimmer() {
        return times(times(new ArrayList<>(), add(DITTANY), 3), cw(), 5);
    }

    private static List<Step> mandrakeSimmered() {
        return simmer(run(add(MANDRAKE), add(MANDRAKE), add(ASPHODEL), cw(), cw(), cw(), cw()), 45);
    }

    private static List<Step> livingDeathStart() {
        return run(add(ASPHODEL), add(WORMWOOD), add(VALERIAN), add(VALERIAN));
    }

    private static List<Step> livingDeathSimmered() {
        return simmer(run(times(livingDeathStart(), ccw(), 7), cw()), 60);
    }

    private static List<Step> felixSimmered() {
        return simmer(run(add(ASHWINDER), add(VENOM), add(NUGGET), cw(), cw(), cw()), 600);
    }

    @Test
    void dittanyBrews() {
        assertEquals(30, BrewRules.simmerLeft(dittanyToSimmer()));
        Match match = BrewRules.match(simmer(dittanyToSimmer(), 30));
        assertEquals(Status.FINISHED, match.status());
        assertSame(BrewRules.DITTANY_ESSENCE, match.recipe());
        assertEquals(BrewRules.MAX_DOSES, match.recipe().doses());
        assertEquals(0xC9E3A6, BrewRules.color(simmer(dittanyToSimmer(), 30)));
    }

    @Test
    void mandrakeDraughtFinishesOnArcaneDust() {
        List<Step> simmered = mandrakeSimmered();
        assertEquals(Status.ON_TRACK, BrewRules.match(simmered).status());
        assertTrue(BrewRules.overbrewClock(simmered));
        List<Step> brewed = run(simmered, add(DUST));
        Match match = BrewRules.match(brewed);
        assertEquals(Status.FINISHED, match.status());
        assertSame(BrewRules.MANDRAKE_DRAUGHT, match.recipe());
        assertFalse(BrewRules.usedTail(brewed, GUNPOWDER));
        assertEquals(0x5FD3C0, BrewRules.color(brewed));
    }

    @Test
    void mandrakeGunpowderMakesItSplashOnce() {
        List<Step> splash = run(mandrakeSimmered(), add(DUST), add(GUNPOWDER));
        assertEquals(Status.FINISHED, BrewRules.match(splash).status());
        assertTrue(BrewRules.usedTail(splash, GUNPOWDER));
        assertEquals(0x6E8F86, BrewRules.color(splash));
        assertEquals(Status.RUINED, BrewRules.match(run(splash, add(GUNPOWDER))).status());
    }

    @Test
    void livingDeathNeedsItsGunpowder() {
        List<Step> simmered = livingDeathSimmered();
        Match waiting = BrewRules.match(simmered);
        assertEquals(Status.ON_TRACK, waiting.status());
        assertSame(BrewRules.LIVING_DEATH, waiting.recipe());
        assertFalse(BrewRules.wantsSimmer(simmered));
        assertTrue(BrewRules.overbrewClock(simmered));
        List<Step> done = run(simmered, add(GUNPOWDER));
        Match finished = BrewRules.match(done);
        assertEquals(Status.FINISHED, finished.status());
        assertSame(BrewRules.LIVING_DEATH, finished.recipe());
        assertEquals(0x9AA3AD, BrewRules.color(done));
        assertFalse(BrewRules.usedTail(done, GUNPOWDER));
    }

    @Test
    void livingDeathStirsAreCounted() {
        List<Step> start = livingDeathStart();
        assertEquals(Status.RUINED, BrewRules.match(run(start, cw())).status());
        assertEquals(Status.RUINED, BrewRules.match(run(times(start, ccw(), 6), cw())).status());
        assertEquals(Status.RUINED, BrewRules.match(times(start, ccw(), 8)).status());
        List<Step> right = run(times(start, ccw(), 7), cw());
        assertEquals(Status.ON_TRACK, BrewRules.match(right).status());
        assertEquals(0xE8EEF2, BrewRules.color(right));
        assertEquals(60, BrewRules.simmerLeft(right));
    }

    @Test
    void felixSimmersTenMinutesAndGivesOneDose() {
        assertEquals(600, BrewRules.simmerLeft(run(add(ASHWINDER), add(VENOM), add(NUGGET), cw(), cw(), cw())));
        List<Step> simmered = felixSimmered();
        assertEquals(Status.ON_TRACK, BrewRules.match(simmered).status());
        assertTrue(BrewRules.overbrewClock(simmered));
        List<Step> done = run(simmered, ccw());
        Match match = BrewRules.match(done);
        assertEquals(Status.FINISHED, match.status());
        assertSame(BrewRules.FELIX_FELICIS, match.recipe());
        assertEquals(1, BrewRules.FELIX_DOSES);
        assertEquals(BrewRules.FELIX_DOSES, match.recipe().doses());
        assertEquals(0xFFD966, BrewRules.color(done));
    }

    @Test
    void felixLastStirMustBeAnticlockwise() {
        assertEquals(Status.RUINED, BrewRules.match(run(felixSimmered(), cw())).status());
    }

    @Test
    void newIngredientsAreIngredients() {
        assertEquals(DITTANY, BrewRules.DITTANY);
        assertEquals(ASPHODEL, BrewRules.ASPHODEL);
        assertEquals(WORMWOOD, BrewRules.WORMWOOD);
        assertEquals(VALERIAN, BrewRules.VALERIAN_SPRIGS);
        assertEquals(ASHWINDER, BrewRules.ASHWINDER_EGG);
        assertEquals(DUST, BrewRules.ARCANE_DUST);
        assertEquals(NUGGET, BrewRules.GOLD_NUGGET);
        for (String id : List.of(DITTANY, ASPHODEL, WORMWOOD, VALERIAN, ASHWINDER, DUST, NUGGET)) {
            assertTrue(BrewRules.isIngredient(id), id);
        }
    }

    @Test
    void aFirstIngredientNamesTheNewRecipes() {
        assertSame(BrewRules.DITTANY_ESSENCE, BrewRules.match(run(add(DITTANY))).recipe());
        assertSame(BrewRules.MANDRAKE_DRAUGHT, BrewRules.match(run(add(MANDRAKE))).recipe());
        assertSame(BrewRules.LIVING_DEATH, BrewRules.match(run(add(ASPHODEL))).recipe());
        assertSame(BrewRules.FELIX_FELICIS, BrewRules.match(run(add(ASHWINDER))).recipe());
        assertEquals(Status.RUINED, BrewRules.match(run(add(NUGGET))).status());
        assertEquals(Status.RUINED, BrewRules.match(run(add(DUST))).status());
    }

    @Test
    void remorseBrewsFromAGhastTear() {
        List<Step> history = simmer(run(add("minecraft:ghast_tear"), add(DITTANY), add(DITTANY), ccw(), ccw(), ccw()), 90);
        Match match = BrewRules.match(history);
        assertEquals(Status.FINISHED, match.status());
        assertSame(BrewRules.REMORSE, match.recipe());
        assertEquals(BrewRules.MAX_DOSES, match.recipe().doses());
        assertTrue(BrewRules.isIngredient("minecraft:ghast_tear"));
        assertSame(BrewRules.REMORSE, BrewRules.match(run(add("minecraft:ghast_tear"))).recipe());
    }

    @Test
    void veritaserumBrewsFromAFeatherAndAnAmethystShard() {
        List<Step> history = run(add(BrewRules.FEATHER), add(BrewRules.AMETHYST_SHARD), add(VALERIAN), add(VALERIAN));
        for (int i = 0; i < 7; i++) {
            history = run(history, cw());
        }
        history = run(simmer(history, 120), add(DUST));
        Match match = BrewRules.match(history);
        assertEquals(Status.FINISHED, match.status());
        assertSame(BrewRules.VERITASERUM, match.recipe());
        assertEquals(BrewRules.MAX_DOSES, match.recipe().doses());
        assertTrue(BrewRules.isAdvanced(BrewRules.VERITASERUM));
        assertTrue(BrewRules.isIngredient(BrewRules.AMETHYST_SHARD));
    }
}
