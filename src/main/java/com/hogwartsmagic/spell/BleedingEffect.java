package com.hogwartsmagic.spell;

import com.hogwartsmagic.mixin.LivingEntityLastHurtAccessor;
import com.hogwartsmagic.spell.combat.BleedRules;
import com.hogwartsmagic.spell.combat.CombatDamage;
import com.hogwartsmagic.spell.combat.CombatEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

/**
 * One damage per second per stack (amplifier + 1, at most 3), credited to whoever applied it when known
 * (see CombatEffects.applyBleeding). The no-regeneration and half-healing
 * parts are in FoodDataBleedingMixin, PlayerBleedingMixin and CombatEffects.onLivingHeal.
 */
public class BleedingEffect extends MobEffect {
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.55f, 0.02f, 0.02f), 1.0f);

    public BleedingEffect() {
        super(MobEffectCategory.HARMFUL, 0x8A0303);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return BleedRules.isDamageTick(duration);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level() instanceof ServerLevel level) {
            // The bleeding damage type bypasses invulnerability so a tick always lands, but a bypassing
            // hit also re-arms the victim's i-frames (invulnerableTime = 20, lastHurt = amount). Put both
            // back so bleeding neither shields the victim from the next hit nor weakens it.
            int invulnerableTime = entity.invulnerableTime;
            float lastHurt = ((LivingEntityLastHurtAccessor) entity).hogwartsmagic$getLastHurt();
            Entity attacker = null;
            CompoundTag data = entity.getPersistentData();
            if (data.hasUUID(CombatEffects.BLEED_SOURCE_KEY)) {
                Entity found = level.getEntity(data.getUUID(CombatEffects.BLEED_SOURCE_KEY));
                if (found != null && found.isAlive()) {
                    attacker = found;
                }
            }
            entity.hurt(CombatDamage.bleeding(level, attacker), BleedRules.damagePerSecond(amplifier));
            entity.invulnerableTime = invulnerableTime;
            ((LivingEntityLastHurtAccessor) entity).hogwartsmagic$setLastHurt(lastHurt);
            level.sendParticles(BLOOD, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    6 + 3 * BleedRules.stacks(amplifier), entity.getBbWidth() * 0.3, entity.getBbHeight() * 0.25,
                    entity.getBbWidth() * 0.3, 0.0);
        }
        return true;
    }
}
