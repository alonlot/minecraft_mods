package com.hogwartsmagic.spell.mastery;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

/**
 * Per-player spell XP, stored as {@code PlayerPersisted/<modid>/SpellMastery/<spellId> = float}.
 * Forge copies PlayerPersisted across death, so ranks survive dying. Reads are safe on either side
 * (the client copy arrives via {@link SpellPackets.SyncSpellMasteryPacket}); writes are server-only.
 */
public final class SpellMasteryData {
    public static final String KEY = "SpellMastery";

    private SpellMasteryData() {
    }

    private static CompoundTag tag(Player player) {
        return PlayerMagicData.getModData(player).getCompound(KEY);
    }

    public static float getXp(Player player, SpellType spell) {
        return tag(player).getFloat(spell.getId());
    }

    public static int getRank(Player player, SpellType spell) {
        return MasteryMath.rankForXp(getXp(player, spell), Config.getMasteryThresholds());
    }

    public static int getRanksGained(Player player) {
        CompoundTag tag = tag(player);
        int[] thresholds = Config.getMasteryThresholds();
        int total = 0;
        for (String id : tag.getAllKeys()) {
            total += MasteryMath.rankForXp(tag.getFloat(id), thresholds) - MasteryMath.NOVICE;
        }
        return total;
    }

    public static float getMaxManaBonus(Player player) {
        return MasteryMath.maxManaBonus(getRanksGained(player),
                Config.getMasteryManaPerRank(), Config.getMasteryManaCap());
    }

    public static int addXp(ServerPlayer player, SpellType spell, float amount) {
        if (amount <= 0f) {
            return 0;
        }
        float before = getXp(player, spell);
        float after = before + amount;
        write(player, spell, after);
        int[] thresholds = Config.getMasteryThresholds();
        int oldRank = MasteryMath.rankForXp(before, thresholds);
        int newRank = MasteryMath.rankForXp(after, thresholds);
        return newRank > oldRank ? newRank : 0;
    }

    public static void setXp(ServerPlayer player, SpellType spell, float xp) {
        write(player, spell, Math.max(0f, xp));
    }

    public static void reset(ServerPlayer player) {
        CompoundTag modData = PlayerMagicData.getModData(player);
        modData.remove(KEY);
        PlayerMagicData.saveModData(player, modData);
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        SpellPackets.CHANNEL.send(new SpellPackets.SyncSpellMasteryPacket(tag(player).copy()),
                PacketDistributor.PLAYER.with(player));
    }

    private static void write(ServerPlayer player, SpellType spell, float xp) {
        CompoundTag modData = PlayerMagicData.getModData(player);
        CompoundTag tag = modData.getCompound(KEY);
        tag.putFloat(spell.getId(), xp);
        modData.put(KEY, tag);
        PlayerMagicData.saveModData(player, modData);
        sync(player);
    }
}
