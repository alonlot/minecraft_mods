package com.hogwartsmagic.entity;

import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.worldgen.undercroft.CartTrack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The cart that runs the Gringotts line.
 *
 * <p>It follows a {@link CartTrack} spline rather than rails, because the point of the thing is the
 * near-vertical plunges and sustained banking that vanilla rails cannot do at all.
 *
 * <h2>It is server-authoritative, and that is deliberate</h2>
 *
 * <p>{@link #getControllingPassenger()} returns null on purpose. Returning the rider is what the
 * broom does, and it would be wrong here: vanilla then treats the vehicle as client-driven, which
 * turns on a collision re-check that snaps the cart back whenever it grazes the tunnel, overwrites
 * the orientation we derive from the spline tangent with whatever the client sent, and points the
 * anti-fly kick at exactly the free-falls this ride is built around. The rider has no steering
 * input to lose, so client authority buys nothing and costs all three.
 *
 * <h2>What is synced is the parameter, not the position</h2>
 *
 * <p>Both sides hold the same track and both integrate the same {@code t} along it, so the client
 * knows where the cart is without being told. That matters because vanilla quantises entity
 * rotation to a byte - 1.4 degrees a step - and has no roll field at all, which at this speed and
 * bank would be visible flutter. Orientation is re-derived from the tangent on each side instead.
 *
 * <p>The track itself never goes over the wire either: it is deterministic from an origin, a depth
 * and a version, so those three are synced and the client rebuilds an identical line.
 */
public class GringottsCartEntity extends Entity {

    private static final EntityDataAccessor<BlockPos> DATA_ORIGIN =
            SynchedEntityData.defineId(GringottsCartEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> DATA_DEPTH =
            SynchedEntityData.defineId(GringottsCartEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_T =
            SynchedEntityData.defineId(GringottsCartEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SPEED =
            SynchedEntityData.defineId(GringottsCartEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> DATA_STATE =
            SynchedEntityData.defineId(GringottsCartEntity.class, EntityDataSerializers.BYTE);

    /** States are bytes, not an enum: model and renderer code branches on them. */
    public static final byte STATE_IDLE = 0;
    public static final byte STATE_RIDING = 1;
    public static final byte STATE_ARRIVED = 2;
    public static final byte STATE_LOOSE = 3;

    /** Blocks per tick. 1.55 is about 31 m/s - roughly six times sprinting. */
    private static final double V_MAX = 1.55D;
    /** The cart is enchanted; it does not stall on a shallow stretch. */
    private static final double V_FLOOR = 0.16D;
    private static final double GRAVITY = 0.055D;
    private static final double DRAG = 0.0016D;
    private static final double ASSIST = 0.06D;
    private static final double BRAKE = 0.09D;
    /** Lateral acceleration budget, blocks/tick^2. About 1.3g, which is a hard but fair corner. */
    private static final double A_LAT_MAX = 0.033D;
    /** How far ahead curvature is read, so the cart slows BEFORE a corner rather than inside it. */
    private static final double LOOKAHEAD = 22.0D;

    @Nullable
    private CartTrack track;
    private long trackKey = Long.MIN_VALUE;

    private double t;
    private double v;

    /** Recomputed identically on both sides for render interpolation; never sent. */
    public float displayRoll, prevDisplayRoll;
    public float displayPitch, prevDisplayPitch;
    public double clientT, prevClientT;

    public GringottsCartEntity(EntityType<?> type, Level level) {
        super(type, level);
        // On a rail there is nothing to collide with: the corridor was carved around this line.
        this.noPhysics = true;
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ORIGIN, BlockPos.ZERO);
        builder.define(DATA_DEPTH, 0);
        builder.define(DATA_T, 0.0F);
        builder.define(DATA_SPEED, 0.0F);
        builder.define(DATA_STATE, STATE_IDLE);
    }

    /** Binds this cart to a line. Server side only. */
    public void bind(BlockPos origin, int depth) {
        this.entityData.set(DATA_ORIGIN, origin);
        this.entityData.set(DATA_DEPTH, depth);
        this.track = CartTrack.build(origin, depth);
        this.trackKey = key(origin, depth);
        this.t = 0.0D;
        this.v = 0.0D;
        setStateByte(STATE_IDLE);
        Vec3 start = this.track.positionAt(0.0D);
        setPos(start.x, start.y, start.z);
    }

    private static long key(BlockPos origin, int depth) {
        return origin.asLong() * 31L + depth;
    }

    /** Rebuilt from the three synced numbers, on whichever side is asking. */
    @Nullable
    private CartTrack track() {
        BlockPos origin = this.entityData.get(DATA_ORIGIN);
        int depth = this.entityData.get(DATA_DEPTH);
        if (depth <= 0) {
            return null;
        }
        long k = key(origin, depth);
        if (this.track == null || this.trackKey != k) {
            this.track = CartTrack.build(origin, depth);
            this.trackKey = k;
        }
        return this.track;
    }

    public byte getStateByte() {
        return this.entityData.get(DATA_STATE);
    }

    private void setStateByte(byte state) {
        this.entityData.set(DATA_STATE, state);
    }

    public double getSpeed() {
        return this.level().isClientSide ? this.entityData.get(DATA_SPEED) : this.v;
    }

    /** Top speed, blocks per tick. Exposed so client audio can normalise against it. */
    public static double maxSpeed() {
        return V_MAX;
    }

    /**
     * How far the wheels have rolled, in model units, so the renderer can turn them.
     *
     * <p>Arc length rather than accumulated movement: the cart is on a spline and {@code t} <em>is</em>
     * the distance it has covered, so the wheels cannot drift out of step with the track no matter
     * how the client interpolates.
     */
    public double rolledDistance() {
        return (this.level().isClientSide ? this.clientT : this.t) * 16.0D;
    }

    /**
     * Curvature where the cart is now.
     *
     * <p>This is what the flange screech is keyed on rather than speed, and it is the difference
     * between a ride that sounds like a spiral and one that sounds like a long straight tunnel: on
     * a straight it is zero and the screech is silent, and it rises through every switchback.
     */
    public double getCurvature() {
        CartTrack line = track();
        if (line == null) {
            return 0.0D;
        }
        return line.curvatureAt(this.level().isClientSide ? this.clientT : this.t);
    }

    // ------------------------------------------------------------------ riding

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.level().isClientSide) {
            return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < 2;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        // See the class comment. This is not an omission.
        return null;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dims,
                                               float partialTick) {
        // Seat by type, not by index: Entity.addPassenger force-inserts a Player at index 0, so an
        // index-based seat swaps the two riders the moment a player climbs in behind a goblin.
        double z = passenger instanceof Player ? 0.30D : -0.30D;
        float toRad = (float) Math.PI / 180.0F;
        float roll = Mth.lerp(partialTick, this.prevDisplayRoll, this.displayRoll) * toRad;
        float pitch = Mth.lerp(partialTick, this.prevDisplayPitch, this.displayPitch) * toRad;
        float yaw = Mth.rotLerp(partialTick, this.yRotO, this.getYRot()) * toRad;
        // On the bench, not hovering over the line. The model is anchored at the railhead a block
        // and a half below the cart line, so the cushion is here and the rider goes on it.
        return new Vec3(0.0D, -0.55D, z).zRot(-roll).xRot(pitch).yRot(-yaw);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        // Custom transforms plus steep angles false-cull otherwise, the same way the broom does.
        return this.getBoundingBox().inflate(3.0D, 2.0D, 3.0D);
    }

    // ------------------------------------------------------------------- ticking

    @Override
    public void tick() {
        this.prevDisplayRoll = this.displayRoll;
        this.prevDisplayPitch = this.displayPitch;
        this.prevClientT = this.clientT;
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();

        CartTrack line = track();
        if (line == null) {
            tickLoose();
            super.tick();
            return;
        }

        if (this.level().isClientSide) {
            // Follow the authoritative parameter without snapping: the two sides integrate the
            // same physics, so the gap is small and closing it smoothly is invisible.
            double authoritative = this.entityData.get(DATA_T);
            this.clientT += (authoritative - this.clientT) * 0.35D;
            if (Math.abs(authoritative - this.clientT) > 6.0D) {
                this.clientT = authoritative;
            }
        } else {
            // W sets it off, S brakes. Both ride in on vanilla's own input sync for passengers,
            // so neither needs a packet of its own.
            if (getStateByte() == STATE_IDLE) {
                for (Entity passenger : getPassengers()) {
                    if (passenger instanceof LivingEntity rider && rider.zza > 0.1F) {
                        launch();
                        break;
                    }
                }
            }
            advance(line);
            this.clientT = this.t;
            this.entityData.set(DATA_T, (float) this.t);
            this.entityData.set(DATA_SPEED, (float) this.v);
        }

        double at = Mth.clamp(this.clientT, 0.0D, line.length());
        Vec3 pos = line.positionAt(at);
        setPos(pos.x, pos.y, pos.z);
        applyOrientation(line, at);

        super.tick();
    }

    /** Gravity, drag, a magical shove, and a curvature speed limit read ahead of time. */
    private void advance(CartTrack line) {
        if (getStateByte() != STATE_RIDING) {
            this.v *= 0.6D;
            return;
        }
        Vec3 tangent = line.tangentAt(this.t);

        double kMax = 0.0D;
        for (int i = 1; i <= 4; i++) {
            kMax = Math.max(kMax, line.curvatureAt(this.t + LOOKAHEAD * i / 4.0D));
        }
        double target = Math.min(V_MAX, Math.sqrt(A_LAT_MAX / Math.max(kMax, 1.0E-4D)));

        double brake = 0.0D;
        for (Entity passenger : getPassengers()) {
            if (passenger instanceof LivingEntity rider && rider.zza < -0.1F) {
                brake = Math.min(1.0D, -rider.zza) * BRAKE;
            }
        }
        // Re-struck rather than looped: the clip is most of two seconds, so one every thirty ticks
        // is continuous drag without the two ever overlapping into a drone.
        if (brake > 0.0D && this.tickCount % 30 == 0 && this.level() instanceof ServerLevel s) {
            s.playSound(null, blockPosition(), ModSounds.CART_BRAKE.get(), SoundSource.BLOCKS,
                    1.1F, 1.0F);
        }

        double gravity = -GRAVITY * tangent.y;
        double drag = -this.v * this.v * DRAG;
        double assist = (target - this.v) * ASSIST;
        this.v = Mth.clamp(this.v + gravity + drag + assist - brake, V_FLOOR, V_MAX);
        this.t += this.v;

        if (this.t >= line.length()) {
            this.t = line.length();
            this.v = 0.0D;
            setStateByte(STATE_ARRIVED);
            if (this.level() instanceof ServerLevel server) {
                server.playSound(null, blockPosition(), ModSounds.CART_SLAM.get(),
                        SoundSource.BLOCKS, 1.4F, 1.0F);
            }
        }
    }

    private void applyOrientation(CartTrack line, double at) {
        Vec3 tangent = line.tangentAt(at);
        setYRot((float) (Mth.atan2(tangent.z, tangent.x) * 180.0D / Math.PI) - 90.0F);
        this.displayPitch = (float) -Math.toDegrees(Math.asin(Mth.clamp(tangent.y, -1.0D, 1.0D)));
        setXRot(this.displayPitch);
        float targetRoll = Mth.clamp(line.bankAt(at), -55.0F, 55.0F);
        this.displayRoll += (targetRoll - this.displayRoll) * 0.18F;
    }

    /** No line bound: behave like a heavy prop rather than pretending to be on a rail. */
    private void tickLoose() {
        if (getStateByte() != STATE_LOOSE) {
            setStateByte(STATE_LOOSE);
        }
        this.noPhysics = false;
        if (!this.level().isClientSide) {
            setDeltaMovement(getDeltaMovement().add(0.0D, -0.04D, 0.0D).multiply(0.95D, 1.0D, 0.95D));
            move(MoverType.SELF, getDeltaMovement());
        }
    }

    /** Space, from anyone aboard, sets it off. */
    public void launch() {
        if (getStateByte() == STATE_RIDING) {
            return;
        }
        setStateByte(STATE_RIDING);
        this.v = Math.max(this.v, V_FLOOR);
        if (this.level() instanceof ServerLevel server) {
            server.playSound(null, blockPosition(), ModSounds.CART_LAUNCH.get(), SoundSource.BLOCKS,
                    1.5F, 1.0F);
        }
    }

    @Override
    public void push(Entity entity) {
        // A cart on a rail is not shoved off it.
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        // Authority flips back to the server while the client is still at a stale position, and
        // without this the cart appears to vanish. Same fix the broom carries.
        if (this.level() instanceof ServerLevel server) {
            server.getChunkSource().broadcast(this,
                    new net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket(this));
            server.getChunkSource().broadcast(this,
                    new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(this));
        }
    }

    // --------------------------------------------------------------- persistence

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(DATA_ORIGIN, BlockPos.of(tag.getLong("Origin")));
        this.entityData.set(DATA_DEPTH, tag.getInt("Depth"));
        this.t = tag.getDouble("T");
        this.clientT = this.t;
        this.v = 0.0D;
        // Never resume mid-plunge into a client that is still loading chunks.
        setStateByte(tag.getByte("State") == STATE_LOOSE ? STATE_LOOSE : STATE_IDLE);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("Origin", this.entityData.get(DATA_ORIGIN).asLong());
        tag.putInt("Depth", this.entityData.get(DATA_DEPTH));
        tag.putDouble("T", this.t);
        tag.putByte("State", getStateByte());
    }
}
