package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.net.PensieveGhostsPacket;
import com.hogwartsmagic.pensieve.net.PensieveRecordingPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordedPacketsTest {
    @Test
    void partsCoverTheBlobWithAtLeastOneAndAtMostTheCap() {
        assertEquals(1, PensieveRecordingPacket.partsFor(0));
        assertEquals(1, PensieveRecordingPacket.partsFor(1));
        assertEquals(1, PensieveRecordingPacket.partsFor(PensieveRecordingPacket.MAX_PART));
        assertEquals(2, PensieveRecordingPacket.partsFor(PensieveRecordingPacket.MAX_PART + 1));
        assertEquals(19, PensieveRecordingPacket.partsFor(16 * 1024 * 1024));
        assertTrue(PensieveRecordingPacket.partsFor(16 * 1024 * 1024) <= PensieveRecordingPacket.MAX_PARTS);
    }

    @Test
    void aFullPartFitsThePayloadLimitAndRoundTrips() {
        byte[] data = new byte[PensieveRecordingPacket.MAX_PART];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        PensieveRecordingPacket p = new PensieveRecordingPacket("recorded/" + UUID.randomUUID(), 3, 5, data);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        p.encode(buf);
        assertTrue(buf.readableBytes() < 1048576 - 1024, "body is " + buf.readableBytes());
        PensieveRecordingPacket q = PensieveRecordingPacket.decode(buf);
        assertEquals(p.sceneId(), q.sceneId());
        assertEquals(3, q.part());
        assertEquals(5, q.parts());
        assertArrayEquals(data, q.data());
    }

    @Test
    void aPartOutsideItsCountIsRefused() {
        PensieveRecordingPacket bad = new PensieveRecordingPacket("recorded/x", 2, 2, new byte[0]);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        bad.encode(buf);
        assertThrows(IllegalArgumentException.class, () -> PensieveRecordingPacket.decode(buf));
    }

    @Test
    void ghostsRoundTripInOrder() {
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new PensieveGhostsPacket(ids).encode(buf);
        assertEquals(ids, PensieveGhostsPacket.decode(buf).ghosts());
        FriendlyByteBuf empty = new FriendlyByteBuf(Unpooled.buffer());
        new PensieveGhostsPacket(List.of()).encode(empty);
        assertTrue(PensieveGhostsPacket.decode(empty).ghosts().isEmpty());
    }
}
