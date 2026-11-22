package com.hogwartsmagic.worldgen.mechanism;

import com.hogwartsmagic.worldgen.MiniHogwartsAnchors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** GameTests only: drive one mechanism directly, tick by tick, without a castle or a player near it. */
public final class MechanismTestHooks {
    private MechanismTestHooks() {
    }

    public static final class Stair {
        private final HeadmasterStair s;

        Stair(HeadmasterStair s) {
            this.s = s;
        }

        public boolean raise() {
            return s.raise();
        }

        /** Start sinking now (as if nobody had used it for long enough). */
        public void lower() {
            s.openSince = s.level.getGameTime() - HeadmasterStair.OPEN_TICKS - 1;
        }

        public void tick(List<ServerPlayer> near) {
            s.tick(near);
        }

        public void settle(boolean open) {
            s.settle(open);
        }

        public String phase() {
            return s.phase.name();
        }
    }

    public static final class Flight {
        private final MovingStair f;

        Flight(MovingStair f) {
            this.f = f;
        }

        public void start(int to) {
            f.start(to);
        }

        public void tick(List<ServerPlayer> near) {
            f.tick(near);
        }

        public void settle(int config) {
            f.settle(config);
        }

        public boolean busy() {
            return f.busy();
        }

        public int config() {
            return f.config;
        }
    }

    public static Stair stair(ServerLevel level, BlockPos anchor, MiniHogwartsAnchors.HeadmasterStair spec) {
        return new Stair(new HeadmasterStair(level, anchor, spec));
    }

    public static Flight flight(ServerLevel level, BlockPos anchor, MiniHogwartsAnchors.MovingStair spec) {
        return new Flight(new MovingStair(level, anchor, 99, spec));
    }

    public static boolean isPassword(String said) {
        return CastleMechanisms.isPassword(said);
    }

    /** Hand {@code player}'s chat line to the gargoyles, as the chat event does. */
    public static void heard(ServerPlayer player, String said) {
        CastleMechanisms.heard(player, said);
    }

    /** The phase of the awake stair of the castle at {@code anchor}, or null when that castle is not awake. */
    @javax.annotation.Nullable
    public static String stairPhase(ServerLevel level, BlockPos anchor) {
        HeadmasterStair s = CastleMechanisms.stairAt(level, anchor);
        return s == null ? null : s.phase.name();
    }

    /** Put the castle at {@code anchor} to sleep for good, its parts where they are (a test's castle, at its end). */
    public static void forget(ServerLevel level, BlockPos anchor) {
        CastleMechanisms.forget(level, anchor);
    }

    /** How many mechanism displays are alive (owned by a running animation). */
    public static int liveDisplays() {
        return MechanismBlocks.LIVE.size();
    }
}
