package com.hogwartsmagic.locket;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.ConcealedRockBlock;
import com.hogwartsmagic.block.entity.CaveMarkerBlockEntity;
import com.hogwartsmagic.block.entity.DespairBasinBlockEntity;
import com.hogwartsmagic.item.SlytherinsLocketItem;
import com.hogwartsmagic.spell.combat.CombatEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Optional;

/** World-facing helpers for the Locket cave (the pure rules live beside this class). Server only. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LocketCaves {
    public static final int REVEAL_RADIUS = 48;
    private static final int REVEAL_TICKS = 1200;

    private LocketCaves() {
    }

    /** Reveals Concealed Rock and hidden chains near center. Only walks loaded chunk block entities. */
    public static int reveal(ServerLevel level, Vec3 center, int radius) {
        int count = 0;
        double r2 = (double) radius * radius;
        int minCx = SectionPos.blockToSectionCoord(center.x - radius);
        int maxCx = SectionPos.blockToSectionCoord(center.x + radius);
        int minCz = SectionPos.blockToSectionCoord(center.z - radius);
        int maxCz = SectionPos.blockToSectionCoord(center.z + radius);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : List.copyOf(chunk.getBlockEntities().values())) {
                    if (!(be instanceof CaveMarkerBlockEntity)) {
                        continue;
                    }
                    BlockPos p = be.getBlockPos();
                    if (p.distToCenterSqr(center) > r2) {
                        continue;
                    }
                    BlockState s = level.getBlockState(p);
                    if (!s.hasProperty(ConcealedRockBlock.REVEALED) || s.getValue(ConcealedRockBlock.REVEALED)) {
                        continue;
                    }
                    level.setBlock(p, s.setValue(ConcealedRockBlock.REVEALED, true), Block.UPDATE_ALL);
                    level.scheduleTick(p, s.getBlock(), REVEAL_TICKS);
                    count++;
                }
            }
        }
        return count;
    }

    /** The nearest Basin of Despair whose cave contains pos, searching loaded chunks only. */
    public static Optional<DespairBasinBlockEntity> findBasinFor(ServerLevel level, BlockPos pos) {
        int r = CaveGeometry.CAVE_RADIUS;
        int minCx = SectionPos.blockToSectionCoord(pos.getX() - r);
        int maxCx = SectionPos.blockToSectionCoord(pos.getX() + r);
        int minCz = SectionPos.blockToSectionCoord(pos.getZ() - r);
        int maxCz = SectionPos.blockToSectionCoord(pos.getZ() + r);
        DespairBasinBlockEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : List.copyOf(chunk.getBlockEntities().values())) {
                    if (!(be instanceof DespairBasinBlockEntity basin)) {
                        continue;
                    }
                    BlockPos d = pos.subtract(be.getBlockPos());
                    double dist = pos.distSqr(be.getBlockPos());
                    if (CaveGeometry.inCave(d.getX(), d.getY(), d.getZ()) && dist < bestDist) {
                        best = basin;
                        bestDist = dist;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Lifts Unquenchable Thirst, with the drink sound and message. Returns whether it was actually removed. */
    public static boolean cureThirst(ServerPlayer p) {
        if (!CombatEffects.cureUnquenchableThirst(p)) {
            return false;
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1f, 0.8f);
        p.displayClientMessage(Component.translatable("message.hogwartsmagic.thirst_cured"), true);
        return true;
    }

    /** A glass bottle used on cave lake water while thirsty drinks the black water instead of filling. */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        if (!(e.getEntity() instanceof ServerPlayer player) || !(e.getLevel() instanceof ServerLevel level)
                || !e.getItemStack().is(Items.GLASS_BOTTLE)) {
            return;
        }
        var thirst = ExampleMod.UNQUENCHABLE_THIRST.getHolder().orElse(null);
        if (thirst == null || !player.hasEffect(thirst)) {
            return;
        }
        if (!(player.pick(5, 0, true) instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
            return;
        }
        Optional<DespairBasinBlockEntity> found = findBasinFor(level, hit.getBlockPos());
        if (found.isEmpty()) {
            return;
        }
        BlockPos d = player.blockPosition().subtract(found.get().getBlockPos());
        if (!CaveGeometry.inCave(d.getX(), d.getY(), d.getZ())) {
            return;
        }
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (cureThirst(player) && !found.get().isAmbushActive()) {
            found.get().startAmbush(player);
        }
    }

    /** Dropped Horcrux items (the Locket) neither burn, explode nor despawn. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof ItemEntity item)
                || !item.getItem().is(SlytherinsLocketItem.DARK_LORD_HORCRUXES)) {
            return;
        }
        item.setInvulnerable(true);
        item.setUnlimitedLifetime();
    }

    /** Task 11 records the looted cave here. */
    public static void recordLooted(ServerLevel level, BlockPos basin) {
    }
}
