package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.ChamberDoorBlock;
import com.hogwartsmagic.block.ChamberDoorLeafBlock;
import com.hogwartsmagic.block.entity.ChamberDoorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * The Chamber door opens as one moving piece, not by deleting blocks: Parseltongue hands the leaf to
 * the renderer (invisible, still solid), the bolt heads leave the world, and at the end the doorway
 * is clear but the leaf blocks are still there, so the renderer keeps the swung door drawn.
 *
 * <p>Built the way {@code round_door} in the structure script builds it, smaller: a disc of leaf
 * three deep, serpent bolts (a blackstone socket with a lightning rod one block proud), the lock dead
 * centre in the front layer. Once facing along Z and once along X, because the door's axes and which
 * way the hall lies are measured, and a measurement has to be checked in more than one rotation.
 */
@GameTestHolder(ExampleMod.MODID)
public final class ChamberDoorGameTests {
    private static final String EMPTY = "hogwartsmagic:gametest/empty9";
    private static final int R = 3;

    private ChamberDoorGameTests() {
    }

    private record Door(BlockPos lock, List<BlockPos> leaf, List<BlockPos> sockets, List<BlockPos> rods) {
    }

    /** Builds a shut door whose front faces {@code out}; the hall lies the other way. */
    private static Door build(GameTestHelper helper, Direction out) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(new BlockPos(4, 4, 4));
        Direction in = out.getOpposite();
        Direction acrossDir = out.getAxis() == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH;
        List<BlockPos> leaf = new ArrayList<>();
        List<BlockPos> sockets = new ArrayList<>();
        List<BlockPos> rods = new ArrayList<>();
        for (int a = -R; a <= R; a++) {
            for (int y = -R; y <= R; y++) {
                if (Math.hypot(a, y) > R) {
                    continue;
                }
                for (int d = 0; d < 3; d++) {
                    BlockPos p = centre.relative(acrossDir, a).above(y).relative(in, d);
                    level.setBlock(p, ExampleMod.CHAMBER_DOOR_LEAF.get().defaultBlockState(), 3);
                }
            }
        }
        for (int i = 0; i < 5; i++) {
            double ang = 2 * Math.PI * i / 5.0 + 0.3;
            BlockPos socket = centre.relative(acrossDir, (int) Math.round((R - 0.6) * Math.cos(ang)))
                    .above((int) Math.round((R - 0.6) * Math.sin(ang)));
            if (sockets.contains(socket) || socket.equals(centre)) {
                continue;
            }
            level.setBlock(socket, Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 3);
            BlockPos rod = socket.relative(out);
            level.setBlock(rod, Blocks.LIGHTNING_ROD.defaultBlockState().setValue(LightningRodBlock.FACING, out), 3);
            sockets.add(socket);
            rods.add(rod);
        }
        level.setBlock(centre, ExampleMod.CHAMBER_DOOR.get().defaultBlockState(), 3);
        // What is still leaf now that the sockets and the lock have taken their cells.
        for (int a = -R; a <= R; a++) {
            for (int y = -R; y <= R; y++) {
                for (int d = 0; d < 3; d++) {
                    BlockPos p = centre.relative(acrossDir, a).above(y).relative(in, d);
                    if (level.getBlockState(p).is(ExampleMod.CHAMBER_DOOR_LEAF.get())) {
                        leaf.add(p);
                    }
                }
            }
        }
        return new Door(centre, leaf, sockets, rods);
    }

    private static int phase(BlockState state) {
        return state.hasProperty(ChamberDoorLeafBlock.PHASE) ? state.getValue(ChamberDoorLeafBlock.PHASE) : -1;
    }

    private static void run(GameTestHelper helper, Direction out) {
        ServerLevel level = helper.getLevel();
        Door door = build(helper, out);
        helper.assertTrue(!door.rods().isEmpty(), "the test door has bolts");
        helper.assertTrue(!ChamberDoorBlock.speakTo(level, door.lock().above(20)), "nothing there to speak to");
        helper.assertTrue(ChamberDoorBlock.speakTo(level, door.lock()), "Parseltongue starts the door");
        helper.assertTrue(!ChamberDoorBlock.speakTo(level, door.lock()), "a moving door does not start twice");

        ChamberDoorBlockEntity be = (ChamberDoorBlockEntity) level.getBlockEntity(door.lock());
        helper.assertTrue(be != null, "the lock has its block entity");
        helper.assertTrue(be.normalAxis() == (out.getAxis() == Direction.Axis.X ? 0 : 2),
                "the door faces along " + out.getAxis() + ", measured axis " + be.normalAxis());
        helper.assertTrue(be.outward() == out, "bolts point " + out + ", measured " + be.outward());
        helper.assertTrue(be.rodPositions().size() == door.rods().size(), "every bolt was found");
        helper.assertTrue(be.studPositions().size() == door.sockets().size(), "every socket was found");

        for (BlockPos rod : door.rods()) {
            helper.assertTrue(level.getBlockState(rod).isAir(), "bolt heads leave the world for the renderer");
        }
        for (BlockPos p : door.sockets()) {
            helper.assertTrue(phase(level.getBlockState(p)) == ChamberDoorLeafBlock.MOVING,
                    "a socket becomes moving leaf, so it leaves with the door");
        }
        for (BlockPos p : door.leaf()) {
            BlockState s = level.getBlockState(p);
            helper.assertTrue(phase(s) == ChamberDoorLeafBlock.MOVING, "the leaf is handed to the renderer");
            helper.assertTrue(!s.getCollisionShape(level, p).isEmpty(), "and is still solid while it moves");
        }
        double lastFire = -1;
        for (BlockPos rod : be.rodPositions()) {
            double fire = be.boltFireTick(rod);
            helper.assertTrue(fire >= lastFire, "bolts fire in order round the rim");
            helper.assertTrue(fire >= ChamberDoorBlockEntity.AWAKEN
                    && fire < ChamberDoorBlockEntity.AWAKEN + ChamberDoorBlockEntity.RIM, "every bolt fires during the serpent");
            lastFire = fire;
        }

        helper.runAfterDelay(ChamberDoorBlockEntity.TOTAL + 2, () -> {
            helper.assertTrue(be.ticks() == ChamberDoorBlockEntity.TOTAL, "the door ends open, got " + be.ticks());
            BlockState lock = level.getBlockState(door.lock());
            helper.assertTrue(lock.is(ExampleMod.CHAMBER_DOOR.get()), "the lock stays, to keep drawing the door");
            helper.assertTrue(phase(lock) == ChamberDoorLeafBlock.PARTED, "the lock is parted");
            helper.assertTrue(lock.getCollisionShape(level, door.lock()).isEmpty(), "and is not in the way");
            for (List<BlockPos> cells : List.of(door.leaf(), door.sockets())) {
                for (BlockPos p : cells) {
                    BlockState s = level.getBlockState(p);
                    helper.assertTrue(phase(s) == ChamberDoorLeafBlock.PARTED, "every cell ends parted");
                    helper.assertTrue(s.getCollisionShape(level, p).isEmpty(), "the doorway is clear");
                }
            }
            helper.assertTrue(!ChamberDoorBlock.speakTo(level, door.lock()), "an open door does not open again");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "chamber_door_z", timeoutTicks = 400)
    public static void opensFacingNorth(GameTestHelper helper) {
        run(helper, Direction.NORTH);
    }

    @GameTest(template = EMPTY, batch = "chamber_door_x", timeoutTicks = 400)
    public static void opensFacingEast(GameTestHelper helper) {
        run(helper, Direction.EAST);
    }
}
