package com.hogwartsmagic.spell;

import com.hogwartsmagic.caveinimicum.CaveInimicumManager;
import com.hogwartsmagic.spell.combat.CombatEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
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
}
