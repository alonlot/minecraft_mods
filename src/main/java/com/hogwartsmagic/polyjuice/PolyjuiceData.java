package com.hogwartsmagic.polyjuice;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.PolyjuicePotionItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;
import java.util.UUID;

public final class PolyjuiceData {

    public static final String MOD_DATA = ExampleMod.MODID;
    public static final String STACK_SAMPLE_TAG = "PolyjuiceSample";
    public static final String STACK_TARGET_TAG = "PolyjuiceTarget";
    public static final String PLAYER_PENDING_TAG = "PolyjuicePending";
    public static final String PLAYER_PENDING_TICKS = "PolyjuicePendingTicks";
    /** Mob effect duration (ticks) for the disguise once transformation finishes. */
    public static final String PLAYER_PENDING_DISGUISE_TICKS = "PolyjuicePendingDisguiseTicks";
    public static final String PLAYER_ACTIVE_TAG = "PolyjuiceActive";
    public static final String TARGET_KIND = "Kind";
    public static final String KIND_PLAYER = "player";
    public static final String KIND_MOB = "mob";
    public static final String TARGET_NAME = "Name";
    public static final String PLAYER_UUID = "PlayerUUID";
    public static final String ENTITY_TYPE = "EntityType";

    private PolyjuiceData() {
    }

    public static CompoundTag createSample(LivingEntity target) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TARGET_NAME, target.getName().getString());
        if (target instanceof Player player) {
            tag.putString(TARGET_KIND, KIND_PLAYER);
            tag.putUUID(PLAYER_UUID, player.getUUID());
            tag.putString(ENTITY_TYPE, EntityType.PLAYER.builtInRegistryHolder().key().location().toString());
        } else {
            tag.putString(TARGET_KIND, KIND_MOB);
            ResourceLocation id = EntityType.getKey(target.getType());
            if (id != null) {
                tag.putString(ENTITY_TYPE, id.toString());
            }
        }
        return tag;
    }

    public static void setSample(ItemStack stack, CompoundTag sample) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(STACK_SAMPLE_TAG, sample.copy()));
    }

    public static CompoundTag getSample(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (!data.getUnsafe().contains(STACK_SAMPLE_TAG, CompoundTag.TAG_COMPOUND)) {
            return new CompoundTag();
        }
        return data.getUnsafe().getCompound(STACK_SAMPLE_TAG).copy();
    }

    public static boolean hasSample(ItemStack stack) {
        return !getSample(stack).isEmpty();
    }

    public static void setPolyjuiceTarget(ItemStack stack, CompoundTag sample) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(STACK_TARGET_TAG, sample.copy()));
    }

    public static CompoundTag getPolyjuiceTarget(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (!data.getUnsafe().contains(STACK_TARGET_TAG, CompoundTag.TAG_COMPOUND)) {
            return new CompoundTag();
        }
        return data.getUnsafe().getCompound(STACK_TARGET_TAG).copy();
    }

    public static boolean hasPolyjuiceTarget(ItemStack stack) {
        return !getPolyjuiceTarget(stack).isEmpty();
    }

    public static CompoundTag getModData(Player player) {
        CompoundTag persisted = player.getPersistentData().getCompound("PlayerPersisted");
        CompoundTag modData = persisted.getCompound(MOD_DATA);
        persisted.put(MOD_DATA, modData);
        player.getPersistentData().put("PlayerPersisted", persisted);
        return modData;
    }

    public static void putModData(Player player, CompoundTag modData) {
        CompoundTag persisted = player.getPersistentData().getCompound("PlayerPersisted");
        persisted.put(MOD_DATA, modData);
        player.getPersistentData().put("PlayerPersisted", persisted);
    }

    public static void setPending(Player player, CompoundTag sample, int ticks) {
        setPending(player, sample, ticks, PolyjuicePotionItem.BASE_DISGUISE_DURATION_TICKS);
    }

    public static void setPending(Player player, CompoundTag sample, int ticks, int disguiseEffectTicks) {
        CompoundTag modData = getModData(player);
        modData.put(PLAYER_PENDING_TAG, sample.copy());
        modData.putInt(PLAYER_PENDING_TICKS, ticks);
        modData.putInt(PLAYER_PENDING_DISGUISE_TICKS, disguiseEffectTicks);
        modData.remove(PLAYER_ACTIVE_TAG);
        putModData(player, modData);
    }

    public static CompoundTag getPending(Player player) {
        return getModData(player).getCompound(PLAYER_PENDING_TAG).copy();
    }

    public static int getPendingTicks(Player player) {
        return getModData(player).getInt(PLAYER_PENDING_TICKS);
    }

    public static int getPendingDisguiseTicks(Player player) {
        int v = getModData(player).getInt(PLAYER_PENDING_DISGUISE_TICKS);
        return v > 0 ? v : PolyjuicePotionItem.BASE_DISGUISE_DURATION_TICKS;
    }

    public static void setPendingTicks(Player player, int ticks) {
        CompoundTag modData = getModData(player);
        if (ticks <= 0) {
            modData.remove(PLAYER_PENDING_TICKS);
            modData.remove(PLAYER_PENDING_TAG);
            modData.remove(PLAYER_PENDING_DISGUISE_TICKS);
        } else {
            modData.putInt(PLAYER_PENDING_TICKS, ticks);
        }
        putModData(player, modData);
    }

    public static void activate(Player player, CompoundTag sample) {
        CompoundTag modData = getModData(player);
        modData.put(PLAYER_ACTIVE_TAG, sample.copy());
        modData.remove(PLAYER_PENDING_TAG);
        modData.remove(PLAYER_PENDING_TICKS);
        modData.remove(PLAYER_PENDING_DISGUISE_TICKS);
        putModData(player, modData);
    }

    public static CompoundTag getActive(Player player) {
        return getModData(player).getCompound(PLAYER_ACTIVE_TAG).copy();
    }

    public static boolean hasActive(Player player) {
        return !getActive(player).isEmpty();
    }

    public static void clearAll(Player player) {
        CompoundTag modData = getModData(player);
        modData.remove(PLAYER_PENDING_TAG);
        modData.remove(PLAYER_PENDING_TICKS);
        modData.remove(PLAYER_PENDING_DISGUISE_TICKS);
        modData.remove(PLAYER_ACTIVE_TAG);
        putModData(player, modData);
    }

    public static boolean isPlayerSample(CompoundTag sample) {
        return KIND_PLAYER.equals(sample.getString(TARGET_KIND)) && sample.hasUUID(PLAYER_UUID);
    }

    public static boolean isMobSample(CompoundTag sample) {
        return KIND_MOB.equals(sample.getString(TARGET_KIND)) && sample.contains(ENTITY_TYPE);
    }

    public static Component describe(CompoundTag sample) {
        if (sample.isEmpty()) {
            return Component.translatable("item." + ExampleMod.MODID + ".polyjuice_sample.empty");
        }
        return Component.literal(sample.getString(TARGET_NAME));
    }

    public static Optional<EntityType<?>> getEntityType(CompoundTag sample) {
        if (!sample.contains(ENTITY_TYPE)) {
            return Optional.empty();
        }
        return EntityType.byString(sample.getString(ENTITY_TYPE));
    }

    public static UUID getPlayerUuid(CompoundTag sample) {
        return sample.hasUUID(PLAYER_UUID) ? sample.getUUID(PLAYER_UUID) : new UUID(0L, 0L);
    }
}
