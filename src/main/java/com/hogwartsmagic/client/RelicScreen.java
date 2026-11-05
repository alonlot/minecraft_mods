package com.hogwartsmagic.client;

import com.hogwartsmagic.client.ThemedGui.Theme;
import com.hogwartsmagic.client.ThemedGui.ThemedButton;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.relic.RelicItem;
import com.hogwartsmagic.relic.RelicSlotType;
import com.hogwartsmagic.relic.RelicType;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import static com.hogwartsmagic.client.ThemedGui.drawPanel;
import static com.hogwartsmagic.client.ThemedGui.drawSlot;
import static com.hogwartsmagic.client.ThemedGui.drawWell;

/**
 * Relic loadout: two equip cards (capacity, regen), the resulting mana stats, and a scrollable list of
 * the relics in the inventory. Click a card to unequip, click a row to equip. Server owns the change.
 */
public class RelicScreen extends Screen {
    private static final Theme TH = ThemedGui.VIOLET;
    private static final int W = 280;
    private static final int H = 236;
    private static final int CARD_Y = 33;
    private static final int CARD_H = 48;
    private static final int CARD_W = 126;
    private static final int STATS_Y = 90;
    private static final int LIST_HEADER_Y = 110;
    private static final int LIST_Y0 = 122;
    private static final int LIST_Y1 = 206;
    private static final int ROW_H = 21;
    private static final int VISIBLE_ROWS = (LIST_Y1 - LIST_Y0 - 1) / ROW_H;

    private final Player player;
    private final List<Integer> relicSlots = new ArrayList<>();
    private int left;
    private int top;
    private int scrollRows;

    private ItemStack hoveredStack = ItemStack.EMPTY;
    @Nullable
    private Component hoveredAction;

    public RelicScreen(Player player) {
        super(Component.translatable("gui.hogwartsmagic.relics"));
        this.player = player;
    }

    @Override
    protected void init() {
        super.init();
        this.left = (this.width - W) / 2;
        this.top = (this.height - H) / 2;
        this.addRenderableWidget(new ThemedButton(this.left + W - 70, this.top + H - 25, 60, 18,
                Component.translatable("gui.hogwartsmagic.close"), b -> this.onClose(), TH));
        this.refreshRelics();
    }

    private void refreshRelics() {
        this.relicSlots.clear();
        for (int i = 0; i < this.player.getInventory().getContainerSize(); i++) {
            if (RelicItem.isRelic(this.player.getInventory().getItem(i))) {
                this.relicSlots.add(i);
            }
        }
        this.scrollRows = Mth.clamp(this.scrollRows, 0, this.maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, this.relicSlots.size() - VISIBLE_ROWS);
    }

    private int cardX(RelicSlotType type) {
        return this.left + (type == RelicSlotType.CAPACITY ? 10 : W - 10 - CARD_W);
    }

    private static Component slotName(RelicSlotType type) {
        return Component.translatable(type == RelicSlotType.CAPACITY
                ? "gui.hogwartsmagic.relics.slot_capacity" : "gui.hogwartsmagic.relics.slot_regen");
    }

    /** "+20 max mana" / "+15% regen" for a relic stack, or null when it is not a relic. */
    @Nullable
    private static Component bonusLine(ItemStack stack) {
        RelicType type = RelicItem.getRelicType(stack);
        if (type == null) {
            return null;
        }
        if (type.getSlotType() == RelicSlotType.CAPACITY) {
            return Component.translatable("gui.hogwartsmagic.relics.bonus_mana", Math.round(type.getMaxManaBonus()));
        }
        return Component.translatable("gui.hogwartsmagic.relics.bonus_regen", Math.round((type.getManaRegenMultiplier() - 1f) * 100f));
    }

    private static boolean within(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        for (RelicSlotType type : new RelicSlotType[]{RelicSlotType.CAPACITY, RelicSlotType.REGEN}) {
            if (!PlayerMagicData.getEquippedRelic(this.player, type).isEmpty()
                    && within(mouseX, mouseY, this.cardX(type), this.top + CARD_Y, CARD_W, CARD_H)) {
                SpellPackets.CHANNEL.send(new SpellPackets.UnequipRelicPacket(type.getIndex()), PacketDistributor.SERVER.noArg());
                return true;
            }
        }
        int row = this.rowAt(mouseX, mouseY);
        if (row >= 0) {
            int slot = this.relicSlots.get(row);
            if (RelicItem.isRelic(this.player.getInventory().getItem(slot))) {
                SpellPackets.CHANNEL.send(new SpellPackets.EquipRelicPacket(slot), PacketDistributor.SERVER.noArg());
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && this.maxScroll() > 0) {
            this.scrollRows = Mth.clamp(this.scrollRows - (int) Math.signum(scrollY), 0, this.maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int rowAt(double mouseX, double mouseY) {
        int x0 = this.left + 11;
        int x1 = this.left + W - 17;
        if (mouseX < x0 || mouseX >= x1 || mouseY < this.top + LIST_Y0 + 1 || mouseY >= this.top + LIST_Y0 + 1 + VISIBLE_ROWS * ROW_H) {
            return -1;
        }
        int index = this.scrollRows + (int) ((mouseY - this.top - LIST_Y0 - 1) / ROW_H);
        return index < this.relicSlots.size() ? index : -1;
    }

    // ---------------------------------------------------------------- rendering

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // No blur, like the Grimoire.
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No-op: super.render() calls this after the panel is drawn, and the menu backdrop would cover it.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        // Re-read every frame: an equip or unequip moves relics between the inventory and the slots.
        this.refreshRelics();
        this.hoveredStack = ItemStack.EMPTY;
        this.hoveredAction = null;

        int l = this.left;
        int t = this.top;
        drawPanel(g, l, t, W, H, TH);
        g.drawString(this.font, this.title, l + 10, t + 8, TH.title(), false);
        ThemedGui.drawFittedCentered(g, this.font, Component.translatable("gui.hogwartsmagic.relics.subtitle"),
                l + W / 2f, t + 20, TH.textDim(), W - 20);

        this.renderCard(g, RelicSlotType.CAPACITY, Component.translatable("gui.hogwartsmagic.relics.capacity"), mouseX, mouseY);
        this.renderCard(g, RelicSlotType.REGEN, Component.translatable("gui.hogwartsmagic.relics.regen"), mouseX, mouseY);

        int regenPercent = Math.round((PlayerMagicData.getManaRegenMultiplier(this.player) - 1.0f) * 100.0f);
        this.renderStat(g, l + 12, t + STATS_Y,
                Component.translatable("gui.hogwartsmagic.relics.max_mana", (int) PlayerMagicData.getMaxMana(this.player)));
        this.renderStat(g, l + W - 10 - CARD_W + 2, t + STATS_Y,
                Component.translatable("gui.hogwartsmagic.relics.regen_bonus", regenPercent));

        g.drawString(this.font, Component.translatable("gui.hogwartsmagic.relics.inventory")
                .append(" (" + this.relicSlots.size() + ")"), l + 12, t + LIST_HEADER_Y, TH.title(), false);
        this.renderList(g, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);

        if (!this.hoveredStack.isEmpty()) {
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(this.minecraft, this.hoveredStack));
            if (this.hoveredAction != null) {
                lines.add(this.hoveredAction.copy().withStyle(ChatFormatting.GOLD));
            }
            g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        }
    }

    private void renderCard(GuiGraphics g, RelicSlotType type, Component label, int mouseX, int mouseY) {
        int x = this.cardX(type);
        int y = this.top + CARD_Y;
        ItemStack stack = PlayerMagicData.getEquippedRelic(this.player, type);
        boolean hovered = !stack.isEmpty() && within(mouseX, mouseY, x, y, CARD_W, CARD_H);
        drawWell(g, x, y, x + CARD_W, y + CARD_H, TH);
        if (hovered) {
            g.fill(x + 1, y + 1, x + CARD_W - 1, y + CARD_H - 1, TH.wellLight());
        }
        g.drawString(this.font, label, x + 6, y + 5, TH.title(), false);
        int sx = x + 8;
        int sy = y + 23;
        drawSlot(g, sx, sy, TH);
        if (stack.isEmpty()) {
            g.drawString(this.font, Component.translatable("gui.hogwartsmagic.wand_radial.empty"), x + 30, y + 23, TH.textDim(), false);
            g.drawString(this.font, Component.translatable("gui.hogwartsmagic.relics.equip_below"), x + 30, y + 33, TH.textDim(), false);
            return;
        }
        g.renderItem(stack, sx, sy);
        g.renderItemDecorations(this.font, stack, sx, sy);
        int textW = CARD_W - 34;
        this.drawFitted(g, stack.getHoverName(), x + 30, y + 22, hovered ? TH.trimBright() : TH.text(), textW);
        Component bonus = bonusLine(stack);
        if (bonus != null) {
            this.drawFitted(g, bonus, x + 30, y + 33, 0xFF95D7FF, textW);
        }
        if (hovered) {
            this.hoveredStack = stack;
            this.hoveredAction = Component.translatable("gui.hogwartsmagic.relics.unequip_hint");
        }
    }

    private void renderStat(GuiGraphics g, int x, int y, Component text) {
        // Little mana gem before each stat.
        g.fill(x + 2, y + 1, x + 5, y + 2, 0xFFBFE9FF);
        g.fill(x + 1, y + 2, x + 6, y + 5, 0xFF5BB8F0);
        g.fill(x + 2, y + 5, x + 5, y + 6, 0xFF2E7FC0);
        g.fill(x + 3, y + 6, x + 4, y + 7, 0xFF2E7FC0);
        g.drawString(this.font, text, x + 10, y, 0xFF95D7FF, false);
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = this.left + 10;
        int x1 = this.left + W - 10;
        int y0 = this.top + LIST_Y0;
        drawWell(g, x0, y0, x1, this.top + LIST_Y1, TH);
        if (this.relicSlots.isEmpty()) {
            ThemedGui.drawFittedCentered(g, this.font, Component.translatable("gui.hogwartsmagic.relics.none"),
                    (x0 + x1) / 2f, (y0 + this.top + LIST_Y1) / 2f - 4, TH.textDim(), x1 - x0 - 12);
            return;
        }
        int hoveredRow = this.rowAt(mouseX, mouseY);
        int rowX1 = x1 - 7;
        for (int r = 0; r < VISIBLE_ROWS; r++) {
            int index = this.scrollRows + r;
            if (index >= this.relicSlots.size()) {
                break;
            }
            ItemStack stack = this.player.getInventory().getItem(this.relicSlots.get(index));
            RelicType type = RelicItem.getRelicType(stack);
            int ry = y0 + 1 + r * ROW_H;
            boolean hovered = index == hoveredRow;
            if (hovered) {
                g.fill(x0 + 1, ry, rowX1, ry + ROW_H - 1, TH.wellLight());
            } else if ((r & 1) == 1) {
                g.fill(x0 + 1, ry, rowX1, ry + ROW_H - 1, TH.slotFill());
            }
            int ix = x0 + 5;
            int iy = ry + 2;
            drawSlot(g, ix, iy, TH);
            g.renderItem(stack, ix, iy);
            g.renderItemDecorations(this.font, stack, ix, iy);
            int textX = ix + 22;
            int textW = rowX1 - textX - 4;
            this.drawFitted(g, stack.getHoverName(), textX, ry + 2, hovered ? TH.trimBright() : TH.text(), textW);
            if (type != null) {
                Component bonus = bonusLine(stack);
                Component line = Component.translatable("gui.hogwartsmagic.relics.equip_as", slotName(type.getSlotType()));
                if (bonus != null) {
                    line = line.copy().append("  ").append(bonus);
                }
                this.drawFitted(g, line, textX, ry + 11, TH.textDim(), textW);
                if (hovered) {
                    this.hoveredStack = stack;
                    this.hoveredAction = Component.translatable("gui.hogwartsmagic.relics.equip_hint", slotName(type.getSlotType()));
                }
            }
        }
        if (this.maxScroll() > 0) {
            int trackX = x1 - 5;
            int trackY0 = y0 + 2;
            int trackY1 = this.top + LIST_Y1 - 2;
            g.fill(trackX, trackY0, trackX + 3, trackY1, TH.wellShadow());
            int trackH = trackY1 - trackY0;
            int thumbH = Math.max(10, trackH * VISIBLE_ROWS / this.relicSlots.size());
            int thumbY = trackY0 + (trackH - thumbH) * this.scrollRows / this.maxScroll();
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, TH.trim());
        }
    }

    /** Left-aligned text, shrunk only when it would overflow {@code maxWidth}. */
    private void drawFitted(GuiGraphics g, Component text, int x, int y, int color, int maxWidth) {
        int w = this.font.width(text);
        float scale = w > maxWidth ? Math.max(0.5f, maxWidth / (float) w) : 1f;
        GuiRenderUtil.drawScaledString(g, this.font, text, x, y + (1f - scale) * 4f, color, scale);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
