package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class SpellHUD {
    
    public static void render(GuiGraphics guiGraphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        
        Player player = mc.player;
        if (player == null) return;

        ItemStack stack = player.getMainHandItem();
        if (!MagicWandItem.isAnyWand(stack)) {
            stack = player.getOffhandItem();
        }

        if (MagicWandItem.isAnyWand(stack)) {
            renderWandHUD(guiGraphics, player, stack);
        }
    }

    private static void renderWandHUD(GuiGraphics guiGraphics, Player player, ItemStack stack) {
        SpellType selected = MagicWandItem.getSelectedSpell(stack);
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        // Position on the right side
        int x = screenWidth - 115;
        int y = screenHeight / 2 - 20;

        // Background box
        guiGraphics.fill(x - 5, y - 5, screenWidth - 5, y + 35, 0x88000000);
        guiGraphics.fill(x - 5, y - 5, screenWidth - 5, y - 4, 0xFFD4AF37); // Gold border top
        guiGraphics.fill(x - 5, y + 34, screenWidth - 5, y + 35, 0xFFD4AF37); // Gold border bottom
        guiGraphics.fill(x - 5, y - 5, x - 4, y + 35, 0xFFD4AF37); // Gold border left

        if (selected == null) {
            guiGraphics.drawString(Minecraft.getInstance().font, 
                Component.translatable("item.hogwartsmagic.harry_potter_wand.none_selected"), 
                x, y, 0xAAAAAA);
        } else {
            Component name = Component.translatable("spell.hogwartsmagic." + selected.getId());
            guiGraphics.drawString(Minecraft.getInstance().font, name, x, y, 0xFFFFFF);

            long now = player.level().getGameTime();
            long cooldownEnd = MagicWandItem.getSpellCooldown(stack, selected);
            long remaining = cooldownEnd - now;

            if (remaining > 0) {
                float progress = 1.0f - ((float) remaining / (float) Math.max(1L, com.hogwartsmagic.spell.SpellCosts.cooldownTicks(player, stack, selected)));
                int barWidth = 100;
                // Clamped: a cooldown can run longer than today's figure (started before a rank-up, or a
                // longer penalty cooldown), and a negative width would draw the bar out to the left.
                int filledWidth = (int) (barWidth * Math.max(0.0f, Math.min(1.0f, progress)));

                // Cooldown Bar Background
                guiGraphics.fill(x, y + 15, x + barWidth, y + 20, 0xFF444444);
                // Cooldown Bar Foreground
                guiGraphics.fill(x, y + 15, x + filledWidth, y + 20, selected.getColor());
                
                String timeText = String.format("%.1fs", remaining / 20.0f);
                guiGraphics.drawString(Minecraft.getInstance().font, timeText, x, y + 22, 0xAAAAAA, false);
            } else {
                guiGraphics.drawString(Minecraft.getInstance().font, 
                    Component.translatable("gui.hogwartsmagic.ready"), 
                    x, y + 15, 0x55FF55);
            }
        }
    }
}
