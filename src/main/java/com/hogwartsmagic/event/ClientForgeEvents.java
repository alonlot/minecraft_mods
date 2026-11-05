package com.hogwartsmagic.event;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.worldgen.ModBiomes;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.level.biome.Biome;

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

    /**
     * Per-biome fog overrides, applied only while the camera is actually inside the biome.
     *
     * <p>The biome JSON's own {@code fog_color} tints distant fog but cannot close the view down —
     * that is a render-distance decision, so it has to happen here. Each entry is
     * {@code (colour, near plane, far plane)}; a far plane below the player's render distance is what
     * makes a place feel enclosed.
     */
    private record BiomeFog(ResourceKey<Biome> biome, float red, float green, float blue,
                            float near, float far) {}

    private static final BiomeFog[] BIOME_FOG = {
            // Old and unfriendly, but you can see a little way down the aisles of trunks now --
            // enough to read the shape of the forest and spot what is moving in it.
            new BiomeFog(ModBiomes.FORBIDDEN_FOREST, 0.07F, 0.09F, 0.08F, 12.0F, 80.0F),
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
