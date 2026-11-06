package com.hogwartsmagic.fx;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.pensieve.PensieveDimension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Finds the moment a player first lays eyes on one of the great beasts ({@link BossBeasts}) and tells that player,
 * and only them, so their client can mark it (black bars, a slow push-in, a title card). A beast counts as seen
 * when it is within {@link #RANGE} blocks, in line of sight, and within about 35 degrees of where the player is
 * looking; which kinds a player has seen is remembered in their persistent data, and survives death.
 *
 * <p>Every few ticks, and only while some beast is loaded: the beasts are tracked as they join and leave a level,
 * so a world with none costs one empty-set check.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class BossIntroDetector {
    /** How far a beast can be and still be "seen". */
    public static final double RANGE = 48.0;
    /** Cosine of the half-angle of "roughly looking at it". */
    private static final double LOOK_COS = Math.cos(Math.toRadians(35.0));
    private static final int CHECK_EVERY = 10;
    /** The player's persistent-data key: a compound of beast id to true. */
    private static final String TAG = "hogwartsmagic_boss_seen";

    /** Loaded beasts (server thread only). */
    private static final Set<LivingEntity> BEASTS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static int counter;

    private BossIntroDetector() {
    }

    // ---- what a player has seen ---------------------------------------------------------------------------

    public static boolean hasSeen(ServerPlayer player, int beast) {
        String id = BossBeasts.id(beast);
        return id != null && player.getPersistentData().getCompound(TAG).getBoolean(id);
    }

    private static void markSeen(ServerPlayer player, int beast) {
        CompoundTag root = player.getPersistentData();
        CompoundTag seen = root.getCompound(TAG);
        seen.putBoolean(BossBeasts.id(beast), true);
        root.put(TAG, seen);
    }

    /** Forget every beast this player has seen, so each is a first sighting again (for testing). */
    public static void reset(ServerPlayer player) {
        player.getPersistentData().remove(TAG);
    }

    // ---- tracking and detection ---------------------------------------------------------------------------

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        if (BossBeasts.indexOf(living) >= 0) {
            BEASTS.add(living);
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!BEASTS.isEmpty() && event.getEntity() instanceof LivingEntity living) {
            BEASTS.remove(living);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        BEASTS.clear();
        counter = 0;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BEASTS.isEmpty() || ++counter < CHECK_EVERY) {
            return;
        }
        counter = 0;
        for (LivingEntity beast : BEASTS) {
            if (!beast.isAlive() || !(beast.level() instanceof ServerLevel level)
                    || level.dimension().equals(PensieveDimension.KEY)) {
                continue;
            }
            int index = BossBeasts.indexOf(beast);
            if (index < 0) {
                continue;
            }
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || !player.isAlive() || hasSeen(player, index)
                        || player.distanceToSqr(beast) > RANGE * RANGE) {
                    continue;
                }
                if (looksAt(player, beast) && canSee(player, beast)) {
                    markSeen(player, index);
                    Fx.sendTo(player, FxKinds.BOSS_INTRO, beast.getX(), beast.getY(), beast.getZ(), beast.getId(),
                            (float) index);
                }
            }
        }
    }

    /** Is the beast within the look cone? Judged on the closest of its centre and the point of it at eye height. */
    private static boolean looksAt(ServerPlayer player, Entity beast) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        AABB box = beast.getBoundingBox();
        Vec3 centre = box.getCenter();
        Vec3 level = new Vec3(centre.x, Math.max(box.minY, Math.min(box.maxY, eye.y)), centre.z);
        return dot(look, eye, centre) >= LOOK_COS || dot(look, eye, level) >= LOOK_COS;
    }

    private static double dot(Vec3 look, Vec3 eye, Vec3 target) {
        Vec3 to = target.subtract(eye);
        double length = to.length();
        return length < 1.0E-4 ? 1.0 : look.dot(to) / length;
    }

    /** Nothing solid between the player's eyes and the beast's eyes or middle. */
    private static boolean canSee(ServerPlayer player, LivingEntity beast) {
        if (player.hasLineOfSight(beast)) {
            return true;
        }
        Vec3 eye = player.getEyePosition();
        return player.level().clip(new ClipContext(eye, beast.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    // ---- surviving death ----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.contains(TAG)) {
            event.getEntity().getPersistentData().put(TAG, old.getCompound(TAG).copy());
        }
    }
}
