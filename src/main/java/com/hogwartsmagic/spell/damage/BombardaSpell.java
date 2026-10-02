package com.hogwartsmagic.spell.damage;

import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Bombarda: the terrain-breaking charm. The blast hurts creatures less than Confringo (5..8 damage,
 * falling off to a quarter at the edge, plus knockback; the struck target and enemies per
 * {@link DamageSupport#isSplashTarget}) and breaks blocks in a sphere whose radius and hardness tier grow
 * with rank. Only a player's Bombarda breaks blocks, and only with mobGriefing on; the blocks themselves
 * are broken over the next few ticks by BombardaBlasts.
 */
public final class BombardaSpell {
    private BombardaSpell() {
    }

    public static void onEntityHit(LivingEntity target, @Nullable Entity caster) {
        if (target.level() instanceof ServerLevel level) {
            detonate(level, target.blockPosition(), target.position(), caster, target);
        }
    }

    /** @return how many positions were queued for breaking (0: nothing to break, or not allowed to) */
    public static int detonateAtBlock(ServerLevel level, BlockPos pos, Vec3 impact, @Nullable Entity caster) {
        return detonate(level, pos, impact, caster, null);
    }

    private static int detonate(ServerLevel level, BlockPos center, Vec3 impact, @Nullable Entity caster,
                                @Nullable LivingEntity primary) {
        int rank = DisableSupport.casterRank(caster, SpellType.BOMBARDA);
        double radius = BombardaRules.radius(rank);
        level.sendParticles(radius >= 2.5 ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION,
                impact.x, impact.y, impact.z, radius >= 2.5 ? 1 : 3, radius * 0.3, radius * 0.3, radius * 0.3, 0.0);
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                2.0f + (float) radius * 0.3f, 0.9f + level.random.nextFloat() * 0.2f);
        splash(level, impact, caster, primary, rank, radius);
        return queueBlocks(level, center, caster, rank, radius);
    }

    private static void splash(ServerLevel level, Vec3 impact, @Nullable Entity caster, @Nullable LivingEntity primary,
                               int rank, double radius) {
        Entity responsible = CombatEffects.responsible(caster);
        float base = DamageSupport.scaled(caster, BombardaRules.entityDamage(rank));
        if (base <= 0f) {
            return;
        }
        double reach = radius + 1.0;
        DamageSource source = level.damageSources().explosion(responsible, responsible);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(impact, reach * 2, reach * 2, reach * 2),
                e -> e != responsible && e.isAlive() && !e.isSpectator()
                        && (e == primary || DamageSupport.isSplashTarget(caster, e, primary)))) {
            double distance = e == primary ? 0.0 : e.position().distanceTo(impact);
            float amount = BombardaRules.splashDamage(base, distance, radius);
            if (amount <= 0f) {
                continue;
            }
            e.hurt(source, amount);
            double dx = impact.x - e.getX();
            double dz = impact.z - e.getZ();
            if (dx * dx + dz * dz < 1.0e-4) {
                dx = level.random.nextDouble() - 0.5;
                dz = level.random.nextDouble() - 0.5;
            }
            e.knockback(0.6 * amount / base, dx, dz);
            e.hurtMarked = true;
        }
    }

    private static int queueBlocks(ServerLevel level, BlockPos center, @Nullable Entity caster, int rank, double radius) {
        if (!(CombatEffects.responsible(caster) instanceof ServerPlayer player)) {
            return 0; // NPC Bombarda (Voldemort) never breaks terrain
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.bombarda.no_griefing")
                    .withStyle(ChatFormatting.GRAY), true);
            return 0;
        }
        List<BlockPos> positions = new ArrayList<>();
        for (int[] o : BlastShape.offsets(radius)) {
            BlockPos pos = center.offset(o[0], o[1], o[2]);
            if (level.isLoaded(pos) && !level.getBlockState(pos).isAir()) {
                positions.add(pos);
            }
        }
        return BombardaBlasts.queue(level, player, rank, positions) ? positions.size() : 0;
    }
}
