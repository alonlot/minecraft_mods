package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.pensieve.PensieveBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * The Pensieve's swirling silver. The block model already carries the animated surface; this draws a spiral disc
 * over it, turning one way and counter-turning a fainter copy the other. An empty basin turns slowly and dimly, a
 * basin holding a memory turns faster, brighter and tinted by the memory, and while a session plays at it the disc
 * burns brightest and fastest. All client side: it only reads the memory and {@code playing} flag the block entity
 * syncs.
 */
public class PensieveBasinRenderer implements BlockEntityRenderer<PensieveBlockEntity> {
    private static final ResourceLocation SWIRL = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID,
            "block/pensieve_swirl");
    /** The disc's half-width in blocks: the opening is 8 wide (x/z 4..12). */
    private static final float HALF = 0.5f;
    /** Just over the model's liquid plane at y = 10.5/16. */
    private static final float Y = 10.5f / 16f + 0.004f;
    private static final int FULL_BRIGHT = 0xF000F0;

    public PensieveBasinRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(PensieveBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        if (be.getLevel() == null) return;
        boolean holding = !be.memory().isEmpty();
        boolean playing = be.playing();
        float speed = playing ? 7.0f : holding ? 3.0f : 0.8f; // degrees per tick
        float alpha = playing ? 1.0f : holding ? 0.8f : 0.34f;
        float t = be.getLevel().getGameTime() + partialTick;
        float pulse = holding ? 0.88f + 0.12f * Mth.sin(t * (playing ? 0.35f : 0.12f)) : 1.0f;
        float[] tint = holding ? tint(be.memory()) : new float[] {0.80f, 0.88f, 1.0f};
        int light = FULL_BRIGHT; // the emissive render type ignores the lightmap anyway

        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(SWIRL);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS));

        disc(pose, vc, sprite, t * speed, Y, alpha * pulse, tint, light, 1.0f);
        disc(pose, vc, sprite, -t * speed * 0.6f + 40f, Y + 0.003f, alpha * pulse * 0.55f, tint, light, 0.78f);
    }

    private static void disc(PoseStack pose, VertexConsumer vc, TextureAtlasSprite sprite, float degrees, float y,
                             float alpha, float[] tint, int light, float scale) {
        pose.pushPose();
        pose.translate(0.5f, y, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(degrees));
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        float h = HALF * scale;
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        int a = Mth.clamp((int) (alpha * 255f), 0, 255);
        int r = (int) (tint[0] * 255f), g = (int) (tint[1] * 255f), b = (int) (tint[2] * 255f);
        // Up-facing quad, wound counter-clockwise seen from above.
        vertex(vc, m, last, -h, 0, -h, u0, v0, r, g, b, a, light);
        vertex(vc, m, last, -h, 0, h, u0, v1, r, g, b, a, light);
        vertex(vc, m, last, h, 0, h, u1, v1, r, g, b, a, light);
        vertex(vc, m, last, h, 0, -h, u1, v0, r, g, b, a, light);
        pose.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, int r, int g, int b, int a, int light) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, 0f, 1f, 0f);
    }

    /** Silver for the cave, a cold green for the chamber, a bruised green for the summoning. */
    private static float[] tint(ItemStack memory) {
        String path = BuiltInRegistries.ITEM.getKey(memory.getItem()).getPath();
        if (path.startsWith("chamber")) return new float[] {0.65f, 1.0f, 0.82f};
        if (path.startsWith("summoning")) return new float[] {0.62f, 0.9f, 0.4f};
        return new float[] {0.85f, 0.93f, 1.0f};
    }

    @Override
    public boolean shouldRenderOffScreen(PensieveBlockEntity be) {
        return false;
    }
}
