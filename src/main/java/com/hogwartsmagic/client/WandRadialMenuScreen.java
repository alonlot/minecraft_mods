package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicBookItem;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.event.ClientModEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WandRadialMenuScreen extends Screen {
    private static final int SLOT_COUNT = MagicWandItem.SPELL_SLOT_COUNT;
    private static final float START_ANGLE = (float) (-Math.PI / 2.0);

    private final Player player;
    private int hoveredSlot = -1;
    private boolean wasClickSelection = false;
    private final List<SpellType> slots = new ArrayList<>(SLOT_COUNT);

    public WandRadialMenuScreen(Player player) {
        super(Component.translatable("gui." + ExampleMod.MODID + ".wand_radial.title"));
        this.player = player;
    }

    @Override
    protected void init() {
        super.init();
        refreshSlotsFromWand();
        fillMissingSlotsFromKnownSpells();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
        updateHovered(mouseX, mouseY);

        int cx = this.width / 2;
        int cy = this.height / 2;
        int radius = Math.max(92, Math.min(this.width, this.height) / 5);
        int iconRadius = 20;

        drawFilledCircle(guiGraphics, cx, cy, radius + 52, 0x55000000);
        drawRing(guiGraphics, cx, cy, radius + 30, radius + 36, 0x88A066FF);
        drawRing(guiGraphics, cx, cy, radius + 8, radius + 12, 0x66D4AF37);

        for (int i = 0; i < SLOT_COUNT; i++) {
            float angle = START_ANGLE + ((float) (Math.PI * 2.0) * i / SLOT_COUNT);
            int sx = cx + Math.round((float) Math.cos(angle) * radius);
            int sy = cy + Math.round((float) Math.sin(angle) * radius);
            SpellType spell = slots.get(i);
            boolean hovered = i == hoveredSlot;

            int frameColor = hovered ? 0xFFFFD86B : 0xAA6644AA;
            int bgColor = hovered ? 0xCC1D1233 : 0xAA120A1F;
            drawFilledCircle(guiGraphics, sx, sy, iconRadius + 6, frameColor);
            drawFilledCircle(guiGraphics, sx, sy, iconRadius + 3, bgColor);

            if (spell != null) {
                drawFilledCircle(guiGraphics, sx, sy, iconRadius - 1, 0xFF000000 | spell.getColor());
                String initials = initialsFor(spell);
                guiGraphics.drawCenteredString(this.font, initials, sx, sy - 4, 0xFFFFFFFF);
                guiGraphics.drawCenteredString(this.font, keybindLabel(i), sx, sy - iconRadius - 11, hovered ? 0xFFFFF0B3 : 0xFFE2D8FF);
                Component shortName = Component.translatable("spell." + ExampleMod.MODID + "." + spell.getId());
                guiGraphics.drawCenteredString(this.font, shortName.getString(), sx, sy + iconRadius + 7, hovered ? 0xFFF2DFA8 : 0xFFD0C8E8);
            } else {
                drawFilledCircle(guiGraphics, sx, sy, iconRadius - 1, 0xFF333333);
                guiGraphics.drawCenteredString(this.font, "-", sx, sy - 4, 0xFFB0B0B0);
                guiGraphics.drawCenteredString(this.font, keybindLabel(i), sx, sy - iconRadius - 11, 0xFFC7BCD9);
                guiGraphics.drawCenteredString(this.font, Component.translatable("gui." + ExampleMod.MODID + ".wand_radial.empty"), sx, sy + iconRadius + 7, 0xFF8A8A8A);
            }
        }

        SpellType hoveredSpell = hoveredSlot >= 0 ? slots.get(hoveredSlot) : null;
        if (hoveredSpell != null) {
            Component spellName = Component.translatable("spell." + ExampleMod.MODID + "." + hoveredSpell.getId());
            guiGraphics.drawCenteredString(this.font, spellName, cx, cy - 8, 0xFFFFFFFF);
            guiGraphics.drawCenteredString(this.font, Component.translatable("gui." + ExampleMod.MODID + ".wand_radial.hint_click_or_release"), cx, cy + 10, 0xFFD7CCF5);
        } else {
            guiGraphics.drawCenteredString(this.font, Component.translatable("gui." + ExampleMod.MODID + ".wand_radial.hint_hover"), cx, cy - 8, 0xFFD7CCF5);
        }
    }

    public void confirmSelectionAndClose() {
        if (this.wasClickSelection) {
            return;
        }
        selectHoveredAndClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.wasClickSelection = true;
            bindHoveredAndClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void selectHoveredAndClose() {
        if (hoveredSlot >= 0 && hoveredSlot < slots.size()) {
            SpellType spell = slots.get(hoveredSlot);
            if (spell != null) {
                ModEventsClientHooks.selectSpellAndCast(player, spell);
            }
        }
        onClose();
    }

    private void bindHoveredAndClose() {
        if (hoveredSlot >= 0 && hoveredSlot < slots.size()) {
            SpellType spell = slots.get(hoveredSlot);
            if (spell != null) {
                ItemStack wand = getHeldWand();
                if (!wand.isEmpty()) {
                    MagicWandItem.setSelectedSpell(wand, spell);
                }
                SpellPackets.CHANNEL.send(new SpellPackets.SelectSpellPacket(spell.getId()), PacketDistributor.SERVER.noArg());
            }
        }
        onClose();
    }

    private void refreshSlotsFromWand() {
        slots.clear();
        ItemStack wand = getHeldWand();
        if (wand.isEmpty()) {
            for (int i = 0; i < SLOT_COUNT; i++) {
                slots.add(null);
            }
            return;
        }
        slots.addAll(MagicWandItem.getSpellSlots(wand));
    }

    private void fillMissingSlotsFromKnownSpells() {
        ItemStack wand = getHeldWand();
        if (wand.isEmpty()) {
            return;
        }

        List<SpellType> known = getKnownSpells();
        Set<SpellType> used = new HashSet<>();
        boolean allEmpty = true;
        for (int i = 0; i < slots.size(); i++) {
            SpellType current = slots.get(i);
            if (current != null && known.contains(current)) {
                used.add(current);
                allEmpty = false;
            } else if (current != null) {
                slots.set(i, null);
                MagicWandItem.setSpellSlot(wand, i, null);
                SpellPackets.CHANNEL.send(new SpellPackets.SetSpellSlotPacket(i, ""), PacketDistributor.SERVER.noArg());
            }
        }

        // Respect manual slot configuration. Auto-fill only when no configured slots exist.
        if (!allEmpty) {
            return;
        }

        int cursor = 0;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i) != null) {
                continue;
            }
            while (cursor < known.size() && used.contains(known.get(cursor))) {
                cursor++;
            }
            if (cursor >= known.size()) {
                break;
            }
            SpellType fill = known.get(cursor++);
            slots.set(i, fill);
            used.add(fill);
            MagicWandItem.setSpellSlot(wand, i, fill);
            SpellPackets.CHANNEL.send(new SpellPackets.SetSpellSlotPacket(i, fill.getId()), PacketDistributor.SERVER.noArg());
        }
    }

    private List<SpellType> getKnownSpells() {
        List<SpellType> known = new ArrayList<>();
        for (SpellType spell : SpellType.values()) {
            if (MagicBookItem.isSpellLearned(player, spell)) {
                known.add(spell);
            }
        }
        return known;
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

    private void updateHovered(double mouseX, double mouseY) {
        int cx = this.width / 2;
        int cy = this.height / 2;
        int radius = Math.max(92, Math.min(this.width, this.height) / 5);
        int hitRadius = 32;

        int found = -1;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < SLOT_COUNT; i++) {
            float angle = START_ANGLE + ((float) (Math.PI * 2.0) * i / SLOT_COUNT);
            int sx = cx + Math.round((float) Math.cos(angle) * radius);
            int sy = cy + Math.round((float) Math.sin(angle) * radius);
            double dx = mouseX - sx;
            double dy = mouseY - sy;
            double distSq = (dx * dx) + (dy * dy);
            if (distSq <= (double) (hitRadius * hitRadius) && distSq < best) {
                best = distSq;
                found = i;
            }
        }
        this.hoveredSlot = found;
    }

    private String initialsFor(SpellType spell) {
        String key = Component.translatable("spell." + ExampleMod.MODID + "." + spell.getId()).getString();
        String[] words = key.split("\\s+");
        if (words.length == 0) {
            return "?";
        }
        String first = words[0].isEmpty() ? "?" : words[0].substring(0, 1);
        if (words.length == 1) {
            return first.toUpperCase();
        }
        String second = words[1].isEmpty() ? "" : words[1].substring(0, 1);
        return (first + second).toUpperCase();
    }

    private String keybindLabel(int slotIndex) {
        KeyMapping mapping = ClientModEvents.getSpellSlotKeyMapping(slotIndex);
        if (mapping == null) {
            return "[?]";
        }
        return "[" + mapping.getTranslatedKeyMessage().getString() + "]";
    }

    private static void drawRing(GuiGraphics graphics, int cx, int cy, int innerRadius, int outerRadius, int color) {
        for (int r = innerRadius; r <= outerRadius; r++) {
            drawFilledCircle(graphics, cx, cy, r, color);
        }
    }

    private static void drawFilledCircle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int span = (int) Math.sqrt((radius * radius) - (y * y));
            graphics.fill(cx - span, cy + y, cx + span + 1, cy + y + 1, color);
        }
    }
}
