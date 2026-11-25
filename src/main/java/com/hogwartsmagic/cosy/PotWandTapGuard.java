package com.hogwartsmagic.cosy;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicWandItem;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A wand tap on a cooking pot enchants the ladle on the server, and must not also cast the selected spell. The
 * wand cast lives in {@code ModEvents.onRightClickBlock}, which runs on the client and cancels the event; but the
 * client still sends the use-item-on packet whatever the event result, so the server enchants the ladle.
 *
 * <p>This handler runs first (HIGH) on the client only and cancels the event with SUCCESS for a wand on a pot, so the
 * cast handler is skipped (it does not receive cancelled events). The server is left alone: cancelling there would stop
 * the block from being used. A sneaking wand still opens the spell menu, as vanilla skips block use while sneaking.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PotWandTapGuard {
    private PotWandTapGuard() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide || event.getEntity().isShiftKeyDown()
                || !MagicWandItem.isAnyWand(event.getItemStack())) {
            return;
        }
        if (event.getLevel().getBlockState(event.getPos()).getBlock() instanceof CookingPotBlock) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }
}
