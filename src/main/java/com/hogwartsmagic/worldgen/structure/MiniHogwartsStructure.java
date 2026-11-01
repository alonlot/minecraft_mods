package com.hogwartsmagic.worldgen.structure;

import com.hogwartsmagic.worldgen.MiniHogwartsSchematic;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Mini Hogwarts as a normal worldgen structure: it generates with the world and is already standing when a
 * player finds it, the way a woodland mansion is. Where it may appear (the Forbidden Forest) and how rare it is
 * are data (the structure JSON and its random-spread structure set); this class only sites the castle.
 *
 * <p>The castle is centred on its start chunk's middle column, unrotated (the schematic's anchors know no
 * rotation), its base sunk {@link #FOUNDATION_SINK} blocks below the highest ground of its footprint. A chunk only
 * learns about structure starts within 8 chunks of it, so the whole piece must stay inside that radius of the
 * start chunk; a schematic that would not is refused with an error in the log rather than generating a castle
 * with its far ends cut off.
 */
public class MiniHogwartsStructure extends Structure {
    private static final Logger LOGGER = LoggerFactory.getLogger("HogwartsMagic-MiniHogwarts");

    public static final MapCodec<MiniHogwartsStructure> CODEC = simpleCodec(MiniHogwartsStructure::new);

    /** Tuck the base this far below the HIGHEST ground under the footprint so it sits on the hill, not buried. */
    public static final int FOUNDATION_SINK = 2;
    /** How far (in chunks) a chunk looks for structure starts that reach it; vanilla's fixed reference radius. */
    static final int REFERENCE_RADIUS_CHUNKS = 8;

    private static volatile boolean warnedTooBig;

    public MiniHogwartsStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        MiniHogwartsSchematic schem = MiniHogwartsSchematic.get();
        if (schem == null) {
            return Optional.empty();
        }
        ChunkPos start = context.chunkPos();
        int centreX = start.getMiddleBlockX();
        int centreZ = start.getMiddleBlockZ();
        int w = schem.width();
        int l = schem.length();
        int minY = context.heightAccessor().getMinBuildHeight();

        // Anchor the base to the HIGHEST ground under the footprint (sampled on a 3x3 grid), so the castle sits on
        // top of the hill rather than burying its lower floors; the stone skirt fills under the downhill side.
        int ground = Integer.MIN_VALUE;
        for (int sx = -w / 2; sx <= w / 2; sx += Math.max(1, w / 2)) {
            for (int sz = -l / 2; sz <= l / 2; sz += Math.max(1, l / 2)) {
                int h = context.chunkGenerator().getFirstOccupiedHeight(centreX + sx, centreZ + sz,
                        Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
                if (h > minY + 1) {
                    ground = Math.max(ground, h);
                }
            }
        }
        if (ground == Integer.MIN_VALUE) {
            ground = minY + 1 + FOUNDATION_SINK; // a void column everywhere: sit on the floor of the world
        }
        int anchorY = Math.max(minY + 1, ground - FOUNDATION_SINK);
        BlockPos anchor = new BlockPos(centreX - w / 2, anchorY, centreZ - l / 2);

        BoundingBox box = MiniHogwartsPiece.boxFor(anchor, schem);
        if (!fitsReferenceRadius(start, box)) {
            if (!warnedTooBig) {
                warnedTooBig = true;
                LOGGER.error("Mini Hogwarts schematic {}x{}x{} reaches past {} chunks from its start chunk; the castle"
                                + " cannot generate (chunks beyond that radius would never see it)",
                        w, schem.height(), l, REFERENCE_RADIUS_CHUNKS);
            }
            return Optional.empty();
        }
        return Optional.of(new GenerationStub(new BlockPos(centreX, ground, centreZ),
                builder -> builder.addPiece(new MiniHogwartsPiece(anchor))));
    }

    /** True when {@code box} lies within {@link #REFERENCE_RADIUS_CHUNKS} chunks of {@code start} on X and Z. */
    static boolean fitsReferenceRadius(ChunkPos start, BoundingBox box) {
        return (box.minX() >> 4) >= start.x - REFERENCE_RADIUS_CHUNKS
                && (box.maxX() >> 4) <= start.x + REFERENCE_RADIUS_CHUNKS
                && (box.minZ() >> 4) >= start.z - REFERENCE_RADIUS_CHUNKS
                && (box.maxZ() >> 4) <= start.z + REFERENCE_RADIUS_CHUNKS;
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.MINI_HOGWARTS_TYPE.get();
    }
}
