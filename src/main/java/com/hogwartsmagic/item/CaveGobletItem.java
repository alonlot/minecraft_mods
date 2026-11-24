package com.hogwartsmagic.item;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.DespairBasinBlockEntity;
import com.hogwartsmagic.locket.GobletFill;
import com.hogwartsmagic.locket.GobletRules;
import com.hogwartsmagic.locket.LocketCaves;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
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

    /** A goblet lifted from a block or dipped remembers the basin it belongs to. */
    public static void setHome(ItemStack stack, BlockPos basin) {
        stack.set(ExampleMod.GOBLET_HOME.get(), basin.immutable());
    }

    @Nullable
    public static BlockPos homeOf(ItemStack stack) {
        return stack.get(ExampleMod.GOBLET_HOME.get());
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
            return InteractionResultHolder.pass(stack);
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
            DespairBasinBlockEntity basin = basinOf(stack, level, player.blockPosition());
            if (basin != null) {
                basin.drink(player);
            }
        }
        // WATER only empties the goblet for now: the lake dip and the thirst cure are wired separately.
    }

    @Nullable
    private static DespairBasinBlockEntity basinOf(ItemStack stack, ServerLevel level, BlockPos near) {
        BlockPos home = homeOf(stack);
        if (home != null && level.hasChunkAt(home) && level.getBlockEntity(home) instanceof DespairBasinBlockEntity be) {
            return be;
        }
        return LocketCaves.findBasinFor(level, near).orElse(null);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        if (!(level instanceof ServerLevel server) || !(holder instanceof ServerPlayer player)
                || (player.tickCount + slot) % CHECK_INTERVAL != 0) {
            return;
        }
        DespairBasinBlockEntity basin = basinOf(stack, server, player.blockPosition());
        if (basin == null) {
            // Outside every cave. A goblet with a home crumbles back to it; one with none just crumbles.
            crumble(stack, server, player, homeOf(stack));
            return;
        }
        if (homeOf(stack) == null) {
            setHome(stack, basin.getBlockPos());
        }
        if (GobletRules.leavesCave(basin.layout().inCave(player.blockPosition()))) {
            crumble(stack, server, player, basin.getBlockPos());
        }
    }

    /** Takes the goblet from the player and sets a goblet block back beside its basin. */
    private static void crumble(ItemStack stack, ServerLevel level, ServerPlayer player, @Nullable BlockPos basin) {
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1f, 0.7f);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
        stack.shrink(1);
        if (basin != null) {
            placeBesideBasin(level, basin);
        }
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.goblet_crumbles"), true);
    }

    /** The nearest free cell next to the basin gets a goblet block. Returns where it went, or null. */
    @Nullable
    public static BlockPos placeBesideBasin(ServerLevel level, BlockPos basin) {
        if (!level.hasChunkAt(basin)) {
            level.getChunk(basin); // the basin is in a generated cave, so this is a load from disk
        }
        if (!level.getBlockState(basin).is(ExampleMod.DESPAIR_BASIN.get())) {
            return null;
        }
        BlockState goblet = ExampleMod.CRYSTAL_GOBLET.get().defaultBlockState();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos at = basin.offset(dx, dy, dz);
                    double dist = at.distSqr(basin);
                    if (dist == 0 || dist >= bestDist || !level.hasChunkAt(at)) {
                        continue;
                    }
                    if (level.getBlockState(at).isAir() && level.getFluidState(at).isEmpty()
                            && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
                        best = at;
                        bestDist = dist;
                    }
                }
            }
        }
        if (best != null) {
            level.setBlock(best, goblet, Block.UPDATE_ALL);
        }
        return best;
    }
}
