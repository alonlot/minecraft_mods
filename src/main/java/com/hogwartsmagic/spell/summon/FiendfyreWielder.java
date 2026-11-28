package com.hogwartsmagic.spell.summon;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A non-player that holds a Fiendfyre channel (Voldemort). Its serpents ({@code FiendfyreSerpentEntity} with a
 * mob caster) ask it every tick whether the fire is still held, where it is aimed and how far out to drive it, and
 * whom it may burn; the moment it stops answering yes they go out. Server only, no mana, no rogue serpents.
 */
public interface FiendfyreWielder {
    /** Whether the channel is held right now. */
    boolean fiendfyreHeld();

    /** The way the fire is aimed (unit), from {@link #fiendfyreOrigin()}. */
    Vec3 fiendfyreAim();

    /** Where the serpents' formation is measured from (the wielder's eyes). */
    Vec3 fiendfyreOrigin();

    /** How far along the aim the serpents fly. */
    double fiendfyreReach();

    /** Whether the fire may burn {@code victim} (never the wielder, its own allies, or creative players). */
    boolean fiendfyreMayBurn(LivingEntity victim);
}
