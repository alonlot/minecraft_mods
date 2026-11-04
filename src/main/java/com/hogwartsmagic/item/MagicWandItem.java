package com.hogwartsmagic.item;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;

/**
 * Wand item: Shift + Right Click spell menu, Right Click cast, radial with R.
 */
public class MagicWandItem extends SwordItem {

    private static final String TAG_SELECTED_SPELL = "SelectedSpell";
    private static final String TAG_COOLDOWN_PREFIX = "Cooldown_";
    private static final String TAG_SLOT_PREFIX = "SpellSlot_";
    public static final int SPELL_SLOT_COUNT = 9;

    public MagicWandItem(Properties properties) {
        super(Tiers.WOOD, properties.stacksTo(1));
    }

    /**
     * No wear from melee. A SwordItem spends a point of durability per hit, and the wooden tier gives
     * 59, so punching mobs with a wand would break it (the Elder Wand included). Nothing in the mod
     * means wands to wear out.
     */
    @Override
    public void postHurtEnemy(ItemStack stack, net.minecraft.world.entity.LivingEntity target, net.minecraft.world.entity.LivingEntity attacker) {
    }

    public static boolean isAnyWand(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return item == ExampleMod.HARRY_POTTER_WAND.get()
                || item == ExampleMod.PHOENIX_CORE_WAND.get()
                || item == ExampleMod.UNICORN_CORE_WAND.get()
                || item == ExampleMod.DRAGON_CORE_WAND.get()
                || item == ExampleMod.ELDER_WAND.get();
    }

    /** Mana cost multiplier (higher = more expensive). */
    public static float getManaCostMultiplier(ItemStack stack) {
        Item item = stack.getItem();
        if (item == ExampleMod.HARRY_POTTER_WAND.get()) {
            return 1.12f;
        }
        if (item == ExampleMod.UNICORN_CORE_WAND.get()) {
            return 0.88f;
        }
        if (item == ExampleMod.DRAGON_CORE_WAND.get()) {
            return 1.10f;
        }
        if (item == ExampleMod.PHOENIX_CORE_WAND.get()) {
            return 1.0f;
        }
        if (item == ExampleMod.ELDER_WAND.get()) {
            return 0.94f;
        }
        return 1.0f;
    }

    public static float getCooldownMultiplier(ItemStack stack) {
        Item item = stack.getItem();
        if (item == ExampleMod.HARRY_POTTER_WAND.get()) {
            return 1.06f;
        }
        if (item == ExampleMod.PHOENIX_CORE_WAND.get()) {
            return 0.84f;
        }
        if (item == ExampleMod.UNICORN_CORE_WAND.get()) {
            return 1.02f;
        }
        if (item == ExampleMod.DRAGON_CORE_WAND.get()) {
            return 1.06f;
        }
        if (item == ExampleMod.ELDER_WAND.get()) {
            return 0.92f;
        }
        return 1.0f;
    }

    public static float getSpellPowerMultiplier(ItemStack stack) {
        Item item = stack.getItem();
        if (item == ExampleMod.HARRY_POTTER_WAND.get()) {
            return 0.88f;
        }
        if (item == ExampleMod.DRAGON_CORE_WAND.get()) {
            return 1.12f;
        }
        if (item == ExampleMod.UNICORN_CORE_WAND.get()) {
            return 0.97f;
        }
        if (item == ExampleMod.PHOENIX_CORE_WAND.get()) {
            return 1.02f;
        }
        if (item == ExampleMod.ELDER_WAND.get()) {
            return 1.16f;
        }
        return 1.0f;
    }

    /** Multiplies base spell beam speed after house perks. */
    public static float getBeamSpeedMultiplier(ItemStack stack) {
        Item item = stack.getItem();
        if (item == ExampleMod.HARRY_POTTER_WAND.get()) {
            return 0.95f;
        }
        if (item == ExampleMod.PHOENIX_CORE_WAND.get()) {
            return 1.15f;
        }
        if (item == ExampleMod.UNICORN_CORE_WAND.get()) {
            return 1.0f;
        }
        if (item == ExampleMod.DRAGON_CORE_WAND.get()) {
            return 0.88f;
        }
        if (item == ExampleMod.ELDER_WAND.get()) {
            return 1.08f;
        }
        return 1.0f;
    }

    /** Unicorn: small chance to refund a slice of mana after a paid cast. */
    public static void rollManaRefund(ServerPlayer player, ItemStack wand, int manaSpent) {
        if (wand.getItem() != ExampleMod.UNICORN_CORE_WAND.get() || manaSpent <= 0) {
            return;
        }
        if (player.getRandom().nextFloat() >= 0.12f) {
            return;
        }
        float refund = Math.max(1f, manaSpent * 0.18f);
        net.minecraft.nbt.CompoundTag persisted = player.getPersistentData().getCompound("PlayerPersisted");
        net.minecraft.nbt.CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
        float maxMana = modData.contains("MaxMana") ? modData.getFloat("MaxMana") : com.hogwartsmagic.Config.getBaseMaxMana();
        float mana = modData.contains("Mana") ? modData.getFloat("Mana") : maxMana;
        float newMana = Math.min(maxMana, mana + refund);
        modData.putFloat("Mana", newMana);
        persisted.put(ExampleMod.MODID, modData);
        player.getPersistentData().put("PlayerPersisted", persisted);
        com.hogwartsmagic.network.SpellPackets.CHANNEL.send(
                new com.hogwartsmagic.network.SpellPackets.SyncManaPacket(newMana, maxMana),
                net.minecraftforge.network.PacketDistributor.PLAYER.with(player));
    }

    public static SpellType getSelectedSpell(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (data.getUnsafe().isEmpty()) {
            return null;
        }
        String id = data.getUnsafe().getString(TAG_SELECTED_SPELL);
        return id.isEmpty() ? null : SpellType.fromId(id);
    }

    public static void setSelectedSpell(ItemStack stack, SpellType spell) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(TAG_SELECTED_SPELL, spell.getId()));
    }

    public static void setSpellCooldown(ItemStack stack, SpellType spell, long gameTime) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(TAG_COOLDOWN_PREFIX + spell.getId(), gameTime));
    }

    public static long getSpellCooldown(ItemStack stack, SpellType spell) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return data.getUnsafe().getLong(TAG_COOLDOWN_PREFIX + spell.getId());
    }

    public static boolean isOnCooldown(ItemStack stack, SpellType spell, long currentGameTime) {
        return currentGameTime < getSpellCooldown(stack, spell);
    }

    public static SpellType getSpellSlot(ItemStack stack, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= SPELL_SLOT_COUNT || stack.isEmpty()) {
            return null;
        }
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        String id = data.getUnsafe().getString(TAG_SLOT_PREFIX + slotIndex);
        return id.isEmpty() ? null : SpellType.fromId(id);
    }

    public static void setSpellSlot(ItemStack stack, int slotIndex, SpellType spell) {
        if (slotIndex < 0 || slotIndex >= SPELL_SLOT_COUNT || stack.isEmpty()) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            String key = TAG_SLOT_PREFIX + slotIndex;
            if (spell == null) {
                tag.remove(key);
            } else {
                tag.putString(key, spell.getId());
            }
        });
    }

    public static List<SpellType> getSpellSlots(ItemStack stack) {
        List<SpellType> slots = new ArrayList<>(SPELL_SLOT_COUNT);
        for (int i = 0; i < SPELL_SLOT_COUNT; i++) {
            slots.add(getSpellSlot(stack, i));
        }
        return slots;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag flag) {
        tooltipComponents.add(Component.translatable("item." + ExampleMod.MODID + ".harry_potter_wand.tooltip"));
        Item item = stack.getItem();
        if (item == ExampleMod.HARRY_POTTER_WAND.get()) {
            tooltipComponents.add(Component.translatable("item." + ExampleMod.MODID + ".wand.playstyle.basic").withStyle(ChatFormatting.DARK_AQUA));
        } else if (item == ExampleMod.PHOENIX_CORE_WAND.get()) {
            tooltipComponents.add(Component.translatable("item." + ExampleMod.MODID + ".wand.playstyle.phoenix").withStyle(ChatFormatting.GOLD));
        } else if (item == ExampleMod.UNICORN_CORE_WAND.get()) {
            tooltipComponents.add(Component.translatable("item." + ExampleMod.MODID + ".wand.playstyle.unicorn").withStyle(ChatFormatting.GREEN));
        } else if (item == ExampleMod.DRAGON_CORE_WAND.get()) {
            tooltipComponents.add(Component.translatable("item." + ExampleMod.MODID + ".wand.playstyle.dragon").withStyle(ChatFormatting.RED));
        } else if (item == ExampleMod.ELDER_WAND.get()) {
            tooltipComponents.add(Component.translatable("item." + ExampleMod.MODID + ".wand.playstyle.elder").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        SpellType selected = getSelectedSpell(stack);
        if (selected != null) {
            tooltipComponents.add(Component.translatable("item." + ExampleMod.MODID + ".harry_potter_wand.selected",
                    Component.translatable("spell." + ExampleMod.MODID + "." + selected.getId())));
        } else {
            tooltipComponents.add(Component.translatable("item." + ExampleMod.MODID + ".harry_potter_wand.none_selected"));
        }
    }
}
