package com.hogwartsmagic.mixin;

import com.hogwartsmagic.spell.combat.CombatEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Bleeding acts like naturalRegeneration=false for that one player: both food-regeneration branches
 * of FoodData.tick read the game rule through this single getBoolean call, so the saturation and the
 * slow regen are both skipped (and no exhaustion is spent on them). Starvation is unaffected.
 */
@Mixin(FoodData.class)
public abstract class FoodDataBleedingMixin {

    @Redirect(method = "tick(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
    private boolean hogwartsmagic$noRegenWhileBleeding(GameRules rules, GameRules.Key<GameRules.BooleanValue> key,
                                                       Player player) {
        return rules.getBoolean(key) && !CombatEffects.isBleeding(player);
    }
}
