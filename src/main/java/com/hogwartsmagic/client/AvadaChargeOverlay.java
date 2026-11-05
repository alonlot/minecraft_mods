package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.mind.AvadaChargeFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * What the Killing Curse does to the view. The caster's world closes in: the edges go black, a faint green
 * heartbeat throbs at the rim, quickening as the curse gathers, and the view tightens a little, as if leaning
 * into the target; on the cast it all snaps back. Anyone standing near a caster feels a fainter version of the
 * dark, so a windup across the room is felt before it is seen, and anyone near where the curse strikes sees the
 * edges of their view flash green.
 *
 * <p>Drawn from the HUD mixin (Forge 51 has no HUD event); everything here comes from {@link AvadaChargeClientState}.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class AvadaChargeOverlay {
    private static final double NEARBY_RANGE = 24.0;
    private static final float NEARBY_STRENGTH = 0.4f;
    private static final int STRIPS = 24;
    /** How far the view tightens at full charge. */
    private static final float FOV_SQUEEZE = 0.10f;

    private AvadaChargeOverlay() {
    }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if ((AvadaChargeClientState.isEmpty() && SpellBeamClientState.isEmpty()) || minecraft.player == null
                || minecraft.options.hideGui) {
            return;
        }
        float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        double now = AvadaChargeClientState.now(partial);
        float dark = 0.0f;
        float beat = 0.0f;
        float flash = 0.0f;
        // A heavy spell striking close by flashes the edges of everyone's view in its own colour.
        float[] strike = SpellBeamClientState.screenFlash(minecraft.gameRenderer.getMainCamera().getPosition(), now);
        for (AvadaChargeClientState.Charge charge : AvadaChargeClientState.all()) {
            float strength = strength(charge, now);
            if (strength <= 0.0f) {
                continue;
            }
            if (charge.entityId == minecraft.player.getId()) {
                dark = Math.max(dark, strength);
                beat = Math.max(beat, heartbeat(charge, now) * strength);
                if (charge.ended() && charge.endPhase() == AvadaChargeFx.FIRED) {
                    flash = Math.max(flash, 1.0f - charge.aftermath(now));
                }
                continue;
            }
            Entity caster = minecraft.level == null ? null : minecraft.level.getEntity(charge.entityId);
            if (caster == null || caster.isInvisibleTo(minecraft.player)) {
                continue;
            }
            double distance = caster.distanceTo(minecraft.player);
            if (distance < NEARBY_RANGE) {
                dark = Math.max(dark, NEARBY_STRENGTH * strength * (float) (1.0 - distance / NEARBY_RANGE));
            }
        }
        if (dark <= 0.003f && flash <= 0.003f && strike[0] <= 0.003f) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        // A dim over everything, then black closing in from the edges.
        graphics.fill(0, 0, width, height, argb(0.30f * dark, 0x000000));
        float depth = 0.20f + 0.16f * dark;
        edges(graphics, width, height, depth, 0.85f * dark, 0x010402);
        // The green heartbeat at the very rim, and on the cast a green flash that drains out.
        float rim = Math.max(0.28f * beat, 0.45f * flash * flash);
        if (rim > 0.003f) {
            edges(graphics, width, height, 0.07f + 0.05f * flash, rim, 0x2BFF5A);
        }
        if (strike[0] > 0.003f) {
            int rgb = (Math.round(strike[1] * 255.0f) << 16) | (Math.round(strike[2] * 255.0f) << 8)
                    | Math.round(strike[3] * 255.0f);
            edges(graphics, width, height, 0.12f, 0.45f * strike[0] * strike[0], rgb);
        }
    }

    /** How strongly this charge darkens the view: builds with the charge, lets go when it ends. */
    private static float strength(AvadaChargeClientState.Charge charge, double now) {
        float c = AvadaChargePose.ease(charge.charge(now));
        if (!charge.ended()) {
            return c;
        }
        float after = charge.aftermath(now);
        // The cast lets go at once; a broken curse leaves you in the dark a moment longer.
        float release = charge.endPhase() == AvadaChargeFx.FIRED ? 1.0f - AvadaChargePose.smoothstep(0.0f, 0.4f, after)
                : 1.0f - after;
        return c * release;
    }

    /** A double thump, quickening from about one beat a second to two and a half as the curse gathers. */
    private static float heartbeat(AvadaChargeClientState.Charge charge, double now) {
        if (charge.ended()) {
            return 0.0f;
        }
        // Beats so far: the integral of a rate ramping from 1/20 to 2.5/20 a tick over the windup.
        double elapsed = Math.min(charge.elapsed(now), charge.duration);
        double beats = elapsed / 20.0 + 1.5 / 20.0 * elapsed * elapsed / (2.0 * charge.duration);
        float phase = (float) (beats - Math.floor(beats));
        float first = (float) Math.exp(-Math.pow((phase - 0.08) / 0.05, 2.0));
        float second = 0.6f * (float) Math.exp(-Math.pow((phase - 0.30) / 0.06, 2.0));
        return first + second;
    }

    /**
     * Four soft bands from the screen edges inward: {@code depth} of the shorter side deep, {@code alpha} at the
     * edge, nothing at the inner end. The corners overlap, which is what a vignette's corners should do.
     */
    private static void edges(GuiGraphics graphics, int width, int height, float depth, float alpha, int rgb) {
        int band = Math.max(1, Math.round(Math.min(width, height) * depth));
        int outer = argb(alpha, rgb);
        int clear = argb(0.0f, rgb);
        graphics.fillGradient(0, 0, width, band, outer, clear);
        graphics.fillGradient(0, height - band, width, height, clear, outer);
        for (int i = 0; i < STRIPS; i++) {
            float f = 1.0f - i / (float) STRIPS;
            int colour = argb(alpha * f * f, rgb);
            int x0 = band * i / STRIPS;
            int x1 = band * (i + 1) / STRIPS;
            graphics.fill(x0, 0, x1, height, colour);
            graphics.fill(width - x1, 0, width - x0, height, colour);
        }
    }

    private static int argb(float alpha, int rgb) {
        return (Mth.clamp(Math.round(alpha * 255.0f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        Player player = event.getPlayer();
        if (AvadaChargeClientState.isEmpty() || player != Minecraft.getInstance().player) {
            return;
        }
        AvadaChargeClientState.Charge charge = AvadaChargeClientState.get(player.getId());
        if (charge == null) {
            return;
        }
        double now = AvadaChargeClientState.now(0.0f);
        float squeeze = FOV_SQUEEZE * strength(charge, now);
        event.setNewFovModifier(event.getNewFovModifier() * (1.0f - squeeze));
    }

    /**
     * The cast kicks the view out past normal for a moment. Per frame (ComputeFov): through the FOV modifier,
     * which vanilla eases halfway each tick, the kick smeared into a slow drift.
     */
    @SubscribeEvent
    public static void onViewportFov(net.minecraftforge.client.event.ViewportEvent.ComputeFov event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (AvadaChargeClientState.isEmpty() || minecraft.player == null || !event.usedConfiguredFov()) {
            return;
        }
        AvadaChargeClientState.Charge charge = AvadaChargeClientState.get(minecraft.player.getId());
        if (charge == null || !charge.ended() || charge.endPhase() != AvadaChargeFx.FIRED) {
            return;
        }
        float since = (float) charge.sinceEnd(AvadaChargeClientState.now((float) event.getPartialTick()));
        float kick = since < 1.0f ? since : Math.max(0.0f, 1.0f - (since - 1.0f) / 6.0f);
        event.setFOV(event.getFOV() * (1.0 + 0.12 * kick));
    }
}
