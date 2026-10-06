package com.hogwartsmagic.locket;

import net.minecraft.util.StringRepresentable;

/**
 * Visual and logical stage of the Basin of Despair. This is a block-state enum, so unlike the pure rule
 * classes beside it, it imports Minecraft's {@link StringRepresentable}.
 */
public enum BasinStage implements StringRepresentable {
    FULL("full"),
    DRINKING("drinking"),
    LOOTED("looted");

    private final String name;

    BasinStage(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
