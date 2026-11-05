package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.CaveInimicumClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses sounds originating inside active Cave Inimicum wards for clients
 * that are still outside the protected volume.
 *
 * <p>Both injections sit right after the hop to the render thread (a HEAD injection runs first on the netty
 * thread, where {@code level.getEntity} reads the client's entity map while the main thread mutates it), the
 * same way {@code ServerGamePacketListenerMixin} does on the server.
 */
@Mixin(ClientPacketListener.class)
public abstract class CaveInimicumClientPacketListenerMixin {

    private static final String ENSURE_MAIN_THREAD =
            "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V";

    @Inject(
            method = "handleSoundEvent(Lnet/minecraft/network/protocol/game/ClientboundSoundPacket;)V",
            at = @At(value = "INVOKE", target = ENSURE_MAIN_THREAD, shift = At.Shift.AFTER),
            cancellable = true,
            remap = false)
    private void hogwartsmagic$muteWardedPositionalSounds(ClientboundSoundPacket packet, CallbackInfo ci) {
        BlockPos sourcePos = BlockPos.containing(packet.getX(), packet.getY(), packet.getZ());
        if (CaveInimicumClientState.shouldMuteBlockPos(sourcePos)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "handleSoundEntityEvent(Lnet/minecraft/network/protocol/game/ClientboundSoundEntityPacket;)V",
            at = @At(value = "INVOKE", target = ENSURE_MAIN_THREAD, shift = At.Shift.AFTER),
            cancellable = true,
            remap = false)
    private void hogwartsmagic$muteWardedEntitySounds(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Entity entity = minecraft.level.getEntity(packet.getId());
        if (CaveInimicumClientState.shouldMuteEntity(entity)) {
            ci.cancel();
        }
    }
}
