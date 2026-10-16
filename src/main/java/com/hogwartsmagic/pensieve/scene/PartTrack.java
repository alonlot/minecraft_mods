package com.hogwartsmagic.pensieve.scene;

/**
 * Per-limb override curves: rotation in degrees [x,y,z], offset in model pixels. {@code set} (the limb key's
 * {@code "mode":"set"}) makes the curves replace the part's current values instead of being added to them.
 */
public record PartTrack(Track rot, Track offset, boolean set) {
    public PartTrack(Track rot, Track offset) {
        this(rot, offset, false);
    }
}
