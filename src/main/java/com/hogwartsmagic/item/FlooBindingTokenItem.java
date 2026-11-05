package com.hogwartsmagic.item;

import com.hogwartsmagic.block.entity.FlooFireplaceBlockEntity;
import com.hogwartsmagic.network.SpellPackets;
import net.minecraft.core.component.DataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

public class FlooBindingTokenItem extends Item {
    public FlooBindingTokenItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof FlooFireplaceBlockEntity fireplace)) {
            return InteractionResult.PASS;
        }
        if (!fireplace.isNamed()) {
            if (!context.getLevel().isClientSide && context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                SpellPackets.CHANNEL.send(new SpellPackets.OpenFlooNamingScreenPacket(context.getClickedPos(), fireplace.getFireplaceName()),
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(serverPlayer));
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }

        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains("FirstFireplace")) {
            tag.putLong("FirstFireplace", context.getClickedPos().asLong());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            if (!context.getLevel().isClientSide && context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.translatable("message.hogwartsmagic.floo.first_set")
                        .withStyle(ChatFormatting.GOLD), true);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }

        BlockPos firstPos = BlockPos.of(tag.getLong("FirstFireplace"));
        if (firstPos.equals(context.getClickedPos())) {
            return InteractionResult.SUCCESS;
        }

        // getBlockEntity on a far, unloaded fireplace would load its chunk; keep the stored one instead.
        if (!context.getLevel().isLoaded(firstPos)) {
            if (!context.getLevel().isClientSide && context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.translatable("message.hogwartsmagic.floo.first_unloaded")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        if (context.getLevel().getBlockEntity(firstPos) instanceof FlooFireplaceBlockEntity firstFireplace) {
            firstFireplace.addLink(context.getClickedPos(), context.getPlayer());
            fireplace.addLink(firstPos, context.getPlayer());
            tag.remove("FirstFireplace");
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            if (!context.getLevel().isClientSide && context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.translatable("message.hogwartsmagic.floo.linked")
                        .withStyle(ChatFormatting.GREEN), true);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }

        tag.remove("FirstFireplace");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
