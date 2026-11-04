package com.hogwartsmagic.util;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.HorcruxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class HorcruxData {
    private static final ResourceLocation HORCRUX_HEALTH_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "horcrux_health_cost");
    public static final String HORCRUX_DIM = "HorcruxDim";
    public static final String HORCRUX_POS = "HorcruxPos";
    public static final String HORCRUX_PENDING = "HorcruxPending";
    public static final String HORCRUX_RITUAL_ACTIVE = "HorcruxRitualActive";
    public static final String HORCRUX_HEALTH_COST = "HorcruxHealthCost";
    public static final String HORCRUX_STORED_INV = "HorcruxStoredInventory";
    public static final String HORCRUX_SELECTED_SLOT = "HorcruxSelectedSlot";
    public static final String HORCRUX_DEATH_HANDLED = "HorcruxDeathHandled";
    public static final String HORCRUX_MAINHAND = "HorcruxMainHand";
    public static final String HORCRUX_OFFHAND = "HorcruxOffhand";
    public static final String HORCRUX_HEAD = "HorcruxHead";
    public static final String HORCRUX_CHEST = "HorcruxChest";
    public static final String HORCRUX_LEGS = "HorcruxLegs";
    public static final String HORCRUX_FEET = "HorcruxFeet";

    private HorcruxData() {
    }

    public static CompoundTag getModData(Player player) {
        return PlayerMagicData.getModData(player);
    }

    public static void saveModData(Player player, CompoundTag modData) {
        PlayerMagicData.saveModData(player, modData);
    }

    public static boolean hasActiveHorcrux(Player player) {
        CompoundTag modData = getModData(player);
        return modData.contains(HORCRUX_DIM) && modData.contains(HORCRUX_POS);
    }

    public static boolean isRitualActive(Player player) {
        return getModData(player).getBoolean(HORCRUX_RITUAL_ACTIVE);
    }

    public static void startRitual(Player player, ServerLevel level, BlockPos pos) {
        CompoundTag modData = getModData(player);
        modData.putString(HORCRUX_DIM, level.dimension().location().toString());
        modData.putLong(HORCRUX_POS, pos.asLong());
        modData.putBoolean(HORCRUX_RITUAL_ACTIVE, true);
        saveModData(player, modData);
    }

    public static void completeRitual(Player player) {
        CompoundTag modData = getModData(player);
        modData.remove(HORCRUX_RITUAL_ACTIVE);
        saveModData(player, modData);
    }

    public static void bindToHorcrux(Player player, ServerLevel level, BlockPos pos) {
        CompoundTag modData = getModData(player);
        modData.putString(HORCRUX_DIM, level.dimension().location().toString());
        modData.putLong(HORCRUX_POS, pos.asLong());
        modData.remove(HORCRUX_RITUAL_ACTIVE);
        saveModData(player, modData);
    }

    public static void clearBinding(Player player) {
        CompoundTag modData = getModData(player);
        modData.remove(HORCRUX_DIM);
        modData.remove(HORCRUX_POS);
        modData.remove(HORCRUX_RITUAL_ACTIVE);
        saveModData(player, modData);
    }

    public static double getHealthCost(Player player) {
        return getModData(player).getDouble(HORCRUX_HEALTH_COST);
    }

    public static void addHealthCost(Player player, double amount) {
        CompoundTag modData = getModData(player);
        modData.putDouble(HORCRUX_HEALTH_COST, Math.max(0.0D, modData.getDouble(HORCRUX_HEALTH_COST) + amount));
        saveModData(player, modData);
    }

    public static void restoreSacrifice(Player player) {
        CompoundTag modData = getModData(player);
        modData.remove(HORCRUX_HEALTH_COST);
        modData.remove(HORCRUX_RITUAL_ACTIVE);
        saveModData(player, modData);
        applyMaxHealthPenalty(player);
    }

    public static void applyMaxHealthPenalty(Player player) {
        var attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        // Runs every player tick: touch the attribute only when the cost changed, or every tick would mark
        // max health dirty and send an attribute packet to the player and everyone tracking them.
        double cost = getHealthCost(player);
        AttributeModifier existing = attribute.getModifier(HORCRUX_HEALTH_MODIFIER_ID);
        if (cost > 0.0D) {
            if (existing == null || existing.amount() != -cost) {
                attribute.removeModifier(HORCRUX_HEALTH_MODIFIER_ID);
                attribute.addTransientModifier(new AttributeModifier(HORCRUX_HEALTH_MODIFIER_ID, -cost, AttributeModifier.Operation.ADD_VALUE));
            }
        } else if (existing != null) {
            attribute.removeModifier(HORCRUX_HEALTH_MODIFIER_ID);
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    public static void markPendingRebirth(Player player) {
        CompoundTag modData = getModData(player);
        modData.putBoolean(HORCRUX_PENDING, true);
        saveModData(player, modData);
    }

    public static boolean isPendingRebirth(Player player) {
        return getModData(player).getBoolean(HORCRUX_PENDING);
    }

    public static void clearPendingRebirth(Player player) {
        CompoundTag modData = getModData(player);
        modData.remove(HORCRUX_PENDING);
        saveModData(player, modData);
    }

    public static void captureInventory(ServerPlayer player) {
        CompoundTag modData = getModData(player);
        ListTag list = new ListTag();
        player.getInventory().save(list);
        modData.put(HORCRUX_STORED_INV, list);
        modData.putInt(HORCRUX_SELECTED_SLOT, player.getInventory().selected);
        modData.putBoolean(HORCRUX_DEATH_HANDLED, true);
        saveDisplayEquipment(modData, player.registryAccess(), player);
        saveModData(player, modData);
    }

    private static void saveDisplayEquipment(CompoundTag modData, HolderLookup.Provider registries, Player player) {
        modData.put(HORCRUX_MAINHAND, player.getMainHandItem().saveOptional(registries));
        modData.put(HORCRUX_OFFHAND, player.getOffhandItem().saveOptional(registries));
        modData.put(HORCRUX_HEAD, player.getItemBySlot(EquipmentSlot.HEAD).saveOptional(registries));
        modData.put(HORCRUX_CHEST, player.getItemBySlot(EquipmentSlot.CHEST).saveOptional(registries));
        modData.put(HORCRUX_LEGS, player.getItemBySlot(EquipmentSlot.LEGS).saveOptional(registries));
        modData.put(HORCRUX_FEET, player.getItemBySlot(EquipmentSlot.FEET).saveOptional(registries));
    }

    public static boolean hasStoredInventory(Player player) {
        return getModData(player).contains(HORCRUX_STORED_INV);
    }

    public static void restoreInventory(ServerPlayer player) {
        CompoundTag modData = getModData(player);
        if (!modData.contains(HORCRUX_STORED_INV)) {
            return;
        }
        Inventory inventory = player.getInventory();
        inventory.clearContent();
        try {
            inventory.load(modData.getList(HORCRUX_STORED_INV, CompoundTag.TAG_COMPOUND));
        } catch (RuntimeException exception) {
            inventory.clearContent();
        }
        inventory.selected = Math.max(0, Math.min(8, modData.getInt(HORCRUX_SELECTED_SLOT)));
        player.containerMenu.broadcastChanges();
        clearStoredInventory(player);
    }

    public static void clearStoredInventory(Player player) {
        CompoundTag modData = getModData(player);
        modData.remove(HORCRUX_STORED_INV);
        modData.remove(HORCRUX_SELECTED_SLOT);
        modData.remove(HORCRUX_DEATH_HANDLED);
        modData.remove(HORCRUX_MAINHAND);
        modData.remove(HORCRUX_OFFHAND);
        modData.remove(HORCRUX_HEAD);
        modData.remove(HORCRUX_CHEST);
        modData.remove(HORCRUX_LEGS);
        modData.remove(HORCRUX_FEET);
        saveModData(player, modData);
    }

    public static boolean isDeathHandled(Player player) {
        return getModData(player).getBoolean(HORCRUX_DEATH_HANDLED);
    }

    public static ItemStack getDisplayStack(Player player, String key, HolderLookup.Provider registries) {
        CompoundTag modData = getModData(player);
        if (!modData.contains(key, CompoundTag.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.parseOptional(registries, modData.getCompound(key));
    }

    public static ServerLevel getBoundLevel(ServerPlayer player) {
        CompoundTag modData = getModData(player);
        if (!modData.contains(HORCRUX_DIM)) {
            return null;
        }
        MinecraftServer server = player.server;
        if (server == null) {
            return null;
        }
        try {
            ResourceLocation id = ResourceLocation.parse(modData.getString(HORCRUX_DIM));
            return server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public static BlockPos getBoundPos(Player player) {
        CompoundTag modData = getModData(player);
        return modData.contains(HORCRUX_POS) ? BlockPos.of(modData.getLong(HORCRUX_POS)) : null;
    }

    public static HorcruxBlockEntity getValidHorcrux(ServerPlayer player) {
        HorcruxBlockEntity horcrux = getOwnedHorcrux(player);
        if (horcrux == null || !horcrux.isActive()) {
            return null;
        }
        return horcrux;
    }

    public static HorcruxBlockEntity getOwnedHorcrux(ServerPlayer player) {
        ServerLevel level = getBoundLevel(player);
        BlockPos pos = getBoundPos(player);
        if (level == null || pos == null) {
            return null;
        }
        if (!(level.getBlockEntity(pos) instanceof HorcruxBlockEntity horcrux)) {
            return null;
        }
        if (!player.getUUID().equals(horcrux.getOwnerUuid())) {
            return null;
        }
        return horcrux;
    }

    public static void reconcileMissingHorcrux(ServerPlayer player) {
        if (getHealthCost(player) <= 0.0D || isPendingRebirth(player)) {
            return;
        }
        // Called every player tick: never load the vessel's chunk (from any distance or dimension) just to look.
        // A vessel in an unloaded chunk is checked again once something loads that chunk.
        ServerLevel boundLevel = getBoundLevel(player);
        BlockPos boundPos = getBoundPos(player);
        if (boundLevel != null && boundPos != null && !boundLevel.isLoaded(boundPos)) {
            return;
        }
        HorcruxBlockEntity horcrux = getOwnedHorcrux(player);
        if (horcrux != null) {
            return;
        }
        restoreSacrifice(player);
        clearBinding(player);
    }

    public static void copyPersistent(Player original, Player clone) {
        CompoundTag persisted = original.getPersistentData().getCompound(PlayerMagicData.PLAYER_PERSISTED).copy();
        clone.getPersistentData().put(PlayerMagicData.PLAYER_PERSISTED, persisted);
    }
}
