package com.hogwartsmagic.spell;

import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A player's spell cooldowns belong to the player, not to the wand in their hand: swapping to a second wand must not
 * skip a cooldown. The server keeps them in the player's persisted mod data (so they also survive death and logout),
 * and copies them onto whichever wand is held, since the client reads the wand's own tag for the action bar timer and
 * the cooldown fizzle. A cooldown is the later of the two, so old wand tags still count.
 */
public final class SpellCooldowns {
    private static final String KEY = "SpellCooldowns";

    private SpellCooldowns() {
    }

    /** The game time {@code spell} is ready again for {@code player} casting with {@code wand}. */
    public static long get(Player player, ItemStack wand, SpellType spell) {
        long onWand = MagicWandItem.isAnyWand(wand) ? MagicWandItem.getSpellCooldown(wand, spell) : 0L;
        return Math.max(onWand, playerEnd(player, spell));
    }

    public static boolean isOnCooldown(Player player, ItemStack wand, SpellType spell, long now) {
        return now < get(player, wand, spell);
    }

    /** Starts (or replaces) {@code spell}'s cooldown on the player, and on {@code wand} for the client to show. */
    public static void set(Player player, ItemStack wand, SpellType spell, long readyAt) {
        if (MagicWandItem.isAnyWand(wand)) {
            MagicWandItem.setSpellCooldown(wand, spell, readyAt);
        }
        if (player.level().isClientSide) {
            return;
        }
        long now = player.level().getGameTime();
        CompoundTag modData = PlayerMagicData.getModData(player);
        CompoundTag cooldowns = modData.getCompound(KEY);
        // Expired entries are dropped as new ones come in, so the tag never grows past the spells cooling now.
        for (String id : cooldowns.getAllKeys().toArray(new String[0])) {
            if (cooldowns.getLong(id) <= now) {
                cooldowns.remove(id);
            }
        }
        if (readyAt > now) {
            cooldowns.putLong(spell.getId(), readyAt);
        } else {
            cooldowns.remove(spell.getId());
        }
        modData.put(KEY, cooldowns);
        PlayerMagicData.saveModData(player, modData);
    }

    /**
     * Server side: writes the player's running cooldowns onto {@code wand} wherever the wand's own tag is behind, so a
     * freshly picked up wand shows the true timer. Writes nothing when the wand is already up to date.
     */
    public static void mirrorOnto(Player player, ItemStack wand) {
        if (!MagicWandItem.isAnyWand(wand)) {
            return;
        }
        CompoundTag cooldowns = PlayerMagicData.getModData(player).getCompound(KEY);
        if (cooldowns.isEmpty()) {
            return;
        }
        long now = player.level().getGameTime();
        for (String id : cooldowns.getAllKeys()) {
            long end = cooldowns.getLong(id);
            if (end <= now) {
                continue;
            }
            SpellType spell = SpellType.fromId(id);
            if (spell != null && MagicWandItem.getSpellCooldown(wand, spell) < end) {
                MagicWandItem.setSpellCooldown(wand, spell, end);
            }
        }
    }

    private static long playerEnd(Player player, SpellType spell) {
        if (player == null || player.level().isClientSide) {
            return 0L;
        }
        return PlayerMagicData.getModData(player).getCompound(KEY).getLong(spell.getId());
    }
}
