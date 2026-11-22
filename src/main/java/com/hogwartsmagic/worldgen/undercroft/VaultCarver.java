package com.hogwartsmagic.worldgen.undercroft;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The vaults themselves: chambers cut into the rock on the far side of the track from the drop.
 *
 * <p>Always on the rock side, never the void side, which is the outside of the spiral
 * ({@link CartTrack#voidSideAt}). This used to be read off the sign of the bank, which was the
 * wrong way round, and every vault was dug out over the chasm: a rock box hanging in the void with
 * its door facing a wall.
 *
 * <p>A vault is three things, not one: a <b>door</b> on the line, a short <b>throat</b> behind it,
 * and the <b>chamber</b>. The throat exists so that "the door is open" and "you have the gold" are
 * two different moments - it is where the vapour pools, and where somebody leaving in a hurry gets
 * caught.
 *
 * <p>The hoard is built as a <b>heap with a kerb</b> rather than a floor of gold blocks. Mass and a
 * silhouette are what make it read as money.
 *
 * <h2>Level, numbered, and opened through the lining</h2>
 *
 * <p>Vaults are cut level, in world axes, from the height of the deck at their mouth. Cut in the
 * track's banked frame they tilted with the bank, so a nine-deep chamber had a floor three blocks
 * higher at one end than the other. The mouth is cut through the corridor's lining too: the
 * throat began one block beyond it, which left a course of rock standing in front of every door.
 * Each door has its number on a plaque, irregular, the way the reference asks.
 *
 * <p>The deepest vault is not on the line any more. It is in the back wall of the dragon's hall
 * ({@link CavernCarver}), with the dragon between it and the track, which is where the film keeps
 * it.
 */
public final class VaultCarver {

    /** Where along the line the vaults sit, as fractions of its length. The last is the hall's. */
    private static final double[] AT = {0.30D, 0.44D, 0.57D, 0.69D, 0.80D, 0.90D, 0.97D};

    /** Bricked-up mouths that go nowhere: the bank is older than you. */
    private static final double[] SEALED = {0.375D, 0.745D};

    /** Plaque numbers, shallow to deep. Irregular on purpose: 687 and 713 are not neighbours. */
    private static final int[] NUMBERS = {687, 211, 459, 802, 118, 640, 713};

    /** Chamber size: wide and TALL. A low vault reads as a cupboard. */
    private static final int HALF_W = 4;
    private static final int HEIGHT = 8;
    private static final int DEPTH = 9;
    /** How far the throat runs from the door before the chamber begins. */
    private static final int THROAT = 4;

    /** Cosine of the largest angle off a compass axis at which a vault is squared to the grid. */
    private static final double SNAP_COS = Math.cos(Math.toRadians(20.0D));

    /** Where the door stands, out from the line: just past the corridor's lining. */
    private static final int CLEAR = 5;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState FLOOR = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState FRAME = Blocks.CHISELED_DEEPSLATE.defaultBlockState();
    private static final BlockState KERB = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    private static final BlockState LAMP = Blocks.LANTERN.defaultBlockState()
            .setValue(BlockStateProperties.HANGING, Boolean.TRUE);
    private static final BlockState BRICK = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    private static final BlockState CRACKED = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
    private static final BlockState RUBBLE = Blocks.COBBLED_DEEPSLATE_SLAB.defaultBlockState();
    private static final BlockState RAIL = Blocks.SMOOTH_STONE.defaultBlockState();

    /** Gold, and gold in shadow. Emerald is NOT mixed in: it rendered as a chessboard. */
    private static final BlockState[] HOARD = weighted(
            Blocks.GOLD_BLOCK.defaultBlockState(), 10,
            Blocks.RAW_GOLD_BLOCK.defaultBlockState(), 5);

    /** Gems sit ON the coin, in ones and twos, which is where a jewel in a hoard actually is. */
    private static final BlockState GEM = Blocks.EMERALD_BLOCK.defaultBlockState();
    private static final double GEM_CHANCE = 0.18D;

    private static final ResourceLocation LOOT =
            ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "chests/gringotts_vault");

    private final CartTrack track;
    private final UndercroftPalette palette;
    @Nullable
    private final CavernCarver cavern;

    public VaultCarver(CartTrack track, UndercroftPalette palette, @Nullable CavernCarver cavern) {
        this.track = track;
        this.palette = palette;
        this.cavern = cavern;
    }

    public int stepCount() {
        return AT.length + SEALED.length;
    }

    /** One whole vault (or one sealed mouth) per step. */
    public void carveStep(CarveSink sink, int step) {
        if (step >= AT.length) {
            sealed(sink, SEALED[step - AT.length]);
            return;
        }
        Frame f = frame(step);
        int clear = clearFor(step);


        // Throat, then chamber.
        for (int d = clear; d <= clear + THROAT + DEPTH; d++) {
            boolean inChamber = d > clear + THROAT;
            int halfW = inChamber ? HALF_W : 2;
            int height = inChamber ? HEIGHT : 5;
            for (int u = -halfW - 1; u <= halfW + 1; u++) {
                for (int v = -1; v <= height + 1; v++) {
                    boolean interior = Math.abs(u) <= halfW && v >= 0 && v <= height;
                    BlockState state;
                    if (interior) {
                        state = v == 0 ? FLOOR : AIR;
                    } else if (d <= clear + THROAT) {
                        state = FRAME;
                    } else {
                        int[] c = f.cell(d, u, v);
                        state = this.palette.rock(c[0], c[1], c[2]);
                    }
                    f.set(sink, d, u, v, state);
                }
            }
        }

        // The mouth, cut back through the corridor's lining to meet the line. After the throat,
        // so the throat's frame cannot close it again.
        for (int v = 0; v <= 6; v++) {
            int row = v;
            f.slab(sink, clear - 1.5D, clear - 0.5D, -3.5D, 3.5D, v,
                    u -> Math.abs(u) <= 2.5D && row <= 5 ? (row == 0 ? FLOOR : AIR) : FRAME);
        }

        boolean cursed = step == AT.length - 1;
        furnish(sink, f, clear, step, cursed);
        door(sink, f, clear, step);
    }

    // ------------------------------------------------------------------ geometry

    /**
     * A level frame for one vault: a point on the line at deck height, the horizontal direction
     * out into the rock, and the horizontal direction along the line.
     */
    private static final class Frame {
        final double x;
        final double z;
        final int floorY;
        final double outX;
        final double outZ;
        final double acrossX;
        final double acrossZ;

        Frame(double x, int floorY, double z, double outX, double outZ) {
            this.x = x;
            this.z = z;
            this.floorY = floorY;
            this.outX = outX;
            this.outZ = outZ;
            this.acrossX = -outZ;
            this.acrossZ = outX;
        }

        int[] cell(double d, double u, int v) {
            return new int[]{
                    (int) Math.floor(this.x + this.outX * d + this.acrossX * u),
                    this.floorY + v,
                    (int) Math.floor(this.z + this.outZ * d + this.acrossZ * u)};
        }

        void set(CarveSink sink, double d, double u, int v, BlockState state) {
            int[] c = cell(d, u, v);
            sink.set(c[0], c[1], c[2], state);
        }

        /**
         * Every cell whose CENTRE projects into [dFrom, dTo) along the axis and [uFrom, uTo]
         * across it, at height v. Stepping integer (d, u) offsets and flooring - what {@link #set}
         * does - aliases on a diagonal heading: two offsets land in one cell and a neighbouring
         * cell is never reached, which put a column of door leaf a block forward into the mouth.
         * Bucketing cells by projection gives every cell exactly one owner.
         */
        void slab(CarveSink sink, double dFrom, double dTo, double uFrom, double uTo, int v,
                  StateFor state) {
            double reach = Math.max(Math.abs(dFrom), Math.abs(dTo)) + Math.max(Math.abs(uFrom), Math.abs(uTo)) + 2.0D;
            int x0 = (int) Math.floor(this.x - reach);
            int x1 = (int) Math.ceil(this.x + reach);
            int z0 = (int) Math.floor(this.z - reach);
            int z1 = (int) Math.ceil(this.z + reach);
            for (int bx = x0; bx <= x1; bx++) {
                for (int bz = z0; bz <= z1; bz++) {
                    double rx = bx + 0.5D - this.x;
                    double rz = bz + 0.5D - this.z;
                    double d = rx * this.outX + rz * this.outZ;
                    double u = rx * this.acrossX + rz * this.acrossZ;
                    if (d >= dFrom && d < dTo && u >= uFrom && u <= uTo) {
                        BlockState st = state.at(u);
                        if (st != null) {
                            sink.set(bx, this.floorY + v, bz, st);
                        }
                    }
                }
            }
        }

        Direction facingLine() {
            return Direction.getNearest(-this.outX, 0.0D, -this.outZ);
        }

        /** Toward the middle of the mouth from its +u side wall. */
        Direction facingAcross() {
            return Direction.getNearest(-this.acrossX, 0.0D, -this.acrossZ);
        }
    }

    /** What a slab cell becomes, by how far across the mouth it is; null leaves it alone. */
    @FunctionalInterface
    private interface StateFor {
        @Nullable
        BlockState at(double u);
    }

    private double sFor(int step) {
        if (step == AT.length - 1 && this.cavern != null) {
            return this.cavern.vaultS();
        }
        double s = this.track.length() * AT[step];
        if (this.cavern != null) {
            // Keep the line's own vaults clear of the hall, whatever the line's length.
            s = Math.min(s, this.track.length() - CavernCarver.LENGTH - 12.0D - (AT.length - 2 - step) * 14.0D);
        }
        return Math.max(0.0D, s);
    }

    private int clearFor(int step) {
        if (step == AT.length - 1 && this.cavern != null) {
            // The door goes in the hall's back wall: walk out along the vault's own axis until the
            // hall ends. The hall's reach is measured square to the line and the vault runs on a
            // compass axis, so the two disagree by up to the cosine of 45 degrees.
            Frame f = frameAt(this.cavern.vaultS());
            for (int d = CLEAR; d < 48; d++) {
                int[] c = f.cell(d, 0, 1);
                if (this.cavern.floorAt(c[0], c[2]) == Integer.MIN_VALUE) {
                    return d;
                }
            }
            return Math.max(CLEAR, (int) Math.round(this.cavern.vaultClear()));
        }
        return CLEAR;
    }

    private Frame frame(int step) {
        return frameAt(sFor(step));
    }

    private Frame frameAt(double s) {
        Vec3 centre = this.track.positionAt(s);
        Vec3 right = this.track.rightAt(s);
        Vec3 out = new Vec3(right.x, 0.0D, right.z).normalize().scale(-this.track.voidSideAt(s));
        // Snapped to the nearest compass axis when it is near one anyway. Off-axis, the 5x5 door
        // rasterises jagged - a column of leaf a block proud into the mouth - and square to the
        // grid it is one flat plate, which its animation wants. Only when near: snapping a heading
        // at 45 degrees swings the whole mouth sideways into the corridor, plaque and all, and the
        // first try at this put a sign in the cart's path.
        if (Math.max(Math.abs(out.x), Math.abs(out.z)) > SNAP_COS) {
            out = Math.abs(out.x) >= Math.abs(out.z)
                    ? new Vec3(Math.signum(out.x), 0.0D, 0.0D)
                    : new Vec3(0.0D, 0.0D, Math.signum(out.z));
        }
        // The floor is the deck's height at the mouth, which on a banked line is not the height
        // at the centre: the rock side is the outside of the curve, and it is the high side.
        Vec3 up = this.track.upAt(s);
        Vec3 banked = right.scale(-this.track.voidSideAt(s));
        double deckAtMouth = centre.y + up.y * CorridorCarver.FLOOR + banked.y * (CLEAR - 1);
        return new Frame(centre.x, (int) Math.floor(deckAtMouth), centre.z, out.x, out.z);
    }

    // ------------------------------------------------------------------ dressing

    /** The hoard, a kerb to hold it, lanterns, and the chests. */
    private void furnish(CarveSink sink, Frame f, int clear, int step, boolean cursed) {
        int back = clear + THROAT + DEPTH - 1;
        int front = clear + THROAT + 2;

        for (int d = front; d <= back; d++) {
            for (int u = -HALF_W; u <= HALF_W; u++) {
                // A heap: tallest at the back and middle, thinning to nothing at the edges.
                double toBack = (double) (d - front) / Math.max(1, back - front);
                double toEdge = 1.0D - Math.abs(u) / (double) HALF_W;
                double n = UndercroftPalette.hash(d - clear, u, step, 71);
                int stack = (int) Math.round((toBack * 2.4D + toEdge * 2.2D + n * 1.4D) - 1.6D);
                if (stack <= 0) {
                    continue;
                }
                for (int v = 1; v <= Math.min(stack, 4); v++) {
                    BlockState state;
                    if (v == 1 && stack == 1) {
                        state = KERB;
                    } else if (cursed && v >= stack - 1) {
                        // Cursed only on the surface of the heap, which is what a foot comes down on.
                        state = ExampleMod.GEMINO_PILE.get().defaultBlockState();
                    } else if (v == stack && UndercroftPalette.hash(d - clear, v, u, 79) < GEM_CHANCE) {
                        state = GEM;
                    } else {
                        state = HOARD[(int) (UndercroftPalette.hash(d - clear, v, u, 73) * HOARD.length)
                                % HOARD.length];
                    }
                    f.set(sink, d, u, v, state);
                }
            }
        }

        // A kerb across the front of the heap, so it reads as contained rather than spilled.
        for (int u = -HALF_W; u <= HALF_W; u++) {
            f.set(sink, clear + THROAT + 1, u, 1, KERB);
        }

        // Lanterns hung from the chamber roof, which is really there above them.
        for (int i = -1; i <= 1; i += 2) {
            f.set(sink, clear + THROAT + 3, i * (HALF_W - 1), HEIGHT, LAMP);
        }
        if (cursed) {
            // The deep vault is the payoff: gold light at the back of the heap as well.
            f.set(sink, back, 0, HEIGHT, LAMP);
        }

        for (int i = -1; i <= 1; i += 2) {
            int[] c = f.cell(clear + THROAT + 1, i * (HALF_W - 1), 1);
            sink.set(c[0], c[1], c[2], Blocks.CHEST.defaultBlockState()
                    .setValue(ChestBlock.FACING, f.facingLine()));
            sink.setLoot(c[0], c[1], c[2], LOOT);
        }
    }

    /** The door itself, its lamps, and its number. */
    private void door(CarveSink sink, Frame f, int clear, int step) {
        BlockState leaf = ExampleMod.GRINGOTTS_VAULT_DOOR.get().defaultBlockState();
        for (int v = 0; v <= 4; v++) {
            f.slab(sink, clear - 0.5D, clear + 0.5D, -2.5D, 2.5D, v, u -> leaf);
        }
        // Warm and small, hung under the lintel in the corners of the mouth. These used to be soul
        // lanterns hung in mid-air beside the door; the reference keeps cold light for the
        // Downfall alone, so that it reads as wrong.
        for (int i = -1; i <= 1; i += 2) {
            f.set(sink, clear - 1, i * 2, 5, LAMP);
        }
        // The number, on a plaque on the side wall of the mouth at head height, facing across it.
        // Inside the recess rather than on the corridor wall, so nothing of it is in the cart's way.
        int[] plaque = f.cell(clear - 1, 2, 3);
        sink.set(plaque[0], plaque[1], plaque[2], Blocks.DARK_OAK_WALL_SIGN.defaultBlockState()
                .setValue(WallSignBlock.FACING, f.facingAcross()));
        sink.setSign(plaque[0], plaque[1], plaque[2],
                new String[]{"", "VAULT", String.valueOf(NUMBERS[step % NUMBERS.length]), ""});
    }

    /**
     * A vault mouth that was bricked up long ago: a frame, a short throat, rubble at the foot, the
     * old rails running in under the bricks. A spur that goes nowhere says the bank is older than
     * the player, which no amount of gold says.
     */
    private void sealed(CarveSink sink, double fraction) {
        double s = this.track.length() * fraction;
        if (this.cavern != null && this.cavern.covers(s)) {
            return;
        }
        Frame f = frameAt(s);
        for (int d = CLEAR - 1; d <= CLEAR + 2; d++) {
            for (int u = -3; u <= 3; u++) {
                for (int v = -1; v <= 6; v++) {
                    boolean interior = Math.abs(u) <= 2 && v >= 0 && v <= 5;
                    BlockState state;
                    if (!interior) {
                        state = FRAME;
                    } else if (v == 0) {
                        state = Math.abs(u) == 1 ? RAIL : FLOOR;
                    } else if (d >= CLEAR + 1) {
                        int[] c = f.cell(d, u, v);
                        state = UndercroftPalette.hash(c[0], c[1], c[2], 77) < 0.35D ? CRACKED : BRICK;
                    } else {
                        state = AIR;
                    }
                    f.set(sink, d, u, v, state);
                }
            }
        }
        // A few fallen bricks at the foot of the wall.
        for (int u = -2; u <= 2; u++) {
            int[] c = f.cell(CLEAR, u, 1);
            if (UndercroftPalette.hash(c[0], c[1], c[2], 78) < 0.45D) {
                sink.set(c[0], c[1], c[2], RUBBLE);
            }
        }
    }

    /** Expands {block, weight, block, weight, ...} into a flat pick table. */
    private static BlockState[] weighted(Object... pairs) {
        int total = 0;
        for (int i = 1; i < pairs.length; i += 2) {
            total += (Integer) pairs[i];
        }
        BlockState[] table = new BlockState[total];
        int at = 0;
        for (int i = 0; i < pairs.length; i += 2) {
            BlockState state = (BlockState) pairs[i];
            for (int n = 0; n < (Integer) pairs[i + 1]; n++) {
                table[at++] = state;
            }
        }
        return table;
    }

    // ------------------------------------------------------------------ for everyone else

    /** Middle of the deepest chamber. The dragon is in the hall now, not in here. */
    public Vec3 deepestChamberCentre() {
        return chamberCentre(AT.length - 1);
    }

    /** Middle of one chamber, standing on its floor. Derived from the same frame the carve used. */
    public Vec3 chamberCentre(int index) {
        Frame f = frame(index);
        int clear = clearFor(index);
        double d = clear + THROAT + DEPTH * 0.5D;
        return new Vec3(f.x + f.outX * d, f.floorY + 1.0D, f.z + f.outZ * d);
    }

    public int vaultCount() {
        return AT.length;
    }

    /** Where each vault's door sits, for anything that needs to find them afterwards. */
    public Vec3 doorPosition(int index) {
        Frame f = frame(index);
        int clear = clearFor(index);
        return new Vec3(f.x + f.outX * clear, f.floorY + 1.0D, f.z + f.outZ * clear);
    }

    /** The horizontal direction out of the line into a vault, for audits. */
    public Vec3 outward(int index) {
        Frame f = frame(index);
        return new Vec3(f.outX, 0.0D, f.outZ);
    }

    public int number(int index) {
        return NUMBERS[index % NUMBERS.length];
    }
}
