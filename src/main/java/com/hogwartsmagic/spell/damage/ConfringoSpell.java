package com.hogwartsmagic.spell.damage;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.fx.Fx;
import com.hogwartsmagic.fx.FxKinds;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Confringo: the combat blast. A vanilla explosion with {@code ExplosionInteraction.NONE} and no fire,
 * so it never breaks or lights blocks (Bombarda breaks, Incendio lights). It sets the target alight;
 * from Adept the burning debris also ignites the caster's enemies nearby. At Master the blast deals
 * x1.5 to brittle targets: this hook runs at HIGH priority, before Phase 2's brittle hook multiplies
 * and consumes the effect, so the two bonuses stack.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ConfringoSpell {
    /** Set only while a Master Confringo explosion is resolving (explosions resolve synchronously). */
    private static float activeBrittleBonus = 1.0f;

    private ConfringoSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.CONFRINGO);
        target.igniteForSeconds(ConfringoRules.igniteSeconds(rank));
        detonate(level, target.position(), caster, target, rank);
    }

    /** A Confringo that struck a block: the same blast with no primary target. */
    public static void detonateAt(ServerLevel level, Vec3 at, @Nullable Entity caster) {
        detonate(level, at, caster, null, DisableSupport.casterRank(caster, SpellType.CONFRINGO));
    }

    private static void detonate(ServerLevel level, Vec3 at, @Nullable Entity caster, @Nullable LivingEntity primary, int rank) {
        boolean raining = level.isRainingAt(BlockPos.containing(at));
        float power = ConfringoRules.power(rank, raining);
        activeBrittleBonus = ConfringoRules.brittleBonus(rank);
        try {
            level.explode(caster, at.x, at.y, at.z, power, Level.ExplosionInteraction.NONE);
        } finally {
            activeBrittleBonus = 1.0f;
        }
        // The blast scorches the ground and walls it hits, and weighs on the view of whoever is near.
        Fx.send(level, FxKinds.MARK_SCORCH, at, 64.0, -1, power * 1.1f + 0.4f, 60.0f);
        Fx.shake(level, at, 0.25f + 0.2f * power, 14.0f, 10);
        double radius = ConfringoRules.splashIgniteRadius(rank);
        if (radius <= 0.0) {
            return;
        }
        int seconds = ConfringoRules.splashIgniteSeconds(rank);
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(at, radius * 2, radius * 2, radius * 2),
                e -> e != primary && e.position().distanceToSqr(at) <= radius * radius
                        && DamageSupport.isSplashTarget(caster, e, primary))) {
            nearby.igniteForSeconds(seconds);
            level.sendParticles(ParticleTypes.FLAME, nearby.getX(), nearby.getY() + nearby.getBbHeight() * 0.5, nearby.getZ(),
                    8, nearby.getBbWidth() * 0.3, nearby.getBbHeight() * 0.3, nearby.getBbWidth() * 0.3, 0.02);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (activeBrittleBonus <= 1.0f || !event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || !CombatEffects.has(target, ExampleMod.BRITTLE)) {
            return;
        }
        event.setAmount(event.getAmount() * activeBrittleBonus);
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    14, target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.2);
        }
    }
}
