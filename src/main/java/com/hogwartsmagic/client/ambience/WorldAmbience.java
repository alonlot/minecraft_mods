package com.hogwartsmagic.client.ambience;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ambience.AmbienceNet;
import com.hogwartsmagic.ambience.AmbientEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
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
    /** What {@link #revealStart} says when no reveal is pending. */
    public static final long NO_REVEAL = Long.MIN_VALUE;
    /** Ceiling id to the (whole tick) game time its reveal started at; a long, a float loses ticks past 16.7 million. */
    private static final Map<UUID, Long> REVEALS = new HashMap<>();
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

    /**
     * The game time (a whole tick, on the level's clock) the ceiling's reveal started at, or {@link #NO_REVEAL} when
     * none has been told: the ceiling is then simply open. Turn it into drawing time with {@link AmbienceClock#at}.
     */
    public static long revealStart(UUID ceiling) {
        dropIfLevelChanged();
        Long start = REVEALS.get(ceiling);
        return start == null ? NO_REVEAL : start;
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
        REVEALS.put(ceiling, level.getGameTime());
    }

    /**
     * Squared distance from {@code cam} to the nearest point of the effect's volume, for the renderers' range test (a
     * big hall must not vanish because its centre is far while the viewer stands at its wall). A ceiling reaches from its
     * plane up its clearance, candles are the box round them, a ray stands on its window and reaches as far as its
     * widest extent, and a door is its hinge.
     */
    public static double distanceSqr(AmbientEffect e, Vec3 cam) {
        double hx = e.hx(), hy = 0.0, hz = e.hz();
        double y0 = e.y(), y1 = e.y();
        if (e.kind() == AmbientEffect.KIND_CEILING) {
            y1 = e.y() + Math.max(0f, e.hy());
        } else if (e.kind() == AmbientEffect.KIND_CANDLES) {
            hy = e.hy();
        } else if (e.kind() == AmbientEffect.KIND_RAYS) {
            double reach = Math.max(e.hx(), e.hz());
            hx = reach;
            hz = reach;
            y1 = e.y() + e.hy();
        } else {
            hx = 0.0;
            hz = 0.0;
        }
        double dx = Math.max(0.0, Math.abs(cam.x - e.x()) - hx);
        double dz = Math.max(0.0, Math.abs(cam.z - e.z()) - hz);
        double dy = Math.max(0.0, Math.max(y0 - hy - cam.y, cam.y - (y1 + hy)));
        return dx * dx + dy * dy + dz * dz;
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
