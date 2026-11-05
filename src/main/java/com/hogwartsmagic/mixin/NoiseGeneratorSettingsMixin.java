package com.hogwartsmagic.mixin;

import com.hogwartsmagic.worldgen.ModSurfaceRules;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Puts the mod's surface rules ({@link ModSurfaceRules}) in front of every noise settings' rule tree.
 *
 * <p>In 1.21 the noise settings come from the vanilla datapack JSON, so a mixin on
 * {@code SurfaceRuleData.overworld()} never runs in a real world. The chunk generator reads the
 * rule through {@link NoiseGeneratorSettings#surfaceRule()} (surface build, carvers, top material),
 * so wrapping the accessor covers every reader. NoiseGeneratorSettings is a record, so no field is
 * added to it: the mod rules are built once in {@link ModSurfaceRules} and only the cheap outer
 * sequence is made per call.
 */
@Mixin(NoiseGeneratorSettings.class)
public abstract class NoiseGeneratorSettingsMixin {

    @Inject(method = "surfaceRule", at = @At("RETURN"), cancellable = true)
    private void hogwartsmagic$prependModSurfaceRules(CallbackInfoReturnable<SurfaceRules.RuleSource> cir) {
        cir.setReturnValue(ModSurfaceRules.prependTo(cir.getReturnValue()));
    }
}
