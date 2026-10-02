package com.hogwartsmagic.spell.damage;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.BleedRules;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * Sectumsempra: invisible blades and bleeding that shuts off natural healing (Phase 2's bleeding
 * effect). The stacks a cast can build are capped by rank (1/2/3/3). At Master, hitting a target that
 * is already bleeding tears every wound open: +2 damage per existing stack, in the same hurt call as
 * the cut (a second call would be eaten by invulnerability frames), and the bleed is refreshed.
 *
 * <p>The rank cap on stacks is computed here (SectumsempraRules.nextStacks) and passed to Phase 2's
 * CombatEffects.applyBleeding, which also credits the caster for a bleed-out kill.
 */
public final class SectumsempraSpell {
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.55f, 0.02f, 0.02f), 1.4f);

    private SectumsempraSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.SECTUMSEMPRA);
        Holder<MobEffect> bleeding = DisableSupport.holder(ExampleMod.BLEEDING);
        MobEffectInstance current = target.getEffect(bleeding);
        int stacksBefore = current == null ? 0 : BleedRules.stacks(current.getAmplifier());
        float burst = SectumsempraRules.burstDamage(rank, stacksBefore);

        target.hurt(target.damageSources().magic(),
                DamageSupport.scaled(caster, SectumsempraRules.damage(rank) + burst));
        if (!target.isAlive()) {
            return;
        }
        int stacksAfter = SectumsempraRules.nextStacks(rank, stacksBefore);
        // Phase 2's applyBleeding keeps the longer duration, caps the stacks and records the caster for kill credit.
        CombatEffects.applyBleeding(target, SectumsempraRules.BLEED_TICKS, stacksAfter, caster);

        if (burst > 0f) {
            level.sendParticles(BLOOD, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    12 + 8 * stacksBefore, target.getBbWidth() * 0.45, target.getBbHeight() * 0.35,
                    target.getBbWidth() * 0.45, 0.05);
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    ModSounds.SPELL_SECTUMSEMPRA.get(), SoundSource.PLAYERS, 0.9f, 0.7f);
        }
    }
}
