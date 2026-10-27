package com.hogwartsmagic.spell.dark;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Dark Magic rules, free of Minecraft so they are unit-tested: which spells are dark and how much Darkness a paid
 * cast adds, the tiers, the clamp and the two-decimal rounding, losses that stop at a floor, the slow decay, who pays
 * for a forced cast, and the tier keys and colours the client draws. Every cost and reward reads the tier, never the
 * raw number. The deed and redemption numbers are here for Phase 3.
 *
 * Darkness is a float 0..100 kept to two decimals: every stored or compared value goes through sanitize, so a table
 * value added N times lands exactly on a tier bound. Spells are keyed by their id string; no SpellType import.
 */
public final class DarkRules {
    public static final float MIN_DARKNESS = 0.0f;
    public static final float MAX_DARKNESS = 100.0f;

    public static final int CLEAN = 0;
    public static final int SHADOWED = 1;
    public static final int TAINTED = 2;
    public static final int CORRUPTED = 3;
    public static final int LOST = 4;

    /** Inclusive lower bound of each tier, indexed by tier: 20.00 is Shadowed, 19.99 is Clean. */
    private static final float[] TIER_LOWER_BOUNDS = {0.0f, 20.0f, 40.0f, 60.0f, 80.0f};
    private static final String[] TIER_NAMES = {"clean", "shadowed", "tainted", "corrupted", "lost"};
    /** ARGB, white to dark green, indexed by tier. */
    private static final int[] TIER_COLORS = {0xFFFFFFFF, 0xFFCDE8CD, 0xFF98CC98, 0xFF5CAE5C, 0xFF2E8B3A};
    private static final String TIER_KEY_PREFIX = "gui.hogwartsmagic.dark.tier.";

    /** The mana bar fill below Corrupted (the colour it always had). */
    public static final int MANA_BAR_COLOR = 0xFF00AAFF;
    /** The mana bar fill at Corrupted and Lost: a dark green-black. */
    public static final int MANA_BAR_DARK_COLOR = 0xFF1B3A22;

    /** Decay waits until no dark spell has been cast for this long (10 minutes of game time). */
    public static final long QUIET_TICKS = 12_000L;
    /** Points lost per full decay period. */
    public static final float DECAY_STEP = 1.0f;
    /** Online quiet ticks per point lost (30 minutes): 100 to 0 in 50 hours. The config can change it. */
    public static final int DEFAULT_DECAY_TICKS = 36_000;
    /** The decay check runs once in this many player ticks. */
    public static final int DECAY_CHECK_INTERVAL_TICKS = 200;

    /** No floor: Darkness can fall to 0. */
    public static final float NO_FLOOR = 0.0f;
    /** Phase 3: Darkness cannot decay below this while the player's Horcrux exists. */
    public static final float HORCRUX_FLOOR = 60.0f;
    /** Phase 3: added when a Horcrux is made. */
    public static final float HORCRUX_BONUS = 30.0f;
    /** Phase 3: added by an Avada kill of an innocent. */
    public static final float AVADA_INNOCENT_BONUS = 3.0f;
    /** Phase 3: removed by one Heal of another hurt living thing. */
    public static final float REDEMPTION_AMOUNT = 1.0f;
    /** Phase 3: at most one redemption per this many ticks of play. The config can change it. */
    public static final int DEFAULT_REDEMPTION_TICKS = 36_000;

    /** Who gains for a paid dark cast. */
    public enum Payer {
        BODY,
        PUPPETEER,
        NOBODY
    }

    /** The result of one decay check: the new Darkness and the online quiet ticks carried to the next check. */
    public record Decay(float darkness, int progressTicks) {
    }

    private static final Map<String, Float> GAIN;
    /** Grey charms: they darken the soul a little but are not dark spells (no mana discount, no deeds). */
    private static final Map<String, Float> GREY_GAIN = Map.of("obliviate", 0.2f, "confundo", 0.1f);

    static {
        Map<String, Float> gain = new LinkedHashMap<>();
        gain.put("avada_kedavra", 6.0f);
        gain.put("fiendfyre", 2.5f);
        gain.put("morsmordre", 2.5f);
        gain.put("imperio", 1.5f);
        gain.put("crucio", 1.2f);
        gain.put("tenebris_flight", 0.6f);
        gain.put("sectumsempra", 0.4f);
        gain.put("dolohov_curse", 0.4f);
        gain.put("serpensortia", 0.3f);
        GAIN = Collections.unmodifiableMap(gain);
    }

    private DarkRules() {
    }

    /** The ids of the nine dark spells. */
    public static Set<String> darkSpellIds() {
        return GAIN.keySet();
    }

    public static boolean isDark(String spellId) {
        return spellId != null && GAIN.containsKey(spellId);
    }

    /** True for the grey charms, Obliviate and Confundo: a little Darkness per cast, yet not dark spells. */
    public static boolean isGrey(String spellId) {
        return spellId != null && GREY_GAIN.containsKey(spellId);
    }

    /** Darkness per paid cast before the multiplier; 0 for a spell that is neither dark nor grey. */
    public static float baseGain(String spellId) {
        Float gain = spellId == null ? null : GAIN.get(spellId);
        if (gain == null && spellId != null) {
            gain = GREY_GAIN.get(spellId);
        }
        return gain == null ? 0.0f : gain;
    }

    /** Slytherins darken more slowly: three quarters of the gain. Any other house, or none, gains in full. */
    public static double houseGainMultiplier(@Nullable String house) {
        return "slytherin".equals(house) ? 0.75 : 1.0;
    }

    /** Hufflepuffs find their way back faster: half again as much redemption and remorse. */
    public static double houseRedemptionMultiplier(@Nullable String house) {
        return "hufflepuff".equals(house) ? 1.5 : 1.0;
    }

    /** The lower-case name of a house enum constant, or null for no house. */
    public static @Nullable String houseKey(@Nullable Enum<?> house) {
        return house == null ? null : house.name().toLowerCase(Locale.ROOT);
    }

    /** Darkness per paid cast after the server multiplier, rounded to two decimals. 0 for a bad multiplier. */
    public static float gain(String spellId, double multiplier) {
        if (!(multiplier > 0.0) || Double.isInfinite(multiplier)) {
            return 0.0f;
        }
        double raw = baseGain(spellId) * multiplier;
        return (float) (Math.round(raw * 100.0) / 100.0);
    }

    /** NaN and infinities become 0, then clamp to 0..100, then round to two decimals. */
    public static float sanitize(float darkness) {
        if (Float.isNaN(darkness) || Float.isInfinite(darkness)) {
            return MIN_DARKNESS;
        }
        float clamped = Math.max(MIN_DARKNESS, Math.min(MAX_DARKNESS, darkness));
        return Math.round(clamped * 100.0) / 100.0f;
    }

    private static int clampTier(int tier) {
        return Math.max(CLEAN, Math.min(LOST, tier));
    }

    public static int tier(float darkness) {
        float value = sanitize(darkness);
        for (int t = LOST; t > CLEAN; t--) {
            if (value >= TIER_LOWER_BOUNDS[t]) {
                return t;
            }
        }
        return CLEAN;
    }

    public static float tierLowerBound(int tier) {
        return TIER_LOWER_BOUNDS[clampTier(tier)];
    }

    /** Adds a positive amount and stops at 100. Anything else leaves the value as it is. */
    public static float afterGain(float darkness, float amount) {
        float value = sanitize(darkness);
        if (!(amount > 0.0f) || Float.isInfinite(amount)) {
            return value;
        }
        return sanitize(value + amount);
    }

    /** Removes a positive amount, never below the floor; a value already at or below the floor is left alone. */
    public static float afterLoss(float darkness, float amount, float floor) {
        float value = sanitize(darkness);
        float bottom = sanitize(floor);
        if (!(amount > 0.0f) || Float.isInfinite(amount) || value <= bottom) {
            return value;
        }
        return sanitize(Math.max(bottom, value - amount));
    }

    /** A signed change: a gain above 0, a loss through the floor below 0. */
    public static float add(float darkness, float delta, float floor) {
        if (delta > 0.0f) {
            return afterGain(darkness, delta);
        }
        if (delta < 0.0f) {
            return afterLoss(darkness, -delta, floor);
        }
        return sanitize(darkness);
    }

    /** True once 10 minutes of game time have passed since the last dark cast (or the clock is behind it). */
    public static boolean quiet(long now, long lastDarkCast) {
        return now < lastDarkCast || now - lastDarkCast >= QUIET_TICKS;
    }

    /**
     * One decay check. Not quiet, or at or below the floor: nothing falls and the carried ticks reset. Otherwise the
     * elapsed ticks are added to the carried ticks (first clamped below one period, so a lowered config never dumps
     * points), each full period removes DECAY_STEP through the floor, and the rest is carried.
     */
    public static Decay decay(float darkness, int progressTicks, int elapsedTicks, boolean quiet, float floor,
                              int decayTicks) {
        float value = sanitize(darkness);
        float bottom = sanitize(floor);
        if (!quiet || value <= bottom) {
            return new Decay(value, 0);
        }
        int period = Math.max(1, decayTicks);
        long progress = Math.min(Math.max(0L, progressTicks), period - 1L) + Math.max(0L, elapsedTicks);
        long steps = progress / period;
        int rest = (int) (progress % period);
        if (steps == 0L) {
            return new Decay(value, rest);
        }
        float after = afterLoss(value, steps * DECAY_STEP, bottom);
        return new Decay(after, after <= bottom ? 0 : rest);
    }

    /** True on one tick in every DECAY_CHECK_INTERVAL_TICKS, phased by a per-player key (the entity id). */
    public static boolean dueToDecayCheck(long tickCount, long phaseKey) {
        return Math.floorMod(tickCount + phaseKey, (long) DECAY_CHECK_INTERVAL_TICKS) == 0L;
    }

    /** A puppet cast (a puppeteer recorded for it) charges the puppeteer; any other forced cast charges nobody. */
    public static Payer payer(boolean puppetCastRunning, boolean bodyIsForced) {
        if (puppetCastRunning) {
            return Payer.PUPPETEER;
        }
        return bodyIsForced ? Payer.NOBODY : Payer.BODY;
    }

    /** Creative and spectator players gain nothing. */
    public static boolean earns(boolean creative, boolean spectator) {
        return !creative && !spectator;
    }

    /** What the world and the client see: the stored value, or 0 with Dark Magic off. */
    public static float effective(boolean enabled, float stored) {
        return enabled ? sanitize(stored) : MIN_DARKNESS;
    }

    public static int effectiveTier(boolean enabled, float stored) {
        return enabled ? tier(stored) : CLEAN;
    }

    /** 1 when the soul darkened, -1 when it lightened, 0 when the tier is the same. */
    public static int tierChange(int oldTier, int newTier) {
        return Integer.compare(newTier, oldTier);
    }

    public static String tierKey(int tier) {
        return TIER_KEY_PREFIX + TIER_NAMES[clampTier(tier)];
    }

    public static int tierColor(int tier) {
        return TIER_COLORS[clampTier(tier)];
    }

    public static int manaBarColor(int tier) {
        return tier >= CORRUPTED ? MANA_BAR_DARK_COLOR : MANA_BAR_COLOR;
    }
}
