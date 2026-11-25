package com.hogwartsmagic.cosy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The fuel an enchanted stove holds, in burn ticks. It is lit exactly while that is above zero. */
public class EnchantedStoveBlockEntity extends BlockEntity {
    private int burn;

    public EnchantedStoveBlockEntity(BlockPos pos, BlockState state) {
        super(CosyKitchen.ENCHANTED_STOVE_BLOCK_ENTITY.get(), pos, state);
    }

    public int burn() {
        return this.burn;
    }

    /** Adds fuel worth {@code ticks}. False when the stove has no room for it. */
    public boolean addFuel(int ticks) {
        if (!PotRules.canTakeFuel(this.burn, ticks)) {
            return false;
        }
        this.burn = Math.min(PotRules.STOVE_MAX_BURN, this.burn + ticks);
        setChanged();
        return true;
    }

    /** Sets the LIT state to match the fuel. */
    public void refreshLit(Level level) {
        BlockState state = getBlockState();
        boolean lit = this.burn > 0;
        if (state.getValue(EnchantedStoveBlock.LIT) != lit) {
            level.setBlock(this.worldPosition, state.setValue(EnchantedStoveBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EnchantedStoveBlockEntity stove) {
        if (stove.burn > 0) {
            stove.burn--;
            if (stove.burn % 200 == 0) {
                stove.setChanged();
            }
        }
        stove.refreshLit(level);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Burn", this.burn);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.burn = Math.max(0, Math.min(PotRules.STOVE_MAX_BURN, tag.getInt("Burn")));
    }
}
