package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.pensieve.net.PensieveCapturePacket;
import com.hogwartsmagic.pensieve.net.PensieveNet;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A corked glass phial. Sneak and use it with a wand in the other hand to draw the last minute out of your own head
 * (the server decides; see {@code MemoryCapture}). A wand used with a phial in the other hand does the same, from
 * {@code ModEvents.handleWandUse}.
 */
public class EmptyMemoryPhialItem extends Item {
    public EmptyMemoryPhialItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (MagicWandItem.isAnyWand(player.getItemInHand(other))) {
            if (level.isClientSide()) {
                PensieveNet.sendToServer(new PensieveCapturePacket());
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (level.isClientSide()) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.pensieve.capture.need_wand"), true);
        }
        return InteractionResultHolder.fail(stack);
    }
}
