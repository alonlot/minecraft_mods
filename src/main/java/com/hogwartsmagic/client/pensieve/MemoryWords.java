package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.WordText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * What the people in a memory say, drawn on the screen and never written to the real chat log (nothing here
 * touches {@code ChatComponent}, the server, or any other player). Actor lines ({@code by}) stack at the bottom
 * left as chat-styled {@code <name> text} lines and fade after eight seconds; narration is centred low in italic
 * and fades in and out across its {@code duration}. Everything is a function of the shared scene clock, so a
 * late joiner sees the recent lines and two viewers see the same words.
 */
public final class MemoryWords {
    private static final int SILVER = 0xC9D6E3;
    /** An actor line stays this long, and fades over its last {@link #LINE_FADE}. */
    static final int LINE_TICKS = 160;
    static final int LINE_FADE = 20;
    private static final int NARRATION_FADE = 10;
    private static final int DEFAULT_NARRATION_TICKS = 60;
    private static final int MAX_LINES = 8;
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
        for (SceneDef.WordDef word : scene.words()) {
            float age = now - word.t();
            if (age < 0) continue;
            if (word.narration()) {
                int life = word.duration() > 0 ? word.duration() : DEFAULT_NARRATION_TICKS;
                if (age >= life) continue;
                float a = Mth.clamp(Math.min(age / NARRATION_FADE, (life - age) / NARRATION_FADE), 0f, 1f);
                narration(g, font, WordText.fill(word.text(), viewer), a, w, h);
            } else if (age < LINE_TICKS) {
                float a = Mth.clamp((LINE_TICKS - age) / LINE_FADE, 0f, 1f);
                for (FormattedCharSequence l : font.split(Component.literal(speaker(scene, word, viewer)), Math.max(80, Math.min(w / 2, 320)))) {
                    lines.add(l);
                    alphas.add(a);
                }
            }
        }
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

    private static void narration(GuiGraphics g, Font font, String text, float alpha, int w, int h) {
        int maxWidth = Math.max(120, (int) (w * 0.6f));
        List<FormattedCharSequence> lines = font.split(Component.literal(text).withStyle(ChatFormatting.ITALIC), maxWidth);
        int y = (int) (h * 0.78f);
        for (FormattedCharSequence l : lines) {
            g.drawString(font, l, (w - font.width(l)) / 2, y, argb(alpha, SILVER), true);
            y += 10;
        }
    }

    /** {@code <name> text}, the way chat shows a player's line. */
    private static String speaker(SceneDef scene, SceneDef.WordDef word, String viewer) {
        String by = word.by();
        String text = WordText.fill(word.text(), viewer);
        if (by == null || by.isBlank()) return text;
        String name = by;
        for (SceneDef.ActorDef a : scene.actors()) {
            if (by.equals(a.id())) {
                if (a.name() != null && !a.name().isBlank()) name = a.name();
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
