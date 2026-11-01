package com.hogwartsmagic.command;

import com.hogwartsmagic.worldgen.MiniHogwartsTracker;
import com.hogwartsmagic.worldgen.structure.ModStructures;
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
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

public class FindMiniHogwartsCommand {

    /** How far the structure locate searches, in structure-set regions (200 chunks each). */
    private static final int LOCATE_RADIUS = 32;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /findminihogwarts        — report the nearest Mini Hogwarts castle (generated, or due to generate)
        // /findminihogwarts list   — list all castles that have generated
        dispatcher.register(Commands.literal("findminihogwarts")
                .requires(src -> src.hasPermission(2))
                .executes(FindMiniHogwartsCommand::findNearest)
                .then(Commands.literal("list").executes(FindMiniHogwartsCommand::listAll))
        );
    }

    private static int findNearest(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        BlockPos from = BlockPos.containing(source.getPosition());
        MiniHogwartsTracker.Castle known = MiniHogwartsTracker.get(level).findClosest(from);
        BlockPos target;
        String what;
        if (known != null) {
            target = known.center();
            what = "Nearest Mini Hogwarts";
        } else {
            // Nothing generated yet: ask the structure system where the next one will stand, like /locate.
            BlockPos located = level.findNearestMapStructure(ModStructures.MINI_HOGWARTS_TAG, from, LOCATE_RADIUS, false);
            if (located == null) {
                source.sendFailure(Component.literal(
                        "No Mini Hogwarts castle found nearby. They stand in Forbidden Forest biomes, far apart."));
                return 0;
            }
            // The locate gives the start chunk's corner; aim at the surface there (the castle is centred on it).
            int y = level.getChunkSource().getGenerator().getFirstOccupiedHeight(located.getX() + 8, located.getZ() + 8,
                    Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
            target = new BlockPos(located.getX() + 8, y, located.getZ() + 8);
            what = "Nearest Mini Hogwarts (not yet visited)";
        }
        int dx = target.getX() - from.getX();
        int dz = target.getZ() - from.getZ();
        int distance = (int) Math.sqrt((double) dx * dx + (double) dz * dz);

        String tpCmd = "/execute in " + level.dimension().location()
                + " run tp @s " + target.getX() + " " + target.getY() + " " + target.getZ();
        Component clickable = Component.literal(
                what + ": " + target.getX() + ", " + target.getY() + ", " + target.getZ()
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
