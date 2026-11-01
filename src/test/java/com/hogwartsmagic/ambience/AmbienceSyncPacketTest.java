package com.hogwartsmagic.ambience;

import com.hogwartsmagic.ambience.AmbienceNet.AmbienceSyncPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The whole-dimension sync: whatever the server can write, the client can read back. */
class AmbienceSyncPacketTest {

    @Test
    void aLongDimensionIdSurvivesTheWire() {
        // Longer than the old 256-character read limit, which disconnected the client for such a modded dimension.
        String dimension = "somemod:" + "a".repeat(400);
        AmbientEffect ceiling = new AmbientEffect(UUID.randomUUID(), AmbientEffect.KIND_CEILING, 1, 70, 3, 4f, 5f, 6f,
                0f, AmbientEffect.CEILING_SEALED, null, 42L);
        UUID opened = ceiling.id();
        AmbienceSyncPacket sent = new AmbienceSyncPacket(dimension, List.of(ceiling), List.of(opened));

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        sent.encode(buf);
        AmbienceSyncPacket back = AmbienceSyncPacket.decode(buf);

        assertEquals(dimension, back.dimension());
        assertEquals(List.of(ceiling), back.all());
        assertEquals(List.of(opened), back.opened());
        assertEquals(0, buf.readableBytes(), "decode must consume exactly what encode wrote");
    }
}
