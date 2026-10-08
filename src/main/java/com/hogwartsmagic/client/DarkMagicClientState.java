package com.hogwartsmagic.client;

import com.hogwartsmagic.spell.dark.DarkRules;

/**
 * Client only: this player's own Darkness as the server last sent it (0 with Dark Magic off on the server).
 * Never another player's. A static, so it survives respawn and dimension change; cleared on logout.
 */
public final class DarkMagicClientState {
    private static volatile float darkness;

    private DarkMagicClientState() {
    }

    public static void accept(float value) {
        darkness = DarkRules.sanitize(value);
        // Phase 3: SpellCosts (common code) reads this copy for the client's own dark mana discount.
        com.hogwartsmagic.spell.dark.DarkRewards.acceptClientTier(DarkRules.tier(darkness));
    }

    public static void clear() {
        darkness = DarkRules.MIN_DARKNESS;
        com.hogwartsmagic.spell.dark.DarkRewards.acceptClientTier(DarkRules.CLEAN);
    }

    public static float darkness() {
        return darkness;
    }

    public static int tier() {
        return DarkRules.tier(darkness);
    }
}
