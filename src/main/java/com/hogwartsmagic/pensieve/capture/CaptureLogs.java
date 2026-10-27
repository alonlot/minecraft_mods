package com.hogwartsmagic.pensieve.capture;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.pensieve.PensieveDimension;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Who keeps the logs: one {@link LevelLog} per recorded level and one {@link PlayerChatLog} per player. */
public final class CaptureLogs {
    private static final Map<ServerLevel, LevelLog> LEVELS = new HashMap<>();
    private static final Map<UUID, PlayerChatLog> CHAT = new HashMap<>();

    private CaptureLogs() {
    }

    /** True when this level is never recorded: capture is off, or it is the memory dimension. */
    private static boolean excluded(ServerLevel level) {
        return !Config.pensieveCaptureEnabled || PensieveDimension.isMemory(level);
    }

    /**
     * The log of a level, created on first use. Null when capture is disabled, the level is the memory dimension,
     * the level has no players, or the caller is not the server thread. Cheap enough for hot paths.
     */
    @Nullable
    public static LevelLog of(ServerLevel level) {
        if (excluded(level) || level.players().isEmpty() || !level.getServer().isSameThread()) {
            return null;
        }
        LevelLog log = LEVELS.get(level);
        if (log == null) {
            log = new LevelLog(level.getGameTime());
            LEVELS.put(level, log);
        }
        return log;
    }

    /** The log if one exists already; unlike {@link #of} it never creates one. */
    @Nullable
    public static LevelLog existing(ServerLevel level) {
        return LEVELS.get(level);
    }

    public static PlayerChatLog chat(UUID player) {
        return CHAT.computeIfAbsent(player, k -> new PlayerChatLog());
    }

    public static void forget(ServerLevel level) {
        LEVELS.remove(level);
    }

    public static void forgetPlayer(UUID player) {
        CHAT.remove(player);
    }

    public static void forgetAll() {
        LEVELS.clear();
        CHAT.clear();
    }

    /** End of a server-side level tick. */
    public static void tick(ServerLevel level) {
        if (excluded(level) || level.players().isEmpty()) {
            LEVELS.remove(level); // nobody here: keep nothing
            return;
        }
        LevelLog log = of(level);
        if (log == null) {
            return;
        }
        long now = level.getGameTime();
        log.tick(level, now);
        if (now % 20 == 0) {
            long cutoff = now - LevelLog.windowTicks() - 40;
            for (ServerPlayer p : level.players()) {
                PlayerChatLog chat = CHAT.get(p.getUUID());
                if (chat != null) {
                    chat.trim(cutoff);
                }
            }
        }
    }

    /** Start of a server tick: data changes made between ticks (packets, commands) are still flagged dirty. */
    public static void lateTick(net.minecraft.server.MinecraftServer server) {
        if (LEVELS.isEmpty()) {
            return;
        }
        for (Map.Entry<ServerLevel, LevelLog> e : LEVELS.entrySet()) {
            if (e.getKey().getServer() == server) {
                e.getValue().sampleLateData(e.getKey(), e.getKey().getGameTime());
            }
        }
    }
}
