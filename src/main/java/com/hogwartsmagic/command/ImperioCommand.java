package com.hogwartsmagic.command;

import com.hogwartsmagic.spell.mind.PossessionManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@code /imperio possess <target>} and {@code /imperio release}, op only. A debug possession needs no
 * Master rank, beam hit, held key, mana or cooldown; everything else runs exactly as a real one. This is how
 * possession is tested on a dedicated server with two clients.
 */
public final class ImperioCommand {
    private ImperioCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("imperio")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("possess")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ImperioCommand::possess)))
                .then(Commands.literal("release")
                        .executes(ImperioCommand::release)));
    }

    private static int possess(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer caster = ctx.getSource().getPlayerOrException();
        Entity entity = EntityArgument.getEntity(ctx, "target");
        if (!(entity instanceof LivingEntity target) || !PossessionManager.start(caster, target, true)) {
            ctx.getSource().sendFailure(Component.translatable("commands.hogwartsmagic.imperio.failed", entity.getDisplayName()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.hogwartsmagic.imperio.started", target.getDisplayName()), false);
        return 1;
    }

    private static int release(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer caster = ctx.getSource().getPlayerOrException();
        if (!PossessionManager.isPossessing(caster)) {
            ctx.getSource().sendFailure(Component.translatable("commands.hogwartsmagic.imperio.not_possessing"));
            return 0;
        }
        PossessionManager.endByCaster(caster, PossessionManager.EndReason.RELEASED);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.hogwartsmagic.imperio.released"), false);
        return 1;
    }
}
