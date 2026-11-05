package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.ThemedGui.Theme;
import com.hogwartsmagic.client.ThemedGui.ThemedButton;
import com.hogwartsmagic.event.ClientModEvents;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellType;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import static com.hogwartsmagic.client.ThemedGui.drawFilledCircle;
import static com.hogwartsmagic.client.ThemedGui.drawFittedCentered;
import static com.hogwartsmagic.client.ThemedGui.drawPanel;
import static com.hogwartsmagic.client.ThemedGui.drawWell;

/**
 * Spell menu (Shift + Right Click with a wand): the nine slots laid out as the same wheel the combat
 * radial uses (slot 1 at the top, clockwise), beside a scrollable list of learned spells. Select a slot
 * (click, or press 1-9), then click a spell to put it there; right-click a slot to clear it.
 */
public class SpellMenuScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Theme TH = ThemedGui.VIOLET;
    private static final int SLOTS = MagicWandItem.SPELL_SLOT_COUNT;
    private static final float START_ANGLE = (float) (-Math.PI / 2.0);

    private static final int W = 324;
    private static final int H = 200;
    /** Wheel well and its centre, panel-local. */
    private static final int WHEEL_X0 = 9, WHEEL_Y0 = 22, WHEEL_X1 = 161, WHEEL_Y1 = 168;
    private static final int WHEEL_CX = 85, WHEEL_CY = 95;
    private static final int WHEEL_R = 54;
    private static final int SOCKET_R = 12;
    /** Learned-spell list well, panel-local. */
    private static final int LIST_X0 = 169, LIST_Y0 = 22, LIST_X1 = 315, LIST_Y1 = 168;
    private static final int ROWS_TOP = 37;
    private static final int ROW_H = 13;
    private static final int VISIBLE_ROWS = (LIST_Y1 - 2 - ROWS_TOP) / ROW_H;
    private static final int BAR_Y = 175;
    private static final int BAR_H = 18;

    private final Player player;
    private final List<SpellType> learnedSpells = new ArrayList<>();
    private final SpellType[] slotSpells = new SpellType[SLOTS];
    private int selectedSlot = 0;
    private int scrollRows = 0;
    private ThemedButton clearButton;

    private int hoveredSocket = -1;
    private int hoveredRow = -1;

    public SpellMenuScreen(Player player) {
        super(Component.translatable("gui." + ExampleMod.MODID + ".spell_menu"));
        this.player = player;
    }

    private int left() {
        return (this.width - W) / 2;
    }

    private int top() {
        return (this.height - H) / 2;
    }

    @Override
    protected void init() {
        super.init();
        this.refreshLearnedSpells();
        int left = this.left();
        int y = this.top() + BAR_Y;
        int x = left + 9;
        this.clearButton = this.addRenderableWidget(new ThemedButton(x, y, 66, BAR_H,
                Component.translatable("gui." + ExampleMod.MODID + ".spell_menu.clear_slot"), b -> this.setSlot(this.selectedSlot, null), TH));
        x += 66 + 4;
        this.addRenderableWidget(new ThemedButton(x, y, 58, BAR_H,
                Component.translatable("gui." + ExampleMod.MODID + ".relics"), b -> this.openRelicScreen(), TH));
        x += 58 + 4;
        this.addRenderableWidget(new ThemedButton(x, y, 112, BAR_H,
                Component.translatable("gui." + ExampleMod.MODID + ".grimoire"),
                b -> Minecraft.getInstance().setScreen(new SpellCompendiumScreen(this.player)), TH));
        x += 112 + 4;
        this.addRenderableWidget(new ThemedButton(x, y, left + W - 9 - x, BAR_H,
                Component.translatable("gui." + ExampleMod.MODID + ".close"), b -> this.onClose(), TH));
        this.clampScroll();
    }

    private void refreshLearnedSpells() {
        this.learnedSpells.clear();
        for (SpellType spell : SpellType.values()) {
            if (com.hogwartsmagic.item.MagicBookItem.isSpellLearned(this.player, spell)) {
                this.learnedSpells.add(spell);
            }
        }
    }

    private void readSlots() {
        ItemStack wand = this.getHeldWand();
        for (int i = 0; i < SLOTS; i++) {
            this.slotSpells[i] = wand.isEmpty() ? null : MagicWandItem.getSpellSlot(wand, i);
        }
    }

    /** Writes the slot locally for instant feedback and tells the server, which owns the real wand. */
    private void setSlot(int slot, @Nullable SpellType spell) {
        ItemStack wand = this.getHeldWand();
        if (wand.isEmpty()) {
            return;
        }
        MagicWandItem.setSpellSlot(wand, slot, spell);
        SpellPackets.CHANNEL.send(new SpellPackets.SetSpellSlotPacket(slot, spell == null ? "" : spell.getId()),
                PacketDistributor.SERVER.noArg());
    }

    private ItemStack getHeldWand() {
        ItemStack main = this.player.getMainHandItem();
        if (MagicWandItem.isAnyWand(main)) {
            return main;
        }
        ItemStack off = this.player.getOffhandItem();
        if (MagicWandItem.isAnyWand(off)) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    private static Component spellName(SpellType spell) {
        return Component.translatable("spell." + ExampleMod.MODID + "." + spell.getId());
    }

    private static String initialsFor(SpellType spell) {
        String[] words = spellName(spell).getString().trim().split("\\s+");
        if (words.length == 0 || words[0].isEmpty()) {
            return "?";
        }
        String first = words[0].substring(0, 1);
        String second = words.length > 1 && !words[1].isEmpty() ? words[1].substring(0, 1) : "";
        return (first + second).toUpperCase();
    }

    private static Component keybindLabel(int slotIndex) {
        KeyMapping mapping = ClientModEvents.getSpellSlotKeyMapping(slotIndex);
        if (mapping == null || mapping.isUnbound()) {
            return Component.translatable("gui." + ExampleMod.MODID + ".spell_menu.unbound");
        }
        return mapping.getTranslatedKeyMessage();
    }

    private static int socketX(int slot) {
        float angle = START_ANGLE + (float) (Math.PI * 2.0) * slot / SLOTS;
        return WHEEL_CX + Math.round(Mth.cos(angle) * WHEEL_R);
    }

    private static int socketY(int slot) {
        float angle = START_ANGLE + (float) (Math.PI * 2.0) * slot / SLOTS;
        return WHEEL_CY + Math.round(Mth.sin(angle) * WHEEL_R);
    }

    private int socketAt(double mouseX, double mouseY) {
        double lx = mouseX - this.left();
        double ly = mouseY - this.top();
        for (int i = 0; i < SLOTS; i++) {
            double dx = lx - socketX(i);
            double dy = ly - socketY(i);
            if (dx * dx + dy * dy <= (SOCKET_R + 2) * (SOCKET_R + 2)) {
                return i;
            }
        }
        return -1;
    }

    /** Index into {@link #learnedSpells} under the mouse, or -1. */
    private int rowAt(double mouseX, double mouseY) {
        double lx = mouseX - this.left();
        double ly = mouseY - this.top();
        if (lx < LIST_X0 + 1 || lx >= LIST_X1 - 6 || ly < ROWS_TOP || ly >= ROWS_TOP + VISIBLE_ROWS * ROW_H) {
            return -1;
        }
        int index = this.scrollRows + (int) ((ly - ROWS_TOP) / ROW_H);
        return index < this.learnedSpells.size() ? index : -1;
    }

    private int maxScroll() {
        return Math.max(0, this.learnedSpells.size() - VISIBLE_ROWS);
    }

    private void clampScroll() {
        this.scrollRows = Mth.clamp(this.scrollRows, 0, this.maxScroll());
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int socket = this.socketAt(mouseX, mouseY);
        if (socket >= 0) {
            this.selectedSlot = socket;
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                this.setSlot(socket, null);
            }
            this.playClick();
            return true;
        }
        int row = this.rowAt(mouseX, mouseY);
        if (row >= 0 && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !this.getHeldWand().isEmpty()) {
            this.setSlot(this.selectedSlot, this.learnedSpells.get(row));
            this.playClick();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && this.maxScroll() > 0) {
            this.scrollRows -= (int) Math.signum(scrollY);
            this.clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode < GLFW.GLFW_KEY_1 + SLOTS) {
            this.selectedSlot = keyCode - GLFW.GLFW_KEY_1;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            this.setSlot(this.selectedSlot, null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private void openRelicScreen() {
        try {
            Class<?> relicScreenClass = Class.forName("com.hogwartsmagic.client.RelicScreen");
            Object screen = relicScreenClass.getConstructor(Player.class).newInstance(this.player);
            if (screen instanceof Screen screenInstance) {
                Minecraft.getInstance().setScreen(screenInstance);
                return;
            }
            throw new IllegalStateException("RelicScreen is not a Screen: " + relicScreenClass.getName());
        } catch (ReflectiveOperationException | LinkageError exception) {
            if (this.player != null) {
                this.player.displayClientMessage(Component.literal("Relics screen is unavailable in this build."), true);
            }
            LOGGER.error("Failed to open relic screen", exception);
        }
    }

    // ---------------------------------------------------------------- rendering

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // No blur, like the Grimoire.
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No-op: super.render() calls this after the panel is drawn, and the menu backdrop would cover it.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        this.readSlots();
        boolean hasWand = !this.getHeldWand().isEmpty();
        this.clearButton.active = hasWand && this.slotSpells[this.selectedSlot] != null;
        this.hoveredSocket = this.socketAt(mouseX, mouseY);
        this.hoveredRow = this.rowAt(mouseX, mouseY);

        int left = this.left();
        int top = this.top();
        drawPanel(g, left, top, W, H, TH);

        g.drawString(this.font, this.title, left + 10, top + 8, TH.title(), false);
        Component hint = Component.translatable("gui." + ExampleMod.MODID + ".spell_menu.assign_hint");
        int titleW = this.font.width(this.title);
        int hintMax = W - 20 - titleW - 12;
        int hintW = Math.min(this.font.width(hint), hintMax);
        drawFittedCentered(g, this.font, hint, left + W - 10 - hintW / 2f, top + 8, TH.textDim(), hintMax);

        this.renderWheel(g, left, top, hasWand);
        this.renderList(g, left, top, hasWand);

        super.render(g, mouseX, mouseY, partialTick);
        this.renderHoverTooltip(g, mouseX, mouseY, hasWand);
    }

    private void renderWheel(GuiGraphics g, int left, int top, boolean hasWand) {
        drawWell(g, left + WHEEL_X0, top + WHEEL_Y0, left + WHEEL_X1, top + WHEEL_Y1, TH);
        int cx = left + WHEEL_CX;
        int cy = top + WHEEL_CY;
        // Gold orbit the sockets sit on, and the hub that shows the selected slot.
        drawFilledCircle(g, cx, cy, WHEEL_R + 1, TH.trim());
        drawFilledCircle(g, cx, cy, WHEEL_R - 1, TH.well());
        drawFilledCircle(g, cx, cy, 31, TH.wellLight());
        drawFilledCircle(g, cx, cy, 30, TH.wellShadow());

        for (int i = 0; i < SLOTS; i++) {
            int sx = left + socketX(i);
            int sy = top + socketY(i);
            boolean selected = i == this.selectedSlot;
            boolean hovered = i == this.hoveredSocket;
            SpellType spell = this.slotSpells[i];
            if (selected) {
                drawFilledCircle(g, sx, sy, SOCKET_R + 3, ThemedGui.pulse(TH.trim(), TH.trimBright()));
            }
            drawFilledCircle(g, sx, sy, SOCKET_R + 1, selected || hovered ? TH.trimBright() : TH.bevelLight());
            drawFilledCircle(g, sx, sy, SOCKET_R, TH.wellShadow());
            if (spell != null) {
                drawFilledCircle(g, sx, sy, SOCKET_R - 2, 0xFF000000 | spell.getColor());
                g.drawCenteredString(this.font, initialsFor(spell), sx, sy - 4, 0xFFFFFFFF);
            } else {
                drawFilledCircle(g, sx, sy, SOCKET_R - 2, TH.slotFill());
                g.drawCenteredString(this.font, String.valueOf(i + 1), sx, sy - 4, TH.textDim());
            }
            if (spell != null) {
                // Slot number tucked on the rim so filled sockets still read as 1-9.
                int bx = sx + SOCKET_R - 5;
                int by = sy + SOCKET_R - 7;
                g.fill(bx - 1, by - 1, bx + 8, by + 10, TH.border());
                g.fill(bx, by, bx + 7, by + 9, selected ? TH.trimBright() : TH.trim());
                g.drawString(this.font, String.valueOf(i + 1), bx + 1, by + 1, TH.border(), false);
            }
        }

        // Hub: which slot, its key, and what it holds.
        SpellType current = this.slotSpells[this.selectedSlot];
        drawFittedCentered(g, this.font, Component.translatable("gui." + ExampleMod.MODID + ".spell_menu.slot", this.selectedSlot + 1),
                cx, cy - 15, TH.trimBright(), 56);
        drawFittedCentered(g, this.font, Component.literal("[").append(keybindLabel(this.selectedSlot)).append("]"),
                cx, cy - 4, TH.textDim(), 56);
        Component holding = !hasWand
                ? Component.translatable("gui." + ExampleMod.MODID + ".spell_menu.no_wand_short")
                : current == null ? Component.translatable("gui." + ExampleMod.MODID + ".wand_radial.empty") : spellName(current);
        drawFittedCentered(g, this.font, holding, cx, cy + 7, current == null ? TH.textDim() : TH.text(), 56);
    }

    private void renderList(GuiGraphics g, int left, int top, boolean hasWand) {
        drawWell(g, left + LIST_X0, top + LIST_Y0, left + LIST_X1, top + LIST_Y1, TH);
        g.drawString(this.font, Component.translatable("gui." + ExampleMod.MODID + ".spell_menu.learned", this.learnedSpells.size()),
                left + LIST_X0 + 5, top + LIST_Y0 + 4, TH.title(), false);
        g.fill(left + LIST_X0 + 4, top + ROWS_TOP - 3, left + LIST_X1 - 4, top + ROWS_TOP - 2, TH.wellLight());

        if (this.learnedSpells.isEmpty()) {
            drawFittedCentered(g, this.font, Component.translatable("gui." + ExampleMod.MODID + ".spell_menu.none_learned"),
                    left + (LIST_X0 + LIST_X1) / 2f, top + (LIST_Y0 + LIST_Y1) / 2f, TH.textDim(), LIST_X1 - LIST_X0 - 10);
            return;
        }

        SpellType current = this.slotSpells[this.selectedSlot];
        int rowX0 = left + LIST_X0 + 2;
        int rowX1 = left + LIST_X1 - 7;
        for (int r = 0; r < VISIBLE_ROWS; r++) {
            int index = this.scrollRows + r;
            if (index >= this.learnedSpells.size()) {
                break;
            }
            SpellType spell = this.learnedSpells.get(index);
            int ry = top + ROWS_TOP + r * ROW_H;
            boolean hovered = index == this.hoveredRow;
            if (hovered) {
                g.fill(rowX0, ry, rowX1, ry + ROW_H - 1, hasWand ? TH.wellLight() : TH.slotFill());
            } else if ((r & 1) == 1) {
                g.fill(rowX0, ry, rowX1, ry + ROW_H - 1, TH.slotFill());
            }
            if (spell == current) {
                g.fill(rowX0, ry, rowX0 + 2, ry + ROW_H - 1, TH.trimBright());
            }
            // Colour chip, matching the disc the spell gets on the wheel.
            g.fill(rowX0 + 4, ry + 2, rowX0 + 12, ry + 10, TH.border());
            g.fill(rowX0 + 5, ry + 3, rowX0 + 11, ry + 9, 0xFF000000 | spell.getColor());

            // Badges for every slot already holding this spell, right-aligned.
            int bx = rowX1 - 2;
            for (int s = SLOTS - 1; s >= 0; s--) {
                if (this.slotSpells[s] == spell) {
                    bx -= 9;
                    g.fill(bx, ry + 1, bx + 8, ry + 11, s == this.selectedSlot ? TH.trimBright() : TH.trim());
                    g.drawString(this.font, String.valueOf(s + 1), bx + 2, ry + 2, TH.border(), false);
                    bx -= 1;
                }
            }
            int nameX = rowX0 + 15;
            int maxName = bx - 3 - nameX;
            Component name = spellName(spell);
            int nameW = this.font.width(name);
            float scale = nameW > maxName ? Math.max(0.5f, maxName / (float) nameW) : 1f;
            GuiRenderUtil.drawScaledString(g, this.font, name, nameX, ry + 2 + (1f - scale) * 4f,
                    hovered && hasWand ? TH.trimBright() : TH.text(), scale);
        }

        if (this.maxScroll() > 0) {
            int trackX = left + LIST_X1 - 5;
            int trackY0 = top + ROWS_TOP;
            int trackY1 = top + ROWS_TOP + VISIBLE_ROWS * ROW_H - 1;
            g.fill(trackX, trackY0, trackX + 3, trackY1, TH.wellShadow());
            int trackH = trackY1 - trackY0;
            int thumbH = Math.max(10, trackH * VISIBLE_ROWS / this.learnedSpells.size());
            int thumbY = trackY0 + (trackH - thumbH) * this.scrollRows / this.maxScroll();
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, TH.trim());
        }
    }

    private void renderHoverTooltip(GuiGraphics g, int mouseX, int mouseY, boolean hasWand) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (this.hoveredSocket >= 0) {
            int i = this.hoveredSocket;
            SpellType spell = this.slotSpells[i];
            lines.add(Component.translatable("gui." + ExampleMod.MODID + ".spell_menu.slot", i + 1)
                    .append(" [").append(keybindLabel(i)).append("]").withStyle(net.minecraft.ChatFormatting.GOLD).getVisualOrderText());
            lines.add((spell == null ? Component.translatable("gui." + ExampleMod.MODID + ".wand_radial.empty") : spellName(spell))
                    .copy().withStyle(net.minecraft.ChatFormatting.WHITE).getVisualOrderText());
            lines.add(Component.translatable("gui." + ExampleMod.MODID + ".spell_menu.socket_hint")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY).getVisualOrderText());
        } else if (this.hoveredRow >= 0) {
            SpellType spell = this.learnedSpells.get(this.hoveredRow);
            lines.add(spellName(spell).copy().withStyle(net.minecraft.ChatFormatting.GOLD).getVisualOrderText());
            lines.addAll(this.font.split(Component.translatable("spell." + ExampleMod.MODID + "." + spell.getId() + ".desc")
                    .withStyle(net.minecraft.ChatFormatting.GRAY), 200));
            lines.add(Component.translatable("gui." + ExampleMod.MODID + ".spell_menu.mana", spell.getManaCost())
                    .withStyle(net.minecraft.ChatFormatting.AQUA).getVisualOrderText());
            Component action = hasWand
                    ? Component.translatable("gui." + ExampleMod.MODID + ".spell_menu.assign", this.selectedSlot + 1)
                    : Component.translatable("gui." + ExampleMod.MODID + ".spell_menu.no_wand");
            lines.add(action.copy().withStyle(net.minecraft.ChatFormatting.DARK_GRAY).getVisualOrderText());
        }
        if (!lines.isEmpty()) {
            g.renderTooltip(this.font, lines, mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
