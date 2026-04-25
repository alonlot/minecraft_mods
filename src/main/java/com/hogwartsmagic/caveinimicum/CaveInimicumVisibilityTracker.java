package com.hogwartsmagic.caveinimicum;

import com.hogwartsmagic.block.entity.CaveInimicumWardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers whether each player previously had access to reality inside each
 * nearby ward so region re-sync only happens on meaningful visibility transitions.
 */
public final class CaveInimicumVisibilityTracker {
    private final Map<UUID, Map<BlockPos, Boolean>> truthSightByPlayer = new HashMap<>();

    public void tick(CaveInimicumManager manager, Iterable<CaveInimicumWardBlockEntity> wards) {
        for (ServerPlayer player : manager.level().players()) {
            Map<BlockPos, Boolean> playerState = truthSightByPlayer.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
            for (CaveInimicumWardBlockEntity ward : wards) {
                if (!ward.isActive() || !manager.isPlayerRelevantToWard(player, ward)) {
                    if (playerState.remove(ward.getBlockPos()) != null) {
                        manager.syncActiveWardListToPlayer(player);
                    }
                    continue;
                }

                boolean seesReality = manager.playerSeesRealityInWard(player, ward);
                Boolean previous = playerState.put(ward.getBlockPos(), seesReality);
                if (previous == null || previous.booleanValue() != seesReality) {
                    manager.syncWardRegionToPlayer(player, ward.bounds());
                    manager.syncActiveWardListToPlayer(player);
                }
            }

            Iterator<Map.Entry<BlockPos, Boolean>> iterator = playerState.entrySet().iterator();
            while (iterator.hasNext()) {
                BlockPos wardPos = iterator.next().getKey();
                if (!manager.isWardStillTracked(wardPos)) {
                    iterator.remove();
                }
            }

            if (playerState.isEmpty()) {
                truthSightByPlayer.remove(player.getUUID());
            }
        }
    }

    public void removeWard(BlockPos wardPos) {
        for (Map<BlockPos, Boolean> playerState : truthSightByPlayer.values()) {
            playerState.remove(wardPos);
        }
    }

    public void removePlayer(UUID playerId) {
        truthSightByPlayer.remove(playerId);
    }
}
