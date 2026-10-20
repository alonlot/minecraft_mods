package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.pensieve.PensieveCamera;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.Future;

/**
 * A Pensieve cut is a hard cut. Vanilla rebuilds the visible-section search on a background thread once the camera
 * has moved, and keeps drawing from the old position's list until it is done: after a jump across the set that is
 * a frame or two of the new shot with whole sections missing (the void showing through). On the frame the
 * directed camera jumps, the search is run to completion before culling instead. Never for the player's own
 * camera. Client-list mixin: never applied on a dedicated server.
 */
@Mixin(SectionOcclusionGraph.class)
public abstract class PensieveOcclusionGraphMixin {
    @Unique
    private static final Logger hogwartsmagic$LOGGER = LogUtils.getLogger();

    @Shadow
    @Nullable
    private Future<?> fullUpdateTask;

    @Shadow
    private void scheduleFullUpdate(boolean smartCull, Camera camera, Vec3 cameraPos) {
        throw new AssertionError();
    }

    @Inject(method = "update(ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Ljava/util/List;)V",
            at = @At("HEAD"))
    private void hogwartsmagic$searchNowAfterACut(boolean smartCull, Camera camera, Frustum frustum,
                                                 List<SectionRenderDispatcher.RenderSection> visible,
                                                 CallbackInfo ci) {
        if (!PensieveCamera.consumeJump()) return;
        try {
            // One from the old shot may still be running: let it land first, so the new one is the last word.
            if (fullUpdateTask != null) fullUpdateTask.get();
            scheduleFullUpdate(smartCull, camera, camera.getPosition());
            if (fullUpdateTask != null) fullUpdateTask.get();
        } catch (Exception e) {
            hogwartsmagic$LOGGER.warn("Pensieve: the visible-section search after a cut failed", e);
        }
    }
}
