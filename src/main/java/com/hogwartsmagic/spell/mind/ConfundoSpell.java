package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Confundo: friendly fire. A confused mob attacks the nearest other creature (never the caster) and can't
 * pick its own targets until the confunded marker runs out. At Master the marker's amplifier is 1 and a
 * confused mob's hits confuse their victims for 3s (that confusion doesn't spread again). Players reel
 * with Nausea and Slowness. Bosses resist.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ConfundoSpell {
    static final String TAG_CASTER = ExampleMod.MODID + ":confundo_caster";
    private static Holder<MobEffect> confundedHolder;

    private ConfundoSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (target.level().isClientSide()) {
            return;
        }
        DisableSupport.interrupt(target);
        if (CombatTags.isBoss(target)) {
            MindFx.resisted(target, caster);
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.CONFUNDO);
        Entity owner = CombatEffects.responsible(caster);
        if (target instanceof Player) {
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ConfundoRules.playerNauseaTicks(rank), 0), owner);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ConfundoRules.playerSlownessTicks(rank), 0), owner);
            MindFx.burst(target, ParticleTypes.NAUTILUS);
            return;
        }
        if (target instanceof Mob mob) {
            confuse(mob, owner != null ? owner.getUUID() : null, ConfundoRules.confuseTicks(rank), ConfundoRules.contagious(rank));
        }
    }

    public static boolean isConfused(LivingEntity entity) {
        Holder<MobEffect> holder = holder();
        return holder != null && entity.hasEffect(holder);
    }

    @Nullable
    private static Holder<MobEffect> holder() {
        if (confundedHolder == null) {
            confundedHolder = ExampleMod.CONFUNDED.getHolder().orElse(null);
        }
        return confundedHolder;
    }

    private static void confuse(Mob mob, @Nullable UUID casterId, int ticks, boolean contagious) {
        Holder<MobEffect> holder = holder();
        if (holder == null) {
            return;
        }
        mob.addEffect(new MobEffectInstance(holder, ticks, ConfundoRules.amplifierFor(contagious), false, false, false));
        if (casterId != null) {
            mob.getPersistentData().putUUID(TAG_CASTER, casterId);
        } else {
            mob.getPersistentData().remove(TAG_CASTER);
        }
        MindFx.burst(mob, ParticleTypes.NAUTILUS);
        retarget(mob);
    }

    /** The nearest living thing within 12 blocks that isn't the mob, the caster, a dummy or a creative player. */
    private static void retarget(Mob mob) {
        UUID casterId = mob.getPersistentData().hasUUID(TAG_CASTER) ? mob.getPersistentData().getUUID(TAG_CASTER) : null;
        AABB box = mob.getBoundingBox().inflate(ConfundoRules.SEARCH_RADIUS);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity candidate : mob.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != mob && e.isAlive())) {
            if (candidate instanceof ArmorStand || candidate.isSpectator()
                    || (candidate instanceof Player player && player.isCreative())
                    || (casterId != null && casterId.equals(candidate.getUUID()))) {
                continue;
            }
            double dist = candidate.distanceToSqr(mob);
            if (dist < bestDist) {
                best = candidate;
                bestDist = dist;
            }
        }
        MindControl.setTargetBypassing(mob, best);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide() || !isConfused(mob)) {
            return;
        }
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || mob.tickCount % ConfundoRules.RETARGET_INTERVAL == 0) {
            retarget(mob);
        }
    }

    /** A confused mob picks no targets of its own; only retarget() (through the bypass) chooses. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() == null || MindControl.isBypassingTargetRules()) {
            return;
        }
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide() && isConfused(mob)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance != null && instance.getEffect().value() == ExampleMod.CONFUNDED.get()) {
            clear(event.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (event.isCanceled()) {
            return;
        }
        if (event.getEffect() == ExampleMod.CONFUNDED.get()) {
            clear(event.getEntity());
        }
    }

    private static void clear(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        entity.getPersistentData().remove(TAG_CASTER);
        if (entity instanceof Mob mob) {
            MindControl.setTargetBypassing(mob, null);
        }
    }

    /** Master contagion: a contagious mob's hit on another mob passes the confusion on for 3s. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (!(event.getEntity() instanceof Mob victim) || victim.level().isClientSide()
                || !(event.getSource().getEntity() instanceof Mob attacker) || attacker == victim) {
            return;
        }
        Holder<MobEffect> holder = holder();
        MobEffectInstance confusion = holder == null ? null : attacker.getEffect(holder);
        if (confusion == null) {
            return;
        }
        boolean contagious = confusion.getAmplifier() >= ConfundoRules.AMP_CONTAGIOUS;
        if (!ConfundoRules.spreads(contagious, CombatTags.isBoss(victim), isConfused(victim))) {
            return;
        }
        UUID casterId = attacker.getPersistentData().hasUUID(TAG_CASTER) ? attacker.getPersistentData().getUUID(TAG_CASTER) : null;
        confuse(victim, casterId, ConfundoRules.CONTAGION_TICKS, false);
    }
}
