package com.hogwartsmagic.spell.enchant;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;

/**
 * The spell enchantments. None of them come from the enchanting table, loot or trades: the Spell Transmuter binds a
 * spell book into a level I book of its enchantment, and an anvil raises it from there.
 */
public final class SpellEnchantments {
    public static final ResourceKey<Enchantment> WARDING = key("warding");
    public static final ResourceKey<Enchantment> ARRESTO = key("arresto");
    public static final ResourceKey<Enchantment> DOUSE = key("douse");
    public static final ResourceKey<Enchantment> ACCIO = key("accio");
    public static final ResourceKey<Enchantment> REPARO = key("reparo");
    public static final ResourceKey<Enchantment> LUMOS = key("lumos");
    public static final ResourceKey<Enchantment> ASCENDIO = key("ascendio");

    /** Which spell book binds into which enchantment. */
    public static final Map<SpellType, ResourceKey<Enchantment>> FROM_SPELL = Map.of(
            SpellType.PROTEGO, WARDING,
            SpellType.ARRESTO_MOMENTUM, ARRESTO,
            SpellType.AQUA_ERUCTO, DOUSE,
            SpellType.ACCIO, ACCIO,
            SpellType.REPARO, REPARO,
            SpellType.LUMOS, LUMOS,
            SpellType.ASCENDIO, ASCENDIO);

    private SpellEnchantments() {
    }

    private static ResourceKey<Enchantment> key(String path) {
        return ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, path));
    }

    public static Optional<Holder.Reference<Enchantment>> holder(Level level, ResourceKey<Enchantment> key) {
        return level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(key);
    }

    @Nullable
    public static ResourceKey<Enchantment> forSpell(@Nullable SpellType spell) {
        return spell == null ? null : FROM_SPELL.get(spell);
    }

    /** {@code key}'s level on one item. */
    public static int level(Level level, ResourceKey<Enchantment> key, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return holder(level, key).map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, stack)).orElse(0);
    }

    /** {@code key}'s level on what {@code entity} wears in {@code slot}. */
    public static int level(LivingEntity entity, ResourceKey<Enchantment> key, EquipmentSlot slot) {
        return level(entity.level(), key, entity.getItemBySlot(slot));
    }

    /** {@code key}'s levels summed over {@code entity}'s four armour slots. */
    public static int armourLevels(LivingEntity entity, ResourceKey<Enchantment> key) {
        Optional<Holder.Reference<Enchantment>> h = holder(entity.level(), key);
        if (h.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ItemStack piece : entity.getArmorSlots()) {
            total += EnchantmentHelper.getItemEnchantmentLevel(h.get(), piece);
        }
        return total;
    }
}
