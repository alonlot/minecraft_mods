package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.ThemedGui.Theme;
import com.hogwartsmagic.event.ClientModEvents;
import com.hogwartsmagic.item.MagicBookItem;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

import static com.hogwartsmagic.client.ThemedGui.fillAnnulus;

/**
 * Combat spell wheel (hold R). A pie menu: slot 1 at the top, clockwise; anywhere outside the hub picks
 * the wedge under the cursor, so a flick in a direction is enough. Release R casts, left click binds.
 */
public class WandRadialMenuScreen extends Screen {
    private static final int SLOT_COUNT = MagicWandItem.SPELL_SLOT_COUNT;
    private static final float START_ANGLE = (float) (-Math.PI / 2.0);
    private static final float FULL = (float) (Math.PI * 2.0);
    private static final float STEP = FULL / SLOT_COUNT;
    private static final int SOCKET_R = 18;
    private static final int HUB_R = 40;
    private static final int OUTER_PAD = 30;

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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        updateHovered(mouseX, mouseY);

        Theme th = ThemedGui.VIOLET;
        int cx = this.width / 2;
        int cy = this.height / 2;
        int radius = wheelRadius();
        boolean hasPlayer = this.minecraft != null && this.minecraft.player != null;
        boolean stunned = hasPlayer && com.hogwartsmagic.spell.combat.CombatEffects.isStunned(this.minecraft.player);
        boolean castingBlocked = stunned
                || (hasPlayer && com.hogwartsmagic.spell.combat.CombatEffects.isSilenced(this.minecraft.player));
        ItemStack wand = getHeldWand();
        SpellType selectedSpell = wand.isEmpty() ? null : MagicWandItem.getSelectedSpell(wand);

        // Backdrop disc, gold rim, and the faint orbit the sockets ride on.
        fillAnnulus(g, cx, cy, 0, radius + OUTER_PAD, 0, FULL, 0xC40E0A1C);
        fillAnnulus(g, cx, cy, radius + OUTER_PAD, radius + OUTER_PAD + 1.5f, 0, FULL, th.trim());
        fillAnnulus(g, cx, cy, radius - 0.75f, radius + 0.75f, 0, FULL, ThemedGui.withAlpha(th.trim(), 0x70));
        if (hoveredSlot >= 0) {
            float mid = START_ANGLE + STEP * hoveredSlot;
            fillAnnulus(g, cx, cy, HUB_R + 2, radius + OUTER_PAD, mid - STEP / 2f, mid + STEP / 2f,
                    ThemedGui.withAlpha(th.trimBright(), 0x30));
        }
        fillAnnulus(g, cx, cy, 0, HUB_R + 1.5f, 0, FULL, th.trim());
        fillAnnulus(g, cx, cy, 0, HUB_R, 0, FULL, 0xF0120C22);

        for (int i = 0; i < SLOT_COUNT; i++) {
            float angle = START_ANGLE + STEP * i;
            float cos = Mth.cos(angle);
            float sin = Mth.sin(angle);
            int sx = cx + Math.round(cos * radius);
            int sy = cy + Math.round(sin * radius);
            SpellType spell = slots.get(i);
            boolean hovered = i == hoveredSlot;
            float r = hovered ? SOCKET_R + 2 : SOCKET_R;

            if (hovered) {
                fillAnnulus(g, sx, sy, r + 2, r + 5, 0, FULL, ThemedGui.withAlpha(th.trimBright(), 0x60));
            }
            fillAnnulus(g, sx, sy, 0, r + 2, 0, FULL, hovered ? th.trimBright() : spell != null ? th.trim() : th.bevelLight());
            fillAnnulus(g, sx, sy, 0, r, 0, FULL, th.wellShadow());
            if (spell != null) {
                int color = castingBlocked ? 0xFF4A4A4A : 0xFF000000 | spell.getColor();
                fillAnnulus(g, sx, sy, 0, r - 2, 0, FULL, color);
                // Soft gloss on the upper left so the discs read as glass beads rather than flat dots.
                fillAnnulus(g, sx, sy, r - 7, r - 3, (float) Math.PI * 1.05f, (float) Math.PI * 1.55f, 0x48FFFFFF);
                int rank = hasPlayer ? com.hogwartsmagic.spell.mastery.SpellMasteryData.getRank(this.minecraft.player, spell) : 0;
                boolean showRank = rank > 1;
                g.drawCenteredString(this.font, initialsFor(spell), sx, showRank ? sy - 8 : sy - 4,
                        castingBlocked ? 0xFF9A9A9A : 0xFFFFFFFF);
                if (showRank) {
                    g.drawCenteredString(this.font, com.hogwartsmagic.spell.mastery.MasteryMath.roman(rank), sx, sy + 2, 0xFFFFE08A);
                }
                if (spell == selectedSpell) {
                    // Gold pip on the inner rim marks the spell the wand casts right now.
                    float px = sx - cos * (r + 1);
                    float py = sy - sin * (r + 1);
                    fillAnnulus(g, px, py, 0, 3.5f, 0, FULL, th.border());
                    fillAnnulus(g, px, py, 0, 2.5f, 0, FULL, th.trimBright());
                }
                drawOuterLabel(g, spellName(spell), cx, cy, cos, sin, radius + SOCKET_R + 9,
                        hovered ? th.trimBright() : th.text());
            } else {
                fillAnnulus(g, sx, sy, 0, r - 2, 0, FULL, th.slotFill());
                g.drawCenteredString(this.font, String.valueOf(i + 1), sx, sy - 4, th.textDim());
            }

            Component key = keybindLabel(i);
            if (key != null) {
                int kw = this.font.width(key) + 5;
                int kx = sx + Math.round(r * 0.62f) - 2;
                int ky = sy - Math.round(r * 0.62f) - 6;
                g.fill(kx - 1, ky - 1, kx + kw + 1, ky + 11, th.border());
                g.fill(kx, ky, kx + kw, ky + 10, hovered ? th.trimBright() : th.trim());
                g.drawString(this.font, key, kx + 3, ky + 1, th.border(), false);
            }
        }

        // Hub: the hovered slot, else the spell the wand has selected.
        SpellType hoveredSpell = hoveredSlot >= 0 ? slots.get(hoveredSlot) : null;
        int hubW = HUB_R * 2 - 8;
        if (castingBlocked) {
            String key = stunned ? ".wand_radial.stunned" : ".wand_radial.silenced";
            ThemedGui.drawFittedCentered(g, this.font, Component.translatable("gui." + ExampleMod.MODID + key), cx, cy - 4, 0xFFFF6A5A, hubW);
        } else if (hoveredSpell != null) {
            ThemedGui.drawFittedCentered(g, this.font, spellName(hoveredSpell), cx, cy - 10, 0xFFFFFFFF, hubW);
            ThemedGui.drawFittedCentered(g, this.font,
                    Component.translatable("gui." + ExampleMod.MODID + ".wand_radial.mana", hoveredSpell.getManaCost()),
                    cx, cy + 2, th.textDim(), hubW);
        } else if (hoveredSlot >= 0) {
            ThemedGui.drawFittedCentered(g, this.font, Component.translatable("gui." + ExampleMod.MODID + ".wand_radial.empty"),
                    cx, cy - 4, th.textDim(), hubW);
        } else if (selectedSpell != null) {
            ThemedGui.drawFittedCentered(g, this.font, Component.translatable("gui." + ExampleMod.MODID + ".wand_radial.selected"),
                    cx, cy - 10, th.textDim(), hubW);
            ThemedGui.drawFittedCentered(g, this.font, spellName(selectedSpell), cx, cy + 2, th.trimBright(), hubW);
        } else {
            ThemedGui.drawFittedCentered(g, this.font, this.title, cx, cy - 4, th.title(), hubW);
        }

        Component hint = Component.translatable("gui." + ExampleMod.MODID
                + (hoveredSpell != null ? ".wand_radial.hint_click_or_release" : ".wand_radial.hint_hover"));
        g.drawCenteredString(this.font, hint, cx, cy + radius + OUTER_PAD + 8, th.textDim());
    }

    /** Spell name just outside its socket, anchored away from the wheel so neighbours never overlap. */
    private void drawOuterLabel(GuiGraphics g, Component text, int cx, int cy, float cos, float sin, int dist, int color) {
        int lx = cx + Math.round(cos * dist);
        int ly = cy + Math.round(sin * dist) - 4;
        int w = this.font.width(text);
        int x;
        if (cos > 0.35f) {
            x = lx;
        } else if (cos < -0.35f) {
            x = lx - w;
        } else {
            x = lx - w / 2;
            ly += sin < 0 ? -4 : 4;
        }
        g.drawString(this.font, text, x, ly, color, true);
    }

    private int wheelRadius() {
        return Math.max(84, Math.min(this.width, this.height) / 5);
    }

    private static Component spellName(SpellType spell) {
        return Component.translatable("spell." + ExampleMod.MODID + "." + spell.getId());
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

    /** Pie-menu hover: anywhere outside the hub picks the slot whose wedge the cursor is in. */
    private void updateHovered(double mouseX, double mouseY) {
        double dx = mouseX - this.width / 2.0;
        double dy = mouseY - this.height / 2.0;
        if (dx * dx + dy * dy < (double) HUB_R * HUB_R) {
            this.hoveredSlot = -1;
            return;
        }
        double rel = Math.atan2(dy, dx) - START_ANGLE;
        this.hoveredSlot = Math.floorMod((int) Math.round(rel / STEP), SLOT_COUNT);
    }

    private String initialsFor(SpellType spell) {
        String key = spellName(spell).getString();
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

    /** Bound key for the slot, or null when it has none (unbound keys are simply not shown). */
    @Nullable
    private Component keybindLabel(int slotIndex) {
        KeyMapping mapping = ClientModEvents.getSpellSlotKeyMapping(slotIndex);
        if (mapping == null || mapping.isUnbound()) {
            return null;
        }
        return mapping.getTranslatedKeyMessage();
    }
}
