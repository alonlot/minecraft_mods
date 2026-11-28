package com.hogwartsmagic.spell.voice;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceAliasesTest {
    @Test
    void mishearingsFromTheLogsAreLearnedAndThenCast() {
        // Real transcripts from the game log (tiny model, the player's own voice).
        VoiceAliases a = new VoiceAliases();
        assertNull(IncantationMatcher.match("Aqua, Opto.", true).spellId());
        assertEquals(VoiceAliases.Verdict.LEARNED, a.practise("aqua_eructo", "Aqua, Opto.").verdict());
        assertEquals("aqua_eructo", IncantationMatcher.match("Aqua, Opto.", true, a.view()).spellId());
        assertEquals("aqua_eructo", IncantationMatcher.match("aqua opto!", false, a.view()).spellId());
        // Already heard right without an alias: nothing to learn.
        assertEquals(VoiceAliases.Verdict.RIGHT, a.practise("revelio", "Refillio.").verdict());
        assertEquals(VoiceAliases.Verdict.LEARNED, a.practise("ascendio", "That's Sandio.").verdict());
        assertEquals("ascendio", IncantationMatcher.match("That's Sandio.", true, a.view()).spellId());
    }

    @Test
    void aTakeHeardRightTeachesNothing() {
        VoiceAliases a = new VoiceAliases();
        assertEquals(VoiceAliases.Verdict.RIGHT, a.practise("bombarda", "Bombarda.").verdict());
        assertEquals(VoiceAliases.Verdict.RIGHT, a.practise("avada_kedavra", "Abada Kedavra,").verdict());
        assertEquals(0, a.size());
    }

    @Test
    void talkIsNeverKept() {
        VoiceAliases a = new VoiceAliases();
        assertEquals(VoiceAliases.Verdict.TALK, a.practise("revelio", "Have a video.").verdict());
        assertEquals(VoiceAliases.Verdict.TALK, a.practise("accio", "Thank you.").verdict());
        assertEquals(VoiceAliases.Verdict.TALK, a.practise("alatus", "What? Yeah, cool.").verdict());
        assertEquals(0, a.size());
    }

    @Test
    void anotherSpellIsNeverKept() {
        VoiceAliases a = new VoiceAliases();
        VoiceAliases.Judgement j = a.practise("revelio", "Reparo.");
        assertEquals(VoiceAliases.Verdict.OTHER_SPELL, j.verdict());
        assertEquals("reparo", j.otherSpellId());
        assertEquals(0, a.size());
    }

    @Test
    void noAliasStopsAnotherSpellFromCasting() {
        VoiceAliases a = new VoiceAliases();
        a.practise("revelio", "Refillio.");
        a.practise("ascendio", "That's Sandio.");
        a.practise("aqua_eructo", "Aqua, Opto.");
        a.practise("alohomora", "Alamo.");
        Map<String, List<String>> v = a.view();
        for (Map.Entry<String, String> e : IncantationMatcher.incantations().entrySet()) {
            assertEquals(e.getKey(), IncantationMatcher.match(e.getValue(), true, v).spellId(), e.getValue());
            assertEquals(e.getKey(), IncantationMatcher.match(e.getValue(), false, v).spellId(), e.getValue());
        }
    }

    @Test
    void theSameSoundIsKeptOnce() {
        VoiceAliases a = new VoiceAliases();
        assertEquals(VoiceAliases.Verdict.LEARNED, a.practise("alohomora", "Alamo.").verdict());
        // Spelled differently, same sound: the alias already casts, so it counts as heard right.
        VoiceAliases.Verdict v = a.practise("alohomora", "Allamo").verdict();
        assertTrue(v == VoiceAliases.Verdict.RIGHT || v == VoiceAliases.Verdict.ALREADY_KNOWN, v.toString());
        assertEquals(1, a.of("alohomora").size());
    }

    @Test
    void anAliasInsideASentenceDoesNotCastOnTheOpenMic() {
        VoiceAliases a = new VoiceAliases();
        a.practise("aqua_eructo", "Aqua, Opto.");
        assertNull(IncantationMatcher.match("did you see his aqua opto thing", true, a.view()).spellId());
    }

    @Test
    void noiseIsNothing() {
        VoiceAliases a = new VoiceAliases();
        assertEquals(VoiceAliases.Verdict.NOTHING, a.practise("revelio", "").verdict());
        assertEquals(VoiceAliases.Verdict.NOTHING, a.practise("revelio", "Hm.").verdict());
    }

    @Test
    void aSpellKeepsAtMostEight() {
        VoiceAliases a = new VoiceAliases();
        String letters = "abdefghjlmno"; // no two fold to one sound (c/k, y/i, s/z)
        for (int i = 0; i < letters.length(); i++) {
            a.put("revelio", "Zorbla" + letters.charAt(i) + "q");
        }
        assertEquals(VoiceAliases.MAX_PER_SPELL, a.of("revelio").size());
        assertEquals("Zorblafq", a.of("revelio").get(0)); // the oldest four dropped
    }

    @Test
    void unknownSpellIdsAreIgnored() {
        VoiceAliases a = new VoiceAliases();
        a.put("not_a_spell", "Refillio");
        assertEquals(0, a.size());
    }
}
