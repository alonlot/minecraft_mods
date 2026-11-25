package com.hogwartsmagic.item;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.DespairBasinBlockEntity;
import com.hogwartsmagic.locket.GobletFill;
import com.hogwartsmagic.locket.GobletRules;
import com.hogwartsmagic.locket.LocketCaves;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The crystal goblet of the Locket cave. Dipped in the basin it holds a cup of the potion; drunk, it empties.
 * It remembers its basin and crumbles back to a goblet block beside it the moment it leaves the cave.
 */
public class CaveGobletItem extends Item {
    private static final int CHECK_INTERVAL = 20;

    public CaveGobletItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static GobletFill fillOf(ItemStack stack) {
        return stack.getOrDefault(ExampleMod.GOBLET_FILL.get(), GobletFill.EMPTY);
    }

    public static void setFill(ItemStack stack, GobletFill fill) {
        if (fill == GobletFill.EMPTY) {
            stack.remove(ExampleMod.GOBLET_FILL.get());
        } else {
            stack.set(ExampleMod.GOBLET_FILL.get(), fill);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (fillOf(stack) == GobletFill.EMPTY) {
            // An empty goblet dipped in the water: it fills only from the cave lake (the basin dip is a block use).
            BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
            if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
                return InteractionResultHolder.pass(stack);
            }
            if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
                LocketCaves.dipGoblet(server, sp, stack, hit.getBlockPos());
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level instanceof ServerLevel server && entity instanceof ServerPlayer player) {
            drinkFrom(stack, server, player);
        }
        return stack;
    }

    /** The server half of finishing a drink. Tests call it directly: a mock player cannot run the use animation. */
    public static void drinkFrom(ItemStack stack, ServerLevel level, ServerPlayer player) {
        GobletFill fill = fillOf(stack);
        if (fill == GobletFill.EMPTY) {
            return;
        }
        setFill(stack, GobletFill.EMPTY);
        if (fill == GobletFill.POTION) {
            DespairBasinBlockEntity basin = basinOf(level, player.blockPosition());
            if (basin != null) {
                basin.drink(player);
            }
        }
        if (fill == GobletFill.WATER && LocketCaves.isThirsty(player)) {
            DespairBasinBlockEntity basin = basinOf(level, player.blockPosition());
            if (basin != null) {
                LocketCaves.slakeThirst(player, basin);
            }
        }
    }

    /** The basin of the cave this holder stands in, if any (loaded chunks only). */
    @Nullable
    private static DespairBasinBlockEntity basinOf(ServerLevel level, BlockPos near) {
        return LocketCaves.findBasinFor(level, near).orElse(null);
    }

    /** A goblet outside a cave crumbles: particles, a sound, and the item is gone. The basin restores its own. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        if (!(level instanceof ServerLevel server) || !(holder instanceof ServerPlayer player)
                || (player.tickCount + slot) % CHECK_INTERVAL != 0) {
            return;
        }
        DespairBasinBlockEntity basin = basinOf(server, player.blockPosition());
        boolean inCave = basin != null && basin.layout().inCave(player.blockPosition());
        if (GobletRules.leavesCave(inCave)) {
            server.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1f, 0.7f);
            server.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
            stack.shrink(1);
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.goblet_crumbles"), true);
        }
    }
}
