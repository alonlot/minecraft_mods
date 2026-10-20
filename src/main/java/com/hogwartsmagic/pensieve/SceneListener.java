package com.hogwartsmagic.pensieve;

import net.minecraft.server.level.ServerPlayer;

/**
 * Told, on the server thread, about one viewer and a scene registered by id: see
 * {@link PensieveSessions#addSceneEnterListener} and {@link PensieveSessions#addSceneTimeoutListener}. A listener may
 * call {@link PensieveSessions#handOver} or {@link PensieveSessions#leave} for that viewer.
 */
@FunctionalInterface
public interface SceneListener {
    void on(ServerPlayer player, Session session);
}
