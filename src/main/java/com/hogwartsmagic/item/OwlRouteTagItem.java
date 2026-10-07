package com.hogwartsmagic.item;

import com.hogwartsmagic.block.entity.OwlPerchBlockEntity;
import com.hogwartsmagic.entity.OwlEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

public class OwlRouteTagItem extends Item {
    private static final String FIRST_PERCH = "FirstPerch";
    private static final String ROUTE_A = "RouteA";
    private static final String ROUTE_B = "RouteB";

    public OwlRouteTagItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof OwlPerchBlockEntity perch)) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(FIRST_PERCH)) {
            tag.putLong(FIRST_PERCH, context.getClickedPos().asLong());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.translatable("message.hogwartsmagic.owl_route.first_set")
                        .withStyle(ChatFormatting.GOLD), true);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }

        BlockPos firstPos = BlockPos.of(tag.getLong(FIRST_PERCH));
        if (firstPos.equals(context.getClickedPos())) {
            return InteractionResult.SUCCESS;
        }

        if (context.getLevel().getBlockEntity(firstPos) instanceof OwlPerchBlockEntity firstPerch) {
            firstPerch.setLinkedPos(context.getClickedPos());
            firstPerch.setOwner(context.getPlayer());
            perch.setLinkedPos(firstPos);
            perch.setOwner(context.getPlayer());
            setRoute(stack, firstPos, context.getClickedPos());
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.translatable("message.hogwartsmagic.owl_route.linked")
                        .withStyle(ChatFormatting.GREEN), true);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }

        tag.remove(FIRST_PERCH);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity interactionTarget, InteractionHand usedHand) {
        return assignToOwl(stack, player, interactionTarget);
    }

    public static boolean hasRoute(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(ROUTE_A) && tag.contains(ROUTE_B);
    }

    public static boolean matchesRoute(ItemStack stack, BlockPos source, BlockPos target) {
        if (!hasRoute(stack)) {
            return false;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        BlockPos routeA = BlockPos.of(tag.getLong(ROUTE_A));
        BlockPos routeB = BlockPos.of(tag.getLong(ROUTE_B));
        return (routeA.equals(source) && routeB.equals(target)) || (routeA.equals(target) && routeB.equals(source));
    }

    public static BlockPos getRouteA(ItemStack stack) {
        if (!hasRoute(stack)) {
            return null;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return BlockPos.of(tag.getLong(ROUTE_A));
    }

    public static BlockPos getRouteB(ItemStack stack) {
        if (!hasRoute(stack)) {
            return null;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return BlockPos.of(tag.getLong(ROUTE_B));
    }

    public static InteractionResult assignToOwl(ItemStack stack, Player player, LivingEntity interactionTarget) {
        if (!(interactionTarget instanceof OwlEntity owl)) {
            return InteractionResult.PASS;
        }
        if (!owl.isTame() || !owl.isOwnedBy(player)) {
            if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(Component.translatable("message.hogwartsmagic.owl.route_owner_only"), true);
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        if (!hasRoute(stack)) {
            if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(Component.translatable("message.hogwartsmagic.owl.route_missing"), true);
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        if (!player.level().isClientSide) {
            owl.setRouteTag(stack.copyWithCount(1));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(Component.translatable("message.hogwartsmagic.owl.route_set"), true);
            }
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    private static void setRoute(ItemStack stack, BlockPos routeA, BlockPos routeB) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(FIRST_PERCH);
        tag.putLong(ROUTE_A, routeA.asLong());
        tag.putLong(ROUTE_B, routeB.asLong());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
