package com.hogwartsmagic.item;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.BroomEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.spell.motion.AlatusRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class BroomItem extends Item {

    public final int broomType;

    public BroomItem(Properties properties, int broomType) {
        super(properties);
        this.broomType = broomType;
    }

    /** The broom's Alatus quality (AlatusRules.QUALITY_*); a broom without the component is normal. */
    public static int quality(ItemStack stack) {
        Integer quality = stack.get(ExampleMod.BROOM_QUALITY.get());
        return quality == null ? AlatusRules.QUALITY_NORMAL : AlatusRules.clampQuality(quality);
    }

    public static void setQuality(ItemStack stack, int quality) {
        int clamped = AlatusRules.clampQuality(quality);
        if (clamped == AlatusRules.QUALITY_NORMAL) {
            stack.remove(ExampleMod.BROOM_QUALITY.get());
        } else {
            stack.set(ExampleMod.BROOM_QUALITY.get(), clamped);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            Vec3 pos = player.position().add(player.getLookAngle().scale(1.5)).add(0.0, 1.0, 0.0);
            BroomEntity broom = new BroomEntity(ModEntities.BROOM.get(), level);
            broom.setBroomType(this.broomType);
            broom.setQuality(quality(itemstack));
            broom.moveTo(pos.x, pos.y, pos.z, player.getYRot(), 0.0F);
            level.addFreshEntity(broom);

            if (!player.getAbilities().instabuild) {
                itemstack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
    }

    @Override
    public Component getName(ItemStack stack) {
        int quality = quality(stack);
        Component base = super.getName(stack);
        if (quality == AlatusRules.QUALITY_FINE) {
            return Component.translatable("item.hogwartsmagic.broom.quality.fine", base);
        }
        if (quality == AlatusRules.QUALITY_MASTERWORK) {
            return Component.translatable("item.hogwartsmagic.broom.quality.masterwork", base);
        }
        return base;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        int quality = quality(stack);
        if (quality == AlatusRules.QUALITY_FINE) {
            tooltip.add(Component.translatable("item.hogwartsmagic.broom.quality.fine.tooltip").withStyle(ChatFormatting.AQUA));
        } else if (quality == AlatusRules.QUALITY_MASTERWORK) {
            tooltip.add(Component.translatable("item.hogwartsmagic.broom.quality.masterwork.tooltip").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }
}
