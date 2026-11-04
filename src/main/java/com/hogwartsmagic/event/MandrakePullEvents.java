package com.hogwartsmagic.event;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.block.MandrakePlantBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MandrakePullEvents {
    private static final int PULL_TICKS = 20 * 3;
    private static final float FAIL_CHANCE = 0.30F;
    private static final double MAX_PULL_DISTANCE_SQ = 5.0D * 5.0D;
    private static final Map<UUID, PullState> ACTIVE_PULLS = new HashMap<>();

    private MandrakePullEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (player.isCreative() || player.isSpectator() || !event.getItemStack().isEmpty()) {
            return;
        }
        // Pulling one up harvests it, so it needs the same rights as breaking it (adventure mode).
        if (!player.mayBuild()) {
            return;
        }
        if (!event.getLevel().getBlockState(event.getPos()).is(ExampleMod.MANDRAKE_PLANT.get())) {
            return;
        }

        startPull(player, event.getPos());
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!level.getBlockState(event.getPos()).is(ExampleMod.MANDRAKE_PLANT.get())) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }

        event.setCanceled(true);
        if (player.getMainHandItem().isEmpty()) {
            startPull(player, event.getPos());
        } else {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.mandrake.empty_hand"), true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        PullState pull = ACTIVE_PULLS.get(player.getUUID());
        if (pull == null) {
            return;
        }
        if (pull.expired) {
            ACTIVE_PULLS.remove(player.getUUID());
            return;
        }

        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(pull.dimension)
                || !level.getBlockState(pull.pos).is(ExampleMod.MANDRAKE_PLANT.get())
                || !player.getMainHandItem().isEmpty()
                || player.distanceToSqr(pull.pos.getX() + 0.5D, pull.pos.getY() + 0.5D, pull.pos.getZ() + 0.5D) > MAX_PULL_DISTANCE_SQ) {
            cancelPull(player, level, pull);
            return;
        }

        pull.ticks++;
        animatePull(player, level, pull);

        if (pull.ticks >= PULL_TICKS) {
            finishPull(player, level, pull);
        }
    }

    /**
     * A pull abandoned by logging out (or changing dimension) would otherwise leave the plant
     * stuck in its pulling pose for good, and the entry behind in the map.
     */
    @SubscribeEvent
    public static void onLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        abandon(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangedDimension(
            net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        abandon(event.getEntity());
    }

    private static void abandon(Player player) {
        PullState pull = ACTIVE_PULLS.remove(player.getUUID());
        if (pull == null || player.getServer() == null) {
            return;
        }
        ServerLevel level = player.getServer().getLevel(pull.dimension);
        if (level != null && level.isLoaded(pull.pos)) {
            level.destroyBlockProgress(player.getId(), pull.pos, -1);
            restoreIdleState(level, pull.pos);
        }
        pull.expired = true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE_PULLS.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, PullState>> iterator = ACTIVE_PULLS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PullState> entry = iterator.next();
            if (entry.getValue().expired) {
                iterator.remove();
            }
        }
    }

    private static void startPull(ServerPlayer player, BlockPos pos) {
        PullState existing = ACTIVE_PULLS.get(player.getUUID());
        if (existing != null && existing.pos.equals(pos) && existing.dimension.equals(player.level().dimension())) {
            return;
        }

        PullState pull = new PullState(player.level().dimension(), pos.immutable());
        ACTIVE_PULLS.put(player.getUUID(), pull);
        BlockState state = player.serverLevel().getBlockState(pos);
        if (state.is(ExampleMod.MANDRAKE_PLANT.get())) {
            player.serverLevel().setBlock(pos, state.setValue(MandrakePlantBlock.PULLING, true), 3);
        }
        player.serverLevel().playSound(null, pos, ModSounds.MANDRAKE_SCREAM.get(), SoundSource.BLOCKS, 1.35F, 1.0F);
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.mandrake.pull_start"), true);
        animatePull(player, player.serverLevel(), pull);
    }

    private static void animatePull(ServerPlayer player, ServerLevel level, PullState pull) {
        BlockState state = level.getBlockState(pull.pos);
        int progress = Math.min(9, (pull.ticks * 10) / PULL_TICKS);
        level.destroyBlockProgress(player.getId(), pull.pos, progress);
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(pull.pos).add(0.0D, 0.25D, 0.0D));

        if (pull.ticks % 4 == 0) {
            player.swing(InteractionHand.MAIN_HAND);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 1, false, false, false));
            Vec3 tug = Vec3.atCenterOf(pull.pos).subtract(player.position()).normalize().scale(0.035D);
            player.setDeltaMovement(player.getDeltaMovement().add(tug.x, 0.0D, tug.z));
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pull.pos.getX() + 0.5D,
                    pull.pos.getY() + 0.12D,
                    pull.pos.getZ() + 0.5D,
                    8,
                    0.25D,
                    0.08D,
                    0.25D,
                    0.04D);
        }

        if (pull.ticks % 12 == 0) {
            level.sendParticles(ParticleTypes.WITCH,
                    pull.pos.getX() + 0.5D,
                    pull.pos.getY() + 0.85D,
                    pull.pos.getZ() + 0.5D,
                    5,
                    0.18D,
                    0.25D,
                    0.18D,
                    0.01D);
            applyScreamStrain(level, pull.pos);
        }
    }

    private static void finishPull(ServerPlayer player, ServerLevel level, PullState pull) {
        level.destroyBlockProgress(player.getId(), pull.pos, -1);

        boolean failed = player.getRandom().nextFloat() < FAIL_CHANCE;
        if (failed) {
            restoreIdleState(level, pull.pos);
            level.sendParticles(ParticleTypes.POOF,
                    pull.pos.getX() + 0.5D,
                    pull.pos.getY() + 0.55D,
                    pull.pos.getZ() + 0.5D,
                    18,
                    0.35D,
                    0.35D,
                    0.35D,
                    0.03D);
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.mandrake.pull_failed"), true);
            pull.expired = true;
            ACTIVE_PULLS.remove(player.getUUID());
            return;
        }

        level.removeBlock(pull.pos, false);
        ItemStack root = new ItemStack(ExampleMod.MANDRAKE_ROOT.get());
        ItemEntity drop = new ItemEntity(level,
                pull.pos.getX() + 0.5D,
                pull.pos.getY() + 0.25D,
                pull.pos.getZ() + 0.5D,
                root);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
        level.levelEvent(2001, pull.pos, net.minecraft.world.level.block.Block.getId(ExampleMod.MANDRAKE_PLANT.get().defaultBlockState()));
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.mandrake.pull_success"), true);
        pull.expired = true;
        ACTIVE_PULLS.remove(player.getUUID());
    }

    private static void cancelPull(ServerPlayer player, ServerLevel level, PullState pull) {
        level.destroyBlockProgress(player.getId(), pull.pos, -1);
        restoreIdleState(level, pull.pos);
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.mandrake.pull_interrupted"), true);
        pull.expired = true;
        ACTIVE_PULLS.remove(player.getUUID());
    }

    private static void restoreIdleState(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ExampleMod.MANDRAKE_PLANT.get()) && state.getValue(MandrakePlantBlock.PULLING)) {
            level.setBlock(pos, state.setValue(MandrakePlantBlock.PULLING, false), 3);
        }
    }

    private static void applyScreamStrain(ServerLevel level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(5.0D);
        for (Player player : level.getEntitiesOfClass(Player.class, area, Player::isAlive)) {
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 45, 0, false, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 45, 0, false, false, false));
        }
    }

    private static final class PullState {
        private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        private final BlockPos pos;
        private int ticks;
        private boolean expired;

        private PullState(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos pos) {
            this.dimension = dimension;
            this.pos = pos;
        }
    }
}
