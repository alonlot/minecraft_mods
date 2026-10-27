package com.hogwartsmagic.mixin;

import com.hogwartsmagic.pensieve.PensieveGhost;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A free ghost in a recorded memory passes through blocks, as a spectator does: {@code Player#tick} sets
 * {@code noPhysics} from spectator mode every tick, so the flag is raised right after that write. Common mixin: the
 * predicate reads a plain flag on the client and the sessions on the server, never a client class.
 */
@Mixin(Player.class)
public abstract class PlayerGhostNoclipMixin {

    @Inject(method = "tick", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/player/Player;noPhysics:Z", opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER))
    private void hogwartsmagic$ghostNoclip(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (PensieveGhost.noclip(self)) self.noPhysics = true;
    }
}
