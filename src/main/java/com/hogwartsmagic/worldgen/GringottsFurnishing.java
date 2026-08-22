package com.hogwartsmagic.worldgen;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.GoblinEntity;
import com.hogwartsmagic.entity.GoblinGuardEntity;
import com.hogwartsmagic.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Staffing a Gringotts, wherever it happens to have been built.
 *
 * <p>This used to live inside {@code VillageGringottsBank.PlacementJob}, which was fine while there
 * was exactly one way a bank got built. There are now two: the village branch, and the copy baked
 * into the Diagon Alley template. Both paste the same schematic, so both want the same tellers
 * behind the same counters, and neither should own the code.
 *
 * <p>Everything here needs only the schematic and the anchor the schematic was pasted at. Nothing
 * needs the placement job, which is what made the extraction possible at all.
 *
 * <h2>Positions are found, not hard-coded</h2>
 *
 * <p>The counters are located by scanning the schematic for the gold pressure plates set into the
 * desk tops. Which side of a desk is "behind" is decided by measuring how far open floor runs away
 * from it in each direction: the public aisle is wide and the staff strip is narrow, so the shorter
 * run wins. That is why re-authoring the building does not break the staffing.
 */
public final class GringottsFurnishing {

    private static final Logger LOGGER = LoggerFactory.getLogger("HogwartsMagic-Gringotts");

    private static final int[][] HORIZONTAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private GringottsFurnishing() {
    }

    /**
     * Everything a freshly-placed bank needs: tellers, guards, and the rotunda screen turned into a
     * real gate.
     *
     * <p>Deliberately does <em>not</em> touch {@link VillageBankTracker} or start an undercroft.
     * The tracker's positions are read back by {@code BuildUndercroftCommand}, which reconstructs
     * an anchor as {@code locator - (width/2, 0, length/2)} — arithmetic that is only true for a
     * bank the village placer positioned, and would point somewhere meaningless for one embedded in
     * a larger template.
     */
    public static void furnish(ServerLevel level, BlockPos anchor, RandomSource rng) {
        staff(level, anchor, rng);
        VillageGringottsBank.lockGate(level, anchor);
    }

    /** A teller behind each counter, and a couple of guards loose in the hall. */
    public static void staff(ServerLevel level, BlockPos anchor, RandomSource rng) {
        GringottsSchematic schem = GringottsSchematic.get();
        if (schem == null) {
            return;
        }

        List<BlockPos> tellerSpots = findTellerSpots(schem, anchor);
        if (tellerSpots.isEmpty()) {
            // No recognisable counters; fall back to any standing room.
            LOGGER.warn("No teller counters found in the Gringotts schematic; using open floor");
            tellerSpots = findStandingRoom(schem, anchor, 3);
        }

        for (BlockPos pos : tellerSpots) {
            GoblinEntity teller = ModEntities.GOBLIN.get().create(level);
            if (teller == null) {
                continue;
            }
            teller.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                    yawTowardHall(schem, anchor, pos), 0.0F);
            teller.makeTeller("Gringotts Teller");
            level.addFreshEntity(teller);
        }

        for (BlockPos pos : findStandingRoom(schem, anchor, 2)) {
            GoblinGuardEntity guard = ModEntities.GOBLIN_GUARD.get().create(level);
            if (guard == null) {
                continue;
            }
            guard.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                    rng.nextInt(4) * 90.0F, 0.0F);
            guard.makeGuard("Vault Guard");
            guard.setItemSlot(EquipmentSlot.MAINHAND,
                    new ItemStack(ExampleMod.GOBLIN_SILVER_DAGGER.get()));
            guard.setDropChance(EquipmentSlot.MAINHAND, 0.15F);
            level.addFreshEntity(guard);
        }
    }

    /** Turns a teller to face the middle of the building, i.e. the customers. */
    private static float yawTowardHall(GringottsSchematic schem, BlockPos anchor, BlockPos pos) {
        double dx = (anchor.getX() + schem.width() / 2.0D) - pos.getX();
        double dz = (anchor.getZ() + schem.length() / 2.0D) - pos.getZ();
        return (float) (Math.toDegrees(Math.atan2(-dx, dz)));
    }

    /** One standing position behind each gold-plated counter. */
    private static List<BlockPos> findTellerSpots(GringottsSchematic schem, BlockPos anchor) {
        List<BlockPos> spots = new ArrayList<>();
        int width = schem.width();
        int length = schem.length();
        for (int index = 0; index < schem.volume(); index++) {
            if (schem.isAir(index)
                    || !schem.blockAt(index).is(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE)) {
                continue;
            }
            int x = index % width;
            int z = (index / width) % length;
            int y = index / (width * length);
            int[] behind = behindCounter(schem, x, y, z);
            if (behind == null) {
                continue;
            }
            BlockPos pos = new BlockPos(anchor.getX() + behind[0],
                    anchor.getY() + behind[1], anchor.getZ() + behind[2]);
            if (farEnough(spots, pos, 4.0D)) {
                spots.add(pos);
            }
        }
        return spots;
    }

    /**
     * Picks the staff side of a counter: of the neighbouring cells a goblin could stand in, the one
     * whose open floor runs out soonest.
     */
    @Nullable
    private static int[] behindCounter(GringottsSchematic schem, int px, int py, int pz) {
        int shortestRun = Integer.MAX_VALUE;
        int[] best = null;
        for (int[] dir : HORIZONTAL) {
            int nx = px + dir[0];
            int nz = pz + dir[1];
            // The plate sits on the desk top, so the floor beside it is a block lower; allow one
            // more in case the desk is two tall.
            for (int footY = py - 1; footY >= py - 2; footY--) {
                if (!standable(schem, nx, footY, nz)) {
                    continue;
                }
                int run = openRun(schem, nx, footY, nz, dir[0], dir[1]);
                if (run < shortestRun) {
                    shortestRun = run;
                    best = new int[]{nx, footY, nz};
                }
                break;
            }
        }
        return best;
    }

    /** True if a goblin could stand at this schematic cell. */
    private static boolean standable(GringottsSchematic schem, int x, int y, int z) {
        if (x < 0 || z < 0 || x >= schem.width() || z >= schem.length()) {
            return false;
        }
        if (y < 1 || y + 1 >= schem.height()) {
            return false;
        }
        return !schem.isAir(schem.indexOf(x, y - 1, z))
                && schem.isAir(schem.indexOf(x, y, z))
                && schem.isAir(schem.indexOf(x, y + 1, z));
    }

    /** How many cells of open, head-high floor continue in one direction. */
    private static int openRun(GringottsSchematic schem, int x, int y, int z, int dx, int dz) {
        int run = 0;
        for (int step = 1; step <= 12; step++) {
            int nx = x + dx * step;
            int nz = z + dz * step;
            if (nx < 0 || nz < 0 || nx >= schem.width() || nz >= schem.length()) {
                break;
            }
            if (!schem.isAir(schem.indexOf(nx, y, nz))
                    || !schem.isAir(schem.indexOf(nx, y + 1, nz))) {
                break;
            }
            run++;
        }
        return run;
    }

    /** Finds up to {@code wanted} well-separated spots with head room inside the build. */
    private static List<BlockPos> findStandingRoom(GringottsSchematic schem, BlockPos anchor, int wanted) {
        List<BlockPos> found = new ArrayList<>();
        int width = schem.width();
        int length = schem.length();
        int maxY = Math.min(schem.height() - 2, 12);
        // Search outward from the horizontal centre so staff end up in the hall.
        int centreX = width / 2;
        int centreZ = length / 2;
        for (int ring = 0; ring < Math.max(width, length) && found.size() < wanted; ring++) {
            for (int dx = -ring; dx <= ring && found.size() < wanted; dx++) {
                for (int dz = -ring; dz <= ring && found.size() < wanted; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int x = centreX + dx;
                    int z = centreZ + dz;
                    if (x < 0 || z < 0 || x >= width || z >= length) {
                        continue;
                    }
                    for (int y = 1; y <= maxY; y++) {
                        boolean floor = !schem.isAir(schem.indexOf(x, y - 1, z));
                        boolean feet = schem.isAir(schem.indexOf(x, y, z));
                        boolean head = schem.isAir(schem.indexOf(x, y + 1, z));
                        if (floor && feet && head) {
                            BlockPos pos = new BlockPos(anchor.getX() + x,
                                    anchor.getY() + y, anchor.getZ() + z);
                            if (farEnough(found, pos, 6.0D)) {
                                found.add(pos);
                            }
                            break;
                        }
                    }
                }
            }
        }
        return found;
    }

    private static boolean farEnough(List<BlockPos> existing, BlockPos candidate, double minDistance) {
        double minSqr = minDistance * minDistance;
        for (BlockPos p : existing) {
            if (p.distSqr(candidate) < minSqr) {
                return false;
            }
        }
        return true;
    }
}
