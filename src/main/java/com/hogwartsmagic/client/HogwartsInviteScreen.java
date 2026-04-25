package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

public class HogwartsInviteScreen extends Screen {
    private static final ResourceLocation EMBLEM_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/gui/hogwarts_invite_emblem.png");
    private static final int EMBLEM_TEXTURE_WIDTH = 1600;
    private static final int EMBLEM_TEXTURE_HEIGHT = 1067;
    private static final int OPEN_ANIMATION_TICKS = 22;

    private final String recipient;
    private int animationTicks;
    private int letterLeft;
    private int letterTop;
    private int letterWidth;
    private int letterHeight;
    private int closeX;
    private int closeY;
    private int closeWidth;
    private int closeHeight;

    public HogwartsInviteScreen(String recipient) {
        super(Component.translatable("gui.hogwartsmagic.hogwarts_invite"));
        this.recipient = recipient == null || recipient.isBlank() ? "Young Witch or Wizard" : recipient;
    }

    @Override
    protected void init() {
        super.init();
        recalculateLayout();
    }

    @Override
    public void resize(net.minecraft.client.Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        recalculateLayout();
    }

    private void recalculateLayout() {
        this.letterWidth = Math.min(360, this.width - 60);
        this.letterHeight = Math.min(236, this.height - 52);
        this.letterLeft = (this.width - this.letterWidth) / 2;
        this.letterTop = (this.height - this.letterHeight) / 2;
        this.closeWidth = 68;
        this.closeHeight = 38;
        this.closeX = this.letterLeft + this.letterWidth - this.closeWidth - 14;
        this.closeY = this.letterTop + 12;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.animationTicks < OPEN_ANIMATION_TICKS) {
            this.animationTicks++;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fill(0, 0, this.width, this.height, 0xB40F0906);

        float openProgress = easeOutCubic(Mth.clamp((this.animationTicks + partialTick) / OPEN_ANIMATION_TICKS, 0.0F, 1.0F));
        float paperProgress = easeOutCubic(Mth.clamp((openProgress - 0.12F) / 0.88F, 0.0F, 1.0F));
        float textProgress = easeOutCubic(Mth.clamp((openProgress - 0.35F) / 0.65F, 0.0F, 1.0F));

        renderLetterShadow(guiGraphics, paperProgress);
        renderParchment(guiGraphics, paperProgress);
        renderEnvelope(guiGraphics, openProgress);
        renderCloseSeal(guiGraphics, mouseX, mouseY, openProgress);
        renderInviteText(guiGraphics, textProgress);
    }

    private void renderLetterShadow(GuiGraphics guiGraphics, float paperProgress) {
        if (paperProgress <= 0.01F) {
            return;
        }
        int visibleHeight = Math.max(20, Math.round(this.letterHeight * paperProgress));
        int shadowTop = this.letterTop + this.letterHeight - visibleHeight + 6;
        guiGraphics.fill(this.letterLeft + 10, shadowTop, this.letterLeft + this.letterWidth + 10, shadowTop + visibleHeight + 10, 0x3A000000);
    }

    private void renderParchment(GuiGraphics guiGraphics, float paperProgress) {
        if (paperProgress <= 0.01F) {
            return;
        }

        int visibleHeight = Math.max(12, Math.round(this.letterHeight * paperProgress));
        int paperTop = this.letterTop + this.letterHeight - visibleHeight;
        int paperBottom = this.letterTop + this.letterHeight;
        guiGraphics.fill(this.letterLeft, paperTop, this.letterLeft + this.letterWidth, paperBottom, 0xFFF4E6C8);
        guiGraphics.fill(this.letterLeft, paperTop, this.letterLeft + this.letterWidth, paperTop + 3, 0xFFFDF5E3);
        guiGraphics.fill(this.letterLeft, paperBottom - 3, this.letterLeft + this.letterWidth, paperBottom, 0xFFD3B890);
        guiGraphics.fill(this.letterLeft, paperTop, this.letterLeft + 3, paperBottom, 0xFFE7D0AA);
        guiGraphics.fill(this.letterLeft + this.letterWidth - 3, paperTop, this.letterLeft + this.letterWidth, paperBottom, 0xFFD6BA92);
    }

    private void renderEnvelope(GuiGraphics guiGraphics, float openProgress) {
        int envelopeInset = 20;
        int envelopeLeft = this.letterLeft + envelopeInset;
        int envelopeRight = this.letterLeft + this.letterWidth - envelopeInset;
        int envelopeWidth = envelopeRight - envelopeLeft;
        int envelopeHeight = Math.max(94, this.letterHeight / 2);
        int baseY = this.letterTop + this.letterHeight - envelopeHeight + 14;
        int frontTop = baseY + Math.round(openProgress * 10.0F);
        int frontBottom = baseY + envelopeHeight;
        int flapLift = Math.round(openProgress * 44.0F);
        int flapTop = baseY - flapLift;
        int flapHeight = Math.max(34, envelopeHeight / 2);
        int flapInset = Math.round(10.0F + openProgress * 16.0F);

        guiGraphics.fill(envelopeLeft, baseY - 6, envelopeRight, frontBottom, 0xFFDDC09A);
        guiGraphics.fill(envelopeLeft + 3, baseY - 3, envelopeRight - 3, frontBottom - 3, 0xFFE9D0AB);

        int crestWidth = Math.min(126, envelopeWidth - 48);
        int crestHeight = Math.round(crestWidth * (EMBLEM_TEXTURE_HEIGHT / (float) EMBLEM_TEXTURE_WIDTH));
        int crestX = envelopeLeft + (envelopeWidth - crestWidth) / 2;
        int crestY = flapTop + 6;
        guiGraphics.blit(EMBLEM_TEXTURE, crestX, crestY, 0, 0, crestWidth, crestHeight, EMBLEM_TEXTURE_WIDTH, EMBLEM_TEXTURE_HEIGHT);

        guiGraphics.fill(envelopeLeft + flapInset, flapTop, envelopeRight - flapInset, flapTop + flapHeight, 0xFFF3E3C7);
        guiGraphics.fill(envelopeLeft + flapInset, flapTop, envelopeRight - flapInset, flapTop + 2, 0xFFFFF8ED);
        guiGraphics.fill(envelopeLeft + flapInset, flapTop + flapHeight - 2, envelopeRight - flapInset, flapTop + flapHeight, 0xFFD9BC95);

        guiGraphics.fill(envelopeLeft, frontTop, envelopeRight, frontBottom, 0xFFF1D8B3);
        guiGraphics.fill(envelopeLeft, frontTop, envelopeRight, frontTop + 2, 0xFFFDF0DA);
        guiGraphics.fill(envelopeLeft, frontBottom - 3, envelopeRight, frontBottom, 0xFFCFA97E);
        guiGraphics.fill(envelopeLeft, frontTop, envelopeLeft + 3, frontBottom, 0xFFE0C299);
        guiGraphics.fill(envelopeRight - 3, frontTop, envelopeRight, frontBottom, 0xFFD3AE87);

        int ribbonTop = frontTop + 18;
        guiGraphics.fill(envelopeLeft + 20, ribbonTop, envelopeRight - 20, ribbonTop + 2, 0x55A77241);
        guiGraphics.fill(envelopeLeft + 20, ribbonTop + 16, envelopeRight - 20, ribbonTop + 18, 0x338D5E31);
    }

    private void renderCloseSeal(GuiGraphics guiGraphics, int mouseX, int mouseY, float openProgress) {
        if (openProgress < 0.55F) {
            return;
        }
        boolean hovered = isHoveringClose(mouseX, mouseY);
        int x = hovered ? this.closeX - 1 : this.closeX;
        int y = hovered ? this.closeY - 1 : this.closeY;
        int width = hovered ? this.closeWidth + 2 : this.closeWidth;
        int height = hovered ? this.closeHeight + 2 : this.closeHeight;

        guiGraphics.fill(x, y + 4, x + width, y + height, hovered ? 0xFFD7B286 : 0xFFCDA57B);
        guiGraphics.fill(x + 2, y + 6, x + width - 2, y + height - 2, hovered ? 0xFFF3DEC0 : 0xFFECD4AF);
        guiGraphics.fill(x + 6, y + 12, x + width - 6, y + 14, 0xFFB7372D);
        guiGraphics.fill(x + 6, y + 14, x + width - 6, y + 16, 0xFF7C1E1A);

        int crestWidth = 22;
        int crestHeight = Math.round(crestWidth * (EMBLEM_TEXTURE_HEIGHT / (float) EMBLEM_TEXTURE_WIDTH));
        guiGraphics.blit(EMBLEM_TEXTURE, x + 8, y + 5, 0, 0, crestWidth, crestHeight, EMBLEM_TEXTURE_WIDTH, EMBLEM_TEXTURE_HEIGHT);
        guiGraphics.drawString(this.font, Component.translatable("gui.hogwartsmagic.close"), x + 32, y + 14, hovered ? 0xFF6F1A15 : 0xFF52201A, false);
    }

    private void renderInviteText(GuiGraphics guiGraphics, float textProgress) {
        if (textProgress <= 0.01F) {
            return;
        }
        int alpha = Mth.clamp(Math.round(textProgress * 255.0F), 0, 255);
        int color = withAlpha(0x3C2415, alpha);
        int accentColor = withAlpha(0x5A3420, alpha);
        int signatureColor = withAlpha(0x6C1F17, alpha);

        int textX = this.letterLeft + 26;
        int textY = this.letterTop + 62;
        int illustrationWidth = Math.min(126, this.letterWidth / 3);
        int illustrationHeight = Math.round(illustrationWidth * (EMBLEM_TEXTURE_HEIGHT / (float) EMBLEM_TEXTURE_WIDTH));
        int illustrationX = this.letterLeft + this.letterWidth - illustrationWidth - 24;
        int illustrationY = this.letterTop + 72;
        int textRight = illustrationX - 18;
        int maxWidth = Math.max(120, textRight - textX);

        guiGraphics.drawString(this.font, this.title, textX, this.letterTop + 44, accentColor, false);

        guiGraphics.fill(illustrationX - 8, illustrationY - 8, illustrationX + illustrationWidth + 8, illustrationY + illustrationHeight + 8, withAlpha(0xEBD8B7, alpha));
        guiGraphics.fill(illustrationX - 8, illustrationY - 8, illustrationX + illustrationWidth + 8, illustrationY - 6, withAlpha(0xFFF7E7, alpha));
        guiGraphics.fill(illustrationX - 8, illustrationY + illustrationHeight + 6, illustrationX + illustrationWidth + 8, illustrationY + illustrationHeight + 8, withAlpha(0xD3B890, alpha));
        guiGraphics.blit(EMBLEM_TEXTURE, illustrationX, illustrationY, 0, 0, illustrationWidth, illustrationHeight, EMBLEM_TEXTURE_WIDTH, EMBLEM_TEXTURE_HEIGHT);

        textY = drawWrapped(guiGraphics, Component.translatable("item.hogwartsmagic.hogwarts_invite.line1", this.recipient), textX, textY, maxWidth, accentColor) + 10;
        textY = drawWrapped(guiGraphics, Component.translatable("item.hogwartsmagic.hogwarts_invite.line2"), textX, textY, maxWidth, color) + 8;
        textY = drawWrapped(guiGraphics, Component.translatable("item.hogwartsmagic.hogwarts_invite.line3"), textX, textY, maxWidth, color) + 8;
        textY = drawWrapped(guiGraphics, Component.translatable("item.hogwartsmagic.hogwarts_invite.line4"), textX, textY, maxWidth, color) + 12;
        drawWrapped(guiGraphics, Component.translatable("item.hogwartsmagic.hogwarts_invite.signature"), textX, textY, maxWidth, signatureColor);
    }

    private int drawWrapped(GuiGraphics guiGraphics, Component component, int x, int y, int maxWidth, int color) {
        List<FormattedCharSequence> lines = this.font.split(component, maxWidth);
        int lineY = y;
        for (FormattedCharSequence line : lines) {
            guiGraphics.drawString(this.font, line, x, lineY, color, false);
            lineY += this.font.lineHeight + 1;
        }
        return lineY;
    }

    private boolean isHoveringClose(double mouseX, double mouseY) {
        return mouseX >= this.closeX && mouseX < this.closeX + this.closeWidth
                && mouseY >= this.closeY && mouseY < this.closeY + this.closeHeight;
    }

    private static float easeOutCubic(float value) {
        float inverse = 1.0F - value;
        return 1.0F - inverse * inverse * inverse;
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHoveringClose(mouseX, mouseY)) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.minecraft != null && keyCode == this.minecraft.options.keyInventory.getKey().getValue()) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
