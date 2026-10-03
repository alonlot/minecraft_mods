package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.spell.SpellType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Set;

/**
 * Called at the top of SpellType.applyHitEffect for every beam that lands on a living target. A beam's
 * chip damage is anonymous magic damage, so this is the one place that knows a spell from X hit Y.
 */
public final class MindHooks {
    /** Helpful spells, and the two that set up control: they don't count as "hitting" anyone. */
    private static final Set<SpellType> NOT_HOSTILE = EnumSet.of(
            SpellType.HEAL, SpellType.PROTEGO, SpellType.LUMOS, SpellType.TENEBRIS_FLIGHT,
            SpellType.REVELIO, SpellType.OBLIVIATE, SpellType.IMPERIO);

    private MindHooks() {
    }

    public static void beforeSpellHit(SpellType spell, LivingEntity target, @Nullable Entity caster) {
        if (target.level().isClientSide() || !isHostile(spell)) {
            return;
        }
        ObliviateSpell.onHurtBySpell(target, caster);
        ImperioThralls.noteAttack(caster, target);
    }

    static boolean isHostile(SpellType spell) {
        return !NOT_HOSTILE.contains(spell);
    }
}
