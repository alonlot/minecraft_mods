package com.hogwartsmagic.spell.summon;

import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.SnakeEntity;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.disable.DisableSupport;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Serpensortia: conjures the caster's snakes (count, lifetime and venom by rank) where the beam lands.
 * A creature hit is their first target. A new cast replaces the caster's previous snakes. Works for
 * NPC casters too: Voldemort's snakes are his and defend him. Server only.
 */
public final class SerpensortiaSpell {
    private SerpensortiaSpell() {
    }

    public static int conjureAt(ServerLevel level, Vec3 at, @Nullable LivingEntity target, @Nullable Entity caster) {
        Entity responsible = CombatEffects.responsible(caster);
        if (!(responsible instanceof LivingEntity owner) || !owner.isAlive()) {
            return 0;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.SERPENSORTIA);
        for (SnakeEntity previous : snakesOf(level, owner)) {
            previous.vanish();
        }

        int count = SerpensortiaRules.snakeCount(rank);
        float yaw = owner.getYRot();
        double radians = Math.toRadians(yaw);
        // The caster's right-hand side, so the snakes line up across the line of the beam.
        double rightX = -Math.cos(radians);
        double rightZ = -Math.sin(radians);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            SnakeEntity snake = ModEntities.SNAKE.get().create(level);
            if (snake == null) {
                continue;
            }
            double offset = SerpensortiaRules.spawnOffset(i, count);
            double x = at.x + rightX * offset;
            double z = at.z + rightZ * offset;
            snake.moveTo(x, at.y, z, yaw, 0.0F);
            if (!level.noCollision(snake)) {
                snake.moveTo(at.x, at.y, at.z, yaw, 0.0F);
                if (!level.noCollision(snake)) {
                    continue; // nowhere free to stand: skip this snake rather than wedge it in a wall
                }
            }
            snake.conjure(owner, SerpensortiaRules.lifetimeTicks(rank), SerpensortiaRules.poisonAmplifier(rank));
            if (owner instanceof Player) {
                snake.addTag(CombatTags.ALLIED);
            }
            if (target != null && snake.isFairTarget(target)) {
                snake.setTarget(target);
            }
            level.addFreshEntity(snake);
            level.sendParticles(ParticleTypes.SQUID_INK, snake.getX(), snake.getY() + 0.2D, snake.getZ(),
                    8, 0.2D, 0.1D, 0.2D, 0.02D);
            spawned++;
        }
        if (spawned > 0) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.CAT_HISS, SoundSource.HOSTILE, 0.8f, 1.5f);
        }
        return spawned;
    }

    private static final String TAG_LAST_COMMAND = "hogwartsmagic:parseltongue_at";

    /**
     * Parseltongue (Master): aimed at a creature within COMMAND_RANGE, every snake of the caster attacks
     * it; aimed anywhere else, they switch between following the caster and guarding the aimed spot (or
     * the caster's feet when aiming at the sky). Never costs mana or starts a cooldown. A player is only
     * a legal target while PvP is on and the caster may harm them (teams, friendly fire).
     */
    public static void command(ServerPlayer player) {
        if (!SerpensortiaRules.parseltongue(SpellMasteryData.getRank(player, SpellType.SERPENSORTIA))) {
            return;
        }
        if (CombatEffects.isCastingBlocked(player)) {
            return; // stunned or silenced: no Parseltongue either
        }
        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();
        if (data.contains(TAG_LAST_COMMAND) && now - data.getLong(TAG_LAST_COMMAND) < SerpensortiaRules.COMMAND_COOLDOWN_TICKS) {
            return;
        }
        data.putLong(TAG_LAST_COMMAND, now);

        ServerLevel level = player.serverLevel();
        List<SnakeEntity> snakes = snakesOf(level, player);
        if (snakes.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.parseltongue.no_snakes")
                    .withStyle(ChatFormatting.DARK_GREEN), true);
            return;
        }
        LivingEntity aimed = aimedCreature(player, snakes);
        if (aimed instanceof Player victim && victim != player
                && !SerpensortiaRules.commandMayHitPlayer(level.getServer().isPvpAllowed(), player.canHarmPlayer(victim))) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.parseltongue.no_pvp")
                    .withStyle(ChatFormatting.DARK_GREEN), true);
            return;
        }
        if (aimed != null && snakes.get(0).isFairTarget(aimed)) {
            for (SnakeEntity snake : snakes) {
                snake.commandAttack(aimed);
            }
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.parseltongue.attack", aimed.getDisplayName())
                    .withStyle(ChatFormatting.DARK_GREEN), true);
        } else {
            int mode = SerpensortiaRules.toggledMode(snakes.get(0).getMode());
            BlockPos anchor = player.blockPosition();
            HitResult hit = player.pick(SerpensortiaRules.COMMAND_RANGE, 1.0F, false);
            if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
                anchor = blockHit.getBlockPos().relative(blockHit.getDirection());
            }
            for (SnakeEntity snake : snakes) {
                snake.commandMode(mode, anchor, level.dimension());
            }
            player.displayClientMessage(Component.translatable(mode == SerpensortiaRules.MODE_GUARD
                    ? "message.hogwartsmagic.parseltongue.guard" : "message.hogwartsmagic.parseltongue.follow")
                    .withStyle(ChatFormatting.DARK_GREEN), true);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CAT_HISS, SoundSource.PLAYERS, 0.7f, 1.8f);
    }

    @Nullable
    private static LivingEntity aimedCreature(ServerPlayer player, List<SnakeEntity> own) {
        double range = SerpensortiaRules.COMMAND_RANGE;
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        HitResult block = player.pick(range, 1.0F, false);
        double reach = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(start);
        Vec3 end = start.add(look.scale(reach));
        AABB box = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, start, end, box,
                entity -> entity instanceof LivingEntity living && living.isAlive() && entity.isPickable()
                        && !entity.isSpectator() && !own.contains(entity));
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    /** The conjured snakes belonging to {@code owner} within DISMISS_RADIUS. */
    public static List<SnakeEntity> snakesOf(ServerLevel level, Entity owner) {
        return level.getEntitiesOfClass(SnakeEntity.class,
                owner.getBoundingBox().inflate(SerpensortiaRules.DISMISS_RADIUS),
                snake -> owner.getUUID().equals(snake.getOwnerUUID()));
    }
}
