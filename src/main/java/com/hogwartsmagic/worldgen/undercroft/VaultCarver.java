package com.hogwartsmagic.worldgen.undercroft;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * The vaults themselves: chambers cut into the rock on the far side of the track from the drop.
 *
 * <p>Always on the rock side, never the void side. Which side that is falls out of the bank the
 * track is already carrying - the chasm is on the inside of the turn, so the vault goes outside it,
 * and no vault ever opens onto thin air.
 *
 * <p>A vault is three things, not one: a <b>door</b> on the line, a short <b>throat</b> behind it,
 * and the <b>chamber</b>. The throat exists so that "the door is open" and "you have the gold" are
 * two different moments - it is where the vapour pools, and where somebody leaving in a hurry gets
 * caught.
 *
 * <p>The hoard is built as a <b>heap with a kerb</b> rather than a floor of gold blocks. The mod
 * already has a vault whose treasure is flush gold set into a wall behind bars, and rendered it
 * reads as two bright patches of wallpaper. Mass and a silhouette are what make it read as money.
 */
public final class VaultCarver {

    /** Where along the line the vaults sit, as fractions of its length. */
    private static final double[] AT = {0.30D, 0.44D, 0.57D, 0.69D, 0.80D, 0.90D, 0.97D};

    /** Chamber size: wide and TALL. A low vault reads as a cupboard. */
    private static final int HALF_W = 4;
    private static final int HEIGHT = 8;
    private static final int DEPTH = 9;
    /** How far the throat runs from the track before the chamber begins. */
    private static final int THROAT = 4;

    /**
     * How far out the vault starts. The corridor bore is 3 half-widths plus a course of lining, so
     * anything nearer than this is cut INSIDE the tunnel - which is how the first attempt put seven
     * vault doors and a good deal of chiselled deepslate directly in the cart's path.
     */
    private static final int CLEAR = 5;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState FLOOR = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState FRAME = Blocks.CHISELED_DEEPSLATE.defaultBlockState();
    private static final BlockState KERB = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    private static final BlockState LAMP = Blocks.LANTERN.defaultBlockState()
            .setValue(BlockStateProperties.HANGING, Boolean.TRUE);

    /**
     * The body of a hoard: gold, and gold in shadow.
     *
     * <p>Emerald is <b>not</b> in here, and that is the fix rather than an omission. The first
     * version mixed gold and emerald blocks one in four through the whole heap, and rendered it
     * came out as a green-and-yellow chessboard - maximum hue contrast at one-block frequency,
     * which is the exact failure the render skill puts on its list. Raw gold is the shadow tone
     * because it is the same hue two steps darker, so the heap gets modelling instead of noise.
     */
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

    public VaultCarver(CartTrack track, UndercroftPalette palette) {
        this.track = track;
        this.palette = palette;
    }

    public int stepCount() {
        return AT.length;
    }

    /** One whole vault per step. */
    public void carveStep(CarveSink sink, int step) {
        double s = this.track.length() * AT[step];
        Vec3 centre = this.track.positionAt(s);
        Vec3 up = this.track.upAt(s);
        Vec3 right = this.track.rightAt(s);

        // Away from the drop. The bank already knows which way the void is.
        double side = this.track.bankAt(s) < 0.0F ? 1.0D : -1.0D;
        Vec3 out = right.scale(side);

        // Throat, then chamber, both measured out along the same heading.
        for (int d = CLEAR; d <= CLEAR + THROAT + DEPTH; d++) {
            boolean inChamber = d > CLEAR + THROAT;
            // Five by five rather than three by four. The door is the headline of the feature and
            // the animation needs room: a ripple crossing three plates is a flicker, and a leaf
            // that folds back in two halves needs a middle to split down.
            int halfW = inChamber ? HALF_W : 2;
            int height = inChamber ? HEIGHT : 5;

            for (int u = -halfW - 1; u <= halfW + 1; u++) {
                for (int v = -1; v <= height + 1; v++) {
                    Vec3 p = centre
                            .add(out.scale(d))
                            .add(cross(out, up).scale(u))
                            .add(up.scale(v - 2.0D));
                    int bx = (int) Math.floor(p.x);
                    int by = (int) Math.floor(p.y);
                    int bz = (int) Math.floor(p.z);

                    boolean interior = Math.abs(u) <= halfW && v >= 0 && v <= height;
                    if (interior) {
                        sink.set(bx, by, bz, v == 0 ? FLOOR : AIR);
                    } else {
                        sink.set(bx, by, bz, d <= CLEAR + THROAT ? FRAME
                                : this.palette.rock(bx, by, bz));
                    }
                }
            }
        }

        // The deepest vault is the cursed one, which is where the films put it too.
        boolean cursed = step == AT.length - 1;
        furnish(sink, centre, out, up, step, cursed);
        if (cursed) {
            chains(sink, centre, out, up);
        }
        door(sink, centre, out, up);
    }

    /** The hoard, a kerb to hold it, lanterns, and the chests. */
    private void furnish(CarveSink sink, Vec3 centre, Vec3 out, Vec3 up, int step,
                         boolean cursed) {
        Vec3 across = cross(out, up);
        int back = CLEAR + THROAT + DEPTH - 1;

        for (int d = CLEAR + THROAT + 2; d <= back; d++) {
            for (int u = -HALF_W; u <= HALF_W; u++) {
                // A heap: tallest at the back and middle, thinning to nothing at the edges.
                double toBack = (double) (d - CLEAR - THROAT - 2)
                        / Math.max(1, back - CLEAR - THROAT - 2);
                double toEdge = 1.0D - Math.abs(u) / (double) HALF_W;
                double n = UndercroftPalette.hash(d, u, step, 71);
                int stack = (int) Math.round((toBack * 2.4D + toEdge * 2.2D + n * 1.4D) - 1.6D);
                if (stack <= 0) {
                    continue;
                }
                for (int v = 1; v <= Math.min(stack, 4); v++) {
                    Vec3 p = centre.add(out.scale(d)).add(across.scale(u)).add(up.scale(v - 2.0D));
                    BlockState state;
                    if (v == 1 && stack == 1) {
                        state = KERB;
                    } else if (cursed && v >= stack - 1) {
                        // Cursed only on the surface of the heap. Buried coin nobody can stand on
                        // would breed into rock it can never escape, and the top is what a foot
                        // comes down on anyway.
                        state = ExampleMod.GEMINO_PILE.get().defaultBlockState();
                    } else if (v == stack && UndercroftPalette.hash(d, v, u, 79) < GEM_CHANCE) {
                        state = GEM;
                    } else {
                        state = HOARD[(int) (UndercroftPalette.hash(d, v, u, 73) * HOARD.length)
                                % HOARD.length];
                    }
                    sink.set((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z),
                            state);
                }
            }
        }

        // A kerb across the front of the heap, so it reads as contained rather than spilled.
        for (int u = -HALF_W; u <= HALF_W; u++) {
            Vec3 p = centre.add(out.scale(CLEAR + THROAT + 1)).add(across.scale(u)).add(up.scale(-1.0D));
            sink.set((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z), KERB);
        }

        for (int i = -1; i <= 1; i += 2) {
            Vec3 p = centre.add(out.scale(CLEAR + THROAT + 3)).add(across.scale(i * (HALF_W - 1)))
                    .add(up.scale(HEIGHT - 3.0D));
            sink.set((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z), LAMP);
        }

        for (int i = -1; i <= 1; i += 2) {
            Vec3 p = centre.add(out.scale(CLEAR + THROAT + 1)).add(across.scale(i * (HALF_W - 1)))
                    .add(up.scale(-1.0D));
            chest(sink, new BlockPos((int) Math.floor(p.x), (int) Math.floor(p.y),
                    (int) Math.floor(p.z)));
        }
    }

    /** The door itself, filling the throat's mouth where it meets the line. */
    private void door(CarveSink sink, Vec3 centre, Vec3 out, Vec3 up) {
        Vec3 across = cross(out, up);
        BlockState leaf = ExampleMod.GRINGOTTS_VAULT_DOOR.get().defaultBlockState();
        for (int u = -2; u <= 2; u++) {
            for (int v = 0; v <= 4; v++) {
                Vec3 p = centre.add(out.scale(CLEAR)).add(across.scale(u)).add(up.scale(v - 2.0D));
                sink.set((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z), leaf);
            }
        }
        // A pair on the corridor side of the door. Without them a vault is a slightly different
        // shade of black going past at thirty metres a second, and you never know it was there.
        for (int i = -1; i <= 1; i += 2) {
            Vec3 p = centre.add(out.scale(CLEAR - 1.0D)).add(across.scale(i * 3.5D))
                    .add(up.scale(1.0D));
            sink.set((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z),
                    Blocks.SOUL_LANTERN.defaultBlockState()
                            .setValue(BlockStateProperties.HANGING, Boolean.TRUE));
        }
    }

    private void chest(CarveSink sink, BlockPos pos) {
        sink.set(pos.getX(), pos.getY(), pos.getZ(),
                Blocks.CHEST.defaultBlockState()
                        .setValue(ChestBlock.FACING, Direction.NORTH));
        sink.setLoot(pos.getX(), pos.getY(), pos.getZ(), LOOT);
    }

    private static Vec3 cross(Vec3 a, Vec3 b) {
        return a.cross(b).normalize();
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

    /**
     * The four anchor stubs the Ironbelly is chained to, and the chains hanging over them.
     *
     * <p>They are the only breakable blocks in the room, which is the whole puzzle: everything else
     * is unminable shell, so the one thing you CAN break is the one thing worth breaking.
     */
    private void chains(CarveSink sink, Vec3 centre, Vec3 out, Vec3 up) {
        Vec3 across = cross(out, up);
        for (int i = 0; i < 4; i++) {
            int u = (i % 2 == 0) ? -HALF_W + 1 : HALF_W - 1;
            int d = CLEAR + THROAT + ((i < 2) ? 3 : DEPTH - 2);
            Vec3 foot = centre.add(out.scale(d)).add(across.scale(u)).add(up.scale(-1.0D));
            int bx = (int) Math.floor(foot.x);
            int by = (int) Math.floor(foot.y);
            int bz = (int) Math.floor(foot.z);
            sink.set(bx, by, bz, Blocks.LIGHTNING_ROD.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties
                            .FACING, Direction.UP));
            // Chain running up from each stub, so it reads as tethered rather than decorated.
            for (int v = 1; v <= HEIGHT - 2; v++) {
                sink.set(bx, by + v, bz, Blocks.CHAIN.defaultBlockState());
            }
        }
    }

    /** Middle of the deepest chamber, where the dragon stands. */
    public Vec3 deepestChamberCentre() {
        return chamberCentre(AT.length - 1);
    }

    /**
     * Middle of one chamber, on its floor.
     *
     * <p>Derived from the same three numbers the carve used rather than remembered, so it cannot
     * drift out of step with where the room actually ended up.
     */
    public Vec3 chamberCentre(int index) {
        double s = this.track.length() * AT[index];
        Vec3 centre = this.track.positionAt(s);
        Vec3 up = this.track.upAt(s);
        Vec3 right = this.track.rightAt(s);
        double side = this.track.bankAt(s) < 0.0F ? 1.0D : -1.0D;
        return centre.add(right.scale(side).scale(CLEAR + THROAT + DEPTH * 0.5D))
                .add(up.scale(-1.0D));
    }

    public int vaultCount() {
        return AT.length;
    }

    /** Where each vault's door sits, for anything that needs to find them afterwards. */
    public Vec3 doorPosition(int index) {
        double s = this.track.length() * AT[index];
        return this.track.positionAt(s);
    }

}
