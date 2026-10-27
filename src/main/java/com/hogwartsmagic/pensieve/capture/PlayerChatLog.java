package com.hogwartsmagic.pensieve.capture;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** The chat lines one player received lately, and when they last jumped somewhere else. Server thread only. */
public final class PlayerChatLog {
    private static final int MAX_LINES = 200;

    public record Line(long tick, @Nullable String sender, String text) {
    }

    private final ArrayDeque<Line> lines = new ArrayDeque<>();
    /** Game time of the last teleport, dimension change, respawn or login; a memory never reaches back across it. */
    public long lastDiscontinuity = Long.MIN_VALUE / 2;

    public void add(long tick, @Nullable String sender, String text) {
        if (lines.size() >= MAX_LINES) {
            lines.pollFirst();
        }
        lines.addLast(new Line(tick, sender, text));
    }

    public void trim(long cutoff) {
        while (!lines.isEmpty() && lines.peekFirst().tick() < cutoff) {
            lines.pollFirst();
        }
    }

    public List<Line> since(long tick) {
        List<Line> out = new ArrayList<>();
        for (Line l : lines) {
            if (l.tick() >= tick) {
                out.add(l);
            }
        }
        return out;
    }
}
