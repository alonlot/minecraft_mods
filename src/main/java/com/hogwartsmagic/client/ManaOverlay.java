package com.hogwartsmagic.client;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;

public class ManaOverlay {
    public static void render(GuiGraphics guiGraphics, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.isSpectator()) return;

        // Ensure we are holding the wand
        boolean holdingWand = com.hogwartsmagic.item.MagicWandItem.isAnyWand(minecraft.player.getMainHandItem()) ||
                              com.hogwartsmagic.item.MagicWandItem.isAnyWand(minecraft.player.getOffhandItem());

        if (!holdingWand) return; // Only show mana when holding a wand

        CompoundTag modData = minecraft.player.getPersistentData()
                .getCompound("PlayerPersisted")
                .getCompound(ExampleMod.MODID);

        float currentMana = modData.contains("Mana") ? modData.getFloat("Mana") : Config.getBaseMaxMana();
        float maxMana = modData.contains("MaxMana") ? modData.getFloat("MaxMana") : Config.getBaseMaxMana();

        if (maxMana <= 0) maxMana = Config.getBaseMaxMana();

        int x = width - 120; // Right side
        int y = height - 30; // Above hotbar area

        // Draw background bar
        int barWidth = 100;
        int barHeight = 8;
        guiGraphics.fill(x, y, x + barWidth, y + barHeight, 0x88000000); // Semi-transparent black

        // Draw foreground mana bar
        int fillWidth = (int) ((currentMana / maxMana) * barWidth);
        fillWidth = Math.max(0, Math.min(barWidth, fillWidth));

        // Light blue color
        guiGraphics.fill(x, y, x + fillWidth, y + barHeight, 0xFF00AAFF);

        // Draw text
        String text = (int) currentMana + " / " + (int) maxMana;
        int textWidth = minecraft.font.width(text);
        guiGraphics.drawString(minecraft.font, text, x + (barWidth - textWidth) / 2, y - 10, 0xFFFFFF, true);
    }
}
