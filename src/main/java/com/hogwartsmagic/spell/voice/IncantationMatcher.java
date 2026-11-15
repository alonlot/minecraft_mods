package com.hogwartsmagic.spell.voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns the text a speech recogniser heard into a spell id. Pure Java (no Minecraft classes), so it is unit tested.
 *
 * <p>The recogniser is told the spell names, but it still writes near misses for made-up Latin words
 * ("Defendo" for Diffindo, "Acho" for Accio). The matcher folds spelling to sound (ph to f, c to k, double letters
 * to one), then scores an edit distance where vowel swaps and voiced/unvoiced swaps (b/p, d/t...) cost half.
 * A spell is only returned when it is close enough and clearly closer than the next spell.</p>
 */
public final class IncantationMatcher {
    /** Most of a spell must be right: a third of its sounds may differ. */
    static final double MAX_DISTANCE = 0.34;
    /** The best spell must beat the second best by this much, or nothing is cast (a guess is worse than a miss). */
    static final double MIN_MARGIN = 0.08;
    /** Shorter heard text never casts: one syllable matches too much. */
    static final int MIN_LETTERS = 3;
    /**
     * Added when only some of the heard words match the spell. Without it "Protego Maxima" ties with Protego, and
     * "Protect the village" casts Protego. "Uh, Stupefy" still casts (an exact span scores just this).
     */
    static final double PARTIAL_PENALTY = 0.15;
    /**
     * Open mic only: added for each heard word beyond the spell's own word count, so short talk that merely sounds
     * like a spell ("A lot of us" for Alatus) does not cast. Spell names inside a sentence are refused by
     * {@code spellInsideSentence}.
     */
    static final double STRICT_EXTRA_WORD_PENALTY = 0.08;
    /** Open mic only: a run of heard words this close to the spell is the spell itself, said inside a sentence. */
    static final double STRICT_SPAN_HIT = 0.25;

    /** Spell id to the words a player says. The ids are {@code SpellType} ids. */
    private static final Map<String, String> INCANTATIONS = new LinkedHashMap<>();

    static {
        put("expelliarmus", "Expelliarmus");
        put("glacius", "Glacius");
        put("confringo", "Confringo");
        put("protego", "Protego");
        put("protego_maxima", "Protego Maxima");
        put("lumos", "Lumos");
        put("accio", "Accio");
        put("wingardium_leviosa", "Wingardium Leviosa");
        put("alohomora", "Alohomora");
        put("incendio", "Incendio");
        put("stupefy", "Stupefy");
        put("revelio", "Revelio");
        put("avada_kedavra", "Avada Kedavra");
        put("crucio", "Crucio");
        put("imperio", "Imperio");
        put("fiendfyre", "Fiendfyre");
        put("sectumsempra", "Sectumsempra");
        put("morsmordre", "Morsmordre");
        put("dolohov_curse", "Dolohov's Curse");
        put("incarcerous", "Incarcerous");
        put("heal", "Episkey");
        put("tenebris_flight", "Tenebris Flight");
        put("expecto_patronum", "Expecto Patronum");
        put("bombarda", "Bombarda");
        put("depulso", "Depulso");
        put("levicorpus", "Levicorpus");
        put("diffindo", "Diffindo");
        put("petrificus_totalus", "Petrificus Totalus");
        put("aqua_eructo", "Aqua Eructo");
        put("obliviate", "Obliviate");
        put("ascendio", "Ascendio");
        put("confundo", "Confundo");
        put("cave_inimicum", "Cave Inimicum");
        put("alatus", "Alatus");
        put("serpensortia", "Serpensortia");
        put("arresto_momentum", "Arresto Momentum");
        put("reparo", "Reparo");
        put("apparition", "Apparition");
        put("piertotum_locomotor", "Piertotum Locomotor");
        put("colloportus", "Colloportus");
    }

    private static void put(String id, String words) {
        INCANTATIONS.put(id, words);
    }

    private IncantationMatcher() {
    }

    /** Spell id to spoken words, in a fixed order. */
    public static Map<String, String> incantations() {
        return Collections.unmodifiableMap(INCANTATIONS);
    }

    /** The prompt that primes the recogniser with the spell names, so it spells them the way we expect. */
    public static String recogniserPrompt() {
        return "Spells: " + String.join(", ", INCANTATIONS.values()) + ".";
    }

    /** What the matcher decided, with the scores, so the caller can say what was heard. */
    public record Result(String spellId, double distance, double margin) {
        public boolean matched() {
            return spellId != null;
        }
    }

    /** The best spell for {@code heard}, or a result with a null id when nothing is close enough. */
    public static Result match(String heard) {
        return match(heard, false, STRICT_EXTRA_WORD_PENALTY);
    }

    /**
     * {@code strict} is for the open mic: the whole heard text must be the spell. No run of words inside a longer
     * sentence counts, and every extra word costs {@link #STRICT_EXTRA_WORD_PENALTY}. Push-to-talk uses the
     * lenient form, because the player pressed the key to cast.
     */
    public static Result match(String heard, boolean strict) {
        return match(heard, strict, STRICT_EXTRA_WORD_PENALTY);
    }

    static Result match(String heard, boolean strict, double extraWordPenalty) {
        List<String> words = collapseRepeats(words(heard));
        if (words.isEmpty()) {
            return new Result(null, 1.0, 0.0);
        }
        double best = Double.MAX_VALUE;
        double second = Double.MAX_VALUE;
        String bestId = null;
        for (Map.Entry<String, String> e : INCANTATIONS.entrySet()) {
            double d = strict
                    ? strictDistanceToSpell(words, e.getValue(), extraWordPenalty)
                    : distanceToSpell(words, e.getValue());
            if (d < best) {
                second = best;
                best = d;
                bestId = e.getKey();
            } else if (d < second) {
                second = d;
            }
        }
        double margin = second - best;
        // Open mic: a sentence that names a spell never casts ("Oh Depulso", "Protego him!").
        if (strict && bestId != null && spellInsideSentence(words, INCANTATIONS.get(bestId))) {
            return new Result(null, best, margin);
        }
        if (bestId == null || best > MAX_DISTANCE || margin < MIN_MARGIN) {
            return new Result(null, best, margin);
        }
        return new Result(bestId, best, margin);
    }

    /** The whole heard text against the spell, plus {@code extraWordPenalty} for each word beyond the spell's count. */
    static double strictDistanceToSpell(List<String> heardWords, String incantation, double extraWordPenalty) {
        List<String> spell = words(incantation);
        int extra = Math.max(0, heardWords.size() - spell.size());
        return score(fold(String.join("", heardWords)), fold(String.join("", spell))) + extra * extraWordPenalty;
    }

    /**
     * True when a shorter run of the heard words is, on its own, a spell ({@link #STRICT_SPAN_HIT}): the best spell
     * itself said with extra words ("Oh Depulso", "Protego him!"), or another spell inside talk ("Did you see his
     * Stupefy?"). A spell whose words are all part of the best spell does not count, so "Protego Maxima" (which
     * holds "Protego") still casts. A recogniser that splits one made-up word in two ("Fiend fear") has no such run.
     */
    static boolean spellInsideSentence(List<String> heardWords, String bestIncantation) {
        List<String> bestWords = words(bestIncantation);
        String bestTarget = fold(String.join("", bestWords));
        double whole = score(fold(String.join("", heardWords)), bestTarget);
        for (int len = 1; len < heardWords.size(); len++) {
            for (int start = 0; start + len <= heardWords.size(); start++) {
                String span = fold(String.join("", heardWords.subList(start, start + len)));
                for (String incantation : INCANTATIONS.values()) {
                    List<String> spellWords = words(incantation);
                    double d = score(span, fold(String.join("", spellWords)));
                    if (d > STRICT_SPAN_HIT) {
                        continue;
                    }
                    if (incantation.equals(bestIncantation)) {
                        // The best spell inside the text: extra words only when the run beats the whole text
                        // ("Oh Depulso"), not when the whole is closer ("Pier totum locomotor").
                        if (d < whole) {
                            return true;
                        }
                    } else if (!bestWords.containsAll(spellWords)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * The whole heard text against the spell, and also every run of heard words as long as the spell, so
     * "uh, Stupefy" and "Stupefy, Stupefy" still find it. A run that leaves words out pays {@link #PARTIAL_PENALTY}.
     * The best (lowest) score wins.
     */
    static double distanceToSpell(List<String> heardWords, String incantation) {
        String target = fold(String.join("", words(incantation)));
        int spellWords = words(incantation).size();
        double best = score(fold(String.join("", heardWords)), target);
        if (heardWords.size() > spellWords) {
            for (int start = 0; start + spellWords <= heardWords.size(); start++) {
                String span = fold(String.join("", heardWords.subList(start, start + spellWords)));
                best = Math.min(best, score(span, target) + PARTIAL_PENALTY);
            }
        }
        return best;
    }

    private static double score(String heard, String target) {
        if (heard.length() < MIN_LETTERS) {
            return 1.0;
        }
        return weightedDistance(heard, target) / Math.max(heard.length(), target.length());
    }

    /** Lower case letters only, split on anything else. An apostrophe is dropped, not split on ("Dolohov's"). */
    static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        String cleaned = text.toLowerCase(Locale.ROOT).replace("'", "");
        for (String w : cleaned.split("[^a-z]+")) {
            if (!w.isEmpty()) {
                out.add(w);
            }
        }
        return out;
    }

    /**
     * Drops a word that repeats the one before it. Whisper sometimes loops on a word it is unsure of
     * ("Stupefyre, Stupefyre, Stupefyre, Stupefyre"), which would otherwise be too long to match.
     */
    static List<String> collapseRepeats(List<String> words) {
        List<String> out = new ArrayList<>(words.size());
        for (String w : words) {
            if (out.isEmpty() || !out.get(out.size() - 1).equals(w)) {
                out.add(w);
            }
        }
        return out;
    }

    /** Spelling to sound: the recogniser picks a spelling at random when it does not know the word. */
    static String fold(String s) {
        String t = s.replace("ph", "f").replace("ck", "k").replace("qu", "kw").replace('c', 'k').replace('y', 'i')
                .replace('z', 's').replace('x', 'k');
        StringBuilder out = new StringBuilder(t.length());
        char last = 0;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch != last) {
                out.append(ch);
            }
            last = ch;
        }
        return out.toString();
    }

    private static boolean vowel(char c) {
        return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u';
    }

    private static boolean voicedPair(char a, char b) {
        String pairs = "bpdtgkvfsj";
        int ia = pairs.indexOf(a);
        int ib = pairs.indexOf(b);
        return ia >= 0 && ib >= 0 && ia / 2 == ib / 2 && ia != ib;
    }

    private static double substitution(char a, char b) {
        if (a == b) {
            return 0.0;
        }
        if ((vowel(a) && vowel(b)) || voicedPair(a, b)) {
            return 0.5;
        }
        return 1.0;
    }

    static double weightedDistance(String a, String b) {
        double[] prev = new double[b.length() + 1];
        double[] cur = new double[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                double sub = prev[j - 1] + substitution(a.charAt(i - 1), b.charAt(j - 1));
                cur[j] = Math.min(sub, Math.min(prev[j] + 1.0, cur[j - 1] + 1.0));
            }
            double[] swap = prev;
            prev = cur;
            cur = swap;
        }
        return prev[b.length()];
    }
}
