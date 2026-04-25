package com.hogwartsmagic.item;

import com.hogwartsmagic.network.SpellPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class HogwartsGuideItem extends Item {
    public HogwartsGuideItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            SpellPackets.CHANNEL.send(
                    new SpellPackets.OpenHogwartsGuideScreenPacket(),
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(serverPlayer)
            );
            player.getCooldowns().addCooldown(this, 10);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hogwartsmagic.hogwarts_guide.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
