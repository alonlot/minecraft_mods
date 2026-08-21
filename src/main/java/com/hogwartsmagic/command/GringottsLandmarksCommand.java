package com.hogwartsmagic.command;

import com.hogwartsmagic.worldgen.GringottsSchematic;
import com.hogwartsmagic.worldgen.VillageBankTracker;
import com.hogwartsmagic.worldgen.undercroft.GringottsLandmarks;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;

/**
 * Prints the landmarks {@link GringottsLandmarks} derives from the Gringotts schematic, so the
 * numbers the undercroft is about to build against can be checked before anything is cut.
 *
 * <p>Deliberately read-only. The whole point of the landmark scan is that it is measured rather than
 * hard-coded, which is only reassuring if you can see what it measured.
 */
public final class GringottsLandmarksCommand {

    private GringottsLandmarksCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("gringottslandmarks")
                .requires(source -> source.hasPermission(2))
                .executes(GringottsLandmarksCommand::run));
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        GringottsSchematic schem = GringottsSchematic.get();
        if (schem == null) {
            source.sendFailure(Component.literal(
                    "The Gringotts schematic failed to load; nothing to measure."));
            return 0;
        }

        GringottsLandmarks marks = GringottsLandmarks.of(schem);
        if (marks == null) {
            source.sendFailure(Component.literal(
                    "Landmarks could not be derived from the schematic - see the log. The build has"
                            + " probably been re-extracted and moved."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Gringotts schematic "
                        + schem.width() + "x" + schem.height() + "x" + schem.length())
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), false);
        line(source, "station island", marks.islandX, marks.islandY, marks.islandZ);
        line(source, "rotunda interior", "x " + marks.minX + ".." + marks.maxX
                + ",  z " + marks.minZ + ".." + marks.maxZ);
        line(source, "ceiling", "apex y" + marks.apexY + ",  " + marks.headroom() + " of headroom");
        line(source, "gate in the bar screen",
                marks.gateX < 0 ? "not found" : "x" + marks.gateX);

        // And where that lands in this world, if a branch has actually been built.
        ServerLevel level = source.getLevel();
        BlockPos nearest = VillageBankTracker.get(level).findClosest(
                BlockPos.containing(source.getPosition()));
        if (nearest == null) {
            source.sendSuccess(() -> Component.literal(
                            "  (no branch built in this dimension yet - try /buildbank)")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY)), false);
            return 1;
        }

        // findClosest records the locator, which is the anchor offset to the footprint centre.
        BlockPos anchor = new BlockPos(nearest.getX() - schem.width() / 2, nearest.getY(),
                nearest.getZ() - schem.length() / 2);
        BlockPos island = anchor.offset(marks.islandX, marks.islandY, marks.islandZ);
        String tp = "/execute in " + level.dimension().location()
                + " run tp @s " + island.getX() + " " + (island.getY() + 1) + " " + island.getZ();
        source.sendSuccess(() -> Component.literal(
                        "  nearest branch puts the station island at "
                                + island.getX() + ", " + island.getY() + ", " + island.getZ())
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tp))), false);
        return 1;
    }

    private static void line(CommandSourceStack source, String label, int x, int y, int z) {
        line(source, label, x + ", " + y + ", " + z);
    }

    private static void line(CommandSourceStack source, String label, String value) {
        source.sendSuccess(() -> Component.literal("  " + label + ": ")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY))
                .append(Component.literal(value)
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE))), false);
    }
}
