package com.hogwartsmagic.spell.dark;

/**
 * When the two Dark Magic advancements are granted (Phase 4), free of Minecraft so it is unit-tested.
 *
 * DarkMagicData.write calls this after every write (gain, decay, deed, redemption, command) with the effective tier
 * the write left. "A Torn Soul" is granted on any write that leaves the player Lost, and the persisted flag
 * KEY_WAS_LOST is set. "Redemption" is granted on a write that leaves the player Clean while that flag is set, and the
 * flag clears, so a second journey needs Lost again. The tiers in between keep the flag. With enableDarkMagic off
 * every tier reads Clean, so nothing is granted and the flag is kept.
 */
public final class DarkAdvancementRules {
    /** Advancement path inside the hogwartsmagic namespace: reach Lost. */
    public static final String TORN_SOUL = "hp/torn_soul";
    /** Advancement path inside the hogwartsmagic namespace: from Lost back to Clean. */
    public static final String REDEMPTION = "hp/redemption";
    /** PlayerPersisted/hogwartsmagic boolean: the player has been Lost since they were last Clean. */
    public static final String KEY_WAS_LOST = "DarkMagicWasLost";

    /** What one write grants, and the flag to store after it. */
    public record Outcome(boolean grantTornSoul, boolean grantRedemption, boolean wasLost) {
        /** True when the stored flag must be rewritten. */
        public boolean flagChanged(boolean before) {
            return this.wasLost != before;
        }
    }

    private DarkAdvancementRules() {
    }

    public static Outcome afterWrite(boolean enabled, int newTier, boolean wasLost) {
        if (!enabled) {
            return new Outcome(false, false, wasLost);
        }
        int tier = Math.max(DarkRules.CLEAN, Math.min(DarkRules.LOST, newTier));
        if (tier == DarkRules.LOST) {
            return new Outcome(true, false, true);
        }
        if (tier == DarkRules.CLEAN && wasLost) {
            return new Outcome(false, true, false);
        }
        return new Outcome(false, false, wasLost);
    }
}
