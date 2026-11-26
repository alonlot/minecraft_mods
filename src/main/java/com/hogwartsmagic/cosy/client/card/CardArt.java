package com.hogwartsmagic.cosy.client.card;

import com.hogwartsmagic.cosy.CardTimeline;
import com.hogwartsmagic.cosy.WizardCards;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Draws a living Famous Wizard Card: the one renderer the reveal screen, the hand, item frames and the album share.
 *
 * <p>Everything is laid out in <b>card pixels</b>: a 320 x 448 card (y down) whose portrait window is the 256 x 384
 * rectangle at ({@link #WIN_X}, {@link #WIN_Y}). The layers, bottom to top: the empty plate, the figure (offset by the
 * walk, bobbing, breathing), the eye overlays (only while the figure sits still), the exit clip frame (legendaries),
 * the gilded frame, the name plate. The figure and overlays are clipped to the window geometrically (their vertices
 * and UVs are cut), so there is no scissor and the same code draws in the GUI and in the world.
 *
 * <p>What the portrait does at any moment is {@link CardTimeline}, a pure function of (game time, card, view seed).
 *
 * <p>Client only. No enum switches: this is on the render path.
 */
public final class CardArt {
    public static final int CARD_W = 320;
    public static final int CARD_H = 448;
    public static final int WIN_X = 32;
    public static final int WIN_Y = 22;
    public static final int WIN_W = 256;
    public static final int WIN_H = 384;
    /** Where the card name is centred, in card pixels. */
    public static final float NAME_X = 160.0F;
    public static final float NAME_Y = 419.0F;
    private static final float NAME_MAX_W = 226.0F;

    private static final int NAME_INK = 0xFF3A2A18;
    private static final int NAME_HIGHLIGHT = 0xFFF8EBC8;
    /** Each layer sits this much closer to the viewer than the one below, in card pixels (world path only). */
    private static final float LAYER_STEP = 0.12F;

    /** A perspective tilt of the card about its centre, for the GUI (degrees). {@link #NONE} is flat. */
    public record Tilt(float yawDeg, float pitchDeg) {
        public static final Tilt NONE = new Tilt(0.0F, 0.0F);

        boolean flat() {
            return this.yawDeg == 0.0F && this.pitchDeg == 0.0F;
        }
    }

    private CardArt() {
    }

    // ------------------------------------------------------------------ public entry points

    /**
     * Draws the living card into a GUI: its top-left corner at ({@code x}, {@code y}) in GUI pixels, {@code scale}
     * GUI pixels per card pixel (so the card is 320 * scale wide), fully named.
     */
    public static void drawCard(GuiGraphics g, int cardId, long viewSeed, float x, float y, float scale, float partial) {
        drawCard(g, cardId, viewSeed, x, y, scale, partial, 1.0F, Tilt.NONE, 1.0F);
    }

    /**
     * The same with the reveal's extras: {@code nameFraction} (0..1) is how much of the name is engraved so far,
     * {@code tilt} turns the card in perspective, {@code alpha} fades the whole card.
     */
    public static void drawCard(GuiGraphics g, int cardId, long viewSeed, float x, float y, float scale, float partial,
                                float nameFraction, Tilt tilt, float alpha) {
        drawCard(g, cardId, viewSeed, x, y, scale, partial, nameFraction, tilt, alpha, 0L);
    }

    /** The same on a schedule shifted by {@code timeOffset} ticks from the world's clock (a reveal's own clock). */
    public static void drawCard(GuiGraphics g, int cardId, long viewSeed, float x, float y, float scale, float partial,
                                float nameFraction, Tilt tilt, float alpha, long timeOffset) {
        WizardCards.Card card = WizardCards.byId(cardId);
        if (card == null) {
            return;
        }
        // The float a Screen is handed is the frame delta, not the partial tick: always read the real one.
        float tick = partialTick();
        boolean mini = scale < MINI_BELOW && CardMini.ready(card.key());
        g.flush(); // anything batched so far must land before this raw drawing
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        GuiSink sink = new GuiSink(g, tilt, alpha, mini);
        render(sink, card, mini ? stateOfNoClip(card, viewSeed, tick, timeOffset) : stateOf(card, viewSeed, tick, timeOffset),
                nameFraction,
                scale >= 0.2F, mini, tick);
        g.flush();
        g.pose().popPose();
        RenderSystem.disableBlend();
    }

    /** Below this scale (GUI px per card px) a card is drawn from the pre-shrunk copies, so it does not sparkle. */
    public static final float MINI_BELOW = 0.35F;

    /** Album support: is this card's pre-shrunk copy built yet? */
    public static boolean miniReady(String key) {
        return CardMini.ready(key);
    }

    /** Album support: build this card's pre-shrunk copy (a few PNG decodes: spread over frames). */
    public static void prepareMini(String key) {
        CardMini.prepare(key);
    }

    /** Resource reload or logout: forget cached lookups, names, filters and the small copies. */
    public static void releaseAll() {
        forget();
        CardMini.clear();
    }

    /** The real partial tick of the game (the float screens get is only the frame delta). */
    public static float partialTick() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    /**
     * Draws the living card into the world: a 1 wide, 1.4 tall quad in the pose's local space, x to the right and y
     * <b>down</b> from the top-left corner at the origin, facing the way a vanilla map does in the same transform
     * chain (so the hand and item-frame renderers can reuse vanilla's own map setup).
     */
    public static void drawCard(PoseStack pose, MultiBufferSource buffers, int light, int cardId, long viewSeed, float partial) {
        WizardCards.Card card = WizardCards.byId(cardId);
        if (card == null) {
            return;
        }
        WorldSink sink = new WorldSink(pose, buffers, light);
        render(sink, card, stateOf(card, viewSeed, partial, 0L), 1.0F, true, false, partial);
    }

    /**
     * The reveal's shimmer: a soft diagonal band of light that sweeps across the card as {@code progress} runs 0 to 1
     * (nothing is drawn outside that range). Additive, clipped to the card, and tilted with it. GUI only.
     */
    public static void drawShimmer(GuiGraphics g, float x, float y, float scale, Tilt tilt, float progress, float strength) {
        if (progress <= 0.0F || progress >= 1.0F || strength <= 0.0F) {
            return;
        }
        final float slope = 0.55F;
        final float halfWidth = 54.0F;
        float c0 = -slope * CARD_H - halfWidth;
        float c1 = CARD_W + halfWidth;
        float c = c0 + (c1 - c0) * progress;
        g.flush();
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        GuiSink sink = new GuiSink(g, tilt, 1.0F, false);
        shimmerStrip(g, sink, c - halfWidth, c, 0.0F, strength, slope);
        shimmerStrip(g, sink, c, c + halfWidth, strength, 0.0F, slope);
        g.pose().popPose();
    }

    /** One half of the band: the parallelogram between the lines x + slope*y = cA and = cB, clipped to the card. */
    private static void shimmerStrip(GuiGraphics g, GuiSink sink, float cA, float cB, float aA, float aB, float slope) {
        final float minX = 5.0F;
        final float maxX = CARD_W - 5.0F;
        final float minY = 5.0F;
        final float maxY = CARD_H - 5.0F;
        // corners: top A, top B, bottom B, bottom A  as (x, y, alpha)
        float[][] poly = {
                {cA - slope * minY, minY, aA}, {cB - slope * minY, minY, aB},
                {cB - slope * maxY, maxY, aB}, {cA - slope * maxY, maxY, aA}};
        poly = clipX(poly, minX, true);
        poly = clipX(poly, maxX, false);
        if (poly.length < 3) {
            return;
        }
        float[] xs = new float[poly.length];
        float[] ys = new float[poly.length];
        int[] cols = new int[poly.length];
        float[] p = new float[2];
        for (int i = 0; i < poly.length; i++) {
            sink.project(poly[i][0], poly[i][1], p);
            xs[i] = p[0];
            ys[i] = p[1];
            cols[i] = CardFx.withAlpha(0xFFF2C8, poly[i][2]);
        }
        CardFx.fan(g, xs, ys, cols, true);
    }

    /** Sutherland-Hodgman against one vertical edge: keeps x >= edge (or x <= edge), interpolating alpha. */
    private static float[][] clipX(float[][] in, float edge, boolean keepGreater) {
        java.util.ArrayList<float[]> out = new java.util.ArrayList<>();
        for (int i = 0; i < in.length; i++) {
            float[] a = in[i];
            float[] b = in[(i + 1) % in.length];
            boolean aIn = keepGreater ? a[0] >= edge : a[0] <= edge;
            boolean bIn = keepGreater ? b[0] >= edge : b[0] <= edge;
            if (aIn) {
                out.add(a);
            }
            if (aIn != bIn) {
                float k = (edge - a[0]) / (b[0] - a[0]);
                out.add(new float[]{edge, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k});
            }
        }
        return out.toArray(new float[0][]);
    }

    /** What the card's portrait is doing right now (for sound cues). */
    public static CardTimeline.State stateOf(int cardId, long viewSeed, float partial) {
        return stateOf(cardId, viewSeed, partial, 0L);
    }

    /** The same on a schedule shifted by {@code timeOffset} ticks. */
    public static CardTimeline.State stateOf(int cardId, long viewSeed, float partial, long timeOffset) {
        WizardCards.Card card = WizardCards.byId(cardId);
        return card == null ? new CardTimeline.State(true, 0.0F, false, 0, -1) : stateOf(card, viewSeed, partial, timeOffset);
    }

    /** The same without the exit clip: small cards have no clip frames, so a legendary just walks out. */
    private static CardTimeline.State stateOfNoClip(WizardCards.Card card, long viewSeed, float partial, long timeOffset) {
        return CardTimeline.at(gameTime() + timeOffset, partial, card.id(), viewSeed, false, 0);
    }

    private static CardTimeline.State stateOf(WizardCards.Card card, long viewSeed, float partial, long timeOffset) {
        CardAssets.Assets a = CardAssets.forKey(card.key());
        boolean legendaryClip = a.clipFrames() > 0;
        return CardTimeline.at(gameTime() + timeOffset, partial, card.id(), viewSeed, legendaryClip, a.clipFrames());
    }

    /** The world's clock (or the GUI's, with no world loaded), in ticks. */
    public static long gameTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? mc.gui.getGuiTicks() : mc.level.getGameTime();
    }

    // ------------------------------------------------------------------ the layers

    /** One quad of a layer, in card pixels, with the texture region it shows. */
    interface Sink {
        /** A quad whose top edge is shifted sideways by sh0 and bottom edge by sh1 (the sway). */
        void quad(ResourceLocation tex, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
                  float sh0, float sh1);

        default void quad(ResourceLocation tex, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
            quad(tex, x0, y0, x1, y1, u0, v0, u1, v1, 0.0F, 0.0F);
        }

        /** The name plate text, centred on (cx, cy) card pixels, in card-pixel units of height scale. */
        void text(String text, float left, float cy, float scale, int color);

        float textWidth(String text);

        /** Moves to the next layer (the world sink lifts it a hair off the one below). */
        void nextLayer();
    }

    private static void render(Sink sink, WizardCards.Card card, CardTimeline.State st, float nameFraction,
                               boolean withName, boolean mini, float partial) {
        CardAssets.Assets a = CardAssets.forKey(card.key());
        long gt = gameTime();
        // phases are taken modulo the period in long first, so they stay exact in a world that has run for weeks
        float breathPhase = phase(gt, partial, 76);
        float walkPhase = phase(gt, partial, 7);
        float swayA = phase(gt, partial, 97);
        float swayB = phase(gt, partial, 131);

        // 1. the empty plate
        sink.quad(tex(a.empty(), mini), WIN_X, WIN_Y, WIN_X + WIN_W, WIN_Y + WIN_H, 0, 0, 1, 1);
        sink.nextLayer();

        if (st.clipFrame() >= 0 && a.clipFrames() > 0 && !mini) {
            // 4. the clip frame stands in for the figure: it is the whole painting at that moment
            ResourceLocation frame = CardVideo.use(card.key(), a.clipFrames(), st.clipFrame(), System.nanoTime());
            if (CardAssets.has(frame)) {
                sink.quad(frame, WIN_X, WIN_Y, WIN_X + WIN_W, WIN_Y + WIN_H, 0, 0, 1, 1);
            }
            sink.nextLayer();
        } else if (st.present()) {
            // 2. the figure: slid by the walk, bobbing as it walks, breathing at 1% on Y, rooted at the window bottom,
            //    swaying a hair (a shear about the bottom: two slow sines, like the puppet mesh of wall portraits)
            boolean walking = st.figureOffsetX() != 0.0F;
            float dx = st.figureOffsetX() * WIN_W;
            float bob = walking ? (float) Math.sin(walkPhase * Mth.TWO_PI) * 2.0F : 0.0F;
            float breath = 1.0F + 0.01F * (0.5F + 0.5F * (float) Math.sin(breathPhase * Mth.TWO_PI));
            float sway = SWAY_PX * (0.6F * (float) Math.sin(swayA * Mth.TWO_PI)
                    + 0.4F * (float) Math.sin(swayB * Mth.TWO_PI + 1.3F));
            float bottom = WIN_Y + WIN_H + bob;
            float top = bottom - WIN_H * breath;
            clippedQuad(sink, tex(a.figure(), mini), WIN_X + dx, top, WIN_X + dx + WIN_W, bottom, 0, 0, 1, 1, sway);
            sink.nextLayer();

            // 3. the eyes, only while the figure sits still (they ride on the figure, sway included)
            if (st.idle()) {
                if (st.blink() && CardAssets.has(a.blink())) {
                    overlay(sink, tex(a.blink(), mini), a.meta().eyes(), bottom, breath, sway);
                } else if (st.look() != 0) {
                    ResourceLocation look = st.look() < 0 ? a.lookL() : a.lookR();
                    if (CardAssets.has(look)) {
                        overlay(sink, tex(look, mini), a.meta().face(), bottom, breath, sway);
                    }
                }
                sink.nextLayer();
            }
        }

        // 5. the frame
        ResourceLocation frameTex = CardAssets.frame(card.rarity());
        if (CardAssets.has(frameTex)) {
            sink.quad(tex(frameTex, mini), 0, 0, CARD_W, CARD_H, 0, 0, 1, 1);
        }
        sink.nextLayer();

        // 6. the name plate
        if (withName && nameFraction > 0.0F) {
            String name = nameOf(card);
            float scale = Math.min(2.0F, NAME_MAX_W / Math.max(1.0F, sink.textWidth(name)));
            float full = sink.textWidth(name) * scale;
            int shown = Math.min(name.length(), (int) Math.ceil(name.length() * Math.min(1.0F, nameFraction)));
            String part = name.substring(0, shown);
            float left = NAME_X - full / 2.0F;
            float cy = NAME_Y - 4.5F * scale;
            sink.text(part, left + 0.7F, cy + 0.7F, scale, NAME_HIGHLIGHT);
            sink.nextLayer();
            sink.text(part, left, cy, scale, NAME_INK);
        }
    }

    /** The idle sway at the top of the window, in card px at its largest (256 px wide portrait): about 1.5 px. */
    private static final float SWAY_PX = 1.5F;

    /** Fraction (0..1) through a cycle of the given period in ticks, exact however large the game time. */
    private static float phase(long gameTime, float partial, int period) {
        return (Math.floorMod(gameTime, (long) period) + partial) / period;
    }

    private static final java.util.Map<String, String> NAMES = new java.util.HashMap<>();

    /** The translated card name, cached (cleared on resource reload, which a language change triggers). */
    static String nameOf(WizardCards.Card card) {
        return NAMES.computeIfAbsent(card.key(),
                k -> Component.translatable("card.hogwartsmagic." + k + ".name").getString());
    }

    static void forget() {
        NAMES.clear();
        SMOOTHED.clear();
    }

    private static ResourceLocation tex(ResourceLocation full, boolean mini) {
        return mini ? CardMini.resolve(full) : full;
    }

    /** An eye overlay: only the box region of the texture is drawn, moved and scaled with the figure. */
    private static void overlay(Sink sink, ResourceLocation tex, CardAssets.Box box, float figureBottom, float breath, float sway) {
        float l = Math.max(0.0F, box.left());
        float r = Math.min(WIN_W, box.right());
        float tp = Math.max(0.0F, box.top());
        float bt = Math.min(WIN_H, box.bottom());
        if (r <= l || bt <= tp) {
            return;
        }
        // texture row y sits at figureBottom - (WIN_H - y) * breath, like the figure itself
        float y0 = figureBottom - (WIN_H - tp) * breath;
        float y1 = figureBottom - (WIN_H - bt) * breath;
        sink.quad(tex, WIN_X + l, y0, WIN_X + r, y1, l / WIN_W, tp / WIN_H, r / WIN_W, bt / WIN_H,
                shear(sway, y0), shear(sway, y1));
    }

    /** A portrait-sized quad cut to the window: the part outside is dropped and the UVs follow. */
    private static void clippedQuad(Sink sink, ResourceLocation tex, float x0, float y0, float x1, float y1,
                                    float u0, float v0, float u1, float v1, float sway) {
        float wx0 = WIN_X;
        float wy0 = WIN_Y;
        float wx1 = WIN_X + WIN_W;
        float wy1 = WIN_Y + WIN_H;
        float w = x1 - x0;
        float h = y1 - y0;
        if (w <= 0.0F || h <= 0.0F) {
            return;
        }
        float cx0 = Math.max(x0, wx0);
        float cx1 = Math.min(x1, wx1);
        float cy0 = Math.max(y0, wy0);
        float cy1 = Math.min(y1, wy1);
        if (cx1 <= cx0 || cy1 <= cy0) {
            return;
        }
        float nu0 = u0 + (u1 - u0) * ((cx0 - x0) / w);
        float nu1 = u0 + (u1 - u0) * ((cx1 - x0) / w);
        float nv0 = v0 + (v1 - v0) * ((cy0 - y0) / h);
        float nv1 = v0 + (v1 - v0) * ((cy1 - y0) / h);
        sink.quad(tex, cx0, cy0, cx1, cy1, nu0, nv0, nu1, nv1, shear(sway, cy0), shear(sway, cy1));
    }

    /** Sideways shift at card-pixel row y: the sway swings the top of the window, the bottom stays put. */
    private static float shear(float sway, float y) {
        return sway * (WIN_Y + WIN_H - y) / WIN_H;
    }

    private static final java.util.Set<ResourceLocation> SMOOTHED = new java.util.HashSet<>();

    /** Linear scaling for card textures in the GUI, set once per texture (the world path uses a blurred render type). */
    static void smooth(ResourceLocation tex) {
        if (SMOOTHED.add(tex)) {
            AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(tex);
            t.setFilter(true, false);
        }
    }

    // ------------------------------------------------------------------ the GUI sink

    /** Draws straight into the GUI with a perspective tilt about the card centre. */
    static final class GuiSink implements Sink {
        private final GuiGraphics g;
        private final Tilt tilt;
        private final boolean mini;
        private final float[] p = new float[2];
        private final int alpha;
        private final float cosY;
        private final float sinY;
        private final float cosX;
        private final float sinX;
        private static final float DISTANCE = 1100.0F;

        GuiSink(GuiGraphics g, Tilt tilt, float alpha, boolean mini) {
            this.g = g;
            this.tilt = tilt;
            this.mini = mini;
            this.alpha = Math.max(0, Math.min(255, (int) (alpha * 255.0F)));
            double yaw = Math.toRadians(tilt.yawDeg());
            double pitch = Math.toRadians(tilt.pitchDeg());
            this.cosY = (float) Math.cos(yaw);
            this.sinY = (float) Math.sin(yaw);
            this.cosX = (float) Math.cos(pitch);
            this.sinX = (float) Math.sin(pitch);
        }

        /** Card pixel to tilted card pixel: rotate about the centre, then perspective. */
        void project(float x, float y, float[] out) {
            if (this.tilt.flat()) {
                out[0] = x;
                out[1] = y;
                return;
            }
            float px = x - CARD_W / 2.0F;
            float py = y - CARD_H / 2.0F;
            float x1 = px * this.cosY;
            float z1 = -px * this.sinY;
            float y2 = py * this.cosX - z1 * this.sinX;
            float z2 = py * this.sinX + z1 * this.cosX;
            float k = DISTANCE / (DISTANCE - z2);
            out[0] = CARD_W / 2.0F + x1 * k;
            out[1] = CARD_H / 2.0F + y2 * k;
        }

        @Override
        public void quad(ResourceLocation tex, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
                         float sh0, float sh1) {
            smooth(tex);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, tex);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            Matrix4f m = this.g.pose().last().pose();
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            int color = (this.alpha << 24) | 0xFFFFFF;
            float[] p = this.p;
            project(x0 + sh0, y0, p);
            b.addVertex(m, p[0], p[1], 0.0F).setUv(u0, v0).setColor(color);
            project(x0 + sh1, y1, p);
            b.addVertex(m, p[0], p[1], 0.0F).setUv(u0, v1).setColor(color);
            project(x1 + sh1, y1, p);
            b.addVertex(m, p[0], p[1], 0.0F).setUv(u1, v1).setColor(color);
            project(x1 + sh0, y0, p);
            b.addVertex(m, p[0], p[1], 0.0F).setUv(u1, v0).setColor(color);
            BufferUploader.drawWithShader(b.buildOrThrow());
            RenderSystem.disableBlend();
        }

        @Override
        public void text(String text, float left, float cy, float scale, int color) {
            if (text.isEmpty()) {
                return;
            }
            this.g.pose().pushPose();
            this.g.pose().translate(left, cy, 0.0F);
            this.g.pose().scale(scale, scale, 1.0F);
            int a = (int) (((color >>> 24) & 0xFF) * (this.alpha / 255.0F));
            this.g.drawString(Minecraft.getInstance().font, text, 0, 0, (a << 24) | (color & 0xFFFFFF), false);
            this.g.flush();
            this.g.pose().popPose();
        }

        @Override
        public float textWidth(String text) {
            return Minecraft.getInstance().font.width(text);
        }

        @Override
        public void nextLayer() {
        }
    }

    // ------------------------------------------------------------------ the world sink

    /** Draws into the world with {@link RenderType#text}: lit by the lightmap, translucent, in vanilla map winding. */
    static final class WorldSink implements Sink {
        private final PoseStack pose;
        private final MultiBufferSource buffers;
        private final int light;
        private int layer;

        WorldSink(PoseStack pose, MultiBufferSource buffers, int light) {
            this.pose = pose;
            this.buffers = buffers;
            this.light = light;
        }

        private float z() {
            return -this.layer * LAYER_STEP / CARD_W;
        }

        @Override
        public void quad(ResourceLocation tex, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
                         float sh0, float sh1) {
            VertexConsumer vc = this.buffers.getBuffer(CardRenderTypes.SMOOTH_TEXT.apply(tex));
            Matrix4f m = this.pose.last().pose();
            float s = 1.0F / CARD_W;
            float z = z();
            int white = 0xFFFFFFFF;
            // vanilla map order: bottom-left, bottom-right, top-right, top-left
            vc.addVertex(m, (x0 + sh1) * s, y1 * s, z).setColor(white).setUv(u0, v1).setLight(this.light);
            vc.addVertex(m, (x1 + sh1) * s, y1 * s, z).setColor(white).setUv(u1, v1).setLight(this.light);
            vc.addVertex(m, (x1 + sh0) * s, y0 * s, z).setColor(white).setUv(u1, v0).setLight(this.light);
            vc.addVertex(m, (x0 + sh0) * s, y0 * s, z).setColor(white).setUv(u0, v0).setLight(this.light);
        }

        @Override
        public void text(String text, float left, float cy, float scale, int color) {
            if (text.isEmpty()) {
                return;
            }
            Font font = Minecraft.getInstance().font;
            Matrix4f m = new Matrix4f(this.pose.last().pose());
            float s = 1.0F / CARD_W;
            m.translate(left * s, cy * s, z());
            m.scale(scale * s, scale * s, 1.0F);
            font.drawInBatch(text, 0.0F, 0.0F, color, false, m, this.buffers, Font.DisplayMode.NORMAL, 0, this.light);
        }

        @Override
        public float textWidth(String text) {
            return Minecraft.getInstance().font.width(text);
        }

        @Override
        public void nextLayer() {
            this.layer++;
        }
    }
}
