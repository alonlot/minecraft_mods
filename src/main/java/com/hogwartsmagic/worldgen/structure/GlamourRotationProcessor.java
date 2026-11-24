package com.hogwartsmagic.worldgen.structure;

import com.hogwartsmagic.ExampleMod;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;

/**
 * Turns a hidden wizard house furniture with its ruin. A jigsaw rotates or mirrors the whole template,
 * but the real block a glamour is hiding sits in block-entity data, which the template system leaves
 * alone: a bed hidden facing south would reveal facing south in a house turned to face east, splitting
 * in two. This rewrites the stored state the same way the template turns the visible blocks.
 */
public final class GlamourRotationProcessor extends StructureProcessor {
    public static final GlamourRotationProcessor INSTANCE = new GlamourRotationProcessor();

    private static final String REAL = "Real";

    public static final DeferredRegister<StructureProcessorType<?>> PROCESSOR_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, ExampleMod.MODID);
    public static final RegistryObject<StructureProcessorType<GlamourRotationProcessor>> TYPE =
            PROCESSOR_TYPES.register("glamour_rotation", () -> () -> MapCodec.unit(INSTANCE));

    private GlamourRotationProcessor() {
    }

    public static void register(IEventBus modEventBus) {
        PROCESSOR_TYPES.register(modEventBus);
    }

    @Nullable
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pos,
                                                             StructureTemplate.StructureBlockInfo blockInfo,
                                                             StructureTemplate.StructureBlockInfo relativeBlockInfo,
                                                             StructurePlaceSettings settings) {
        CompoundTag nbt = relativeBlockInfo.nbt();
        if (nbt == null || !nbt.contains(REAL)) {
            return relativeBlockInfo;
        }
        BlockState real = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), nbt.getCompound(REAL));
        // The same order StructureTemplate.placeInWorld turns the visible blocks in.
        real = real.mirror(settings.getMirror()).rotate(settings.getRotation());
        CompoundTag turned = nbt.copy(); // never edit the template own tag: it is shared by every placement
        turned.put(REAL, NbtUtils.writeBlockState(real));
        return new StructureTemplate.StructureBlockInfo(relativeBlockInfo.pos(), relativeBlockInfo.state(), turned);
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return TYPE.get();
    }
}
