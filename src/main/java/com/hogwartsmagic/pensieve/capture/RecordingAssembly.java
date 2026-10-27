package com.hogwartsmagic.pensieve.capture;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.pensieve.PensieveDimension;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.Util;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Turns the rolling logs into a {@link Recording}. Only the section copy and the copying of log slices run on the
 * server thread; rewinding the blocks, selecting entities, converting and thinning run on the background executor.
 */
public final class RecordingAssembly {
    public static final String REFUSED = "capture.refused";
    public static final String TOO_SOON = "capture.too_soon";
    public static final String TOO_BIG = "capture.too_big";

    /** Why a capture failed; {@code key} is a translation key suffix the caller can show on the action bar. */
    public static final class CaptureException extends RuntimeException {
        public final String key;

        public CaptureException(String key) {
            super(key);
            this.key = key;
        }
    }

    /** The reason of a failed assembly future, whichever way it was wrapped. */
    public static String failureKey(Throwable t) {
        while (t != null) {
            if (t instanceof CaptureException ce) {
                return ce.key;
            }
            t = t.getCause();
        }
        return null;
    }

    private static final int SOUND_MARGIN = 16;
    private static volatile boolean[] opaqueById;

    private RecordingAssembly() {
    }

    // ---- the server-thread half ----

    private record BeamCopy(long from, long to, String spell, UUID caster, double sx, double sy, double sz,
                            double ex, double ey, double ez) {
    }

    private static final class Prepared {
        UUID id, owner;
        String ownerName, title, dimension;
        long start, now, day, dayTime;
        int length, radius;
        int[] centre;
        RegistryAccess access;
        boolean[] opaque;
        final Long2ObjectOpenHashMap<PalettedContainer<BlockState>> sections = new Long2ObjectOpenHashMap<>();
        final List<BlockRewind.Change> changes = new ArrayList<>();
        final List<EntityHistory.Slice> slices = new ArrayList<>();
        final List<BeamCopy> beams = new ArrayList<>();
        final List<LevelLog.CastRec> casts = new ArrayList<>();
        final List<Recording.Sound> sounds = new ArrayList<>();
        final List<LevelLog.ParticleRec> particles = new ArrayList<>();
        final List<Recording.LevelEvt> levelEvents = new ArrayList<>();
        final List<Recording.BlockEntityData> blockEntities = new ArrayList<>();
        final List<Recording.Chat> chat = new ArrayList<>();
    }

    /**
     * Captures {@code owner}'s surroundings over the last {@code pensieveCaptureSeconds}. Fails with a
     * {@link CaptureException} ({@link #TOO_SOON}, {@link #REFUSED}, {@link #TOO_BIG}) rather than returning null.
     */
    public static CompletableFuture<Recording> assemble(ServerPlayer owner, UUID id, String title) {
        MinecraftServer server = owner.getServer();
        if (server != null && !server.isSameThread()) {
            return CompletableFuture.supplyAsync(() -> assemble(owner, id, title), server).thenCompose(f -> f);
        }
        Prepared p;
        try {
            p = prepare(owner, id, title);
        } catch (CaptureException e) {
            return CompletableFuture.failedFuture(e);
        }
        return CompletableFuture.supplyAsync(() -> build(p), Util.backgroundExecutor());
    }

    private static boolean[] opaque() {
        boolean[] o = opaqueById;
        if (o == null) {
            int n = Block.BLOCK_STATE_REGISTRY.size();
            o = new boolean[n];
            for (int i = 0; i < n; i++) {
                BlockState s = Block.stateById(i);
                o[i] = s != null && s.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            }
            opaqueById = o;
        }
        return o;
    }

    private static Prepared prepare(ServerPlayer owner, UUID id, String title) {
        ServerLevel level = owner.serverLevel();
        if (!Config.pensieveCaptureEnabled || PensieveDimension.isMemory(level)) {
            throw new CaptureException(REFUSED);
        }
        LevelLog log = CaptureLogs.of(level);
        if (log == null) {
            throw new CaptureException(REFUSED);
        }
        EntityHistory ownerHistory = log.entities.get(owner.getUUID());
        if (ownerHistory == null || ownerHistory.count() == 0) {
            throw new CaptureException(TOO_SOON);
        }
        int lengthTicks = LevelLog.windowTicks();
        int radius = Config.pensieveCaptureRadius;
        long now = Math.min(level.getGameTime(), ownerHistory.lastTick());
        BlockPos c = owner.blockPosition();

        long from = Math.max(ownerHistory.firstTick(), now - lengthTicks + 1);
        int n = (int) (now - from + 1);
        if (n <= 0) {
            throw new CaptureException(TOO_SOON);
        }
        long[] ownerTicks = new long[n];
        double[] ownerXyz = new double[n * 3];
        for (int i = 0; i < n; i++) {
            long t = from + i;
            ownerTicks[i] = t;
            ownerXyz[i * 3] = ownerHistory.worldX(t);
            ownerXyz[i * 3 + 1] = ownerHistory.worldY(t);
            ownerXyz[i * 3 + 2] = ownerHistory.worldZ(t);
        }
        PlayerChatLog chat = CaptureLogs.chat(owner.getUUID());
        long start = WindowRules.start(now, lengthTicks, log.validSince(), chat.lastDiscontinuity, ownerTicks,
                ownerXyz, new double[] {c.getX(), c.getY(), c.getZ()}, radius);
        if (!WindowRules.usable(start, now)) {
            throw new CaptureException(TOO_SOON);
        }

        Prepared p = new Prepared();
        p.id = id;
        p.owner = owner.getUUID();
        p.ownerName = owner.getGameProfile().getName();
        p.title = title;
        p.dimension = level.dimension().location().toString();
        p.start = start;
        p.now = now;
        p.length = (int) (now - start + 1);
        p.radius = radius;
        p.centre = new int[] {c.getX(), c.getY(), c.getZ()};
        p.day = level.getDayTime() / 24000L;
        p.dayTime = level.getDayTime() - (now - start);
        p.access = level.registryAccess();
        p.opaque = opaque();

        copySections(level, p);
        copyChanges(log, p);
        copySlices(log, ownerHistory, p);
        copyEvents(level, log, p);
        for (PlayerChatLog.Line l : chat.since(start)) {
            p.chat.add(new Recording.Chat((int) (l.tick() - start), l.sender(), l.text()));
        }
        return p;
    }

    private static void copySections(ServerLevel level, Prepared p) {
        int size = p.radius;
        int cx = p.centre[0], cy = p.centre[1], cz = p.centre[2];
        int minY = Math.max(cy - size, level.getMinBuildHeight());
        int maxY = Math.min(cy + size, level.getMaxBuildHeight() - 1);
        if (minY > maxY) {
            return;
        }
        for (int chunkX = (cx - size) >> 4; chunkX <= (cx + size) >> 4; chunkX++) {
            for (int chunkZ = (cz - size) >> 4; chunkZ <= (cz + size) >> 4; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue; // unloaded: reads as air
                }
                for (int sy = SectionPos.blockToSectionCoord(minY); sy <= SectionPos.blockToSectionCoord(maxY); sy++) {
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
                    if (section.hasOnlyAir()) {
                        continue;
                    }
                    p.sections.put(SectionPos.asLong(chunkX, sy, chunkZ), section.getStates().copy());
                }
                for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
                    BlockEntity be = e.getValue();
                    if (!(be instanceof SignBlockEntity || be instanceof BannerBlockEntity
                            || be instanceof SkullBlockEntity)) {
                        continue;
                    }
                    BlockPos pos = e.getKey();
                    int dx = pos.getX() - cx, dy = pos.getY() - cy, dz = pos.getZ() - cz;
                    if (Math.abs(dx) > size || Math.abs(dy) > size || Math.abs(dz) > size) {
                        continue;
                    }
                    p.blockEntities.add(new Recording.BlockEntityData(dx, dy, dz,
                            be.saveWithoutMetadata(level.registryAccess()).toString()));
                }
            }
        }
    }

    private static void copyChanges(LevelLog log, Prepared p) {
        int size = p.radius;
        long r2 = (long) size * size;
        log.forEachChange(p.start, (tick, pos, oldState, newState) -> {
            if (tick > p.now) {
                return;
            }
            int dx = BlockPos.getX(pos) - p.centre[0];
            int dy = BlockPos.getY(pos) - p.centre[1];
            int dz = BlockPos.getZ(pos) - p.centre[2];
            if ((long) dx * dx + (long) dy * dy + (long) dz * dz > r2) {
                return;
            }
            p.changes.add(new BlockRewind.Change(tick, Recording.Blocks.index(dx, dy, dz, size), oldState, newState));
        });
    }

    private static void copySlices(LevelLog log, EntityHistory ownerHistory, Prepared p) {
        EntityHistory.Slice self = ownerHistory.copy(p.start, p.now, p.now);
        if (self == null) {
            throw new CaptureException(TOO_SOON);
        }
        p.slices.add(self);
        record Near(EntityHistory h, double d2) {
        }
        List<Near> players = new ArrayList<>();
        List<Near> others = new ArrayList<>();
        for (EntityHistory h : log.entities.values()) {
            if (h == ownerHistory || h.count() == 0 || h.lastTick() < p.start) {
                continue;
            }
            double dx = h.worldX(h.lastTick()) - p.centre[0];
            double dy = h.worldY(h.lastTick()) - p.centre[1];
            double dz = h.worldZ(h.lastTick()) - p.centre[2];
            (h.player ? players : others).add(new Near(h, dx * dx + dy * dy + dz * dz));
        }
        others.sort(Comparator.comparingDouble(Near::d2));
        // The exact choice is made off-thread by EntitySelect; copying twice the cap keeps this cheap on a busy server.
        int budget = Math.max(1, Config.pensieveCaptureMaxEntities) * 2;
        for (Near n : players) {
            EntityHistory.Slice s = n.h.copy(p.start, p.now, p.now);
            if (s != null) {
                p.slices.add(s);
            }
        }
        for (Near n : others) {
            if (budget-- <= 0) {
                break;
            }
            EntityHistory.Slice s = n.h.copy(p.start, p.now, p.now);
            if (s != null) {
                p.slices.add(s);
            }
        }
    }

    private static boolean inBox(double x, double y, double z, int[] centre, int half) {
        return Math.abs(x - centre[0]) <= half && Math.abs(y - centre[1]) <= half && Math.abs(z - centre[2]) <= half;
    }

    private static void copyEvents(ServerLevel level, LevelLog log, Prepared p) {
        int[] c = p.centre;
        int half = p.radius;
        for (LevelLog.SoundRec s : log.sounds) {
            if (s.tick() >= p.start && s.tick() <= p.now && inBox(s.x(), s.y(), s.z(), c, half + SOUND_MARGIN)) {
                p.sounds.add(new Recording.Sound((int) (s.tick() - p.start), s.id().toString(), s.x() - c[0],
                        s.y() - c[1], s.z() - c[2], s.volume(), s.pitch()));
            }
        }
        for (LevelLog.ParticleRec r : log.particles) {
            if (r.tick() >= p.start && r.tick() <= p.now && inBox(r.x(), r.y(), r.z(), c, half)) {
                p.particles.add(r);
            }
        }
        for (LevelLog.LevelEvtRec e : log.levelEvents) {
            if (e.tick() < p.start || e.tick() > p.now) {
                continue;
            }
            int dx = BlockPos.getX(e.pos()) - c[0], dy = BlockPos.getY(e.pos()) - c[1], dz = BlockPos.getZ(e.pos()) - c[2];
            if (Math.abs(dx) <= half && Math.abs(dy) <= half && Math.abs(dz) <= half) {
                p.levelEvents.add(new Recording.LevelEvt((int) (e.tick() - p.start), e.type(), dx, dy, dz, e.data()));
            }
        }
        for (LevelLog.CastRec cast : log.casts) {
            if (cast.tick() >= p.start && cast.tick() <= p.now) {
                p.casts.add(cast);
            }
        }
        for (LevelLog.BeamRec b : log.beamsBetween(p.start, p.now)) {
            double ex = b.ex, ey = b.ey, ez = b.ez;
            if (b.to < 0) {
                Entity live = level.getEntity(b.entityId);
                if (live != null) {
                    ex = live.getX();
                    ey = live.getY();
                    ez = live.getZ();
                }
            }
            if (inBox(b.sx, b.sy, b.sz, c, half + SOUND_MARGIN) || inBox(ex, ey, ez, c, half + SOUND_MARGIN)) {
                p.beams.add(new BeamCopy(b.from, b.to, b.spell, b.caster, b.sx, b.sy, b.sz, ex, ey, ez));
            }
        }
    }

    // ---- the background half ----

    private static Recording build(Prepared p) {
        int size = p.radius;
        int d = 2 * size + 1;
        int[] cells = currentCells(p, size, d);

        int[] norm = new int[Block.BLOCK_STATE_REGISTRY.size()];
        Arrays.fill(norm, -1);
        List<BlockRewind.Change> changes = new ArrayList<>(p.changes.size());
        Set<Integer> keep = new HashSet<>();
        for (BlockRewind.Change ch : p.changes) {
            changes.add(new BlockRewind.Change(ch.tick(), ch.index(), normalise(norm, ch.oldState()),
                    normalise(norm, ch.newState())));
            keep.add(ch.index());
        }
        int[] start = BlockRewind.rewind(cells, changes);
        SnapshotCull.cull(start, size, p.radius, id -> id >= 0 && id < p.opaque.length && p.opaque[id], keep);

        List<String> palette = new ArrayList<>();
        palette.add("minecraft:air");
        Map<Integer, Integer> paletteIndex = new HashMap<>();
        for (int i = 0; i < start.length; i++) {
            if (start[i] > 0) {
                start[i] = paletteOf(start[i], palette, paletteIndex);
            }
        }
        List<Recording.BlockChange> blockChanges = new ArrayList<>(changes.size());
        for (BlockRewind.Change ch : BlockRewind.forward(changes)) {
            int x = ch.index() % d - size;
            int z = (ch.index() / d) % d - size;
            int y = ch.index() / (d * d) - size;
            blockChanges.add(new Recording.BlockChange((int) (ch.tick() - p.start), x, y, z,
                    paletteOf(ch.newState(), palette, paletteIndex)));
        }
        List<Recording.BlockEntityData> blockEntities = new ArrayList<>();
        for (Recording.BlockEntityData be : p.blockEntities) {
            if (start[Recording.Blocks.index(be.x(), be.y(), be.z(), size)] >= 0) {
                blockEntities.add(be);
            }
        }
        Recording.Blocks blocks = new Recording.Blocks(palette, size, start, blockEntities, blockChanges);

        // entities
        List<EntitySelect.Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < p.slices.size(); i++) {
            EntityHistory.Slice s = p.slices.get(i);
            candidates.add(new EntitySelect.Candidate(i, s.player, i == 0, minDistance(s, p.centre)));
        }
        List<Integer> picked = EntitySelect.pick(candidates, Math.max(1, Config.pensieveCaptureMaxEntities));
        Map<UUID, Integer> actorOf = new HashMap<>();
        List<Recording.Track> tracks = new ArrayList<>();
        for (int actor = 0; actor < picked.size(); actor++) {
            EntityHistory.Slice s = p.slices.get(picked.get(actor));
            actorOf.put(s.uuid, actor);
            tracks.add(track(actor, s, p));
        }

        List<Recording.Beam> beams = new ArrayList<>();
        for (BeamCopy b : p.beams) {
            int from = (int) (Math.max(b.from(), p.start) - p.start);
            int to = (int) ((b.to() < 0 ? p.now : Math.min(b.to(), p.now)) - p.start);
            Integer caster = b.caster() == null ? null : actorOf.get(b.caster());
            beams.add(new Recording.Beam(from, Math.max(from, to), b.spell(),
                    rel(b.sx(), b.sy(), b.sz(), p.centre), rel(b.ex(), b.ey(), b.ez(), p.centre),
                    caster == null ? -1 : caster));
        }
        List<Recording.Cast> casts = new ArrayList<>();
        for (LevelLog.CastRec c : p.casts) {
            Integer actor = actorOf.get(c.caster());
            if (actor != null) {
                casts.add(new Recording.Cast((int) (c.tick() - p.start), actor, c.spell(),
                        new float[] {c.tx() - p.centre[0], c.ty() - p.centre[1], c.tz() - p.centre[2]}));
            }
        }
        List<Recording.Particle> particles = new ArrayList<>(p.particles.size());
        for (LevelLog.ParticleRec r : p.particles) {
            particles.add(new Recording.Particle((int) (r.tick() - p.start), (float) (r.x() - p.centre[0]),
                    (float) (r.y() - p.centre[1]), (float) (r.z() - p.centre[2]), (float) r.dx(), (float) r.dy(),
                    (float) r.dz(), (float) r.speed(), r.count(), encode(r.options(), p.access)));
        }

        Recording recording = new Recording(p.id, p.owner, p.ownerName, p.title, p.day, p.dimension, p.centre,
                p.radius, p.length, p.dayTime, blocks, tracks, beams, casts, p.sounds, particles, p.levelEvents,
                p.chat);
        return thin(recording, Config.pensieveCaptureMaxKb * 1024);
    }

    private static int[] currentCells(Prepared p, int size, int d) {
        int[] cells = new int[d * d * d];
        long lastKey = Long.MIN_VALUE;
        PalettedContainer<BlockState> container = null;
        for (int dy = -size; dy <= size; dy++) {
            int wy = p.centre[1] + dy;
            for (int dz = -size; dz <= size; dz++) {
                int wz = p.centre[2] + dz;
                for (int dx = -size; dx <= size; dx++) {
                    int wx = p.centre[0] + dx;
                    long key = SectionPos.asLong(wx >> 4, wy >> 4, wz >> 4);
                    if (key != lastKey) {
                        lastKey = key;
                        container = p.sections.get(key);
                    }
                    if (container != null) {
                        BlockState s = container.get(wx & 15, wy & 15, wz & 15);
                        if (!s.isAir()) {
                            cells[Recording.Blocks.index(dx, dy, dz, size)] = Block.getId(s);
                        }
                    }
                }
            }
        }
        return cells;
    }

    private static int normalise(int[] cache, int id) {
        if (id <= 0) {
            return 0;
        }
        int v = cache[id];
        if (v < 0) {
            v = Block.stateById(id).isAir() ? 0 : id;
            cache[id] = v;
        }
        return v;
    }

    private static int paletteOf(int id, List<String> palette, Map<Integer, Integer> index) {
        if (id <= 0) {
            return 0;
        }
        Integer known = index.get(id);
        if (known != null) {
            return known;
        }
        palette.add(BlockStateParser.serialize(Block.stateById(id)));
        index.put(id, palette.size() - 1);
        return palette.size() - 1;
    }

    private static float[] rel(double x, double y, double z, int[] centre) {
        return new float[] {(float) (x - centre[0]), (float) (y - centre[1]), (float) (z - centre[2])};
    }

    private static double minDistance(EntityHistory.Slice s, int[] centre) {
        double best = Double.MAX_VALUE;
        int n = s.lanes[0].length;
        for (int i = 0; i < n; i++) {
            double dx = s.ox + s.lanes[EntityHistory.X][i] - centre[0];
            double dy = s.oy + s.lanes[EntityHistory.Y][i] - centre[1];
            double dz = s.oz + s.lanes[EntityHistory.Z][i] - centre[2];
            best = Math.min(best, dx * dx + dy * dy + dz * dz);
        }
        return Math.sqrt(best);
    }

    private static Recording.Track track(int actor, EntityHistory.Slice s, Prepared p) {
        int n = s.lanes[0].length;
        float[] x = new float[n], y = new float[n], z = new float[n];
        for (int i = 0; i < n; i++) {
            x[i] = (float) (s.ox + s.lanes[EntityHistory.X][i] - p.centre[0]);
            y[i] = (float) (s.oy + s.lanes[EntityHistory.Y][i] - p.centre[1]);
            z[i] = (float) (s.oz + s.lanes[EntityHistory.Z][i] - p.centre[2]);
        }
        int first = (int) (s.first - p.start);
        List<Recording.DataPatch> data = new ArrayList<>();
        for (EntityHistory.DataRec d : s.data) {
            data.add(new Recording.DataPatch((int) Math.max(0, d.tick - p.start), d.key, d.value));
        }
        List<Recording.EquipPatch> equipment = new ArrayList<>();
        for (EntityHistory.EquipRec q : s.equipment) {
            equipment.add(new Recording.EquipPatch((int) Math.max(0, q.tick - p.start), q.slot, q.stack));
        }
        List<Recording.Act> acts = new ArrayList<>();
        for (EntityHistory.ActRec a : s.acts) {
            acts.add(new Recording.Act((int) (a.tick() - p.start), a.kind()));
        }
        int removal = s.killed ? Recording.REMOVAL_KILLED : s.gone ? Recording.REMOVAL_GONE : Recording.REMOVAL_NONE;
        return new Recording.Track(actor, s.type, s.uuid, s.name, s.player, s.skinValue, s.skinSignature, first,
                first + n - 1, removal, x, y, z, s.lanes[EntityHistory.YAW], s.lanes[EntityHistory.HEAD],
                s.lanes[EntityHistory.BODY], s.lanes[EntityHistory.PITCH], data, equipment, acts);
    }

    private static byte[] encode(ParticleOptions options, RegistryAccess access) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(32), access);
        ParticleTypes.STREAM_CODEC.encode(buf, options);
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return out;
    }

    // ---- thinning ----

    private static int encodedSize(Recording r) {
        return RecordingCodec.write(r, false).length;
    }

    private static Recording with(Recording r, List<Recording.Track> tracks, List<Recording.Beam> beams,
                                  List<Recording.Cast> casts, List<Recording.Particle> particles) {
        return new Recording(r.id(), r.owner(), r.ownerName(), r.title(), r.day(), r.dimension(), r.centre(), r.radius(),
                r.length(), r.dayTime(), r.blocks(), tracks, beams, casts, r.sounds(), particles, r.levelEvents(),
                r.chat());
    }

    private static double trackDistance(Recording.Track t) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < t.x().length; i++) {
            best = Math.min(best, (double) t.x()[i] * t.x()[i] + (double) t.y()[i] * t.y()[i]
                    + (double) t.z()[i] * t.z()[i]);
        }
        return best;
    }

    /**
     * Makes the recording fit {@code maxBytes} once encoded: first every particle goes, then the farthest tracks
     * other than the owner's, as few as it takes. Throws {@link #TOO_BIG} when the owner and the blocks alone do not fit.
     */
    static Recording thin(Recording r, int maxBytes) {
        if (encodedSize(r) <= maxBytes) {
            return r;
        }
        Recording noParticles = with(r, r.tracks(), r.beams(), r.casts(), List.of());
        if (encodedSize(noParticles) <= maxBytes) {
            return noParticles;
        }
        List<Recording.Track> farthestFirst = new ArrayList<>();
        for (Recording.Track t : r.tracks()) {
            if (t.uuid() == null || !t.uuid().equals(r.owner())) {
                farthestFirst.add(t);
            }
        }
        farthestFirst.sort(Comparator.comparingDouble(RecordingAssembly::trackDistance).reversed());
        if (farthestFirst.isEmpty() || encodedSize(dropFirst(noParticles, farthestFirst, farthestFirst.size())) > maxBytes) {
            throw new CaptureException(TOO_BIG);
        }
        int lo = 1;
        int hi = farthestFirst.size();
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (encodedSize(dropFirst(noParticles, farthestFirst, mid)) <= maxBytes) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return dropFirst(noParticles, farthestFirst, lo);
    }

    private static Recording dropFirst(Recording r, List<Recording.Track> farthestFirst, int count) {
        Set<Integer> dropped = new HashSet<>();
        for (int i = 0; i < count; i++) {
            dropped.add(farthestFirst.get(i).actor());
        }
        List<Recording.Track> tracks = new ArrayList<>();
        for (Recording.Track t : r.tracks()) {
            if (!dropped.contains(t.actor())) {
                tracks.add(t);
            }
        }
        List<Recording.Cast> casts = new ArrayList<>();
        for (Recording.Cast c : r.casts()) {
            if (!dropped.contains(c.actor())) {
                casts.add(c);
            }
        }
        List<Recording.Beam> beams = new ArrayList<>();
        for (Recording.Beam b : r.beams()) {
            beams.add(dropped.contains(b.caster())
                    ? new Recording.Beam(b.from(), b.to(), b.spell(), b.start(), b.end(), -1) : b);
        }
        return with(r, tracks, beams, casts, r.particles());
    }

    // kept for tests that want the pure ordering of the dropped tracks
    static List<Integer> droppedActors(Recording before, Recording after) {
        Set<Integer> kept = new HashSet<>();
        for (Recording.Track t : after.tracks()) {
            kept.add(t.actor());
        }
        List<Integer> out = new ArrayList<>();
        for (Recording.Track t : before.tracks()) {
            if (!kept.contains(t.actor())) {
                out.add(t.actor());
            }
        }
        return out;
    }
}
