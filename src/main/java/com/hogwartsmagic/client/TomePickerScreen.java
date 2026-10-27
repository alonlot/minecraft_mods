package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.TomeOfMasteryItem;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellLootRarity;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.mastery.MasteryAwards;
import com.hogwartsmagic.spell.mastery.MasteryMath;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The Tome of Mastery picker: the spells the server said this tome can raise, each with its
 * rank step. It is only a menu; a click sends {@link SpellPackets.UseTomePacket} and the server
 * re-checks everything. Same look as the Grimoire (dark panel, thin border, no blur).
 *
 * <p>No {@code switch} on enums in here: the synthetic switch-map class has been seen to fail
 * to load in this mod's dev client, so the tier colour is an if-chain.
 */
public class TomePickerScreen extends Screen {
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 196;
    private static final int ROW_HEIGHT = 14;
    private static final int COLOR_BODY = 0xFFD8C9F0;
    private static final int COLOR_NAME = 0xFFF2DFA8;
    private static final int COLOR_RANK = 0xFFFFE08A;
    private static final int COLOR_HOVER = 0x40D4AF37;
    private static final int TOOLTIP_WRAP = 200;

    private final boolean mainHand;
    private final SpellLootRarity tier;
    private final List<SpellType> spells;

    private int panelLeft;
    private int panelTop;
    private int listLeft;
    private int listRight;
    private int listTop;
    private int listBottom;
    private float scroll;
    private boolean chosen;

    public TomePickerScreen(boolean mainHand, SpellLootRarity tier, List<SpellType> spells) {
        super(Component.translatable("item." + ExampleMod.MODID + "." + TomeOfMasteryItem.registryName(tier)));
        this.mainHand = mainHand;
        this.tier = tier;
        this.spells = List.copyOf(spells);
    }

    /** The tome's name colour (vanilla rarity): white, yellow, aqua, light purple. */
    private static int tierColor(SpellLootRarity tier) {
        if (tier == SpellLootRarity.UNCOMMON) {
            return 0xFFFFFF55;
        }
        if (tier == SpellLootRarity.RARE) {
            return 0xFF55FFFF;
        }
        if (tier == SpellLootRarity.EPIC) {
            return 0xFFFF55FF;
        }
        return 0xFFFFFFFF;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        panelLeft = (this.width - PANEL_WIDTH) / 2;
        panelTop = (this.height - PANEL_HEIGHT) / 2;
        listLeft = panelLeft + 10;
        listRight = panelLeft + PANEL_WIDTH - 10;
        listTop = panelTop + 34;
        listBottom = panelTop + PANEL_HEIGHT - 32;
        scroll = clampScroll(scroll);
        addRenderableWidget(Button.builder(
                        Component.translatable("gui." + ExampleMod.MODID + ".close"),
                        b -> onClose())
                .bounds(panelLeft + (PANEL_WIDTH - 60) / 2, panelTop + PANEL_HEIGHT - 26, 60, 18)
                .build());
    }

    private float maxScroll() {
        return Math.max(0.0F, spells.size() * ROW_HEIGHT - (float) (listBottom - listTop));
    }

    private float clampScroll(float value) {
        return Math.max(0.0F, Math.min(value, maxScroll()));
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < listLeft || mouseX > listRight || mouseY < listTop || mouseY > listBottom) {
            return -1;
        }
        int index = (int) ((mouseY - listTop + scroll) / ROW_HEIGHT);
        return index >= 0 && index < spells.size() ? index : -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scroll = clampScroll(scroll - (float) deltaY * ROW_HEIGHT * 2.0F);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !chosen) {
            int index = rowAt(mouseX, mouseY);
            if (index >= 0) {
                chosen = true;
                SpellPackets.CHANNEL.send(new SpellPackets.UseTomePacket(mainHand, spells.get(index).getId()),
                        PacketDistributor.SERVER.noArg());
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Hard no-op: never run the menu-blur post effect for this screen.
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No-op so super.render() can't draw the blurred/menu backdrop over the panel.
        // render() draws the plain dim itself with renderTransparentBackground.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);

        int right = panelLeft + PANEL_WIDTH;
        int bottom = panelTop + PANEL_HEIGHT;
        int accent = tierColor(tier);
        g.fill(panelLeft - 4, panelTop - 4, right + 4, bottom + 4, 0xF0140A1E);
        drawBorder(g, panelLeft - 4, panelTop - 4, right + 4, bottom + 4, accent);

        int centerX = panelLeft + PANEL_WIDTH / 2;
        g.drawCenteredString(this.font, this.title, centerX, panelTop + 6, accent);
        g.drawCenteredString(this.font, Component.translatable("gui." + ExampleMod.MODID + ".tome.choose"),
                centerX, panelTop + 19, COLOR_BODY);

        if (spells.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui." + ExampleMod.MODID + ".tome.empty"),
                    centerX, listTop + 8, COLOR_BODY);
        }

        int hovered = rowAt(mouseX, mouseY);
        g.enableScissor(listLeft, listTop, listRight, listBottom);
        int y = (int) (listTop - scroll);
        for (int i = 0; i < spells.size(); i++) {
            if (y + ROW_HEIGHT >= listTop && y <= listBottom) {
                SpellType spell = spells.get(i);
                if (i == hovered) {
                    g.fill(listLeft, y, listRight, y + ROW_HEIGHT, COLOR_HOVER);
                }
                g.drawString(this.font, Component.translatable("spell." + ExampleMod.MODID + "." + spell.getId()),
                        listLeft + 4, y + 3, COLOR_NAME);
                String step = rankStep(spell);
                g.drawString(this.font, step, listRight - 4 - this.font.width(step), y + 3, COLOR_RANK);
            }
            y += ROW_HEIGHT;
        }
        g.disableScissor();
        drawScrollCue(g);

        super.render(g, mouseX, mouseY, partialTick);

        // After everything else and outside the scissor, so the tooltip is never clipped.
        if (hovered >= 0 && hovered < spells.size()) {
            g.renderTooltip(this.font, upgradeTooltip(spells.get(hovered)), mouseX, mouseY);
        }
    }

    /** A thin track with a thumb at the list's right edge, plus an arrow where more rows hide. */
    private void drawScrollCue(GuiGraphics g) {
        float max = maxScroll();
        if (max <= 0.0F) {
            return;
        }
        int height = listBottom - listTop;
        int[] thumb = ScrollCue.thumb(scroll, max, height, 8);
        g.fill(listRight, listTop, listRight + 3, listBottom, 0x40FFFFFF);
        g.fill(listRight, listTop + thumb[0], listRight + 3, listTop + thumb[0] + thumb[1], COLOR_NAME);
        int arrowX = listRight - 8;
        if (ScrollCue.moreAbove(scroll)) {
            g.drawString(this.font, "▲", arrowX, listTop - 10, COLOR_RANK);
        }
        if (ScrollCue.moreBelow(scroll, max)) {
            g.drawString(this.font, "▼", arrowX, listBottom + 2, COLOR_RANK);
        }
    }

    /** Name, "Next rank: III Expert", what the spell gains there (wrapped), and the generic rank bonus. */
    private List<FormattedCharSequence> upgradeTooltip(SpellType spell) {
        int rank = currentRank(spell);
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.translatable("spell." + ExampleMod.MODID + "." + spell.getId())
                .withStyle(ChatFormatting.GOLD).getVisualOrderText());
        if (rank >= MasteryMath.MAX_RANK) {
            lines.add(Component.translatable("gui." + ExampleMod.MODID + ".mastery.at_master")
                    .withStyle(ChatFormatting.YELLOW).getVisualOrderText());
            return lines;
        }
        int next = rank + 1;
        lines.add(Component.translatable("gui." + ExampleMod.MODID + ".mastery.upgrade_preview",
                        MasteryMath.roman(next), Component.translatable(MasteryAwards.rankKey(next)))
                .withStyle(ChatFormatting.YELLOW).getVisualOrderText());
        lines.addAll(this.font.split(Component.translatable(MasteryAwards.rankChangeKey(spell, next))
                .withStyle(ChatFormatting.WHITE), TOOLTIP_WRAP));
        lines.addAll(this.font.split(Component.translatable(MasteryAwards.genericUpgradeKey())
                .withStyle(ChatFormatting.GRAY), TOOLTIP_WRAP));
        return lines;
    }

    private int currentRank(SpellType spell) {
        return this.minecraft != null && this.minecraft.player != null
                ? SpellMasteryData.getRank(this.minecraft.player, spell)
                : MasteryMath.NOVICE;
    }

    /** "II -> III" from the synced rank; the server decides the real outcome. */
    private String rankStep(SpellType spell) {
        int rank = currentRank(spell);
        int next = Math.min(MasteryMath.MAX_RANK, rank + 1);
        return MasteryMath.roman(rank) + " → " + MasteryMath.roman(next);
    }

    private void drawBorder(GuiGraphics g, int left, int top, int right, int bottom, int color) {
        g.fill(left, top, right, top + 1, color);
        g.fill(left, bottom - 1, right, bottom, color);
        g.fill(left, top, left + 1, bottom, color);
        g.fill(right - 1, top, right, bottom, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
