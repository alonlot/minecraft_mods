package com.hogwartsmagic.spell.combat;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import javax.annotation.Nullable;

/** Tags shared by the combat and mastery rules, in one place so the spellings can't drift. */
public final class CombatTags {
    /** Scoreboard tag on a mob fighting for a caster (Imperio thralls, Serpensortia snakes). */
    public static final String ALLIED = "hogwartsmagic:allied";

    /**
     * Bosses: hard crowd control lasts half as long. Later phases also read it (Obliviate, Confundo
     * and Avada's instant kill skip bosses; Imperio is capped at short durations).
     */
    public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "bosses"));

    private CombatTags() {
    }

    public static boolean isBoss(@Nullable Entity entity) {
        return entity != null && entity.getType().is(BOSSES);
    }
}
