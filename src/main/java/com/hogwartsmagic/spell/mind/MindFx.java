package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.combat.CombatEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/** Particles and action-bar lines shared by the mind spells. Server side; no-ops on the client. */
public final class MindFx {
    private MindFx() {
    }

    public static void burst(LivingEntity target, ParticleOptions particle) {
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(particle, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    14, target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.02);
        }
    }

    /** A boss (or an immune target) shrugs the spell off: a puff of smoke and a line to the caster. */
    public static void resisted(LivingEntity target, @Nullable Entity caster) {
        burst(target, ParticleTypes.SMOKE);
        tell(caster, "mind.resisted", target.getDisplayName());
    }

    /** Action-bar line {@code gui.hogwartsmagic.<key>} to whoever is responsible for {@code who}, if a player. */
    public static void tell(@Nullable Entity who, String key, Object... args) {
        if (CombatEffects.responsible(who) instanceof Player player && !player.level().isClientSide()) {
            player.displayClientMessage(Component.translatable("gui." + ExampleMod.MODID + "." + key, args)
                    .withStyle(ChatFormatting.GREEN), true);
        }
    }
}
