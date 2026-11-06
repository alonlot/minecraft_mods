package com.hogwartsmagic.client.fx.intro;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.MusicStingClient;
import com.hogwartsmagic.client.fx.CameraShake;
import com.hogwartsmagic.client.fx.Cinematic;
import com.hogwartsmagic.client.fx.FxClient;
import com.hogwartsmagic.client.fx.FxOverlays;
import com.hogwartsmagic.fx.BossBeasts;
import com.hogwartsmagic.fx.FxConfig;
import com.hogwartsmagic.fx.FxKinds;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.MusicStings;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Locale;

/**
 * The first time you ever see one of the great beasts, the moment is marked like the first shot of a film: black
 * bars slide in, the view pushes slowly in towards the beast over about two seconds while the edges of the screen
 * darken, a sting plays (and a low rumble for the dragons and the Giant), and under the bars the beast's name
 * appears in large spaced capitals over a thin rule and a short line about it, fading in and out over three and a
 * half seconds. The server decides when ({@code fx.BossIntroDetector}); this only plays it. Off with
 * {@code bossIntros}.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class BossIntroClient {
    /** The whole moment, in ticks (3.5 s). The letterbox is held for the same. */
    private static final int DURATION = 70;
    /** The push-in builds over this many ticks, then lets go over the rest. */
    private static final int PUSH_TICKS = 40;
    /** How much the view narrows at the deepest point of the push-in. */
    private static final float PUSH_IN = 0.16f;

    private static boolean active;
    private static long startTick;
    private static int beast = -1;

    private BossIntroClient() {
    }

    /** The server says: this is your first look at this beast. */
    static void start(FxClient.FxEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        int index = Math.round(event.param(0, -1.0f));
        if (!FxConfig.get(FxConfig.BOSS_INTROS) || minecraft.level == null || BossBeasts.id(index) == null
                || (active && elapsed(0.0f) < DURATION)) {
            return;
        }
        active = true;
        beast = index;
        startTick = minecraft.level.getGameTime();

        Cinematic.letterbox(DURATION);
        if (BossBeasts.isDragon(index)) {
            MusicStingClient.accept(new SpellPackets.MusicStingPacket(MusicStings.BOSS_DRAGON));
        } else if (index == BossBeasts.BASILISK || index == BossBeasts.ACROMANTULA) {
            MusicStingClient.accept(new SpellPackets.MusicStingPacket(MusicStings.BOSS_SERPENT));
        } else if (index == BossBeasts.VOLDEMORT) {
            MusicStingClient.accept(new SpellPackets.MusicStingPacket(MusicStings.BOSS_DARK));
        }
        // A low, slow rumble: the earth taking notice of the dragons and the Giant.
        if (BossBeasts.isDragon(index)) {
            CameraShake.addLocal(0.7f, 60.0f, 0.45f);
        } else if (index == BossBeasts.GIANT) {
            CameraShake.addLocal(0.9f, 60.0f, 0.4f);
        }
    }

    /** Ticks since it began (with the frame's partial tick), or a huge number when nothing is playing. */
    private static float elapsed(float partial) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active || minecraft.level == null) {
            return Float.MAX_VALUE;
        }
        return (minecraft.level.getGameTime() - startTick) + partial;
    }

    @SubscribeEvent
    public static void onViewportFov(ViewportEvent.ComputeFov event) {
        if (!active || !event.usedConfiguredFov()) {
            return;
        }
        float t = elapsed((float) event.getPartialTick());
        if (t >= DURATION) {
            return;
        }
        // Eases in over the push, then lets go.
        float push = t < PUSH_TICKS ? smoothstep(0.0f, PUSH_TICKS, t) : 1.0f - smoothstep(PUSH_TICKS, DURATION - 4, t);
        event.setFOV(event.getFOV() * (1.0 - PUSH_IN * push));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
        beast = -1;
    }

    @Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            FxClient.register(FxKinds.BOSS_INTRO, BossIntroClient::start);
            // Just under the letterbox bars: the vignette first, the card over it.
            FxOverlays.register(FxOverlays.ORDER_LETTERBOX - 20, "boss_intro_vignette", BossIntroClient::renderVignette);
            FxOverlays.register(FxOverlays.ORDER_LETTERBOX - 10, "boss_intro_card", BossIntroClient::renderCard);
        }
    }

    // ---- the dark edges ----------------------------------------------------------------------------------

    private static void renderVignette(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active || minecraft.options.hideGui) {
            return;
        }
        float t = elapsed(delta.getGameTimeDeltaPartialTick(false));
        if (t >= DURATION) {
            active = false;
            return;
        }
        float envelope = smoothstep(0.0f, 16.0f, t) * (1.0f - smoothstep(DURATION - 22, DURATION, t));
        if (envelope <= 0.01f) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        graphics.fill(0, 0, width, height, argb(0.14f * envelope, 0x000000));
        int band = Math.max(1, Math.round(Math.min(width, height) * 0.30f));
        int outer = argb(0.80f * envelope, 0x000000);
        int clear = argb(0.0f, 0x000000);
        graphics.fillGradient(0, 0, width, band, outer, clear);
        graphics.fillGradient(0, height - band, width, height, clear, outer);
        int strips = 24;
        for (int i = 0; i < strips; i++) {
            float f = 1.0f - i / (float) strips;
            int colour = argb(0.80f * envelope * f * f, 0x000000);
            int x0 = band * i / strips;
            int x1 = band * (i + 1) / strips;
            graphics.fill(x0, 0, x1, height, colour);
            graphics.fill(width - x1, 0, width - x0, height, colour);
        }
    }

    // ---- the title card ----------------------------------------------------------------------------------

    private static void renderCard(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        String id = BossBeasts.id(beast);
        if (!active || id == null || minecraft.options.hideGui) {
            return;
        }
        float t = elapsed(delta.getGameTimeDeltaPartialTick(false));
        if (t >= DURATION) {
            return;
        }
        float fade = smoothstep(6.0f, 26.0f, t) * (1.0f - smoothstep(DURATION - 18, DURATION, t));
        if (fade <= 0.03f) {
            return;
        }
        Font font = minecraft.font;
        String name = Component.translatable("hogwartsmagic.boss_intro." + id + ".name").getString()
                .toUpperCase(Locale.ROOT);
        String sub = Component.translatable("hogwartsmagic.boss_intro." + id + ".sub").getString();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        // The letters start spread wide and draw together as the card settles.
        float settle = smoothstep(6.0f, 50.0f, t);
        float spacingEm = 0.9f - 0.45f * settle;
        float scale = 3.0f;
        float total = spacedWidth(font, name, scale, spacingEm);
        float limit = width * 0.82f;
        if (total > limit) {
            scale *= limit / total;
            total = limit;
        }
        float y = height * 0.72f;
        float x = (width - total) * 0.5f;

        int nameColour = argb(fade, 0xF2EBDD);
        drawSpaced(graphics, font, name, x, y, scale, spacingEm, nameColour);

        // The rule draws out from the middle under the name.
        float ruleY = y + 9.0f * scale + 6.0f;
        float ruleHalf = total * 0.5f * smoothstep(10.0f, 40.0f, t);
        int ruleColour = argb(0.85f * fade, 0xC9B27A);
        graphics.fill(Math.round(width * 0.5f - ruleHalf), Math.round(ruleY), Math.round(width * 0.5f + ruleHalf),
                Math.round(ruleY) + 1, ruleColour);

        // The line about it comes in a beat later.
        float subFade = fade * smoothstep(22.0f, 40.0f, t);
        if (subFade > 0.03f) {
            drawSpaced(graphics, font, sub, (width - spacedWidth(font, sub, 1.25f, 0.18f)) * 0.5f, ruleY + 9.0f,
                    1.25f, 0.18f, argb(subFade, 0xC9C2B0));
        }
    }

    private static float spacedWidth(Font font, String text, float scale, float spacingEm) {
        float w = 0.0f;
        for (int i = 0; i < text.length(); i++) {
            w += font.width(String.valueOf(text.charAt(i))) * scale;
            if (i < text.length() - 1) {
                w += spacingEm * 8.0f * scale;
            }
        }
        return w;
    }

    private static void drawSpaced(GuiGraphics graphics, Font font, String text, float x, float y, float scale,
                                   float spacingEm, int colour) {
        if ((colour >>> 24) < 8) {
            return;
        }
        float cursor = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            graphics.pose().pushPose();
            graphics.pose().translate(cursor, y, 0.0f);
            graphics.pose().scale(scale, scale, 1.0f);
            graphics.drawString(font, ch, 0, 0, colour, true);
            graphics.pose().popPose();
            cursor += font.width(ch) * scale + spacingEm * 8.0f * scale;
        }
    }

    private static int argb(float alpha, int rgb) {
        return (Mth.clamp(Math.round(alpha * 255.0f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
