package com.hogwartsmagic.spell.disable;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.IncarcerousChainAnimation;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Incarcerous: chains from the ground tether the target to where it was bound. It can still attack and
 * cast, but can't get more than the leash (2 blocks, measured flat) from that spot. Adept and Expert
 * also catch nearby enemies; Expert pulls tethered flyers down and keeps them from taking off; Master
 * drags every bound target to the main one on a 1-block leash and makes them share knockback.
 *
 * <p>The bind itself is the hidden incarcerous_chains effect (hard crowd control, applied through
 * DisableSupport.applyHiddenCrowdControl). The leash lives in the entity's persistent NBT under
 * {@link #TAG} and is enforced every server tick while the effect lasts; it is dropped as soon as the
 * effect is gone. Players are corrected with velocity packets and, if far out, a teleport.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IncarcerousTether {
    public static final String TAG = "hogwartsmagic:tether";
    private static boolean sharingKnockback;

    private IncarcerousTether() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.INCARCEROUS);
        boolean pull = IncarcerousRules.pullsFlyersDown(rank);
        boolean bind = IncarcerousRules.bindsTogether(rank);
        double leash = IncarcerousRules.leash(rank);

        int mainTicks = DisableSupport.applyHiddenCrowdControl(target, ExampleMod.INCARCEROUS_CHAINS,
                IncarcerousRules.mainTicks(rank), 0, caster);
        if (mainTicks > 0) {
            tether(level, target, leash, pull, bind ? target.getUUID() : null, null);
            IncarcerousChainAnimation.schedule(level, target);
        }

        double radius = IncarcerousRules.secondaryRadius(rank);
        if (radius <= 0.0) {
            return;
        }
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(radius),
                e -> DisableSupport.isEnemyOf(caster, e, target));
        for (LivingEntity other : nearby) {
            int ticks = DisableSupport.applyHiddenCrowdControl(other, ExampleMod.INCARCEROUS_CHAINS,
                    IncarcerousRules.secondaryTicks(rank), 0, caster);
            if (ticks <= 0) {
                continue;
            }
            if (bind && mainTicks > 0) {
                double boundLeash = IncarcerousRules.boundLeash(rank, target.getBbWidth(), other.getBbWidth());
                tether(level, other, boundLeash, pull, target.getUUID(), target.getUUID());
            } else {
                tether(level, other, leash, pull, null, null);
            }
            IncarcerousChainAnimation.schedule(level, other);
        }
    }

    private static void tether(ServerLevel level, LivingEntity target, double leash, boolean pull,
                               @Nullable UUID group, @Nullable UUID anchorEntity) {
        if (target.isPassenger()) {
            target.stopRiding(); // a mount or broom would carry the bound target away
        }
        CompoundTag tag = new CompoundTag();
        tag.putDouble("X", target.getX());
        tag.putDouble("Y", pull ? groundY(level, target) : target.getY());
        tag.putDouble("Z", target.getZ());
        tag.putString("Dim", level.dimension().location().toString());
        tag.putDouble("LastY", target.getY());
        tag.putDouble("Leash", leash);
        tag.putBoolean("Pull", pull);
        if (group != null) {
            tag.putUUID("Group", group);
        }
        if (anchorEntity != null) {
            tag.putUUID("Anchor", anchorEntity);
        }
        target.getPersistentData().put(TAG, tag);
    }

    /** Top of the first solid block under the target (up to 32 down), so a flyer is anchored to the ground. */
    private static double groundY(ServerLevel level, LivingEntity target) {
        BlockPos.MutableBlockPos cursor = target.blockPosition().mutable();
        for (int i = 0; i < 32 && cursor.getY() > level.getMinBuildHeight(); i++) {
            BlockPos below = cursor.below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return below.getY() + 1.0;
            }
            cursor.move(Direction.DOWN);
        }
        return target.getY();
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag data = target.getPersistentData();
        if (!data.contains(TAG)) {
            return;
        }
        Holder<MobEffect> chains = DisableSupport.holder(ExampleMod.INCARCEROUS_CHAINS);
        if (!target.isAlive() || !target.hasEffect(chains)) {
            data.remove(TAG);
            return;
        }
        CompoundTag tag = data.getCompound(TAG);
        if (!level.dimension().location().toString().equals(tag.getString("Dim"))) {
            // The anchor belongs to another dimension; the leash would be meaningless here.
            data.remove(TAG);
            target.removeEffect(chains);
            return;
        }
        double ax = tag.getDouble("X");
        double ay = tag.getDouble("Y");
        double az = tag.getDouble("Z");
        double leash = tag.getDouble("Leash");
        boolean pull = tag.getBoolean("Pull");
        if (tag.hasUUID("Anchor")) {
            Entity anchor = level.getEntity(tag.getUUID("Anchor"));
            if (anchor instanceof LivingEntity bound && bound.isAlive() && bound.hasEffect(chains)) {
                ax = bound.getX();
                ay = bound.getY();
                az = bound.getZ();
                if (target.tickCount % 2 == 0) {
                    IncarcerousChainAnimation.renderLink(level, target, bound);
                }
            }
        }

        if (target.isPassenger()) {
            target.stopRiding();
        }
        Vec3 v = target.getDeltaMovement();
        boolean changed = false;
        boolean isPlayer = target instanceof ServerPlayer;
        // A ServerPlayer's yo is reset to getY() before this event, so the client-reported vertical
        // movement is tracked here instead: the Y seen on the previous tether tick.
        double playerVy = 0.0;
        if (isPlayer && tag.contains("LastY")) {
            playerVy = Math.max(-3.92, Math.min(3.92, target.getY() - tag.getDouble("LastY")));
        }
        double dx = target.getX() - ax;
        double dz = target.getZ() - az;
        if (TetherMath.shouldSnap(dx, 0.0, dz, leash)) {
            double[] p = TetherMath.snapPoint(ax, target.getY(), az, dx, 0.0, dz, leash);
            if (safeSnap(level, target, p[0], p[1], p[2])) {
                v = new Vec3(0.0, isPlayer ? playerVy : v.y, 0.0);
                changed = true;
            } else if (!isPlayer) {
                double[] c = TetherMath.correctedVelocity(dx, 0.0, dz, leash, v.x, v.y, v.z);
                v = new Vec3(c[0], c[1], c[2]);
                changed = true;
            } else {
                double[] c = TetherMath.correctedVelocity(dx, 0.0, dz, leash, 0.0, 0.0, 0.0);
                v = new Vec3(c[0], playerVy, c[2]);
                changed = true;
            }
        } else if (TetherMath.isOutside(dx, 0.0, dz, leash)) {
            // A player's own motion is client-authoritative, so its server-side velocity is stale:
            // derive the inward push from the position offset alone. Mobs keep their real velocity.
            double[] c = TetherMath.correctedVelocity(dx, 0.0, dz, leash,
                    isPlayer ? 0.0 : v.x, isPlayer ? 0.0 : v.y, isPlayer ? 0.0 : v.z);
            v = new Vec3(c[0], isPlayer ? playerVy : c[1], c[2]);
            changed = true;
        }

        if (pull) {
            if (target instanceof Player player && player.isFallFlying()) {
                player.stopFallFlying();
            }
            double above = target.getY() - ay;
            double slack = IncarcerousRules.PULL_DOWN_VERTICAL_SLACK;
            if (above > slack + TetherMath.SNAP_SLACK
                    && safeSnap(level, target, target.getX(), ay + slack, target.getZ())) {
                v = new Vec3(v.x, 0.0, v.z);
                changed = true;
            } else if (above > slack
                    && (isPlayer ? !target.onGround() && playerVy > 0.0
                                 : v.y > -IncarcerousRules.PULL_DOWN_SPEED)) {
                // players: rising is judged from the client-reported Y change (LastY), not the stale server velocity
                v = new Vec3(v.x, -IncarcerousRules.PULL_DOWN_SPEED, v.z);
                changed = true;
            }
        }

        if (changed) {
            target.setDeltaMovement(v);
            if (isPlayer) {
                target.hurtMarked = true;
            }
        }
        if (isPlayer) {
            tag.putDouble("LastY", target.getY());
        }
    }

    /**
     * Teleport onto the tether only to a collision-free spot: the destination, else 1 or 2 blocks higher.
     * Returns false (and does nothing) when all three are blocked, so the caller falls back to velocity.
     */
    private static boolean safeSnap(ServerLevel level, LivingEntity target, double x, double y, double z) {
        for (int up = 0; up <= 2; up++) {
            if (level.noCollision(target, target.getBoundingBox().move(x - target.getX(), y + up - target.getY(), z - target.getZ()))) {
                DisableSupport.snapTo(target, x, y + up, z);
                return true;
            }
        }
        return false;
    }

    /** Master: knockback on one member of a bound group is passed to the others (they're chained together). */
    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent event) {
        LivingEntity target = event.getEntity();
        if (sharingKnockback || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        UUID group = groupOf(target);
        if (group == null) {
            return;
        }
        Holder<MobEffect> chains = DisableSupport.holder(ExampleMod.INCARCEROUS_CHAINS);
        if (!target.hasEffect(chains)) {
            return;
        }
        List<LivingEntity> members = level.getEntitiesOfClass(LivingEntity.class,
                target.getBoundingBox().inflate(IncarcerousRules.GROUP_SEARCH_RADIUS),
                e -> e != target && e.hasEffect(chains) && group.equals(groupOf(e)));
        sharingKnockback = true;
        try {
            for (LivingEntity member : members) {
                member.knockback(event.getStrength(), event.getRatioX(), event.getRatioZ());
                if (member instanceof ServerPlayer) {
                    member.hurtMarked = true;
                }
            }
        } finally {
            sharingKnockback = false;
        }
    }

    @Nullable
    private static UUID groupOf(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(TAG)) {
            return null;
        }
        CompoundTag tag = data.getCompound(TAG);
        return tag.hasUUID("Group") ? tag.getUUID("Group") : null;
    }
}
