package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ExampleMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client event wiring for {@link MemoryActors}. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class MemoryActorEvents {
    private MemoryActorEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) MemoryActors.tick();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        // Identity, deliberately: the camera rotation is already on RenderSystem's modelview stack, so the
        // event's own PoseStack would apply it a second time. MemoryActors translates by (actor - camera).
        MemoryActors.renderAll(new PoseStack(), Minecraft.getInstance().renderBuffers().bufferSource(),
                event.getCamera(), event.getPartialTick());
    }

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        MemoryActors.onNameTag(event);
    }
}
