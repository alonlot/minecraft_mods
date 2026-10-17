package com.hogwartsmagic.client.sorting;

import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.sorting.HogwartsHouse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The Sorting Hat quiz: a parchment panel with the question and four full-width answer buttons. The server owns the
 * quiz; a click only sends (step, choice) and locks the buttons until the next packet arrives. The tie-break reuses
 * the same screen with the prompt text and one button per tied house, in house colours.
 */
public class SortingQuizScreen extends Screen {
    /** The server's tie-break step (its question count); the tie packet does not carry a step. */
    private static final int TIE_STEP = 3;
    private static final int QUESTION_COUNT = 3;

    /** The narrowest the panel gets; it widens to fit the longest answer, up to the screen edge. */
    private static final int MIN_PANEL_W = 300;
    private static final int BUTTON_TEXT_MARGIN = 16;
    private static final int PARCHMENT = 0xFFE8D5A8;
    private static final int PARCHMENT_BORDER = 0xFF6B4A22;
    private static final int INK = 0xFF3B2A12;
    private static final int BUTTON_H = 20;
    private static final int BUTTON_GAP = 4;
    private static final int PAD = 12;

    private int step;
    private Component prompt = Component.empty();
    private List<Component> answers = List.of();
    private boolean tie;
    private boolean locked;
    private final List<Button> buttons = new ArrayList<>();
    private List<FormattedCharSequence> promptLines = List.of();
    private int panelTop;
    private int panelHeight;
    private int panelW = MIN_PANEL_W;

    private SortingQuizScreen() {
        super(Component.translatable("screen.hogwartsmagic.sorting.title"));
    }

    public static void openQuestion(SpellPackets.SortingQuestionPacket p) {
        List<Component> answers = new ArrayList<>();
        for (String a : p.answers()) answers.add(Component.literal(a));
        show(p.step(), Component.literal(p.text()), answers, false);
        HatVoice.play(p.voice());
    }

    public static void openTie(SpellPackets.SortingTiePacket p) {
        List<Component> answers = new ArrayList<>();
        for (HogwartsHouse h : p.houses()) {
            int color = houseColor(h);
            answers.add(Component.literal(h.displayName()).withStyle(s -> s.withColor(color)));
        }
        show(TIE_STEP, Component.translatable("screen.hogwartsmagic.sorting.tie"), answers, true);
        HatVoice.play(p.voice());
    }

    public static void closeFromServer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof SortingQuizScreen) {
            mc.setScreen(null);
        }
        HatVoice.closed();
    }

    private static void show(int step, Component prompt, List<Component> answers, boolean tie) {
        Minecraft mc = Minecraft.getInstance();
        SortingQuizScreen screen;
        boolean fresh = !(mc.screen instanceof SortingQuizScreen);
        if (fresh) {
            screen = new SortingQuizScreen();
        } else {
            screen = (SortingQuizScreen) mc.screen;
        }
        screen.step = step;
        screen.prompt = prompt;
        screen.answers = answers;
        screen.tie = tie;
        screen.locked = false;
        if (fresh) {
            mc.setScreen(screen);
        } else {
            screen.rebuildWidgets();
        }
    }

    private static int houseColor(HogwartsHouse h) {
        if (h == HogwartsHouse.GRYFFINDOR) return 0xAA0000;
        if (h == HogwartsHouse.RAVENCLAW) return 0x2255CC;
        if (h == HogwartsHouse.HUFFLEPUFF) return 0xC8A000;
        return 0x1A7A2E;
    }

    @Override
    protected void init() {
        super.init();
        buttons.clear();
        int widest = 0;
        for (Component a : answers) widest = Math.max(widest, font.width(a));
        panelW = Math.min(Math.max(MIN_PANEL_W, widest + BUTTON_TEXT_MARGIN + PAD * 2), width - 16);
        int innerW = panelW - PAD * 2;
        promptLines = font.split(prompt, innerW);
        int textH = promptLines.size() * (font.lineHeight + 1);
        int buttonsH = answers.size() * (BUTTON_H + BUTTON_GAP) - BUTTON_GAP;
        panelHeight = PAD + font.lineHeight + 4 + font.lineHeight + 8 + textH + 10 + buttonsH + PAD;
        panelTop = Math.max(4, (height - panelHeight) / 2);
        int left = (width - panelW) / 2;
        int y = panelTop + panelHeight - PAD - buttonsH;
        for (int i = 0; i < answers.size(); i++) {
            final int choice = i;
            Button b = Button.builder(answers.get(i), button -> choose(choice))
                    .bounds(left + PAD, y, innerW, BUTTON_H).build();
            b.active = !locked;
            buttons.add(b);
            addRenderableWidget(b);
            y += BUTTON_H + BUTTON_GAP;
        }
    }

    private void choose(int choice) {
        if (locked) return;
        locked = true;
        for (Button b : buttons) b.active = false;
        SpellPackets.CHANNEL.send(new SpellPackets.SortingChoicePacket(step, choice), PacketDistributor.SERVER.noArg());
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Never run the menu-blur shader: it would blur the panel drawn below as well as the world.
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Screen.render calls this after the panel is drawn; the plain dim is drawn in render() instead.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        int left = (width - panelW) / 2;
        g.fill(left - 2, panelTop - 2, left + panelW + 2, panelTop + panelHeight + 2, PARCHMENT_BORDER);
        g.fill(left, panelTop, left + panelW, panelTop + panelHeight, PARCHMENT);
        int y = panelTop + PAD;
        g.drawCenteredString(font, title, width / 2, y, INK);
        y += font.lineHeight + 4;
        if (!tie) {
            g.drawCenteredString(font, Component.translatable("screen.hogwartsmagic.sorting.step",
                    step + 1, QUESTION_COUNT), width / 2, y, 0xFF7A5C2E);
        }
        y += font.lineHeight + 8;
        for (FormattedCharSequence line : promptLines) {
            g.drawString(font, line, left + PAD, y, INK, false);
            y += font.lineHeight + 1;
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
