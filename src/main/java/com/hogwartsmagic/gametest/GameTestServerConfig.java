package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

/**
 * The GameTestServer never loads SERVER-type configs (a dedicated server does that in
 * {@code ServerLifecycleHooks.handleServerAboutToStart}). Forge's own {@code Level.guardEntityTick} reads
 * {@code ForgeConfig.SERVER.removeErroringEntities} whenever an entity tick throws, and in dev that read
 * throws "Cannot get config value before config is loaded", hiding the real exception and crashing the run.
 * Load them here, for the gametest server only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class GameTestServerConfig {
    private static boolean loaded;

    private GameTestServerConfig() {
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) throws IOException {
        if (loaded || !(event.getLevel() instanceof ServerLevel level) || !(level.getServer() instanceof GameTestServer server)) {
            return;
        }
        loaded = true;
        Path dir = server.getWorldPath(new LevelResource("serverconfig"));
        Files.createDirectories(dir);
        ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.SERVER, dir);
    }
}
