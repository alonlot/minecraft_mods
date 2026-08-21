package com.hogwartsmagic.event;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.BroomEntity;
import com.hogwartsmagic.worldgen.ModBiomes;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
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

    /**
     * Per-biome fog overrides, applied only while the camera is actually inside the biome.
     *
     * <p>The biome JSON's own {@code fog_color} tints distant fog but cannot close the view down â€”
     * that is a render-distance decision, so it has to happen here. Each entry is
     * {@code (colour, near plane, far plane)}; a far plane below the player's render distance is what
     * makes a place feel enclosed.
     */
    private record BiomeFog(ResourceKey<Biome> biome, float red, float green, float blue,
                            float near, float far) {}

    private static final BiomeFog[] BIOME_FOG = {
            // Old, close, and unfriendly: you can barely see the next tree.
            new BiomeFog(ModBiomes.FORBIDDEN_FOREST, 0.07F, 0.09F, 0.08F, 4.0F, 44.0F),
            // Cold grey murk sitting on the water. Tighter than the forest -- the fen is meant to feel
            // like being lost, and it hides the Dementors until they are already close.
            new BiomeFog(ModBiomes.DEMENTOR_FEN, 0.29F, 0.32F, 0.31F, 1.0F, 30.0F),
            // Volcanic haze. Kept far back so the crags still read as vast; it is atmosphere, not a wall.
            new BiomeFog(ModBiomes.HORNTAIL_PEAKS, 0.42F, 0.29F, 0.20F, 12.0F, 128.0F),
            // Sickly green, and the tightest of the lot: in the Chamber you see the next pillar and
            // not the thing behind it.
            new BiomeFog(ModBiomes.CHAMBER_DEPTHS, 0.05F, 0.11F, 0.07F, 0.5F, 24.0F),
            // Forge-lit soot. Loose enough to still read the shape of a vault.
            new BiomeFog(ModBiomes.GOBLIN_UNDERCITY, 0.20F, 0.13F, 0.06F, 2.0F, 40.0F),
    };

    /** The fog entry for the biome the camera is standing in, or null outside all of them. */
    private static BiomeFog currentBiomeFog() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.cameraEntity == null) {
            return null;
        }
        var biome = mc.level.getBiome(mc.cameraEntity.blockPosition());
        for (BiomeFog fog : BIOME_FOG) {
            if (biome.is(fog.biome())) {
                return fog;
            }
        }
        return null;
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
        float depth = undercroftDepth();
        if (depth >= 0.0F) {
            event.setRed(com.hogwartsmagic.client.UndercroftFogClientState.red(depth));
            event.setGreen(com.hogwartsmagic.client.UndercroftFogClientState.green(depth));
            event.setBlue(com.hogwartsmagic.client.UndercroftFogClientState.blue(depth));
            return;
        }
        BiomeFog fog = currentBiomeFog();
        if (fog == null) {
            return;
        }

        event.setRed(fog.red());
        event.setGreen(fog.green());
        event.setBlue(fog.blue());
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        float depth = undercroftDepth();
        if (depth >= 0.0F) {
            event.setNearPlaneDistance(
                    com.hogwartsmagic.client.UndercroftFogClientState.near());
            event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(),
                    com.hogwartsmagic.client.UndercroftFogClientState.far(depth)));
            event.setCanceled(true);
            return;
        }
        BiomeFog fog = currentBiomeFog();
        if (fog == null) {
            return;
        }

        event.setNearPlaneDistance(fog.near());
        // min() so a player on a low render distance never has the fog pushed *further* out than
        // vanilla would have drawn it.
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), fog.far()));
        event.setCanceled(true);
    }
}
