package com.hogwartsmagic.spell.combat;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * The single door for hard crowd control (stun, petrify, bind, hang, levitate, freeze). Every later
 * phase calls {@link #apply} (or {@link #scaleDuration} for crowd control that isn't a MobEffect), so
 * diminishing returns, the boss rule and the PvP rule can't be forgotten. Server side only: on the
 * client every method returns 0 and records nothing.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CrowdControl {
    private static final DiminishingReturns DIMINISHING = new DiminishingReturns();

    private CrowdControl() {
    }

    /** {@link #apply(LivingEntity, Holder, int, int, Entity, boolean)} at amplifier 0, visible. */
    public static int apply(LivingEntity target, Holder<MobEffect> effect, int baseTicks, @Nullable Entity source) {
        return apply(target, effect, baseTicks, 0, source, true);
    }

    /** {@link #apply(LivingEntity, Holder, int, int, Entity, boolean)} visible. */
    public static int apply(LivingEntity target, Holder<MobEffect> effect, int baseTicks, int amplifier, @Nullable Entity source) {
        return apply(target, effect, baseTicks, amplifier, source, true);
    }

    /**
     * Apply a hard crowd-control effect for {@code baseTicks} scaled by the rules.
     *
     * <p>For a hidden marker effect (no particles, no icon) that should still count against diminishing
     * returns only when it lands, pass {@code visible = false} here rather than calling
     * {@link #scaleDuration} and {@code addEffect} yourself.
     *
     * @param source the caster (or its projectile/beam); decides the PvP rule
     * @param visible whether the effect shows particles and an icon (false for hidden markers)
     * @return the ticks applied; 0 if nothing landed (diminishing returns, an immune target, a dead
     *         target, the client side, or a longer copy of the effect already running)
     */
    public static int apply(LivingEntity target, Holder<MobEffect> effect, int baseTicks, int amplifier,
                            @Nullable Entity source, boolean visible) {
        if (target.level().isClientSide() || !target.isAlive() || baseTicks <= 0) {
            return 0;
        }
        if (immuneByMode(target)) {
            return 0;
        }
        MobEffectInstance probe = new MobEffectInstance(effect, baseTicks, Math.max(0, amplifier), false, visible, visible);
        if (!target.canBeAffected(probe)) {
            return 0; // immune targets don't burn a diminishing-returns step
        }
        long now = target.level().getGameTime();
        int ticks = scaledNow(target, baseTicks, source, now);
        if (ticks <= 0) {
            return 0;
        }
        boolean added = target.addEffect(new MobEffectInstance(effect, ticks, Math.max(0, amplifier), false, visible, visible),
                CombatEffects.responsible(source));
        if (!added) {
            return 0; // a same-or-stronger, longer copy is already running: no step burned
        }
        DIMINISHING.commit(target.getUUID(), now);
        return ticks;
    }

    /** Scaled duration for this target right now. Consumes one diminishing-returns step when it is above 0. */
    public static int scaleDuration(LivingEntity target, int baseTicks, @Nullable Entity source) {
        if (target.level().isClientSide() || !target.isAlive() || baseTicks <= 0 || immuneByMode(target)) {
            return 0;
        }
        long now = target.level().getGameTime();
        int ticks = scaledNow(target, baseTicks, source, now);
        if (ticks > 0) {
            DIMINISHING.commit(target.getUUID(), now);
        }
        return ticks;
    }

    /** Peek and scale without recording anything. */
    private static int scaledNow(LivingEntity target, int baseTicks, @Nullable Entity source, long now) {
        double factor = DIMINISHING.peek(target.getUUID(), now);
        return CrowdControlMath.scaledTicks(baseTicks, factor, CombatTags.isBoss(target),
                isPvp(target, source), Config.getPvpCrowdControlMultiplier());
    }

    private static boolean immuneByMode(LivingEntity target) {
        return target instanceof Player p && (p.isCreative() || p.isSpectator());
    }

    /** A player's crowd control landing on another player. NPC casters and self-casts don't count. */
    public static boolean isPvp(LivingEntity target, @Nullable Entity source) {
        Entity responsible = CombatEffects.responsible(source);
        return target instanceof Player && responsible instanceof Player && responsible != target;
    }

    /** Forget a target's diminishing-returns chain (debug command, tests). */
    public static void resetDiminishing(LivingEntity target) {
        DIMINISHING.clear(target.getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DIMINISHING.clearAll();
    }
}
