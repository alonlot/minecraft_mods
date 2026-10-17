package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.WordText;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WordTextTest {
    @Test void fillsViewer() { assertEquals("Mr. Steve, The Cupboard under the Stairs", WordText.fill("Mr. {viewer}, The Cupboard under the Stairs", "Steve")); }
    @Test void fillsEveryOccurrence() { assertEquals("Steve Steve", WordText.fill("{viewer} {viewer}", "Steve")); }
    @Test void leavesOtherBraces() { assertEquals("{x} Steve", WordText.fill("{x} {viewer}", "Steve")); }
    @Test void nullNameIsEmpty() { assertEquals("Mr. ", WordText.fill("Mr. {viewer}", null)); }
}
