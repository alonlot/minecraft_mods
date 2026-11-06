package com.hogwartsmagic.client.fx;

import com.hogwartsmagic.network.SpellPackets;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Client side of the cinematic effects ({@code fx.Fx}): each feature registers a handler for its kinds, from its own
 * client-setup listener, and every {@link SpellPackets.FxPacket} that arrives is handed to the handler for its kind.
 * A kind no one registered is ignored (logged once), so an older client never breaks on a newer server's effect.
 *
 * <p>Handlers run on the client main thread with the level loaded.
 */
public final class FxClient {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Consumer<FxEvent>> HANDLERS = new ConcurrentHashMap<>();
    private static final Set<String> UNKNOWN = ConcurrentHashMap.newKeySet();

    private FxClient() {
    }

    /** One effect as it arrived: params are the kind's own numbers (missing ones read as the fallback). */
    public record FxEvent(String kind, double x, double y, double z, int entityId, float[] params) {
        public float param(int index, float fallback) {
            return index >= 0 && index < params.length ? params[index] : fallback;
        }

        public net.minecraft.world.phys.Vec3 pos() {
            return new net.minecraft.world.phys.Vec3(x, y, z);
        }

        /** The entity the effect is about, if any and if this client has it. */
        public net.minecraft.world.entity.Entity entity() {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            return entityId < 0 || level == null ? null : level.getEntity(entityId);
        }
    }

    /** Register (or replace) the handler for {@code kind}. Safe from any thread (client setup runs off-thread). */
    public static void register(String kind, Consumer<FxEvent> handler) {
        HANDLERS.put(kind, handler);
    }

    /** Run an effect on this client directly, as if the server had sent it (for effects a client decides itself). */
    public static void play(String kind, double x, double y, double z, int entityId, float... params) {
        handle(new FxEvent(kind, x, y, z, entityId, params == null ? new float[0] : params));
    }

    public static void dispatch(SpellPackets.FxPacket packet) {
        handle(new FxEvent(packet.kind(), packet.x(), packet.y(), packet.z(), packet.entityId(), packet.params()));
    }

    private static void handle(FxEvent event) {
        if (net.minecraft.client.Minecraft.getInstance().level == null) {
            return;
        }
        Consumer<FxEvent> handler = HANDLERS.get(event.kind());
        if (handler == null) {
            if (UNKNOWN.add(event.kind())) {
                LOGGER.debug("No client handler for cinematic effect '{}'; ignoring it", event.kind());
            }
            return;
        }
        try {
            handler.accept(event);
        } catch (RuntimeException e) {
            // An effect is never worth a disconnect.
            LOGGER.error("Cinematic effect '{}' failed", event.kind(), e);
        }
    }
}
