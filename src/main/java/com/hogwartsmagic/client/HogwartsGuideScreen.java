package com.hogwartsmagic.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The Field Guide.
 *
 * <p>All of its text lives in the lang file, under {@code guide.hogwartsmagic.<section>.<page>}: the
 * page's heading is {@code .title} and its paragraphs are {@code .1}, {@code .2} and so on. This
 * class only knows how many paragraphs each page has, which is what lets a translation reword a
 * paragraph freely but not quietly lose one.
 *
 * <h2>Pages are measured, not assumed</h2>
 *
 * <p>The guide used to draw each page's paragraphs in one run from the top, and the longer ones
 * (the merpeople, the Willow) ran straight off the bottom of the sheet. A page here is a
 * <em>topic</em>, and the sheets actually shown are cut from it when the screen opens: each
 * paragraph is wrapped to the real text width with the real font, and a topic that does not fit
 * carries on to a following sheet headed "(continued)". Cutting it at open rather than at write time
 * is the only way the answer stays right under another language, another font or a different GUI
 * scale.
 *
 * <p>Paragraphs are kept whole where they can be. A paragraph moves to the next sheet rather than
 * being split across two, unless it is longer than a sheet by itself, in which case it has to be
 * split somewhere.
 */
public class HogwartsGuideScreen extends Screen {
    private static final int PAGE_WIDTH = 420;
    private static final int PAGE_HEIGHT = 248;

    /** Where the text starts, below the section title, and the heading line that opens every sheet. */
    private static final int TEXT_TOP = 52;
    private static final int HEADING_HEIGHT = 16;
    /** The text stops this far above the bottom edge, clear of the page buttons and the page count. */
    private static final int TEXT_BOTTOM_MARGIN = 32;
    private static final int PARAGRAPH_GAP = 5;
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_PITCH = 22;

    private Section currentSection = Section.WELCOME;
    private int currentPage;
    private int left;
    private int top;
    /** Every section's sheets, cut once per open so a page flip costs nothing. */
    private final Map<Section, List<Sheet>> sheets = new EnumMap<>(Section.class);

    public HogwartsGuideScreen() {
        super(Component.translatable("gui.hogwartsmagic.hogwarts_guide"));
    }

    @Override
    protected void init() {
        super.init();
        this.left = (this.width - PAGE_WIDTH) / 2;
        this.top = (this.height - PAGE_HEIGHT) / 2;
        this.sheets.clear();
        for (Section section : Section.values()) {
            this.sheets.put(section, layOut(section));
        }
        rebuildButtons();
    }

    private void rebuildButtons() {
        this.clearWidgets();

        int tabX = this.left - 126;
        int tabY = this.top + 16;
        for (Section section : Section.values()) {
            Section target = section;
            this.addRenderableWidget(Button.builder(section.label(), button -> {
                this.currentSection = target;
                this.currentPage = 0;
                rebuildButtons();
            }).bounds(tabX, tabY, 112, TAB_HEIGHT).build());
            tabY += TAB_PITCH;
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.hogwartsmagic.previous"), button -> turn(-1))
                .bounds(this.left + 18, this.top + PAGE_HEIGHT - 28, 70, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.hogwartsmagic.next"), button -> turn(1))
                .bounds(this.left + PAGE_WIDTH - 88, this.top + PAGE_HEIGHT - 28, 70, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.hogwartsmagic.close"), button -> onClose())
                .bounds(this.left + PAGE_WIDTH - 88, this.top + 12, 70, 20)
                .build());
    }

    private void turn(int by) {
        int last = this.sheets.get(this.currentSection).size() - 1;
        this.currentPage = Mth.clamp(this.currentPage + by, 0, Math.max(0, last));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // The wheel turns pages, which is the natural thing to reach for now that a section can run
        // to a dozen of them.
        if (scrollY != 0.0D) {
            turn(scrollY < 0.0D ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fill(0, 0, this.width, this.height, 0xB0100906);

        guiGraphics.fill(this.left - 2, this.top - 2, this.left + PAGE_WIDTH + 2, this.top + PAGE_HEIGHT + 2, 0xFF6A4A25);
        guiGraphics.fill(this.left, this.top, this.left + PAGE_WIDTH, this.top + PAGE_HEIGHT, 0xFFF3E2BE);
        guiGraphics.fill(this.left, this.top, this.left + PAGE_WIDTH, this.top + 4, 0xFFFFF5DE);
        guiGraphics.fill(this.left, this.top + PAGE_HEIGHT - 4, this.left + PAGE_WIDTH, this.top + PAGE_HEIGHT, 0xFFD4B07B);
        guiGraphics.fill(this.left + PAGE_WIDTH / 2 - 1, this.top + 8, this.left + PAGE_WIDTH / 2 + 1, this.top + PAGE_HEIGHT - 36, 0x44926C3B);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.hogwartsmagic.hogwarts_guide.header"), this.left + PAGE_WIDTH / 2, this.top + 14, 0x5A3420);
        guiGraphics.drawCenteredString(this.font, this.currentSection.label(), this.left + PAGE_WIDTH / 2, this.top + 30, 0x6D4725);

        List<Sheet> pages = this.sheets.get(this.currentSection);
        this.currentPage = Mth.clamp(this.currentPage, 0, Math.max(0, pages.size() - 1));
        Sheet page = pages.get(this.currentPage);

        int textLeft = this.left + 20;
        int textTop = this.top + TEXT_TOP;

        guiGraphics.drawString(this.font, page.title().copy().withStyle(ChatFormatting.BOLD), textLeft, textTop, 0x402616, false);
        int cursorY = textTop + HEADING_HEIGHT;
        for (Row row : page.rows()) {
            guiGraphics.drawString(this.font, row.text(), textLeft, cursorY, 0x3A2416, false);
            cursorY += row.height();
        }

        guiGraphics.drawCenteredString(this.font, Component.literal((this.currentPage + 1) + " / " + pages.size()), this.left + PAGE_WIDTH / 2, this.top + PAGE_HEIGHT - 22, 0x6D4725);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Cuts a section's topics into sheets that fit.
     *
     * <p>The budget is counted in pixels rather than lines because a paragraph's trailing gap is not
     * a line high, and counting it as one would waste a line on every sheet.
     */
    private List<Sheet> layOut(Section section) {
        int textWidth = PAGE_WIDTH - 40;
        int budget = PAGE_HEIGHT - TEXT_BOTTOM_MARGIN - TEXT_TOP - HEADING_HEIGHT;
        int lineHeight = this.font.lineHeight + 1;

        List<Sheet> out = new ArrayList<>();
        for (Page topic : section.pages()) {
            Component title = topic.title();
            List<Row> rows = new ArrayList<>();
            int used = 0;
            for (Component paragraph : topic.paragraphs()) {
                List<FormattedCharSequence> lines = this.font.split(paragraph, textWidth);
                int whole = lines.size() * lineHeight;
                // Not enough room left, but it would fit on a clean sheet: start one rather than split it.
                if (used > 0 && used + whole > budget && whole <= budget) {
                    out.add(new Sheet(title, rows));
                    title = continued(topic.title());
                    rows = new ArrayList<>();
                    used = 0;
                }
                for (int i = 0; i < lines.size(); i++) {
                    if (used > 0 && used + lineHeight > budget) {
                        out.add(new Sheet(title, rows));
                        title = continued(topic.title());
                        rows = new ArrayList<>();
                        used = 0;
                    }
                    boolean last = i == lines.size() - 1;
                    int height = lineHeight + (last ? PARAGRAPH_GAP : 0);
                    rows.add(new Row(lines.get(i), height));
                    used += height;
                }
            }
            out.add(new Sheet(title, rows));
        }
        return out;
    }

    private static Component continued(Component title) {
        return title.copy().append(" ").append(Component.translatable("gui.hogwartsmagic.hogwarts_guide.continued"));
    }

    /** One topic as written: a heading and its paragraphs, all by lang key. */
    private record Page(Component title, List<Component> paragraphs) {}

    /** One line of wrapped text and how far to drop afterwards. */
    private record Row(FormattedCharSequence text, int height) {}

    /** One sheet as shown: a topic, or the next part of one that did not fit. */
    private record Sheet(Component title, List<Row> rows) {}

    private enum Section {
        WELCOME("gui.hogwartsmagic.hogwarts_guide.section.welcome", List.of(
                page("welcome", "letter", 3),
                page("welcome", "first_days", 4)
        )),
        SURVIVAL("gui.hogwartsmagic.hogwarts_guide.section.survival", List.of(
                page("survival", "staying_alive", 3),
                page("survival", "older_students", 3),
                page("survival", "sleepless_nights", 3),
                page("survival", "death_eater_raids", 3),
                page("survival", "trees_that_hit_back", 2)
        )),
        SPELLS("gui.hogwartsmagic.hogwarts_guide.section.spells", List.of(
                page("spells", "study", 2),
                page("spells", "disciplines", 5),
                page("spells", "wheel", 4),
                page("spells", "mastery", 2),
                page("spells", "tomes", 2),
                page("spells", "duels", 3),
                page("spells", "apparition", 4)
        )),
        SYSTEMS("gui.hogwartsmagic.hogwarts_guide.section.systems", List.of(
                page("systems", "wider_world", 4),
                page("systems", "floo", 2),
                page("systems", "owl_post", 3),
                page("systems", "brooms", 3),
                page("systems", "mysteries", 3),
                page("systems", "dark_rite", 3)
        )),
        GRINGOTTS("gui.hogwartsmagic.hogwarts_guide.section.gringotts", List.of(
                page("gringotts", "finding", 2),
                page("gringotts", "gate", 2),
                page("gringotts", "cart", 3),
                page("gringotts", "vaults", 2),
                page("gringotts", "ironbelly", 2)
        )),
        DARK_MAGIC("gui.hogwartsmagic.hogwarts_guide.section.dark_magic", List.of(
                page("dark_magic", "weight", 5),
                page("dark_magic", "five_states", 3),
                page("dark_magic", "costs", 5),
                page("dark_magic", "offers", 4),
                page("dark_magic", "way_back", 5)
        )),
        CREATURES("gui.hogwartsmagic.hogwarts_guide.section.creatures", List.of(
                page("creatures", "friends_and_terrors", 2),
                page("creatures", "warnings", 3),
                page("creatures", "hippogriffs", 2),
                page("creatures", "house_elves", 4),
                page("creatures", "basilisk", 4),
                page("creatures", "merpeople", 8),
                page("creatures", "grindylows", 5),
                page("creatures", "boggarts", 5),
                page("creatures", "giants", 4),
                page("creatures", "willow", 6),
                page("creatures", "freeing_elf", 4)
        )),
        CRAFTING("gui.hogwartsmagic.hogwarts_guide.section.crafting", List.of(
                page("crafting", "wands", 3),
                page("crafting", "benches", 3),
                page("crafting", "broom_recipes", 3),
                page("crafting", "build_first", 3)
        )),
        POTIONS("gui.hogwartsmagic.hogwarts_guide.section.potions", List.of(
                page("potions", "the_wizard_cauldron", 4),
                page("potions", "signs_in_the_liquid", 4),
                page("potions", "wiggenweld_potion", 5),
                page("potions", "pepperup_potion", 5),
                page("potions", "polyjuice_potion", 6),
                page("potions", "polyjuice_the_sample", 4),
                page("potions", "essence_of_dittany", 4),
                page("potions", "mandrake_restorative_draught", 6),
                page("potions", "using_the_mandrake_draught", 3),
                page("potions", "draught_of_living_death", 5),
                page("potions", "living_death_the_finish", 4),
                page("potions", "felix_felicis", 5),
                page("potions", "felix_felicis_patience", 3),
                page("potions", "potion_of_remorse", 6),
                page("potions", "where_ingredients_grow", 5),
                page("potions", "turned_to_stone", 3)
        ));

        private final Component label;
        private final List<Page> pages;

        Section(String labelKey, List<Page> pages) {
            this.label = Component.translatable(labelKey);
            this.pages = pages;
        }

        public Component label() {
            return this.label;
        }

        public List<Page> pages() {
            return this.pages;
        }

        /** A topic whose heading is {@code guide.hogwartsmagic.<section>.<id>.title} and whose paragraphs are {@code .1} to {@code .<paragraphs>}. */
        private static Page page(String section, String id, int paragraphs) {
            String base = "guide.hogwartsmagic." + section + "." + id;
            List<Component> components = new ArrayList<>();
            for (int i = 1; i <= paragraphs; i++) {
                components.add(Component.translatable(base + "." + i));
            }
            return new Page(Component.translatable(base + ".title"), components);
        }
    }
}
