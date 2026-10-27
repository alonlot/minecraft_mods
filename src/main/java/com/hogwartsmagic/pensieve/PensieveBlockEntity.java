package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** The memory held in a Pensieve basin, who poured it, and whether the basin is a shared one. */
public class PensieveBlockEntity extends BlockEntity {
    private ItemStack memory = ItemStack.EMPTY;
    @Nullable private UUID pourer;
    private boolean publicBasin;
    /** True while a session is playing at this basin. Synced to clients for the glow; deliberately not written to
     *  disk, so a crash can never leave a basin lit with nobody inside. */
    private boolean playing;

    public PensieveBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.PENSIEVE_BLOCK_ENTITY.get(), pos, state);
    }

    public ItemStack memory() {
        return memory;
    }

    @Nullable
    public UUID pourer() {
        return pourer;
    }

    public boolean publicBasin() {
        return publicBasin;
    }

    public boolean playing() {
        return playing;
    }

    /** Server side: a session started or stopped playing at this basin. */
    public void setPlaying(boolean playing) {
        if (this.playing != playing) {
            this.playing = playing;
            sync();
        }
    }

    public void setPublicBasin(boolean publicBasin) {
        this.publicBasin = publicBasin;
        setChanged();
    }

    public void pour(ItemStack stack, @Nullable UUID who) {
        this.memory = stack.copyWithCount(1);
        this.pourer = who;
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && stack.getItem() instanceof PensieveMemory memory) {
            // A recorded memory is read from the world save: start loading it before the diver arrives.
            com.hogwartsmagic.pensieve.capture.RecordingScene.idOf(memory.sceneId(stack)).ifPresent(
                    id -> com.hogwartsmagic.pensieve.capture.RecordingStore.prefetch(serverLevel.getServer(), id));
        }
        sync();
    }

    public ItemStack takeOut() {
        ItemStack out = memory;
        memory = ItemStack.EMPTY;
        pourer = null;
        sync();
        return out;
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
        if (!memory.isEmpty()) {
            tag.put("Memory", memory.save(registries));
        }
        if (pourer != null) {
            tag.putUUID("Pourer", pourer);
        }
        tag.putBoolean("Public", publicBasin);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        memory = tag.contains("Memory") ? ItemStack.parse(registries, tag.getCompound("Memory")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        pourer = tag.hasUUID("Pourer") ? tag.getUUID("Pourer") : null;
        publicBasin = tag.getBoolean("Public");
        playing = tag.getBoolean("Playing");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("Playing", playing);
        if (!memory.isEmpty()) {
            tag.put("Memory", memory.save(registries));
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
