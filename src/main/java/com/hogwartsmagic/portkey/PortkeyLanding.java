package com.hogwartsmagic.portkey;

import com.hogwartsmagic.caveinimicum.CaveInimicumManager;
import com.hogwartsmagic.event.ModEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Where a Portkey journey ends, found without ever stalling the server: the far chunk is asked for through a
 * self-expiring ticket and polled each tick with {@code getChunkNow}, never loaded with a blocking {@code getChunk}.
 * A bound spot that has been built over, flooded or warded lands the group on the nearest safe spot within four
 * blocks, or the journey fizzles. An Old Portkey weighs a couple of dozen far spots by their biome (no ocean, no
 * river), loads at most four chunks and lands on dry ground under the sky.
 */
public final class PortkeyLanding {

    /**
     * The ticket that pulls a far chunk in. Its lifespan (20 s) means a ticket this class ever forgets to release
     * still expires on its own.
     */
    private static final TicketType<ChunkPos> TICKET = TicketType.create("hogwartsmagic_portkey",
            Comparator.comparingLong(ChunkPos::toLong), 400);
    /** Region distance of the ticket: the chunk itself ticks, its neighbours are fully loaded. */
    private static final int TICKET_DISTANCE = 1;
    /** An Old Portkey keeps away from the world border by this much. */
    private static final double BORDER_MARGIN = 32.0;
    /** The width and height of a body that has to fit where it lands (a player's). */
    private static final double BODY_HALF_WIDTH = 0.3;
    private static final double BODY_HEIGHT = 1.8;
    /** A floor block shorter than this (a slab, a carpet) is stood on, not in. */
    private static final double LOW_FLOOR = 0.5;
    /** The columns tried round an Old Portkey's chosen column, inside the same chunk. */
    private static final int[][] SPIRAL = {{0, 0}, {4, 0}, {0, 4}, {-4, 0}, {0, -4}, {4, 4}, {-4, -4}, {4, -4}, {-4, 4}};

    private PortkeyLanding() {
    }

    /** The feet height a body standing in {@code feet} would have, or NaN when there is nothing to stand on. */
    private static double floorY(ServerLevel level, BlockPos feet) {
        BlockState at = level.getBlockState(feet);
        VoxelShape shape = at.getCollisionShape(level, feet);
        if (!shape.isEmpty()) {
            double top = shape.max(Direction.Axis.Y);
            return top <= LOW_FLOOR + 1.0e-6 ? feet.getY() + top : Double.NaN;
        }
        BlockPos below = feet.below();
        BlockState under = level.getBlockState(below);
        VoxelShape floor = under.getCollisionShape(level, below);
        if (floor.isEmpty()) {
            return Double.NaN;
        }
        double top = floor.max(Direction.Axis.Y);
        return top >= LOW_FLOOR && top <= 1.0 + 1.0e-6 ? below.getY() + top : Double.NaN;
    }

    /** Where a body standing in {@code feet} would land, or null when it is unsafe there. */
    public static Vec3 landingAt(ServerLevel level, BlockPos feet) {
        if (level.isOutsideBuildHeight(feet) || level.isOutsideBuildHeight(feet.above())) {
            return null;
        }
        double y = floorY(level, feet);
        if (Double.isNaN(y)) {
            return null;
        }
        BlockPos below = feet.below();
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()
                || !level.getFluidState(below).isEmpty()) {
            return null; // never into water or lava
        }
        BlockState at = level.getBlockState(feet);
        BlockState under = level.getBlockState(below);
        if (at.is(BlockTags.FIRE) || under.is(Blocks.MAGMA_BLOCK) || under.is(BlockTags.CAMPFIRES)
                || at.is(Blocks.POWDER_SNOW) || at.is(Blocks.SWEET_BERRY_BUSH) || at.is(Blocks.CACTUS)) {
            return null;
        }
        Vec3 spot = new Vec3(feet.getX() + 0.5, y, feet.getZ() + 0.5);
        AABB body = new AABB(spot.x - BODY_HALF_WIDTH, spot.y + 1.0e-3, spot.z - BODY_HALF_WIDTH,
                spot.x + BODY_HALF_WIDTH, spot.y + BODY_HEIGHT, spot.z + BODY_HALF_WIDTH);
        if (!level.noCollision(body)) {
            return null; // never suffocate
        }
        if (CaveInimicumManager.get(level).isWardedAt(feet)) {
            return null;
        }
        return spot;
    }

    /** Whether a body can stand in {@code feet}: solid footing, room for the head, dry, not warded. */
    public static boolean isSafeFeet(ServerLevel level, BlockPos feet) {
        return landingAt(level, feet) != null;
    }

    /**
     * The bound spot itself when it is safe, else the nearest safe spot within {@code BOUND_SEARCH_RADIUS} blocks
     * (sideways, and as far up and down), else null. The chunks round the spot must already be loaded.
     */
    public static Vec3 nearBound(ServerLevel level, BlockPos bound, float yaw) {
        Vec3 exact = landingAt(level, bound);
        if (exact != null) {
            return exact;
        }
        int r = PortkeyRules.BOUND_SEARCH_RADIUS;
        List<BlockPos> around = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        around.add(bound.offset(dx, dy, dz));
                    }
                }
            }
        }
        // Nearest first; on a tie, the same level before up or down (land beside the spot, not on its roof).
        around.sort(Comparator.<BlockPos>comparingInt(p -> (int) p.distSqr(bound))
                .thenComparingInt(p -> Math.abs(p.getY() - bound.getY())));
        for (BlockPos p : around) {
            Vec3 spot = landingAt(level, p);
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    private static boolean loaded(ServerLevel level, int chunkX, int chunkZ) {
        return level.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
    }

    /** A request for a landing that may take a few ticks to load. Release it when done, whatever the outcome. */
    public static final class Request {

        public enum Status { PENDING, READY, FAILED }

        private final ServerLevel level;
        private final boolean old;
        private final BlockPos bound;
        private final float yaw;
        private final List<BlockPos> columns;
        private final Set<ChunkPos> ticketed = new LinkedHashSet<>();
        private int next;
        private int tries;
        private BlockPos current;
        private Status status = Status.PENDING;
        private Vec3 landing;
        private boolean warded;

        private Request(ServerLevel level, boolean old, BlockPos bound, float yaw, List<BlockPos> columns) {
            this.level = level;
            this.old = old;
            this.bound = bound;
            this.yaw = yaw;
            this.columns = columns;
        }

        /** A Portkey's own bound spot in {@code target}. */
        public static Request bound(ServerLevel target, BlockPos pos, float yaw) {
            Request request = new Request(target, false, pos.immutable(), yaw, List.of());
            request.ticket(new ChunkPos(pos));
            return request;
        }

        /** An Old Portkey's throw from {@code from} in {@code level}: FAILED at once when nowhere fits. */
        public static Request old(ServerLevel level, BlockPos from, RandomSource random) {
            List<BlockPos> columns = new ArrayList<>();
            WorldBorder border = level.getWorldBorder();
            var source = level.getChunkSource().getGenerator().getBiomeSource();
            var sampler = level.getChunkSource().randomState().sampler();
            int quartY = QuartPos.fromBlock(Math.max(level.getSeaLevel(), 64));
            for (int i = 0; i < PortkeyRules.OLD_BIOME_SAMPLES; i++) {
                double[] offset = PortkeyRules.oldOffset(random.nextDouble(), random.nextDouble());
                int x = (int) Math.floor(from.getX() + offset[0]);
                int z = (int) Math.floor(from.getZ() + offset[1]);
                if (!PortkeyRules.insideBorder(x, z, border.getMinX(), border.getMinZ(), border.getMaxX(),
                        border.getMaxZ(), BORDER_MARGIN)) {
                    continue;
                }
                Holder<Biome> biome = source.getNoiseBiome(QuartPos.fromBlock(x), quartY, QuartPos.fromBlock(z),
                        sampler);
                if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER)
                        || biome.is(BiomeTags.IS_BEACH)) {
                    continue;
                }
                columns.add(new BlockPos(x, 0, z));
            }
            Request request = new Request(level, true, from, 0.0f, columns);
            if (columns.isEmpty()) {
                request.status = Status.FAILED;
            } else {
                request.advance();
            }
            return request;
        }

        private void ticket(ChunkPos pos) {
            if (ticketed.add(pos)) {
                level.getChunkSource().addRegionTicket(TICKET, pos, TICKET_DISTANCE, pos);
            }
        }

        private void unticket(ChunkPos pos) {
            if (ticketed.remove(pos)) {
                level.getChunkSource().removeRegionTicket(TICKET, pos, TICKET_DISTANCE, pos);
            }
        }

        /** The Old Portkey moves on to its next candidate column, or gives up. */
        private void advance() {
            if (current != null) {
                unticket(new ChunkPos(current));
            }
            if (next >= columns.size() || tries >= PortkeyRules.OLD_CHUNK_TRIES) {
                current = null;
                status = Status.FAILED;
                return;
            }
            current = columns.get(next++);
            tries++;
            ticket(new ChunkPos(current));
        }

        /** Look again: loads what it needs without blocking, and settles once it can. */
        public Status poll() {
            if (status != Status.PENDING) {
                return status;
            }
            if (!old) {
                int r = PortkeyRules.BOUND_SEARCH_RADIUS;
                int minX = (bound.getX() - r) >> 4;
                int maxX = (bound.getX() + r) >> 4;
                int minZ = (bound.getZ() - r) >> 4;
                int maxZ = (bound.getZ() + r) >> 4;
                for (int cx = minX; cx <= maxX; cx++) {
                    for (int cz = minZ; cz <= maxZ; cz++) {
                        if (!loaded(level, cx, cz)) {
                            return status;
                        }
                    }
                }
                landing = nearBound(level, bound, yaw);
                warded = landing == null && CaveInimicumManager.get(level).isWardedAt(bound);
                status = Status.READY;
                return status;
            }
            ChunkPos chunk = new ChunkPos(current);
            if (!loaded(level, chunk.x, chunk.z)) {
                return status;
            }
            for (int[] step : SPIRAL) {
                int x = Math.max(chunk.getMinBlockX(), Math.min(chunk.getMaxBlockX(), current.getX() + step[0]));
                int z = Math.max(chunk.getMinBlockZ(), Math.min(chunk.getMaxBlockZ(), current.getZ() + step[1]));
                Vec3 found = ModEvents.findApparitionLanding(level, x, z);
                if (found == null) {
                    continue;
                }
                BlockPos feet = BlockPos.containing(found.x, found.y, found.z);
                Vec3 spot = landingAt(level, feet);
                if (spot != null && level.canSeeSky(feet)) {
                    landing = spot;
                    status = Status.READY;
                    return status;
                }
            }
            advance();
            return status;
        }

        /** The landing once READY; null when READY means "nowhere safe" (a fizzle). */
        public Vec3 landing() {
            return landing;
        }

        /** Whether a READY request found nothing because the bound spot is warded. */
        public boolean warded() {
            return warded;
        }

        public ServerLevel level() {
            return level;
        }

        /** The facing on landing: the bound yaw for a Portkey. */
        public float yaw() {
            return yaw;
        }

        /** Drop every ticket this request holds (safe to call more than once). */
        public void release() {
            for (ChunkPos pos : new ArrayList<>(ticketed)) {
                unticket(pos);
            }
        }
    }
}
