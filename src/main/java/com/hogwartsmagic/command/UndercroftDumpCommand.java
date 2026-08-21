package com.hogwartsmagic.command;

import com.hogwartsmagic.worldgen.undercroft.CartTrack;
import com.hogwartsmagic.worldgen.undercroft.UndercroftDump;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.nio.file.Path;

/**
 * Carves the undercroft into memory and writes it out as a structure template, so it can be
 * rendered before it is ever put in the ground.
 *
 * <p>Runs from the server console with no player, deliberately: that makes "look at the build" a
 * headless one-liner rather than a client launch.
 */
public final class UndercroftDumpCommand {

    private UndercroftDumpCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("undercroftdump")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> run(ctx, 122))
                .then(Commands.argument("depth",
                                IntegerArgumentType.integer(CartTrack.DEPTH_MIN, CartTrack.DEPTH_MAX))
                        .executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "depth")))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, int depth) {
        CommandSourceStack source = ctx.getSource();
        Path out = Path.of("undercroft_dumps");
        try {
            long began = System.nanoTime();
            UndercroftDump.Result result = UndercroftDump.dump(out, "corridor_d" + depth, depth);
            double seconds = (System.nanoTime() - began) / 1.0E9D;
            source.sendSuccess(() -> Component.literal(String.format(
                            "dumped %s  %dx%dx%d  %d blocks  in %.1fs",
                            result.path(), result.width(), result.height(), result.length(),
                            result.blocks(), seconds))
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)), false);
            for (String problem : result.problems()) {
                source.sendSuccess(() -> Component.literal("  PROBLEM  " + problem)
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.RED)), false);
            }
            if (result.problems().isEmpty()) {
                source.sendSuccess(() -> Component.literal("  rideable end to end")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)), false);
            }
            return result.problems().isEmpty() ? 1 : 0;
        } catch (Exception e) {
            source.sendFailure(Component.literal("dump failed: " + e));
            return 0;
        }
    }
}
