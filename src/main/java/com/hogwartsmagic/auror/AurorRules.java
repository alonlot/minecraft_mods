package com.hogwartsmagic.auror;

import com.hogwartsmagic.spell.dark.DarkRules;

/**
 * The numbers behind the Aurors: who they hunt, how long a witnessed dark cast keeps a player wanted, how often a
 * hunting squad Apparates after a dark player, and how a Death Eater prisoner is held and freed. Plain constants and
 * functions, no Minecraft types, so they can be tested without a world.
 */
public final class AurorRules {
    private AurorRules() {
    }

    // ---- who is hunted ----------------------------------------------------------------------------------------

    /** Aurors attack a player on sight from this dark tier up (Tainted: a habit of dark casting, not one slip). */
    public static final int HUNTED_TIER = DarkRules.TAINTED;
    /** From this tier up a player in the Overworld may draw a hunting squad. */
    public static final int SQUAD_TIER = DarkRules.CORRUPTED;

    /** Player persistent-data key: game time until which every Auror treats the player as wanted. */
    public static final String WANTED_UNTIL_KEY = "hogwartsmagic:auror_wanted_until";
    /** A witnessed dark cast, or freeing a prisoner, keeps a player wanted for this long (5 minutes). */
    public static final long WANTED_TICKS = 6_000L;
    /** An Auror this close to a dark cast, with line of sight, witnesses it. */
    public static final double WITNESS_RADIUS = 32.0;

    public static boolean hunts(int tier, long now, long wantedUntil) {
        return tier >= HUNTED_TIER || now < wantedUntil;
    }

    // ---- hunting squads ---------------------------------------------------------------------------------------

    /** How often (ticks) each eligible player is considered for a squad. */
    public static final int SQUAD_CHECK_TICKS = 1_200;
    /** Chance per check at Corrupted, and at Lost. */
    public static final float SQUAD_CHANCE_CORRUPTED = 0.06f;
    public static final float SQUAD_CHANCE_LOST = 0.12f;
    /** Never two squads on the same player closer together than this (15 minutes). */
    public static final long SQUAD_COOLDOWN_TICKS = 18_000L;
    /** Player persistent-data key: game time of the last squad sent after them. */
    public static final String LAST_SQUAD_KEY = "hogwartsmagic:auror_last_squad";
    public static final int SQUAD_MIN_SIZE = 2;
    public static final int SQUAD_MAX_SIZE = 3;
    public static final double SQUAD_SPAWN_MIN_DISTANCE = 18.0;
    public static final double SQUAD_SPAWN_MAX_DISTANCE = 26.0;
    /** A squad member with nothing to fight for this long Disapparates (1 minute). */
    public static final int SQUAD_IDLE_LEAVE_TICKS = 1_200;
    /** And every squad member leaves after this long regardless (6 minutes). */
    public static final int SQUAD_MAX_LIFE_TICKS = 7_200;

    public static float squadChance(int tier) {
        if (tier >= DarkRules.LOST) {
            return SQUAD_CHANCE_LOST;
        }
        return tier >= SQUAD_TIER ? SQUAD_CHANCE_CORRUPTED : 0.0f;
    }

    public static boolean squadOffCooldown(long now, long lastSquad) {
        return lastSquad <= 0L || now - lastSquad >= SQUAD_COOLDOWN_TICKS;
    }

    public static int squadSize(int roll) {
        return Math.max(SQUAD_MIN_SIZE, Math.min(SQUAD_MAX_SIZE, SQUAD_MIN_SIZE + roll));
    }

    // ---- light flight: the silver-white streak, the Auror answer to Death Eater smoke ------------------------

    /** An Auror further than this from its target may fly at it. */
    public static final double FLIGHT_MIN_DISTANCE = 12.0;
    /** Blocks per tick in flight. */
    public static final double FLIGHT_SPEED = 1.15;
    /** Height it skims over the ground on the way. */
    public static final double FLIGHT_CRUISE_HEIGHT = 2.5;
    /** It lands this far short of a chased target, and once this close (horizontally) to its destination. */
    public static final double FLIGHT_LAND_SHORT = 5.0;
    public static final double FLIGHT_ARRIVE = 1.5;
    public static final int FLIGHT_MAX_TICKS = 50;
    public static final int FLIGHT_COOLDOWN_TICKS = 160;
    /** Chance a hit sends it streaking aside, and how far. */
    public static final float DODGE_CHANCE = 0.3f;
    public static final double DODGE_DISTANCE = 7.0;
    /** A squad appears this far out and this high, then streaks in. */
    public static final double SQUAD_ARRIVE_HEIGHT = 5.0;

    // ---- prisoners --------------------------------------------------------------------------------------------

    /** Entity tag the tower generator bakes onto a caged Death Eater. */
    public static final String PRISONER_TAG = "hogwartsmagic:auror_prisoner";
    /** Persistent-data key holding a prisoner's cell block, as a long. */
    public static final String CELL_KEY = "hogwartsmagic:auror_cell";
    /** A prisoner checks its cell this often. */
    public static final int PRISONER_CHECK_TICKS = 10;
    /** An open door this close (horizontally) to the cell block frees the prisoner. */
    public static final int CELL_DOOR_REACH = 2;
    /** A prisoner this far from its cell (bars broken, pushed out) is free too. */
    public static final double ESCAPED_DISTANCE = 3.0;
    /** The player nearest the freed prisoner within this range is its liberator. */
    public static final double LIBERATOR_RADIUS = 12.0;
    /** A freed Death Eater fights at its liberator's side this long, then Disapparates (5 minutes). */
    public static final long FREED_ESCORT_TICKS = 6_000L;
    /** Aurors this close to a breakout hear it and come. */
    public static final double ALARM_RADIUS = 48.0;

    public static boolean escaped(double horizontalDistanceSqr) {
        return horizontalDistanceSqr > ESCAPED_DISTANCE * ESCAPED_DISTANCE;
    }
}
