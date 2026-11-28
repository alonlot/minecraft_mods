package com.hogwartsmagic.client.enchant;

import com.hogwartsmagic.spell.enchant.SpellEnchantments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/** Client-only looks of the spell enchantments: Revelio's glow and Cave Inimicum's veil. Called from mixins. */
public final class SpellEnchantClient {
    public static final double REVELIO_RANGE = 16.0;

    private static long revelioCheckedAt = Long.MIN_VALUE;
    private static boolean revelioWorn;

    private SpellEnchantClient() {
    }

    /** Revelio: hostile mobs near a player wearing it glow through walls, for that player only. */
    public static boolean revelioGlows(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !(entity instanceof Enemy) || !(entity instanceof LivingEntity living)
                || !living.isAlive() || entity.isInvisible()) {
            return false;
        }
        long now = mc.level.getGameTime();
        if (now != revelioCheckedAt) {
            revelioCheckedAt = now;
            revelioWorn = SpellEnchantments.level(player, SpellEnchantments.REVELIO, EquipmentSlot.HEAD) > 0;
        }
        return revelioWorn && entity.distanceToSqr(player) < REVELIO_RANGE * REVELIO_RANGE;
    }

    /** Cave Inimicum: the body alpha to draw {@code entity} with, 1 for none. */
    public static float veilAlpha(LivingEntity entity) {
        int levels = SpellEnchantments.armourLevels(entity, SpellEnchantments.CAVE_INIMICUM);
        return levels <= 0 ? 1.0f : SpellEnchantments.caveInimicumAlpha(levels);
    }
}
