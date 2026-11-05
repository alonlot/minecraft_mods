package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.ThemedGui.Theme;
import com.hogwartsmagic.event.ClientModEvents;
import com.hogwartsmagic.item.MagicBookItem;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.Util;
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
    private static final int OUTER_PAD = 34;
    /** Fixed rune strings (lowercase letters render as glyphs in the minecraft:alt font). */
    private static final String RUNES_OUTER = "hogwartsdracodormiensnunquamtitillandoslumosnoxaccioexpelliarmusprotego";
    private static final String RUNES_INNER = "wingardiumleviosaalohomora";

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

        float time = Util.getMillis() / 1000f;
        float rim = radius + OUTER_PAD;
        SpellType hoveredSpellEarly = hoveredSlot >= 0 ? slots.get(hoveredSlot) : null;

        this.renderMagicBackdrop(g, cx, cy, radius, rim, time, th);
        if (hoveredSlot >= 0) {
            float mid = START_ANGLE + STEP * hoveredSlot;
            int tint = hoveredSpellEarly != null && !castingBlocked ? hoveredSpellEarly.getColor() : th.trimBright();
            // Wedge glow, brightest near the socket, plus a thin beam from the hub to it.
            fillAnnulus(g, cx, cy, HUB_R + 2, rim, mid - STEP / 2f, mid + STEP / 2f, ThemedGui.withAlpha(tint, 0x18));
            fillAnnulus(g, cx, cy, radius - SOCKET_R - 8, radius + SOCKET_R + 8, mid - STEP / 2f, mid + STEP / 2f,
                    ThemedGui.withAlpha(tint, 0x22));
            this.drawBeam(g, cx, cy, mid, HUB_R + 2, radius - SOCKET_R - 3, tint, time);
        }
        this.renderHub(g, cx, cy, time, th);

        for (int i = 0; i < SLOT_COUNT; i++) {
            float angle = START_ANGLE + STEP * i;
            float cos = Mth.cos(angle);
            float sin = Mth.sin(angle);
            int sx = cx + Math.round(cos * radius);
            int sy = cy + Math.round(sin * radius);
            SpellType spell = slots.get(i);
            boolean hovered = i == hoveredSlot;
            float r = hovered ? SOCKET_R + 2 : SOCKET_R;

            if (spell != null && !castingBlocked) {
                // Soft halo in the spell colour; the selected spell breathes.
                float breathe = spell == selectedSpell ? 0.5f + 0.5f * Mth.sin(time * 3.2f) : 0f;
                int c = spell.getColor();
                fillAnnulus(g, sx, sy, r + 2, r + 5 + breathe * 2, 0, FULL, ThemedGui.withAlpha(c, hovered ? 0x70 : 0x40 + Math.round(breathe * 0x30)));
                fillAnnulus(g, sx, sy, r + 5 + breathe * 2, r + 9 + breathe * 3, 0, FULL, ThemedGui.withAlpha(c, hovered ? 0x30 : 0x16));
            } else if (hovered) {
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
                drawOuterLabel(g, spellName(spell), cx, cy, cos, sin, Math.round(rim) + 9,
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
        g.drawCenteredString(this.font, hint, cx, cy + radius + OUTER_PAD + 24, th.textDim());
    }

    /**
     * Night-sky disc: a radial violet-to-ink gradient, an aura outside the rim, a band of slowly turning
     * runes (the enchanting-table alphabet), twinkling motes, and a glint that travels round the gold rim.
     */
    private void renderMagicBackdrop(GuiGraphics g, int cx, int cy, int radius, float rim, float time, Theme th) {
        // Aura just outside the rim.
        fillAnnulus(g, cx, cy, rim + 1.5f, rim + 5f, 0, FULL, 0x30A066FF);
        fillAnnulus(g, cx, cy, rim + 5f, rim + 10f, 0, FULL, 0x14A066FF);
        // Radial gradient, centre glow to dark edge.
        int bands = 7;
        for (int b = 0; b < bands; b++) {
            float r0 = rim * b / bands;
            float r1 = rim * (b + 1) / bands;
            int color = ThemedGui.lerpColor(0x2A1B52, 0x0B0718, b / (float) (bands - 1));
            fillAnnulus(g, cx, cy, r0, r1, 0, FULL, ThemedGui.withAlpha(color, 0xD8));
        }
        // Rune band between the sockets and the rim, with hairlines either side.
        float runeInner = radius + SOCKET_R + 3;
        float runeOuter = rim - 2;
        fillAnnulus(g, cx, cy, runeInner, runeInner + 0.8f, 0, FULL, ThemedGui.withAlpha(th.trim(), 0x50));
        fillAnnulus(g, cx, cy, runeOuter - 0.8f, runeOuter, 0, FULL, ThemedGui.withAlpha(th.trim(), 0x50));
        this.drawRuneRing(g, cx, cy, (runeInner + runeOuter) / 2f, time * 0.12f, RUNES_OUTER, 0x70D9B44A, 1f);
        // Faint orbit the sockets ride on.
        fillAnnulus(g, cx, cy, radius - 0.6f, radius + 0.6f, 0, FULL, ThemedGui.withAlpha(th.trim(), 0x48));
        // Motes: fixed seeds, slow drift, sine twinkle.
        java.util.Random rng = new java.util.Random(0x5EEDL);
        for (int i = 0; i < 46; i++) {
            float baseA = rng.nextFloat() * FULL;
            float rr = HUB_R + 4 + rng.nextFloat() * (rim - HUB_R - 8);
            float speed = (rng.nextFloat() - 0.5f) * 0.12f;
            float phase = rng.nextFloat() * FULL;
            float a = baseA + time * speed;
            float tw = 0.5f + 0.5f * Mth.sin(time * (1.5f + rng.nextFloat() * 2f) + phase);
            int alpha = Math.round(40 + 200 * tw * tw);
            int color = rng.nextBoolean() ? 0xFFF2DC : 0xD8C4FF;
            float mx = cx + Mth.cos(a) * rr;
            float my = cy + Mth.sin(a) * rr;
            int ix = Math.round(mx);
            int iy = Math.round(my);
            g.fill(ix, iy, ix + 1, iy + 1, ThemedGui.withAlpha(color, alpha));
            if (tw > 0.82f && i % 3 == 0) {
                int arm = ThemedGui.withAlpha(color, alpha / 3);
                g.fill(ix - 1, iy, ix, iy + 1, arm);
                g.fill(ix + 1, iy, ix + 2, iy + 1, arm);
                g.fill(ix, iy - 1, ix + 1, iy, arm);
                g.fill(ix, iy + 1, ix + 1, iy + 2, arm);
            }
        }
        // Gold rim with a travelling glint.
        fillAnnulus(g, cx, cy, rim, rim + 1.5f, 0, FULL, th.trim());
        float glint = time * 0.9f;
        fillAnnulus(g, cx, cy, rim - 0.5f, rim + 2f, glint - 0.10f, glint + 0.10f, 0xC0FFF3B0);
        fillAnnulus(g, cx, cy, rim - 0.5f, rim + 2f, glint - 0.32f, glint - 0.10f, 0x50FFF3B0);
        fillAnnulus(g, cx, cy, rim - 0.5f, rim + 2f, glint + 0.10f, glint + 0.32f, 0x50FFF3B0);
        float glint2 = glint + (float) Math.PI;
        fillAnnulus(g, cx, cy, rim - 0.5f, rim + 2f, glint2 - 0.07f, glint2 + 0.07f, 0x80FFF3B0);
    }

    /** Hub: gold-rimmed well with a turning inner circle of small runes and two counter-rotating arcs. */
    private void renderHub(GuiGraphics g, int cx, int cy, float time, Theme th) {
        fillAnnulus(g, cx, cy, HUB_R + 1.5f, HUB_R + 6f, 0, FULL, 0x28A066FF);
        fillAnnulus(g, cx, cy, 0, HUB_R + 1.5f, 0, FULL, th.trim());
        fillAnnulus(g, cx, cy, 0, HUB_R, 0, FULL, 0xF0120C22);
        fillAnnulus(g, cx, cy, 0, HUB_R * 0.7f, 0, FULL, 0x30402A78);
        this.drawRuneRing(g, cx, cy, HUB_R + 10, -time * 0.2f, RUNES_INNER, 0x48C9B6FF, 0.75f);
        float a = time * 0.6f;
        fillAnnulus(g, cx, cy, HUB_R - 3.5f, HUB_R - 2.5f, a, a + 1.4f, ThemedGui.withAlpha(th.trimBright(), 0x70));
        fillAnnulus(g, cx, cy, HUB_R - 3.5f, HUB_R - 2.5f, a + (float) Math.PI, a + (float) Math.PI + 1.4f, ThemedGui.withAlpha(th.trimBright(), 0x70));
        float b = -time * 0.45f;
        fillAnnulus(g, cx, cy, HUB_R - 6.5f, HUB_R - 5.8f, b, b + 2.2f, 0x50C9B6FF);
    }

    /** Glyphs of the enchanting-table alphabet set around a circle, each turned to face outward. */
    private void drawRuneRing(GuiGraphics g, int cx, int cy, float r, float rotation, String runes, int color, float scale) {
        int count = runes.length();
        for (int i = 0; i < count; i++) {
            float angle = rotation + FULL * i / count;
            Component glyph = Component.literal(String.valueOf(runes.charAt(i)))
                    .withStyle(s -> s.withFont(net.minecraft.client.Minecraft.ALT_FONT));
            g.pose().pushPose();
            g.pose().translate(cx + Mth.cos(angle) * r, cy + Mth.sin(angle) * r, 0f);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotation(angle + (float) Math.PI / 2f));
            g.pose().scale(scale, scale, 1f);
            g.drawString(this.font, glyph, -this.font.width(glyph) / 2, -4, color, false);
            g.pose().popPose();
        }
    }

    /** Tapered, flickering beam from the hub edge out to the hovered socket. */
    private void drawBeam(GuiGraphics g, int cx, int cy, float angle, float from, float to, int color, float time) {
        float c = Mth.cos(angle);
        float s = Mth.sin(angle);
        float nx = -s;
        float ny = c;
        float flicker = 0.8f + 0.2f * Mth.sin(time * 17f);
        float x0 = cx + c * from, y0 = cy + s * from;
        float x1 = cx + c * to, y1 = cy + s * to;
        ThemedGui.fillQuad(g, x0 + nx * 4f, y0 + ny * 4f, x1 + nx * 2f, y1 + ny * 2f, x1 - nx * 2f, y1 - ny * 2f,
                x0 - nx * 4f, y0 - ny * 4f, ThemedGui.withAlpha(color, Math.round(0x30 * flicker)));
        ThemedGui.fillQuad(g, x0 + nx * 1.4f, y0 + ny * 1.4f, x1 + nx * 0.7f, y1 + ny * 0.7f, x1 - nx * 0.7f, y1 - ny * 0.7f,
                x0 - nx * 1.4f, y0 - ny * 1.4f, ThemedGui.withAlpha(ThemedGui.lerpColor(color, 0xFFFFFF, 0.55f), Math.round(0xC0 * flicker)));
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
