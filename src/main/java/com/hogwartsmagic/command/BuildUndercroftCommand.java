package com.hogwartsmagic.command;

import com.hogwartsmagic.worldgen.GringottsSchematic;
import com.hogwartsmagic.worldgen.VillageBankTracker;
import com.hogwartsmagic.worldgen.undercroft.CartTrack;
import com.hogwartsmagic.worldgen.undercroft.GringottsLandmarks;
import com.hogwartsmagic.worldgen.undercroft.UndercroftBuilder;
import com.hogwartsmagic.worldgen.undercroft.UndercroftTracker;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Digs the undercroft under a Gringotts branch, or reports on one already dug.
 *
 * <p>With no argument it finds the nearest known branch and works out where its rotunda island is,
 * which is the same route the automatic build takes. So what this exercises is the real path rather
 * than a shortcut around it.
 */
public final class BuildUndercroftCommand {

    private static final String[] STAGES = {"pending", "carving", "complete", "refused"};

    private BuildUndercroftCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("buildundercroft")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> run(ctx, null))
                .then(Commands.argument("station", BlockPosArgument.blockPos())
                        .executes(ctx -> run(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "station"))))
                .then(Commands.literal("status").executes(BuildUndercroftCommand::status))
                .then(Commands.literal("verify").executes(BuildUndercroftCommand::verify))
                .then(Commands.literal("redig").executes(BuildUndercroftCommand::redig)));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, @Nullable BlockPos explicit) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();

        BlockPos station = explicit != null ? explicit : stationOfNearestBank(source);
        if (station == null) {
            source.sendFailure(Component.literal(
                    "No Gringotts branch known nearby. Build one with /buildbank first, or name a"
                            + " station position."));
            return 0;
        }

        int depth = UndercroftBuilder.depthFor(level, station);
        if (depth < 0) {
            source.sendFailure(Component.literal(
                    "There is not enough rock under " + station.toShortString()
                            + " for an undercroft."));
            return 0;
        }
        if (UndercroftBuilder.isBusy()) {
            source.sendFailure(Component.literal("An undercroft is already being dug."));
            return 0;
        }
        if (UndercroftBuilder.request(level, station) == null) {
            source.sendFailure(Component.literal(
                    "Could not start: the footprint is not all loaded. Stand nearer and retry."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                        "Digging the undercroft under " + station.toShortString() + ", "
                                + depth + " blocks deep. Give it a few seconds.")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), true);
        return 1;
    }

    /**
     * Forgets the nearest undercroft so it can be dug again.
     *
     * <p>Needed because the carve is versioned in practice if not in name: change the layout or the
     * frame and every undercroft already in the ground is the old shape, while the tracker still
     * says complete and refuses to touch it. This does not undo the old carve - the new one is
     * written straight over it.
     */
    private static int redig(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        BlockPos from = BlockPos.containing(source.getPosition());
        UndercroftTracker.Undercroft nearest = null;
        double best = Double.MAX_VALUE;
        for (UndercroftTracker.Undercroft candidate : UndercroftTracker.get(level).all()) {
            double d = candidate.station.distSqr(from);
            if (d < best) {
                best = d;
                nearest = candidate;
            }
        }
        if (nearest == null) {
            source.sendFailure(Component.literal("No undercroft recorded in this dimension."));
            return 0;
        }
        final BlockPos station = nearest.station;
        nearest.stage = UndercroftTracker.PENDING;
        nearest.workItem = 0;
        nearest.cursor = 0L;
        UndercroftTracker.get(level).setDirty();
        if (UndercroftBuilder.request(level, station) == null) {
            source.sendFailure(Component.literal(
                    "Marked for re-digging, but could not start yet. Stand nearer and retry."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                        "Re-digging the undercroft under " + station.toShortString())
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), true);
        return 1;
    }

    /** The rotunda island of the nearest recorded branch, in world coordinates. */
    @Nullable
    private static BlockPos stationOfNearestBank(CommandSourceStack source) {
        GringottsSchematic schem = GringottsSchematic.get();
        if (schem == null) {
            return null;
        }
        GringottsLandmarks marks = GringottsLandmarks.of(schem);
        if (marks == null) {
            return null;
        }
        BlockPos locator = VillageBankTracker.get(source.getLevel())
                .findClosest(BlockPos.containing(source.getPosition()));
        if (locator == null) {
            return null;
        }
        // The tracker records the locator, which is the paste anchor offset to the footprint centre.
        BlockPos anchor = new BlockPos(locator.getX() - schem.width() / 2, locator.getY(),
                locator.getZ() - schem.length() / 2);
        return anchor.offset(marks.islandX, marks.islandY, marks.islandZ);
    }

    /**
     * Walks the line in the WORLD and checks the cart could actually get down it.
     *
     * <p>The dump audit checks the carve in memory, which proves the geometry but not the
     * placement: it says nothing about a chunk that was not resident when its turn came, a cell
     * some other feature had already claimed, or a job that stopped halfway and was recorded as
     * finished. This reads the blocks back out of the level.
     */
    private static int verify(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        UndercroftTracker.Undercroft nearest = null;
        BlockPos from = BlockPos.containing(source.getPosition());
        double best = Double.MAX_VALUE;
        for (UndercroftTracker.Undercroft candidate : UndercroftTracker.get(level).all()) {
            double d = candidate.station.distSqr(from);
            if (d < best) {
                best = d;
                nearest = candidate;
            }
        }
        final UndercroftTracker.Undercroft site = nearest;
        if (site == null) {
            source.sendFailure(Component.literal("No undercroft recorded in this dimension."));
            return 0;
        }

        BlockPos foot = new BlockPos(site.station.getX(),
                site.station.getY() - UndercroftBuilder.SHAFT_DROP, site.station.getZ());
        CartTrack track = CartTrack.build(CartTrack.originForStart(foot, site.depth), site.depth);

        int blocked = 0;
        int unsupported = 0;
        double firstBlocked = -1.0D;
        double firstUnsupported = -1.0D;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (double s = 0.0D; s <= track.length(); s += 0.5D) {
            Vec3 centre = track.positionAt(s);
            Vec3 up = track.upAt(s);
            for (int v = 0; v <= 2; v++) {
                cursor.set(Math.floor(centre.x + up.x * v), Math.floor(centre.y + up.y * v),
                        Math.floor(centre.z + up.z * v));
                if (!level.getBlockState(cursor).isAir()) {
                    blocked++;
                    if (firstBlocked < 0) {
                        firstBlocked = s;
                    }
                    break;
                }
            }
            boolean supported = false;
            for (double d = 1.6D; d <= 3.2D && !supported; d += 0.8D) {
                cursor.set(Math.floor(centre.x - up.x * d), Math.floor(centre.y - up.y * d),
                        Math.floor(centre.z - up.z * d));
                supported = !level.getBlockState(cursor).isAir();
            }
            if (!supported) {
                unsupported++;
                if (firstUnsupported < 0) {
                    firstUnsupported = s;
                }
            }
        }

        final int b = blocked;
        final int u = unsupported;
        final double fb = firstBlocked;
        final double fu = firstUnsupported;
        source.sendSuccess(() -> Component.literal(String.format(
                        "verified %.0f blocks of line under %s", track.length(),
                        site.station.toShortString()))
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), false);
        if (b == 0 && u == 0) {
            source.sendSuccess(() -> Component.literal("  rideable end to end, in the world")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal(String.format(
                        "  %d blocked (first s=%.0f), %d unsupported (first s=%.0f)", b, fb, u, fu))
                .withStyle(Style.EMPTY.withColor(ChatFormatting.RED)), false);
        return 0;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        UndercroftTracker tracker = UndercroftTracker.get(source.getLevel());
        if (tracker.all().isEmpty()) {
            source.sendFailure(Component.literal("No undercrofts recorded in this dimension."));
            return 0;
        }
        for (UndercroftTracker.Undercroft site : tracker.all()) {
            source.sendSuccess(() -> Component.literal(String.format(
                            "  %s  depth %d  %s  (item %d, cursor %d)",
                            site.station.toShortString(), site.depth,
                            STAGES[Math.min(site.stage, STAGES.length - 1)],
                            site.workItem, site.cursor))
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)), false);
        }
        return tracker.all().size();
    }
}
