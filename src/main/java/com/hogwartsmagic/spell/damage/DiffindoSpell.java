package com.hogwartsmagic.spell.damage;

import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.TerrainRules;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Diffindo: a precise severing charm. It cuts partly through armour (dealt as armour-bypassing magic
 * damage after the reduced armour is applied once), shears a sheep instead of hurting it, and on a
 * block hit cuts leaves, vines, webs, wool and grass free as if sheared. At Master the same cut also
 * lands on the nearest enemy up to 3 blocks behind the target.
 */
public final class DiffindoSpell {
    private DiffindoSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        Entity responsible = CombatEffects.responsible(caster);
        if (target instanceof Sheep sheep && sheep.readyForShearing()) {
            sheep.shear(SoundSource.PLAYERS);
            sheep.gameEvent(GameEvent.SHEAR, responsible);
            return; // the shearing charm, not a killing blow
        }
        int rank = DisableSupport.casterRank(caster, SpellType.DIFFINDO);
        float damage = DamageSupport.scaled(caster, DiffindoRules.damage(rank));
        double ignore = DiffindoRules.armorIgnore(rank);
        sever(target, damage, ignore);
        if (DiffindoRules.cleaves(rank) && responsible != null) {
            cleave(level, target, caster, responsible, damage, ignore);
        }
    }

    private static void sever(LivingEntity target, float damage, double ignore) {
        float dealt = DiffindoRules.damageAfterArmor(damage, target.getArmorValue(),
                target.getAttributeValue(Attributes.ARMOR_TOUGHNESS), ignore);
        if (dealt > 0f) {
            target.hurt(target.damageSources().magic(), dealt);
        }
    }

    private static void cleave(ServerLevel level, LivingEntity first, @Nullable Entity caster, Entity responsible,
                               float damage, double ignore) {
        double fx = first.getX() - responsible.getX();
        double fz = first.getZ() - responsible.getZ();
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
                first.getBoundingBox().inflate(DiffindoRules.CLEAVE_RANGE + 1.0),
                e -> DisableSupport.isEnemyOf(caster, e, first)
                        && DiffindoRules.inCleaveLane(fx, fz, e.getX() - first.getX(), e.getZ() - first.getZ()));
        LivingEntity next = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            double d = candidate.distanceToSqr(first);
            if (d < best) {
                best = d;
                next = candidate;
            }
        }
        if (next == null) {
            return;
        }
        sever(next, damage, ignore);
        Vec3 from = first.position().add(0.0, first.getBbHeight() * 0.6, 0.0);
        Vec3 to = next.position().add(0.0, next.getBbHeight() * 0.6, 0.0);
        int steps = Math.max(3, (int) (from.distanceTo(to) * 2));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.playSound(null, next.getX(), next.getY(), next.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.9f, 1.3f);
    }

    /**
     * Cuts every #hogwartsmagic:diffindo_cuttable block within the rank's radius of the struck block,
     * dropping what shears would. A tall plant hit on its top half is cut from its bottom half, where its
     * loot lives. Each block must pass TerrainRules.canBreak (ward, spawn protection, BreakEvent).
     *
     * @return how many blocks were cut
     */
    public static int cutBlocks(ServerLevel level, BlockPos hit, @Nullable Entity caster) {
        int rank = DisableSupport.casterRank(caster, SpellType.DIFFINDO);
        Entity responsible = CombatEffects.responsible(caster);
        ItemStack shears = new ItemStack(Items.SHEARS);
        Set<BlockPos> done = new HashSet<>();
        int cut = 0;
        for (int[] o : BlastShape.offsets(DiffindoRules.cutRadius(rank))) {
            BlockPos pos = hit.offset(o[0], o[1], o[2]);
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                pos = pos.below();
                if (!level.isLoaded(pos)) {
                    continue;
                }
                state = level.getBlockState(pos);
            }
            if (!state.is(DamageSupport.DIFFINDO_CUTTABLE) || !done.add(pos)) {
                continue;
            }
            // canBreak posts a real BreakEvent with side effects: it is the last gate, and a yes means break now.
            if (!TerrainRules.canBreak(level, pos, state, caster)) {
                continue;
            }
            if (state.is(net.minecraft.world.level.block.Blocks.TRIPWIRE)) {
                // As real shears do: disarm first so cutting the line does not spring the trap.
                level.setBlock(pos, state.setValue(net.minecraft.world.level.block.TripWireBlock.DISARMED, true), 4);
            }
            Block.dropResources(state, level, pos, null, responsible, shears);
            level.levelEvent(2001, pos, Block.getId(state));
            level.removeBlock(pos, false);
            level.gameEvent(responsible, GameEvent.BLOCK_DESTROY, pos);
            cut++;
        }
        return cut;
    }
}
