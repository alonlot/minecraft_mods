package com.hogwartsmagic.command;

import com.hogwartsmagic.worldgen.MiniHogwartsTracker;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
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

public class FindMiniHogwartsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /findminihogwarts        — report the nearest known Mini Hogwarts castle
        // /findminihogwarts list   — list all known positions
        dispatcher.register(Commands.literal("findminihogwarts")
                .requires(src -> src.hasPermission(2))
                .executes(FindMiniHogwartsCommand::findNearest)
                .then(Commands.literal("list").executes(FindMiniHogwartsCommand::listAll))
        );
    }

    private static int findNearest(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        MiniHogwartsTracker tracker = MiniHogwartsTracker.get(level);
        BlockPos from = BlockPos.containing(source.getPosition());
        BlockPos closest = tracker.findClosest(from);
        if (closest == null) {
            source.sendFailure(Component.literal(
                    "No Mini Hogwarts castles known in this dimension yet. Explore a Forbidden Forest biome — one will rise and be announced."));
            return 0;
        }
        int dx = closest.getX() - from.getX();
        int dz = closest.getZ() - from.getZ();
        int distance = (int) Math.sqrt((double) dx * dx + (double) dz * dz);

        String tpCmd = "/execute in " + level.dimension().location()
                + " run tp @s " + closest.getX() + " " + closest.getY() + " " + closest.getZ();
        Component clickable = Component.literal(
                "Nearest Mini Hogwarts: " + closest.getX() + ", " + closest.getY() + ", " + closest.getZ()
                        + " (" + distance + " blocks away). Click to teleport.")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GOLD)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCmd))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(tpCmd))));
        source.sendSuccess(() -> clickable, false);
        return 1;
    }

    private static int listAll(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        MiniHogwartsTracker tracker = MiniHogwartsTracker.get(level);
        List<BlockPos> all = tracker.getAll();
        if (all.isEmpty()) {
            source.sendFailure(Component.literal("No Mini Hogwarts castles recorded in this dimension."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Known Mini Hogwarts castles (" + all.size() + "):"), false);
        for (BlockPos p : all) {
            source.sendSuccess(() -> Component.literal("  " + p.getX() + ", " + p.getY() + ", " + p.getZ()), false);
        }
        return all.size();
    }
}
