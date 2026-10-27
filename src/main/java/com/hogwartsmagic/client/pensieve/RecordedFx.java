package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.pensieve.capture.Recording;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.slf4j.Logger;

import java.util.List;

/**
 * The recorded particles and level events (block-break puffs, the door's click, an explosion's crater sound) of a
 * recorded memory, replayed at their tick. Each is fired once when the scene clock crosses its tick; a late joiner
 * skips the ones already more than {@link #LATE_TICKS} ticks old. Driven by {@link MemoryEvents}.
 */
final class RecordedFx {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** A particle or level event older than this when it comes due is old news. */
    static final float LATE_TICKS = 2;

    private static List<Recording.Particle> particles = List.of();
    private static List<Recording.LevelEvt> events = List.of();
    private static boolean active;
    private static ClientLevel level;
    private static BlockPos origin;
    private static int nextParticle;
    private static int nextEvent;
    private static boolean warned;

    private RecordedFx() {
    }

    static void start(Recording r, BlockPos at, ClientLevel clientLevel) {
        clear();
        if (r == null || at == null || clientLevel == null) return;
        particles = r.particles().stream().sorted(java.util.Comparator.comparingInt(Recording.Particle::tick)).toList();
        events = r.levelEvents().stream().sorted(java.util.Comparator.comparingInt(Recording.LevelEvt::tick)).toList();
        active = true;
        origin = at;
        level = clientLevel;
    }

    static void clear() {
        active = false;
        particles = List.of();
        events = List.of();
        level = null;
        origin = null;
        nextParticle = 0;
        nextEvent = 0;
        warned = false;
    }

    /** Fire everything that came due since the last tick, at scene time {@code t}. */
    static void tick(float t) {
        if (!active) return;
        while (nextParticle < particles.size() && particles.get(nextParticle).tick() <= t) {
            Recording.Particle p = particles.get(nextParticle++);
            if (t - p.tick() > LATE_TICKS) continue;
            try {
                particle(p);
            } catch (RuntimeException e) {
                warnOnce("particle", e);
            }
        }
        while (nextEvent < events.size() && events.get(nextEvent).tick() <= t) {
            Recording.LevelEvt e = events.get(nextEvent++);
            if (t - e.tick() > LATE_TICKS || !replayable(e.type())) continue;
            try {
                level.levelEvent(null, e.type(),
                        new BlockPos(origin.getX() + e.x(), origin.getY() + e.y(), origin.getZ() + e.z()), e.data());
            } catch (RuntimeException ex) {
                warnOnce("level event", ex);
            }
        }
    }

    /**
     * Level events not to replay: the jukebox (1010) starts a song nothing here would stop, and the global ones
     * (wither spawn, dragon death, end portal opening) are heard across the whole dimension.
     */
    static boolean replayable(int type) {
        return type != 1010 && type != 1023 && type != 1028 && type != 1038;
    }

    /** The vanilla client's handling of a particle packet: a spread of {@code count}, or one flung by the delta. */
    private static void particle(Recording.Particle p) {
        ParticleOptions options = ParticleTypes.STREAM_CODEC.decode(
                new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(p.options()), level.registryAccess()));
        double x = origin.getX() + p.x();
        double y = origin.getY() + p.y();
        double z = origin.getZ() + p.z();
        if (p.count() == 0) {
            level.addParticle(options, x, y, z, p.dx() * p.speed(), p.dy() * p.speed(), p.dz() * p.speed());
            return;
        }
        int count = Math.min(p.count(), 500);
        for (int i = 0; i < count; i++) {
            double ox = level.random.nextGaussian() * p.dx();
            double oy = level.random.nextGaussian() * p.dy();
            double oz = level.random.nextGaussian() * p.dz();
            double vx = level.random.nextGaussian() * p.speed();
            double vy = level.random.nextGaussian() * p.speed();
            double vz = level.random.nextGaussian() * p.speed();
            level.addParticle(options, x + ox, y + oy, z + oz, vx, vy, vz);
        }
    }

    private static void warnOnce(String what, RuntimeException e) {
        if (warned) return;
        warned = true;
        LOGGER.warn("Pensieve: a recorded {} could not be replayed (further ones are skipped silently): {}", what,
                e.toString());
    }
}
