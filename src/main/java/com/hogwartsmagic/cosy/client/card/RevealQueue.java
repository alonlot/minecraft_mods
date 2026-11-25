package com.hogwartsmagic.cosy.client.card;

import java.util.ArrayDeque;
import java.util.OptionalInt;

/**
 * The reveals waiting their turn. Opening several boxes quickly must not stack screens on top of each other: they
 * show one at a time, in the order they were opened, and the line is capped at five so a pile of boxes cannot lock
 * the player into a long show. Pure: no Minecraft classes, so JUnit covers it.
 */
public final class RevealQueue {
    public static final int MAX_QUEUED = 5;

    private final ArrayDeque<Integer> waiting = new ArrayDeque<>();

    /** Adds a card to the line. Returns false (and drops it) when five are already waiting. */
    public boolean offer(int cardId) {
        if (this.waiting.size() >= MAX_QUEUED) {
            return false;
        }
        this.waiting.addLast(cardId);
        return true;
    }

    /** The next card to show, or empty. */
    public OptionalInt poll() {
        Integer next = this.waiting.pollFirst();
        return next == null ? OptionalInt.empty() : OptionalInt.of(next);
    }

    public int size() {
        return this.waiting.size();
    }

    public boolean isEmpty() {
        return this.waiting.isEmpty();
    }

    public void clear() {
        this.waiting.clear();
    }
}
