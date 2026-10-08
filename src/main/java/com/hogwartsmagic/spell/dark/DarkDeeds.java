package com.hogwartsmagic.spell.dark;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.entity.HouseElfEntity;
import com.hogwartsmagic.entity.StudentEntity;
import com.hogwartsmagic.spell.mastery.MasteryAwards;
import com.hogwartsmagic.spell.mind.MindControl;
import com.hogwartsmagic.spell.mind.PossessionManager;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Villager;

import javax.annotation.Nullable;

/**
 * Dark Magic deeds and redemption (Phase 3): an Avada kill of an innocent (+3), forging a Horcrux (+30, at least 60),
 * and a Heal of another hurt living thing (-1, at most once per darkMagicRedemptionTicks of play). Server only. Every
 * change goes through DarkMagicData.add, so it passes the floor and the clamp, syncs, and announces a tier change.
 * Nothing here runs with enableDarkMagic off, for a creative or spectator player, or for an NPC.
 */
public final class DarkDeeds {
    /** The play-time stat (online ticks) at the last redemption. */
    public static final String KEY_LAST_REDEMPTION = "DarkMagicLastRedemption";
    private static final String KEY_INNOCENT = "gui.hogwartsmagic.dark.innocent";
    private static final String KEY_REDEEMED = "gui.hogwartsmagic.dark.redeemed";

    private DarkDeeds() {
    }

    /**
     * From SpellBeamEntity after an Avada hit on a living thing. The payer is the beam's caster, the puppeteer of a
     * puppet beam, or null (a reflected beam, another forced body); it is the same rule as a paid cast.
     */
    public static void onAvadaHit(LivingEntity victim, @Nullable ServerPlayer payer) {
        if (payer == null || !Config.isDarkMagicEnabled() || !DarkRules.earns(payer.isCreative(), payer.isSpectator())) {
            return;
        }
        boolean innocent = DarkRewardRules.isInnocent(victim instanceof Villager, victim instanceof StudentEntity,
                victim instanceof HouseElfEntity, MasteryAwards.isPassive(victim));
        float bonus = DarkRewardRules.innocentBonus(!victim.isAlive(), innocent);
        if (!(bonus > 0.0f)) {
            return;
        }
        DarkMagicData.add(payer, bonus);
        payer.displayClientMessage(Component.translatable(KEY_INNOCENT).withStyle(ChatFormatting.DARK_GREEN), true);
    }

    /** From ModEvents.tickHorcruxRitual when the Horcrux is forged: +30, and at least the floor of 60. */
    public static void onHorcruxForged(ServerPlayer player) {
        if (!Config.isDarkMagicEnabled() || !DarkRules.earns(player.isCreative(), player.isSpectator())) {
            return;
        }
        float stored = DarkMagicData.getStored(player);
        float after = DarkRewardRules.afterHorcrux(stored);
        if (after > stored) {
            DarkMagicData.add(player, after - stored);
        }
    }

    /**
     * From HealSpell.apply after the heal. wasHurt was read before any cleanse or heal. Redeems 1 point when a player
     * healed another hurt living thing of their own will, at most once per window of play; a heal that would lighten
     * nothing (Clean, or at the Horcrux floor) does not spend the window.
     */
    public static void onHeal(LivingEntity target, @Nullable Entity caster, boolean wasHurt) {
        if (!(caster instanceof ServerPlayer healer) || !Config.isDarkMagicEnabled()) {
            return;
        }
        boolean forced = MindControl.hitPuppeteer() != null || PossessionManager.isForcedCaster(healer);
        if (!DarkRewardRules.healRedeems(target == healer, target instanceof ArmorStand, forced,
                DarkRules.earns(healer.isCreative(), healer.isSpectator()), wasHurt)) {
            return;
        }
        float stored = DarkMagicData.getStored(healer);
        if (!(DarkRewardRules.afterRedemption(stored, DarkMagicData.floor(healer)) < stored)) {
            return;
        }
        CompoundTag modData = PlayerMagicData.getModData(healer);
        int played = healer.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        if (!DarkRewardRules.redemptionWindowOpen(modData.contains(KEY_LAST_REDEMPTION), played,
                modData.getInt(KEY_LAST_REDEMPTION), Config.getDarkMagicRedemptionTicks())) {
            return;
        }
        modData.putInt(KEY_LAST_REDEMPTION, played);
        PlayerMagicData.saveModData(healer, modData);
        DarkMagicData.add(healer, -DarkRules.REDEMPTION_AMOUNT);
        healer.displayClientMessage(Component.translatable(KEY_REDEEMED).withStyle(ChatFormatting.GRAY), true);
    }
}
