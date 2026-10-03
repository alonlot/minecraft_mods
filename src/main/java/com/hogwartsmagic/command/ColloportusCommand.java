package com.hogwartsmagic.command;

import com.hogwartsmagic.spell.support.seal.Seal;
import com.hogwartsmagic.spell.support.seal.SealData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

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

    private static int clear(CommandSourceStack source, int radius) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        SealData data = SealData.get(level);
        BlockPos at = player.blockPosition();
        List<Seal> hit = new ArrayList<>();
        for (Seal seal : data.seals()) {
            long dx = seal.x() - at.getX();
            long dy = seal.y() - at.getY();
            long dz = seal.z() - at.getZ();
            long reach = (long) seal.radius() + radius;
            if (dx * dx + dy * dy + dz * dz <= reach * reach) {
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
