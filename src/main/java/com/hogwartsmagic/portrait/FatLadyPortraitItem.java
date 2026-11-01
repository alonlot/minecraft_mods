package com.hogwartsmagic.portrait;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/** Hangs the Fat Lady on a wall exactly the way a painting is hung (a horizontal face, room for 2 x 4 blocks). */
public class FatLadyPortraitItem extends Item {
    public FatLadyPortraitItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("The Fat Lady");
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockPos hangPos = clicked.relative(face);
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player != null && !mayPlace(player, face, stack, hangPos)) {
            return InteractionResult.FAIL;
        }
        Level level = context.getLevel();
        FatLadyPortraitEntity portrait = new FatLadyPortraitEntity(level, hangPos, face);
        if (!portrait.survives()) {
            return InteractionResult.CONSUME;
        }
        if (!level.isClientSide) {
            portrait.playPlacementSound();
            level.gameEvent(player, GameEvent.ENTITY_PLACE, portrait.position());
            level.addFreshEntity(portrait);
        }
        stack.shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean mayPlace(Player player, Direction face, ItemStack stack, BlockPos pos) {
        return !face.getAxis().isVertical() && player.mayUseItemAt(pos, face, stack);
    }
}
