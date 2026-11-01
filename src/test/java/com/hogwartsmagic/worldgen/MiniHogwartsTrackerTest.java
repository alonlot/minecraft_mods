package com.hogwartsmagic.worldgen;

import com.hogwartsmagic.worldgen.MiniHogwartsTracker.Castle;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The castle list: what an old save loads as, and that each castle and its per-player memory survive a save. */
class MiniHogwartsTrackerTest {

    /** A save written before castles carried an anchor: only the LongTag list of centres. */
    @Test
    void loadsOldSaveFormat() {
        BlockPos a = new BlockPos(100, 70, -250);
        BlockPos b = new BlockPos(-6000, 64, 12000);
        CompoundTag old = new CompoundTag();
        ListTag centres = new ListTag();
        centres.add(LongTag.valueOf(a.asLong()));
        centres.add(LongTag.valueOf(b.asLong()));
        old.put("Castles", centres);

        MiniHogwartsTracker tracker = MiniHogwartsTracker.load(old, null);

        assertEquals(2, tracker.castles().size());
        for (Castle c : tracker.castles()) {
            assertNull(c.anchor(), "an old save never recorded the anchor");
            assertTrue(c.titled().isEmpty() && c.enteredHall().isEmpty());
        }
        assertEquals(a, tracker.castles().get(0).center());
        assertEquals(b, tracker.castles().get(1).center());
        assertEquals(a, tracker.findClosest(new BlockPos(90, 70, -240)).center());
        assertEquals(List.of(a, b), tracker.getAll());
    }

    @Test
    void roundTripsCastles() {
        MiniHogwartsTracker tracker = new MiniHogwartsTracker();
        Castle first = tracker.add(new BlockPos(60, 90, 130), new BlockPos(10, 60, 20));
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        tracker.markTitled(p1, first);
        tracker.markEnteredHall(p1, first);
        tracker.markEnteredHall(p2, first);
        tracker.add(new BlockPos(-450, 90, 110), new BlockPos(-500, 64, 0));

        MiniHogwartsTracker back = MiniHogwartsTracker.load(tracker.save(new CompoundTag(), null), null);

        assertEquals(2, back.castles().size());
        Castle c = back.castles().get(0);
        assertEquals(new BlockPos(60, 90, 130), c.center());
        assertEquals(new BlockPos(10, 60, 20), c.anchor());
        assertEquals(Set.of(p1), c.titled());
        assertEquals(Set.of(p1, p2), c.enteredHall());
        assertEquals(new BlockPos(-500, 64, 0), back.castles().get(1).anchor());
        // The legacy centre list is still written, so an older build opening this save keeps its castles.
        ListTag legacy = tracker.save(new CompoundTag(), null).getList("Castles", net.minecraft.nbt.Tag.TAG_LONG);
        assertEquals(new BlockPos(60, 90, 130).asLong(), ((LongTag) legacy.get(0)).getAsLong());
    }

    @Test
    void addDedupesWithin128Blocks() {
        MiniHogwartsTracker tracker = new MiniHogwartsTracker();
        Castle first = tracker.add(new BlockPos(0, 80, 0), new BlockPos(-50, 60, -100));
        Castle again = tracker.add(new BlockPos(100, 80, 0), new BlockPos(50, 60, -100));
        assertSame(first, again, "a second record inside 128 blocks is the same castle");
        assertEquals(1, tracker.castles().size());
        tracker.add(new BlockPos(200, 80, 0), new BlockPos(150, 60, -100));
        assertEquals(2, tracker.castles().size());
        assertSame(first, tracker.findClosest(new BlockPos(10, 0, 10)));
        assertNull(new MiniHogwartsTracker().findClosest(BlockPos.ZERO));
    }
}
