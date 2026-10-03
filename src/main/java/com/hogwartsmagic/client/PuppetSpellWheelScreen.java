package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.mastery.MasteryMath;
import com.hogwartsmagic.spell.mind.PossessionRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The spell wheel while possessing a player who holds their wand: the puppet's own wand slots, gaps filled
 * with their other learned spells, at their ranks and cooldowns, under a "Puppet" header. Hold the wheel key,
 * release over a spell (or click it) to cast it as them. Spells that can't go through a puppet show greyed.
 * Draws itself (no widgets), so it never calls super.render.
 */
public class PuppetSpellWheelScreen extends Screen {
    private static final int SLOT_COUNT = 9;
    private static final double START_ANGLE = -Math.PI / 2.0;

    private final SpellPackets.PuppetSnapshotPacket snapshot;
    private final List<String> slots = new ArrayList<>(SLOT_COUNT);
    private final Map<String, Integer> ranks = new HashMap<>();
    private final Map<String, Integer> cooldowns = new HashMap<>();
    private int hoveredSlot = -1;
    private final long snapshotAtTick;

    public PuppetSpellWheelScreen(SpellPackets.PuppetSnapshotPacket snapshot) {
        super(Component.translatable("gui." + ExampleMod.MODID + ".puppet.title"));
        this.snapshot = snapshot;
        this.snapshotAtTick = PossessionClient.snapshotTick();
        for (int i = 0; i < snapshot.learnedIds().size(); i++) {
            this.ranks.put(snapshot.learnedIds().get(i), snapshot.learnedRanks().get(i));
            this.cooldowns.put(snapshot.learnedIds().get(i), snapshot.cooldownTicks().get(i));
        }
        for (String id : snapshot.wandSlots()) {
            this.slots.add(id == null || !this.ranks.containsKey(id) ? "" : id);
        }
        while (this.slots.size() < SLOT_COUNT) {
            this.slots.add("");
        }
        while (this.slots.size() > SLOT_COUNT) {
            this.slots.remove(this.slots.size() - 1);
        }
        for (String id : snapshot.learnedIds()) {
            int empty = this.slots.indexOf("");
            if (empty < 0) {
                break;
            }
            if (!this.slots.contains(id) && !PossessionRules.isPuppetBlocked(id)) {
                this.slots.set(empty, id);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBlurredBackground(float partialTick) {
        // No blur, like the Grimoire.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics);
        int cx = this.width / 2;
        int cy = this.height / 2;
        int radius = Math.max(80, Math.min(this.width, this.height) / 5);
        this.hoveredSlot = slotAt(mouseX, mouseY, cx, cy, radius);
        graphics.drawCenteredString(this.font, Component.translatable("gui." + ExampleMod.MODID + ".puppet.header",
                this.snapshot.victimName()), cx, cy - radius - 40, 0xFF9CFF9C);
        for (int i = 0; i < SLOT_COUNT; i++) {
            double angle = START_ANGLE + Math.PI * 2.0 * i / SLOT_COUNT;
            int sx = cx + (int) Math.round(Math.cos(angle) * radius);
            int sy = cy + (int) Math.round(Math.sin(angle) * radius);
            boolean hovered = i == this.hoveredSlot;
            graphics.fill(sx - 20, sy - 20, sx + 20, sy + 20, hovered ? 0xFFB8F5B8 : 0xAA2E6B36);
            graphics.fill(sx - 18, sy - 18, sx + 18, sy + 18, hovered ? 0xCC1E4A25 : 0xCC0E1F12);
            String id = this.slots.get(i);
            if (id.isEmpty()) {
                continue;
            }
            net.minecraft.client.multiplayer.ClientLevel clientLevel = this.minecraft == null ? null : this.minecraft.level;
            long elapsed = clientLevel == null ? 0L : Math.max(0L, clientLevel.getGameTime() - this.snapshotAtTick);
            int cooldown = (int) Math.max(0L, this.cooldowns.getOrDefault(id, 0) - elapsed);
            boolean usable = !PossessionRules.isPuppetBlocked(id) && cooldown <= 0;
            graphics.drawCenteredString(this.font, initials(id), sx, sy - 9, usable ? 0xFFFFFFFF : 0xFF8A8A8A);
            graphics.drawCenteredString(this.font, MasteryMath.roman(this.ranks.getOrDefault(id, 1)), sx, sy + 1, 0xFFD4AF37);
            if (cooldown > 0) {
                graphics.drawCenteredString(this.font, String.format(Locale.ROOT, "%.1fs", cooldown / 20.0f), sx, sy + 11, 0xFFFF6B6B);
            }
        }
        if (this.hoveredSlot >= 0 && !this.slots.get(this.hoveredSlot).isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("spell." + ExampleMod.MODID + "." + this.slots.get(this.hoveredSlot)),
                    cx, cy - 4, 0xFFFFFFFF);
        }
    }

    private int slotAt(int mouseX, int mouseY, int cx, int cy, int radius) {
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        if (dx * dx + dy * dy < (radius * 0.4) * (radius * 0.4)) {
            return -1;
        }
        double angle = Math.atan2(dy, dx) - START_ANGLE;
        double turns = angle / (Math.PI * 2.0);
        return Math.floorMod((int) Math.round(turns * SLOT_COUNT), SLOT_COUNT);
    }

    private String initials(String id) {
        String name = Component.translatable("spell." + ExampleMod.MODID + "." + id).getString();
        StringBuilder out = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty() && out.length() < 2) {
                out.append(Character.toUpperCase(word.charAt(0)));
            }
        }
        return out.length() == 0 ? "?" : out.toString();
    }

    /** Releasing the wheel key casts the hovered spell (the same gesture as the normal spell wheel). */
    public void confirmSelectionAndClose() {
        if (this.hoveredSlot >= 0) {
            String id = this.slots.get(this.hoveredSlot);
            if (!id.isEmpty()) {
                PossessionClient.castPuppetSpell(id);
            }
        }
        this.onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            confirmSelectionAndClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
