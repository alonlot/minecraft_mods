package com.hogwartsmagic.mixin;

import com.hogwartsmagic.spell.mind.MindControl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Collection;

/**
 * A creeper that blows up copies every active effect into a lingering cloud. An Imperio thrall (or a
 * possessed / rooted / feral creeper) would splash its hidden control marker onto whatever stands in the
 * cloud, so the copy is filtered first; with nothing left the empty-check skips the cloud entirely.
 *
 * <p>Common mixin: spawnLingeringCloud only runs on the server, which is where the thrall map lives.
 */
@Mixin(Creeper.class)
public abstract class CreeperLingeringCloudMixin {

    @ModifyVariable(method = "spawnLingeringCloud", at = @At("STORE"), ordinal = 0)
    private Collection<MobEffectInstance> hogwartsmagic$keepControlMarkers(Collection<MobEffectInstance> effects) {
        return MindControl.withoutBodyBoundEffects((LivingEntity) (Object) this, effects);
    }
}
