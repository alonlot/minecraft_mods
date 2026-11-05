package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import static com.hogwartsmagic.client.ThemedGui.fillQuad;
import static com.hogwartsmagic.client.ThemedGui.withAlpha;

/**
 * The acceptance letter. Opens as a sealed envelope: the wax seal breaks, the flap lifts, the letter
 * slides out and settles while the envelope drops away. Click or press any key to skip the opening.
 * The settled letter is a single parchment sheet: crest, emerald school heading, body, signature, and
 * a wax-seal close button.
 */
public class HogwartsInviteScreen extends Screen {
    private static final ResourceLocation CREST = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/gui/hogwarts_crest.png");
    private static final ResourceLocation SEAL = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/gui/hogwarts_seal.png");
    private static final int CREST_TEX_W = 256, CREST_TEX_H = 280;
    private static final int SEAL_TEX_W = 256, SEAL_TEX_H = 218;
    private static final int OPEN_TICKS = 36;

    private static final int PAPER = 0xF3E4C4;
    private static final int PAPER_EDGE = 0xE6D0A6;
    private static final int PAPER_RIM = 0xB08D5E;
    private static final int INK = 0x3C2415;
    private static final int INK_EMERALD = 0x1F5236;
    private static final int INK_SIGNATURE = 0x6C1F17;
    private static final int ENVELOPE_FRONT = 0xEBD3A8;
    private static final int ENVELOPE_BACK = 0xC9A979;
    private static final int ENVELOPE_FLAP = 0xE0C495;
    private static final int ENVELOPE_LINE = 0xB8936A;

    private final String recipient;
    private int animationTicks;
    private boolean pageSoundPlayed;

    // Settled-letter layout, screen coords.
    private int letterW;
    private int letterH;
    private int letterLeft;
    private int letterTop;
    private int crestW;
    private int crestH;
    private int textW;

    public HogwartsInviteScreen(String recipient) {
        super(Component.translatable("gui.hogwartsmagic.hogwarts_invite"));
        this.recipient = recipient == null || recipient.isBlank() ? "Young Witch or Wizard" : recipient;
    }

    @Override
    protected void init() {
        super.init();
        this.layout();
    }

    private Component[] paragraphs() {
        return new Component[]{
                Component.translatable("item.hogwartsmagic.hogwarts_invite.line1", this.recipient),
                Component.translatable("item.hogwartsmagic.hogwarts_invite.line2"),
                Component.translatable("item.hogwartsmagic.hogwarts_invite.line3"),
                Component.translatable("item.hogwartsmagic.hogwarts_invite.line4")
        };
    }

    private Component signature() {
        return Component.translatable("item.hogwartsmagic.hogwarts_invite.signature").withStyle(ChatFormatting.ITALIC);
    }

    /** Sizes the sheet to its content; the crest shrinks first when the screen is short. */
    private void layout() {
        this.letterW = Math.min(300, this.width - 40);
        this.textW = this.letterW - 52;
        int body = 0;
        for (Component p : this.paragraphs()) {
            body += this.font.split(p, this.textW).size() * 10 + 6;
        }
        int sig = this.font.split(this.signature(), this.textW - 60).size() * 10;
        // Mirrors drawLetter: crest top 14, crest, 6, heading block 37, body, 6, signature; the seal
        // button (24 tall, 10 above the bottom) must clear the last body line on the left.
        int withoutCrest = 14 + 6 + 37 + body;
        this.crestW = 46;
        this.crestH = Math.round(this.crestW * CREST_TEX_H / (float) CREST_TEX_W);
        int full = Math.max(withoutCrest + this.crestH + 6 + sig + 14, withoutCrest + this.crestH + 36);
        if (full > this.height - 16) {
            this.crestW = 30;
            this.crestH = Math.round(this.crestW * CREST_TEX_H / (float) CREST_TEX_W);
        }
        this.letterH = Math.max(withoutCrest + this.crestH + 6 + sig + 14, withoutCrest + this.crestH + 36);
        this.letterLeft = (this.width - this.letterW) / 2;
        this.letterTop = Math.max(4, (this.height - this.letterH) / 2);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.animationTicks < OPEN_TICKS) {
            this.animationTicks++;
        }
    }

    private boolean opened() {
        return this.animationTicks >= OPEN_TICKS;
    }

    private void skipOpening() {
        this.animationTicks = OPEN_TICKS;
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        g.fill(0, 0, this.width, this.height, 0x900F0906);

        float t = this.opened() ? 1f : Mth.clamp((this.animationTicks + partialTick) / OPEN_TICKS, 0f, 1f);
        float seal = phase(t, 0.16f, 0.30f);
        float flap = phase(t, 0.22f, 0.42f);
        float rise = ease(phase(t, 0.38f, 0.70f));
        float settle = ease(phase(t, 0.68f, 1.0f));
        float text = phase(t, 0.84f, 1.0f);
        if (rise > 0 && !this.pageSoundPlayed) {
            this.pageSoundPlayed = true;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
        }

        // Envelope geometry; it drops and fades while the letter settles.
        float ew = Math.min(220f, this.letterW * 0.75f);
        float eh = ew * 0.6f;
        float ecx = this.width / 2f;
        float etop = this.height / 2f - eh / 2f + 10f + settle * (eh + 40f);
        int envAlpha = Math.round(255 * (1f - settle));
        float el = ecx - ew / 2f;
        float er = ecx + ew / 2f;
        float eb = etop + eh;

        // Letter transform: tucked inside the envelope, rising out, then growing to full size.
        float s0 = Math.min((ew - 14f) / this.letterW, (eh - 8f) / this.letterH);
        float insideCy = eb - 4f - this.letterH * s0 / 2f;
        float raisedCy = etop + eh * 0.25f - this.letterH * s0 / 2f;
        float finalCx = this.letterLeft + this.letterW / 2f;
        float finalCy = this.letterTop + this.letterH / 2f;
        float scale = Mth.lerp(settle, s0, 1f);
        float cx = Mth.lerp(settle, ecx, finalCx);
        float cy = Mth.lerp(settle, Mth.lerp(rise, insideCy, raisedCy), finalCy);

        if (envAlpha > 0) {
            g.fill(Math.round(el), Math.round(etop), Math.round(er), Math.round(eb), withAlpha(ENVELOPE_BACK, envAlpha));
            if (flap >= 0.5f) {
                this.drawFlap(g, el, er, etop, eh, flap, envAlpha);
            }
        }

        if (rise > 0f) {
            g.pose().pushPose();
            g.pose().translate(cx - this.letterW * scale / 2f, cy - this.letterH * scale / 2f, 0f);
            g.pose().scale(scale, scale, 1f);
            boolean final1 = settle >= 1f;
            int mx = final1 ? mouseX - this.letterLeft : -1000;
            int my = final1 ? mouseY - this.letterTop : -1000;
            this.drawLetter(g, Math.round(text * 255f), mx, my, settle > 0.5f);
            g.pose().popPose();
        }

        if (envAlpha > 0) {
            this.drawEnvelopeFront(g, el, er, etop, eb, ecx, eh, envAlpha);
            if (flap < 0.5f) {
                this.drawFlap(g, el, er, etop, eh, flap, envAlpha);
            }
            if (seal < 1f) {
                float sealGrow = 1f + seal * 0.5f;
                float sw = 30f * sealGrow;
                float sh = sw * SEAL_TEX_H / SEAL_TEX_W;
                float apexY = etop + eh * 0.55f;
                this.blitTinted(g, SEAL, ecx - sw / 2f, apexY - sh / 2f, sw, sh, SEAL_TEX_W, SEAL_TEX_H, 1f - seal);
            }
        }

        if (!this.opened()) {
            g.drawCenteredString(this.font, Component.translatable("gui.hogwartsmagic.hogwarts_invite.skip"),
                    this.width / 2, this.height - 16, withAlpha(0xBFA98A, 180));
        }
    }

    /** Top flap: closed it points down over the front; past halfway it has flipped up behind the letter. */
    private void drawFlap(GuiGraphics g, float el, float er, float etop, float eh, float p, int alpha) {
        float cx = (el + er) / 2f;
        float apexY = p < 0.5f
                ? etop + eh * 0.55f * (1f - p * 2f)
                : etop - eh * 0.42f * (p * 2f - 1f);
        int color = p < 0.5f ? ENVELOPE_FLAP : ENVELOPE_BACK;
        fillQuad(g, el, etop, er, etop, cx, apexY, cx, apexY, withAlpha(color, alpha));
        int line = withAlpha(ENVELOPE_LINE, alpha);
        fillQuad(g, el, etop, el + 1.2f, etop, cx, apexY + 0.6f, cx, apexY - 0.6f, line);
        fillQuad(g, er, etop, er - 1.2f, etop, cx, apexY + 0.6f, cx, apexY - 0.6f, line);
    }

    /** Front pocket with the V notch the side flaps make, so the letter shows through the top. */
    private void drawEnvelopeFront(GuiGraphics g, float el, float er, float etop, float eb, float ecx, float eh, int alpha) {
        float notch = etop + eh * 0.5f;
        int front = withAlpha(ENVELOPE_FRONT, alpha);
        fillQuad(g, el, etop, ecx, notch, ecx, eb, el, eb, front);
        fillQuad(g, er, etop, ecx, notch, ecx, eb, er, eb, front);
        int line = withAlpha(ENVELOPE_LINE, alpha);
        fillQuad(g, el, etop, el + 1.2f, etop, ecx, notch + 0.6f, ecx, notch - 0.6f, line);
        fillQuad(g, er, etop, er - 1.2f, etop, ecx, notch + 0.6f, ecx, notch - 0.6f, line);
        // Bottom flap folds.
        fillQuad(g, el, eb, el + 1.2f, eb, ecx, eb - eh * 0.32f, ecx, eb - eh * 0.32f + 1.2f, line);
        fillQuad(g, er, eb, er - 1.2f, eb, ecx, eb - eh * 0.32f, ecx, eb - eh * 0.32f + 1.2f, line);
        int rim = withAlpha(PAPER_RIM, alpha);
        g.fill(Math.round(el), Math.round(eb) - 1, Math.round(er), Math.round(eb), rim);
        g.fill(Math.round(el), Math.round(etop), Math.round(el) + 1, Math.round(eb), rim);
        g.fill(Math.round(er) - 1, Math.round(etop), Math.round(er), Math.round(eb), rim);
    }

    /** The sheet in its own coords (0,0 = top-left); {@code mouseX/Y} are sheet-local, or far away. */
    private void drawLetter(GuiGraphics g, int textAlpha, int mouseX, int mouseY, boolean shadow) {
        int w = this.letterW;
        int h = this.letterH;
        if (shadow) {
            g.fill(4, 5, w + 4, h + 5, 0x50000000);
        }
        g.fill(0, 0, w, h, 0xFF000000 | PAPER_RIM);
        g.fill(1, 1, w - 1, h - 1, 0xFF000000 | PAPER_EDGE);
        g.fill(3, 3, w - 3, h - 3, 0xFF000000 | PAPER);
        // Age: warm vignette at the edges and a few fixed speckles.
        g.fillGradient(3, 3, w - 3, 16, 0x26805A2A, 0x00805A2A);
        g.fillGradient(3, h - 16, w - 3, h - 3, 0x00805A2A, 0x30805A2A);
        g.fill(3, 3, 7, h - 3, 0x14805A2A);
        g.fill(w - 7, 3, w - 3, h - 3, 0x14805A2A);
        Random speckle = new Random(0x484F47L);
        for (int i = 0; i < 36; i++) {
            int sx = 8 + speckle.nextInt(Math.max(1, w - 16));
            int sy = 8 + speckle.nextInt(Math.max(1, h - 16));
            int size = 1 + speckle.nextInt(2);
            g.fill(sx, sy, sx + size, sy + size, 0x18603E1A);
        }
        // Emerald double rule just inside the edge, like printed stationery.
        outline(g, 7, 7, w - 7, h - 7, 0x70000000 | INK_EMERALD);
        outline(g, 9, 9, w - 9, h - 9, 0x38000000 | INK_EMERALD);

        int y = 14;
        this.blitTinted(g, CREST, (w - this.crestW) / 2f, y, this.crestW, this.crestH, CREST_TEX_W, CREST_TEX_H, 1f);
        y += this.crestH + 6;
        if (textAlpha < 8) {
            return;
        }
        int emerald = withAlpha(INK_EMERALD, textAlpha);
        drawCenteredPlain(g, Component.translatable("gui.hogwartsmagic.hogwarts_invite.school1"), w / 2, y, emerald);
        y += 10;
        drawCenteredPlain(g, Component.translatable("gui.hogwartsmagic.hogwarts_invite.school2"), w / 2, y, emerald);
        y += 13;
        int ruleW = 60;
        g.fill(w / 2 - ruleW, y, w / 2 + ruleW, y + 1, withAlpha(INK_EMERALD, textAlpha * 3 / 4));
        g.fill(w / 2 - 2, y - 1, w / 2 + 2, y + 2, withAlpha(INK_EMERALD, textAlpha));
        y += 8;

        int ink = withAlpha(INK, textAlpha);
        int x = 26;
        Component[] paragraphs = this.paragraphs();
        for (int i = 0; i < paragraphs.length; i++) {
            int color = i == 0 ? withAlpha(INK_EMERALD, textAlpha) : ink;
            for (FormattedCharSequence line : this.font.split(paragraphs[i], this.textW)) {
                g.drawString(this.font, line, x, y, color, false);
                y += 10;
            }
            y += 6;
        }
        y += 6;
        int sigColor = withAlpha(INK_SIGNATURE, textAlpha);
        for (FormattedCharSequence line : this.font.split(this.signature(), this.textW - 60)) {
            g.drawString(this.font, line, w - 26 - this.font.width(line), y, sigColor, false);
            y += 10;
        }

        // Wax seal close button, bottom left.
        boolean hovered = this.overClose(mouseX, mouseY);
        int bx = 22;
        int by = h - 34;
        float grow = hovered ? 1.12f : 1f;
        float sw = 28f * grow;
        float sh = sw * SEAL_TEX_H / SEAL_TEX_W;
        this.blitTinted(g, SEAL, bx + 14 - sw / 2f, by + 12 - sh / 2f, sw, sh, SEAL_TEX_W, SEAL_TEX_H, textAlpha / 255f);
        int labelColor = withAlpha(hovered ? INK_SIGNATURE : INK, textAlpha);
        Component close = Component.translatable("gui.hogwartsmagic.close");
        g.drawString(this.font, hovered ? close.copy().withStyle(ChatFormatting.UNDERLINE) : close, bx + 32, by + 8, labelColor, false);
    }

    private void drawCenteredPlain(GuiGraphics g, Component text, int cx, int y, int color) {
        g.drawString(this.font, text, cx - this.font.width(text) / 2, y, color, false);
    }

    private static void outline(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
        g.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
    }

    /** Whole-texture blit, linearly filtered (these are big painted images drawn small), with alpha. */
    private void blitTinted(GuiGraphics g, ResourceLocation tex, float x, float y, float w, float h, int texW, int texH, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        Minecraft.getInstance().getTextureManager().getTexture(tex).setFilter(true, false);
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().scale(w / texW, h / texH, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        g.blit(tex, 0, 0, texW, texH, 0f, 0f, texW, texH, texW, texH);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        g.pose().popPose();
    }

    private boolean overClose(int localX, int localY) {
        int bx = 22;
        int by = this.letterH - 34;
        return localX >= bx && localX < bx + 70 && localY >= by && localY < by + 24;
    }

    private static float phase(float t, float start, float end) {
        return Mth.clamp((t - start) / (end - start), 0f, 1f);
    }

    private static float ease(float value) {
        float inverse = 1.0F - value;
        return 1.0F - inverse * inverse * inverse;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.opened()) {
            this.skipOpening();
            return true;
        }
        if (button == 0 && this.overClose((int) mouseX - this.letterLeft, (int) mouseY - this.letterTop)) {
            this.onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.minecraft != null && keyCode == this.minecraft.options.keyInventory.getKey().getValue()) {
            this.onClose();
            return true;
        }
        if (!this.opened() && keyCode != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            this.skipOpening();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No-op: Screen.render() calls this again after the panel is drawn, which put the menu blur
        // and backdrop on top of it. render() draws the plain dim itself (no blur, per the screen rule).
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
