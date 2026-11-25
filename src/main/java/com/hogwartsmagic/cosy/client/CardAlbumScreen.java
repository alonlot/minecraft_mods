package com.hogwartsmagic.cosy.client;

import com.hogwartsmagic.client.ThemedGui;
import com.hogwartsmagic.cosy.WizardCards;
import com.hogwartsmagic.cosy.client.card.CardArt;
import com.hogwartsmagic.cosy.client.card.CardRevealScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The card album: a 5 by 4 grid of cards. A collected card is drawn as a small living portrait in its frame (bronze,
 * silver or gold); hovering it tells its story and clicking it opens it large. A missing card is a dark silhouette.
 * Read-only: the server moves cards into the album, this just draws what it was told. Uses the transparent
 * backdrop, never the blur.
 */
public class CardAlbumScreen extends Screen {
    private static final ThemedGui.Theme TH = ThemedGui.VIOLET;
    private static final int COLS = 5;
    private static final int ROWS = 4;
    private static final int GAP = 4;

    /** Cards whose small copies may be built in one frame: the first open must not decode every PNG at once. */
    private static final int LOADS_PER_FRAME = 4;

    private final Set<Integer> have;
    private int loadBudget;
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
        this.loadBudget = LOADS_PER_FRAME;
        for (int i = 0; i < WizardCards.ALL.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x = this.gridX + col * (this.cellW + GAP);
            int y = this.gridY + row * (this.cellH + GAP);
            WizardCards.Card card = WizardCards.ALL.get(i);
            if (this.have.contains(card.id()) && prepared(card)) {
                drawCard(g, x, y, card, partialTick);
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

    /** True once the card's small copies exist; builds them (within this frame's budget) if not, else it stays a silhouette. */
    private boolean prepared(WizardCards.Card card) {
        if (CardArt.miniReady(card.key())) {
            return true;
        }
        if (this.loadBudget > 0) {
            this.loadBudget--;
            CardArt.prepareMini(card.key());
            return CardArt.miniReady(card.key());
        }
        return false;
    }

    /** A collected card: the living portrait, as large as fits the cell, centred in it. */
    private void drawCard(GuiGraphics g, int x, int y, WizardCards.Card card, float partial) {
        float scale = Math.min(this.cellW / (float) CardArt.CARD_W, this.cellH / (float) CardArt.CARD_H);
        float px = x + (this.cellW - CardArt.CARD_W * scale) / 2.0F;
        float py = y + (this.cellH - CardArt.CARD_H * scale) / 2.0F;
        CardArt.drawCard(g, card.id(), CardRevealScreen.viewSeedOf(card.id()), px, py, scale, partial);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < WizardCards.ALL.size(); i++) {
                int x = this.gridX + (i % COLS) * (this.cellW + GAP);
                int y = this.gridY + (i / COLS) * (this.cellH + GAP);
                WizardCards.Card card = WizardCards.ALL.get(i);
                if (this.have.contains(card.id()) && mouseX >= x && mouseX < x + this.cellW
                        && mouseY >= y && mouseY < y + this.cellH && this.minecraft != null) {
                    this.minecraft.setScreen(new CardRevealScreen(card.id(), this));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
