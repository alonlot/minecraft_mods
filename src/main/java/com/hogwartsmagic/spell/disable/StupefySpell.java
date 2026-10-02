package com.hogwartsmagic.spell.disable;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CrowdControl;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Stupefy: the fastest true stun in the mod and the interrupt button. Novice: 1.5s stun and the
 * target's channel or windup is broken (always, even when diminishing returns eat the stun). Adept and
 * Expert: longer, plus a small knockback. Master: the stun jumps to one more enemy within 5 blocks for
 * half the duration (and the beam is faster, see SpellPackets).
 */
public final class StupefySpell {
    private StupefySpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.STUPEFY);
        DisableSupport.interrupt(target);
        CrowdControl.apply(target, DisableSupport.holder(ExampleMod.STUNNED), StupefyRules.stunTicks(rank), caster);
        double knockback = StupefyRules.knockback(rank);
        if (knockback > 0.0 && caster != null) {
            target.knockback(knockback, caster.getX() - target.getX(), caster.getZ() - target.getZ());
            target.hurtMarked = true;
        }
        if (StupefyRules.chains(rank)) {
            chain(level, target, caster, rank);
        }
    }

    private static void chain(ServerLevel level, LivingEntity from, @Nullable Entity caster, int rank) {
        double radius = StupefyRules.CHAIN_RADIUS;
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(radius),
                e -> e.distanceToSqr(from) <= radius * radius && DisableSupport.isEnemyOf(caster, e, from));
        LivingEntity next = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            double d = candidate.distanceToSqr(from);
            if (d < best) {
                best = d;
                next = candidate;
            }
        }
        if (next == null) {
            return;
        }
        DisableSupport.interrupt(next);
        CrowdControl.apply(next, DisableSupport.holder(ExampleMod.STUNNED), StupefyRules.chainStunTicks(rank), caster);
        drawArc(level, from, next);
        level.playSound(null, next.getX(), next.getY(), next.getZ(), ModSounds.SPELL_STUPEFY.get(), SoundSource.PLAYERS, 0.7f, 1.4f);
    }

    /** A red spark line between the two targets, so the chain reads as one spell. */
    private static void drawArc(ServerLevel level, LivingEntity a, LivingEntity b) {
        Vec3 from = a.position().add(0.0, a.getBbHeight() * 0.6, 0.0);
        Vec3 to = b.position().add(0.0, b.getBbHeight() * 0.6, 0.0);
        int steps = Math.max(6, (int) (from.distanceTo(to) * 4));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
        level.sendParticles(ParticleTypes.FLASH, to.x, to.y, to.z, 1, 0.0, 0.0, 0.0, 0.0);
    }
}
