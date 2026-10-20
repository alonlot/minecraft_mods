package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.WordText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * What the people in a memory say, drawn on the screen and never written to the real chat log (nothing here
 * touches {@code ChatComponent}, the server, or any other player). Actor lines ({@code by}) stack at the bottom
 * left as chat-styled {@code <name> text} lines and fade after eight seconds; narration is centred low in italic
 * and fades in and out across its {@code duration}. Actor lines are hidden under shut eyelids and a chain's fade to
 * black; narration is meant to be read over black and is not. Everything is a function of the shared scene clock, so a
 * late joiner sees the recent lines and two viewers see the same words.
 */
public final class MemoryWords {
    private static final int SILVER = 0xC9D6E3;
    /** An actor line stays this long, and fades over its last {@link #LINE_FADE}. */
    static final int LINE_TICKS = 160;
    static final int LINE_FADE = 20;
    private static final int NARRATION_FADE = 10;
    private static final int DEFAULT_NARRATION_TICKS = 60;
    private static final int SUBTITLE_FADE = 6;
    private static final int GOLD = 0xD8B65A;
    /** Subtitle baseline above the lower edge: clear of the hotbar. */
    private static final int SUBTITLE_BOTTOM = 40;
    private static final int MAX_LINES = 8;
    /** Below this an actor line is not drawn at all (see {@link #argb}). */
    private static final float MIN_ALPHA = 0.02f;
    private static final int LEFT = 4;
    /** Bottom of the actor lines above the screen's lower edge: clear of the chat's own bottom line. */
    private static final int BOTTOM_MARGIN = 62;

    private MemoryWords() {
    }

    public static void render(GuiGraphics g, float partial) {
        if (PensieveClientState.phase() != PensieveClientState.PLAY) return;
        SceneDef scene = PensieveClientState.scene();
        if (scene == null || scene.words().isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        float now = PensieveClientState.sceneTime(partial);
        int w = g.guiWidth();
        int h = g.guiHeight();

        String viewer = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getGameProfile().getName() : null;
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<Float> alphas = new ArrayList<>();
        float spoken = PensieveTimeline.spokenWordsAlpha(PensieveClientState.lidsClosed(partial),
                PensieveClientState.chainEndDarkness(partial));
        List<Subtitle> subtitles = new ArrayList<>();
        for (SceneDef.WordDef word : scene.words()) {
            float age = now - word.t();
            if (age < 0) continue;
            if (word.subtitle()) {
                int life = word.duration() > 0 ? word.duration() : DEFAULT_NARRATION_TICKS + 20;
                if (age >= life) continue;
                float a = Mth.clamp(Math.min(age / SUBTITLE_FADE, (life - age) / SUBTITLE_FADE), 0f, 1f) * spoken;
                if (a < MIN_ALPHA) continue;
                subtitles.add(new Subtitle(word, a));
            } else if (word.narration()) {
                int life = word.duration() > 0 ? word.duration() : DEFAULT_NARRATION_TICKS;
                if (age >= life) continue;
                float a = Mth.clamp(Math.min(age / NARRATION_FADE, (life - age) / NARRATION_FADE), 0f, 1f);
                narration(g, font, WordText.fill(word.text(), viewer), a, w, h, word.size());
            } else if (age < LINE_TICKS) {
                float a = Mth.clamp((LINE_TICKS - age) / LINE_FADE, 0f, 1f) * spoken;
                if (a < MIN_ALPHA) continue;
                for (FormattedCharSequence l : font.split(Component.literal(speaker(scene, word, viewer)), Math.max(80, Math.min(w / 2, 320)))) {
                    lines.add(l);
                    alphas.add(a);
                }
            }
        }
        subtitles(g, font, subtitles, viewer, w, h);
        // Newest at the bottom; only the last few lines fit.
        int from = Math.max(0, lines.size() - MAX_LINES);
        int y = h - BOTTOM_MARGIN - 9 * (lines.size() - from);
        for (int i = from; i < lines.size(); i++) {
            float a = alphas.get(i);
            int width = font.width(lines.get(i));
            g.fill(LEFT - 2, y - 1, LEFT + width + 2, y + 9, argb(a * 0.4f, 0x000000));
            g.drawString(font, lines.get(i), LEFT, y, argb(a, SILVER), true);
            y += 9;
        }
    }

    private record Subtitle(SceneDef.WordDef word, float alpha) {
    }

    /** Centred film subtitles above the hotbar; the newest at the bottom, older ones stacked above it. */
    private static void subtitles(GuiGraphics g, Font font, List<Subtitle> subs, String viewer, int w, int h) {
        int maxWidth = Math.max(80, (int) (w * 0.7f));
        int bottom = h - SUBTITLE_BOTTOM;
        for (int s = subs.size() - 1; s >= 0; s--) {
            Subtitle sub = subs.get(s);
            String speaker = sub.word().speaker();
            MutableComponent c = Component.empty();
            if (speaker != null && !speaker.isBlank()) {
                c.append(Component.literal(WordText.fill(speaker, viewer) + ": ").withStyle(Style.EMPTY.withColor(GOLD)));
            }
            c.append(Component.literal(WordText.fill(sub.word().text(), viewer)));
            List<FormattedCharSequence> lines = font.split(c, maxWidth);
            if (lines.isEmpty()) continue;
            int blockW = 0;
            for (FormattedCharSequence l : lines) blockW = Math.max(blockW, font.width(l));
            int blockH = lines.size() * 10;
            int top = bottom - blockH;
            int left = (w - blockW) / 2;
            g.fill(left - 4, top - 3, left + blockW + 4, bottom + 1, argb(sub.alpha() * 0.55f, 0x000000));
            int y = top;
            for (FormattedCharSequence l : lines) {
                g.drawString(font, l, (w - font.width(l)) / 2, y, argb(sub.alpha(), 0xFFFFFF), true);
                y += 10;
            }
            bottom = top - 6;
        }
    }

    /** {@code size} scales the block about its own centre (the wrap width shrinks to match, so it stays on screen). */
    private static void narration(GuiGraphics g, Font font, String text, float alpha, int w, int h, float size) {
        float s = Math.max(1f, size);
        int maxWidth = (int) (Math.min(w * 0.9f, Math.max(120, w * 0.6f)) / s);
        List<FormattedCharSequence> lines = font.split(Component.literal(text).withStyle(ChatFormatting.ITALIC), maxWidth);
        // A big size is a title card, centred; plain narration sits low. Either way the block stays on screen.
        float half = lines.size() * 5f * s;
        float cy = Mth.clamp((s > 1f ? h * 0.5f : h * 0.78f + lines.size() * 5f), half + 4, Math.max(half + 4, h - half - 4));
        g.pose().pushPose();
        g.pose().translate(w / 2f, cy, 0);
        g.pose().scale(s, s, 1f);
        int y = -lines.size() * 5;
        for (FormattedCharSequence l : lines) {
            g.drawString(font, l, -font.width(l) / 2, y, argb(alpha, SILVER), true);
            y += 10;
        }
        g.pose().popPose();
    }

    /** {@code <name> text}, the way chat shows a player's line. */
    private static String speaker(SceneDef scene, SceneDef.WordDef word, String viewer) {
        String by = word.by();
        String text = WordText.fill(word.text(), viewer);
        if (by == null || by.isBlank()) return text;
        String name = by;
        for (SceneDef.ActorDef a : scene.actors()) {
            if (by.equals(a.id())) {
                if (a.name() != null && !a.name().isBlank()) name = WordText.fill(a.name(), viewer);
                break;
            }
        }
        return "<" + name + "> " + text;
    }

    /** Alpha under 4/255 would be drawn opaque by the font renderer, so a faded-out line is skipped by its caller. */
    private static int argb(float alpha, int rgb) {
        int a = Mth.clamp((int) (alpha * 255f), 4, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
