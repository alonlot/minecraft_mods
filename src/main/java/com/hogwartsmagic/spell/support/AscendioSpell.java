package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.damage.DamageSupport;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ascendio cast on yourself (aim down, or a crouching keybind cast): launches you to the rank height,
 * keeping your forward momentum, with no fall damage for 5 seconds. Expert: one midair second cast per
 * jump at half the height, which ignores the cooldown. Master: cast in water, you surge to the surface;
 * on take-off, enemies within 2 blocks are shoved away. A cast at a target still tosses it up
 * (SpellType, unchanged). State lives in the player's persistent data. Server only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AscendioSpell {
    public static final String TAG_SAFE_UNTIL = ExampleMod.MODID + ":ascendio_safe_until";
    public static final String TAG_AIR_CHARGE = ExampleMod.MODID + ":ascendio_air_charge";
    public static final String TAG_LAUNCHED_AT = ExampleMod.MODID + ":ascendio_launched_at";
    public static final String TAG_SURGE_UNTIL = ExampleMod.MODID + ":ascendio_surge_until";

    private AscendioSpell() {
    }

    public static boolean wantsSelfCast(ServerPlayer player) {
        return AscendioRules.wantsSelfCast(player.getXRot(), player.isShiftKeyDown());
    }

    /** Expert and up, airborne, with this jump's charge unspent, and casting on yourself. */
    public static boolean canDoubleCast(ServerPlayer player) {
        return !player.onGround()
                && player.getPersistentData().getBoolean(TAG_AIR_CHARGE)
                && AscendioRules.doubleCasts(SpellMasteryData.getRank(player, SpellType.ASCENDIO))
                && wantsSelfCast(player);
    }

    /** @return true if this was the midair double cast (the caller then leaves the cooldown alone). */
    public static boolean launchSelf(ServerPlayer player, long now) {
        int rank = SpellMasteryData.getRank(player, SpellType.ASCENDIO);
        CompoundTag data = player.getPersistentData();
        ServerLevel level = player.serverLevel();
        if (AscendioRules.surges(rank) && player.isInWater()) {
            // Triwizard Task 2: straight up and out. The tick hook keeps pushing until the surface.
            data.putLong(TAG_SURGE_UNTIL, now + AscendioRules.SURGE_MAX_TICKS);
            data.putLong(TAG_SAFE_UNTIL, now + AscendioRules.SURGE_MAX_TICKS + AscendioRules.SAFE_FALL_TICKS);
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, AscendioRules.SURGE_SPEED, motion.z);
            player.hurtMarked = true;
            level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, player.getX(), player.getY(), player.getZ(), 30, 0.3, 0.5, 0.3, 0.1);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.PLAYERS, 1.0f, 1.0f);
            return false;
        }
        boolean doubleCast = !player.onGround() && data.getBoolean(TAG_AIR_CHARGE) && AscendioRules.doubleCasts(rank);
        double blocks = AscendioRules.heightBlocks(rank) * (doubleCast ? AscendioRules.DOUBLE_CAST_HEIGHT_FACTOR : 1.0);
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, AscendioRules.launchVelocity(blocks), motion.z);
        player.hurtMarked = true;
        player.fallDistance = 0.0f;
        data.putLong(TAG_SAFE_UNTIL, now + AscendioRules.SAFE_FALL_TICKS);
        data.putLong(TAG_LAUNCHED_AT, now);
        if (doubleCast) {
            data.remove(TAG_AIR_CHARGE);
        } else if (AscendioRules.doubleCasts(rank)) {
            data.putBoolean(TAG_AIR_CHARGE, true);
        }
        if (!doubleCast && AscendioRules.pushesOnTakeoff(rank)) {
            shoveAway(level, player);
        }
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 16, 0.4, 0.1, 0.4, 0.05);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.8f, 1.2f);
        return doubleCast;
    }

    private static void shoveAway(ServerLevel level, ServerPlayer player) {
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(AscendioRules.PUSH_RADIUS),
                e -> DamageSupport.isSplashTarget(player, e, null))) {
            Vec3 away = new Vec3(other.getX() - player.getX(), 0.0, other.getZ() - player.getZ());
            away = away.lengthSqr() < 1.0e-6 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
            other.push(away.x * AscendioRules.PUSH_STRENGTH, AscendioRules.PUSH_LIFT, away.z * AscendioRules.PUSH_STRENGTH);
            other.hurtMarked = true;
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (!data.contains(TAG_SURGE_UNTIL) && !data.contains(TAG_AIR_CHARGE)) {
            return; // nothing pending: the common case costs two key lookups
        }
        long now = player.level().getGameTime();
        if (data.contains(TAG_SURGE_UNTIL)) {
            if (now < data.getLong(TAG_SURGE_UNTIL) && player.isInWater()) {
                Vec3 motion = player.getDeltaMovement();
                if (motion.y < AscendioRules.SURGE_SPEED) {
                    player.setDeltaMovement(motion.x, AscendioRules.SURGE_SPEED, motion.z);
                    player.hurtMarked = true;
                }
            } else {
                data.remove(TAG_SURGE_UNTIL);
            }
        }
        if (data.getBoolean(TAG_AIR_CHARGE) && player.onGround()
                && now - data.getLong(TAG_LAUNCHED_AT) > AscendioRules.AIR_CHARGE_GRACE_TICKS) {
            data.remove(TAG_AIR_CHARGE); // landed: the next jump earns a fresh charge
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.level().getGameTime() < player.getPersistentData().getLong(TAG_SAFE_UNTIL)) {
            event.setCanceled(true);
        }
    }
}
