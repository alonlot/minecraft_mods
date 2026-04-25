package com.hogwartsmagic.util;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicBookItem;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReparoManager {
    private static final int RECORD_LIFETIME_TICKS = 15 * 20;
    private static final double RETENTION_RADIUS = 30.0D;
    private static final double CAST_RADIUS = 6.0D;
    private static final int MAX_REPAIRS_PER_CAST = 32;
    private static final int MAX_RECORDS_PER_LEVEL = 2048;
    private static final float EXTRA_MANA_PER_BLOCK = 2.0f;
    private static final int FLIGHT_MAX_TICKS = 16;

    private static final Map<String, TrackedBreakRecord> RECORDS = new HashMap<>();
    private static final ArrayDeque<String> RECORD_ORDER = new ArrayDeque<>();
    private static final Map<UUID, ActiveRepairFlight> ACTIVE_FLIGHTS = new HashMap<>();

    private ReparoManager() {
    }

    public static void recordBrokenBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (!isRepairable(state)) {
            return;
        }
        String key = key(level, pos);
        TrackedBreakRecord record = new TrackedBreakRecord(level.dimension().location().toString(), pos.immutable(), state, level.getGameTime());
        RECORDS.put(key, record);
        RECORD_ORDER.remove(key);
        RECORD_ORDER.addLast(key);
        pruneOverflow();
    }

    public static boolean castReparo(ServerLevel level, net.minecraft.world.entity.LivingEntity caster, BlockPos impactPos) {
        if (!(caster instanceof ServerPlayer player)) {
            return false;
        }

        prune(level);

        List<TrackedBreakRecord> candidates = RECORDS.values().stream()
                .filter(record -> record.dimensionKey.equals(level.dimension().location().toString()))
                .filter(record -> record.pos.closerToCenterThan(Vec3.atCenterOf(impactPos), CAST_RADIUS))
                .filter(record -> isRepairStillPossible(level, record))
                .sorted(Comparator.comparingDouble(record -> record.pos.distSqr(impactPos)))
                .limit(MAX_REPAIRS_PER_CAST)
                .toList();

        if (candidates.isEmpty()) {
            return false;
        }

        boolean creative = player.isCreative();
        float availableMana = getCurrentMana(player);
        float maxMana = PlayerMagicData.getMaxMana(player);
        int repairedCount = 0;

        for (TrackedBreakRecord record : candidates) {
            ItemStack payment = getRequiredPayment(record.state);
            if (payment.isEmpty()) {
                continue;
            }
            if (!creative && !consumeMatchingBlock(player, payment)) {
                continue;
            }
            if (!creative && availableMana < EXTRA_MANA_PER_BLOCK) {
                break;
            }
            if (!creative) {
                availableMana -= EXTRA_MANA_PER_BLOCK;
            }
            startRepairFlight(level, player, record, payment);
            RECORDS.remove(key(level, record.pos));
            RECORD_ORDER.remove(key(level, record.pos));
            repairedCount++;
        }

        if (!creative && repairedCount > 0) {
            setCurrentMana(player, availableMana, maxMana);
        }
        return repairedCount > 0;
    }

    public static void tick(ServerLevel level) {
        prune(level);

        Iterator<Map.Entry<UUID, ActiveRepairFlight>> iterator = ACTIVE_FLIGHTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveRepairFlight> entry = iterator.next();
            ActiveRepairFlight flight = entry.getValue();
            if (!flight.dimensionKey.equals(level.dimension().location().toString())) {
                continue;
            }

            net.minecraft.world.entity.Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof ItemEntity itemEntity) || !itemEntity.isAlive()) {
                iterator.remove();
                continue;
            }

            flight.ticks++;
            itemEntity.setNoGravity(true);
            itemEntity.setPickUpDelay(32767);
            itemEntity.setInvulnerable(true);

            Vec3 target = Vec3.atCenterOf(flight.targetPos).add(0.0D, 0.2D, 0.0D);
            Vec3 current = itemEntity.position();
            Vec3 delta = target.subtract(current);
            double distance = delta.length();
            if (distance < 0.3D || flight.ticks >= FLIGHT_MAX_TICKS) {
                if (level.getBlockState(flight.targetPos).canBeReplaced() && flight.state.canSurvive(level, flight.targetPos)) {
                    level.setBlock(flight.targetPos, flight.state, 3);
                    level.sendParticles(ParticleTypes.ENCHANT, target.x, target.y, target.z, 16, 0.18, 0.18, 0.18, 0.02);
                    level.sendParticles(ParticleTypes.END_ROD, target.x, target.y, target.z, 12, 0.12, 0.12, 0.12, 0.01);
                    level.playSound(null, flight.targetPos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.65f, 1.55f);
                }
                itemEntity.discard();
                iterator.remove();
                continue;
            }

            Vec3 velocity = delta.normalize().scale(Mth.clamp(distance * 0.22D, 0.12D, 0.5D));
            itemEntity.setDeltaMovement(velocity);
            itemEntity.hurtMarked = true;
        }
    }

    private static void startRepairFlight(ServerLevel level, Player player, TrackedBreakRecord record, ItemStack payment) {
        Vec3 start = player.getEyePosition().add(player.getLookAngle().scale(0.65D));
        ItemEntity entity = new ItemEntity(level, start.x, start.y, start.z, payment.copyWithCount(1));
        entity.setNoGravity(true);
        entity.setPickUpDelay(32767);
        entity.setInvulnerable(true);
        entity.setGlowingTag(true);
        level.addFreshEntity(entity);
        ACTIVE_FLIGHTS.put(entity.getUUID(), new ActiveRepairFlight(level.dimension().location().toString(), record.pos, record.state));

        level.sendParticles(ParticleTypes.WITCH, start.x, start.y, start.z, 8, 0.15, 0.15, 0.15, 0.01);
        level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.45f, 1.35f);
    }

    private static boolean isRepairStillPossible(ServerLevel level, TrackedBreakRecord record) {
        BlockState current = level.getBlockState(record.pos);
        return (current.isAir() || current.canBeReplaced())
                && record.state.canSurvive(level, record.pos)
                && getRequiredPayment(record.state).getItem() != ItemStack.EMPTY.getItem();
    }

    private static ItemStack getRequiredPayment(BlockState state) {
        Item item = state.getBlock().asItem();
        if (item == net.minecraft.world.item.Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    private static boolean consumeMatchingBlock(Player player, ItemStack required) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameComponents(stack, required) || stack.is(required.getItem())) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static float getCurrentMana(ServerPlayer player) {
        net.minecraft.nbt.CompoundTag modData = PlayerMagicData.getModData(player);
        return modData.contains("Mana") ? modData.getFloat("Mana") : PlayerMagicData.getMaxMana(player);
    }

    private static void setCurrentMana(ServerPlayer player, float mana, float maxMana) {
        net.minecraft.nbt.CompoundTag modData = PlayerMagicData.getModData(player);
        modData.putFloat("Mana", mana);
        modData.putFloat("MaxMana", maxMana);
        PlayerMagicData.saveModData(player, modData);
        SpellPackets.CHANNEL.send(new SpellPackets.SyncManaPacket(mana, maxMana),
                net.minecraftforge.network.PacketDistributor.PLAYER.with(player));
    }

    private static void prune(ServerLevel level) {
        long now = level.getGameTime();
        List<ServerPlayer> nearbyReparoPlayers = level.players().stream()
                .filter(player -> MagicBookItem.isSpellLearned(player, SpellType.REPARO))
                .toList();

        Iterator<Map.Entry<String, TrackedBreakRecord>> iterator = RECORDS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, TrackedBreakRecord> entry = iterator.next();
            TrackedBreakRecord record = entry.getValue();
            if (!record.dimensionKey.equals(level.dimension().location().toString())) {
                continue;
            }

            if (now - record.gameTime > RECORD_LIFETIME_TICKS || shouldForgetEarly(record, nearbyReparoPlayers)) {
                iterator.remove();
                RECORD_ORDER.remove(entry.getKey());
            }
        }
    }

    private static boolean shouldForgetEarly(TrackedBreakRecord record, List<ServerPlayer> nearbyReparoPlayers) {
        if (nearbyReparoPlayers.isEmpty()) {
            return true;
        }
        for (ServerPlayer player : nearbyReparoPlayers) {
            if (player.blockPosition().closerThan(record.pos, RETENTION_RADIUS)) {
                return false;
            }
        }
        return true;
    }

    private static void pruneOverflow() {
        while (RECORD_ORDER.size() > MAX_RECORDS_PER_LEVEL) {
            String oldest = RECORD_ORDER.pollFirst();
            if (oldest != null) {
                RECORDS.remove(oldest);
            }
        }
    }

    private static boolean isRepairable(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        Block block = state.getBlock();
        if (block instanceof EntityBlock || state.hasBlockEntity()) {
            return false;
        }
        if (state.getDestroySpeed(null, BlockPos.ZERO) < 0) {
            return false;
        }
        Item item = block.asItem();
        return item != net.minecraft.world.item.Items.AIR;
    }

    private static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.asLong();
    }

    private record TrackedBreakRecord(String dimensionKey, BlockPos pos, BlockState state, long gameTime) {
    }

    private static final class ActiveRepairFlight {
        private final String dimensionKey;
        private final BlockPos targetPos;
        private final BlockState state;
        private int ticks;

        private ActiveRepairFlight(String dimensionKey, BlockPos targetPos, BlockState state) {
            this.dimensionKey = dimensionKey;
            this.targetPos = targetPos;
            this.state = state;
        }
    }
}
