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

/** Hangs a living portrait on a wall exactly the way a painting is hung (a horizontal face, room for its canvas). */
public class PortraitItem extends Item {
    private final PortraitKind kind;

    public PortraitItem(Properties properties, PortraitKind kind) {
        super(properties);
        this.kind = kind;
    }

    public PortraitKind kind() {
        return kind;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(kind.displayName());
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
        PortraitEntity portrait = new PortraitEntity(level, kind, hangPos, face);
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
