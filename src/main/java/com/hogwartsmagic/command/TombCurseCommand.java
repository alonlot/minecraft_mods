package com.hogwartsmagic.command;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.CursedGlyphBlock;
import com.hogwartsmagic.tomb.TombCurse;
import com.hogwartsmagic.tomb.TombCurses;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.Locale;

/**
 * {@code /tombcurse} (op only): try the tomb curses without finding a tomb.
 * <ul>
 *   <li>{@code room}: builds a small dark tomb room round you, with one tile of each curse in its floor.</li>
 *   <li>{@code reveal}: Revelio's reveal from where you stand, with its wave.</li>
 *   <li>{@code trigger <collapse|risen|withering>}: sets that curse off on you, as if you stepped on it.</li>
 *   <li>{@code lift <collapse|risen|withering>}: lifts that curse at your feet, as if you broke a shown tile.</li>
 * </ul>
 */
public final class TombCurseCommand {
    private TombCurseCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tombcurse")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("room").executes(TombCurseCommand::room))
                .then(Commands.literal("reveal").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    int n = TombCurses.reveal(p.serverLevel(), p.position().add(0.0, 0.5, 0.0));
                    ctx.getSource().sendSuccess(() -> Component.literal("Revealing " + n + " hidden blocks."), false);
                    return n;
                }))
                .then(Commands.literal("trigger").then(curseArg().executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    TombCurses.trigger(p.serverLevel(), p.blockPosition().below(), curse(ctx), p);
                    return 1;
                })))
                .then(Commands.literal("lift").then(curseArg().executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    TombCurses.disarm(p.serverLevel(), p.blockPosition().below(), curse(ctx), p);
                    return 1;
                }))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> curseArg() {
        return Commands.argument("curse", StringArgumentType.word()).suggests((ctx, b) ->
                SharedSuggestionProvider.suggest(Arrays.stream(TombCurse.values()).map(TombCurse::getSerializedName), b));
    }

    private static TombCurse curse(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "curse").toLowerCase(Locale.ROOT);
        for (TombCurse c : TombCurse.values()) {
            if (c.getSerializedName().equals(name)) {
                return c;
            }
        }
        throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                Component.literal("No curse called " + name + ": collapse, risen or withering")).create();
    }

    /**
     * A sandstone room nine across and five high round the caller, unlit but for two soul lanterns, its
     * floor a chequer of glyph tiles with one cursed tile of each kind (west Collapse, north Risen, east
     * Withering), two blocks from the centre.
     */
    private static int room(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        BlockPos c = p.blockPosition();
        BlockState glyph = ExampleMod.HIEROGLYPH_SANDSTONE.get().defaultBlockState();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int dy = -1; dy <= 5; dy++) {
                    BlockPos at = c.offset(dx, dy, dz);
                    boolean wall = Math.abs(dx) == 5 || Math.abs(dz) == 5 || dy == -1 || dy == 5;
                    BlockState s;
                    if (!wall) {
                        s = Blocks.AIR.defaultBlockState();
                    } else if (dy == -1) {
                        s = (dx + dz) % 2 == 0 ? glyph : Blocks.SMOOTH_SANDSTONE.defaultBlockState();
                    } else if (dy == 2 || dy == 3) {
                        s = glyph;
                    } else {
                        s = Blocks.CUT_SANDSTONE.defaultBlockState();
                    }
                    level.setBlock(at, s, 2);
                }
            }
        }
        TombCurse[] curses = {TombCurse.COLLAPSE, TombCurse.RISEN, TombCurse.WITHERING};
        BlockPos[] spots = {c.offset(-2, -1, 0), c.offset(0, -1, -2), c.offset(2, -1, 0)};
        for (int i = 0; i < 3; i++) {
            level.setBlock(spots[i], ExampleMod.CURSED_GLYPH.get().defaultBlockState()
                    .setValue(CursedGlyphBlock.CURSE, curses[i]), 3);
        }
        level.setBlock(c.offset(-4, 0, -4), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
        level.setBlock(c.offset(4, 0, 4), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "A tomb room: Collapse to the west, Risen to the north, Withering to the east."), false);
        return 1;
    }
}
