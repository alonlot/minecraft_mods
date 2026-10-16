package com.hogwartsmagic.command;

import com.hogwartsmagic.event.OllivandersCeremony;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * {@code /ollivanders} -- replay the wand ceremony.
 *
 * <p>It normally fires once per player, on their first wand, which makes it awkward to look at
 * while it is being built and impossible to show anyone afterwards. This runs it on demand, using a
 * real wandmaker if one is standing nearby and a spot two blocks in front of the caller if not.
 */
public class OllivandersCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ollivanders")
                .requires(src -> src.hasPermission(2))
                .executes(OllivandersCommand::run));
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Only a player can be measured for a wand."));
            return 0;
        }
        if (OllivandersCeremony.isBusy(player)) {
            src.sendFailure(Component.literal("You are already in the middle of it."));
            return 0;
        }

        // Use a real wandmaker if there is one to hand, so the camera frames the pair of you.
        Entity wandmaker = null;
        List<net.minecraft.world.entity.npc.Villager> nearby = player.serverLevel().getEntitiesOfClass(
                net.minecraft.world.entity.npc.Villager.class,
                new AABB(player.blockPosition()).inflate(8.0D),
                v -> v.getVillagerData().getProfession()
                        == com.hogwartsmagic.entity.ModVillagers.WANDMAKER.get());
        if (!nearby.isEmpty()) {
            wandmaker = nearby.get(0);
        }

        if (!OllivandersCeremony.begin(player, wandmaker)) {
            src.sendFailure(Component.literal("The shop is not ready for you."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("The shop goes quiet...")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), false);
        return 1;
    }
}
