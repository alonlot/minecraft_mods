package com.hogwartsmagic.spell;

import com.hogwartsmagic.caveinimicum.CaveInimicumManager;
import com.hogwartsmagic.spell.combat.CombatEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.level.BlockEvent;

import javax.annotation.Nullable;

/**
 * May a spell put a block here? One check for every spell that places blocks (Glacius ice today).
 * Refused inside a Cave Inimicum ward. A player caster also needs {@code mayInteract} (spawn protection,
 * world border) and an uncancelled {@link BlockEvent.EntityPlaceEvent}, which is the hook claim mods
 * guard placement with. An NPC caster, or no caster, needs the {@code mobGriefing} game rule.
 */
public final class TerrainRules {
    private TerrainRules() {
    }

    public static boolean canPlace(ServerLevel level, BlockPos pos, @Nullable Entity caster) {
        if (CaveInimicumManager.get(level).isWardedAt(pos)) {
            return false;
        }
        Entity responsible = CombatEffects.responsible(caster);
        if (responsible instanceof Player player) {
            if (!level.mayInteract(player, pos)) {
                return false;
            }
            if (!player.mayBuild()) {
                return false; // adventure mode
            }
            BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
            BlockEvent.EntityPlaceEvent event = new BlockEvent.EntityPlaceEvent(snapshot, level.getBlockState(pos.below()), player);
            return !MinecraftForge.EVENT_BUS.post(event);
        }
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    /**
     * May a spell break (or cut) the block here? Refused inside a Cave Inimicum ward. A player caster
     * must also be allowed to build in their game mode (adventure mode), pass {@code mayInteract}
     * (spawn protection, world border) and see an uncancelled {@link BlockEvent.BreakEvent} posted as
     * them, which is the hook claim mods guard breaking with. An NPC caster, or no caster, needs the
     * {@code mobGriefing} game rule. Callers check {@code level.isLoaded(pos)} first.
     * <p>
     * <b>This is NOT a pure check.</b> For a player caster it posts a REAL {@code BreakEvent}, and this
     * mod's listeners act on it irreversibly: Reparo's RecentlyBrokenTracker logs the block as broken, an
     * active Horcrux is destroyed and summons a Dementor, nearby Ironbellies wake, the merfolk alarm
     * fires, and a mandrake pull starts.
     * <ul>
     * <li>Call it only as the LAST step, immediately before actually breaking the block, after every tag,
     * block-entity, hardness, unbreakable and chance filter has already passed.</li>
     * <li>Never use it to probe or preview whether a block could be broken.</li>
     * <li>A {@code true} result means the caller must break the block now.</li>
     * </ul>
     */
    public static boolean canBreak(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity caster) {
        if (CaveInimicumManager.get(level).isWardedAt(pos)) {
            return false;
        }
        Entity responsible = CombatEffects.responsible(caster);
        if (responsible instanceof Player player) {
            if (!player.mayBuild()) {
                return false; // adventure mode
            }
            if (!level.mayInteract(player, pos)) {
                return false;
            }
            return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player));
        }
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }
}
