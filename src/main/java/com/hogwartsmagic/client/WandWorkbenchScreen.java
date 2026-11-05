package com.hogwartsmagic.client;

import com.hogwartsmagic.menu.WandWorkbenchMenu;
import com.hogwartsmagic.menu.WorkbenchLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class WandWorkbenchScreen extends AbstractContainerScreen<WandWorkbenchMenu> {
    private static final float LABEL_SCALE = 0.62f;
    private static final float RECIPE_TEXT_SCALE = 0.44f;
    private static final int RECIPE_PANEL_TOP = 18;
    private static final int RECIPE_PANEL_BODY_H = 108;
    private static final String[] RECIPE_KEYS = {
            "gui.hogwartsmagic.wand_workbench.recipe.1",
            "gui.hogwartsmagic.wand_workbench.recipe.2",
            "gui.hogwartsmagic.wand_workbench.recipe.3"
    };

    private boolean recipeBookOpen;
    private Button recipeBookButton;

    public WandWorkbenchScreen(WandWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WorkbenchLayout.GUI_WIDTH;
        this.imageHeight = WorkbenchLayout.GUI_HEIGHT;
        this.inventoryLabelY = WorkbenchLayout.INVENTORY_LABEL_Y;
    }

    @Override
    protected void init() {
        super.init();
        int btnW = 32;
        int btnH = 14;
        this.recipeBookButton = Button.builder(Component.translatable("gui.hogwartsmagic.recipe_book"), b -> this.recipeBookOpen = !this.recipeBookOpen)
                .bounds(this.leftPos + this.imageWidth - btnW - 6, this.topPos + 4, btnW, btnH)
                .tooltip(Tooltip.create(Component.translatable("gui.hogwartsmagic.recipe_book.tooltip")))
                .build();
        this.addRenderableWidget(this.recipeBookButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.positionRecipeBookButton();
        // AbstractContainerScreen.render already draws renderTransparentBackground + renderBg once;
        // an explicit renderBackground call here drew the dim and the panel a second time.
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void positionRecipeBookButton() {
        int btnW = this.recipeBookButton.getWidth();
        int x = this.recipeBookOpen
                ? this.leftPos + WorkbenchLayout.craftAreaRight(true) - btnW - 8
                : this.leftPos + this.imageWidth - btnW - 6;
        this.recipeBookButton.setX(x);
        this.recipeBookButton.setY(this.topPos + 4);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        int w = this.imageWidth;
        int craftRight = WorkbenchLayout.craftAreaRight(this.recipeBookOpen);

        guiGraphics.fill(left, top, left + w, top + this.imageHeight, 0xE11C1330);
        guiGraphics.fill(left + 1, top + 1, left + w - 1, top + this.imageHeight - 1, 0xCC261844);

        guiGraphics.fill(left + 14, top + 12, left + craftRight, top + 72, 0xAA160F2A);
        guiGraphics.fill(left + 14, top + 12, left + craftRight, top + 13, 0xFFD4AF37);
        guiGraphics.fill(left + 14, top + 71, left + craftRight, top + 72, 0xFFD4AF37);

        int rowY = top + WorkbenchLayout.CRAFT_ROW_Y;
        for (int i = 0; i < 3; i++) {
            drawSlot(guiGraphics, left + WorkbenchLayout.wandInputSlotX(i), rowY);
        }
        drawCraftArrow(guiGraphics, left + WorkbenchLayout.wandInputSlotX(2) + 20, left + WorkbenchLayout.WAND_RESULT_SLOT_X - 3, rowY + 9);
        drawSlot(guiGraphics, left + WorkbenchLayout.WAND_RESULT_SLOT_X, rowY);

        int craftTotal = this.menu.getCraftTotalTicks();
        if (craftTotal > 0) {
            int ax0 = left + WorkbenchLayout.wandInputSlotX(2) + 22;
            int ax1 = left + WorkbenchLayout.WAND_RESULT_SLOT_X - 5;
            int barY = rowY + 20;
            int barH = 4;
            int barW = Math.max(0, ax1 - ax0);
            int rem = this.menu.getCraftTicksRemaining();
            float frac = 1f - rem / (float) craftTotal;
            int fill = Math.round(barW * Mth.clamp(frac, 0f, 1f));
            guiGraphics.fill(ax0, barY, ax1, barY + barH, 0x66000000);
            guiGraphics.fill(ax0, barY, ax0 + fill, barY + barH, 0xFFD4AF37);
            guiGraphics.fill(ax0, barY, ax1, barY + 1, 0xFF8B7355);
            guiGraphics.fill(ax0, barY + barH - 1, ax1, barY + barH, 0xFF8B7355);
        }

        if (this.recipeBookOpen) {
            int px = left + w - WorkbenchLayout.RECIPE_PANEL_WIDTH - WorkbenchLayout.RECIPE_PANEL_RIGHT_MARGIN;
            int py = top + RECIPE_PANEL_TOP;
            renderRecipePanel(guiGraphics, px, py, "gui.hogwartsmagic.wand_workbench.recipes_title", RECIPE_KEYS, 0xF5E6BE, 0xD6C4E8);
        }
    }

    private void renderRecipePanel(GuiGraphics g, int px, int py, String titleKey, String[] keys, int titleColor, int bodyColor) {
        int pw = WorkbenchLayout.RECIPE_PANEL_WIDTH;
        g.fill(px, py, px + pw, py + RECIPE_PANEL_BODY_H, 0xEE1A0F28);
        g.fill(px, py, px + pw, py + 1, 0xFFD4AF37);
        g.fill(px, py + RECIPE_PANEL_BODY_H - 1, px + pw, py + RECIPE_PANEL_BODY_H, 0xFFD4AF37);
        GuiRenderUtil.drawScaledString(g, this.font, Component.translatable(titleKey), px + 5, py + 5, titleColor, 0.68f);
        float y = py + 20;
        int innerW = pw - 10;
        for (String key : keys) {
            float h = GuiRenderUtil.drawScaledMultiline(g, this.font, Component.translatable(key), px + 5, y, bodyColor, RECIPE_TEXT_SCALE, innerW);
            y += h + 6;
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        GuiRenderUtil.drawScaledString(guiGraphics, this.font, this.title, 8, 6, 0xF5E6BE, 0.85f);
        GuiRenderUtil.drawScaledString(guiGraphics, this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xEAD7FF, 0.9f);

        float labelY = WorkbenchLayout.CRAFT_ROW_Y - 11;
        GuiRenderUtil.drawScaledStringCentered(guiGraphics, this.font, Component.translatable("gui.hogwartsmagic.wand_workbench.slot.wood"),
                WorkbenchLayout.wandInputSlotX(0) + 9, labelY, 0xD9BE7E, LABEL_SCALE);
        GuiRenderUtil.drawScaledStringCentered(guiGraphics, this.font, Component.translatable("gui.hogwartsmagic.wand_workbench.slot.core"),
                WorkbenchLayout.wandInputSlotX(1) + 9, labelY, 0xD9BE7E, LABEL_SCALE);
        GuiRenderUtil.drawScaledStringCentered(guiGraphics, this.font, Component.translatable("gui.hogwartsmagic.wand_workbench.slot.catalyst"),
                WorkbenchLayout.wandInputSlotX(2) + 9, labelY, 0xD9BE7E, LABEL_SCALE);
        GuiRenderUtil.drawScaledStringCentered(guiGraphics, this.font, Component.translatable("gui.hogwartsmagic.slot.result"),
                WorkbenchLayout.WAND_RESULT_SLOT_X + 9, labelY, 0xD9BE7E, LABEL_SCALE);

        if (this.menu.getCraftTotalTicks() > 0) {
            int midX = (WorkbenchLayout.wandInputSlotX(2) + WorkbenchLayout.WAND_RESULT_SLOT_X) / 2 + 9;
            int ty = WorkbenchLayout.CRAFT_ROW_Y + 14;
            GuiRenderUtil.drawScaledStringCentered(guiGraphics, this.font,
                    Component.translatable("gui.hogwartsmagic.wand_workbench.crafting"), midX, ty, 0xE8D5FF, 0.5f);
        }
    }

    private static void drawCraftArrow(GuiGraphics g, int x0, int x1, int yMid) {
        if (x1 <= x0 + 2) {
            return;
        }
        g.fill(x0, yMid, x1, yMid + 1, 0xFFE4D3A5);
        g.fill(x1 - 1, yMid - 3, x1 + 1, yMid + 4, 0xFFE4D3A5);
        g.fill(x1 - 4, yMid - 2, x1, yMid + 3, 0xFFE4D3A5);
    }

    private static void drawSlot(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.fill(x, y, x + 18, y + 18, 0xFF8B7A57);
        guiGraphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF3E2F20);
        guiGraphics.fill(x + 2, y + 2, x + 16, y + 16, 0xFF161616);
    }
}
