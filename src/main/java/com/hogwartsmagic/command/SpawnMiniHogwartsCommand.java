package com.hogwartsmagic.command;

import com.hogwartsmagic.worldgen.MiniHogwartsManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;

public class SpawnMiniHogwartsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /spawnminihogwarts — build the Mini Hogwarts castle near the caller. Op-only (perm 2);
        // it overwrites a large patch of terrain. Placement is spread over server ticks.
        dispatcher.register(Commands.literal("spawnminihogwarts")
                .requires(s -> s.hasPermission(2))
                .executes(SpawnMiniHogwartsCommand::run));
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        if (MiniHogwartsManager.isBusy()) {
            src.sendFailure(Component.literal("A Mini Hogwarts castle is already being built. Wait for it to finish."));
            return 0;
        }
        // Offset from the caller so the castle doesn't bury them.
        BlockPos center = BlockPos.containing(src.getPosition()).offset(60, 0, 60);
        BlockPos recorded = MiniHogwartsManager.requestSpawn(level, center);
        if (recorded == null) {
            src.sendFailure(Component.literal("Could not build Mini Hogwarts (schematic missing or unreadable)."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(
                        "Building Mini Hogwarts near " + recorded.getX() + ", " + recorded.getY() + ", " + recorded.getZ()
                                + ". It will rise over a few seconds — use /findminihogwarts to locate it.")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), true);
        return 1;
    }
}
