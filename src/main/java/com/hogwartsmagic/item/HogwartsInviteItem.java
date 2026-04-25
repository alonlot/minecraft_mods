package com.hogwartsmagic.item;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

public class HogwartsInviteItem extends Item {
    private static final String TAG_RECIPIENT = "Recipient";

    public HogwartsInviteItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ItemStack createFor(ServerPlayer player) {
        ItemStack stack = new ItemStack(ExampleMod.HOGWARTS_INVITE.get());
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString(TAG_RECIPIENT, player.getGameProfile().getName());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static String getRecipient(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String recipient = tag.getString(TAG_RECIPIENT);
        return recipient.isEmpty() ? "Young Witch or Wizard" : recipient;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            SpellPackets.CHANNEL.send(
                    new SpellPackets.OpenHogwartsInviteScreenPacket(getRecipient(stack)),
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(serverPlayer)
            );
            player.getCooldowns().addCooldown(this, 20);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.hogwartsmagic.hogwarts_invite.named", getRecipient(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hogwartsmagic.hogwarts_invite.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
