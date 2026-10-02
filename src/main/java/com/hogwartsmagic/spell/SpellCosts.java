package com.hogwartsmagic.spell;

import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.sorting.HousePerks;
import com.hogwartsmagic.spell.mastery.MasteryMath;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The one place a player's spell cost and cooldown are worked out. Mana cost = base x wand x
 * mastery; cooldown = base x house x wand x mastery. (The house only ever touched cooldown, and
 * that stays as it was.) NPC casters keep their own maths and don't come through here.
 */
public final class SpellCosts {
    private SpellCosts() {
    }

    public static float masteryMultiplier(Player player, SpellType spell) {
        return MasteryMath.costMultiplier(SpellMasteryData.getRank(player, spell),
                com.hogwartsmagic.spell.mastery.MasteryConfig.of(player).rankDiscount());
    }

    public static int manaCost(Player player, ItemStack wand, SpellType spell) {
        return Math.max(1, Math.round(spell.getManaCost()
                * MagicWandItem.getManaCostMultiplier(wand) * masteryMultiplier(player, spell)));
    }

    public static long cooldownTicks(Player player, ItemStack wand, SpellType spell) {
        return cooldownTicks(player, wand, spell, spell.getCooldownTicks());
    }

    /** For the sites that cool down on a fraction of the base (Patronus uses a quarter). */
    public static long cooldownTicks(Player player, ItemStack wand, SpellType spell, double baseTicks) {
        return Math.max(1L, Math.round(baseTicks
                * HousePerks.getCooldownMultiplier(player)
                * MagicWandItem.getCooldownMultiplier(wand)
                * masteryMultiplier(player, spell)));
    }

    public static void startCooldown(ServerPlayer player, ItemStack wand, SpellType spell, long now) {
        startCooldown(player, wand, spell, now, spell.getCooldownTicks());
    }

    public static void startCooldown(ServerPlayer player, ItemStack wand, SpellType spell, long now, double baseTicks) {
        startCooldown(player, wand, spell, now, baseTicks, true);
    }

    /** awardMastery is false for cancelled or aborted channels, which did not complete a cast. */
    public static void startCooldown(ServerPlayer player, ItemStack wand, SpellType spell, long now, double baseTicks, boolean awardMastery) {
        MagicWandItem.setSpellCooldown(wand, spell, now + cooldownTicks(player, wand, spell, baseTicks));
        if (awardMastery) {
            com.hogwartsmagic.spell.mastery.MasteryAwards.onCastCompleted(player, spell);
        }
    }
}
