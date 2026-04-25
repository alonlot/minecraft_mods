package com.hogwartsmagic.client;

import com.hogwartsmagic.network.SpellPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class FlooNamingScreen extends Screen {
    private final BlockPos fireplacePos;
    private final String initialName;
    private EditBox nameBox;

    public FlooNamingScreen(BlockPos fireplacePos, String initialName) {
        super(Component.translatable("gui.hogwartsmagic.floo_name"));
        this.fireplacePos = fireplacePos;
        this.initialName = initialName == null ? "" : initialName;
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();
        int centerX = width / 2;
        int top = height / 2 - 50;

        this.nameBox = new EditBox(font, centerX - 100, top + 28, 200, 20, Component.translatable("gui.hogwartsmagic.floo_name"));
        this.nameBox.setMaxLength(32);
        this.nameBox.setValue(initialName);
        this.addRenderableWidget(this.nameBox);
        setInitialFocus(this.nameBox);

        addRenderableWidget(Button.builder(Component.translatable("gui.hogwartsmagic.save"), button -> saveAndClose())
                .bounds(centerX - 90, top + 58, 80, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.hogwartsmagic.close"), button -> onClose())
                .bounds(centerX + 10, top + 58, 80, 20)
                .build());
    }

    private void saveAndClose() {
        String name = this.nameBox.getValue().trim();
        if (!name.isEmpty()) {
            SpellPackets.CHANNEL.send(new SpellPackets.SetFlooNamePacket(fireplacePos, name), net.minecraftforge.network.PacketDistributor.SERVER.noArg());
        }
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            saveAndClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        int top = height / 2 - 50;
        guiGraphics.fill(centerX - 120, top, centerX + 120, top + 90, 0xE0121B16);
        guiGraphics.fill(centerX - 120, top, centerX + 120, top + 2, 0xFF66E89D);
        guiGraphics.drawCenteredString(font, title, centerX, top + 10, 0xFFEAF8EF);
        guiGraphics.drawCenteredString(font, Component.translatable("gui.hogwartsmagic.floo_name_hint"), centerX, top + 16 + font.lineHeight, 0xFF9EDDB7);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
