package com.hogwartsmagic.entity;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.util.HorcruxData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

public class HorcruxSoulEntity extends Entity {
    private static final int MAX_FLIGHT_TICKS = 140;
    private static final int ARRIVAL_LINGER_TICKS = 20;
    private static final EntityDataAccessor<Optional<UUID>> DATA_PLAYER_ID =
            SynchedEntityData.defineId(HorcruxSoulEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> DATA_PLAYER_NAME =
            SynchedEntityData.defineId(HorcruxSoulEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<ItemStack> DATA_MAINHAND =
            SynchedEntityData.defineId(HorcruxSoulEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_OFFHAND =
            SynchedEntityData.defineId(HorcruxSoulEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_HEAD =
            SynchedEntityData.defineId(HorcruxSoulEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_CHEST =
            SynchedEntityData.defineId(HorcruxSoulEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_LEGS =
            SynchedEntityData.defineId(HorcruxSoulEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_FEET =
            SynchedEntityData.defineId(HorcruxSoulEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Float> DATA_BODY_YAW =
            SynchedEntityData.defineId(HorcruxSoulEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<BlockPos> DATA_TARGET =
            SynchedEntityData.defineId(HorcruxSoulEntity.class, EntityDataSerializers.BLOCK_POS);
    private boolean arrivalTriggered;
    private int arrivalTick = -1;

    public HorcruxSoulEntity(EntityType<? extends HorcruxSoulEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static HorcruxSoulEntity fromPlayer(ServerLevel level, net.minecraft.server.level.ServerPlayer player, BlockPos targetPos) {
        HorcruxSoulEntity soul = ModEntities.HORCRUX_SOUL.get().create(level);
        if (soul == null) {
            return null;
        }
        soul.setPlayerId(player.getUUID());
        soul.setPlayerName(player.getScoreboardName());
        soul.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        soul.setBodyYaw(player.yBodyRot);
        soul.setTarget(targetPos);
        soul.setItemSlot(EquipmentSlot.MAINHAND, HorcruxData.getDisplayStack(player, HorcruxData.HORCRUX_MAINHAND, player.registryAccess()));
        soul.setItemSlot(EquipmentSlot.OFFHAND, HorcruxData.getDisplayStack(player, HorcruxData.HORCRUX_OFFHAND, player.registryAccess()));
        soul.setItemSlot(EquipmentSlot.HEAD, HorcruxData.getDisplayStack(player, HorcruxData.HORCRUX_HEAD, player.registryAccess()));
        soul.setItemSlot(EquipmentSlot.CHEST, HorcruxData.getDisplayStack(player, HorcruxData.HORCRUX_CHEST, player.registryAccess()));
        soul.setItemSlot(EquipmentSlot.LEGS, HorcruxData.getDisplayStack(player, HorcruxData.HORCRUX_LEGS, player.registryAccess()));
        soul.setItemSlot(EquipmentSlot.FEET, HorcruxData.getDisplayStack(player, HorcruxData.HORCRUX_FEET, player.registryAccess()));
        return soul;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_PLAYER_ID, Optional.empty());
        builder.define(DATA_PLAYER_NAME, "");
        builder.define(DATA_MAINHAND, ItemStack.EMPTY);
        builder.define(DATA_OFFHAND, ItemStack.EMPTY);
        builder.define(DATA_HEAD, ItemStack.EMPTY);
        builder.define(DATA_CHEST, ItemStack.EMPTY);
        builder.define(DATA_LEGS, ItemStack.EMPTY);
        builder.define(DATA_FEET, ItemStack.EMPTY);
        builder.define(DATA_BODY_YAW, 0.0F);
        builder.define(DATA_TARGET, BlockPos.ZERO);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 destination = Vec3.atCenterOf(getTarget()).add(0.0D, 0.6D, 0.0D);
        Vec3 toTarget = destination.subtract(position());
        // Flight integration is server-authoritative; the client receives position via entity sync.
        if (!level().isClientSide && !arrivalTriggered && toTarget.lengthSqr() > 1.0E-4D) {
            Vec3 velocity = toTarget.normalize().scale(Config.getHorcruxSoulSpeed()).add(0.0D, 0.015D, 0.0D);
            setDeltaMovement(velocity);
            moveTo(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z, getYRot(), getXRot());
        }
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.9, getZ(),
                    3, 0.14, 0.2, 0.14, 0.01);
            serverLevel.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.0, getZ(),
                    2, 0.12, 0.15, 0.12, 0.005);
            if (tickCount % 2 == 0) {
                serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, getX(), getY() + 0.7, getZ(),
                        1, 0.08, 0.08, 0.08, 0.01);
            }
        }

        if (!level().isClientSide && !arrivalTriggered && (toTarget.lengthSqr() < 0.5D || tickCount > MAX_FLIGHT_TICKS)) {
            arrivalTriggered = true;
            arrivalTick = tickCount;
            setDeltaMovement(Vec3.ZERO);
            moveTo(destination.x, destination.y, destination.z, getYRot(), getXRot());
            notifyOwnerFlightComplete();
        }
        if (!level().isClientSide && arrivalTriggered && arrivalTick >= 0 && tickCount >= arrivalTick + ARRIVAL_LINGER_TICKS) {
            discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("PlayerId")) {
            setPlayerId(tag.getUUID("PlayerId"));
        }
        setPlayerName(tag.getString("PlayerName"));
        setBodyYaw(tag.getFloat("BodyYaw"));
        setTarget(BlockPos.of(tag.getLong("Target")));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        getPlayerId().ifPresent(id -> tag.putUUID("PlayerId", id));
        tag.putString("PlayerName", getPlayerName());
        tag.putFloat("BodyYaw", getBodyYaw());
        tag.putLong("Target", getTarget().asLong());
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(0.6F, 1.95F);
    }

    public Optional<UUID> getPlayerId() {
        return entityData.get(DATA_PLAYER_ID);
    }

    public void setPlayerId(UUID id) {
        entityData.set(DATA_PLAYER_ID, Optional.ofNullable(id));
    }

    public String getPlayerName() {
        return entityData.get(DATA_PLAYER_NAME);
    }

    public void setPlayerName(String name) {
        entityData.set(DATA_PLAYER_NAME, name == null ? "" : name);
    }

    public float getBodyYaw() {
        return entityData.get(DATA_BODY_YAW);
    }

    public void setBodyYaw(float bodyYaw) {
        entityData.set(DATA_BODY_YAW, bodyYaw);
    }

    public BlockPos getTarget() {
        return entityData.get(DATA_TARGET);
    }

    public void setTarget(BlockPos target) {
        entityData.set(DATA_TARGET, target == null ? BlockPos.ZERO : target);
    }

    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        ItemStack copy = stack == null ? ItemStack.EMPTY : stack.copy();
        switch (slot) {
            case MAINHAND -> entityData.set(DATA_MAINHAND, copy);
            case OFFHAND -> entityData.set(DATA_OFFHAND, copy);
            case HEAD -> entityData.set(DATA_HEAD, copy);
            case CHEST -> entityData.set(DATA_CHEST, copy);
            case LEGS -> entityData.set(DATA_LEGS, copy);
            case FEET -> entityData.set(DATA_FEET, copy);
            default -> {
            }
        }
    }

    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> entityData.get(DATA_MAINHAND);
            case OFFHAND -> entityData.get(DATA_OFFHAND);
            case HEAD -> entityData.get(DATA_HEAD);
            case CHEST -> entityData.get(DATA_CHEST);
            case LEGS -> entityData.get(DATA_LEGS);
            case FEET -> entityData.get(DATA_FEET);
            default -> ItemStack.EMPTY;
        };
    }

    private void notifyOwnerFlightComplete() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        getPlayerId()
                .map(id -> serverLevel.getServer().getPlayerList().getPlayer(id))
                .ifPresent(player -> SpellPackets.CHANNEL.send(new SpellPackets.FinishHorcruxDeathSequencePacket(),
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(player)));
    }
}
