package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.SneakoscopeBlock;
import com.hogwartsmagic.sneakoscope.SneakoscopeRules;
import com.hogwartsmagic.sneakoscope.SneakoscopeSensor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The placed Sneakoscope's clock. It holds no data: once a second (phased by position, as the cauldron) it measures
 * the strength and writes it into the block state, only when it changed. Going from 0 to more whistles once.
 */
public class SneakoscopeBlockEntity extends BlockEntity {
    public SneakoscopeBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.SNEAKOSCOPE_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SneakoscopeBlockEntity sneakoscope) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!SneakoscopeRules.dueToCheck(level.getGameTime(), pos.asLong())) {
            return;
        }
        if (!state.hasProperty(SneakoscopeBlock.POWER)) {
            return;
        }
        int power = SneakoscopeSensor.strength(serverLevel, Vec3.atCenterOf(pos), null);
        int old = state.getValue(SneakoscopeBlock.POWER);
        if (power == old) {
            return;
        }
        // UPDATE_ALL notifies the neighbours, so lamps, wire and comparators re-read getSignal.
        level.setBlock(pos, state.setValue(SneakoscopeBlock.POWER, power), Block.UPDATE_ALL);
        if (SneakoscopeRules.whistlesOnRise(old, power)) {
            SneakoscopeSensor.whistleAt(serverLevel, pos);
        }
    }
}
