package com.hogwartsmagic.item;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.polyjuice.PolyjuiceData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class SampleCollectorItem extends Item {

    public SampleCollectorItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity interactionTarget, InteractionHand usedHand) {
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack sample = new ItemStack(ExampleMod.POLYJUICE_SAMPLE.get());
        PolyjuiceData.setSample(sample, PolyjuiceData.createSample(interactionTarget));

        if (!player.isCreative()) {
            player.setItemInHand(usedHand, sample);
        } else if (!player.addItem(sample)) {
            player.drop(sample, false);
        }

        player.level().playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL_DRAGONBREATH, SoundSource.PLAYERS, 0.8f, 1.1f);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("item." + ExampleMod.MODID + ".sample_collector.collected", interactionTarget.getName()).withStyle(ChatFormatting.LIGHT_PURPLE),
                    true
            );
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag flag) {
        tooltipComponents.add(Component.translatable("item." + ExampleMod.MODID + ".sample_collector.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
