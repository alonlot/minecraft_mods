package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.util.MagicParticles;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.block.GringottsVaultDoorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Opens a vault door, once, in five beats over ten seconds, and <b>the blocks move</b>.
 *
 * <p>The order is the order the films play it, and each beat is doing a different job:
 *
 * <ol>
 *   <li><b>The touch</b> - a bright point runs down the seam from where the hand landed, and every
 *       plate leans a degree or two toward it. Nothing has opened yet, and that is the point: it is
 *       the wind-up that makes the ripple land.</li>
 *   <li><b>The ripple</b> - the iron goes liquid. A crest with a trailing trough travels outward
 *       from the touch and every plate it passes lifts a third of a block out of the door and tips
 *       to catch the light. The crest draws at full brightness, which is the single trick that
 *       makes iron read as molten rather than merely bumpy.</li>
 *   <li><b>The mechanism</b> - three rings of plates turn behind the face, counter to each other,
 *       because three rings turning the same way read as wallpaper. They ease in over eight ticks,
 *       because a mechanism this size does not start at speed.</li>
 *   <li><b>The bolts</b> - the rim plates withdraw one at a time in angular order, each screwing
 *       about its own axis as it goes. Screwing reads as a lock; sliding reads as a drawer.</li>
 *   <li><b>The parting</b> - the leaf splits down the middle and both halves fold back a hundred
 *       degrees into the frame, seam first, and the vault breathes out.</li>
 * </ol>
 *
 * <h2>How a block moves</h2>
 *
 * <p>It does not. Minecraft can only draw a block where its block is, so the whole animation is
 * drawn by {@link com.hogwartsmagic.client.GringottsVaultDoorRenderer} - one textured cube per
 * leaf cell, free to be anywhere - while the real blocks sit in {@code PHASE = MOVING}: invisible,
 * so nothing is drawn twice, and still solid, so the doorway is not open until it is open. Only at
 * the end do they become {@code PARTED}, which is when you can walk through.
 *
 * <p>Which means the client has to know the shape of the leaf. It is sent the whole thing once when
 * the sequence starts and then <b>counts along on its own</b>, with a correction at each beat
 * boundary - five or six packets for ten seconds rather than one a tick.
 *
 * <p>Only the block entity that was touched runs any of this. The rest of the leaf sits at
 * {@code ticks = -1} and draws nothing, so there is exactly one animation per door however many
 * blocks it is made of.
 *
 * <p>Not removing the leaf is also what makes the reseal possible, and not removing it is forced
 * anyway: this block entity lives inside one of those cells, so deleting them deletes the thing
 * running the sequence, and the door would freeze half-open with an unminable remnant in the
 * doorway forever.
 *
 * <p>The door's plane is measured, never assumed. A vault can face any direction depending on which
 * way the line was banking when it passed, so the axis the leaf barely varies along is the one it
 * faces, and the other two are the ones a distance can be measured in. Hard-coded offsets would
 * open three doors in four sideways into the rock.
 */
public class GringottsVaultDoorBlockEntity extends BlockEntity {

    public static final int TOUCH = 20;
    public static final int RIPPLE = 44;
    public static final int COGS = 40;
    public static final int BOLTS = 48;
    public static final int PART = 48;
    public static final int TOTAL = TOUCH + RIPPLE + COGS + BOLTS + PART;

    /** How many bolts go round the rim. The clacks climb in pitch so it reads as a sequence. */
    public static final int BOLT_COUNT = 24;

    private static final int SEARCH = 6;

    /** How long an open vault stays open, once the bank has noticed. */
    private static final int RESEAL_DELAY = 20 * 60;
    private static final int RESEAL_PER_TICK = 5;

    private static final String TICKS_KEY = "OpenTicks";
    private static final String TOUCH_KEY = "Touch";
    private static final String RESEAL_KEY = "ResealAt";
    private static final String RESEALING_KEY = "Resealing";
    private static final String LEAF_KEY = "Leaf";
    private static final String AXES_KEY = "Axes";
    private static final String RADIUS_KEY = "Radius";

    private int ticks = -1;
    /**
     * Who laid the hand on the door, for the advancement at the far end of the sequence. Not saved:
     * a vault half-open across a restart simply finishes without crediting anyone, which is a
     * better failure than crediting whoever happens to be standing nearest.
     */
    @Nullable
    private java.util.UUID opener;
    private Vec3 touch = Vec3.ZERO;

    private final List<BlockPos> leaf = new ArrayList<>();
    private boolean surveyed;

    /** Game time the door should shut itself, or -1 for a vault nobody was caught opening. */
    private long resealAt = -1L;
    private boolean resealing;
    private int resealTaken;

    private int uAxis = 0;
    private int vAxis = 1;
    private double maxRadius = 1.0D;

    public GringottsVaultDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.GRINGOTTS_VAULT_DOOR_BLOCK_ENTITY.get(), pos, state);
    }

    // ------------------------------------------------------------------ what the renderer reads

    public int ticks() {
        return this.ticks;
    }

    public Vec3 touch() {
        return this.touch;
    }

    public List<BlockPos> leafPositions() {
        return this.leaf;
    }

    public int uAxis() {
        return this.uAxis;
    }

    public int vAxis() {
        return this.vAxis;
    }

    /** The axis the door faces: whichever one is neither u nor v. */
    public int normalAxis() {
        return 3 - this.uAxis - this.vAxis;
    }

    public double maxRadius() {
        return this.maxRadius;
    }

    /** Plane coordinates of a cell relative to this one, in the door's own two axes. */
    public double planeU(BlockPos pos) {
        return axis(pos, this.uAxis) - axis(this.worldPosition, this.uAxis);
    }

    public double planeV(BlockPos pos) {
        return axis(pos, this.vAxis) - axis(this.worldPosition, this.vAxis);
    }

    @Override
    public AABB getRenderBoundingBox() {
        // The animation throws plates well outside their own cells, and a block entity is culled
        // against this box, so the default one-block bound would pop the whole door off screen.
        return new AABB(this.worldPosition).inflate(SEARCH + 8);
    }

    // ------------------------------------------------------------------ starting

    public boolean beginOpening(ServerLevel level, Vec3 touchedAt,
                                @Nullable net.minecraft.server.level.ServerPlayer opener) {
        if (this.ticks >= 0 || this.resealing
                || getBlockState().getValue(GringottsVaultDoorBlock.PHASE)
                        != GringottsVaultDoorBlock.SHUT) {
            return false;
        }
        // Any block of the leaf can start it, so the lock-out has to be asked of the whole leaf
        // rather than of this one cell: somebody may already have a sequence running two blocks
        // over, and two sequences on one door would fight over the same cells.
        for (BlockPos pos : findLeaf(level)) {
            if (level.getBlockState(pos).getValue(GringottsVaultDoorBlock.PHASE)
                    != GringottsVaultDoorBlock.SHUT) {
                return false;
            }
            if (!pos.equals(this.worldPosition)
                    && level.getBlockEntity(pos) instanceof GringottsVaultDoorBlockEntity other
                    && (other.ticks >= 0 || other.resealing)) {
                return false;
            }
        }

        this.ticks = 0;
        this.opener = opener != null ? opener.getUUID() : null;
        this.touch = touchedAt;
        this.surveyed = false;
        survey(level);

        // Hand the whole leaf over to the renderer at once: invisible, so it is not drawn twice,
        // and still solid, so nobody walks through a door that has not opened yet.
        for (BlockPos pos : this.leaf) {
            setPhase(level, pos, GringottsVaultDoorBlock.MOVING);
        }

        level.playSound(null, this.worldPosition, ModSounds.VAULT_DOOR_TOUCH.get(),
                SoundSource.BLOCKS, 1.1F, 1.0F);
        // Opening a vault is the moment the bank turns on you, which is the whole heist - unless
        // the hand on the door was a goblin's, in which case as far as the bank is concerned a
        // goblin opened a vault, which is what goblins are for. Polyjuice is not a key here, it is
        // the difference between a robbery and a withdrawal, and the Thief's Downfall halfway down
        // the line is what stops that being a free pass to the deepest vaults.
        if (opener != null && !com.hogwartsmagic.gringotts.GringottsDisguise.isGoblin(opener)) {
            com.hogwartsmagic.gringotts.GringottsAlarm alarm =
                    com.hogwartsmagic.gringotts.GringottsAlarm.get(level);
            alarm.raise(level, opener, this.worldPosition, 2, "gringotts.alarm.vault");
            // Only a vault the bank knows about shuts itself. Open one clean and you keep it.
            if (alarm.heatOf(opener) > 0) {
                this.resealAt = level.getGameTime() + TOTAL + RESEAL_DELAY;
            }
        }
        sync(level);
        return true;
    }

    private void setPhase(ServerLevel level, BlockPos pos, int phase) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof GringottsVaultDoorBlock
                && state.getValue(GringottsVaultDoorBlock.PHASE) != phase) {
            level.setBlock(pos, state.setValue(GringottsVaultDoorBlock.PHASE, phase),
                    Block.UPDATE_ALL);
        }
    }

    /** Pushes the whole animation state to everyone watching. Called at beat boundaries only. */
    private void sync(ServerLevel level) {
        setChanged();
        BlockState state = getBlockState();
        level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    private List<BlockPos> findLeaf(ServerLevel level) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                this.worldPosition.offset(-SEARCH, -SEARCH, -SEARCH),
                this.worldPosition.offset(SEARCH, SEARCH, SEARCH))) {
            if (level.getBlockState(pos).getBlock() instanceof GringottsVaultDoorBlock) {
                found.add(pos.immutable());
            }
        }
        return found;
    }

    private static int axis(BlockPos p, int a) {
        return a == 0 ? p.getX() : a == 1 ? p.getY() : p.getZ();
    }

    private double radiusOf(BlockPos p) {
        return Math.hypot(planeU(p), planeV(p));
    }

    private Vec3 at(double angle, double radius) {
        double[] c = {this.worldPosition.getX() + 0.5D, this.worldPosition.getY() + 0.5D,
                this.worldPosition.getZ() + 0.5D};
        c[this.uAxis] += radius * Math.cos(angle);
        c[this.vAxis] += radius * Math.sin(angle);
        return new Vec3(c[0], c[1], c[2]);
    }

    private void survey(ServerLevel level) {
        this.leaf.clear();
        this.leaf.addAll(findLeaf(level));

        int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (BlockPos pos : this.leaf) {
            for (int a = 0; a < 3; a++) {
                lo[a] = Math.min(lo[a], axis(pos, a));
                hi[a] = Math.max(hi[a], axis(pos, a));
            }
        }
        int normal = 0;
        for (int a = 1; a < 3; a++) {
            if (hi[a] - lo[a] < hi[normal] - lo[normal]) {
                normal = a;
            }
        }
        // A vault door is never a floor hatch, so if the thinnest axis came out vertical the leaf
        // is wider than it is tall and the real face is one of the horizontals.
        if (normal == 1) {
            normal = (hi[0] - lo[0]) <= (hi[2] - lo[2]) ? 0 : 2;
        }
        this.uAxis = normal == 0 ? 1 : 0;
        this.vAxis = normal == 2 ? 1 : 2;

        this.maxRadius = 1.0D;
        for (BlockPos pos : this.leaf) {
            this.maxRadius = Math.max(this.maxRadius, radiusOf(pos));
        }
        // Outward from the touch: the door opens where the hand was, not where the scan began.
        this.leaf.sort(Comparator.comparingDouble(pos ->
                pos.distToCenterSqr(this.touch.x, this.touch.y, this.touch.z)));
        this.surveyed = true;
    }

    // ------------------------------------------------------------------ ticking

    public static void tick(Level level, BlockPos pos, BlockState state,
                            GringottsVaultDoorBlockEntity door) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (door.ticks >= 0) {
            door.tickOpening(server);
        } else if (door.resealing) {
            door.tickResealing(server);
        } else if (door.resealAt >= 0L && server.getGameTime() >= door.resealAt) {
            door.beginResealing(server);
        }
    }

    /**
     * The client's whole job: count.
     *
     * <p>It is told the leaf and the tick number when the sequence starts and corrected at every
     * beat boundary, so this only ever has to carry it across a beat - a few seconds at worst, and
     * the animation is a pure function of the count, so a correction never jumps.
     */
    public static void clientTick(Level level, BlockPos pos, BlockState state,
                                  GringottsVaultDoorBlockEntity door) {
        if (door.ticks >= 0 && door.ticks < TOTAL) {
            door.ticks++;
        }
    }

    private void tickOpening(ServerLevel level) {
        if (!this.surveyed) {
            survey(level);
        }
        int t = this.ticks++;

        if (t == TOUCH || t == TOUCH + RIPPLE || t == TOUCH + RIPPLE + COGS
                || t == TOUCH + RIPPLE + COGS + BOLTS) {
            sync(level);
        }

        if (t < TOUCH) {
            beatTouch(level, t);
        } else if (t < TOUCH + RIPPLE) {
            beatRipple(level, t - TOUCH);
        } else if (t < TOUCH + RIPPLE + COGS) {
            beatCogs(level, t - TOUCH - RIPPLE);
        } else if (t < TOUCH + RIPPLE + COGS + BOLTS) {
            beatBolts(level, t - TOUCH - RIPPLE - COGS);
        } else if (t < TOTAL) {
            beatPart(level, t - TOUCH - RIPPLE - COGS - BOLTS);
        } else {
            // The plates have folded back into the frame. Only now is the doorway a doorway.
            for (BlockPos pos : this.leaf) {
                setPhase(level, pos, GringottsVaultDoorBlock.PARTED);
            }
            this.ticks = -1;
            // Credited here rather than when the hand lands: the vault is open, not merely touched.
            if (this.opener != null) {
                com.hogwartsmagic.util.ModAdvancements.award(
                        level.getServer().getPlayerList().getPlayer(this.opener), "vault_opened");
                this.opener = null;
            }
            sync(level);
        }
    }

    /** A bright point runs down the seam from where the hand landed. */
    private void beatTouch(ServerLevel level, int t) {
        double f = t / (double) TOUCH;
        Vec3 p = this.touch.add(0.0D, this.maxRadius * (0.5D - f) * 1.4D, 0.0D);
        level.sendParticles(MagicParticles.SPARK_METAL, p.x, p.y, p.z, 2, 0.04D, 0.04D, 0.04D, 0.0D);
        if (t % 4 == 0) {
            level.sendParticles(MagicParticles.GLINT_GOLD, p.x, p.y, p.z, 1, 0.1D, 0.1D, 0.1D, 0.0D);
        }
    }

    /** The iron goes liquid. The plates themselves do the work; this is the spray off the crest. */
    private void beatRipple(ServerLevel level, int t) {
        if (t == 0) {
            level.playSound(null, this.worldPosition, ModSounds.VAULT_DOOR_RIPPLE.get(),
                    SoundSource.BLOCKS, 1.5F, 1.0F);
        }
        double front = (t / (double) RIPPLE) * (this.maxRadius + 3.0D);
        for (int i = 0; i < 14; i++) {
            Vec3 p = at(i * 2.0D * Math.PI / 14.0D, front);
            level.sendParticles(MagicParticles.GLINT_GOLD, p.x, p.y, p.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
            if (i % 3 == 0) {
                level.sendParticles(MagicParticles.SPARK_METAL, p.x, p.y, p.z, 1,
                        0.05D, 0.05D, 0.05D, 0.0D);
            }
        }
    }

    /** Three rings turn behind the face, counter to each other, and the frame vents steam. */
    private void beatCogs(ServerLevel level, int t) {
        if (t == 0) {
            level.playSound(null, this.worldPosition, ModSounds.VAULT_DOOR_COGS.get(),
                    SoundSource.BLOCKS, 1.6F, 0.9F);
        }
        if (t % 10 == 0) {
            // Four vents in the frame, on the diagonals so they do not line up with the bolts.
            for (int i = 0; i < 4; i++) {
                Vec3 p = at(Math.PI / 4.0D + i * Math.PI / 2.0D, this.maxRadius + 0.6D);
                level.sendParticles(MagicParticles.SMOKE_GREY, p.x, p.y, p.z, 6,
                        0.05D, 0.05D, 0.05D, 0.03D);
            }
        }
    }

    /** The rim plates withdraw in angular order, each a fraction above the last. */
    private void beatBolts(ServerLevel level, int t) {
        if (t == 0) {
            level.playSound(null, this.worldPosition, ModSounds.VAULT_DOOR_BOLTS.get(),
                    SoundSource.BLOCKS, 1.4F, 1.0F);
        }
        int index = t * BOLT_COUNT / BOLTS;
        if (t * BOLT_COUNT % BOLTS != 0 && t != 0) {
            return;
        }
        double a = -Math.PI + index * 2.0D * Math.PI / BOLT_COUNT;
        Vec3 p = at(a, this.maxRadius * 0.85D);
        level.sendParticles(MagicParticles.SPARK_METAL, p.x, p.y, p.z, 8, 0.10D, 0.10D, 0.10D, 0.08D);
        level.sendParticles(MagicParticles.SMOKE_GREY, p.x, p.y, p.z, 3, 0.08D, 0.08D, 0.08D, 0.01D);
        // Identical noise twenty-four times is a rattle. Climbing, it is a sequence.
        level.playSound(null, BlockPos.containing(p), SoundEvents.IRON_TRAPDOOR_OPEN,
                SoundSource.BLOCKS, 0.55F, 0.5F + 0.02F * index);
    }

    /** Both halves fold back, and the vault breathes out. */
    private void beatPart(ServerLevel level, int t) {
        if (t == 0) {
            level.playSound(null, this.worldPosition, ModSounds.VAULT_DOOR_OPEN.get(),
                    SoundSource.BLOCKS, 2.0F, 1.0F);
        }
        // Green vapour, pooling along the floor rather than rising: the vault has been shut a
        // long time and what comes out of it is heavier than air.
        Vec3 mouth = Vec3.atCenterOf(this.worldPosition);
        level.sendParticles(MagicParticles.SMOKE_GREEN, mouth.x, mouth.y - 0.4D, mouth.z, 4,
                0.8D, 0.2D, 0.8D, 0.005D);
        level.sendParticles(MagicParticles.MOTE_GREEN, mouth.x, mouth.y - 0.8D, mouth.z, 3,
                1.0D, 0.1D, 1.0D, 0.002D);
        if (t % 6 == 0) {
            level.sendParticles(MagicParticles.SMOKE_DARK, mouth.x, mouth.y, mouth.z, 3,
                    this.maxRadius * 0.5D, this.maxRadius * 0.5D, this.maxRadius * 0.5D, 0.01D);
        }
    }

    // ------------------------------------------------------------------ shutting again

    private void beginResealing(ServerLevel level) {
        this.resealing = true;
        this.resealAt = -1L;
        this.resealTaken = 0;
        survey(level);
        level.playSound(null, this.worldPosition, ModSounds.VAULT_DOOR_SEAL.get(),
                SoundSource.BLOCKS, 2.0F, 1.0F);
        sync(level);
    }

    /**
     * Shuts the leaf again, five cells a tick, skipping anything anybody is standing in.
     *
     * <p>The skip is the whole point: putting a solid block back into an occupied cell suffocates
     * whoever is in it, and the doorway is exactly where a fleeing player is. A door that cannot
     * shut keeps trying rather than giving up or crushing them.
     *
     * <p>Outside in, so the last thing to close is the middle - the reverse of the way it opened.
     */
    private void tickResealing(ServerLevel level) {
        int done = 0;
        boolean anyLeft = false;
        for (int i = this.leaf.size() - 1; i >= 0; i--) {
            BlockPos pos = this.leaf.get(i);
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof GringottsVaultDoorBlock)
                    || state.getValue(GringottsVaultDoorBlock.PHASE)
                            == GringottsVaultDoorBlock.SHUT) {
                continue;
            }
            anyLeft = true;
            if (done >= RESEAL_PER_TICK) {
                break;
            }
            if (!level.getEntities((Entity) null, new AABB(pos), e -> !e.isSpectator()).isEmpty()) {
                continue;
            }
            setPhase(level, pos, GringottsVaultDoorBlock.SHUT);
            level.sendParticles(MagicParticles.SPARK_METAL, pos.getX() + 0.5D, pos.getY() + 0.5D,
                    pos.getZ() + 0.5D, 4, 0.3D, 0.3D, 0.3D, 0.02D);
            done++;
        }
        if (!anyLeft) {
            this.resealing = false;
            sync(level);
        }
    }

    // ------------------------------------------------------------------ persistence and sync

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TICKS_KEY, this.ticks);
        tag.putLong(RESEAL_KEY, this.resealAt);
        tag.putBoolean(RESEALING_KEY, this.resealing);
        tag.putDouble(TOUCH_KEY + "X", this.touch.x);
        tag.putDouble(TOUCH_KEY + "Y", this.touch.y);
        tag.putDouble(TOUCH_KEY + "Z", this.touch.z);
        long[] cells = new long[this.leaf.size()];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = this.leaf.get(i).asLong();
        }
        tag.putLongArray(LEAF_KEY, cells);
        tag.putIntArray(AXES_KEY, new int[]{this.uAxis, this.vAxis});
        tag.putDouble(RADIUS_KEY, this.maxRadius);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.ticks = tag.contains(TICKS_KEY) ? tag.getInt(TICKS_KEY) : -1;
        this.resealAt = tag.contains(RESEAL_KEY) ? tag.getLong(RESEAL_KEY) : -1L;
        this.resealing = tag.getBoolean(RESEALING_KEY);
        this.touch = new Vec3(tag.getDouble(TOUCH_KEY + "X"), tag.getDouble(TOUCH_KEY + "Y"),
                tag.getDouble(TOUCH_KEY + "Z"));
        this.leaf.clear();
        for (long packed : tag.getLongArray(LEAF_KEY)) {
            this.leaf.add(BlockPos.of(packed));
        }
        int[] axes = tag.getIntArray(AXES_KEY);
        if (axes.length == 2) {
            this.uAxis = axes[0];
            this.vAxis = axes[1];
        }
        this.maxRadius = Math.max(1.0D, tag.getDouble(RADIUS_KEY));
        // The server re-surveys before it does anything, so a door that reloads mid-sequence picks
        // up whatever the leaf actually looks like now rather than what it looked like then.
        this.surveyed = false;
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
