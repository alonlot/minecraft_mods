package com.hogwartsmagic.menu;

/**
 * Shared dimensions and slot positions for wand/broom workbench menus and screens. Common code (the menus
 * place their slots from it on both sides), so it must stay free of client classes.
 */
public final class WorkbenchLayout {
    /** Vanilla container width, so the 9-wide player inventory sits flush with the panel edges. */
    public static final int GUI_WIDTH = 176;
    public static final int GUI_HEIGHT = 166;

    /** Recipe book side panel, drawn to the right of the main panel when open. */
    public static final int RECIPE_PANEL_WIDTH = 128;
    public static final int RECIPE_PANEL_GAP = 4;

    /** Recessed crafting well behind the ingredient row. */
    public static final int CRAFT_AREA_TOP = 22;
    public static final int CRAFT_AREA_BOTTOM = 69;
    /** Item Y of every ingredient/result slot (one horizontal row). */
    public static final int CRAFT_ROW_Y = 32;
    public static final int STATUS_Y = 58;

    /** Distance between ingredient slots' left edges (both benches). */
    public static final int SLOT_STEP_X = 26;
    /** Gap from an input slot's item edge to the arrow, and the arrow's width. */
    public static final int ARROW_GAP = 4;
    public static final int ARROW_W = 22;
    public static final int ARROW_H = 15;
    /** The result frame is 26 px with the item centred, i.e. a 5 px inset. */
    public static final int RESULT_FRAME = 26;

    /** Broom: four inputs, then result. */
    public static final int BROOM_SLOT_LEFT0 = 13;
    public static final int BROOM_RESULT_SLOT_X = 143;

    /** Wand: wood, core, catalyst, then result. */
    public static final int WAND_SLOT_LEFT0 = 26;
    public static final int WAND_RESULT_SLOT_X = 130;

    public static final int INVENTORY_LABEL_Y = 73;
    public static final int PLAYER_INV_Y = 84;
    public static final int HOTBAR_Y = PLAYER_INV_Y + 58;

    private WorkbenchLayout() {}

    public static int broomInputSlotX(int index) {
        return BROOM_SLOT_LEFT0 + index * SLOT_STEP_X;
    }

    public static int wandInputSlotX(int index) {
        return WAND_SLOT_LEFT0 + index * SLOT_STEP_X;
    }
}
