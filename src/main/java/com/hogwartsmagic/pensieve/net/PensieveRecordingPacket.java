package com.hogwartsmagic.pensieve.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Server to client: one part of a recorded memory's client blob, sent before the start packet. A clientbound custom
 * payload is capped at 1 MiB, so the blob travels in parts of at most {@link #MAX_PART} bytes, in order.
 */
public record PensieveRecordingPacket(String sceneId, int part, int parts, byte[] data) {
    /** Bytes of the blob per part: 900,000 plus the ids and lengths stays well inside the 1 MiB payload. */
    public static final int MAX_PART = 900000;
    /** Parts per blob a client accepts (the capture cap is 16 MiB, about 19 parts). */
    public static final int MAX_PARTS = 64;

    /** The number of parts a blob of {@code length} bytes is sent in (at least one). */
    public static int partsFor(int length) {
        return Math.max(1, (length + MAX_PART - 1) / MAX_PART);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(sceneId, PensieveStartPacket.MAX_ID);
        buf.writeVarInt(part);
        buf.writeVarInt(parts);
        buf.writeByteArray(data);
    }

    public static PensieveRecordingPacket decode(FriendlyByteBuf buf) {
        String id = buf.readUtf(PensieveStartPacket.MAX_ID);
        int part = buf.readVarInt();
        int parts = buf.readVarInt();
        if (parts < 1 || parts > MAX_PARTS || part < 0 || part >= parts) {
            throw new IllegalArgumentException("recording part " + part + " of " + parts);
        }
        return new PensieveRecordingPacket(id, part, parts, buf.readByteArray(MAX_PART));
    }

    public static void handle(PensieveRecordingPacket p, CustomPayloadEvent.Context ctx) {
        PensieveNet.toClient(ctx, "pensieveRecording", p);
    }
}
