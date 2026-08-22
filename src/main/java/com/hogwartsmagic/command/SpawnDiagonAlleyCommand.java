package com.hogwartsmagic.command;

import com.hogwartsmagic.worldgen.DiagonAlleyManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;

public class SpawnDiagonAlleyCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /spawnalley — drop Diagon Alley at the caller's position. Requires op (perm 2)
        // because it overwrites a sizeable patch of terrain.
        dispatcher.register(Commands.literal("spawnalley")
                .requires(s -> s.hasPermission(2))
                .executes(SpawnDiagonAlleyCommand::run));
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        BlockPos at = BlockPos.containing(src.getPosition());

        if (DiagonAlleyManager.isBusy()) {
            src.sendFailure(Component.literal("An alley is already being built — wait for it to finish."));
            return 0;
        }
        // The build runs across ticks rather than all at once: it is a third of a million cells, and
        // placing that inside the command would stall the server long enough for the watchdog to
        // complain. requestSpawn returns immediately with the position it recorded.
        BlockPos spawned = DiagonAlleyManager.requestSpawn(level, at);
        if (spawned == null) {
            src.sendFailure(Component.literal(
                    "Could not spawn Diagon Alley here — the structure template failed to load."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(
                        "Diagon Alley is rising at " + spawned.getX() + ", " + spawned.getY() + ", " + spawned.getZ()
                                + ". It builds over a few seconds. Use /findalley to return to it later.")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), true);
        return 1;
    }
}
