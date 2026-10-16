package com.hogwartsmagic.client;

import com.hogwartsmagic.assignment.OpenAssignmentPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The parchment an assignment is written on: year and subject, the teacher's line, each objective
 * with a tick box and a progress bar, the promised book and the signature. Dark Orders are drawn on
 * black parchment in green ink. Read-only: everything comes from the server in one packet.
 */
public class AssignmentScreen extends Screen {
    private static final int WIDTH = 270;
    private static final int PADDING = 14;
    /** Seal colours by rarity (Common, Uncommon, Rare, Epic); the same tiers as the Tomes of Mastery. */
    private static final int[] SEAL = {0xFFE8E8E8, 0xFFE8C547, 0xFF55D6E0, 0xFFC77DFF};
    private static final int DARK_SEAL = 0xFF2FA84F;

    private final OpenAssignmentPacket data;
    private final List<FormattedCharSequence> quote = new ArrayList<>();
    private final List<List<FormattedCharSequence>> lineText = new ArrayList<>();
    private int left;
    private int top;
    private int panelHeight;

    public AssignmentScreen(OpenAssignmentPacket data) {
        super(Component.translatable("item.hogwartsmagic.assignment"));
        this.data = data;
    }

    private int paper() {
        return this.data.dark() ? 0xFF1B1A1F : 0xFFF3E2BE;
    }

    private int ink() {
        return this.data.dark() ? 0xFF7CE08F : 0xFF4A2E17;
    }

    private int faint() {
        return this.data.dark() ? 0xFF4F8F5C : 0xFF8A6A45;
    }

    private int seal() {
        if (this.data.dark()) {
            return DARK_SEAL;
        }
        int rarity = Math.max(0, Math.min(SEAL.length - 1, this.data.rarity()));
        return SEAL[rarity];
    }

    @Override
    protected void init() {
        super.init();
        int textWidth = WIDTH - PADDING * 2;
        this.quote.clear();
        this.quote.addAll(this.font.split(Component.translatable(this.data.quoteKey())
                .withStyle(Style.EMPTY.withItalic(true)), textWidth));
        this.lineText.clear();
        for (OpenAssignmentPacket.Line line : this.data.lines()) {
            this.lineText.add(this.font.split(Component.translatable(line.key(), line.count()), textWidth - 16));
        }
        int body = 46 + this.quote.size() * 10 + 8;
        for (List<FormattedCharSequence> text : this.lineText) {
            body += text.size() * 10 + 10;
        }
        this.panelHeight = body + 66;
        this.left = (this.width - WIDTH) / 2;
        this.top = Math.max(8, (this.height - this.panelHeight) / 2);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.hogwartsmagic.close"), b -> this.onClose())
                .bounds(this.left + WIDTH - 64, this.top + this.panelHeight - 26, 54, 18).build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No-op so super.render() can't draw the blurred backdrop; render() draws a plain dim itself.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        int x0 = this.left;
        int y0 = this.top;
        int x1 = x0 + WIDTH;
        int y1 = y0 + this.panelHeight;
        g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, this.data.dark() ? 0xFF0A0A0C : 0xFF6A4A25);
        g.fill(x0, y0, x1, y1, paper());
        // Rolled edges at the top and bottom.
        int edge = this.data.dark() ? 0xFF2A2830 : 0xFFD9BF8C;
        g.fill(x0, y0, x1, y0 + 5, edge);
        g.fill(x0, y1 - 5, x1, y1, edge);

        int y = y0 + 12;
        Component year = Component.translatable(this.data.dark() ? "assignment.hogwartsmagic.year.dark"
                : "assignment.hogwartsmagic.year." + Math.max(0, Math.min(3, this.data.rarity())));
        g.drawCenteredString(this.font, year, x0 + WIDTH / 2, y, faint());
        y += 12;
        Component title = Component.translatable(this.data.dark() ? "assignment.hogwartsmagic.title.dark"
                : "assignment.hogwartsmagic.title", Component.translatable("spell.hogwartsmagic." + this.data.spellId()));
        drawCentered(g, title, x0 + WIDTH / 2, y, ink());
        y += 22;
        for (FormattedCharSequence seq : this.quote) {
            g.drawString(this.font, seq, x0 + PADDING, y, faint(), false);
            y += 10;
        }
        y += 8;

        int textX = x0 + PADDING + 16;
        int barWidth = WIDTH - PADDING * 2 - 16;
        for (int i = 0; i < this.data.lines().size(); i++) {
            OpenAssignmentPacket.Line line = this.data.lines().get(i);
            int box = x0 + PADDING;
            g.fill(box, y, box + 10, y + 10, ink());
            g.fill(box + 1, y + 1, box + 9, y + 9, paper());
            if (line.done()) {
                g.fill(box + 3, y + 3, box + 7, y + 7, seal() | 0xFF000000);
            }
            List<FormattedCharSequence> text = this.lineText.get(i);
            int ty = y + 1;
            for (FormattedCharSequence seq : text) {
                g.drawString(this.font, seq, textX, ty, line.done() ? faint() : ink(), false);
                ty += 10;
            }
            int barY = ty + 1;
            int filled = line.count() <= 0 ? 0 : (int) ((long) barWidth * Math.min(line.progress(), line.count()) / line.count());
            g.fill(textX, barY, textX + barWidth, barY + 3, this.data.dark() ? 0xFF2E3A32 : 0xFFD6BF93);
            g.fill(textX, barY, textX + filled, barY + 3, seal());
            String amount = Math.min(line.progress(), line.count()) + "/" + line.count();
            g.drawString(this.font, amount, textX + barWidth - this.font.width(amount), ty - 10 + 1,
                    line.done() ? faint() : ink(), false);
            y = barY + 9;
        }

        y += 4;
        g.drawString(this.font, Component.translatable("assignment.hogwartsmagic.reward",
                Component.translatable("spell.hogwartsmagic." + this.data.spellId())), x0 + PADDING, y, ink(), false);
        y += 12;
        String stateKey = this.data.state() == OpenAssignmentPacket.STATE_COMPLETE ? "assignment.hogwartsmagic.state.complete"
                : "assignment.hogwartsmagic.state.active";
        g.drawString(this.font, Component.translatable(stateKey), x0 + PADDING, y, faint(), false);

        // Wax seal and signature.
        int sx = x0 + PADDING;
        int sy = y1 - 26;
        g.fill(sx + 2, sy, sx + 16, sy + 18, seal());
        g.fill(sx, sy + 2, sx + 18, sy + 16, seal());
        g.fill(sx + 6, sy + 6, sx + 12, sy + 12, this.data.dark() ? 0xFF103018 : 0x55000000);
        Component signature = Component.translatable(this.data.teacherKey()).withStyle(Style.EMPTY.withItalic(true));
        g.drawString(this.font, signature, sx + 24, sy + 5, ink(), false);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawCentered(GuiGraphics g, Component text, int centerX, int y, int color) {
        g.drawString(this.font, text, centerX - this.font.width(text) / 2, y, color, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
