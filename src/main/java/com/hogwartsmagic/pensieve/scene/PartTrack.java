package com.hogwartsmagic.pensieve.scene;

/** Per-limb override curves: rotation in degrees [x,y,z], offset in model pixels. */
public record PartTrack(Track rot, Track offset) {
}
