package com.hogwartsmagic.client.fx;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.ModRenderTypes;
import com.hogwartsmagic.client.SpellFx;
import com.hogwartsmagic.client.SpellLight;
import com.hogwartsmagic.fx.FxKinds;
import com.hogwartsmagic.spell.vfx.SpellVfxOptions;
import com.hogwartsmagic.tomb.TombCurse;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * How the tomb curses look: hieroglyphs written in light.
 *
 * <ul>
 *   <li><b>Waking</b> ({@link FxKinds#CURSE_TRIGGER}): a circle of twelve sigils opens flat on the floor in the
 *       curse's colour and turns, a burning ring round it and a glow inside it, while sigils spiral up round the
 *       victim; the room takes the curse's colour (spell light) and the victim's view flashes.</li>
 *   <li><b>Landing</b> ({@link FxKinds#CURSE_LAND}): a flash where it lands; for the Withering, the Eye of Horus
 *       burns over the victim's head and, if the victim is you, a violet dark pulses at the edges of your view
 *       for as long as the curse lasts.</li>
 *   <li><b>Revelio's wave</b> ({@link FxKinds#CURSE_WAVE}): a red ring runs out across the floor; each hidden tile
 *       it reaches sends up its sigil ({@link FxKinds#CURSE_REVEAL}).</li>
 *   <li><b>Lifting</b> ({@link FxKinds#CURSE_LIFT}): the sigil is torn up out of the tile, swells, and breaks into
 *       gold sparks with a flash.</li>
 * </ul>
 *
 * <p>All sigils are drawn with the additive {@link ModRenderTypes#magicGlow} type, which the bloom pass makes glow.
 * Effects are capped and forgotten on logout. Client only; cosmetic.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class CurseFx {

    public static final ResourceLocation SIGILS =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/fx/curse_sigils.png");
    private static final RenderType SIGIL_TYPE = ModRenderTypes.magicGlow(SIGILS);
    /** Revelio red: the wave and the walls it opens. */
    public static final int REVEAL_RGB = 0xFF4A28;
    private static final int GOLD = 0xFFD36A;
    private static final int MAX_EFFECTS = 48;
    private static final double DRAW_DISTANCE = 96.0;

    private enum Type { TRIGGER, LAND, WAVE, REVEAL, LIFT }

    private static final class Effect {
        final Type type;
        final Vec3 pos;
        final int rgb;
        final int sigil;
        final int entityId;
        final float a;
        final float b;
        final long start;
        final int life;
        boolean burst;

        Effect(Type type, Vec3 pos, int rgb, int sigil, int entityId, float a, float b, long start, int life) {
            this.type = type;
            this.pos = pos;
            this.rgb = rgb;
            this.sigil = sigil;
            this.entityId = entityId;
            this.a = a;
            this.b = b;
            this.start = start;
            this.life = life;
        }
    }

    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static long clientTick;
    /** The view's own curse effects: a flash on waking (ticks left, colour) and the Withering's dark (ticks left). */
    private static int flashLeft;
    private static int flashRgb;
    private static int witherLeft;
    private static int witherTotal;

    private CurseFx() {
    }

    @Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            FxClient.register(FxKinds.CURSE_TRIGGER, e -> {
                TombCurse curse = TombCurse.byId((int) e.param(0, 0));
                int windup = (int) e.param(1, 30);
                add(new Effect(Type.TRIGGER, e.pos(), curse.rgb(), curse.sigil(), e.entityId(), windup, 0, clientTick,
                        windup + 18));
                if (isLocal(e.entityId())) {
                    flashLeft = 14;
                    flashRgb = curse.rgb();
                }
            });
            FxClient.register(FxKinds.CURSE_LAND, e -> {
                TombCurse curse = TombCurse.byId((int) e.param(0, 0));
                int lasts = (int) e.param(1, 0);
                add(new Effect(Type.LAND, e.pos(), curse.rgb(), curse.sigil(), e.entityId(), lasts, 0, clientTick,
                        curse == TombCurse.WITHERING ? 46 : 14));
                if (curse == TombCurse.WITHERING && isLocal(e.entityId())) {
                    witherLeft = witherTotal = Math.max(1, lasts);
                    flashLeft = 10;
                    flashRgb = curse.rgb();
                }
            });
            FxClient.register(FxKinds.CURSE_WAVE, e -> {
                float radius = e.param(0, 8.0f);
                float speed = Math.max(0.1f, e.param(1, 0.7f));
                Vec3 floor = floorUnder(e.pos());
                add(new Effect(Type.WAVE, floor, REVEAL_RGB, 0, -1, radius, speed, clientTick,
                        Mth.ceil(radius / speed) + 8));
            });
            FxClient.register(FxKinds.CURSE_REVEAL, e -> {
                int id = (int) e.param(0, -1);
                int rgb = id < 0 ? REVEAL_RGB : TombCurse.byId(id).rgb();
                int sigil = id < 0 ? 7 : TombCurse.byId(id).sigil();
                add(new Effect(Type.REVEAL, e.pos(), rgb, sigil, -1, 0, 0, clientTick, 28));
            });
            FxClient.register(FxKinds.CURSE_LIFT, e -> {
                TombCurse curse = TombCurse.byId((int) e.param(0, 0));
                add(new Effect(Type.LIFT, e.pos(), curse.rgb(), curse.sigil(), e.entityId(), 0, 0, clientTick, 22));
            });
            FxOverlays.register(FxOverlays.ORDER_WORLD_TINT + 5, "curse_edges", CurseFx::renderOverlay);
        }
    }

    private static boolean isLocal(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getId() == entityId;
    }

    private static void add(Effect effect) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(effect.pos) > DRAW_DISTANCE * DRAW_DISTANCE) {
            return;
        }
        while (EFFECTS.size() >= MAX_EFFECTS) {
            EFFECTS.remove(0);
        }
        EFFECTS.add(effect);
    }

    /** The top of the floor under {@code at} (within a few blocks), so a wave cast at a wall still runs on the floor. */
    private static Vec3 floorUnder(Vec3 at) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return at;
        }
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy >= -5; dy--) {
            probe.set(Mth.floor(at.x), Mth.floor(at.y) + dy, Mth.floor(at.z));
            if (level.getBlockState(probe).isFaceSturdy(level, probe, net.minecraft.core.Direction.UP)) {
                return new Vec3(at.x, probe.getY() + 1.0, at.z);
            }
        }
        return at;
    }

    // ------------------------------------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        clientTick++;
        if (flashLeft > 0) {
            flashLeft--;
        }
        if (witherLeft > 0) {
            witherLeft--;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        EFFECTS.removeIf(e -> clientTick - e.start > e.life);
        for (Effect e : EFFECTS) {
            long age = clientTick - e.start;
            if (e.type == Type.TRIGGER && age < e.a && age % 2 == 0) {
                // Sparks thrown up off the rune circle while the curse gathers.
                double ang = level.random.nextDouble() * Math.PI * 2.0;
                double r = 1.4 + level.random.nextDouble() * 0.7;
                level.addParticle(new SpellVfxOptions(SpellVfxOptions.Kind.SPARK, e.rgb, 0.06f, 14),
                        e.pos.x + Math.cos(ang) * r, e.pos.y + 0.05, e.pos.z + Math.sin(ang) * r, 0.0, 0.12, 0.0);
            }
            if (e.type == Type.LIFT && age >= 10 && !e.burst) {
                e.burst = true;
                Vec3 c = e.pos.add(0.0, 1.5, 0.0);
                for (int i = 0; i < 36; i++) {
                    double yaw = level.random.nextDouble() * Math.PI * 2.0;
                    double pitch = (level.random.nextDouble() - 0.3) * Math.PI;
                    double s = 0.12 + level.random.nextDouble() * 0.18;
                    level.addParticle(new SpellVfxOptions(i % 3 == 0 ? SpellVfxOptions.Kind.GLOW : SpellVfxOptions.Kind.SPARK,
                                    i % 4 == 0 ? e.rgb : GOLD, 0.08f, 18 + level.random.nextInt(10)),
                            c.x, c.y, c.z, Math.cos(yaw) * Math.cos(pitch) * s, Math.sin(pitch) * s,
                            Math.sin(yaw) * Math.cos(pitch) * s);
                }
            }
            if (e.type == Type.LAND && e.rgb == TombCurse.RISEN.rgb() && age < 8) {
                level.addParticle(new SpellVfxOptions(SpellVfxOptions.Kind.FLAME, e.rgb, 0.16f, 14),
                        e.pos.x + (level.random.nextDouble() - 0.5) * 0.6, e.pos.y + level.random.nextDouble() * 1.8,
                        e.pos.z + (level.random.nextDouble() - 0.5) * 0.6, 0.0, 0.05, 0.0);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        EFFECTS.clear();
        flashLeft = 0;
        witherLeft = 0;
    }

    // ------------------------------------------------------------------------------------------------ light

    /** Coloured light the curses throw on the room ({@link SpellLight} calls this every frame). */
    public static void lights(float partial, double now, SpellLight.Sink sink) {
        for (Effect e : EFFECTS) {
            float age = (float) (clientTick - e.start) + partial;
            float t = age / e.life;
            float r = red(e.rgb), g = green(e.rgb), b = blue(e.rgb);
            switch (e.type) {
                case TRIGGER -> sink.add(e.pos.add(0.0, 0.6, 0.0), r, g, b, 6.5f,
                        0.9f * fadeInOut(age, 3.0f, e.life, 12.0f));
                case LAND -> sink.add(e.pos.add(0.0, 1.2, 0.0), r, g, b, 7.0f, 1.0f * (1.0f - Math.min(1.0f, t * 1.6f)));
                case LIFT -> sink.add(e.pos.add(0.0, 1.2, 0.0), 1.0f, 0.84f, 0.45f, 6.0f,
                        age < 10 ? 0.4f + 0.05f * age : 0.9f * (1.0f - (age - 10) / 12.0f));
                case REVEAL -> sink.add(e.pos.add(0.0, 0.8, 0.0), r, g, b, 3.5f, 0.45f * (1.0f - t));
                default -> {
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ drawing

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || EFFECTS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        VertexConsumer glow = buffer.getBuffer(ModRenderTypes.SPELL_GLOW);
        for (Effect e : EFFECTS) {
            drawGlows(glow, m, e, (float) (clientTick - e.start) + partial, cam, left, up);
        }
        buffer.endBatch(ModRenderTypes.SPELL_GLOW);

        VertexConsumer ring = buffer.getBuffer(ModRenderTypes.SPELL_RING);
        for (Effect e : EFFECTS) {
            drawRings(ring, m, e, (float) (clientTick - e.start) + partial, cam);
        }
        buffer.endBatch(ModRenderTypes.SPELL_RING);

        VertexConsumer sig = buffer.getBuffer(SIGIL_TYPE);
        for (Effect e : EFFECTS) {
            drawSigils(sig, m, e, (float) (clientTick - e.start) + partial, cam, left, up);
        }
        buffer.endBatch(SIGIL_TYPE);
    }

    /** Soft glows: the disc inside the rune circle, the flashes where curses land and lift. */
    private static void drawGlows(VertexConsumer vc, Matrix4f m, Effect e, float age, Vec3 cam, Vector3f left, Vector3f up) {
        float r = red(e.rgb), g = green(e.rgb), b = blue(e.rgb);
        switch (e.type) {
            case TRIGGER -> {
                float open = ease(Math.min(1.0f, age / 8.0f));
                float alpha = 0.3f * fadeInOut(age, 3.0f, e.life, 12.0f) * (0.85f + 0.15f * Mth.sin(age * 0.6f));
                flat(vc, m, e.pos, cam, 2.0f * open, age * 0.02f, r, g, b, alpha, 0.0f, 0.0f, 1.0f, 1.0f, 0.03f);
            }
            case LAND -> {
                float t = age / 12.0f;
                if (t < 1.0f) {
                    Vec3 at = e.pos.add(0.0, 1.1, 0.0);
                    Entity who = entity(e.entityId);
                    if (who != null) {
                        at = who.getPosition(1.0f).add(0.0, who.getBbHeight() * 0.6, 0.0);
                    }
                    billboard(vc, m, at, cam, left, up, 1.2f + 1.6f * t, r, g, b, 0.9f * (1.0f - t), 0, 0, 1, 1);
                }
            }
            case LIFT -> {
                if (age >= 9.0f) {
                    float t = (age - 9.0f) / 10.0f;
                    if (t < 1.0f) {
                        billboard(vc, m, e.pos.add(0.0, 1.5, 0.0), cam, left, up, 0.8f + 2.2f * t,
                                1.0f, 0.86f, 0.5f, 1.0f - t, 0, 0, 1, 1);
                    }
                }
            }
            case REVEAL -> {
                float t = age / e.life;
                flat(vc, m, e.pos, cam, 0.7f, 0.0f, r, g, b, 0.5f * (1.0f - t), 0, 0, 1, 1, 0.025f);
            }
            default -> {
            }
        }
    }

    /** Burning rings drawn flat on the floor: round the rune circle, Revelio's wave, the pulse on a revealed tile. */
    private static void drawRings(VertexConsumer vc, Matrix4f m, Effect e, float age, Vec3 cam) {
        float r = red(e.rgb), g = green(e.rgb), b = blue(e.rgb);
        switch (e.type) {
            case TRIGGER -> {
                float open = ease(Math.min(1.0f, age / 8.0f));
                float alpha = fadeInOut(age, 2.0f, e.life, 12.0f);
                flat(vc, m, e.pos, cam, 2.35f * open, -age * 0.015f, r, g, b, alpha * 0.8f, 0, 0, 1, 1, 0.035f);
                flat(vc, m, e.pos, cam, 1.25f * open, age * 0.02f, r, g, b, alpha * 0.7f, 0, 0, 1, 1, 0.036f);
                // A shock ring that leaves the circle as it opens.
                float shock = age / 10.0f;
                if (shock < 1.0f) {
                    flat(vc, m, e.pos, cam, 0.5f + 4.0f * shock, 0.0f, r, g, b, 0.8f * (1.0f - shock), 0, 0, 1, 1, 0.04f);
                }
            }
            case WAVE -> {
                float radius = Math.min(e.a, age * e.b);
                float alpha = radius >= e.a ? Math.max(0.0f, 1.0f - (age - e.a / e.b) / 8.0f) : 1.0f - 0.4f * radius / e.a;
                flat(vc, m, e.pos, cam, radius, 0.0f, r, g, b, alpha, 0, 0, 1, 1, 0.04f);
                if (radius > 1.2f) {
                    flat(vc, m, e.pos, cam, radius - 1.0f, 0.0f, r, g, b, alpha * 0.4f, 0, 0, 1, 1, 0.041f);
                }
            }
            case REVEAL -> {
                float t = Math.min(1.0f, age / 12.0f);
                flat(vc, m, e.pos, cam, 0.2f + 0.9f * t, 0.0f, r, g, b, 1.0f - t, 0, 0, 1, 1, 0.03f);
            }
            default -> {
            }
        }
    }

    /** The hieroglyphs themselves. */
    private static void drawSigils(VertexConsumer vc, Matrix4f m, Effect e, float age, Vec3 cam, Vector3f left, Vector3f up) {
        float r = red(e.rgb), g = green(e.rgb), b = blue(e.rgb);
        switch (e.type) {
            case TRIGGER -> {
                float open = ease(Math.min(1.0f, age / 8.0f));
                float alpha = fadeInOut(age, 3.0f, e.life, 12.0f);
                // Twelve sigils round the circle, flat on the floor, turning; each its own glyph.
                float spin = age * 0.03f;
                for (int i = 0; i < 12; i++) {
                    double ang = spin + i * Math.PI * 2.0 / 12.0;
                    double cr = 1.8 * open;
                    Vec3 at = e.pos.add(Math.cos(ang) * cr, 0.0, Math.sin(ang) * cr);
                    float flick = 0.75f + 0.25f * Mth.sin(age * 0.9f + i * 1.7f);
                    int s = (e.sigil + i) % 8;
                    sigilFlat(vc, m, at, cam, 0.32f * open, (float) ang + Mth.HALF_PI, r, g, b, alpha * flick,
                            u0(s), v0(s), u0(s) + 0.25f, v0(s) + 0.5f, 0.045f);
                }
                // The curse's own sigil, large, in the middle.
                sigilFlat(vc, m, e.pos, cam, 0.7f * open, -spin * 0.5f, r, g, b, alpha,
                        u0(e.sigil), v0(e.sigil), u0(e.sigil) + 0.25f, v0(e.sigil) + 0.5f, 0.05f);
                // Sigils spiralling up round the victim while the curse gathers.
                if (age < e.a + 6.0f) {
                    Vec3 centre = e.pos;
                    Entity who = entity(e.entityId);
                    if (who != null) {
                        centre = new Vec3(who.getX(), e.pos.y, who.getZ());
                    }
                    for (int i = 0; i < 6; i++) {
                        float rise = (age * 0.07f + i * 0.45f) % 2.7f;
                        double ang = age * 0.15f + i * Math.PI / 3.0;
                        double cr = 1.1 - rise * 0.15;
                        float fade = alpha * (1.0f - rise / 2.7f) * Math.min(1.0f, rise * 3.0f);
                        int s = (e.sigil + i * 3) % 8;
                        sigilBillboard(vc, m, centre.add(Math.cos(ang) * cr, rise, Math.sin(ang) * cr), cam, left, up,
                                0.28f, r, g, b, fade, u0(s), v0(s), u0(s) + 0.25f, v0(s) + 0.5f);
                    }
                }
            }
            case LAND -> {
                if (e.rgb == TombCurse.WITHERING.rgb()) {
                    // The Eye of Horus burns over the victim's head: it slams in large, settles, holds, fades.
                    Entity who = entity(e.entityId);
                    Vec3 at = who != null ? who.getPosition(1.0f).add(0.0, who.getBbHeight() + 0.75, 0.0)
                            : e.pos.add(0.0, 2.6, 0.0);
                    float in = Math.min(1.0f, age / 6.0f);
                    float size = Mth.lerp(ease(in), 1.4f, 0.55f);
                    float alpha = in * Math.min(1.0f, (e.life - age) / 12.0f) * (0.85f + 0.15f * Mth.sin(age * 0.8f));
                    int s = TombCurse.WITHERING.sigil();
                    sigilBillboard(vc, m, at, cam, left, up, size, r, g, b, alpha, u0(s), v0(s), u0(s) + 0.25f, v0(s) + 0.5f);
                } else if (e.rgb == TombCurse.RISEN.rgb()) {
                    float t = age / e.life;
                    int s = TombCurse.RISEN.sigil();
                    sigilBillboard(vc, m, e.pos.add(0.0, 1.0 + 1.6 * t, 0.0), cam, left, up, 0.45f, r, g, b, 1.0f - t,
                            u0(s), v0(s), u0(s) + 0.25f, v0(s) + 0.5f);
                }
            }
            case REVEAL -> {
                float t = age / e.life;
                float alpha = Math.min(1.0f, age / 4.0f) * (1.0f - t * t);
                sigilBillboard(vc, m, e.pos.add(0.0, 0.25 + 1.2 * ease(t), 0.0), cam, left, up, 0.3f + 0.25f * t, r, g, b,
                        alpha, u0(e.sigil), v0(e.sigil), u0(e.sigil) + 0.25f, v0(e.sigil) + 0.5f);
            }
            case LIFT -> {
                if (age < 11.0f) {
                    // Torn up out of the tile, swelling as it comes, shaking with the strain.
                    float t = age / 10.0f;
                    float shake = 0.04f * Mth.sin(age * 3.1f);
                    sigilBillboard(vc, m, e.pos.add(shake, 0.2 + 1.3 * ease(t), 0.0), cam, left, up, 0.35f + 0.55f * t,
                            r, g, b, 1.0f, u0(e.sigil), v0(e.sigil), u0(e.sigil) + 0.25f, v0(e.sigil) + 0.5f);
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ the view

    /** The edges of your own view: a flash in the curse's colour as it wakes, and the Withering's pulsing dark. */
    private static void renderOverlay(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        if (witherLeft > 0) {
            float life = witherLeft / (float) witherTotal;
            float beat = (float) Math.pow(Math.max(0.0, Mth.sin((clientTick + partial) * 0.12f)), 6.0);
            float strength = Math.min(1.0f, witherLeft / 20.0f) * (0.55f + 0.25f * beat) * (0.6f + 0.4f * life);
            edges(graphics, 0x1A0828, TombCurse.WITHERING.rgb(), strength);
        }
        if (flashLeft > 0) {
            float s = (flashLeft - partial) / 14.0f;
            edges(graphics, flashRgb, flashRgb, Math.max(0.0f, s) * 0.8f);
        }
    }

    /** A soft frame closing in from the edges: deep colour at the very edge, the bright colour just inside it. */
    private static void edges(GuiGraphics graphics, int deep, int bright, float strength) {
        if (strength <= 0.01f) {
            return;
        }
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        int strips = 20;
        int side = (int) (w * 0.22f);
        int top = (int) (h * 0.2f);
        for (int i = 0; i < strips; i++) {
            float t = 1.0f - i / (float) strips;
            int alpha = (int) (220 * strength * t * t);
            if (alpha < 3) {
                continue;
            }
            int rgb = i < strips / 3 ? deep : bright;
            int colour = (alpha << 24) | (rgb & 0xFFFFFF);
            int sx = i * side / strips;
            int sw = Math.max(1, side / strips);
            graphics.fill(sx, 0, sx + sw, h, colour);
            graphics.fill(w - sx - sw, 0, w - sx, h, colour);
            int sy = i * top / strips;
            int sh = Math.max(1, top / strips);
            graphics.fill(0, sy, w, sy + sh, colour);
            graphics.fill(0, h - sy - sh, w, h - sy, colour);
        }
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static Entity entity(int id) {
        ClientLevel level = Minecraft.getInstance().level;
        return id < 0 || level == null ? null : level.getEntity(id);
    }

    private static float u0(int sigil) {
        return (sigil % 4) * 0.25f;
    }

    private static float v0(int sigil) {
        return (sigil / 4) * 0.5f;
    }

    /**
     * The sigils are drawn with the additive glow type, which ignores alpha: a fade has to darken the colour
     * itself. These scale the colour by the alpha and pass it on.
     */
    private static void sigilFlat(VertexConsumer vc, Matrix4f m, Vec3 at, Vec3 cam, float half, float rot,
                                  float r, float g, float b, float a, float u0, float v0, float u1, float v1, float lift) {
        flat(vc, m, at, cam, half, rot, r * a, g * a, b * a, a, u0, v0, u1, v1, lift);
    }

    private static void sigilBillboard(VertexConsumer vc, Matrix4f m, Vec3 at, Vec3 cam, Vector3f left, Vector3f up,
                                       float size, float r, float g, float b, float a, float u0, float v0, float u1, float v1) {
        billboard(vc, m, at, cam, left, up, size, r * a, g * a, b * a, a, u0, v0, u1, v1);
    }

    /** A quad lying flat over (x, y + lift, z), {@code half} blocks to each side, turned by {@code rot}. */
    private static void flat(VertexConsumer vc, Matrix4f m, Vec3 at, Vec3 cam, float half, float rot,
                             float r, float g, float b, float a, float u0, float v0, float u1, float v1, float lift) {
        if (a <= 0.004f || half <= 0.001f) {
            return;
        }
        float cx = (float) (at.x - cam.x), cy = (float) (at.y + lift - cam.y), cz = (float) (at.z - cam.z);
        float c = Mth.cos(rot) * half, s = Mth.sin(rot) * half;
        SpellFx.vertex(vc, m, cx - c + s, cy, cz - s - c, u0, v0, r, g, b, a);
        SpellFx.vertex(vc, m, cx + c + s, cy, cz + s - c, u1, v0, r, g, b, a);
        SpellFx.vertex(vc, m, cx + c - s, cy, cz + s + c, u1, v1, r, g, b, a);
        SpellFx.vertex(vc, m, cx - c - s, cy, cz - s + c, u0, v1, r, g, b, a);
    }

    /** A quad facing the camera at {@code at}, {@code size} blocks across. */
    private static void billboard(VertexConsumer vc, Matrix4f m, Vec3 at, Vec3 cam, Vector3f left, Vector3f up,
                                  float size, float r, float g, float b, float a, float u0, float v0, float u1, float v1) {
        if (a <= 0.004f) {
            return;
        }
        float h = size * 0.5f;
        float cx = (float) (at.x - cam.x), cy = (float) (at.y - cam.y), cz = (float) (at.z - cam.z);
        float lx = left.x() * h, ly = left.y() * h, lz = left.z() * h;
        float ux = up.x() * h, uy = up.y() * h, uz = up.z() * h;
        SpellFx.vertex(vc, m, cx + lx + ux, cy + ly + uy, cz + lz + uz, u0, v0, r, g, b, a);
        SpellFx.vertex(vc, m, cx - lx + ux, cy - ly + uy, cz - lz + uz, u1, v0, r, g, b, a);
        SpellFx.vertex(vc, m, cx - lx - ux, cy - ly - uy, cz - lz - uz, u1, v1, r, g, b, a);
        SpellFx.vertex(vc, m, cx + lx - ux, cy + ly - uy, cz + lz - uz, u0, v1, r, g, b, a);
    }

    private static float fadeInOut(float age, float in, float life, float out) {
        return Mth.clamp(Math.min(age / in, (life - age) / out), 0.0f, 1.0f);
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        return 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
    }

    static float red(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255.0f;
    }

    static float green(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255.0f;
    }

    static float blue(int rgb) {
        return (rgb & 0xFF) / 255.0f;
    }
}
