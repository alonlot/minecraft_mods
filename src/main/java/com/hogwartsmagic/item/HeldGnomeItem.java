package com.hogwartsmagic.item;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.GnomeEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * The invisible placeholder in your hand while you hold a garden gnome by the ankle. It draws nothing: the
 * gnome itself hangs at your hand. It exists so that vanilla item use carries "hold to whirl, release to fling"
 * to the server without a new packet. It can never be kept: it cannot be dropped, and it removes itself the
 * moment no gnome is hanging from its owner.
 */
public class HeldGnomeItem extends Item {
    /** Effectively endless: the whirl lasts as long as you hold the button. */
    public static final int USE_TICKS = 72000;

    public HeldGnomeItem(Properties properties) {
        super(properties);
    }

    /** The gnome this entity is carrying, or null. */
    @Nullable
    public static GnomeEntity carriedBy(Entity e) {
        for (Entity passenger : e.getPassengers()) {
            if (passenger instanceof GnomeEntity g) {
                return g;
            }
        }
        return null;
    }

    /** Clears every placeholder from the player's inventory, offhand included. */
    public static void removeFrom(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() instanceof HeldGnomeItem) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        if (p.containerMenu != null) {
            // An open chest or other menu: the placeholder may have been shuffled into one of its slots.
            for (var slot : p.containerMenu.slots) {
                if (slot.getItem().getItem() instanceof HeldGnomeItem) {
                    slot.set(ItemStack.EMPTY);
                }
            }
        }
        if (p.containerMenu != null && p.containerMenu.getCarried().getItem() instanceof HeldGnomeItem) {
            p.containerMenu.setCarried(ItemStack.EMPTY);
        }
        if (p.containerMenu != null && !p.level().isClientSide) {
            p.containerMenu.broadcastChanges();
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (carriedBy(player) == null) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide) {
            return;
        }
        GnomeEntity g = carriedBy(entity);
        if (g != null) {
            g.spinTick();
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }
        GnomeEntity g = carriedBy(entity);
        if (g != null) {
            g.throwFrom(player, USE_TICKS - timeLeft);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof Player p && carriedBy(entity) == null) {
            removeFrom(p);
        }
    }

    /** It can never be thrown away: the gnome would be left hanging from nothing. */
    @Override
    public boolean onDroppedByPlayer(ItemStack stack, Player player) {
        return false;
    }

    /** One that reaches the ground by any route (death, hopper, dispenser) simply vanishes. */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (!entity.level().isClientSide) {
            entity.discard();
        }
        return true;
    }
}
