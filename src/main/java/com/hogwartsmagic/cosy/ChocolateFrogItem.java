package com.hogwartsmagic.cosy;

import com.hogwartsmagic.entity.ModEntities;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A box of Chocolate Frog. Using it opens it: you always get a random Famous Wizard Card (into the pack, or at
 * your feet when the pack is full), and a small chocolate frog jumps out in front of you.
 */
public class ChocolateFrogItem extends Item {
    public ChocolateFrogItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack box = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(box, true);
        }
        // The card first: shrinking the last box empties the held slot, which Inventory.add would then fill, and the
        // caller clears the held slot of an emptied stack again, so the card would vanish.
        giveOrDrop(player, WizardCardItem.create(WizardCards.roll(level.random)));
        if (!player.getAbilities().instabuild) {
            box.shrink(1);
        }
        spawnFrog(level, player);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS, 0.8F, 1.1F);
        return InteractionResultHolder.sidedSuccess(box, false);
    }

    /** Into the pack if it fits, otherwise on the ground at the feet of the player. Never lost. */
    static void giveOrDrop(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack) || !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    private static void spawnFrog(Level level, Player player) {
        ChocolateFrogEntity frog = ModEntities.CHOCOLATE_FROG.get().create(level);
        if (frog == null) {
            return;
        }
        Vec3 ahead = Vec3.directionFromRotation(0.0F, player.getYRot()).scale(0.9);
        frog.moveTo(player.getX() + ahead.x, player.getY() + 0.1, player.getZ() + ahead.z, player.getYRot(), 0.0F);
        level.addFreshEntity(frog);
    }
}
