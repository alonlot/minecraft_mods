package com.hogwartsmagic.command;

import com.hogwartsmagic.entity.SphinxEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * {@code /sphinx answer <sphinx> <choice>}: what the clickable answers in a Sphinx's riddle run.
 * Open to every player, because clicking an answer is how a player speaks to her. It only works for
 * a riddle she asked that player, from close by, in the last minute, so typing it by hand gains
 * nothing.
 */
public final class SphinxCommand {
    private SphinxCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sphinx")
                .then(Commands.literal("answer")
                        .then(Commands.argument("sphinx", IntegerArgumentType.integer())
                                .then(Commands.argument("choice", IntegerArgumentType.integer(0, 2))
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            Entity e = player.serverLevel().getEntity(IntegerArgumentType.getInteger(ctx, "sphinx"));
                                            boolean taken = e instanceof SphinxEntity sphinx
                                                    && sphinx.answer(player, IntegerArgumentType.getInteger(ctx, "choice"));
                                            if (!taken) {
                                                player.sendSystemMessage(Component.translatable("sphinx.hogwartsmagic.no_riddle")
                                                        .withStyle(ChatFormatting.GRAY));
                                            }
                                            return taken ? 1 : 0;
                                        })))));
    }
}
