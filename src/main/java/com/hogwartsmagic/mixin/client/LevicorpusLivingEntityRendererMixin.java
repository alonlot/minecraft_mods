package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In 1.21, {@link LivingEntityRenderer} uses {@link LivingEntityRenderer#isEntityUpsideDown} in
 * <b>two</b> places: inside {@code render()} it flips pitch/body angles <i>before</i>
 * {@code setupRotations}, and inside {@code setupRotations} it applies the Z 180° pose.
 * Only patching {@code setupRotations} misses the first pass — mobs levitate but do not look
 * properly inverted. Forcing {@code true} here for Levicorpus enables both vanilla steps.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LevicorpusLivingEntityRendererMixin {

    private static boolean hogwartsmagic$hasLevicorpus(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        var levicorpus = ExampleMod.LEVICORPUS_EFFECT.get();
        for (MobEffectInstance inst : entity.getActiveEffects()) {
            if (inst.getEffect().value() == levicorpus) {
                return true;
            }
        }
        return false;
    }

    @Inject(
            method = "isEntityUpsideDown(Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void hogwartsmagic$levicorpusAsUpsideDown(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (hogwartsmagic$hasLevicorpus(entity)) {
            cir.setReturnValue(true);
        }
    }
}
