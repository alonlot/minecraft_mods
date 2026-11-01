package com.hogwartsmagic.spell.ceiling;

import com.hogwartsmagic.ambience.AmbientEffect;
import com.hogwartsmagic.spell.support.ceiling.CaelumFascinoSpell;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The pure limits of Caelum Fascino: which pairs of corner blocks may become a ceiling. */
class CaelumFascinoRulesTest {
    private static final String KEY = "message.hogwartsmagic.caelum_fascino.";
    private static final BlockPos A = new BlockPos(0, 70, 0);

    private static AmbientEffect ceilingAt(BlockPos a, BlockPos b) {
        return CaelumFascinoSpell.make(UUID.randomUUID(), a, b, UUID.randomUUID(), 0L);
    }

    @Test
    void sixtyFiveWideIsTooBig() {
        // 65 blocks inclusive in x (0..64), and the same in z.
        assertEquals(KEY + "too_big",
                CaelumFascinoSpell.refusal(A, Level.OVERWORLD, new BlockPos(64, 70, 10), Level.OVERWORLD, List.of(), 0));
        assertEquals(KEY + "too_big",
                CaelumFascinoSpell.refusal(A, Level.OVERWORLD, new BlockPos(10, 70, -64), Level.OVERWORLD, List.of(), 0));
        // 64 inclusive is the largest allowed, in either direction.
        assertNull(CaelumFascinoSpell.refusal(A, Level.OVERWORLD, new BlockPos(63, 70, -63), Level.OVERWORLD, List.of(), 0));
    }

    @Test
    void tooFarApartVerticallyIsTooBig() {
        // 16 blocks of rise is the most; 17 gets the same hint as a too-wide ceiling, whichever corner is higher.
        assertNull(CaelumFascinoSpell.refusal(A, Level.OVERWORLD, new BlockPos(5, 70 + CaelumFascinoSpell.MAX_RISE, 5),
                Level.OVERWORLD, List.of(), 0));
        assertEquals(KEY + "too_big", CaelumFascinoSpell.refusal(A, Level.OVERWORLD,
                new BlockPos(5, 70 + CaelumFascinoSpell.MAX_RISE + 1, 5), Level.OVERWORLD, List.of(), 0));
        assertEquals(KEY + "too_big", CaelumFascinoSpell.refusal(A, Level.OVERWORLD,
                new BlockPos(5, 70 - CaelumFascinoSpell.MAX_RISE - 1, 5), Level.OVERWORLD, List.of(), 0));
    }

    @Test
    void ninthCeilingRefused() {
        BlockPos b = new BlockPos(5, 70, 5);
        assertNull(CaelumFascinoSpell.refusal(A, Level.OVERWORLD, b, Level.OVERWORLD, List.of(),
                CaelumFascinoSpell.MAX_PER_PLAYER - 1));
        assertEquals(KEY + "too_many", CaelumFascinoSpell.refusal(A, Level.OVERWORLD, b, Level.OVERWORLD, List.of(),
                CaelumFascinoSpell.MAX_PER_PLAYER));
    }

    @Test
    void overlapRefused() {
        AmbientEffect existing = ceilingAt(new BlockPos(10, 70, 10), new BlockPos(20, 70, 20)); // x,z 10..21
        // Shares floor area at the same plane.
        assertEquals(KEY + "overlap", CaelumFascinoSpell.refusal(new BlockPos(15, 70, 15), Level.OVERWORLD,
                new BlockPos(30, 70, 30), Level.OVERWORLD, List.of(existing), 0));
        // Only touching along an edge (x 21..30 against 10..21) is not overlap.
        assertNull(CaelumFascinoSpell.refusal(new BlockPos(21, 70, 10), Level.OVERWORLD,
                new BlockPos(30, 70, 20), Level.OVERWORLD, List.of(existing), 0));
        // A storey above (planes STOREY_GAP apart) over the same floor area is allowed; one block above is not.
        assertNull(CaelumFascinoSpell.refusal(new BlockPos(10, 70 + CaelumFascinoSpell.STOREY_GAP, 10), Level.OVERWORLD,
                new BlockPos(20, 70 + CaelumFascinoSpell.STOREY_GAP, 20), Level.OVERWORLD, List.of(existing), 0));
        assertEquals(KEY + "overlap", CaelumFascinoSpell.refusal(new BlockPos(10, 71, 10), Level.OVERWORLD,
                new BlockPos(20, 71, 20), Level.OVERWORLD, List.of(existing), 0));
        // A castle ceiling (roof clearance 17 above its plane) also keeps spell ceilings out of its roof volume.
        AmbientEffect hall = new AmbientEffect(UUID.randomUUID(), AmbientEffect.KIND_CEILING, 100, 70, 100, 20f, 17f, 10f,
                0f, 0, null, 0L);
        assertEquals(KEY + "overlap", CaelumFascinoSpell.refusal(new BlockPos(95, 80, 95), Level.OVERWORLD,
                new BlockPos(105, 80, 105), Level.OVERWORLD, List.of(hall), 0));
        // Only ceilings count: a candle box with the same footprint is no obstacle.
        AmbientEffect candles = new AmbientEffect(UUID.randomUUID(), AmbientEffect.KIND_CANDLES, 15.5, 70, 15.5, 5f, 1f, 5f,
                0f, 6, null, 0L);
        assertNull(CaelumFascinoSpell.refusal(new BlockPos(10, 70, 10), Level.OVERWORLD,
                new BlockPos(20, 70, 20), Level.OVERWORLD, List.of(candles), 0));
    }

    @Test
    void otherDimensionRefused() {
        assertEquals(KEY + "other_dimension",
                CaelumFascinoSpell.refusal(A, Level.OVERWORLD, new BlockPos(5, 70, 5), Level.NETHER, List.of(), 0));
    }

    @Test
    void validPairAccepted() {
        assertNull(CaelumFascinoSpell.refusal(A, Level.OVERWORLD, new BlockPos(9, 74, 6), Level.OVERWORLD, List.of(), 0));
        // The same block twice is a 1x1 ceiling; allowed, not refused.
        assertNull(CaelumFascinoSpell.refusal(A, Level.OVERWORLD, A, Level.OVERWORLD, List.of(), 0));
    }

    @Test
    void rectangleRunsFromTheLowerToOnePastTheHigherCorner() {
        AmbientEffect e = CaelumFascinoSpell.make(UUID.randomUUID(), new BlockPos(9, 74, -4), new BlockPos(2, 71, 3), null, 77L);
        // x 2..10 and z -4..4, the plane at the lower corner's lower face.
        assertEquals(6.0, e.x());
        assertEquals(0.0, e.z());
        assertEquals(4.0f, e.hx());
        assertEquals(4.0f, e.hz());
        assertEquals(71.0, e.y());
        assertEquals(0f, e.hy());
        assertEquals(AmbientEffect.KIND_CEILING, e.kind());
        assertEquals(77L, e.createdTick());
    }
}
