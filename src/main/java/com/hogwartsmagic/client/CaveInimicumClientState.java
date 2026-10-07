package com.hogwartsmagic.client;

import com.hogwartsmagic.caveinimicum.CaveInimicumWardBounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Client-side view of active ward bounds used to hide entities when the local
 * player is outside a ward but the entity is inside it.
 */
public final class CaveInimicumClientState {
    private static volatile List<CaveInimicumWardBounds> activeWards = List.of();

    private CaveInimicumClientState() {
    }

    public static void setActiveWards(List<CaveInimicumWardBounds> wards) {
        activeWards = List.copyOf(wards);
    }

    public static boolean shouldHideEntity(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        Player localPlayer = minecraft.player;
        if (localPlayer == null || entity == null || entity == localPlayer || !entity.isAlive()) {
            return false;
        }

        // Called once per entity per frame from the render dispatcher: no per-call allocation.
        BlockPos entityPos = entity.blockPosition();
        BlockPos localPos = localPlayer.blockPosition();
        boolean entityInsideWard = false;
        for (CaveInimicumWardBounds ward : activeWards) {
            if (!ward.contains(entityPos)) {
                continue;
            }
            entityInsideWard = true;
            if (ward.contains(localPos)) {
                return false;
            }
        }
        return entityInsideWard;
    }

    public static boolean shouldMuteBlockPos(BlockPos sourcePos) {
        Minecraft minecraft = Minecraft.getInstance();
        Player localPlayer = minecraft.player;
        if (localPlayer == null || sourcePos == null) {
            return false;
        }

        boolean sourceInsideWard = false;
        for (CaveInimicumWardBounds ward : activeWards) {
            if (!ward.contains(sourcePos)) {
                continue;
            }

            sourceInsideWard = true;
            if (ward.contains(localPlayer.blockPosition())) {
                return false;
            }
        }
        return sourceInsideWard;
    }

    public static boolean shouldMuteEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        return shouldMuteBlockPos(entity.blockPosition());
    }

    public static void clear() {
        activeWards = List.of();
    }
}
