package com.hogwartsmagic.brewing;

import java.util.ArrayList;
import java.util.List;

/**
 * Cauldron brewing: the recipe table, the step model, how a history matches the recipes, when time simmers, the
 * overbrew clock and the liquid colour. Pure: ingredients are registry-name strings.
 *
 * A history is a list of runs. Consecutive identical actions merge: two glow berries in a row are one run of 2,
 * and each simmered second adds 1 to the simmer run. A history fits a recipe when every run but the last equals
 * the recipe step at its index and the last run is the same action with an amount up to the step's. After the last
 * required step, runs must be the recipe's optional tail steps, in order, each exactly once. A recipe may not have
 * two consecutive steps of the same action.
 */
public final class BrewRules {
    public static final String HONEY_BOTTLE = "minecraft:honey_bottle";
    public static final String GLOW_BERRIES = "minecraft:glow_berries";
    public static final String BLAZE_POWDER = "minecraft:blaze_powder";
    public static final String REDSTONE = "minecraft:redstone";
    public static final String GUNPOWDER = "minecraft:gunpowder";
    public static final String MANDRAKE_ROOT = "hogwartsmagic:mandrake_root";
    public static final String ACROMANTULA_VENOM = "hogwartsmagic:acromantula_venom";
    public static final String GILLYWEED = "hogwartsmagic:gillyweed";
    public static final String POLYJUICE_SAMPLE = "hogwartsmagic:polyjuice_sample";

    /** A full cauldron; ingredients go only into a full one, so a brew gives this many doses. */
    public static final int MAX_DOSES = 3;
    /** A finished brew (or one waiting after its last simmer) on heat for longer than this is ruined. */
    public static final int OVERBREW_SECONDS = 120;
    /** The last seconds before an overbrew, when the cauldron warns. */
    public static final int OVERBREW_WARNING_SECONDS = 20;
    /** While simmering, the client colour is refreshed this often (and when the step completes). */
    public static final int SIMMER_SYNC_SECONDS = 5;

    /** Plain water. */
    public static final int WATER_COLOR = 0x3F76E4;
    /** A cauldron nobody has used: the green that decorative cauldrons always showed. */
    public static final int CLASSIC_GREEN = 0x3A9E4E;
    /** A ruined brew: grey-brown. */
    public static final int RUINED_COLOR = 0x6B5A45;

    public enum Kind {
        ADD, STIR_CLOCKWISE, STIR_ANTICLOCKWISE, SIMMER;

        /** The kind with this name, or null (an unknown name from a damaged save). */
        public static Kind byName(String name) {
            for (Kind kind : values()) {
                if (kind.name().equals(name)) {
                    return kind;
                }
            }
            return null;
        }
    }

    /**
     * One recipe step, or one run of the history. item: the registry name for ADD, "" otherwise. amount: items,
     * turns or seconds.
     */
    public record Step(Kind kind, String item, int amount) {
        public static Step add(String item, int count) {
            return new Step(Kind.ADD, item, count);
        }

        public static Step stirClockwise(int turns) {
            return new Step(Kind.STIR_CLOCKWISE, "", turns);
        }

        public static Step stirAnticlockwise(int turns) {
            return new Step(Kind.STIR_ANTICLOCKWISE, "", turns);
        }

        public static Step simmer(int seconds) {
            return new Step(Kind.SIMMER, "", seconds);
        }

        /** The same action, whatever the amount. */
        public boolean sameAction(Step other) {
            return kind == other.kind && item.equals(other.item);
        }
    }

    /**
     * colors.get(i): the liquid once step i is done. tail: optional last steps, in order, each at most once, after
     * the brew is finished; tailColors likewise.
     */
    public record Recipe(String id, List<Step> steps, List<Integer> colors, List<Step> tail, List<Integer> tailColors,
                         int doses) {
    }

    public enum Status { ON_TRACK, FINISHED, RUINED }

    /**
     * recipe: the finished recipe (FINISHED); the first recipe the history still fits (ON_TRACK, null for an empty
     * history); null (RUINED).
     */
    public record Match(Status status, Recipe recipe) {
    }

    /**
     * index: the required step the last required run belongs to; done: that run's amount; complete: every required
     * step is done; lastTail: the index of the last tail step used, or -1.
     */
    private record Fit(int index, int done, boolean complete, int lastTail) {
    }

    public static final Recipe WIGGENWELD = new Recipe("wiggenweld",
            List.of(Step.add(HONEY_BOTTLE, 1), Step.add(GLOW_BERRIES, 2), Step.stirClockwise(3), Step.simmer(20)),
            List.of(0xD9A33A, 0xE8C85A, 0x8FD16A, 0x4CC38A),
            List.of(), List.of(), MAX_DOSES);

    public static final Recipe PEPPERUP = new Recipe("pepperup",
            List.of(Step.add(BLAZE_POWDER, 1), Step.add(MANDRAKE_ROOT, 1), Step.stirAnticlockwise(2), Step.simmer(15)),
            List.of(0xE0662B, 0xB5562E, 0xD9452B, 0xE8322A),
            List.of(), List.of(), MAX_DOSES);

    public static final Recipe POLYJUICE = new Recipe("polyjuice",
            List.of(Step.add(ACROMANTULA_VENOM, 1), Step.add(MANDRAKE_ROOT, 2), Step.add(GILLYWEED, 1),
                    Step.stirClockwise(4), Step.simmer(480), Step.add(POLYJUICE_SAMPLE, 1)),
            List.of(0x6E8B3D, 0x5C6E2E, 0x4E7A55, 0x55663A, 0x6B6B2A, 0x9C8F4A),
            List.of(Step.add(REDSTONE, 1), Step.add(GUNPOWDER, 1)),
            List.of(0xA0563E, 0x7D7468),
            MAX_DOSES);

    public static final List<Recipe> RECIPES = List.of(WIGGENWELD, PEPPERUP, POLYJUICE);

    private BrewRules() {
    }

    /** The history after one more action: merged into the last run when it is the same action. */
    public static List<Step> append(List<Step> history, Step action) {
        List<Step> next = new ArrayList<>(history);
        if (!next.isEmpty() && next.get(next.size() - 1).sameAction(action)) {
            Step last = next.remove(next.size() - 1);
            next.add(new Step(last.kind(), last.item(), last.amount() + action.amount()));
        } else {
            next.add(action);
        }
        return next;
    }

    public static Match match(List<Step> history) {
        if (history.isEmpty()) {
            return new Match(Status.ON_TRACK, null);
        }
        Recipe first = null;
        for (Recipe recipe : RECIPES) {
            Fit fit = fit(recipe, history);
            if (fit == null) {
                continue;
            }
            if (fit.complete()) {
                return new Match(Status.FINISHED, recipe);
            }
            if (first == null) {
                first = recipe;
            }
        }
        return first == null ? new Match(Status.RUINED, null) : new Match(Status.ON_TRACK, first);
    }

    /**
     * Seconds of simmering the brew still wants: the rest of the current simmer step, or the whole next step when
     * the current step is done and the next one is a simmer. 0 when time does not count now.
     */
    public static int simmerLeft(List<Step> history) {
        Match match = match(history);
        if (match.status() != Status.ON_TRACK || match.recipe() == null) {
            return 0;
        }
        List<Step> steps = match.recipe().steps();
        Fit fit = fit(match.recipe(), history);
        Step current = steps.get(fit.index());
        if (current.kind() == Kind.SIMMER && fit.done() < current.amount()) {
            return current.amount() - fit.done();
        }
        if (fit.done() == current.amount() && fit.index() + 1 < steps.size()
                && steps.get(fit.index() + 1).kind() == Kind.SIMMER) {
            return steps.get(fit.index() + 1).amount();
        }
        return 0;
    }

    /** Heat makes time count: the brew is at (or right before) a simmer step. */
    public static boolean wantsSimmer(List<Step> history) {
        return simmerLeft(history) > 0;
    }

    /** The overbrew clock runs: the brew is finished, or its last simmer step is done and it waits for the rest. */
    public static boolean overbrewClock(List<Step> history) {
        Match match = match(history);
        if (match.status() == Status.FINISHED) {
            return true;
        }
        if (match.status() == Status.RUINED || match.recipe() == null) {
            return false;
        }
        List<Step> steps = match.recipe().steps();
        int lastSimmer = -1;
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).kind() == Kind.SIMMER) {
                lastSimmer = i;
            }
        }
        if (lastSimmer < 0) {
            return false;
        }
        Fit fit = fit(match.recipe(), history);
        return fit.index() > lastSimmer
                || (fit.index() == lastSimmer && fit.done() == steps.get(lastSimmer).amount());
    }

    public static boolean overbrewed(int seconds) {
        return seconds > OVERBREW_SECONDS;
    }

    public static int overbrewLeft(int seconds) {
        return Math.max(0, OVERBREW_SECONDS - seconds);
    }

    /** The last OVERBREW_WARNING_SECONDS before the brew is lost. */
    public static boolean overbrewWarning(int seconds) {
        int left = overbrewLeft(seconds);
        return left > 0 && left <= OVERBREW_WARNING_SECONDS;
    }

    /**
     * The liquid colour (RGB). Inside a step it blends from the previous step's colour by progress; plain water is
     * WATER_COLOR and a ruined brew RUINED_COLOR.
     */
    public static int color(List<Step> history) {
        if (history.isEmpty()) {
            return WATER_COLOR;
        }
        Match match = match(history);
        if (match.status() == Status.RUINED) {
            return RUINED_COLOR;
        }
        Recipe recipe = match.recipe();
        Fit fit = fit(recipe, history);
        if (fit.lastTail() >= 0) {
            return recipe.tailColors().get(fit.lastTail());
        }
        int from = fit.index() == 0 ? WATER_COLOR : recipe.colors().get(fit.index() - 1);
        int to = recipe.colors().get(fit.index());
        return lerpColor(from, to, fit.done() / (float) recipe.steps().get(fit.index()).amount());
    }

    public static int lerpColor(int from, int to, float t) {
        float c = Math.max(0.0f, Math.min(1.0f, t));
        int r = channel(from >> 16, to >> 16, c);
        int g = channel(from >> 8, to >> 8, c);
        int b = channel(from, to, c);
        return (r << 16) | (g << 8) | b;
    }

    /** A finished brew used this tail ingredient (Polyjuice: redstone extends, gunpowder makes it splash). */
    public static boolean usedTail(List<Step> history, String item) {
        Match match = match(history);
        if (match.status() != Status.FINISHED) {
            return false;
        }
        for (int i = match.recipe().steps().size(); i < history.size(); i++) {
            if (history.get(i).item().equals(item)) {
                return true;
            }
        }
        return false;
    }

    /** The item appears as an Add step (or tail step) of some recipe. Other items are not brewing actions. */
    public static boolean isIngredient(String item) {
        for (Recipe recipe : RECIPES) {
            for (Step step : recipe.steps()) {
                if (step.kind() == Kind.ADD && step.item().equals(item)) {
                    return true;
                }
            }
            for (Step step : recipe.tail()) {
                if (step.kind() == Kind.ADD && step.item().equals(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** An empty Polyjuice sample is refused with a line and does not count as a step (or ruin anything). */
    public static boolean refusesEmptySample(String item, boolean sampleFilled) {
        return POLYJUICE_SAMPLE.equals(item) && !sampleFilled;
    }

    private static int channel(int from, int to, float c) {
        int a = from & 0xFF;
        int b = to & 0xFF;
        return Math.round(a + (b - a) * c);
    }

    /** How the history fits the recipe, or null when it does not. */
    private static Fit fit(Recipe recipe, List<Step> history) {
        if (history.isEmpty()) {
            return new Fit(-1, 0, false, -1);
        }
        List<Step> steps = recipe.steps();
        int required = Math.min(history.size(), steps.size());
        for (int i = 0; i < required; i++) {
            Step run = history.get(i);
            Step want = steps.get(i);
            if (!run.sameAction(want)) {
                return null;
            }
            boolean lastRun = i == history.size() - 1;
            if (lastRun ? run.amount() > want.amount() : run.amount() != want.amount()) {
                return null;
            }
        }
        int index = required - 1;
        int done = history.get(index).amount();
        boolean complete = history.size() > steps.size()
                || (history.size() == steps.size() && done == steps.get(index).amount());
        int lastTail = -1;
        int next = 0;
        for (int i = steps.size(); i < history.size(); i++) {
            Step run = history.get(i);
            int found = -1;
            for (int t = next; t < recipe.tail().size(); t++) {
                if (recipe.tail().get(t).sameAction(run)) {
                    found = t;
                    break;
                }
            }
            if (found < 0 || run.amount() != recipe.tail().get(found).amount()) {
                return null;
            }
            lastTail = found;
            next = found + 1;
        }
        return new Fit(index, done, complete, lastTail);
    }
}
