package com.hogwartsmagic.portkey;

/**
 * The plain rules a Portkey travels by, with no Minecraft in them so they can be tested on their own: who is taken
 * along, which dimensions it refuses, how long it rests between journeys, and where an Old Portkey throws you.
 *
 * <p>What the player sees: hold the boot for three seconds and everyone standing within two blocks of you (players and
 * pets that are not sitting) is hooked off their feet with you. A Portkey will not carry you into a Pensieve memory
 * or Diagon Alley, nor out of a memory. An Old Portkey goes somewhere two to four thousand blocks away, never into
 * the sea or a river.
 */
public final class PortkeyRules {

    /** Ticks of holding use before the hook (3 s). */
    public static final int CHARGE_TICKS = 60;
    /** Ticks a Portkey rests after a journey before it will go again (10 min). */
    public static final int COOLDOWN_TICKS = 12000;
    /** Journeys a crafted Portkey makes before it crumbles, like an Old one, after the last landing. */
    public static final int TRIPS = 3;
    /** Ticks after the hook before the server moves the travellers (they are inside the implosion until then). */
    public static final int TELEPORT_DELAY_TICKS = 5;
    /** The shortest tunnel, in ticks after the hook, even when the far end is ready at once (1.2 s). */
    public static final int TRANSIT_MIN_TICKS = 24;
    /** The client lets go of the tunnel this long after the hook whatever happens (12 s). */
    public static final int TRANSIT_MAX_TICKS = 240;
    /** How long the server waits for the far chunk to load before it drops the travellers back (10 s). */
    public static final int DESTINATION_WAIT_MAX_TICKS = 200;
    /** Ticks after the landing before a spent Old Portkey crumbles in the hand. */
    public static final int OLD_CRUMBLE_DELAY_TICKS = 40;
    /** Ticks of Slowness II after a landing: the stagger. */
    public static final int LANDING_SLOW_TICKS = 40;
    /** How far (blocks, horizontally, and the same up and down) a blocked bound spot looks for a safe one. */
    public static final int BOUND_SEARCH_RADIUS = 4;
    /** Everyone within this many blocks of the holder travels. */
    public static final double GROUP_RADIUS = 2.0;

    /** An Old Portkey throws you at least this far... */
    public static final double OLD_MIN_DISTANCE = 2000.0;
    /** ...and at most this far (blocks). */
    public static final double OLD_MAX_DISTANCE = 4000.0;
    /** Spots an Old Portkey weighs (biome checks only, no chunk loads) before it picks where to go. */
    public static final int OLD_BIOME_SAMPLES = 24;
    /** Chunks an Old Portkey loads at most, looking for a dry place to land. */
    public static final int OLD_CHUNK_TRIES = 4;

    public static final String MEMORY = "hogwartsmagic:memory";
    public static final String ALLEY = "hogwartsmagic:diagon_alley";

    private static final double TICKS_PER_SECOND = 20.0;

    private PortkeyRules() {
    }

    /** Why a journey is refused, or NONE. */
    public enum Refusal { NONE, INTO_MEMORY, INTO_ALLEY, OUT_OF_MEMORY }

    /**
     * One body near the holder at the hook. {@code distanceSq} from the holder's feet; {@code tamed} is a pet with an
     * owner; {@code heldInPlace} is Incarcerous or Levicorpus; {@code spectatorOrViewer} a spectator or someone
     * inside a Pensieve memory; {@code alreadyTravelling} someone another Portkey has already hooked.
     */
    public record Candidate(double distanceSq, boolean player, boolean tamed, boolean sitting, boolean heldInPlace,
                            boolean spectatorOrViewer, boolean alreadyTravelling) {
    }

    /** Whether this body is taken along. */
    public static boolean travels(Candidate c) {
        if (c.distanceSq() > GROUP_RADIUS * GROUP_RADIUS) {
            return false;
        }
        if (!c.player() && (!c.tamed() || c.sitting())) {
            return false;
        }
        return !c.heldInPlace() && !c.spectatorOrViewer() && !c.alreadyTravelling();
    }

    /** Whether a journey from {@code fromDim} to {@code toDim} (dimension ids) is refused, and why. */
    public static Refusal refusal(String fromDim, String toDim) {
        if (MEMORY.equals(toDim)) {
            return Refusal.INTO_MEMORY;
        }
        if (ALLEY.equals(toDim)) {
            return Refusal.INTO_ALLEY;
        }
        if (MEMORY.equals(fromDim)) {
            return Refusal.OUT_OF_MEMORY;
        }
        return Refusal.NONE;
    }

    /** A Portkey cannot be bound inside a memory or in Diagon Alley. */
    public static boolean canBindIn(String dim) {
        return !MEMORY.equals(dim) && !ALLEY.equals(dim);
    }

    /** An Old Portkey only works under an open sky: not the Nether, the End, a memory or the alley. */
    public static boolean oldWorksIn(String dim, boolean hasSkyLight, boolean hasCeiling) {
        return canBindIn(dim) && hasSkyLight && !hasCeiling;
    }

    /** Ticks until a Portkey that rests until {@code readyAt} may go again, 0 when ready. */
    public static long cooldownLeft(long now, long readyAt) {
        return Math.max(0L, readyAt - now);
    }

    /** Whole seconds to show for {@code ticksLeft} (rounded up, so 1 tick reads as 1 s). */
    public static int cooldownSeconds(long ticksLeft) {
        return (int) Math.ceil(Math.max(0L, ticksLeft) / TICKS_PER_SECOND);
    }

    /** Whether the travellers have waited long enough in the tunnel for a far end that never loaded. */
    public static boolean waitGivesUp(int waitedTicks) {
        return waitedTicks >= DESTINATION_WAIT_MAX_TICKS;
    }

    /**
     * An Old Portkey's throw from two 0..1 numbers: uniform over the area of the ring between the two distances (so
     * the far half is not starved), {@code angleUnit} round the circle. Returns {dx, dz}.
     */
    public static double[] oldOffset(double angleUnit, double distanceUnit) {
        double min2 = OLD_MIN_DISTANCE * OLD_MIN_DISTANCE;
        double max2 = OLD_MAX_DISTANCE * OLD_MAX_DISTANCE;
        double r = Math.sqrt(min2 + distanceUnit * (max2 - min2));
        double angle = Math.PI * 2.0 * angleUnit;
        return new double[]{r * Math.cos(angle), r * Math.sin(angle)};
    }

    /** Whether (x, z) lies inside the world border box at least {@code margin} blocks in from every edge. */
    public static boolean insideBorder(double x, double z, double minX, double minZ, double maxX, double maxZ,
                                       double margin) {
        return x >= minX + margin && x <= maxX - margin && z >= minZ + margin && z <= maxZ - margin;
    }

    /** The lang key naming a dimension in the tooltip ("the Overworld"). */
    public static String dimensionKey(String dimId) {
        int colon = dimId.indexOf(':');
        String ns = colon < 0 ? "minecraft" : dimId.substring(0, colon);
        String path = colon < 0 ? dimId : dimId.substring(colon + 1);
        return "portkey.hogwartsmagic.dim." + ns + "." + path.replace('/', '.');
    }

    /** A readable name for a dimension no lang file knows: its path with spaces. */
    public static String fallbackDimensionName(String dimId) {
        int colon = dimId.indexOf(':');
        String path = colon < 0 ? dimId : dimId.substring(colon + 1);
        return path.replace('_', ' ').replace('/', ' ');
    }
}
