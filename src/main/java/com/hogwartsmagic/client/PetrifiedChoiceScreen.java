package com.hogwartsmagic.client;

import com.hogwartsmagic.network.SpellPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.PacketDistributor;

/**
 * Shown when the local player turns to stone, like the death screen: wait for rescue or give in. It does not pause,
 * Esc means "wait", and the chat key (or the command key, with "/") opens chat so the player can call for help.
 */
public class PetrifiedChoiceScreen extends Screen {
    private static final int BUTTON_WIDTH = 200;
    private static final int TITLE_COLOR = 0xC8CCD0;
    private static final int BODY_COLOR = 0xE0E0E0;
    private boolean sent;

    public PetrifiedChoiceScreen() {
        super(Component.translatable("gui.hogwartsmagic.petrified.title"));
    }

    @Override
    protected void init() {
        int x = (this.width - BUTTON_WIDTH) / 2;
        int y = this.height / 4 + 72;
        addRenderableWidget(Button.builder(Component.translatable("gui.hogwartsmagic.petrified.wait"), b -> onClose())
                .bounds(x, y, BUTTON_WIDTH, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.hogwartsmagic.petrified.give_in"), b -> giveIn())
                .bounds(x, y + 24, BUTTON_WIDTH, 20).build());
    }

    private void giveIn() {
        if (!sent) {
            sent = true;
            SpellPackets.CHANNEL.send(new SpellPackets.GiveInPacket(), PacketDistributor.SERVER.noArg());
        }
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.minecraft != null && this.minecraft.options.keyChat.matches(keyCode, scanCode)) {
            this.minecraft.setScreen(new ChatScreen(""));
            return true;
        }
        if (this.minecraft != null && this.minecraft.options.keyCommand.matches(keyCode, scanCode)) {
            this.minecraft.setScreen(new ChatScreen("/"));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Hard no-op: never run the menu-blur post effect for this screen.
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No-op so super.render() can't draw the blurred backdrop; render() draws the plain dim itself.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        g.pose().pushPose();
        g.pose().scale(2.0F, 2.0F, 2.0F);
        g.drawCenteredString(this.font, this.title, this.width / 4, this.height / 8, TITLE_COLOR);
        g.pose().popPose();
        g.drawCenteredString(this.font, Component.translatable("gui.hogwartsmagic.petrified.body"),
                this.width / 2, this.height / 4 + 36, BODY_COLOR);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
