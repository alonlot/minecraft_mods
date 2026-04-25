package com.hogwartsmagic.item;

import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.BroomEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class BroomItem extends Item {

    public final int broomType;

    public BroomItem(Properties properties, int broomType) {
        super(properties);
        this.broomType = broomType;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            Vec3 pos = player.position().add(player.getLookAngle().scale(1.5)).add(0.0, 1.0, 0.0);
            BroomEntity broom = new BroomEntity(ModEntities.BROOM.get(), level);
            broom.setBroomType(this.broomType);
            broom.moveTo(pos.x, pos.y, pos.z, player.getYRot(), 0.0F);
            level.addFreshEntity(broom);
            
            if (!player.getAbilities().instabuild) {
                itemstack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
    }
}
