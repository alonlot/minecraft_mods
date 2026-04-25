package com.hogwartsmagic.sorting;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

public final class HousePerks {
    private static final String HOUSE_KEY = "SortingHouse";
    private static final String LAST_HURT_TICK_KEY = "LastHurtGameTime";
    private static final ResourceLocation HUFFLEPUFF_HEALTH_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "hufflepuff_health_bonus");

    private HousePerks() {
    }

    public static HogwartsHouse getHouse(Player player) {
        CompoundTag persisted = player.getPersistentData().getCompound("PlayerPersisted");
        CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
        String raw = modData.getString(HOUSE_KEY);
        return raw.isEmpty() ? null : HogwartsHouse.fromModelText(raw);
    }

    public static float getMaxManaBonus(Player player) {
        HogwartsHouse house = getHouse(player);
        if (house == HogwartsHouse.RAVENCLAW) {
            return 25.0f;
        }
        return 0.0f;
    }

    public static float getManaRegenMultiplier(Player player) {
        HogwartsHouse house = getHouse(player);
        if (house == HogwartsHouse.RAVENCLAW) {
            return 1.20f;
        }
        return 1.0f;
    }

    public static float getCooldownMultiplier(Player player) {
        HogwartsHouse house = getHouse(player);
        if (house == HogwartsHouse.SLYTHERIN) {
            return 0.85f;
        }
        return 1.0f;
    }

    public static float getBeamSpeedMultiplier(Player player) {
        HogwartsHouse house = getHouse(player);
        if (house == HogwartsHouse.SLYTHERIN) {
            return 1.20f;
        }
        return 1.0f;
    }

    public static float getSpellDamageMultiplier(Entity caster) {
        if (caster instanceof Player player && getHouse(player) == HogwartsHouse.GRYFFINDOR) {
            return 1.10f;
        }
        return 1.0f;
    }

    public static float getMeleeDamageMultiplier(Player attacker) {
        return getHouse(attacker) == HogwartsHouse.GRYFFINDOR ? 1.10f : 1.0f;
    }

    public static void applyHouseAttributes(ServerPlayer player) {
        var attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }

        attribute.removeModifier(HUFFLEPUFF_HEALTH_MODIFIER_ID);
        if (getHouse(player) == HogwartsHouse.HUFFLEPUFF) {
            AttributeModifier modifier = new AttributeModifier(HUFFLEPUFF_HEALTH_MODIFIER_ID, 4.0, AttributeModifier.Operation.ADD_VALUE);
            attribute.addTransientModifier(modifier);
        }

        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    public static void markRecentlyHurt(Player player, long gameTime) {
        CompoundTag persisted = player.getPersistentData().getCompound("PlayerPersisted");
        CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
        modData.putLong(LAST_HURT_TICK_KEY, gameTime);
        persisted.put(ExampleMod.MODID, modData);
        player.getPersistentData().put("PlayerPersisted", persisted);
    }

    public static boolean shouldHufflepuffHeal(Player player, long gameTime) {
        if (getHouse(player) != HogwartsHouse.HUFFLEPUFF) {
            return false;
        }
        CompoundTag persisted = player.getPersistentData().getCompound("PlayerPersisted");
        CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
        long lastHurt = modData.contains(LAST_HURT_TICK_KEY) ? modData.getLong(LAST_HURT_TICK_KEY) : -10000L;
        return gameTime - lastHurt >= 160L;
    }
}
