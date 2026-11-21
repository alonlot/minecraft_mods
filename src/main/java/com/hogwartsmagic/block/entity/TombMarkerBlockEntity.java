package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tags a cursed glyph or a concealed sandstone wall in a desert tomb, so {@code TombCurses.reveal}
 * finds them by walking loaded block entities, as {@link CaveMarkerBlockEntity} does for the Locket
 * cave. It is a separate type on purpose: the Locket cave reveals everything within 48 blocks, and
 * a tomb has to be cleared one room at a time. Holds no data and never ticks.
 */
public class TombMarkerBlockEntity extends BlockEntity {
    public TombMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.TOMB_MARKER_BLOCK_ENTITY.get(), pos, state);
    }
}
