package com.hogwartsmagic.util;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.relic.RelicItem;
import com.hogwartsmagic.relic.RelicSlotType;
import com.hogwartsmagic.relic.RelicType;
import com.hogwartsmagic.sorting.HousePerks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

public final class PlayerMagicData {
    public static final String PLAYER_PERSISTED = "PlayerPersisted";
    public static final String RELICS_KEY = "Relics";
    public static final int RELIC_SLOT_COUNT = 2;

    private PlayerMagicData() {
    }

    public static CompoundTag getPersisted(Player player) {
        return player.getPersistentData().getCompound(PLAYER_PERSISTED);
    }

    public static CompoundTag getModData(Player player) {
        return getPersisted(player).getCompound(ExampleMod.MODID);
    }

    public static void saveModData(Player player, CompoundTag modData) {
        CompoundTag persisted = getPersisted(player);
        persisted.put(ExampleMod.MODID, modData);
        player.getPersistentData().put(PLAYER_PERSISTED, persisted);
    }

    public static ItemStackHandler createRelicHandler() {
        return new ItemStackHandler(RELIC_SLOT_COUNT) {
            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                RelicType type = RelicItem.getRelicType(stack);
                return type != null && type.getSlotType().getIndex() == slot;
            }
        };
    }

    public static ItemStackHandler getRelicHandler(Player player) {
        ItemStackHandler handler = createRelicHandler();
        CompoundTag modData = getModData(player);
        if (modData.contains(RELICS_KEY)) {
            handler.deserializeNBT(player.level().registryAccess(), modData.getCompound(RELICS_KEY));
        }
        return handler;
    }

    public static void saveRelicHandler(Player player, ItemStackHandler handler) {
        CompoundTag modData = getModData(player);
        modData.put(RELICS_KEY, handler.serializeNBT(player.level().registryAccess()));
        saveModData(player, modData);
    }

    public static ItemStack getEquippedRelic(Player player, RelicSlotType slotType) {
        return getRelicHandler(player).getStackInSlot(slotType.getIndex());
    }

    public static float getRelicMaxManaBonus(Player player) {
        float total = 0.0f;
        ItemStackHandler handler = getRelicHandler(player);
        for (int i = 0; i < handler.getSlots(); i++) {
            RelicType type = RelicItem.getRelicType(handler.getStackInSlot(i));
            if (type != null) {
                total += type.getMaxManaBonus();
            }
        }
        return total;
    }

    public static float getRelicManaRegenMultiplier(Player player) {
        float multiplier = 1.0f;
        ItemStackHandler handler = getRelicHandler(player);
        for (int i = 0; i < handler.getSlots(); i++) {
            RelicType type = RelicItem.getRelicType(handler.getStackInSlot(i));
            if (type != null) {
                multiplier *= type.getManaRegenMultiplier();
            }
        }
        return multiplier;
    }

    public static float getMaxMana(Player player) {
        return Config.getBaseMaxMana()
                + HousePerks.getMaxManaBonus(player)
                + getRelicMaxManaBonus(player)
                + com.hogwartsmagic.spell.mastery.SpellMasteryData.getMaxManaBonus(player);
    }

    public static float getManaRegenMultiplier(Player player) {
        return HousePerks.getManaRegenMultiplier(player) * getRelicManaRegenMultiplier(player);
    }
}
