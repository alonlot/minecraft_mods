package com.hogwartsmagic.worldgen;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.GoblinEntity;
import com.hogwartsmagic.entity.GoblinGuardEntity;
import com.hogwartsmagic.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;

/**
 * Drops a Gringotts branch office into some villages: a small marble-and-gold
 * bank staffed by a goblin teller with two guards on the door.
 *
 * <p>Placed from the server player tick rather than from a worldgen feature -
 * building during chunk generation in a far chunk is what crashes this mod (see
 * {@link MiniHogwartsManager}). The build is only ~1,500 blocks, so unlike the
 * castle it goes down in a single tick rather than being spread over several.
 *
 * <p>Each village is judged exactly once and the verdict is recorded in
 * {@link VillageBankTracker}, so a village that misses out never re-rolls.
 */
public final class VillageGringottsBank {

    private VillageGringottsBank() {
    }

    /** Roughly one village in three gets a branch. */
    private static final int BANK_CHANCE_DENOMINATOR = 3;

    /** How often a given player re-checks for an unbanked village, in ticks. */
    private static final int CHECK_INTERVAL = 60;

    // Footprint: 11 wide, 9 deep, walls 5 tall with a roof and pediment above.
    private static final int WIDTH = 11;
    private static final int DEPTH = 9;
    private static final int WALL_HEIGHT = 5;

    /** One block of clearance is required all round the footprint. */
    private static final int MARGIN = 1;

    /** Largest tolerated ground height spread across the footprint. */
    private static final int MAX_GROUND_SPREAD = 2;

    private static final int SETBLOCK_FLAGS = Block.UPDATE_CLIENTS;

    private static final BlockState MARBLE = Blocks.QUARTZ_BLOCK.defaultBlockState();
    private static final BlockState PILLAR = Blocks.QUARTZ_PILLAR.defaultBlockState();
    private static final BlockState FLOOR = Blocks.SMOOTH_QUARTZ.defaultBlockState();
    private static final BlockState CHISELED = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
    private static final BlockState GOLD = Blocks.GOLD_BLOCK.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /**
     * Called every server player tick. Cheap to reject: it only does real work
     * when the player is actually standing inside a village.
     */
    public static void maybeBuild(ServerPlayer player) {
        if (player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();

        StructureStart village = level.structureManager()
                .getStructureWithPieceAt(playerPos, StructureTags.VILLAGE);
        if (!village.isValid()) {
            return;
        }

        BoundingBox villageBox = village.getBoundingBox();
        BlockPos center = new BlockPos(villageBox.getCenter().getX(),
                villageBox.getCenter().getY(), villageBox.getCenter().getZ());

        VillageBankTracker tracker = VillageBankTracker.get(level);
        if (tracker.hasVisited(center)) {
            return;
        }

        if (!villageGetsBank(level, center)) {
            // A settled "no" - record it so this village is never re-rolled.
            tracker.markVisited(center);
            return;
        }

        Site site = findSite(level, villageBox, center);
        if (site == null) {
            // Probably just chunks that are not loaded yet. Deliberately not
            // marked visited, so walking further into the village retries rather
            // than costing the village its bank forever.
            return;
        }

        RandomSource rng = RandomSource.create(level.getSeed() ^ center.asLong());
        build(level, site, rng);
        tracker.markVisited(center);
        tracker.addBank(site.origin);
    }

    /**
     * Deterministic per village and per world: the same village in the same
     * world always gets the same answer, regardless of who walks in first.
     */
    private static boolean villageGetsBank(ServerLevel level, BlockPos center) {
        RandomSource rng = RandomSource.create(level.getSeed() ^ (center.asLong() * 0x9E3779B97F4A7C15L));
        return rng.nextInt(BANK_CHANCE_DENOMINATOR) == 0;
    }

    // ------------------------------------------------------------- site search

    /** An accepted build location: the front-left floor corner plus a facing. */
    private record Site(BlockPos origin, Direction facing) {
    }

    /**
     * Looks for a flat, unbuilt patch inside the village, working outward from
     * the centre so the bank lands among the houses rather than in a field. The
     * entrance is turned to face back toward the village centre.
     */
    @Nullable
    private static Site findSite(ServerLevel level, BoundingBox villageBox, BlockPos center) {
        int maxRadius = Math.max(villageBox.getXSpan(), villageBox.getZSpan()) / 2 + 16;
        for (int radius = 12; radius <= maxRadius; radius += 4) {
            for (int angleStep = 0; angleStep < 16; angleStep++) {
                double angle = angleStep * Math.PI / 8.0D;
                int cx = center.getX() + (int) Math.round(Math.cos(angle) * radius);
                int cz = center.getZ() + (int) Math.round(Math.sin(angle) * radius);

                // Entrance looks back at the village centre.
                Direction facing = facingToward(cx, cz, center);
                Site candidate = siteAt(level, cx, cz, facing);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** The cardinal direction pointing from (x, z) back toward the village centre. */
    private static Direction facingToward(int x, int z, BlockPos center) {
        int dx = center.getX() - x;
        int dz = center.getZ() - z;
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * Validates a candidate centred on (cx, cz): every column must be loaded,
     * standing on natural ground, free of fluid, and level to within
     * {@link #MAX_GROUND_SPREAD}.
     */
    @Nullable
    private static Site siteAt(ServerLevel level, int cx, int cz, Direction facing) {
        boolean rotated = facing.getAxis() == Direction.Axis.X;
        int spanX = (rotated ? DEPTH : WIDTH) + MARGIN * 2;
        int spanZ = (rotated ? WIDTH : DEPTH) + MARGIN * 2;
        int minX = cx - spanX / 2;
        int minZ = cz - spanZ / 2;

        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;

        for (int x = minX; x < minX + spanX; x++) {
            for (int z = minZ; z < minZ + spanZ; z++) {
                if (!level.hasChunkAt(new BlockPos(x, level.getMinBuildHeight(), z))) {
                    return null;
                }
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos ground = new BlockPos(x, surface - 1, z);
                if (!isNaturalGround(level.getBlockState(ground))) {
                    return null;
                }
                if (!level.getFluidState(ground).is(Fluids.EMPTY)) {
                    return null;
                }
                lowest = Math.min(lowest, surface);
                highest = Math.max(highest, surface);
                if (highest - lowest > MAX_GROUND_SPREAD) {
                    return null;
                }
            }
        }

        // The floor sits one block into the ground so the doorway is at grade.
        BlockPos floorCorner = frontLeftCorner(minX + MARGIN, lowest - 1, minZ + MARGIN, facing);
        return new Site(floorCorner, facing);
    }

    /**
     * Converts the min-corner of the axis-aligned footprint into the local-space
     * origin (local 0,0,0 = front-left of the floor) for the given facing.
     */
    private static BlockPos frontLeftCorner(int minX, int y, int minZ, Direction facing) {
        if (facing == Direction.NORTH) {
            return new BlockPos(minX, y, minZ);
        }
        if (facing == Direction.SOUTH) {
            return new BlockPos(minX + WIDTH - 1, y, minZ + DEPTH - 1);
        }
        if (facing == Direction.WEST) {
            return new BlockPos(minX, y, minZ + WIDTH - 1);
        }
        return new BlockPos(minX + DEPTH - 1, y, minZ);
    }

    /** Only build on untouched terrain - this also keeps us off village roads. */
    private static boolean isNaturalGround(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.STONE)
                || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.TERRACOTTA)
                || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MYCELIUM);
    }

    // ---------------------------------------------------------------- building

    /**
     * Local coordinates: {@code dx} runs left-to-right across the front,
     * {@code dz} runs from the front wall (0) into the building, {@code dy} up
     * from the floor.
     */
    private static BlockPos world(Site site, int dx, int dy, int dz) {
        BlockPos o = site.origin;
        Direction f = site.facing;
        if (f == Direction.NORTH) {
            return new BlockPos(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
        }
        if (f == Direction.SOUTH) {
            return new BlockPos(o.getX() - dx, o.getY() + dy, o.getZ() - dz);
        }
        if (f == Direction.WEST) {
            return new BlockPos(o.getX() + dz, o.getY() + dy, o.getZ() - dx);
        }
        return new BlockPos(o.getX() - dz, o.getY() + dy, o.getZ() + dx);
    }

    private static void set(ServerLevel level, Site site, int dx, int dy, int dz, BlockState state) {
        level.setBlock(world(site, dx, dy, dz), state, SETBLOCK_FLAGS);
    }

    private static void build(ServerLevel level, Site site, RandomSource rng) {
        buildShell(level, site);
        buildFacade(level, site);
        buildRoof(level, site);
        furnish(level, site, rng);
        staff(level, site);
    }

    /** Floor, foundation skirt, walls, and the hollow interior. */
    private static void buildShell(ServerLevel level, Site site) {
        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < DEPTH; dz++) {
                set(level, site, dx, 0, dz, FLOOR);

                // Carry the foundation down to solid ground on sloped sites.
                BlockPos below = world(site, dx, -1, dz);
                for (int drop = 0; drop < 6; drop++) {
                    BlockPos p = below.below(drop);
                    if (level.getBlockState(p).isSolid()) {
                        break;
                    }
                    level.setBlock(p, Blocks.STONE_BRICKS.defaultBlockState(), SETBLOCK_FLAGS);
                }

                boolean edge = dx == 0 || dx == WIDTH - 1 || dz == 0 || dz == DEPTH - 1;
                for (int dy = 1; dy <= WALL_HEIGHT; dy++) {
                    if (edge) {
                        boolean corner = (dx == 0 || dx == WIDTH - 1) && (dz == 0 || dz == DEPTH - 1);
                        set(level, site, dx, dy, dz, corner ? PILLAR : MARBLE);
                    } else {
                        set(level, site, dx, dy, dz, AIR);
                    }
                }
            }
        }
    }

    /** Colonnade, doorway and steps across the front (dz == 0) face. */
    private static void buildFacade(ServerLevel level, Site site) {
        // Open the whole front wall, then put the columns back.
        for (int dx = 1; dx < WIDTH - 1; dx++) {
            for (int dy = 1; dy < WALL_HEIGHT; dy++) {
                set(level, site, dx, dy, 0, AIR);
            }
        }
        for (int col : new int[]{1, 3, 7, 9}) {
            for (int dy = 1; dy < WALL_HEIGHT; dy++) {
                set(level, site, col, dy, 0, PILLAR);
            }
            set(level, site, col, WALL_HEIGHT - 1, 0, CHISELED);
        }
        // Gold lintel over the entrance gap.
        for (int dx = 4; dx <= 6; dx++) {
            set(level, site, dx, WALL_HEIGHT, 0, GOLD);
        }

        // A single threshold row in front of the door. The floor already sits
        // flush with the surrounding ground, so this is trim rather than stairs -
        // and it stays inside the one-block margin the site search validated.
        for (int dx = 2; dx <= WIDTH - 3; dx++) {
            set(level, site, dx, 0, -1, FLOOR);
            set(level, site, dx, 1, -1, AIR);
        }
        set(level, site, 5, 0, -1, GOLD);

        // Lanterns flanking the door.
        BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        set(level, site, 3, WALL_HEIGHT - 1, 1, lantern);
        set(level, site, 7, WALL_HEIGHT - 1, 1, lantern);
    }

    /** Flat marble roof with a gold cornice and a small pediment over the door. */
    private static void buildRoof(ServerLevel level, Site site) {
        for (int dx = -1; dx <= WIDTH; dx++) {
            for (int dz = -1; dz <= DEPTH; dz++) {
                boolean overhang = dx < 0 || dx >= WIDTH || dz < 0 || dz >= DEPTH;
                set(level, site, dx, WALL_HEIGHT + 1, dz, overhang ? GOLD : MARBLE);
            }
        }
        // Pediment: a chiseled crest stepping up over the entrance.
        for (int dx = 3; dx <= 7; dx++) {
            set(level, site, dx, WALL_HEIGHT + 2, 0, CHISELED);
        }
        for (int dx = 4; dx <= 6; dx++) {
            set(level, site, dx, WALL_HEIGHT + 3, 0, CHISELED);
        }
        set(level, site, 5, WALL_HEIGHT + 4, 0, GOLD);
    }

    /** Teller counter, vault grille, loot and lighting. */
    private static void furnish(ServerLevel level, Site site, RandomSource rng) {
        // Counter running across the hall, with a gap at each end to get behind.
        for (int dx = 2; dx <= WIDTH - 3; dx++) {
            set(level, site, dx, 1, DEPTH - 4, FLOOR);
            set(level, site, dx, 2, DEPTH - 4, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
        }
        // Brass fittings along the counter top.
        set(level, site, 3, 2, DEPTH - 4, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
        set(level, site, WIDTH - 4, 2, DEPTH - 4, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState());

        // Vault grille across the back wall, gold-framed.
        for (int dx = 3; dx <= WIDTH - 4; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                set(level, site, dx, dy, DEPTH - 1, Blocks.IRON_BARS.defaultBlockState());
            }
        }
        set(level, site, 2, 1, DEPTH - 1, GOLD);
        set(level, site, WIDTH - 3, 1, DEPTH - 1, GOLD);

        // Two strongboxes behind the counter, facing the hall.
        placeVaultChest(level, site, 3, 1, DEPTH - 2, rng);
        placeVaultChest(level, site, WIDTH - 4, 1, DEPTH - 2, rng);

        // Interior lighting so the bank does not spawn monsters at night.
        BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        set(level, site, 2, WALL_HEIGHT, 3, lantern);
        set(level, site, WIDTH - 3, WALL_HEIGHT, 3, lantern);
        set(level, site, 5, WALL_HEIGHT, DEPTH - 3, lantern);

        // A strip of gold inlay down the middle of the floor.
        for (int dz = 1; dz < DEPTH - 3; dz++) {
            set(level, site, 5, 0, dz, GOLD);
        }
    }

    private static void placeVaultChest(ServerLevel level, Site site, int dx, int dy, int dz, RandomSource rng) {
        BlockPos pos = world(site, dx, dy, dz);
        BlockState chest = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, site.facing);
        level.setBlock(pos, chest, SETBLOCK_FLAGS);

        if (!(level.getBlockEntity(pos) instanceof Container container)) {
            return;
        }
        container.setItem(0, new ItemStack(ExampleMod.GALLEON.get(), 6 + rng.nextInt(11)));
        container.setItem(2, new ItemStack(Items.GOLD_INGOT, 2 + rng.nextInt(4)));
        container.setItem(4, new ItemStack(Items.GOLD_NUGGET, 4 + rng.nextInt(9)));
        if (rng.nextInt(3) == 0) {
            container.setItem(6, new ItemStack(Items.EMERALD, 1 + rng.nextInt(3)));
        }
        if (rng.nextInt(6) == 0) {
            container.setItem(8, new ItemStack(ExampleMod.GOBLIN_SILVER_DAGGER.get()));
        }
    }

    /** One teller behind the counter, two guards on the door. */
    private static void staff(ServerLevel level, Site site) {
        GoblinEntity teller = ModEntities.GOBLIN.get().create(level);
        if (teller != null) {
            BlockPos pos = world(site, 5, 1, DEPTH - 3);
            teller.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                    site.facing.toYRot(), 0.0F);
            teller.makeTeller("Gringotts Teller");
            level.addFreshEntity(teller);
        }

        for (int dx : new int[]{2, WIDTH - 3}) {
            GoblinGuardEntity guard = ModEntities.GOBLIN_GUARD.get().create(level);
            if (guard == null) {
                continue;
            }
            BlockPos pos = world(site, dx, 1, 2);
            guard.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                    site.facing.toYRot(), 0.0F);
            guard.makeGuard("Vault Guard");
            guard.setItemSlot(EquipmentSlot.MAINHAND,
                    new ItemStack(ExampleMod.GOBLIN_SILVER_DAGGER.get()));
            guard.setDropChance(EquipmentSlot.MAINHAND, 0.15F);
            level.addFreshEntity(guard);
        }
    }
}
