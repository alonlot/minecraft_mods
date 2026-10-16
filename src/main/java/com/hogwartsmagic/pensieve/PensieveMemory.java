package com.hogwartsmagic.pensieve;

import net.minecraft.world.item.ItemStack;

/** An item that can be poured into a Pensieve and watched as a scene. */
public interface PensieveMemory {
    /** The scene (see MemoryRegistry) this stack plays. */
    String sceneId(ItemStack stack);

    /** True when watching the memory uses it up. */
    default boolean consumedOnWatch() {
        return false;
    }
}
