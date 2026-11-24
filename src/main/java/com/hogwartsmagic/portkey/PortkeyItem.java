package com.hogwartsmagic.portkey;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * A Portkey: a battered old boot that, held for three seconds, hooks you (and everyone standing within two blocks of
 * you) off your feet and drops you where it is bound. Sneak and use it to bind it to the block you stand on; the
 * tooltip then reads "Bound: X Y Z in the Overworld". After a journey it rests for a minute.
 *
 * <p>Holding use is the charge; letting go, switching slot, or being taken along by someone else's Portkey cancels
 * it. The journey itself is run on the server by {@link PortkeyJourneys}; this class only starts it.
 */
public class PortkeyItem extends Item {

    /** Long enough that holding never "finishes" the item: the journey ends the hold, not the duration. */
    private static final int USE_DURATION = 72000;

    public PortkeyItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** Whether this Portkey can be bound (the Old Portkey cannot: it already knows where it goes). */
    protected boolean bindable() {
        return true;
    }

    /** Whether this stack is set to go somewhere. */
    protected boolean active(ItemStack stack) {
        return PortkeyData.binding(stack).isPresent();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (bindable() && player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                String dim = level.dimension().location().toString();
                if (!PortkeyRules.canBindIn(dim)) {
                    serverPlayer.displayClientMessage(Component.translatable(
                            "message.hogwartsmagic.portkey.cannot_bind").withStyle(ChatFormatting.RED), true);
                } else {
                    BlockPos feet = serverPlayer.blockPosition();
                    PortkeyData.bind(stack, level.dimension(), feet, serverPlayer.getYRot());
                    serverPlayer.displayClientMessage(Component.translatable("message.hogwartsmagic.portkey.bound")
                            .withStyle(ChatFormatting.AQUA), true);
                    PortkeyFx.bind(serverPlayer);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!active(stack)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.portkey.unbound")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        long left = PortkeyRules.cooldownLeft(level.getGameTime(), PortkeyData.readyAt(stack));
        if (left > 0L) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.portkey.cooldown",
                        PortkeyRules.cooldownSeconds(left)).withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && !PortkeyJourneys.begin(serverPlayer, hand, true)) {
            serverPlayer.stopUsingItem();
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW; // both arms out in third person, holding the boot up between them
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        Optional<PortkeyData.Binding> binding = PortkeyData.binding(stack);
        if (binding.isPresent()) {
            PortkeyData.Binding b = binding.get();
            String dim = b.dim().location().toString();
            Component where = Component.translatableWithFallback(PortkeyRules.dimensionKey(dim),
                    PortkeyRules.fallbackDimensionName(dim));
            lines.add(Component.translatable("tooltip.hogwartsmagic.portkey.bound", b.pos().getX(), b.pos().getY(),
                    b.pos().getZ(), where).withStyle(ChatFormatting.AQUA));
            lines.add(Component.translatable("tooltip.hogwartsmagic.portkey.hold").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("tooltip.hogwartsmagic.portkey.unbound").withStyle(ChatFormatting.GRAY));
        }
    }
}
