package com.hogwartsmagic.client.fx.bloom;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Whether an Oculus / Iris shader pack is drawing the world. A pack brings its own bloom and its own render targets,
 * so the mod's glow stands aside while one is in use. Looked up by reflection, never a hard dependency: without
 * Oculus installed this is always false.
 */
final class ShaderPacks {
    private static boolean looked;
    private static Object api;
    private static Method inUse;
    /** Older Iris: {@code net.coderbot.iris.Iris.getCurrentPack()} returns an Optional pack. */
    private static Method currentPack;

    private ShaderPacks() {
    }

    static boolean inUse() {
        if (!looked) {
            looked = true;
            find();
        }
        try {
            if (inUse != null) {
                return Boolean.TRUE.equals(inUse.invoke(api));
            }
            if (currentPack != null) {
                Object pack = currentPack.invoke(null);
                return pack instanceof Optional<?> optional && optional.isPresent();
            }
        } catch (Throwable broken) {
            inUse = null;
            currentPack = null;
        }
        return false;
    }

    private static void find() {
        ClassLoader loader = ShaderPacks.class.getClassLoader();
        try {
            Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi", false, loader);
            api = type.getMethod("getInstance").invoke(null);
            inUse = type.getMethod("isShaderPackInUse");
            return;
        } catch (Throwable absent) {
            api = null;
            inUse = null;
        }
        try {
            Class<?> type = Class.forName("net.coderbot.iris.Iris", false, loader);
            currentPack = type.getMethod("getCurrentPack");
        } catch (Throwable absent) {
            currentPack = null;
        }
    }
}
