package com.hogwartsmagic.entity.boggart;

import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/** Something Riddikulus does more to than make it glow. Today that is only the Boggart. */
public interface RiddikulusTarget {
    void onRiddikulus(int rank, @Nullable Entity caster);
}
