package com.hogwartsmagic.pensieve;

/** When a hurt body tears its viewer out of the memory. */
public final class YankRule {
    private YankRule() {
    }

    /** Below one third of max health the viewer is yanked out. */
    public static boolean shouldYank(float health, float maxHealth) {
        return health < maxHealth / 3f;
    }
}
