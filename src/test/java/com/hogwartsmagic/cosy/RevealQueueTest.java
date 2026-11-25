package com.hogwartsmagic.cosy;

import com.hogwartsmagic.cosy.client.card.RevealQueue;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Several boxes opened quickly: the reveals queue, one at a time, at most five waiting. */
class RevealQueueTest {
    @Test
    void firstInFirstOut() {
        RevealQueue q = new RevealQueue();
        assertTrue(q.offer(4));
        assertTrue(q.offer(9));
        assertTrue(q.offer(1));
        assertEquals(OptionalInt.of(4), q.poll());
        assertEquals(OptionalInt.of(9), q.poll());
        assertEquals(OptionalInt.of(1), q.poll());
    }

    @Test
    void aSixthOfferIsDropped() {
        RevealQueue q = new RevealQueue();
        for (int i = 1; i <= 5; i++) {
            assertTrue(q.offer(i), "offer " + i);
        }
        assertFalse(q.offer(6), "the sixth is dropped");
        assertEquals(5, q.size());
        assertEquals(OptionalInt.of(1), q.poll());
        assertTrue(q.offer(7), "room again after a poll");
        for (int i = 2; i <= 5; i++) {
            assertEquals(OptionalInt.of(i), q.poll());
        }
        assertEquals(OptionalInt.of(7), q.poll());
        assertFalse(q.poll().isPresent(), "the dropped card never comes back");
    }

    @Test
    void pollOnAnEmptyQueueIsEmpty() {
        RevealQueue q = new RevealQueue();
        assertEquals(OptionalInt.empty(), q.poll());
        assertTrue(q.isEmpty());
        q.offer(3);
        q.clear();
        assertEquals(OptionalInt.empty(), q.poll());
    }
}
