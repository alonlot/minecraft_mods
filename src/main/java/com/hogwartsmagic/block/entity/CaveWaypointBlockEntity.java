package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The role (basin, arch, ring, dock_shore, dock_island, path, lake) and order of one cave waypoint. Never ticks. */
public class CaveWaypointBlockEntity extends BlockEntity {
    private String role = "";
    private int index;

    public CaveWaypointBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.CAVE_WAYPOINT_BLOCK_ENTITY.get(), pos, state);
    }

    public String role() {
        return role;
    }

    public int index() {
        return index;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Role", role);
        tag.putInt("Index", index);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        role = tag.getString("Role");
        index = tag.getInt("Index");
    }
}
