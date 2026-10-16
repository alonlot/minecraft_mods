package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.OwlModel;
import com.hogwartsmagic.client.model.PatronusModel;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.OwlEntity;
import com.hogwartsmagic.entity.PatronusEntity;
import com.hogwartsmagic.spell.summon.PatronusForm;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.FoxModel;
import net.minecraft.client.model.OcelotModel;
import net.minecraft.client.model.ParrotModel;
import net.minecraft.client.model.RabbitModel;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.Level;

/**
 * Draws a corporeal Patronus. The generic guardian, the Stag and the Doe use the mod's stag model and
 * texture (the Doe hides the antlers). The other personal forms reuse vanilla animal models and the
 * mod's owl model, all drawn with one silver texture through the translucent emissive render type and
 * a pale translucent tint, so every form reads as the same ghostly light. Client only; forms are
 * compared with ==, never switched on.
 */
public class PatronusRenderer extends MobRenderer<PatronusEntity, PatronusModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/patronus.png");
    private static final ResourceLocation FORM_TEXTURE = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/patronus_form.png");
    /** Pale silver-blue at about two-thirds opacity. */
    private static final int FORM_TINT = FastColor.ARGB32.color(176, 214, 236, 255);

    private final WolfModel<Wolf> wolfModel;
    private final FoxModel<Fox> foxModel;
    private final RabbitModel<Rabbit> rabbitModel;
    private final OcelotModel<PatronusEntity> catModel;
    private final ParrotModel parrotModel;
    private final OwlModel<OwlEntity> owlModel;
    private final PatronusFormDummies dummies = new PatronusFormDummies();

    public PatronusRenderer(EntityRendererProvider.Context context) {
        super(context, new PatronusModel(context.bakeLayer(PatronusModel.LAYER_LOCATION)), 0.0F); // No shadow for light spirits
        this.wolfModel = new WolfModel<>(context.bakeLayer(ModelLayers.WOLF));
        this.foxModel = new FoxModel<>(context.bakeLayer(ModelLayers.FOX));
        this.rabbitModel = new RabbitModel<>(context.bakeLayer(ModelLayers.RABBIT));
        this.catModel = new OcelotModel<>(context.bakeLayer(ModelLayers.OCELOT));
        this.parrotModel = new ParrotModel(context.bakeLayer(ModelLayers.PARROT));
        this.owlModel = new OwlModel<>(context.bakeLayer(OwlModel.LAYER_LOCATION));
    }

    @Override
    public void render(PatronusEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        PatronusForm form = PatronusForm.byId(entity.getFormId());
        if (form == PatronusForm.GENERIC || form == PatronusForm.STAG || form == PatronusForm.DOE) {
            this.model.setAntlersVisible(form != PatronusForm.DOE);
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            this.model.setAntlersVisible(true);
            return;
        }
        this.renderForm(entity, form, partialTicks, poseStack, buffer);
    }

    @Override
    protected void scale(PatronusEntity entity, PoseStack poseStack, float partialTickTime) {
        poseStack.scale(1.2F, 1.2F, 1.2F); // Majestic size
        super.scale(entity, poseStack, partialTickTime);
    }

    @Override
    protected RenderType getRenderType(PatronusEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        // Force the translucent emissive (glowing) render type for the celestial look
        return RenderType.entityTranslucentEmissive(getTextureLocation(entity));
    }

    @Override
    public ResourceLocation getTextureLocation(PatronusEntity entity) {
        return TEXTURE;
    }

    /** The same body transform LivingEntityRenderer applies, then the form's model in silver light. */
    private void renderForm(PatronusEntity entity, PatronusForm form, float partialTicks, PoseStack poseStack, MultiBufferSource buffer) {
        Level level = entity.level();
        float bodyYaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
        float netHeadYaw = Mth.wrapDegrees(headYaw - bodyYaw);
        float headPitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        float age = entity.tickCount + partialTicks;
        float limbSwing = entity.walkAnimation.position(partialTicks);
        float limbAmount = Math.min(1.0F, entity.walkAnimation.speed(partialTicks));
        float scale = formScale(form);
        // The same gentle hover the stag model does with its parts.
        float bob = Mth.sin(age * 0.1F) * 0.03F;

        poseStack.pushPose();
        poseStack.translate(0.0F, bob, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        poseStack.scale(-scale, -scale, scale);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(FORM_TEXTURE));
        int light = LightTexture.FULL_BRIGHT;
        int overlay = OverlayTexture.NO_OVERLAY;

        if (form == PatronusForm.CAT) {
            this.catModel.young = false;
            this.catModel.prepareMobModel(entity, limbSwing, limbAmount, partialTicks);
            this.catModel.setupAnim(entity, limbSwing, limbAmount, age, netHeadYaw, headPitch);
            this.catModel.renderToBuffer(poseStack, consumer, light, overlay, FORM_TINT);
        } else if (form == PatronusForm.WOLF) {
            Wolf wolf = this.dummies.get(EntityType.WOLF, level);
            if (wolf != null) {
                this.wolfModel.young = false;
                this.wolfModel.prepareMobModel(wolf, limbSwing, limbAmount, partialTicks);
                this.wolfModel.setupAnim(wolf, limbSwing, limbAmount, age, netHeadYaw, headPitch);
                this.wolfModel.renderToBuffer(poseStack, consumer, light, overlay, FORM_TINT);
            }
        } else if (form == PatronusForm.FOX) {
            Fox fox = this.dummies.get(EntityType.FOX, level);
            if (fox != null) {
                this.foxModel.young = false;
                this.foxModel.prepareMobModel(fox, limbSwing, limbAmount, partialTicks);
                this.foxModel.setupAnim(fox, limbSwing, limbAmount, age, netHeadYaw, headPitch);
                this.foxModel.renderToBuffer(poseStack, consumer, light, overlay, FORM_TINT);
            }
        } else if (form == PatronusForm.HARE) {
            Rabbit rabbit = this.dummies.get(EntityType.RABBIT, level);
            if (rabbit != null) {
                this.rabbitModel.young = false;
                this.rabbitModel.prepareMobModel(rabbit, limbSwing, limbAmount, partialTicks);
                this.rabbitModel.setupAnim(rabbit, limbSwing, limbAmount, age, netHeadYaw, headPitch);
                this.rabbitModel.renderToBuffer(poseStack, consumer, light, overlay, FORM_TINT);
            }
        } else if (form == PatronusForm.PHOENIX) {
            Parrot parrot = this.dummies.get(EntityType.PARROT, level);
            if (parrot != null) {
                this.parrotModel.young = false;
                this.parrotModel.prepareMobModel(parrot, limbSwing, limbAmount, partialTicks);
                this.parrotModel.setupAnim(parrot, limbSwing, limbAmount, age, netHeadYaw, headPitch);
                this.parrotModel.renderToBuffer(poseStack, consumer, light, overlay, FORM_TINT);
            }
        } else if (form == PatronusForm.OWL) {
            OwlEntity owl = this.dummies.get(ModEntities.OWL.get(), level);
            if (owl != null) {
                this.owlModel.young = false;
                this.owlModel.prepareMobModel(owl, limbSwing, limbAmount, partialTicks);
                this.owlModel.setupAnim(owl, limbSwing, limbAmount, age, netHeadYaw, headPitch);
                this.owlModel.renderToBuffer(poseStack, consumer, light, overlay, FORM_TINT);
            }
        }
        poseStack.popPose();
    }

    /** Small animals are drawn larger so a Patronus still reads as a guardian. */
    private static float formScale(PatronusForm form) {
        if (form == PatronusForm.HARE) {
            return 1.6F;
        }
        if (form == PatronusForm.CAT) {
            return 1.2F;
        }
        if (form == PatronusForm.OWL) {
            return 1.4F;
        }
        if (form == PatronusForm.FOX) {
            return 1.1F;
        }
        if (form == PatronusForm.PHOENIX) {
            return 1.8F;
        }
        return 1.0F;
    }
}
