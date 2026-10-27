package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.HorcruxBlockEntity;
import com.hogwartsmagic.spell.TerrainRules;
import com.hogwartsmagic.spell.summon.FiendfyreChannel;
import com.hogwartsmagic.spell.summon.FiendfyreRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * One serpent of a held Fiendfyre channel (FiendfyreChannel). While controlled it flies where its
 * caster aims, at its place in the formation. A serpent configured to turn glows red for
 * FiendfyreRules.TELEGRAPH_TICKS (synced state byte; the renderer tints it, the server adds red dust),
 * then goes rogue: it hunts the nearest living creature FiendfyreRules.rogueMayTarget allows, its
 * caster included, until the cast's time is up, independent of the channel. A serpent that never had a
 * caster (a command or a gametest) hovers where it is and runs its fate alone; one that is not fated to
 * turn simply goes out. All fire it sets goes through TerrainRules.canPlace.
 */
public class FiendfyreSerpentEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_LIFE_TICKS = SynchedEntityData.defineId(FiendfyreSerpentEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(FiendfyreSerpentEntity.class, EntityDataSerializers.BYTE);
    public static final byte STATE_CONTROLLED = 0;
    public static final byte STATE_TELEGRAPH = 1;
    public static final byte STATE_ROGUE = 2;
    public static final byte STATE_DISPATCHED = 3;
    private static final DustParticleOptions WARNING_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.05F, 0.05F), 2.0F);
    private static final double CONTROL_DISTANCE = 6.2D;
    private static final double ADVANCE_BONUS_DISTANCE = 4.8D;
    private static final double MAX_STEP = 0.72D;
    private static final double ADVANCE_MAX_STEP = 1.08D;
    private static final double TURN_RESPONSE = 0.18D;
    private static final double ADVANCE_TURN_RESPONSE = 0.12D;
    private static final int TRAIL_LENGTH = 18;

    @Nullable
    private UUID casterUuid;
    private int formationIndex;
    private int formationCount = 1;
    /** Life tick at which this serpent turns rogue; -1 if it never will. */
    private int rogueTurnTick = -1;
    /** Life tick at which a rogue serpent burns out. */
    private int rogueEndTick = -1;
    @Nullable
    private UUID rogueTargetId;
    @Nullable
    private UUID dispatchTargetId;
    private long dispatchUntil;
    private boolean destroysHorcrux;
    private final Deque<Vec3> trailPoints = new ArrayDeque<>();

    public FiendfyreSerpentEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_LIFE_TICKS, 0);
        builder.define(DATA_STATE, STATE_CONTROLLED);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setLifeTicks(tag.getInt("LifeTicks"));
        this.casterUuid = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
        this.formationIndex = tag.getInt("Index");
        this.formationCount = Math.max(1, tag.getInt("Count"));
        this.rogueTurnTick = tag.contains("RogueTurn") ? tag.getInt("RogueTurn") : -1;
        this.rogueEndTick = tag.contains("RogueEnd") ? tag.getInt("RogueEnd") : -1;
        this.setState(tag.getByte("State"));
        this.destroysHorcrux = tag.getBoolean("DestroysHorcrux");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("LifeTicks", getLifeTicks());
        if (this.casterUuid != null) {
            tag.putUUID("Caster", this.casterUuid);
        }
        tag.putInt("Index", this.formationIndex);
        tag.putInt("Count", this.formationCount);
        tag.putInt("RogueTurn", this.rogueTurnTick);
        tag.putInt("RogueEnd", this.rogueEndTick);
        tag.putByte("State", this.getState());
        tag.putBoolean("DestroysHorcrux", this.destroysHorcrux);
    }

    public int getLifeTicks() {
        return this.entityData.get(DATA_LIFE_TICKS);
    }

    public void setLifeTicks(int ticks) {
        this.entityData.set(DATA_LIFE_TICKS, ticks);
    }

    public void setCaster(ServerPlayer caster) {
        this.casterUuid = caster.getUUID();
    }

    /**
     * Place {@code index} of {@code count} in the formation; {@code rogueTurnTick} is the life tick it
     * turns at (-1: never) and {@code durationTicks} the cast's length. Call before adding it.
     */
    public void configure(int index, int count, int rogueTurnTick, int durationTicks) {
        this.formationIndex = Math.max(0, index);
        this.formationCount = Math.max(1, count);
        this.rogueTurnTick = rogueTurnTick;
        this.rogueEndTick = rogueTurnTick >= 0 ? FiendfyreRules.rogueLifeEnd(durationTicks, rogueTurnTick) : -1;
    }

    public byte getState() {
        return this.entityData.get(DATA_STATE);
    }

    private void setState(byte state) {
        this.entityData.set(DATA_STATE, state);
    }

    public boolean isTelegraphing() {
        return this.getState() == STATE_TELEGRAPH;
    }

    public boolean isRogue() {
        return this.getState() == STATE_ROGUE;
    }

    @Override
    public void tick() {
        super.tick();

        int ticks = getLifeTicks() + 1;
        setLifeTicks(ticks);
        this.noPhysics = true;
        this.setNoGravity(true);

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (this.isRogue()) {
            tickRogue(serverLevel, ticks);
            return;
        }

        ServerPlayer caster = getCaster(serverLevel);
        boolean casterless = this.casterUuid == null && this.rogueTurnTick >= 0;
        if (!casterless && (caster == null || !caster.isAlive() || caster.level() != serverLevel
                || !FiendfyreChannel.isChanneling(caster))) {
            this.discard();
            return;
        }
        if (this.rogueTurnTick >= 0) {
            if (ticks >= this.rogueTurnTick) {
                turnRogue(serverLevel, caster);
                tickRogue(serverLevel, ticks);
                return;
            }
            if (!this.isTelegraphing() && FiendfyreRules.telegraphing(ticks, this.rogueTurnTick)) {
                beginTelegraph(serverLevel, caster);
            }
        }

        if (caster != null) {
            if (this.getState() != STATE_DISPATCHED || !tickDispatch(serverLevel, caster)) {
                steerToCasterAim(caster, ticks);
            }
        } else {
            moveToward(this.position().add(0.0D, Math.sin(ticks * 0.3D) * 0.3D, 0.0D), MAX_STEP, TURN_RESPONSE);
        }
        recordTrail();
        scorchNearby(caster, null);
        if (this.destroysHorcrux && ticks % FiendfyreRules.HORCRUX_SCAN_INTERVAL == 0) {
            devourHorcruxes(serverLevel, caster);
        }

        if (ticks % 2 == 0) {
            spreadFire(serverLevel, caster);
        }
        spawnTrailParticles(serverLevel, ticks);
        spawnBurstParticles(serverLevel, ticks);
        if (this.isTelegraphing() && ticks % 2 == 0) {
            serverLevel.sendParticles(WARNING_DUST, this.getX(), this.getY(), this.getZ(), 6, 0.5D, 0.5D, 0.5D, 0.0D);
        }
    }

    public void setDestroysHorcrux(boolean destroys) {
        this.destroysHorcrux = destroys;
    }

    public boolean isAvailableForDispatch() {
        return this.getState() == STATE_CONTROLLED;
    }

    /** Master: home in on {@code target} for DISPATCH_TICKS, then rejoin the formation. */
    public void dispatchAt(LivingEntity target, long now) {
        this.dispatchTargetId = target.getUUID();
        this.dispatchUntil = now + FiendfyreRules.DISPATCH_TICKS;
        this.setState(STATE_DISPATCHED);
    }

    /** Returns false (and rejoins the formation) once the target is gone, out of range or time is up. */
    private boolean tickDispatch(ServerLevel level, ServerPlayer caster) {
        LivingEntity target = null;
        if (this.dispatchTargetId != null && level.getEntity(this.dispatchTargetId) instanceof LivingEntity living && living.isAlive()) {
            target = living;
        }
        if (target == null || level.getGameTime() > this.dispatchUntil
                || target.distanceToSqr(caster) > FiendfyreRules.DISPATCH_RANGE * FiendfyreRules.DISPATCH_RANGE) {
            this.dispatchTargetId = null;
            this.setState(STATE_CONTROLLED);
            return false;
        }
        moveToward(target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D), ADVANCE_MAX_STEP, ADVANCE_TURN_RESPONSE);
        return true;
    }

    /**
     * Master: an active or primed Horcrux within HORCRUX_REACH is destroyed (canon: Fiendfyre is one of the
     * few things that can). TerrainRules.canBreak is the last check; it posts the real BreakEvent, which is
     * what runs the Horcrux's own break effects, and a true result means the block is destroyed now.
     */
    private void devourHorcruxes(ServerLevel level, @Nullable Entity caster) {
        BlockPos center = this.blockPosition();
        int reach = FiendfyreRules.HORCRUX_REACH;
        for (BlockPos scan : BlockPos.betweenClosed(center.offset(-reach, -reach, -reach), center.offset(reach, reach, reach))) {
            if (!level.isLoaded(scan)) {
                continue;
            }
            BlockState state = level.getBlockState(scan);
            if (!state.is(ExampleMod.HORCRUX.get())) {
                continue;
            }
            if (!(level.getBlockEntity(scan) instanceof HorcruxBlockEntity horcrux) || !(horcrux.isActive() || horcrux.isPrimed())) {
                continue;
            }
            BlockPos pos = scan.immutable();
            if (!TerrainRules.canBreak(level, pos, state, caster)) {
                continue;
            }
            level.destroyBlock(pos, false);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5D, pos.getY() + 0.6D, pos.getZ() + 0.5D,
                    30, 0.4D, 0.4D, 0.4D, 0.04D);
            if (caster instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.fiendfyre.horcrux")
                        .withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    @Nullable
    private ServerPlayer getCaster(ServerLevel level) {
        return this.casterUuid == null ? null : level.getServer().getPlayerList().getPlayer(this.casterUuid);
    }

    /** The mandatory one-second warning (roadmap Decision 11). */
    private void beginTelegraph(ServerLevel level, @Nullable ServerPlayer caster) {
        this.setState(STATE_TELEGRAPH);
        if (caster != null) {
            caster.displayClientMessage(Component.translatable("message.hogwartsmagic.fiendfyre.writhes")
                    .withStyle(ChatFormatting.RED), true);
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLAZE_AMBIENT, SoundSource.PLAYERS, 1.2f, 0.5f);
    }

    private void turnRogue(ServerLevel level, @Nullable ServerPlayer caster) {
        this.setState(STATE_ROGUE);
        this.rogueTargetId = null;
        if (caster != null) {
            caster.displayClientMessage(Component.translatable("message.hogwartsmagic.fiendfyre.rogue")
                    .withStyle(ChatFormatting.DARK_RED), true);
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5f, 0.6f);
    }

    /** Rogue: hunt the nearest creature it may (caster included) until the cast's time is up. */
    private void tickRogue(ServerLevel level, int ticks) {
        if (this.rogueEndTick >= 0 && ticks >= this.rogueEndTick) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(), this.getZ(), 20, 0.6D, 0.6D, 0.6D, 0.02D);
            this.discard();
            return;
        }
        ServerPlayer caster = getCaster(level);
        LivingEntity target = null;
        if (this.rogueTargetId != null && level.getEntity(this.rogueTargetId) instanceof LivingEntity living
                && living.isAlive() && mayHunt(level, living, caster)) {
            target = living;
        }
        if (target == null || ticks % FiendfyreRules.ROGUE_RETARGET_INTERVAL == 0) {
            target = nearestVictim(level, caster);
            this.rogueTargetId = target == null ? null : target.getUUID();
        }
        Vec3 desired = target != null
                ? target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
                : this.position().add(0.0D, Math.sin(ticks * 0.3D) * 0.3D, 0.0D);
        moveToward(desired, MAX_STEP, TURN_RESPONSE);
        recordTrail();
        scorchNearby(null, victim -> mayHunt(level, victim, caster)); // PvP rules protect bystanders
        if (ticks % 2 == 0) {
            spreadFire(level, caster);
            level.sendParticles(WARNING_DUST, this.getX(), this.getY(), this.getZ(), 6, 0.5D, 0.5D, 0.5D, 0.0D);
        }
        spawnTrailParticles(level, ticks);
        spawnBurstParticles(level, ticks);
    }

    @Nullable
    private LivingEntity nearestVictim(ServerLevel level, @Nullable ServerPlayer caster) {
        double radius = FiendfyreRules.ROGUE_SEARCH_RADIUS;
        LivingEntity best = null;
        double bestDistance = radius * radius;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(radius),
                c -> c.isAlive() && mayHunt(level, c, caster))) {
            double distance = candidate.distanceToSqr(this);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /** FiendfyreRules.rogueMayTarget for one candidate; an offline caster can harm nobody. */
    private boolean mayHunt(ServerLevel level, LivingEntity candidate, @Nullable ServerPlayer caster) {
        boolean isCaster = this.casterUuid != null && this.casterUuid.equals(candidate.getUUID());
        boolean isPlayer = candidate instanceof Player;
        boolean creativeOrSpectator = candidate instanceof Player player && (player.isCreative() || player.isSpectator());
        boolean pvpAllowed = level.getServer().isPvpAllowed();
        boolean casterCanHarm = isPlayer && caster != null && caster.canHarmPlayer((Player) candidate);
        boolean isMobOrPlayer = candidate instanceof Mob || isPlayer;
        return FiendfyreRules.rogueMayTarget(isCaster, isPlayer, creativeOrSpectator, pvpAllowed, casterCanHarm, isMobOrPlayer);
    }

    private void steerToCasterAim(ServerPlayer caster, int ticks) {
        boolean advancing = caster.getPersistentData().getBoolean(FiendfyreChannel.TAG_ADVANCE);
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        if (look.lengthSqr() < 1.0E-6D) {
            look = new Vec3(0.0D, 0.0D, 1.0D);
        }

        Vec3 direction = look.normalize();
        double distanceScale = CONTROL_DISTANCE + (advancing ? ADVANCE_BONUS_DISTANCE : 0.0D);
        Vec3 desired = eye.add(direction.scale(distanceScale));
        double lateral = FiendfyreRules.lateralOffset(this.formationIndex, this.formationCount);
        if (lateral != 0.0D) {
            Vec3 right = new Vec3(-direction.z, 0.0D, direction.x);
            if (right.lengthSqr() > 1.0E-6D) {
                desired = desired.add(right.normalize().scale(lateral));
            }
        }
        desired = desired.add(0.0D, Math.sin(ticks * 0.3D + this.formationIndex) * (advancing ? 0.25D : 0.45D), 0.0D);
        moveToward(desired, advancing ? ADVANCE_MAX_STEP : MAX_STEP, advancing ? ADVANCE_TURN_RESPONSE : TURN_RESPONSE);
    }

    private void moveToward(Vec3 desired, double maxStep, double turnResponse) {
        Vec3 current = this.position();
        Vec3 desiredMovement = desired.subtract(current);
        if (desiredMovement.length() > maxStep) {
            desiredMovement = desiredMovement.normalize().scale(maxStep);
        }
        Vec3 movement = this.getDeltaMovement().scale(1.0D - turnResponse).add(desiredMovement.scale(turnResponse));
        if (movement.lengthSqr() > (maxStep * maxStep)) {
            movement = movement.normalize().scale(maxStep);
        }
        Vec3 next = current.add(movement);
        this.setPos(next.x, next.y, next.z);
        this.setDeltaMovement(movement);
        if (movement.horizontalDistanceSqr() > 1.0E-4D) {
            this.setYRot((float) (Mth.atan2(movement.z, movement.x) * (180F / Math.PI)) - 90.0F);
        }
    }

    private void recordTrail() {
        this.trailPoints.addFirst(this.position());
        while (this.trailPoints.size() > TRAIL_LENGTH) {
            this.trailPoints.removeLast();
        }
    }

    /**
     * Burns everything close; {@code spared} (the caster of a controlled serpent) is left alone, and
     * when {@code only} is given just the creatures it accepts are burnt.
     */
    private void scorchNearby(@Nullable Entity spared, @Nullable Predicate<LivingEntity> only) {
        AABB area = this.getBoundingBox().inflate(2.6D, 1.4D, 2.6D);
        for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity.isAlive() && entity != spared && (only == null || only.test(entity)))) {
            target.igniteForSeconds(5);
            target.hurt(target.damageSources().onFire(), 4.0f);
            Vec3 lift = target.position().subtract(this.position());
            if (lift.lengthSqr() > 1.0E-4D) {
                Vec3 push = lift.normalize().scale(0.32D);
                target.push(push.x, 0.12D, push.z);
            }
        }
    }

    private void spawnTrailParticles(ServerLevel serverLevel, int ticks) {
        Vec3 previous = null;
        int index = 0;
        for (Vec3 point : this.trailPoints) {
            serverLevel.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 3, 0.18D, 0.18D, 0.18D, 0.01D);
            if (index % 2 == 0) {
                serverLevel.sendParticles(ParticleTypes.LAVA, point.x, point.y, point.z, 1, 0.08D, 0.08D, 0.08D, 0.0D);
            }
            if (previous != null) {
                Vec3 segment = previous.subtract(point);
                int steps = Math.max(2, (int) Math.ceil(segment.length() * 5.0D));
                for (int i = 1; i < steps; i++) {
                    Vec3 mid = point.add(segment.scale(i / (double) steps));
                    serverLevel.sendParticles(ParticleTypes.SMALL_FLAME, mid.x, mid.y, mid.z, 1, 0.03D, 0.03D, 0.03D, 0.0D);
                }
            }
            previous = point;
            index++;
        }

        if (ticks % 3 == 0) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(), this.getZ(), 2, 0.2D, 0.25D, 0.2D, 0.01D);
        }
    }

    private void spawnBurstParticles(ServerLevel serverLevel, int ticks) {
        if (ticks % 4 != 0) {
            return;
        }
        for (int i = 0; i < 12; i++) {
            double angle = (Math.PI * 2.0D * i) / 12.0D + ticks * 0.08D;
            double radius = 1.4D + Math.sin(ticks * 0.22D + i) * 0.25D;
            double x = this.getX() + Math.cos(angle) * radius;
            double y = this.getY() + 0.2D + (i % 3) * 0.18D;
            double z = this.getZ() + Math.sin(angle) * radius;
            serverLevel.sendParticles(ParticleTypes.FLAME, x, y, z, 1, 0.04D, 0.04D, 0.04D, 0.01D);
            if (i % 2 == 0) {
                serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 1, 0.01D, 0.05D, 0.01D, 0.0D);
            }
        }
    }

    /** Terrain-safe fire: loaded, empty positions only, and TerrainRules.canPlace as the caster (null if offline). */
    private void spreadFire(ServerLevel level, @Nullable Entity caster) {
        BlockPos center = this.blockPosition();
        int radius = 2 + Math.min(5, getLifeTicks() / 20);

        for (int i = 0; i < 8; i++) {
            int rx = this.random.nextInt(radius * 2 + 1) - radius;
            int ry = this.random.nextInt(5) - 2;
            int rz = this.random.nextInt(radius * 2 + 1) - radius;
            BlockPos target = center.offset(rx, ry, rz);
            if (!level.isLoaded(target) || !level.isEmptyBlock(target)) {
                continue;
            }
            if (TerrainRules.canPlace(level, target, caster)) {
                level.setBlockAndUpdate(target, Blocks.FIRE.defaultBlockState());
            }
        }
    }
}
