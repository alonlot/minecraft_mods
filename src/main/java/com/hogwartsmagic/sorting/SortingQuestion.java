package com.hogwartsmagic.sorting;

import java.util.Map;

/** One fixed Sorting Hat question: its text, the voice line id, and one answer per house. */
public record SortingQuestion(String id, String text, String voice, Map<HogwartsHouse, String> answers) {
}
