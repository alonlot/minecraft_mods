package com.hogwartsmagic.pensieve;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A story memory: a vial of silvery thought that plays one fixed scene when poured into a Pensieve. */
public class StoryMemoryItem extends Item implements PensieveMemory {
    private final String sceneId;

    public StoryMemoryItem(Properties properties, String sceneId) {
        super(properties.stacksTo(1));
        this.sceneId = sceneId;
    }

    @Override
    public String sceneId(ItemStack stack) {
        return sceneId;
    }
}
