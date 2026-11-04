package com.hogwartsmagic.client;

import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.relic.RelicSlotType;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class RelicScreen extends Screen {
    private static final int PANEL_WIDTH = 384;
    private static final int PANEL_HEIGHT = 362;
    private static final int SLOT_PANEL_WIDTH = 160;
    private static final int SLOT_PANEL_HEIGHT = 56;
    private static final int INVENTORY_ROW_HEIGHT = 24;

    private final Player player;
    private final List<Integer> relicSlots = new ArrayList<>();
    private Button closeButton;
    private int left;
    private int top;

    public RelicScreen(Player player) {
        super(Component.translatable("gui.hogwartsmagic.relics"));
        this.player = player;
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        relicSlots.clear();
        relicSlots.addAll(collectRelicInventorySlots());

        closeButton = addRenderableWidget(Button.builder(Component.translatable("gui.hogwartsmagic.close"), button -> onClose())
                .bounds(left + PANEL_WIDTH - 96, top + PANEL_HEIGHT - 30, 80, 20)
                .build());
    }

    private List<Integer> collectRelicInventorySlots() {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (com.hogwartsmagic.relic.RelicItem.isRelic(player.getInventory().getItem(i))) {
                slots.add(i);
            }
        }
        return slots;
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // No blur, like the Grimoire.
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No-op: super.render() calls this after the panel is drawn, and the menu backdrop would cover it.
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xEC151126);
        guiGraphics.fill(left + 4, top + 4, left + PANEL_WIDTH - 4, top + PANEL_HEIGHT - 4, 0xF2E2C892);
        guiGraphics.fill(left + 10, top + 28, left + PANEL_WIDTH - 10, top + PANEL_HEIGHT - 38, 0xEE1D1731);
        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + 3, 0xFFB68B3E);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(font, title, left + PANEL_WIDTH / 2, top + 10, 0xFFF3D88D);
        guiGraphics.drawCenteredString(font, Component.translatable("gui.hogwartsmagic.relics.subtitle"),
                left + PANEL_WIDTH / 2, top + 24, 0xFFD9C99A);

        ItemStack capacity = PlayerMagicData.getEquippedRelic(player, RelicSlotType.CAPACITY);
        ItemStack regen = PlayerMagicData.getEquippedRelic(player, RelicSlotType.REGEN);
        int slotY = top + 44;
        renderEquippedSlot(guiGraphics, left + 16, slotY, SLOT_PANEL_WIDTH, capacity,
                Component.translatable("gui.hogwartsmagic.relics.capacity"),
                Component.translatable("gui.hogwartsmagic.relics.unequip_capacity"), mouseX, mouseY);
        renderEquippedSlot(guiGraphics, left + PANEL_WIDTH - 16 - SLOT_PANEL_WIDTH, slotY, SLOT_PANEL_WIDTH, regen,
                Component.translatable("gui.hogwartsmagic.relics.regen"),
                Component.translatable("gui.hogwartsmagic.relics.unequip_regen"), mouseX, mouseY);

        guiGraphics.drawString(font, Component.translatable("gui.hogwartsmagic.relics.max_mana", (int) PlayerMagicData.getMaxMana(player)),
                left + 20, top + 114, 0xFF95D7FF, false);
        int regenPercent = Math.round((PlayerMagicData.getManaRegenMultiplier(player) - 1.0f) * 100.0f);
        guiGraphics.drawString(font, Component.translatable("gui.hogwartsmagic.relics.regen_bonus", regenPercent),
                left + 20, top + 128, 0xFFAED2FF, false);
        guiGraphics.drawString(font, Component.translatable("gui.hogwartsmagic.relics.inventory"),
                left + 20, top + 156, 0xFFF0DE9C, false);
        guiGraphics.fill(left + 18, top + 168, left + PANEL_WIDTH - 18, top + 169, 0x667A6034);

        renderInventoryRelics(guiGraphics, mouseX, mouseY);
        if (closeButton != null) {
            closeButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderEquippedSlot(GuiGraphics guiGraphics, int x, int y, int width, ItemStack stack,
                                     Component label, Component action, int mouseX, int mouseY) {
        guiGraphics.fill(x, y, x + width, y + SLOT_PANEL_HEIGHT, 0x9942321E);
        guiGraphics.fill(x, y, x + width, y + 1, 0xFFC7A05D);
        guiGraphics.fill(x + 8, y + 18, x + 28, y + 38, 0xFF715C2C);
        guiGraphics.fill(x + 10, y + 20, x + 26, y + 36, 0xFF201934);
        guiGraphics.drawString(font, label, x + 8, y + 6, 0xFFF3E5B5, false);
        if (!stack.isEmpty()) {
            guiGraphics.renderItem(stack, x + 10, y + 20);
            guiGraphics.renderItemDecorations(Minecraft.getInstance().font, stack, x + 10, y + 20);
            guiGraphics.drawString(font, trim(stack.getHoverName(), 14), x + 34, y + 18, 0xFFF7EFDB, false);
            guiGraphics.drawString(font, action, x + 34, y + 31, 0xFF8DD7FF, false);
        } else {
            guiGraphics.drawString(font, Component.translatable("gui.hogwartsmagic.wand_radial.empty"), x + 34, y + 24, 0xFF9B9077, false);
        }
        if (isWithin(mouseX, mouseY, x, y, width, SLOT_PANEL_HEIGHT) && !stack.isEmpty()) {
            guiGraphics.renderOutline(x, y, width, SLOT_PANEL_HEIGHT, 0xFFE4C271);
        }
    }

    private void renderInventoryRelics(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startX = left + 18;
        int startY = top + 176;
        int rowWidth = PANEL_WIDTH - 36;
        relicSlots.removeIf(slot -> !isInventorySlotRelic(slot));
        if (relicSlots.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("gui.hogwartsmagic.relics.none"),
                    startX, startY + 8, 0xFF9B9077, false);
            return;
        }

        for (int i = 0; i < relicSlots.size() && i < 6; i++) {
            int slot = relicSlots.get(i);
            ItemStack stack = player.getInventory().getItem(slot);
            int rowY = startY + i * INVENTORY_ROW_HEIGHT;
            guiGraphics.fill(startX, rowY, startX + rowWidth, rowY + 20, 0x8A33281C);
            guiGraphics.fill(startX, rowY, startX + rowWidth, rowY + 1, 0xFF6E5A37);
            guiGraphics.fill(startX + 6, rowY + 2, startX + 24, rowY + 18, 0xFF1E1833);
            guiGraphics.renderItem(stack, startX + 7, rowY + 2);
            guiGraphics.renderItemDecorations(Minecraft.getInstance().font, stack, startX + 7, rowY + 2);

            com.hogwartsmagic.relic.RelicType relicType = com.hogwartsmagic.relic.RelicItem.getRelicType(stack);
            if (relicType == null) {
                continue;
            }
            RelicSlotType slotType = relicType.getSlotType();
            String slotName = slotType == RelicSlotType.CAPACITY ? "Capacity" : "Regen";
            guiGraphics.drawString(font, trim(stack.getHoverName(), 20), startX + 30, rowY + 4, 0xFFF5E8C6, false);
            guiGraphics.drawString(font, Component.literal("Click to equip to " + slotName), startX + 30, rowY + 12, 0xFF8DD7FF, false);

            if (isWithin(mouseX, mouseY, startX, rowY, rowWidth, 20)) {
                guiGraphics.renderOutline(startX, rowY, rowWidth, 20, 0xFFE4C271);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int equippedY = top + 44;
            if (handleEquippedClick(mouseX, mouseY, left + 16, equippedY, SLOT_PANEL_WIDTH, RelicSlotType.CAPACITY)) {
                return true;
            }
            if (handleEquippedClick(mouseX, mouseY, left + PANEL_WIDTH - 16 - SLOT_PANEL_WIDTH, equippedY, SLOT_PANEL_WIDTH, RelicSlotType.REGEN)) {
                return true;
            }

            int startX = left + 18;
            int startY = top + 176;
            int rowWidth = PANEL_WIDTH - 36;
            for (int i = 0; i < relicSlots.size() && i < 6; i++) {
                int slot = relicSlots.get(i);
                if (!isInventorySlotRelic(slot)) {
                    continue;
                }
                int rowY = startY + i * INVENTORY_ROW_HEIGHT;
                if (isWithin(mouseX, mouseY, startX, rowY, rowWidth, 20)) {
                    SpellPackets.CHANNEL.send(new SpellPackets.EquipRelicPacket(slot),
                            net.minecraftforge.network.PacketDistributor.SERVER.noArg());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isInventorySlotRelic(int slot) {
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
            return false;
        }
        return com.hogwartsmagic.relic.RelicItem.isRelic(player.getInventory().getItem(slot));
    }

    private boolean handleEquippedClick(double mouseX, double mouseY, int x, int y, int width, RelicSlotType slotType) {
        ItemStack stack = PlayerMagicData.getEquippedRelic(player, slotType);
        if (!stack.isEmpty() && isWithin(mouseX, mouseY, x, y, width, SLOT_PANEL_HEIGHT)) {
            SpellPackets.CHANNEL.send(new SpellPackets.UnequipRelicPacket(slotType.getIndex()),
                    net.minecraftforge.network.PacketDistributor.SERVER.noArg());
            return true;
        }
        return false;
    }

    private boolean isWithin(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private Component trim(Component text, int maxChars) {
        String value = text.getString();
        if (value.length() <= maxChars) {
            return text;
        }
        return Component.literal(value.substring(0, Math.max(0, maxChars - 3)) + "...");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
