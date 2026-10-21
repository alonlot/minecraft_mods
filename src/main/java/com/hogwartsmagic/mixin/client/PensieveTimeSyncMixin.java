package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.pensieve.PensieveClientState;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the memory scene clock that a time sync arrived. A sync that agrees with the client's own time changes
 * nothing the clock could see, yet it is the evidence that the server is keeping pace. TAIL runs only on the main
 * thread: on the network thread vanilla's {@code ensureRunningOnSameThread} reschedules the packet and throws first.
 */
@Mixin(ClientPacketListener.class)
public abstract class PensieveTimeSyncMixin {

    @Inject(
            method = "handleSetTime(Lnet/minecraft/network/protocol/game/ClientboundSetTimePacket;)V",
            at = @At("TAIL"),
            remap = false)
    private void hogwartsmagic$noteTimeSync(ClientboundSetTimePacket packet, CallbackInfo ci) {
        PensieveClientState.onTimeSync();
    }
}
