package com.hogwartsmagic.assignment;

import com.hogwartsmagic.item.MagicBookItem;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Per-player assignment progress, kept in PlayerPersisted/hogwartsmagic/Assignments (it survives death).
 * One compound per assignment in progress, keyed by spell id: {@code P} is the int[] of counters,
 * {@code S<i>} the comma-joined distinct ids for objective i, {@code N} whether "complete" was announced.
 * The scroll item only shows this data, so dropping or copying a scroll can never copy progress.
 */
public final class AssignmentProgress {
    public static final String KEY = "Assignments";
    private static final String PROGRESS = "P";
    private static final String NOTIFIED = "N";

    private AssignmentProgress() {
    }

    private static CompoundTag all(Player player) {
        return PlayerMagicData.getModData(player).getCompound(KEY);
    }

    private static void saveAll(Player player, CompoundTag tag) {
        CompoundTag modData = PlayerMagicData.getModData(player);
        modData.put(KEY, tag);
        PlayerMagicData.saveModData(player, modData);
    }

    public static boolean hasAny(Player player) {
        return !all(player).isEmpty();
    }

    public static boolean isActive(Player player, String spellId) {
        return all(player).contains(spellId);
    }

    public static int activeCount(Player player) {
        return all(player).size();
    }

    public static List<String> activeIds(Player player) {
        return new ArrayList<>(all(player).getAllKeys());
    }

    public static void start(Player player, String spellId) {
        CompoundTag tag = all(player);
        CompoundTag entry = new CompoundTag();
        entry.putIntArray(PROGRESS, new int[0]);
        tag.put(spellId, entry);
        saveAll(player, tag);
    }

    public static void remove(Player player, String spellId) {
        CompoundTag tag = all(player);
        tag.remove(spellId);
        saveAll(player, tag);
    }

    public static int[] progress(Player player, AssignmentDef def) {
        CompoundTag entry = all(player).getCompound(def.spellId());
        return AssignmentRules.fit(entry.getIntArray(PROGRESS), def.objectives());
    }

    /** Live objectives judged now; stored objectives are always false here. */
    public static boolean[] live(ServerPlayer player, AssignmentDef def) {
        List<ObjectiveDef> objectives = def.objectives();
        boolean[] done = new boolean[objectives.size()];
        for (int i = 0; i < done.length; i++) {
            ObjectiveDef objective = objectives.get(i);
            if (objective.type() == ObjectiveType.KNOW_SPELL) {
                done[i] = knowsAll(player, objective);
            } else if (objective.type() == ObjectiveType.DELIVER) {
                done[i] = countHeld(player, objective) >= objective.count();
            } else if (objective.type() == ObjectiveType.ADVANCEMENT) {
                done[i] = hasAdvancements(player, objective);
            }
        }
        return done;
    }

    /** How much of a live objective is met, for the screen: items held, or 0/1. */
    public static int liveAmount(ServerPlayer player, ObjectiveDef objective, boolean done) {
        if (objective.type() == ObjectiveType.DELIVER) {
            return Math.min(objective.count(), countHeld(player, objective));
        }
        return done ? objective.count() : 0;
    }

    public static boolean isComplete(ServerPlayer player, AssignmentDef def) {
        return AssignmentRules.isComplete(def.objectives(), progress(player, def), live(player, def));
    }

    /**
     * Adds {@code amount} to every in-progress objective of {@code type} that {@code filter} accepts. Shows
     * the new count on the action bar and announces completion once.
     */
    public static void bump(ServerPlayer player, ObjectiveType type, int amount, Predicate<ObjectiveDef> filter) {
        update(player, type, filter, null, amount);
    }

    /** Counts {@code value} once per objective: for "brew 5 different potions" style lines. */
    public static void bumpDistinct(ServerPlayer player, ObjectiveType type, String value, Predicate<ObjectiveDef> filter) {
        update(player, type, filter, value, 1);
    }

    private static void update(ServerPlayer player, ObjectiveType type, Predicate<ObjectiveDef> filter,
                               @Nullable String distinctValue, int amount) {
        if (amount <= 0) {
            return;
        }
        CompoundTag tag = all(player);
        if (tag.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (String spellId : tag.getAllKeys()) {
            AssignmentDef def = AssignmentRegistry.get(spellId);
            if (def == null) {
                continue;
            }
            CompoundTag entry = tag.getCompound(spellId);
            int[] progress = AssignmentRules.fit(entry.getIntArray(PROGRESS), def.objectives());
            boolean entryChanged = false;
            for (int i = 0; i < progress.length; i++) {
                ObjectiveDef objective = def.objectives().get(i);
                if (objective.type() != type || progress[i] >= objective.count() || !filter.test(objective)) {
                    continue;
                }
                int next;
                if (distinctValue != null && objective.flag("distinct")) {
                    String key = "S" + i;
                    String joined = entry.getString(key);
                    String grown = AssignmentRules.addDistinct(joined, distinctValue);
                    if (grown.equals(joined)) {
                        continue;
                    }
                    entry.putString(key, grown);
                    next = Math.min(objective.count(), AssignmentRules.distinctCount(grown));
                } else {
                    next = AssignmentRules.add(progress[i], amount, objective.count());
                }
                if (next != progress[i]) {
                    progress[i] = next;
                    entryChanged = true;
                    player.displayClientMessage(Component.translatable("assignment.hogwartsmagic.progress",
                            Component.translatable("spell.hogwartsmagic." + spellId), next, objective.count())
                            .withStyle(ChatFormatting.GOLD), true);
                }
            }
            if (entryChanged) {
                entry.putIntArray(PROGRESS, progress);
                tag.put(spellId, entry);
                changed = true;
            }
        }
        if (changed) {
            saveAll(player, tag);
            announceCompleted(player);
        }
    }

    /** Tells the player once when an assignment is done. Also run each second for live objectives. */
    public static void announceCompleted(ServerPlayer player) {
        CompoundTag tag = all(player);
        boolean changed = false;
        for (String spellId : tag.getAllKeys()) {
            CompoundTag entry = tag.getCompound(spellId);
            AssignmentDef def = AssignmentRegistry.get(spellId);
            if (def == null || entry.getBoolean(NOTIFIED) || !isComplete(player, def)) {
                continue;
            }
            entry.putBoolean(NOTIFIED, true);
            tag.put(spellId, entry);
            changed = true;
            player.displayClientMessage(Component.translatable("assignment.hogwartsmagic.complete",
                    Component.translatable("spell.hogwartsmagic." + spellId)).withStyle(ChatFormatting.GREEN), false);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.4f);
        }
        if (changed) {
            saveAll(player, tag);
        }
    }

    /** Removes the items DELIVER objectives asked for. Call only once the assignment is complete. */
    public static void consumeDeliveries(ServerPlayer player, AssignmentDef def) {
        for (ObjectiveDef objective : def.objectives()) {
            if (objective.type() != ObjectiveType.DELIVER || objective.flag("keep")) {
                continue;
            }
            int left = objective.count();
            for (int slot = 0; slot < player.getInventory().getContainerSize() && left > 0; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.isEmpty() && itemMatches(objective, stack.getItem())) {
                    int take = Math.min(left, stack.getCount());
                    stack.shrink(take);
                    left -= take;
                }
            }
        }
    }

    private static boolean knowsAll(Player player, ObjectiveDef objective) {
        for (String id : objective.targets()) {
            SpellType spell = SpellType.fromId(id);
            if (!spell.getId().equals(id) || !MagicBookItem.isSpellLearned(player, spell)) {
                return false;
            }
        }
        return true;
    }

    private static int countHeld(Player player, ObjectiveDef objective) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && itemMatches(objective, stack.getItem())) {
                total += stack.getCount();
            }
        }
        return total;
    }

    static boolean itemMatches(ObjectiveDef objective, Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return objective.matches(id.toString(), tag -> item.builtInRegistryHolder()
                .is(TagKey.create(Registries.ITEM, ResourceLocation.parse(tag))));
    }

    private static boolean hasAdvancements(ServerPlayer player, ObjectiveDef objective) {
        if (player.getServer() == null) {
            return false;
        }
        for (String id : objective.targets()) {
            ResourceLocation location = ResourceLocation.tryParse(id);
            if (location == null) {
                return false;
            }
            AdvancementHolder holder = player.getServer().getAdvancements().get(location);
            if (holder == null || !player.getAdvancements().getOrStartProgress(holder).isDone()) {
                return false;
            }
        }
        return true;
    }
}
