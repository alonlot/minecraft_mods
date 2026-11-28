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
    public static final ResourceKey<Enchantment> CAVE_INIMICUM = key("cave_inimicum");
    public static final ResourceKey<Enchantment> DEPULSO = key("depulso");
    public static final ResourceKey<Enchantment> PROTEGO_MAXIMA = key("protego_maxima");
    public static final ResourceKey<Enchantment> REVELIO = key("revelio");
    public static final ResourceKey<Enchantment> INCENDIO = key("incendio");
    public static final ResourceKey<Enchantment> INCARCEROUS = key("incarcerous");
    public static final ResourceKey<Enchantment> LEVIOSA = key("leviosa");
    public static final ResourceKey<Enchantment> ALATUS = key("alatus");
    public static final ResourceKey<Enchantment> PATRONUS = key("patronus");
    public static final ResourceKey<Enchantment> GLACIUS = key("glacius");
    public static final ResourceKey<Enchantment> BOMBARDA_MAXIMA = key("bombarda_maxima");
    public static final ResourceKey<Enchantment> IMPERIO = key("imperio");

    /** Leviosa: how long one airborne spell can hover (ticks), and how fast a hover sinks (blocks a tick). */
    public static int leviosaHoverTicks(int level) {
        return 40 * Math.max(0, Math.min(2, level));
    }

    /** Sinking a little, never still: a hover must not read as flying to the server's float check. */
    public static final double LEVIOSA_SINK = -0.05;

    /** Alatus: horizontal speed kept each gliding tick on top of vanilla's drag, per level. */
    public static double alatusCarry(int level) {
        return 1.0 + 0.004 * Math.max(0, Math.min(2, level));
    }

    /** Glacius: Slowness ticks for a hit at this level (I 3 s, II 4 s, III 5 s). */
    public static int glaciusTicks(int level) {
        return 40 + 20 * Math.max(1, Math.min(3, level));
    }

    /** Which spell book binds into which enchantment. */
    public static final Map<SpellType, ResourceKey<Enchantment>> FROM_SPELL = Map.ofEntries(
            Map.entry(SpellType.PROTEGO, WARDING),
            Map.entry(SpellType.ARRESTO_MOMENTUM, ARRESTO),
            Map.entry(SpellType.AQUA_ERUCTO, DOUSE),
            Map.entry(SpellType.ACCIO, ACCIO),
            Map.entry(SpellType.REPARO, REPARO),
            Map.entry(SpellType.LUMOS, LUMOS),
            Map.entry(SpellType.ASCENDIO, ASCENDIO),
            Map.entry(SpellType.CAVE_INIMICUM, CAVE_INIMICUM),
            Map.entry(SpellType.DEPULSO, DEPULSO),
            Map.entry(SpellType.PROTEGO_MAXIMA, PROTEGO_MAXIMA),
            Map.entry(SpellType.REVELIO, REVELIO),
            Map.entry(SpellType.INCENDIO, INCENDIO),
            Map.entry(SpellType.INCARCEROUS, INCARCEROUS),
            Map.entry(SpellType.WINGARDIUM_LEVIOSA, LEVIOSA),
            Map.entry(SpellType.ALATUS, ALATUS),
            Map.entry(SpellType.EXPECTO_PATRONUM, PATRONUS),
            Map.entry(SpellType.GLACIUS, GLACIUS),
            Map.entry(SpellType.BOMBARDA, BOMBARDA_MAXIMA),
            Map.entry(SpellType.IMPERIO, IMPERIO));

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

    /**
     * Cave Inimicum: how much less mobs notice the wearer (their detection range is multiplied by this). Each level
     * summed over the armour takes 7.5% off, to 40% with a full set of II.
     */
    public static double caveInimicumVisibility(int levels) {
        return Math.max(0.4, 1.0 - 0.075 * Math.max(0, levels));
    }

    /** Cave Inimicum: the wearer's body alpha, from 1 (none) down to 0.45 for a full set of II. */
    public static float caveInimicumAlpha(int levels) {
        return Math.max(0.45f, 1.0f - 0.07f * Math.max(0, levels));
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
