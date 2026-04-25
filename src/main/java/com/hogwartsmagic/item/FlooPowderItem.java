package com.hogwartsmagic.item;

import com.hogwartsmagic.block.entity.FlooFireplaceBlockEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public class FlooPowderItem extends Item {
    public FlooPowderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof FlooFireplaceBlockEntity fireplace)) {
            return InteractionResult.PASS;
        }
        if (!context.getLevel().isClientSide && context.getPlayer() != null) {
            context.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.translatable("message.hogwartsmagic.floo.drop_powder"), true);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
