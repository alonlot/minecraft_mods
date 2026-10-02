package com.hogwartsmagic.spell.disable;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CrowdControl;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Petrificus Totalus: the target goes rigid and topples over (a stun plus the petrified marker, which
 * watching clients render rotated 90 degrees). Any damage ends it at Novice; from Adept, hits under 4
 * damage don't. At Master the hit that breaks it deals +50%. Gravity still applies, so a petrified mob
 * in the air falls and takes normal fall damage.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PetrificusSpell {
    private PetrificusSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.PETRIFICUS_TOTALUS);
        int ticks = CrowdControl.apply(target, DisableSupport.holder(ExampleMod.STUNNED),
                PetrificusRules.durationTicks(rank), caster);
        if (ticks <= 0) {
            return;
        }
        DisableSupport.interrupt(target);
        DisableSupport.addHiddenMarker(target, ExampleMod.PETRIFIED, ticks, rank - 1, caster);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.STONE_PLACE, SoundSource.NEUTRAL, 1.0f, 0.6f);
    }

    /** LOW so it sees the amount after other multipliers (brittle); before armour, like brittle. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        Holder<MobEffect> petrified = DisableSupport.holder(ExampleMod.PETRIFIED);
        MobEffectInstance instance = target.getEffect(petrified);
        if (instance == null) {
            return;
        }
        int rank = instance.getAmplifier() + 1;
        if (!PetrificusRules.breaks(rank, event.getAmount())) {
            return;
        }
        event.setAmount(event.getAmount() * PetrificusRules.shatterMultiplier(rank));
        target.removeEffect(petrified);
        target.removeEffect(DisableSupport.holder(ExampleMod.STUNNED));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 24,
                target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.1);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.STONE_BREAK, SoundSource.NEUTRAL, 1.0f, 0.8f);
    }
}
