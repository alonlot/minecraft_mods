package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.WizardEntity;
import com.hogwartsmagic.event.ModEvents;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Obliviate: the escape spell. A mob drops its target and will not pick the caster again until the time
 * runs out or the caster hurts it; a player is silenced. At Master the mob also forgets its raid (vanilla
 * raids and Death Eater raids) and its home. Bosses resist. The forgetting lives in a server-only map keyed
 * by mob UUID (no persistent data is touched on ordinary mobs); losing it on a chunk reload or restart is
 * accepted, since it lasts 30 seconds at most.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ObliviateSpell {
    record Forget(UUID casterId, long until) {
    }

    private static final Map<UUID, Forget> FORGOTTEN = new HashMap<>();

    private ObliviateSpell() {
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
        int rank = DisableSupport.casterRank(caster, SpellType.OBLIVIATE);
        if (target instanceof Player) {
            CombatEffects.applySilenced(target, ObliviateRules.silenceTicks(rank), caster);
            MindFx.burst(target, ParticleTypes.ENCHANT);
            return;
        }
        if (!(target instanceof Mob mob)) {
            return;
        }
        Entity owner = CombatEffects.responsible(caster);
        if (owner != null) {
            long now = mob.level().getGameTime();
            FORGOTTEN.values().removeIf(f -> !ObliviateRules.stillForgotten(now, f.until()));
            FORGOTTEN.put(mob.getUUID(), new Forget(owner.getUUID(), now + ObliviateRules.ignoreTicks(rank)));
        }
        MindControl.setTargetBypassing(mob, null);
        mob.setLastHurtByMob(null);
        if (mob instanceof NeutralMob neutral) {
            neutral.stopBeingAngry();
        }
        mob.getNavigation().stop();
        if (ObliviateRules.memoryCharm(rank)) {
            memoryCharm(mob);
        }
        MindFx.burst(mob, ParticleTypes.ENCHANT);
    }

    /** Master: out of the raid (and no rejoining), no longer a raid leader, no home to go back to. */
    private static void memoryCharm(Mob mob) {
        if (mob instanceof Raider raider) {
            Raid raid = raider.getCurrentRaid();
            if (raid != null) {
                raid.removeFromRaid(raider, true);
            }
            raider.setCanJoinRaid(false);
        }
        if (mob instanceof WizardEntity wizard && mob.level() instanceof ServerLevel level) {
            ModEvents.removeFromWizardRaid(level, wizard);
            if (wizard.isRaidLeader()) {
                wizard.setRaidLeader(false);
            }
        }
        mob.clearRestriction();
    }

    /** A spell from the forgotten caster ("unless you hit it"). Spell chip damage carries no attacker, so it comes here. */
    static void onHurtBySpell(LivingEntity target, @Nullable Entity caster) {
        if (FORGOTTEN.isEmpty()) {
            return;
        }
        Entity owner = CombatEffects.responsible(caster);
        if (owner != null) {
            remember(target, owner);
        }
    }

    private static void remember(LivingEntity mob, Entity attacker) {
        Forget forget = FORGOTTEN.get(mob.getUUID());
        if (forget != null && forget.casterId().equals(attacker.getUUID())) {
            FORGOTTEN.remove(mob.getUUID());
        }
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (FORGOTTEN.isEmpty() || event.isCanceled() || MindControl.isBypassingTargetRules()) {
            return;
        }
        LivingEntity newTarget = event.getNewTarget();
        if (newTarget == null || !(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) {
            return;
        }
        Forget forget = FORGOTTEN.get(mob.getUUID());
        if (forget == null) {
            return;
        }
        if (!ObliviateRules.stillForgotten(mob.level().getGameTime(), forget.until())) {
            FORGOTTEN.remove(mob.getUUID());
            return;
        }
        if (newTarget.getUUID().equals(forget.casterId())) {
            event.setCanceled(true);
        }
    }

    /** Melee, arrows and anything else that names its attacker. A cancelled hit does not count. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (FORGOTTEN.isEmpty() || event.isCanceled()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        Entity attacker = CombatEffects.responsible(event.getSource().getEntity());
        if (attacker != null) {
            remember(victim, attacker);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        FORGOTTEN.clear();
    }
}
