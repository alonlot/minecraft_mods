package com.hogwartsmagic.client.particle;

import com.hogwartsmagic.ExampleMod;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The glowing particles ({@link MagicParticleSet}) draw with an additive blend set in their render type's
 * {@code begin}, and 1.21's ParticleRenderType has no end hook to put it back. The particle engine only disables
 * blending when it is done, so the additive function would leak into whatever enables blending next without setting
 * its own. Right after the particles are drawn, put the default blend function back.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class ParticleBlendReset {

    private ParticleBlendReset() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            RenderSystem.defaultBlendFunc();
        }
    }
}
