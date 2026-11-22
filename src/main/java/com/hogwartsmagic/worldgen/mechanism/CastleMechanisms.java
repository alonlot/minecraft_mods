package com.hogwartsmagic.worldgen.mechanism;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.worldgen.MiniHogwartsAnchors;
import com.hogwartsmagic.worldgen.MiniHogwartsTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The Mini Hogwarts castle's moving parts at play time: the Headmaster's gargoyle stair and the Grand Staircase's moving
 * flight (both described by {@link MiniHogwartsAnchors}). A castle's parts wake when a player comes within
 * {@link #WAKE_RANGE} of it and its blocks are loaded; they then tick every server tick while someone is near.
 *
 * <p>Before a castle's parts first move, the world is checked against the anchors: a castle generated before these parts
 * existed (its stair already standing, its flight fixed) or one rebuilt by players does not match either state of a
 * part, and that part is left alone for good, so nothing is ever written over a building the anchors do not describe.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CastleMechanisms {
    private static final Logger LOGGER = LoggerFactory.getLogger("HogwartsMagic-Mechanisms");

    static final double WAKE_RANGE = 96.0D;
    /** How close to the gargoyle (blocks) the password must be said. */
    static final double PASSWORD_RANGE = 6.0D;
    static final double HINT_RANGE = 3.5D;
    static final int HINT_EVERY = 20 * 20;
    /** At least this share of a part's cells must already be as the anchors say before it may move. */
    static final double AGREEMENT = 0.85D;

    /** One castle's runtime: its stair and flights; null parts are absent or left alone. */
    static final class Castle {
        final BlockPos anchor;
        @Nullable
        HeadmasterStair stair;
        final List<MovingStair> flights = new ArrayList<>();
        long lastTick;

        Castle(BlockPos anchor) {
            this.anchor = anchor;
        }
    }

    private static final Map<ResourceKey<Level>, Map<BlockPos, Castle>> CASTLES = new HashMap<>();
    /** Castles already judged unusable (their area loaded but not as the anchors describe), per level. */
    private static final Map<ResourceKey<Level>, java.util.Set<BlockPos>> REFUSED = new HashMap<>();
    private static final Map<UUID, Long> NEXT_HINT = new HashMap<>();

    private CastleMechanisms() {
    }

    // ── ticking ──────────────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side.isClient() || !(event.level instanceof ServerLevel level)) {
            return;
        }
        if (level.players().isEmpty()) {
            return;
        }
        MiniHogwartsAnchors anchors = MiniHogwartsAnchors.get();
        if (anchors == null || (anchors.headmasterStair() == null && anchors.movingStairs().isEmpty())) {
            return;
        }
        MiniHogwartsTracker tracker = MiniHogwartsTracker.get(level);
        Map<BlockPos, Castle> live = CASTLES.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        for (MiniHogwartsTracker.Castle record : tracker.castles()) {
            BlockPos anchor = record.anchor();
            if (anchor == null) {
                continue;
            }
            Castle castle = live.get(anchor);
            boolean isBusy = castle != null && busy(castle);
            List<ServerPlayer> near = playersNear(level, anchor, anchors, WAKE_RANGE);
            if (near.isEmpty() && !isBusy) {
                continue;
            }
            if (!loaded(level, anchor, anchors)) {
                continue;
            }
            if (castle == null) {
                if (REFUSED.computeIfAbsent(level.dimension(), k -> new java.util.HashSet<>()).contains(anchor)) {
                    continue;
                }
                castle = wake(level, anchor, anchors);
                if (castle == null) {
                    continue;
                }
                live.put(anchor, castle);
            } else if (busy(castle) && level.getGameTime() - castle.lastTick > 2) {
                // it stopped ticking mid-move (its chunks unloaded): its displays are gone, so jump to the end
                settle(castle);
            }
            castle.lastTick = level.getGameTime();
            if (castle.stair != null) {
                castle.stair.tick(near);
                hints(castle.stair, near);
            }
            for (MovingStair f : castle.flights) {
                f.tick(near);
            }
        }
    }

    private static boolean busy(Castle c) {
        if (c.stair != null && c.stair.busy()) {
            return true;
        }
        for (MovingStair f : c.flights) {
            if (f.busy()) {
                return true;
            }
        }
        return false;
    }

    private static void settle(Castle c) {
        if (c.stair != null && c.stair.busy()) {
            c.stair.settle(c.stair.phase == HeadmasterStair.Phase.OPENING);
        }
        for (MovingStair f : c.flights) {
            if (f.busy()) {
                f.settle(f.target);
            }
        }
    }

    private static List<ServerPlayer> playersNear(ServerLevel level, BlockPos anchor, MiniHogwartsAnchors anchors,
                                                  double range) {
        BlockPos mid = anchors.headmasterStair() != null ? anchor.offset(anchors.headmasterStair().listen())
                : anchor.offset(anchors.movingStairs().get(0).near());
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && p.distanceToSqr(Vec3.atCenterOf(mid)) < range * range) {
                out.add(p);
            }
        }
        return out;
    }

    /** Whether every block the castle's parts use is loaded (checked on the box round them all). */
    private static boolean loaded(ServerLevel level, BlockPos anchor, MiniHogwartsAnchors anchors) {
        int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
                Integer.MIN_VALUE};
        if (anchors.headmasterStair() != null) {
            grow(box, anchors.headmasterStair().gargoyle().open());
            grow(box, anchors.headmasterStair().cover().open());
        }
        for (MiniHogwartsAnchors.MovingStair m : anchors.movingStairs()) {
            for (List<MiniHogwartsAnchors.Placed> c : m.configs()) {
                grow(box, c);
            }
        }
        if (box[0] > box[3]) {
            return false;
        }
        return level.hasChunksAt(anchor.getX() + box[0] - 2, anchor.getY() + box[1], anchor.getZ() + box[2] - 2,
                anchor.getX() + box[3] + 2, anchor.getY() + box[4], anchor.getZ() + box[5] + 2);
    }

    private static void grow(int[] box, List<MiniHogwartsAnchors.Placed> entries) {
        for (MiniHogwartsAnchors.Placed p : entries) {
            BlockPos c = p.at();
            box[0] = Math.min(box[0], c.getX());
            box[1] = Math.min(box[1], c.getY());
            box[2] = Math.min(box[2], c.getZ());
            box[3] = Math.max(box[3], c.getX());
            box[4] = Math.max(box[4], c.getY());
            box[5] = Math.max(box[5], c.getZ());
        }
    }

    /**
     * The castle's runtime, its parts where the saved data says they are. A part saved mid-move jumps to where it was
     * going; a part the world does not agree with in either state is left out (logged once).
     */
    @Nullable
    private static Castle wake(ServerLevel level, BlockPos anchor, MiniHogwartsAnchors anchors) {
        MechanismData data = MechanismData.get(level);
        Castle castle = new Castle(anchor);
        MiniHogwartsAnchors.HeadmasterStair stairSpec = anchors.headmasterStair();
        if (stairSpec != null) {
            HeadmasterStair stair = new HeadmasterStair(level, anchor, stairSpec);
            int saved = data.stair(anchor);
            if (data.stairBusy(anchor)) {
                stair.settle(saved == 1);
                castle.stair = stair;
            } else {
                double closed = MechanismBlocks.agreement(level, anchor, stair.all(false));
                double open = MechanismBlocks.agreement(level, anchor, stair.all(true));
                if (Math.max(closed, open) >= AGREEMENT) {
                    stair.phase = open > closed ? HeadmasterStair.Phase.OPEN : HeadmasterStair.Phase.CLOSED;
                    stair.openSince = level.getGameTime();
                    castle.stair = stair;
                } else {
                    LOGGER.info("Mini Hogwarts at {}: the Headmaster's stair is not as the castle plan has it "
                            + "(closed {}, open {}); it will not move", anchor.toShortString(), closed, open);
                }
            }
        }
        for (int i = 0; i < anchors.movingStairs().size(); i++) {
            MiniHogwartsAnchors.MovingStair spec = anchors.movingStairs().get(i);
            MovingStair flight = new MovingStair(level, anchor, i, spec);
            if (data.flightBusy(anchor, i)) {
                flight.settle(Math.min(data.flight(anchor, i), spec.configs().size() - 1));
                castle.flights.add(flight);
                continue;
            }
            int best = -1;
            double bestScore = 0.0D;
            for (int k = 0; k < spec.configs().size(); k++) {
                double score = MechanismBlocks.agreement(level, anchor, spec.configs().get(k));
                if (score > bestScore) {
                    bestScore = score;
                    best = k;
                }
            }
            if (best >= 0 && bestScore >= AGREEMENT) {
                flight.config = best;
                flight.target = best;
                flight.schedule();
                castle.flights.add(flight);
            } else {
                LOGGER.info("Mini Hogwarts at {}: moving flight {} is not as the castle plan has it ({}); it will not "
                        + "move", anchor.toShortString(), spec.name(), bestScore);
            }
        }
        if (castle.stair == null && castle.flights.isEmpty()) {
            REFUSED.computeIfAbsent(level.dimension(), k -> new java.util.HashSet<>()).add(anchor);
            return null;
        }
        return castle;
    }

    // ── the password ─────────────────────────────────────────────────────────────────────

    /** Lower case, letters and digits only, single spaces: "Sherbet  lemon!" and "sherbet lemon" are one password. */
    static String normalise(String text) {
        StringBuilder out = new StringBuilder();
        boolean space = false;
        for (char ch : text.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                if (space && out.length() > 0) {
                    out.append(' ');
                }
                out.append(ch);
                space = false;
            } else {
                space = true;
            }
        }
        return out.toString();
    }

    static boolean isPassword(String said) {
        String want = normalise(Config.HEADMASTER_PASSWORD.get());
        return !want.isEmpty() && normalise(said).equals(want);
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String said = event.getRawText();
        player.server.execute(() -> heard(player, said));
    }

    /** {@code player} said {@code said} in chat: the nearest gargoyle in earshot answers. */
    static void heard(ServerPlayer player, String said) {
        ServerLevel level = player.serverLevel();
        Map<BlockPos, Castle> live = CASTLES.get(level.dimension());
        if (live == null) {
            return;
        }
        for (Castle c : live.values()) {
            HeadmasterStair stair = c.stair;
            if (stair == null) {
                continue;
            }
            double d = player.position().add(0.0D, 1.0D, 0.0D).distanceTo(stair.listenPoint());
            if (d > PASSWORD_RANGE) {
                continue;
            }
            if (isPassword(said)) {
                if (stair.raise()) {
                    player.displayClientMessage(Component.translatable("message.hogwartsmagic.headmaster_stair.open"), true);
                } else if (stair.isOpen()) {
                    player.displayClientMessage(Component.translatable("message.hogwartsmagic.headmaster_stair.already"), true);
                }
            } else if (stair.phase == HeadmasterStair.Phase.CLOSED && said.length() <= 40) {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.headmaster_stair.wrong"), true);
            }
            return;
        }
    }

    private static void hints(HeadmasterStair stair, List<ServerPlayer> near) {
        if (stair.phase != HeadmasterStair.Phase.CLOSED) {
            return;
        }
        long now = stair.level.getGameTime();
        Vec3 g = stair.listenPoint();
        for (ServerPlayer p : near) {
            if (p.position().add(0.0D, 1.0D, 0.0D).distanceTo(g) > HINT_RANGE) {
                continue;
            }
            Long next = NEXT_HINT.get(p.getUUID());
            if (next != null && now < next) {
                continue;
            }
            NEXT_HINT.put(p.getUUID(), now + HINT_EVERY);
            boolean usual = normalise(Config.HEADMASTER_PASSWORD.get()).equals("sherbet lemon");
            p.displayClientMessage(Component.translatable(usual ? "message.hogwartsmagic.headmaster_stair.hint_sweets"
                    : "message.hogwartsmagic.headmaster_stair.hint"), true);
        }
    }

    // ── leftovers and shutdown ───────────────────────────────────────────────────────────

    /** A mechanism display that no animation owns (saved in a chunk when the server stopped mid-move) never loads. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity e = event.getEntity();
        if (e instanceof Display.BlockDisplay && e.getTags().contains(MechanismBlocks.DISPLAY_TAG)
                && !MechanismBlocks.LIVE.contains(e.getUUID()) && !event.getLevel().isClientSide()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Map<BlockPos, Castle> live : CASTLES.values()) {
            for (Castle c : live.values()) {
                try {
                    settle(c);
                } catch (RuntimeException e) {
                    LOGGER.error("Could not settle the castle mechanisms at {}", c.anchor.toShortString(), e);
                }
            }
        }
        CASTLES.clear();
        REFUSED.clear();
        NEXT_HINT.clear();
        MechanismBlocks.LIVE.clear();
    }

    // ── for GameTests and commands ───────────────────────────────────────────────────────

    /** Drop the castle's runtime (its moving parts settled where they were going) and never wake it again. */
    static void forget(ServerLevel level, BlockPos anchor) {
        Map<BlockPos, Castle> live = CASTLES.get(level.dimension());
        Castle c = live == null ? null : live.remove(anchor);
        if (c != null) {
            settle(c);
        }
        REFUSED.computeIfAbsent(level.dimension(), k -> new java.util.HashSet<>()).add(anchor);
    }

    /** The stair of the castle anchored at {@code anchor} in {@code level}, if it is awake. */
    @Nullable
    static HeadmasterStair stairAt(ServerLevel level, BlockPos anchor) {
        Map<BlockPos, Castle> live = CASTLES.get(level.dimension());
        Castle c = live == null ? null : live.get(anchor);
        return c == null ? null : c.stair;
    }
}
