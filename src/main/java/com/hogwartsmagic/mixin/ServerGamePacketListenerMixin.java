package com.hogwartsmagic.mixin;

import com.hogwartsmagic.spell.mind.MindControl;
import com.hogwartsmagic.spell.mind.PossessionManager;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A controlled body (thrall or possessed) and a possessing caster's frozen body can't change their own
 * hotbar slot or move items. The injections sit right after the hop to the server thread (a HEAD injection
 * would run on the netty thread), so the session maps are read on the right thread. Common mixin.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {
    @Shadow
    public ServerPlayer player;

    private boolean hogwartsmagic$locked() {
        return MindControl.isControlled(this.player) || PossessionManager.isPossessing(this.player);
    }

    @Inject(method = "handleSetCarriedItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void hogwartsmagic$lockCarriedItem(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        if (hogwartsmagic$locked()) {
            this.player.connection.send(new ClientboundSetCarriedItemPacket(this.player.getInventory().selected));
            ci.cancel();
        }
    }

    @Inject(method = "handleContainerClick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void hogwartsmagic$lockContainerClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (packet.getContainerId() == this.player.inventoryMenu.containerId && hogwartsmagic$locked()) {
            this.player.inventoryMenu.sendAllDataToRemote();
            ci.cancel();
        }
    }

    @Inject(method = "handleSetCreativeModeSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void hogwartsmagic$lockCreativeSlot(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        if (hogwartsmagic$locked()) {
            this.player.inventoryMenu.sendAllDataToRemote();
            ci.cancel();
        }
    }

    @Inject(method = "handlePickItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void hogwartsmagic$lockPickItem(ServerboundPickItemPacket packet, CallbackInfo ci) {
        if (hogwartsmagic$locked()) {
            this.player.connection.send(new ClientboundSetCarriedItemPacket(this.player.getInventory().selected));
            this.player.inventoryMenu.sendAllDataToRemote();
            ci.cancel();
        }
    }

    @Inject(method = "handlePlaceRecipe", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void hogwartsmagic$lockPlaceRecipe(ServerboundPlaceRecipePacket packet, CallbackInfo ci) {
        if (packet.getContainerId() == this.player.inventoryMenu.containerId && hogwartsmagic$locked()) {
            this.player.inventoryMenu.sendAllDataToRemote();
            ci.cancel();
        }
    }
}
