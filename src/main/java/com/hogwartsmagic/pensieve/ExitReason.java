package com.hogwartsmagic.pensieve;

/**
 * Why a viewer left a memory. Server side; the client receives {@link #ordinal()} in the exit packet, so the
 * order is part of the wire format: append only.
 */
public enum ExitReason {
    /** The scene played to its end. */
    END,
    /** The body was hurt badly enough to tear the viewer out (Task 7). */
    YANK,
    /** The viewer chose to leave early. */
    LEFT,
    /** The basin was broken or its memory taken out. */
    ABORT
}
