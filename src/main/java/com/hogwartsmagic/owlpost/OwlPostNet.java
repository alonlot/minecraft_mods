package com.hogwartsmagic.owlpost;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.OwlPerchBlockEntity;
import com.hogwartsmagic.entity.OwlEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Owl post packets, on their own channel. Server to client: open the destination screen. Client to server: what the
 * player chose on it, checked again here in full (the screen's list is only a suggestion).
 */
public final class OwlPostNet {
    public static final int MODE_SEND = 0;
    public static final int MODE_DEFAULT = 1;
    public static final int MODE_HOME = 2;

    public static final int ACTION_SEND = 0;
    public static final int ACTION_SEND_AND_DEFAULT = 1;
    public static final int ACTION_SET_DEFAULT = 2;
    public static final int ACTION_CLEAR_DEFAULT = 3;
    public static final int ACTION_SET_HOME = 4;
    public static final int ACTION_TAKE_TAG = 5;

    private static final int MAX_ENTRIES = 64;
    private static final int MAX_NAME = 128;
    private static final int MAX_DIM = 256;

    public static final SimpleChannel CHANNEL = ChannelBuilder
            .named(ExampleMod.MODID + ":owl_post")
            .networkProtocolVersion(1)
            .simpleChannel();

    private OwlPostNet() {
    }

    public static void register() {
        CHANNEL.messageBuilder(OpenScreenPacket.class)
                .encoder(OpenScreenPacket::encode).decoder(OpenScreenPacket::decode)
                .consumerMainThread(OpenScreenPacket::handle).add();
        CHANNEL.messageBuilder(ActionPacket.class)
                .encoder(ActionPacket::encode).decoder(ActionPacket::decode)
                .consumerMainThread(ActionPacket::handle).add();
    }

    public record Entry(String dimension, BlockPos pos, String name) {
    }

    public record OpenScreenPacket(int mode, BlockPos pos, int owlId, String hereDimension, int selected,
                                   int owlsHome, int owlsTotal, List<Entry> entries) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(mode);
            buf.writeBlockPos(pos);
            buf.writeVarInt(owlId);
            buf.writeUtf(hereDimension, MAX_DIM);
            buf.writeVarInt(selected);
            buf.writeVarInt(owlsHome);
            buf.writeVarInt(owlsTotal);
            buf.writeVarInt(entries.size());
            for (Entry e : entries) {
                buf.writeUtf(e.dimension(), MAX_DIM);
                buf.writeBlockPos(e.pos());
                buf.writeUtf(e.name(), MAX_NAME);
            }
        }

        static OpenScreenPacket decode(FriendlyByteBuf buf) {
            int mode = buf.readVarInt();
            BlockPos pos = buf.readBlockPos();
            int owlId = buf.readVarInt();
            String here = buf.readUtf(MAX_DIM);
            int selected = buf.readVarInt();
            int owlsHome = buf.readVarInt();
            int owlsTotal = buf.readVarInt();
            int n = Math.max(0, Math.min(buf.readVarInt(), MAX_ENTRIES));
            List<Entry> entries = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                entries.add(new Entry(buf.readUtf(MAX_DIM), buf.readBlockPos(), buf.readUtf(MAX_NAME)));
            }
            return new OpenScreenPacket(mode, pos, owlId, here, selected, owlsHome, owlsTotal, entries);
        }

        static void handle(OpenScreenPacket p, CustomPayloadEvent.Context ctx) {
            if (ctx.getSender() != null) {
                ctx.setPacketHandled(true); // server-to-client only
                return;
            }
            ctx.enqueueWork(() -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("openOwlPost", p));
            ctx.setPacketHandled(true);
        }
    }

    public record ActionPacket(int action, BlockPos pos, int owlId, String dimension, BlockPos dest) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(action);
            buf.writeBlockPos(pos);
            buf.writeVarInt(owlId);
            buf.writeUtf(dimension, MAX_DIM);
            buf.writeBlockPos(dest);
        }

        static ActionPacket decode(FriendlyByteBuf buf) {
            return new ActionPacket(buf.readVarInt(), buf.readBlockPos(), buf.readVarInt(), buf.readUtf(MAX_DIM), buf.readBlockPos());
        }

        static void handle(ActionPacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            ctx.setPacketHandled(true);
            if (player == null) {
                return;
            }
            ctx.enqueueWork(() -> act(player, p));
        }
    }

    /** Sends the destination screen. {@code pos} is the perch (or, for {@link #MODE_HOME}, the owl's block). */
    public static void openScreen(ServerPlayer player, int mode, BlockPos pos, int owlId, List<OwlPost.Dest> dests,
                                  int selected, int owlsHome, int owlsTotal) {
        List<Entry> entries = new ArrayList<>();
        for (OwlPost.Dest d : dests) {
            if (entries.size() >= MAX_ENTRIES) {
                break;
            }
            String name = OwlPost.displayName(d.pos(), d.name());
            entries.add(new Entry(d.pos().dimension().location().toString(), d.pos().pos(),
                    name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name));
        }
        CHANNEL.send(new OpenScreenPacket(mode, pos, owlId, player.level().dimension().location().toString(),
                selected, owlsHome, owlsTotal, entries), PacketDistributor.PLAYER.with(player));
    }

    @Nullable
    private static GlobalPos destOf(ActionPacket p) {
        ResourceLocation dim = ResourceLocation.tryParse(p.dimension());
        return dim == null ? null : GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), p.dest());
    }

    private static void act(ServerPlayer player, ActionPacket p) {
        if (player.isSpectator() || !player.isAlive()
                || com.hogwartsmagic.pensieve.PensieveSessions.isViewer(player)
                || com.hogwartsmagic.spell.mind.MindControl.isControlled(player)
                || com.hogwartsmagic.spell.mind.PossessionManager.isPossessing(player)) {
            return;
        }
        if (p.action() == ACTION_SET_HOME || p.action() == ACTION_TAKE_TAG) {
            actOnOwl(player, p);
            return;
        }
        if (!player.level().isLoaded(p.pos())
                || player.distanceToSqr(Vec3.atCenterOf(p.pos())) > OwlPost.USE_RANGE * OwlPost.USE_RANGE
                || !(player.level().getBlockEntity(p.pos()) instanceof OwlPerchBlockEntity perch)) {
            return;
        }
        GlobalPos self = perch.globalPos();
        if (p.action() == ACTION_CLEAR_DEFAULT) {
            perch.setDefaultDest(null);
            player.displayClientMessage(Component.translatableWithFallback("message.hogwartsmagic.owl_post.default_cleared",
                    "Hoppers no longer send from this perch."), true);
            return;
        }
        GlobalPos dest = destOf(p);
        List<OwlPost.Dest> dests = OwlPost.destinationsFor(player.getServer(), self);
        if (dest == null || dest.equals(self) || !OwlPost.containsPos(dests, dest)) {
            return;
        }
        String destName = OwlPost.displayName(OwlPostData.get(player.getServer()), dest);
        if (p.action() == ACTION_SET_DEFAULT) {
            perch.setDefaultDest(dest);
            player.displayClientMessage(Component.translatableWithFallback("message.hogwartsmagic.owl_post.default_set",
                    "Hoppers now send from this perch to %s.", destName).withStyle(ChatFormatting.GREEN), true);
            return;
        }
        if (p.action() != ACTION_SEND && p.action() != ACTION_SEND_AND_DEFAULT) {
            return;
        }
        if (perch.hasOutbox()) {
            player.displayClientMessage(Component.translatableWithFallback("message.hogwartsmagic.owl_post.outbox_busy",
                    "This perch is already holding a parcel. Take it back with an empty hand first."), true);
            return;
        }
        InteractionHand hand = !player.getMainHandItem().isEmpty() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            return;
        }
        ItemStack payload = held.copy();
        if (!perch.setOutbox(payload, dest, player.getUUID())) {
            return;
        }
        // Even in creative: this moves items, and a kept copy would be a free duplicate at the other end.
        player.setItemInHand(hand, ItemStack.EMPTY);
        if (p.action() == ACTION_SEND_AND_DEFAULT) {
            perch.setDefaultDest(dest);
        }
        player.displayClientMessage(Component.translatableWithFallback("message.hogwartsmagic.owl_post.sent",
                "%s x %s is waiting for an owl to %s.", payload.getCount(), payload.getHoverName(), destName)
                .withStyle(ChatFormatting.GOLD), true);
    }

    private static void actOnOwl(ServerPlayer player, ActionPacket p) {
        if (!(player.level().getEntity(p.owlId()) instanceof OwlEntity owl)
                || !owl.isAlive() || !owl.isTame() || !owl.isOwnedBy(player)
                || owl.distanceToSqr(player) > 10.0D * 10.0D || owl.isOnErrand()) {
            return;
        }
        if (p.action() == ACTION_TAKE_TAG) {
            ItemStack tag = owl.removeRouteTag();
            if (!tag.isEmpty() && !player.addItem(tag)) {
                player.drop(tag, false);
            }
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.owl.route_cleared"), true);
            return;
        }
        GlobalPos dest = destOf(p);
        if (dest == null || !OwlPost.containsPos(OwlPost.knownBy(player.getServer(), owl), dest)) {
            return;
        }
        owl.setHome(dest);
        owl.setOrderedToSit(false);
        player.displayClientMessage(Component.translatableWithFallback("message.hogwartsmagic.owl_post.home_set",
                "This owl now lives at %s.", OwlPost.displayName(OwlPostData.get(player.getServer()), dest))
                .withStyle(ChatFormatting.GREEN), true);
    }
}
