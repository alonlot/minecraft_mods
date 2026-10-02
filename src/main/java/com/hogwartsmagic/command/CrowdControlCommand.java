package com.hogwartsmagic.command;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.combat.CrowdControl;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@code /crowdcontrol stun <targets> <seconds>} and {@code /crowdcontrol reset <targets>}, op only.
 * Stuns through {@link CrowdControl#apply} with the command's executor as the source, so diminishing
 * returns, the boss rule and (when one player runs it on another) the PvP rule all show in the
 * reported ticks. Phase 2 has no spell that applies crowd control yet; this is how it is tested.
 */
public final class CrowdControlCommand {
    private CrowdControlCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("crowdcontrol")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("stun")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("seconds", FloatArgumentType.floatArg(0.05f, 60f))
                                        .executes(CrowdControlCommand::stun))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(CrowdControlCommand::reset))));
    }

    private static int stun(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int baseTicks = Math.round(FloatArgumentType.getFloat(ctx, "seconds") * 20f);
        Entity source = ctx.getSource().getEntity();
        Holder<MobEffect> stunned = ExampleMod.STUNNED.getHolder().orElseThrow();
        int count = 0;
        for (Entity entity : EntityArgument.getEntities(ctx, "targets")) {
            if (!(entity instanceof LivingEntity target)) {
                continue;
            }
            int ticks = CrowdControl.apply(target, stunned, baseTicks, source);
            Component name = target.getDisplayName();
            ctx.getSource().sendSuccess(() -> ticks > 0
                    ? Component.translatable("commands.hogwartsmagic.crowdcontrol.stunned", name, ticks)
                    : Component.translatable("commands.hogwartsmagic.crowdcontrol.resisted", name), false);
            count++;
        }
        return count;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int count = 0;
        for (Entity entity : EntityArgument.getEntities(ctx, "targets")) {
            if (entity instanceof LivingEntity target) {
                CrowdControl.resetDiminishing(target);
                count++;
            }
        }
        int cleared = count;
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.hogwartsmagic.crowdcontrol.reset", cleared), true);
        return cleared;
    }
}
