package com.hogwartsmagic.item;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.polyjuice.PolyjuiceData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;

public class PolyjuicePotionItem extends Item {

    public static final int TRANSFORMATION_DELAY_TICKS = 60;
    /** Vanilla-style beneficial potion length (3:00). */
    public static final int BASE_DISGUISE_DURATION_TICKS = 20 * 60 * 3;
    /** Extended with redstone (8:00). */
    public static final int EXTENDED_DISGUISE_DURATION_TICKS = 20 * 60 * 8;

    /** @deprecated Use {@link #BASE_DISGUISE_DURATION_TICKS}. */
    @Deprecated
    public static final int DISGUISE_DURATION_TICKS = BASE_DISGUISE_DURATION_TICKS;

    public PolyjuicePotionItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static PotionContents defaultPotionContents() {
        return new PotionContents(
                Optional.empty(),
                Optional.empty(),
                List.of(new MobEffectInstance(ExampleMod.POLYJUICE_EFFECT.getHolder().get(), BASE_DISGUISE_DURATION_TICKS, 0)));
    }

    public static int getPolyjuiceEffectDurationTicks(ItemStack stack) {
        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        for (MobEffectInstance e : contents.getAllEffects()) {
            if (e.getEffect().is(ExampleMod.POLYJUICE_EFFECT.getKey())) {
                return e.getDuration();
            }
        }
        return BASE_DISGUISE_DURATION_TICKS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!PolyjuiceData.hasPolyjuiceTarget(stack)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(usedHand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, net.minecraft.world.level.Level level, LivingEntity livingEntity) {
        ItemStack result = super.finishUsingItem(stack, level, livingEntity);
        if (!(livingEntity instanceof Player player)) {
            return result;
        }
        if (!level.isClientSide && PolyjuiceData.hasPolyjuiceTarget(stack)) {
            int disguiseTicks = getPolyjuiceEffectDurationTicks(stack);
            PolyjuiceData.setPending(player, PolyjuiceData.getPolyjuiceTarget(stack), TRANSFORMATION_DELAY_TICKS, disguiseTicks);
            level.playSound(null, player.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.0f, 0.8f);
            if (player instanceof ServerPlayer serverPlayer) {
                SpellPackets.CHANNEL.send(new SpellPackets.SyncPolyjuiceDisguisePacket(serverPlayer.getUUID(), new net.minecraft.nbt.CompoundTag()),
                        PacketDistributor.TRACKING_ENTITY_AND_SELF.with(serverPlayer));
            }
        }
        return player.hasInfiniteMaterials() ? result : new ItemStack(Items.GLASS_BOTTLE);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag flag) {
        if (PolyjuiceData.hasPolyjuiceTarget(stack)) {
            int sec = getPolyjuiceEffectDurationTicks(stack) / 20;
            tooltipComponents.add(Component.translatable("effect.hogwartsmagic.polyjuice")
                    .append(": ")
                    .append(Component.literal(String.format("%d:%02d", sec / 60, sec % 60)))
                    .withStyle(ChatFormatting.BLUE));
            tooltipComponents.add(Component.translatable("item." + ExampleMod.MODID + ".polyjuice_potion.target", PolyjuiceData.describe(PolyjuiceData.getPolyjuiceTarget(stack))).withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            tooltipComponents.add(Component.translatable("item." + ExampleMod.MODID + ".polyjuice_potion.invalid").withStyle(ChatFormatting.RED));
        }
        tooltipComponents.add(Component.translatable("item." + ExampleMod.MODID + ".polyjuice_potion.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
