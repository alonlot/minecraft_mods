package com.hogwartsmagic.mixin.client;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The render sections and their mesher, so a memory can have its set drawn ahead of a cut (see SetWarmup). */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @Accessor("viewArea")
    ViewArea hogwartsmagic$viewArea();

    @Accessor("sectionRenderDispatcher")
    SectionRenderDispatcher hogwartsmagic$sectionRenderDispatcher();
}
