package com.hogwartsmagic.owlpost;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.OwlPerchBlockEntity;
import com.hogwartsmagic.entity.OwlEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Owl post: sending a stack from one perch to another, any distance away.
 *
 * <p>An owl lives at a home perch and knows the perches on the route tag it was given. A stack put in a perch's
 * outbox is taken by one of the owls that live there and know its destination; the owl flies up and out of sight
 * ({@link OwlEntity}), and from then on the trip is bookkeeping in {@link OwlPostData}: the stack lands in the
 * destination's inbox after {@link #travelTicks}, and the owl reappears above its home perch after the round trip.
 * Neither needs the far chunk loaded on time; each waits for its chunk.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OwlPost {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** How close (blocks) an owl that lives at a perch has to be to pick up its outbox. */
    public static final double PICKUP_RANGE = 8.0D;
    /** How close a player must be to a perch (or their owl) for its screens. */
    public static final double USE_RANGE = 6.0D;
    /** A trip between dimensions takes this long, whatever the coordinates. */
    private static final int CROSS_DIMENSION_TICKS = 600;
    private static final int MAX_TRAVEL_TICKS = 6000;

    public record Dest(GlobalPos pos, String name) {
    }

    private OwlPost() {
    }

    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    /** Out of sight an owl covers about twenty blocks a second, plus two seconds for take-off and landing. */
    public static int travelTicks(GlobalPos from, GlobalPos to) {
        if (!from.dimension().equals(to.dimension())) {
            return CROSS_DIMENSION_TICKS;
        }
        double distance = Math.sqrt(from.pos().distSqr(to.pos()));
        return (int) Math.min(MAX_TRAVEL_TICKS, 40 + Math.round(distance));
    }

    public static String displayName(GlobalPos pos, String name) {
        if (name != null && !name.isBlank()) {
            return name;
        }
        BlockPos p = pos.pos();
        return "Perch at " + p.getX() + " " + p.getY() + " " + p.getZ();
    }

    public static String displayName(OwlPostData data, GlobalPos pos) {
        return displayName(pos, data.perchName(pos));
    }

    // ------------------------------------------------------------------ destinations

    /** Every perch an owl living at {@code perch} can fly to: what those owls know, minus the perch itself and any perch that is gone. */
    public static List<Dest> destinationsFor(MinecraftServer server, GlobalPos perch) {
        OwlPostData data = OwlPostData.get(server);
        Set<GlobalPos> known = new LinkedHashSet<>();
        for (Map.Entry<UUID, OwlPostData.OwlRecord> owl : data.owlsHomedAt(perch)) {
            known.addAll(owl.getValue().known());
        }
        return sortedDests(data, perch, known);
    }

    /**
     * The perches this owl could live at: ones it knows that still exist, in its dimension and near enough for it to
     * stay there ({@link OwlEntity#HOME_RANGE}; farther, it would follow its owner and never be home).
     */
    public static List<Dest> knownBy(MinecraftServer server, OwlEntity owl) {
        OwlPostData data = OwlPostData.get(server);
        GlobalPos here = GlobalPos.of(owl.level().dimension(), owl.blockPosition());
        List<GlobalPos> near = new ArrayList<>();
        for (GlobalPos pos : owl.getKnownPerches()) {
            if (pos.dimension().equals(here.dimension())
                    && pos.pos().distSqr(here.pos()) <= OwlEntity.HOME_RANGE * OwlEntity.HOME_RANGE) {
                near.add(pos);
            }
        }
        return sortedDests(data, here, near);
    }

    private static List<Dest> sortedDests(OwlPostData data, GlobalPos from, Iterable<GlobalPos> positions) {
        List<Dest> out = new ArrayList<>();
        for (GlobalPos pos : positions) {
            if (!pos.equals(from) && data.hasPerch(pos)) {
                out.add(new Dest(pos, data.perchName(pos)));
            }
        }
        out.sort(Comparator.<Dest>comparingInt(d -> d.pos().dimension().equals(from.dimension()) ? 0 : 1)
                .thenComparingDouble(d -> d.pos().pos().distSqr(from.pos())));
        return out;
    }

    public static boolean containsPos(List<Dest> dests, GlobalPos pos) {
        for (Dest d : dests) {
            if (d.pos().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ dispatch

    /** Hands the perch's outbox to an owl that lives here, is close, idle and knows the destination. */
    public static boolean tryDispatch(ServerLevel level, OwlPerchBlockEntity perch) {
        GlobalPos self = perch.globalPos();
        GlobalPos dest = perch.getOutboxDest();
        if (dest == null || !perch.hasOutbox()) {
            return false;
        }
        if (dest.equals(self)) {
            return false; // the screens never offer the perch itself; nothing to fly
        }
        if (!OwlPostData.get(level.getServer()).hasPerch(dest)) {
            return false; // the destination perch is gone; the status message says so, the stack stays here
        }
        List<OwlEntity> owls = level.getEntitiesOfClass(OwlEntity.class,
                new AABB(perch.getBlockPos()).inflate(PICKUP_RANGE),
                owl -> owl.isAlive() && owl.isTame() && self.equals(owl.getHome()) && !owl.isOnErrand() && owl.knows(dest));
        if (owls.isEmpty()) {
            return false;
        }
        Vec3 top = Vec3.atCenterOf(perch.getBlockPos()).add(0.0D, 1.0D, 0.0D);
        owls.sort(Comparator.comparingDouble(owl -> owl.position().distanceToSqr(top)));
        OwlEntity owl = owls.get(0);
        UUID sender = perch.getOutboxSender();
        ItemStack payload = perch.takeOutbox();
        if (payload.isEmpty()) {
            return false;
        }
        owl.carryOff(payload, self, dest, sender);
        return true;
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 10 != 0) {
            return;
        }
        tick(event.getServer());
    }

    public static void tick(MinecraftServer server) {
        OwlPostData data = OwlPostData.get(server);
        if (data.deliveries.isEmpty() && data.returns.isEmpty()) {
            return;
        }
        long now = now(server);
        tickDeliveries(server, data, now);
        tickReturns(server, data, now);
    }

    private static void tickDeliveries(MinecraftServer server, OwlPostData data, long now) {
        Iterator<OwlPostData.Delivery> it = data.deliveries.iterator();
        List<OwlPostData.Delivery> rerouted = new ArrayList<>();
        while (it.hasNext()) {
            OwlPostData.Delivery d = it.next();
            if (now < d.arriveAt) {
                continue;
            }
            ServerLevel level = server.getLevel(d.dest.dimension());
            if (level == null) {
                // The dimension is gone (a removed mod): send it back, or give up on it with a log line.
                it.remove();
                data.setDirty();
                if (d.returnTo != null && !d.returnTo.equals(d.dest)) {
                    rerouted.add(new OwlPostData.Delivery(d.returnTo, d.payload, now + CROSS_DIMENSION_TICKS, d.sender, null));
                } else {
                    LOGGER.warn("Owl post: dropped {} bound for missing dimension {}", d.payload, d.dest.dimension().location());
                }
                continue;
            }
            BlockPos pos = d.dest.pos();
            if (!level.isLoaded(pos)) {
                continue; // lands when somebody is there to load the chunk
            }
            if (level.getBlockEntity(pos) instanceof OwlPerchBlockEntity perch) {
                ItemStack rest = perch.acceptDelivery(d.payload);
                if (rest.getCount() != d.payload.getCount()) {
                    arrivalEffects(level, pos);
                }
                if (rest.isEmpty()) {
                    it.remove();
                    notifySender(server, d.sender, Component.translatableWithFallback("message.hogwartsmagic.owl_post.delivered",
                            "Owl post: %s x %s delivered to %s.", d.payload.getCount(), d.payload.getHoverName(),
                            displayName(data, d.dest)).withStyle(ChatFormatting.GOLD));
                } else {
                    d.payload = rest; // inbox full: the owl leaves the rest on the next round
                }
                data.setDirty();
                continue;
            }
            // The perch was broken while the owl was in the air.
            it.remove();
            data.setDirty();
            data.removePerch(d.dest);
            if (d.returnTo != null && !d.returnTo.equals(d.dest)) {
                rerouted.add(new OwlPostData.Delivery(d.returnTo, d.payload, now + travelTicks(d.dest, d.returnTo), d.sender, null));
                notifySender(server, d.sender, Component.translatableWithFallback("message.hogwartsmagic.owl_post.returned",
                        "Owl post: the perch at %s is gone, so your owl is bringing the parcel back.", displayName(d.dest, ""))
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, d.payload.copy());
            }
        }
        data.deliveries.addAll(rerouted);
    }

    private static void tickReturns(MinecraftServer server, OwlPostData data, long now) {
        Iterator<OwlPostData.Return> it = data.returns.iterator();
        while (it.hasNext()) {
            OwlPostData.Return r = it.next();
            if (now < r.at) {
                continue;
            }
            ServerLevel level = server.getLevel(r.home.dimension());
            BlockPos home = r.home.pos();
            if (level == null) {
                level = server.overworld();
                home = level.getSharedSpawnPos();
            }
            if (!level.isLoaded(home) || !level.isPositionEntityTicking(home)) {
                continue; // comes home when its perch's chunk is in play again
            }
            it.remove();
            data.setDirty();
            if (findEntity(server, r.owlId) != null) {
                continue; // somehow already back: never two of the same owl
            }
            Optional<Entity> created = EntityType.create(r.owl, level);
            if (created.isEmpty()) {
                LOGGER.warn("Owl post: could not bring owl {} home to {}", r.owlId, r.home);
                continue;
            }
            Entity owl = created.get();
            Vec3 spot = returnSpot(level, home, owl);
            owl.moveTo(spot.x, spot.y, spot.z, level.random.nextFloat() * 360.0F, 0.0F);
            owl.setDeltaMovement(Vec3.ZERO);
            if (!level.addFreshEntity(owl)) {
                LOGGER.warn("Owl post: owl {} could not be added back at {}", r.owlId, r.home);
            }
        }
    }

    /** High over the perch if the sky is open there (it glides down), otherwise right on top of it. */
    private static Vec3 returnSpot(ServerLevel level, BlockPos home, Entity owl) {
        for (int up = 14; up >= 1; up--) {
            Vec3 at = Vec3.atBottomCenterOf(home).add(0.0D, up, 0.0D);
            if (at.y < level.getMaxBuildHeight() - 2 && level.noCollision(owl, owl.getDimensions(owl.getPose()).makeBoundingBox(at))) {
                boolean clearBelow = true;
                for (int y = 1; y < up; y++) {
                    if (!level.getBlockState(home.above(y)).getCollisionShape(level, home.above(y)).isEmpty()) {
                        clearBelow = false;
                        break;
                    }
                }
                if (clearBelow) {
                    return at;
                }
            }
        }
        return Vec3.atBottomCenterOf(home).add(0.0D, 1.0D, 0.0D);
    }

    @Nullable
    private static Entity findEntity(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(id);
            if (e != null) {
                return e;
            }
        }
        return null;
    }

    private static void arrivalEffects(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, com.hogwartsmagic.ModSounds.OWL_AMBIENT.get(), SoundSource.NEUTRAL, 0.9F, 1.05F);
        level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5D, pos.getY() + 1.4D, pos.getZ() + 0.5D, 6, 0.3D, 0.2D, 0.3D, 0.01D);
    }

    private static void notifySender(MinecraftServer server, @Nullable UUID sender, Component message) {
        if (sender == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(sender);
        if (player != null) {
            player.displayClientMessage(message, false);
        }
    }

    // ------------------------------------------------------------------ screens and status

    /** A player used a perch with an item in hand: the destination screen, or why there is none. */
    public static void openSendScreen(ServerPlayer player, OwlPerchBlockEntity perch) {
        MinecraftServer server = player.getServer();
        GlobalPos self = perch.globalPos();
        perch.registerNow(player.serverLevel());
        if (perch.hasOutbox()) {
            player.displayClientMessage(Component.translatableWithFallback("message.hogwartsmagic.owl_post.outbox_busy",
                    "This perch is already holding a parcel. Take it back with an empty hand first."), true);
            return;
        }
        OwlPostData data = OwlPostData.get(server);
        int owls = data.owlsHomedAt(self).size();
        if (owls == 0) {
            player.displayClientMessage(Component.translatableWithFallback("message.hogwartsmagic.owl_post.no_owls",
                    "No owl lives at this perch. Give your owl a Route Tag with this perch on it, then make it the owl's home."), false);
            return;
        }
        List<Dest> dests = destinationsFor(server, self);
        if (dests.isEmpty()) {
            player.displayClientMessage(Component.translatableWithFallback("message.hogwartsmagic.owl_post.no_destinations",
                    "The owls here know no other perch. Add perches to a Route Tag and give it to an owl that lives here."), false);
            return;
        }
        OwlPostNet.openScreen(player, OwlPostNet.MODE_SEND, perch.getBlockPos(), -1, dests,
                indexOf(dests, perch.getDefaultDest()), owlsAtHome(player.serverLevel(), self), owls);
    }

    /** Sneak-use with an empty hand: choose (or clear) where hoppers send to. */
    public static void openDefaultScreen(ServerPlayer player, OwlPerchBlockEntity perch) {
        MinecraftServer server = player.getServer();
        GlobalPos self = perch.globalPos();
        perch.registerNow(player.serverLevel());
        List<Dest> dests = destinationsFor(server, self);
        if (dests.isEmpty() && perch.getDefaultDest() == null) {
            player.displayClientMessage(Component.translatableWithFallback("message.hogwartsmagic.owl_post.no_destinations",
                    "The owls here know no other perch. Add perches to a Route Tag and give it to an owl that lives here."), false);
            return;
        }
        OwlPostNet.openScreen(player, OwlPostNet.MODE_DEFAULT, perch.getBlockPos(), -1, dests,
                indexOf(dests, perch.getDefaultDest()), owlsAtHome(player.serverLevel(), self),
                OwlPostData.get(server).owlsHomedAt(self).size());
    }

    /** Sneak-use on your owl with an empty hand: choose its home among the perches it knows. */
    public static void openOwlScreen(ServerPlayer player, OwlEntity owl) {
        List<Dest> dests = knownBy(player.getServer(), owl);
        if (dests.isEmpty()) {
            // Still open it: "Take Back Tag" has to stay reachable.
            player.displayClientMessage(Component.translatableWithFallback("message.hogwartsmagic.owl_post.no_home_near",
                    "None of the perches on this owl's route tag is near enough to live at. Bring the owl to one of them."), false);
        }
        OwlPostNet.openScreen(player, OwlPostNet.MODE_HOME, owl.blockPosition(), owl.getId(), dests,
                indexOf(dests, owl.getHome()), 0, 0);
    }

    private static int indexOf(List<Dest> dests, @Nullable GlobalPos pos) {
        if (pos == null) {
            return -1;
        }
        for (int i = 0; i < dests.size(); i++) {
            if (dests.get(i).pos().equals(pos)) {
                return i;
            }
        }
        return -1;
    }

    private static int owlsAtHome(ServerLevel level, GlobalPos perch) {
        return level.getEntitiesOfClass(OwlEntity.class, new AABB(perch.pos()).inflate(PICKUP_RANGE),
                owl -> owl.isAlive() && perch.equals(owl.getHome()) && !owl.isOnErrand()).size();
    }

    /** What a plain click on a perch with nothing in it says. */
    public static Component statusMessage(ServerLevel level, OwlPerchBlockEntity perch) {
        OwlPostData data = OwlPostData.get(level.getServer());
        GlobalPos self = perch.globalPos();
        MutableComponent msg = Component.literal(displayName(self, perch.getPerchName())).withStyle(ChatFormatting.GOLD);
        int owls = data.owlsHomedAt(self).size();
        int home = owlsAtHome(level, self);
        msg.append(Component.literal(" - ").withStyle(ChatFormatting.GRAY));
        if (owls == 0) {
            msg.append(Component.translatableWithFallback("message.hogwartsmagic.owl_post.status.no_owls", "no owl lives here")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            msg.append(Component.translatableWithFallback("message.hogwartsmagic.owl_post.status.owls",
                    "%s owl(s) live here, %s at home", owls, home).withStyle(ChatFormatting.WHITE));
        }
        if (perch.getDefaultDest() != null) {
            msg.append(Component.literal("\n"));
            msg.append(Component.translatableWithFallback("message.hogwartsmagic.owl_post.status.default",
                    "Hoppers send to: %s", displayName(data, perch.getDefaultDest())).withStyle(ChatFormatting.GRAY));
        }
        if (perch.hasOutbox() && perch.getOutboxDest() != null) {
            msg.append(Component.literal("\n"));
            String dest = displayName(data, perch.getOutboxDest());
            Component reason;
            if (!data.hasPerch(perch.getOutboxDest())) {
                reason = Component.translatableWithFallback("message.hogwartsmagic.owl_post.status.dest_gone", "that perch is gone");
            } else if (home == 0) {
                reason = Component.translatableWithFallback("message.hogwartsmagic.owl_post.status.no_owl_home", "waiting for an owl to come home");
            } else {
                reason = Component.translatableWithFallback("message.hogwartsmagic.owl_post.status.no_owl_knows", "waiting for an owl that knows the way");
            }
            msg.append(Component.translatableWithFallback("message.hogwartsmagic.owl_post.status.outbox",
                    "Sending %s x %s to %s (%s)", perch.peekOutbox().getCount(), perch.peekOutbox().getHoverName(), dest, reason)
                    .withStyle(ChatFormatting.YELLOW));
        }
        int waiting = data.deliveriesWaitingFor(self, now(level.getServer()));
        if (waiting > 0) {
            msg.append(Component.literal("\n"));
            msg.append(Component.translatableWithFallback("message.hogwartsmagic.owl_post.status.waiting",
                    "%s parcel(s) waiting for room in the inbox", waiting).withStyle(ChatFormatting.YELLOW));
        }
        return msg;
    }
}
