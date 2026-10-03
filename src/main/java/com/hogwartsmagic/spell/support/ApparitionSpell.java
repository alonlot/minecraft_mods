package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Floo fireplaces a player knows: the source and destination of every completed Floo trip and
 * every fireplace they open the Floo menu at. Kept in PlayerPersisted (survives death), at most 32,
 * oldest dropped first. A Master Apparates to any of them in the same dimension. Server only.
 */
public final class ApparitionSpell {
    private static final String KEY = "KnownFireplaces";

    private ApparitionSpell() {
    }

    public static void rememberFireplace(ServerPlayer player, ServerLevel level, BlockPos pos, String name) {
        if (name == null || name.isBlank()) {
            return; // an unnamed fireplace isn't on the network yet
        }
        CompoundTag modData = PlayerMagicData.getModData(player);
        ListTag list = modData.getList(KEY, Tag.TAG_COMPOUND);
        removeEntry(list, level, pos);
        CompoundTag entry = new CompoundTag();
        entry.putString("Dim", level.dimension().location().toString());
        entry.putLong("Pos", pos.asLong());
        entry.putString("Name", name);
        list.add(entry);
        while (list.size() > ApparitionRules.MAX_KNOWN_FIREPLACES) {
            list.remove(0);
        }
        modData.put(KEY, list);
        PlayerMagicData.saveModData(player, modData);
    }

    public static boolean isKnown(ServerPlayer player, ServerLevel level, BlockPos pos) {
        ListTag list = PlayerMagicData.getModData(player).getList(KEY, Tag.TAG_COMPOUND);
        String dim = level.dimension().location().toString();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (dim.equals(entry.getString("Dim")) && entry.getLong("Pos") == pos.asLong()) {
                return true;
            }
        }
        return false;
    }

    public static void forgetFireplace(ServerPlayer player, ServerLevel level, BlockPos pos) {
        CompoundTag modData = PlayerMagicData.getModData(player);
        ListTag list = modData.getList(KEY, Tag.TAG_COMPOUND);
        removeEntry(list, level, pos);
        modData.put(KEY, list);
        PlayerMagicData.saveModData(player, modData);
    }

    /** Known fireplaces in the player's dimension, nearest first, at most {@link ApparitionRules#MAX_LISTED_FIREPLACES}. */
    public static List<SpellPackets.FlooDestinationData> knownFireplaces(ServerPlayer player, ServerLevel level) {
        ListTag list = PlayerMagicData.getModData(player).getList(KEY, Tag.TAG_COMPOUND);
        String dim = level.dimension().location().toString();
        List<SpellPackets.FlooDestinationData> found = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (dim.equals(entry.getString("Dim"))) {
                found.add(new SpellPackets.FlooDestinationData(BlockPos.of(entry.getLong("Pos")), entry.getString("Name")));
            }
        }
        BlockPos here = player.blockPosition();
        found.sort(Comparator.comparingDouble(d -> d.pos().distSqr(here)));
        return found.size() > ApparitionRules.MAX_LISTED_FIREPLACES
                ? new ArrayList<>(found.subList(0, ApparitionRules.MAX_LISTED_FIREPLACES))
                : found;
    }

    private static void removeEntry(ListTag list, ServerLevel level, BlockPos pos) {
        String dim = level.dimension().location().toString();
        for (int i = list.size() - 1; i >= 0; i--) {
            CompoundTag entry = list.getCompound(i);
            if (dim.equals(entry.getString("Dim")) && entry.getLong("Pos") == pos.asLong()) {
                list.remove(i);
            }
        }
    }
}
