package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.DiagonArchwayBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * <p>A spell to arm it, three taps on the right brick, and then nine seconds in five beats:
 *
 * <ol>
 *   <li><b>The seam</b> (30 ticks). Nothing moves. A ring of light spreads out from the tapped
 *       brick through the mortar, one course at a time, and that is all -- the wall is still whole.
 *       Holding the opening back until the light has crossed the whole panel is what makes the
 *       opening land; a wall that starts coming apart on the third tap is just a door.</li>
 *   <li><b>The shudder</b> (25 ticks). The whole wall goes over to the renderer and a crest rolls
 *       outward from the tap: every brick leans out and back as it passes. Still nothing has
 *       opened. It is the wind-up.</li>
 *   <li><b>The peel</b> (80 ticks). The tapped brick goes first, alone, and a hole appears. Then
 *       the bricks around it follow in a spiral -- each one slides out of the archway into the wall
 *       beside it, screwing about its own axis as it goes and hopping slightly as it clears.
 *       Screwing reads as masonry giving way; sliding flat reads as a drawer.</li>
 *   <li><b>The unfold</b> (25 ticks). The last bricks of the arch head withdraw, so the shape
 *       finishes as an arch rather than as a hole that grew.</li>
 *   <li><b>The settle</b> (20 ticks). The surround settles back, the dust drops, and the arch
 *       stands open.</li>
 * </ol>
 *
 * <p>Bricks, not blocks: each block of the panel is drawn as eight separate bricks, two across and
 * four up, each with its own timing and spin. See {@code DiagonArchwayRenderer}.
 *
 * <p>The alley itself starts building the moment the sequence starts, not when it ends -- it needs
 * a few seconds, and these nine are exactly the cover it wants.
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

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("HogwartsMagic-DiagonAlley");

    /** The light runs out through the mortar, and nothing moves yet. */
    public static final int GLOW = 14;
    /**
     * The shudder. The whole wall goes to the renderer and a shockwave rolls outward from the
     * tapped brick: every brick in the panel leans out and back as the crest passes, and nothing
     * has opened yet.
     *
     * <p>This beat exists because the first version animated only the twenty-five bricks of the
     * archway and left the other eighty-five standing dead still. That reads as a hole appearing in
     * a wall. A wall that goes liquid first, and only then opens, reads as magic doing it.
     */
    public static final int SHUDDER = 10;
    /** The spiral: bricks peel out of the archway into the flanking wall. */
    public static final int PEEL = 34;
    /** The head of the arch finishes. */
    public static final int UNFOLD = 12;
    /** The wall settles back, and the dust drops. */
    public static final int SETTLE = 10;
    public static final int TOTAL = GLOW + SHUDDER + PEEL + UNFOLD + SETTLE;

    /** When the bricks of the arch itself start leaving. */
    public static final int PEEL_START = GLOW + SHUDDER;

    /**
     * Spread of the per-brick jitter the renderer adds. Declared here, not there, because the
     * server needs it to know when a cell's last brick has actually left -- see freeCleared.
     */
    public static final float SUB_SPREAD = 7.0F;

    /** How long one brick takes to clear the opening. */
    public static final int TRAVEL = 12;

    /** Half-width of the opening, so the arch is five bricks wide. */
    public static final int HALF_WIDTH = 2;
    /** How high the straight jambs run before the head starts to curve in. */
    public static final int SPRING = 3;

    /**
     * The smallest wall an archway fits in. The opening is five wide with a rounded head on top, so
     * anything narrower or shorter than this has nowhere to put it.
     */
    public static final int MIN_PANEL_WIDTH = 7;
    public static final int MIN_PANEL_HEIGHT = 7;

    /** How long the wall glows after a spell arms it. */
    private static final int ARMED_FLASH = 30;

    private static final int TAPS_NEEDED = 3;
    private static final int TAP_MEMORY = 60;

    private static final int PANEL_CAP = 512;
    private static final int BIN_SEARCH = 6;

    private static final String TICKS_KEY = "OpenTicks";
    private static final String ARCH_KEY = "Arch";
    private static final String PANEL_KEY = "Panel";
    private static final String ARMED_KEY = "Armed";
    private static final String KEY_KEY = "KeyBrick";
    private static final String CLOSING_KEY = "Closing";
    private static final String AXES_KEY = "Axes";
    private static final String CENTRE_KEY = "ArchCentre";

    private int ticks = -1;
    private int taps;
    private long lastTapAt = Long.MIN_VALUE;
    /**
     * Whether a spell has been cast on this wall. Tapping an unarmed wall does nothing but knock
     * dust off it however many times you do it, and however right the brick is.
     */
    private boolean armed;
    /**
     * Whether this is the brick to tap. Worked out once when the wall is armed and then kept
     * lit, because a wall that gives no sign of where to knock is indistinguishable from one
     * that is unarmed, too small, or has no dustbin in range -- four different problems with
     * one symptom.
     */
    private boolean isKey;
    /** Countdown on the glow a spell leaves behind. Ticks down even when no sequence is running. */
    private int flashTicks;
    /**
     * Running the sequence backwards.
     *
     * <p>Every pose the renderer draws is a pure function of the clock, so counting down
     * instead of up flies every brick back into the wall for nothing. Doing it any other way --
     * setting the blocks back and letting the client find out -- made the wall reassemble itself
     * one block at a time, which read as the arch badly reopening rather than as it closing.
     */
    private boolean closing;

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
        // Scaled with the rest of the sequence when it came down from nine seconds to four.
        // The worst cell of a five-wide arch is clear at PEEL_START + 28 + SUB_SPREAD + TRAVEL,
        // about 71 of the 80 ticks, so nothing is still in the air when the wall settles.
        return (float) (dist * 4.5 + angle * 4.0);
    }

    // ---------------------------------------------------------------- the tap

    /**
     * Somebody hit this brick. Only the brick three up and two across from the dustbin counts, and
     * it has to be hit three times.
     */
    public void tapped(ServerLevel level, @Nullable ServerPlayer player) {
        BlockState state = getBlockState();
        if (this.ticks >= 0) {
            return;                             // a sequence is already running
        }

        // Tap to open, tap to close. A wall that could only be shut by walking away and waiting
        // out a timer left the player with nothing to press and no way to undo it.
        List<BlockPos> wall = floodPanel(level);
        boolean standingOpen = false;
        for (BlockPos p : wall) {
            BlockState cell = level.getBlockState(p);
            if (cell.getBlock() instanceof DiagonArchwayBlock
                    && cell.getValue(DiagonArchwayBlock.PHASE) == DiagonArchwayBlock.OPEN) {
                standingOpen = true;
                break;
            }
        }
        // Second opinion, because the two can disagree and an arch that cannot be shut is worse
        // than one that shuts when you did not quite mean it.
        if (!standingOpen
                && com.hogwartsmagic.worldgen.DiagonAlleyPortal.openDoorwayNear(
                        level, this.worldPosition) != null) {
            standingOpen = true;
        }
        if (standingOpen) {
            if (com.hogwartsmagic.worldgen.DiagonAlleyManager.isBusy()) {
                if (player != null) {
                    player.displayClientMessage(Component.literal(
                            "The street is still going up. Give it a moment."), true);
                }
                return;
            }
            LOGGER.info("Closing the archway at {} ({} bricks in the panel)",
                    this.worldPosition, wall.size());
            beginClosing(level, player, wall);
            return;
        }

        if (state.getValue(DiagonArchwayBlock.PHASE) != DiagonArchwayBlock.SHUT) {
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

        if (!this.armed) {
            // Unarmed: a knock and dust, whatever brick it is. No message, because the wall being
            // inert is not an error the player made -- it is the state every wall starts in.
            level.playSound(null, this.worldPosition, SoundEvents.STONE_HIT, SoundSource.BLOCKS,
                    0.7F, 0.75F);
            dust(level, this.worldPosition, 2);
            return;
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
     * Run the whole thing backwards.
     *
     * <p>The way through stops being a way through immediately -- both walls go solid on the first
     * tick, before a single brick has moved -- and then the bricks fly back in over three seconds
     * and the wall knits shut. Closing the door first and animating second is the order that reads
     * as a door closing; the other way round is a door that is already shut pretending otherwise.
     */
    private void beginClosing(ServerLevel level, @Nullable ServerPlayer player,
                              List<BlockPos> found) {
        if (found.isEmpty()) {
            return;
        }
        measureAxes(found);
        buildArch(found);
        this.panel.clear();
        for (BlockPos p : found) {
            this.panel.add(p.immutable());
        }
        setPhase(level, this.panel, DiagonArchwayBlock.MOVING);
        com.hogwartsmagic.worldgen.DiagonAlleyPortal.mirrorArch(
                level, archCentrePos(), this.arch, false);
        this.closing = true;
        this.ticks = TOTAL;
        setChanged();
        sync(level);
        level.playSound(null, this.worldPosition, SoundEvents.ANCIENT_DEBRIS_BREAK,
                SoundSource.BLOCKS, 1.0F, 0.55F);
        if (player != null) {
            player.displayClientMessage(Component.literal(
                    "The bricks fold themselves back, and the wall knits shut."), true);
        }
    }

    /** The middle of the opening, at its threshold -- the one cell that names this doorway. */
    private BlockPos archCentrePos() {
        return new BlockPos(
                this.acrossAxis == 0 ? this.archCentre : this.worldPosition.getX(),
                this.archBottom,
                this.acrossAxis == 2 ? this.archCentre : this.worldPosition.getZ());
    }

    /**
     * A spell has been cast at this wall. Arms every brick of it, so the taps start counting.
     *
     * <p>Returns false if the wall is too small to hold an archway, and says so — the one case
     * where telling the player is right, because the magic worked and it is only the wall that is
     * wrong. Everywhere else the wall stays silent on purpose: finding the brick is the puzzle.
     */
    public boolean enchant(ServerLevel level, @Nullable ServerPlayer caster) {
        List<BlockPos> found = floodPanel(level);
        if (found.isEmpty()) {
            return false;
        }
        measureAxes(found);

        int minA = Integer.MAX_VALUE, maxA = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (BlockPos p : found) {
            int a = axis(p, this.acrossAxis);
            minA = Math.min(minA, a);
            maxA = Math.max(maxA, a);
            minY = Math.min(minY, p.getY());
            maxY = Math.max(maxY, p.getY());
        }
        int wide = maxA - minA + 1;
        int tall = maxY - minY + 1;
        if (wide < MIN_PANEL_WIDTH || tall < MIN_PANEL_HEIGHT) {
            if (caster != null) {
                caster.displayClientMessage(Component.literal(
                        "The wall is too small for an archway — it needs to be at least "
                                + MIN_PANEL_WIDTH + " wide and " + MIN_PANEL_HEIGHT
                                + " tall (this one is " + wide + " by " + tall + ")."), false);
            }
            return false;
        }

        boolean changed = false;
        BlockPos key = null;
        for (BlockPos p : found) {
            if (!(level.getBlockEntity(p) instanceof DiagonArchwayBlockEntity brick)) {
                continue;
            }
            if (!brick.armed) {
                changed = true;
            }
            brick.armed = true;
            // Every brick gets the wall's axes, so each can judge for itself whether it is the
            // one -- otherwise only the brick the spell happened to hit would know.
            brick.acrossAxis = this.acrossAxis;
            brick.normalAxis = this.normalAxis;
            brick.isKey = brick.isKeyBrick(level);
            if (brick.isKey) {
                key = p;
            }
            brick.setChanged();
        }
        if (!changed) {
            return false;                       // already armed; the spell finds nothing to do
        }
        if (key == null && caster != null) {
            caster.displayClientMessage(Component.literal(
                    "The wall answers, but nothing marks it. Stand a cauldron against it "
                            + "and cast again."), false);
        }

        // A pulse of light across the whole wall, so arming is unmistakable.
        for (BlockPos p : found) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof DiagonArchwayBlock) {
                level.setBlock(p, s.setValue(DiagonArchwayBlock.LIT, true), Block.UPDATE_CLIENTS);
            }
        }
        this.flashTicks = ARMED_FLASH;          // cleared by serverTick a moment later
        level.playSound(null, this.worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 0.9F, 1.1F);
        level.playSound(null, this.worldPosition, SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.BLOCKS, 0.7F, 0.8F);
        if (caster != null) {
            caster.displayClientMessage(Component.literal(
                    "The mortar glows, and one brick is glowing brighter. Tap it three times."),
                    false);
        }
        return true;
    }

    public boolean isArmed() {
        return this.armed;
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

        // Which way the street runs: away from whoever is standing at the wall.
        int wallN = axis(this.worldPosition, this.normalAxis);
        int openerN = opener != null ? axis(opener.blockPosition(), this.normalAxis) : wallN - 1;
        int sign = wallN >= openerN ? 1 : -1;
        Direction facing = this.normalAxis == 0
                ? (sign > 0 ? Direction.EAST : Direction.WEST)
                : (sign > 0 ? Direction.SOUTH : Direction.NORTH);

        // The middle of the opening, at its threshold.
        BlockPos archCentrePos = new BlockPos(
                this.acrossAxis == 0 ? this.archCentre : this.worldPosition.getX(),
                this.archBottom,
                this.acrossAxis == 2 ? this.archCentre : this.worldPosition.getZ());

        // Start the alley building NOW, not when the animation ends. It needs a few seconds, and
        // the nine the wall spends coming apart is exactly the cover it wants -- so by the time
        // there is a hole to walk through, there is a street on the other side of it.
        // Tapping from inside is the way out, and the street is already standing: there is nothing
        // to build, only a hole to cut. Only a wall in somebody else world starts a build.
        if (!com.hogwartsmagic.worldgen.DiagonAlleyDimension.isAlley(level)
                && com.hogwartsmagic.worldgen.DiagonAlleyManager.open(level, archCentrePos, facing)
                        == null) {
            if (opener != null) {
                opener.displayClientMessage(Component.literal(
                        "The bricks shudder and go still. Somewhere, another archway is already opening."),
                        false);
            }
            this.arch.clear();
            this.panel.clear();
            return;
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

    /**
     * Every archway brick reachable from this one, <b>in the same plane</b>.
     *
     * <p>The plane filter is what lets the alley's perimeter wall be made of the same brick and
     * joined to the entrance wall. Without it the flood runs round the corner and off down the
     * boundary, and then the wall the player built is measured as being a couple of hundred
     * blocks wide with its arch somewhere out in the terraces.
     *
     * <p>The flood itself is still three-dimensional -- the plane is not known until the shape
     * has been measured, so it is measured first and the result trimmed to it.
     */
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
        if (found.size() < 2) {
            return found;
        }
        measureAxes(found);
        int mine = axis(this.worldPosition, this.normalAxis);
        List<BlockPos> coplanar = new ArrayList<>();
        for (BlockPos p : found) {
            if (axis(p, this.normalAxis) == mine) {
                coplanar.add(p);
            }
        }
        return coplanar;
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
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        // The arming glow fades on its own, and it has to be handled before the early return below:
        // no sequence is running when a wall is merely armed, so nothing else would ever clear it.
        if (brick.flashTicks > 0) {
            brick.flashTicks--;
            if (brick.flashTicks == 0) {
                brick.clearSeam(server);
            }
        }

        // The marked brick stays lit and sparkles for as long as the wall is armed. This is the
        // only thing telling the player where to knock, so it holds even while the wall is
        // flashing, resealed, or reloaded from disk.
        if (brick.armed && brick.isKey && brick.ticks < 0) {
            BlockState here = brick.getBlockState();
            if (here.getBlock() instanceof DiagonArchwayBlock
                    && here.getValue(DiagonArchwayBlock.PHASE) == DiagonArchwayBlock.SHUT
                    && !here.getValue(DiagonArchwayBlock.LIT)) {
                server.setBlock(pos, here.setValue(DiagonArchwayBlock.LIT, true),
                        Block.UPDATE_CLIENTS);
            }
            if (server.getGameTime() % 12 == 0) {
                server.sendParticles(ParticleTypes.ENCHANT,
                        pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                        3, 0.35D, 0.35D, 0.35D, 0.01D);
            }
        }
        if (brick.ticks < 0) {
            return;
        }

        // Hold at the end of the shudder until the street behind the wall actually exists.
        // The build normally finishes inside the wind-up, but on a slow server or a big clear
        // it can run long, and an arch that opens onto a hole in the ground is worse than an
        // arch that takes a moment longer. The wall keeps rippling while it waits.
        if (brick.ticks >= PEEL_START
                && com.hogwartsmagic.worldgen.DiagonAlleyManager.isBusy()) {
            brick.ticks = PEEL_START;
            return;
        }
        if (brick.closing) {
            brick.ticks--;
            if (brick.ticks % 10 == 0) {
                server.playSound(null, pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS,
                        0.8F, 0.6F);
            }
            if (brick.ticks <= GLOW) {
                brick.setPhase(server, brick.panel, DiagonArchwayBlock.SHUT);
                server.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS,
                        1.0F, 0.6F);
                brick.closing = false;
                brick.ticks = -1;
                brick.arch.clear();
                brick.panel.clear();
                brick.setChanged();
                brick.sync(server);
            }
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
            brick.freeCleared(server);
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
            // The same hole has to be cut in the other world, or stepping through the open side
            // would put somebody inside the solid wall standing at the same coordinates there.
            com.hogwartsmagic.worldgen.DiagonAlleyPortal.mirrorArch(
                    server, brick.archCentrePos(), brick.arch, true);
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
            // Never put out the marked brick: it is the instruction.
            if (level.getBlockEntity(p) instanceof DiagonArchwayBlockEntity b
                    && b.armed && b.isKey) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof DiagonArchwayBlock && s.getValue(DiagonArchwayBlock.LIT)) {
                level.setBlock(p, s.setValue(DiagonArchwayBlock.LIT, false), Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * Turn each arch cell into a way through as soon as its own bricks have flown clear,
     * rather than turning the whole opening to air when the sequence ends.
     *
     * <p>Blocks at MOVING are invisible but still solid, which is what lets the renderer move
     * them. Holding all of them until the last one landed meant the arch looked open for a few
     * seconds before you could walk through it, and an invisible wall you can see daylight
     * through is worse than a slow door.
     */
    private void freeCleared(ServerLevel level) {
        if (this.arch.isEmpty()) {
            return;
        }
        int tapA = axisOf(this.worldPosition, this.acrossAxis);
        int tapY = this.worldPosition.getY();
        for (BlockPos cell : this.arch) {
            BlockState at = level.getBlockState(cell);
            if (!(at.getBlock() instanceof DiagonArchwayBlock)
                    || at.getValue(DiagonArchwayBlock.PHASE) != DiagonArchwayBlock.MOVING) {
                continue;
            }
            float gone = PEEL_START + brickDelay(axisOf(cell, this.acrossAxis) - tapA,
                    cell.getY() - tapY) + SUB_SPREAD + TRAVEL;
            if (this.ticks >= gone) {
                level.setBlock(cell,
                        at.setValue(DiagonArchwayBlock.PHASE, DiagonArchwayBlock.OPEN),
                        Block.UPDATE_CLIENTS);
            }
        }
    }

    private static int axisOf(BlockPos pos, int axis) {
        return axis == 0 ? pos.getX() : axis == 1 ? pos.getY() : pos.getZ();
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
        tag.putBoolean(ARMED_KEY, this.armed);
        tag.putBoolean(KEY_KEY, this.isKey);
        tag.putBoolean(CLOSING_KEY, this.closing);
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
        this.armed = tag.getBoolean(ARMED_KEY);
        this.isKey = tag.getBoolean(KEY_KEY);
        this.closing = tag.getBoolean(CLOSING_KEY);
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
