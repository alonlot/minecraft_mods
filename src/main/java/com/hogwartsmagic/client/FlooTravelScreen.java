package com.hogwartsmagic.client;

import com.hogwartsmagic.client.ThemedGui.Theme;
import com.hogwartsmagic.client.ThemedGui.ThemedButton;
import com.hogwartsmagic.network.SpellPackets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

import static com.hogwartsmagic.client.ThemedGui.drawPanel;
import static com.hogwartsmagic.client.ThemedGui.drawWell;
import static com.hogwartsmagic.client.ThemedGui.fillAnnulus;
import static com.hogwartsmagic.client.ThemedGui.fillQuad;
import static com.hogwartsmagic.client.ThemedGui.withAlpha;

/**
 * Floo Network chart: an inked parchment map of the land around the source fireplace with every linked
 * fire on it, dashed routes (the selected one alight with Floo flame), plus a list and a details card.
 * Click a fire to select it; double-click, Enter or Travel to go. The server validates the trip.
 */
public class FlooTravelScreen extends Screen {
    private static final Theme TH = ThemedGui.OAK;
    private static final int SIDE_W = 136;
    private static final int ROW_H = 14;
    private static final int FLOO = 0x3CFF9A;
    private static final int FLOO_PALE = 0xB8FFE0;
    private static final int INK = 0x3A2814;
    private static final float FULL = (float) (Math.PI * 2.0);

    private final BlockPos sourcePos;
    private final List<SpellPackets.FlooDestinationData> destinations;
    private final List<int[]> nodePx = new ArrayList<>();
    private int selectedIndex;
    private int scrollRows;
    private long lastClickMs;
    private int lastClickIndex = -1;

    private int left, top, w, h;
    private int mapX, mapY, mapW, mapH;
    private int sideX;
    private int listY0, listY1, visibleRows;
    private int detailY0, detailY1;
    /** Blocks per GUI pixel on the map. */
    private float scale = 1f;
    private ParchmentMap map;
    private ThemedButton travelButton;

    public FlooTravelScreen(BlockPos sourcePos, List<SpellPackets.FlooDestinationData> destinations) {
        super(Component.translatable("gui.hogwartsmagic.floo_network"));
        this.sourcePos = sourcePos;
        this.destinations = destinations == null ? List.of() : List.copyOf(destinations);
        this.selectedIndex = this.destinations.isEmpty() ? -1 : 0;
    }

    @Override
    protected void init() {
        super.init();
        this.w = Math.min(424, this.width - 16);
        this.h = Math.min(256, this.height - 16);
        this.left = (this.width - this.w) / 2;
        this.top = (this.height - this.h) / 2;
        this.mapX = this.left + 10;
        this.mapY = this.top + 30;
        this.mapW = this.w - SIDE_W - 28;
        this.mapH = this.h - 42;
        this.sideX = this.left + this.w - SIDE_W - 10;
        this.listY0 = this.top + 30;
        this.visibleRows = Math.max(3, (this.h - 150) / ROW_H);
        this.listY1 = this.listY0 + 4 + this.visibleRows * ROW_H;
        this.detailY0 = this.listY1 + 8;
        this.detailY1 = this.top + this.h - 32;

        this.travelButton = this.addRenderableWidget(new ThemedButton(this.sideX, this.top + this.h - 26, 74, 18,
                Component.translatable("gui.hogwartsmagic.floo.travel"), b -> this.travelToSelected(), TH));
        this.addRenderableWidget(new ThemedButton(this.sideX + 78, this.top + this.h - 26, SIDE_W - 78, 18,
                Component.translatable("gui.hogwartsmagic.close"), b -> this.onClose(), TH));
        this.travelButton.active = this.selectedIndex >= 0;

        this.buildProjection();
        this.bakeMap();
    }

    /** One scale for both axes, wide enough that every fire fits with a margin; at least ~96 blocks out. */
    private void buildProjection() {
        int maxDx = 1;
        int maxDz = 1;
        for (SpellPackets.FlooDestinationData d : this.destinations) {
            maxDx = Math.max(maxDx, Math.abs(d.pos().getX() - this.sourcePos.getX()));
            maxDz = Math.max(maxDz, Math.abs(d.pos().getZ() - this.sourcePos.getZ()));
        }
        float halfW = this.mapW / 2f - 16f;
        float halfH = this.mapH / 2f - 16f;
        this.scale = Math.max(Math.max((maxDx + 24) / halfW, (maxDz + 24) / halfH), 96f / (this.mapH / 2f));
        this.nodePx.clear();
        for (SpellPackets.FlooDestinationData d : this.destinations) {
            this.nodePx.add(this.project(d.pos()));
        }
    }

    private int[] project(BlockPos pos) {
        float cx = this.mapX + this.mapW / 2f;
        float cy = this.mapY + this.mapH / 2f;
        int x = Math.round(cx + (pos.getX() - this.sourcePos.getX()) / this.scale);
        int y = Math.round(cy + (pos.getZ() - this.sourcePos.getZ()) / this.scale);
        return new int[]{Mth.clamp(x, this.mapX + 8, this.mapX + this.mapW - 8), Mth.clamp(y, this.mapY + 10, this.mapY + this.mapH - 6)};
    }

    private void bakeMap() {
        if (this.map != null) {
            this.map.close();
            this.map = null;
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        int texW = Math.max(16, this.mapW / 2);
        int texH = Math.max(16, this.mapH / 2);
        float perTexel = this.scale * this.mapW / texW;
        this.map = ParchmentMap.bake(level, texW, texH, perTexel, (tx, tz) -> new int[]{
                this.sourcePos.getX() + Math.round((tx + 0.5f - texW / 2f) * perTexel),
                this.sourcePos.getZ() + Math.round((tz + 0.5f - texH / 2f) * perTexel)});
    }

    @Override
    public void removed() {
        super.removed();
        if (this.map != null) {
            this.map.close();
            this.map = null;
        }
    }

    private void travelToSelected() {
        if (this.selectedIndex < 0 || this.selectedIndex >= this.destinations.size()) {
            return;
        }
        SpellPackets.FlooDestinationData destination = this.destinations.get(this.selectedIndex);
        SpellPackets.CHANNEL.send(new SpellPackets.ChooseFlooDestinationPacket(this.sourcePos, destination.pos()),
                net.minecraftforge.network.PacketDistributor.SERVER.noArg());
    }

    private void select(int index) {
        long now = Util.getMillis();
        boolean doubleClick = index == this.lastClickIndex && now - this.lastClickMs < 350L;
        this.lastClickIndex = index;
        this.lastClickMs = now;
        this.selectedIndex = index;
        this.travelButton.active = true;
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
        if (doubleClick) {
            this.travelToSelected();
        }
    }

    // ---------------------------------------------------------------- input

    private int nodeAt(double mouseX, double mouseY) {
        int best = -1;
        double bestD = 10 * 10;
        for (int i = 0; i < this.nodePx.size(); i++) {
            int[] p = this.nodePx.get(i);
            double dx = mouseX - p[0];
            double dy = mouseY - (p[1] - 3);
            double d = dx * dx + dy * dy;
            if (d <= bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < this.sideX + 1 || mouseX >= this.sideX + SIDE_W - 6 || mouseY < this.listY0 + 2
                || mouseY >= this.listY0 + 2 + this.visibleRows * ROW_H) {
            return -1;
        }
        int index = this.scrollRows + (int) ((mouseY - this.listY0 - 2) / ROW_H);
        return index < this.destinations.size() ? index : -1;
    }

    private int maxScroll() {
        return Math.max(0, this.destinations.size() - this.visibleRows);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        int node = this.nodeAt(mouseX, mouseY);
        if (node < 0) {
            node = this.rowAt(mouseX, mouseY);
        }
        if (node >= 0) {
            this.select(node);
            return true;
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && this.selectedIndex >= 0) {
            this.travelToSelected();
            return true;
        }
        if (!this.destinations.isEmpty() && (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_UP)) {
            int n = this.destinations.size();
            this.selectedIndex = Math.floorMod(this.selectedIndex + (keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1), n);
            if (this.selectedIndex < this.scrollRows) {
                this.scrollRows = this.selectedIndex;
            } else if (this.selectedIndex >= this.scrollRows + this.visibleRows) {
                this.scrollRows = this.selectedIndex - this.visibleRows + 1;
            }
            this.travelButton.active = true;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No-op: Screen.render() calls this again after the panel is drawn, which put the menu blur
        // and backdrop on top of it. render() draws the plain dim itself (no blur, per the screen rule).
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        float time = Util.getMillis() / 1000f;
        int hoveredNode = this.nodeAt(mouseX, mouseY);

        drawPanel(g, this.left, this.top, this.w, this.h, TH);
        g.drawString(this.font, this.title, this.left + 10, this.top + 9, TH.title(), false);
        int titleW = this.font.width(this.title);
        ThemedGui.drawFittedCentered(g, this.font, Component.translatable("gui.hogwartsmagic.floo.map_subtitle"),
                this.left + 10 + titleW + 8 + (this.mapW - titleW - 8) / 2f, this.top + 9, TH.textDim(), this.mapW - titleW - 12);

        this.renderMap(g, mouseX, mouseY, hoveredNode, time);
        this.renderList(g, mouseX, mouseY, time);
        this.renderDetails(g);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderMap(GuiGraphics g, int mouseX, int mouseY, int hoveredNode, float time) {
        int x0 = this.mapX, y0 = this.mapY, x1 = this.mapX + this.mapW, y1 = this.mapY + this.mapH;
        drawWell(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, TH);
        g.enableScissor(x0, y0, x1, y1);
        if (this.map != null) {
            this.map.draw(g, x0, y0, this.mapW, this.mapH);
        } else {
            g.fill(x0, y0, x1, y1, 0xFFE9D7AE);
        }
        // Light graticule and a burnt-edge vignette.
        for (int gx = x0 + 32; gx < x1; gx += 48) {
            g.fill(gx, y0, gx + 1, y1, 0x14603E1A);
        }
        for (int gy = y0 + 28; gy < y1; gy += 48) {
            g.fill(x0, gy, x1, gy + 1, 0x14603E1A);
        }
        g.fillGradient(x0, y0, x1, y0 + 10, 0x40603E1A, 0x00603E1A);
        g.fillGradient(x0, y1 - 10, x1, y1, 0x00603E1A, 0x48603E1A);

        float cx = x0 + this.mapW / 2f;
        float cy = y0 + this.mapH / 2f;
        // Routes: dashed ink to every fire; the selected one burns with Floo flame and moving sparks.
        for (int i = 0; i < this.nodePx.size(); i++) {
            int[] p = this.nodePx.get(i);
            boolean sel = i == this.selectedIndex;
            this.drawDashed(g, cx, cy, p[0], p[1] - 2, sel ? 0xA0236B45 : 0x90503A22, sel ? 1.6f : 1.0f, sel ? time * 18f : 0f);
            if (sel) {
                this.drawDashed(g, cx, cy, p[0], p[1] - 2, withAlpha(FLOO, 0x40), 3.2f, time * 18f);
                for (int s = 0; s < 4; s++) {
                    float t = (time * 0.45f + s / 4f) % 1f;
                    float sx = Mth.lerp(t, cx, p[0]);
                    float sy = Mth.lerp(t, cy, p[1] - 2);
                    fillAnnulus(g, sx, sy, 0, 2.6f, 0, FULL, withAlpha(FLOO, 0x60));
                    fillAnnulus(g, sx, sy, 0, 1.2f, 0, FULL, withAlpha(FLOO_PALE, 0xF0));
                }
            }
        }

        this.drawCompass(g, x1 - 20, y0 + 22);
        this.drawScaleBar(g, x0 + 8, y1 - 12);

        // Source hearth: "you are here" ripple.
        float ripple = (time * 0.8f) % 1f;
        fillAnnulus(g, cx, cy, 6 + ripple * 10, 7 + ripple * 10, 0, FULL, withAlpha(0x236B45, Math.round(160 * (1 - ripple))));
        this.drawHearth(g, cx, cy, true, false, time);

        for (int i = 0; i < this.nodePx.size(); i++) {
            int[] p = this.nodePx.get(i);
            this.drawHearth(g, p[0], p[1], i == this.selectedIndex, i == hoveredNode, time + i * 0.37f);
        }
        for (int i = 0; i < this.nodePx.size(); i++) {
            int[] p = this.nodePx.get(i);
            boolean sel = i == this.selectedIndex;
            boolean hov = i == hoveredNode;
            this.drawTag(g, this.destinations.get(i).name(), p[0] + 8, p[1] - 9, sel, hov);
        }
        // The source tag sits under its hearth so it never collides with a nearby fire's tag (up-right).
        String here = Component.translatable("gui.hogwartsmagic.floo.you_are_here").getString();
        this.drawTag(g, here, Math.round(cx) - (Math.min(84, this.font.width(here)) + 6) / 2, Math.round(cy) + 7, false, false);

        if (this.destinations.isEmpty()) {
            ThemedGui.drawFittedCentered(g, this.font, Component.translatable("message.hogwartsmagic.floo.no_destinations"),
                    cx, y1 - 26, withAlpha(INK, 0xFF), this.mapW - 16);
        }
        g.disableScissor();
    }

    /** Fireplace glyph: a stone hearth with a flickering green Floo flame. */
    private void drawHearth(GuiGraphics g, float x, float y, boolean selected, boolean hovered, float time) {
        if (selected || hovered) {
            float pulse = 0.5f + 0.5f * Mth.sin(time * 4f);
            fillAnnulus(g, x, y - 3, 0, 9 + pulse * 2, 0, FULL, withAlpha(FLOO, selected ? 0x38 : 0x22));
        }
        int stone = hovered || selected ? 0xFF8E7B62 : 0xFF6E5C46;
        g.fill(Math.round(x) - 6, Math.round(y) + 1, Math.round(x) + 6, Math.round(y) + 4, 0xFF2A1E12);
        g.fill(Math.round(x) - 5, Math.round(y) - 5, Math.round(x) - 3, Math.round(y) + 2, stone);
        g.fill(Math.round(x) + 3, Math.round(y) - 5, Math.round(x) + 5, Math.round(y) + 2, stone);
        g.fill(Math.round(x) - 6, Math.round(y) - 7, Math.round(x) + 6, Math.round(y) - 5, stone);
        float flick = Mth.sin(time * 9f) * 0.8f + Mth.sin(time * 13f + 1f) * 0.5f;
        float tall = selected ? 8f : 6f;
        fillQuad(g, x - 3, y + 1, x + 3, y + 1, x + flick, y + 1 - tall, x + flick, y + 1 - tall, withAlpha(FLOO, 0xF0));
        fillQuad(g, x - 1.5f, y + 1, x + 1.5f, y + 1, x + flick * 0.6f, y + 1 - tall * 0.6f, x + flick * 0.6f, y + 1 - tall * 0.6f,
                withAlpha(FLOO_PALE, 0xFF));
    }

    private void drawTag(GuiGraphics g, String name, int x, int y, boolean selected, boolean hovered) {
        String label = this.font.plainSubstrByWidth(name, 84);
        int tw = this.font.width(label) + 6;
        x = Math.min(x, this.mapX + this.mapW - tw - 2);
        g.fill(x - 1, y - 1, x + tw + 1, y + 10, selected ? 0xFF236B45 : 0xC05A4026);
        g.fill(x, y, x + tw, y + 9, selected ? 0xFFE6F5E4 : hovered ? 0xFFF7EBCB : 0xE8EFDFB8);
        g.drawString(this.font, label, x + 3, y + 1, selected ? 0xFF1C4A30 : 0xFF3A2814, false);
    }

    /** Marauder-style dashed ink line; {@code phase} scrolls the dashes along it. */
    private void drawDashed(GuiGraphics g, float x0, float y0, float x1, float y1, int color, float width, float phase) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 1f) {
            return;
        }
        float ux = dx / len, uy = dy / len;
        float nx = -uy * width / 2f, ny = ux * width / 2f;
        float dash = 5f, gap = 3f;
        float start = -((phase % (dash + gap)) + (dash + gap)) % (dash + gap);
        for (float s = start; s < len; s += dash + gap) {
            float a = Math.max(0f, s);
            float b = Math.min(len, s + dash);
            if (b <= a) {
                continue;
            }
            float ax = x0 + ux * a, ay = y0 + uy * a, bx = x0 + ux * b, by = y0 + uy * b;
            fillQuad(g, ax + nx, ay + ny, bx + nx, by + ny, bx - nx, by - ny, ax - nx, ay - ny, color);
        }
    }

    private void drawCompass(GuiGraphics g, float x, float y) {
        int ink = 0xC03A2814;
        int pale = 0xA0A08060;
        fillAnnulus(g, x, y, 9.5f, 10.5f, 0, FULL, 0x803A2814);
        // Four long points (north inked dark), four short diagonals.
        fillQuad(g, x, y - 14, x + 3, y, x, y, x - 3, y, ink);
        fillQuad(g, x, y + 14, x + 3, y, x, y, x - 3, y, pale);
        fillQuad(g, x - 14, y, x, y - 3, x, y, x, y + 3, pale);
        fillQuad(g, x + 14, y, x, y - 3, x, y, x, y + 3, pale);
        float d = 7f;
        fillQuad(g, x + d, y - d, x + 1.5f, y, x, y, x, y - 1.5f, 0x90705030);
        fillQuad(g, x - d, y - d, x - 1.5f, y, x, y, x, y - 1.5f, 0x90705030);
        fillQuad(g, x + d, y + d, x + 1.5f, y, x, y, x, y + 1.5f, 0x90705030);
        fillQuad(g, x - d, y + d, x - 1.5f, y, x, y, x, y + 1.5f, 0x90705030);
        g.drawString(this.font, "N", Math.round(x) - 2, Math.round(y) - 24, 0xFF3A2814, false);
    }

    /** Ink scale bar with a round block count between ~28 and ~70 px long. */
    private void drawScaleBar(GuiGraphics g, int x, int y) {
        int[] nice = {10, 20, 25, 50, 100, 200, 250, 500, 1000, 2000};
        int blocks = nice[nice.length - 1];
        for (int n : nice) {
            if (n / this.scale >= 28) {
                blocks = n;
                break;
            }
        }
        int px = Math.round(blocks / this.scale);
        g.fill(x, y, x + px, y + 1, 0xFF3A2814);
        g.fill(x, y - 3, x + 1, y + 2, 0xFF3A2814);
        g.fill(x + px - 1, y - 3, x + px, y + 2, 0xFF3A2814);
        g.fill(x + px / 2, y - 2, x + px / 2 + 1, y + 1, 0xFF3A2814);
        g.drawString(this.font, blocks + "m", x + px + 4, y - 4, 0xFF3A2814, false);
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY, float time) {
        int x0 = this.sideX;
        int x1 = this.sideX + SIDE_W;
        g.drawString(this.font, Component.translatable("gui.hogwartsmagic.floo.saved_fireplaces"), x0 + 1, this.top + 19, TH.title(), false);
        drawWell(g, x0, this.listY0, x1, this.listY1, TH);
        if (this.destinations.isEmpty()) {
            ThemedGui.drawFittedCentered(g, this.font, Component.translatable("gui.hogwartsmagic.floo.none"),
                    (x0 + x1) / 2f, (this.listY0 + this.listY1) / 2f - 4, TH.textDim(), SIDE_W - 10);
            return;
        }
        int hovered = this.rowAt(mouseX, mouseY);
        int rowX1 = x1 - 6;
        for (int r = 0; r < this.visibleRows; r++) {
            int index = this.scrollRows + r;
            if (index >= this.destinations.size()) {
                break;
            }
            SpellPackets.FlooDestinationData d = this.destinations.get(index);
            int ry = this.listY0 + 2 + r * ROW_H;
            boolean sel = index == this.selectedIndex;
            if (sel) {
                g.fill(x0 + 1, ry, rowX1, ry + ROW_H - 1, TH.wellLight());
                g.fill(x0 + 1, ry, x0 + 3, ry + ROW_H - 1, 0xFF000000 | FLOO);
            } else if (index == hovered) {
                g.fill(x0 + 1, ry, rowX1, ry + ROW_H - 1, TH.slotFill());
            }
            float flick = 0.5f + 0.5f * Mth.sin(time * 6f + index);
            fillAnnulus(g, x0 + 9, ry + 6.5f, 0, sel ? 3f + flick : 2.5f, 0, FULL, withAlpha(FLOO, sel ? 0xFF : 0xA0));
            String dist = Math.round(Math.sqrt(this.sourcePos.distSqr(d.pos()))) + "m";
            int distW = this.font.width(dist);
            g.drawString(this.font, dist, rowX1 - 3 - distW, ry + 3, TH.textDim(), false);
            String name = this.font.plainSubstrByWidth(d.name(), rowX1 - 3 - distW - (x0 + 16) - 4);
            g.drawString(this.font, name, x0 + 16, ry + 3, sel ? 0xFF000000 | FLOO_PALE : TH.text(), false);
        }
        if (this.maxScroll() > 0) {
            int trackX = x1 - 4;
            int t0 = this.listY0 + 2;
            int t1 = this.listY1 - 2;
            g.fill(trackX, t0, trackX + 2, t1, TH.wellShadow());
            int thumbH = Math.max(8, (t1 - t0) * this.visibleRows / this.destinations.size());
            int thumbY = t0 + (t1 - t0 - thumbH) * this.scrollRows / this.maxScroll();
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, TH.trim());
        }
    }

    private void renderDetails(GuiGraphics g) {
        int x0 = this.sideX;
        int x1 = this.sideX + SIDE_W;
        drawWell(g, x0, this.detailY0, x1, this.detailY1, TH);
        if (this.selectedIndex < 0 || this.selectedIndex >= this.destinations.size()) {
            return;
        }
        SpellPackets.FlooDestinationData d = this.destinations.get(this.selectedIndex);
        int x = x0 + 6;
        int y = this.detailY0 + 5;
        g.drawString(this.font, this.font.plainSubstrByWidth(d.name(), SIDE_W - 12), x, y, 0xFF000000 | FLOO_PALE, false);
        int dx = d.pos().getX() - this.sourcePos.getX();
        int dz = d.pos().getZ() - this.sourcePos.getZ();
        int dist = Math.round(Mth.sqrt(dx * dx + dz * dz));
        y += 12;
        this.fitted(g, Component.translatable("gui.hogwartsmagic.floo.distance", dist), x, y, TH.text());
        y += 10;
        this.fitted(g, Component.translatable("gui.hogwartsmagic.floo.heading", heading(dx, dz)), x, y, TH.text());
        y += 10;
        this.fitted(g, Component.translatable("gui.hogwartsmagic.floo.coords", d.pos().getX(), d.pos().getY(), d.pos().getZ()), x, y, TH.textDim());
    }

    private void fitted(GuiGraphics g, Component text, int x, int y, int color) {
        int maxW = SIDE_W - 12;
        int tw = this.font.width(text);
        float s = tw > maxW ? Math.max(0.6f, maxW / (float) tw) : 1f;
        GuiRenderUtil.drawScaledString(g, this.font, text, x, y + (1f - s) * 4f, color, s);
    }

    /** Eight-point compass word for a world offset (north is -Z). */
    private static String heading(int dx, int dz) {
        if (dx == 0 && dz == 0) {
            return "-";
        }
        String[] names = {"E", "SE", "S", "SW", "W", "NW", "N", "NE"};
        double a = Math.atan2(dz, dx);
        int octant = Math.floorMod((int) Math.round(a / (Math.PI / 4.0)), 8);
        return names[octant];
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
