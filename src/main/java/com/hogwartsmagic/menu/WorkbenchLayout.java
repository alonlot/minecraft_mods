package com.hogwartsmagic.menu;

/**
 * Shared dimensions and slot positions for wand/broom workbench menus and screens.
 */
public final class WorkbenchLayout {
    public static final int GUI_WIDTH = 300;
    /** Taller window so the recipe panel fits above the player inventory. */
    public static final int GUI_HEIGHT = 220;

    /** Recipe column (right); used when the book is open. */
    public static final int RECIPE_PANEL_WIDTH = 120;
    public static final int RECIPE_PANEL_RIGHT_MARGIN = 8;

    /** Shared Y for all workbench ingredient/result slots (one horizontal row). */
    public static final int CRAFT_ROW_Y = 40;

    /** Broom: four inputs, then result; {@code step} is distance between slot left edges. */
    public static final int BROOM_SLOT_LEFT0 = 22;
    public static final int BROOM_SLOT_STEP_X = 32;
    public static final int BROOM_RESULT_SLOT_X = 146;

    /** Wand: wood, core, catalyst, then result in one row. */
    public static final int WAND_SLOT_LEFT0 = 34;
    public static final int WAND_SLOT_STEP_X = 36;
    public static final int WAND_RESULT_SLOT_X = 154;

    /** First row of player inventory (below crafting + recipe header zone). */
    public static final int PLAYER_INV_Y = 128;
    public static final int HOTBAR_Y = 186;
    public static final int INVENTORY_LABEL_Y = PLAYER_INV_Y - 12;

    private WorkbenchLayout() {}

    public static int broomInputSlotX(int index) {
        return BROOM_SLOT_LEFT0 + index * BROOM_SLOT_STEP_X;
    }

    public static int wandInputSlotX(int index) {
        return WAND_SLOT_LEFT0 + index * WAND_SLOT_STEP_X;
    }

    /** Right edge (GUI-local X) of the brown crafting header bar. */
    public static int craftAreaRight(boolean recipeBookOpen) {
        if (recipeBookOpen) {
            return GUI_WIDTH - RECIPE_PANEL_WIDTH - RECIPE_PANEL_RIGHT_MARGIN;
        }
        return GUI_WIDTH - 12;
    }
}
