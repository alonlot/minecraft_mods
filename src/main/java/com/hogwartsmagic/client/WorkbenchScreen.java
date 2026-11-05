package com.hogwartsmagic.client;

import com.hogwartsmagic.client.ThemedGui.Theme;
import com.hogwartsmagic.menu.WorkbenchLayout;
import static com.hogwartsmagic.client.ThemedGui.drawPanel;
import static com.hogwartsmagic.client.ThemedGui.drawSlot;
import static com.hogwartsmagic.client.ThemedGui.drawWell;
import static com.hogwartsmagic.client.ThemedGui.pulse;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Shared look for the wand and broom benches: a bevelled themed panel, a recessed crafting well with
 * ghost hints in empty slots, a furnace-style progress arrow, a framed result slot, real inventory slot
 * frames, and a visual recipe book that docks to the right of the panel.
 *
 * <p>Menu slot order is assumed to be: inputs {@code 0..inputCount-1}, then the result, then the player.
 */
public abstract class WorkbenchScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {

    /**
     * One recipe for the book and the ghost hints. Each input is the list of items that slot accepts for
     * this recipe (an empty list means the slot must stay empty); a stack's count is the minimum needed.
     */
    protected record BenchRecipe(List<List<ItemStack>> inputs, List<ItemStack> results, @Nullable Component note) {}

    private static final int BOOK_ROW_H = 21;
    private static final int BOOK_TOP = 19;

    private boolean bookOpen;
    private BookButton bookButton;
    private ItemStack hoveredBookStack = ItemStack.EMPTY;
    @Nullable
    private Component hoveredBookNote;

    protected WorkbenchScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WorkbenchLayout.GUI_WIDTH;
        this.imageHeight = WorkbenchLayout.GUI_HEIGHT;
        this.inventoryLabelY = WorkbenchLayout.INVENTORY_LABEL_Y;
    }

    protected abstract Theme theme();

    protected abstract int inputCount();

    /** GUI-local item X of input slot {@code i} (the menu's slot X). */
    protected abstract int inputSlotX(int i);

    protected abstract int resultSlotX();

    protected abstract Component slotName(int i);

    /** Extra grey tooltip line for an empty input slot, or null. */
    @Nullable
    protected Component slotHint(int i) {
        return null;
    }

    protected abstract List<BenchRecipe> recipes();

    protected abstract int craftTotalTicks();

    protected abstract int craftTicksRemaining();

    protected abstract Component craftingText();

    /** Shown when the inputs complete a recipe but nothing is crafting yet. */
    protected abstract Component readyText();

    protected abstract Component bookTitle();

    protected abstract Component bookHint();

    /** What the current inputs would make, for the result ghost; empty when they make nothing. */
    protected ItemStack predictedResult() {
        BenchRecipe recipe = this.matchedRecipe();
        return recipe == null || recipe.results().isEmpty() ? ItemStack.EMPTY : recipe.results().get(0);
    }

    protected final ItemStack inputStack(int i) {
        return this.menu.slots.get(i).getItem();
    }

    protected final ItemStack resultStack() {
        return this.menu.slots.get(this.inputCount()).getItem();
    }

    /** The recipe the current inputs complete exactly, or null. */
    @Nullable
    protected final BenchRecipe matchedRecipe() {
        for (BenchRecipe recipe : this.recipes()) {
            if (this.inputsFit(recipe, true)) {
                return recipe;
            }
        }
        return null;
    }

    /** True when every filled input fits {@code recipe}; with {@code exact}, the empty ones must be empty there too. */
    private boolean inputsFit(BenchRecipe recipe, boolean exact) {
        for (int i = 0; i < this.inputCount(); i++) {
            ItemStack have = this.inputStack(i);
            List<ItemStack> options = recipe.inputs().get(i);
            if (have.isEmpty()) {
                if (exact && !options.isEmpty()) {
                    return false;
                }
                continue;
            }
            if (!accepts(options, have, exact)) {
                return false;
            }
        }
        return true;
    }

    private static boolean accepts(List<ItemStack> options, ItemStack have, boolean checkCount) {
        for (ItemStack option : options) {
            if (have.is(option.getItem()) && (!checkCount || have.getCount() >= option.getCount())) {
                return true;
            }
        }
        return false;
    }

    /** Items slot {@code i} could take next: from recipes the filled slots still fit, else from all of them. */
    private List<ItemStack> ghostOptions(int i) {
        List<ItemStack> out = new ArrayList<>();
        for (BenchRecipe recipe : this.recipes()) {
            if (this.inputsFit(recipe, false)) {
                addDistinct(out, recipe.inputs().get(i));
            }
        }
        if (out.isEmpty()) {
            for (BenchRecipe recipe : this.recipes()) {
                addDistinct(out, recipe.inputs().get(i));
            }
        }
        return out;
    }

    private List<ItemStack> allOptions(int i) {
        List<ItemStack> out = new ArrayList<>();
        for (BenchRecipe recipe : this.recipes()) {
            addDistinct(out, recipe.inputs().get(i));
        }
        return out;
    }

    private static void addDistinct(List<ItemStack> into, List<ItemStack> from) {
        outer:
        for (ItemStack stack : from) {
            for (ItemStack have : into) {
                if (have.is(stack.getItem())) {
                    continue outer;
                }
            }
            into.add(stack);
        }
    }

    private static ItemStack cycle(List<ItemStack> options) {
        if (options.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return options.get((int) ((Util.getMillis() / 1000L) % options.size()));
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void init() {
        super.init();
        this.bookButton = this.addRenderableWidget(new BookButton(this));
        this.layoutPanels();
    }

    /** Centres the panel, or the panel plus the docked book, and pins the book button to the panel. */
    private void layoutPanels() {
        int total = this.imageWidth + (this.bookOpen ? WorkbenchLayout.RECIPE_PANEL_GAP + WorkbenchLayout.RECIPE_PANEL_WIDTH : 0);
        this.leftPos = (this.width - total) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        if (this.bookButton != null) {
            this.bookButton.setPosition(this.leftPos + this.imageWidth - 22, this.topPos + 2);
        }
    }

    private void toggleBook() {
        this.bookOpen = !this.bookOpen;
        this.layoutPanels();
    }

    private int bookLeft() {
        return this.leftPos + this.imageWidth + WorkbenchLayout.RECIPE_PANEL_GAP;
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int button) {
        boolean outside = super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, button);
        if (!outside || !this.bookOpen) {
            return outside;
        }
        // A click on the docked book must not count as "outside", or it would drop the carried stack.
        int px = this.bookLeft();
        return !(mouseX >= px && mouseX < px + WorkbenchLayout.RECIPE_PANEL_WIDTH
                && mouseY >= guiTop && mouseY < guiTop + this.imageHeight);
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // AbstractContainerScreen.render already draws renderTransparentBackground + renderBg once (no blur).
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        Theme th = this.theme();
        int left = this.leftPos;
        int top = this.topPos;
        this.hoveredBookStack = ItemStack.EMPTY;
        this.hoveredBookNote = null;

        drawPanel(g, left, top, this.imageWidth, this.imageHeight, th);
        drawWell(g, left + 7, top + WorkbenchLayout.CRAFT_AREA_TOP, left + this.imageWidth - 7, top + WorkbenchLayout.CRAFT_AREA_BOTTOM, th);

        int rowY = top + WorkbenchLayout.CRAFT_ROW_Y;
        for (int i = 0; i < this.inputCount(); i++) {
            int sx = left + this.inputSlotX(i);
            drawSlot(g, sx, rowY, th);
            if (this.inputStack(i).isEmpty()) {
                drawGhost(g, cycle(this.ghostOptions(i)), sx, rowY);
            }
        }

        int total = this.craftTotalTicks();
        float progress = total > 0 ? Mth.clamp(1f - this.craftTicksRemaining() / (float) total, 0f, 1f) : 0f;
        boolean ready = total <= 0 && this.resultStack().isEmpty() && this.matchedRecipe() != null;
        int arrowX = left + this.inputSlotX(this.inputCount() - 1) + 17 + WorkbenchLayout.ARROW_GAP;
        drawArrow(g, arrowX, rowY + 1, progress, ready ? pulse(th.arrowBase(), th.trim()) : th.arrowBase(), th.trimBright());

        int rx = left + this.resultSlotX();
        boolean hasResult = !this.resultStack().isEmpty();
        drawResultFrame(g, rx - 5, rowY - 5, th, hasResult);
        if (!hasResult) {
            drawGhost(g, this.predictedResult(), rx, rowY);
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(g, left + 8 + col * 18, top + WorkbenchLayout.PLAYER_INV_Y + row * 18, th);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(g, left + 8 + col * 18, top + WorkbenchLayout.HOTBAR_Y, th);
        }

        if (this.bookOpen) {
            this.renderBook(g, this.bookLeft(), top, th, mouseX, mouseY);
        }
    }

    private void renderBook(GuiGraphics g, int px, int py, Theme th, int mouseX, int mouseY) {
        int pw = WorkbenchLayout.RECIPE_PANEL_WIDTH;
        drawPanel(g, px, py, pw, this.imageHeight, th);
        g.drawString(this.font, this.bookTitle(), px + 8, py + 7, th.title(), false);

        int n = this.inputCount();
        int rowW = n * 18 + 3 + 12 + 3 + 18;
        int x0 = px + (pw - rowW) / 2;
        int y = py + BOOK_TOP;
        long tick = Util.getMillis() / 1000L;
        for (BenchRecipe recipe : this.recipes()) {
            drawWell(g, px + 6, y, px + pw - 6, y + BOOK_ROW_H - 1, th);
            int iy = y + 2;
            int sync = (int) tick;
            for (int i = 0; i < n; i++) {
                int ix = x0 + i * 18 + 1;
                List<ItemStack> options = recipe.inputs().get(i);
                ItemStack shown = options.isEmpty() ? ItemStack.EMPTY : options.get(sync % options.size());
                this.drawBookItem(g, shown, ix, iy, mouseX, mouseY);
            }
            int ax = x0 + n * 18 + 3;
            drawSmallArrow(g, ax, iy + 4, th.trim());
            int resX = ax + 12 + 3 + 1;
            List<ItemStack> results = recipe.results();
            ItemStack result = results.isEmpty() ? ItemStack.EMPTY : results.get(sync % results.size());
            g.fill(resX - 2, iy - 1, resX + 18, iy + 17, th.trim());
            g.fill(resX - 1, iy, resX + 17, iy + 16, th.slotFill());
            this.drawBookItem(g, result, resX, iy, mouseX, mouseY);
            if (recipe.note() != null && this.hoveredBookStack.isEmpty()
                    && mouseX >= px + 6 && mouseX < px + pw - 6 && mouseY >= y && mouseY < y + BOOK_ROW_H - 1) {
                this.hoveredBookNote = recipe.note();
            }
            if (recipe.note() != null) {
                // A small star marks rows that carry a hover note.
                g.fill(px + 8, y + 2, px + 10, y + 4, th.trimBright());
            }
            y += BOOK_ROW_H + 1;
        }

        List<FormattedCharSequence> lines = this.font.split(this.bookHint(), pw - 16);
        int ty = py + this.imageHeight - 8 - lines.size() * 10;
        for (FormattedCharSequence line : lines) {
            g.drawString(this.font, line, px + 8, ty, th.textDim(), false);
            ty += 10;
        }
    }

    private void drawBookItem(GuiGraphics g, ItemStack stack, int x, int y, int mouseX, int mouseY) {
        if (stack.isEmpty()) {
            return;
        }
        g.renderItem(stack, x, y);
        g.renderItemDecorations(this.font, stack, x, y);
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            this.hoveredBookStack = stack;
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        Theme th = this.theme();
        g.drawString(this.font, this.title, 8, 7, th.title(), false);
        g.drawString(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY, th.text(), false);

        Component status;
        int color;
        if (this.craftTotalTicks() > 0) {
            float frac = Mth.clamp(1f - this.craftTicksRemaining() / (float) this.craftTotalTicks(), 0f, 1f);
            status = this.craftingText().copy().append(" " + Math.round(frac * 100f) + "%");
            color = th.trimBright();
        } else if (!this.resultStack().isEmpty()) {
            status = Component.translatable("gui.hogwartsmagic.workbench.done");
            color = th.trimBright();
        } else if (this.matchedRecipe() != null) {
            status = this.readyText();
            color = pulse(th.trim(), th.trimBright());
        } else {
            status = Component.translatable("gui.hogwartsmagic.workbench.idle");
            color = th.textDim();
        }
        int maxW = this.imageWidth - 20;
        int w = this.font.width(status);
        float scale = w > maxW ? maxW / (float) w : 1f;
        GuiRenderUtil.drawScaledStringCentered(g, this.font, status, this.imageWidth / 2f, WorkbenchLayout.STATUS_Y, color, scale);
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        super.renderTooltip(g, mouseX, mouseY);
        if (!this.menu.getCarried().isEmpty()) {
            return;
        }
        if (!this.hoveredBookStack.isEmpty()) {
            g.renderTooltip(this.font, this.hoveredBookStack, mouseX, mouseY);
            return;
        }
        if (this.hoveredBookNote != null) {
            g.renderComponentTooltip(this.font, List.of(this.hoveredBookNote), mouseX, mouseY);
            return;
        }
        if (this.hoveredSlot == null || this.hoveredSlot.hasItem()) {
            return;
        }
        int index = this.hoveredSlot.index;
        List<Component> lines = new ArrayList<>();
        if (index < this.inputCount()) {
            lines.add(this.slotName(index).copy().withStyle(ChatFormatting.GOLD));
            for (ItemStack option : this.allOptions(index)) {
                Component name = option.getCount() > 1
                        ? Component.literal(option.getCount() + "x ").append(option.getHoverName())
                        : option.getHoverName();
                lines.add(Component.literal("  ").append(name).withStyle(ChatFormatting.GRAY));
            }
            Component hint = this.slotHint(index);
            if (hint != null) {
                lines.add(hint.copy().withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
        } else if (index == this.inputCount()) {
            lines.add(Component.translatable("gui.hogwartsmagic.slot.result").withStyle(ChatFormatting.GOLD));
            ItemStack predicted = this.predictedResult();
            if (!predicted.isEmpty()) {
                lines.add(predicted.getHoverName().copy().withStyle(ChatFormatting.GRAY));
            }
        } else {
            return;
        }
        g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    // ---------------------------------------------------------------- drawing helpers

    private static void drawResultFrame(GuiGraphics g, int x, int y, Theme th, boolean glow) {
        int s = WorkbenchLayout.RESULT_FRAME;
        if (glow) {
            int a = Math.round(70 + 60 * ThemedGui.pulse01());
            g.fill(x - 2, y - 2, x + s + 2, y + s + 2, (a << 24) | (th.trimBright() & 0xFFFFFF));
        }
        g.fill(x, y, x + s, y + s, glow ? th.trimBright() : th.trim());
        g.fill(x + 1, y + 1, x + s - 1, y + s - 1, th.wellShadow());
        g.fill(x + 2, y + 2, x + s - 1, y + s - 1, th.wellLight());
        g.fill(x + 2, y + 2, x + s - 2, y + s - 2, th.slotFill());
    }

    /** 22x15 arrow; columns left of {@code progress * width} are drawn in {@code fillColor}. */
    private static void drawArrow(GuiGraphics g, int x, int y, float progress, int baseColor, int fillColor) {
        int w = WorkbenchLayout.ARROW_W;
        int h = WorkbenchLayout.ARROW_H;
        int mid = h / 2;
        int headStart = 13;
        int filled = Math.round(progress * w);
        for (int c = 0; c < w; c++) {
            int half = c < headStart ? 2 : Math.max(0, mid - Math.round((c - headStart) * mid / (float) (w - headStart)));
            int color = c < filled ? fillColor : baseColor;
            g.fill(x + c, y + mid - half, x + c + 1, y + mid + half + 1, color);
        }
    }

    private static void drawSmallArrow(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y + 3, x + 7, y + 5, color);
        for (int c = 0; c < 5; c++) {
            int half = 4 - c;
            g.fill(x + 7 + c, y + 4 - half, x + 8 + c, y + 4 + half, color);
        }
    }

    /** Dimmed item silhouette for an empty slot. */
    private static void drawGhost(GuiGraphics g, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) {
            return;
        }
        g.flush();
        RenderSystem.setShaderColor(0.38f, 0.38f, 0.42f, 1f);
        g.renderItem(stack, x, y);
        g.flush();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Small themed button with a book icon; lit while the book is open. */
    private static final class BookButton extends Button {
        private final WorkbenchScreen<?> screen;

        BookButton(WorkbenchScreen<?> screen) {
            super(0, 0, 18, 18, Component.translatable("gui.hogwartsmagic.recipe_book"), b -> screen.toggleBook(), DEFAULT_NARRATION);
            this.screen = screen;
            this.setTooltip(Tooltip.create(Component.translatable("gui.hogwartsmagic.recipe_book.tooltip")));
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Theme th = this.screen.theme();
            int x = this.getX();
            int y = this.getY();
            boolean lit = this.screen.bookOpen || this.isHoveredOrFocused();
            g.fill(x, y, x + 18, y + 18, lit ? th.trimBright() : th.trim());
            g.fill(x + 1, y + 1, x + 17, y + 17, th.bevelDark());
            g.fill(x + 1, y + 1, x + 16, y + 16, lit ? th.wellLight() : th.bevelLight());
            g.fill(x + 2, y + 2, x + 16, y + 16, lit ? th.bevelLight() : th.fill());
            g.renderItem(new ItemStack(this.screen.bookOpen ? Items.WRITABLE_BOOK : Items.BOOK), x + 1, y + 1);
        }
    }
}
