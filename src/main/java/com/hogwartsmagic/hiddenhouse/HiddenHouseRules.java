package com.hogwartsmagic.hiddenhouse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The plain logic behind a hidden wizard house: which blocks Revelio reveals when it is cast, how
 * long each waits for the shimmer to reach it, and which blocks the generator may hide at all.
 *
 * <p>What the player sees: cast Revelio within ten blocks of a mossy ruin and the whole cottage
 * shimmers into being, wave by wave outward from the caster, not only the part within ten blocks.
 * Nothing here touches Minecraft, so it is unit tested.
 */
public final class HiddenHouseRules {
    private HiddenHouseRules() {
    }

    /** How near the caster a hidden block must be for Revelio to find the house at all. */
    public static final int REVEAL_RADIUS = 10;
    /** The largest gap (Chebyshev, in blocks) between two blocks that still belong to one house. */
    public static final int LINK = 3;
    /** How far from the first block found the flood may run: no house is wider than this. */
    public static final int MAX_HOUSE_SPAN = 24;
    /** A hard cap on one reveal, so a freak build of hidden blocks cannot stall the server. */
    public static final int MAX_HOUSE_BLOCKS = 2048;
    /** How fast the shimmer runs outward from the caster, blocks a tick. */
    public static final float WAVE_SPEED = 0.8F;
    /** The longest any block waits for the shimmer, in ticks. */
    public static final int MAX_DELAY = 40;
    /** When someone stands in a wall about to appear, how long before it tries again. */
    public static final int OCCUPIED_RETRY_TICKS = 10;
    /** How many times it tries before giving up and appearing anyway. */
    public static final int OCCUPIED_MAX_RETRIES = 12;
    /** How hard the jinxed chest throws whoever opens it. */
    public static final double JINX_PUSH = 1.6;
    /** How long the jinx blinds, in ticks. */
    public static final int JINX_BLIND_TICKS = 120;

    /** One block position; plain ints so the rules need no Minecraft classes. */
    public record Cell(int x, int y, int z) {
    }

    /** How far, in blocks either way, the server looks for hidden blocks to flood from a cast. */
    public static int searchHalfWidth() {
        return REVEAL_RADIUS + MAX_HOUSE_SPAN;
    }

    /**
     * The blocks of the house a cast at ({@code cx, cy, cz}) reveals: every candidate within
     * {@link #REVEAL_RADIUS} of the cast, then everything linked to those by gaps of at most
     * {@link #LINK}, no further than {@link #MAX_HOUSE_SPAN} from the nearest of them, and no more
     * than {@link #MAX_HOUSE_BLOCKS} in all. Breadth-first, so the cap trims the far edge.
     */
    public static List<Cell> house(Set<Cell> candidates, double cx, double cy, double cz) {
        List<Cell> seeds = new ArrayList<>();
        double r2 = (double) REVEAL_RADIUS * REVEAL_RADIUS;
        for (Cell c : candidates) {
            if (distSq(c, cx, cy, cz) <= r2) {
                seeds.add(c);
            }
        }
        if (seeds.isEmpty()) {
            return List.of();
        }
        seeds.sort(Comparator.comparingDouble((Cell c) -> distSq(c, cx, cy, cz))
                .thenComparingInt(Cell::x).thenComparingInt(Cell::y).thenComparingInt(Cell::z));
        Cell origin = seeds.get(0);

        List<Cell> out = new ArrayList<>();
        Set<Cell> seen = new HashSet<>();
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        for (Cell s : seeds) {
            if (seen.add(s)) {
                queue.add(s);
            }
        }
        while (!queue.isEmpty() && out.size() < MAX_HOUSE_BLOCKS) {
            Cell c = queue.poll();
            out.add(c);
            for (int dx = -LINK; dx <= LINK; dx++) {
                for (int dy = -LINK; dy <= LINK; dy++) {
                    for (int dz = -LINK; dz <= LINK; dz++) {
                        Cell n = new Cell(c.x() + dx, c.y() + dy, c.z() + dz);
                        if (!candidates.contains(n) || seen.contains(n) || span(n, origin) > MAX_HOUSE_SPAN) {
                            continue;
                        }
                        seen.add(n);
                        queue.add(n);
                    }
                }
            }
        }
        return out;
    }

    /** Ticks a block {@code distance} blocks from the cast waits for the shimmer, capped at {@link #MAX_DELAY}. */
    public static int revealDelay(double distance) {
        return Math.min((int) Math.ceil(distance / WAVE_SPEED), MAX_DELAY);
    }

    /**
     * Whether the generator may hide this block under a glamour. The reveal places blocks without
     * shape updates (so a bed half or a lantern never pops while its neighbour is still hidden),
     * which rules out the blocks whose shape comes from their neighbours: fences, panes and walls.
     * Liquids, fire, wires and the hidden-block family itself are never glamoured either.
     */
    public static boolean isGlamourTarget(String blockId) {
        int colon = blockId.indexOf(':');
        if (colon < 0) {
            return false;
        }
        String ns = blockId.substring(0, colon);
        String path = blockId.substring(colon + 1);
        if (path.endsWith("_fence") || path.endsWith("_pane") || path.endsWith("_wall")) {
            return false;
        }
        if (ns.equals("minecraft")) {
            return !MINECRAFT_DENY.contains(path);
        }
        if (ns.equals("hogwartsmagic")) {
            return !MOD_DENY.contains(path);
        }
        return true;
    }

    private static final Set<String> MINECRAFT_DENY = Set.of("air", "cave_air", "void_air", "water", "lava",
            "bubble_column", "fire", "soul_fire", "redstone_wire", "tripwire", "iron_bars", "chorus_plant",
            "spawner", "trial_spawner", "piston_head", "moving_piston", "nether_portal", "end_portal",
            "end_gateway");
    private static final Set<String> MOD_DENY = Set.of("glamoured_block", "phantom_ruin", "jinx_ward",
            "cursed_glyph", "concealed_sandstone", "concealed_rock");

    private static double distSq(Cell c, double cx, double cy, double cz) {
        double dx = c.x() + 0.5 - cx;
        double dy = c.y() + 0.5 - cy;
        double dz = c.z() + 0.5 - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static int span(Cell a, Cell b) {
        return Math.max(Math.abs(a.x() - b.x()), Math.max(Math.abs(a.y() - b.y()), Math.abs(a.z() - b.z())));
    }
}
