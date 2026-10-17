package com.hogwartsmagic.sorting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class SortingQuestionBankTest {
    private static JsonObject question(String id, int answerCount) {
        JsonObject q = new JsonObject();
        q.addProperty("id", id);
        q.addProperty("text", "Question " + id);
        q.addProperty("voice", "hogwartsmagic:sorting.voice." + id);
        JsonObject answers = new JsonObject();
        HogwartsHouse[] houses = HogwartsHouse.values();
        for (int i = 0; i < answerCount; i++) {
            answers.addProperty(houses[i].name(), "Answer " + houses[i].name());
        }
        q.add("answers", answers);
        return q;
    }

    private static JsonObject root(JsonObject... questions) {
        JsonArray array = new JsonArray();
        for (JsonObject q : questions) {
            array.add(q);
        }
        JsonObject root = new JsonObject();
        root.add("questions", array);
        return root;
    }

    @Test
    void shippedBankHasTenValidQuestions() {
        var b = SortingQuestionBank.load();
        assertEquals(10, b.size());
        b.forEach(q -> assertEquals(EnumSet.allOf(HogwartsHouse.class), q.answers().keySet()));
        b.forEach(q -> assertTrue(q.voice().startsWith("hogwartsmagic:sorting.voice.")));
    }

    @Test
    void parsesAWellFormedQuestion() {
        var b = SortingQuestionBank.parse(root(question("q1", 4)));
        assertEquals(1, b.size());
        assertEquals("q1", b.get(0).id());
        assertEquals("Answer RAVENCLAW", b.get(0).answers().get(HogwartsHouse.RAVENCLAW));
    }

    @Test
    void rejectsMissingHouse() {
        assertThrows(IllegalArgumentException.class, () -> SortingQuestionBank.parse(root(question("q1", 3))));
    }

    @Test
    void rejectsDuplicateIds() {
        assertThrows(IllegalArgumentException.class,
                () -> SortingQuestionBank.parse(root(question("q1", 4), question("q1", 4))));
    }
}
