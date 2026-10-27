package com.hogwartsmagic.mixin;

import com.hogwartsmagic.pensieve.capture.CaptureHooks;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the chat a player receives for the Pensieve recorder: player lines and system lines, not the action bar. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerChatCaptureMixin {

    @Inject(method = "sendChatMessage(Lnet/minecraft/network/chat/OutgoingChatMessage;ZLnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At("HEAD"))
    private void hogwartsmagic$captureChat(OutgoingChatMessage message, boolean filtered, ChatType.Bound bound,
                                           CallbackInfo ci) {
        if (CaptureHooks.isPrivateChatType(bound.chatType())) {
            return; // whispers and team messages are the owner's alone, not for every viewer of the memory
        }
        CaptureHooks.onChatComponent((ServerPlayer) (Object) this, bound.name(), message.content());
    }

    @Inject(method = "sendSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"))
    private void hogwartsmagic$captureSystemChat(Component message, boolean overlay, CallbackInfo ci) {
        if (!overlay) {
            CaptureHooks.onChatComponent((ServerPlayer) (Object) this, null, message);
        }
    }
}
