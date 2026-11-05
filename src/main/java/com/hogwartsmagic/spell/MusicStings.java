package com.hogwartsmagic.spell;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.BasiliskEntity;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.hogwartsmagic.entity.VoldemortEntity;
import com.hogwartsmagic.entity.WizardEntity;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.combat.CombatTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Short original orchestral stings at the moments a fight turns: a duel sting when a Death Eater first turns on
 * you, an entrance for each boss (the Dark Lord, the Basilisk, the Horntail) when it first comes for you, and a
 * victory when a boss falls near you. Each has a cooldown per player so a long fight isn't scored over and over.
 * The client plays them under the Music slider.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MusicStings {
    public static final byte DUEL = 0;
    public static final byte BOSS_DARK = 1;
    public static final byte BOSS_SERPENT = 2;
    public static final byte BOSS_DRAGON = 3;
    public static final byte VICTORY = 4;
    /** Two duellists' beams lock (Priori Incantatem). */
    public static final byte PRIORI = 5;

    private static final long DUEL_COOLDOWN = 20L * 90L;
    private static final long BOSS_COOLDOWN = 20L * 240L;
    private static final double VICTORY_RANGE = 48.0;

    /** Per player, per sting: the game time it may next play. */
    private static final Map<UUID, long[]> NEXT = new HashMap<>();

    private MusicStings() {
    }

    /** Last, so a disguise, petrification, a thrall or Confundo that turns the mob away has already had its say. */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity mob = event.getEntity();
        // The event fires before Mob.setTarget assigns it (and starts the original equal to the new target),
        // so "already after this player" is the mob's current target.
        if (!(event.getNewTarget() instanceof ServerPlayer player) || mob.level().isClientSide()
                || mob instanceof net.minecraft.world.entity.Mob m && m.getTarget() == player) {
            return;
        }
        byte sting;
        if (mob instanceof VoldemortEntity) {
            sting = BOSS_DARK;
        } else if (mob instanceof BasiliskEntity) {
            sting = BOSS_SERPENT;
        } else if (mob instanceof HungarianHorntailEntity) {
            sting = BOSS_DRAGON;
        } else if (mob instanceof WizardEntity) {
            sting = DUEL;
        } else {
            return;
        }
        play(player, sting, sting == DUEL ? DUEL_COOLDOWN : BOSS_COOLDOWN);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || !CombatTags.isBoss(dead)) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceTo(dead) < VICTORY_RANGE) {
                play(player, VICTORY, 0L);
            }
        }
    }

    /** A Priori Incantatem lock begins: both duellists get its sting (PrioriIncantatem). */
    public static void priori(LivingEntity duellist) {
        if (duellist instanceof ServerPlayer player) {
            play(player, PRIORI, 20L * 30L);
        }
    }

    private static void play(ServerPlayer player, byte sting, long cooldown) {
        long now = player.level().getGameTime();
        long[] next = NEXT.computeIfAbsent(player.getUUID(), id -> new long[6]);
        if (now < next[sting]) {
            return;
        }
        next[sting] = now + cooldown;
        SpellPackets.CHANNEL.send(new SpellPackets.MusicStingPacket(sting), PacketDistributor.PLAYER.with(player));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        NEXT.remove(event.getEntity().getUUID());
    }
}
