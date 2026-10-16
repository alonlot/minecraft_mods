package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.entity.ModEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The helpless body a viewer leaves standing at the basin while their mind is inside a memory. Hitting it hurts
 * the viewer (see {@link PensieveSessions#forwardDamage}). It never moves, never drops anything and is never
 * saved, so a crash cannot leave a body behind. If its owner is not a live viewer any more it removes itself.
 */
public class PensieveBodyEntity extends LivingEntity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(PensieveBodyEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> OWNER_NAME =
            SynchedEntityData.defineId(PensieveBodyEntity.class, EntityDataSerializers.STRING);

    public PensieveBodyEntity(EntityType<? extends PensieveBodyEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    /** Spawns a body for {@code owner} at {@code at}, turned to {@code yaw}. */
    public static PensieveBodyEntity spawn(ServerLevel level, ServerPlayer owner, Vec3 at, float yaw) {
        PensieveBodyEntity body = ModEntities.PENSIEVE_BODY.get().create(level);
        if (body == null) return null;
        body.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        body.moveTo(at.x, at.y, at.z, yaw, 0f);
        body.setYHeadRot(yaw);
        body.setYBodyRot(yaw);
        level.addFreshEntity(body);
        return body;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
        builder.define(OWNER_NAME, "");
    }

    public void setOwner(UUID id, String name) {
        this.entityData.set(OWNER, Optional.of(id));
        this.entityData.set(OWNER_NAME, name);
    }

    @Nullable
    public UUID getOwnerId() {
        return this.entityData.get(OWNER).orElse(null);
    }

    public String getOwnerName() {
        return this.entityData.get(OWNER_NAME);
    }

    @Override
    public Component getName() {
        String n = getOwnerName();
        return n.isEmpty() ? super.getName() : Component.literal(n);
    }

    @Nullable
    private ServerPlayer liveOwner() {
        UUID id = getOwnerId();
        if (id == null || level().getServer() == null) return null;
        ServerPlayer p = level().getServer().getPlayerList().getPlayer(id);
        return p != null && PensieveSessions.isViewer(p) ? p : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 20 == 0 && liveOwner() == null) {
            discard();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) return false;
        ServerPlayer owner = liveOwner();
        if (owner == null) {
            discard();
            return false;
        }
        // A body clipped into a block edge would otherwise suffocate its viewer a heart a second.
        if (amount <= 0f || source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) return false;
        PensieveSessions.forwardDamage(owner, source, amount);
        this.hurtTime = this.hurtDuration = 10;
        level().broadcastEntityEvent(this, (byte) 2);
        return true;
    }

    // ---- A body that stays put ----------------------------------------------------------------------------

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean attackable() {
        return true;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return List.of();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }
}
