package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.entity.HorcruxSoulEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

public class HorcruxSoulRenderer extends EntityRenderer<HorcruxSoulEntity> {
    public HorcruxSoulRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(HorcruxSoulEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        RemotePlayer surrogate = createSurrogate(entity);
        if (surrogate == null) {
            return;
        }

        surrogate.tickCount = entity.tickCount;
        surrogate.setPos(entity.getX(), entity.getY(), entity.getZ());
        surrogate.setYRot(entity.getYRot());
        surrogate.setXRot(entity.getXRot());
        surrogate.yRotO = entity.yRotO;
        surrogate.xRotO = entity.xRotO;
        surrogate.setYBodyRot(entity.getBodyYaw());
        surrogate.yBodyRotO = entity.getBodyYaw();
        surrogate.setYHeadRot(entity.getYRot());
        surrogate.yHeadRotO = entity.getYRot();
        surrogate.setItemSlot(EquipmentSlot.MAINHAND, entity.getItemBySlot(EquipmentSlot.MAINHAND));
        surrogate.setItemSlot(EquipmentSlot.OFFHAND, entity.getItemBySlot(EquipmentSlot.OFFHAND));
        surrogate.setItemSlot(EquipmentSlot.HEAD, entity.getItemBySlot(EquipmentSlot.HEAD));
        surrogate.setItemSlot(EquipmentSlot.CHEST, entity.getItemBySlot(EquipmentSlot.CHEST));
        surrogate.setItemSlot(EquipmentSlot.LEGS, entity.getItemBySlot(EquipmentSlot.LEGS));
        surrogate.setItemSlot(EquipmentSlot.FEET, entity.getItemBySlot(EquipmentSlot.FEET));

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        @SuppressWarnings("unchecked")
        EntityRenderer<Entity> renderer = (EntityRenderer<Entity>) dispatcher.getRenderer(surrogate);
        if (renderer == null) {
            return;
        }

        poseStack.pushPose();
        MultiBufferSource darkBuffer = new BlackMagicBufferSource(buffer);
        renderer.render(surrogate, entityYaw, partialTick, poseStack, darkBuffer, packedLight);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private RemotePlayer createSurrogate(HorcruxSoulEntity entity) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        UUID id = entity.getPlayerId().orElse(new UUID(0L, 0L));
        return new RemotePlayer(level, new GameProfile(id, entity.getPlayerName().isBlank() ? "horcrux_soul" : entity.getPlayerName()));
    }

    @Override
    public ResourceLocation getTextureLocation(HorcruxSoulEntity entity) {
        return net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation();
    }

    private static class BlackMagicBufferSource implements MultiBufferSource {
        private final MultiBufferSource delegate;

        private BlackMagicBufferSource(MultiBufferSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return new AlphaLockedVertexConsumer(delegate.getBuffer(renderType));
        }
    }

    private static class AlphaLockedVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;

        private AlphaLockedVertexConsumer(VertexConsumer delegate) {
            this.delegate = delegate;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(18, 8, 18, 220);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }
}
