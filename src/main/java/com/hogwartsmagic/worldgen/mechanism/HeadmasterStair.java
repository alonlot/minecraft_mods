package com.hogwartsmagic.worldgen.mechanism;

import com.hogwartsmagic.worldgen.MiniHogwartsAnchors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The stone gargoyle and the stair behind it, for one castle. Closed (as the castle generates), the gargoyle stands in
 * the stair's door, the treads are sunk into the gallery floor and planks cover the opening in the office floor. Said
 * the password near it, the gargoyle spins and leaps aside, the treads rise out of the floor one after another from the
 * bottom, and the cover opens. Open, it stays up while anyone stands on it, and sinks again (cover first, then the
 * treads from the top, then the gargoyle back into the door) once nobody has used it for a while. Somebody in the office
 * who walks to the top of the stair makes it rise without a password, so nobody is shut in.
 */
final class HeadmasterStair {
    enum Phase { CLOSED, OPENING, OPEN, CLOSING }

    static final int GARGOYLE_TICKS = 30;
    static final int TREAD_START = 14;
    static final int TREAD_GAP = 9;
    static final int TREAD_TICKS = 16;
    /** How long it stays up with nobody on it. */
    static final int OPEN_TICKS = 20 * 25;

    final ServerLevel level;
    final BlockPos anchor;
    final MiniHogwartsAnchors.HeadmasterStair spec;

    Phase phase = Phase.CLOSED;
    int t;
    long openSince;
    private final List<Display.BlockDisplay> gargoyle = new ArrayList<>();
    private final List<List<Display.BlockDisplay>> treads = new ArrayList<>();
    private final Set<UUID> ids = new HashSet<>();

    HeadmasterStair(ServerLevel level, BlockPos anchor, MiniHogwartsAnchors.HeadmasterStair spec) {
        this.level = level;
        this.anchor = anchor;
        this.spec = spec;
        for (int i = 0; i < spec.treads().size(); i++) {
            treads.add(new ArrayList<>());
        }
    }

    boolean busy() {
        return phase == Phase.OPENING || phase == Phase.CLOSING;
    }

    boolean isOpen() {
        return phase == Phase.OPEN || phase == Phase.OPENING;
    }

    /** The world cell of the gargoyle's middle (where the password is listened for). */
    Vec3 listenPoint() {
        return Vec3.atBottomCenterOf(anchor.offset(spec.listen())).add(0.0D, 1.0D, 0.0D);
    }

    Vec3 officeCallPoint() {
        return Vec3.atBottomCenterOf(anchor.offset(spec.officeCall()));
    }

    /** Every cell this stair writes, in both states. */
    List<MiniHogwartsAnchors.Placed> all(boolean open) {
        List<MiniHogwartsAnchors.Placed> out = new ArrayList<>(open ? spec.gargoyle().open() : spec.gargoyle().closed());
        for (MiniHogwartsAnchors.Part p : spec.treads()) {
            out.addAll(open ? p.open() : p.closed());
        }
        out.addAll(open ? spec.cover().open() : spec.cover().closed());
        return out;
    }

    /** Jump straight to a state (after a restart mid-move, or when the server stops). */
    void settle(boolean open) {
        MechanismBlocks.discard(level, gargoyle);
        for (List<Display.BlockDisplay> l : treads) {
            MechanismBlocks.discard(level, l);
        }
        MechanismBlocks.discardIds(level, ids);
        MechanismBlocks.write(level, anchor, all(open));
        phase = open ? Phase.OPEN : Phase.CLOSED;
        openSince = level.getGameTime();
        MechanismData.get(level).setStair(anchor, open ? 1 : 0, false);
    }

    /** Start rising; false when it is not closed. */
    boolean raise() {
        if (phase != Phase.CLOSED) {
            if (isOpen()) {
                openSince = level.getGameTime();
            }
            return false;
        }
        phase = Phase.OPENING;
        t = 0;
        MechanismData.get(level).setStair(anchor, 1, true);
        BlockPos g = anchor.offset(spec.listen());
        sound(g, SoundEvents.PISTON_CONTRACT, 1.0F, 0.5F);
        sound(g, SoundEvents.GRINDSTONE_USE, 1.0F, 0.6F);
        startGargoyle(true);
        return true;
    }

    private void startClosing() {
        phase = Phase.CLOSING;
        t = 0;
        MechanismData.get(level).setStair(anchor, 0, true);
        MechanismBlocks.write(level, anchor, spec.cover().closed());
        sound(anchor.offset(spec.officeCall()), SoundEvents.WOODEN_TRAPDOOR_CLOSE, 1.0F, 0.6F);
    }

    void tick(List<ServerPlayer> near) {
        switch (phase) {
            case CLOSED -> {
                Vec3 call = officeCallPoint();
                for (ServerPlayer p : near) {
                    Vec3 at = p.position();
                    if (Math.abs(at.y - call.y) < 1.5D && horizontal(at, call) < 2.2D) {
                        if (raise()) {
                            p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                                    "message.hogwartsmagic.headmaster_stair.leaving"), true);
                        }
                        break;
                    }
                }
            }
            case OPENING -> tickOpening();
            case OPEN -> {
                if (level.getGameTime() - openSince > OPEN_TICKS && !occupied(near)) {
                    startClosing();
                } else if (occupied(near)) {
                    openSince = level.getGameTime();
                }
            }
            case CLOSING -> tickClosing();
        }
    }

    private boolean occupied(List<ServerPlayer> near) {
        for (ServerPlayer p : near) {
            AABB box = p.getBoundingBox().inflate(0.3D);
            for (BlockPos r : spec.riders()) {
                BlockPos w = anchor.offset(r);
                if (box.intersects(new AABB(w.getX(), w.getY(), w.getZ(), w.getX() + 1, w.getY() + 2, w.getZ() + 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    // ── opening ──────────────────────────────────────────────────────────────────────────

    private void tickOpening() {
        int n = spec.treads().size();
        if (t <= GARGOYLE_TICKS) {
            moveGargoyle(true, t / (double) GARGOYLE_TICKS);
        }
        if (t == GARGOYLE_TICKS) {
            MechanismBlocks.discard(level, gargoyle);
            MechanismBlocks.write(level, anchor, spec.gargoyle().open());
            sound(anchor.offset(spec.listen()), SoundEvents.STONE_PLACE, 1.0F, 0.5F);
        }
        for (int i = 0; i < n; i++) {
            int start = TREAD_START + TREAD_GAP * i;
            if (t == start) {
                startTread(i, true);
            }
            if (t >= start && t <= start + TREAD_TICKS) {
                moveTread(i, true, (t - start) / (double) TREAD_TICKS);
            }
            if (t == start + TREAD_TICKS) {
                MechanismBlocks.discard(level, treads.get(i));
                MechanismBlocks.write(level, anchor, spec.treads().get(i).open());
                BlockPos top = anchor.offset(topOf(spec.treads().get(i).open()));
                sound(top, SoundEvents.STONE_PLACE, 0.8F, 0.7F + 0.05F * i);
            }
        }
        int end = TREAD_START + TREAD_GAP * (n - 1) + TREAD_TICKS + 3;
        if (t == end) {
            MechanismBlocks.write(level, anchor, spec.cover().open());
            sound(anchor.offset(spec.officeCall()), SoundEvents.WOODEN_TRAPDOOR_OPEN, 1.0F, 0.6F);
        }
        if (t >= end + 1) {
            phase = Phase.OPEN;
            openSince = level.getGameTime();
            MechanismData.get(level).setStair(anchor, 1, false);
            return;
        }
        t++;
    }

    // ── closing ──────────────────────────────────────────────────────────────────────────

    private void tickClosing() {
        int n = spec.treads().size();
        for (int k = 0; k < n; k++) {
            int i = n - 1 - k;                                   // top tread first
            int start = 4 + TREAD_GAP * k;
            if (t == start) {
                MechanismBlocks.write(level, anchor, spec.treads().get(i).closed());
                startTread(i, false);
                BlockPos top = anchor.offset(topOf(spec.treads().get(i).open()));
                sound(top, SoundEvents.GRINDSTONE_USE, 0.7F, 0.5F);
            }
            if (t >= start && t <= start + TREAD_TICKS) {
                moveTread(i, false, (t - start) / (double) TREAD_TICKS);
            }
            if (t == start + TREAD_TICKS) {
                MechanismBlocks.discard(level, treads.get(i));
            }
        }
        int gStart = 4 + TREAD_GAP * (n - 1) + TREAD_TICKS + 2;
        if (t == gStart) {
            MechanismBlocks.write(level, anchor, gargoyleCells(spec.gargoyle().open(), true));
            startGargoyle(false);
            sound(anchor.offset(spec.listen()), SoundEvents.PISTON_EXTEND, 1.0F, 0.5F);
        }
        if (t >= gStart && t <= gStart + GARGOYLE_TICKS) {
            moveGargoyle(false, (t - gStart) / (double) GARGOYLE_TICKS);
        }
        if (t == gStart + GARGOYLE_TICKS) {
            MechanismBlocks.discard(level, gargoyle);
            MechanismBlocks.write(level, anchor, all(false));
            sound(anchor.offset(spec.listen()), SoundEvents.STONE_PLACE, 1.0F, 0.5F);
            phase = Phase.CLOSED;
            MechanismData.get(level).setStair(anchor, 0, false);
            return;
        }
        t++;
    }

    // ── the gargoyle ─────────────────────────────────────────────────────────────────────

    /** The cells of {@code entries} as empty (cave air): the gargoyle's blocks taken away while its display moves. */
    private static List<MiniHogwartsAnchors.Placed> gargoyleCells(List<MiniHogwartsAnchors.Placed> entries, boolean empty) {
        List<MiniHogwartsAnchors.Placed> out = new ArrayList<>();
        for (MiniHogwartsAnchors.Placed p : entries) {
            out.add(empty ? new MiniHogwartsAnchors.Placed(p.at(), "minecraft:cave_air") : p);
        }
        return out;
    }

    /** The gargoyle's blocks where it stands closed (bottom first). */
    private List<MiniHogwartsAnchors.Placed> closedGargoyle() {
        List<MiniHogwartsAnchors.Placed> out = new ArrayList<>();
        for (MiniHogwartsAnchors.Placed p : spec.gargoyle().closed()) {
            if (!MechanismBlocks.isEmpty(MechanismBlocks.state(p.state()))) {
                out.add(p);
            }
        }
        out.sort((a, b) -> Integer.compare(a.at().getY(), b.at().getY()));
        return out;
    }

    private void startGargoyle(boolean opening) {
        MechanismBlocks.discard(level, gargoyle);
        if (opening) {
            MechanismBlocks.write(level, anchor, gargoyleCells(spec.gargoyle().closed(), true));
        }
        BlockPos s = opening ? BlockPos.ZERO : spec.slide();
        BlockPos light = brightestBeside(anchor.offset(spec.listen()));
        for (MiniHogwartsAnchors.Placed p : closedGargoyle()) {
            BlockPos w = anchor.offset(p.at()).offset(s);
            Display.BlockDisplay d = MechanismBlocks.spawn(level, MechanismBlocks.state(p.state()), w.getX() + 0.5D,
                    w.getY(), w.getZ() + 0.5D, opening ? 0.0F : 360.0F, -0.5F, 0.0F, -0.5F, light, 2);
            if (d != null) {
                gargoyle.add(d);
                ids.add(d.getUUID());
            }
        }
        BlockPos g = anchor.offset(spec.listen());
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()),
                g.getX() + 0.5D, g.getY() + 0.2D, g.getZ() + 0.5D, 24, 0.5D, 0.2D, 0.5D, 0.05D);
    }

    /** u from 0 to 1 along the leap: from the door to the side (opening) or back (closing), spinning once. */
    private void moveGargoyle(boolean opening, double u) {
        double e = MechanismBlocks.ease(opening ? u : 1.0D - u);
        double hop = Math.sin(Math.PI * u) * 0.4D;
        BlockPos s = spec.slide();
        List<MiniHogwartsAnchors.Placed> blocks = closedGargoyle();
        for (int i = 0; i < gargoyle.size() && i < blocks.size(); i++) {
            BlockPos w = anchor.offset(blocks.get(i).at());
            MechanismBlocks.move(gargoyle.get(i), w.getX() + 0.5D + s.getX() * e, w.getY() + hop + s.getY() * e,
                    w.getZ() + 0.5D + s.getZ() * e, (float) (360.0D * e));
        }
        if ((int) (u * GARGOYLE_TICKS) % 8 == 0) {
            sound(anchor.offset(spec.listen()), SoundEvents.GRINDSTONE_USE, 0.6F, 0.5F);
        }
    }

    // ── the treads ───────────────────────────────────────────────────────────────────────

    private static BlockPos topOf(List<MiniHogwartsAnchors.Placed> col) {
        BlockPos top = col.get(0).at();
        for (MiniHogwartsAnchors.Placed p : col) {
            if (p.at().getY() > top.getY()) {
                top = p.at();
            }
        }
        return top;
    }

    private void startTread(int i, boolean opening) {
        List<Display.BlockDisplay> list = treads.get(i);
        MechanismBlocks.discard(level, list);
        List<MiniHogwartsAnchors.Placed> col = spec.treads().get(i).open();
        BlockPos light = anchor.offset(topOf(col)).above();
        for (MiniHogwartsAnchors.Placed p : col) {
            BlockState st = MechanismBlocks.state(p.state());
            if (MechanismBlocks.isEmpty(st)) {
                continue;
            }
            BlockPos w = anchor.offset(p.at());
            int h = col.size();
            Display.BlockDisplay d = MechanismBlocks.spawn(level, st, w.getX(), w.getY() - (opening ? h : 0), w.getZ(),
                    0.0F, 0.0F, 0.0F, 0.0F, light, 2);
            if (d != null) {
                list.add(d);
                ids.add(d.getUUID());
            }
        }
        BlockPos foot = anchor.offset(col.get(0).at());
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()),
                foot.getX() + 0.5D, foot.getY() + 0.1D, foot.getZ() + 0.5D, 12, 0.35D, 0.1D, 0.35D, 0.05D);
        sound(foot, SoundEvents.GRINDSTONE_USE, 0.7F, 0.55F + 0.05F * i);
    }

    private void moveTread(int i, boolean opening, double u) {
        List<MiniHogwartsAnchors.Placed> col = spec.treads().get(i).open();
        int h = col.size();
        double rise = h * MechanismBlocks.ease(opening ? u : 1.0D - u);
        List<Display.BlockDisplay> list = treads.get(i);
        int k = 0;
        for (MiniHogwartsAnchors.Placed p : col) {
            if (MechanismBlocks.isEmpty(MechanismBlocks.state(p.state()))) {
                continue;
            }
            if (k >= list.size()) {
                break;
            }
            BlockPos w = anchor.offset(p.at());
            MechanismBlocks.move(list.get(k++), w.getX(), w.getY() - h + rise, w.getZ(), 0.0F);
        }
    }

    /** The open cell beside {@code pos} with the most block light (the displays are lit as it is). */
    private BlockPos brightestBeside(BlockPos pos) {
        BlockPos best = pos.above();
        int most = -1;
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos n = pos.relative(d).above();
            if (!level.getBlockState(n).isAir()) {
                continue;
            }
            int l = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, n);
            if (l > most) {
                most = l;
                best = n;
            }
        }
        return best;
    }

    private void sound(BlockPos pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
    }

    static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
