package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.entity.PetrifiedRemainsEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.hogwartsmagic.ExampleMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public class PetrifiedRemainsRenderer extends EntityRenderer<PetrifiedRemainsEntity> {
    public static final ResourceLocation STONE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/petrified_stone.png");

    public PetrifiedRemainsRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(PetrifiedRemainsEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        Entity surrogate = createSurrogate(entity);
        if (surrogate == null) {
            return;
        }

        surrogate.tickCount = entity.tickCount;
        surrogate.setPos(entity.getX(), entity.getY(), entity.getZ());
        surrogate.setYRot(entity.getYRot());
        surrogate.setXRot(entity.getXRot());
        surrogate.yRotO = entity.yRotO;
        surrogate.xRotO = entity.xRotO;

        if (surrogate instanceof LivingEntity living) {
            living.setYBodyRot(entity.getStoredBodyYaw());
            living.yBodyRotO = entity.getStoredBodyYaw();
            living.setYHeadRot(entity.getStoredHeadYaw());
            living.yHeadRotO = entity.getStoredHeadYaw();
            living.walkAnimation.setSpeed(0.0F);
            living.hurtTime = 0;
            living.deathTime = 0;
        }

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        @SuppressWarnings("unchecked")
        EntityRenderer<Entity> surrogateRenderer = (EntityRenderer<Entity>) dispatcher.getRenderer(surrogate);
        if (surrogateRenderer == null) {
            return;
        }

        poseStack.pushPose();
        MultiBufferSource stoneBuffer = new StoneBufferSource(buffer);
        surrogateRenderer.render(surrogate, entityYaw, partialTick, poseStack, stoneBuffer, packedLight);
        if (surrogate instanceof LivingEntity living) {
            VertexConsumer overlayBuffer = buffer.getBuffer(RenderType.entityTranslucent(STONE_TEXTURE));
            dispatcher.getRenderer(living).render(living, entityYaw, partialTick, poseStack, new OverlayOnlyBufferSource(overlayBuffer), packedLight);
        }
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private Entity createSurrogate(PetrifiedRemainsEntity remains) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        EntityType<?> type = EntityType.byString(remains.getMimicTypeId()).orElse(null);
        if (type == null || type == remains.getType()) {
            return null;
        }
        return type.create(level);
    }

    @Override
    public ResourceLocation getTextureLocation(PetrifiedRemainsEntity entity) {
        return net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation();
    }

    public static class StoneBufferSource implements MultiBufferSource {
        private final MultiBufferSource delegate;

        public StoneBufferSource(MultiBufferSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return this.delegate.getBuffer(RenderType.entityCutoutNoCull(STONE_TEXTURE));
        }
    }

    public static class OverlayOnlyBufferSource implements MultiBufferSource {
        private final VertexConsumer buffer;

        public OverlayOnlyBufferSource(VertexConsumer buffer) {
            this.buffer = buffer;
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return new AlphaLockedVertexConsumer(this.buffer, 120);
        }
    }

    private static class AlphaLockedVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int alpha;

        private AlphaLockedVertexConsumer(VertexConsumer delegate, int alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.delegate.setColor(164, 168, 172, this.alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.delegate.setNormal(x, y, z);
            return this;
        }
    }
}
