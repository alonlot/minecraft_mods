package com.hogwartsmagic.cosy.client.card;

import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.cosy.CardTimeline;
import com.hogwartsmagic.cosy.WizardCards;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The card reveal, and the album's large view of a card.
 *
 * <p><b>Reveal</b> (opened by {@link CardRevealClient} after a Chocolate Frog box rolls a card), about 3 seconds in:
 * the lid pops with gold and cocoa sparks (0.0 s), the frog leaps out (0.3 s), the card rises out of the box to the
 * screen centre, tilting up to 10 degrees with the mouse (0.6 s). A shimmer sweeps the frame and the name engraves
 * letter by letter. Rare cards get silver motes; legendary ones golden light rays and a chime. A click, any key, or 8
 * seconds idle sends the card flying down to the hotbar and the screen ends. The game is not paused.
 *
 * <p><b>Showcase</b> (from the album): the same living card at the centre with none of the box effects; a click or any
 * key goes back to the album.
 *
 * <p>Uses {@code renderTransparentBackground}, never {@code renderBackground}: no menu blur. Client only.
 */
public class CardRevealScreen extends Screen {
    static final double LID_END = 0.3;
    static final double FROG_START = 0.3;
    static final double RISE_START = 0.6;
    static final double RISE_END = 1.4;
    static final double INPUT_AFTER = 0.8;
    static final double IDLE_SECONDS = 8.0;
    static final double FLY_SECONDS = 0.4;
    static final float MAX_TILT_DEG = 10.0F;

    private static final ResourceLocation FROG_SPRITE = ResourceLocation.fromNamespaceAndPath(CardAssets.NAMESPACE, "textures/item/chocolate_frog.png");
    private static final int BOX_TEX = 192;
    private static final int BOX_LID_H = 77;

    private final int cardId;
    private final WizardCards.Card card;
    private final boolean reveal;
    @Nullable
    private final Screen parent;
    private final long viewSeed;
    /**
     * A reveal runs its card on its own clock, started at the top of a schedule slot, so the wizard is always there when
     * the card rises (and stays for at least a minute). The album's large view keeps the world clock, to match its grid.
     */
    private final long timeOffset;
    private final CardCues cues = new CardCues();
    private final List<Spark> sparks = new ArrayList<>();
    private final RandomSource rnd = RandomSource.create();

    private long openNanos;
    private long lastNanos;
    private long exitNanos = -1L;
    private boolean burst;
    private boolean shimmerSound;
    private float yaw;
    private float pitch;
    private float moteBank;

    /** A reveal: the box, the lid, the frog, the card rising. */
    public CardRevealScreen(int cardId) {
        this(cardId, true, null);
    }

    /** The album's large view of a card: no box effects; closing returns to {@code parent}. */
    public CardRevealScreen(int cardId, Screen parent) {
        this(cardId, false, parent);
    }

    private CardRevealScreen(int cardId, boolean reveal, @Nullable Screen parent) {
        super(Component.empty());
        this.cardId = cardId;
        this.card = WizardCards.byId(cardId);
        this.reveal = reveal;
        this.parent = parent;
        this.viewSeed = viewSeedOf(cardId);
        this.timeOffset = reveal ? -CardArt.gameTime() - CardTimeline.shift(cardId, this.viewSeed) : 0L;
    }

    /** The view seed the album and the large view share, so a card is in the same moment in both. */
    public static long viewSeedOf(int cardId) {
        return cardId * 7919L;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Escape is "any key": handled in keyPressed so a reveal always flies to the hotbar
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No-op: render() draws the plain dim itself (no menu blur, per the screen rule).
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE && !this.reveal) {
            leave();
            return true;
        }
        dismiss();
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        dismiss();
        return true;
    }

    private double age() {
        return this.openNanos == 0L ? 0.0 : (Util.getNanos() - this.openNanos) / 1.0E9;
    }

    /** A click or key: a reveal flies away (once the card is up), the large view goes back. */
    private void dismiss() {
        if (!this.reveal) {
            leave();
            return;
        }
        if (this.exitNanos < 0L && age() >= INPUT_AFTER) {
            this.exitNanos = Util.getNanos();
        }
    }

    private void leave() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        if (this.card == null) {
            leave();
            return;
        }
        long now = Util.getNanos();
        if (this.openNanos == 0L) {
            this.openNanos = now;
        }
        float dt = this.lastNanos == 0L ? 0.0F : Math.min(0.1F, (now - this.lastNanos) / 1.0E9F);
        this.lastNanos = now;
        double age = (now - this.openNanos) / 1.0E9;

        // 8 s of idling flies it away too
        if (this.reveal && this.exitNanos < 0L && age > RISE_END + IDLE_SECONDS) {
            this.exitNanos = now;
        }
        float fly = this.exitNanos < 0L ? 0.0F : Mth.clamp((now - this.exitNanos) / 1.0E9F / (float) FLY_SECONDS, 0.0F, 1.0F);

        this.renderTransparentBackground(g);

        int w = this.width;
        int h = this.height;
        float cx = w / 2.0F;
        float cy = h / 2.0F;
        float full = Math.min(h * 0.66F / CardArt.CARD_H, w * 0.8F / CardArt.CARD_W);
        float cardH = CardArt.CARD_H * full;

        CardAssets.Assets assets = CardAssets.forKey(this.card.key());
        boolean legendary = this.card.rarity() == WizardCards.LEGENDARY;
        boolean rare = this.card.rarity() == WizardCards.RARE;

        // ---- where the card is
        float riseT = this.reveal ? (float) Mth.clamp((age - RISE_START) / (RISE_END - RISE_START), 0.0, 1.0) : 1.0F;
        float boxSize = Math.min(cardH * 0.55F, 170.0F);
        float boxX = cx - boxSize / 2.0F;
        float boxY = cy + cardH * 0.5F - boxSize * 0.78F;
        float mouthX = cx;
        float mouthY = boxY + boxSize * 0.42F;
        float centerX;
        float centerY;
        float scale;
        float alpha;
        if (this.reveal) {
            float pos = easeOutCubic(riseT);
            centerX = Mth.lerp(pos, mouthX, cx);
            centerY = Mth.lerp(pos, mouthY, cy);
            scale = full * (0.32F + 0.68F * easeOutBack(riseT));
            alpha = Math.min(1.0F, riseT * 3.0F);
        } else {
            float in = (float) Mth.clamp(age / 0.25, 0.0, 1.0);
            centerX = cx;
            centerY = cy;
            scale = full * (0.9F + 0.1F * easeOutCubic(in));
            alpha = in;
        }
        if (fly > 0.0F) {
            float e = fly * fly;
            float slot = this.minecraft.player == null ? 4 : this.minecraft.player.getInventory().selected;
            centerX = Mth.lerp(e, centerX, w / 2.0F - 90.0F + slot * 20.0F + 10.0F);
            centerY = Mth.lerp(e, centerY, h - 11.0F);
            scale *= 1.0F - 0.93F * e;
            alpha *= 1.0F - Mth.clamp((fly - 0.8F) * 5.0F, 0.0F, 1.0F);
        }

        // ---- effects behind the card
        double cardAge = this.reveal ? age - RISE_START : age;
        if (legendary && cardAge > 0.0) {
            float strength = (float) Math.min(1.0, cardAge / 0.6) * 0.34F * (1.0F - fly);
            CardFx.rays(g, centerX, centerY, cardH * 0.95F, (float) (age * 0.35), 14, 0xFFD36A, strength);
        }

        // ---- cues and sparks
        if (this.reveal) {
            if (!this.burst) {
                this.burst = true;
                CardCues.play(ModSounds.CARD_LID_POP.get(), 1.0F, 0.9F);
                burstFromBox(mouthX, mouthY, boxSize);
            }
        }
        if (!this.shimmerSound && age >= (this.reveal ? RISE_START : 0.05)) {
            this.shimmerSound = true;
            CardCues.play(ModSounds.CARD_SHIMMER.get(), 1.0F, 0.6F);
            if (legendary) {
                CardCues.play(ModSounds.CARD_LEGENDARY_CHIME.get(), 1.0F, 0.8F);
            }
        }
        if ((rare || legendary) && cardAge > 0.0 && fly == 0.0F) {
            this.moteBank += dt * (legendary ? 26.0F : 16.0F);
            while (this.moteBank >= 1.0F) {
                this.moteBank -= 1.0F;
                spawnMote(centerX, centerY, scale, legendary);
            }
        }
        stepSparks(dt);

        // ---- the card
        float tiltEase = this.reveal ? (float) Mth.clamp((age - 1.0) / 0.4, 0.0, 1.0) : (float) Mth.clamp(age / 0.3, 0.0, 1.0);
        float tx = Mth.clamp((mouseX - cx) / (CardArt.CARD_W * full / 2.0F), -1.0F, 1.0F) * MAX_TILT_DEG * tiltEase;
        float ty = Mth.clamp(-(mouseY - cy) / (cardH / 2.0F), -1.0F, 1.0F) * MAX_TILT_DEG * tiltEase;
        float follow = Math.min(1.0F, dt * 9.0F);
        this.yaw += (tx - this.yaw) * follow;
        this.pitch += (ty - this.pitch) * follow;
        CardArt.Tilt tilt = new CardArt.Tilt(this.yaw, this.pitch);

        boolean cardUp = !this.reveal || age >= RISE_START;
        if (cardUp) {
            float nameFrac;
            if (this.reveal) {
                int len = CardArt.nameOf(this.card).length();
                nameFrac = (float) Mth.clamp((age - 1.0) / Math.min(1.4, 0.07 * Math.max(1, len)), 0.0, 1.0);
            } else {
                nameFrac = 1.0F;
            }
            float x = centerX - CardArt.CARD_W * scale / 2.0F;
            float y = centerY - CardArt.CARD_H * scale / 2.0F;
            CardArt.drawCard(g, this.cardId, this.viewSeed, x, y, scale, 0.0F, nameFrac, tilt, alpha, this.timeOffset);
            double shimmerStart = this.reveal ? RISE_START + 0.15 : 0.12;
            CardArt.drawShimmer(g, x, y, scale, tilt, (float) ((age - shimmerStart) / 0.9), 0.55F * alpha);

            // not the render delta Screen.render is handed: the game's own partial tick
            CardTimeline.State st = CardArt.stateOf(this.cardId, this.viewSeed, CardArt.partialTick(), this.timeOffset);
            this.cues.update(st, this.card, assets, 0.5F);
        }

        // ---- the box, in front of the card so it climbs out of it
        if (this.reveal && age < 1.6 && CardAssets.has(CardAssets.common("box"))) {
            drawBox(g, age, boxX, boxY, boxSize);
            if (age >= FROG_START && age < 0.95) {
                drawFrog(g, (float) ((age - FROG_START) / 0.65), mouthX, mouthY, cardH, boxSize);
            }
        }

        drawSparks(g);

        // ---- the hint
        if ((!this.reveal || age > RISE_END + 0.6) && fly == 0.0F) {
            Component hint = Component.translatable(this.reveal ? "gui.hogwartsmagic.card_reveal.hint" : "gui.hogwartsmagic.card_reveal.back");
            float a = (float) Mth.clamp((this.reveal ? age - (RISE_END + 0.6) : age - 0.8) / 0.5, 0.0, 1.0) * 0.8F;
            g.drawCenteredString(this.font, hint, w / 2, h - 46, CardFx.withAlpha(0xD8D0F0, a));
        }

        if (this.reveal && fly >= 1.0F) {
            this.minecraft.setScreen(null);
        }
    }

    // ------------------------------------------------------------------ box and frog

    private void drawBox(GuiGraphics g, double age, float boxX, float boxY, float size) {
        float fade = 1.0F - (float) Mth.clamp((age - 1.1) / 0.45, 0.0, 1.0);
        if (fade <= 0.0F) {
            return;
        }
        ResourceLocation box = CardAssets.common("box");
        float k = size / BOX_TEX;
        float lidOpen = easeOutCubic((float) Mth.clamp(age / LID_END, 0.0, 1.0));
        float lidAlpha = 1.0F - (float) Mth.clamp((age - 0.3) / 0.4, 0.0, 1.0);
        float sink = (float) Mth.clamp((age - 1.0) / 0.6, 0.0, 1.0) * size * 0.25F;
        // body
        g.pose().pushPose();
        g.pose().translate(boxX, boxY + BOX_LID_H * k + sink, 0.0F);
        g.pose().scale(k, k, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, fade);
        g.blit(box, 0, 0, 0, BOX_LID_H, BOX_TEX, BOX_TEX - BOX_LID_H, BOX_TEX, BOX_TEX);
        g.pose().popPose();
        // lid: pops up and tumbles away, fading
        if (lidAlpha > 0.0F) {
            g.pose().pushPose();
            g.pose().translate(boxX + BOX_TEX * k / 2.0F, boxY + BOX_LID_H * k + sink, 0.0F);
            g.pose().translate(0.0F, -lidOpen * size * 0.55F, 0.0F);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-lidOpen * 26.0F));
            g.pose().scale(k, k, 1.0F);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, fade * lidAlpha);
            g.blit(box, -BOX_TEX / 2, -BOX_LID_H, 0, 0, 0, BOX_TEX, BOX_LID_H, BOX_TEX, BOX_TEX);
            g.pose().popPose();
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    /** The frog leaps out of the box and away up and to the right, spinning. */
    private void drawFrog(GuiGraphics g, float t, float mouthX, float mouthY, float cardH, float boxSize) {
        float x = mouthX + t * cardH * 0.55F;
        float y = mouthY - (float) Math.sin(Math.PI * Math.min(1.0F, t * 0.8F)) * cardH * 0.45F - t * cardH * 0.1F;
        float size = boxSize * 0.3F;
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(t * 520.0F));
        g.pose().scale(size / 16.0F, size / 16.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F - t * t * t);
        g.blit(FROG_SPRITE, -8, -8, 0, 0, 0, 16, 16, 16, 16);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ sparks

    /** A GUI particle: a small square with a life, drifting under gravity. */
    private static final class Spark {
        float x;
        float y;
        float vx;
        float vy;
        float gravity;
        float life;
        float max;
        float size;
        int rgb;
    }

    private void burstFromBox(float x, float y, float boxSize) {
        int[] palette = {0xFFD36A, 0xFFE9A8, 0xFFF6D8, 0x7A4425, 0x5A301A};
        for (int i = 0; i < 44; i++) {
            Spark s = new Spark();
            s.x = x + (this.rnd.nextFloat() - 0.5F) * boxSize * 0.5F;
            s.y = y - boxSize * 0.1F;
            float ang = (float) (-Math.PI / 2.0 + (this.rnd.nextFloat() - 0.5F) * 2.2F);
            float speed = 60.0F + this.rnd.nextFloat() * 170.0F;
            s.vx = (float) Math.cos(ang) * speed;
            s.vy = (float) Math.sin(ang) * speed;
            s.gravity = 260.0F;
            s.max = s.life = 0.55F + this.rnd.nextFloat() * 0.6F;
            s.size = 1.0F + this.rnd.nextInt(3);
            s.rgb = palette[this.rnd.nextInt(palette.length)];
            this.sparks.add(s);
        }
    }

    private void spawnMote(float cx, float cy, float scale, boolean gold) {
        Spark s = new Spark();
        float w = CardArt.CARD_W * scale;
        float h = CardArt.CARD_H * scale;
        // around the card's edge, drifting up
        float ang = this.rnd.nextFloat() * Mth.TWO_PI;
        s.x = cx + (float) Math.cos(ang) * w * 0.55F;
        s.y = cy + (float) Math.sin(ang) * h * 0.55F;
        s.vx = (this.rnd.nextFloat() - 0.5F) * 14.0F;
        s.vy = -8.0F - this.rnd.nextFloat() * 22.0F;
        s.gravity = 0.0F;
        s.max = s.life = 0.9F + this.rnd.nextFloat() * 1.0F;
        s.size = this.rnd.nextInt(4) == 0 ? 2.0F : 1.0F;
        s.rgb = gold ? (this.rnd.nextBoolean() ? 0xFFD36A : 0xFFF2B8) : (this.rnd.nextBoolean() ? 0xDCE6F5 : 0xFFFFFF);
        this.sparks.add(s);
    }

    private void stepSparks(float dt) {
        for (int i = this.sparks.size() - 1; i >= 0; i--) {
            Spark s = this.sparks.get(i);
            s.life -= dt;
            if (s.life <= 0.0F) {
                this.sparks.remove(i);
                continue;
            }
            s.vy += s.gravity * dt;
            s.x += s.vx * dt;
            s.y += s.vy * dt;
        }
    }

    private void drawSparks(GuiGraphics g) {
        for (Spark s : this.sparks) {
            float f = s.life / s.max;
            float twinkle = 0.65F + 0.35F * (float) Math.sin((s.x + s.y) * 0.35F + s.life * 18.0F);
            int argb = CardFx.withAlpha(s.rgb, Math.min(1.0F, f * 1.6F) * twinkle);
            int px = Math.round(s.x);
            int py = Math.round(s.y);
            int sz = Math.max(1, Math.round(s.size));
            g.fill(px, py, px + sz, py + sz, argb);
        }
    }

    // ------------------------------------------------------------------ easing

    static float easeOutCubic(float t) {
        float u = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
        return 1.0F - u * u * u;
    }

    /** Overshoots a little before settling: the card pops up to the centre. */
    static float easeOutBack(float t) {
        float c1 = 1.2F;
        float c3 = c1 + 1.0F;
        float u = Mth.clamp(t, 0.0F, 1.0F) - 1.0F;
        return 1.0F + c3 * u * u * u + c1 * u * u;
    }
}
