package com.hogwartsmagic.caveinimicum;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.block.entity.CaveInimicumWardBlockEntity;
import com.hogwartsmagic.event.ModEvents;
import com.hogwartsmagic.item.CaveInimicumTotemItem;
import com.hogwartsmagic.network.SpellPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owns active ward lifecycle per level: registration, expiration, player
 * visibility transitions, and delta illusion repair when the real world changes.
 */
public final class CaveInimicumManager {
    private static final Map<String, CaveInimicumManager> BY_LEVEL = new HashMap<>();

    private final ServerLevel level;
    private final Set<BlockPos> trackedWardPositions = new HashSet<>();
    private final Map<BlockPos, Boolean> concealmentByWard = new HashMap<>();
    private final CaveInimicumVisibilityTracker visibilityTracker = new CaveInimicumVisibilityTracker();

    private CaveInimicumManager(ServerLevel level) {
        this.level = level;
    }

    public static CaveInimicumManager get(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level.dimension().location().toString(), ignored -> new CaveInimicumManager(level));
    }

    public static void unload(Level level) {
        BY_LEVEL.remove(level.dimension().location().toString());
    }

    public ServerLevel level() {
        return level;
    }

    public void registerWard(CaveInimicumWardBlockEntity ward) {
        boolean added = trackedWardPositions.add(ward.getBlockPos().immutable());
        concealmentByWard.put(ward.getBlockPos().immutable(), ward.isConcealing(level.getGameTime()));
        if (added && ward.isActive()) {
            syncWardRegionToRelevantPlayers(ward.bounds());
        }
    }

    public void unregisterWard(CaveInimicumWardBlockEntity ward, boolean syncRestore) {
        trackedWardPositions.remove(ward.getBlockPos());
        concealmentByWard.remove(ward.getBlockPos());
        visibilityTracker.removeWard(ward.getBlockPos());
        if (syncRestore) {
            syncWardRegionToRelevantPlayers(ward.bounds());
        }
    }

    public boolean isWardStillTracked(BlockPos wardPos) {
        return trackedWardPositions.contains(wardPos);
    }

    public void onPlayerLeft(ServerPlayer player) {
        visibilityTracker.removePlayer(player.getUUID());
    }

    public void syncRelevantWardsToPlayer(ServerPlayer player) {
        for (CaveInimicumWardBlockEntity ward : illusionWards()) {
            if (isPlayerRelevantToWard(player, ward)) {
                syncWardRegionToPlayer(player, ward.bounds());
            }
        }
        syncActiveWardListToPlayer(player);
    }

    public void tick() {
        List<CaveInimicumWardBlockEntity> wards = trackedWards();
        if (wards.isEmpty()) {
            return;
        }

        for (CaveInimicumWardBlockEntity ward : wards) {
            if (ward.shouldExpire(level.getGameTime())) {
                ward.deactivate(level, true);
            }
        }

        wards = trackedWards();
        if (wards.isEmpty()) {
            return;
        }

        boolean concealmentChanged = false;
        long gameTime = level.getGameTime();
        for (CaveInimicumWardBlockEntity ward : wards) {
            boolean concealing = ward.isConcealing(gameTime);
            Boolean previous = concealmentByWard.put(ward.getBlockPos().immutable(), concealing);
            if (previous != null && previous.booleanValue() != concealing) {
                syncWardRegionToRelevantPlayers(ward.bounds());
                concealmentChanged = true;
            }
        }

        visibilityTracker.tick(this, wards);
        tickRevelioWards(wards);
        if (concealmentChanged || level.getGameTime() % 20L == 0L) {
            syncActiveWardLists();
        }

        int interval = Math.max(1, Config.getCaveInimicumScanIntervalTicks());
        if (level.getGameTime() % interval != 0L) {
            tickBarrierWards(wards);
            return;
        }

        for (CaveInimicumWardBlockEntity ward : illusionWards(wards)) {
            scanWardForRealChanges(ward);
        }
        tickBarrierWards(wards);
    }

    public void handlePotentialBlockChanges(Collection<BlockPos> changedPositions) {
        if (changedPositions.isEmpty()) {
            return;
        }

        for (CaveInimicumWardBlockEntity ward : illusionWards()) {
            boolean intersects = false;
            for (BlockPos changedPos : changedPositions) {
                if (ward.bounds().contains(changedPos)) {
                    intersects = true;
                    break;
                }
            }
            if (!intersects) {
                continue;
            }

            ward.refreshActualStateCache(level);
            syncWardRegionToRelevantPlayers(ward.bounds());
        }
    }

    public void syncWardRegionToRelevantPlayers(CaveInimicumWardBounds bounds) {
        for (ServerPlayer player : level.players()) {
            if (isPlayerRelevantToBounds(player, bounds)) {
                syncWardRegionToPlayer(player, bounds);
            }
        }
    }

    public void syncWardRegionToPlayer(ServerPlayer player, CaveInimicumWardBounds bounds) {
        int[] stateIds = new int[bounds.volume()];
        BlockPos min = bounds.min();
        int index = 0;
        for (int y = 0; y < bounds.edgeLength(); y++) {
            for (int z = 0; z < bounds.edgeLength(); z++) {
                for (int x = 0; x < bounds.edgeLength(); x++) {
                    BlockPos pos = min.offset(x, y, z);
                    stateIds[index++] = Block.getId(resolveVisibleStateForPlayer(player, pos));
                }
            }
        }

        SpellPackets.CHANNEL.send(
                new SpellPackets.CaveInimicumRegionSyncPacket(min, bounds.edgeLength(), stateIds),
                net.minecraftforge.network.PacketDistributor.PLAYER.with(player));

        sendVisibleBlockEntityUpdates(player, bounds);
    }

    public boolean isPlayerRelevantToWard(ServerPlayer player, CaveInimicumWardBlockEntity ward) {
        return isPlayerRelevantToBounds(player, ward.bounds());
    }

    public boolean playerSeesRealityInWard(ServerPlayer player, CaveInimicumWardBlockEntity ward) {
        if (!ward.isActive()) {
            return true;
        }
        if (player.isSpectator() || ward.isInside(player) || ward.isRevealed(level.getGameTime())) {
            return true;
        }
        return CaveInimicumTotemItem.isWardBoundToPlayerInventory(player, level, ward);
    }

    public BlockState resolveVisibleStateForPlayer(ServerPlayer player, BlockPos pos) {
        List<CaveInimicumWardBlockEntity> affecting = wardsContaining(pos);
        if (affecting.isEmpty()) {
            return level.getBlockState(pos);
        }

        for (CaveInimicumWardBlockEntity ward : affecting) {
            if (playerSeesRealityInWard(player, ward)) {
                return level.getBlockState(pos);
            }
        }

        CaveInimicumWardBlockEntity chosen = affecting.stream()
                .filter(ward -> ward.isConcealing(level.getGameTime()))
                .max(Comparator.comparingLong(CaveInimicumWardBlockEntity::getActivatedGameTime))
                .orElse(null);
        return chosen == null ? level.getBlockState(pos) : chosen.getOutsideVisibleState(pos);
    }

    public boolean disruptWardsAt(BlockPos pos, int durationTicks) {
        boolean changed = false;
        for (CaveInimicumWardBlockEntity ward : wardsContaining(pos)) {
            if (!ward.disruptFor(level, durationTicks)) {
                continue;
            }
            concealmentByWard.put(ward.getBlockPos().immutable(), ward.isConcealing(level.getGameTime()));
            syncWardRegionToRelevantPlayers(ward.bounds());
            changed = true;
        }
        if (changed) {
            syncActiveWardLists();
        }
        return changed;
    }

    public boolean toggleProtegoMaximaAt(BlockPos pos) {
        boolean changed = false;
        for (CaveInimicumWardBlockEntity ward : wardsContaining(pos)) {
            if (!ward.toggleProtegoMaxima(level)) {
                continue;
            }
            syncWardRegionToRelevantPlayers(ward.bounds());
            changed = true;
        }
        if (changed) {
            syncActiveWardLists();
        }
        return changed;
    }

    private void scanWardForRealChanges(CaveInimicumWardBlockEntity ward) {
        int[] cache = ward.getOrCreateActualStateCache(level);
        CaveInimicumWardBounds bounds = ward.bounds();
        boolean changed = false;
        for (int i = 0; i < cache.length; i++) {
            BlockPos pos = bounds.posAtIndex(i);
            int currentId = Block.getId(level.getBlockState(pos));
            if (cache[i] == currentId) {
                continue;
            }
            cache[i] = currentId;
            changed = true;
        }
        if (changed) {
            syncWardRegionToRelevantPlayers(bounds);
        }
    }

    private void tickBarrierWards(List<CaveInimicumWardBlockEntity> wards) {
        for (CaveInimicumWardBlockEntity ward : wards) {
            if (!ward.isBarrierUp(level.getGameTime())) {
                continue; // off, or switched off for a while by a Master Alohomora
            }
            handleBarrierProjectiles(ward);
        }
    }

    private void tickRevelioWards(List<CaveInimicumWardBlockEntity> wards) {
        if (level.getGameTime() % 5L != 0L) {
            return;
        }
        for (CaveInimicumWardBlockEntity ward : wards) {
            if (!ward.isRevelioActive()) {
                continue;
            }
            revealEntitiesInsideWard(ward);
        }
    }

    private void revealEntitiesInsideWard(CaveInimicumWardBlockEntity ward) {
        CaveInimicumWardBounds bounds = ward.bounds();
        BlockPos min = bounds.min();
        BlockPos max = bounds.max();
        AABB area = new AABB(
                min.getX(), min.getY(), min.getZ(),
                max.getX() + 1.0D, max.getY() + 1.0D, max.getZ() + 1.0D
        );
        boolean changed = false;
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (!bounds.contains(living.blockPosition())) {
                continue;
            }
            if (!ModEvents.dispelIllusionsAndInvisibility(living)) {
                continue;
            }
            changed = true;
            level.sendParticles(ParticleTypes.GLOW, living.getX(), living.getY() + 1.0D, living.getZ(), 8, 0.3D, 0.45D, 0.3D, 0.02D);
            level.sendParticles(ParticleTypes.END_ROD, living.getX(), living.getY() + 1.0D, living.getZ(), 5, 0.24D, 0.4D, 0.24D, 0.01D);
        }
        if (changed) {
            level.playSound(null, ward.getBlockPos(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.4f, 1.4f);
        }
    }

    private void handleBarrierProjectiles(CaveInimicumWardBlockEntity ward) {
        CaveInimicumWardBounds bounds = ward.bounds();
        BlockPos min = bounds.min();
        BlockPos max = bounds.max();
        AABB scanBox = new AABB(
                min.getX() - 1.5, min.getY() - 1.5, min.getZ() - 1.5,
                max.getX() + 2.5, max.getY() + 2.5, max.getZ() + 2.5
        );

        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, scanBox, this::shouldBarrierAffectProjectile)) {
            Vec3 current = projectile.position();
            Vec3 previous = current.subtract(projectile.getDeltaMovement());
            boolean previousInside = bounds.contains(BlockPos.containing(previous));
            boolean currentInside = bounds.contains(projectile.blockPosition());
            if (previousInside == currentInside) {
                continue;
            }
            if (projectile instanceof com.hogwartsmagic.entity.SpellBeamEntity beam
                    && com.hogwartsmagic.spell.support.AlohomoraSpell.tryUnlockBarrier(level, ward, beam)) {
                continue;
            }
            blockProjectileAtBarrier(projectile, previousInside ? previous : current);
        }
    }

    private boolean shouldBarrierAffectProjectile(Projectile projectile) {
        if (projectile == null || !projectile.isAlive()) {
            return false;
        }
        if (projectile instanceof com.hogwartsmagic.entity.SpellBeamEntity beam
                && beam.getSpellType() == com.hogwartsmagic.spell.SpellType.REVELIO) {
            return false;
        }
        Entity owner = projectile.getOwner();
        return !(owner instanceof ServerPlayer player && player.isSpectator());
    }

    private void blockProjectileAtBarrier(Projectile projectile, Vec3 impactPos) {
        level.sendParticles(ParticleTypes.FLASH, impactPos.x, impactPos.y, impactPos.z, 1, 0.08, 0.08, 0.08, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, impactPos.x, impactPos.y, impactPos.z, 16, 0.18, 0.18, 0.18, 0.01);
        level.sendParticles(ParticleTypes.ENCHANT, impactPos.x, impactPos.y, impactPos.z, 14, 0.22, 0.22, 0.22, 0.02);
        level.playSound(null, impactPos.x, impactPos.y, impactPos.z, SoundEvents.SHIELD_BLOCK, SoundSource.BLOCKS, 0.9f, 1.3f);
        projectile.discard();
    }

    private void sendVisibleBlockEntityUpdates(ServerPlayer player, CaveInimicumWardBounds bounds) {
        BlockPos min = bounds.min();
        for (int y = 0; y < bounds.edgeLength(); y++) {
            for (int z = 0; z < bounds.edgeLength(); z++) {
                for (int x = 0; x < bounds.edgeLength(); x++) {
                    BlockPos pos = min.offset(x, y, z);
                    if (!resolveVisibleStateForPlayer(player, pos).equals(level.getBlockState(pos))) {
                        continue;
                    }
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (blockEntity != null) {
                        net.minecraft.network.protocol.Packet<?> updatePacket = blockEntity.getUpdatePacket();
                        if (updatePacket != null) {
                            player.connection.send(updatePacket);
                        }
                    }
                }
            }
        }
    }

    private void syncActiveWardLists() {
        for (ServerPlayer player : level.players()) {
            syncActiveWardListToPlayer(player);
        }
    }

    public void syncActiveWardListToPlayer(ServerPlayer player) {
        List<SpellPackets.CaveInimicumActiveWardData> wards = new ArrayList<>();
        for (CaveInimicumWardBlockEntity ward : illusionWards()) {
            if (isPlayerRelevantToWard(player, ward) && ward.isConcealing(level.getGameTime()) && !playerSeesRealityInWard(player, ward)) {
                wards.add(new SpellPackets.CaveInimicumActiveWardData(ward.bounds().center(), ward.bounds().edgeLength()));
            }
        }
        SpellPackets.CHANNEL.send(
                new SpellPackets.CaveInimicumActiveWardsPacket(wards),
                net.minecraftforge.network.PacketDistributor.PLAYER.with(player)
        );
    }

    private List<CaveInimicumWardBlockEntity> trackedWards() {
        List<CaveInimicumWardBlockEntity> wards = new ArrayList<>();
        List<BlockPos> invalid = new ArrayList<>();
        for (BlockPos wardPos : trackedWardPositions) {
            BlockEntity blockEntity = level.getBlockEntity(wardPos);
            if (blockEntity instanceof CaveInimicumWardBlockEntity ward && ward.isOperational()) {
                wards.add(ward);
            } else if (blockEntity instanceof CaveInimicumWardBlockEntity) {
                invalid.add(wardPos);
            } else if (!level.isLoaded(wardPos)) {
                invalid.add(wardPos);
            }
        }
        trackedWardPositions.removeAll(invalid);
        invalid.forEach(concealmentByWard::remove);
        return wards;
    }

    private List<CaveInimicumWardBlockEntity> illusionWards() {
        return illusionWards(trackedWards());
    }

    private List<CaveInimicumWardBlockEntity> illusionWards(List<CaveInimicumWardBlockEntity> wards) {
        List<CaveInimicumWardBlockEntity> illusionWards = new ArrayList<>();
        for (CaveInimicumWardBlockEntity ward : wards) {
            if (ward.isActive()) {
                illusionWards.add(ward);
            }
        }
        return illusionWards;
    }

    /**
     * True if {@code pos} lies inside any active ward. Used by Apparition as the
     * Hogwarts-style anti-Apparition jinx: you cannot vanish into or out of a warded area.
     */
    public boolean isWardedAt(BlockPos pos) {
        for (CaveInimicumWardBlockEntity ward : trackedWards()) {
            if (ward.isActive() && ward.bounds().contains(pos)) {
                return true;
            }
        }
        return false;
    }

    private List<CaveInimicumWardBlockEntity> wardsContaining(BlockPos pos) {
        List<CaveInimicumWardBlockEntity> wards = new ArrayList<>();
        for (CaveInimicumWardBlockEntity ward : trackedWards()) {
            if (ward.bounds().contains(pos)) {
                wards.add(ward);
            }
        }
        return wards;
    }

    private boolean isPlayerRelevantToBounds(ServerPlayer player, CaveInimicumWardBounds bounds) {
        int range = Math.max(48, bounds.edgeLength() * 3);
        return player.blockPosition().closerThan(bounds.center(), range);
    }

}
