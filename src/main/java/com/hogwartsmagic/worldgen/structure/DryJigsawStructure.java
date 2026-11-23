package com.hogwartsmagic.worldgen.structure;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

import java.util.Optional;

/**
 * A vanilla jigsaw structure that refuses to start on water. A jigsaw projected to {@code WORLD_SURFACE_WG} treats
 * the top of a lake, river or the sea as ground, so a surface build (the Cursebreaker's dig site) ended up sunk into
 * an oasis with its pit full of water. This type reads exactly the {@code minecraft:jigsaw} JSON fields, lets
 * vanilla pick the start, then samples a grid over the footprint and drops the start if any sample has fluid above
 * the solid floor (the two worldgen heightmaps disagree only where water or lava sits on top).
 *
 * <p>{@link JigsawStructure} is final, so this wraps one instead of extending it; the wrapper carries the same
 * structure settings, so biome, step, terrain adaptation and spawn overrides behave as before.
 */
public class DryJigsawStructure extends Structure {
    public static final MapCodec<DryJigsawStructure> CODEC =
            JigsawStructure.CODEC.xmap(DryJigsawStructure::new, DryJigsawStructure::inner);

    /** Half the side of the sampled square around the start; covers a 25x21 template in any rotation. */
    static final int SAMPLE_RADIUS = 13;

    private final JigsawStructure inner;

    public DryJigsawStructure(JigsawStructure inner) {
        super(inner.modifiableStructureInfo().getOriginalStructureInfo().structureSettings());
        this.inner = inner;
    }

    JigsawStructure inner() {
        return inner;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        Optional<GenerationStub> stub = inner.findGenerationPoint(context);
        if (stub.isEmpty()) {
            return stub;
        }
        BlockPos centre = stub.get().position();
        for (int dx = -SAMPLE_RADIUS; dx <= SAMPLE_RADIUS; dx += SAMPLE_RADIUS) {
            for (int dz = -SAMPLE_RADIUS; dz <= SAMPLE_RADIUS; dz += SAMPLE_RADIUS) {
                if (isWet(context, centre.getX() + dx, centre.getZ() + dz)) {
                    return Optional.empty();
                }
            }
        }
        return stub;
    }

    /** True when the column at (x, z) has fluid sitting on its solid floor in the noise terrain. */
    private static boolean isWet(GenerationContext context, int x, int z) {
        int surface = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
        int floor = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                context.heightAccessor(), context.randomState());
        return surface > floor;
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.DRY_JIGSAW_TYPE.get();
    }
}
