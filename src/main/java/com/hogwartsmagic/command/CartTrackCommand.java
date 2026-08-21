package com.hogwartsmagic.command;

import com.hogwartsmagic.worldgen.undercroft.CartTrack;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * Audits the generated cart line and prints what it found.
 *
 * <p>Exists to prove the Java port of the layout produces the same curve the Python design tool
 * settled on, rather than a plausible-looking different one. Both sides run the same audit, so the
 * numbers are directly comparable: run {@code python build_scripts/preview_cart_track.py} and this,
 * and they should agree. Numbers that disagree mean the port has drifted.
 *
 * <p>Runs happily from the server console with no player, which is the point - it makes the check
 * something a headless server can answer in a few seconds.
 */
public final class CartTrackCommand {

    private CartTrackCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("carttrack")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> run(ctx, 122))
                .then(Commands.argument("depth",
                                IntegerArgumentType.integer(CartTrack.DEPTH_MIN, CartTrack.DEPTH_MAX))
                        .executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "depth"))))
                .then(Commands.literal("sweep").executes(CartTrackCommand::sweep)));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, int depth) {
        CommandSourceStack source = ctx.getSource();
        BlockPos origin = BlockPos.containing(source.getPosition());
        CartTrack track = CartTrack.build(origin, depth);
        CartTrack.Audit audit = track.audit();

        source.sendSuccess(() -> Component.literal(String.format(
                        "cart line   %.0f blocks   depth %d   %d samples",
                        audit.length(), depth, track.sampleCount()))
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), false);
        say(source, String.format("  drop                 %.0f", audit.drop()));
        say(source, String.format("  worst gradient       1:%.1f at s=%.0f   (limit 1:%.1f)",
                1.0D / Math.max(audit.worstGrade(), 1.0E-6D), audit.worstGradeAt(),
                1.0D / CartTrack.MAX_GRADE));
        say(source, String.format("  tightest gap         %.0f%% of clearance at s=%.0f",
                audit.tightestGap() * 100.0D, audit.tightestGapAt()));
        say(source, String.format("  reach from station   %.0f blocks", audit.reach()));

        for (int i = 0; i < CartTrack.segmentCount(); i++) {
            double from = track.segmentStartLength(i);
            double to = track.segmentStartLength(i + 1);
            say(source, String.format("  %d %-18s %6.0f blocks", i, CartTrack.SEGMENT_NAMES[i],
                    to - from));
        }

        source.sendSuccess(() -> Component.literal(audit.ok() ? "  OK" : "  FAILS ITS OWN AUDIT")
                .withStyle(Style.EMPTY.withColor(
                        audit.ok() ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
        return audit.ok() ? 1 : 0;
    }

    /** The layout has to work under any bank, not just one, so check the whole depth range. */
    private static int sweep(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        BlockPos origin = BlockPos.containing(source.getPosition());
        int failures = 0;
        for (int depth = CartTrack.DEPTH_MIN; depth <= CartTrack.DEPTH_MAX; depth += 6) {
            CartTrack.Audit audit = CartTrack.build(origin, depth).audit();
            boolean ok = audit.ok();
            failures += ok ? 0 : 1;
            say(source, String.format("  depth %3d  %5.0f blocks  1:%-5.1f  gap %3.0f%%  reach %2.0f  %s",
                    depth, audit.length(), 1.0D / Math.max(audit.worstGrade(), 1.0E-6D),
                    audit.tightestGap() * 100.0D, audit.reach(), ok ? "ok" : "FAIL"));
        }
        int bad = failures;
        source.sendSuccess(() -> Component.literal(bad == 0
                        ? String.format("all depths pass across %d..%d",
                                CartTrack.DEPTH_MIN, CartTrack.DEPTH_MAX)
                        : bad + " depths FAIL")
                .withStyle(Style.EMPTY.withColor(
                        bad == 0 ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
        return bad == 0 ? 1 : 0;
    }

    private static void say(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text)
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)), false);
    }
}
