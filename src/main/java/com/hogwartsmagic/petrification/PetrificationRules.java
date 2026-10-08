package com.hogwartsmagic.petrification;

import com.hogwartsmagic.brewing.PotionRules;

import java.util.List;

/**
 * Canon petrification, free of Minecraft so it is unit-tested: who the full gaze turns to stone, which damage still
 * reaches stone, the optional time limit, the Mandrake ward, which statues can come back and what a statue forgets of
 * its mob. Petrification and PetrificationEvents apply these rules in the world.
 */
public final class PetrificationRules {
    /** What the Basilisk's full gaze does to a victim. */
    public enum GazeOutcome {
        /** A survival or adventure player: stone, with the choice to wait or give in. */
        PETRIFY,
        /**
         * Everyone else, bosses included: the old lethal rule (a mob leaves a statue). For creative and spectator
         * players this path is harmless: the magic damage does not bypass invulnerability.
         */
        KILL
    }

    /** Under PlayerPersisted/hogwartsmagic: the player is stone. */
    public static final String KEY_PETRIFIED = "Petrified";
    /** Under PlayerPersisted/hogwartsmagic: the overworld game time the stone began. */
    public static final String KEY_SINCE = "PetrifiedSince";
    /** Entity persistent data: the game time until which the gaze cannot build (a Mandrake Draught drink). */
    public static final String WARD_KEY = "hogwartsmagic:gaze_ward_until";
    public static final String DAMAGE_TYPE = "hogwartsmagic:petrification";
    public static final String VOID_DAMAGE = "minecraft:out_of_world";
    public static final String KILL_DAMAGE = "minecraft:generic_kill";
    public static final int DEFAULT_MAX_SECONDS = 0;
    public static final int MAX_MAX_SECONDS = 86400;
    /** Mobs this close that still target a stone player lose the target. */
    public static final int TARGET_CLEAR_RADIUS = 32;
    public static final int CHECK_INTERVAL_TICKS = 20;
    /**
     * What a statue drops from the mob's save: the death itself and anything that would duplicate or misplace it.
     * active_effects is kept on purpose: effects add permanent attribute modifiers saved in attributes, so the revive
     * loads the effects and calls removeAllEffects() to let vanilla take those modifiers off. attributes and Brain are
     * kept.
     */
    public static final List<String> STRIPPED_MOB_KEYS = List.of("Health", "DeathTime", "HurtTime",
            "HurtByTimestamp", "Fire", "FallDistance", "Motion", "Passengers", "leash", "SleepingX", "SleepingY",
            "SleepingZ", "TicksFrozen", "HasVisualFire");
    /**
     * Brain memories a statue forgets: vanilla released these POI tickets when the villager died, so a revived
     * villager must claim them again instead of holding a ticket it no longer owns.
     */
    public static final List<String> RELEASED_MEMORIES = List.of("minecraft:job_site", "minecraft:potential_job_site",
            "minecraft:home", "minecraft:meeting_point");

    private PetrificationRules() {
    }

    public static GazeOutcome gazeOutcome(boolean player, boolean creativeOrSpectator) {
        return player && !creativeOrSpectator ? GazeOutcome.PETRIFY : GazeOutcome.KILL;
    }

    /** Stone takes no damage except the void, /kill and giving in. */
    public static boolean damagePasses(String damageTypeId) {
        return VOID_DAMAGE.equals(damageTypeId) || KILL_DAMAGE.equals(damageTypeId) || DAMAGE_TYPE.equals(damageTypeId);
    }

    /** The configured limit (0 or less: none) has run out. Game time, so time logged out counts. */
    public static boolean limitReached(long since, long now, int maxSeconds) {
        return maxSeconds > 0 && now - since >= maxSeconds * 20L;
    }

    public static long ticksPetrified(long since, long now) {
        return Math.max(0L, now - since);
    }

    /** A statue comes back only as a known, living, non-boss type. */
    public static boolean canRevive(boolean typeKnown, boolean living, boolean boss) {
        return typeKnown && living && !boss;
    }

    /** The GiveInPacket is acted on only for a player who is stone and alive. */
    public static boolean giveInAccepted(boolean petrified, boolean alive) {
        return petrified && alive;
    }

    /**
     * After the give-in damage: the player is still stone and alive (another mod cancelled the hurt). PetrifiedSince
     * is then re-armed to now, so the time limit starts over instead of sending the time-up line every check.
     */
    public static boolean giveInFailed(boolean stillPetrified, boolean alive) {
        return stillPetrified && alive;
    }

    public static long wardUntil(long now) {
        return now + PotionRules.MANDRAKE_GAZE_WARD_TICKS;
    }

    public static boolean warded(long wardUntil, long now) {
        return now < wardUntil;
    }

    /** Once every CHECK_INTERVAL_TICKS per entity, phased by its id. */
    public static boolean dueCheck(long gameTime, int entityId) {
        return Math.floorMod(gameTime + entityId, (long) CHECK_INTERVAL_TICKS) == 0L;
    }
}
