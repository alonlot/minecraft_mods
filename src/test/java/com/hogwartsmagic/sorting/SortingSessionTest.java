package com.hogwartsmagic.sorting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SortingSessionTest {
    private static final List<HogwartsHouse> ENUM_ORDER = List.of(HogwartsHouse.values());

    private static List<SortingQuestion> bank() {
        List<SortingQuestion> bank = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            var answers = new EnumMap<HogwartsHouse, String>(HogwartsHouse.class);
            for (HogwartsHouse h : HogwartsHouse.values()) {
                answers.put(h, "a" + i + h.name());
            }
            bank.add(new SortingQuestion("q" + i, "text" + i, "voice" + i, answers));
        }
        return bank;
    }

    private static SortingSession session() {
        return new SortingSession(bank(), new Random(42));
    }

    /** Answers the current question with the choice that displays the given house. */
    private static SortingSession.Outcome pick(SortingSession s, HogwartsHouse house) {
        return s.answer(s.step(), s.order().indexOf(house));
    }

    @Test
    void drawsThreeDistinctQuestions() {
        for (long seed = 0; seed < 30; seed++) {
            var s = new SortingSession(bank(), new Random(seed));
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < 3; i++) {
                assertEquals(i, s.step());
                ids.add(s.question().id());
                pick(s, HogwartsHouse.GRYFFINDOR);
            }
            assertEquals(3, ids.size());
        }
    }

    @Test
    void majorityWins() {
        var s = session();
        assertEquals(SortingSession.Outcome.NEXT_QUESTION, pick(s, HogwartsHouse.GRYFFINDOR));
        assertEquals(SortingSession.Outcome.NEXT_QUESTION, pick(s, HogwartsHouse.GRYFFINDOR));
        assertEquals(SortingSession.Outcome.SORTED, pick(s, HogwartsHouse.RAVENCLAW));
        assertEquals(4, s.step());
        assertEquals(HogwartsHouse.GRYFFINDOR, s.result());
    }

    @Test
    void threeWayTieAsksPick() {
        var s = session();
        pick(s, HogwartsHouse.RAVENCLAW);
        pick(s, HogwartsHouse.GRYFFINDOR);
        assertEquals(SortingSession.Outcome.TIE, pick(s, HogwartsHouse.HUFFLEPUFF));
        assertEquals(3, s.step());
        assertEquals(List.of(HogwartsHouse.GRYFFINDOR, HogwartsHouse.RAVENCLAW, HogwartsHouse.HUFFLEPUFF), s.tied());
    }

    @Test
    void tiePickSorts() {
        var s = session();
        pick(s, HogwartsHouse.GRYFFINDOR);
        pick(s, HogwartsHouse.RAVENCLAW);
        pick(s, HogwartsHouse.SLYTHERIN);
        int idx = s.tied().indexOf(HogwartsHouse.RAVENCLAW);
        assertEquals(SortingSession.Outcome.SORTED, s.answer(3, idx));
        assertEquals(HogwartsHouse.RAVENCLAW, s.result());
        assertEquals(4, s.step());
    }

    @Test
    void rejectsOutOfRangeChoice() {
        var s = session();
        assertEquals(SortingSession.Outcome.REJECTED, s.answer(0, 4));
        assertEquals(SortingSession.Outcome.REJECTED, s.answer(0, -1));
        assertEquals(0, s.step());
    }

    @Test
    void rejectsStaleStep() {
        var s = session();
        assertEquals(SortingSession.Outcome.NEXT_QUESTION, s.answer(0, 0));
        assertEquals(SortingSession.Outcome.REJECTED, s.answer(0, 0));
        assertEquals(1, s.step());
    }

    @Test
    void rejectsNonTiedPick() {
        var s = session();
        pick(s, HogwartsHouse.GRYFFINDOR);
        pick(s, HogwartsHouse.RAVENCLAW);
        pick(s, HogwartsHouse.HUFFLEPUFF);
        assertEquals(SortingSession.Outcome.REJECTED, s.answer(3, 3));
        assertEquals(SortingSession.Outcome.REJECTED, s.answer(3, -1));
        assertEquals(3, s.step());
    }

    @Test
    void orderIsShuffledPerQuestion() {
        boolean sawShuffled = false;
        for (long seed = 0; seed < 50; seed++) {
            var s = new SortingSession(bank(), new Random(seed));
            assertEquals(4, s.order().size());
            assertEquals(Set.copyOf(ENUM_ORDER), Set.copyOf(s.order()));
            if (!s.order().equals(ENUM_ORDER)) {
                sawShuffled = true;
            }
        }
        assertTrue(sawShuffled);
    }
}
