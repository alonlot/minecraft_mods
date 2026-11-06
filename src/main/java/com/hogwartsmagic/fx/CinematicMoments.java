package com.hogwartsmagic.fx;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.AcromantulaEntity;
import com.hogwartsmagic.entity.BasiliskEntity;
import com.hogwartsmagic.entity.GiantEntity;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.hogwartsmagic.entity.MemoryHorntailEntity;
import com.hogwartsmagic.entity.TrollEntity;
import com.hogwartsmagic.entity.VoldemortEntity;
import com.hogwartsmagic.entity.WizardEntity;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.mind.AvadaSpell;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Decides which deaths are cinematic moments and tells the nearby players ({@link FxKinds#MOMENT}); what the player
 * sees (freeze-frame, flash, colour drain, letterbox, slow motion, shake) is the client's business.
 * <ul>
 * <li>A boss dies (the Horntail, the Ironbelly, the Basilisk, a Giant, an Acromantula, a Troll, Voldemort, anything
 * in the {@code bosses} tag): weight 1 to players within 64 blocks.</li>
 * <li>A Killing Curse kills anything: weight 1, green-white, to the caster and everyone within 32 blocks.</li>
 * <li>A player's spell kills a Death Eater: weight 0.5 to the killer alone.</li>
 * </ul>
 * Common code; it names no client class and is safe on a dedicated server.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CinematicMoments {

    /** How far a boss's fall is felt. */
    private static final double BOSS_RANGE = 64.0;
    /** How far a Killing Curse's kill is felt, besides the caster. */
    private static final double AVADA_RANGE = 32.0;
    /** The Killing Curse's flash: green-white. */
    private static final float[] AVADA_COLOUR = {0.72f, 1.0f, 0.78f};
    /** A second moment of the same kind within this many ticks (a pack of Acromantulas dying) is not sent. */
    private static final long BOSS_COOLDOWN_TICKS = 60L;

    private static long lastBossTick = Long.MIN_VALUE;

    private CinematicMoments() {
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || victim instanceof ServerPlayer) {
            return;
        }
        DamageSource source = event.getSource();
        Entity owner = CombatEffects.responsible(source.getEntity());

        if (AvadaSpell.isLanding(victim)) {
            // The Killing Curse: to the caster wherever they are, and to everyone close.
            Fx.send(level, FxKinds.MOMENT, victim.getX(), victim.getY(), victim.getZ(), AVADA_RANGE, victim.getId(),
                    1.0f, AVADA_COLOUR[0], AVADA_COLOUR[1], AVADA_COLOUR[2]);
            if (owner instanceof ServerPlayer caster && caster.level() == level
                    && caster.distanceToSqr(victim) > AVADA_RANGE * AVADA_RANGE) {
                Fx.sendTo(caster, FxKinds.MOMENT, victim.getX(), victim.getY(), victim.getZ(), victim.getId(),
                        1.0f, AVADA_COLOUR[0], AVADA_COLOUR[1], AVADA_COLOUR[2]);
            }
            return;
        }

        if (isBoss(victim)) {
            long now = level.getGameTime();
            if (now - lastBossTick < BOSS_COOLDOWN_TICKS && now >= lastBossTick) {
                return;
            }
            lastBossTick = now;
            Fx.sendAbout(victim, FxKinds.MOMENT, BOSS_RANGE, 1.0f);
            return;
        }

        if (victim instanceof WizardEntity && owner instanceof ServerPlayer killer
                && (source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.MAGIC))) {
            Fx.sendTo(killer, FxKinds.MOMENT, victim.getX(), victim.getY(), victim.getZ(), victim.getId(), 0.5f);
        }
    }

    private static boolean isBoss(LivingEntity victim) {
        if (victim instanceof MemoryHorntailEntity) {
            return false; // a Pensieve memory runs its own show
        }
        return CombatTags.isBoss(victim)
                || victim instanceof HungarianHorntailEntity
                || victim instanceof BasiliskEntity
                || victim instanceof GiantEntity
                || victim instanceof AcromantulaEntity
                || victim instanceof TrollEntity
                || victim instanceof VoldemortEntity;
    }
}
