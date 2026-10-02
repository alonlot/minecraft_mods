package com.hogwartsmagic.spell.combat;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/** Damage sources for the shared combat effects. The types are data-driven (data/hogwartsmagic/damage_type). */
public final class CombatDamage {
    public static final ResourceKey<DamageType> BLEEDING = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "bleeding"));

    private CombatDamage() {
    }

    /** Bypasses armour, no knockback (see the damage_type tags); death message "bled out". */
    public static DamageSource bleeding(Level level) {
        return bleeding(level, null);
    }

    /**
     * As {@link #bleeding(Level)}, with {@code attacker} as the causing entity only (no direct entity) so a bleed-out
     * kill is credited to them without every tick looking like a melee hit by them.
     */
    public static DamageSource bleeding(Level level, @Nullable Entity attacker) {
        Holder<DamageType> type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BLEEDING);
        return attacker == null ? new DamageSource(type) : new DamageSource(type, null, attacker);
    }
}
