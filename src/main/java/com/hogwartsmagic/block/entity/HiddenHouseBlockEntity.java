package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Tags a block of a hidden wizard house (a phantom ruin stone, a glamoured block or a jinx ward) so
 * Revelio finds it by walking loaded block entities, and holds what a glamour is hiding: the real
 * block state and, for a chest or a bed, its real block-entity data.
 *
 * <p>It never ticks and never sends an update packet, so a client cannot read the house out of its
 * own chunk data before it is revealed.
 */
public class HiddenHouseBlockEntity extends BlockEntity {
    private static final String REAL = "Real";
    private static final String REAL_NBT = "RealNbt";

    @Nullable
    private BlockState real;
    @Nullable
    private CompoundTag realNbt;

    public HiddenHouseBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.HIDDEN_HOUSE_BLOCK_ENTITY.get(), pos, state);
    }

    /** The block this glamour becomes, or null for a phantom (which just vanishes). */
    @Nullable
    public BlockState real() {
        return real;
    }

    /** The data the real block starts with (a chest loot table), or null. */
    @Nullable
    public CompoundTag realNbt() {
        return realNbt;
    }

    public void setReal(@Nullable BlockState state, @Nullable CompoundTag nbt) {
        this.real = state;
        this.realNbt = nbt == null ? null : nbt.copy();
        setChanged();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // The level is not set yet while a template or chunk loads this, so read blocks through the registry.
        real = tag.contains(REAL)
                ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound(REAL))
                : null;
        realNbt = tag.contains(REAL_NBT) ? tag.getCompound(REAL_NBT).copy() : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (real != null) {
            tag.put(REAL, NbtUtils.writeBlockState(real));
        }
        if (realNbt != null) {
            tag.put(REAL_NBT, realNbt.copy());
        }
    }
}
