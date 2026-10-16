package com.hogwartsmagic.command;

import com.hogwartsmagic.worldgen.VillageBankTracker;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * Locates Gringotts village branches recorded by
 * {@link com.hogwartsmagic.worldgen.VillageGringottsBank}. Only branches a
 * player has already caused to be built are known - the tracker is populated on
 * visit, not at world generation.
 */
public class FindGringottsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /findgringotts       — nearest known village branch
        // /findgringotts list  — every branch known in this dimension
        dispatcher.register(Commands.literal("findgringotts")
                .requires(src -> src.hasPermission(2))
                .executes(FindGringottsCommand::findNearest)
                .then(Commands.literal("list").executes(FindGringottsCommand::listAll))
        );
    }

    private static int findNearest(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        VillageBankTracker tracker = VillageBankTracker.get(level);
        BlockPos from = BlockPos.containing(source.getPosition());
        BlockPos closest = tracker.findClosest(from);

        if (closest == null) {
            source.sendFailure(Component.literal(
                    "No Gringotts branches known in this dimension yet. Visit villages — roughly one in three has one."));
            return 0;
        }

        int dx = closest.getX() - from.getX();
        int dz = closest.getZ() - from.getZ();
        int distance = (int) Math.sqrt((double) dx * dx + (double) dz * dz);

        String tpCmd = "/execute in " + level.dimension().location()
                + " run tp @s " + closest.getX() + " " + closest.getY() + " " + closest.getZ();
        Component message = Component.literal(
                        "Nearest Gringotts branch: " + closest.getX() + ", " + closest.getY() + ", " + closest.getZ()
                                + " (" + distance + " blocks away)")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GOLD)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tpCmd))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Click to copy a teleport command"))));
        source.sendSuccess(() -> message, false);
        return 1;
    }

    private static int listAll(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        VillageBankTracker tracker = VillageBankTracker.get(source.getLevel());
        List<BlockPos> banks = tracker.getBanks();

        if (banks.isEmpty()) {
            source.sendFailure(Component.literal("No Gringotts branches known in this dimension yet."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Known Gringotts branches (" + banks.size() + "):")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), false);
        for (BlockPos pos : banks) {
            source.sendSuccess(() -> Component.literal(
                    "  " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
        }
        return banks.size();
    }
}
