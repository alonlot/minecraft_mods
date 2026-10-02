package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/** Mana changes outside the cast handler's flat charge: Protego's refund, Reparo's priced item repair. Server only. */
public final class SupportMana {
    private SupportMana() {
    }

    public static float current(ServerPlayer player) {
        CompoundTag modData = PlayerMagicData.getModData(player);
        return modData.contains("Mana") ? modData.getFloat("Mana") : PlayerMagicData.getMaxMana(player);
    }

    /** Takes {@code amount} if the player has it (creative always does, for free). */
    public static boolean trySpend(ServerPlayer player, float amount) {
        if (player.isCreative() || amount <= 0.0f) {
            return true;
        }
        float mana = current(player);
        if (mana < amount) {
            return false;
        }
        set(player, mana - amount);
        return true;
    }

    public static void add(ServerPlayer player, float amount) {
        if (amount <= 0.0f) {
            return;
        }
        set(player, Math.min(PlayerMagicData.getMaxMana(player), current(player) + amount));
    }

    private static void set(ServerPlayer player, float mana) {
        float maxMana = PlayerMagicData.getMaxMana(player);
        CompoundTag modData = PlayerMagicData.getModData(player);
        modData.putFloat("Mana", mana);
        modData.putFloat("MaxMana", maxMana);
        PlayerMagicData.saveModData(player, modData);
        SpellPackets.CHANNEL.send(new SpellPackets.SyncManaPacket(mana, maxMana), PacketDistributor.PLAYER.with(player));
    }
}
