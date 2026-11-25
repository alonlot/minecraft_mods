package com.hogwartsmagic.cosy;

/** The neighbour rules of the cosy furniture. Pure, so JUnit can pin them. */
public final class FurnitureRules {
    private FurnitureRules() {
    }

    public static final int BORDER_NORTH = 1;
    public static final int BORDER_EAST = 2;
    public static final int BORDER_SOUTH = 4;
    public static final int BORDER_WEST = 8;

    /**
     * Which part of a run of sofas this one is. Left and right are from the sitter's view, facing the way the
     * sofa faces. A neighbour on the left means this is the right-hand end, so the arm goes on the right.
     */
    public static SofaPart sofaPart(boolean sameOnLeft, boolean sameOnRight) {
        if (sameOnLeft && sameOnRight) {
            return SofaPart.MIDDLE;
        }
        if (sameOnLeft) {
            return SofaPart.RIGHT;
        }
        if (sameOnRight) {
            return SofaPart.LEFT;
        }
        return SofaPart.SINGLE;
    }

    /** A bit per side that has NO matching rug beside it, so needs a border: N=1, E=2, S=4, W=8. */
    public static int rugBorderMask(boolean n, boolean e, boolean s, boolean w) {
        return (n ? 0 : BORDER_NORTH) | (e ? 0 : BORDER_EAST) | (s ? 0 : BORDER_SOUTH) | (w ? 0 : BORDER_WEST);
    }
}
