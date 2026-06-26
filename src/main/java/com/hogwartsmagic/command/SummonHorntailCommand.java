package com.hogwartsmagic.command;

import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /summonhorntail} (op only) — spawns a Hungarian Horntail a short distance in front of the
 * caster on solid ground. Mirrors the project's other op spawn commands.
 */
public class SummonHorntailCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("summonhorntail")
                .requires(source -> source.hasPermission(2))
                .executes(SummonHorntailCommand::summon));
    }

    private static int summon(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        Vec3 origin = source.getPosition();
        float yaw = source.getRotation().y;
        Vec3 forward = Vec3.directionFromRotation(0.0F, yaw);
        double x = origin.x + forward.x * 8.0D;
        double z = origin.z + forward.z * 8.0D;
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) Math.floor(x), (int) Math.floor(z));

        HungarianHorntailEntity dragon = ModEntities.HUNGARIAN_HORNTAIL.get().create(level);
        if (dragon == null) {
            source.sendFailure(Component.literal("Failed to create the Hungarian Horntail."));
            return 0;
        }
        dragon.moveTo(x + 0.5D, y, z + 0.5D, yaw + 180.0F, 0.0F);
        dragon.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, y, z)),
                MobSpawnType.COMMAND, null);
        level.addFreshEntity(dragon);

        source.sendSuccess(() -> Component.literal("A Hungarian Horntail rises from its lair!"), true);
        return 1;
    }
}
