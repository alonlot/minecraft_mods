package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.DiagonAlleyClientState;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While you are in Diagon Alley, the rest of the world is not drawn.
 *
 * <p>The alley is a street in London. It should not have the hillside you built the wall against
 * looming over the rooftops, or the lake you happened to be standing next to visible past the end
 * of it — those are features of wherever the player was, and the whole point of walking through the
 * arch is to have left there. With the sky shaft cleared for real there is now open sky overhead,
 * which made the surrounding terrain more conspicuous rather than less: it used to be hidden behind
 * a mountain, and now it is silhouetted against daylight.
 *
 * <h2>Drawn, not deleted</h2>
 *
 * <p>This drops sections from the renderer's visible list and touches nothing else. The blocks are
 * still there, still collide, still tick, still light. Nobody's world is altered by looking away
 * from it, and the moment the alley closes the occlusion graph puts every section straight back —
 * a section dropped while dirty is still dirty when it returns, so it rebuilds itself then.
 *
 * <p>{@code setupRender} is the hook rather than {@code applyFrustum}, because the frustum is only
 * reapplied when the camera has turned far enough to be worth it; {@code setupRender} runs every
 * frame and leaves {@code visibleSections} in the state the rest of the frame will use.
 *
 * <p>A section is kept if its cube touches the region at all, so the sections along the boundary —
 * the ones holding the alley's own perimeter wall — are drawn whole. That can leave a sliver of
 * real ground beside the wall. Cutting those would take the wall with them, which is worse.
 */
@Mixin(LevelRenderer.class)
public abstract class DiagonAlleyLevelRendererMixin {

    @Shadow
    @Final
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

    @Inject(method = "setupRender", at = @At("RETURN"))
    private void hogwartsmagic$hideTheWorldOutsideTheAlley(Camera camera, Frustum frustum,
                                                           boolean capturedFrustum,
                                                           boolean spectator, CallbackInfo ci) {
        if (!DiagonAlleyClientState.cameraInside()) {
            return;
        }
        this.visibleSections.removeIf(
                section -> !DiagonAlleyClientState.sectionShown(section.getOrigin()));
    }
}
