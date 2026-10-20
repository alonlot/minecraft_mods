package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.net.PensieveStartPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartPacketSizeTest {
    /** A clientbound custom payload may not exceed this (ClientboundCustomPayloadPacket.MAX_PAYLOAD_SIZE). */
    private static final int PAYLOAD_LIMIT = 1048576;

    @Test
    void aMaxSizeAsciiSceneRoundTripsWithinThePayloadLimit() {
        String json = "a".repeat(PensieveStartPacket.MAX_JSON);
        assertTrue(PensieveStartPacket.fits(json));
        String id = "i".repeat(PensieveStartPacket.MAX_ID);
        PensieveStartPacket p = new PensieveStartPacket(id, json, new BlockPos(1, 2, 3), 12345L, 999, true);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        p.encode(buf);
        // Leave room for the channel and packet ids the channel adds around the body.
        assertTrue(buf.readableBytes() < PAYLOAD_LIMIT - 1024, "body is " + buf.readableBytes());
        PensieveStartPacket q = PensieveStartPacket.decode(buf);
        assertEquals(json, q.sceneJson());
        assertEquals(id, q.sceneId());
        assertEquals(new BlockPos(1, 2, 3), q.origin());
        assertEquals(12345L, q.startGameTime());
        assertEquals(999, q.buildTicks());
        assertTrue(q.preview());
    }

    @Test
    void theGuardCountsBytesNotChars() {
        // 400,000 three-byte chars is under the char cap but 1.2 MB on the wire.
        String wide = "€".repeat(400000);
        assertTrue(wide.length() <= PensieveStartPacket.MAX_JSON);
        assertFalse(PensieveStartPacket.fits(wide));
        assertTrue(PensieveStartPacket.fits("€".repeat(300000)));
        assertFalse(PensieveStartPacket.fits("a".repeat(PensieveStartPacket.MAX_JSON + 1)));
    }
}
