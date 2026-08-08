package com.hogwartsmagic.client;

import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which players are currently holding a non-corporeal Patronus shield, as far as this
 * client knows. Fed by {@code patronusShieldState} packets from the server.
 *
 * <p>Entries carry an expiry rather than living until an explicit "off". A caster who stops
 * channelling while outside this client's tracking range never delivers that "off", and the
 * cone would reappear when they walked back into view. The server refreshes the state while
 * the shield is up, so anything that stops being refreshed simply lapses.
 */
public final class PatronusShieldClientState {

    /** Comfortably longer than the server's refresh interval, short enough to hide a stale cone. */
    private static final long TTL_TICKS = 30L;

    private static final Map<Integer, Long> EXPIRY = new ConcurrentHashMap<>();

    private PatronusShieldClientState() {
    }

    public static void set(int entityId, boolean active) {
        if (active) {
            EXPIRY.put(entityId, now() + TTL_TICKS);
        } else {
            EXPIRY.remove(entityId);
        }
    }

    public static boolean isChanneling(int entityId) {
        Long expiry = EXPIRY.get(entityId);
        if (expiry == null) {
            return false;
        }
        if (now() > expiry) {
            EXPIRY.remove(entityId);
            return false;
        }
        return true;
    }

    public static boolean isEmpty() {
        return EXPIRY.isEmpty();
    }

    /** Leaving a world must not carry stale casters into the next one. */
    public static void clear() {
        EXPIRY.clear();
    }

    private static long now() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }
}
