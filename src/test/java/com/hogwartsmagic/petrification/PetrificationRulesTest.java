package com.hogwartsmagic.petrification;

import com.hogwartsmagic.petrification.PetrificationRules.GazeOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetrificationRulesTest {
    @Test
    void constantsMatchThePlan() {
        assertEquals("Petrified", PetrificationRules.KEY_PETRIFIED);
        assertEquals("PetrifiedSince", PetrificationRules.KEY_SINCE);
        assertEquals("hogwartsmagic:gaze_ward_until", PetrificationRules.WARD_KEY);
        assertEquals("hogwartsmagic:petrification", PetrificationRules.DAMAGE_TYPE);
        assertEquals(0, PetrificationRules.DEFAULT_MAX_SECONDS);
        assertEquals(86400, PetrificationRules.MAX_MAX_SECONDS);
        assertEquals(32, PetrificationRules.TARGET_CLEAR_RADIUS);
        assertEquals(20, PetrificationRules.CHECK_INTERVAL_TICKS);
    }

    @Test
    void aPlayerTurnsToStoneAndAMobDies() {
        assertEquals(GazeOutcome.PETRIFY, PetrificationRules.gazeOutcome(true, false));
        assertEquals(GazeOutcome.KILL, PetrificationRules.gazeOutcome(true, true));
        assertEquals(GazeOutcome.KILL, PetrificationRules.gazeOutcome(false, false));
        assertEquals(GazeOutcome.KILL, PetrificationRules.gazeOutcome(false, true));
    }

    @Test
    void onlyTheVoidKillAndGivingInHurtStone() {
        assertTrue(PetrificationRules.damagePasses("minecraft:out_of_world"));
        assertTrue(PetrificationRules.damagePasses("minecraft:generic_kill"));
        assertTrue(PetrificationRules.damagePasses("hogwartsmagic:petrification"));
        for (String id : List.of("minecraft:magic", "minecraft:lava", "minecraft:starve", "minecraft:drown",
                "minecraft:player_attack", "hogwartsmagic:bleeding", "")) {
            assertFalse(PetrificationRules.damagePasses(id), id);
        }
    }

    @Test
    void noLimitMeansStoneForever() {
        assertFalse(PetrificationRules.limitReached(0L, 1_000_000_000L, 0));
        assertFalse(PetrificationRules.limitReached(0L, 1_000_000_000L, -5));
    }

    @Test
    void theLimitCountsGameTicks() {
        assertFalse(PetrificationRules.limitReached(100L, 199L, 5));
        assertTrue(PetrificationRules.limitReached(100L, 200L, 5));
        assertTrue(PetrificationRules.limitReached(100L, 900L, 5));
    }

    @Test
    void timeAsStoneIsNeverNegative() {
        assertEquals(0L, PetrificationRules.ticksPetrified(100L, 50L));
        assertEquals(60L, PetrificationRules.ticksPetrified(100L, 160L));
    }

    @Test
    void onlyKnownLivingNonBossesComeBack() {
        assertTrue(PetrificationRules.canRevive(true, true, false));
        assertFalse(PetrificationRules.canRevive(false, true, false));
        assertFalse(PetrificationRules.canRevive(true, false, false));
        assertFalse(PetrificationRules.canRevive(true, true, true));
    }

    @Test
    void givingInNeedsALivingStatue() {
        assertTrue(PetrificationRules.giveInAccepted(true, true));
        assertFalse(PetrificationRules.giveInAccepted(false, true));
        assertFalse(PetrificationRules.giveInAccepted(true, false));
    }

    @Test
    void aGiveInThatLeavesTheStatueAliveRearmsTheClock() {
        assertTrue(PetrificationRules.giveInFailed(true, true));
        assertFalse(PetrificationRules.giveInFailed(true, false));
        assertFalse(PetrificationRules.giveInFailed(false, true));
        assertFalse(PetrificationRules.giveInFailed(false, false));
        // re-armed at the failed give-in: the limit starts over, so the time-up line is not sent again next check
        long failedAt = 1000L;
        assertFalse(PetrificationRules.limitReached(failedAt, failedAt + PetrificationRules.CHECK_INTERVAL_TICKS, 5));
        assertTrue(PetrificationRules.limitReached(failedAt, failedAt + 100L, 5));
    }

    @Test
    void theWardLastsEightSeconds() {
        assertEquals(1160L, PetrificationRules.wardUntil(1000L, com.hogwartsmagic.brewing.PotionRules.MANDRAKE_GAZE_WARD_TICKS));
        assertTrue(PetrificationRules.warded(1160L, 1159L));
        assertFalse(PetrificationRules.warded(1160L, 1160L));
        assertFalse(PetrificationRules.warded(0L, 5L));
    }

    @Test
    void aStatueKeepsWhoTheMobWas() {
        for (String key : List.of("Health", "DeathTime", "HurtTime", "HurtByTimestamp", "Fire", "FallDistance",
                "Motion", "Passengers", "leash", "SleepingX", "SleepingY", "SleepingZ", "TicksFrozen",
                "HasVisualFire")) {
            assertTrue(PetrificationRules.STRIPPED_MOB_KEYS.contains(key), key);
        }
        for (String key : List.of("UUID", "CustomName", "Owner", "Pos", "Rotation", "HandItems", "ArmorItems",
                "Inventory", "Offers", "Tags", "active_effects", "attributes", "Brain")) {
            assertFalse(PetrificationRules.STRIPPED_MOB_KEYS.contains(key), key);
        }
    }

    @Test
    void aStatueForgetsTheVillagerTicketsVanillaReleased() {
        assertEquals(List.of("minecraft:job_site", "minecraft:potential_job_site", "minecraft:home",
                "minecraft:meeting_point"), PetrificationRules.RELEASED_MEMORIES);
        assertFalse(PetrificationRules.RELEASED_MEMORIES.contains("minecraft:gossips"));
    }

    @Test
    void eachPlayerIsCheckedOnceASecond() {
        int[] ids = {0, 7, 19, 12345, -3};
        for (int id : ids) {
            int due = 0;
            for (long t = 1000L; t < 1020L; t++) {
                if (PetrificationRules.dueCheck(t, id)) {
                    due++;
                    assertTrue(PetrificationRules.dueCheck(t + 20L, id));
                }
            }
            assertEquals(1, due, "id " + id);
        }
    }
}
