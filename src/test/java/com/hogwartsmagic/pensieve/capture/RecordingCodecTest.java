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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingCodecTest {
    private static final float POS_TOL = 1f / 512f;
    private static final float ANGLE_TOL = 1f / 64f;

    private static Track wanderer(int ticks) {
        float[] x = new float[ticks];
        float[] y = new float[ticks];
        float[] z = new float[ticks];
        float[] yaw = new float[ticks];
        float[] head = new float[ticks];
        float[] body = new float[ticks];
        float[] pitch = new float[ticks];
        for (int i = 0; i < ticks; i++) {
            x[i] = -3.3f + i * 0.217f;
            y[i] = 1.0f + (float) Math.sin(i * 0.3) * 0.4f;
            z[i] = 2.71828f - i * 0.1031f;
            yaw[i] = -170f + i * 7.3f;
            head[i] = yaw[i] + 5.55f;
            body[i] = yaw[i] - 3.21f;
            pitch[i] = (i % 20) - 10.3f;
        }
        return new Track(4, "minecraft:player", UUID.fromString("00000000-0000-0001-0000-000000000002"), "Harry", true,
                "skinvalue", null, 3, 3 + ticks - 1, Recording.REMOVAL_KILLED, x, y, z, yaw, head, body, pitch,
                List.of(new DataPatch(0, 8, new byte[]{1, 2, 3}), new DataPatch(12, 9, new byte[0])),
                List.of(new EquipPatch(5, 0, new byte[]{9, 8, 7, 6})),
                List.of(new Act(7, Recording.ACT_SWING_MAIN)));
    }

    private static Recording sample() {
        int size = 2;
        int side = 2 * size + 1;
        int[] cells = new int[side * side * side];
        Arrays.fill(cells, 0);
        cells[Blocks.index(0, -2, 0, size)] = 1;
        cells[Blocks.index(2, 2, -2, size)] = -1;
        Blocks blocks = new Blocks(List.of("minecraft:air", "minecraft:oak_door[facing=north,half=lower]"), size, cells,
                List.of(new BlockEntityData(1, 0, -1, "{Items:[]}")),
                List.of(new BlockChange(10, 0, -2, 0, 0), new BlockChange(30, 0, -2, 0, 1)));
        return new Recording(UUID.fromString("11111111-2222-3333-4444-555555555555"),
                UUID.fromString("66666666-7777-8888-9999-000000000000"), "Harry", "A quiet evening", 123456L,
                "minecraft:overworld", new int[]{-100, 64, 2000}, 32, 1200, 18000L, blocks, List.of(wanderer(40)),
                List.of(new Beam(2, 9, "hogwartsmagic:stupefy", new float[]{0f, 1f, 0f}, new float[]{4f, 1.5f, -2f}, 4)),
                List.of(new Cast(6, 4, "hogwartsmagic:lumos", new float[]{1.5f, 0.25f, -3f})),
                List.of(new Sound(3, "minecraft:entity.generic.explode", 1f, 2f, 3f, 0.8f, 1.1f)),
                List.of(new Particle(8, 0.5f, 1.5f, -0.5f, 0.1f, 0.2f, 0.3f, 0.05f, 12, new byte[]{4, 5})),
                List.of(new LevelEvt(14, 2001, -1, 0, 5, 77)),
                List.of(new Chat(20, "Harry", "hello"), new Chat(21, null, "a system line")));
    }

    private static void assertNear(float[] expected, float[] actual, float tol) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], tol, "index " + i);
        }
    }

    private static void assertTrackEquals(Track a, Track b) {
        assertEquals(a.actor(), b.actor());
        assertEquals(a.type(), b.type());
        assertEquals(a.uuid(), b.uuid());
        assertEquals(a.name(), b.name());
        assertEquals(a.player(), b.player());
        assertEquals(a.skinValue(), b.skinValue());
        assertEquals(a.skinSignature(), b.skinSignature());
        assertEquals(a.first(), b.first());
        assertEquals(a.last(), b.last());
        assertEquals(a.removal(), b.removal());
        assertNear(a.x(), b.x(), POS_TOL);
        assertNear(a.y(), b.y(), POS_TOL);
        assertNear(a.z(), b.z(), POS_TOL);
        assertNear(a.yaw(), b.yaw(), ANGLE_TOL);
        assertNear(a.headYaw(), b.headYaw(), ANGLE_TOL);
        assertNear(a.bodyYaw(), b.bodyYaw(), ANGLE_TOL);
        assertNear(a.pitch(), b.pitch(), ANGLE_TOL);
        assertEquals(a.data().size(), b.data().size());
        for (int i = 0; i < a.data().size(); i++) {
            assertEquals(a.data().get(i).tick(), b.data().get(i).tick());
            assertEquals(a.data().get(i).key(), b.data().get(i).key());
            assertArrayEquals(a.data().get(i).value(), b.data().get(i).value());
        }
        assertEquals(a.equipment().size(), b.equipment().size());
        for (int i = 0; i < a.equipment().size(); i++) {
            assertEquals(a.equipment().get(i).tick(), b.equipment().get(i).tick());
            assertEquals(a.equipment().get(i).slot(), b.equipment().get(i).slot());
            assertArrayEquals(a.equipment().get(i).stack(), b.equipment().get(i).stack());
        }
        assertEquals(a.acts(), b.acts());
    }

    private static void assertHeaderEquals(Recording a, Recording b) {
        assertEquals(a.id(), b.id());
        assertEquals(a.owner(), b.owner());
        assertEquals(a.ownerName(), b.ownerName());
        assertEquals(a.title(), b.title());
        assertEquals(a.day(), b.day());
        assertEquals(a.dimension(), b.dimension());
        assertArrayEquals(a.centre(), b.centre());
        assertEquals(a.radius(), b.radius());
        assertEquals(a.length(), b.length());
        assertEquals(a.dayTime(), b.dayTime());
        assertEquals(a.tracks().size(), b.tracks().size());
        for (int i = 0; i < a.tracks().size(); i++) {
            assertTrackEquals(a.tracks().get(i), b.tracks().get(i));
        }
    }

    @Test
    void roundTripsEverything() throws IOException {
        Recording r = sample();
        Recording back = RecordingCodec.read(RecordingCodec.write(r, false));
        assertHeaderEquals(r, back);

        assertEquals(r.blocks().palette(), back.blocks().palette());
        assertEquals(r.blocks().size(), back.blocks().size());
        assertArrayEquals(r.blocks().cells(), back.blocks().cells());
        assertEquals(r.blocks().blockEntities(), back.blocks().blockEntities());
        assertEquals(r.blocks().changes(), back.blocks().changes());

        assertEquals(1, back.beams().size());
        Beam bb = back.beams().get(0);
        assertEquals(2, bb.from());
        assertEquals(9, bb.to());
        assertEquals("hogwartsmagic:stupefy", bb.spell());
        assertArrayEquals(new float[]{0f, 1f, 0f}, bb.start());
        assertArrayEquals(new float[]{4f, 1.5f, -2f}, bb.end());
        assertEquals(4, bb.caster());

        Cast c = back.casts().get(0);
        assertEquals(6, c.tick());
        assertEquals(4, c.actor());
        assertEquals("hogwartsmagic:lumos", c.spell());
        assertArrayEquals(new float[]{1.5f, 0.25f, -3f}, c.target());

        assertEquals(r.sounds(), back.sounds());
        Particle p = back.particles().get(0);
        Particle pe = r.particles().get(0);
        assertEquals(pe.tick(), p.tick());
        assertEquals(pe.x(), p.x());
        assertEquals(pe.speed(), p.speed());
        assertEquals(pe.count(), p.count());
        assertArrayEquals(pe.options(), p.options());
        assertEquals(r.levelEvents(), back.levelEvents());
        assertEquals(r.chat(), back.chat());
        assertNull(back.chat().get(1).sender());
    }

    @Test
    void indexMatchesTheDocumentedLayout() {
        assertEquals(0, Blocks.index(-2, -2, -2, 2));
        assertEquals(1, Blocks.index(-1, -2, -2, 2));
        assertEquals(5, Blocks.index(-2, -2, -1, 2));
        assertEquals(25, Blocks.index(-2, -1, -2, 2));
        assertEquals(124, Blocks.index(2, 2, 2, 2));
    }

    @Test
    void clientBlobHasNoBlocks() throws IOException {
        Recording r = sample();
        Recording back = RecordingCodec.read(RecordingCodec.write(r, true));
        assertEquals(0, back.blocks().cells().length);
        assertEquals(0, back.blocks().palette().size());
        assertEquals(0, back.blocks().size());
        assertTrue(back.blocks().changes().isEmpty());
        assertHeaderEquals(r, back);
    }

    @Test
    void staticCrowdIsSmall() {
        int ticks = 1200;
        List<Track> tracks = new ArrayList<>();
        for (int a = 0; a < 64; a++) {
            float[] pos = new float[ticks];
            float[] zeros = new float[ticks];
            Arrays.fill(pos, 5.25f + a);
            tracks.add(new Track(a, "minecraft:zombie", UUID.randomUUID(), null, false, null, null, 0, ticks - 1,
                    Recording.REMOVAL_NONE, pos, pos.clone(), pos.clone(), zeros, zeros.clone(), zeros.clone(),
                    zeros.clone(), List.of(), List.of(), List.of()));
        }
        int size = 32;
        int side = 2 * size + 1;
        int[] cells = new int[side * side * side]; // all air
        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                cells[Blocks.index(x, -1, z, size)] = 1;
            }
        }
        Blocks blocks = new Blocks(List.of("minecraft:air", "minecraft:stone"), size, cells, List.of(), List.of());
        Recording r = new Recording(UUID.randomUUID(), UUID.randomUUID(), "Harry", "Crowd", 1L, "minecraft:overworld",
                new int[]{0, 64, 0}, size, ticks, 0L, blocks, tracks, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());
        int bytes = RecordingCodec.write(r, false).length;
        assertTrue(bytes < 60_000, "recording took " + bytes + " bytes");
    }

    @Test
    void rejectsUnknownVersion() {
        byte[] blob = {'H', 'P', 'M', '1', 99};
        IOException e = assertThrows(IOException.class, () -> RecordingCodec.read(blob));
        assertTrue(e.getMessage().contains("format 99"), e.getMessage());
    }

    @Test
    void rejectsGarbage() {
        Random rnd = new Random(42);
        for (int round = 0; round < 50; round++) {
            byte[] junk = new byte[100];
            rnd.nextBytes(junk);
            assertThrows(IOException.class, () -> RecordingCodec.read(junk));
            // a valid magic and format in front of noise must still fail cleanly
            byte[] headed = new byte[105];
            headed[0] = 'H';
            headed[1] = 'P';
            headed[2] = 'M';
            headed[3] = '1';
            headed[4] = 1;
            System.arraycopy(junk, 0, headed, 5, 100);
            assertThrows(IOException.class, () -> RecordingCodec.read(headed));
        }
    }

    @Test
    void rejectsTruncation() {
        byte[] good = RecordingCodec.write(sample(), false);
        byte[] cut = Arrays.copyOf(good, good.length / 2);
        assertThrows(IOException.class, () -> RecordingCodec.read(cut));
    }
}
