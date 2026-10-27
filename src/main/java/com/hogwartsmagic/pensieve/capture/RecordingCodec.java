package com.hogwartsmagic.pensieve.capture;

import com.hogwartsmagic.pensieve.capture.Recording.Act;
import com.hogwartsmagic.pensieve.capture.Recording.Beam;
import com.hogwartsmagic.pensieve.capture.Recording.BlockChange;
import com.hogwartsmagic.pensieve.capture.Recording.BlockEntityData;
import com.hogwartsmagic.pensieve.capture.Recording.Blocks;
import com.hogwartsmagic.pensieve.capture.Recording.Cast;
import com.hogwartsmagic.pensieve.capture.Recording.Chat;
import com.hogwartsmagic.pensieve.capture.Recording.DataPatch;
import com.hogwartsmagic.pensieve.capture.Recording.EquipPatch;
import com.hogwartsmagic.pensieve.capture.Recording.LevelEvt;
import com.hogwartsmagic.pensieve.capture.Recording.Particle;
import com.hogwartsmagic.pensieve.capture.Recording.Sound;
import com.hogwartsmagic.pensieve.capture.Recording.Track;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Bytes for a {@link Recording}: magic {@code "HPM1"}, the format varint, then a deflated stream of fields. Float
 * tracks are quantised (positions to 1/512 block, angles to 1/64 degree) and stored as zig-zag varint deltas, so a
 * standing entity costs almost nothing. Reading is defensive: every length is capped and every runtime failure
 * surfaces as an {@link IOException}. Pure.
 */
public final class RecordingCodec {
    private static final byte[] MAGIC = {'H', 'P', 'M', '1'};

    private static final int MAX_PALETTE = 65_536;
    private static final int MAX_LIST = 5_000_000;
    private static final int MAX_SIZE = 128;
    private static final int MAX_BYTES = 64 * 1024 * 1024;

    private static final float POS_SCALE = 512f;
    private static final float ANGLE_SCALE = 64f;

    private RecordingCodec() {
    }

    /** A {@code forClient} blob carries no island (empty palette, size 0, no cells, entities or changes): the server builds blocks. */
    public static byte[] write(Recording r, boolean forClient) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            raw.write(MAGIC);
            DataOutputStream head = new DataOutputStream(raw);
            writeVarInt(head, Recording.FORMAT);
            head.flush();
            Deflater deflater = new Deflater(Deflater.BEST_SPEED);
            try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(raw, deflater))) {
                writeBody(out, r, forClient);
            } finally {
                deflater.end();
            }
            return raw.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("in-memory write failed", e);
        }
    }

    public static Recording read(byte[] data) throws IOException {
        try {
            if (data == null || data.length < MAGIC.length + 1) {
                throw new IOException("not a recording: too short");
            }
            for (int i = 0; i < MAGIC.length; i++) {
                if (data[i] != MAGIC[i]) {
                    throw new IOException("not a recording: bad magic");
                }
            }
            ByteArrayInputStream raw = new ByteArrayInputStream(data, MAGIC.length, data.length - MAGIC.length);
            DataInputStream head = new DataInputStream(raw);
            int format = readVarInt(head);
            if (format != Recording.FORMAT) {
                throw new IOException("unsupported recording format " + format);
            }
            try (DataInputStream in = new DataInputStream(new InflaterInputStream(raw))) {
                return readBody(in);
            }
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("corrupt recording: " + e, e);
        }
    }

    // ---- body ----

    private static void writeBody(DataOutputStream out, Recording r, boolean forClient) throws IOException {
        writeUuid(out, r.id());
        writeUuid(out, r.owner());
        writeString(out, r.ownerName());
        writeString(out, r.title());
        writeVarLong(out, zig(r.day()));
        writeString(out, r.dimension());
        writeVarInt(out, zig(r.centre()[0]));
        writeVarInt(out, zig(r.centre()[1]));
        writeVarInt(out, zig(r.centre()[2]));
        writeVarInt(out, r.radius());
        writeVarInt(out, r.length());
        writeVarLong(out, zig(r.dayTime()));

        writeBlocks(out, r.blocks(), forClient);

        writeVarInt(out, r.tracks().size());
        for (Track t : r.tracks()) {
            writeTrack(out, t);
        }
        writeVarInt(out, r.beams().size());
        for (Beam b : r.beams()) {
            writeVarInt(out, zig(b.from()));
            writeVarInt(out, zig(b.to()));
            writeString(out, b.spell());
            writeFloats(out, b.start());
            writeFloats(out, b.end());
            writeVarInt(out, zig(b.caster()));
        }
        writeVarInt(out, r.casts().size());
        for (Cast c : r.casts()) {
            writeVarInt(out, zig(c.tick()));
            writeVarInt(out, zig(c.actor()));
            writeString(out, c.spell());
            writeFloats(out, c.target());
        }
        writeVarInt(out, r.sounds().size());
        for (Sound s : r.sounds()) {
            writeVarInt(out, zig(s.tick()));
            writeString(out, s.id());
            out.writeFloat(s.x());
            out.writeFloat(s.y());
            out.writeFloat(s.z());
            out.writeFloat(s.volume());
            out.writeFloat(s.pitch());
        }
        writeVarInt(out, r.particles().size());
        for (Particle p : r.particles()) {
            writeVarInt(out, zig(p.tick()));
            out.writeFloat(p.x());
            out.writeFloat(p.y());
            out.writeFloat(p.z());
            out.writeFloat(p.dx());
            out.writeFloat(p.dy());
            out.writeFloat(p.dz());
            out.writeFloat(p.speed());
            writeVarInt(out, zig(p.count()));
            writeBytes(out, p.options());
        }
        writeVarInt(out, r.levelEvents().size());
        for (LevelEvt e : r.levelEvents()) {
            writeVarInt(out, zig(e.tick()));
            writeVarInt(out, zig(e.type()));
            writeVarInt(out, zig(e.x()));
            writeVarInt(out, zig(e.y()));
            writeVarInt(out, zig(e.z()));
            writeVarInt(out, zig(e.data()));
        }
        writeVarInt(out, r.chat().size());
        for (Chat c : r.chat()) {
            writeVarInt(out, zig(c.tick()));
            writeString(out, c.sender());
            writeString(out, c.text());
        }
    }

    private static Recording readBody(DataInputStream in) throws IOException {
        UUID id = readUuid(in);
        UUID owner = readUuid(in);
        String ownerName = readString(in);
        String title = readString(in);
        long day = unzig(readVarLong(in));
        String dimension = readString(in);
        int[] centre = {unzig(readVarInt(in)), unzig(readVarInt(in)), unzig(readVarInt(in))};
        int radius = readVarInt(in);
        int length = readVarInt(in);
        long dayTime = unzig(readVarLong(in));

        Blocks blocks = readBlocks(in);

        int n = readCount(in, MAX_LIST);
        List<Track> tracks = new ArrayList<>(Math.min(n, 1024));
        for (int i = 0; i < n; i++) {
            tracks.add(readTrack(in));
        }
        n = readCount(in, MAX_LIST);
        List<Beam> beams = new ArrayList<>(Math.min(n, 1024));
        for (int i = 0; i < n; i++) {
            beams.add(new Beam(unzig(readVarInt(in)), unzig(readVarInt(in)), readString(in),
                    readFloats(in), readFloats(in), unzig(readVarInt(in))));
        }
        n = readCount(in, MAX_LIST);
        List<Cast> casts = new ArrayList<>(Math.min(n, 1024));
        for (int i = 0; i < n; i++) {
            casts.add(new Cast(unzig(readVarInt(in)), unzig(readVarInt(in)), readString(in), readFloats(in)));
        }
        n = readCount(in, MAX_LIST);
        List<Sound> sounds = new ArrayList<>(Math.min(n, 1024));
        for (int i = 0; i < n; i++) {
            sounds.add(new Sound(unzig(readVarInt(in)), readString(in), in.readFloat(), in.readFloat(),
                    in.readFloat(), in.readFloat(), in.readFloat()));
        }
        n = readCount(in, MAX_LIST);
        List<Particle> particles = new ArrayList<>(Math.min(n, 1024));
        for (int i = 0; i < n; i++) {
            particles.add(new Particle(unzig(readVarInt(in)), in.readFloat(), in.readFloat(), in.readFloat(),
                    in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(),
                    unzig(readVarInt(in)), readBytes(in)));
        }
        n = readCount(in, MAX_LIST);
        List<LevelEvt> levelEvents = new ArrayList<>(Math.min(n, 1024));
        for (int i = 0; i < n; i++) {
            levelEvents.add(new LevelEvt(unzig(readVarInt(in)), unzig(readVarInt(in)), unzig(readVarInt(in)),
                    unzig(readVarInt(in)), unzig(readVarInt(in)), unzig(readVarInt(in))));
        }
        n = readCount(in, MAX_LIST);
        List<Chat> chat = new ArrayList<>(Math.min(n, 1024));
        for (int i = 0; i < n; i++) {
            chat.add(new Chat(unzig(readVarInt(in)), readString(in), readString(in)));
        }
        return new Recording(id, owner, ownerName, title, day, dimension, centre, radius, length, dayTime,
                blocks, tracks, beams, casts, sounds, particles, levelEvents, chat);
    }

    // ---- blocks ----

    private static void writeBlocks(DataOutputStream out, Blocks b, boolean forClient) throws IOException {
        if (forClient) {
            for (int i = 0; i < 5; i++) { // palette, size, cell count, block entities, changes: all empty
                writeVarInt(out, 0);
            }
            return;
        }
        writeVarInt(out, b.palette().size());
        for (String s : b.palette()) {
            writeString(out, s);
        }
        writeVarInt(out, b.size());
        int side = 2 * b.size() + 1;
        if (b.cells().length != side * side * side) {
            throw new IllegalArgumentException("cells length " + b.cells().length + " does not match size " + b.size());
        }
        writeVarInt(out, b.cells().length);
        for (int cell : b.cells()) {
            writeVarInt(out, cell + 1); // -1 ("do not place") becomes 0
        }
        writeVarInt(out, b.blockEntities().size());
        for (BlockEntityData e : b.blockEntities()) {
            writeVarInt(out, zig(e.x()));
            writeVarInt(out, zig(e.y()));
            writeVarInt(out, zig(e.z()));
            writeString(out, e.snbt());
        }
        writeVarInt(out, b.changes().size());
        for (BlockChange c : b.changes()) {
            writeVarInt(out, zig(c.tick()));
            writeVarInt(out, zig(c.x()));
            writeVarInt(out, zig(c.y()));
            writeVarInt(out, zig(c.z()));
            writeVarInt(out, zig(c.state()));
        }
    }

    private static Blocks readBlocks(DataInputStream in) throws IOException {
        int paletteSize = readCount(in, MAX_PALETTE);
        List<String> palette = new ArrayList<>(Math.min(paletteSize, 1024));
        for (int i = 0; i < paletteSize; i++) {
            palette.add(readString(in));
        }
        int size = readVarInt(in);
        if (size < 0 || size > MAX_SIZE) {
            throw new IOException("bad block size " + size);
        }
        int side = 2 * size + 1;
        int cellCount = readVarInt(in);
        if (cellCount != 0 && cellCount != side * side * side) {
            throw new IOException("bad cell count " + cellCount);
        }
        int[] cells = new int[cellCount];
        for (int i = 0; i < cellCount; i++) {
            int cell = readVarInt(in) - 1;
            if (cell < -1 || cell >= paletteSize) {
                throw new IOException("block cell outside the palette: " + cell);
            }
            cells[i] = cell;
        }
        int n = readCount(in, MAX_LIST);
        List<BlockEntityData> entities = new ArrayList<>(Math.min(n, 1024));
        for (int i = 0; i < n; i++) {
            entities.add(new BlockEntityData(unzig(readVarInt(in)), unzig(readVarInt(in)), unzig(readVarInt(in)),
                    readString(in)));
        }
        n = readCount(in, MAX_LIST);
        List<BlockChange> changes = new ArrayList<>(Math.min(n, 1024));
        for (int i = 0; i < n; i++) {
            changes.add(new BlockChange(unzig(readVarInt(in)), unzig(readVarInt(in)), unzig(readVarInt(in)),
                    unzig(readVarInt(in)), unzig(readVarInt(in))));
        }
        return new Blocks(palette, size, cells, entities, changes);
    }

    // ---- tracks ----

    private static void writeTrack(DataOutputStream out, Track t) throws IOException {
        writeVarInt(out, zig(t.actor()));
        writeString(out, t.type());
        writeNullableUuid(out, t.uuid());
        writeString(out, t.name());
        out.writeBoolean(t.player());
        writeString(out, t.skinValue());
        writeString(out, t.skinSignature());
        writeVarInt(out, zig(t.first()));
        writeVarInt(out, zig(t.last()));
        writeVarInt(out, t.removal());
        int n = t.last() - t.first() + 1;
        writeQuantised(out, t.x(), n, POS_SCALE);
        writeQuantised(out, t.y(), n, POS_SCALE);
        writeQuantised(out, t.z(), n, POS_SCALE);
        writeQuantised(out, t.yaw(), n, ANGLE_SCALE);
        writeQuantised(out, t.headYaw(), n, ANGLE_SCALE);
        writeQuantised(out, t.bodyYaw(), n, ANGLE_SCALE);
        writeQuantised(out, t.pitch(), n, ANGLE_SCALE);
        writeVarInt(out, t.data().size());
        for (DataPatch d : t.data()) {
            writeVarInt(out, zig(d.tick()));
            writeVarInt(out, zig(d.key()));
            writeBytes(out, d.value());
        }
        writeVarInt(out, t.equipment().size());
        for (EquipPatch e : t.equipment()) {
            writeVarInt(out, zig(e.tick()));
            writeVarInt(out, zig(e.slot()));
            writeBytes(out, e.stack());
        }
        writeVarInt(out, t.acts().size());
        for (Act a : t.acts()) {
            writeVarInt(out, zig(a.tick()));
            writeVarInt(out, zig(a.kind()));
        }
    }

    private static Track readTrack(DataInputStream in) throws IOException {
        int actor = unzig(readVarInt(in));
        String type = readString(in);
        UUID uuid = readNullableUuid(in);
        String name = readString(in);
        boolean player = in.readBoolean();
        String skinValue = readString(in);
        String skinSignature = readString(in);
        int first = unzig(readVarInt(in));
        int last = unzig(readVarInt(in));
        int removal = readVarInt(in);
        long span = (long) last - first + 1;
        if (span < 0 || span > MAX_LIST) {
            throw new IOException("bad track span " + span);
        }
        int n = (int) span;
        float[] x = readQuantised(in, n, POS_SCALE);
        float[] y = readQuantised(in, n, POS_SCALE);
        float[] z = readQuantised(in, n, POS_SCALE);
        float[] yaw = readQuantised(in, n, ANGLE_SCALE);
        float[] headYaw = readQuantised(in, n, ANGLE_SCALE);
        float[] bodyYaw = readQuantised(in, n, ANGLE_SCALE);
        float[] pitch = readQuantised(in, n, ANGLE_SCALE);
        int count = readCount(in, MAX_LIST);
        List<DataPatch> data = new ArrayList<>(Math.min(count, 1024));
        for (int i = 0; i < count; i++) {
            data.add(new DataPatch(unzig(readVarInt(in)), unzig(readVarInt(in)), readBytes(in)));
        }
        count = readCount(in, MAX_LIST);
        List<EquipPatch> equipment = new ArrayList<>(Math.min(count, 1024));
        for (int i = 0; i < count; i++) {
            equipment.add(new EquipPatch(unzig(readVarInt(in)), unzig(readVarInt(in)), readBytes(in)));
        }
        count = readCount(in, MAX_LIST);
        List<Act> acts = new ArrayList<>(Math.min(count, 1024));
        for (int i = 0; i < count; i++) {
            acts.add(new Act(unzig(readVarInt(in)), unzig(readVarInt(in))));
        }
        return new Track(actor, type, uuid, name, player, skinValue, skinSignature, first, last, removal,
                x, y, z, yaw, headYaw, bodyYaw, pitch, data, equipment, acts);
    }

    private static void writeQuantised(DataOutputStream out, float[] values, int n, float scale) throws IOException {
        if (values.length != n) {
            throw new IllegalArgumentException("track array length " + values.length + " != " + n);
        }
        int prev = 0;
        for (float v : values) {
            int q = Math.round(v * scale);
            writeVarInt(out, zig(q - prev));
            prev = q;
        }
    }

    private static float[] readQuantised(DataInputStream in, int n, float scale) throws IOException {
        float[] values = new float[n];
        int prev = 0;
        for (int i = 0; i < n; i++) {
            prev += unzig(readVarInt(in));
            values[i] = prev / scale;
        }
        return values;
    }

    // ---- primitives ----

    private static int zig(int v) {
        return (v << 1) ^ (v >> 31);
    }

    private static long zig(long v) {
        return (v << 1) ^ (v >> 63);
    }

    private static int unzig(int v) {
        return (v >>> 1) ^ -(v & 1);
    }

    private static long unzig(long v) {
        return (v >>> 1) ^ -(v & 1);
    }

    private static void writeVarInt(DataOutputStream out, int v) throws IOException {
        while ((v & ~0x7F) != 0) {
            out.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.writeByte(v);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int result = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = in.readUnsignedByte();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new IOException("varint too long");
    }

    private static void writeVarLong(DataOutputStream out, long v) throws IOException {
        while ((v & ~0x7FL) != 0) {
            out.writeByte((int) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        out.writeByte((int) v);
    }

    private static long readVarLong(DataInputStream in) throws IOException {
        long result = 0;
        for (int shift = 0; shift < 70; shift += 7) {
            int b = in.readUnsignedByte();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new IOException("varlong too long");
    }

    /** A non-negative length, capped so a corrupt file cannot ask for gigabytes. */
    private static int readCount(DataInputStream in, int max) throws IOException {
        int n = readVarInt(in);
        if (n < 0 || n > max) {
            throw new IOException("length " + n + " out of range (max " + max + ")");
        }
        return n;
    }

    private static void writeUuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeNullableUuid(DataOutputStream out, UUID id) throws IOException {
        out.writeBoolean(id != null);
        if (id != null) {
            writeUuid(out, id);
        }
    }

    private static UUID readNullableUuid(DataInputStream in) throws IOException {
        return in.readBoolean() ? readUuid(in) : null;
    }

    /** Null is 0, otherwise length + 1 then the UTF-8 bytes. */
    private static void writeString(DataOutputStream out, String s) throws IOException {
        if (s == null) {
            writeVarInt(out, 0);
            return;
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length + 1);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int n = readCount(in, MAX_BYTES);
        if (n == 0) {
            return null;
        }
        return new String(readFully(in, n - 1), StandardCharsets.UTF_8);
    }

    private static void writeBytes(DataOutputStream out, byte[] b) throws IOException {
        writeVarInt(out, b.length);
        out.write(b);
    }

    private static byte[] readBytes(DataInputStream in) throws IOException {
        return readFully(in, readCount(in, MAX_BYTES));
    }

    /** Reads in chunks, so a lying length hits EOF before it allocates. */
    private static byte[] readFully(DataInputStream in, int n) throws IOException {
        ByteArrayOutputStream acc = new ByteArrayOutputStream(Math.min(n, 8192));
        byte[] buf = new byte[Math.min(Math.max(n, 1), 8192)];
        int left = n;
        while (left > 0) {
            int got = in.read(buf, 0, Math.min(buf.length, left));
            if (got < 0) {
                throw new EOFException();
            }
            acc.write(buf, 0, got);
            left -= got;
        }
        return acc.toByteArray();
    }

    /** Null is 0, otherwise length + 1 then raw floats. */
    private static void writeFloats(DataOutputStream out, float[] f) throws IOException {
        if (f == null) {
            writeVarInt(out, 0);
            return;
        }
        writeVarInt(out, f.length + 1);
        for (float v : f) {
            out.writeFloat(v);
        }
    }

    private static float[] readFloats(DataInputStream in) throws IOException {
        int n = readCount(in, MAX_LIST);
        if (n == 0) {
            return null;
        }
        if (n - 1 > 16) {
            throw new IOException("float array too long: " + (n - 1));
        }
        float[] f = new float[n - 1];
        for (int i = 0; i < f.length; i++) {
            f[i] = in.readFloat();
        }
        return f;
    }
}
