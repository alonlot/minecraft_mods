package com.hogwartsmagic.event;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.GoblinGuardEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ties goblin guards to the vaults they are standing in front of.
 *
 * <p>Rather than tracking a Gringotts region (which would have to be persisted
 * and kept in sync), a guard simply treats any chest opened within
 * {@link GoblinGuardEntity#THEFT_RADIUS} blocks of itself as a robbery. Guards
 * are only ever placed inside the bank, so in practice that is the vault.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class GoblinEvents {

    private GoblinEvents() {
    }

    @SubscribeEvent
    public static void onChestOpened(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.isCreative() || player.isSpectator()) {
            return;
        }
        BlockState state = player.level().getBlockState(event.getPos());
        if (!isVaultContainer(state)) {
            return;
        }
        // Opening a strongbox under a guard's nose is all the provocation needed.
        GoblinGuardEntity.alertGuards(player, player, GoblinGuardEntity.THEFT_RADIUS);
    }

    private static boolean isVaultContainer(BlockState state) {
        return state.getBlock() instanceof ChestBlock
                || state.getBlock() instanceof BarrelBlock
                || state.getBlock() instanceof ShulkerBoxBlock
                || state.getBlock() instanceof EnderChestBlock;
    }
}
