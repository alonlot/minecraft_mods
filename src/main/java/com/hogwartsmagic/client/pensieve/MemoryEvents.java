package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs a scene's {@code sound}, {@code particles} and {@code effect} events on the client: each fires exactly once,
 * when the shared scene clock crosses its {@code t}. ({@code block} events are the server's.) Like the actors it
 * is a pure function of the scene time, so two viewers of one memory see the same film.
 *
 * <p>A late joiner arrives with the clock already running: an event older than {@link #LATE_TICKS} ticks is
 * skipped, except an {@code effect} whose {@code duration} has not run out, which is picked up part-way.
 *
 * <p>Also owns the per-tick upkeep of the other Task 11 visuals ({@link SpellVisuals}, {@link EffectVisuals},
 * {@link MemorySounds}), and the negative ids their client-only entities use. Cleared wherever
 * {@link MemoryActors#clear()} is, which is every reset path.
 */
public final class MemoryEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Events older than this at the moment they come due are old news (same rule as actor actions). */
    static final float LATE_TICKS = 5;

    private static List<SceneDef.EventDef> events = List.of();
    private static int next;
    private static ClientLevel level;
    private static BlockPos origin;
    private static final Set<String> WARNED = new HashSet<>();
    /** Ids for the client-only entities visuals add; negative so they can never meet a real entity's id. */
    private static int nextId = -2_000_000;

    private MemoryEvents() {
    }

    static int nextEntityId() {
        return nextId--;
    }

    /** Begin running {@code scene}'s events. Called as PLAY begins, by {@link MemoryActors#start}. */
    static void start(SceneDef scene, BlockPos at, ClientLevel clientLevel) {
        clear();
        if (scene == null || at == null || clientLevel == null) return;
        List<SceneDef.EventDef> sorted = new ArrayList<>(scene.events());
        sorted.removeIf(e -> "block".equals(e.kind()));
        sorted.sort(Comparator.comparingDouble(SceneDef.EventDef::t));
        events = sorted;
        next = 0;
        level = clientLevel;
        origin = at;
    }

    /** Forget the events and remove everything the visuals added. Safe at any time. */
    static void clear() {
        events = List.of();
        next = 0;
        level = null;
        origin = null;
        SpellVisuals.clear();
        EffectVisuals.clear();
        MemorySounds.clear();
        MemoryAudio.cut();
    }

    /** One client tick. */
    static void tick() {
        if (level == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level || !PensieveClientState.inMemory()) {
            clear();
            return;
        }
        if (mc.isPaused()) return;
        float t = PensieveClientState.sceneTime(0);
        while (next < events.size() && events.get(next).t() <= t) {
            SceneDef.EventDef e = events.get(next++);
            try {
                fire(e, t);
            } catch (RuntimeException ex) {
                LOGGER.warn("Pensieve: event '{}' at {} failed: {}", e.kind(), e.t(), ex.toString());
            }
        }
        SpellVisuals.tick(level);
        EffectVisuals.tick(level);
        MemorySounds.tick();
    }

    private static void fire(SceneDef.EventDef e, float now) {
        float age = now - e.t();
        boolean late = age > LATE_TICKS;
        String kind = e.kind();
        if ("effect".equals(kind)) {
            // A long effect is still going: join it part-way. A short or finished one is old news. A letter flood
            // never finishes (its letters lie on the floor until the scene ends), so it is always joined.
            if (!late || (e.duration() > 0 && age < e.duration()) || "letter_flood".equals(e.id())) {
                EffectVisuals.play(e.id(), position(e), e.duration(), late ? (int) age : 0, e.count(), e.spread(),
                        e.yaw(), e.t());
            }
            return;
        }
        if (late) return;
        if ("sound".equals(kind)) {
            MemorySounds.play(e.id(), e.at() != null ? position(e) : listener(), e.volume(), e.pitch());
        } else if ("music".equals(kind)) {
            MemoryAudio.playMusic(e.id(), e.volume());
        } else if ("voice".equals(kind)) {
            MemoryAudio.playVoice(e.id(), e.volume());
        } else if ("particles".equals(kind)) {
            particles(e);
        }
    }

    private static void particles(SceneDef.EventDef e) {
        ResourceLocation rl = e.id() == null ? null : ResourceLocation.tryParse(e.id());
        ParticleType<?> type = rl == null ? null : BuiltInRegistries.PARTICLE_TYPE.getOptional(rl).orElse(null);
        if (!(type instanceof ParticleOptions options)) {
            // Particles with parameters (dust, blocks, items) cannot be named by an id alone.
            if (WARNED.add(String.valueOf(e.id()))) LOGGER.warn("Pensieve: particle '{}' in a memory is not a plain particle", e.id());
            return;
        }
        Vec3 c = position(e);
        float[] s = e.spread() == null || e.spread().length < 3 ? new float[3] : e.spread();
        int count = Math.max(1, Math.min(e.count(), 500));
        for (int i = 0; i < count; i++) {
            level.addParticle(options,
                    c.x + (level.random.nextDouble() * 2 - 1) * s[0],
                    c.y + (level.random.nextDouble() * 2 - 1) * s[1],
                    c.z + (level.random.nextDouble() * 2 - 1) * s[2], 0, 0, 0);
        }
    }

    /** {@code origin + at} (the origin itself when the event has no position). */
    private static Vec3 position(SceneDef.EventDef e) {
        int[] at = e.at();
        return at == null || at.length < 3
                ? Vec3.atLowerCornerOf(origin)
                : new Vec3(origin.getX() + at[0], origin.getY() + at[1], origin.getZ() + at[2]);
    }

    /** Where a sound with no position is heard from: the viewer's own ears. */
    private static Vec3 listener() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getEyePosition() : Vec3.atLowerCornerOf(origin);
    }
}
