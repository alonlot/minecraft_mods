package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.pensieve.capture.Recording;
import com.hogwartsmagic.pensieve.capture.RecordingCodec;
import com.hogwartsmagic.pensieve.capture.RecordingScene;
import com.hogwartsmagic.pensieve.net.PensieveRecordingPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RecordingCacheTest {
    @AfterEach
    void reset() {
        RecordingCache.clear();
    }

    private static Recording recording(UUID id) {
        int[] cells = new int[27];
        Recording.Blocks blocks = new Recording.Blocks(List.of("minecraft:air"), 1, cells, List.of(), List.of());
        float[] a = new float[3];
        Recording.Track t = new Recording.Track(0, "minecraft:zombie", UUID.randomUUID(), "Z", false, null, null, 0, 2,
                0, a, a, a, a, a, a, a, List.of(), List.of(), List.of());
        return new Recording(id, UUID.randomUUID(), "Alon", "A memory", 3, "minecraft:overworld",
                new int[] {0, 64, 0}, 32, 100, 6000, blocks, List.of(t), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());
    }

    private static List<PensieveRecordingPacket> split(String sceneId, byte[] blob, int partSize) {
        int parts = Math.max(1, (blob.length + partSize - 1) / partSize);
        java.util.ArrayList<PensieveRecordingPacket> out = new java.util.ArrayList<>();
        for (int i = 0; i < parts; i++) {
            int from = i * partSize;
            out.add(new PensieveRecordingPacket(sceneId, i, parts,
                    java.util.Arrays.copyOfRange(blob, from, Math.min(blob.length, from + partSize))));
        }
        return out;
    }

    @Test
    void partsReassembleInAnyOrderAndDecodeOnce() {
        UUID id = UUID.randomUUID();
        String sceneId = RecordingScene.sceneId(id);
        List<PensieveRecordingPacket> parts = split(sceneId, RecordingCodec.write(recording(id), true), 40);
        assertEquals(true, parts.size() > 2, "the test needs several parts");
        assertNull(RecordingCache.scene(sceneId));
        for (int i = parts.size() - 1; i > 0; i--) {
            RecordingCache.accept(parts.get(i));
            assertNull(RecordingCache.scene(sceneId), "incomplete until the last part");
        }
        RecordingCache.accept(parts.get(0));
        assertNotNull(RecordingCache.scene(sceneId));
        assertEquals(sceneId, RecordingCache.scene(sceneId).id());
        assertEquals(id, RecordingCache.recording(sceneId).id());
        assertNull(RecordingCache.scene(RecordingScene.sceneId(UUID.randomUUID())));
    }

    @Test
    void aNewSceneIdDropsTheOlderPartials() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<PensieveRecordingPacket> pa = split(RecordingScene.sceneId(a), RecordingCodec.write(recording(a), true), 40);
        List<PensieveRecordingPacket> pb = split(RecordingScene.sceneId(b), RecordingCodec.write(recording(b), true), 40);
        RecordingCache.accept(pa.get(0));
        for (PensieveRecordingPacket p : pb) RecordingCache.accept(p);
        // The rest of a's parts now start a fresh collection that can never complete without part 0.
        for (int i = 1; i < pa.size(); i++) RecordingCache.accept(pa.get(i));
        assertNull(RecordingCache.scene(RecordingScene.sceneId(a)));
    }

    @Test
    void anUnreadableBlobIsMissing() {
        String sceneId = RecordingScene.sceneId(UUID.randomUUID());
        RecordingCache.accept(new PensieveRecordingPacket(sceneId, 0, 1, new byte[] {1, 2, 3}));
        assertNull(RecordingCache.scene(sceneId));
        assertNull(RecordingCache.recording(sceneId));
    }
}
