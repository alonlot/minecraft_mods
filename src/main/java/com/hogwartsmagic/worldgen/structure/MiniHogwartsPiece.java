package com.hogwartsmagic.worldgen.structure;

import com.hogwartsmagic.worldgen.MiniHogwartsAmbience;
import com.hogwartsmagic.worldgen.MiniHogwartsAnchors;
import com.hogwartsmagic.worldgen.MiniHogwartsManager;
import com.hogwartsmagic.worldgen.MiniHogwartsSchematic;
import com.hogwartsmagic.worldgen.MiniHogwartsTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraftforge.common.world.PieceBeardifierModifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The one piece of the Mini Hogwarts structure: the whole schematic, anchored at its min corner.
 *
 * <p>Worldgen decorates a structure one chunk at a time, handing {@link #postProcess} that chunk's box. Everything
 * here writes only inside that box: the schematic cells that fall in it, the stone skirt under the castle's
 * columns that fall in it, and the furnishings whose positions fall in it. That is the vanilla-safe way to build
 * something many chunks wide (woodland mansions and ancient cities do the same); a worldgen Feature writing the
 * whole castle at once is what produced the "setBlock in a far chunk" crash. Nothing here loads a chunk, and the
 * level-wide state, the castle tracker and the ambience registry, is handed to the server thread.
 *
 * <p>The bounding box is the schematic's box extended {@link #MAX_SKIRT_DEPTH} blocks down, so the chunks under
 * the skirt are decorated too. The terrain is bearded to the schematic's own box (not the skirt), so the ground
 * meets the castle's base rather than being pulled down to the bottom of the skirt.
 */
public class MiniHogwartsPiece extends StructurePiece implements PieceBeardifierModifier {
    private static final Logger LOGGER = LoggerFactory.getLogger("HogwartsMagic-MiniHogwarts");

    /** Max depth of the stone support skirt filled under floating parts (only fills air, stops at ground). */
    public static final int MAX_SKIRT_DEPTH = 64;
    private static final BlockState FOUNDATION_FILLER = Blocks.STONE.defaultBlockState();
    private static final int SETBLOCK_FLAGS = Block.UPDATE_CLIENTS;

    private static volatile boolean warnedSizeChanged;

    /** The schematic's min corner in the world. */
    private final BlockPos anchor;
    /** The schematic's size when this piece was generated; a different size later means the castle file changed. */
    private final int width, height, length;

    public MiniHogwartsPiece(BlockPos anchor) {
        this(anchor, requireSchematic());
    }

    private MiniHogwartsPiece(BlockPos anchor, MiniHogwartsSchematic schem) {
        super(ModStructures.MINI_HOGWARTS_PIECE.get(), 0, boxFor(anchor, schem));
        this.anchor = anchor.immutable();
        this.width = schem.width();
        this.height = schem.height();
        this.length = schem.length();
        this.setOrientation(null);
    }

    /** Read back from a saved chunk. */
    public MiniHogwartsPiece(CompoundTag tag) {
        super(ModStructures.MINI_HOGWARTS_PIECE.get(), tag);
        this.anchor = new BlockPos(tag.getInt("AX"), tag.getInt("AY"), tag.getInt("AZ"));
        this.width = tag.getInt("W");
        this.height = tag.getInt("H");
        this.length = tag.getInt("L");
    }

    private static MiniHogwartsSchematic requireSchematic() {
        MiniHogwartsSchematic schem = MiniHogwartsSchematic.get();
        if (schem == null) {
            throw new IllegalStateException("mini_hogwarts schematic is missing");
        }
        return schem;
    }

    /** The piece's box for a castle anchored at {@code anchor}: the schematic box plus the skirt below it. */
    static BoundingBox boxFor(BlockPos anchor, MiniHogwartsSchematic schem) {
        return new BoundingBox(anchor.getX(), anchor.getY() - MAX_SKIRT_DEPTH, anchor.getZ(),
                anchor.getX() + schem.width() - 1, anchor.getY() + schem.height() - 1,
                anchor.getZ() + schem.length() - 1);
    }

    public BlockPos anchor() {
        return anchor;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("AX", anchor.getX());
        tag.putInt("AY", anchor.getY());
        tag.putInt("AZ", anchor.getZ());
        tag.putInt("W", width);
        tag.putInt("H", height);
        tag.putInt("L", length);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        MiniHogwartsSchematic schem = MiniHogwartsSchematic.get();
        if (schem == null) {
            return;
        }
        if ((schem.width() != width || schem.height() != height || schem.length() != length) && !warnedSizeChanged) {
            warnedSizeChanged = true;
            LOGGER.warn("Mini Hogwarts generated as {}x{}x{} but the schematic is now {}x{}x{}; chunks decorated from"
                            + " now on use the new file, clipped to the old castle's box",
                    width, height, length, schem.width(), schem.height(), schem.length());
        }
        // Never write outside this chunk, nor outside the box the structure start promised.
        BoundingBox box = intersection(chunkBox, getBoundingBox());
        if (box == null) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int ax = anchor.getX();
        int ay = anchor.getY();
        int az = anchor.getZ();
        int x0 = box.minX() - ax, x1 = box.maxX() - ax;
        int z0 = box.minZ() - az, z1 = box.maxZ() - az;
        int y0 = Math.max(0, box.minY() - ay), y1 = Math.min(schem.height() - 1, box.maxY() - ay);

        // 1. The schematic's cells. Plain air is skipped, so the terrain around the castle is never carved; cave
        //    air marks interior space that must be cleared (rooms cut into the hill, the hall, tower insides) and
        //    is written as cave air like any block. (So the skip test is is(AIR), never isAir(): that is true for
        //    cave air too.)
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    BlockState state = schem.blockAt(x, y, z);
                    if (!state.is(Blocks.AIR)) {
                        level.setBlock(pos.set(ax + x, ay + y, az + z), state, SETBLOCK_FLAGS);
                    }
                }
            }
        }

        // 2. The skirt: under each column, stone straight down from the castle's lowest block until it meets
        //    the ground. Only air is filled, so no terrain is replaced and the castle never floats.
        int floor = Math.max(level.getMinBuildHeight() + 1, box.minY());
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                int low = schem.lowestSolidY(x, z);
                if (low < 0) {
                    continue;
                }
                int y = ay + low - 1;
                for (int depth = 0; depth < MAX_SKIRT_DEPTH && y >= floor; depth++, y--) {
                    if (y >= ay && schem.blockAt(x, y - ay, z).is(Blocks.CAVE_AIR)) {
                        break; // space the schematic means to keep clear (a passage under an overhang)
                    }
                    pos.set(ax + x, y, az + z);
                    if (!level.getBlockState(pos).isAir()) {
                        break; // reached solid ground: stop, never replace existing terrain
                    }
                    level.setBlock(pos, FOUNDATION_FILLER, SETBLOCK_FLAGS);
                }
            }
        }

        // 3. Chests, the Pensieve, the living portraits, students and elves that belong to this chunk.
        MiniHogwartsManager.furnishChunk(level, anchor, box, random);

        // 4. The chunk holding the centre column records the castle, on the server thread (SavedData is not safe
        //    to touch from a worldgen thread).
        int centreX = ax + schem.width() / 2;
        int centreZ = az + schem.length() / 2;
        if (chunkBox.minX() <= centreX && centreX <= chunkBox.maxX()
                && chunkBox.minZ() <= centreZ && centreZ <= chunkBox.maxZ()) {
            MinecraftServer server = level.getServer();
            ServerLevel serverLevel = level.getLevel();
            if (server != null && serverLevel != null) {
                BlockPos center = new BlockPos(centreX, ay + schem.height() / 3, centreZ);
                BlockPos at = anchor;
                // The anchors are immutable, so they are read here and handed over; the castle's ambient effects (the
                // hall's ceiling, candles, light rays and doors) are registered by the same server-thread task. Their
                // ids come from the anchor, so a chunk decorated again registers nothing twice.
                MiniHogwartsAnchors anchors = MiniHogwartsAnchors.forSchematic(schem);
                server.execute(() -> {
                    MiniHogwartsTracker.get(serverLevel).add(center, at);
                    if (anchors != null) {
                        MiniHogwartsAmbience.register(serverLevel, at, anchors);
                    }
                });
                LOGGER.info("Mini Hogwarts generated: anchor {} {} {}, centre {}", ax, ay, az, center);
            }
        }
    }

    /** The overlap of two boxes, or null when they do not touch. */
    private static BoundingBox intersection(BoundingBox a, BoundingBox b) {
        int minX = Math.max(a.minX(), b.minX()), maxX = Math.min(a.maxX(), b.maxX());
        int minY = Math.max(a.minY(), b.minY()), maxY = Math.min(a.maxY(), b.maxY());
        int minZ = Math.max(a.minZ(), b.minZ()), maxZ = Math.min(a.maxZ(), b.maxZ());
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return null;
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // ---------------------------------------------------------------- terrain adaptation (Forge hook)

    /** Beard the terrain to the schematic's own box, not the skirt below it. */
    @Override
    public BoundingBox getBeardifierBox() {
        BoundingBox full = getBoundingBox();
        return new BoundingBox(full.minX(), anchor.getY(), full.minZ(), full.maxX(), full.maxY(), full.maxZ());
    }

    @Override
    public TerrainAdjustment getTerrainAdjustment() {
        return TerrainAdjustment.BEARD_THIN;
    }

    @Override
    public int getGroundLevelDelta() {
        return 0;
    }
}
