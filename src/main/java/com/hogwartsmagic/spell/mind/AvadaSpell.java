package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.damage.DamageSupport;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * The Killing Curse landing. Non-bosses die; bosses lose a fifth of their max health. The caster pays a
 * backlash that shrinks with rank (none at Master); NPC casters keep today's flat backlash.
 */
public final class AvadaSpell {
    private AvadaSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (target.level().isClientSide()) {
            return;
        }
        Entity owner = CombatEffects.responsible(caster);
        DamageSource source = DamageSupport.magicFrom(target, caster);
        if (CombatTags.isBoss(target)) {
            target.hurt(source, AvadaRules.bossDamage(target.getMaxHealth()));
        } else {
            target.hurt(source, Float.MAX_VALUE);
        }
        if (owner instanceof ServerPlayer player) {
            float backlash = AvadaRules.backlash(SpellMasteryData.getRank(player, SpellType.AVADA_KEDAVRA));
            if (backlash > 0.0f) {
                player.hurt(player.damageSources().magic(), backlash);
            }
        } else if (owner instanceof LivingEntity npc && owner != target) {
            npc.hurt(npc.damageSources().magic(), DamageSupport.scaled(npc, AvadaRules.NPC_BACKLASH));
        }
    }
}
