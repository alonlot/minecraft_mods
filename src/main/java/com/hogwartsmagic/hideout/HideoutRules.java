package com.hogwartsmagic.hideout;

import java.util.List;

/**
 * The numbers behind a dark wizard hideout: how tough its named leader is, how far he strays from the
 * altar before he turns back, how high the freed soul rises and how long the reward takes to appear.
 * Plain constants and functions, no Minecraft types, so they can be tested without a world.
 */
public final class HideoutRules {
    /** Entity tag the generator bakes onto the leader wizard. */
    public static final String LEADER_TAG = "hogwartsmagic:hideout_leader";
    /** Persistent-data key holding the leader's home block, as a long. */
    public static final String HOME_KEY = "hogwartsmagic:hideout_home";
    /** Persistent-data key set once the leader has been armed, so a chunk reload does not re-arm him. */
    public static final String ARMED_KEY = "hogwartsmagic:hideout_armed";

    public static final double LEADER_MAX_HEALTH = 80.0;
    public static final double LEADER_ARMOR = 8.0;
    public static final double LEADER_KNOCKBACK_RESISTANCE = 0.5;
    public static final double LEADER_FOLLOW_RANGE = 24.0;

    /** He starts walking home when further than this from the altar. */
    public static final double LEASH_RADIUS = 9.0;
    /** And stops once this close. */
    public static final double HOME_RADIUS = 2.5;
    /** How often a returning leader re-plans his path. */
    public static final int LEASH_REPATH_TICKS = 20;

    /** How far above the cup the freed soul climbs (through the ceiling) before it vanishes. */
    public static final int SOUL_RISE_BLOCKS = 12;
    /** Ticks between the cup breaking and the reward appearing. */
    public static final int REWARD_DELAY_TICKS = 30;
    /** Players this close see the "freed" message. */
    public static final int FREED_MESSAGE_RADIUS = 16;

    /** Loot table path (under the mod namespace) of the reward chest. */
    public static final String VAULT_LOOT = "chests/dark_hideout_vault";

    public static final List<String> LEADER_NAMES = List.of(
            "Corvin Mortlake", "Aldous Grimsditch", "Morwenna Vex",
            "Silas Blackwood", "Theron Ashcombe", "Hester Grimwald");

    private HideoutRules() {
    }

    /** A stable pick of name for a seed (the leader's home block). */
    public static String leaderName(long seed) {
        int mixed = Long.hashCode(seed * 0x9E3779B97F4A7C15L);
        return LEADER_NAMES.get(Math.floorMod(mixed, LEADER_NAMES.size()));
    }

    /** Leash hysteresis: leave the post beyond {@link #LEASH_RADIUS}, keep walking until within {@link #HOME_RADIUS}. */
    public static boolean shouldHeadHome(double distSqFromHome, boolean alreadyReturning) {
        double r = alreadyReturning ? HOME_RADIUS : LEASH_RADIUS;
        return distSqFromHome > r * r;
    }
}
