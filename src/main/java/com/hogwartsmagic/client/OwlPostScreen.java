package com.hogwartsmagic.client;

import com.hogwartsmagic.client.ThemedGui.Theme;
import com.hogwartsmagic.client.ThemedGui.ThemedButton;
import com.hogwartsmagic.owlpost.OwlPostNet;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.hogwartsmagic.client.ThemedGui.drawPanel;
import static com.hogwartsmagic.client.ThemedGui.drawWell;
import static com.hogwartsmagic.client.ThemedGui.fillAnnulus;
import static com.hogwartsmagic.client.ThemedGui.withAlpha;

/**
 * Owl post: the list of perches an owl can fly to. Three uses, one screen: sending the held stack from a perch,
 * choosing where a perch's hoppers send to, and choosing an owl's home. The server checks every choice again.
 */
public class OwlPostScreen extends Screen {
    private static final Theme TH = ThemedGui.OAK;
    private static final int ROW_H = 14;
    private static final int AMBER = 0xE8B04A;
    private static final float FULL = (float) (Math.PI * 2.0);

    private final OwlPostNet.OpenScreenPacket packet;
    private final List<OwlPostNet.Entry> entries;
    private int selectedIndex;
    private int scrollRows;
    private long lastClickMs;
    private int lastClickIndex = -1;

    private int left, top, w, h;
    private int listX0, listX1, listY0, listY1, visibleRows;
    private ThemedButton primary;
    private ThemedButton secondary;

    public OwlPostScreen(OwlPostNet.OpenScreenPacket packet) {
        super(Component.translatableWithFallback(titleKey(packet.mode()), titleFallback(packet.mode())));
        this.packet = packet;
        this.entries = List.copyOf(packet.entries());
        this.selectedIndex = packet.selected() >= 0 && packet.selected() < this.entries.size()
                ? packet.selected() : (this.entries.isEmpty() ? -1 : 0);
    }

    private static String titleKey(int mode) {
        return switch (mode) {
            case OwlPostNet.MODE_DEFAULT -> "gui.hogwartsmagic.owl_post.default_title";
            case OwlPostNet.MODE_HOME -> "gui.hogwartsmagic.owl_post.home_title";
            default -> "gui.hogwartsmagic.owl_post.send_title";
        };
    }

    private static String titleFallback(int mode) {
        return switch (mode) {
            case OwlPostNet.MODE_DEFAULT -> "Owl Post - Hopper Destination";
            case OwlPostNet.MODE_HOME -> "Owl Post - Home Perch";
            default -> "Owl Post - Send";
        };
    }

    @Override
    protected void init() {
        super.init();
        this.w = Math.min(300, this.width - 16);
        this.h = Math.min(236, this.height - 16);
        this.left = (this.width - this.w) / 2;
        this.top = (this.height - this.h) / 2;
        this.listX0 = this.left + 10;
        this.listX1 = this.left + this.w - 10;
        this.listY0 = this.top + 44;
        this.visibleRows = Math.max(3, (this.h - 44 - 36) / ROW_H);
        this.listY1 = this.listY0 + 4 + this.visibleRows * ROW_H;

        int by = this.top + this.h - 26;
        int closeW = 56;
        int gap = 4;
        int bw = (this.w - 20 - closeW - gap * 2) / 2;
        int x = this.left + 10;
        this.primary = this.addRenderableWidget(new ThemedButton(x, by, bw, 18, primaryLabel(), b -> this.primaryAction(), TH));
        this.secondary = this.addRenderableWidget(new ThemedButton(x + bw + gap, by, bw, 18, secondaryLabel(), b -> this.secondaryAction(), TH));
        this.addRenderableWidget(new ThemedButton(x + 2 * (bw + gap), by, closeW, 18,
                Component.translatable("gui.hogwartsmagic.close"), b -> this.onClose(), TH));
        this.updateButtons();
    }

    private Component primaryLabel() {
        return switch (this.packet.mode()) {
            case OwlPostNet.MODE_DEFAULT -> Component.translatableWithFallback("gui.hogwartsmagic.owl_post.set_default", "Set Destination");
            case OwlPostNet.MODE_HOME -> Component.translatableWithFallback("gui.hogwartsmagic.owl_post.make_home", "Live Here");
            default -> Component.translatableWithFallback("gui.hogwartsmagic.owl_post.send", "Send");
        };
    }

    private Component secondaryLabel() {
        return switch (this.packet.mode()) {
            case OwlPostNet.MODE_DEFAULT -> Component.translatableWithFallback("gui.hogwartsmagic.owl_post.clear_default", "Stop Hoppers");
            case OwlPostNet.MODE_HOME -> Component.translatableWithFallback("gui.hogwartsmagic.owl_post.take_tag", "Take Back Tag");
            default -> Component.translatableWithFallback("gui.hogwartsmagic.owl_post.send_default", "Send + Hoppers");
        };
    }

    private void updateButtons() {
        boolean picked = this.selectedIndex >= 0 && this.selectedIndex < this.entries.size();
        this.primary.active = picked;
        this.secondary.active = switch (this.packet.mode()) {
            case OwlPostNet.MODE_DEFAULT -> this.packet.selected() >= 0;
            case OwlPostNet.MODE_HOME -> true;
            default -> picked;
        };
    }

    private void send(int action, boolean needsEntry) {
        OwlPostNet.Entry e = needsEntry && this.selectedIndex >= 0 && this.selectedIndex < this.entries.size()
                ? this.entries.get(this.selectedIndex) : null;
        if (needsEntry && e == null) {
            return;
        }
        OwlPostNet.CHANNEL.send(new OwlPostNet.ActionPacket(action, this.packet.pos(), this.packet.owlId(),
                e == null ? "" : e.dimension(), e == null ? this.packet.pos() : e.pos()), PacketDistributor.SERVER.noArg());
        this.onClose();
    }

    private void primaryAction() {
        switch (this.packet.mode()) {
            case OwlPostNet.MODE_DEFAULT -> this.send(OwlPostNet.ACTION_SET_DEFAULT, true);
            case OwlPostNet.MODE_HOME -> this.send(OwlPostNet.ACTION_SET_HOME, true);
            default -> this.send(OwlPostNet.ACTION_SEND, true);
        }
    }

    private void secondaryAction() {
        switch (this.packet.mode()) {
            case OwlPostNet.MODE_DEFAULT -> this.send(OwlPostNet.ACTION_CLEAR_DEFAULT, false);
            case OwlPostNet.MODE_HOME -> this.send(OwlPostNet.ACTION_TAKE_TAG, false);
            default -> this.send(OwlPostNet.ACTION_SEND_AND_DEFAULT, true);
        }
    }

    // ---------------------------------------------------------------- input

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < this.listX0 + 1 || mouseX >= this.listX1 - 6 || mouseY < this.listY0 + 2
                || mouseY >= this.listY0 + 2 + this.visibleRows * ROW_H) {
            return -1;
        }
        int index = this.scrollRows + (int) ((mouseY - this.listY0 - 2) / ROW_H);
        return index < this.entries.size() ? index : -1;
    }

    private int maxScroll() {
        return Math.max(0, this.entries.size() - this.visibleRows);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        int row = this.rowAt(mouseX, mouseY);
        if (row < 0) {
            return false;
        }
        long now = Util.getMillis();
        boolean doubleClick = row == this.lastClickIndex && now - this.lastClickMs < 350L;
        this.lastClickIndex = row;
        this.lastClickMs = now;
        this.selectedIndex = row;
        this.updateButtons();
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
        if (doubleClick) {
            this.primaryAction();
        }
        return true;
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
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && this.primary.active) {
            this.primaryAction();
            return true;
        }
        if (!this.entries.isEmpty() && (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_UP)) {
            int n = this.entries.size();
            this.selectedIndex = Math.floorMod(Math.max(0, this.selectedIndex) + (keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1), n);
            if (this.selectedIndex < this.scrollRows) {
                this.scrollRows = this.selectedIndex;
            } else if (this.selectedIndex >= this.scrollRows + this.visibleRows) {
                this.scrollRows = this.selectedIndex - this.visibleRows + 1;
            }
            this.updateButtons();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No-op: render() draws the plain dim itself (no menu blur, per the screen rule).
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        drawPanel(g, this.left, this.top, this.w, this.h, TH);
        g.drawString(this.font, this.title, this.left + 10, this.top + 9, TH.title(), false);
        ThemedGui.drawFittedCentered(g, this.font, this.subtitle(), this.left + this.w / 2f, this.top + 24, TH.textDim(), this.w - 20);
        this.renderList(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private Component subtitle() {
        switch (this.packet.mode()) {
            case OwlPostNet.MODE_DEFAULT:
                return Component.translatableWithFallback("gui.hogwartsmagic.owl_post.default_hint",
                        "Hoppers feeding this perch send what they bring here.");
            case OwlPostNet.MODE_HOME:
                return Component.translatableWithFallback("gui.hogwartsmagic.owl_post.home_hint",
                        "The perches this owl knows. It waits at its home for parcels.");
            default:
                ItemStack held = Minecraft.getInstance().player == null ? ItemStack.EMPTY
                        : !Minecraft.getInstance().player.getMainHandItem().isEmpty()
                        ? Minecraft.getInstance().player.getMainHandItem() : Minecraft.getInstance().player.getOffhandItem();
                return Component.translatableWithFallback("gui.hogwartsmagic.owl_post.send_hint",
                        "%s x %s  -  owls home: %s of %s", held.getCount(), held.getHoverName(),
                        this.packet.owlsHome(), this.packet.owlsTotal());
        }
    }

    private String distanceLabel(OwlPostNet.Entry e) {
        if (!e.dimension().equals(this.packet.hereDimension())) {
            String dim = e.dimension();
            int colon = dim.indexOf(':');
            dim = colon >= 0 ? dim.substring(colon + 1) : dim;
            dim = dim.replace('_', ' ');
            return dim + " - 30s";
        }
        double dist = Math.sqrt(this.packet.pos().distSqr(e.pos()));
        long seconds = Math.round(Math.min(6000, 40 + Math.round(dist)) / 20.0);
        return Math.round(dist) + "m - " + seconds + "s";
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        drawWell(g, this.listX0, this.listY0, this.listX1, this.listY1, TH);
        if (this.entries.isEmpty()) {
            ThemedGui.drawFittedCentered(g, this.font, Component.translatableWithFallback("gui.hogwartsmagic.owl_post.none",
                            "No perches known"), (this.listX0 + this.listX1) / 2f, (this.listY0 + this.listY1) / 2f - 4,
                    TH.textDim(), this.listX1 - this.listX0 - 10);
            return;
        }
        float time = Util.getMillis() / 1000f;
        int hovered = this.rowAt(mouseX, mouseY);
        int rowX1 = this.listX1 - 6;
        String current = switch (this.packet.mode()) {
            case OwlPostNet.MODE_HOME -> Component.translatableWithFallback("gui.hogwartsmagic.owl_post.current_home", "home").getString();
            case OwlPostNet.MODE_DEFAULT -> Component.translatableWithFallback("gui.hogwartsmagic.owl_post.current_default", "hoppers").getString();
            default -> Component.translatableWithFallback("gui.hogwartsmagic.owl_post.current_default", "hoppers").getString();
        };
        for (int r = 0; r < this.visibleRows; r++) {
            int index = this.scrollRows + r;
            if (index >= this.entries.size()) {
                break;
            }
            OwlPostNet.Entry e = this.entries.get(index);
            int ry = this.listY0 + 2 + r * ROW_H;
            boolean sel = index == this.selectedIndex;
            if (sel) {
                g.fill(this.listX0 + 1, ry, rowX1, ry + ROW_H - 1, TH.wellLight());
                g.fill(this.listX0 + 1, ry, this.listX0 + 3, ry + ROW_H - 1, 0xFF000000 | AMBER);
            } else if (index == hovered) {
                g.fill(this.listX0 + 1, ry, rowX1, ry + ROW_H - 1, TH.slotFill());
            }
            float flick = 0.5f + 0.5f * Mth.sin(time * 5f + index);
            fillAnnulus(g, this.listX0 + 9, ry + 6.5f, 0, sel ? 2.8f + flick * 0.6f : 2.2f, 0, FULL, withAlpha(AMBER, sel ? 0xFF : 0x90));
            String right = this.distanceLabel(e);
            if (index == this.packet.selected()) {
                right = current + "  " + right;
            }
            int rightW = this.font.width(right);
            g.drawString(this.font, right, rowX1 - 3 - rightW, ry + 3, index == this.packet.selected() ? TH.trimBright() : TH.textDim(), false);
            String name = this.font.plainSubstrByWidth(e.name(), rowX1 - 3 - rightW - (this.listX0 + 16) - 4);
            g.drawString(this.font, name, this.listX0 + 16, ry + 3, sel ? TH.title() : TH.text(), false);
        }
        if (this.maxScroll() > 0) {
            int trackX = this.listX1 - 4;
            int t0 = this.listY0 + 2;
            int t1 = this.listY1 - 2;
            g.fill(trackX, t0, trackX + 2, t1, TH.wellShadow());
            int thumbH = Math.max(8, (t1 - t0) * this.visibleRows / this.entries.size());
            int thumbY = t0 + (t1 - t0 - thumbH) * this.scrollRows / this.maxScroll();
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, TH.trim());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
