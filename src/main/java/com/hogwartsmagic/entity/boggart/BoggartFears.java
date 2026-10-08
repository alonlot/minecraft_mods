package com.hogwartsmagic.entity.boggart;

import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * What each player is afraid of: a count of deaths per killer type, stored as
 * {@code PlayerPersisted/hogwartsmagic/BoggartFears} so it survives dying (Forge copies PlayerPersisted). Server only.
 * A player killer is recorded under the key "player"; the Boggart never wears a player, so pickForm turns that into a
 * Dementor.
 */
public final class BoggartFears {
    public static final String KEY = "BoggartFears";
    /** More keys than this and the smallest count is dropped. Keeps the tag small for players who die a lot. */
    public static final int MAX_KEYS = 16;

    private BoggartFears() {
    }

    /** One player's fears: killer id to count. */
    public record Fears(Map<String, Integer> counts) {
        public String pickForm() {
            return BoggartRules.pickForm(counts);
        }
    }

    public static Fears load(ServerPlayer player) {
        CompoundTag tag = PlayerMagicData.getModData(player).getCompound(KEY);
        Map<String, Integer> counts = new HashMap<>();
        for (String key : tag.getAllKeys()) {
            if (tag.contains(key, Tag.TAG_ANY_NUMERIC)) {
                counts.put(key, tag.getInt(key));
            }
        }
        return new Fears(counts);
    }

    /** From ModEvents.onLivingDeath: counts the killer. No killer (fall, lava, the void) records nothing. */
    public static void record(ServerPlayer victim, DamageSource source) {
        Entity killer = source.getEntity();
        if (killer == null) {
            killer = source.getDirectEntity();
        }
        // Arrows, anvils, TNT and lightning have no face to wear.
        if (!(killer instanceof LivingEntity)) {
            return;
        }
        String key = killer instanceof Player
                ? BoggartRules.PLAYER_KEY
                : EntityType.getKey(killer.getType()).toString();
        if (killer instanceof BoggartEntity) {
            // A Boggart cannot become a Boggart; it wears what it wore.
            key = BoggartRules.DEFAULT_FORM;
        }

        Map<String, Integer> counts = load(victim).counts();
        counts.merge(key, 1, Integer::sum);
        while (counts.size() > MAX_KEYS) {
            String smallest = null;
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (smallest == null || entry.getValue() < counts.get(smallest)
                        || (entry.getValue().equals(counts.get(smallest)) && entry.getKey().compareTo(smallest) > 0)) {
                    smallest = entry.getKey();
                }
            }
            counts.remove(smallest);
        }

        CompoundTag tag = new CompoundTag();
        counts.forEach(tag::putInt);
        CompoundTag modData = PlayerMagicData.getModData(victim);
        modData.put(KEY, tag);
        PlayerMagicData.saveModData(victim, modData);
    }

    /** The entity type id a Boggart takes for this player. */
    public static String fearOf(ServerPlayer player) {
        return load(player).pickForm();
    }
}
