package com.hogwartsmagic.mixin;

import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.mind.PossessionManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A stunned entity, a possessed mob and a possessing caster's body are immobile (the last two use hidden
 * marker effects, never the server-only session maps, since this runs on both sides). LivingEntity.aiStep
 * then skips serverAiStep (goals, brain, navigation, move and look controls: the mob's AI is paused) and
 * zeroes xxa, zza and jumping, so gravity and knockback still apply but the entity can't walk. For players
 * this runs on the server and on the stunned player's own client (which has the synced effect), so their movement input is
 * zeroed on both sides. Player.isImmobile calls super, so players are covered too.
 *
 * <p>Common mixin (registered under "mixins"): a dedicated server needs it as much as a client.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityStunMixin {

    @Inject(method = "isImmobile", at = @At("HEAD"), cancellable = true)
    private void hogwartsmagic$stunnedIsImmobile(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (CombatEffects.isStunned(self) || PossessionManager.isPossessedMob(self)
                || (self instanceof Player && PossessionManager.isPossessingBody(self))) {
            cir.setReturnValue(true);
        }
    }
}
