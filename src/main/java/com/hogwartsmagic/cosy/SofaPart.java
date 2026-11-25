package com.hogwartsmagic.cosy;

import net.minecraft.util.StringRepresentable;

/** Where a sofa sits in a run of sofas, which decides where its arms go. */
public enum SofaPart implements StringRepresentable {
    SINGLE("single"),
    LEFT("left"),
    MIDDLE("middle"),
    RIGHT("right");

    private final String name;

    SofaPart(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** True if this part has an arm on the sitter's left. */
    public boolean hasLeftArm() {
        return this == SINGLE || this == LEFT;
    }

    /** True if this part has an arm on the sitter's right. */
    public boolean hasRightArm() {
        return this == SINGLE || this == RIGHT;
    }
}
