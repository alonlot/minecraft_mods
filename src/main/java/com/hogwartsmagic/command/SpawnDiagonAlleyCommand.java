package com.hogwartsmagic.command;

import com.hogwartsmagic.worldgen.DiagonAlleyFeature;
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
        BlockPos spawned = DiagonAlleyFeature.spawnManually(level, at);
        if (spawned == null) {
            src.sendFailure(Component.literal("Could not spawn Diagon Alley here."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(
                        "Diagon Alley spawned at " + spawned.getX() + ", " + spawned.getY() + ", " + spawned.getZ()
                                + ". Use /findalley to locate it later.")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), true);
        return 1;
    }
}
