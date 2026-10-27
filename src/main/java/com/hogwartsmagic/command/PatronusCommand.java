package com.hogwartsmagic.command;

import com.hogwartsmagic.spell.summon.PatronusForm;
import com.hogwartsmagic.spell.summon.PatronusMessenger;
import com.hogwartsmagic.spell.summon.PatronusSpell;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /patronus <player> <message>}: a Master of Expecto Patronum sends a message by Patronus. Open to
 * every player; PatronusMessenger checks the wand, rank, range, mana and cooldown.
 * {@code /patronus form <1-8>} (op): set your stored personal form, to check each form's rendering.
 */
public final class PatronusCommand {
    private PatronusCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("patronus")
                .then(Commands.literal("form")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1, 8))
                                .executes(ctx -> {
                                    ServerPlayer self = ctx.getSource().getPlayerOrException();
                                    PatronusForm form = PatronusForm.byId(IntegerArgumentType.getInteger(ctx, "id"));
                                    PatronusSpell.setForm(self, form);
                                    ctx.getSource().sendSuccess(() -> Component.translatable(form.langKey()), false);
                                    return 1;
                                })))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ServerPlayer sender = ctx.getSource().getPlayerOrException();
                                    ServerPlayer recipient = EntityArgument.getPlayer(ctx, "player");
                                    return PatronusMessenger.send(sender, recipient,
                                            StringArgumentType.getString(ctx, "message")) ? 1 : 0;
                                }))));
    }
}
