package com.hogwartsmagic.duel;

import com.hogwartsmagic.entity.StudentEntity;
import com.hogwartsmagic.entity.WizardEntity;
import com.hogwartsmagic.item.MagicBookItem;
import com.hogwartsmagic.spell.SpellLootRarity;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side wizard-duel referee. A duel is a formal 1v1 between a player and a wand-wielding
 * NPC (Student or Wizard/Death Eater), started by sneak-right-clicking the NPC with a wand.
 *
 * <p>Flow: BOW (2s, both stand down) → COUNTDOWN (3‑2‑1 titles) → FIGHT. The player wins by
 * disarming the opponent (Expelliarmus) or forcing a yield below 25% health; the player loses
 * by being disarmed or dropping below 2 hearts. Attacking during the bow/countdown voids the
 * duel — dueling has etiquette.
 *
 * <p>All state lives here, keyed by player UUID, and is ticked from
 * {@link com.hogwartsmagic.event.ModEvents#onPlayerTick}. Nothing is persisted: a duel simply
 * dissolves on relog/dimension change, which is the safe default. Everything in this class runs
 * on the server; no client-only classes are referenced.
 */
public final class DuelManager {

    // Phases as int constants — mirrors StudentEntity's house ids; nested enums have caused
    // class-load trouble in this dev env before.
    private static final int PHASE_BOW = 0;
    private static final int PHASE_COUNTDOWN = 1;
    private static final int PHASE_FIGHT = 2;

    private static final int BOW_TICKS = 40;
    private static final int COUNTDOWN_TICKS = 60;      // 3 × 20 ticks
    private static final int FIGHT_TIMEOUT_TICKS = 20 * 120;
    private static final double MAX_DUEL_DISTANCE_SQ = 32.0 * 32.0;
    private static final float NPC_YIELD_FRACTION = 0.25f;
    private static final float PLAYER_YIELD_HEALTH = 4.0f;
    private static final long NPC_REMATCH_COOLDOWN_TICKS = 600;

    /** Set while an NPC is in a duel; StudentEntity's panic goal checks it so students stand and fight. */
    public static final String TAG_DUELING = "hogwartsmagic:dueling";
    private static final String TAG_DUEL_COOLDOWN_UNTIL = "hogwartsmagic:duel_cooldown_until";

    private static final Map<UUID, Duel> DUELS = new HashMap<>();

    private DuelManager() {}

    private static final class Duel {
        final Mob npc;
        int phase = PHASE_BOW;
        int ticks = 0;
        int lastAnnouncedSecond = -1;

        Duel(Mob npc) {
            this.npc = npc;
        }
    }

    // ---- starting ------------------------------------------------------------------

    /** Sneak-right-click entry point, called from StudentEntity/WizardEntity mobInteract (server side). */
    public static void requestDuel(ServerPlayer player, Mob npc) {
        if (DUELS.containsKey(player.getUUID())) {
            player.displayClientMessage(Component.translatable("hogwartsmagic.duel.already_dueling"), true);
            return;
        }
        if (isNpcDueling(npc)) {
            player.displayClientMessage(Component.translatable("hogwartsmagic.duel.npc_busy", npc.getDisplayName()), true);
            return;
        }
        long now = npc.level().getGameTime();
        if (npc.getPersistentData().getLong(TAG_DUEL_COOLDOWN_UNTIL) > now) {
            player.displayClientMessage(Component.translatable("hogwartsmagic.duel.npc_tired", npc.getDisplayName()), true);
            return;
        }
        if (npc instanceof com.hogwartsmagic.entity.DisarmableWandUser du && du.isDisarmed()) {
            player.displayClientMessage(Component.translatable("hogwartsmagic.duel.npc_unarmed", npc.getDisplayName()), true);
            return;
        }

        DUELS.put(player.getUUID(), new Duel(npc));
        npc.getPersistentData().putBoolean(TAG_DUELING, true);
        npc.setTarget(null);
        npc.getNavigation().stop();

        player.displayClientMessage(Component.translatable("hogwartsmagic.duel.challenge_accepted", npc.getDisplayName()), true);
        npc.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 1.0f, 0.8f);
    }

    private static boolean isNpcDueling(Mob npc) {
        for (Duel duel : DUELS.values()) {
            if (duel.npc == npc) return true;
        }
        return false;
    }

    // ---- ticking -------------------------------------------------------------------

    /** Driven once per server tick per player from ModEvents.onPlayerTick. */
    public static void tick(ServerPlayer player) {
        Duel duel = DUELS.get(player.getUUID());
        if (duel == null) return;

        Mob npc = duel.npc;
        if (!npc.isAlive() || npc.isRemoved() || !player.isAlive()) {
            cancel(player, duel, "hogwartsmagic.duel.cancelled");
            return;
        }
        if (player.level() != npc.level() || player.distanceToSqr(npc) > MAX_DUEL_DISTANCE_SQ) {
            cancel(player, duel, "hogwartsmagic.duel.too_far");
            return;
        }

        duel.ticks++;
        if (duel.phase == PHASE_BOW) {
            tickBow(player, duel);
        } else if (duel.phase == PHASE_COUNTDOWN) {
            tickCountdown(player, duel);
        } else {
            tickFight(player, duel);
        }
    }

    private static void tickBow(ServerPlayer player, Duel duel) {
        Mob npc = duel.npc;
        holdNpcStill(player, npc);
        if (brokeEtiquette(player, duel)) return;

        if (duel.ticks == 1) {
            player.displayClientMessage(Component.translatable("hogwartsmagic.duel.bow"), true);
        }
        if (duel.ticks == BOW_TICKS / 2) {
            // The NPC "bows": dips its gaze to the ground and swings.
            npc.getLookControl().setLookAt(player.getX(), player.getY() - 1.5, player.getZ());
            npc.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            npc.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                    SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.NEUTRAL, 1.0f, 0.7f);
        }
        if (duel.ticks >= BOW_TICKS) {
            duel.phase = PHASE_COUNTDOWN;
            duel.ticks = 0;
            duel.lastAnnouncedSecond = -1;
        }
    }

    private static void tickCountdown(ServerPlayer player, Duel duel) {
        Mob npc = duel.npc;
        holdNpcStill(player, npc);
        if (brokeEtiquette(player, duel)) return;

        int second = 3 - duel.ticks / 20; // 3, 2, 1
        if (second != duel.lastAnnouncedSecond && second >= 1) {
            duel.lastAnnouncedSecond = second;
            sendTitle(player, Component.literal("§e" + second), null, 0, 15, 5);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0f, 0.8f + (3 - second) * 0.2f);
        }
        if (duel.ticks >= COUNTDOWN_TICKS) {
            duel.phase = PHASE_FIGHT;
            duel.ticks = 0;
            sendTitle(player, Component.translatable("hogwartsmagic.duel.fight"), null, 0, 20, 10);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.2f, 1.0f);
            npc.setTarget(player);
        }
    }

    private static void tickFight(ServerPlayer player, Duel duel) {
        Mob npc = duel.npc;

        // Keep the NPC locked onto its duel opponent — students in particular have no
        // player-targeting goal, so vanilla target forgetting must be undone every tick.
        if (npc.getTarget() != player) {
            npc.setTarget(player);
        }

        if (npc instanceof com.hogwartsmagic.entity.DisarmableWandUser du && du.isDisarmed()) {
            // Backup for the SpellType hook: however the wand left their hand, it's a win.
            win(player, duel, "hogwartsmagic.duel.win_disarm");
            return;
        }
        if (npc.getHealth() <= npc.getMaxHealth() * NPC_YIELD_FRACTION) {
            win(player, duel, "hogwartsmagic.duel.win_yield");
            return;
        }
        if (player.getHealth() <= PLAYER_YIELD_HEALTH) {
            lose(player, duel, "hogwartsmagic.duel.lose_yield");
            return;
        }
        if (duel.ticks >= FIGHT_TIMEOUT_TICKS) {
            cancel(player, duel, "hogwartsmagic.duel.draw");
            return;
        }

        if (duel.ticks % 20 == 0) {
            int pct = (int) (npc.getHealth() * 100 / npc.getMaxHealth());
            player.displayClientMessage(
                    Component.translatable("hogwartsmagic.duel.opponent_health", npc.getDisplayName(), pct), true);
        }
    }

    // ---- outcome hooks ---------------------------------------------------------------

    /**
     * Called from SpellType's EXPELLIARMUS branch whenever anything is disarmed.
     * Resolves the duel if the disarmed entity is a dueling player or a dueling NPC.
     */
    public static void onDisarmed(LivingEntity target) {
        if (target.level().isClientSide) return;

        if (target instanceof ServerPlayer player) {
            Duel duel = DUELS.get(player.getUUID());
            if (duel != null && duel.phase == PHASE_FIGHT) {
                lose(player, duel, "hogwartsmagic.duel.lose_disarm");
            }
            return;
        }
        if (target instanceof Mob npc) {
            Iterator<Map.Entry<UUID, Duel>> it = DUELS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Duel> entry = it.next();
                Duel duel = entry.getValue();
                if (duel.npc == npc && duel.phase == PHASE_FIGHT
                        && npc.level() instanceof ServerLevel sl
                        && sl.getPlayerByUUID(entry.getKey()) instanceof ServerPlayer player) {
                    win(player, duel, "hogwartsmagic.duel.win_disarm");
                    return;
                }
            }
        }
    }

    private static void win(ServerPlayer player, Duel duel, String messageKey) {
        endDuel(player, duel);
        Mob npc = duel.npc;

        sendTitle(player, Component.translatable("hogwartsmagic.duel.victory"),
                Component.translatable(messageKey, npc.getDisplayName()), 5, 50, 15);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6f, 1.2f);

        com.hogwartsmagic.util.ModAdvancements.award(player, "duel_victor");
        boolean vsWizard = npc instanceof WizardEntity;
        player.giveExperiencePoints(vsWizard ? 75 : 25);
        maybeDropBookReward(npc, vsWizard);

        // The loser concedes gracefully: healed back up, grudge cleared, brief rematch cooldown.
        npc.setTarget(null);
        npc.setLastHurtByMob(null);
        npc.heal(npc.getMaxHealth());
        if (npc.level() instanceof ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                    player.getX(), player.getY() + 1.2, player.getZ(), 40, 0.6, 0.8, 0.6, 0.25);
        }
    }

    private static void lose(ServerPlayer player, Duel duel, String messageKey) {
        endDuel(player, duel);
        Mob npc = duel.npc;

        sendTitle(player, Component.translatable("hogwartsmagic.duel.defeat"),
                Component.translatable(messageKey, npc.getDisplayName()), 5, 50, 15);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0f, 0.8f);

        // Victorious NPC gloats, then stands down. (A Death Eater's targeting goals may well
        // re-acquire the player on their own — dueling dark wizards is played at your own risk.)
        npc.playSound(SoundEvents.EVOKER_CELEBRATE, 1.0f, 1.0f);
        npc.setTarget(null);
        npc.setLastHurtByMob(null);
    }

    private static void cancel(ServerPlayer player, Duel duel, String messageKey) {
        endDuel(player, duel);
        duel.npc.setTarget(null);
        player.displayClientMessage(Component.translatable(messageKey), true);
    }

    private static void endDuel(ServerPlayer player, Duel duel) {
        DUELS.remove(player.getUUID());
        duel.npc.getPersistentData().remove(TAG_DUELING);
        duel.npc.getPersistentData().putLong(TAG_DUEL_COOLDOWN_UNTIL,
                duel.npc.level().getGameTime() + NPC_REMATCH_COOLDOWN_TICKS);
    }

    // ---- helpers ---------------------------------------------------------------------

    /** True while this NPC is mid-duel (any phase). Lets AI goals like panic stand down. */
    public static boolean isDueling(Mob npc) {
        return npc.getPersistentData().getBoolean(TAG_DUELING);
    }

    private static void holdNpcStill(ServerPlayer player, Mob npc) {
        npc.setTarget(null);
        npc.getNavigation().stop();
        npc.getLookControl().setLookAt(player, 30.0f, 30.0f);
    }

    private static boolean brokeEtiquette(ServerPlayer player, Duel duel) {
        Mob npc = duel.npc;
        if (npc.getLastHurtByMob() == player) {
            endDuel(player, duel);
            player.displayClientMessage(Component.translatable("hogwartsmagic.duel.etiquette"), true);
            // No stand-down for the NPC: it got sucker-punched and will defend itself.
            return true;
        }
        return false;
    }

    private static void maybeDropBookReward(Mob npc, boolean vsWizard) {
        float chance = vsWizard ? 0.35f : 0.20f;
        if (npc.getRandom().nextFloat() >= chance) return;

        List<SpellType> pool = new ArrayList<>();
        for (SpellType spell : SpellType.values()) {
            SpellLootRarity rarity = spell.getLootRarity();
            if (vsWizard || rarity == SpellLootRarity.COMMON || rarity == SpellLootRarity.UNCOMMON) {
                pool.add(spell);
            }
        }
        if (pool.isEmpty()) return;
        SpellType spell = pool.get(npc.getRandom().nextInt(pool.size()));
        npc.spawnAtLocation(MagicBookItem.createBook(spell));
    }

    private static void sendTitle(ServerPlayer player, Component title, Component subtitle,
                                  int fadeIn, int stay, int fadeOut) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        if (subtitle != null) {
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }
}
