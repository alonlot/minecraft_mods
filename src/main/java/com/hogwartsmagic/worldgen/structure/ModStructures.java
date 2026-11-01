package com.hogwartsmagic.worldgen.structure;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's code-defined structures. The jigsaw ones (camps, ruins, the chamber) need no code and live in data
 * only; Mini Hogwarts is placed from a Sponge schematic, so it has its own structure type and piece type.
 * The structure itself, where it may generate and how rare it is, is data:
 * {@code data/hogwartsmagic/worldgen/structure/mini_hogwarts.json} and its structure set.
 */
public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, ExampleMod.MODID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, ExampleMod.MODID);

    public static final RegistryObject<StructureType<MiniHogwartsStructure>> MINI_HOGWARTS_TYPE =
            STRUCTURE_TYPES.register("mini_hogwarts", () -> () -> MiniHogwartsStructure.CODEC);

    public static final RegistryObject<StructurePieceType> MINI_HOGWARTS_PIECE =
            STRUCTURE_PIECE_TYPES.register("mini_hogwarts",
                    () -> (StructurePieceType.ContextlessType) MiniHogwartsPiece::new);

    /** The data-driven structure of that type. */
    public static final ResourceKey<Structure> MINI_HOGWARTS = ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "mini_hogwarts"));

    /** A structure tag holding just the castle, for {@code ServerLevel.findNearestMapStructure}. */
    public static final TagKey<Structure> MINI_HOGWARTS_TAG = TagKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "mini_hogwarts"));

    private ModStructures() {
    }

    public static void register(IEventBus modEventBus) {
        STRUCTURE_TYPES.register(modEventBus);
        STRUCTURE_PIECE_TYPES.register(modEventBus);
    }
}
