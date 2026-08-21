package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.block.GringottsVaultDoorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Opens a vault door, once, in five beats over ten seconds.
 *
 * <p>The order is the order the films play it, and each beat is doing a different job:
 *
 * <ol>
 *   <li><b>The touch</b> - a bright point runs down the seam from where the hand landed. Nothing has
 *       happened yet, and that is the point: it is the wind-up that makes the ripple land.</li>
 *   <li><b>The ripple</b> - rings travel out across the face of the door from the touch, and the
 *       iron sings. Drawn in particles rather than by swapping blocks: a wave of block changes
 *       across a door is a colour ramp at one-block resolution and a chunk-section rebuild every
 *       tick, and it reads as a stone door changing colour rather than as metal going liquid.</li>
 *   <li><b>The mechanism</b> - three cog rings turn behind the face and the frame vents steam. They
 *       <em>counter-rotate</em>, because three rings all turning the same way read as wallpaper,
 *       and they ease in over eight ticks, because a mechanism this size does not start at speed.</li>
 *   <li><b>The bolts</b> - twenty-four of them draw back around the rim <em>in angular order</em>,
 *       each one a clack a fraction above the last. Sorting them by angle rather than by scan order
 *       is what makes it a sequence instead of twenty-four identical noises.</li>
 *   <li><b>The parting</b> - the leaf goes, from the touch point outward, and the vault breathes
 *       out.</li>
 * </ol>
 *
 * <p><b>The leaf is never removed, only made to part.</b> That is not a shortcut, it is the only
 * version that works: this block entity lives inside one of the leaf blocks, so deleting them
 * deletes the thing running the sequence, and the door would freeze half-dissolved with an
 * unminable remnant standing in the doorway forever. Setting {@code OPEN} keeps the block entity
 * alive - vanilla preserves a block entity across a state change on the same block - and the open
 * state is invisible, has no collision and blocks no light, so it reads as gone while still being
 * something the door can pull back.
 *
 * <p>Which also buys the reseal for free. A vault opened by somebody the bank noticed shuts again a
 * minute later, and the only real hazard in that - a state change suffocating whoever is standing
 * in the doorway - is handled by skipping occupied cells and coming back for them. A door that
 * cannot shut keeps trying, which is also the correct behaviour.
 *
 * <p>The door's plane is measured, never assumed. A vault can face any direction depending on which
 * way the line was banking when it passed, so the axis the leaf barely varies along is the one it
 * faces, and the other two are the ones a distance can be measured in. Hard-coded offsets would
 * open three doors in four sideways into the rock.
 */
public class GringottsVaultDoorBlockEntity extends BlockEntity {

    private static final int TOUCH = 20;
    private static final int RIPPLE = 44;
    private static final int COGS = 40;
    private static final int BOLTS = 48;
    private static final int PART = 48;
    private static final int TOTAL = TOUCH + RIPPLE + COGS + BOLTS + PART;

    private static final int SEARCH = 6;
    private static final int BOLT_COUNT = 24;
    private static final int LEAF_PER_TICK = 5;

    /** How long an open vault stays open, once the bank has noticed. */
    private static final int RESEAL_DELAY = 20 * 60;
    private static final int RESEAL_PER_TICK = 5;

    private static final String TICKS_KEY = "OpenTicks";
    private static final String TOUCH_KEY = "Touch";
    private static final String RESEAL_KEY = "ResealAt";
    private static final String RESEALING_KEY = "Resealing";

    private int ticks = -1;
    private Vec3 touch = Vec3.ZERO;

    private final List<BlockPos> leaf = new ArrayList<>();
    private int leafTaken;
    private boolean surveyed;

    /** Game time the door should shut itself, or -1 for a vault nobody was caught opening. */
    private long resealAt = -1L;
    private boolean resealing;

    private int uAxis = 0;
    private int vAxis = 1;
    private double maxRadius = 1.0D;

    public GringottsVaultDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.GRINGOTTS_VAULT_DOOR_BLOCK_ENTITY.get(), pos, state);
    }

    public boolean beginOpening(ServerLevel level, Vec3 touchedAt,
                                @javax.annotation.Nullable net.minecraft.server.level.ServerPlayer opener) {
        if (this.ticks >= 0 || this.resealing
                || getBlockState().getValue(GringottsVaultDoorBlock.OPEN)) {
            return false;
        }
        // Any block of the leaf can start it, so the lock-out has to be asked of the whole leaf
        // rather than of this one cell: somebody may already have a sequence running two blocks
        // over, and two sequences on one door would fight over the same cells.
        for (BlockPos pos : findLeaf(level)) {
            if (level.getBlockState(pos).getValue(GringottsVaultDoorBlock.OPEN)) {
                return false;
            }
            if (!pos.equals(this.worldPosition)
                    && level.getBlockEntity(pos) instanceof GringottsVaultDoorBlockEntity other
                    && (other.ticks >= 0 || other.resealing)) {
                return false;
            }
        }

        this.ticks = 0;
        this.touch = touchedAt;
        this.surveyed = false;
        this.leafTaken = 0;

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
        setChanged();
        return true;
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

    private double[] plane(BlockPos p) {
        return new double[]{axis(p, this.uAxis) - axis(this.worldPosition, this.uAxis),
                axis(p, this.vAxis) - axis(this.worldPosition, this.vAxis)};
    }

    private double radiusOf(BlockPos p) {
        double[] d = plane(p);
        return Math.hypot(d[0], d[1]);
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

    private void tickOpening(ServerLevel level) {
        if (!this.surveyed) {
            survey(level);
        }
        int t = this.ticks++;

        if (t < TOUCH) {
            beatTouch(level, t);
        } else if (t < TOUCH + RIPPLE) {
            beatRipple(level, t - TOUCH);
        } else if (t < TOUCH + RIPPLE + COGS) {
            beatCogs(level, t - TOUCH - RIPPLE);
        } else if (t < TOUCH + RIPPLE + COGS + BOLTS) {
            beatBolts(level, t - TOUCH - RIPPLE - COGS);
        } else if (t < TOTAL) {
            beatPart(level);
        } else {
            this.ticks = -1;
            setChanged();
        }
    }

    /** A bright point runs down the seam from where the hand landed. */
    private void beatTouch(ServerLevel level, int t) {
        double f = t / (double) TOUCH;
        Vec3 p = this.touch.add(0.0D, this.maxRadius * (0.5D - f) * 1.4D, 0.0D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 0.04D, 0.04D, 0.04D, 0.0D);
        if (t % 4 == 0) {
            level.sendParticles(ParticleTypes.WAX_OFF, p.x, p.y, p.z, 1, 0.1D, 0.1D, 0.1D, 0.0D);
        }
    }

    /** The iron goes liquid: rings travelling out from the touch, and the metal singing. */
    private void beatRipple(ServerLevel level, int t) {
        if (t == 0) {
            level.playSound(null, this.worldPosition, ModSounds.VAULT_DOOR_RIPPLE.get(),
                    SoundSource.BLOCKS, 1.5F, 1.0F);
        }
        double front = (t / (double) RIPPLE) * (this.maxRadius + 2.0D);
        int points = 22;
        for (int i = 0; i < points; i++) {
            double a = i * 2.0D * Math.PI / points;
            Vec3 p = at(a, front);
            level.sendParticles(ParticleTypes.WAX_OFF, p.x, p.y, p.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1,
                        0.05D, 0.05D, 0.05D, 0.0D);
            }
        }
        // One trailing trough behind the crest, so it reads as a wave rather than as a ring.
        double back = front - 1.6D;
        if (back > 0.0D) {
            for (int i = 0; i < points; i += 2) {
                Vec3 p = at(i * 2.0D * Math.PI / points, back);
                level.sendParticles(ParticleTypes.WAX_OFF, p.x, p.y, p.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
            }
        }
    }

    /** Three cog rings turn behind the face, counter to each other, and the frame vents steam. */
    private void beatCogs(ServerLevel level, int t) {
        if (t == 0) {
            level.playSound(null, this.worldPosition, ModSounds.VAULT_DOOR_COGS.get(),
                    SoundSource.BLOCKS, 1.6F, 0.9F);
        }
        // Eased in over eight ticks. A mechanism this size does not start at speed.
        double ease = Math.min(1.0D, t / 8.0D);
        for (int ring = 0; ring < 3; ring++) {
            double r = this.maxRadius * (0.34D + 0.28D * ring);
            double dir = (ring % 2 == 0) ? 1.0D : -1.0D;
            double spin = dir * ease * t * 0.09D;
            int teeth = 6 + ring * 5;
            for (int i = 0; i < teeth; i++) {
                Vec3 p = at(spin + i * 2.0D * Math.PI / teeth, r);
                level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
        if (t % 10 == 0) {
            // Four vents in the frame, on the diagonals so they do not line up with the bolts.
            for (int i = 0; i < 4; i++) {
                Vec3 p = at(Math.PI / 4.0D + i * Math.PI / 2.0D, this.maxRadius + 0.6D);
                level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 6,
                        0.05D, 0.05D, 0.05D, 0.03D);
            }
        }
    }

    /** Twenty-four bolts draw back around the rim, in angular order, each above the last. */
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
        level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 8, 0.10D, 0.10D, 0.10D, 0.08D);
        level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 3, 0.08D, 0.08D, 0.08D, 0.01D);
        // Each one screws as it withdraws rather than sliding: a short arc trailing off the bolt,
        // because screwing reads as a lock and sliding reads as a drawer.
        for (int k = 1; k <= 3; k++) {
            Vec3 q = at(a + k * Math.toRadians(5.0D), this.maxRadius * (0.85D - 0.05D * k));
            level.sendParticles(ParticleTypes.WAX_OFF, q.x, q.y, q.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        // Identical noise twenty-four times is a rattle. Climbing, it is a sequence.
        level.playSound(null, BlockPos.containing(p), SoundEvents.IRON_TRAPDOOR_OPEN,
                SoundSource.BLOCKS, 0.55F, 0.5F + 0.02F * index);
    }

    /** The leaf parts, from the touch outward, and the vault breathes out. */
    private void beatPart(ServerLevel level) {
        if (this.leafTaken == 0) {
            level.playSound(null, this.worldPosition, ModSounds.VAULT_DOOR_OPEN.get(),
                    SoundSource.BLOCKS, 2.0F, 1.0F);
        }
        for (int i = 0; i < LEAF_PER_TICK && this.leafTaken < this.leaf.size(); i++) {
            BlockPos pos = this.leaf.get(this.leafTaken++);
            BlockState was = level.getBlockState(pos);
            if (!(was.getBlock() instanceof GringottsVaultDoorBlock)
                    || was.getValue(GringottsVaultDoorBlock.OPEN)) {
                continue;
            }
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, was),
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 5, 0.3D, 0.3D, 0.3D,
                    0.02D);
            level.setBlock(pos, was.setValue(GringottsVaultDoorBlock.OPEN, Boolean.TRUE), 3);
        }
        // Green vapour, pooling along the floor rather than rising: the vault has been shut a
        // long time and what comes out of it is heavier than air.
        Vec3 mouth = Vec3.atCenterOf(this.worldPosition);
        level.sendParticles(ParticleTypes.SCULK_SOUL, mouth.x, mouth.y - 0.4D, mouth.z, 4,
                0.8D, 0.2D, 0.8D, 0.005D);
        level.sendParticles(ParticleTypes.COMPOSTER, mouth.x, mouth.y - 0.8D, mouth.z, 3,
                1.0D, 0.1D, 1.0D, 0.002D);
    }

    private void beginResealing(ServerLevel level) {
        this.resealing = true;
        this.resealAt = -1L;
        survey(level);
        // Outside in, so the last thing to close is where the hand was.
        Collections.reverse(this.leaf);
        level.playSound(null, this.worldPosition, ModSounds.VAULT_DOOR_SEAL.get(),
                SoundSource.BLOCKS, 2.0F, 1.0F);
        setChanged();
    }

    /**
     * Shuts the leaf again, five cells a tick, skipping anything anybody is standing in.
     *
     * <p>The skip is the whole point: {@code setBlock} on an occupied cell suffocates whoever is in
     * it, and the doorway is exactly where a fleeing player is. A door that cannot shut keeps
     * trying rather than giving up or crushing them.
     */
    private void tickResealing(ServerLevel level) {
        int done = 0;
        boolean anyLeft = false;
        for (BlockPos pos : this.leaf) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof GringottsVaultDoorBlock)
                    || !state.getValue(GringottsVaultDoorBlock.OPEN)) {
                continue;
            }
            anyLeft = true;
            if (done >= RESEAL_PER_TICK) {
                break;
            }
            if (!level.getEntities((Entity) null, new AABB(pos), e -> !e.isSpectator()).isEmpty()) {
                continue;
            }
            level.setBlock(pos, state.setValue(GringottsVaultDoorBlock.OPEN, Boolean.FALSE), 3);
            level.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5D, pos.getY() + 0.5D,
                    pos.getZ() + 0.5D, 4, 0.3D, 0.3D, 0.3D, 0.02D);
            done++;
        }
        if (!anyLeft) {
            this.resealing = false;
            this.leafTaken = 0;
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TICKS_KEY, this.ticks);
        tag.putLong(RESEAL_KEY, this.resealAt);
        tag.putBoolean(RESEALING_KEY, this.resealing);
        tag.putDouble(TOUCH_KEY + "X", this.touch.x);
        tag.putDouble(TOUCH_KEY + "Y", this.touch.y);
        tag.putDouble(TOUCH_KEY + "Z", this.touch.z);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.ticks = tag.contains(TICKS_KEY) ? tag.getInt(TICKS_KEY) : -1;
        this.resealAt = tag.contains(RESEAL_KEY) ? tag.getLong(RESEAL_KEY) : -1L;
        this.resealing = tag.getBoolean(RESEALING_KEY);
        this.touch = new Vec3(tag.getDouble(TOUCH_KEY + "X"), tag.getDouble(TOUCH_KEY + "Y"),
                tag.getDouble(TOUCH_KEY + "Z"));
        // Whatever had already parted stays parted: the survey re-runs and simply finds the leaf
        // already half open, and the cells it re-walks are skipped because they are already open.
        this.surveyed = false;
        this.leafTaken = 0;
    }
}
