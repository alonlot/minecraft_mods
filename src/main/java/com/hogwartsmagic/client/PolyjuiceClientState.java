package com.hogwartsmagic.client;

import com.hogwartsmagic.polyjuice.PolyjuiceData;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PolyjuiceClientState {

    private static final Map<UUID, CompoundTag> ACTIVE_DISGUISES = new ConcurrentHashMap<>();
    private static final Map<UUID, Entity> SURROGATES = new ConcurrentHashMap<>();

    /**
     * Mob disguises: scale only {@link LivingEntity#walkAnimation} <em>speed</em> (not position).
     * Scaling {@code position} causes phase jumps / flicker; position must stay 1:1 with the player.
     */
    private static final float MOB_DISGUISE_LIMB_SCALE = 0.28F;

    private PolyjuiceClientState() {
    }

    public static void update(UUID playerId, CompoundTag disguise) {
        if (disguise == null || disguise.isEmpty()) {
            ACTIVE_DISGUISES.remove(playerId);
            SURROGATES.remove(playerId);
            return;
        }
        CompoundTag incoming = disguise.copy();
        CompoundTag previous = ACTIVE_DISGUISES.get(playerId);
        if (incoming.equals(previous)) {
            return;
        }
        ACTIVE_DISGUISES.put(playerId, incoming);
        SURROGATES.remove(playerId);
    }

    public static CompoundTag get(UUID playerId) {
        CompoundTag tag = ACTIVE_DISGUISES.get(playerId);
        return tag == null ? new CompoundTag() : tag.copy();
    }

    public static boolean hasDisguise(UUID playerId) {
        CompoundTag tag = ACTIVE_DISGUISES.get(playerId);
        return tag != null && !tag.isEmpty();
    }

    /** Client game time until which Revelio (Master) shows this player undisguised. */
    private static final Map<UUID, Long> REVEALED_UNTIL = new ConcurrentHashMap<>();

    public static void reveal(UUID playerId, long untilGameTime) {
        REVEALED_UNTIL.merge(playerId, untilGameTime, Math::max);
    }

    public static boolean isRevealed(UUID playerId, long gameTime) {
        Long until = REVEALED_UNTIL.get(playerId);
        if (until == null) {
            return false;
        }
        if (gameTime >= until) {
            REVEALED_UNTIL.remove(playerId);
            return false;
        }
        return true;
    }

    public static Entity getOrCreateSurrogate(Player realPlayer, float partialTick) {
        CompoundTag disguise = ACTIVE_DISGUISES.get(realPlayer.getUUID());
        if (disguise == null || disguise.isEmpty()) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return null;
        }

        Entity surrogate = SURROGATES.get(realPlayer.getUUID());
        if (surrogate == null || !matches(surrogate, disguise)) {
            surrogate = createSurrogate(level, disguise);
            if (surrogate == null) {
                return null;
            }
            SURROGATES.put(realPlayer.getUUID(), surrogate);
        }

        copyState(realPlayer, surrogate, partialTick);
        return surrogate;
    }

    /**
     * Run once per client game tick (not each render frame). Lets {@link LivingEntity#tick()} advance
     * internal animation state so limbs interpolate cleanly; then snaps the surrogate back to the
     * tracked player so there is no slippery position drift.
     */
    public static void advanceSurrogateOneGameTick(Player trackedPlayer) {
        if (!hasDisguise(trackedPlayer.getUUID())) {
            return;
        }
        Entity surrogate = SURROGATES.get(trackedPlayer.getUUID());
        if (!(surrogate instanceof LivingEntity le)) {
            return;
        }

        le.setPos(trackedPlayer.getX(), trackedPlayer.getY(), trackedPlayer.getZ());
        le.setYRot(trackedPlayer.getYRot());
        le.setXRot(trackedPlayer.getXRot());
        le.yRotO = trackedPlayer.yRotO;
        le.xRotO = trackedPlayer.xRotO;
        le.xOld = trackedPlayer.xOld;
        le.yOld = trackedPlayer.yOld;
        le.zOld = trackedPlayer.zOld;
        le.setOnGround(trackedPlayer.onGround());
        le.setDeltaMovement(Vec3.ZERO);
        le.yBodyRot = trackedPlayer.yBodyRot;
        le.yBodyRotO = trackedPlayer.yBodyRotO;
        le.yHeadRot = trackedPlayer.getYHeadRot();
        le.yHeadRotO = trackedPlayer.yHeadRotO;
        le.swinging = trackedPlayer.swinging;
        le.swingTime = trackedPlayer.swingTime;
        le.hurtTime = trackedPlayer.hurtTime;
        le.deathTime = trackedPlayer.deathTime;
        le.setPose(resolvePose(trackedPlayer));
        le.setShiftKeyDown(trackedPlayer.isShiftKeyDown());
        le.setSprinting(trackedPlayer.isSprinting());
        le.setSwimming(trackedPlayer.isSwimming());

        float limb = limbSlowdownFor(surrogate);
        le.walkAnimation.setSpeed(trackedPlayer.walkAnimation.speed() * limb);
        le.walkAnimation.position(trackedPlayer.walkAnimation.position());

        int playerTicks = trackedPlayer.tickCount;
        le.tickCount = Math.max(0, playerTicks - 1);
        le.tick();
        le.tickCount = playerTicks;

        le.setPos(trackedPlayer.getX(), trackedPlayer.getY(), trackedPlayer.getZ());
        le.setYRot(trackedPlayer.getYRot());
        le.setXRot(trackedPlayer.getXRot());
        le.setDeltaMovement(Vec3.ZERO);
    }

    private static boolean matches(Entity surrogate, CompoundTag disguise) {
        if (PolyjuiceData.isPlayerSample(disguise)) {
            return surrogate instanceof RemotePlayer remotePlayer
                    && remotePlayer.getUUID().equals(PolyjuiceData.getPlayerUuid(disguise));
        }
        EntityType<?> type = PolyjuiceData.getEntityType(disguise).orElse(null);
        return type != null && surrogate.getType() == type;
    }

    private static Entity createSurrogate(ClientLevel level, CompoundTag disguise) {
        Entity entity;
        if (PolyjuiceData.isPlayerSample(disguise)) {
            UUID targetId = PolyjuiceData.getPlayerUuid(disguise);
            String name = disguise.getString(PolyjuiceData.TARGET_NAME);
            RemotePlayer remote = new RemotePlayer(level, new GameProfile(targetId, name.isBlank() ? "Polyjuice" : name));
            remote.setId(-1);
            entity = remote;
        } else {
            EntityType<?> type = PolyjuiceData.getEntityType(disguise).orElse(null);
            entity = type == null ? null : type.create(level);
            if (entity != null) {
                entity.setId(-1);
            }
        }
        if (entity instanceof LivingEntity living) {
            living.refreshDimensions();
        }
        return entity;
    }

    private static void copyState(Player realPlayer, Entity surrogate, float partialTick) {
        surrogate.setOldPosAndRot();

        double x = Mth.lerp((double) partialTick, realPlayer.xOld, realPlayer.getX());
        double y = Mth.lerp((double) partialTick, realPlayer.yOld, realPlayer.getY());
        double z = Mth.lerp((double) partialTick, realPlayer.zOld, realPlayer.getZ());
        surrogate.setPos(x, y, z);

        float yRot = Mth.rotLerp(partialTick, realPlayer.yRotO, realPlayer.getYRot());
        float xRot = Mth.lerp(partialTick, realPlayer.xRotO, realPlayer.getXRot());
        surrogate.setYRot(yRot);
        surrogate.setXRot(xRot);

        surrogate.tickCount = realPlayer.tickCount;
        surrogate.setOnGround(realPlayer.onGround());
        surrogate.setDeltaMovement(Vec3.ZERO);

        if (surrogate instanceof LivingEntity living) {
            living.yBodyRot = Mth.rotLerp(partialTick, realPlayer.yBodyRotO, realPlayer.yBodyRot);
            living.yBodyRotO = realPlayer.yBodyRotO;
            living.yHeadRot = Mth.rotLerp(partialTick, realPlayer.yHeadRotO, realPlayer.getYHeadRot());
            living.yHeadRotO = realPlayer.yHeadRotO;
            living.swinging = realPlayer.swinging;
            living.swingTime = realPlayer.swingTime;
            living.hurtTime = realPlayer.hurtTime;
            living.deathTime = realPlayer.deathTime;
            float limb = limbSlowdownFor(surrogate);
            living.walkAnimation.setSpeed(realPlayer.walkAnimation.speed() * limb);
            living.walkAnimation.position(realPlayer.walkAnimation.position(partialTick));
            living.setPose(resolvePose(realPlayer));
            living.setShiftKeyDown(realPlayer.isShiftKeyDown());
            living.setSprinting(realPlayer.isSprinting());
            living.setSwimming(realPlayer.isSwimming());
            living.setInvisible(false);
        }
    }

    private static float limbSlowdownFor(Entity surrogate) {
        return surrogate instanceof RemotePlayer ? 1.0F : MOB_DISGUISE_LIMB_SCALE;
    }

    private static Pose resolvePose(Player player) {
        if (player.isFallFlying()) {
            return Pose.FALL_FLYING;
        }
        if (player.isSwimming()) {
            return Pose.SWIMMING;
        }
        if (player.isShiftKeyDown()) {
            return Pose.CROUCHING;
        }
        return Pose.STANDING;
    }
}
