package com.hogwartsmagic.client.ambience;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ambience.AmbienceNet;
import com.hogwartsmagic.ambience.AmbientEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The client's copy of the ambient effects of the level it is in, filled by the sync and delta packets
 * ({@code ClientRuntimeBridge}) and read by the renderers. It belongs to one {@link ClientLevel}: the moment the
 * client holds a different level (a dimension change, a rejoin) the old list is gone and the next sync fills it
 * again, so nothing from the overworld is ever drawn in the Nether. Render thread only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class WorldAmbience {
    private static final Map<UUID, AmbientEffect> EFFECTS = new LinkedHashMap<>();
    /** Ceiling id to the game time its reveal started at. */
    private static final Map<UUID, Float> REVEALS = new HashMap<>();
    @SuppressWarnings("unchecked")
    private static final List<AmbientEffect>[] BY_KIND = new List[AmbientEffect.KIND_COUNT];
    /** The level the contents belong to; a different current level empties the store. */
    private static ClientLevel owner;

    static {
        rebuild();
    }

    private WorldAmbience() {
    }

    /** The current level's effects of one kind (read only; a new list after every change, safe to keep a frame). */
    public static List<AmbientEffect> ofKind(int kind) {
        dropIfLevelChanged();
        if (kind < 0 || kind >= BY_KIND.length) {
            return Collections.emptyList();
        }
        return BY_KIND[kind];
    }

    /** The game time the ceiling's reveal started at, or {@code Float.NaN} when none is pending. */
    public static float revealStart(UUID ceiling) {
        dropIfLevelChanged();
        Float start = REVEALS.get(ceiling);
        return start == null ? Float.NaN : start;
    }

    /** Replaces everything with the server's list for the level the client is in now. */
    public static void accept(AmbienceNet.AmbienceSyncPacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().toString().equals(packet.dimension())) {
            // A sync for a level we are not in (it raced a dimension change); the right one follows.
            return;
        }
        clear();
        owner = level;
        for (AmbientEffect e : packet.all()) {
            EFFECTS.put(e.id(), e);
        }
        rebuild();
    }

    /** Applies one add or remove. */
    public static void accept(AmbienceNet.AmbienceDeltaPacket packet) {
        dropIfLevelChanged();
        if (owner == null) {
            return;
        }
        AmbientEffect e = packet.effect();
        if (packet.add()) {
            EFFECTS.put(e.id(), e);
        } else {
            EFFECTS.remove(e.id());
            REVEALS.remove(e.id());
        }
        rebuild();
    }

    /** Starts the ceiling's reveal now, on the level's game clock. */
    public static void startReveal(UUID ceiling) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        dropIfLevelChanged();
        REVEALS.put(ceiling, (float) level.getGameTime());
    }

    private static void dropIfLevelChanged() {
        if (owner != null && owner != Minecraft.getInstance().level) {
            clear();
        }
    }

    private static void clear() {
        EFFECTS.clear();
        REVEALS.clear();
        owner = null;
        rebuild();
    }

    private static void rebuild() {
        List<List<AmbientEffect>> lists = new ArrayList<>();
        for (int k = 0; k < BY_KIND.length; k++) {
            lists.add(new ArrayList<>());
        }
        for (AmbientEffect e : EFFECTS.values()) {
            lists.get(e.kind()).add(e);
        }
        for (int k = 0; k < BY_KIND.length; k++) {
            BY_KIND[k] = Collections.unmodifiableList(lists.get(k));
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}
