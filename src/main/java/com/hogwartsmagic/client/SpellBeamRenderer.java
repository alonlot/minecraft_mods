package com.hogwartsmagic.client;

import com.hogwartsmagic.entity.SpellBeamEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * The spell beam entity's renderer draws almost nothing of its own: a beam is drawn from the path it has flown,
 * wand to head, by {@link SpellBeamFxRenderer} in its spell's {@link BeamStyle}, which an entity renderer (culled
 * to the entity's small box, and only ever seeing where it is now) can't do. The one thing drawn here is the
 * ribbon two spells add on top of their beam ({@link SpellRibbon}): Glacius's whip of ice and Reparo's tether.
 */
public class SpellBeamRenderer extends EntityRenderer<SpellBeamEntity> {

    /** How far above the entity's feet the drawn spell sits (the client spell effects all measure from it). */
    public static final float ORB_HEIGHT = 0.125F;

    public SpellBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SpellBeamEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (!SpellRibbon.RIBBON.contains(entity.getSpellType())) {
            return;
        }
        Vec3 origin = entity.getPosition(partialTick);
        Vec3 head = SpellBeamClientState.drawnHead(entity.getId(), partialTick);
        if (head == null) {
            head = origin.add(0.0, ORB_HEIGHT, 0.0);
        }
        SpellRibbon.draw(entity, poseStack.last().pose(), buffer, partialTick, this.entityRenderDispatcher.camera,
                origin, head);
    }

    /** A ribbon runs from the wand to the spell, well outside the beam's own small box. */
    @Override
    public boolean shouldRender(SpellBeamEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double camX, double camY, double camZ) {
        if (super.shouldRender(entity, frustum, camX, camY, camZ)) {
            return true;
        }
        return SpellRibbon.RIBBON.contains(entity.getSpellType()) && entity.distanceToSqr(camX, camY, camZ) < 64.0 * 64.0;
    }

    @Override
    public ResourceLocation getTextureLocation(SpellBeamEntity entity) {
        return null;
    }
}
