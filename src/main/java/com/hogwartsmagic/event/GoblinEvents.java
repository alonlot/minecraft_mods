package com.hogwartsmagic.event;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.GoblinGuardEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ties goblin guards to the vaults they are standing in front of.
 *
 * <p>It used to work by proximity alone: any chest opened within
 * {@link GoblinGuardEntity#THEFT_RADIUS} blocks of any guard was a robbery, on the assumption that
 * guards are only ever inside the bank. They are not - they never despawn and they follow, so a
 * player who led one home could never open their own chest again, and it fired on ender chests,
 * which are somebody's private storage.
 *
 * <p>It asks {@link com.hogwartsmagic.gringotts.GringottsAlarm} whether the bank actually owns the
 * ground now, and ender chests are nobody's business but their owner's.
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
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        // Sneaking with something in hand places or uses it against the chest instead of opening
        // it, which is not a robbery (the same rule the merfolk hoard uses).
        if (player.isSecondaryUseActive() && !player.getMainHandItem().isEmpty()) {
            return;
        }
        BlockState state = player.level().getBlockState(event.getPos());
        if (!isVaultContainer(state)) {
            return;
        }
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)
                || !(player instanceof net.minecraft.server.level.ServerPlayer thief)) {
            return;
        }
        com.hogwartsmagic.gringotts.GringottsAlarm alarm =
                com.hogwartsmagic.gringotts.GringottsAlarm.get(level);
        if (!alarm.inside(event.getPos())) {
            return;      // not the bank's ground, not the bank's business
        }
        if (com.hogwartsmagic.gringotts.GringottsDisguise.isGoblin(thief)) {
            return;      // a goblin at a vault chest is a withdrawal, the same as at its door
        }
        alarm.raise(level, thief, event.getPos(), 1, "gringotts.alarm.chest");
    }

    /**
     * Killing bank staff on the bank's own ground is one of the things that heats it up. LOWEST with
     * a cancel guard, so a death another handler prevented counts for nothing.
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onStaffKilled(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.isCanceled()
                || !(event.getEntity() instanceof com.hogwartsmagic.entity.GoblinEntity
                        || event.getEntity() instanceof GoblinGuardEntity)
                || !(event.getEntity().level() instanceof net.minecraft.server.level.ServerLevel level)
                || !(event.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer killer)) {
            return;
        }
        com.hogwartsmagic.gringotts.GringottsAlarm alarm =
                com.hogwartsmagic.gringotts.GringottsAlarm.get(level);
        net.minecraft.core.BlockPos where = event.getEntity().blockPosition();
        if (!alarm.inside(where)) {
            return;
        }
        alarm.raise(level, killer, where, 1, "gringotts.alarm.staff");
    }

    private static boolean isVaultContainer(BlockState state) {
        return state.getBlock() instanceof ChestBlock
                || state.getBlock() instanceof BarrelBlock
                || state.getBlock() instanceof ShulkerBoxBlock;
    }
}
