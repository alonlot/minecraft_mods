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
 * backlash that shrinks with rank (none at Master); NPC casters keep today's flat backlash. No Protego of
 * any kind (ranked, legacy, player or NPC) blocks it: the hurt call runs inside {@link #isLanding()}.
 * The masteryEligible guard is future-proofing: Avada can not be reflected today (ProtegoSpell.onBeamHit
 * returns NOT_SHIELDED for it), but a reflected beam would be the defender's, so no backlash. There is
 * also no backlash when the hit never landed.
 */
public final class AvadaSpell {
    private static final ThreadLocal<Boolean> LANDING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private AvadaSpell() {
    }

    /** True on the server thread while an Avada hit is being dealt: shields let it through. */
    public static boolean isLanding() {
        return LANDING.get();
    }

    public static void apply(LivingEntity target, @Nullable Entity caster, boolean masteryEligible) {
        if (target.level().isClientSide()) {
            return;
        }
        Entity owner = CombatEffects.responsible(caster);
        DamageSource source = DamageSupport.magicFrom(target, caster);
        boolean landed;
        LANDING.set(Boolean.TRUE);
        try {
            landed = CombatTags.isBoss(target)
                    ? target.hurt(source, AvadaRules.bossDamage(target.getMaxHealth()))
                    : target.hurt(source, Float.MAX_VALUE);
        } finally {
            LANDING.set(Boolean.FALSE);
        }
        // A reflected beam (masteryEligible false) is the defender's: no backlash for the reflector, and none
        // when the hit never landed (creative or vetoed target).
        if (!landed || !masteryEligible) {
            return;
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
