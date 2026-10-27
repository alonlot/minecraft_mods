package com.hogwartsmagic.item;

import com.hogwartsmagic.brewing.AdvancedPotions;
import com.hogwartsmagic.network.SpellPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Libatius Borage's textbook. Reading it (first use) unlocks the advanced cauldron recipes for that player. */
public class AdvancedPotionMakingItem extends Item {
    public AdvancedPotionMakingItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (AdvancedPotions.markRead(serverPlayer)) {
                serverPlayer.sendSystemMessage(Component.translatable("message.hogwartsmagic.advanced_potions.learned"));
            }
            SpellPackets.CHANNEL.send(
                    new SpellPackets.OpenAdvancedPotionsScreenPacket(),
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(serverPlayer)
            );
            player.getCooldowns().addCooldown(this, 10);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hogwartsmagic.advanced_potion_making.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
