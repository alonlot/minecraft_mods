package com.hogwartsmagic.client.pensieve;

import net.minecraft.world.phys.Vec3;

/** Implemented on {@code Camera} by {@code PensieveCameraMixin}: reaches its protected {@code setPosition}. */
public interface PensieveCameraAccess {
    void hogwartsmagic$setPosition(Vec3 position);
}
