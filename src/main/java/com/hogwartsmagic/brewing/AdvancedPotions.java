package com.hogwartsmagic.brewing;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Whether a player has read Advanced Potion-Making. Kept in PlayerPersisted/hogwartsmagic (Forge copies it on
 * death, so the knowledge survives dying). Server side only: the cauldron checks it for the player making each step.
 */
public final class AdvancedPotions {
    public static final String KEY = "AdvancedPotionsRead";

    private AdvancedPotions() {
    }

    public static boolean hasRead(Player player) {
        return player.getPersistentData().getCompound("PlayerPersisted").getCompound(ExampleMod.MODID).getBoolean(KEY);
    }

    /** Records the reading; true the first time, false when already read. */
    public static boolean markRead(ServerPlayer player) {
        if (hasRead(player)) {
            return false;
        }
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound("PlayerPersisted");
        CompoundTag mod = persisted.getCompound(ExampleMod.MODID);
        mod.putBoolean(KEY, true);
        persisted.put(ExampleMod.MODID, mod);
        root.put("PlayerPersisted", persisted);
        return true;
    }
}
