package com.hogwartsmagic.mixin;

import com.hogwartsmagic.spell.combat.CombatEffects;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Peaceful difficulty's once-a-second heal (the only heal call in Player.aiStep) is natural regeneration too. */
@Mixin(Player.class)
public abstract class PlayerBleedingMixin {

    @Redirect(method = "aiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;heal(F)V"))
    private void hogwartsmagic$noPeacefulRegenWhileBleeding(Player self, float amount) {
        if (!CombatEffects.isBleeding(self)) {
            self.heal(amount);
        }
    }
}
