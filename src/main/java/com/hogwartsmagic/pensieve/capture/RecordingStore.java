package com.hogwartsmagic.pensieve.capture;

import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Recordings live in the world save, one file each, with a small cache of decoded ones. */
public final class RecordingStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CACHE_SIZE = 8;

    private static final class Entry {
        final Recording recording;
        volatile byte[] clientBlob;

        Entry(Recording recording) {
            this.recording = recording;
        }
    }

    private static final Map<UUID, Entry> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, Entry> eldest) {
                    return size() > CACHE_SIZE;
                }
            });
    private static final Set<UUID> WARNED = Collections.synchronizedSet(new HashSet<>());
    private static final Set<UUID> LOADING = Collections.synchronizedSet(new HashSet<>());
    @Nullable
    private static volatile Function<UUID, Optional<Recording>> override;

    private RecordingStore() {
    }

    public static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("hogwartsmagic_memories");
    }

    private static Path file(MinecraftServer server, UUID id) {
        return dir(server).resolve(id + ".hpm");
    }

    /** Writes the full recording to the world save on the IO pool; later loads see it at once through the cache. */
    public static CompletableFuture<Void> save(MinecraftServer server, Recording recording) {
        CACHE.put(recording.id(), new Entry(recording));
        Path target = file(server, recording.id());
        return CompletableFuture.runAsync(() -> {
            try {
                byte[] bytes = RecordingCodec.write(recording, false);
                Files.createDirectories(target.getParent());
                Path tmp = target.resolveSibling(recording.id() + ".hpm.tmp");
                Files.write(tmp, bytes);
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, Util.ioPool());
    }

    /** Synchronous: from the cache, else from disk. A missing or unreadable file is empty, logged once per id. */
    public static Optional<Recording> load(MinecraftServer server, UUID id) {
        Function<UUID, Optional<Recording>> fn = override;
        if (fn != null) {
            return fn.apply(id);
        }
        Entry cached = CACHE.get(id);
        if (cached != null) {
            return Optional.of(cached.recording);
        }
        Path path = file(server, id);
        try {
            Recording r = RecordingCodec.read(Files.readAllBytes(path));
            CACHE.put(id, new Entry(r));
            return Optional.of(r);
        } catch (IOException | RuntimeException e) {
            if (WARNED.add(id)) {
                LOGGER.warn("Memory recording {} could not be read from {}: {}", id, path, e.toString());
            }
            return Optional.empty();
        }
    }

    /** Starts loading a recording in the background so the next {@link #load} is a cache hit. */
    public static void prefetch(MinecraftServer server, UUID id) {
        if (override != null || CACHE.containsKey(id) || !LOADING.add(id)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                load(server, id);
            } finally {
                LOADING.remove(id);
            }
        }, Util.ioPool());
    }

    /** The client form of a cached recording (no island), encoded once. Null when it is not in the cache. */
    @Nullable
    public static byte[] clientBlob(UUID id) {
        Function<UUID, Optional<Recording>> fn = override;
        Entry entry = CACHE.get(id);
        if (entry == null) {
            if (fn == null) {
                return null;
            }
            Optional<Recording> r = fn.apply(id);
            if (r.isEmpty()) {
                return null;
            }
            entry = new Entry(r.get());
            CACHE.put(id, entry);
        }
        byte[] blob = entry.clientBlob;
        if (blob == null) {
            blob = RecordingCodec.write(entry.recording, true);
            entry.clientBlob = blob;
        }
        return blob;
    }

    /** Loads if needed, then returns the client blob. */
    @Nullable
    public static byte[] clientBlob(MinecraftServer server, UUID id) {
        if (load(server, id).isEmpty()) {
            return null;
        }
        return clientBlob(id);
    }

    public static void clearCache() {
        CACHE.clear();
        WARNED.clear();
    }

    /** Tests: replaces disk loading. Null restores it. */
    public static void overrideForTests(@Nullable Function<UUID, Optional<Recording>> loader) {
        override = loader;
        CACHE.clear();
    }
}
