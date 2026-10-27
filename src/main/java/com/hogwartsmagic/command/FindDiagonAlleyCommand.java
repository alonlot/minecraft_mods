package com.hogwartsmagic.command;

import com.hogwartsmagic.worldgen.DiagonAlleyTracker;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

public class FindDiagonAlleyCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /findalley                — report the nearest wall that has been opened onto Diagon Alley
        // /findalley list           — list all of them (positions are the arch in this dimension)
        dispatcher.register(Commands.literal("findalley")
                .requires(src -> src.hasPermission(2))
                .executes(FindDiagonAlleyCommand::findNearest)
                .then(Commands.literal("list").executes(FindDiagonAlleyCommand::listAll))
        );
    }

    private static int findNearest(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        DiagonAlleyTracker tracker = DiagonAlleyTracker.get(level);
        BlockPos from = BlockPos.containing(source.getPosition());
        BlockPos closest = tracker.findClosest(from);
        if (closest == null) {
            source.sendFailure(Component.literal(
                    "No Diagon Alleys have been opened from this dimension yet. Build a wall of Diagon archway brick "
                            + "behind a cauldron, arm it with Alohomora or Revelio, and tap the glowing brick "
                            + "three times -- the alley behind the arch is recorded here when it opens."));
            return 0;
        }
        int dx = closest.getX() - from.getX();
        int dz = closest.getZ() - from.getZ();
        int distance = (int) Math.sqrt(dx * dx + dz * dz);

        String tpCmd = "/execute in " + level.dimension().location()
                + " run tp @s " + closest.getX() + " " + closest.getY() + " " + closest.getZ();
        Component clickable = Component.literal(
                "Nearest archway wall onto Diagon Alley: " + closest.getX() + ", " + closest.getY() + ", " + closest.getZ()
                        + " (" + distance + " blocks away). Click to teleport.")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GOLD)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCmd))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(tpCmd))));
        source.sendSuccess(() -> clickable, false);
        return 1;
    }

    private static int listAll(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        DiagonAlleyTracker tracker = DiagonAlleyTracker.get(level);
        List<BlockPos> all = tracker.getAll();
        if (all.isEmpty()) {
            source.sendFailure(Component.literal(
                    "No archway walls have been opened onto Diagon Alley from this dimension."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Archway walls opened onto Diagon Alley (" + all.size() + "):"), false);
        for (BlockPos p : all) {
            source.sendSuccess(() -> Component.literal("  " + p.getX() + ", " + p.getY() + ", " + p.getZ()), false);
        }
        return all.size();
    }
}
