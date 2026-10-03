package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.event.ModEvents;
import com.hogwartsmagic.spell.SpellCosts;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.mastery.MasteryAwards;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Reparo cast on yourself (left-click), Expert and up: mends the off-hand item, or else the first
 * damaged armour piece from head to feet, by a tenth of its durability. It prices itself (the normal
 * cast plus a share for the item's durability, both mastery-discounted) and takes nothing if there
 * is nothing to mend. Only the damage value changes, so Mending and enchantments are untouched.
 */
public final class ReparoSpell {
    private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private ReparoSpell() {
    }

    public static void castOnSelf(ServerPlayer player, ItemStack wand, long now) {
        int rank = SpellMasteryData.getRank(player, SpellType.REPARO);
        if (!ReparoRules.repairsItems(rank)) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.reparo.items_locked")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ItemStack item = findDamaged(player);
        if (item.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.reparo.nothing")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int maxDamage = item.getMaxDamage();
        float cost = SpellCosts.manaCost(player, wand, SpellType.REPARO)
                + ReparoRules.itemExtraMana(maxDamage) * SpellCosts.masteryMultiplier(player, SpellType.REPARO);
        if (!SupportMana.trySpend(player, cost)) {
            player.displayClientMessage(Component.literal("Not enough mana!").withStyle(ChatFormatting.RED), true);
            return;
        }
        item.setDamageValue(Math.max(0, item.getDamageValue() - ReparoRules.itemRepairAmount(maxDamage)));
        player.serverLevel().sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY() + 1.0, player.getZ(),
                14, 0.4, 0.5, 0.4, 0.02);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.7f, 1.4f);
        ModEvents.revealInvisibilityCloak(player);
        if (!player.isCreative()) {
            SpellCosts.startCooldown(player, wand, SpellType.REPARO, now);
        }
        MasteryAwards.onBlockEffect(player, SpellType.REPARO);
    }

    /** The cloak is excluded: Magical Silk stays its only repair. */
    private static boolean mendable(ItemStack s) {
        return s.isDamageableItem() && s.isDamaged() && !s.is(com.hogwartsmagic.ExampleMod.INVISIBILITY_CLOAK.get());
    }

    private static ItemStack findDamaged(ServerPlayer player) {
        ItemStack off = player.getOffhandItem();
        if (mendable(off)) {
            return off;
        }
        for (EquipmentSlot slot : ARMOUR) {
            ItemStack worn = player.getItemBySlot(slot);
            if (mendable(worn)) {
                return worn;
            }
        }
        return ItemStack.EMPTY;
    }
}
