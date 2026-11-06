package com.hogwartsmagic.event;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.hogwartsmagic.ExampleMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientForgeEvents {

    /** A server action-bar line pauses the wand cooldown line so the player can read it. */
    @SubscribeEvent
    public static void onSystemChat(net.minecraftforge.client.event.ClientChatReceivedEvent.System event) {
        if (event.isOverlay() && !event.isCanceled()) {
            com.hogwartsmagic.client.ModEventsClientHooks.onServerActionBar();
        }
    }

    @SubscribeEvent
    public static void onClientLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        com.hogwartsmagic.client.ModEventsClientHooks.clearServerActionBar();
    }

    private static boolean hasLevicorpus(LivingEntity entity) {
        var levicorpus = ExampleMod.LEVICORPUS_EFFECT.get();
        for (MobEffectInstance inst : entity.getActiveEffects()) {
            if (inst.getEffect().value() == levicorpus) {
                return true;
            }
        }
        return false;
    }

    /** When each visible Petrificus topple started on this client, so the fall plays over a few ticks. */
    private static final java.util.WeakHashMap<LivingEntity, Long> TOPPLE_START = new java.util.WeakHashMap<>();
    private static final float TOPPLE_TICKS = 5.0F;

    /** Petrificus Totalus (synced by VisibleEffects). Levicorpus wins if both are on: it has its own pose. */
    private static boolean isToppled(LivingEntity entity) {
        return !hasLevicorpus(entity) && entity.hasEffect(ExampleMod.PETRIFIED.getHolder().get());
    }

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        boolean levicorpus = hasLevicorpus(entity);
        boolean petrified = entity.hasEffect(ExampleMod.BASILISK_PETRIFICATION.getHolder().get());

        if (levicorpus) {
            event.getPoseStack().pushPose();
            event.getPoseStack().translate(0.0D, entity.getBbHeight() + 0.1D, 0.0D);
            event.getPoseStack().mulPose(Axis.ZP.rotationDegrees(180.0F));
        }

        if (isToppled(entity)) {
            long now = entity.level().getGameTime();
            net.minecraft.world.effect.MobEffectInstance marker = entity.getEffect(ExampleMod.PETRIFIED.getHolder().get());
            Long recorded = TOPPLE_START.get(entity);
            // An entry older than the whole lock is left over from an earlier topple that ended off-screen.
            long maxAge = marker == null ? 0L : marker.getDuration() + 400L;
            if (recorded == null || now < recorded || now - recorded > maxAge) {
                recorded = now;
                TOPPLE_START.put(entity, recorded);
            }
            long start = recorded;
            float progress = Math.min(1.0F, (now - start + event.getPartialTick()) / TOPPLE_TICKS);
            float yaw = net.minecraft.util.Mth.rotLerp(event.getPartialTick(), entity.yBodyRotO, entity.yBodyRot)
                    * net.minecraft.util.Mth.DEG_TO_RAD;
            com.mojang.blaze3d.vertex.PoseStack pose = event.getPoseStack();
            pose.pushPose();
            // Pivot at mid-height so the body stays centred on the entity (and inside its hitbox), tip
            // backwards about the body's own left-right axis (cos yaw, 0, sin yaw), then drop/lift so the
            // body's back rests on the floor: the pivot ends half a body depth above the ground.
            double half = entity.getBbHeight() * 0.5D;
            pose.translate(0.0D, (entity.getBbWidth() * 0.5D - half) * progress, 0.0D);
            pose.translate(0.0D, half, 0.0D);
            pose.mulPose(new org.joml.Quaternionf().rotateAxis(-net.minecraft.util.Mth.HALF_PI * progress,
                    net.minecraft.util.Mth.cos(yaw), 0.0F, net.minecraft.util.Mth.sin(yaw)));
            pose.translate(0.0D, -half, 0.0D);
        } else {
            TOPPLE_START.remove(entity);
        }

        // A Petrificus Totalus victim turns to stone the same way a basilisk's does (the tint), only
        // temporarily: the tint ends with the marker and nothing dies.
        if (petrified || isToppled(entity)) {
            RenderSystem.setShaderColor(0.42F, 0.43F, 0.45F, 1.0F);
        }
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (event.getEntity().hasEffect(ExampleMod.BASILISK_PETRIFICATION.getHolder().get()) || isToppled(event.getEntity())) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (isToppled(event.getEntity())) {
            event.getPoseStack().popPose();
        }
        if (hasLevicorpus(event.getEntity())) {
            event.getPoseStack().popPose();
        }
    }

    /**
     * How far down a Gringotts undercroft the camera is, or -1 outside one.
     *
     * <p>Checked before the biome table because the undercroft claims no biome of its own - see
     * {@link com.hogwartsmagic.client.UndercroftFogClientState} for why rewriting chunk biomes was
     * the wrong tool for a region with its own weather.
     */
    private static float undercroftDepth() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.cameraEntity == null) {
            return -1.0F;
        }
        var eye = mc.cameraEntity.getEyePosition();
        return com.hogwartsmagic.client.UndercroftFogClientState.depthAt(eye.x, eye.y, eye.z);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (com.hogwartsmagic.client.pensieve.PensieveVoid.active()) {
            com.hogwartsmagic.client.pensieve.PensieveVoid.fogColor(event);
            return;
        }
        baseFogColor(event);
        com.hogwartsmagic.client.DarkMarkSkyTint.apply(event);
    }

    private static void baseFogColor(ViewportEvent.ComputeFogColor event) {
        float depth = undercroftDepth();
        if (depth >= 0.0F) {
            event.setRed(com.hogwartsmagic.client.UndercroftFogClientState.red(depth));
            event.setGreen(com.hogwartsmagic.client.UndercroftFogClientState.green(depth));
            event.setBlue(com.hogwartsmagic.client.UndercroftFogClientState.blue(depth));
            return;
        }
        com.hogwartsmagic.client.BiomeFogClient.fogColor(event);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (com.hogwartsmagic.client.pensieve.PensieveVoid.active()) {
            com.hogwartsmagic.client.pensieve.PensieveVoid.renderFog(event);
            return;
        }
        float depth = undercroftDepth();
        if (depth >= 0.0F) {
            event.setNearPlaneDistance(
                    com.hogwartsmagic.client.UndercroftFogClientState.near());
            event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(),
                    com.hogwartsmagic.client.UndercroftFogClientState.far(depth)));
            event.setCanceled(true);
            return;
        }
        if (com.hogwartsmagic.client.BiomeFogClient.renderFog(event)) {
            event.setCanceled(true);
        }
    }
}
