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

    /**
     * Area Reparo around {@code impactPos}. Works for both player and mob casters:
     *
     * <ul>
     *   <li>For any caster, ground rubble (matching ItemEntity within 5 blocks of each
     *       record) is the preferred cost source — the rubble lifts off and flies to the
     *       broken slot.</li>
     *   <li>For a {@link ServerPlayer} caster, if no rubble exists for a record we fall
     *       back to consuming an inventory stack and spawning a flying item from the
     *       player's eye. Mana is also charged per repaired block.</li>
     *   <li>For mob casters (students), the inventory fallback is unavailable, so a
     *       record with no nearby rubble is just skipped.</li>
     * </ul>
     *
     * Up to {@link #MAX_REPAIRS_PER_CAST} blocks are repaired per cast, sorted closest
     * to impact first.
     */
    public static boolean castReparo(ServerLevel level, net.minecraft.world.entity.LivingEntity caster, BlockPos impactPos) {
        if (caster == null) return false;

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

        ServerPlayer player = caster instanceof ServerPlayer p ? p : null;
        boolean creative = player != null && player.isCreative();
        float availableMana = player != null ? getCurrentMana(player) : 0f;
        float maxMana = player != null ? PlayerMagicData.getMaxMana(player) : 0f;
        // The per-block surcharge gets the same mastery discount as the cast itself.
        float manaPerBlock = player != null
                ? EXTRA_MANA_PER_BLOCK * com.hogwartsmagic.spell.SpellCosts.masteryMultiplier(player, SpellType.REPARO)
                : EXTRA_MANA_PER_BLOCK;
        int repairedCount = 0;

        for (TrackedBreakRecord record : candidates) {
            ItemStack payment = getRequiredPayment(record.state);
            if (payment.isEmpty()) {
                continue;
            }
            // Mana check first so we don't consume from inventory then fail. Mobs have
            // no mana — they pay only with on-the-ground rubble.
            if (player != null && !creative && availableMana < manaPerBlock) {
                break;
            }
            ItemEntity sourceDrop = creative ? null
                    : findGroundDropFor(level, record.pos, record.state, 5.0D);
            boolean fromInventory = false;
            if (sourceDrop == null && !creative) {
                // Only players can pay from inventory; mobs without rubble just skip.
                if (player == null) continue;
                fromInventory = consumeMatchingBlock(player, payment);
                if (!fromInventory) continue;
            }
            if (player != null && !creative) {
                availableMana -= manaPerBlock;
            }
            if (sourceDrop != null) {
                startGroundDropFlight(level, sourceDrop, record.pos, record.state);
            } else {
                // Only reachable when player != null (or creative with player != null),
                // since mob casters with no ground drop hit `continue` above.
                startRepairFlight(level, player, record, payment);
            }
            RECORDS.remove(key(level, record.pos));
            RECORD_ORDER.remove(key(level, record.pos));
            repairedCount++;
        }

        if (player != null && !creative && repairedCount > 0) {
            setCurrentMana(player, availableMana, maxMana);
        }
        if (repairedCount > 0) {
            playCinematicBurst(level, impactPos, repairedCount);
        }
        return repairedCount > 0;
    }

    /**
     * Cinematic flourish at the impact point — a flash, a ring of end-rod sparks,
     * a swirl of enchantment glyphs and a chord of chimes. Density scales with the
     * number of blocks being repaired so a single-block fix doesn't fire the full
     * spectacle but a sweep through a creeper crater absolutely does.
     */
    private static void playCinematicBurst(ServerLevel level, BlockPos impactPos, int repairedCount) {
        Vec3 c = Vec3.atCenterOf(impactPos);
        int intensity = Math.min(repairedCount, 8);
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 30 + intensity * 6, 0.45, 0.45, 0.45, 0.04);
        level.sendParticles(ParticleTypes.ENCHANT, c.x, c.y, c.z, 24 + intensity * 4, 0.8, 0.8, 0.8, 0.6);
        level.sendParticles(ParticleTypes.WAX_ON, c.x, c.y, c.z, 18 + intensity * 3, 0.6, 0.6, 0.6, 0.04);
        // Outward shockwave ring of sparkles so the spell visibly *reaches* the rubble.
        int ringSteps = 24;
        double ringRadius = 1.8 + 0.18 * intensity;
        for (int i = 0; i < ringSteps; i++) {
            double a = (Math.PI * 2.0 * i) / ringSteps;
            level.sendParticles(ParticleTypes.END_ROD,
                    c.x + Math.cos(a) * ringRadius, c.y + 0.2, c.z + Math.sin(a) * ringRadius,
                    1, 0, 0, 0, 0);
        }
        level.playSound(null, impactPos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.9f, 1.25f);
        level.playSound(null, impactPos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.4f);
        if (repairedCount >= 3) {
            level.playSound(null, impactPos, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.45f, 1.7f);
        }
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

            // Magical comet trail behind the flying rubble — golden sparkle every other tick
            // gives a clear visual of the spell pulling the piece into its old slot.
            if ((flight.ticks & 1) == 0) {
                level.sendParticles(ParticleTypes.WAX_ON,
                        current.x, current.y + 0.1, current.z, 2, 0.05, 0.05, 0.05, 0.01);
                level.sendParticles(ParticleTypes.END_ROD,
                        current.x, current.y + 0.1, current.z, 1, 0.04, 0.04, 0.04, 0.005);
            }
        }
    }

    /**
     * Find an {@link ItemEntity} near {@code center} whose stack matches the
     * block-item form of {@code state}. Doesn't mutate it — that's the caller's job
     * (either consuming immediately or, preferably, starting a flight to the target
     * via {@link #startGroundDropFlight}).
     */
    public static ItemEntity findGroundDropFor(ServerLevel level, BlockPos center, BlockState state, double radius) {
        Item item = state.getBlock().asItem();
        if (item == net.minecraft.world.item.Items.AIR) return null;
        java.util.List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(center).inflate(radius),
                ie -> ie.isAlive() && ie.getItem().is(item));
        if (drops.isEmpty()) return null;
        // Closest first so the visual flight is the shortest path.
        drops.sort(java.util.Comparator.comparingDouble(ie -> ie.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(center))));
        return drops.get(0);
    }

    /**
     * Start the Reparo flight from a real dropped {@link ItemEntity} on the ground.
     * Takes one item off the source stack (discards the source if it's now empty)
     * and spawns a new count-1 flying copy at the source's current position so it
     * physically lifts off the rubble pile and homes onto the target block. Once
     * the flight tick lands, {@code state} is set at {@code target} and the flying
     * copy is discarded — identical to the inventory-path arrival.
     */
    public static boolean startGroundDropFlight(ServerLevel level, ItemEntity source,
                                                BlockPos target, BlockState state) {
        if (source == null || !source.isAlive() || source.getItem().isEmpty()) return false;
        ItemStack stack = source.getItem();
        Item item = state.getBlock().asItem();
        if (item == net.minecraft.world.item.Items.AIR || !stack.is(item)) return false;

        Vec3 start = source.position();
        // Detach one item from the rubble pile.
        stack.shrink(1);
        if (stack.isEmpty()) {
            source.discard();
        } else {
            source.setItem(stack);
        }

        ItemEntity flier = new ItemEntity(level, start.x, start.y, start.z, new ItemStack(item, 1));
        flier.setNoGravity(true);
        flier.setPickUpDelay(32767);
        flier.setInvulnerable(true);
        flier.setGlowingTag(true);
        level.addFreshEntity(flier);
        ACTIVE_FLIGHTS.put(flier.getUUID(),
                new ActiveRepairFlight(level.dimension().location().toString(), target.immutable(), state));

        level.sendParticles(ParticleTypes.WITCH, start.x, start.y, start.z, 6, 0.1, 0.1, 0.1, 0.01);
        level.playSound(null, target, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.45f, 1.35f);
        return true;
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
        // TTL-only pruning. The previous version also called shouldForgetEarly() to drop
        // records when no nearby player had learned Reparo — but mob casters (students)
        // use this tracker too and don't care about a player having the spell. The
        // overflow cap (MAX_RECORDS_PER_LEVEL) keeps memory bounded.
        long now = level.getGameTime();
        Iterator<Map.Entry<String, TrackedBreakRecord>> iterator = RECORDS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, TrackedBreakRecord> entry = iterator.next();
            TrackedBreakRecord record = entry.getValue();
            if (!record.dimensionKey.equals(level.dimension().location().toString())) {
                continue;
            }
            if (now - record.gameTime > RECORD_LIFETIME_TICKS) {
                iterator.remove();
                RECORD_ORDER.remove(entry.getKey());
            }
        }
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
