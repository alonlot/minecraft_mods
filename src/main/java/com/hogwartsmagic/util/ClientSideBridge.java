package com.hogwartsmagic.util;

import com.hogwartsmagic.ExampleMod;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

import java.lang.reflect.Method;

public final class ClientSideBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BRIDGE_CLASS_NAME = "com.hogwartsmagic.client.ClientRuntimeBridge";

    private ClientSideBridge() {
    }

    public static void handlePacket(String action, Object payload) {
        invoke("handlePacket", new Class<?>[]{String.class, Object.class}, action, payload);
    }

    public static void onClientPlayerTick(net.minecraft.world.entity.player.Player player,
                                          boolean serpensortiaUseLatched,
                                          boolean fiendfyreUseLatched,
                                          boolean wingardiumUseLatched,
                                          boolean imperioUseLatched) {
        invoke(
                "onClientPlayerTick",
                new Class<?>[]{
                        net.minecraft.world.entity.player.Player.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        boolean.class
                },
                player,
                serpensortiaUseLatched,
                fiendfyreUseLatched,
                wingardiumUseLatched,
                imperioUseLatched
        );
    }

    public static void openSpellMenu(net.minecraft.world.entity.player.Player player) {
        invoke("openSpellMenu", new Class<?>[]{net.minecraft.world.entity.player.Player.class}, player);
    }

    public static void startPatronusHold(net.minecraft.world.entity.player.Player player) {
        invoke("startPatronusHold", new Class<?>[]{net.minecraft.world.entity.player.Player.class}, player);
    }

    public static void playNearMissFeedback(com.hogwartsmagic.entity.SpellBeamEntity beam) {
        invoke("playNearMissFeedback", new Class<?>[]{com.hogwartsmagic.entity.SpellBeamEntity.class}, beam);
    }

    private static void invoke(String methodName, Class<?>[] parameterTypes, Object... args) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }

        try {
            Class<?> bridgeClass = Class.forName(BRIDGE_CLASS_NAME);
            Method method = bridgeClass.getMethod(methodName, parameterTypes);
            method.invoke(null, args);
        } catch (ReflectiveOperationException exception) {
            LOGGER.error("Failed to invoke client bridge method {} for mod {}", methodName, ExampleMod.MODID, exception);
        }
    }
}
