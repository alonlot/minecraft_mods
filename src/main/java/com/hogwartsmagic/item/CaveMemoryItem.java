package com.hogwartsmagic.item;

import com.hogwartsmagic.locket.DespairRules;
import com.hogwartsmagic.locket.LocketCaves;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Optional;

/** A vial of memory. Drunk, it shows a short vision and becomes a Cave Map to the nearest unlooted Locket cave. */
public class CaveMemoryItem extends Item {
    private static final int COOLDOWN_TICKS = 40;
    private static final String[] VISION = {
            "A cliff above a black, restless sea...",
            "Salt spray, and a crack in the rock that is not quite there...",
            "Something waits beneath still water."
    };

    public CaveMemoryItem(Properties p) {
        super(p.stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) {
            return stack;
        }
        ServerLevel server = (ServerLevel) level;
        Optional<BlockPos> found = server.dimension() == Level.OVERWORLD
                ? LocketCaves.findNearestUnlootedCave(server, player.blockPosition())
                : Optional.empty();
        if (found.isEmpty()) {
            player.displayClientMessage(Component.literal("The memory is too faint..."), true);
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            return stack;
        }
        BlockPos pos = found.get();
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, DespairRules.VISION_TICKS, 0));
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
        for (String line : VISION) {
            player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
        }
        ItemStack map = MapItem.create(server, pos.getX(), pos.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(server, map);
        MapItemSavedData.addTargetDecoration(map, pos, "+", MapDecorationTypes.RED_X);
        map.set(DataComponents.CUSTOM_NAME, Component.translatable("item.hogwartsmagic.cave_map"));
        stack.shrink(1);
        if (!player.getInventory().add(map)) {
            player.drop(map, false);
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }
}
