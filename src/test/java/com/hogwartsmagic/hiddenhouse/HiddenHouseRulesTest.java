package com.hogwartsmagic.hiddenhouse;

import com.hogwartsmagic.hiddenhouse.HiddenHouseRules.Cell;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.hogwartsmagic.hiddenhouse.HiddenHouseRules.isGlamourTarget;
import static com.hogwartsmagic.hiddenhouse.HiddenHouseRules.revealDelay;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiddenHouseRulesTest {
    @Test
    void nothingWithinTenBlocksRevealsNothing() {
        assertTrue(HiddenHouseRules.house(Set.of(new Cell(11, 0, 0)), 0.5, 0.5, 0.5).isEmpty());
    }

    @Test
    void floodReachesPastTheRadiusAlongConnectedBlocks() {
        Set<Cell> chain = new HashSet<>();
        for (int x = 5; x <= 25; x += 2) {
            chain.add(new Cell(x, 0, 0));
        }
        assertEquals(chain.size(), HiddenHouseRules.house(chain, 0.5, 0.5, 0.5).size());
    }

    @Test
    void aGapWiderThanLinkSplitsHouses() {
        Set<Cell> s = Set.of(new Cell(5, 0, 0), new Cell(11, 0, 0)); // 11 is out of reach, gap 6 > LINK
        assertEquals(List.of(new Cell(5, 0, 0)), HiddenHouseRules.house(s, 0.5, 0.5, 0.5));
    }

    @Test
    void floodStopsAtSpanAndCap() {
        Set<Cell> line = new HashSet<>();
        for (int x = 0; x <= 40; x++) {
            line.add(new Cell(x, 0, 0));
        }
        assertTrue(HiddenHouseRules.house(line, 0.5, 0.5, 0.5).stream()
                .allMatch(c -> c.x() <= HiddenHouseRules.MAX_HOUSE_SPAN));
        Set<Cell> slab = new HashSet<>();
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                for (int y = 0; y < 6; y++) {
                    slab.add(new Cell(x, y, z));
                }
            }
        }
        assertEquals(HiddenHouseRules.MAX_HOUSE_BLOCKS, HiddenHouseRules.house(slab, 0.5, 0.5, 0.5).size());
    }

    @Test
    void revealDelayGrowsWithDistanceAndIsCapped() {
        assertEquals(0, revealDelay(0));
        assertEquals(10, revealDelay(8.0));
        assertEquals(11, revealDelay(8.1));
        assertEquals(HiddenHouseRules.MAX_DELAY, revealDelay(500));
    }

    @Test
    void glamourTargets() {
        for (String ok : List.of("minecraft:oak_planks", "minecraft:bookshelf", "minecraft:red_bed", "minecraft:chest",
                "minecraft:lantern", "minecraft:spruce_stairs", "minecraft:spruce_fence_gate", "minecraft:wall_torch",
                "minecraft:glass", "hogwartsmagic:wizard_table", "hogwartsmagic:wizard_cauldron")) {
            assertTrue(isGlamourTarget(ok), ok);
        }
        for (String no : List.of("minecraft:air", "minecraft:cave_air", "minecraft:water", "minecraft:oak_fence",
                "minecraft:glass_pane", "minecraft:cobblestone_wall", "minecraft:iron_bars", "minecraft:redstone_wire",
                "minecraft:spawner", "hogwartsmagic:glamoured_block", "hogwartsmagic:phantom_ruin",
                "hogwartsmagic:jinx_ward", "hogwartsmagic:cursed_glyph", "stone")) {
            assertFalse(isGlamourTarget(no), no);
        }
    }
}
