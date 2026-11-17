package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.vfx.SpellVfxOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tenebris flight as the flyer sees it, in first person: the edges of the view close in with dark smoke that
 * breathes, and wisps of black smoke stream past the camera against the direction of flight. Visual only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class TenebrisView {

    private static final net.minecraft.resources.ResourceLocation VIGNETTE =
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/misc/vignette.png");

    /** Eased 0..1 strength of the effect, so it closes in and opens out instead of snapping. */
    private static float strength;
    private static float strengthO;

    private TenebrisView() {
    }

    private static boolean flying(LocalPlayer player) {
        return player != null && player.hasEffect(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) {
            return;
        }
        LocalPlayer player = minecraft.player;
        strengthO = strength;
        strength = Mth.clamp(strength + (flying(player) ? 0.15F : -0.1F), 0.0F, 1.0F);
        if (strength <= 0.0F || player == null || !minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        // Smoke streaming past: puffs a little ahead and around the eye, drifting back along the flight.
        RandomSource random = player.getRandom();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 motion = player.getDeltaMovement();
        int count = random.nextFloat() < 0.5F * strength ? 1 : 0;
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = 1.0 + random.nextDouble() * 0.8; // around the edge of the view, not over the middle
            Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
            Vec3 up = side.cross(look).normalize();
            Vec3 at = eye.add(look.scale(1.0 + random.nextDouble() * 1.0))
                    .add(side.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r * 0.7));
            Vec3 v = motion.scale(-0.35).add(look.scale(-0.04));
            minecraft.level.addParticle(new SpellVfxOptions(SpellVfxOptions.Kind.SMOKE, 0x0B0910,
                            0.25F + random.nextFloat() * 0.25F, 10 + random.nextInt(6)),
                    at.x, at.y, at.z, v.x, v.y, v.z);
        }
    }

    /**
     * The dark edge of the view closing in, breathing slowly. Drawn under the HUD.
     */
    public static void render(GuiGraphics g, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        float k = Mth.lerp(partialTick, strengthO, strength);
        if (k <= 0.01F || minecraft.player == null || !minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        // The game's own round vignette, drawn with a darkening blend (as Gui.renderVignette does), twice
        // over for a heavy edge, breathing slowly.
        float time = minecraft.player.tickCount + partialTick;
        float dark = Mth.clamp(k * (0.85F + 0.15F * Mth.sin(time * 0.15F)), 0.0F, 1.0F);
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.blendFuncSeparate(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ZERO,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ZERO);
        g.setColor(dark, dark, dark * 0.9F, 1.0F);
        for (int i = 0; i < 2; i++) {
            g.blit(VIGNETTE, 0, 0, -90, 0.0F, 0.0F, g.guiWidth(), g.guiHeight(), g.guiWidth(), g.guiHeight());
        }
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }
}
