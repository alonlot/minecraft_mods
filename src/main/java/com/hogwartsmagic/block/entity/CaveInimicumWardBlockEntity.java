package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.WardBlock;
import com.hogwartsmagic.caveinimicum.CaveInimicumManager;
import com.hogwartsmagic.caveinimicum.CaveInimicumSnapshot;
import com.hogwartsmagic.caveinimicum.CaveInimicumSnapshotFactory;
import com.hogwartsmagic.caveinimicum.CaveInimicumWardBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Persistent ward anchor that owns the saved illusion snapshot and the runtime
 * data needed to keep per-player block views coherent.
 */
public class CaveInimicumWardBlockEntity extends BlockEntity {
    private static final DustParticleOptions OUTLINE_PARTICLE =
            new DustParticleOptions(new Vector3f(0.46f, 0.94f, 0.88f), 1.0f);
    private static final DustParticleOptions BARRIER_PARTICLE =
            new DustParticleOptions(new Vector3f(0.72f, 0.86f, 1.0f), 1.0f);

    private boolean active;
    private UUID ownerUuid;
    private long expireGameTime;
    private long activatedGameTime;
    private long revealedUntilGameTime;
    private boolean protegoMaximaActive;
    private boolean revelioActive;
    /** Game time until which the Protego Maxima barrier is down (Alohomora at Master). */
    private long barrierSuppressedUntil;
    private boolean caveInimicumPrepared;
    private boolean protegoMaximaPrepared;
    private boolean revelioPrepared;
    private int configuredEdgeLength = CaveInimicumWardBounds.DEFAULT_EDGE_LENGTH;
    private int outlineTicks;
    private CaveInimicumSnapshot snapshot;
    private int[] actualStateCache;

    public CaveInimicumWardBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.CAVE_INIMICUM_WARD_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CaveInimicumWardBlockEntity ward) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (ward.outlineTicks > 0) {
            ward.outlineTicks--;
            if (serverLevel.getGameTime() % 5L == 0L) {
                ward.spawnOutline(serverLevel);
            }
        }
        if (ward.shouldRenderProtegoBarrier(serverLevel.getGameTime()) && serverLevel.getGameTime() % 3L == 0L) {
            ward.spawnProtegoBarrier(serverLevel);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        syncVisualState();
        if (level instanceof ServerLevel serverLevel && isOperational()) {
            CaveInimicumManager.get(serverLevel).registerWard(this);
            if (active) {
                refreshActualStateCache(serverLevel);
            }
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel && isOperational()) {
            CaveInimicumManager.get(serverLevel).unregisterWard(this, true);
        }
        super.setRemoved();
    }

    public boolean toggleOrActivate(ServerLevel level, @Nullable LivingEntity caster) {
        if (active && caster != null && caster.getUUID().equals(ownerUuid)) {
            deactivate(level, true);
            return true;
        }
        return activate(level, caster);
    }

    public boolean prepareCaveInimicum(@Nullable LivingEntity caster) {
        boolean changed = !caveInimicumPrepared;
        caveInimicumPrepared = true;
        if (caster != null) {
            ownerUuid = caster.getUUID();
        }
        outlineTicks = Math.max(outlineTicks, 100);
        setChanged();
        syncToClients();
        return changed;
    }

    public boolean prepareProtegoMaxima(@Nullable LivingEntity caster) {
        boolean changed = !protegoMaximaPrepared;
        protegoMaximaPrepared = true;
        if (caster != null) {
            ownerUuid = caster.getUUID();
        }
        outlineTicks = Math.max(outlineTicks, 100);
        setChanged();
        syncToClients();
        return changed;
    }

    public boolean prepareRevelio(@Nullable LivingEntity caster) {
        boolean changed = !revelioPrepared;
        revelioPrepared = true;
        if (caster != null) {
            ownerUuid = caster.getUUID();
        }
        outlineTicks = Math.max(outlineTicks, 100);
        setChanged();
        syncToClients();
        return changed;
    }

    public boolean activatePreparedModes(ServerLevel level, @Nullable LivingEntity caster) {
        boolean wasOperational = isOperational();
        boolean changed = false;
        if (caveInimicumPrepared && !isActive()) {
            changed |= activate(level, caster);
        }
        if (protegoMaximaPrepared && !protegoMaximaActive) {
            changed |= enableProtegoMaxima(level);
        }
        if (revelioPrepared && !revelioActive) {
            changed |= enableRevelio(level);
        }
        if (changed) {
            if (!wasOperational && isOperational()) {
                CaveInimicumManager.get(level).registerWard(this);
            }
            outlineTicks = Math.max(outlineTicks, 80);
            setChanged();
            syncToClients();
        }
        return changed;
    }

    public boolean activate(ServerLevel level, @Nullable LivingEntity caster) {
        int edgeLength = Math.max(CaveInimicumWardBounds.DEFAULT_EDGE_LENGTH, configuredEdgeLength);
        boolean hadProtegoMaxima = protegoMaximaActive;
        this.snapshot = CaveInimicumSnapshotFactory.capture(level, worldPosition, edgeLength);
        this.actualStateCache = snapshot.copyStateIds();
        this.active = true;
        this.ownerUuid = caster == null ? null : caster.getUUID();
        this.caveInimicumPrepared = true;
        this.activatedGameTime = level.getGameTime();
        this.expireGameTime = activatedGameTime + Config.getCaveInimicumDurationTicks();
        this.revealedUntilGameTime = 0L;
        this.protegoMaximaActive = hadProtegoMaxima;
        this.revelioActive = revelioPrepared || revelioActive;
        this.outlineTicks = 100;
        setChanged();
        syncToClients();
        CaveInimicumManager.get(level).registerWard(this);
        level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
        return true;
    }

    public void deactivate(ServerLevel level, boolean syncRestore) {
        if (!isOperational()) {
            return;
        }
        active = false;
        expireGameTime = 0L;
        revealedUntilGameTime = 0L;
        protegoMaximaActive = false;
        revelioActive = false;
        actualStateCache = null;
        setChanged();
        syncToClients();
        CaveInimicumManager.get(level).unregisterWard(this, syncRestore);
        level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    public boolean shouldExpire(long currentGameTime) {
        return isOperational() && expireGameTime > 0L && currentGameTime >= expireGameTime;
    }

    public boolean isActive() {
        return active && snapshot != null;
    }

    public boolean isOperational() {
        return isActive() || protegoMaximaActive || revelioActive;
    }

    public boolean isIdle() {
        return !isOperational() && !caveInimicumPrepared && !protegoMaximaPrepared;
    }

    public boolean isPrepared() {
        return caveInimicumPrepared || protegoMaximaPrepared || revelioPrepared;
    }

    public boolean isCaveInimicumPrepared() {
        return caveInimicumPrepared;
    }

    public boolean isProtegoMaximaPrepared() {
        return protegoMaximaPrepared;
    }

    public boolean isRevelioPrepared() {
        return revelioPrepared;
    }

    public long getActivatedGameTime() {
        return activatedGameTime;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public long getExpireGameTime() {
        return expireGameTime;
    }

    public long getRevealedUntilGameTime() {
        return revealedUntilGameTime;
    }

    public boolean isProtegoMaximaActive() {
        return protegoMaximaActive;
    }

    public boolean isRevelioActive() {
        return revelioActive;
    }

    public boolean shouldRenderProtegoBarrier(long gameTime) {
        return isBarrierUp(gameTime) && (!isActive() || isRevealed(gameTime));
    }

    /** The barrier is on and not switched off by an Alohomora. */
    public boolean isBarrierUp(long gameTime) {
        return protegoMaximaActive && gameTime >= barrierSuppressedUntil;
    }

    public void suppressBarrier(ServerLevel level, int ticks) {
        barrierSuppressedUntil = Math.max(barrierSuppressedUntil, level.getGameTime() + Math.max(1, ticks));
        outlineTicks = Math.max(outlineTicks, 40);
        setChanged();
        syncToClients();
    }

    public int getConfiguredEdgeLength() {
        return configuredEdgeLength;
    }

    public CaveInimicumSnapshot getSnapshot() {
        return snapshot;
    }

    public boolean isRevealed(long gameTime) {
        return isActive() && revealedUntilGameTime > gameTime;
    }

    public boolean isConcealing(long gameTime) {
        return isActive() && !isRevealed(gameTime);
    }

    public boolean disruptFor(ServerLevel level, int durationTicks) {
        if (!isActive()) {
            return false;
        }
        long newRevealUntil = level.getGameTime() + Math.max(1, durationTicks);
        if (newRevealUntil <= revealedUntilGameTime) {
            return false;
        }
        revealedUntilGameTime = newRevealUntil;
        outlineTicks = Math.max(outlineTicks, 40);
        setChanged();
        syncToClients();
        return true;
    }

    public boolean toggleProtegoMaxima(ServerLevel level) {
        if (!isOperational()) {
            activatedGameTime = level.getGameTime();
        }
        protegoMaximaActive = !protegoMaximaActive;
        protegoMaximaPrepared = protegoMaximaActive || protegoMaximaPrepared;
        if (protegoMaximaActive) {
            expireGameTime = level.getGameTime() + Config.getCaveInimicumDurationTicks();
        } else if (!isActive()) {
            expireGameTime = 0L;
        }
        outlineTicks = Math.max(outlineTicks, 60);
        setChanged();
        syncToClients();
        return true;
    }

    public boolean enableProtegoMaxima(ServerLevel level) {
        if (protegoMaximaActive) {
            return false;
        }
        if (!isOperational()) {
            activatedGameTime = level.getGameTime();
        }
        protegoMaximaPrepared = true;
        protegoMaximaActive = true;
        expireGameTime = level.getGameTime() + Config.getCaveInimicumDurationTicks();
        outlineTicks = Math.max(outlineTicks, 60);
        setChanged();
        syncToClients();
        return true;
    }

    public boolean enableRevelio(ServerLevel level) {
        if (revelioActive) {
            return false;
        }
        if (!isOperational()) {
            activatedGameTime = level.getGameTime();
        }
        revelioPrepared = true;
        revelioActive = true;
        expireGameTime = level.getGameTime() + Config.getCaveInimicumDurationTicks();
        outlineTicks = Math.max(outlineTicks, 60);
        setChanged();
        syncToClients();
        return true;
    }

    public BlockState getOutsideVisibleState(BlockPos pos) {
        if (pos.equals(worldPosition)) {
            return Blocks.AIR.defaultBlockState();
        }
        return snapshot.stateAt(pos);
    }

    public CaveInimicumWardBounds bounds() {
        int edgeLength = snapshot != null ? snapshot.edgeLength() : configuredEdgeLength;
        return new CaveInimicumWardBounds(worldPosition, edgeLength);
    }

    public boolean isInside(Player player) {
        return bounds().contains(player);
    }

    public boolean isInside(BlockPos pos) {
        return bounds().contains(pos);
    }

    public int[] getOrCreateActualStateCache(ServerLevel level) {
        if (actualStateCache == null && snapshot != null) {
            refreshActualStateCache(level);
        }
        return actualStateCache == null ? new int[0] : actualStateCache;
    }

    public void refreshActualStateCache(ServerLevel level) {
        if (snapshot == null) {
            actualStateCache = null;
            return;
        }
        CaveInimicumWardBounds bounds = snapshot.bounds();
        actualStateCache = new int[bounds.volume()];
        for (int i = 0; i < actualStateCache.length; i++) {
            actualStateCache[i] = net.minecraft.world.level.block.Block.getId(level.getBlockState(bounds.posAtIndex(i)));
        }
    }

    public int cycleConfiguredSize() {
        if (isOperational()) {
            outlineTicks = 100;
            return configuredEdgeLength;
        }
        int min = CaveInimicumWardBounds.DEFAULT_EDGE_LENGTH;
        int max = Math.max(min, Config.getCaveInimicumMaxEdgeLength());
        int step = Math.max(2, Config.getCaveInimicumSizeStep());
        int next = configuredEdgeLength + step;
        if (next > max) {
            next = min;
        }
        configuredEdgeLength = next;
        outlineTicks = 100;
        setChanged();
        syncToClients();
        return configuredEdgeLength;
    }

    public void showOutlinePreview() {
        outlineTicks = 100;
        setChanged();
        syncToClients();
    }

    public Component getStatusMessage() {
        if (isOperational()) {
            long remaining = Math.max(0L, expireGameTime - (level == null ? 0L : level.getGameTime()));
            long revealRemaining = Math.max(0L, revealedUntilGameTime - (level == null ? 0L : level.getGameTime()));
            long seconds = remaining / 20L;
            if (revealRemaining > 0L) {
                return Component.literal("Ward exposed - reality visible for " + (revealRemaining / 20L) + "s, " + seconds + "s total remaining");
            }
            if (isActive()) {
                String barrier = protegoMaximaActive ? ", Protego Maxima barrier active" : "";
                String revelio = revelioActive ? ", Revelio field active" : "";
                return Component.literal("Ward active - " + bounds().edgeLength() + "x" + bounds().edgeLength() + "x" + bounds().edgeLength() + " illusion, " + seconds + "s remaining" + barrier + revelio);
            }
            if (revelioActive && protegoMaximaActive) {
                return Component.literal("Ward active - Revelio field and Protego Maxima barrier for " + seconds + "s");
            }
            if (revelioActive) {
                return Component.literal("Ward active - Revelio field for " + seconds + "s");
            }
            return Component.literal("Protego Maxima active - visible barrier for " + seconds + "s");
        }
        String prepared = describePreparedModes();
        if (!prepared.isEmpty()) {
            return Component.literal("Ward idle - prepared: " + prepared + ", size set to " + configuredEdgeLength + "x" + configuredEdgeLength + "x" + configuredEdgeLength);
        }
        return Component.literal("Ward idle - size set to " + configuredEdgeLength + "x" + configuredEdgeLength + "x" + configuredEdgeLength);
    }

    private String describePreparedModes() {
        if (caveInimicumPrepared && protegoMaximaPrepared) {
            return revelioPrepared ? "Cave Inimicum + Protego Maxima + Revelio" : "Cave Inimicum + Protego Maxima";
        }
        if (caveInimicumPrepared) {
            return revelioPrepared ? "Cave Inimicum + Revelio" : "Cave Inimicum";
        }
        if (protegoMaximaPrepared) {
            return revelioPrepared ? "Protego Maxima + Revelio" : "Protego Maxima";
        }
        if (revelioPrepared) {
            return "Revelio";
        }
        return "";
    }

    private void spawnOutline(ServerLevel level) {
        CaveInimicumWardBounds bounds = bounds();
        BlockPos min = bounds.min();
        BlockPos max = bounds.max();
        double step = Math.max(1.0, bounds.edgeLength() / 8.0);
        for (double x = min.getX(); x <= max.getX() + 1; x += step) {
            for (double y = min.getY(); y <= max.getY() + 1; y += step) {
                spawnEdgePoint(level, x, y, min.getZ());
                spawnEdgePoint(level, x, y, max.getZ() + 1);
            }
        }
        for (double x = min.getX(); x <= max.getX() + 1; x += step) {
            for (double z = min.getZ(); z <= max.getZ() + 1; z += step) {
                spawnEdgePoint(level, x, min.getY(), z);
                spawnEdgePoint(level, x, max.getY() + 1, z);
            }
        }
        for (double y = min.getY(); y <= max.getY() + 1; y += step) {
            for (double z = min.getZ(); z <= max.getZ() + 1; z += step) {
                spawnEdgePoint(level, min.getX(), y, z);
                spawnEdgePoint(level, max.getX() + 1, y, z);
            }
        }
    }

    private void spawnEdgePoint(ServerLevel level, double x, double y, double z) {
        level.sendParticles(OUTLINE_PARTICLE, x + 0.02, y + 0.02, z + 0.02, 1, 0.0, 0.0, 0.0, 0.0);
        if (level.random.nextInt(4) == 0) {
            level.sendParticles(ParticleTypes.ENCHANT, x + 0.02, y + 0.02, z + 0.02, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private void spawnProtegoBarrier(ServerLevel level) {
        CaveInimicumWardBounds bounds = bounds();
        BlockPos min = bounds.min();
        BlockPos max = bounds.max();
        double centerX = (min.getX() + max.getX() + 1.0) * 0.5;
        double centerY = (min.getY() + max.getY() + 1.0) * 0.5;
        double centerZ = (min.getZ() + max.getZ() + 1.0) * 0.5;
        double radius = Math.max(4.5D, (bounds.edgeLength() * 0.5D) + 0.5D);
        long time = level.getGameTime();

        for (int i = 0; i < 28; i++) {
            double theta = level.random.nextDouble() * Math.PI * 2.0D;
            double phi = Math.acos((level.random.nextDouble() * 2.0D) - 1.0D);
            double variedRadius = radius + (level.random.nextDouble() - 0.5D) * 0.25D;
            double x = centerX + variedRadius * Math.sin(phi) * Math.cos(theta);
            double y = centerY + variedRadius * Math.cos(phi);
            double z = centerZ + variedRadius * Math.sin(phi) * Math.sin(theta);
            spawnBarrierPoint(level, x, y, z);
        }

        for (int ring = 0; ring < 3; ring++) {
            double y = centerY + (-1.2D + (ring * 1.2D));
            for (int step = 0; step < 18; step++) {
                double angle = ((Math.PI * 2.0D) / 18.0D) * step + (time * 0.08D * (ring % 2 == 0 ? 1.0D : -1.0D));
                double x = centerX + Math.cos(angle) * radius;
                double z = centerZ + Math.sin(angle) * radius;
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                if (step % 3 == 0) {
                    level.sendParticles(ParticleTypes.GLOW, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }
        }
    }

    private void spawnBarrierPoint(ServerLevel level, double x, double y, double z) {
        level.sendParticles(BARRIER_PARTICLE, x + 0.02, y + 0.02, z + 0.02, 1, 0.015, 0.015, 0.015, 0.0);
        if (level.random.nextBoolean()) {
            level.sendParticles(ParticleTypes.END_ROD, x + 0.02, y + 0.02, z + 0.02, 1, 0.02, 0.02, 0.02, 0.0);
        }
        if (level.random.nextInt(5) == 0) {
            level.sendParticles(ParticleTypes.GLOW, x + 0.02, y + 0.02, z + 0.02, 1, 0.03, 0.03, 0.03, 0.0);
        }
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide) {
            syncVisualState();
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    private void syncVisualState() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(WardBlock.LIT)) {
            return;
        }
        boolean shouldBeLit = !isIdle();
        if (state.getValue(WardBlock.LIT) != shouldBeLit) {
            level.setBlock(worldPosition, state.setValue(WardBlock.LIT, shouldBeLit), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Active", active);
        tag.putInt("ConfiguredEdgeLength", configuredEdgeLength);
        tag.putLong("ExpireGameTime", expireGameTime);
        tag.putLong("ActivatedGameTime", activatedGameTime);
        tag.putLong("RevealedUntilGameTime", revealedUntilGameTime);
        tag.putBoolean("ProtegoMaximaActive", protegoMaximaActive);
        tag.putBoolean("RevelioActive", revelioActive);
        tag.putLong("BarrierSuppressedUntil", barrierSuppressedUntil);
        tag.putBoolean("CaveInimicumPrepared", caveInimicumPrepared);
        tag.putBoolean("ProtegoMaximaPrepared", protegoMaximaPrepared);
        tag.putBoolean("RevelioPrepared", revelioPrepared);
        tag.putInt("OutlineTicks", outlineTicks);
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        if (snapshot != null) {
            tag.put("Snapshot", snapshot.save());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        active = tag.getBoolean("Active");
        configuredEdgeLength = Math.max(
                1,
                Math.min(
                        com.hogwartsmagic.caveinimicum.CaveInimicumSnapshot.MAX_EDGE_LENGTH,
                        tag.contains("ConfiguredEdgeLength") ? tag.getInt("ConfiguredEdgeLength") : CaveInimicumWardBounds.DEFAULT_EDGE_LENGTH
                )
        );
        expireGameTime = tag.getLong("ExpireGameTime");
        activatedGameTime = tag.getLong("ActivatedGameTime");
        revealedUntilGameTime = tag.getLong("RevealedUntilGameTime");
        protegoMaximaActive = tag.getBoolean("ProtegoMaximaActive");
        revelioActive = tag.getBoolean("RevelioActive");
        barrierSuppressedUntil = tag.getLong("BarrierSuppressedUntil");
        caveInimicumPrepared = tag.getBoolean("CaveInimicumPrepared");
        protegoMaximaPrepared = tag.getBoolean("ProtegoMaximaPrepared");
        revelioPrepared = tag.getBoolean("RevelioPrepared");
        outlineTicks = Math.max(0, tag.getInt("OutlineTicks"));
        ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        snapshot = tag.contains("Snapshot") ? CaveInimicumSnapshot.load(tag.getCompound("Snapshot")) : null;
        actualStateCache = snapshot == null ? null : snapshot.copyStateIds();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("Active", active);
        tag.putInt("ConfiguredEdgeLength", configuredEdgeLength);
        tag.putLong("ExpireGameTime", expireGameTime);
        tag.putLong("ActivatedGameTime", activatedGameTime);
        tag.putLong("RevealedUntilGameTime", revealedUntilGameTime);
        tag.putBoolean("ProtegoMaximaActive", protegoMaximaActive);
        tag.putBoolean("RevelioActive", revelioActive);
        tag.putLong("BarrierSuppressedUntil", barrierSuppressedUntil);
        tag.putBoolean("CaveInimicumPrepared", caveInimicumPrepared);
        tag.putBoolean("ProtegoMaximaPrepared", protegoMaximaPrepared);
        tag.putBoolean("RevelioPrepared", revelioPrepared);
        tag.putInt("OutlineTicks", outlineTicks);
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        if (snapshot != null) {
            tag.put("Snapshot", snapshot.save());
        }
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
