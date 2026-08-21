package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.GringottsVaultDoorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Opens a vault door, once, in four beats.
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
 *   <li><b>The bolts</b> - eight of them draw back around the rim <em>in angular order</em>, each
 *       one a clack a semitone above the last. Sorting them by angle rather than by scan order is
 *       what makes it a sequence instead of eight identical noises.</li>
 *   <li><b>The parting</b> - the leaf goes, from the touch point outward, and the vault breathes
 *       out.</li>
 * </ol>
 *
 * <p>The door's plane is measured, never assumed. A vault can face any direction depending on which
 * way the line was banking when it passed, so the axis the leaf barely varies along is the one it
 * faces, and the other two are the ones a distance can be measured in. Hard-coded offsets would
 * open three doors in four sideways into the rock.
 */
public class GringottsVaultDoorBlockEntity extends BlockEntity {

    private static final int TOUCH = 16;
    private static final int RIPPLE = 30;
    private static final int BOLTS = 26;
    private static final int PART = 22;
    private static final int TOTAL = TOUCH + RIPPLE + BOLTS + PART;

    private static final int SEARCH = 6;
    private static final int BOLT_COUNT = 8;
    private static final int LEAF_PER_TICK = 2;

    private static final String TICKS_KEY = "OpenTicks";
    private static final String TOUCH_KEY = "Touch";

    private int ticks = -1;
    private Vec3 touch = Vec3.ZERO;

    private final List<BlockPos> leaf = new ArrayList<>();
    private int leafTaken;
    private boolean surveyed;

    private int uAxis = 0;
    private int vAxis = 1;
    private double maxRadius = 1.0D;

    public GringottsVaultDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.GRINGOTTS_VAULT_DOOR_BLOCK_ENTITY.get(), pos, state);
    }

    public boolean beginOpening(ServerLevel level, Vec3 touchedAt,
                                @javax.annotation.Nullable net.minecraft.server.level.ServerPlayer opener) {
        if (this.ticks >= 0 || getBlockState().getValue(GringottsVaultDoorBlock.OPEN)) {
            return false;
        }
        this.ticks = 0;
        this.touch = touchedAt;
        this.surveyed = false;
        // Mark the whole leaf as opening, so no other block of it can start a second sequence.
        for (BlockPos pos : findLeaf(level)) {
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(GringottsVaultDoorBlock.OPEN)) {
                level.setBlock(pos, state.setValue(GringottsVaultDoorBlock.OPEN, Boolean.TRUE), 3);
            }
        }
        level.playSound(null, this.worldPosition, SoundEvents.NETHERITE_BLOCK_HIT,
                SoundSource.BLOCKS, 1.0F, 1.6F);
        // Opening a vault is the moment the bank turns on you, which is the whole heist.
        if (opener != null) {
            com.hogwartsmagic.gringotts.GringottsAlarm.get(level)
                    .raise(level, opener, this.worldPosition, 2, "gringotts.alarm.vault");
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

    private double angleOf(BlockPos p) {
        double[] d = plane(p);
        return Math.atan2(d[1], d[0]);
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
        if (door.ticks < 0 || !(level instanceof ServerLevel server)) {
            return;
        }
        if (!door.surveyed) {
            door.survey(server);
        }
        int t = door.ticks++;

        if (t < TOUCH) {
            door.beatTouch(server, t);
        } else if (t < TOUCH + RIPPLE) {
            door.beatRipple(server, t - TOUCH);
        } else if (t < TOUCH + RIPPLE + BOLTS) {
            door.beatBolts(server, t - TOUCH - RIPPLE);
        } else if (t < TOTAL) {
            door.beatPart(server);
        } else {
            door.ticks = -1;
            door.setChanged();
        }
    }

    /** A bright point runs down the seam from where the hand landed. */
    private void beatTouch(ServerLevel level, int t) {
        double f = t / (double) TOUCH;
        Vec3 p = this.touch.add(0.0D, this.maxRadius * (0.5D - f) * 1.4D, 0.0D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 0.04D, 0.04D, 0.04D, 0.0D);
        if (t % 4 == 0) {
            level.sendParticles(ParticleTypes.WAX_OFF, p.x, p.y, p.z, 1, 0.1D, 0.1D, 0.1D, 0.0D);
            level.playSound(null, this.worldPosition, SoundEvents.CHAIN_STEP, SoundSource.BLOCKS,
                    0.35F, 1.8F);
        }
    }

    /** The iron goes liquid: rings travelling out from the touch, and the metal singing. */
    private void beatRipple(ServerLevel level, int t) {
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
        if (t % 7 == 0) {
            // Pitch climbing with the wave, so the ring you can see and the note you can hear are
            // obviously the same thing.
            level.playSound(null, this.worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS,
                    0.7F, 0.55F + 0.08F * (t / 7.0F));
        }
    }

    /** Eight bolts draw back around the rim, in angular order, each a step above the last. */
    private void beatBolts(ServerLevel level, int t) {
        int index = t * BOLT_COUNT / BOLTS;
        if (t * BOLT_COUNT % BOLTS != 0 && t != 0) {
            return;
        }
        double a = -Math.PI + index * 2.0D * Math.PI / BOLT_COUNT;
        Vec3 p = at(a, this.maxRadius * 0.85D);
        level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 10, 0.12D, 0.12D, 0.12D, 0.09D);
        level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 4, 0.1D, 0.1D, 0.1D, 0.01D);
        level.playSound(null, BlockPos.containing(p), SoundEvents.IRON_TRAPDOOR_OPEN,
                SoundSource.BLOCKS, 0.9F, 0.5F + 0.03F * index);
        if (index % 3 == 0) {
            level.playSound(null, this.worldPosition, SoundEvents.NETHERITE_BLOCK_PLACE,
                    SoundSource.BLOCKS, 0.9F, 0.45F);
        }
    }

    /** The leaf goes, from the touch outward, and the vault breathes out. */
    private void beatPart(ServerLevel level) {
        if (this.leafTaken == 0) {
            level.playSound(null, this.worldPosition, SoundEvents.ANCIENT_DEBRIS_BREAK,
                    SoundSource.BLOCKS, 1.6F, 0.4F);
            level.playSound(null, this.worldPosition, SoundEvents.IRON_DOOR_OPEN,
                    SoundSource.BLOCKS, 1.8F, 0.5F);
        }
        for (int i = 0; i < LEAF_PER_TICK && this.leafTaken < this.leaf.size(); i++) {
            BlockPos pos = this.leaf.get(this.leafTaken++);
            BlockState was = level.getBlockState(pos);
            if (was.isAir()) {
                continue;
            }
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, was),
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 5, 0.3D, 0.3D, 0.3D,
                    0.02D);
            level.removeBlock(pos, false);
        }
        // Green vapour, pooling along the floor rather than rising: the vault has been shut a
        // long time and what comes out of it is heavier than air.
        Vec3 mouth = Vec3.atCenterOf(this.worldPosition);
        level.sendParticles(ParticleTypes.SCULK_SOUL, mouth.x, mouth.y - 0.4D, mouth.z, 4,
                0.8D, 0.2D, 0.8D, 0.005D);
        level.sendParticles(ParticleTypes.COMPOSTER, mouth.x, mouth.y - 0.8D, mouth.z, 3,
                1.0D, 0.1D, 1.0D, 0.002D);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TICKS_KEY, this.ticks);
        tag.putDouble(TOUCH_KEY + "X", this.touch.x);
        tag.putDouble(TOUCH_KEY + "Y", this.touch.y);
        tag.putDouble(TOUCH_KEY + "Z", this.touch.z);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.ticks = tag.contains(TICKS_KEY) ? tag.getInt(TICKS_KEY) : -1;
        this.touch = new Vec3(tag.getDouble(TOUCH_KEY + "X"), tag.getDouble(TOUCH_KEY + "Y"),
                tag.getDouble(TOUCH_KEY + "Z"));
        // Whatever had already gone stays gone: the survey re-runs and simply finds less door.
        this.surveyed = false;
        this.leafTaken = 0;
    }
}
