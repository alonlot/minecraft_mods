package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.CaveBoatEntity;
import com.hogwartsmagic.locket.CaveLayout;
import com.hogwartsmagic.locket.LocketCaves;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * The iron ring set in the cave shore. It owns the chain's state: how long it stays revealed and how far
 * through the haul it is. The server decides everything; the client only gets what the chain renderer
 * draws (revealed, haulTicks, and where the chain meets the water) through the update tag.
 */
public class CaveRingBlockEntity extends BlockEntity {
    public static final int HAUL_TICKS = 60;
    public static final int TOUCH_REVEAL_TICKS = 200;
    private static final double BOAT_NEAR = 8.0D;

    /** Ticks of reveal left. */
    private int revealTicks;
    private int haulTicks;
    /** The water surface cell the chain runs down to (the cell under the shore dock); null until known. */
    @Nullable
    private BlockPos chainEnd;
    /** Transient cache of the basin's scanned (version 2) layout; never saved. */
    @Nullable
    private CaveLayout layout;

    public CaveRingBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.CAVE_RING_BLOCK_ENTITY.get(), pos, state);
    }

    public boolean revealed() {
        return revealTicks > 0;
    }

    public int haulTicks() {
        return haulTicks;
    }

    /** The water surface cell the chain ends at, or null when this cave's layout is unknown. */
    @Nullable
    public BlockPos chainEnd() {
        return chainEnd;
    }

    /** Shows the chain for this many ticks (never shortens a longer reveal). Server only. */
    public void reveal(int ticks) {
        if (level == null || level.isClientSide) {
            return;
        }
        resolveLayout();
        boolean was = revealed();
        revealTicks = Math.max(revealTicks, ticks);
        if (!was) {
            sync();
        }
    }

    /** Starts the haul if the chain is showing, no haul is under way and no skiff is already there. */
    public void pull(ServerPlayer player) {
        if (!(level instanceof ServerLevel server) || !revealed() || haulTicks > 0) {
            return;
        }
        CaveLayout l = resolveLayout();
        if (l == null) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.chain_slack"), true);
            return;
        }
        if (boatNear(server, l)) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.boat_already_here"), true);
            return;
        }
        haulTicks = 1;
        server.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 0.7F);
        sync();
    }

    private static boolean boatNear(ServerLevel server, CaveLayout l) {
        return !server.getEntitiesOfClass(CaveBoatEntity.class, new AABB(l.dockShore()).inflate(BOAT_NEAR)).isEmpty();
    }

    @Nullable
    private CaveLayout resolveLayout() {
        if (layout == null && level instanceof ServerLevel server) {
            Optional<DespairBasinBlockEntity> basin = LocketCaves.findBasinFor(server, worldPosition);
            // Only a scanned (version 2) layout counts; a legacy one means the markers are not read yet.
            if (basin.isPresent() && basin.get().layout().version() >= 2) {
                layout = basin.get().layout();
                chainEnd = layout.dockShore().below();
                setChanged();
            }
        }
        return layout;
    }

    /** The chain hangs well outside the ring block, so the renderer is culled by the whole run. */
    @Override
    public AABB getRenderBoundingBox() {
        BlockPos end = chainEnd != null ? chainEnd : worldPosition;
        return new AABB(worldPosition).minmax(new AABB(end)).inflate(2.0D);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (haulTicks > 0) {
            CaveLayout l = resolveLayout();
            if (l == null) {
                haulTicks = 0;
                sync();
                return;
            }
            haulTicks++;
            BlockPos dock = l.dockShore();
            if (haulTicks % 2 == 0) {
                server.sendParticles(com.hogwartsmagic.spell.vfx.SpellVfx.swap(ParticleTypes.BUBBLE),
                        dock.getX() + 0.5D, dock.getY() - 0.1D, dock.getZ() + 0.5D, 6, 0.5D, 0.1D, 0.5D, 0.05D);
            }
            if (haulTicks % 10 == 0) {
                server.playSound(null, worldPosition, SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 1.0F, 0.9F);
            }
            if (haulTicks > HAUL_TICKS) {
                haulTicks = 0;
                revealTicks = 0;
                CaveBoatEntity.rise(server, l);
            }
            sync();
            return;
        }
        if (revealTicks > 0 && --revealTicks == 0) {
            sync();
        }
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Reveal", revealTicks);
        tag.putInt("Haul", haulTicks);
        if (chainEnd != null) {
            tag.putLong("ChainEnd", chainEnd.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        revealTicks = tag.getInt("Reveal");
        haulTicks = tag.getInt("Haul");
        chainEnd = tag.contains("ChainEnd") ? BlockPos.of(tag.getLong("ChainEnd")) : null;
        layout = null; // never persisted: re-resolved from the basin on first use
    }

    /** The client needs only what the chain renderer draws. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Reveal", revealTicks);
        tag.putInt("Haul", haulTicks);
        if (chainEnd != null) {
            tag.putLong("ChainEnd", chainEnd.asLong());
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
