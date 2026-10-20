package com.hogwartsmagic.entity;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * One block state used as a Pensieve memory actor ({@code hogwartsmagic:memory_block}): a brick of a wall that opens,
 * a wand box flying off a shelf, a vase. It has no AI, gravity, collision or save data. The memory sets its block
 * state from the actor's {@code block} key, moves it along the actor path (the block's centre) and turns it with the
 * look yaw, pitch and roll; the renderer draws the block's baked model about its centre.
 */
public class MemoryBlockEntity extends Entity {
    private BlockState state = Blocks.AIR.defaultBlockState();
    /** The look roll, degrees, this tick and last tick (the vanilla rotation has no roll). */
    public float roll;
    public float rollO;

    public MemoryBlockEntity(EntityType<? extends MemoryBlockEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    /**
     * The block state for a scene's {@code block} string ({@code minecraft:oak_stairs[facing=east]}), parsed by the
     * game's own parser; null (logged by the caller) when the block or a property does not exist.
     */
    @Nullable
    public static BlockState parseState(String block) {
        if (block == null) return null;
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), block, false).blockState();
        } catch (Exception e) {
            return null;
        }
    }

    public BlockState blockState() {
        return state;
    }

    public void setBlockState(BlockState state) {
        this.state = state == null ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /**
     * A tumbling block at scale 4 reaches about 3.5 blocks from its centre. Pensieve draws actors through
     * EntityRenderDispatcher.render, which does not frustum-cull, so this only matters to a path that does.
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        double x = getX(), y = getY(), z = getZ();
        return new AABB(x - 3.5, y - 3.5, z - 3.5, x + 3.5, y + 3.5, z + 3.5);
    }
}
