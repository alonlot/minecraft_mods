package com.hogwartsmagic.spell.voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Personal aliases from voice practice: what the recogniser writes when this player says a spell ("Refillio" for
 * Revelio). Pure Java, unit tested; the client keeps one set per speech model in a file
 * ({@code client.voice.VoiceAliasStore}), because each model mishears in its own way.
 *
 * <p>A take is kept as an alias only when it is safe to: not empty, not normal talk (every word a common English
 * word: "Have a video" must never cast), not another spell or near enough to one to steal its casts, and not
 * making any other spell's own incantation stop matching.</p>
 */
public final class VoiceAliases {
    /** Most aliases kept for one spell; the oldest is dropped past this. */
    public static final int MAX_PER_SPELL = 8;

    /** What a practice take did. */
    public enum Verdict {
        /** The recogniser already heard the spell: nothing to learn. */
        RIGHT,
        /** A new alias was kept. */
        LEARNED,
        /** The same alias (by sound) is already kept. */
        ALREADY_KNOWN,
        /** Too short or empty: nothing usable was heard. */
        NOTHING,
        /** Ordinary English words: not kept, it would cast from talk. */
        TALK,
        /** It is, or is close to, another spell: not kept, it would cast the wrong one. */
        OTHER_SPELL
    }

    /** The verdict, and the spell it was mistaken for ({@link Verdict#OTHER_SPELL} only). */
    public record Judgement(Verdict verdict, String otherSpellId) {
    }

    private final Map<String, List<String>> bySpell = new LinkedHashMap<>();

    /** A read-only view, spell id to phrases (the form {@link IncantationMatcher#match} takes). */
    public Map<String, List<String>> view() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : bySpell.entrySet()) {
            out.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
        }
        return Collections.unmodifiableMap(out);
    }

    public List<String> of(String spellId) {
        List<String> l = bySpell.get(spellId);
        return l == null ? List.of() : Collections.unmodifiableList(l);
    }

    public int size() {
        int n = 0;
        for (List<String> l : bySpell.values()) {
            n += l.size();
        }
        return n;
    }

    /** Adds without judging (loading a saved file). Unknown spell ids are skipped. */
    public void put(String spellId, String phrase) {
        if (!IncantationMatcher.incantations().containsKey(spellId) || IncantationMatcher.words(phrase).isEmpty()) {
            return;
        }
        List<String> l = bySpell.computeIfAbsent(spellId, k -> new ArrayList<>());
        if (indexBySound(l, phrase) >= 0) {
            return;
        }
        l.add(phrase.strip());
        while (l.size() > MAX_PER_SPELL) {
            l.remove(0);
        }
    }

    public void clear(String spellId) {
        bySpell.remove(spellId);
    }

    public void clearAll() {
        bySpell.clear();
    }

    /**
     * Judges one practice take of {@code spellId} that the recogniser wrote as {@code heard}, and keeps it as an
     * alias when that is safe ({@link Verdict#LEARNED}).
     */
    public Judgement practise(String spellId, String heard) {
        Judgement j = judge(spellId, heard);
        if (j.verdict() == Verdict.LEARNED) {
            put(spellId, heard);
        }
        return j;
    }

    /** {@link #practise} without keeping anything. */
    public Judgement judge(String spellId, String heard) {
        List<String> words = IncantationMatcher.collapseRepeats(IncantationMatcher.words(heard));
        if (words.isEmpty() || String.join("", words).length() < IncantationMatcher.MIN_LETTERS) {
            return new Judgement(Verdict.NOTHING, null);
        }
        Map<String, List<String>> current = view();
        // Heard right on the strict open mic (or on push-to-talk, e.g. "Uh, Revelio"): nothing to learn.
        IncantationMatcher.Result strict = IncantationMatcher.match(heard, true, current);
        IncantationMatcher.Result lenient = IncantationMatcher.match(heard, false, current);
        if (spellId.equals(strict.spellId()) || spellId.equals(lenient.spellId())) {
            return new Judgement(Verdict.RIGHT, null);
        }
        if (indexBySound(bySpell.getOrDefault(spellId, List.of()), heard) >= 0) {
            return new Judgement(Verdict.ALREADY_KNOWN, null);
        }
        if (lenient.spellId() != null) {
            return new Judgement(Verdict.OTHER_SPELL, lenient.spellId());
        }
        if (isTalk(words)) {
            return new Judgement(Verdict.TALK, null);
        }
        // Would this alias stop another spell (said right) from casting, or win against it?
        Map<String, List<String>> trial = new LinkedHashMap<>(current);
        List<String> mine = new ArrayList<>(trial.getOrDefault(spellId, List.of()));
        mine.add(heard);
        trial.put(spellId, mine);
        for (Map.Entry<String, String> e : IncantationMatcher.incantations().entrySet()) {
            if (e.getKey().equals(spellId)) {
                continue;
            }
            String said = e.getValue();
            if (!e.getKey().equals(IncantationMatcher.match(said, true, trial).spellId())
                    || !e.getKey().equals(IncantationMatcher.match(said, false, trial).spellId())) {
                return new Judgement(Verdict.OTHER_SPELL, e.getKey());
            }
        }
        return new Judgement(Verdict.LEARNED, null);
    }

    /** Every word an everyday English word: someone talking, not a mangled spell. */
    static boolean isTalk(List<String> words) {
        for (String w : words) {
            if (!COMMON.contains(w)) {
                return false;
            }
        }
        return true;
    }

    private static int indexBySound(List<String> phrases, String phrase) {
        String key = IncantationMatcher.fold(String.join("", IncantationMatcher.words(phrase)));
        for (int i = 0; i < phrases.size(); i++) {
            if (IncantationMatcher.fold(String.join("", IncantationMatcher.words(phrases.get(i)))).equals(key)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Everyday English words, lower case, apostrophes dropped (as {@link IncantationMatcher#words} leaves them).
     * The most common words of spoken English, and the ones the recogniser has been seen to write from talk and
     * noise in this mod's logs ("Thank you", "Have a video", "What? Yeah, cool.").
     */
    private static final Set<String> COMMON = Set.of(
            "a", "about", "above", "after", "again", "against", "ah", "all", "almost", "alone", "along", "already",
            "also", "always", "am", "an", "and", "another", "any", "anyone", "anything", "are", "around", "as", "ask",
            "at", "away", "back", "bad", "be", "because", "bed", "been", "before", "behind", "being", "below",
            "best", "better", "between", "big", "bit", "black", "blue", "book", "both", "boy", "bring", "brother",
            "but", "buy", "by", "call", "came", "can", "cant", "car", "come", "cool", "could", "couldnt", "cut",
            "day", "dead", "did", "didnt", "do", "does", "doesnt", "dog", "doing", "done", "dont", "door", "down",
            "each", "eat", "else", "end", "enough", "even", "ever", "every", "everyone", "everything", "eye", "eyes",
            "face", "far", "fast", "father", "feel", "few", "find", "fine", "first", "five", "for", "found", "four",
            "friend", "from", "fun", "game", "gave", "get", "gets", "getting", "girl", "give", "go", "goes", "going",
            "gone", "good", "got", "great", "guy", "guys", "had", "hand", "happy", "hard", "has", "have", "he",
            "head", "hear", "hello", "help", "her", "here", "hes", "hey", "hi", "him", "his", "hit", "hm", "hmm",
            "hold", "home", "hot", "house", "how", "huh", "i", "if", "ill", "im", "in", "into", "is", "isnt", "it",
            "its", "ive", "just", "keep", "kill", "kind", "know", "last", "later", "left", "lemme", "let", "lets",
            "life", "like", "little", "live", "long", "look", "lot", "love", "made", "make", "man", "many", "may",
            "maybe", "me", "mean", "men", "might", "mine", "money", "more", "most", "mother", "move", "much", "must",
            "my", "name", "need", "never", "new", "next", "nice", "night", "no", "nope", "not", "nothing", "now",
            "of", "off", "oh", "ok", "okay", "old", "on", "once", "one", "only", "open", "or", "other", "our", "out",
            "over", "own", "people", "place", "play", "please", "point", "pretty", "put", "quit", "quite", "read",
            "ready", "real", "really", "red", "right", "room", "run", "said", "same", "saw", "say", "see", "seen",
            "send", "she", "shes", "shot", "should", "show", "side", "since", "sit", "so", "some", "someone",
            "something", "sorry", "start", "still", "stop", "stopped", "stuff", "such", "sure", "take", "talk",
            "tell", "than", "thank", "thanks", "that", "thats", "the", "their", "them", "then", "there", "theres",
            "these", "they", "theyre", "thing", "things", "think", "this", "those", "though", "thought", "three",
            "through", "time", "to", "today", "together", "told", "too", "took", "top", "try", "turn", "two", "uh",
            "um", "under", "until", "up", "us", "use", "very", "video", "wait", "walk", "want", "was", "wasnt",
            "watch", "water", "way", "we", "well", "went", "were", "what", "whats", "when", "where", "which",
            "while", "white", "who", "whole", "why", "will", "with", "without", "wont", "word", "work", "world",
            "would", "wow", "yeah", "yep", "yes", "yet", "you", "youll", "your", "youre", "yup");
}
