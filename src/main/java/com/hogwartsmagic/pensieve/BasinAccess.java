package com.hogwartsmagic.pensieve;

import java.util.UUID;

/** Who may lift a memory back out of a Pensieve. Pure rules, no Minecraft types. */
public final class BasinAccess {
    private BasinAccess() {
    }

    /** The pourer or an operator always; anyone else only from a public basin nobody is currently inside. */
    public static boolean canTakeOut(UUID pourer, UUID who, boolean op, boolean publicBasin, boolean anyoneInside) {
        if (op || (pourer != null && pourer.equals(who))) {
            return true;
        }
        return publicBasin && !anyoneInside;
    }
}
