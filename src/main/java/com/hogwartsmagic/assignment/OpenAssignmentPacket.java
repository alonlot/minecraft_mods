package com.hogwartsmagic.assignment;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: everything the parchment screen draws. Lines carry lang keys, not text, so the
 * client shows them in its own language. {@code state} is 0 = not accepted, 1 = in progress, 2 = complete.
 */
public record OpenAssignmentPacket(String spellId, String teacherKey, String quoteKey, boolean dark, int rarity,
                                   int state, String ownerName, List<Line> lines) {
    public static final int STATE_OFFERED = 0;
    public static final int STATE_ACTIVE = 1;
    public static final int STATE_COMPLETE = 2;
    private static final int MAX_LINES = AssignmentParser.MAX_OBJECTIVES;

    public record Line(String key, int count, int progress, boolean live, boolean done) {
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.spellId);
        buf.writeUtf(this.teacherKey);
        buf.writeUtf(this.quoteKey);
        buf.writeBoolean(this.dark);
        buf.writeVarInt(this.rarity);
        buf.writeVarInt(this.state);
        buf.writeUtf(this.ownerName);
        buf.writeVarInt(this.lines.size());
        for (Line line : this.lines) {
            buf.writeUtf(line.key());
            buf.writeVarInt(line.count());
            buf.writeVarInt(line.progress());
            buf.writeBoolean(line.live());
            buf.writeBoolean(line.done());
        }
    }

    public static OpenAssignmentPacket decode(FriendlyByteBuf buf) {
        String spellId = buf.readUtf();
        String teacherKey = buf.readUtf();
        String quoteKey = buf.readUtf();
        boolean dark = buf.readBoolean();
        int rarity = buf.readVarInt();
        int state = buf.readVarInt();
        String ownerName = buf.readUtf();
        int size = Math.max(0, Math.min(MAX_LINES, buf.readVarInt()));
        List<Line> lines = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            lines.add(new Line(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean()));
        }
        return new OpenAssignmentPacket(spellId, teacherKey, quoteKey, dark, rarity, state, ownerName, lines);
    }

    public static void handle(OpenAssignmentPacket packet, CustomPayloadEvent.Context ctx) {
        if (ctx.getSender() != null) {
            // Server-to-client only.
            ctx.setPacketHandled(true);
            return;
        }
        ctx.enqueueWork(() -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("openAssignment", packet));
        ctx.setPacketHandled(true);
    }
}
