package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.block.WardBlock;
import com.hogwartsmagic.caveinimicum.CaveInimicumManager;
import com.hogwartsmagic.caveinimicum.CaveInimicumSnapshot;
import com.hogwartsmagic.caveinimicum.CaveInimicumSnapshotFactory;
import com.hogwartsmagic.caveinimicum.CaveInimicumWardBounds;
import com.hogwartsmagic.spell.SpellBeamFx;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Persistent ward anchor that owns the saved illusion snapshot and the runtime
 * data needed to keep per-player block views coherent.
 */
public class CaveInimicumWardBlockEntity extends BlockEntity {
    /**
     * The ward anchors loaded on this client, for the client's ward renderer (the outline and the Protego Maxima
     * barrier are drawn there from the synced flags, not with particles). Filled on the client thread only; weak, so
     * a world left behind never holds on to its block entities.
     */
    private static final Set<CaveInimicumWardBlockEntity> CLIENT_LOADED =
            Collections.newSetFromMap(new WeakHashMap<>());

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
    /** Client only: the game time the outline preview runs to, sent in the update tag. */
    private long outlineUntil;
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
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level.isClientSide) {
            CLIENT_LOADED.add(this);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide) {
            CLIENT_LOADED.remove(this);
        }
        if (level instanceof ServerLevel serverLevel && isOperational()) {
            CaveInimicumManager.get(serverLevel).unregisterWard(this, true);
        }
        super.setRemoved();
    }

    /** The ward anchors this client has loaded (client thread only; empty on a server). */
    public static Collection<CaveInimicumWardBlockEntity> clientLoaded() {
        return CLIENT_LOADED;
    }

    /** Client side: the game time the outline preview runs to. */
    public long getOutlineUntil() {
        return outlineUntil;
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
        level.playSound(null, worldPosition, ModSounds.WARD_ACTIVATE.get(), SoundSource.BLOCKS, 1.2f, 1.0f);
        SpellBeamFx.effectAt(level, SpellType.CAVE_INIMICUM, Vec3.atCenterOf(worldPosition).add(0.0, 0.4, 0.0),
                SpellBeamFx.GLOW);
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
        level.playSound(null, worldPosition, ModSounds.WARD_DEACTIVATE.get(), SoundSource.BLOCKS, 1.1f, 1.0f);
        SpellBeamFx.effectAt(level, SpellType.CAVE_INIMICUM, Vec3.atCenterOf(worldPosition).add(0.0, 0.4, 0.0),
                SpellBeamFx.FIZZLE);
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

    /**
     * The barrier is up and Cave Inimicum is not hiding it. Read from the synced flags rather than isActive(),
     * which needs the snapshot the client never gets.
     */
    public boolean isBarrierVisible(long gameTime) {
        return isBarrierUp(gameTime) && (!active || revealedUntilGameTime > gameTime);
    }

    /** Game time until which a Master Alohomora holds the barrier down. */
    public long getBarrierSuppressedUntil() {
        return barrierSuppressedUntil;
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

    /** Where the Protego Maxima dome stands: the middle of the ward block's floor. */
    public Vec3 barrierCentre() {
        return new Vec3(worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5);
    }

    /** The dome's radius: half the ward's width and a little more, never smaller than the cast dome. */
    public double barrierRadius() {
        return Math.max(4.5, bounds().edgeLength() * 0.5 + 0.5);
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
            BlockPos pos = bounds.posAtIndex(i);
            // Never load a chunk for this; an unloaded corner is picked up by the next scan once loaded.
            actualStateCache[i] = level.isLoaded(pos)
                    ? net.minecraft.world.level.block.Block.getId(level.getBlockState(pos))
                    : snapshot.stateIdAt(pos);
        }
    }

    public int cycleConfiguredSize() {
        if (isOperational()) {
            outlineTicks = 100;
            setChanged();
            syncToClients();
            return configuredEdgeLength;
        }
        int min = CaveInimicumWardBounds.DEFAULT_EDGE_LENGTH;
        int max = Math.max(min, Config.getCaveInimicumMaxEdgeLength());
        int step = Math.max(2, Config.getCaveInimicumSizeStep());
        if ((step & 1) != 0) {
            step++; // bounds must stay an even edge length
        }
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
        // Deactivated from WardBlock.onRemove, the level already holds the replacing block: setting the
        // ward back here would undo the break.
        if (isRemoved() || level.getBlockState(worldPosition) != state) {
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
                2,
                Math.min(
                        com.hogwartsmagic.caveinimicum.CaveInimicumSnapshot.MAX_EDGE_LENGTH,
                        tag.contains("ConfiguredEdgeLength") ? tag.getInt("ConfiguredEdgeLength") : CaveInimicumWardBounds.DEFAULT_EDGE_LENGTH
                )
        );
        if ((configuredEdgeLength & 1) != 0) {
            configuredEdgeLength++; // CaveInimicumWardBounds requires an even edge length
        }
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
        outlineUntil = tag.getLong("OutlineUntil");
        ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        snapshot = tag.contains("Snapshot") ? CaveInimicumSnapshot.load(tag.getCompound("Snapshot")) : null;
        // A ward copied elsewhere with its NBT (/clone, a structure template) keeps the old centre: its
        // bounds() are taken from worldPosition, so indexing the snapshot by them would run off the array.
        if (snapshot != null && !snapshot.center().equals(worldPosition)) {
            snapshot = null;
        }
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
        if (level != null) {
            tag.putLong("OutlineUntil", level.getGameTime() + outlineTicks);
        }
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        // No snapshot here: nothing on the client reads it, and at the 64-block maximum it is a
        // 1 MB int array that went out with every chunk send and every ward click.
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
