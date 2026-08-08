package com.hogwartsmagic.command;

import com.hogwartsmagic.worldgen.VillageGringottsBank;
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

/**
 * Builds the Gringotts bank on demand.
 *
 * <p>Op-only: the bank is 100x34 blocks and overwrites whatever is standing
 * there. Placement is spread across server ticks, so the command returns
 * immediately and the building rises over the next few seconds.
 */
public class BuildBankCommand {

    /** Pushed away from the caller so a 100-wide building does not bury them. */
    private static final int CALLER_OFFSET = 70;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /buildbank            — build near the caller
        // /buildbank <pos>      — build centred on an explicit position
        dispatcher.register(Commands.literal("buildbank")
                .requires(source -> source.hasPermission(2))
                .executes(BuildBankCommand::nearCaller)
                .then(Commands.argument("center", BlockPosArgument.blockPos())
                        .executes(ctx -> at(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "center")))));
    }

    private static int nearCaller(CommandContext<CommandSourceStack> ctx) {
        BlockPos center = BlockPos.containing(ctx.getSource().getPosition())
                .offset(CALLER_OFFSET, 0, CALLER_OFFSET);
        return at(ctx, center);
    }

    private static int at(CommandContext<CommandSourceStack> ctx, BlockPos center) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();

        if (VillageGringottsBank.isBusy()) {
            source.sendFailure(Component.literal(
                    "A Gringotts bank is already being built. Wait for it to finish."));
            return 0;
        }

        BlockPos recorded = VillageGringottsBank.requestSpawn(level, center);
        if (recorded == null) {
            source.sendFailure(Component.literal(
                    "Could not build Gringotts there — the schematic is missing, or the chunks "
                            + "around that spot are not loaded. Stand closer and try again."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                        "Building Gringotts at " + recorded.getX() + ", " + recorded.getY() + ", "
                                + recorded.getZ() + ". It will rise over a few seconds — "
                                + "use /findgringotts to locate it.")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), true);
        return 1;
    }
}
