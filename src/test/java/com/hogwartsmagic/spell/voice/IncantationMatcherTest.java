package com.hogwartsmagic.spell.voice;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncantationMatcherTest {
    private static String id(String heard) {
        return IncantationMatcher.match(heard).spellId();
    }

    @Test
    void everyIncantationMatchesItself() {
        for (Map.Entry<String, String> e : IncantationMatcher.incantations().entrySet()) {
            assertEquals(e.getKey(), id(e.getValue()), e.getValue());
            assertEquals(e.getKey(), id(e.getValue().toUpperCase() + "!"), e.getValue());
        }
    }

    @Test
    void noTwoSpellsAreTooCloseToTellApart() {
        // Every spell, said exactly, must clear the margin against every other spell.
        for (Map.Entry<String, String> e : IncantationMatcher.incantations().entrySet()) {
            IncantationMatcher.Result r = IncantationMatcher.match(e.getValue());
            assertTrue(r.margin() >= IncantationMatcher.MIN_MARGIN, e.getValue() + " margin " + r.margin());
        }
    }

    @Test
    void nearMissesTheRecogniserReallyWrote() {
        // Transcripts from the speech engine test (synthetic voices, tiny English model).
        assertEquals("diffindo", id(" Defendo."));
        assertEquals("imperio", id(" Embeddio."));
        assertEquals("accio", id(" Acho."));
        assertEquals("sectumsempra", id(" Sekdomemsempra."));
        assertEquals("incarcerous", id(" Incarthris!"));
        assertEquals("stupefy", id(" Stupefyre.")); // every voice: the prompt's Fiendfyre bleeds in
        assertEquals("stupefy", id("Stupefyre, Stupefyre, Stupefyre, Stupefyre,")); // a repeat loop
        assertEquals("expecto_patronum", id("Inspector Patronum."));
        assertEquals("protego", id("Tretego!"));
        assertEquals("sectumsempra", id("Sector Sempa!"));
    }

    @Test
    void fillerAndRepeatsStillFindTheSpell() {
        assertEquals("stupefy", id("Uh, Stupefy!"));
        assertEquals("stupefy", id("Stupefy, Stupefy!"));
        assertEquals("wingardium_leviosa", id("okay Wingardium Leviosa"));
        assertEquals("avada_kedavra", id("Avada Kedavra."));
    }

    @Test
    void normalSpeechCastsNothing() {
        String[] lines = {
                "", "   ", "...", "Hello there.", "Let us go to the castle.", "Can you help me?", "I need more wood.",
                "Where is the creeper?", "Look at that sunset.", "Follow me.", "Nice house.", "I am going to bed.",
                "Wait for me.", "Thank you.", "Yes.", "No.", "Okay.", "What?", "Hi", "Go", "Oh", "Hmm",
                "That was an accident.", "I am confused.", "Protect the village.", "That is incredible.",
                // Accepted near matches, not listed: "Ascending now" (Ascendio) and "A lot of us" (Alatus).
                // Push-to-talk means the player meant to cast when the key was down.
                "Obviously.", "Stupid zombie.", "Defend the base.",
                "[BLANK_AUDIO]", "(wind blowing)", "you"
        };
        for (String line : lines) {
            assertNull(id(line), line);
        }
    }

    private static String strict(String heard) {
        return IncantationMatcher.match(heard, true).spellId();
    }

    @Test
    void openMicCastsASpellSaidOnItsOwn() {
        for (Map.Entry<String, String> e : IncantationMatcher.incantations().entrySet()) {
            assertEquals(e.getKey(), strict(e.getValue() + "!"), e.getValue());
        }
        assertEquals("protego_maxima", strict("Protego Maxima!")); // holds "Protego", still casts
        assertEquals("stupefy", strict("Stupefy, Stupefy!"));
        assertEquals("piertotum_locomotor", strict("Pier totum locomotor")); // one word split by the recogniser
        assertEquals("sectumsempra", strict("Sector Sempa!"));
        assertEquals("expecto_patronum", strict("Inspector Patronum."));
        assertEquals("stupefy", strict(" Stupefyre."));
    }

    @Test
    void openMicNeverCastsASpellNamedInsideASentence() {
        String[] templates = {"Did you see his %s?", "I need to learn %s", "%s is so useful", "Cast %s on him",
                "He used %s on me", "Let us try %s later", "The %s spell", "Do you know %s?", "%s did not work",
                "%s him!", "Use %s", "Try %s", "Oh %s", "My %s"};
        for (String words : IncantationMatcher.incantations().values()) {
            for (String t : templates) {
                String line = String.format(t, words);
                assertNull(strict(line), line);
            }
        }
    }

    @Test
    void openMicIgnoresTalkThatOnlySoundsLikeASpell() {
        assertNull(strict("A lot of us.")); // Alatus with push-to-talk
        assertNull(strict("Ascending now."));
        assertNull(strict("Protect the village."));
        assertNull(strict("[Music]"));
        assertNull(strict("[XBOX SOUND]"));
        assertNull(strict("Thank you."));
    }

    @Test
    void pushToTalkStillAcceptsFillerAroundTheSpell() {
        assertEquals("stupefy", id("Uh, Stupefy!"));
        assertNull(strict("Uh, Stupefy!")); // the open mic is stricter
    }

    @Test
    void shortNoiseNeverCasts() {
        assertNull(id("Lu"));
        assertNull(id("a"));
        assertFalse(IncantationMatcher.match("ok").matched());
    }

    @Test
    void foldingTreatsSpellingVariantsAsTheSameSound() {
        assertEquals(IncantationMatcher.fold("fiendfire"), IncantationMatcher.fold("fiendfyre"));
        assertEquals(IncantationMatcher.fold("akio"), IncantationMatcher.fold("accio"));
        assertEquals("difindo", IncantationMatcher.fold("diffindo"));
    }

    @Test
    void wordsDropApostrophesAndPunctuation() {
        assertEquals(java.util.List.of("dolohovs", "curse"), IncantationMatcher.words("Dolohov's Curse!"));
        assertTrue(IncantationMatcher.words(null).isEmpty());
    }

    @Test
    void promptNamesEverySpellOnce() {
        String prompt = IncantationMatcher.recogniserPrompt();
        Set<String> seen = new HashSet<>();
        for (String words : IncantationMatcher.incantations().values()) {
            assertTrue(prompt.contains(words), words);
            assertTrue(seen.add(words), "duplicate " + words);
        }
    }

    /** The gargoyle's password said aloud: the recogniser's spellings and punctuation pass, other talk does not. */
    @Test
    void saysThePasswordAsTheRecogniserWritesIt() {
        String password = "Sherbet Lemon";
        for (String heard : new String[]{"Sherbet Lemon", "Sherbet lemon.", "sherbet lemon!", "Sherbert Lemon",
                "Sherbet Lemmon", " Sherbet  Lemon. "}) {
            assertTrue(IncantationMatcher.saysPhrase(heard, password), heard);
        }
        for (String heard : new String[]{"Lemon", "Sherbet", "Lemon sherbet please", "I love sherbet lemon so much",
                "Expelliarmus", "", "uh"}) {
            assertTrue(!IncantationMatcher.saysPhrase(heard, password), heard);
        }
    }
}
