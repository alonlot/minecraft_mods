package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Dev tooling: drive the client with a script, for reviewing effects with nobody at the keyboard (for example
 * under Xvfb). Off unless the environment variable {@code HOGWARTSMAGIC_DEV_SCRIPT} names a script file. Then:
 * on the title screen the client opens the world named by {@code HOGWARTSMAGIC_DEV_WORLD}, creating it first as a flat
 * creative world with commands on if it does not exist (opening it as the menus do: quick play loses Forge's reload
 * listeners, see docs/superpowers/handoff/feature-audit),
 * and once in it runs the script, one line at a time:
 * <pre>
 *   wait 40              ticks to wait
 *   cmd tombcurse room   a command, run on the integrated server as the player with op rights
 *   interact hogwartsmagic:niffler   right-click (empty hand) the nearest entity of that type, as the player
 *   look 90 30           the player's yaw and pitch
 *   gui off              hide the HUD (gui on: show it)
 *   camera back          third person from behind (front, first)
 *   shot name            a screenshot, screenshots/name.png
 *   quit                 close the game
 * </pre>
 * Blank lines and lines starting with # are ignored.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class DevScript {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Deque<String> lines;
    private static int waiting;
    private static boolean opening;
    private static int joinedTicks;

    private DevScript() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        String script = System.getenv("HOGWARTSMAGIC_DEV_SCRIPT");
        if (script == null || script.isBlank()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            String world = System.getenv("HOGWARTSMAGIC_DEV_WORLD");
            if (!opening && world != null && (mc.screen instanceof TitleScreen
                    || mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen)) {
                opening = true;
                if (mc.getLevelSource().levelExists(world)) {
                    LOGGER.info("Dev script: opening world {}", world);
                    mc.createWorldOpenFlows().openWorld(world, () -> mc.setScreen(new TitleScreen()));
                } else {
                    // A flat creative world with commands on, made as the Create World screen makes one.
                    LOGGER.info("Dev script: creating flat world {}", world);
                    net.minecraft.world.level.LevelSettings settings = new net.minecraft.world.level.LevelSettings(
                            world, net.minecraft.world.level.GameType.CREATIVE, false,
                            net.minecraft.world.Difficulty.NORMAL, true, new net.minecraft.world.level.GameRules(),
                            net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                    mc.createWorldOpenFlows().createFreshLevel(world, settings,
                            new net.minecraft.world.level.levelgen.WorldOptions(20261122L, false, false),
                            registries -> registries.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                    .getOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value()
                                    .createWorldDimensions(),
                            new TitleScreen());
                }
            }
            return;
        }
        if (++joinedTicks < 100) {
            return;     // let the world settle and the chunks round the player load
        }
        if (lines == null) {
            try {
                lines = new ArrayDeque<>(Files.readAllLines(Path.of(script)));
                LOGGER.info("Dev script: running {} ({} lines)", script, lines.size());
            } catch (Exception e) {
                LOGGER.error("Dev script: cannot read {}", script, e);
                lines = new ArrayDeque<>();
            }
        }
        if (waiting > 0) {
            waiting--;
            return;
        }
        while (!lines.isEmpty() && waiting == 0) {
            String line = lines.poll().trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] w = line.split("\\s+", 2);
            String arg = w.length > 1 ? w[1] : "";
            LOGGER.info("Dev script: {}", line);
            switch (w[0]) {
                case "wait" -> waiting = Integer.parseInt(arg.trim());
                case "cmd" -> {
                    MinecraftServer server = mc.getSingleplayerServer();
                    if (server != null) {
                        server.execute(() -> {
                            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                            if (player != null) {
                                server.getCommands().performPrefixedCommand(
                                        player.createCommandSourceStack().withPermission(4), arg);
                            }
                        });
                    }
                }
                case "interact" -> {
                    MinecraftServer server = mc.getSingleplayerServer();
                    var type = net.minecraft.world.entity.EntityType.byString(arg.trim());
                    if (server != null && type.isPresent()) {
                        server.execute(() -> {
                            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                            if (player == null) {
                                return;
                            }
                            player.serverLevel().getEntities(type.get(), player.getBoundingBox().inflate(16.0D), e -> e.isAlive())
                                    .stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                                    .ifPresent(e -> player.interactOn(e, net.minecraft.world.InteractionHand.MAIN_HAND));
                        });
                    }
                }
                case "look" -> {
                    String[] a = arg.split("\\s+");
                    mc.player.setYRot(Float.parseFloat(a[0]));
                    mc.player.setXRot(Float.parseFloat(a[1]));
                    mc.player.yRotO = mc.player.getYRot();
                    mc.player.xRotO = mc.player.getXRot();
                }
                case "gui" -> mc.options.hideGui = arg.trim().equals("off");
                case "camera" -> mc.options.setCameraType(switch (arg.trim()) {
                    case "back" -> net.minecraft.client.CameraType.THIRD_PERSON_BACK;
                    case "front" -> net.minecraft.client.CameraType.THIRD_PERSON_FRONT;
                    default -> net.minecraft.client.CameraType.FIRST_PERSON;
                });
                case "shot" -> Screenshot.grab(mc.gameDirectory, arg.trim() + ".png", mc.getMainRenderTarget(),
                        msg -> LOGGER.info("Dev script: {}", msg.getString()));
                case "quit" -> mc.stop();
                default -> LOGGER.warn("Dev script: unknown line {}", line);
            }
        }
    }
}
