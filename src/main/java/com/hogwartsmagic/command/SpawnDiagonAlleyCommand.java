package com.hogwartsmagic.command;

import com.hogwartsmagic.worldgen.DiagonAlleyManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;

public class SpawnDiagonAlleyCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /spawnalley          — drop the whole template, courtyard included, and leave it there.
        // /spawnalley here     — open it in place, the way the archway does: the world under it is
        //                        recorded first and can be given back.
        // /spawnalley restore  — give it back.
        //
        // Op-only (perm 2): all three overwrite a sizeable patch of terrain.
        dispatcher.register(Commands.literal("spawnalley")
                .requires(s -> s.hasPermission(2))
                .executes(SpawnDiagonAlleyCommand::run)
                .then(Commands.literal("here").executes(SpawnDiagonAlleyCommand::openHere)));
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        BlockPos at = BlockPos.containing(src.getPosition());

        if (DiagonAlleyManager.isBusy()) {
            src.sendFailure(Component.literal("An alley is already being built — wait for it to finish."));
            return 0;
        }
        // The build runs across ticks rather than all at once: it is a third of a million cells, and
        // placing that inside the command would stall the server long enough for the watchdog to
        // complain. requestSpawn returns immediately with the position it recorded.
        BlockPos spawned = DiagonAlleyManager.requestSpawn(level, at);
        if (spawned == null) {
            src.sendFailure(Component.literal(
                    "Could not spawn Diagon Alley here — the structure template failed to load."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(
                        "Diagon Alley is rising at " + spawned.getX() + ", " + spawned.getY() + ", " + spawned.getZ()
                                + ". It builds over a few seconds. Use /findalley to return to it later.")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), true);
        return 1;
    }

    /**
     * Open the alley in place, running away from the caller in the direction they are facing —
     * the same path the archway takes, minus the wall and the animation.
     *
     * <p>Nothing of this world is touched: the street goes into the alley dimension, at these
     * coordinates, and the arch is what carries you to it.
     */
    private static int openHere(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();

        if (DiagonAlleyManager.isBusy()) {
            src.sendFailure(Component.literal("Something is already building or restoring — wait for it."));
            return 0;
        }
        BlockPos at = BlockPos.containing(src.getPosition());
        net.minecraft.core.Direction facing = net.minecraft.core.Direction
                .fromYRot(src.getRotation().y);

        BlockPos anchor = DiagonAlleyManager.open(level, at, facing);
        if (anchor == null) {
            src.sendFailure(Component.literal(
                    "Could not open here — the alley dimension or the template failed to load."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(
                        "The street is going up in the alley dimension, running " + facing.getName()
                                + " from here.")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), true);
        return 1;
    }

}
