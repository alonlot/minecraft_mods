package com.hogwartsmagic.ambience;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One piece of world ambience the client draws on top of the blocks: an enchanted ceiling, a box of floating candles,
 * a window's light rays or a door leaf. The server keeps them per level ({@link AmbienceRegistry}) and tells clients.
 *
 * <p>The fields mean slightly different things per kind, but the box is always centre ({@code x, y, z}) plus half
 * extents ({@code hx, hy, hz}):
 * <ul>
 *   <li>ceiling: {@code x, z} the centre, {@code hx, hz} the half extents, {@code y} the plane; {@code hy} is how far
 *       below the plane the room reaches (0 or less: no limit), see {@link AmbienceRegistry#ceilingAt}</li>
 *   <li>candles: the box the {@code count} candles float in</li>
 *   <li>rays: the window, with {@code yaw} the direction the light falls in</li>
 *   <li>door: the hinge point, with {@code yaw} the closed facing</li>
 * </ul>
 *
 * @param owner       who made it (a spell ceiling's caster), or null for the castle's own effects
 * @param createdTick the level's game time when it was registered
 */
public record AmbientEffect(UUID id, int kind, double x, double y, double z, float hx, float hy, float hz, float yaw,
                            int count, @Nullable UUID owner, long createdTick) {
    public static final int KIND_CEILING = 0;
    public static final int KIND_CANDLES = 1;
    public static final int KIND_RAYS = 2;
    public static final int KIND_DOOR = 3;
    /** How many kinds there are; a kind is valid from 0 up to this (exclusive). */
    public static final int KIND_COUNT = 4;

    /** Writes this effect; the mirror of {@link #decode}. */
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeVarInt(kind);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(hx);
        buf.writeFloat(hy);
        buf.writeFloat(hz);
        buf.writeFloat(yaw);
        buf.writeVarInt(count);
        buf.writeBoolean(owner != null);
        if (owner != null) {
            buf.writeUUID(owner);
        }
        buf.writeLong(createdTick);
    }

    /**
     * Reads one effect. The kind is clamped into range and a non finite number becomes 0, so a damaged packet cannot
     * hand a renderer a NaN box.
     */
    public static AmbientEffect decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        int kind = Math.max(0, Math.min(KIND_COUNT - 1, buf.readVarInt()));
        double x = finite(buf.readDouble());
        double y = finite(buf.readDouble());
        double z = finite(buf.readDouble());
        float hx = finite(buf.readFloat());
        float hy = finite(buf.readFloat());
        float hz = finite(buf.readFloat());
        float yaw = finite(buf.readFloat());
        int count = Math.max(0, buf.readVarInt());
        UUID owner = buf.readBoolean() ? buf.readUUID() : null;
        long created = buf.readLong();
        return new AmbientEffect(id, kind, x, y, z, hx, hy, hz, yaw, count, owner, created);
    }

    /** The save file form; the mirror of {@link #load}. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putInt("Kind", kind);
        tag.putDouble("X", x);
        tag.putDouble("Y", y);
        tag.putDouble("Z", z);
        tag.putFloat("Hx", hx);
        tag.putFloat("Hy", hy);
        tag.putFloat("Hz", hz);
        tag.putFloat("Yaw", yaw);
        tag.putInt("Count", count);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putLong("Created", createdTick);
        return tag;
    }

    /** Reads a saved effect, or null when the tag is damaged (no id, an unknown kind, a number that is not finite). */
    public static @Nullable AmbientEffect load(CompoundTag tag) {
        if (!tag.hasUUID("Id")) {
            return null;
        }
        int kind = tag.getInt("Kind");
        if (kind < 0 || kind >= KIND_COUNT) {
            return null;
        }
        double x = tag.getDouble("X");
        double y = tag.getDouble("Y");
        double z = tag.getDouble("Z");
        float hx = tag.getFloat("Hx");
        float hy = tag.getFloat("Hy");
        float hz = tag.getFloat("Hz");
        float yaw = tag.getFloat("Yaw");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(hx) || !Float.isFinite(hy) || !Float.isFinite(hz) || !Float.isFinite(yaw)) {
            return null;
        }
        UUID owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        return new AmbientEffect(tag.getUUID("Id"), kind, x, y, z, hx, hy, hz, yaw,
                Math.max(0, tag.getInt("Count")), owner, tag.getLong("Created"));
    }

    /**
     * Whether this ceiling's rectangle and the other's share any floor area. Two rectangles that only touch along an
     * edge do not overlap. Only ceilings overlap: any other kind, on either side, is false.
     */
    public boolean overlapsXZ(AmbientEffect other) {
        if (kind != KIND_CEILING || other.kind != KIND_CEILING) {
            return false;
        }
        return Math.abs(x - other.x) < hx + other.hx && Math.abs(z - other.z) < hz + other.hz;
    }

    private static double finite(double v) {
        return Double.isFinite(v) ? v : 0.0;
    }

    private static float finite(float v) {
        return Float.isFinite(v) ? v : 0f;
    }
}
