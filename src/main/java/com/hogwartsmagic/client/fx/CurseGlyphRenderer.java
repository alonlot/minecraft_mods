package com.hogwartsmagic.client.fx;

import com.hogwartsmagic.block.CursedGlyphBlock;
import com.hogwartsmagic.block.entity.TombMarkerBlockEntity;
import com.hogwartsmagic.client.ModRenderTypes;
import com.hogwartsmagic.client.SpellFx;
import com.hogwartsmagic.tomb.TombCurse;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * A revealed cursed tile shows its curse: the curse's sigil floats over it, turning slowly and bobbing,
 * burning in the curse's colour, with a glow pooled on the tile beneath it. Amber is the Collapse, green
 * the Risen, violet the Withering, so a player who has learned the colours can read the tile before
 * deciding to lift it. Unrevealed tiles draw nothing here: they must look like any other stone.
 */
public class CurseGlyphRenderer implements BlockEntityRenderer<TombMarkerBlockEntity> {
    private static final RenderType SIGIL = ModRenderTypes.magicGlow(CurseFx.SIGILS);

    public CurseGlyphRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TombMarkerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof CursedGlyphBlock) || !state.getValue(CursedGlyphBlock.REVEALED)
                || be.getLevel() == null) {
            return;
        }
        TombCurse curse = state.getValue(CursedGlyphBlock.CURSE);
        float r = CurseFx.red(curse.rgb()), g = CurseFx.green(curse.rgb()), b = CurseFx.blue(curse.rgb());
        // Each tile keeps its own phase, so a floor of them does not bob in step like a machine.
        float phase = (be.getBlockPos().hashCode() & 1023) * 0.37f;
        float time = be.getLevel().getGameTime() + partialTick + phase;
        float flicker = 0.8f + 0.2f * Mth.sin(time * 0.5f) * Mth.sin(time * 0.13f);

        // The glow pooled on the tile.
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.SPELL_GLOW);
        Matrix4f m = pose.last().pose();
        float h = 0.75f;
        float y = 1.02f;
        float a = 0.38f * flicker;
        SpellFx.vertex(glow, m, 0.5f - h, y, 0.5f - h, 0, 0, r, g, b, a);
        SpellFx.vertex(glow, m, 0.5f + h, y, 0.5f - h, 1, 0, r, g, b, a);
        SpellFx.vertex(glow, m, 0.5f + h, y, 0.5f + h, 1, 1, r, g, b, a);
        SpellFx.vertex(glow, m, 0.5f - h, y, 0.5f + h, 0, 1, r, g, b, a);

        // The sigil, a card of light turning over the tile.
        pose.pushPose();
        pose.translate(0.5f, 1.35f + 0.08f * Mth.sin(time * 0.07f), 0.5f);
        pose.mulPose(Axis.YP.rotation(time * 0.035f));
        Matrix4f s = pose.last().pose();
        VertexConsumer sig = buffers.getBuffer(SIGIL);
        int i = curse.sigil();
        float u0 = (i % 4) * 0.25f, v0 = (i / 4) * 0.5f, u1 = u0 + 0.25f, v1 = v0 + 0.5f;
        float half = 0.27f;
        // Additive: the flicker darkens the colour itself (alpha is ignored by this render type).
        float pr = r * flicker, pg = g * flicker, pb = b * flicker;
        SpellFx.vertex(sig, s, -half, half, 0, u0, v0, pr, pg, pb, 1.0f);
        SpellFx.vertex(sig, s, half, half, 0, u1, v0, pr, pg, pb, 1.0f);
        SpellFx.vertex(sig, s, half, -half, 0, u1, v1, pr, pg, pb, 1.0f);
        SpellFx.vertex(sig, s, -half, -half, 0, u0, v1, pr, pg, pb, 1.0f);
        pose.popPose();
    }
}
