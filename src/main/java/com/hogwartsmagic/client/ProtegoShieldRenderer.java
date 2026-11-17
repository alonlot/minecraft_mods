package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.support.ProtegoRules;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Protego as something you can see: a round ward of light hung in front of the shielded one, facing where
 * they look, that flickers up when raised and thins out before it lapses. Your own shield shows from the
 * Protego effect you carry; anyone else's from the Protego beam the client saw strike them (other
 * creatures' effects are not sent to the client). Protego Maxima's dome (amplifier 3) has its own visuals and
 * is left out. Visual only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class ProtegoShieldRenderer {

    private static final float RADIUS = 0.85F;
    private static final float[] COLOR = {0.55F, 0.80F, 1.0F};

    /** Shielded entities seen through a Protego beam hit: id, game time the ward lapses. */
    private static final Map<Integer, Long> SEEN = new HashMap<>();

    private ProtegoShieldRenderer() {
    }

    /** A Protego beam struck {@code entity}: it holds the ward for the shield's time. */
    static void onShielded(LivingEntity entity) {
        SEEN.put(entity.getId(), entity.level().getGameTime() + ProtegoRules.SHIELD_TICKS);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            SEEN.clear();
            return;
        }
        long now = minecraft.level.getGameTime();
        float partial = event.getPartialTick();
        Map<LivingEntity, Float> left = new HashMap<>(); // entity -> ticks of ward left
        SEEN.entrySet().removeIf(e -> e.getValue() <= now);
        for (Map.Entry<Integer, Long> e : SEEN.entrySet()) {
            Entity entity = minecraft.level.getEntity(e.getKey());
            if (entity instanceof LivingEntity living && living.isAlive()) {
                left.put(living, e.getValue() - now - partial);
            }
        }
        if (minecraft.player != null) {
            MobEffectInstance own = minecraft.player.getEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get());
            if (own != null && own.getAmplifier() == 0) {
                left.put(minecraft.player, Math.max(left.getOrDefault(minecraft.player, 0.0F), own.getDuration() - partial));
            }
        }
        if (left.isEmpty()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f matrix = new Matrix4f(); // camera-relative, unrotated: see PatronusShieldRenderer
        var buffer = minecraft.renderBuffers().bufferSource();
        // One render type at a time: asking the buffer source for another type ends the one before.
        for (int pass = 0; pass < 2; pass++) {
            var type = pass == 0 ? ModRenderTypes.SPELL_GLOW : ModRenderTypes.SPELL_RING;
            VertexConsumer consumer = buffer.getBuffer(type);
            for (Map.Entry<LivingEntity, Float> e : left.entrySet()) {
                LivingEntity entity = e.getKey();
                boolean own = entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
                float age = ProtegoRules.SHIELD_TICKS - e.getValue();
                float fadeIn = Mth.clamp(age / 3.0F, 0.0F, 1.0F);
                float fadeOut = Mth.clamp(e.getValue() / 12.0F, 0.0F, 1.0F);
                float flicker = 0.85F + 0.15F * Mth.sin((entity.tickCount + partial) * 1.7F);
                float alpha = fadeIn * fadeOut * flicker * (own ? 0.35F : 1.0F);
                if (alpha >= 0.01F) {
                    draw(consumer, pass == 1, matrix, entity, camera, partial, alpha, own, fadeIn);
                }
            }
            buffer.endBatch(type);
        }
    }

    /** The ward's fill ({@code rings} false, glow type) or its two rings (ring type). */
    private static void draw(VertexConsumer consumer, boolean rings, Matrix4f matrix, LivingEntity entity,
                             Vec3 camera, float partial, float alpha, boolean own, float grow) {
        Vec3 look = entity.getViewVector(partial).normalize();
        Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
        Vec3 up = side.cross(look).normalize();
        Vec3 eye = entity.getEyePosition(partial);
        Vec3 centre = eye.add(look.scale(own ? 1.1 : 0.75)).add(0.0, own ? 0.0 : -0.25, 0.0).subtract(camera);
        float r = RADIUS * (0.6F + 0.4F * grow) * (own ? 1.4F : 1.0F);
        float spin = (entity.tickCount + partial) * 0.05F;
        float[] c = COLOR;
        // A faint fill, the bright rim, and an inner ring turning slowly the other way.
        if (!rings) {
            disc(consumer, matrix, centre, side, up, r * 1.15F, 0.0F, c, 0.22F * alpha);
        } else {
            disc(consumer, matrix, centre, side, up, r, spin, c, 0.95F * alpha);
            disc(consumer, matrix, centre, side, up, r * 0.62F, -spin * 1.6F, SpellFx.whiten(c, 0.4F), 0.55F * alpha);
        }
    }

    /** A textured square in the plane of {@code side} and {@code up}, turned by {@code angle}, both sides. */
    private static void disc(VertexConsumer consumer, Matrix4f matrix, Vec3 centre, Vec3 side, Vec3 up, float r,
                             float angle, float[] c, float alpha) {
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        Vec3 a = side.scale(cos).add(up.scale(sin)).scale(r);
        Vec3 b = up.scale(cos).subtract(side.scale(sin)).scale(r);
        Vec3[] corners = {centre.subtract(a).subtract(b), centre.add(a).subtract(b), centre.add(a).add(b),
                centre.subtract(a).add(b)};
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (int pass = 0; pass < 2; pass++) {
            for (int n = 0; n < 4; n++) {
                int i = pass == 0 ? n : 3 - n;
                Vec3 p = corners[i];
                SpellFx.vertex(consumer, matrix, (float) p.x, (float) p.y, (float) p.z, uv[i][0], uv[i][1],
                        c[0], c[1], c[2], alpha);
            }
        }
    }
}
