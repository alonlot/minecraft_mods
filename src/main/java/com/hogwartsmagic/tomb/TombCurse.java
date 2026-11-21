package com.hogwartsmagic.tomb;

import net.minecraft.util.StringRepresentable;

/** The curses a tomb's glyph tiles can carry. Each goes off once. */
public enum TombCurse implements StringRepresentable {
    /** The ceiling comes down: sand falls round the player. */
    COLLAPSE("collapse"),
    /** The dead get up: husks and an Inferius climb out of the floor round the player. */
    RISEN("risen"),
    /** The pharaoh's curse: Wither, Hunger and Mining Fatigue, so digging out is slow. */
    WITHERING("withering");

    private final String name;

    TombCurse(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
