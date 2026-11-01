package com.hogwartsmagic.command;

import com.hogwartsmagic.worldgen.structure.ModStructures;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.PlaceCommand;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Optional;

public class SpawnMiniHogwartsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /spawnminihogwarts — place the Mini Hogwarts structure centred on the caller's chunk, exactly as
        // /place structure hogwartsmagic:mini_hogwarts would. Op-only (perm 2); it overwrites a large patch of
        // terrain. Like /place, every chunk the castle covers must already be loaded.
        dispatcher.register(Commands.literal("spawnminihogwarts")
                .requires(s -> s.hasPermission(2))
                .executes(SpawnMiniHogwartsCommand::run));
    }

    private static int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        Optional<Holder.Reference<Structure>> structure = src.registryAccess()
                .registryOrThrow(Registries.STRUCTURE).getHolder(ModStructures.MINI_HOGWARTS);
        if (structure.isEmpty()) {
            src.sendFailure(Component.literal("The Mini Hogwarts structure is not registered (datapack missing?)."));
            return 0;
        }
        return PlaceCommand.placeStructure(src, structure.get(), BlockPos.containing(src.getPosition()));
    }
}
