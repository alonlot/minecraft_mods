package com.hogwartsmagic.block;

import net.minecraft.util.StringRepresentable;

/**
 * What a {@link PhantomRuinBlock} looks like: the three stones an overgrown ruin is built from.
 * Each name is the vanilla block whose model the phantom borrows.
 */
public enum PhantomLook implements StringRepresentable {
    MOSSY_COBBLESTONE("mossy_cobblestone"),
    COBBLESTONE("cobblestone"),
    STONE_BRICKS("stone_bricks");

    private final String key;

    PhantomLook(String key) {
        this.key = key;
    }

    @Override
    public String getSerializedName() {
        return key;
    }
}
