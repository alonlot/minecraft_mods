package com.hogwartsmagic.command;

import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.WizardEntity;
import com.hogwartsmagic.spell.SpellType;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;

public class WizardRaidCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wizardraid")
            .requires(source -> source.hasPermission(2))
            .executes(context -> {
                CommandSourceStack source = context.getSource();
                ServerLevel level = source.getLevel();
                BlockPos pos = BlockPos.containing(source.getPosition());
                
                source.sendSuccess(() -> Component.literal("Summoning Wizard Raid Leader!"), true);
                
                WizardEntity leader = ModEntities.WIZARD.get().create(level);
                if (leader != null) {
                    leader.setPos(pos.getX(), pos.getY(), pos.getZ());
                    leader.setRaidLeader(true);
                    // Explicitly set banner item in head slot
                    leader.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHITE_BANNER));
                    // Force the wand into the main hand
                    leader.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(com.hogwartsmagic.ExampleMod.HARRY_POTTER_WAND.get()));
                    leader.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null);
                    level.addFreshEntity(leader);
                }
                
                return 1;
            })
        );
    }
}
