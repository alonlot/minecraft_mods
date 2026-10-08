package com.hogwartsmagic.command;

import com.hogwartsmagic.spell.support.seal.Seal;
import com.hogwartsmagic.spell.support.seal.SealData;
import com.hogwartsmagic.spell.support.seal.SealRules;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * /colloportus clear [radius]: op command that removes every Colloportus seal in your dimension whose sphere reaches
 * any block within radius of you.
 */
public final class ColloportusCommand {
    private static final int DEFAULT_RADIUS = 8;

    private ColloportusCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("colloportus")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("clear")
                        .executes(ctx -> clear(ctx.getSource(), DEFAULT_RADIUS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> clear(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"))))));
    }

    private static int clear(CommandSourceStack source, int radius) {
        ServerLevel level = source.getLevel();
        SealData data = SealData.get(level);
        BlockPos at = BlockPos.containing(source.getPosition());
        List<Seal> hit = new ArrayList<>();
        for (Seal seal : data.seals()) {
            if (SealRules.withinReach(seal, at.getX(), at.getY(), at.getZ(), radius)) { // centre or any anchor
                hit.add(seal);
            }
        }
        for (Seal seal : hit) {
            data.remove(seal);
        }
        int removed = hit.size();
        source.sendSuccess(() -> Component.literal("Removed " + removed + " seal(s)."), true);
        return removed;
    }
}
