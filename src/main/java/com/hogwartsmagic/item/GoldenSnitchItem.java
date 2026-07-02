package com.hogwartsmagic.item;

import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.entity.GoldenSnitchEntity;
import com.hogwartsmagic.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Golden Snitch item. Right-click to release it — it takes flight around the release
 * point and can only be recovered by catching it while riding a broom.
 */
public class GoldenSnitchItem extends Item {

    public GoldenSnitchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            GoldenSnitchEntity snitch = ModEntities.GOLDEN_SNITCH.get().create(level);
            if (snitch == null) {
                return InteractionResultHolder.pass(stack);
            }
            Vec3 pos = player.getEyePosition().add(player.getLookAngle().scale(1.5));
            snitch.setPos(pos.x, pos.y, pos.z);
            snitch.markReleased(BlockPos.containing(pos));
            level.addFreshEntity(snitch);
            level.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.SNITCH_FLUTTER.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
            player.displayClientMessage(Component.translatable("hogwartsmagic.snitch.released"), true);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("hogwartsmagic.snitch.tooltip"));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
