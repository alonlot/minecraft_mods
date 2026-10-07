package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicBookItem;
import com.hogwartsmagic.loot.MagicBookChestLoot;
import com.hogwartsmagic.spell.SpellLootRarity;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * The Grimoire: a read-only compendium of every spell in the mod, grouped by mastery tier,
 * showing which spells the player has learned, which are still locked, and where each spell's
 * book can be found. Makes the (otherwise invisible) loot-gated spell progression legible.
 *
 * <p>No {@code switch} on enums anywhere in here — the synthetic switch-map class has been seen
 * to fail to load in this mod's dev client, so tier/rarity logic uses plain {@code if/else}.
 */
public class SpellCompendiumScreen extends Screen {

    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 264;
    private static final int LIST_WIDTH = 180;
    private static final int ROW_HEIGHT = 13;

    // Tier accent colors (ARGB).
    private static final int COLOR_NOVICE = 0xFF9BE38B;
    private static final int COLOR_APPRENTICE = 0xFF6FC2FF;
    private static final int COLOR_ADEPT = 0xFFC79BFF;
    private static final int COLOR_MASTER = 0xFFFF7A6B;
    private static final int COLOR_LEARNED = 0xFFF2DFA8;
    private static final int COLOR_LOCKED = 0xFF6A6A6A;
    private static final int COLOR_GOLD = 0xFFD4AF37;
    private static final int COLOR_BODY = 0xFFD8C9F0;

    // Rank accent colours (ARGB) for the mastery rank line and XP bar. Same hues as the tiers on
    // purpose, but named for the rank they mean so the two scales can change independently.
    private static final int RANK_COLOR_NOVICE = 0xFF9BE38B;
    private static final int RANK_COLOR_ADEPT = 0xFF6FC2FF;
    private static final int RANK_COLOR_EXPERT = 0xFFC79BFF;
    private static final int RANK_COLOR_MASTER = 0xFFFF7A6B;
    private static final int DETAIL_LINE = 11;

    private final Player player;
    private boolean tooltipPending;
    private final List<Row> rows = new ArrayList<>();
    private int learnedCount;
    private int totalCount;
    private SpellType selected;

    private int panelLeft;
    private int panelTop;
    private int listTop;
    private int listBottom;
    private float scroll;
    private float detailScroll;
    private int detailContentHeight;
    private int detailTop;
    private int detailBottom;

    public SpellCompendiumScreen(Player player) {
        super(Component.translatable("gui." + ExampleMod.MODID + ".grimoire"));
        this.player = player;
    }

    /** One drawable line in the left column: either a tier header or a spell entry. */
    private record Row(boolean header, Component label, int color, SpellType spell, boolean learned) {
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        panelLeft = (this.width - PANEL_WIDTH) / 2;
        panelTop = (this.height - PANEL_HEIGHT) / 2;
        listTop = panelTop + 40;
        listBottom = panelTop + PANEL_HEIGHT - 14;

        buildRows();

        addRenderableWidget(Button.builder(
                        Component.translatable("gui." + ExampleMod.MODID + ".close"),
                        b -> onClose())
                .bounds(panelLeft + PANEL_WIDTH - 72, panelTop + PANEL_HEIGHT - 26, 60, 18)
                .build());
    }

    private void buildRows() {
        rows.clear();
        learnedCount = 0;
        totalCount = 0;
        if (selected == null) {
            selected = SpellType.values()[0];
        }

        // Walk tiers in ascending difficulty so the player reads novice -> master top to bottom.
        SpellLootRarity[] tierOrder = {
                SpellLootRarity.COMMON, SpellLootRarity.UNCOMMON, SpellLootRarity.RARE, SpellLootRarity.EPIC
        };
        for (SpellLootRarity tier : tierOrder) {
            boolean headerAdded = false;
            for (SpellType spell : SpellType.values()) {
                if (spell.getLootRarity() != tier) {
                    continue;
                }
                if (!headerAdded) {
                    rows.add(new Row(true, tierName(tier), tierColor(tier), null, false));
                    headerAdded = true;
                }
                boolean learned = MagicBookItem.isSpellLearned(player, spell);
                totalCount++;
                if (learned) {
                    learnedCount++;
                }
                rows.add(new Row(false, spellName(spell), learned ? COLOR_LEARNED : COLOR_LOCKED, spell, learned));
            }
        }
    }

    private static MutableComponent tierName(SpellLootRarity tier) {
        String key = "gui." + ExampleMod.MODID + ".grimoire.tier.";
        if (tier == SpellLootRarity.COMMON) {
            return Component.translatable(key + "novice");
        } else if (tier == SpellLootRarity.UNCOMMON) {
            return Component.translatable(key + "apprentice");
        } else if (tier == SpellLootRarity.RARE) {
            return Component.translatable(key + "adept");
        }
        return Component.translatable(key + "master");
    }

    private static int tierColor(SpellLootRarity tier) {
        if (tier == SpellLootRarity.COMMON) {
            return COLOR_NOVICE;
        } else if (tier == SpellLootRarity.UNCOMMON) {
            return COLOR_APPRENTICE;
        } else if (tier == SpellLootRarity.RARE) {
            return COLOR_ADEPT;
        }
        return COLOR_MASTER;
    }

    private static int rankColor(int rank) {
        if (rank >= 4) {
            return RANK_COLOR_MASTER;
        } else if (rank == 3) {
            return RANK_COLOR_EXPERT;
        } else if (rank == 2) {
            return RANK_COLOR_ADEPT;
        }
        return RANK_COLOR_NOVICE;
    }

    private static MutableComponent spellName(SpellType spell) {
        return Component.translatable("spell." + ExampleMod.MODID + "." + spell.getId());
    }

    private float maxScroll() {
        float content = rows.size() * ROW_HEIGHT;
        float viewport = listBottom - listTop;
        return Math.max(0.0F, content - viewport);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int divX = panelLeft + 12 + LIST_WIDTH + 6;
        if (mouseX > divX) {
            // Over the detail column: long entries (Apparition's source list) scroll here.
            detailScroll = clampDetailScroll(detailScroll - (float) deltaY * DETAIL_LINE * 2.0F);
            return true;
        }
        scroll = clampScroll(scroll - (float) deltaY * ROW_HEIGHT * 2.0F);
        return true;
    }

    private float maxDetailScroll() {
        return Math.max(0.0F, detailContentHeight - (detailBottom - detailTop));
    }

    private float clampDetailScroll(float value) {
        return Math.max(0.0F, Math.min(value, maxDetailScroll()));
    }

    private float clampScroll(float value) {
        return Math.max(0.0F, Math.min(value, maxScroll()));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= panelLeft + 12 && mouseX <= panelLeft + 12 + LIST_WIDTH
                && mouseY >= listTop && mouseY <= listBottom) {
            int index = (int) ((mouseY - listTop + scroll) / ROW_HEIGHT);
            if (index >= 0 && index < rows.size()) {
                Row row = rows.get(index);
                if (!row.header() && row.spell() != null) {
                    if (selected != row.spell()) {
                        detailScroll = 0.0F;
                    }
                    selected = row.spell();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Hard no-op: never run the GameRenderer menu-blur post effect for this screen,
        // regardless of how the background gets drawn.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Use the plain dark dim rather than renderBackground(), which also applies the
        // menu blur shader — matches WandRadialMenuScreen and keeps the world crisp behind.
        this.renderTransparentBackground(g);

        int right = panelLeft + PANEL_WIDTH;
        int bottom = panelTop + PANEL_HEIGHT;

        // Panel + border.
        g.fill(panelLeft - 4, panelTop - 4, right + 4, bottom + 4, 0xF0140A1E);
        drawBorder(g, panelLeft - 4, panelTop - 4, right + 4, bottom + 4, COLOR_GOLD);
        // Divider between list and detail panes.
        int divX = panelLeft + 12 + LIST_WIDTH + 6;
        g.fill(divX, listTop, divX + 1, listBottom, 0x66D4AF37);

        // Title + progress header.
        g.drawCenteredString(this.font, this.title, panelLeft + PANEL_WIDTH / 2, panelTop + 8, COLOR_GOLD);
        Component progress = Component.translatable(
                "gui." + ExampleMod.MODID + ".grimoire.progress", learnedCount, totalCount);
        g.drawCenteredString(this.font, progress, panelLeft + PANEL_WIDTH / 2, panelTop + 22, COLOR_BODY);
        drawProgressBar(g, panelLeft + PANEL_WIDTH / 2 - 80, panelTop + 33, 160, 3);

        // Dark Magic: the reader's own Soul, right-aligned in the title row. The tier only, never the number.
        int soulTier = DarkMagicClientState.tier();
        Component soul = Component.translatable("gui." + ExampleMod.MODID + ".grimoire.soul",
                Component.translatable(com.hogwartsmagic.spell.dark.DarkRules.tierKey(soulTier)));
        g.drawString(this.font, soul, right - 8 - this.font.width(soul), panelTop + 8,
                com.hogwartsmagic.spell.dark.DarkRules.tierColor(soulTier));

        // Scrolling spell list (clipped).
        g.enableScissor(panelLeft + 12, listTop, panelLeft + 12 + LIST_WIDTH, listBottom);
        int x = panelLeft + 16;
        int y = (int) (listTop - scroll);
        for (Row row : rows) {
            if (y + ROW_HEIGHT >= listTop && y <= listBottom) {
                if (row.header()) {
                    g.drawString(this.font, row.label(), x - 2, y + 2, row.color());
                } else {
                    boolean isSel = row.spell() == selected;
                    if (isSel) {
                        g.fill(panelLeft + 12, y, panelLeft + 12 + LIST_WIDTH, y + ROW_HEIGHT, 0x40D4AF37);
                    }
                    String marker = row.learned() ? "✔ " : "✖ ";
                    g.drawString(this.font, Component.literal(marker).append(row.label()), x + 4, y + 2, row.color());
                }
            }
            y += ROW_HEIGHT;
        }
        g.disableScissor();

        tooltipPending = false;
        renderDetail(g, divX + 10, listTop, right - 12, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);

        // Drawn after the scissor is released so it is never clipped.
        if (tooltipPending) {
            com.hogwartsmagic.spell.mastery.MasteryConfig.Values mastery =
                    com.hogwartsmagic.spell.mastery.MasteryConfig.of(player);
            List<Component> tip = new java.util.ArrayList<>();
            tip.add(Component.translatable(
                    "gui." + ExampleMod.MODID + ".mastery.tooltip",
                    Math.round(mastery.rankDiscount() * 100),
                    String.format("%.0f", mastery.manaPerRank())));
            int nextRank = com.hogwartsmagic.spell.mastery.SpellMasteryData.getRank(player, selected) + 1;
            if (nextRank <= com.hogwartsmagic.spell.mastery.MasteryMath.MAX_RANK) {
                tip.add(Component.translatable("gui." + ExampleMod.MODID + ".mastery.upgrade_preview",
                        com.hogwartsmagic.spell.mastery.MasteryMath.roman(nextRank),
                        Component.translatable(com.hogwartsmagic.spell.mastery.MasteryAwards.rankKey(nextRank))));
                tip.add(Component.translatable(
                        com.hogwartsmagic.spell.mastery.MasteryAwards.rankChangeKey(selected, nextRank)));
            }
            g.renderComponentTooltip(this.font, tip, mouseX, mouseY);
        }
    }

    private void renderDetail(GuiGraphics g, int x, int y, int rightEdge, int mouseX, int mouseY) {
        if (selected == null) {
            return;
        }
        int wrap = rightEdge - x;
        detailTop = y;
        detailBottom = panelTop + 234;
        detailScroll = clampDetailScroll(detailScroll);
        int start = y - Math.round(detailScroll);

        // Nothing in the detail column may draw over the Close button or the panel edge; what
        // doesn't fit scrolls with the mouse wheel over this column.
        g.enableScissor(x, detailTop, rightEdge, detailBottom);
        try {
            int end = renderDetailBody(g, x, start, wrap, mouseX, mouseY);
            detailContentHeight = end - start;
        } finally {
            g.disableScissor();
        }

        float max = maxDetailScroll();
        if (max > 0.0F) {
            int track = detailBottom - detailTop;
            int thumb = Math.max(12, Math.round(track * (track / (float) (track + max))));
            int thumbTop = detailTop + Math.round((track - thumb) * (detailScroll / max));
            g.fill(rightEdge + 3, detailTop, rightEdge + 5, detailBottom, 0x40D4AF37);
            g.fill(rightEdge + 3, thumbTop, rightEdge + 5, thumbTop + thumb, COLOR_GOLD);
        }
    }

    /** Draws the selected spell's detail from {@code y} down and returns the y just below the last line. */
    private int renderDetailBody(GuiGraphics g, int x, int y, int wrap, int mouseX, int mouseY) {

        // Spell name in its tier color.
        g.drawString(this.font, spellName(selected), x, y, tierColor(selected.getLootRarity()));
        y += 13;

        // Learned / locked status line.
        boolean learned = MagicBookItem.isSpellLearned(player, selected);
        Component status = learned
                ? Component.translatable("gui." + ExampleMod.MODID + ".grimoire.learned")
                : Component.translatable("gui." + ExampleMod.MODID + ".grimoire.locked");
        g.drawString(this.font, status, x, y, learned ? COLOR_NOVICE : COLOR_MASTER);
        y += 16;

        if (learned) {
            float xp = com.hogwartsmagic.spell.mastery.SpellMasteryData.getXp(player, selected);
            int[] thresholds = com.hogwartsmagic.spell.mastery.MasteryConfig.of(player).thresholds();
            int rank = com.hogwartsmagic.spell.mastery.MasteryMath.rankForXp(xp, thresholds);
            int rankColor = rankColor(rank);
            Component rankLine = Component.translatable("gui." + ExampleMod.MODID + ".mastery.rank_line",
                    com.hogwartsmagic.spell.mastery.MasteryMath.roman(rank),
                    Component.translatable(com.hogwartsmagic.spell.mastery.MasteryAwards.rankKey(rank)));
            g.drawString(this.font, rankLine, x, y, rankColor);

            // XP bar on the same row, right after the rank text.
            int barX = x + this.font.width(rankLine) + 6;
            int barWidth = 60;
            float progress = Math.max(0f, Math.min(1f,
                    com.hogwartsmagic.spell.mastery.MasteryMath.rankProgress(xp, thresholds)));
            g.fill(barX, y + 2, barX + barWidth, y + 6, 0xFF2A2340);
            g.fill(barX, y + 2, barX + Math.round(barWidth * progress), y + 6, rankColor);
            if (mouseX >= x && mouseX < barX + barWidth && mouseY >= y && mouseY < y + 9
                    && mouseY >= detailTop && mouseY < detailBottom) {
                tooltipPending = true;
            }
            y += 11;

            float toNext = com.hogwartsmagic.spell.mastery.MasteryMath.xpToNextRank(xp, thresholds);
            Component next = toNext < 0
                    ? Component.translatable("gui." + ExampleMod.MODID + ".mastery.maxed")
                    : Component.translatable("gui." + ExampleMod.MODID + ".mastery.next",
                            String.format("%.0f", Math.ceil(toNext)),
                            com.hogwartsmagic.spell.mastery.MasteryMath.roman(rank + 1));
            g.drawString(this.font, next, x, y, COLOR_BODY);
            y += 15;
        }

        // Description (wrapped).
        Component desc = Component.translatable("spell." + ExampleMod.MODID + "." + selected.getId() + ".desc");
        for (FormattedCharSequence line : this.font.split(desc, wrap)) {
            g.drawString(this.font, line, x, y, COLOR_BODY);
            y += 11;
        }
        y += 8;

        // Stats: mana cost + cooldown in seconds.
        Object manaCost;
        if (selected == SpellType.APPARITION) {
            manaCost = com.hogwartsmagic.event.ModEvents.APPARITION_MANA_COST;
        } else {
            manaCost = com.hogwartsmagic.spell.SpellCosts.manaCost(player, net.minecraft.world.item.ItemStack.EMPTY, selected);
        }
        Component mana = Component.translatable(
                "gui." + ExampleMod.MODID + ".grimoire.mana", manaCost);
        g.drawString(this.font, mana, x, y, 0xFF8FC9FF);
        y += 12;
        String cd = String.format("%.1f",
                com.hogwartsmagic.spell.SpellCosts.cooldownTicks(player, net.minecraft.world.item.ItemStack.EMPTY, selected) / 20.0F);
        Component cooldown = Component.translatable(
                "gui." + ExampleMod.MODID + ".grimoire.cooldown", cd);
        g.drawString(this.font, cooldown, x, y, 0xFFB8B8B8);
        y += 18;

        // Where to find the spellbook.
        g.drawString(this.font, Component.translatable("gui." + ExampleMod.MODID + ".grimoire.found_in"),
                x, y, COLOR_GOLD);
        y += 12;
        for (String sourceKey : MagicBookChestLoot.getSourceHintKeys(selected)) {
            Component bullet = Component.literal("• ").append(Component.translatable(sourceKey));
            for (FormattedCharSequence line : this.font.split(bullet, wrap)) {
                g.drawString(this.font, line, x + 2, y, COLOR_BODY);
                y += 11;
            }
        }
        return y;
    }

    private void drawProgressBar(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF2A2238);
        if (totalCount > 0) {
            int filled = Math.round(w * (learnedCount / (float) totalCount));
            g.fill(x, y, x + filled, y + h, COLOR_GOLD);
        }
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
