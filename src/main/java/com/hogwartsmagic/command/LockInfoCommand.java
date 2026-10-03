package com.hogwartsmagic.command;

import com.hogwartsmagic.spell.support.lock.PistonLockSpell;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * /lockinfo [3|4]: trace and solve the piston door you look at, as Alohomora of that rank. Changes nothing. A lock
 * under a Colloportus seal prints SEALED with its sealer and rank instead.
 */
public final class LockInfoCommand {
    private LockInfoCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lockinfo")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> run(ctx.getSource(), 4))
                .then(Commands.argument("rank", IntegerArgumentType.integer(3, 4))
                        .executes(ctx -> run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "rank")))));
    }

    private static int run(CommandSourceStack source, int rank) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HitResult hit = player.pick(20.0, 1.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            source.sendFailure(Component.literal("Look at a block of a piston door within 20 blocks."));
            return 0;
        }
        com.hogwartsmagic.spell.support.seal.Seal seal = com.hogwartsmagic.spell.support.seal.ColloportusSpell
                .strongestSeal(player.serverLevel(), blockHit.getBlockPos());
        if (seal != null) {
            // One plain line the user can paste back; nothing is read while a seal holds the lock.
            player.sendSystemMessage(Component.literal("SEALED by " + seal.ownerName() + " (rank " + seal.rank() + ")"));
            return 1;
        }
        PistonLockSpell.Begin begin = PistonLockSpell.begin(player.serverLevel(), blockHit.getBlockPos(), player, rank,
                PistonLockSpell.Use.INSPECT);
        if (begin == PistonLockSpell.Begin.NO_PISTON) {
            source.sendFailure(Component.literal("No piston within 6 blocks of that block."));
            return 0;
        }
        if (begin == PistonLockSpell.Begin.STARTED) {
            source.sendSuccess(() -> Component.literal("Reading the lock..."), false);
            return 1;
        }
        return 0; // refused: the player was told why (BUSY or PISTON_LOGIC)
    }
}
