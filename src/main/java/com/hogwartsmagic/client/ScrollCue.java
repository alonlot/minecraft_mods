package com.hogwartsmagic.client;

/**
 * The scroll-bar maths of the tome picker, kept free of Minecraft types so it is unit-tested directly.
 */
public final class ScrollCue {
    private ScrollCue() {
    }

    /** The thumb as {offsetY, height} inside a track; the height never drops below {@code minThumb}. */
    public static int[] thumb(float scroll, float maxScroll, int trackHeight, int minThumb) {
        if (maxScroll <= 0.0F) {
            return new int[] {0, trackHeight};
        }
        int height = Math.min(trackHeight, Math.max(minThumb,
                Math.round(trackHeight * (float) trackHeight / (trackHeight + maxScroll))));
        int offset = Math.round((trackHeight - height) * Math.max(0.0F, Math.min(scroll, maxScroll)) / maxScroll);
        return new int[] {offset, height};
    }

    public static boolean moreAbove(float scroll) {
        return scroll > 0.0F;
    }

    public static boolean moreBelow(float scroll, float maxScroll) {
        return scroll < maxScroll;
    }
}
