package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.event.ClientModEvents;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

/**
 * Spell menu screen that opens when Shift + Right Click with the Harry Potter wand.
 */
public class SpellMenuScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int MENU_WIDTH = 380;
    private static final int MENU_HEIGHT = 300;
    private final Player player;
    private final List<SpellType> learnedSpells = new ArrayList<>();
    private int selectedSlot = 0;

    public SpellMenuScreen(Player player) {
        super(Component.translatable("gui." + ExampleMod.MODID + ".spell_menu"));
        this.player = player;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        refreshLearnedSpells();

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int left = centerX - MENU_WIDTH / 2;
        int top = centerY - MENU_HEIGHT / 2;

        int slotButtonW = 110;
        int slotButtonH = 20;
        int slotSpacingX = 120;
        int slotSpacingY = 24;
        int slotStartX = centerX - slotSpacingX;
        int slotStartY = top + 30;

        // 3x3 slot grid (radial spell wheel configuration).
        for (int slot = 0; slot < MagicWandItem.SPELL_SLOT_COUNT; slot++) {
            final int slotIndex = slot;
            int row = slot / 3;
            int col = slot % 3;
            int x = slotStartX + (col * slotSpacingX) - (slotButtonW / 2);
            int y = slotStartY + (row * slotSpacingY);
            addRenderableWidget(Button.builder(slotButtonLabel(slotIndex), btn -> {
                        selectedSlot = slotIndex;
                        this.init();
                    })
                    .bounds(x, y, slotButtonW, slotButtonH)
                    .tooltip(Tooltip.create(Component.translatable("gui." + ExampleMod.MODID + ".spell_menu.slot_hint", slotIndex + 1)))
                    .build());
        }

        addRenderableWidget(Button.builder(
                        Component.translatable("gui." + ExampleMod.MODID + ".spell_menu.clear_slot"),
                        btn -> clearSelectedSlot())
                .bounds(left + 12, top + 104, 110, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui." + ExampleMod.MODID + ".close"),
                        button -> onClose())
                .bounds(left + MENU_WIDTH - 72, top + 104, 60, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui." + ExampleMod.MODID + ".relics"),
                        button -> openRelicScreen())
                .bounds(left + 132, top + 104, 90, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui." + ExampleMod.MODID + ".grimoire"),
                        button -> Minecraft.getInstance().setScreen(new SpellCompendiumScreen(player)))
                .bounds(left + 228, top + 104, 90, 20)
                .build());

        // Learned spells list (click a spell to assign it to the selected slot).
        int cols = 4;
        int buttonWidth = 86;
        int buttonHeight = 18;
        int hSpacing = 90;
        int vSpacing = 20;
        int startX = centerX - ((cols * hSpacing) / 2) + 2;
        int startY = top + 134;

        for (int i = 0; i < learnedSpells.size(); i++) {
            final SpellType spell = learnedSpells.get(i);
            int col = i % cols;
            int row = i / cols;
            int x = startX + (col * hSpacing);
            int y = startY + (row * vSpacing);
            addRenderableWidget(Button.builder(
                            shortSpellLabel(spell),
                            button -> assignSpellToSelectedSlot(spell))
                    .bounds(x, y, buttonWidth, buttonHeight)
                    .tooltip(Tooltip.create(Component.translatable("spell." + ExampleMod.MODID + "." + spell.getId() + ".desc")))
                    .build());
        }
    }

    private void refreshLearnedSpells() {
        learnedSpells.clear();
        for (SpellType spell : SpellType.values()) {
            if (com.hogwartsmagic.item.MagicBookItem.isSpellLearned(player, spell)) {
                learnedSpells.add(spell);
            }
        }
    }

    private void assignSpellToSelectedSlot(SpellType spell) {
        ItemStack wand = getHeldWand();
        if (wand.isEmpty()) {
            return;
        }
        MagicWandItem.setSpellSlot(wand, selectedSlot, spell);
        SpellPackets.CHANNEL.send(new SpellPackets.SetSpellSlotPacket(selectedSlot, spell.getId()), PacketDistributor.SERVER.noArg());
        this.init();
    }

    private void clearSelectedSlot() {
        ItemStack wand = getHeldWand();
        if (wand.isEmpty()) {
            return;
        }
        MagicWandItem.setSpellSlot(wand, selectedSlot, null);
        SpellPackets.CHANNEL.send(new SpellPackets.SetSpellSlotPacket(selectedSlot, ""), PacketDistributor.SERVER.noArg());
        this.init();
    }

    private ItemStack getHeldWand() {
        ItemStack main = player.getMainHandItem();
        if (MagicWandItem.isAnyWand(main)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (MagicWandItem.isAnyWand(off)) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    private Component slotButtonLabel(int slotIndex) {
        ItemStack wand = getHeldWand();
        SpellType configured = wand.isEmpty() ? null : MagicWandItem.getSpellSlot(wand, slotIndex);
        Component spellPart = configured == null
                ? Component.translatable("gui." + ExampleMod.MODID + ".wand_radial.empty")
                : shortSpellLabel(configured);
        Component keyPart = keybindLabel(slotIndex);
        String marker = selectedSlot == slotIndex ? ">> " : "";
        return Component.literal(marker + (slotIndex + 1) + " ")
                .append(keyPart)
                .append(Component.literal(": "))
                .append(spellPart);
    }

    private Component shortSpellLabel(SpellType spell) {
        return Component.translatable("spell." + ExampleMod.MODID + "." + spell.getId());
    }

    private Component keybindLabel(int slotIndex) {
        KeyMapping mapping = ClientModEvents.getSpellSlotKeyMapping(slotIndex);
        if (mapping == null) {
            return Component.literal("[?]");
        }
        return Component.literal("[")
                .append(mapping.getTranslatedKeyMessage())
                .append(Component.literal("]"));
    }

    private void openRelicScreen() {
        try {
            Class<?> relicScreenClass = Class.forName("com.hogwartsmagic.client.RelicScreen");
            Object screen = relicScreenClass.getConstructor(Player.class).newInstance(player);
            if (screen instanceof Screen screenInstance) {
                Minecraft.getInstance().setScreen(screenInstance);
                return;
            }
            throw new IllegalStateException("RelicScreen is not a Screen: " + relicScreenClass.getName());
        } catch (ReflectiveOperationException | LinkageError exception) {
            if (player != null) {
                player.displayClientMessage(
                        Component.literal("Relics screen is unavailable in this build."),
                        true
                );
            }
            LOGGER.error("Failed to open relic screen", exception);
        }
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

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int left = centerX - MENU_WIDTH / 2;
        int top = centerY - MENU_HEIGHT / 2;

        // Draw semi-transparent dark background
        guiGraphics.fill(left - 10, top - 30, left + MENU_WIDTH + 10, top + MENU_HEIGHT + 10, 0xDD000000);

        // Draw border
        guiGraphics.fill(left - 10, top - 30, left + MENU_WIDTH + 10, top - 29, 0xFF8B4513); // Brown
        guiGraphics.fill(left - 10, top + MENU_HEIGHT + 9, left + MENU_WIDTH + 10, top + MENU_HEIGHT + 10, 0xFF8B4513);
        guiGraphics.fill(left - 10, top - 30, left - 9, top + MENU_HEIGHT + 10, 0xFF8B4513);
        guiGraphics.fill(left + MENU_WIDTH + 9, top - 30, left + MENU_WIDTH + 10, top + MENU_HEIGHT + 10, 0xFF8B4513);

        // Draw title
        guiGraphics.drawCenteredString(this.font, this.title, centerX, top - 22, 0xFFD4AF37);
        guiGraphics.drawString(this.font,
                Component.translatable("gui." + ExampleMod.MODID + ".spell_menu.assign_hint"),
                left + 12, top + 10, 0xFFE8DBFF);
        guiGraphics.drawString(this.font,
                Component.translatable("gui." + ExampleMod.MODID + ".spell_menu.active_slot", selectedSlot + 1),
                left + 12, top + 116, 0xFFF2DFA8);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
