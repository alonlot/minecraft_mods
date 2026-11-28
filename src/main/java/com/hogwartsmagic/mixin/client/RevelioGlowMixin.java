package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.enchant.SpellEnchantClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Revelio helmets: near hostiles get the glowing outline, on the wearer's screen only. */
@Mixin(Minecraft.class)
public abstract class RevelioGlowMixin {
    @Inject(method = "shouldEntityAppearGlowing(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void hogwartsmagic$revelio(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (SpellEnchantClient.revelioGlows(entity)) {
            cir.setReturnValue(true);
        }
    }
}
