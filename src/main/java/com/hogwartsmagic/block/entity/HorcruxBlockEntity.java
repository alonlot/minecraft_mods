package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.HorcruxBlock;
import com.hogwartsmagic.entity.DementorEntity;
import com.hogwartsmagic.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class HorcruxBlockEntity extends BlockEntity {
    private UUID ownerUuid;
    private boolean active;
    private boolean primed;
    private int flareTicks;
    private boolean suppressForcedBreakEffects;

    public HorcruxBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.HORCRUX_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, HorcruxBlockEntity horcrux) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (horcrux.flareTicks > 0) {
            horcrux.flareTicks--;
        }
        if ((horcrux.active || horcrux.primed) && serverLevel.getGameTime() % 4L == 0L) {
            serverLevel.sendParticles(ParticleTypes.SOUL,
                    pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5,
                    2, 0.18, 0.14, 0.18, 0.005);
            serverLevel.sendParticles(ParticleTypes.SQUID_INK,
                    pos.getX() + 0.5, pos.getY() + 0.75, pos.getZ() + 0.5,
                    1, 0.14, 0.06, 0.14, 0.004);
            if (serverLevel.random.nextInt(horcrux.active ? 5 : 3) == 0) {
                serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH,
                        pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
                        1, 0.12, 0.1, 0.12, 0.002);
            }
        }
        if (horcrux.flareTicks > 0 && serverLevel.getGameTime() % 2L == 0L) {
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.getX() + 0.5, pos.getY() + 0.85, pos.getZ() + 0.5,
                    4, 0.2, 0.2, 0.2, 0.01);
        }
    }

    public boolean beginRitual(ServerLevel level, Player player) {
        if ((active || primed) && ownerUuid != null && !ownerUuid.equals(player.getUUID())) {
            return false;
        }
        ownerUuid = player.getUUID();
        primed = true;
        active = false;
        flareTicks = 80;
        setChanged();
        syncToClients();
        level.setBlock(worldPosition, getBlockState().setValue(HorcruxBlock.LIT, true), 3);
        level.playSound(null, worldPosition, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 0.9f, 0.55f);
        return true;
    }

    public boolean completeBinding(ServerLevel level, Player player) {
        if (!primed || ownerUuid == null || !ownerUuid.equals(player.getUUID())) {
            return false;
        }
        active = true;
        primed = false;
        flareTicks = 120;
        setChanged();
        syncToClients();
        level.setBlock(worldPosition, getBlockState().setValue(HorcruxBlock.LIT, true), 3);
        level.playSound(null, worldPosition, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 0.9f, 0.55f);
        level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.55f, 0.5f);
        return true;
    }

    public void shatter(ServerLevel level) {
        suppressForcedBreakEffects = true;
        active = false;
        primed = false;
        ownerUuid = null;
        flareTicks = 0;
        setChanged();
        syncToClients();
        level.sendParticles(ParticleTypes.SQUID_INK, worldPosition.getX() + 0.5, worldPosition.getY() + 0.8, worldPosition.getZ() + 0.5,
                18, 0.28, 0.2, 0.28, 0.02);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, worldPosition.getX() + 0.5, worldPosition.getY() + 0.75, worldPosition.getZ() + 0.5,
                16, 0.25, 0.18, 0.25, 0.03);
        level.playSound(null, worldPosition, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.85f, 0.45f);
        level.playSound(null, worldPosition, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 0.9f, 0.65f);
        level.destroyBlock(worldPosition, false);
    }

    public void handleForcedDestruction(ServerLevel level, @Nullable Player breaker) {
        if (suppressForcedBreakEffects) {
            return;
        }
        suppressForcedBreakEffects = true;
        boolean hostileBreak = ownerUuid != null && breaker != null && !ownerUuid.equals(breaker.getUUID());
        level.sendParticles(ParticleTypes.SQUID_INK, worldPosition.getX() + 0.5, worldPosition.getY() + 0.85, worldPosition.getZ() + 0.5,
                30, 0.35, 0.24, 0.35, 0.03);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 0.85, worldPosition.getZ() + 0.5,
                20, 0.24, 0.18, 0.24, 0.02);
        level.playSound(null, worldPosition, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0f, 0.35f);
        level.playSound(null, worldPosition, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 0.9f, 0.55f);
        if (hostileBreak) {
            summonDementor(level, breaker);
        }
        active = false;
        primed = false;
        ownerUuid = null;
        flareTicks = 0;
        setChanged();
        syncToClients();
    }

    private void summonDementor(ServerLevel level, Player breaker) {
        DementorEntity dementor = ModEntities.DEMENTOR.get().create(level);
        if (dementor == null) {
            return;
        }
        dementor.moveTo(worldPosition.getX() + 0.5D, worldPosition.getY() + 1.8D, worldPosition.getZ() + 0.5D, breaker.getYRot(), 0.0F);
        dementor.finalizeSpawn(level, level.getCurrentDifficultyAt(worldPosition), MobSpawnType.EVENT, null);
        dementor.setTarget(breaker);
        level.addFreshEntity(dementor);
        level.sendParticles(ParticleTypes.SOUL, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5,
                26, 0.35, 0.55, 0.35, 0.02);
        level.playSound(null, worldPosition, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 0.7f, 0.45f);
    }

    public boolean isActive() {
        return active;
    }

    public boolean isPrimed() {
        return primed;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public Component getStatusMessage() {
        if (active) {
            return Component.literal("A Horcrux hums with imprisoned soul magic and stolen life.");
        }
        if (primed) {
            return Component.literal("The altar is blood-warm. Stand near it while it devours your heart-force.");
        }
        return Component.literal("Kill a creature over this altar with Avada Kedavra to begin the forbidden rite.");
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putBoolean("Active", active);
        tag.putBoolean("Primed", primed);
        tag.putInt("FlareTicks", flareTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        active = tag.getBoolean("Active");
        primed = tag.getBoolean("Primed");
        flareTicks = tag.getInt("FlareTicks");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
