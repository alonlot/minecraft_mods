package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dev tooling for reviewing a cinematic with nobody at the keyboard. Everything here is off unless configured:
 * <ul>
 *   <li>{@code pensieveDevAutoPreview}: the client runs {@code /pensieve preview <id>} 5 s after it joins a world
 *       (once per launch);</li>
 *   <li>{@code pensieveDevShotSeconds} above 0: while a memory plays, a screenshot every that many seconds of scene
 *       time, as {@code screenshots/pensieve_<scene>_<seconds>.png};</li>
 *   <li>a request file {@code pensieve_dev/request.txt} in the run directory, {@code <scene id> [seconds between
 *       shots]}: the client leaves any memory it is in, previews that scene (read fresh from the dev scene folder when
 *       {@code pensieveDevSceneDir} is set), shoots it, and writes {@code pensieve_dev/status.txt} ({@code done <id>})
 *       when the memory ends, so a script can drive edit, preview and look without a rebuild.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class DevShots {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static int joinedTicks;
    private static boolean previewSent;
    private static String shotScene;
    private static float nextShot;
    /** Seconds between shots set by the last request; NaN: the config's. */
    private static double requestEvery = Double.NaN;
    /** A requested scene waiting for the memory it interrupted to finish leaving. */
    private static String pendingPreview;
    private static int pendingDelay;
    /** The memory a request started, until it ends. */
    private static String watching;

    private DevShots() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            joinedTicks = 0;
            return;
        }
        joinedTicks++;
        String auto = Config.pensieveDevAutoPreview;
        if (!previewSent && auto != null && !auto.isBlank() && joinedTicks >= 100) {
            previewSent = true;
            LOGGER.info("Pensieve dev: auto preview {}", auto);
            mc.player.connection.sendCommand("pensieve preview " + auto.trim());
        }
        if (joinedTicks % 20 == 0) readRequest(mc);
        if (pendingPreview != null && --pendingDelay <= 0 && !PensieveClientState.active()) {
            LOGGER.info("Pensieve dev: requested preview {}", pendingPreview);
            mc.player.connection.sendCommand("pensieve preview " + pendingPreview);
            watching = pendingPreview;
            pendingPreview = null;
        }
        boolean in = PensieveClientState.inMemory();
        if (watching != null && !PensieveClientState.active() && pendingPreview == null && joinedTicks % 20 == 10
                && shotScene != null) {
            writeStatus("done " + watching);
            watching = null;
        }
        double every = Double.isNaN(requestEvery) ? Config.pensieveDevShotSeconds : requestEvery;
        if (every <= 0 || !in) return;
        SceneDef scene = PensieveClientState.scene();
        if (scene == null) return;
        float t = PensieveClientState.sceneTime(0f) / 20f;
        if (!scene.id().equals(shotScene) || t + 0.5f < nextShot - (float) every) {
            shotScene = scene.id();
            nextShot = (float) every * 0.5f;
        }
        if (t >= nextShot && t > 0.05f) {
            String name = String.format("pensieve_%s_%06.1f.png", scene.id(), t);
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> { });
            nextShot += (float) every;
        }
    }

    private static void readRequest(Minecraft mc) {
        Path req = mc.gameDirectory.toPath().resolve("pensieve_dev").resolve("request.txt");
        if (!Files.isRegularFile(req)) return;
        try {
            String[] parts = Files.readString(req, StandardCharsets.UTF_8).trim().split("\\s+");
            Files.delete(req);
            if (parts.length == 0 || parts[0].isBlank()) return;
            requestEvery = parts.length > 1 ? Double.parseDouble(parts[1]) : Double.NaN;
            pendingPreview = parts[0];
            pendingDelay = 10;
            shotScene = null;
            writeStatus("requested " + parts[0]);
            if (PensieveClientState.active()) {
                mc.player.connection.sendCommand("pensieve leave");
                pendingDelay = 80;
            }
        } catch (IOException | NumberFormatException e) {
            LOGGER.warn("Pensieve dev: bad request file: {}", e.toString());
        }
    }

    private static void writeStatus(String s) {
        try {
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("pensieve_dev");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("status.txt"), s + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Pensieve dev: cannot write status: {}", e.toString());
        }
    }
}
