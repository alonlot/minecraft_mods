package com.hogwartsmagic.pensieve.capture;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.pensieve.PensieveBodyEntity;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The rolling record of one level: block changes, sampled entities and events, all in the level game time. Kept to
 * a few seconds more than a capture window. Server thread only; every hook ignores positions outside the chunks
 * around players.
 */
public final class LevelLog {
    public record SoundRec(long tick, ResourceLocation id, float x, float y, float z, float volume, float pitch) {
    }

    public record ParticleRec(long tick, ParticleOptions options, double x, double y, double z, int count,
                              double dx, double dy, double dz, double speed) {
    }

    public record LevelEvtRec(long tick, int type, long pos, int data) {
    }

    public record CastRec(long tick, UUID caster, String spell, float tx, float ty, float tz) {
    }

    /** {@code to} is -1 while the beam is still flying. */
    public static final class BeamRec {
        public final int entityId;
        public final long from;
        public long to = -1;
        public final String spell;
        public final UUID caster;
        public final double sx, sy, sz;
        public double ex, ey, ez;

        BeamRec(int entityId, long from, String spell, UUID caster, double x, double y, double z) {
            this.entityId = entityId;
            this.from = from;
            this.spell = spell;
            this.caster = caster;
            this.sx = x;
            this.sy = y;
            this.sz = z;
            this.ex = x;
            this.ey = y;
            this.ez = z;
        }
    }

    @FunctionalInterface
    public interface ChangeVisitor {
        void visit(long tick, long pos, int oldState, int newState);
    }

    private static final int MAX_SOUNDS = 6000;
    private static final int MAX_PARTICLES = 40000;
    private static final int MAX_LEVEL_EVENTS = 6000;
    private static final int MAX_CASTS = 500;
    private static final int MAX_BEAMS = 500;
    /** How long a history lives after its entity was last sampled. */
    private static final int HISTORY_GRACE = 20 * 60;
    /** Arrows, items and orbs are only worth keeping this long after they vanish. */
    private static final int NON_LIVING_GRACE = 60;
    private static final int TRIM_EVERY = 20;

    private final int maxChanges;
    private final LongOpenHashSet watched = new LongOpenHashSet();
    private double[] playerPos = new double[0];
    private int watchers;
    private long validSince;
    private long lastTrim;

    private long[] bTick = new long[0];
    private long[] bPos = new long[0];
    private int[] bOld = new int[0];
    private int[] bNew = new int[0];
    private int bHead;
    private int bSize;

    public final Map<UUID, EntityHistory> entities = new HashMap<>();
    public final ArrayDeque<SoundRec> sounds = new ArrayDeque<>();
    public final ArrayDeque<ParticleRec> particles = new ArrayDeque<>();
    public final ArrayDeque<LevelEvtRec> levelEvents = new ArrayDeque<>();
    public final ArrayDeque<CastRec> casts = new ArrayDeque<>();
    public final Map<Integer, BeamRec> openBeams = new HashMap<>();
    public final ArrayDeque<BeamRec> closedBeams = new ArrayDeque<>();

    public LevelLog(long now) {
        this.maxChanges = Config.pensieveCaptureMaxBlockChanges;
        this.validSince = now;
        this.lastTrim = now;
    }

    /** First game tick from which the block log is complete. */
    public long validSince() {
        return validSince;
    }

    public static int windowTicks() {
        return Config.pensieveCaptureSeconds * 20;
    }

    // ---- watching ----

    public boolean isWatched(int blockX, int blockZ) {
        return watched.contains(ChunkPos.asLong(blockX >> 4, blockZ >> 4));
    }

    public boolean isWatched(double x, double z) {
        return isWatched(Mth.floor(x), Mth.floor(z));
    }

    private void rebuildWatched(ServerLevel level) {
        watched.clear();
        int reach = Config.pensieveCaptureRadius + WindowRules.WATCH_MARGIN;
        List<ServerPlayer> players = level.players();
        if (playerPos.length < players.size() * 3) {
            playerPos = new double[players.size() * 3];
        }
        int n = 0;
        for (ServerPlayer p : players) {
            if (p.isSpectator()) {
                continue;
            }
            playerPos[n * 3] = p.getX();
            playerPos[n * 3 + 1] = p.getY();
            playerPos[n * 3 + 2] = p.getZ();
            n++;
            int x0 = Mth.floor(p.getX() - reach) >> 4;
            int x1 = Mth.floor(p.getX() + reach) >> 4;
            int z0 = Mth.floor(p.getZ() - reach) >> 4;
            int z1 = Mth.floor(p.getZ() + reach) >> 4;
            for (int cx = x0; cx <= x1; cx++) {
                for (int cz = z0; cz <= z1; cz++) {
                    watched.add(ChunkPos.asLong(cx, cz));
                }
            }
        }
        watchers = n;
    }

    private boolean nearAPlayer(Entity e, double r2) {
        for (int i = 0; i < watchers; i++) {
            double dx = e.getX() - playerPos[i * 3];
            double dy = e.getY() - playerPos[i * 3 + 1];
            double dz = e.getZ() - playerPos[i * 3 + 2];
            if (dx * dx + dy * dy + dz * dz <= r2) {
                return true;
            }
        }
        return false;
    }

    // ---- block changes ----

    public void addBlockChange(long tick, long pos, int oldState, int newState) {
        if (bSize == bTick.length) {
            if (bSize < maxChanges) {
                grow();
            } else {
                // Full: forget the oldest. If it is still inside the window the log no longer reaches that far back.
                if (bTick[bHead] >= tick - windowTicks()) {
                    validSince = Math.max(validSince, bTick[bHead] + 1);
                }
                bHead = (bHead + 1) % bTick.length;
                bSize--;
            }
        }
        int i = (bHead + bSize) % bTick.length;
        bTick[i] = tick;
        bPos[i] = pos;
        bOld[i] = oldState;
        bNew[i] = newState;
        bSize++;
    }

    private void grow() {
        int cap = Math.min(maxChanges, Math.max(1024, bTick.length * 2));
        long[] t = new long[cap];
        long[] p = new long[cap];
        int[] o = new int[cap];
        int[] nw = new int[cap];
        for (int k = 0; k < bSize; k++) {
            int i = (bHead + k) % bTick.length;
            t[k] = bTick[i];
            p[k] = bPos[i];
            o[k] = bOld[i];
            nw[k] = bNew[i];
        }
        bTick = t;
        bPos = p;
        bOld = o;
        bNew = nw;
        bHead = 0;
    }

    public int blockChangeCount() {
        return bSize;
    }

    /** Visits the logged changes from {@code fromTick} on, oldest first. */
    public void forEachChange(long fromTick, ChangeVisitor visitor) {
        // The ring is chronological, so walk back from the newest to find where the slice starts.
        int k = bSize;
        while (k > 0 && bTick[(bHead + k - 1) % bTick.length] >= fromTick) {
            k--;
        }
        for (int j = k; j < bSize; j++) {
            int i = (bHead + j) % bTick.length;
            visitor.visit(bTick[i], bPos[i], bOld[i], bNew[i]);
        }
    }

    // ---- events ----

    public void addSound(SoundRec r) {
        if (sounds.size() >= MAX_SOUNDS) {
            sounds.pollFirst();
        }
        sounds.addLast(r);
    }

    public void addParticle(ParticleRec r) {
        if (particles.size() >= MAX_PARTICLES) {
            particles.pollFirst();
        }
        particles.addLast(r);
    }

    public void addLevelEvent(LevelEvtRec r) {
        if (levelEvents.size() >= MAX_LEVEL_EVENTS) {
            levelEvents.pollFirst();
        }
        levelEvents.addLast(r);
    }

    public void addCast(CastRec r) {
        if (casts.size() >= MAX_CASTS) {
            casts.pollFirst();
        }
        casts.addLast(r);
    }

    public void openBeam(int entityId, long tick, String spell, UUID caster, double x, double y, double z) {
        if (openBeams.size() >= MAX_BEAMS) {
            return;
        }
        openBeams.put(entityId, new BeamRec(entityId, tick, spell, caster, x, y, z));
    }

    public void closeBeam(int entityId, long tick, double x, double y, double z) {
        BeamRec b = openBeams.remove(entityId);
        if (b == null) {
            return;
        }
        b.to = tick;
        b.ex = x;
        b.ey = y;
        b.ez = z;
        if (closedBeams.size() >= MAX_BEAMS) {
            closedBeams.pollFirst();
        }
        closedBeams.addLast(b);
    }

    /** The open and closed beams that overlap {@code from..to}. */
    public List<BeamRec> beamsBetween(long from, long to) {
        List<BeamRec> out = new ArrayList<>();
        for (BeamRec b : closedBeams) {
            if (b.to >= from && b.from <= to) {
                out.add(b);
            }
        }
        for (BeamRec b : openBeams.values()) {
            if (b.from <= to) {
                out.add(b);
            }
        }
        return out;
    }

    // ---- per tick ----

    /** Called at the end of every server-side level tick. */
    public void tick(ServerLevel level, long now) {
        rebuildWatched(level);
        sampleEntities(level, now);
        if (now - lastTrim >= TRIM_EVERY) {
            lastTrim = now;
            trim(now);
        }
    }

    private void sampleEntities(ServerLevel level, long now) {
        if (watchers == 0) {
            return;
        }
        double reach = Config.pensieveCaptureRadius + WindowRules.WATCH_MARGIN;
        double r2 = reach * reach;
        int capTicks = windowTicks() + 2 * TRIM_EVERY + 40 + 20;
        for (Entity e : level.getAllEntities()) {
            if (e.isRemoved() || e.isSpectator() || e instanceof SpellBeamEntity || e instanceof PensieveBodyEntity) {
                continue;
            }
            if (!isWatched(e.getX(), e.getZ()) || !nearAPlayer(e, r2)) {
                continue;
            }
            EntityHistory h = entities.get(e.getUUID());
            if (h == null) {
                h = new EntityHistory(e.getUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString(),
                        e instanceof ServerPlayer, e instanceof LivingEntity, capTicks);
                entities.put(e.getUUID(), h);
            }
            h.sample(e, now, level);
        }
    }

    /**
     * Data changes made between ticks (a packet handler, a command) are still flagged dirty at the start of the next
     * server tick, before that tick's send clears them. Picks those up.
     */
    public void sampleLateData(ServerLevel level, long now) {
        if (entities.isEmpty()) {
            return;
        }
        for (EntityHistory h : entities.values()) {
            Entity e = h.ref;
            if (e != null && !e.isRemoved() && h.lastTick() >= now - 1 && e.getEntityData().isDirty()) {
                h.sampleData(e, now, level.registryAccess());
            }
        }
    }

    private void trim(long now) {
        long cutoff = now - windowTicks() - 40;
        while (bSize > 0 && bTick[bHead] < cutoff) {
            bHead = (bHead + 1) % bTick.length;
            bSize--;
        }
        if (bSize == 0) {
            bHead = 0;
        }
        for (Iterator<EntityHistory> it = entities.values().iterator(); it.hasNext(); ) {
            EntityHistory h = it.next();
            if (now - h.lastTick() > (h.living ? HISTORY_GRACE : NON_LIVING_GRACE)) {
                it.remove();
                continue;
            }
            if (now - h.lastTick() > 1) {
                h.ref = null;
            }
            h.trim(cutoff);
        }
        while (!sounds.isEmpty() && sounds.peekFirst().tick() < cutoff) {
            sounds.pollFirst();
        }
        while (!particles.isEmpty() && particles.peekFirst().tick() < cutoff) {
            particles.pollFirst();
        }
        while (!levelEvents.isEmpty() && levelEvents.peekFirst().tick() < cutoff) {
            levelEvents.pollFirst();
        }
        while (!casts.isEmpty() && casts.peekFirst().tick() < cutoff) {
            casts.pollFirst();
        }
        while (!closedBeams.isEmpty() && closedBeams.peekFirst().to < cutoff) {
            closedBeams.pollFirst();
        }
    }
}
