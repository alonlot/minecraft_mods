package com.hogwartsmagic.portrait;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The Fat Lady, hanging on a wall like a vanilla painting (2 blocks wide, 4 tall) but alive: {@link FatLadyBrain}
 * decides what she says, the entity only carries the result in three synced values, and the client
 * (com.hogwartsmagic.client.portrait) turns a changed {@link #SERIAL} into a voice line, a subtitle and an animation.
 *
 * <p>Mechanics are the vanilla {@code Painting}'s: bounding box from the tile position and facing, facing in the
 * save data and in the add-entity packet, no movement, discard-and-drop when the wall goes. Differences: a normal
 * punch does not break her (it makes her cross), sneak + punch does, explosions never hurt her.
 *
 * <p>Common class: nothing client-side is referenced.
 */
public class FatLadyPortraitEntity extends HangingEntity {
    public static final int WIDTH = 2;
    public static final int HEIGHT = 4;

    /** +1 for every new line; the client plays a line exactly once per change. */
    private static final EntityDataAccessor<Integer> SERIAL =
            SynchedEntityData.defineId(FatLadyPortraitEntity.class, EntityDataSerializers.INT);
    /** Index into {@link FatLadyLines#LINES} of the current (or last) line. */
    private static final EntityDataAccessor<Integer> LINE =
            SynchedEntityData.defineId(FatLadyPortraitEntity.class, EntityDataSerializers.INT);
    /** Entity id of the player she looks at, -1 for nobody. */
    private static final EntityDataAccessor<Integer> LOOK =
            SynchedEntityData.defineId(FatLadyPortraitEntity.class, EntityDataSerializers.INT);

    private static final int BRAIN_INTERVAL = 10;

    private final FatLadyBrain brain = new FatLadyBrain(this);
    private int brainTimer;

    public FatLadyPortraitEntity(EntityType<? extends FatLadyPortraitEntity> type, Level level) {
        super(type, level);
    }

    public FatLadyPortraitEntity(Level level, BlockPos pos, Direction direction) {
        super(PortraitRegistry.FAT_LADY_TYPE, level, pos);
        this.setDirection(direction);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SERIAL, 0);
        builder.define(LINE, -1);
        builder.define(LOOK, -1);
    }

    // ---------------------------------------------------------------- synced state

    public int getSerial() {
        return this.entityData.get(SERIAL);
    }

    public int getLineIndex() {
        return this.entityData.get(LINE);
    }

    public int getLookId() {
        return this.entityData.get(LOOK);
    }

    /** Server only: starts a line (the client notices the serial change). */
    void startLine(int lineIndex) {
        this.entityData.set(LINE, lineIndex);
        this.entityData.set(SERIAL, this.entityData.get(SERIAL) + 1);
    }

    void setLook(int entityId) {
        if (this.entityData.get(LOOK) != entityId) {
            this.entityData.set(LOOK, entityId);
        }
    }

    public FatLadyBrain getBrain() {
        return brain;
    }

    /** Called by the explosion, lightning and punch hooks. Server only. */
    public void trigger(FatLadyLines.Reaction reaction, @Nullable ServerPlayer who) {
        if (this.level() instanceof ServerLevel && !this.isRemoved()) {
            brain.trigger(reaction, who);
        }
    }

    /** The unit vector pointing out of the canvas toward whoever looks at her. */
    public Vec3 frontVector() {
        Direction d = this.direction == null ? Direction.SOUTH : this.direction;
        return new Vec3(d.getStepX(), 0, d.getStepZ());
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public void tick() {
        super.tick(); // the vanilla "wall still there?" check, server side
        if (!this.level().isClientSide && !this.isRemoved() && this.level() instanceof ServerLevel server) {
            if (++brainTimer >= BRAIN_INTERVAL) {
                brainTimer = 0;
                brain.tick(server);
            }
        }
    }

    // ---------------------------------------------------------------- damage

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved()) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            trigger(FatLadyLines.Reaction.EXPLOSION, null);
            return false;
        }
        if (source.getEntity() instanceof Player player && source.getDirectEntity() == player) {
            if (player.isShiftKeyDown()) {
                return super.hurt(source, amount); // sneak + punch takes her down
            }
            trigger(FatLadyLines.Reaction.PUNCHED, player instanceof ServerPlayer sp ? sp : null);
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount); // /kill and the void
        }
        return false; // arrows, fire and so on do nothing
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (attacker instanceof Player player) {
            if (this.level().mayInteract(player, this.pos)) {
                this.hurt(this.damageSources().playerAttack(player), 0.0F);
            }
            return true; // handled either way: no second pass through the normal attack path
        }
        return false;
    }

    // ---------------------------------------------------------------- vanilla Painting mechanics

    @Override
    protected AABB calculateBoundingBox(BlockPos pos, Direction facing) {
        Vec3 wallPoint = Vec3.atCenterOf(pos).relative(facing, -0.46875);
        double offsetX = WIDTH % 2 == 0 ? 0.5 : 0.0;
        double offsetY = HEIGHT % 2 == 0 ? 0.5 : 0.0;
        Direction right = facing.getCounterClockWise();
        Vec3 center = wallPoint.relative(right, offsetX).relative(Direction.UP, offsetY);
        Direction.Axis axis = facing.getAxis();
        double sizeX = axis == Direction.Axis.X ? 0.0625 : (double) WIDTH;
        double sizeZ = axis == Direction.Axis.Z ? 0.0625 : (double) WIDTH;
        return AABB.ofSize(center, sizeX, (double) HEIGHT, sizeZ);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        tag.putByte("facing", (byte) this.direction.get2DDataValue());
        super.addAdditionalSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        this.direction = Direction.from2DDataValue(tag.getByte("facing"));
        super.readAdditionalSaveData(tag);
        this.setDirection(this.direction);
    }

    @Override
    public void dropItem(@Nullable Entity breaker) {
        if (this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            this.playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);
            if (breaker instanceof Player player && player.hasInfiniteMaterials()) {
                return;
            }
            if (PortraitRegistry.FAT_LADY_ITEM != null) {
                this.spawnAtLocation(PortraitRegistry.FAT_LADY_ITEM);
            }
        }
    }

    @Override
    public void playPlacementSound() {
        this.playSound(SoundEvents.PAINTING_PLACE, 1.0F, 1.0F);
    }

    @Override
    public void moveTo(double x, double y, double z, float yRot, float xRot) {
        this.setPos(x, y, z);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.setPos(x, y, z);
    }

    @Override
    public Vec3 trackingPosition() {
        return Vec3.atLowerCornerOf(this.pos);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, this.direction.get3DDataValue(), this.getPos());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.setDirection(Direction.from3DDataValue(packet.getData()));
    }

    @Override
    public ItemStack getPickResult() {
        return PortraitRegistry.FAT_LADY_ITEM == null ? ItemStack.EMPTY : new ItemStack(PortraitRegistry.FAT_LADY_ITEM);
    }

    @Override
    public Component getTypeName() {
        return Component.literal("The Fat Lady");
    }

    @Override
    public boolean isPickable() {
        return true;
    }
}
