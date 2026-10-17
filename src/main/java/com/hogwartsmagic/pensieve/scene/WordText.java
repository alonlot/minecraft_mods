package com.hogwartsmagic.pensieve.scene;

/** Text substitution for scene words. Pure: the client passes the viewer's name in. */
public final class WordText {
    private WordText() {
    }

    /** Replaces every {@code {viewer}} with {@code viewerName} (null becomes empty); other braces are untouched. */
    public static String fill(String text, String viewerName) {
        if (text == null) return "";
        return text.replace("{viewer}", viewerName == null ? "" : viewerName);
    }
}
