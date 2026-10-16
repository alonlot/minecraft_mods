package com.hogwartsmagic.assignment;

import java.util.Locale;

/**
 * Everything an assignment objective can ask for. Stored objectives count up as events arrive; live
 * objectives (advancement, deliver, know_spell) are checked against the player's state each time they
 * are read, so they can never drift out of date. Pure Java: safe on a dedicated server.
 */
public enum ObjectiveType {
    KILL(false),
    HURT_BY(false),
    CAST(false),
    PLACE(false),
    BREAK(false),
    LOOT_CHEST(false),
    CRAFT(false),
    SMELT(false),
    BREW(false),
    EAT(false),
    FISH(false),
    BREED(false),
    REPAIR(false),
    BLOCK_SHIELD(false),
    MOB_KILLS_MOB(false),
    STAND(false),
    VISIT(false),
    FALL(false),
    DUEL_WIN(false),
    FLOO(false),
    ADVANCEMENT(true),
    DELIVER(true),
    KNOW_SPELL(true);

    private final boolean live;

    ObjectiveType(boolean live) {
        this.live = live;
    }

    public boolean isLive() {
        return this.live;
    }

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /** Null when the id names no type. */
    public static ObjectiveType byId(String id) {
        if (id == null) {
            return null;
        }
        for (ObjectiveType type : values()) {
            if (type.id().equals(id.trim().toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        return null;
    }
}
