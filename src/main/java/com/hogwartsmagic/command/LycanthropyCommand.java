package com.hogwartsmagic.command;

import com.hogwartsmagic.lycanthropy.Lycanthropy;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** {@code /lycanthropy status|infect|cure <player>} -- inspect or force the werewolf curse while testing. Op-only. */
public class LycanthropyCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lycanthropy")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("status")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(LycanthropyCommand::status)))
                .then(Commands.literal("infect")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(LycanthropyCommand::infect)))
                .then(Commands.literal("cure")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(LycanthropyCommand::cure))));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String state = Lycanthropy.isBeast(player) ? "beast" : Lycanthropy.isCursed(player) ? "cursed" : "clean";
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.hogwartsmagic.lycanthropy.status",
                player.getDisplayName(), Component.translatable("commands.hogwartsmagic.lycanthropy." + state)), false);
        return Lycanthropy.isCursed(player) ? 1 : 0;
    }

    private static int infect(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        Lycanthropy.infect(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.hogwartsmagic.lycanthropy.infected",
                player.getDisplayName()), true);
        return 1;
    }

    private static int cure(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        if (!Lycanthropy.cure(player)) {
            ctx.getSource().sendFailure(Component.translatable("commands.hogwartsmagic.lycanthropy.not_cursed",
                    player.getDisplayName()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.hogwartsmagic.lycanthropy.cured",
                player.getDisplayName()), true);
        return 1;
    }
}
