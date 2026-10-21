package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.pensieve.MemoryActors;
import com.hogwartsmagic.entity.GoldenSnitchEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public class GoldenSnitchRenderer extends MobRenderer<GoldenSnitchEntity, GoldenSnitchModel<GoldenSnitchEntity>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/golden_snitch.png");

    public GoldenSnitchRenderer(EntityRendererProvider.Context context) {
        super(context, new GoldenSnitchModel<>(context.bakeLayer(GoldenSnitchModel.LAYER_LOCATION)), 0.15f);
        addLayer(new WingBlurLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(GoldenSnitchEntity entity) {
        return TEXTURE;
    }

    @Override
    protected int getBlockLightLevel(GoldenSnitchEntity entity, net.minecraft.core.BlockPos pos) {
        // The snitch gleams: always rendered near full brightness so it's spottable at dusk.
        return 15;
    }

    /**
     * The wing blur of a Snitch that is a memory puppet: a translucent disc sector per wing, the area the wing sweeps
     * in its beat (the wings themselves keep their flutter, but at half a turn a tick the eye sees only a haze). The
     * real Snitch in the world is untouched. The sector runs between the wing's two extreme angles (the model's
     * constants), a wing thick in z, drawn as both faces of the sweep, each drawn once from the wing's pivot, brightest at the root.
     */
    private static final class WingBlurLayer extends RenderLayer<GoldenSnitchEntity, GoldenSnitchModel<GoldenSnitchEntity>> {
        private static final int STEPS = 10;
        /** Alpha at the wing root and at the tip of the sweep. */
        private static final int ROOT_ALPHA = 120, TIP_ALPHA = 22;
        /** A pale pixel of the wing texture (the wing's feather colour), as uv. */
        private static final float U = 8.5f / 32f, V = 9.5f / 32f;

        WingBlurLayer(MobRenderer<GoldenSnitchEntity, GoldenSnitchModel<GoldenSnitchEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, GoldenSnitchEntity snitch, float limbSwing,
                           float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            if (MemoryActors.runtimeFor(snitch) == null) return;
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
            pose.pushPose();
            getParentModel().bodyPart().translateAndRotate(pose);
            float px = 1f / 16f;
            float lo = -GoldenSnitchModel.WING_TILT - GoldenSnitchModel.FLAP_AMPLITUDE;
            float hi = -GoldenSnitchModel.WING_TILT + GoldenSnitchModel.FLAP_AMPLITUDE;
            // Left wing: out along +x, angle zRot in [lo, hi]. Right wing: out along -x, zRot = tilt - flap in [-hi, -lo].
            sector(vc, pose, GoldenSnitchModel.WING_PIVOT_X * px, GoldenSnitchModel.WING_PIVOT_Y * px, 1f, lo, hi);
            sector(vc, pose, -GoldenSnitchModel.WING_PIVOT_X * px, GoldenSnitchModel.WING_PIVOT_Y * px, -1f, -hi, -lo);
            pose.popPose();
        }

        /** Both faces (z = +-half the wing's thickness, one winding each) of the sector from a to b about a pivot. */
        private static void sector(VertexConsumer vc, PoseStack pose, float cx, float cy, float side, float a, float b) {
            Matrix4f m = pose.last().pose();
            float len = GoldenSnitchModel.WING_LENGTH / 16f;
            float half = GoldenSnitchModel.WING_THICKNESS / 32f;
            for (int s = 0; s < STEPS; s++) {
                float a0 = a + (b - a) * s / STEPS, a1 = a + (b - a) * (s + 1) / STEPS;
                float x0 = cx + side * len * (float) Math.cos(a0), y0 = cy + side * len * (float) Math.sin(a0);
                float x1 = cx + side * len * (float) Math.cos(a1), y1 = cy + side * len * (float) Math.sin(a1);
                for (float z : new float[]{-half, half}) {
                    // One winding per face: entityTranslucent does not cull, so each face shows from either side once.
                    corner(vc, pose, m, cx, cy, z, ROOT_ALPHA);
                    corner(vc, pose, m, x0, y0, z, TIP_ALPHA);
                    corner(vc, pose, m, x1, y1, z, TIP_ALPHA);
                    corner(vc, pose, m, cx, cy, z, ROOT_ALPHA);
                }
            }
        }

        private static void corner(VertexConsumer vc, PoseStack pose, Matrix4f m, float x, float y, float z, int alpha) {
            vc.addVertex(m, x, y, z).setColor(255, 255, 255, alpha).setUv(U, V).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0f, 0f, 1f);
        }
    }
}
