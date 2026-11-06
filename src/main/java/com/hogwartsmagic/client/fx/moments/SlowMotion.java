package com.hogwartsmagic.client.fx.moments;

import com.hogwartsmagic.client.pensieve.PensieveClientState;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import org.slf4j.Logger;

/**
 * The world slowing to a crawl at the biggest moments, single player only. The integrated server's tick rate is
 * lowered to {@code 20 * rate}, held, and eased back to 20 over the last few ticks. Client code calling the
 * integrated server object in the same JVM; it never loads on a dedicated server (only client classes reach it).
 *
 * <p>Timing counts REAL time (System.nanoTime), since game ticks slow down with the world. It is driven once per
 * rendered frame by {@link MomentsClient}, and every change to the tick rate is made on the server thread.
 *
 * <p>Safety, so the world is never left slow:
 * <ul>
 * <li>never starts unless this is a single-player integrated server, not opened to LAN, with exactly one player;</li>
 * <li>never starts, and never touches the rate, if it was already not 20 (or frozen or sprinting) when we began
 * (the player may have used {@code /tick}); if anyone changes the rate while we run, we let go without
 * overwriting their value;</li>
 * <li>restores 20 on logout, on pause or quit, if a second player joins or the world is opened to LAN, when a
 * Pensieve memory starts, and if anything throws.</li>
 * </ul>
 */
public final class SlowMotion {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float NORMAL = 20.0f;
    /** The last few real ticks of a slow-motion run are an ease back to normal speed. */
    private static final float EASE_TICKS = 6.0f;
    private static final double TICK_NANOS = 50_000_000.0;
    /** Tick rate changes smaller than this are not sent (each one is a packet to the client). */
    private static final float STEP = 0.75f;

    /** 0 idle, 1 asked for (waiting for the server thread to confirm we own the rate), 2 owned. */
    private static volatile int state;
    private static volatile float lastSet = NORMAL;
    private static float rate = 1.0f;
    private static long start;
    private static long end;

    private SlowMotion() {
    }

    /** Begin (or deepen and extend) a slow-motion run. Main thread. */
    public static void start(float wanted, int realTicks) {
        try {
            Minecraft mc = Minecraft.getInstance();
            MinecraftServer server = mc.getSingleplayerServer();
            if (!mc.hasSingleplayerServer() || server == null || !safeWorld(server) || realTicks <= 0
                    || PensieveClientState.inMemory()) {
                return;
            }
            float r = Mth.clamp(wanted, 0.1f, 1.0f);
            long now = System.nanoTime();
            long until = now + (long) (realTicks * TICK_NANOS);
            if (state != 0) {
                rate = Math.min(rate, r);
                end = Math.max(end, until);
                return;
            }
            rate = r;
            start = now;
            end = until;
            state = 1;
            lastSet = NORMAL;
            server.execute(() -> {
                try {
                    var manager = server.tickRateManager();
                    if (state != 1 || manager.tickrate() != NORMAL || manager.isFrozen() || manager.isSprinting()) {
                        if (state == 1) {
                            state = 0; // the player is already controlling the tick rate: leave it alone
                        }
                        return;
                    }
                    state = 2;
                } catch (RuntimeException e) {
                    state = 0;
                    LOGGER.warn("Slow motion: could not read the tick rate", e);
                }
            });
        } catch (RuntimeException e) {
            LOGGER.warn("Slow motion failed to start", e);
            cancel();
        }
    }

    /** One call per rendered frame (main thread): follow the real clock and keep the safety conditions. */
    public static void frame() {
        if (state == 0) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            MinecraftServer server = mc.getSingleplayerServer();
            if (server == null || mc.level == null || mc.isPaused() || !safeWorld(server)
                    || PensieveClientState.inMemory()) {
                cancel();
                return;
            }
            long now = System.nanoTime();
            if (now >= end) {
                cancel();
                return;
            }
            float target = rate;
            float left = (float) ((end - now) / TICK_NANOS);
            if (left < EASE_TICKS) {
                float k = left / EASE_TICKS;
                target = Mth.lerp(k * k, 1.0f, rate); // ease back to normal speed
            }
            float tps = Mth.clamp(NORMAL * target, 1.0f, NORMAL);
            if (state == 2 && Math.abs(tps - lastSet) >= STEP) {
                push(server, tps);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Slow motion failed; restoring normal speed", e);
            cancel();
        }
    }

    /** Only a lone player in a private integrated server gets slow motion. */
    private static boolean safeWorld(MinecraftServer server) {
        return server.isSingleplayer() && !server.isPublished() && server.getPlayerCount() == 1;
    }

    private static void push(MinecraftServer server, float tps) {
        lastSet = tps;
        server.execute(() -> {
            try {
                var manager = server.tickRateManager();
                if (state != 2) {
                    return;
                }
                float now = manager.tickrate();
                // Someone else (a /tick command) changed it since our last write: it is theirs now.
                if (Math.abs(now - NORMAL) > 0.001f && Math.abs(now - previous) > 0.001f) {
                    state = 0;
                    return;
                }
                manager.setTickRate(tps);
                previous = tps;
            } catch (RuntimeException e) {
                LOGGER.warn("Slow motion: could not set the tick rate", e);
            }
        });
    }

    /** What we last wrote (server thread only). */
    private static float previous = NORMAL;

    /** Put the tick rate back to 20 if it is ours, and go idle. Safe to call at any time, from the main thread. */
    public static void cancel() {
        int was = state;
        state = 0;
        if (was == 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        try {
            server.execute(() -> restoreOnServerThread(server));
        } catch (RuntimeException e) {
            LOGGER.warn("Slow motion: could not queue the restore", e);
        }
        // The server may be going down (logout/quit) and never run the queue: also restore directly if we are on it.
        if (server.isSameThread()) {
            restoreOnServerThread(server);
        }
    }

    private static void restoreOnServerThread(MinecraftServer server) {
        if (previous == NORMAL) {
            previous = NORMAL;
            return;
        }
        try {
            var manager = server.tickRateManager();
            // Only undo our own value: if the player has set something else since, it stays.
            if (Math.abs(manager.tickrate() - previous) < 0.001f) {
                manager.setTickRate(NORMAL);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Slow motion: could not restore the tick rate", e);
        } finally {
            previous = NORMAL;
        }
    }
}
