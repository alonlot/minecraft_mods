package com.hogwartsmagic.spell.damage;

import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.TerrainRules;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;

import javax.annotation.Nullable;

/**
 * Incendio: fire as a tool. Creatures burn (4..7s by rank, cut to 40% in rain). On blocks it lights
 * campfires, candles and candle cakes, primes TNT, and adds ten seconds of fuel to a furnace; anything
 * else gets one fire on the struck face, like flint and steel (so it still lights Nether portals). From
 * Adept the flames also catch on flammable blocks around the impact, when doFireTick and mobGriefing are
 * on and it isn't raining there. Every block change passes TerrainRules.canPlace (ward, spawn
 * protection, claims; mobGriefing for NPC casters).
 */
public final class IncendioSpell {
    private IncendioSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        int rank = DisableSupport.casterRank(caster, SpellType.INCENDIO);
        boolean raining = target.level().isRainingAt(target.blockPosition());
        target.igniteForSeconds(IncendioRules.igniteSeconds(rank, raining));
    }

    public static boolean onBlockHit(ServerLevel level, BlockPos pos, Direction face, @Nullable Entity caster) {
        BlockState state = level.getBlockState(pos);
        Entity responsible = CombatEffects.responsible(caster);
        if (CampfireBlock.canLight(state) || CandleBlock.canLight(state) || CandleCakeBlock.canLight(state)) {
            if (!TerrainRules.canPlace(level, pos, caster)) {
                return false;
            }
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, true), 11);
            level.gameEvent(responsible, GameEvent.BLOCK_CHANGE, pos);
            playIgnite(level, pos);
            return true;
        }
        if (state.getBlock() instanceof TntBlock) {
            if (!TerrainRules.canPlace(level, pos, caster)) {
                return false;
            }
            state.onCaughtFire(level, pos, face, responsible instanceof LivingEntity living ? living : null);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
            return true;
        }
        if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
            return TerrainRules.canPlace(level, pos, caster) && fuelFurnace(level, pos, state, furnace);
        }

        int rank = DisableSupport.casterRank(caster, SpellType.INCENDIO);
        BlockPos firePos = pos.relative(face);
        boolean lit = false;
        if (level.isLoaded(firePos) && BaseFireBlock.canBePlacedAt(level, firePos, face)
                && TerrainRules.canPlace(level, firePos, caster)) {
            level.setBlock(firePos, BaseFireBlock.getState(level, firePos), 11);
            level.gameEvent(responsible, GameEvent.BLOCK_PLACE, firePos);
            lit = true;
        }
        if (IncendioRules.spreadsFire(rank) && !level.isRainingAt(firePos)
                && level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)
                && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            lit |= spreadFire(level, firePos, IncendioRules.fireRadius(rank), caster) > 0;
        }
        if (lit) {
            playIgnite(level, firePos);
        }
        return lit;
    }

    /**
     * Ten more seconds of burn time (up to 1600). Furnace fields are package-private and this mod has no
     * access transformer, so the burn time goes through the block entity's own NBT round trip; the
     * furnace recomputes its fuel bar from the fuel slot and turns itself off when the time runs out.
     */
    private static boolean fuelFurnace(ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace) {
        CompoundTag tag = furnace.saveWithoutMetadata(level.registryAccess());
        int current = tag.getInt("BurnTime");
        int next = IncendioRules.furnaceBurnTime(current);
        if (next <= current) {
            return false;
        }
        tag.putInt("BurnTime", next);
        furnace.loadWithComponents(tag, level.registryAccess());
        furnace.setChanged();
        if (state.hasProperty(AbstractFurnaceBlock.LIT) && !state.getValue(AbstractFurnaceBlock.LIT)) {
            level.setBlock(pos, state.setValue(AbstractFurnaceBlock.LIT, true), 3);
        }
        playIgnite(level, pos);
        return true;
    }

    /** Fire on empty spots next to flammable blocks within the radius, nearest first, at most 24. */
    private static int spreadFire(ServerLevel level, BlockPos center, double radius, @Nullable Entity caster) {
        int placed = 0;
        for (int[] o : BlastShape.offsets(radius)) {
            if (placed >= IncendioRules.MAX_FIRES) {
                break;
            }
            BlockPos pos = center.offset(o[0], o[1], o[2]);
            if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir() || !touchesFlammable(level, pos)) {
                continue;
            }
            if (!BaseFireBlock.canBePlacedAt(level, pos, Direction.UP) || !TerrainRules.canPlace(level, pos, caster)) {
                continue;
            }
            level.setBlock(pos, BaseFireBlock.getState(level, pos), 11);
            level.gameEvent(CombatEffects.responsible(caster), GameEvent.BLOCK_PLACE, pos);
            placed++;
        }
        return placed;
    }

    private static boolean touchesFlammable(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (level.isLoaded(neighbor) && level.getBlockState(neighbor).isFlammable(level, neighbor, direction.getOpposite())) {
                return true;
            }
        }
        return false;
    }

    private static void playIgnite(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0f,
                level.random.nextFloat() * 0.4f + 0.8f);
    }
}
