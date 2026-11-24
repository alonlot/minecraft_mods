package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/**
 * The Field Guide (and, opened by that book, Advanced Potion-Making).
 *
 * <p>All of its text lives in the lang file, under {@code guide.hogwartsmagic.<section>.<page>}: the
 * page's heading is {@code .title} and its paragraphs are {@code .1}, {@code .2} and so on. This
 * class only knows how many paragraphs each page has, which is what lets a translation reword a
 * paragraph freely but not quietly lose one.
 *
 * <h2>The book</h2>
 *
 * <p>It is drawn as an open leather book: two parchment pages either side of a shaded spine, a
 * ribbon in the current section's colour, and one coloured bookmark tab per section on the fore
 * edge, each carrying an item icon (its name is the tab's tooltip). Pages turn by the curled
 * corners, the wheel, or the arrow keys, with the vanilla page-turn sound. Nothing here is a
 * vanilla button: the grey widgets were the main thing that made the old guide look like a menu.
 *
 * <h2>Pages are measured, not assumed</h2>
 *
 * <p>Every section opens with a contents page whose entries jump to their topic. After it the topics
 * flow one after another, wrapped to the real page width with the real font when the screen opens,
 * so the answer stays right under another language, another font or a different GUI scale. A topic
 * starts on a fresh page only when its heading and first lines would not fit where the last one
 * ended. A paragraph may break across pages, but never leaving a single line on either side, and a
 * page that opens mid-topic repeats the topic's name with "(continued)".
 */
public class HogwartsGuideScreen extends Screen {
    private static final int MAX_BOOK_WIDTH = 480;
    private static final int MAX_BOOK_HEIGHT = 270;
    /** Leather showing around the pages. */
    private static final int COVER = 9;
    private static final int GUTTER = 2;
    private static final int PAD_X = 14;
    /** The running head band at the top of a page, and the page number band at the bottom. */
    private static final int HEAD = 26;
    private static final int FOOT = 22;
    /** How far a bookmark tab sticks out of the fore edge. */
    private static final int TAB_OUT = 24;

    private static final int HEADING_HEIGHT = 14;
    private static final int TITLE_HEIGHT = 32;
    private static final int ENTRY_HEIGHT = 12;
    private static final int PARAGRAPH_GAP = 4;
    private static final int TOPIC_GAP = 8;
    private static final int CURL = 10;

    private static final int INK = 0xFF33200F;
    private static final int HEADING_INK = 0xFF5C2A12;
    private static final int FADED_INK = 0xFF8C6B45;
    private static final int GOLD = 0xFFC9A24E;
    private static final int PARCHMENT_TOP = 0xFFF5E7C6;
    private static final int PARCHMENT_BOTTOM = 0xFFEAD4A6;

    // Row kinds. Plain ints, not an enum: a switch on an enum has failed to load in this mod's dev env.
    private static final int TEXT = 0;
    private static final int HEADING = 1;
    private static final int CONTINUED = 2;
    private static final int TITLE = 3;
    private static final int ENTRY = 4;
    private static final int SPACE = 5;

    /** Where each book was left, so reopening it lands on the same spread. Client-side only. */
    private static Section lastGuideSection = Section.WELCOME;
    private static int lastGuideSpread;
    private static int lastAdvancedSpread;

    /** True when opened by the Advanced Potion-Making book: only the ADVANCED_POTIONS section, no tabs. */
    private final boolean advancedBook;
    private Section currentSection;
    private int spread;

    private int bookX;
    private int bookY;
    private int bookW;
    private int bookH;
    private int pageW;
    private int pageH;

    /** Every section's sheets, cut once per open so a page flip costs nothing. */
    private final Map<Section, List<Sheet>> sheets = new EnumMap<>(Section.class);
    private final Map<Section, ItemStack> icons = new EnumMap<>(Section.class);
    /** Faint flecks in the parchment, as page-relative x, y and alpha triples. */
    private int[] flecks = new int[0];
    /** What can be clicked this frame, rebuilt by every render. */
    private final List<Hit> hits = new ArrayList<>();

    public HogwartsGuideScreen() {
        this(false);
    }

    public HogwartsGuideScreen(boolean advancedBook) {
        super(Component.translatable(advancedBook
                ? "gui.hogwartsmagic.advanced_potion_making" : "gui.hogwartsmagic.hogwarts_guide"));
        this.advancedBook = advancedBook;
        if (advancedBook) {
            this.currentSection = Section.ADVANCED_POTIONS;
            this.spread = lastAdvancedSpread;
        } else {
            this.currentSection = lastGuideSection;
            this.spread = lastGuideSpread;
        }
    }

    @Override
    protected void init() {
        super.init();
        int tabRoom = this.advancedBook ? 0 : TAB_OUT;
        this.bookW = Math.min(MAX_BOOK_WIDTH, this.width - 16 - 2 * tabRoom);
        this.bookH = Math.min(MAX_BOOK_HEIGHT, this.height - 16);
        this.pageW = (this.bookW - 2 * COVER - GUTTER) / 2;
        this.pageH = this.bookH - 2 * COVER;
        this.bookW = 2 * COVER + GUTTER + 2 * this.pageW;
        this.bookX = (this.width - this.bookW) / 2;
        this.bookY = (this.height - this.bookH) / 2;

        this.sheets.clear();
        this.icons.clear();
        for (Section section : Section.values()) {
            if (shown(section)) {
                this.sheets.put(section, layOut(section));
                this.icons.put(section, new ItemStack(section.icon.get()));
            }
        }
        this.spread = Mth.clamp(this.spread, 0, lastSpread());

        Random random = new Random(0x5EEDL);
        int count = 70;
        this.flecks = new int[count * 3];
        for (int i = 0; i < count; i++) {
            this.flecks[i * 3] = random.nextInt(Math.max(1, this.pageW));
            this.flecks[i * 3 + 1] = random.nextInt(Math.max(1, this.pageH));
            this.flecks[i * 3 + 2] = 0x10 + random.nextInt(0x18);
        }
    }

    /** The guide lists every section but ADVANCED_POTIONS; the book shows only that one. */
    private boolean shown(Section section) {
        if (this.advancedBook) {
            return section == Section.ADVANCED_POTIONS;
        }
        return section != Section.ADVANCED_POTIONS;
    }

    private int lastSpread() {
        List<Sheet> list = this.sheets.get(this.currentSection);
        return list == null ? 0 : Math.max(0, (list.size() + 1) / 2 - 1);
    }

    private void turn(int by) {
        goToSpread(this.spread + by);
    }

    private void goToSpread(int target) {
        int next = Mth.clamp(target, 0, lastSpread());
        if (next != this.spread) {
            this.spread = next;
            playPageTurn();
        }
    }

    private void openSection(Section section) {
        if (section != this.currentSection) {
            this.currentSection = section;
            this.spread = 0;
            playPageTurn();
        }
    }

    private static void playPageTurn() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    @Override
    public void removed() {
        if (this.advancedBook) {
            lastAdvancedSpread = this.spread;
        } else {
            lastGuideSection = this.currentSection;
            lastGuideSpread = this.spread;
        }
        super.removed();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D) {
            turn(scrollY < 0.0D ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_PAGE_DOWN || keyCode == GLFW.GLFW_KEY_D) {
            turn(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_A) {
            turn(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            goToSpread(0);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Hit hit : this.hits) {
                if (hit.contains(mouseX, mouseY)) {
                    hit.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ------------------------------------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
        g.fill(0, 0, this.width, this.height, 0x90100906);
        this.hits.clear();

        List<Sheet> pages = this.sheets.get(this.currentSection);
        this.spread = Mth.clamp(this.spread, 0, lastSpread());

        int leftX = this.bookX + COVER;
        int rightX = leftX + this.pageW + GUTTER;
        int pageY = this.bookY + COVER;

        // Drop shadow under the whole book.
        g.fill(this.bookX + 4, this.bookY + 5, this.bookX + this.bookW + 4, this.bookY + this.bookH + 5, 0x70000000);

        if (!this.advancedBook) {
            drawTabs(g, mouseX, mouseY);
        }
        drawCover(g);
        drawPageStacks(g, leftX, rightX, pageY);
        drawParchment(g, leftX, pageY);
        drawParchment(g, rightX, pageY);
        drawSpine(g, leftX, rightX, pageY);
        drawRibbon(g, rightX, pageY);

        // Running heads: the book on the left, the section (or, in the one-section book, the topic) on the right.
        int leftSheet = this.spread * 2;
        int rightSheet = leftSheet + 1;
        Component leftHead = this.advancedBook ? this.title : Component.translatable("gui.hogwartsmagic.hogwarts_guide.header");
        Component rightHead = this.currentSection.label();
        if (this.advancedBook && rightSheet < pages.size()) {
            rightHead = pages.get(rightSheet).topic();
        }
        drawRunningHead(g, leftHead, leftX);
        drawRunningHead(g, rightHead, rightX);

        drawSheet(g, pages, leftSheet, leftX, pageY, mouseX, mouseY);
        if (rightSheet < pages.size()) {
            drawSheet(g, pages, rightSheet, rightX, pageY, mouseX, mouseY);
        } else {
            drawEndOrnament(g, rightX, pageY);
        }

        drawCurls(g, leftX, rightX, pageY, mouseX, mouseY);
        drawCloseButton(g, mouseX, mouseY);

        for (Hit hit : this.hits) {
            if (hit.tooltip() != null && hit.contains(mouseX, mouseY)) {
                g.renderTooltip(this.font, hit.tooltip(), mouseX, mouseY);
                break;
            }
        }
    }

    private void drawCover(GuiGraphics g) {
        int leather = this.advancedBook ? 0xFF1F3A27 : 0xFF5A2D15;
        int x0 = this.bookX;
        int y0 = this.bookY;
        int x1 = x0 + this.bookW;
        int y1 = y0 + this.bookH;
        g.fill(x0, y0, x1, y1, 0xFF1E0F06);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, leather);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, brighten(leather, 28));
        g.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, darken(leather, 30));
        // Gold tooling, with a stud at each corner.
        int t = 4;
        outline(g, x0 + t, y0 + t, x1 - t, y1 - t, 0x99C9A24E);
        int[][] corners = {{x0 + t, y0 + t}, {x1 - t - 1, y0 + t}, {x0 + t, y1 - t - 1}, {x1 - t - 1, y1 - t - 1}};
        for (int[] c : corners) {
            g.fill(c[0] - 1, c[1] - 1, c[0] + 2, c[1] + 2, GOLD);
        }
    }

    private void drawPageStacks(GuiGraphics g, int leftX, int rightX, int pageY) {
        int bottom = pageY + this.pageH;
        // The left stack shows at its left and bottom edges, the right stack at its right and bottom.
        g.fill(leftX - 2, pageY + 2, leftX + this.pageW, bottom + 2, 0xFFBFA273);
        g.fill(leftX - 1, pageY + 1, leftX + this.pageW, bottom + 1, 0xFFDCC594);
        g.fill(rightX, pageY + 2, rightX + this.pageW + 2, bottom + 2, 0xFFBFA273);
        g.fill(rightX, pageY + 1, rightX + this.pageW + 1, bottom + 1, 0xFFDCC594);
    }

    private void drawParchment(GuiGraphics g, int x, int y) {
        g.fillGradient(x, y, x + this.pageW, y + this.pageH, PARCHMENT_TOP, PARCHMENT_BOTTOM);
        for (int i = 0; i + 2 < this.flecks.length; i += 3) {
            int fx = x + this.flecks[i];
            int fy = y + this.flecks[i + 1];
            g.fill(fx, fy, fx + 1, fy + 1, (this.flecks[i + 2] << 24) | 0x6B4A22);
        }
    }

    private void drawSpine(GuiGraphics g, int leftX, int rightX, int pageY) {
        int bottom = pageY + this.pageH;
        int spineL = leftX + this.pageW;
        g.fill(spineL, pageY, rightX, bottom, 0xFF7A5630);
        for (int i = 0; i < 12; i++) {
            int color = ((12 - i) * 5) << 24 | 0x3A2208;
            g.fill(spineL - 1 - i, pageY, spineL - i, bottom, color);
            g.fill(rightX + i, pageY, rightX + i + 1, bottom, color);
        }
        // A softer fall-off at the outer edges.
        for (int i = 0; i < 4; i++) {
            int color = ((4 - i) * 6) << 24 | 0x3A2208;
            g.fill(leftX + i, pageY, leftX + i + 1, bottom, color);
            g.fill(rightX + this.pageW - 1 - i, pageY, rightX + this.pageW - i, bottom, color);
        }
    }

    /** A silk ribbon from the head of the spine, hanging out past the bottom with a notched end. */
    private void drawRibbon(GuiGraphics g, int rightX, int pageY) {
        int color = this.advancedBook ? 0xFF8A1E22 : this.currentSection.color;
        int x0 = rightX + 3;
        int x1 = x0 + 5;
        int end = this.bookY + this.bookH + 9;
        g.fill(x0, pageY - 2, x1, end - 2, color);
        g.fill(x0, pageY - 2, x0 + 1, end - 2, brighten(color, 30));
        g.fill(x1 - 1, pageY - 2, x1, end - 2, darken(color, 35));
        // Notch: the two outer points run on, the middle stops short.
        g.fill(x0, end - 2, x0 + 2, end, color);
        g.fill(x1 - 2, end - 2, x1, end, darken(color, 20));
    }

    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        List<Section> shownSections = new ArrayList<>();
        for (Section section : Section.values()) {
            if (shown(section)) {
                shownSections.add(section);
            }
        }
        int top = this.bookY + COVER + 4;
        int room = this.pageH - 8;
        int pitch = Math.min(26, room / Math.max(1, shownSections.size()));
        int height = pitch - 3;
        int edge = this.bookX + this.bookW;
        for (int i = 0; i < shownSections.size(); i++) {
            Section section = shownSections.get(i);
            int y0 = top + i * pitch;
            int y1 = y0 + height;
            boolean active = section == this.currentSection;
            boolean hover = mouseX >= edge && mouseX < edge + TAB_OUT + 4 && mouseY >= y0 && mouseY < y1;
            int out = TAB_OUT + (active ? 4 : hover ? 2 : 0);
            int base = active ? brighten(section.color, 18) : hover ? brighten(section.color, 8) : section.color;
            g.fill(edge - 6, y0, edge + out, y1, darken(base, 45));
            g.fill(edge - 6, y0 + 1, edge + out - 1, y1 - 1, base);
            g.fill(edge - 6, y0 + 1, edge + out - 1, y0 + 2, brighten(base, 35));
            g.fill(edge - 6, y1 - 2, edge + out - 1, y1 - 1, darken(base, 25));
            g.renderItem(this.icons.get(section), edge + out - 20, y0 + (height - 16) / 2);
            Section target = section;
            this.hits.add(new Hit(edge, y0, edge + out, y1, () -> openSection(target), section.label()));
        }
    }

    private void drawRunningHead(GuiGraphics g, Component text, int pageX) {
        int cx = pageX + this.pageW / 2;
        int y = this.bookY + COVER + 9;
        drawFitted(g, text.copy().withStyle(ChatFormatting.ITALIC), cx, y, this.pageW - 2 * PAD_X, FADED_INK, true, 1.0F);
        int ruleY = y + 11;
        g.fill(pageX + PAD_X, ruleY, pageX + this.pageW - PAD_X, ruleY + 1, 0x50805A30);
    }

    private void drawSheet(GuiGraphics g, List<Sheet> pages, int index, int pageX, int pageY, int mouseX, int mouseY) {
        Sheet sheet = pages.get(index);
        int x = pageX + PAD_X;
        int width = textWidth();
        int y = pageY + HEAD;
        for (Row row : sheet.rows()) {
            if (row.kind() == TEXT) {
                g.drawString(this.font, row.line(), x, y, INK, false);
            } else if (row.kind() == HEADING) {
                drawFitted(g, row.text().copy().withStyle(ChatFormatting.BOLD), x, y, width, HEADING_INK, false, 1.0F);
                int w = Math.min(width, this.font.width(row.text().copy().withStyle(ChatFormatting.BOLD)));
                g.fill(x, y + 10, x + w, y + 11, 0x70A0783F);
            } else if (row.kind() == CONTINUED) {
                Component text = row.text().copy().append(" ")
                        .append(Component.translatable("gui.hogwartsmagic.hogwarts_guide.continued"))
                        .withStyle(ChatFormatting.ITALIC);
                drawFitted(g, text, x, y, width, FADED_INK, false, 1.0F);
            } else if (row.kind() == TITLE) {
                int cx = pageX + this.pageW / 2;
                drawFitted(g, row.text().copy().withStyle(ChatFormatting.BOLD), cx, y + 4, width, HEADING_INK, true, 1.5F);
                ornamentRule(g, cx, y + 22, Math.min(60, width / 2), 0xFFA0783F);
            } else if (row.kind() == ENTRY) {
                drawEntry(g, row, x, y, width, mouseX, mouseY);
            }
            y += row.height();
        }
        drawPageNumber(g, pageX, pageY, index + 1);
    }

    private void drawEntry(GuiGraphics g, Row row, int x, int y, int width, int mouseX, int mouseY) {
        String number = Integer.toString(row.target() + 1);
        int numberW = this.font.width(number);
        boolean hover = mouseX >= x - 3 && mouseX < x + width + 3 && mouseY >= y - 2 && mouseY < y + ENTRY_HEIGHT - 2;
        if (hover) {
            g.fill(x - 3, y - 2, x + width + 3, y + ENTRY_HEIGHT - 2, (0x30 << 24) | (this.currentSection.color & 0xFFFFFF));
        }
        String label = row.text().getString();
        int labelRoom = width - numberW - 12;
        if (this.font.width(label) > labelRoom) {
            label = this.font.plainSubstrByWidth(label, labelRoom - this.font.width("...")) + "...";
        }
        int color = hover ? HEADING_INK : INK;
        g.drawString(this.font, label, x, y, color, false);
        g.drawString(this.font, number, x + width - numberW, y, color, false);
        int dotsFrom = x + this.font.width(label) + 3;
        int dotsTo = x + width - numberW - 3;
        for (int dx = dotsFrom + 1; dx < dotsTo; dx += 3) {
            g.fill(dx, y + 7, dx + 1, y + 8, 0x90805A30);
        }
        int target = row.target();
        this.hits.add(new Hit(x - 3, y - 2, x + width + 3, y + ENTRY_HEIGHT - 2, () -> goToSpread(target / 2), null));
    }

    private void drawPageNumber(GuiGraphics g, int pageX, int pageY, int number) {
        int cx = pageX + this.pageW / 2;
        int y = pageY + this.pageH - 15;
        String text = Integer.toString(number);
        int w = this.font.width(text);
        g.drawString(this.font, text, cx - w / 2, y, FADED_INK, false);
        g.fill(cx - w / 2 - 14, y + 4, cx - w / 2 - 4, y + 5, 0x70805A30);
        g.fill(cx + w / 2 + 4, y + 4, cx + w / 2 + 14, y + 5, 0x70805A30);
    }

    /** The blank page that closes a section with an odd page count. */
    private void drawEndOrnament(GuiGraphics g, int pageX, int pageY) {
        int cx = pageX + this.pageW / 2;
        int cy = pageY + this.pageH / 2;
        ornamentRule(g, cx, cy, 34, 0x80A0783F);
    }

    /** Curled corners for turning: bottom-left of the left page back, bottom-right of the right page on. */
    private void drawCurls(GuiGraphics g, int leftX, int rightX, int pageY, int mouseX, int mouseY) {
        int bottom = pageY + this.pageH;
        int box = 26;
        if (this.spread > 0) {
            boolean hover = mouseX >= leftX && mouseX < leftX + box && mouseY >= bottom - box && mouseY < bottom;
            int s = hover ? CURL + 4 : CURL;
            for (int d = 0; d < s; d++) {
                int y = bottom - 1 - d;
                g.fill(leftX, y, leftX + s - d, y + 1, 0xFF3A1E0C);                 // the corner lifted away
                g.fill(leftX + s - d, y, leftX + s, y + 1, shade(0xFFD8C08E, d, s)); // the flap
            }
            g.fill(leftX + 1, bottom - s - 1, leftX + s, bottom - s, 0x40000000);
            chevron(g, leftX + s + 6, bottom - 9, false, hover ? HEADING_INK : 0x90805A30);
            this.hits.add(new Hit(leftX, bottom - box, leftX + box, bottom, () -> turn(-1),
                    Component.translatable("gui.hogwartsmagic.previous")));
        }
        if (this.spread < lastSpread()) {
            int right = rightX + this.pageW;
            boolean hover = mouseX >= right - box && mouseX < right && mouseY >= bottom - box && mouseY < bottom;
            int s = hover ? CURL + 4 : CURL;
            for (int d = 0; d < s; d++) {
                int y = bottom - 1 - d;
                g.fill(right - s + d, y, right, y + 1, 0xFF3A1E0C);
                g.fill(right - s, y, right - s + d, y + 1, shade(0xFFD8C08E, d, s));
            }
            g.fill(right - s, bottom - s - 1, right - 1, bottom - s, 0x40000000);
            chevron(g, right - s - 7, bottom - 9, true, hover ? HEADING_INK : 0x90805A30);
            this.hits.add(new Hit(right - box, bottom - box, right, bottom, () -> turn(1),
                    Component.translatable("gui.hogwartsmagic.next")));
        }
    }

    private void drawCloseButton(GuiGraphics g, int mouseX, int mouseY) {
        int size = 11;
        int x0 = this.bookX + this.bookW - size - 2;
        int y0 = this.bookY - 1;
        boolean hover = mouseX >= x0 && mouseX < x0 + size && mouseY >= y0 && mouseY < y0 + size;
        int disc = hover ? 0xFFE8C46A : GOLD;
        g.fill(x0 + 1, y0, x0 + size - 1, y0 + size, 0xFF3A2008);
        g.fill(x0, y0 + 1, x0 + size, y0 + size - 1, 0xFF3A2008);
        g.fill(x0 + 1, y0 + 1, x0 + size - 1, y0 + size - 1, disc);
        int cx = x0 + size / 2;
        int cy = y0 + size / 2;
        for (int i = -2; i <= 2; i++) {
            g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, 0xFF3A2008);
            g.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, 0xFF3A2008);
        }
        this.hits.add(new Hit(x0, y0, x0 + size, y0 + size, this::onClose, Component.translatable("gui.hogwartsmagic.close")));
    }

    private void drawFitted(GuiGraphics g, Component text, int x, int y, int maxWidth, int color, boolean centered, float scale) {
        int w = this.font.width(text);
        float s = Math.min(scale, maxWidth / (float) Math.max(1, w));
        g.pose().pushPose();
        g.pose().translate(centered ? x - w * s / 2.0F : x, y + (1.0F - Math.min(1.0F, s)) * 3.0F, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(this.font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private static void ornamentRule(GuiGraphics g, int cx, int y, int halfWidth, int color) {
        g.fill(cx - halfWidth, y, cx - 4, y + 1, color);
        g.fill(cx + 5, y, cx + halfWidth + 1, y + 1, color);
        g.fill(cx, y - 2, cx + 1, y + 3, color);
        g.fill(cx - 1, y - 1, cx + 2, y + 2, color);
        g.fill(cx - 2, y, cx + 3, y + 1, color);
    }

    private static void chevron(GuiGraphics g, int x, int cy, boolean pointsRight, int color) {
        for (int i = 0; i < 4; i++) {
            int col = pointsRight ? x + i : x - i;
            g.fill(col, cy - 3 + i, col + 1, cy + 4 - i, color);
        }
    }

    private static void outline(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
        g.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
    }

    /** The flap darkens toward its fold so it reads as curled rather than flat. */
    private static int shade(int color, int d, int s) {
        return darken(color, (s - d) * 3);
    }

    private static int brighten(int color, int by) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + by);
        int gr = Math.min(255, ((color >> 8) & 0xFF) + by);
        int b = Math.min(255, (color & 0xFF) + by);
        return (color & 0xFF000000) | (r << 16) | (gr << 8) | b;
    }

    private static int darken(int color, int by) {
        int r = Math.max(0, ((color >> 16) & 0xFF) - by);
        int gr = Math.max(0, ((color >> 8) & 0xFF) - by);
        int b = Math.max(0, (color & 0xFF) - by);
        return (color & 0xFF000000) | (r << 16) | (gr << 8) | b;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No-op: render() draws the plain dim itself (no blur, per the screen rule).
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------------------------------------

    private int textWidth() {
        return this.pageW - 2 * PAD_X;
    }

    private int textBudget() {
        return this.pageH - HEAD - FOOT;
    }

    /** Cuts a section into sheets: its contents first, then its topics flowing one after another. */
    private List<Sheet> layOut(Section section) {
        int width = textWidth();
        int budget = Math.max(this.font.lineHeight * 4, textBudget());
        int lineHeight = this.font.lineHeight + 1;
        List<Page> topics = section.pages();

        // Contents. Its entries' page numbers are not known yet, so each carries its topic's index for now.
        List<Sheet> contents = new ArrayList<>();
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(TITLE, null, section.label(), TITLE_HEIGHT, -1));
        int used = TITLE_HEIGHT;
        for (int t = 0; t < topics.size(); t++) {
            if (used + ENTRY_HEIGHT > budget) {
                contents.add(new Sheet(section.label(), rows));
                rows = new ArrayList<>();
                used = 0;
            }
            rows.add(new Row(ENTRY, null, topics.get(t).title(), ENTRY_HEIGHT, t));
            used += ENTRY_HEIGHT;
        }
        contents.add(new Sheet(section.label(), rows));

        Flow flow = new Flow(budget);
        int[] starts = new int[topics.size()];
        for (int t = 0; t < topics.size(); t++) {
            Page topic = topics.get(t);
            List<List<FormattedCharSequence>> paragraphs = new ArrayList<>();
            for (Component paragraph : topic.paragraphs()) {
                paragraphs.add(this.font.split(paragraph, width));
            }
            int firstLines = paragraphs.isEmpty() ? 0 : Math.min(3, paragraphs.get(0).size());
            int need = TOPIC_GAP + HEADING_HEIGHT + firstLines * lineHeight;
            if (flow.used > 0 && flow.used + need > budget) {
                flow.next(null);
            }
            if (flow.used > 0) {
                flow.add(new Row(SPACE, null, null, TOPIC_GAP, -1));
            }
            starts[t] = contents.size() + flow.done.size();
            flow.topic = topic.title();
            flow.add(new Row(HEADING, null, topic.title(), HEADING_HEIGHT, -1));

            for (List<FormattedCharSequence> lines : paragraphs) {
                int n = lines.size();
                int i = 0;
                while (i < n) {
                    int room = (budget - flow.used) / lineHeight;
                    int left = n - i;
                    int take = Math.min(room, left);
                    if (take < left) {
                        if (left - take < 2) {
                            take = left - 2; // never strand one line at the top of the next page
                        }
                        if (take < 2 && !flow.fresh) {
                            take = 0;        // nor leave one alone at the bottom of this one
                        }
                    }
                    if (take <= 0) {
                        if (flow.fresh) {
                            take = Math.max(1, Math.min(left, room)); // the page holds nothing else: it must go here
                        } else {
                            flow.next(topic.title());
                            continue;
                        }
                    }
                    for (int k = 0; k < take; k++) {
                        boolean last = i + k == n - 1;
                        flow.add(new Row(TEXT, lines.get(i + k), null, lineHeight + (last ? PARAGRAPH_GAP : 0), -1));
                    }
                    flow.fresh = false;
                    i += take;
                    if (i < n) {
                        flow.next(topic.title());
                    }
                }
            }
        }
        flow.finish();

        List<Sheet> out = new ArrayList<>();
        for (Sheet sheet : contents) {
            List<Row> resolved = new ArrayList<>();
            for (Row row : sheet.rows()) {
                resolved.add(row.kind() == ENTRY
                        ? new Row(ENTRY, null, row.text(), row.height(), starts[row.target()])
                        : row);
            }
            out.add(new Sheet(sheet.topic(), resolved));
        }
        out.addAll(flow.done);
        return out;
    }

    /** The topic pages being filled, one sheet at a time. */
    private static final class Flow {
        final int budget;
        final List<Sheet> done = new ArrayList<>();
        List<Row> rows = new ArrayList<>();
        int used;
        /** True while the sheet holds no body text of the current topic yet. */
        boolean fresh = true;
        /** The topic in effect at the top of the sheet being filled, for the running head. */
        Component topic = Component.empty();
        Component sheetTopic;

        Flow(int budget) {
            this.budget = budget;
        }

        void add(Row row) {
            if (this.sheetTopic == null) {
                this.sheetTopic = this.topic;
            }
            this.rows.add(row);
            this.used += row.height();
        }

        void next(Component continuedTitle) {
            this.done.add(new Sheet(this.sheetTopic == null ? this.topic : this.sheetTopic, this.rows));
            this.rows = new ArrayList<>();
            this.used = 0;
            this.fresh = true;
            this.sheetTopic = null;
            if (continuedTitle != null) {
                add(new Row(CONTINUED, null, continuedTitle, HEADING_HEIGHT, -1));
            }
        }

        void finish() {
            if (!this.rows.isEmpty()) {
                this.done.add(new Sheet(this.sheetTopic == null ? this.topic : this.sheetTopic, this.rows));
            }
        }
    }

    /** One topic as written: a heading and its paragraphs, all by lang key. */
    private record Page(Component title, List<Component> paragraphs) {}

    /**
     * One line of a sheet. {@code line} is set for wrapped body text, {@code text} for headings and
     * contents entries, and {@code target} is a contents entry's sheet index.
     */
    private record Row(int kind, FormattedCharSequence line, Component text, int height, int target) {}

    /** One page as shown, and the topic its running head names. */
    private record Sheet(Component topic, List<Row> rows) {}

    private record Hit(int x0, int y0, int x1, int y1, Runnable action, Component tooltip) {
        boolean contains(double x, double y) {
            return x >= this.x0 && x < this.x1 && y >= this.y0 && y < this.y1;
        }
    }

    private enum Section {
        WELCOME("gui.hogwartsmagic.hogwarts_guide.section.welcome", 0xFF8E2B2B, ExampleMod.HOGWARTS_INVITE, List.of(
                page("welcome", "letter", 3),
                page("welcome", "first_days", 4)
        )),
        SURVIVAL("gui.hogwartsmagic.hogwarts_guide.section.survival", 0xFF46703C, ExampleMod.WIGGENWELD_POTION, List.of(
                page("survival", "staying_alive", 3),
                page("survival", "older_students", 3),
                page("survival", "sleepless_nights", 3),
                page("survival", "death_eater_raids", 3),
                page("survival", "trees_that_hit_back", 2)
        )),
        SPELLS("gui.hogwartsmagic.hogwarts_guide.section.spells", 0xFF36508C, ExampleMod.PHOENIX_CORE_WAND, List.of(
                page("spells", "study", 2),
                page("spells", "disciplines", 5),
                page("spells", "wheel", 4),
                page("spells", "mastery", 2),
                page("spells", "tomes", 2),
                page("spells", "duels", 3),
                page("spells", "apparition", 4)
        )),
        SYSTEMS("gui.hogwartsmagic.hogwarts_guide.section.systems", 0xFF67468A, ExampleMod.FLOO_POWDER, List.of(
                page("systems", "wider_world", 4),
                page("systems", "floo", 2),
                page("systems", "owl_post", 3),
                page("systems", "brooms", 3),
                page("systems", "mysteries", 3),
                page("systems", "dark_rite", 3)
        )),
        GRINGOTTS("gui.hogwartsmagic.hogwarts_guide.section.gringotts", 0xFFAD8630, () -> Items.GOLD_INGOT, List.of(
                page("gringotts", "finding", 2),
                page("gringotts", "gate", 2),
                page("gringotts", "cart", 3),
                page("gringotts", "vaults", 2),
                page("gringotts", "ironbelly", 2)
        )),
        DARK_MAGIC("gui.hogwartsmagic.hogwarts_guide.section.dark_magic", 0xFF34302E, ExampleMod.SLYTHERINS_LOCKET, List.of(
                page("dark_magic", "weight", 5),
                page("dark_magic", "five_states", 3),
                page("dark_magic", "costs", 5),
                page("dark_magic", "offers", 4),
                page("dark_magic", "way_back", 5)
        )),
        CREATURES("gui.hogwartsmagic.hogwarts_guide.section.creatures", 0xFF7D5432, ExampleMod.HIPPOGRIFF_SPAWN_EGG, List.of(
                page("creatures", "friends_and_terrors", 2),
                page("creatures", "warnings", 3),
                page("creatures", "hippogriffs", 2),
                page("creatures", "unicorns", 4),
                page("creatures", "house_elves", 4),
                page("creatures", "basilisk", 4),
                page("creatures", "merpeople", 8),
                page("creatures", "grindylows", 5),
                page("creatures", "boggarts", 5),
                page("creatures", "giants", 4),
                page("creatures", "willow", 6),
                page("creatures", "freeing_elf", 4)
        )),
        CRAFTING("gui.hogwartsmagic.hogwarts_guide.section.crafting", 0xFF4A6C70, ExampleMod.WAND_WORKBENCH_ITEM, List.of(
                page("crafting", "wands", 3),
                page("crafting", "benches", 3),
                page("crafting", "broom_recipes", 3),
                page("crafting", "build_first", 3)
        )),
        POTIONS("gui.hogwartsmagic.hogwarts_guide.section.potions", 0xFF5B843A, ExampleMod.WIZARD_CAULDRON_ITEM, List.of(
                page("potions", "the_wizard_cauldron", 4),
                page("potions", "signs_in_the_liquid", 4),
                page("potions", "wiggenweld_potion", 5),
                page("potions", "pepperup_potion", 5),
                page("potions", "polyjuice_potion", 6),
                page("potions", "polyjuice_the_sample", 4),
                page("potions", "essence_of_dittany", 4),
                page("potions", "mandrake_restorative_draught", 6),
                page("potions", "using_the_mandrake_draught", 3),
                page("potions", "advanced_book", 3),
                page("potions", "potion_of_remorse", 6),
                page("potions", "where_ingredients_grow", 5),
                page("potions", "turned_to_stone", 3)
        )),
        ADVANCED_POTIONS("gui.hogwartsmagic.advanced_potion_making", 0xFF8A1E22, ExampleMod.FELIX_FELICIS, List.of(
                page("potions", "advanced_intro", 3),
                page("potions", "draught_of_living_death", 5),
                page("potions", "living_death_the_finish", 4),
                page("potions", "felix_felicis", 5),
                page("potions", "felix_felicis_patience", 3),
                page("potions", "veritaserum", 7),
                page("potions", "veritaserum_use", 4)
        ));

        private final Component label;
        private final int color;
        private final Supplier<? extends ItemLike> icon;
        private final List<Page> pages;

        Section(String labelKey, int color, Supplier<? extends ItemLike> icon, List<Page> pages) {
            this.label = Component.translatable(labelKey);
            this.color = color;
            this.icon = icon;
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
