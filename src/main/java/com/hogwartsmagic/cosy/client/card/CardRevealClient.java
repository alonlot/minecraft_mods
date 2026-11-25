package com.hogwartsmagic.cosy.client.card;

import com.hogwartsmagic.cosy.WizardCards;
import net.minecraft.client.Minecraft;

import java.util.OptionalInt;

/**
 * Client entry point for the card reveal: the packet handler calls {@link #offer}, and a client tick opens the screens
 * one at a time. A reveal waits while any other screen is open (an inventory, the chat, the previous reveal), so it
 * never replaces a container screen out from under the player. Boxes opened in quick succession queue (at most five).
 */
public final class CardRevealClient {
    private static final RevealQueue QUEUE = new RevealQueue();

    private CardRevealClient() {
    }

    /** A box was opened and rolled this card. Called on the client main thread. */
    public static void offer(int cardId) {
        if (WizardCards.byId(cardId) == null) {
            return;
        }
        QUEUE.offer(cardId);
    }

    /** Every client tick: when nothing else is on screen, open the next waiting reveal. */
    static void tick() {
        if (QUEUE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null) {
            return;
        }
        OptionalInt next = QUEUE.poll();
        if (next.isPresent()) {
            mc.setScreen(new CardRevealScreen(next.getAsInt()));
        }
    }

    /** Leaving the world forgets the reveals that never showed. */
    static void clear() {
        QUEUE.clear();
    }
}
