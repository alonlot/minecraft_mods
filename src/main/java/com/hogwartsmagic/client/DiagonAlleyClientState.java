package com.hogwartsmagic.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * The client's view of where the alley is, so its shopkeepers can be hidden from anyone outside it.
 *
 * <p>Blocks can be withheld per player — the server simply sends different ones. Entities cannot:
 * the server tracks and broadcasts them to everybody nearby, and there is no per-player filter in
 * the protocol. So the same answer the mod already uses for Cave Inimicum is used here: the server
 * says where the region is, and the client declines to draw entities inside it while the local
 * player is outside.
 *
 * <p><b>This is render suppression, not concealment.</b> The entities are still sent, so a modified
 * client can see them. Blocks are genuinely never sent; villagers are only not drawn. That is the
 * honest limit of doing this without rewriting entity tracking, and it is the same limit Cave
 * Inimicum has.
 */
public final class DiagonAlleyClientState {

    /** Zero size means no alley is open, which is the state everything starts and ends in. */
    private static volatile BlockPos origin = BlockPos.ZERO;
    private static volatile int sizeX;
    private static volatile int sizeY;
    private static volatile int sizeZ;
    /** How far above the street the illusion pretends there is nothing; matches the server's shaft. */
    private static volatile int skyShaft;

    private DiagonAlleyClientState() {
    }

    public static void setRegion(BlockPos at, int sx, int sy, int sz, int shaft) {
        origin = at;
        sizeX = sx;
        sizeY = sy;
        sizeZ = sz;
        skyShaft = shaft;
    }

    public static void clear() {
        sizeX = 0;
        sizeY = 0;
        sizeZ = 0;
    }

    private static boolean inside(BlockPos p, int margin) {
        if (sizeX <= 0) {
            return false;
        }
        return p.getX() >= origin.getX() - margin && p.getX() <= origin.getX() + sizeX + margin
                && p.getZ() >= origin.getZ() - margin && p.getZ() <= origin.getZ() + sizeZ + margin
                && p.getY() >= origin.getY() - margin
                && p.getY() <= origin.getY() + sizeY + skyShaft;
    }

    /**
     * Hide an entity that is in the alley from a viewer who is not.
     *
     * <p>The margin is deliberately tighter for the entity than for the viewer: a shopkeeper
     * standing a block inside the boundary should be hidden, but somebody standing a block outside
     * it should still count as in, or the whole street would flicker as they walked the threshold.
     */
    public static boolean shouldHideEntity(Entity entity) {
        if (sizeX <= 0) {
            return false;
        }
        Player local = Minecraft.getInstance().player;
        if (local == null || entity == null || entity == local || !entity.isAlive()) {
            return false;
        }
        if (!inside(entity.blockPosition(), 0)) {
            return false;
        }
        return !inside(local.blockPosition(), 8);
    }
}
