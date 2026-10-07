package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.entity.boggart.RiddikulusTarget;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * Riddikulus: the Boggart charm. On a {@link RiddikulusTarget} (today only the Boggart) the target decides
 * what happens, from the caster's rank in this spell. On anything else the charm is a harmless joke: no
 * damage, a short Glowing, a ring of notes and a bell. There is nothing to rank up here on purpose; the
 * ranks only matter to the Boggart (see BoggartRules.hitsToBanish).
 */
public final class RiddikulusSpell {
    private static final int JOKE_GLOW_TICKS = 40;
    private static final int RING_NOTES = 16;
    private static final double RING_RADIUS = 0.9;

    private RiddikulusSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        if (target instanceof RiddikulusTarget boggart) {
            boggart.onRiddikulus(DisableSupport.casterRank(caster, SpellType.RIDDIKULUS), caster);
            return;
        }
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, JOKE_GLOW_TICKS, 0, false, false, true));
        double y = target.getY() + target.getBbHeight() * 0.6;
        for (int i = 0; i < RING_NOTES; i++) {
            double angle = (Math.PI * 2.0 * i) / RING_NOTES;
            double x = target.getX() + Math.cos(angle) * RING_RADIUS;
            double z = target.getZ() + Math.sin(angle) * RING_RADIUS;
            level.sendParticles(ParticleTypes.NOTE, x, y, z, 1, 0.0, 0.02, 0.0, 0.0);
        }
        // NOTE_BLOCK_* are Holder<SoundEvent> in 1.21; playSound wants the event itself.
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(),
                SoundSource.PLAYERS, 0.8f, 1.2f);
    }
}
