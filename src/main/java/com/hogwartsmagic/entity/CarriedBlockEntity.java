package com.hogwartsmagic.entity;

import com.hogwartsmagic.spell.TerrainRules;
import com.hogwartsmagic.spell.motion.WingardiumRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A block lifted by Wingardium Leviosa (Spell Mastery Phase 7b). While the caster's channel steers it
 * (markHeld every tick) it floats with no gravity; let go, it falls and, once on the ground, places itself
 * through TerrainRules.canPlace as its lifter, or drops as its own item where it may not (claims, wards,
 * spawn protection, an occupied spot, a block that can't stand there). Thrown, it strikes the first
 * creature in its path for the thrower's damage and then falls. It never breaks blocks, is never hurt,
 * and is saved with the world, so a carried block survives a restart.
 */
public class CarriedBlockEntity extends Entity {
    private static final EntityDataAccessor<BlockState> DATA_BLOCK =
            SynchedEntityData.defineId(CarriedBlockEntity.class, EntityDataSerializers.BLOCK_STATE);

    @Nullable
    private UUID ownerUuid;
    private long heldAt = Long.MIN_VALUE / 2;
    private boolean thrown;
    private float impactDamage;
    private int idleTicks;

    public CarriedBlockEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BLOCK, Blocks.STONE.defaultBlockState());
    }

    /** The block it carries and who lifted it (placement rights). Call before adding it to the level. */
    public void setCarried(BlockState state, @Nullable UUID owner) {
        this.entityData.set(DATA_BLOCK, state);
        this.ownerUuid = owner;
    }

    public BlockState getCarriedState() {
        return this.entityData.get(DATA_BLOCK);
    }

    /** The Wingardium channel calls this every tick it steers the block. */
    public void markHeld(long gameTime) {
        this.heldAt = gameTime;
        this.thrown = false;
        this.idleTicks = 0;
    }

    public boolean isHeld() {
        return this.level().getGameTime() - this.heldAt <= WingardiumRules.HELD_GRACE_TICKS;
    }

    /** Thrown by its lifter: flies on {@code velocity}, hits the first creature in its way for {@code damage}. */
    public void launch(Vec3 velocity, float damage) {
        this.heldAt = Long.MIN_VALUE / 2;
        this.thrown = true;
        this.impactDamage = damage;
        this.setDeltaMovement(velocity);
        this.hasImpulse = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return; // the client only draws it; positions arrive every tick (updateInterval 1)
        }
        boolean held = this.isHeld();
        if (!held) {
            this.idleTicks++;
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -WingardiumRules.GRAVITY, 0.0D).scale(WingardiumRules.DRAG));
        }
        Vec3 motion = this.getDeltaMovement();
        if (this.thrown && this.strikeAlongPath(level, motion)) {
            return;
        }
        this.move(MoverType.SELF, motion);
        if (this.thrown && (this.horizontalCollision || this.verticalCollision)) {
            // Hit a wall or a ceiling: the flight ends and it drops from here.
            this.thrown = false;
            this.setDeltaMovement(0.0D, Math.min(0.0D, this.getDeltaMovement().y), 0.0D);
            this.hasImpulse = true;
        }
        if (!held && (this.onGround() || this.idleTicks > WingardiumRules.IDLE_MAX_TICKS)) {
            this.land(level);
        }
    }

    /** True when it hit a creature this tick (it then stops flying and falls). */
    private boolean strikeAlongPath(ServerLevel level, Vec3 motion) {
        Entity owner = this.ownerUuid == null ? null : level.getEntity(this.ownerUuid);
        for (Entity hit : level.getEntities(this, this.getBoundingBox().expandTowards(motion).inflate(0.25D),
                candidate -> candidate instanceof LivingEntity living && living.isAlive() && !candidate.isSpectator()
                        && (this.ownerUuid == null || !this.ownerUuid.equals(candidate.getUUID())))) {
            DamageSource source = this.damageSources().thrown(this, owner);
            if (this.impactDamage > 0.0F) {
                hit.hurt(source, this.impactDamage);
            }
            this.thrown = false;
            this.setDeltaMovement(motion.scale(0.2D));
            this.hasImpulse = true;
            return true;
        }
        return false;
    }

    /** Places itself if it may, otherwise drops as its own item; never lost, never doubled. */
    private void land(ServerLevel level) {
        BlockState state = this.getCarriedState();
        BlockPos pos = this.blockPosition();
        Entity owner = this.ownerUuid == null ? null : level.getEntity(this.ownerUuid);
        if (level.isLoaded(pos) && level.getBlockState(pos).canBeReplaced() && state.canSurvive(level, pos)
                && TerrainRules.canPlace(level, pos, owner)) {
            level.setBlockAndUpdate(pos, state);
        } else {
            ItemStack stack = new ItemStack(state.getBlock().asItem());
            if (!stack.isEmpty()) {
                this.spawnAtLocation(stack);
            }
        }
        this.discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false; // like a falling block: arrows and spell beams pass through; the channel's hold uses isPickable
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("BlockState")) {
            this.entityData.set(DATA_BLOCK, NbtUtils.readBlockState(this.level().holderLookup(Registries.BLOCK), tag.getCompound("BlockState")));
        }
        this.ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        this.thrown = tag.getBoolean("Thrown");
        this.impactDamage = tag.getFloat("Damage");
        this.idleTicks = tag.getInt("Idle");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("BlockState", NbtUtils.writeBlockState(this.getCarriedState()));
        if (this.ownerUuid != null) {
            tag.putUUID("Owner", this.ownerUuid);
        }
        tag.putBoolean("Thrown", this.thrown);
        tag.putFloat("Damage", this.impactDamage);
        tag.putInt("Idle", this.idleTicks);
    }
}
