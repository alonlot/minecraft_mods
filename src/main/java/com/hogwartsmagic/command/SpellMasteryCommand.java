package com.hogwartsmagic.command;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.mastery.MasteryMath;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;

/**
 * {@code /spellmastery get|set|setrank|reset} -- inspect and force spell ranks while tuning.
 * Op-only. Creative casts earn no XP, so this is how a tester jumps straight to Master.
 */
public class SpellMasteryCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spellmastery")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(SpellMasteryCommand::get)))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("spell", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(SpellType.values()).map(SpellType::getId), b))
                                        .then(Commands.argument("xp", FloatArgumentType.floatArg(0f))
                                                .executes(SpellMasteryCommand::set)))))
                .then(Commands.literal("setrank")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("spell", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(SpellType.values()).map(SpellType::getId), b))
                                        .then(Commands.argument("rank", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 4))
                                                .executes(SpellMasteryCommand::setRank)))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(SpellMasteryCommand::reset))));
    }

    /** SpellType.fromId falls back to Wingardium Leviosa on a typo; a debug command must not. */
    private static SpellType strictSpell(String id) {
        for (SpellType spell : SpellType.values()) {
            if (spell.getId().equals(id)) {
                return spell;
            }
        }
        return null;
    }

    private static int get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        StringBuilder out = new StringBuilder();
        for (SpellType spell : SpellType.values()) {
            float xp = SpellMasteryData.getXp(player, spell);
            if (xp > 0f) {
                out.append(spell.getId()).append(' ')
                        .append(MasteryMath.roman(SpellMasteryData.getRank(player, spell)))
                        .append(String.format(" (%.1f)  ", xp));
            }
        }
        String text = out.length() == 0 ? "no mastery yet" : out.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(player.getScoreboardName() + ": " + text), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        SpellType spell = strictSpell(StringArgumentType.getString(ctx, "spell"));
        if (spell == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown spell"));
            return 0;
        }
        float xp = FloatArgumentType.getFloat(ctx, "xp");
        SpellMasteryData.setXp(player, spell, xp);
        ctx.getSource().sendSuccess(() -> Component.literal(spell.getId() + " -> " + xp + " XP"), true);
        return 1;
    }

    private static int setRank(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        SpellType spell = strictSpell(StringArgumentType.getString(ctx, "spell"));
        if (spell == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown spell"));
            return 0;
        }
        int rank = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "rank");
        SpellMasteryData.setXp(player, spell, Config.getMasteryThresholds()[rank - 1]);
        ctx.getSource().sendSuccess(() -> Component.literal(spell.getId() + " -> rank " + MasteryMath.roman(rank)), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        SpellMasteryData.reset(player);
        ctx.getSource().sendSuccess(() -> Component.literal("Reset spell mastery for " + player.getScoreboardName()), true);
        return 1;
    }
}
