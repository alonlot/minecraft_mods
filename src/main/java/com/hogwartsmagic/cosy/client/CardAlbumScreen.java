package com.hogwartsmagic.cosy.client;

import com.hogwartsmagic.client.ThemedGui;
import com.hogwartsmagic.cosy.WizardCards;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The card album: a 5 by 4 grid of cards. A collected card shows its frame (bronze, silver or gold), a small
 * portrait and its name; hovering it tells its story. A missing card is a dark silhouette. Read-only: the server
 * moves cards into the album, this just draws what it was told. Uses the transparent backdrop, never the blur.
 */
public class CardAlbumScreen extends Screen {
    private static final ThemedGui.Theme TH = ThemedGui.VIOLET;
    private static final int COLS = 5;
    private static final int ROWS = 4;
    private static final int GAP = 4;
    private static final int[] FRAME_LIGHT = {0xFFD89A5A, 0xFFE6EBF2, 0xFFFFE27A};
    private static final int[] FRAME_DARK = {0xFF8A4F26, 0xFF7C879E, 0xFFB07A1C};
    private static final int PARCHMENT = 0xFFF2E2B8;
    private static final int[] ROBES = {0xFF3A2A6B, 0xFF6B2A2A, 0xFF2A5A3A, 0xFF2A4A6B, 0xFF5A4A2A, 0xFF4A2A5A};

    private final Set<Integer> have;
    private int left;
    private int top;
    private int w;
    private int h;
    private int cellW;
    private int cellH;
    private int gridX;
    private int gridY;

    public CardAlbumScreen(List<Integer> collected) {
        super(Component.translatable("gui.hogwartsmagic.card_album.title"));
        this.have = new HashSet<>(collected);
    }

    @Override
    protected void init() {
        super.init();
        this.w = Math.min(270, this.width - 16);
        this.h = Math.min(290, this.height - 16);
        this.left = (this.width - this.w) / 2;
        this.top = (this.height - this.h) / 2;
        this.cellW = (this.w - 24 - (COLS - 1) * GAP) / COLS;
        this.cellH = (this.h - 62 - (ROWS - 1) * GAP) / ROWS;
        this.gridX = this.left + (this.w - (COLS * this.cellW + (COLS - 1) * GAP)) / 2;
        this.gridY = this.top + 34;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No-op: render() draws the plain dim itself (no menu blur, per the screen rule).
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        ThemedGui.drawPanel(g, this.left, this.top, this.w, this.h, TH);
        g.drawString(this.font, this.title, this.left + 10, this.top + 9, TH.title(), false);
        Component count = Component.translatable("gui.hogwartsmagic.card_album.count", this.have.size(), WizardCards.ALL.size());
        g.drawString(this.font, count, this.left + this.w - 10 - this.font.width(count), this.top + 9, TH.textDim(), false);

        int hovered = -1;
        for (int i = 0; i < WizardCards.ALL.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x = this.gridX + col * (this.cellW + GAP);
            int y = this.gridY + row * (this.cellH + GAP);
            WizardCards.Card card = WizardCards.ALL.get(i);
            if (this.have.contains(card.id())) {
                drawCard(g, x, y, card);
            } else {
                drawSilhouette(g, x, y);
            }
            if (mouseX >= x && mouseX < x + this.cellW && mouseY >= y && mouseY < y + this.cellH) {
                hovered = i;
            }
        }
        Component hint = Component.translatable("gui.hogwartsmagic.card_album.hint");
        ThemedGui.drawFittedCentered(g, this.font, hint, this.left + this.w / 2f, this.top + this.h - 16,
                TH.textDim(), this.w - 20);
        super.render(g, mouseX, mouseY, partialTick);
        if (hovered >= 0) {
            drawTooltip(g, WizardCards.ALL.get(hovered), mouseX, mouseY);
        }
    }

    private void drawCard(GuiGraphics g, int x, int y, WizardCards.Card card) {
        int r = card.rarity();
        g.fill(x, y, x + this.cellW, y + this.cellH, FRAME_DARK[r]);
        g.fill(x + 1, y + 1, x + this.cellW - 1, y + this.cellH - 1, FRAME_LIGHT[r]);
        g.fill(x + 3, y + 3, x + this.cellW - 3, y + this.cellH - 3, PARCHMENT);
        // Portrait: a head and shoulders, the robe colour chosen by the card.
        int pw = this.cellW - 6;
        int portraitH = (int) ((this.cellH - 6) * 0.58f);
        int cx = x + this.cellW / 2;
        int py = y + 3;
        int robe = ROBES[card.id() % ROBES.length];
        int head = Math.max(4, pw / 3);
        g.fill(cx - pw / 3, py + portraitH - head, cx + pw / 3, py + portraitH, robe);
        g.fill(cx - head / 2, py + portraitH - head - head / 2 - 1, cx + head / 2, py + portraitH - head, 0xFFE8B890);
        if (card.id() % 3 != 0) {
            g.fill(cx - head / 2 - 1, py + portraitH - head - head / 2 - 3, cx + head / 2 + 1,
                    py + portraitH - head - head / 2 - 1, robe);
        }
        // The name, in up to two small lines.
        Component name = Component.translatable("card.hogwartsmagic." + card.key() + ".name");
        float scale = 0.62f;
        List<FormattedCharSequence> lines = this.font.split(name, (int) ((this.cellW - 8) / scale));
        int ly = y + 3 + portraitH + 2;
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            FormattedCharSequence line = lines.get(i);
            g.pose().pushPose();
            g.pose().translate(cx - this.font.width(line) * scale / 2f, ly + i * 7, 0);
            g.pose().scale(scale, scale, 1f);
            g.drawString(this.font, line, 0, 0, 0xFF3A2A18, false);
            g.pose().popPose();
        }
        // Rarity pips along the bottom.
        for (int i = 0; i <= r; i++) {
            int px = cx - (r * 6) / 2 + i * 6 - 1;
            g.fill(px, y + this.cellH - 6, px + 3, y + this.cellH - 3, FRAME_DARK[r]);
        }
    }

    private void drawSilhouette(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + this.cellW, y + this.cellH, 0xFF0B0814);
        g.fill(x + 1, y + 1, x + this.cellW - 1, y + this.cellH - 1, 0xFF1E1830);
        int cx = x + this.cellW / 2;
        int base = y + (int) (this.cellH * 0.7f);
        int head = Math.max(4, this.cellW / 4);
        g.fill(cx - this.cellW / 3, base - head, cx + this.cellW / 3, base, 0xFF0B0814);
        g.fill(cx - head / 2, base - head - head / 2 - 1, cx + head / 2, base - head, 0xFF0B0814);
        g.drawCenteredString(this.font, "?", cx, y + this.cellH - 12, 0xFF4B3C7A);
    }

    private void drawTooltip(GuiGraphics g, WizardCards.Card card, int mouseX, int mouseY) {
        List<FormattedCharSequence> tip = new ArrayList<>();
        if (this.have.contains(card.id())) {
            tip.add(Component.translatable("card.hogwartsmagic." + card.key() + ".name").getVisualOrderText());
            tip.add(Component.translatable("card.hogwartsmagic.rarity." + card.rarity()).getVisualOrderText());
            tip.addAll(this.font.split(Component.translatable("card.hogwartsmagic." + card.key() + ".line"), 170));
        } else {
            tip.add(Component.translatable("gui.hogwartsmagic.card_album.missing").getVisualOrderText());
        }
        g.renderTooltip(this.font, tip, mouseX, mouseY);
    }
}
