package com.hogwartsmagic.client;

import com.hogwartsmagic.entity.MemoryBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.RenderTypeHelper;
import net.minecraftforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a {@code memory_block} actor: one block state's baked model, turned about the block's centre by the look yaw,
 * pitch and roll (all 0: the block exactly as placed in the world). The actor {@code scale} is already on the
 * PoseStack when Pensieve calls this (MemoryActors scales it about the actor position, which is the centre), so the
 * geometry is taken at scale 1.
 *
 * <p>Built to be cheap in the hundreds (the wall that opens is a few hundred bricks): each state's quads, per render
 * type, are gathered once from its baked model and kept until the memory ends ({@link #clearCache}) or the model
 * changes (a resource reload), so a frame does no model queries, no random-source work and no allocation per block.
 * Per actor that is one light lookup, a few pose operations and the block's quads (six for a full cube) written
 * straight into the shared block-atlas buffer, which the caller flushes in one batch for every actor.
 */
public class MemoryBlockRenderer extends EntityRenderer<MemoryBlockEntity> {
    private static final Map<BlockState, Baked> CACHE = new IdentityHashMap<>();
    private static final float[] FLAT = {1f, 1f, 1f, 1f};

    private final BlockRenderDispatcher blocks;
    private final int[] lights = new int[4];

    /** A state's quads by entity render type, the model they came from and the tint for tinted quads. */
    private record Baked(BakedModel model, RenderType[] types, BakedQuad[][] quads, float r, float g, float b) {
    }

    public MemoryBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
        this.shadowRadius = 0f;
    }

    /** Forget every cached model: called when the Pensieve actors are cleared. */
    public static void clearCache() {
        CACHE.clear();
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(MemoryBlockEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(MemoryBlockEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        BlockState state = entity.blockState();
        RenderShape shape = state.getRenderShape();
        if (shape == RenderShape.INVISIBLE) return;
        // Lit where the block's centre is, not at the eye height the caller sampled.
        int lit = LevelRenderer.getLightColor(entity.level(), BlockPos.containing(entity.getX(), entity.getY(), entity.getZ()));
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        float roll = Mth.lerp(partialTick, entity.rollO, entity.roll);

        pose.pushPose();
        // `yaw` is the look yaw the caller interpolated. Minecraft yaw: 90 turns the block's south face to the west.
        if (yaw != 0f) pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        if (pitch != 0f) pose.mulPose(Axis.XP.rotationDegrees(pitch));
        if (roll != 0f) pose.mulPose(Axis.ZP.rotationDegrees(roll));
        pose.translate(-0.5, -0.5, -0.5);
        if (shape != RenderShape.MODEL) {
            // A block drawn by a block entity renderer (a chest): the slow, general path.
            blocks.renderSingleBlock(state, pose, buffers, lit, OverlayTexture.NO_OVERLAY);
        } else {
            Baked baked = baked(state);
            PoseStack.Pose p = pose.last();
            lights[0] = lights[1] = lights[2] = lights[3] = lit;
            for (int i = 0; i < baked.types.length; i++) {
                VertexConsumer vc = buffers.getBuffer(baked.types[i]);
                for (BakedQuad q : baked.quads[i]) {
                    boolean tint = q.isTinted();
                    vc.putBulkData(p, q, FLAT, tint ? baked.r : 1f, tint ? baked.g : 1f, tint ? baked.b : 1f, 1f,
                            lights, OverlayTexture.NO_OVERLAY, false);
                }
            }
        }
        pose.popPose();
    }

    /** The state's quads, gathered once per model (the way BlockRenderDispatcher#renderSingleBlock asks for them). */
    private Baked baked(BlockState state) {
        BakedModel model = blocks.getBlockModel(state);
        Baked b = CACHE.get(state);
        if (b != null && b.model == model) return b;
        RandomSource random = RandomSource.create(42L);
        List<RenderType> types = new ArrayList<>(1);
        List<BakedQuad[]> quads = new ArrayList<>(1);
        for (RenderType rt : model.getRenderTypes(state, random, ModelData.EMPTY)) {
            List<BakedQuad> all = new ArrayList<>();
            for (Direction d : Direction.values()) {
                random.setSeed(42L);
                all.addAll(model.getQuads(state, d, random, ModelData.EMPTY, rt));
            }
            random.setSeed(42L);
            all.addAll(model.getQuads(state, null, random, ModelData.EMPTY, rt));
            types.add(RenderTypeHelper.getEntityRenderType(rt, false));
            quads.add(all.toArray(new BakedQuad[0]));
        }
        int c = Minecraft.getInstance().getBlockColors().getColor(state, null, null, 0);
        b = new Baked(model, types.toArray(new RenderType[0]), quads.toArray(new BakedQuad[0][]),
                (c >> 16 & 0xFF) / 255f, (c >> 8 & 0xFF) / 255f, (c & 0xFF) / 255f);
        CACHE.put(state, b);
        return b;
    }
}
