package com.hogwartsmagic.spell.dark;

import com.hogwartsmagic.spell.SpellType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Dark Magic rewards (Phase 3), the glue. Every reward reads the tier (DarkCosts.tierOf on the server, so
 * enableDarkMagic off reads Clean); the numbers live in DarkRewardRules. The client computes its own mana cost for the
 * Grimoire line and the "Not enough mana!" check, so it keeps a copy of its own synced tier here (filled by
 * client/DarkMagicClientState), read only for a player on a client level.
 */
public final class DarkRewards {
    private static volatile int clientTier = DarkRules.CLEAN;

    private DarkRewards() {
    }

    /** From client/DarkMagicClientState only: this client player's own tier, as the server last sent it. */
    public static void acceptClientTier(int tier) {
        clientTier = tier;
    }

    /** The tier a mana cost reads: the server's for a server player, the synced copy on the client, else Clean. */
    public static int costTier(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return DarkMagicData.getTier(serverPlayer);
        }
        return player.level().isClientSide() ? clientTier : DarkRules.CLEAN;
    }

    /** 0.85 for a dark spell at Corrupted or Lost, else 1. A spell that is not dark reads no tier at all. */
    public static float manaMultiplier(Player player, SpellType spell) {
        if (!DarkRules.isDark(spell.getId())) {
            return 1.0f;
        }
        return DarkRewardRules.manaMultiplier(spell.getId(), costTier(player));
    }

    /** A Corrupted or Lost player under a Dark Mark gets the Death Eaters' buffs. */
    public static boolean darkMarkBuffs(Entity entity) {
        return DarkRewardRules.darkMarkBuffs(DarkCosts.tierOf(entity));
    }

    /** Death Eaters do not pick a Lost player as a target (the hurt-by goal still answers a strike). */
    public static boolean deathEatersIgnore(Entity entity) {
        return DarkRewardRules.deathEatersNeutral(DarkCosts.tierOf(entity));
    }

    /** A Morsmordre sky raid this player starts calls an escort instead. */
    public static boolean earnsEscort(ServerPlayer player) {
        return DarkRewardRules.morsmordreEscort(DarkMagicData.getTier(player));
    }
}
