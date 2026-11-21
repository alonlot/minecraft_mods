package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.HungarianHorntailModel;
import com.hogwartsmagic.client.model.UkrainianIronbellyModel;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.hogwartsmagic.entity.UkrainianIronbellyEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Ironbelly: its own model, its own hide, half-blind milky eyes, and the chains.
 *
 * <p>The chains were a radius and nothing else. The dragon was pulled back to its anchor by code,
 * the stubs stood in the floor, and nothing connected the two, so the one puzzle in the room - break
 * the stubs and it is free - had nothing on screen to suggest it. Now a chain runs from the shackle
 * on its ankle to every stub still standing: slack and lying on the floor while it stays near the
 * anchor, lifting and coming taut as it reaches the end of its tether, and gone the moment its stub
 * is broken. You can count what is left holding it.
 */
public class UkrainianIronbellyRenderer extends HungarianHorntailRenderer {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ExampleMod.MODID, "textures/entity/ukrainian_ironbelly.png");
    private static final ResourceLocation EYES = ResourceLocation.fromNamespaceAndPath(
            ExampleMod.MODID, "textures/entity/ukrainian_ironbelly_eyes.png");
    private static final RenderType CHAIN = RenderType.entityCutoutNoCull(
            ResourceLocation.withDefaultNamespace("textures/block/chain.png"));

    /** Thickness of a drawn chain, in blocks. Heavier than a chain block: this holds a dragon. */
    private static final float CHAIN_WIDTH = 0.34F;
    /** Length of chain one repeat of the texture covers. */
    private static final float LINK = 0.7F;
    /** How often the stubs round an anchor are looked for again, in ticks. */
    private static final int STUB_REFRESH = 40;

    /** The ring, in world space, as the shackle layer last placed it this frame. */
    private Vec3 ring;

    private final Map<Integer, StubCache> stubs = new HashMap<>();

    private record StubCache(long at, BlockPos anchor, List<BlockPos> stubs) {
    }

    public UkrainianIronbellyRenderer(EntityRendererProvider.Context context) {
        super(context, new UkrainianIronbellyModel(context.bakeLayer(UkrainianIronbellyModel.LAYER_LOCATION)),
                2.2F, EYES);
        this.addLayer(new ShackleLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(HungarianHorntailEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(HungarianHorntailEntity entity, PoseStack poseStack, float partialTickTime) {
        // The larger breed, and it should look it beside the Horntail (1.15).
        poseStack.scale(1.38F, 1.38F, 1.38F);
    }

    @Override
    public void render(HungarianHorntailEntity entity, float yaw, float partial, PoseStack poseStack,
                       MultiBufferSource buffer, int light) {
        this.ring = null;
        super.render(entity, yaw, partial, poseStack, buffer, light);
        if (!(entity instanceof UkrainianIronbellyEntity iron) || !iron.isChained() || this.ring == null) {
            return;
        }
        BlockPos anchor = iron.syncedAnchor();
        if (anchor == null) {
            return;
        }
        Vec3 origin = new Vec3(Mth.lerp(partial, entity.xo, entity.getX()),
                Mth.lerp(partial, entity.yo, entity.getY()),
                Mth.lerp(partial, entity.zo, entity.getZ()));
        VertexConsumer vc = buffer.getBuffer(CHAIN);
        Matrix4f pose = poseStack.last().pose();
        PoseStack.Pose normal = poseStack.last();
        for (BlockPos stub : stubsFor(entity, anchor)) {
            Vec3 end = new Vec3(stub.getX() + 0.5D, stub.getY() + 0.85D, stub.getZ() + 0.5D);
            drawChain(vc, pose, normal, entity.level(), this.ring.subtract(origin), end.subtract(origin),
                    origin, stub.getY() + 0.06D - origin.y, anchor, stub);
        }
    }

    /** The lightning-rod stubs round an anchor, looked up again every couple of seconds. */
    private List<BlockPos> stubsFor(HungarianHorntailEntity entity, BlockPos anchor) {
        long now = entity.level().getGameTime();
        StubCache known = this.stubs.get(entity.getId());
        if (known != null && known.anchor().equals(anchor) && now - known.at() < STUB_REFRESH) {
            return known.stubs();
        }
        if (this.stubs.size() > 32) {
            this.stubs.clear();
        }
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -14; dx <= 14; dx++) {
            for (int dy = -4; dy <= 6; dy++) {
                for (int dz = -14; dz <= 14; dz++) {
                    cursor.set(anchor.getX() + dx, anchor.getY() + dy, anchor.getZ() + dz);
                    if (entity.level().getBlockState(cursor).is(Blocks.LIGHTNING_ROD)) {
                        found.add(cursor.immutable());
                    }
                }
            }
        }
        this.stubs.put(entity.getId(), new StubCache(now, anchor, found));
        return found;
    }

    /**
     * One chain from {@code a} to {@code b} (entity-relative), sagging by however much slack it has.
     *
     * <p>Its length at rest is the stub's distance from the anchor plus the chain radius, so it is
     * slack - lying on the floor, which is what the clamp to {@code floor} is for - while the dragon
     * stays near the anchor, and runs straight and taut once it reaches the end of its tether.
     */
    private static void drawChain(VertexConsumer vc, Matrix4f pose, PoseStack.Pose normal, Level level,
                                  Vec3 a, Vec3 b, Vec3 origin, double floor, BlockPos anchor, BlockPos stub) {
        double span = a.distanceTo(b);
        if (span < 0.2D) {
            return;
        }
        double rest = Math.hypot(stub.getX() - anchor.getX(), stub.getZ() - anchor.getZ())
                + UkrainianIronbellyEntity.CHAIN_RADIUS * 0.85D;
        double slack = Math.max(0.0D, rest - span);
        double sag = Math.min(5.0D, Math.sqrt(0.375D * span * slack));
        int segments = Math.max(4, (int) Math.ceil(span / LINK));

        Vec3 prev = point(a, b, 0.0D, sag, floor);
        for (int i = 1; i <= segments; i++) {
            Vec3 next = point(a, b, (double) i / segments, sag, floor);
            Vec3 mid = prev.add(next).scale(0.5D).add(origin);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(mid));
            link(vc, pose, normal, prev, next, light);
            prev = next;
        }
    }

    private static Vec3 point(Vec3 a, Vec3 b, double t, double sag, double floor) {
        Vec3 p = a.lerp(b, t);
        double y = p.y - sag * 4.0D * t * (1.0D - t);
        return new Vec3(p.x, Math.max(y, Math.min(floor, Math.min(a.y, b.y))), p.z);
    }

    /** Two crossed strips of the chain texture, the way the chain block draws itself. */
    private static void link(VertexConsumer vc, Matrix4f pose, PoseStack.Pose normal, Vec3 p0, Vec3 p1, int light) {
        Vec3 d = p1.subtract(p0);
        if (d.lengthSqr() < 1.0E-6D) {
            return;
        }
        d = d.normalize();
        Vec3 ref = Math.abs(d.y) > 0.9D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 s1 = d.cross(ref).normalize().scale(CHAIN_WIDTH * 0.5D);
        Vec3 s2 = d.cross(s1).normalize().scale(CHAIN_WIDTH * 0.5D);
        strip(vc, pose, normal, p0, p1, s1, s2.normalize(), 0.0F, 3.0F / 16.0F, light);
        strip(vc, pose, normal, p0, p1, s2, s1.normalize(), 3.0F / 16.0F, 6.0F / 16.0F, light);
    }

    private static void strip(VertexConsumer vc, Matrix4f pose, PoseStack.Pose normal, Vec3 p0, Vec3 p1,
                              Vec3 side, Vec3 n, float u0, float u1, int light) {
        vertex(vc, pose, normal, p0.subtract(side), u0, 0.0F, n, light);
        vertex(vc, pose, normal, p0.add(side), u1, 0.0F, n, light);
        vertex(vc, pose, normal, p1.add(side), u1, 1.0F, n, light);
        vertex(vc, pose, normal, p1.subtract(side), u0, 1.0F, n, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, PoseStack.Pose normal, Vec3 p,
                               float u, float v, Vec3 n, int light) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(normal, (float) n.x, (float) n.y, (float) n.z);
    }

    /** Reads the shackle ring's world position off the posed model, for the chains. */
    private static final class ShackleLayer extends RenderLayer<HungarianHorntailEntity, HungarianHorntailModel> {
        private final UkrainianIronbellyRenderer owner;

        ShackleLayer(UkrainianIronbellyRenderer owner) {
            super(owner);
            this.owner = owner;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                           HungarianHorntailEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!(getParentModel() instanceof UkrainianIronbellyModel model)) {
                return;
            }
            PoseStack at = new PoseStack();
            at.last().pose().set(poseStack.last().pose());
            at.last().normal().set(poseStack.last().normal());
            model.translateToShackle(at);
            Vector4f p = new Vector4f(UkrainianIronbellyModel.RING_X / 16.0F,
                    UkrainianIronbellyModel.RING_Y / 16.0F, UkrainianIronbellyModel.RING_Z / 16.0F, 1.0F)
                    .mul(at.last().pose());
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            this.owner.ring = new Vec3(p.x + cam.x, p.y + cam.y, p.z + cam.z);
        }
    }
}
