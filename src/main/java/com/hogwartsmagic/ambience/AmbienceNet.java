package com.hogwartsmagic.ambience;

import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.util.ClientSideBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * The ambience packets and their senders. All three are server to client and ride the shared
 * {@link SpellPackets#CHANNEL}; on the client they go through {@link ClientSideBridge} so nothing here touches a
 * client class.
 */
public final class AmbienceNet {
    /** At most this many effects in one sync; a longer (damaged) list is cut, never allocated. */
    public static final int MAX_SYNC = AmbienceRegistry.MAX_EFFECTS;

    /**
     * Test seam: sees every ambience packet right before it is sent to a player. The sync GameTest uses it to read
     * what a mock player is sent, because the mock connection is an embedded channel that does not decode packets
     * back. Null in play.
     */
    private static volatile @Nullable BiConsumer<ServerPlayer, Object> sendObserver;

    private AmbienceNet() {
    }

    /**
     * The whole effect list of one dimension; the client replaces what it holds with it. {@code opened} are the sealed
     * ceilings in it that this player has already had revealed (it entered that hall before), which the client draws
     * open instead of as a plank roof.
     */
    public record AmbienceSyncPacket(String dimension, List<AmbientEffect> all, List<UUID> opened) {
        /**
         * The longest dimension id either side accepts: the wire's own string limit, the same on encode and decode,
         * so a modded dimension with a long id never disconnects the client.
         */
        public static final int MAX_DIMENSION_LENGTH = 32767;

        public AmbienceSyncPacket(String dimension, List<AmbientEffect> all) {
            this(dimension, all, List.of());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(dimension, MAX_DIMENSION_LENGTH);
            buf.writeVarInt(all.size());
            for (AmbientEffect e : all) {
                e.encode(buf);
            }
            buf.writeVarInt(opened.size());
            for (UUID id : opened) {
                buf.writeUUID(id);
            }
        }

        public static AmbienceSyncPacket decode(FriendlyByteBuf buf) {
            String dimension = buf.readUtf(MAX_DIMENSION_LENGTH);
            int n = Math.min(buf.readVarInt(), MAX_SYNC);
            List<AmbientEffect> all = new ArrayList<>(Math.max(0, Math.min(n, 256)));
            for (int i = 0; i < n; i++) {
                all.add(AmbientEffect.decode(buf));
            }
            int m = Math.min(buf.readVarInt(), MAX_SYNC);
            List<UUID> opened = new ArrayList<>(Math.max(0, Math.min(m, 256)));
            for (int i = 0; i < m; i++) {
                opened.add(buf.readUUID());
            }
            return new AmbienceSyncPacket(dimension, all, opened);
        }

        public static void handle(AmbienceSyncPacket p, CustomPayloadEvent.Context ctx) {
            toClient(ctx, "ambienceSync", p);
        }
    }

    /** One effect added to, or removed from, the client's list. A remove only needs the effect's {@code id}. */
    public record AmbienceDeltaPacket(boolean add, AmbientEffect effect) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(add);
            effect.encode(buf);
        }

        public static AmbienceDeltaPacket decode(FriendlyByteBuf buf) {
            return new AmbienceDeltaPacket(buf.readBoolean(), AmbientEffect.decode(buf));
        }

        public static void handle(AmbienceDeltaPacket p, CustomPayloadEvent.Context ctx) {
            toClient(ctx, "ambienceDelta", p);
        }
    }

    /** Play this ceiling's reveal (the plank roof dissolving into sky) now, on the client's game clock. */
    public record CeilingRevealPacket(UUID id) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(id);
        }

        public static CeilingRevealPacket decode(FriendlyByteBuf buf) {
            return new CeilingRevealPacket(buf.readUUID());
        }

        public static void handle(CeilingRevealPacket p, CustomPayloadEvent.Context ctx) {
            toClient(ctx, "ceilingReveal", p);
        }
    }

    /** Called from {@code SpellPackets.register()}. */
    public static void register() {
        SpellPackets.CHANNEL.messageBuilder(AmbienceSyncPacket.class)
                .encoder(AmbienceSyncPacket::encode).decoder(AmbienceSyncPacket::decode)
                .consumerMainThread(AmbienceSyncPacket::handle).add();
        SpellPackets.CHANNEL.messageBuilder(AmbienceDeltaPacket.class)
                .encoder(AmbienceDeltaPacket::encode).decoder(AmbienceDeltaPacket::decode)
                .consumerMainThread(AmbienceDeltaPacket::handle).add();
        SpellPackets.CHANNEL.messageBuilder(CeilingRevealPacket.class)
                .encoder(CeilingRevealPacket::encode).decoder(CeilingRevealPacket::decode)
                .consumerMainThread(CeilingRevealPacket::handle).add();
    }

    /** Sends this player every effect of the level they are in now (login, respawn, dimension change). */
    public static void sendSync(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        send(player, new AmbienceSyncPacket(level.dimension().location().toString(),
                AmbienceRegistry.get(level).all(), com.hogwartsmagic.worldgen.MiniHogwartsAmbience.openedFor(player)));
    }

    /** Tells every player in the level that an effect was added or removed. */
    static void broadcastDelta(ServerLevel level, boolean add, AmbientEffect effect) {
        AmbienceDeltaPacket packet = new AmbienceDeltaPacket(add, effect);
        for (ServerPlayer player : level.players()) {
            send(player, packet);
        }
    }

    /** Tells one player to play the reveal of this ceiling now. */
    public static void sendReveal(ServerPlayer player, UUID ceiling) {
        send(player, new CeilingRevealPacket(ceiling));
    }

    private static void send(ServerPlayer player, Object packet) {
        BiConsumer<ServerPlayer, Object> observer = sendObserver;
        if (observer != null) {
            observer.accept(player, packet);
        }
        SpellPackets.CHANNEL.send(packet, PacketDistributor.PLAYER.with(player));
    }

    /** Installs (or, with null, removes) the test seam; see {@link #sendObserver}. */
    public static void setSendObserverForTests(@Nullable BiConsumer<ServerPlayer, Object> observer) {
        sendObserver = observer;
    }

    /** Server-to-client packets only run on a client; a client sending one to the server is ignored. */
    private static void toClient(CustomPayloadEvent.Context ctx, String action, Object payload) {
        if (ctx.getSender() == null) {
            ctx.enqueueWork(() -> ClientSideBridge.handlePacket(action, payload));
        }
        ctx.setPacketHandled(true);
    }
}
