package com.hogwartsmagic.client.renderer;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Vanilla's wolf, fox, rabbit and parrot models (and the mod's owl model) are typed to their own entity
 * and read entity state in setupAnim, so they can't be handed a PatronusEntity. Each gets one idle,
 * never-added stand-in of its own type per client level, created on first use; the Patronus supplies
 * the pose numbers (limb swing, head angles, age) and the stand-in only answers the type's getters with
 * defaults. Dropped whenever the client level changes.
 */
final class PatronusFormDummies {
    @Nullable
    private Level level;
    private final Map<EntityType<?>, Entity> cache = new HashMap<>();

    @Nullable
    <T extends Entity> T get(EntityType<T> type, Level current) {
        if (current != this.level) {
            this.cache.clear();
            this.level = current;
        }
        Entity entity = this.cache.get(type);
        if (entity == null) {
            entity = type.create(current);
            if (entity == null) {
                return null;
            }
            this.cache.put(type, entity);
        }
        return type.tryCast(entity);
    }
}
