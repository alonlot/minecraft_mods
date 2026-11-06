package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.util.MagicParticles;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.FlooFireplaceBlock;
import com.hogwartsmagic.network.SpellPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FlooFireplaceBlockEntity extends BlockEntity {
    private static final int MAX_LINKED_FIREPLACES = 256;

    private static final com.hogwartsmagic.MagicParticleOptions FLOO_GLOW =
            com.hogwartsmagic.MagicParticleOptions.mote(0.3f, 1.0f, 0.65f, 1.2f);
    private final List<BlockPos> linkedPositions = new ArrayList<>();
    private UUID owner;
    private String fireplaceName = "";
    private int activeTicks;
    /**
     * The player whose dropped powder opened the travel screen here. That powder pays for the trip,
     * so choosing a destination does not take a second one from the inventory. Not saved.
     */
    @Nullable
    private UUID prepaidPlayer;
    private long prepaidUntil;

    public FlooFireplaceBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.FLOO_FIREPLACE_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FlooFireplaceBlockEntity fireplace) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) {
            level.setBlock(pos, state.setValue(net.minecraft.world.level.block.CampfireBlock.LIT, true), 3);
            state = level.getBlockState(pos);
        }
        fireplace.tryIgniteFromDroppedPowder(serverLevel);
        if (fireplace.activeTicks > 0) {
            fireplace.activeTicks--;
            fireplace.setActiveBlockState(true);
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.getX() + 0.5,
                    pos.getY() + 0.75,
                    pos.getZ() + 0.5,
                    5, 0.2, 0.08, 0.2, 0.008);
            serverLevel.sendParticles(MagicParticles.SMOKE_GREEN,
                    pos.getX() + 0.5,
                    pos.getY() + 0.92,
                    pos.getZ() + 0.5,
                    6, 0.26, 0.16, 0.26, 0.012);
            serverLevel.sendParticles(MagicParticles.SMOKE_DARK,
                    pos.getX() + 0.5,
                    pos.getY() + 0.82,
                    pos.getZ() + 0.5,
                    3, 0.18, 0.22, 0.18, 0.005);
            serverLevel.sendParticles(MagicParticles.MOTE_GREEN,
                    pos.getX() + 0.5,
                    pos.getY() + 0.95,
                    pos.getZ() + 0.5,
                    1, 0.2, 0.1, 0.2, 0.0);
            serverLevel.sendParticles(FLOO_GLOW,
                    pos.getX() + 0.5,
                    pos.getY() + 0.72,
                    pos.getZ() + 0.5,
                    2, 0.22, 0.08, 0.22, 0.0);
            if (serverLevel.random.nextInt(3) == 0) {
                serverLevel.sendParticles(ParticleTypes.ASH,
                        pos.getX() + 0.5,
                        pos.getY() + 0.85,
                        pos.getZ() + 0.5,
                        1, 0.15, 0.08, 0.15, 0.0);
            }
            if (fireplace.activeTicks == 0) {
                fireplace.setActiveBlockState(false);
            }
        } else if (!fireplace.fireplaceName.isBlank() && serverLevel.getGameTime() % 8L == 0L) {
            serverLevel.sendParticles(MagicParticles.SMOKE_GREY,
                    pos.getX() + 0.5,
                    pos.getY() + 0.75,
                    pos.getZ() + 0.5,
                    1, 0.12, 0.05, 0.12, 0.0);
        }
    }

    private void tryIgniteFromDroppedPowder(ServerLevel level) {
        // Prevent reopening the travel screen every tick while the fireplace is already active.
        if (this.activeTicks > 0) {
            return;
        }
        AABB powderBox = new AABB(worldPosition).inflate(0.35, 0.2, 0.35).move(0.0, 0.2, 0.0);
        ItemEntity powderEntity = level.getEntitiesOfClass(ItemEntity.class, powderBox,
                entity -> entity.isAlive() && entity.getItem().is(ExampleMod.FLOO_POWDER.get()))
                .stream()
                .findFirst()
                .orElse(null);
        if (powderEntity == null) {
            return;
        }

        ServerPlayer player = level.getEntitiesOfClass(ServerPlayer.class,
                        new AABB(worldPosition).inflate(0.7, 1.4, 0.7),
                        serverPlayer -> serverPlayer.onGround())
                .stream()
                .findFirst()
                .orElse(null);
        if (player == null) {
            return;
        }

        if (!SpellPackets.openFlooSelection(player, this)) {
            return;
        }
        this.prepaidPlayer = player.getUUID();
        this.prepaidUntil = level.getGameTime() + 1200L;
        ItemEntity itemEntity = powderEntity;
        itemEntity.getItem().shrink(1);
        if (itemEntity.getItem().isEmpty()) {
            itemEntity.discard();
        }
        ignite(level);
        level.playSound(null, worldPosition, com.hogwartsmagic.ModSounds.FLOO_IGNITE.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    public void ignite(ServerLevel level) {
        this.activeTicks = Math.max(this.activeTicks, 110);
        setChanged();
        setActiveBlockState(true);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.75,
                worldPosition.getZ() + 0.5,
                26, 0.25, 0.14, 0.25, 0.01);
        level.sendParticles(MagicParticles.SMOKE_GREEN,
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.9,
                worldPosition.getZ() + 0.5,
                18, 0.24, 0.2, 0.24, 0.01);
        level.sendParticles(MagicParticles.SMOKE_DARK,
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.88,
                worldPosition.getZ() + 0.5,
                12, 0.22, 0.28, 0.22, 0.008);
        level.sendParticles(FLOO_GLOW,
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.72,
                worldPosition.getZ() + 0.5,
                16, 0.26, 0.1, 0.26, 0.0);
    }

    private void setActiveBlockState(boolean active) {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (state.hasProperty(FlooFireplaceBlock.ACTIVE) && state.getValue(FlooFireplaceBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(FlooFireplaceBlock.ACTIVE, active), 3);
        }
    }

    /** True if this player's dropped powder already paid for a trip from here. */
    public boolean hasPrepaidTravel(Player player) {
        return this.prepaidPlayer != null && this.prepaidPlayer.equals(player.getUUID())
                && this.level != null && this.level.getGameTime() <= this.prepaidUntil;
    }

    /** Uses up the prepaid trip; returns false if there was none (the caller then takes inventory powder). */
    public boolean consumePrepaidTravel(Player player) {
        if (!hasPrepaidTravel(player)) {
            return false;
        }
        this.prepaidPlayer = null;
        return true;
    }

    public void addLink(BlockPos pos, @Nullable Player player) {
        if (!linkedPositions.contains(pos)) {
            linkedPositions.add(pos.immutable());
        }
        if (player != null && this.owner == null) {
            this.owner = player.getUUID();
        }
        setChanged();
        syncToClients();
    }

    public void setOwner(@Nullable Player player) {
        if (player != null) {
            this.owner = player.getUUID();
            setChanged();
            syncToClients();
        }
    }

    /** The player who claimed this fireplace by first linking or naming it, or null if unclaimed. */
    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public List<BlockPos> getLinkedPositions() {
        return List.copyOf(linkedPositions);
    }

    public void setFireplaceName(String fireplaceName, @Nullable Player player) {
        this.fireplaceName = fireplaceName;
        if (player != null) {
            this.owner = player.getUUID();
        }
        setChanged();
        syncToClients();
    }

    public String getFireplaceName() {
        return fireplaceName;
    }

    public boolean isNamed() {
        return !fireplaceName.isBlank();
    }

    public Component getStatusMessage(Player player) {
        String name = fireplaceName.isBlank() ? "Unnamed Floo fireplace" : fireplaceName;
        return Component.literal(name + " - stand on the soul flames and drop Floo Powder to travel");
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("FireplaceName", fireplaceName);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        ListTag listTag = new ListTag();
        for (BlockPos linkedPos : linkedPositions) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", linkedPos.asLong());
            listTag.add(entry);
        }
        tag.put("Links", listTag);
        tag.putInt("ActiveTicks", activeTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fireplaceName = tag.getString("FireplaceName");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        linkedPositions.clear();
        ListTag listTag = tag.getList("Links", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(listTag.size(), MAX_LINKED_FIREPLACES); i++) {
            CompoundTag entry = listTag.getCompound(i);
            linkedPositions.add(BlockPos.of(entry.getLong("Pos")));
        }
        activeTicks = Math.max(0, tag.getInt("ActiveTicks"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putString("FireplaceName", fireplaceName);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        ListTag listTag = new ListTag();
        for (BlockPos linkedPos : linkedPositions) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", linkedPos.asLong());
            listTag.add(entry);
        }
        tag.put("Links", listTag);
        tag.putInt("ActiveTicks", activeTicks);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
