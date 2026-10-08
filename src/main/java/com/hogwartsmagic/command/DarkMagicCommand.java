package com.hogwartsmagic.command;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.spell.dark.DarkMagicData;
import com.hogwartsmagic.spell.dark.DarkRules;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * {@code /darkmagic get|set|add <player> [value]} -- inspect and force a player's Darkness while testing. Op-only.
 * set ignores the floor, add goes through it; neither restarts the 10-minute quiet window. A tier change is
 * announced to the player exactly as in play.
 */
public class DarkMagicCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("darkmagic")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(DarkMagicCommand::get)))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", FloatArgumentType.floatArg(
                                                DarkRules.MIN_DARKNESS, DarkRules.MAX_DARKNESS))
                                        .executes(DarkMagicCommand::set))))
                .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", FloatArgumentType.floatArg(
                                                -DarkRules.MAX_DARKNESS, DarkRules.MAX_DARKNESS))
                                        .executes(DarkMagicCommand::add)))));
    }

    private static int get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        MutableComponent line = describe(player);
        if (!Config.isDarkMagicEnabled()) {
            line.append(" - enableDarkMagic is off, so it reads as Clean");
        }
        ctx.getSource().sendSuccess(() -> line, false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        DarkMagicData.set(player, FloatArgumentType.getFloat(ctx, "value"));
        MutableComponent line = describe(player);
        ctx.getSource().sendSuccess(() -> line, true);
        return 1;
    }

    private static int add(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        DarkMagicData.add(player, FloatArgumentType.getFloat(ctx, "value"));
        MutableComponent line = describe(player);
        ctx.getSource().sendSuccess(() -> line, true);
        return 1;
    }

    /** "Name: Darkness 63.50 (Corrupted)", plus ", floor 60" when a floor applies. The stored value and its tier. */
    private static MutableComponent describe(ServerPlayer player) {
        float stored = DarkMagicData.getStored(player);
        MutableComponent line = Component.literal(player.getScoreboardName() + ": Darkness "
                        + String.format(Locale.ROOT, "%.2f", stored) + " (")
                .append(Component.translatable(DarkRules.tierKey(DarkRules.tier(stored))))
                .append(")");
        float floor = DarkMagicData.floor(player);
        if (floor > DarkRules.NO_FLOOR) {
            line.append(String.format(Locale.ROOT, ", floor %.0f", floor));
        }
        return line;
    }
}
