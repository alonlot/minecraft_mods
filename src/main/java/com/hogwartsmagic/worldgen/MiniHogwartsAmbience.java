package com.hogwartsmagic.worldgen;

import com.hogwartsmagic.ambience.AmbienceRegistry;
import com.hogwartsmagic.ambience.AmbientEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The castle's own ambient effects: the Great Hall's enchanted ceiling, its floating candles, its window light rays
 * and its door leaves, worked out from the anchors and registered in the level's {@link AmbienceRegistry} when the
 * castle is generated. Every effect gets a name-based id from the castle's anchor, so a chunk decorated twice, or a
 * server restarted over a saved world, registers nothing twice.
 *
 * <p>{@link #effectsFor} is pure (safe on a worldgen thread, which is where the anchors are read); {@link #register}
 * touches the level's {@code SavedData} and so runs on the server thread only.
 */
public final class MiniHogwartsAmbience {
    private MiniHogwartsAmbience() {
    }

    /** The id of the castle at {@code anchor}'s hall ceiling: the effect a first visit reveals. */
    public static UUID ceilingId(BlockPos anchor) {
        return id(anchor, "ceiling");
    }

    private static UUID id(BlockPos anchor, String part) {
        String name = "hogwartsmagic:mini_hogwarts:" + anchor.getX() + "," + anchor.getY() + "," + anchor.getZ() + ":" + part;
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Every effect of the castle placed with its schematic min corner at {@code anchor}, in world space: the anchors'
     * local positions plus the anchor. One ceiling (centre and half extents from the rectangle, {@code hy} the roof
     * clearance, sealed: a plank roof until a player has entered the hall), one candle effect per candle box, one ray per window and one door per leaf ({@code count == 1}
     * marks the mirrored leaf). Castle effects have no owner.
     */
    public static List<AmbientEffect> effectsFor(BlockPos anchor, MiniHogwartsAnchors anchors, long createdTick) {
        List<AmbientEffect> out = new ArrayList<>();
        double ax = anchor.getX(), ay = anchor.getY(), az = anchor.getZ();
        MiniHogwartsAnchors.Ceiling c = anchors.hallCeiling();
        out.add(new AmbientEffect(ceilingId(anchor), AmbientEffect.KIND_CEILING,
                ax + (c.minX() + c.maxX()) * 0.5, ay + c.y(), az + (c.minZ() + c.maxZ()) * 0.5,
                (float) ((c.maxX() - c.minX()) * 0.5), (float) c.clearance(), (float) ((c.maxZ() - c.minZ()) * 0.5),
                0f, AmbientEffect.CEILING_SEALED, null, createdTick));
        List<MiniHogwartsAnchors.Box> candles = anchors.candles();
        for (int i = 0; i < candles.size(); i++) {
            MiniHogwartsAnchors.Box b = candles.get(i);
            out.add(new AmbientEffect(id(anchor, "candles#" + i), AmbientEffect.KIND_CANDLES,
                    ax + b.at().x, ay + b.at().y, az + b.at().z,
                    (float) b.spread().x, (float) b.spread().y, (float) b.spread().z, 0f, b.count(), null, createdTick));
        }
        List<MiniHogwartsAnchors.Ray> rays = anchors.rays();
        for (int i = 0; i < rays.size(); i++) {
            MiniHogwartsAnchors.Ray r = rays.get(i);
            // A ray's half extents are the window's half width, its height and how far the light reaches.
            out.add(new AmbientEffect(id(anchor, "ray#" + i), AmbientEffect.KIND_RAYS,
                    ax + r.at().x, ay + r.at().y, az + r.at().z,
                    (float) r.spread().x, (float) r.spread().y, (float) r.spread().z, r.yaw(), 0, null, createdTick));
        }
        List<MiniHogwartsAnchors.Door> doors = anchors.doors();
        for (int i = 0; i < doors.size(); i++) {
            MiniHogwartsAnchors.Door d = doors.get(i);
            out.add(new AmbientEffect(id(anchor, "door#" + i), AmbientEffect.KIND_DOOR,
                    ax + d.hinge().x, ay + d.hinge().y, az + d.hinge().z, 0f, 0f, 0f, d.yawClosed(),
                    d.mirrored() ? 1 : 0, null, createdTick));
        }
        return out;
    }

    /**
     * Registers the castle's effects in this level, each told to the players once: an effect the level does not hold is
     * added, one it holds that differs from the anchors' in anything but when it was registered (a saved world whose
     * anchors changed since: a door's yaw, a ceiling's clearance) is replaced, keeping its first registration time, and
     * one that matches is left alone, so decorating the same castle again tells nobody. Server thread only. Returns how
     * many were added or replaced.
     */
    public static int register(ServerLevel level, BlockPos anchor, MiniHogwartsAnchors anchors) {
        AmbienceRegistry registry = AmbienceRegistry.get(level);
        int changed = 0;
        for (AmbientEffect e : effectsFor(anchor, anchors, level.getGameTime())) {
            AmbientEffect held = registry.get(e.id());
            if (held == null) {
                registry.add(level, e);
                changed++;
            } else if (!held.sameAs(e)) {
                registry.add(level, new AmbientEffect(e.id(), e.kind(), e.x(), e.y(), e.z(), e.hx(), e.hy(), e.hz(),
                        e.yaw(), e.count(), e.owner(), held.createdTick()));
                changed++;
            }
        }
        return changed;
    }

    /**
     * The castle ceilings in this player's level that the player has already had revealed (it entered that castle's
     * hall), for the sync that follows a login, a respawn or a dimension change: the client draws those open. Reads
     * the tracker only.
     */
    public static List<UUID> openedFor(ServerPlayer player) {
        List<UUID> out = new ArrayList<>();
        for (MiniHogwartsTracker.Castle castle : MiniHogwartsTracker.get(player.serverLevel()).castles()) {
            if (castle.anchor() != null && castle.enteredHall().contains(player.getUUID())) {
                out.add(ceilingId(castle.anchor()));
            }
        }
        return out;
    }
}
