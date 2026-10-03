package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.mind.MindControl;
import com.hogwartsmagic.network.SpellPackets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The green Imperius vignette over a controlled player's HUD. Its whole state is the imperius effect
 * vanilla syncs to the local player, so there is nothing to desync. Drawn by MindControlGuiMixin.
 */
public final class MindControlOverlay {
    private static final int STRIPS = 20;
    private static final int IMPERIUS_GREEN = 0x2FAF3F;
    private static final ResourceLocation HOTBAR_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar");
    private static final ResourceLocation HOTBAR_SELECTION_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_selection");

    private MindControlOverlay() {
    }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        if (MindControl.isControlled(player)) {
            float pulse = 0.85F + 0.15F * Mth.sin(player.tickCount * 0.2F);
            vignette(graphics, IMPERIUS_GREEN, (int) (170 * pulse));
            if (PossessionClient.isVictim()) {
                renderVictimPrompt(graphics, minecraft, player.tickCount);
            } else {
                graphics.drawCenteredString(minecraft.font, Component.translatable("gui." + ExampleMod.MODID + ".imperio.victim"),
                        graphics.guiWidth() / 2, 14, 0xFF9CFF9C);
            }
        }
        if (PossessionClient.isCaster()) {
            renderCasterHud(graphics, minecraft);
        }
    }

    /** Whose eyes you're behind and for how long; for a player puppet, their hotbar (over yours) and mana. */
    private static void renderCasterHud(GuiGraphics graphics, Minecraft minecraft) {
        int cx = graphics.guiWidth() / 2;
        Entity victim = minecraft.level == null ? null : minecraft.level.getEntity(PossessionClient.otherId());
        Component name = victim != null ? victim.getDisplayName() : Component.literal("?");
        int seconds = (PossessionClient.ticksLeft() + 19) / 20;
        graphics.drawCenteredString(minecraft.font, Component.translatable("gui." + ExampleMod.MODID + ".possession.caster_header",
                name, seconds), cx, 14, 0xFF9CFF9C);
        SpellPackets.PuppetSnapshotPacket snapshot = PossessionClient.snapshot();
        if (snapshot == null) {
            return;
        }
        int x = cx - 91;
        int y = graphics.guiHeight() - 22;
        graphics.fill(x, y, x + 182, y + 22, 0xFF000000); // hide the caster's own hotbar underneath
        graphics.blitSprite(HOTBAR_SPRITE, x, y, 182, 22);
        graphics.blitSprite(HOTBAR_SELECTION_SPRITE, x - 1 + PossessionClient.displayedSlot() * 20, y - 1, 24, 23);
        for (int slot = 0; slot < snapshot.hotbarIds().size() && slot < 9; slot++) {
            ItemStack stack = PossessionClient.stackFor(snapshot.hotbarIds().get(slot), snapshot.hotbarCounts().get(slot));
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 3 + slot * 20, y + 3);
                graphics.renderItemDecorations(minecraft.font, stack, x + 3 + slot * 20, y + 3);
            }
        }
        float maxMana = snapshot.maxMana() <= 0.0F ? 1.0F : snapshot.maxMana();
        int barX = graphics.guiWidth() - 120;
        int barY = graphics.guiHeight() - 58;
        int fill = Math.max(0, Math.min(100, (int) (snapshot.mana() / maxMana * 100)));
        graphics.fill(barX, barY, barX + 100, barY + 8, 0x88000000);
        graphics.fill(barX, barY, barX + fill, barY + 8, 0xFF3FD06A);
        graphics.drawString(minecraft.font, Component.translatable("gui." + ExampleMod.MODID + ".puppet.mana",
                (int) snapshot.mana(), (int) snapshot.maxMana()), barX, barY - 10, 0xFFB8F5B8, true);
        if (snapshot.holdsWand()) {
            graphics.drawCenteredString(minecraft.font, Component.translatable("gui." + ExampleMod.MODID + ".possession.wheel_hint"),
                    cx, graphics.guiHeight() - 48, 0xFFB8F5B8);
        }
    }

    /** A possessed player: who has them, and from 8s on a flashing "Mash SPACE to resist!". */
    private static void renderVictimPrompt(GuiGraphics graphics, Minecraft minecraft, int tick) {
        int cx = graphics.guiWidth() / 2;
        graphics.drawCenteredString(minecraft.font, Component.translatable("gui." + ExampleMod.MODID + ".possession.victim"),
                cx, 14, 0xFF9CFF9C);
        if (PossessionClient.resistOpen()) {
            int colour = (tick / 5) % 2 == 0 ? 0xFFFFFF55 : 0xFFFFAA00;
            graphics.drawCenteredString(minecraft.font, Component.translatable("gui." + ExampleMod.MODID + ".possession.resist"),
                    cx, graphics.guiHeight() / 2 + 24, colour);
        }
    }

    /** A soft edge faked with translucent strips down all four sides, strongest at the rim. */
    static void vignette(GuiGraphics graphics, int rgb, int peak) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int stripW = Math.max(1, (width / 5) / STRIPS);
        int stripH = Math.max(1, (height / 5) / STRIPS);
        for (int i = 0; i < STRIPS; i++) {
            float t = 1.0F - i / (float) STRIPS;
            int alpha = (int) (peak * t * t);
            if (alpha <= 2) {
                continue;
            }
            int colour = (alpha << 24) | rgb;
            int x = i * stripW;
            graphics.fill(x, 0, x + stripW, height, colour);
            graphics.fill(width - x - stripW, 0, width - x, height, colour);
            int y = i * stripH;
            graphics.fill(0, y, width, y + stripH, colour);
            graphics.fill(0, height - y - stripH, width, height - y, colour);
        }
    }
}
