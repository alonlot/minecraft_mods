package com.hogwartsmagic.cosy;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;

import java.util.ArrayList;
import java.util.List;

/** The cosy pack's packets, server to client: open the card album screen, and reveal a freshly opened card. */
public final class CosyNet {
    public static final SimpleChannel CHANNEL = ChannelBuilder
            .named(ExampleMod.MODID + ":cosy_food")
            .networkProtocolVersion(1)
            .simpleChannel();

    private CosyNet() {
    }

    public static void register() {
        CHANNEL.messageBuilder(OpenAlbumPacket.class)
                .encoder(OpenAlbumPacket::encode).decoder(OpenAlbumPacket::decode)
                .consumerMainThread(OpenAlbumPacket::handle).add();
        CHANNEL.messageBuilder(CardRevealPacket.class)
                .encoder(CardRevealPacket::encode).decoder(CardRevealPacket::decode)
                .consumerMainThread(CardRevealPacket::handle).add();
    }

    /**
     * A Chocolate Frog box was opened and rolled this card: play the reveal. Server to client; the client queues it
     * (one reveal at a time) and ignores an id that is not a real card.
     */
    public record CardRevealPacket(int cardId) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(this.cardId);
        }

        static CardRevealPacket decode(FriendlyByteBuf buf) {
            return new CardRevealPacket(buf.readVarInt());
        }

        static void handle(CardRevealPacket p, CustomPayloadEvent.Context ctx) {
            // The client class is named only inside the lambda and only after the dist check, so a dedicated server
            // never loads it.
            if (ctx.getSender() == null && FMLEnvironment.dist == Dist.CLIENT) {
                ctx.enqueueWork(() -> com.hogwartsmagic.cosy.client.card.CardRevealClient.offer(p.cardId()));
            }
            ctx.setPacketHandled(true);
        }
    }

    /** The ids the album holds, for the screen to draw. */
    public record OpenAlbumPacket(List<Integer> collected) {
        void encode(FriendlyByteBuf buf) {
            // Never more than there are cards: an album with extra ids (a command) must not break the packet.
            int n = Math.min(collected.size(), WizardCards.ALL.size());
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                buf.writeVarInt(collected.get(i));
            }
        }

        static OpenAlbumPacket decode(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            if (n < 0 || n > WizardCards.ALL.size()) {
                throw new IllegalArgumentException("card album list too long: " + n);
            }
            List<Integer> ids = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                ids.add(buf.readVarInt());
            }
            return new OpenAlbumPacket(ids);
        }

        static void handle(OpenAlbumPacket p, CustomPayloadEvent.Context ctx) {
            // Server to client only, and only a client has a screen to open.
            if (ctx.getSender() == null && FMLEnvironment.dist == Dist.CLIENT) {
                ctx.enqueueWork(() -> com.hogwartsmagic.cosy.client.CardAlbumClient.open(p.collected()));
            }
            ctx.setPacketHandled(true);
        }
    }
}
