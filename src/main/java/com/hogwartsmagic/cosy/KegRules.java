package com.hogwartsmagic.cosy;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The butterbeer keg's rules, apart from the block. A batch is sugar, a honey bottle and wheat; once all three are
 * in it ages for {@link #AGE_TICKS} (from a stored start time, so a keg in an unloaded chunk still ages) and then
 * pours {@link #SERVINGS} tankards. The block state {@code fill} is 0 empty, 1 some ingredients, 2 aging, 3 ready.
 */
public final class KegRules {
    public static final int AGE_TICKS = 6000;
    public static final int SERVINGS = 8;
    /** The stored start time of a batch that has not begun. */
    public static final long NOT_STARTED = -1L;

    public static final int SUGAR = 0;
    public static final int HONEY = 1;
    public static final int WHEAT = 2;

    private KegRules() {
    }

    /** Which ingredient slot an item fills (SUGAR, HONEY, WHEAT), or -1. Needs the item registry, so game side only. */
    public static int ingredientIndex(Item item) {
        if (item == Items.SUGAR) {
            return SUGAR;
        }
        if (item == Items.HONEY_BOTTLE) {
            return HONEY;
        }
        return item == Items.WHEAT ? WHEAT : -1;
    }

    public static boolean isIngredient(Item item) {
        return ingredientIndex(item) >= 0;
    }

    public static boolean complete(boolean sugar, boolean honey, boolean wheat) {
        return sugar && honey && wheat;
    }

    /** True once a batch started at {@code brewStart} has aged long enough at game time {@code now}. */
    public static boolean ready(long brewStart, long now) {
        return brewStart != NOT_STARTED && now - brewStart >= AGE_TICKS;
    }

    /** The block-state fill: 0 empty, 1 some ingredients, 2 aging, 3 ready to pour. */
    public static int fill(boolean sugar, boolean honey, boolean wheat, long brewStart, int servings, long now) {
        if (complete(sugar, honey, wheat)) {
            if (servings <= 0) {
                return 0;
            }
            return ready(brewStart, now) ? 3 : 2;
        }
        return sugar || honey || wheat ? 1 : 0;
    }
}
