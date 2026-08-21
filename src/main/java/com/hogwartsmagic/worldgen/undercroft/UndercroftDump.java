package com.hogwartsmagic.worldgen.undercroft;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a carve into memory and writes it out as a structure template, purely so it can be looked at.
 *
 * <p>Every other build in this repo is authored as a {@code .nbt} and therefore renders for free.
 * The undercroft is generated procedurally, so without this there is nothing for
 * {@code render_structure.py} or the deepslate viewer to open, and the first sight of a 700-block
 * descent would be from inside it, in game, after a full client launch. The project's own history
 * says that is how you ship a blizzard of cobwebs.
 *
 * <p>The output goes under {@code run/undercroft_dumps/}. Copy it next to the viewer's other
 * templates to look at it:
 * <pre>
 *   cp run/undercroft_dumps/corridor.nbt .claude/skills/rendering-minecraft-structures/textured/build/
 *   http://localhost:8731/?nbt=corridor.nbt&amp;cut=z
 * </pre>
 */
public final class UndercroftDump {

    /** 1.21's data version, so the file is not treated as needing an upgrade. */
    private static final int DATA_VERSION = 3953;

    private UndercroftDump() {
    }

    public record Result(Path path, int blocks, int width, int height, int length,
                         List<String> problems) {
    }

    /**
     * Carves at the origin into memory and writes the template.
     *
     * <p>The station is placed at a nominal height rather than a real one: this dump is for judging
     * shape, and shape does not depend on where in the world it sits.
     */
    public static Result dump(Path outDir, String name, int depth) throws IOException {
        BlockPos station = new BlockPos(0, depth + 8, 0);
        CartTrack track = CartTrack.build(station, depth);
        UndercroftPalette palette = new UndercroftPalette(station.getY());
        CarveSink.Template sink = new CarveSink.Template(0, station.getY() + 12);

        // Same order the world build uses: void first, then the corridor lines itself against it.
        ChasmCarver chasm = new ChasmCarver(track, palette);
        for (int step = 0; step < chasm.stepCount(); step++) {
            chasm.carveStep(sink, step);
        }

        CorridorCarver carver = new CorridorCarver(track, palette);
        for (int pass = 0; pass < CorridorCarver.PASSES; pass++) {
            for (int step = 0; step < carver.stepCount(); step++) {
                carver.carveStep(sink, pass, step);
            }
        }

        VaultCarver vaults = new VaultCarver(track, palette);
        for (int step = 0; step < vaults.stepCount(); step++) {
            vaults.carveStep(sink, step);
        }

        List<String> problems = audit(sink, track, carver);
        return write(outDir, name, sink.blocks(), problems);
    }

    /**
     * Walks the finished carve along the line the cart will actually take and checks it is
     * rideable.
     *
     * <p>A render cannot answer this. A corridor with a pillar left standing in it, or a stretch
     * where the sweep stepped too far and left the floor unpaved, looks perfectly fine from
     * outside and is a wall at forty blocks a second from inside. This is the check the pictures
     * cannot do, so it runs on every dump.
     */
    private static List<String> audit(CarveSink.Template sink, CartTrack track,
                                      CorridorCarver carver) {
        List<String> problems = new ArrayList<>();
        List<Double> blocked = new ArrayList<>();
        List<String> examples = new ArrayList<>();
        List<Double> unsupported = new ArrayList<>();

        for (double s = 0.0D; s <= track.length(); s += 0.5D) {
            Vec3 centre = track.positionAt(s);
            Vec3 up = track.upAt(s);

            // Head room: the cart plus a rider is about three blocks of it.
            for (int v = 0; v <= 2; v++) {
                int x = (int) Math.floor(centre.x + up.x * v);
                int y = (int) Math.floor(centre.y + up.y * v);
                int z = (int) Math.floor(centre.z + up.z * v);
                if (!sink.isPassable(x, y, z)) {
                    blocked.add(s);
                    if (examples.size() < 4) {
                        BlockState what = sink.at(x, y, z);
                        // Where the offending block is, and which OTHER stretch of the line is
                        // closest to it -- an intruding deck or parapet always belongs to some
                        // other pass of the corridor, and knowing which one is the whole answer.
                        double owner = track.project(new Vec3(x + 0.5D, y + 0.5D, z + 0.5D), 64.0D);
                        Vec3 ownerPos = track.positionAt(owner);
                        Vec3 ownerUp = track.upAt(owner);
                        examples.add(String.format(
                                "s=%.1f centre(%.1f,%.1f,%.1f) up(%.2f,%.2f,%.2f) v=%d -> block(%d,%d,%d)=%s"
                                        + " | owner s=%.1f centre(%.1f,%.1f,%.1f) up(%.2f,%.2f,%.2f)",
                                s, centre.x, centre.y, centre.z, up.x, up.y, up.z, v, x, y, z,
                                what == null ? "uncarved" : what.getBlock().getName().getString(),
                                owner, ownerPos.x, ownerPos.y, ownerPos.z,
                                ownerUp.x, ownerUp.y, ownerUp.z));
                    }
                    break;
                }
            }

            // Something to run on. Checked over two courses rather than one exact block: the
            // deck is laid two thick and the carve classifies a block by where its CENTRE falls,
            // so a block that contains the point 2.0 below the line can have its centre as high
            // as 1.1 below it and legitimately be air. Probing a single depth was comparing two
            // different reference points and reported 46 holes in a deck that is not holed.
            boolean supported = false;
            for (double d = 1.6D; d <= 3.2D && !supported; d += 0.8D) {
                int dx = (int) Math.floor(centre.x - up.x * d);
                int dy = (int) Math.floor(centre.y - up.y * d);
                int dz = (int) Math.floor(centre.z - up.z * d);
                supported = !sink.isAir(dx, dy, dz);
            }
            if (!supported) {
                unsupported.add(s);
            }
        }

        // Report WHERE, not just how many. A count sends you guessing at which stretch is at
        // fault; a list of arc lengths points straight at the segment.
        if (!blocked.isEmpty()) {
            problems.add(String.format("%d sample(s) with something in the cart's way, at s=%s%s",
                    blocked.size(), where(blocked), profiles(carver, blocked)));
            for (String example : examples) {
                problems.add("    " + example);
            }
        }
        if (!unsupported.isEmpty()) {
            problems.add(String.format("%d sample(s) with no deck under them, at s=%s%s",
                    unsupported.size(), where(unsupported), profiles(carver, unsupported)));
        }
        return problems;
    }

    private static final String[] PROFILE_NAMES = {"bore", "ledge", "trestle", "gallery"};

    /** Which cross-sections the failures happen in - usually the whole answer. */
    private static String profiles(CorridorCarver carver, List<Double> samples) {
        java.util.Set<String> seen = new java.util.TreeSet<>();
        for (double s : samples) {
            seen.add(PROFILE_NAMES[carver.profileAt(s)]);
        }
        return "  (in: " + String.join(", ", seen) + ")";
    }

    /** Collapses a list of arc lengths into runs, so 40 adjacent failures read as one place. */
    private static String where(List<Double> samples) {
        StringBuilder out = new StringBuilder();
        int runs = 0;
        double runStart = samples.get(0);
        double previous = samples.get(0);
        for (int i = 1; i <= samples.size(); i++) {
            double s = i < samples.size() ? samples.get(i) : Double.MAX_VALUE;
            if (s - previous > 3.0D) {
                if (runs++ < 6) {
                    out.append(out.length() > 0 ? ", " : "");
                    out.append(runStart == previous ? String.format("%.0f", runStart)
                            : String.format("%.0f-%.0f", runStart, previous));
                }
                runStart = s;
            }
            previous = s;
        }
        if (runs > 6) {
            out.append(" and ").append(runs - 6).append(" more");
        }
        return out.toString();
    }

    private static Result write(Path outDir, String name, Map<BlockPos, BlockState> blocks,
                                List<String> problems) throws IOException {
        if (blocks.isEmpty()) {
            throw new IOException("carve produced no blocks");
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : blocks.keySet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        Map<BlockState, Integer> paletteIndex = new HashMap<>();
        List<BlockState> paletteOrder = new ArrayList<>();
        ListTag blockTags = new ListTag();

        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockState state = entry.getValue();
            Integer index = paletteIndex.get(state);
            if (index == null) {
                index = paletteOrder.size();
                paletteIndex.put(state, index);
                paletteOrder.add(state);
            }
            BlockPos pos = entry.getKey();
            ListTag posTag = new ListTag();
            posTag.add(IntTag.valueOf(pos.getX() - minX));
            posTag.add(IntTag.valueOf(pos.getY() - minY));
            posTag.add(IntTag.valueOf(pos.getZ() - minZ));
            CompoundTag blockTag = new CompoundTag();
            blockTag.put("pos", posTag);
            blockTag.putInt("state", index);
            blockTags.add(blockTag);
        }

        ListTag paletteTag = new ListTag();
        for (BlockState state : paletteOrder) {
            paletteTag.add(NbtUtils.writeBlockState(state));
        }

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int length = maxZ - minZ + 1;

        ListTag sizeTag = new ListTag();
        sizeTag.add(IntTag.valueOf(width));
        sizeTag.add(IntTag.valueOf(height));
        sizeTag.add(IntTag.valueOf(length));

        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", DATA_VERSION);
        root.put("size", sizeTag);
        root.put("palette", paletteTag);
        root.put("blocks", blockTags);
        root.put("entities", new ListTag());

        Files.createDirectories(outDir);
        Path path = outDir.resolve(name + ".nbt");
        try (OutputStream out = Files.newOutputStream(path)) {
            NbtIo.writeCompressed(root, out);
        }
        return new Result(path, blocks.size(), width, height, length, problems);
    }
}
