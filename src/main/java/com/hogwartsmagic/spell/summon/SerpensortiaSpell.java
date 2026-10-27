package com.hogwartsmagic.spell.summon;

import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.SnakeEntity;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.particles.ParticleTypes;
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

    /** The conjured snakes belonging to {@code owner} within DISMISS_RADIUS. */
    public static List<SnakeEntity> snakesOf(ServerLevel level, Entity owner) {
        return level.getEntitiesOfClass(SnakeEntity.class,
                owner.getBoundingBox().inflate(SerpensortiaRules.DISMISS_RADIUS),
                snake -> owner.getUUID().equals(snake.getOwnerUUID()));
    }
}
