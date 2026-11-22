package com.hogwartsmagic.worldgen.mechanism;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.worldgen.MiniHogwartsAnchors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One of the Grand Staircase's moving flights, for one castle. It rests docked (config 0, the way up) and every so
 * often, while someone is near enough to see it and nobody stands on it, swings about its pivot to its other config
 * and docks there for a while, then swings back. While it swings, its blocks are block displays turning about the
 * pivot, and the fences of both configs' gates stand, so no edge is ever left open.
 */
final class MovingStair {
    static final int SWING_TICKS = 80;
    /** Docked on the way up: how long before it wanders (ticks), at least and at most. */
    static final int DOCKED_MIN = 20 * 35;
    static final int DOCKED_MAX = 20 * 70;
    /** Swung away: how long it stays. */
    static final int AWAY_MIN = 20 * 10;
    static final int AWAY_MAX = 20 * 16;
    /** A player this close (blocks, to its middle) keeps it moving. */
    static final double WATCH_RANGE = 48.0D;

    final ServerLevel level;
    final BlockPos anchor;
    final int index;
    final MiniHogwartsAnchors.MovingStair spec;

    int config;
    int from;
    int target;
    int t = -1;                                           // -1 while docked
    long nextSwing;
    private final List<Display.BlockDisplay> displays = new ArrayList<>();
    private final Set<UUID> ids = new HashSet<>();

    MovingStair(ServerLevel level, BlockPos anchor, int index, MiniHogwartsAnchors.MovingStair spec) {
        this.level = level;
        this.anchor = anchor;
        this.index = index;
        this.spec = spec;
    }

    boolean busy() {
        return t >= 0;
    }

    void settle(int cfg) {
        MechanismBlocks.discard(level, displays);
        MechanismBlocks.discardIds(level, ids);
        MechanismBlocks.write(level, anchor, spec.configs().get(cfg));
        config = cfg;
        target = cfg;
        t = -1;
        MechanismData.get(level).setFlight(anchor, index, cfg, false);
        schedule();
    }

    void schedule() {
        long now = level.getGameTime();
        var r = level.getRandom();
        nextSwing = now + (config == 0 ? DOCKED_MIN + r.nextInt(DOCKED_MAX - DOCKED_MIN + 1)
                : AWAY_MIN + r.nextInt(AWAY_MAX - AWAY_MIN + 1));
    }

    void tick(List<ServerPlayer> near) {
        if (busy()) {
            tickSwing();
            return;
        }
        boolean enabled = Config.MOVING_STAIRCASES.get();
        if (!enabled && config == 0) {
            return;
        }
        if (level.getGameTime() < nextSwing && enabled) {
            return;
        }
        BlockPos mid = anchor.offset(spec.near());
        boolean watched = false;
        for (ServerPlayer p : near) {
            if (p.distanceToSqr(mid.getX() + 0.5D, mid.getY(), mid.getZ() + 0.5D) < WATCH_RANGE * WATCH_RANGE) {
                watched = true;
                break;
            }
        }
        if (!watched || occupied(near)) {
            if (!watched && config != 0 && !occupied(near)) {
                settle(0);                                // nobody to see it: it is back where it belongs
            }
            return;
        }
        start(enabled ? (config + 1) % spec.configs().size() : 0);
    }

    private boolean occupied(List<ServerPlayer> near) {
        for (ServerPlayer p : near) {
            AABB box = p.getBoundingBox().inflate(0.25D);
            for (BlockPos r : spec.riders()) {
                BlockPos w = anchor.offset(r);
                if (box.intersects(new AABB(w.getX(), w.getY(), w.getZ(), w.getX() + 1, w.getY() + 2, w.getZ() + 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Start swinging to config {@code to}: the flight's blocks become displays, both configs' gates stand. */
    void start(int to) {
        from = config;
        target = to;
        t = 0;
        MechanismData.get(level).setFlight(anchor, index, to, true);
        // take away the flight (every cell of the source config that is not one of its gates), put up the target's gates
        Set<BlockPos> keep = new HashSet<>(spec.gates().get(from));
        List<MiniHogwartsAnchors.Placed> clear = new ArrayList<>();
        for (MiniHogwartsAnchors.Placed p : spec.configs().get(from)) {
            if (!keep.contains(p.at())) {
                clear.add(new MiniHogwartsAnchors.Placed(p.at(), "minecraft:cave_air"));
            }
        }
        MechanismBlocks.write(level, anchor, clear);
        Set<BlockPos> gates = new HashSet<>(spec.gates().get(to));
        List<MiniHogwartsAnchors.Placed> up = new ArrayList<>();
        for (MiniHogwartsAnchors.Placed p : spec.configs().get(to)) {
            if (gates.contains(p.at())) {
                up.add(p);
            }
        }
        MechanismBlocks.write(level, anchor, up);
        // the displays: every moving block, drawn about the pivot from its docked place, turned as config `from` is
        double px = anchor.getX() + spec.pivotX();
        double pz = anchor.getZ() + spec.pivotZ();
        float yaw = 90.0F * spec.turns().get(from);
        BlockPos light = anchor.offset(spec.near());
        for (MiniHogwartsAnchors.Placed p : spec.moving()) {
            BlockPos w = anchor.offset(p.at());
            Display.BlockDisplay d = MechanismBlocks.spawn(level, MechanismBlocks.state(p.state()), px, w.getY(), pz, yaw,
                    (float) (w.getX() - px), 0.0F, (float) (w.getZ() - pz), light, 2);
            if (d != null) {
                displays.add(d);
                ids.add(d.getUUID());
            }
        }
        level.playSound(null, light, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 1.2F, 0.45F);
    }

    private void tickSwing() {
        double u = MechanismBlocks.ease(t / (double) SWING_TICKS);
        float a = spec.turns().get(from);
        float b = spec.turns().get(target);
        float yaw = (float) (90.0D * (a + (b - a) * u));
        double px = anchor.getX() + spec.pivotX();
        double pz = anchor.getZ() + spec.pivotZ();
        for (Display.BlockDisplay d : displays) {
            MechanismBlocks.move(d, px, d.getY(), pz, yaw);
        }
        BlockPos mid = anchor.offset(spec.near());
        if (t % 10 == 0 && t < SWING_TICKS) {
            level.playSound(null, mid, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1.0F, 0.4F + 0.1F * level.getRandom().nextFloat());
        }
        if (t >= SWING_TICKS) {
            MechanismBlocks.discard(level, displays);
            ids.clear();
            MechanismBlocks.write(level, anchor, spec.configs().get(target));
            level.playSound(null, mid, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.35F, 0.5F);
            config = target;
            t = -1;
            MechanismData.get(level).setFlight(anchor, index, config, false);
            schedule();
            return;
        }
        t++;
    }
}
