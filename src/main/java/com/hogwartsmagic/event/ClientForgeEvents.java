package com.hogwartsmagic.event;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.BroomEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientForgeEvents {

    private static final ResourceKey<Biome> FORBIDDEN_FOREST = ResourceKey.create(
            Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "forbidden_forest"));

    private static boolean hasLevicorpus(LivingEntity entity) {
        var levicorpus = ExampleMod.LEVICORPUS_EFFECT.get();
        for (MobEffectInstance inst : entity.getActiveEffects()) {
            if (inst.getEffect().value() == levicorpus) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        if (event.getPlayer().getVehicle() instanceof BroomEntity broom) {
            // Calculate FOV increase based on broom velocity
            double speed = broom.getDeltaMovement().horizontalDistance();
            
            // The broom can go up to 4.5. We stretch FOV up to 30% wider at max speed.
            float fovBoost = (float)(speed * 0.08); 
            
            // Multiply the current base FOV modifier by our boost
            event.setNewFovModifier(event.getFovModifier() * (1.0f + fovBoost));
        }
    }

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.getVehicle() instanceof BroomEntity broom) {
            // Apply a slight camera roll (tilt) when making sharp horizontal turns
            float yawDiff = broom.getYRot() - broom.yRotO;
            
            // Normalize the yaw diff to deal with 360 degree wrapping
            yawDiff = Mth.wrapDegrees(yawDiff);
            
            // Calculate speed to scale the tilt effect
            double speed = broom.getDeltaMovement().horizontalDistance();
            float speedScale = (float)Math.min(speed / 3.0, 1.5); // Max out scale at high speed
            
            // Invert yawDiff and scale by speed so camera leans INTO the turn like a motorcycle
            float targetRoll = -yawDiff * 1.5f * speedScale; 
            
            // Also bank the camera when strafing sideways
            float strafe = Minecraft.getInstance().player.xxa;
            targetRoll += strafe * 10.0f * speedScale;
            
            // Clamp the roll so it doesn't get completely chaotic
            float clampedRoll = Mth.clamp(targetRoll, -30f, 30f);
            
            event.setRoll(clampedRoll);
        }
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

        if (petrified) {
            RenderSystem.setShaderColor(0.42F, 0.43F, 0.45F, 1.0F);
        }
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (event.getEntity().hasEffect(ExampleMod.BASILISK_PETRIFICATION.getHolder().get())) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (hasLevicorpus(event.getEntity())) {
            event.getPoseStack().popPose();
        }
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.cameraEntity == null) {
            return;
        }

        if (!mc.level.getBiome(mc.cameraEntity.blockPosition()).is(FORBIDDEN_FOREST)) {
            return;
        }

        event.setRed(0.07F);
        event.setGreen(0.09F);
        event.setBlue(0.08F);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.cameraEntity == null) {
            return;
        }

        if (!mc.level.getBiome(mc.cameraEntity.blockPosition()).is(FORBIDDEN_FOREST)) {
            return;
        }

        event.setNearPlaneDistance(4.0F);
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), 44.0F));
        event.setCanceled(true);
    }
}
