package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.DiagonArchwayBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The wall behind the Leaky Cauldron, and how it opens.
 *
 * <p>Three taps on the right brick, and then eight seconds in four beats:
 *
 * <ol>
 *   <li><b>The seam</b> (30 ticks). Nothing moves. A ring of light spreads out from the tapped
 *       brick through the mortar, one course at a time, and that is all -- the wall is still whole.
 *       Holding the opening back until the light has crossed the whole panel is what makes the
 *       opening land; a wall that starts coming apart on the third tap is just a door.</li>
 *   <li><b>The peel</b> (80 ticks). The tapped brick goes first, alone, and a hole appears. Then
 *       the bricks around it follow in a spiral -- each one slides out of the archway into the wall
 *       beside it, screwing about its own axis as it goes and hopping slightly as it clears.
 *       Screwing reads as masonry giving way; sliding flat reads as a drawer.</li>
 *   <li><b>The unfold</b> (25 ticks). The last bricks of the arch head withdraw, so the shape
 *       finishes as an arch rather than as a hole that grew.</li>
 *   <li><b>The settle</b> (15 ticks). Dust, a last grind of stone, and the arch stands open.</li>
 * </ol>
 *
 * <h2>How a brick moves</h2>
 *
 * <p>It does not. Minecraft draws a block where its block is, so the whole thing is drawn by
 * {@link com.hogwartsmagic.client.DiagonArchwayRenderer} as free cubes, while the real blocks sit
 * at {@code PHASE = MOVING}: invisible, so nothing is drawn twice, and still solid, so the arch is
 * not a way through until it is. Only at the end do they become {@code OPEN}.
 *
 * <p>The client is sent the arch cells once when the sequence starts and then counts along on its
 * own, so ten seconds of animation costs two packets rather than one a tick.
 *
 * <p>Only the brick that was tapped runs any of this. Every other brick in the panel sits at
 * {@code ticks = -1} and draws nothing, so there is exactly one sequence per wall however many
 * blocks it is made of.
 *
 * <h2>The plane is measured, never assumed</h2>
 *
 * <p>The panel is surveyed by flooding across connected archway bricks; whichever axis it barely
 * varies along is the one it faces, and the other horizontal one is the direction "across" that the
 * bricks slide along. Hard-coding it would work for exactly one rotation of the courtyard.
 */
public class DiagonArchwayBlockEntity extends BlockEntity {

    /** The light runs out through the mortar, and nothing moves yet. */
    public static final int GLOW = 30;
    /**
     * The shudder. The whole wall goes to the renderer and a shockwave rolls outward from the
     * tapped brick: every brick in the panel leans out and back as the crest passes, and nothing
     * has opened yet.
     *
     * <p>This beat exists because the first version animated only the twenty-five bricks of the
     * archway and left the other eighty-five standing dead still. That reads as a hole appearing in
     * a wall. A wall that goes liquid first, and only then opens, reads as magic doing it.
     */
    public static final int SHUDDER = 25;
    /** The spiral: bricks peel out of the archway into the flanking wall. */
    public static final int PEEL = 80;
    /** The head of the arch finishes. */
    public static final int UNFOLD = 25;
    /** The wall settles back, and the dust drops. */
    public static final int SETTLE = 20;
    public static final int TOTAL = GLOW + SHUDDER + PEEL + UNFOLD + SETTLE;

    /** When the bricks of the arch itself start leaving. */
    public static final int PEEL_START = GLOW + SHUDDER;

    /** How long one brick takes to clear the opening. */
    public static final int TRAVEL = 26;

    /** Half-width of the opening, so the arch is five bricks wide. */
    public static final int HALF_WIDTH = 2;
    /** How high the straight jambs run before the head starts to curve in. */
    public static final int SPRING = 3;

    /** How many taps, and how long a part-finished sequence of taps is remembered. */
    private static final int TAPS_NEEDED = 3;
    private static final int TAP_MEMORY = 60;

    private static final int PANEL_CAP = 512;
    private static final int BIN_SEARCH = 6;

    private static final String TICKS_KEY = "OpenTicks";
    private static final String ARCH_KEY = "Arch";
    private static final String PANEL_KEY = "Panel";
    private static final String AXES_KEY = "Axes";
    private static final String CENTRE_KEY = "ArchCentre";

    private int ticks = -1;
    private int taps;
    private long lastTapAt = Long.MIN_VALUE;

    /** The cells that leave. Empty on every brick but the one running the sequence. */
    private final List<BlockPos> arch = new ArrayList<>();
    /**
     * Every brick of the wall, including the ones that stay. The renderer needs all of them: the
     * arch bricks to peel away, and the rest to ripple in place and settle back.
     */
    private final List<BlockPos> panel = new ArrayList<>();
    private int acrossAxis = 0;
    private int normalAxis = 2;
    /** Across-coordinate and bottom Y of the arch, so the renderer can place each brick. */
    private int archCentre;
    private int archBottom;

    private int litRadius = -1;

    public DiagonArchwayBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.DIAGON_ARCHWAY_BLOCK_ENTITY.get(), pos, state);
    }

    // ---------------------------------------------------------------- what the renderer reads

    public int ticks() {
        return this.ticks;
    }

    public List<BlockPos> archCells() {
        return this.arch;
    }

    /** Every brick of the wall — the ones that leave and the ones that only ripple. */
    public List<BlockPos> panelCells() {
        return this.panel;
    }

    /**
     * Does this cell belong to the opening?
     *
     * <p>Shared with the renderer rather than sent, because both sides can work it out from the
     * arch centre and the same shape, and a list that had to agree across the wire is a list that
     * can disagree.
     */
    public static boolean isArchCell(int u, int v) {
        double head = HALF_WIDTH + 0.5D;
        return (Math.abs(u) <= HALF_WIDTH && v <= SPRING)
                || (v > SPRING && u * u + (v - SPRING) * (v - SPRING) <= head * head);
    }

    public int acrossAxis() {
        return this.acrossAxis;
    }

    public int normalAxis() {
        return this.normalAxis;
    }

    public int archCentre() {
        return this.archCentre;
    }

    public int archBottom() {
        return this.archBottom;
    }

    @Override
    public AABB getRenderBoundingBox() {
        // Bricks travel several blocks out of their own cells and a block entity is culled against
        // this box, so the default single-block bound would pop the whole wall off screen the
        // moment the camera drifted.
        return new AABB(this.worldPosition).inflate(24.0D);
    }

    /**
     * When one brick starts moving and how long it takes, from its offset to the tapped brick.
     *
     * <p>Static, and the only place this is worked out, because the server uses it to decide when
     * the sequence is over and the client uses it to decide where to draw -- and a spiral that
     * disagrees between the two would have bricks vanishing before they had gone anywhere.
     */
    public static float brickDelay(int du, int dv) {
        double dist = Math.sqrt((double) du * du + (double) dv * dv);
        double angle = (Math.atan2(dv, du) + Math.PI) / (Math.PI * 2.0);
        // 8 and 10, not 11 and 16. Adding the shudder beat pushed the spiral's start back
        // twenty-five ticks, and at the old spread the furthest brick was still in the air sixteen
        // ticks after the sequence ended -- so it vanished mid-flight when the blocks went to OPEN.
        // These leave about fifteen ticks of margin on the worst cell of a five-wide arch.
        return (float) (dist * 8.0 + angle * 10.0);
    }

    // ---------------------------------------------------------------- the tap

    /**
     * Somebody hit this brick. Only the brick three up and two across from the dustbin counts, and
     * it has to be hit three times.
     */
    public void tapped(ServerLevel level, @Nullable ServerPlayer player) {
        BlockState state = getBlockState();
        if (this.ticks >= 0 || state.getValue(DiagonArchwayBlock.PHASE) != DiagonArchwayBlock.SHUT) {
            return;
        }
        // Somebody else may already be opening this same wall from another brick.
        for (BlockPos pos : floodPanel(level)) {
            if (!pos.equals(this.worldPosition)
                    && level.getBlockEntity(pos) instanceof DiagonArchwayBlockEntity other
                    && other.ticks >= 0) {
                return;
            }
        }

        long now = level.getGameTime();
        if (now - this.lastTapAt > TAP_MEMORY) {
            this.taps = 0;
        }
        this.lastTapAt = now;

        if (!isKeyBrick(level)) {
            // A dull knock and a little dust. Deliberately not a failure message: finding the brick
            // is the puzzle, and telling the player they got it wrong gives away that there is a
            // right one nearby.
            level.playSound(null, this.worldPosition, SoundEvents.STONE_HIT, SoundSource.BLOCKS,
                    0.7F, 0.8F);
            dust(level, this.worldPosition, 3);
            this.taps = 0;
            return;
        }

        this.taps++;
        // The three taps climb in pitch, so the third one is audibly the last.
        float pitch = 0.9F + 0.25F * this.taps;
        level.playSound(null, this.worldPosition, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 1.0F, pitch);
        dust(level, this.worldPosition, 6);

        if (this.taps < TAPS_NEEDED) {
            return;
        }
        this.taps = 0;
        beginOpening(level, player);
    }

    /**
     * Is this the brick? Three up and two across from the dustbin.
     *
     * <p>Either direction across is accepted. The books say "three up, two across" without saying
     * across which way, and a player who counts the correct number of bricks in the wrong direction
     * has solved the puzzle.
     */
    private boolean isKeyBrick(ServerLevel level) {
        BlockPos bin = findDustbin(level);
        if (bin == null) {
            return false;
        }
        if (this.worldPosition.getY() != bin.getY() + 3) {
            return false;
        }
        int mine = axis(this.worldPosition, this.acrossAxis);
        int theirs = axis(bin, this.acrossAxis);
        return Math.abs(mine - theirs) == 2;
    }

    @Nullable
    private BlockPos findDustbin(ServerLevel level) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                this.worldPosition.offset(-BIN_SEARCH, -BIN_SEARCH, -BIN_SEARCH),
                this.worldPosition.offset(BIN_SEARCH, BIN_SEARCH, BIN_SEARCH))) {
            if (!level.getBlockState(pos).is(Blocks.CAULDRON)) {
                continue;
            }
            double d = pos.distSqr(this.worldPosition);
            if (d < bestDist) {
                bestDist = d;
                best = pos.immutable();
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- opening

    private void beginOpening(ServerLevel level, @Nullable ServerPlayer opener) {
        List<BlockPos> panel = floodPanel(level);
        if (panel.isEmpty()) {
            return;
        }
        measureAxes(panel);
        buildArch(panel);
        if (this.arch.isEmpty()) {
            return;
        }
        // Keep the whole wall, not only the part that leaves: the renderer ripples the bricks that
        // stay, and a wall whose surround does not move reads as a hole appearing rather than as
        // masonry giving way.
        this.panel.clear();
        for (BlockPos p : panel) {
            this.panel.add(p.immutable());
        }

        this.ticks = 0;
        this.litRadius = -1;
        setChanged();
        sync(level);

        level.playSound(null, this.worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS,
                0.6F, 1.6F);
        if (opener != null) {
            opener.displayClientMessage(
                    Component.literal("The bricks begin to shift..."), true);
        }
    }

    /** Every archway brick reachable from this one. */
    private List<BlockPos> floodPanel(Level level) {
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(this.worldPosition);
        seen.add(this.worldPosition);
        while (!queue.isEmpty() && found.size() < PANEL_CAP) {
            BlockPos pos = queue.poll();
            if (!(level.getBlockState(pos).getBlock() instanceof DiagonArchwayBlock)) {
                continue;
            }
            found.add(pos);
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return found;
    }

    /** The axis the panel barely varies along is the one it faces. */
    private void measureAxes(List<BlockPos> panel) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : panel) {
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ());
            maxZ = Math.max(maxZ, p.getZ());
        }
        if (maxX - minX <= maxZ - minZ) {
            this.normalAxis = 0;
            this.acrossAxis = 2;
        } else {
            this.normalAxis = 2;
            this.acrossAxis = 0;
        }
    }

    /**
     * The cells that move: a five-wide opening with straight jambs and a rounded head, centred on
     * the tapped brick but nudged so it cannot hang off the end of the panel.
     */
    private void buildArch(List<BlockPos> panel) {
        this.arch.clear();
        int minA = Integer.MAX_VALUE, maxA = Integer.MIN_VALUE, minY = Integer.MAX_VALUE;
        for (BlockPos p : panel) {
            int a = axis(p, this.acrossAxis);
            minA = Math.min(minA, a);
            maxA = Math.max(maxA, a);
            minY = Math.min(minY, p.getY());
        }
        int centre = axis(this.worldPosition, this.acrossAxis);
        centre = Math.max(minA + HALF_WIDTH, Math.min(maxA - HALF_WIDTH, centre));
        this.archCentre = centre;
        this.archBottom = minY;

        for (BlockPos p : panel) {
            int u = axis(p, this.acrossAxis) - centre;
            int v = p.getY() - minY;
            if (isArchCell(u, v)) {
                this.arch.add(p.immutable());
            }
        }
    }

    private static int axis(BlockPos pos, int which) {
        return which == 0 ? pos.getX() : which == 1 ? pos.getY() : pos.getZ();
    }

    // ---------------------------------------------------------------- ticking

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  DiagonArchwayBlockEntity brick) {
        if (brick.ticks < 0 || !(level instanceof ServerLevel server)) {
            return;
        }
        brick.ticks++;

        if (brick.ticks <= GLOW) {
            brick.runSeam(server);
        } else if (brick.ticks == GLOW + 1) {
            brick.clearSeam(server);
            // The WHOLE wall goes over to the renderer, not just the arch: invisible but still
            // solid, so every brick can move and nothing is a way through until it is.
            brick.setPhase(server, brick.panel, DiagonArchwayBlock.MOVING);
            server.playSound(null, pos, SoundEvents.ANCIENT_DEBRIS_BREAK, SoundSource.BLOCKS, 1.0F, 0.5F);
        } else if (brick.ticks < TOTAL) {
            // A grind of stone every half second while bricks are actually moving, so the sound
            // tracks the animation instead of being one hit at the start.
            if (brick.ticks % 10 == 0) {
                float p = (brick.ticks - GLOW) / (float) (PEEL + UNFOLD);
                server.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS,
                        0.8F, 0.55F + 0.35F * p);
            }
            if (brick.ticks % 4 == 0) {
                for (BlockPos cell : brick.arch) {
                    if (server.random.nextFloat() < 0.12F) {
                        brick.dust(server, cell, 1);
                    }
                }
            }
        } else {
            // The arch becomes a way through; everything else in the wall comes back as ordinary
            // brick, so the surround stops being drawn by the renderer and goes solid again.
            List<BlockPos> stays = new ArrayList<>();
            for (BlockPos p : brick.panel) {
                if (!brick.arch.contains(p)) {
                    stays.add(p);
                }
            }
            brick.setPhase(server, stays, DiagonArchwayBlock.SHUT);
            brick.setPhase(server, brick.arch, DiagonArchwayBlock.OPEN);
            server.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.6F);
            brick.ticks = -1;
            brick.arch.clear();
            brick.panel.clear();
            brick.setChanged();
            brick.sync(server);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state,
                                  DiagonArchwayBlockEntity brick) {
        if (brick.ticks >= 0 && brick.ticks < TOTAL) {
            brick.ticks++;
        }
    }

    /** One course of mortar lights per tick, spreading outward from the tap. */
    private void runSeam(ServerLevel level) {
        int radius = Math.max(1, (this.ticks * 9) / GLOW);
        if (radius == this.litRadius) {
            return;
        }
        this.litRadius = radius;
        for (BlockPos p : floodPanel(level)) {
            double d = Math.sqrt(p.distSqr(this.worldPosition));
            boolean lit = d <= radius && d > radius - 2.2D;
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof DiagonArchwayBlock && s.getValue(DiagonArchwayBlock.LIT) != lit) {
                level.setBlock(p, s.setValue(DiagonArchwayBlock.LIT, lit), Block.UPDATE_CLIENTS);
            }
        }
        level.playSound(null, this.worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 0.35F, 0.7F + 0.08F * radius);
    }

    private void clearSeam(ServerLevel level) {
        for (BlockPos p : floodPanel(level)) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof DiagonArchwayBlock && s.getValue(DiagonArchwayBlock.LIT)) {
                level.setBlock(p, s.setValue(DiagonArchwayBlock.LIT, false), Block.UPDATE_CLIENTS);
            }
        }
    }

    private void setPhase(ServerLevel level, List<BlockPos> cells, int phase) {
        for (BlockPos p : cells) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof DiagonArchwayBlock) {
                level.setBlock(p, s.setValue(DiagonArchwayBlock.PHASE, phase)
                        .setValue(DiagonArchwayBlock.LIT, false), Block.UPDATE_ALL);
            }
        }
    }

    private void dust(ServerLevel level, BlockPos at, int count) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, getBlockState()),
                at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D,
                count, 0.3D, 0.3D, 0.3D, 0.02D);
    }

    private void sync(ServerLevel level) {
        BlockState state = getBlockState();
        level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TICKS_KEY, this.ticks);
        tag.putIntArray(AXES_KEY, new int[]{this.acrossAxis, this.normalAxis});
        tag.putIntArray(CENTRE_KEY, new int[]{this.archCentre, this.archBottom});
        long[] cells = new long[this.arch.size()];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = this.arch.get(i).asLong();
        }
        tag.putLongArray(ARCH_KEY, cells);
        long[] all = new long[this.panel.size()];
        for (int i = 0; i < all.length; i++) {
            all[i] = this.panel.get(i).asLong();
        }
        tag.putLongArray(PANEL_KEY, all);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.ticks = tag.contains(TICKS_KEY) ? tag.getInt(TICKS_KEY) : -1;
        int[] axes = tag.getIntArray(AXES_KEY);
        if (axes.length == 2) {
            this.acrossAxis = axes[0];
            this.normalAxis = axes[1];
        }
        int[] centre = tag.getIntArray(CENTRE_KEY);
        if (centre.length == 2) {
            this.archCentre = centre[0];
            this.archBottom = centre[1];
        }
        this.arch.clear();
        for (long packed : tag.getLongArray(ARCH_KEY)) {
            this.arch.add(BlockPos.of(packed));
        }
        this.panel.clear();
        for (long packed : tag.getLongArray(PANEL_KEY)) {
            this.panel.add(BlockPos.of(packed));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
