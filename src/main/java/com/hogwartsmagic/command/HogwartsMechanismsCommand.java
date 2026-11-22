package com.hogwartsmagic.command;

import com.hogwartsmagic.worldgen.MiniHogwartsTracker;
import com.hogwartsmagic.worldgen.mechanism.CastleMechanisms;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * /hogwartsmechanisms (op only): try the nearest Mini Hogwarts castle's moving parts without waiting for them.
 * <pre>
 *   raise | lower    the Headmaster's stair up or down now (raise is what the password does)
 *   swing            the moving flight starts its swing now
 *   goto &lt;view&gt;      stand at a view of the parts: gargoyle, flight, office, book_gallery
 * </pre>
 */
public final class HogwartsMechanismsCommand {
    private HogwartsMechanismsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("hogwartsmechanisms")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("raise").executes(c -> run(c.getSource(), "raise")))
                .then(Commands.literal("lower").executes(c -> run(c.getSource(), "lower")))
                .then(Commands.literal("swing").executes(c -> run(c.getSource(), "swing")))
                .then(Commands.literal("goto").then(Commands.argument("view", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (String v : CastleMechanisms.VIEWS.keySet()) {
                                b.suggest(v);
                            }
                            return b.buildFuture();
                        })
                        .executes(c -> run(c.getSource(), "goto " + StringArgumentType.getString(c, "view"))))));
    }

    private static int run(CommandSourceStack src, String what) {
        ServerLevel level = src.getLevel();
        MiniHogwartsTracker.Castle castle = MiniHogwartsTracker.get(level).findClosest(BlockPos.containing(src.getPosition()));
        if (castle == null || castle.anchor() == null) {
            src.sendFailure(Component.literal("No Mini Hogwarts castle with a recorded anchor in this dimension."));
            return 0;
        }
        if (what.startsWith("goto ")) {
            ServerPlayer player = src.getPlayer();
            float[] v = CastleMechanisms.VIEWS.get(what.substring(5));
            if (player == null || v == null) {
                src.sendFailure(Component.literal("Unknown view (or not a player). Views: " + CastleMechanisms.VIEWS.keySet()));
                return 0;
            }
            BlockPos a = castle.anchor();
            player.teleportTo(level, a.getX() + v[0], a.getY() + v[1], a.getZ() + v[2], v[3], v[4]);
            return 1;
        }
        String result = CastleMechanisms.command(level, castle.anchor(), what);
        src.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }
}
