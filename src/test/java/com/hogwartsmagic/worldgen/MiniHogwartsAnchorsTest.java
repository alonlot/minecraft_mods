package com.hogwartsmagic.worldgen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The anchors file the castle generator writes: every key's shape, and the file that ships. */
class MiniHogwartsAnchorsTest {

    private static final String ONE_OF_EACH = """
            {
              "chests": [{"at": [78, 25, 105], "facing": "west", "theme": "GREAT_HALL"}],
              "student_spots": [[36, 12, 190]],
              "elf_spots": [[10, 3, 12]],
              "statues": [{"at": [58, 25, 153], "yaw": -149}],
              "pensieve": [38, 13, 191],
              "fat_lady": {"wall": [60, 47, 155], "facing": "north"},
              "paintings": [{"at": [53, 33, 148], "facing": "east"}],
              "banners": [{"at": [60, 40, 90], "facing": "south", "house": "gryffindor"}],
              "hall_ceiling": {"min": [45.0, 36.0], "max": [78.0, 109.0], "y": 45, "clearance": 17},
              "candles": [{"at": [61.5, 33.635, 72.0], "spread": [15.5, 2.135, 33.0], "count": 445}],
              "rays": [{"at": [83.0, 29, 40.5], "spread": [1.5, 12, 14], "yaw": 90}],
              "doors": [{"hinge": [65.5, 25.0, 110.0], "mirrored": true, "yaw_closed": 0}],
              "hall_box": {"min": [43, 25, 27], "max": [79, 44, 109]},
              "main_entrance": [38, 26, 184],
              "title_point": [61, 60, 20],
              "stool": [61.5, 26.5, 45.5]
            }
            """;

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void parsesEveryKey() {
        MiniHogwartsAnchors a = MiniHogwartsAnchors.parse(json(ONE_OF_EACH));

        assertEquals(1, a.chests().size());
        assertEquals(new BlockPos(78, 25, 105), a.chests().get(0).at());
        assertEquals(Direction.WEST, a.chests().get(0).facing());
        assertEquals("GREAT_HALL", a.chests().get(0).theme());
        assertEquals(java.util.List.of(new BlockPos(36, 12, 190)), a.studentSpots());
        assertEquals(java.util.List.of(new BlockPos(10, 3, 12)), a.elfSpots());
        assertEquals(new BlockPos(58, 25, 153), a.statues().get(0).at());
        assertEquals(-149.0F, a.statues().get(0).yaw());
        assertEquals(new BlockPos(38, 13, 191), a.pensieve());
        assertEquals(new BlockPos(60, 47, 155), a.fatLady().at());
        assertEquals(Direction.NORTH, a.fatLady().facing());
        assertEquals(new BlockPos(53, 33, 148), a.paintings().get(0).at());
        assertEquals(Direction.EAST, a.paintings().get(0).facing());
        assertEquals(new BlockPos(60, 40, 90), a.banners().get(0).at());
        assertEquals(Direction.SOUTH, a.banners().get(0).facing());
        assertEquals("gryffindor", a.banners().get(0).house());
        MiniHogwartsAnchors.Ceiling c = a.hallCeiling();
        assertEquals(45.0, c.minX());
        assertEquals(36.0, c.minZ());
        assertEquals(78.0, c.maxX());
        assertEquals(109.0, c.maxZ());
        assertEquals(45.0, c.y());
        assertEquals(new Vec3(61.5, 33.635, 72.0), a.candles().get(0).at());
        assertEquals(new Vec3(15.5, 2.135, 33.0), a.candles().get(0).spread());
        assertEquals(445, a.candles().get(0).count());
        assertEquals(new Vec3(83.0, 29, 40.5), a.rays().get(0).at());
        assertEquals(new Vec3(1.5, 12, 14), a.rays().get(0).spread());
        assertEquals(90.0F, a.rays().get(0).yaw());
        assertEquals(new Vec3(65.5, 25.0, 110.0), a.doors().get(0).hinge());
        assertTrue(a.doors().get(0).mirrored());
        assertEquals(0.0F, a.doors().get(0).yawClosed());
        // hall_box is inclusive cells, so the box reaches one past the max cell.
        assertEquals(new AABB(43, 25, 27, 80, 45, 110), a.hallBox());
        assertEquals(new BlockPos(38, 26, 184), a.mainEntrance());
        assertEquals(new BlockPos(61, 60, 20), a.titlePoint());
        assertEquals(new BlockPos(61, 26, 45), a.stool());
    }

    @Test
    void optionalKeysMayBeAbsent() {
        MiniHogwartsAnchors a = MiniHogwartsAnchors.parse(json("""
                {"hall_ceiling": {"min": [0, 0], "max": [1, 1], "y": 5},
                 "hall_box": {"min": [0, 0, 0], "max": [1, 1, 1]},
                 "main_entrance": [0, 1, 0]}
                """));

        assertTrue(a.chests().isEmpty() && a.studentSpots().isEmpty() && a.elfSpots().isEmpty()
                && a.statues().isEmpty() && a.paintings().isEmpty() && a.banners().isEmpty()
                && a.candles().isEmpty() && a.rays().isEmpty() && a.doors().isEmpty());
        assertNull(a.pensieve());
        assertNull(a.fatLady());
        assertNull(a.titlePoint());
        assertNull(a.stool());
    }

    @Test
    void malformedKeysAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> MiniHogwartsAnchors.parse(json("{}")),
                "the hall ceiling, hall box and main entrance are required");
        String bad = ONE_OF_EACH.replace("\"facing\": \"west\"", "\"facing\": \"up\"");
        assertThrows(IllegalArgumentException.class, () -> MiniHogwartsAnchors.parse(json(bad)),
                "a chest cannot face up");
    }

    private static MiniHogwartsAnchors shipped() throws Exception {
        try (Reader in = new InputStreamReader(MiniHogwartsAnchorsTest.class.getResourceAsStream(
                "/data/hogwartsmagic/schematic/mini_hogwarts.anchors.json"), StandardCharsets.UTF_8)) {
            return MiniHogwartsAnchors.parse(JsonParser.parseReader(in).getAsJsonObject());
        }
    }

    /**
     * Every key the generator writes parses, with the counts of the castle as regenerated after the rooms, the
     * corridors pass and the runtime came together (Task 9b): 5 chests, 33 student spots, 6 house-elf spots,
     * 22 statues, 7 candle boxes, 18 paintings, 4 banners, 17 rays, the two hall doors closed at yaw 180 (R37).
     */
    @Test
    void shippedFileParses() throws Exception {
        MiniHogwartsAnchors a = shipped();

        assertTrue(a.chests().size() >= 5, "chests " + a.chests().size());
        assertTrue(a.studentSpots().size() >= 33, "student spots " + a.studentSpots().size());
        assertTrue(a.elfSpots().size() >= 6, "house-elf spots " + a.elfSpots().size());
        assertTrue(a.statues().size() >= 22, "statues " + a.statues().size());
        assertTrue(a.candles().size() >= 7, "candles " + a.candles().size());
        assertTrue(a.paintings().size() >= 18, "paintings " + a.paintings().size());
        assertTrue(a.banners().size() >= 4, "banners " + a.banners().size());
        assertTrue(a.rays().size() >= 17, "rays " + a.rays().size());
        assertEquals(2, a.doors().size(), "the hall's two doors");
        for (MiniHogwartsAnchors.Door door : a.doors()) {
            assertEquals(180.0F, door.yawClosed(), "a hall door's closed yaw (R37)");
        }
        assertNotNull(a.pensieve());
        assertNotNull(a.fatLady());
        assertNotNull(a.stool());
        assertNotNull(a.titlePoint());
        assertTrue(a.hallBox().getXsize() > 10, "hall box");
        for (MiniHogwartsAnchors.Chest chest : a.chests()) {
            assertTrue(!chest.theme().isBlank(), "a chest names its theme");
        }
    }

    /** The thresholds the finished castle must reach (build_castle's check 61 holds the generator to them too). */
    @Test
    void shippedFileFinalCounts() throws Exception {
        MiniHogwartsAnchors a = shipped();

        assertTrue(a.chests().size() >= 5, "chests " + a.chests().size());
        assertTrue(a.studentSpots().size() >= 32, "student spots " + a.studentSpots().size());
        assertTrue(a.statues().size() >= 18, "statues " + a.statues().size());
        assertTrue(a.candles().size() >= 4, "candles " + a.candles().size());
    }
}
