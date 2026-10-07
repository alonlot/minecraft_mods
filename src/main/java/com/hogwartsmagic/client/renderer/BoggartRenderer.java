package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.boggart.BoggartEntity;
import com.hogwartsmagic.entity.boggart.BoggartRules;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Draws a Boggart as a surrogate of another entity type.
 *
 * Hidden (form ""): a vex drawn through a buffer that forces every render type to the translucent
 * boggart shadow texture, so it reads as a dark smoky shape. It bobs a little.
 *
 * Revealed: the surrogate of the fear form, drawn with its own renderer. Position, rotations,
 * walk animation and hurt time are copied from the boggart. Riddikulus hits shrink it, and while
 * it laughs it spins.
 */
public class BoggartRenderer extends EntityRenderer<BoggartEntity> {
    private static final ResourceLocation SHADOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/boggart_shadow.png");
    private static final String SHADOW_FORM = "minecraft:vex";

    private Entity surrogate;
    private String surrogateFormId;
    private int lastWalkTick = Integer.MIN_VALUE;

    public BoggartRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(BoggartEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        String formId = entity.getFormId();
        boolean hidden = formId == null || formId.isEmpty();
        Entity target = surrogateFor(hidden ? SHADOW_FORM : formId, entity);
        if (target == null) {
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
            return;
        }
        // A form that could not be created falls back to the shadow vex.
        boolean drawAsShadow = hidden || SHADOW_FORM.equals(this.surrogateFormId);

        sync(target, entity, partialTick);

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        @SuppressWarnings("unchecked")
        EntityRenderer<Entity> surrogateRenderer = (EntityRenderer<Entity>) dispatcher.getRenderer(target);
        if (surrogateRenderer == null) {
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
            return;
        }

        poseStack.pushPose();
        if (drawAsShadow) {
            float bob = 0.1F * Mth.sin((entity.tickCount + partialTick) * 0.1F);
            poseStack.translate(0.0D, bob, 0.0D);
            surrogateRenderer.render(target, entityYaw, partialTick, poseStack, new ShadowBufferSource(buffer), packedLight);
        } else {
            int needed = Math.max(entity.getRiddikulusHits(), BoggartRules.hitsToBanish(1));
            float scale = BoggartRules.laughScale(entity.getLaughTicks(), entity.getRiddikulusHits(), needed);
            if (entity.isLaughing()) {
                poseStack.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * 25.0F));
            }
            poseStack.scale(scale, scale, scale);
            surrogateRenderer.render(target, entityYaw, partialTick, poseStack, buffer, packedLight);
        }
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** Returns the cached surrogate for the form id, or makes a new one when the form changed. */
    private Entity surrogateFor(String formId, BoggartEntity boggart) {
        if (this.surrogate != null && formId.equals(this.surrogateFormId) && this.surrogate.level() == boggart.level()) {
            return this.surrogate;
        }
        Entity made = create(formId, boggart);
        String madeId = formId;
        if (made == null && !SHADOW_FORM.equals(formId)) {
            made = create(SHADOW_FORM, boggart);
            madeId = SHADOW_FORM;
        }
        this.surrogate = made;
        this.surrogateFormId = made == null ? null : madeId;
        this.lastWalkTick = Integer.MIN_VALUE;
        return made;
    }

    private static Entity create(String formId, BoggartEntity boggart) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        EntityType<?> type = EntityType.byString(formId).orElse(null);
        if (type == null || type == boggart.getType()) {
            return null;
        }
        try {
            return type.create(level);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void sync(Entity target, BoggartEntity source, float partialTick) {
        target.tickCount = source.tickCount;
        target.setPos(source.getX(), source.getY(), source.getZ());
        target.setOldPosAndRot();
        target.setYRot(source.getYRot());
        target.setXRot(source.getXRot());
        target.yRotO = source.yRotO;
        target.xRotO = source.xRotO;
        target.setDeltaMovement(source.getDeltaMovement());

        if (target instanceof LivingEntity living) {
            living.yBodyRot = source.yBodyRot;
            living.yBodyRotO = source.yBodyRotO;
            living.yHeadRot = source.yHeadRot;
            living.yHeadRotO = source.yHeadRotO;
            living.hurtTime = source.hurtTime;
            living.deathTime = 0;
            living.walkAnimation.setSpeed(source.walkAnimation.speed());
            // Step the limb position once per boggart tick so the legs swing at the right rate.
            if (source.tickCount != this.lastWalkTick) {
                living.walkAnimation.update(source.walkAnimation.speed(), 1.0F);
                this.lastWalkTick = source.tickCount;
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(BoggartEntity entity) {
        return MissingTextureAtlasSprite.getLocation();
    }

    /** Forces every render type to the translucent shadow texture. */
    private static class ShadowBufferSource implements MultiBufferSource {
        private final MultiBufferSource delegate;

        private ShadowBufferSource(MultiBufferSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return this.delegate.getBuffer(RenderType.entityTranslucent(SHADOW_TEXTURE));
        }
    }
}
