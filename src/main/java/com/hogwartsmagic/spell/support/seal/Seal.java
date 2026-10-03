package com.hogwartsmagic.spell.support.seal;

import java.util.Arrays;
import java.util.UUID;

/**
 * One Colloportus seal: every block within radius of (x, y, z), or within radius of any anchor, is sealed.
 * ownerName is the caster's name when the seal was made; createdAt is the game time then. Anchors are the door
 * pistons traced when the seal was made, so a seal cast at a piston lock's button also covers the pistons. They
 * are packed positions (see {@link #pack}) in a long[]; empty for plain doors. Pure data, no Minecraft types.
 * Do not modify the array returned by anchors().
 */
public record Seal(UUID owner, String ownerName, int rank, int x, int y, int z, int radius, long createdAt,
                   long[] anchors) {
    private static final long[] NONE = new long[0];

    public Seal {
        anchors = anchors == null || anchors.length == 0 ? NONE : anchors.clone();
    }

    /** A seal without anchors (a plain door). */
    public Seal(UUID owner, String ownerName, int rank, int x, int y, int z, int radius, long createdAt) {
        this(owner, ownerName, rank, x, y, z, radius, createdAt, NONE);
    }

    /** Packs a position the way Minecraft's BlockPos does: x 26 bits, z 26 bits, y 12 bits (signed). */
    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) (y & 0xFFF);
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    public static int unpackY(long packed) {
        return (int) (packed << 52 >> 52);
    }

    public static int unpackZ(long packed) {
        return (int) (packed << 26 >> 38);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Seal s && owner.equals(s.owner) && ownerName.equals(s.ownerName) && rank == s.rank
                && x == s.x && y == s.y && z == s.z && radius == s.radius && createdAt == s.createdAt
                && Arrays.equals(anchors, s.anchors);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(anchors) + java.util.Objects.hash(owner, ownerName, rank, x, y, z, radius, createdAt);
    }

    @Override
    public String toString() {
        return "Seal[" + ownerName + " rank " + rank + " at " + x + "," + y + "," + z + " r" + radius
                + " anchors " + anchors.length + "]";
    }
}
