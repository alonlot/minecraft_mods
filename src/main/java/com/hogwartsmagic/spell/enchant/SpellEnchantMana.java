package com.hogwartsmagic.spell.enchant;

import com.hogwartsmagic.spell.support.SupportMana;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * What the spell enchantments cost. They are spells bound into things, so a player pays for them out of their own
 * mana (out of mana, the enchantment simply does not work); mobs wearing them are not charged. Creative is free.
 * Each cost is paid when the enchantment acts, not for wearing it.
 */
public final class SpellEnchantMana {
    public static final float WARDING = 2.0f;          // each spell it softens
    public static final float CAVE_INIMICUM = 1.0f;    // each second it hides you from a mob looking
    public static final float ARRESTO = 8.0f;          // each fall it catches
    public static final float DOUSE_SOAK = 1.0f;       // each burn it soaks
    public static final float DOUSE_EXTINGUISH = 6.0f; // putting the fire out
    public static final float ACCIO = 1.0f;            // each stack it pulls
    public static final float REPARO = 2.0f;           // each point it mends
    public static final float LUMOS = 1.0f;            // each refresh of the sight in the dark
    public static final float REVELIO = 2.0f;          // every 2 s while hostiles are near
    public static final float DEPULSO = 4.0f;          // each blow it throws back
    public static final float PROTEGO_MAXIMA = 6.0f;   // each arrow or spell it returns
    public static final float INCENDIO = 1.0f;         // each drop it smelts
    public static final float INCARCEROUS = 8.0f;      // each bind
    public static final float LEVIOSA = 2.0f;          // each half second of hover
    public static final float ALATUS = 2.0f;           // each second of glide
    public static final float PATRONUS = 5.0f;         // each effect it turns away
    public static final float GLACIUS = 2.0f;          // each chilling hit
    public static final float BOMBARDA_MAXIMA = 10.0f; // each shockwave
    public static final float IMPERIO_LEASH = 15.0f;   // leashing a mob
    public static final float IMPERIO_HOLD = 1.0f;     // every 3 s it is held

    private SpellEnchantMana() {
    }

    /**
     * Server side: charges {@code cost} to {@code user} if it is a player. True when the enchantment may act (paid,
     * free, or not a player at all).
     */
    public static boolean pay(Entity user, float cost) {
        if (!(user instanceof ServerPlayer player)) {
            return true;
        }
        return SupportMana.trySpend(player, cost);
    }

    /** Client side: whether the local player's last synced mana covers {@code cost} (for client-run effects). */
    public static boolean clientHas(Player player, float cost) {
        if (player.isCreative()) {
            return true;
        }
        var modData = PlayerMagicData.getModData(player);
        return !modData.contains("Mana") || modData.getFloat("Mana") >= cost;
    }
}
