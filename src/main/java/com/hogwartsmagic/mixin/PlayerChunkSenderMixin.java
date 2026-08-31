package com.hogwartsmagic.mixin;

import com.hogwartsmagic.worldgen.DiagonAlleyPortal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The two edits that make walking into Diagon Alley invisible.
 *
 * <p>Both are about what the client is <em>not</em> told. See {@link DiagonAlleyPortal} for why the
 * crossing never sends a respawn packet in the first place.
 *
 * <ol>
 *   <li><b>Nothing is forgotten during a crossing.</b> Leaving a level ends at
 *       {@code ChunkMap.applyChunkTrackingView(player, EMPTY)}, which sends a
 *       {@code ClientboundForgetLevelChunkPacket} for every chunk the client holds. That is
 *       harmless in vanilla, where the client is rebuilding its world anyway, and catastrophic
 *       here: the world would blink out at the threshold and stream back in over the next second,
 *       which is exactly the seam the whole design exists to remove.</li>
 *   <li><b>The empty parts of the alley dimension are never sent.</b> Because of the first edit,
 *       the client arrives still holding the chunks of the world it came from. The alley dimension
 *       is void everywhere except the street, so letting it send those chunks would paint the
 *       player's own world over with nothing. Declining to send them is what leaves the field, or
 *       the hillside, or wherever the wall was built, standing behind you when you look back
 *       through the arch. Nothing is faked; it is simply never overwritten.</li>
 * </ol>
 *
 * <p>Server-side, so it is registered under {@code mixins} rather than {@code client} -- a
 * dedicated server has to do both of these too.
 */
@Mixin(PlayerChunkSender.class)
public abstract class PlayerChunkSenderMixin {

    @Inject(method = "dropChunk", at = @At("HEAD"), cancellable = true)
    private void hogwartsmagic$keepChunksAcrossTheArch(ServerPlayer player, ChunkPos pos,
                                                       CallbackInfo ci) {
        if (DiagonAlleyPortal.isMoving(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "sendChunk", at = @At("HEAD"), cancellable = true)
    private static void hogwartsmagic$doNotSendTheVoid(ServerGamePacketListenerImpl connection,
                                                       ServerLevel level, LevelChunk chunk,
                                                       CallbackInfo ci) {
        if (!DiagonAlleyPortal.shouldSendAlleyChunk(connection.player, chunk.getPos())) {
            ci.cancel();
        }
    }
}
