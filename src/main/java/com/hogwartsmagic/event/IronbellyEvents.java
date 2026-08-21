package com.hogwartsmagic.event;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.UkrainianIronbellyEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * What the Ironbelly can hear, and how it gets loose.
 *
 * <p>It is nearly blind, so noise is the whole game: sprinting and breaking blocks bring it, and
 * walking does not. That makes the deepest cavern a different problem from anywhere else in the
 * mod - the way past is to slow down, which is not a thing any other mob here asks of you.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class IronbellyEvents {

    /** How far a broken block carries. */
    private static final double BREAK_RANGE = 26.0D;
    /** How far a running man carries. Less than a pickaxe, more than a walk. */
    private static final double SPRINT_RANGE = 14.0D;
    /** How far from a stub its dragon is looked for. */
    private static final double ANCHOR_RANGE = 24.0D;

    private IronbellyEvents() {
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();

        for (UkrainianIronbellyEntity dragon : level.getEntitiesOfClass(
                UkrainianIronbellyEntity.class,
                new AABB(pos).inflate(BREAK_RANGE))) {
            dragon.heard(net.minecraft.world.phys.Vec3.atCenterOf(pos));
        }

        if (!event.getState().is(Blocks.LIGHTNING_ROD)) {
            return;
        }
        // An anchor stub. If it was the last one holding a chained dragon, that dragon leaves.
        List<UkrainianIronbellyEntity> dragons = level.getEntitiesOfClass(
                UkrainianIronbellyEntity.class, new AABB(pos).inflate(ANCHOR_RANGE));
        for (UkrainianIronbellyEntity dragon : dragons) {
            if (!dragon.isChained() || dragon.anchor() == null) {
                continue;
            }
            int remaining = countStubs(level, dragon.anchor(), pos);
            if (remaining > 0) {
                if (event.getPlayer() instanceof ServerPlayer player) {
                    player.displayClientMessage(Component
                            .translatable("entity.hogwartsmagic.ukrainian_ironbelly.chain",
                                    remaining)
                            .withStyle(ChatFormatting.GOLD), true);
                }
                continue;
            }
            dragon.free(event.getPlayer() instanceof ServerPlayer p ? p : null);
            if (event.getPlayer() instanceof ServerPlayer player) {
                player.displayClientMessage(Component
                        .translatable("entity.hogwartsmagic.ukrainian_ironbelly.freed")
                        .withStyle(ChatFormatting.GOLD), false);
            }
        }
    }

    /** Stubs still standing around an anchor, not counting the one being broken right now. */
    private static int countStubs(ServerLevel level, BlockPos anchor, BlockPos breaking) {
        int found = 0;
        int r = 14;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -4; dy <= 6; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    cursor.set(anchor.getX() + dx, anchor.getY() + dy, anchor.getZ() + dz);
                    if (cursor.equals(breaking)) {
                        continue;
                    }
                    if (level.getBlockState(cursor).is(Blocks.LIGHTNING_ROD)) {
                        found++;
                    }
                }
            }
        }
        return found;
    }

    /** Running is heard. Walking is not, which is the way past. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 10 != 0
                || !player.isSprinting()) {
            return;
        }
        for (UkrainianIronbellyEntity dragon : player.serverLevel().getEntitiesOfClass(
                UkrainianIronbellyEntity.class,
                player.getBoundingBox().inflate(SPRINT_RANGE))) {
            dragon.heard(player.position());
        }
    }
}
