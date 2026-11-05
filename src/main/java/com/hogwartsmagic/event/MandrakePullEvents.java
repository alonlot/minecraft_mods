package com.hogwartsmagic.event;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.block.MandrakePlantBlock;
import com.hogwartsmagic.network.SpellPackets;
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
    public static final int PULL_TICKS = 20 * 3;
    private static final float FAIL_CHANCE = 0.30F;
    private static final double MAX_PULL_DISTANCE_SQ = 5.0D * 5.0D;
    private static final Map<UUID, PullState> ACTIVE_PULLS = new HashMap<>();

    private MandrakePullEvents() {
    }

    /**
     * Whether {@code player} is the server's own player for their id (the same test FlyingMemory.isRealPlayer makes).
     * Forge 1.21 has no FakePlayer class: another mod's machine player is a ServerPlayer that is never ticked, so a
     * pull started for it would sit in ACTIVE_PULLS for the life of the server and leave the plant stuck PULLING.
     */
    private static boolean isRealPlayer(ServerPlayer player) {
        return player.server != null && player.server.getPlayerList().getPlayer(player.getUUID()) == player;
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (player.isCreative() || player.isSpectator() || !event.getItemStack().isEmpty() || !isRealPlayer(player)) {
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
        if (player.isCreative() || player.isSpectator() || !isRealPlayer(player)) {
            return; // a machine player breaks the plant like any other block: it can never finish a pull
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

    /** A pull in progress when the player leaves would otherwise sit in the map (and leave the plant marked PULLING) until they return. */
    @SubscribeEvent
    public static void onPlayerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PullState pull = ACTIVE_PULLS.remove(player.getUUID());
        if (pull == null) {
            return;
        }
        ServerLevel level = player.serverLevel().getServer().getLevel(pull.dimension);
        if (level != null) {
            level.destroyBlockProgress(player.getId(), pull.pos, -1);
            restoreIdleState(level, pull.pos);
        }
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
        if (existing != null) {
            // Switching to another plant mid-pull: the first one must not be left stuck in its PULLING state.
            ServerLevel previousLevel = player.serverLevel().getServer().getLevel(existing.dimension);
            if (previousLevel != null) {
                restoreIdleState(previousLevel, existing.pos);
            }
        }

        PullState pull = new PullState(player.level().dimension(), pos.immutable());
        ACTIVE_PULLS.put(player.getUUID(), pull);
        BlockState state = player.serverLevel().getBlockState(pos);
        if (state.is(ExampleMod.MANDRAKE_PLANT.get())) {
            player.serverLevel().setBlock(pos, state.setValue(MandrakePlantBlock.PULLING, true), 3);
        }
        broadcast(player, pull.pos, SpellPackets.MandrakePullPacket.START);
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
        // The last heave throws them back a step, harder when the grip tears loose. A player moves itself, so the
        // push has to be sent (hurtMarked) or it never reaches them.
        Vec3 away = player.position().subtract(Vec3.atCenterOf(pull.pos));
        away = new Vec3(away.x, 0.0D, away.z);
        if (away.lengthSqr() > 1.0E-4D) {
            away = away.normalize().scale(failed ? 0.42D : 0.26D);
            player.setDeltaMovement(player.getDeltaMovement().add(away.x, failed ? 0.18D : 0.10D, away.z));
            player.hurtMarked = true;
        }
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
            broadcast(player, pull.pos, SpellPackets.MandrakePullPacket.SLIPPED);
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.mandrake.pull_failed"), true);
            pull.expired = true;
            ACTIVE_PULLS.remove(player.getUUID());
            return;
        }

        level.removeBlock(pull.pos, false);
        ItemStack root = new ItemStack(ExampleMod.MANDRAKE_ROOT.get());
        root.set(ExampleMod.FINE_INGREDIENT.get(), true); // a root pulled by hand is fine
        ItemEntity drop = new ItemEntity(level,
                pull.pos.getX() + 0.5D,
                pull.pos.getY() + 0.25D,
                pull.pos.getZ() + 0.5D,
                root);
        drop.setDefaultPickUpDelay();
        // Torn out of the soil by the last heave: it flies up and back toward whoever pulled it.
        Vec3 toPuller = player.position().subtract(Vec3.atCenterOf(pull.pos));
        Vec3 flat = new Vec3(toPuller.x, 0.0D, toPuller.z);
        flat = flat.lengthSqr() < 1.0E-4D ? Vec3.ZERO : flat.normalize().scale(0.16D);
        drop.setDeltaMovement(flat.x, 0.34D, flat.z);
        level.addFreshEntity(drop);
        level.levelEvent(2001, pull.pos, net.minecraft.world.level.block.Block.getId(ExampleMod.MANDRAKE_PLANT.get().defaultBlockState()));
        broadcast(player, pull.pos, SpellPackets.MandrakePullPacket.PULLED);
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.mandrake.pull_success"), true);
        pull.expired = true;
        ACTIVE_PULLS.remove(player.getUUID());
    }

    private static void cancelPull(ServerPlayer player, ServerLevel level, PullState pull) {
        level.destroyBlockProgress(player.getId(), pull.pos, -1);
        restoreIdleState(level, pull.pos);
        broadcast(player, pull.pos, SpellPackets.MandrakePullPacket.STOPPED);
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.mandrake.pull_interrupted"), true);
        pull.expired = true;
        ACTIVE_PULLS.remove(player.getUUID());
    }

    /** The puller's body animates on every client that can see them (MandrakePullClient). */
    private static void broadcast(ServerPlayer player, BlockPos pos, byte state) {
        SpellPackets.CHANNEL.send(new SpellPackets.MandrakePullPacket(player.getId(), pos, state),
                net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(player));
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
