package com.hogwartsmagic.ambience;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ambient effect record: it must survive the wire and the save file unchanged, and ceilings must know overlap. */
class AmbientEffectTest {
    private static AmbientEffect ceiling(double x, double z, float hx, float hz) {
        return new AmbientEffect(UUID.randomUUID(), AmbientEffect.KIND_CEILING, x, 70.0, z, hx, 12f, hz, 0f, 0,
                UUID.randomUUID(), 1234L);
    }

    @Test
    void roundTripsBuffer() {
        AmbientEffect owned = new AmbientEffect(UUID.randomUUID(), AmbientEffect.KIND_CANDLES, 10.5, 64.25, -300.75,
                3.5f, 1.5f, 2.5f, 90f, 12, UUID.randomUUID(), 99_999_999_999L);
        AmbientEffect unowned = new AmbientEffect(UUID.randomUUID(), AmbientEffect.KIND_DOOR, 1, 2, 3,
                0.5f, 4f, 0.25f, -45f, 0, null, 0L);
        for (AmbientEffect e : new AmbientEffect[]{owned, unowned}) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            e.encode(buf);
            AmbientEffect back = AmbientEffect.decode(buf);
            assertEquals(e, back);
            assertEquals(0, buf.readableBytes(), "decode must consume exactly what encode wrote");
        }
    }

    @Test
    void roundTripsNbt() {
        AmbientEffect owned = ceiling(-5.5, 8.25, 7f, 9f);
        AmbientEffect unowned = new AmbientEffect(UUID.randomUUID(), AmbientEffect.KIND_RAYS, 0, 80, 0,
                1f, 6f, 1f, 180f, 3, null, 7L);
        for (AmbientEffect e : new AmbientEffect[]{owned, unowned}) {
            CompoundTag tag = e.save();
            assertEquals(e, AmbientEffect.load(tag));
        }
    }

    @Test
    void loadRejectsDamagedTags() {
        assertNull(AmbientEffect.load(new CompoundTag()), "an empty tag has no id");
        CompoundTag tag = ceiling(0, 0, 1f, 1f).save();
        tag.putInt("Kind", 99);
        assertNull(AmbientEffect.load(tag), "an unknown kind is dropped, not guessed");
        CompoundTag nan = ceiling(0, 0, 1f, 1f).save();
        nan.putDouble("X", Double.NaN);
        assertNull(AmbientEffect.load(nan), "a non finite position is dropped");
        assertNotNull(AmbientEffect.load(ceiling(0, 0, 1f, 1f).save()));
    }

    /** A registry holding the effects, built through the save path (add() needs a level to broadcast to). */
    private static AmbienceRegistry registryOf(AmbientEffect... effects) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (AmbientEffect e : effects) {
            list.add(e.save());
        }
        tag.put("Effects", list);
        return AmbienceRegistry.load(tag, null);
    }

    @Test
    void ceilingAtFindsRoofBlocksWithinBand() {
        // plane y = 70, rectangle 100..124 x -48..-32, roof clearance 17 above the plane
        AmbientEffect hall = new AmbientEffect(UUID.randomUUID(), AmbientEffect.KIND_CEILING, 112, 70, -40,
                12f, 17f, 8f, 0f, 0, null, 0L);
        AmbientEffect spell = new AmbientEffect(UUID.randomUUID(), AmbientEffect.KIND_CEILING, 0, 50, 0,
                4f, 0f, 4f, 0f, 0, UUID.randomUUID(), 0L);
        AmbienceRegistry reg = registryOf(hall, spell);
        assertEquals(hall, reg.ceilingAt(new Vec3(112, 71, -40), 2), "just above the plane (a roof block)");
        assertEquals(hall, reg.ceilingAt(new Vec3(112, 70, -40), 2), "on the plane");
        assertEquals(hall, reg.ceilingAt(new Vec3(112, 68.5, -40), 2), "slightly under the plane, inside the band");
        assertEquals(hall, reg.ceilingAt(new Vec3(112, 87, -40), 0), "at the roof underside, y + hy");
        assertEquals(hall, reg.ceilingAt(new Vec3(112, 88.5, -40), 2), "just above the roof, inside the band");
        assertNull(reg.ceilingAt(new Vec3(112, 60, -40), 2), "far below the plane is the room, not the ceiling");
        assertNull(reg.ceilingAt(new Vec3(112, 95, -40), 2), "far above the roof");
        assertNull(reg.ceilingAt(new Vec3(130, 71, -40), 2), "outside the rectangle in x");
        assertNull(reg.ceilingAt(new Vec3(112, 71, -60), 2), "outside the rectangle in z");
        assertEquals(spell, reg.ceilingAt(new Vec3(1, 51.5, 1), 2), "a spell ceiling (hy = 0) finds a roof block at its plane");
        assertNull(reg.ceilingAt(new Vec3(1, 53, 1), 2), "and nothing 3 blocks above a plane with no clearance");
    }

    @Test
    void overlapsXZ() {
        AmbientEffect a = ceiling(0, 0, 5f, 5f);
        assertTrue(a.overlapsXZ(ceiling(4, 4, 2f, 2f)), "corner overlap");
        assertTrue(a.overlapsXZ(ceiling(0, 0, 1f, 1f)), "one inside the other");
        assertTrue(ceiling(0, 0, 1f, 1f).overlapsXZ(a), "symmetric");
        assertFalse(a.overlapsXZ(ceiling(20, 0, 5f, 5f)), "far apart in x");
        assertFalse(a.overlapsXZ(ceiling(0, -20, 5f, 5f)), "far apart in z");
        assertFalse(a.overlapsXZ(ceiling(10, 0, 5f, 5f)), "rectangles that only touch do not overlap");
        assertFalse(a.overlapsXZ(ceiling(8, 8, 2f, 2f)), "diagonal gap");
        AmbientEffect candles = new AmbientEffect(UUID.randomUUID(), AmbientEffect.KIND_CANDLES, 0, 70, 0,
                5f, 5f, 5f, 0f, 4, null, 0L);
        assertFalse(a.overlapsXZ(candles), "only ceilings overlap");
        assertFalse(candles.overlapsXZ(a), "only ceilings overlap, either way round");
    }
}
