package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.IncarcerousBoundPose;
import com.hogwartsmagic.client.pensieve.ActorPoser;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pensieve actors (and the body left at the basin) are posed limb by limb: right after the renderer has run the
 * model own setupAnim, {@link ActorPoser} adds named creature animations, poses and limb tracks on top. Models are
 * shared by every entity of a renderer, so the pose is snapshotted before setupAnim and put back on the way out.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class ActorPoseMixin {
    private static final String RENDER =
            "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @Inject(method = RENDER, at = @At("HEAD"))
    private void hogwartsmagic$beforeSetup(LivingEntity entity, float yaw, float partial, PoseStack pose,
                                           MultiBufferSource buffer, int light, CallbackInfo ci) {
        ActorPoser.begin(((LivingEntityRenderer<?, ?>) (Object) this).getModel(), entity);
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
            shift = At.Shift.AFTER))
    private void hogwartsmagic$afterSetup(LivingEntity entity, float yaw, float partial, PoseStack pose,
                                          MultiBufferSource buffer, int light, CallbackInfo ci) {
        ActorPoser.apply(((LivingEntityRenderer<?, ?>) (Object) this).getModel(), entity, partial);
        // Incarcerous: the bound body (arms pinned, straining). Same hook, so it sits on top of any actor pose.
        IncarcerousBoundPose.apply(((LivingEntityRenderer<?, ?>) (Object) this).getModel(), entity, partial);
        // Avada Kedavra windup: the wand arm raised over the aim, striking down on the cast.
        com.hogwartsmagic.client.AvadaChargePose.apply(((LivingEntityRenderer<?, ?>) (Object) this).getModel(),
                entity, partial);
        // Every other cast: the spell's own gesture (a flick, a slash, a whip...).
        com.hogwartsmagic.client.CastMotions.apply(((LivingEntityRenderer<?, ?>) (Object) this).getModel(),
                entity, partial);
        // Struck: a disarmed arm flung up, a petrified body gone rigid.
        com.hogwartsmagic.client.HitReactions.applyPose(((LivingEntityRenderer<?, ?>) (Object) this).getModel(),
                entity, partial);
        // Locked in Priori Incantatem: the wand held out at the other duellist, straining.
        com.hogwartsmagic.client.PrioriClient.applyPose(((LivingEntityRenderer<?, ?>) (Object) this).getModel(),
                entity, partial);
        // Under the Cruciatus Curse: the body writhes; its caster holds the wand out on them.
        com.hogwartsmagic.client.CrucioClient.applyPose(((LivingEntityRenderer<?, ?>) (Object) this).getModel(),
                entity, partial);
        // Pouring Aqua Eructo: the wand held out into the stream.
        com.hogwartsmagic.client.AquaStreamClient.applyPose(((LivingEntityRenderer<?, ?>) (Object) this).getModel(),
                entity, partial);
    }

    @Inject(method = RENDER, at = @At("RETURN"))
    private void hogwartsmagic$afterRender(LivingEntity entity, float yaw, float partial, PoseStack pose,
                                           MultiBufferSource buffer, int light, CallbackInfo ci) {
        ActorPoser.end();
    }
}
