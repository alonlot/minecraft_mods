package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.client.model.WhulvkWerewolfLycanModel;
import com.hogwartsmagic.entity.LycanthropeVillagerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * Daytime: delegates to a vanilla villager renderer via a client-only surrogate entity.
 * Night (werewolf): Whulvk werewolf model, painted texture (tools/werewolf_harness/paint_werewolf.py),
 * glowing eyes, animated by {@link com.hogwartsmagic.client.model.WerewolfAnimator}.
 */
public class LycanthropeVillagerRenderer extends MobRenderer<LycanthropeVillagerEntity, WhulvkWerewolfLycanModel> {

    private static final ResourceLocation WEREWOLF_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "textures/entity/lycan_werewolf.png");

    private Villager villagerSurrogate;
    private ClientLevel surrogateLevel;

    public LycanthropeVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new WhulvkWerewolfLycanModel(context.bakeLayer(WhulvkWerewolfLycanModel.LAYER_LOCATION)), 0.55F);
        // amber eyes that glow in the dark (transparent everywhere but the irises)
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
    }

    private static final RenderType EYES = RenderType.eyes(
            ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "textures/entity/lycan_werewolf_eyes.png"));

    private Villager getOrCreateSurrogate(ClientLevel level) {
        if (this.villagerSurrogate == null || this.surrogateLevel != level || !this.villagerSurrogate.isAlive()) {
            this.villagerSurrogate = EntityType.VILLAGER.create(level);
            this.surrogateLevel = level;
        }
        return this.villagerSurrogate;
    }

    private void syncSurrogate(LycanthropeVillagerEntity source, Villager surrogate, float partialTick) {
        surrogate.tickCount = source.tickCount;
        surrogate.setPos(source.getX(), source.getY(), source.getZ());
        surrogate.setOldPosAndRot();
        surrogate.setYRot(source.getYRot());
        surrogate.setXRot(source.getXRot());
        surrogate.yRotO = source.yRotO;
        surrogate.xRotO = source.xRotO;
        surrogate.yBodyRot = source.yBodyRot;
        surrogate.yBodyRotO = source.yBodyRotO;
        surrogate.yHeadRot = source.yHeadRot;
        surrogate.yHeadRotO = source.yHeadRotO;
        surrogate.setDeltaMovement(source.getDeltaMovement());
        surrogate.walkAnimation.setSpeed(source.walkAnimation.speed());
        surrogate.walkAnimation.position(source.walkAnimation.position(partialTick));
        surrogate.hurtTime = source.hurtTime;
        surrogate.deathTime = source.deathTime;
        surrogate.setInvisible(source.isInvisible());
    }

    @Override
    public void render(LycanthropeVillagerEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        entity.clientSyncMorphBeforeRender();
        if (entity.useWerewolfModelForClientRender()) {
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        Villager surrogate = getOrCreateSurrogate(level);
        if (surrogate == null) {
            return;
        }

        syncSurrogate(entity, surrogate, partialTick);

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        @SuppressWarnings("unchecked")
        EntityRenderer<Entity> villagerRenderer = (EntityRenderer<Entity>) dispatcher.getRenderer(surrogate);
        if (villagerRenderer == null) {
            return;
        }

        villagerRenderer.render(surrogate, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(LycanthropeVillagerEntity entity) {
        return WEREWOLF_TEXTURE;
    }
}
