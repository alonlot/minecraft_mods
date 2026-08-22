package com.hogwartsmagic.client;

import com.hogwartsmagic.block.GringottsVaultDoorBlock;
import com.hogwartsmagic.block.entity.GringottsVaultDoorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Draws a vault door whose plates are somewhere other than their blocks.
 *
 * <p>This is the whole reason the door has three phases. Minecraft draws a block at its block and
 * nowhere else, so a leaf that ripples, turns, unbolts and folds back cannot be a set of blocks
 * while it is doing any of that. The blocks go invisible-but-solid and this takes over: one
 * textured cube per cell, posed by a pure function of the tick count, which is why the client can
 * be corrected five times in ten seconds and never jump.
 *
 * <p>Every displacement is measured in the <b>door's own plane</b> rather than in world axes,
 * because a vault faces whichever way the cart line happened to be banking when it went past. The
 * block entity works the plane out by measuring the leaf and sends the two axes over; everything
 * here is written in terms of {@code u} across, {@code v} up and {@code n} out.
 */
@OnlyIn(Dist.CLIENT)
public class GringottsVaultDoorRenderer
        implements BlockEntityRenderer<GringottsVaultDoorBlockEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "hogwartsmagic", "textures/entity/gringotts_vault_door.png");

    /** How far out of the door a plate lifts on the crest of the ripple. */
    private static final float RIPPLE_AMP = 0.32F;
    /**
      * Width of the crest.
      *
      * <p>Narrow on purpose. At 1.5 the envelope covered half a five-plate door at once, which is
      * a swell rather than a wave - you cannot see it travelling if it is everywhere.
      */
    private static final float RIPPLE_SIGMA = 0.85F;
    /** How far a bolt withdraws. */
    private static final float BOLT_PULL = 0.55F;
    /** How far each half folds back, in radians. A hundred degrees, same as the films. */
    private static final float PART_FOLD = 1.75F;

    private final ModelPart plate;

    public GringottsVaultDoorRenderer(BlockEntityRendererProvider.Context context) {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("plate", CubeListBuilder.create()
                .texOffs(0, 0).addBox(0.0F, 0.0F, 0.0F, 16.0F, 16.0F, 16.0F), PartPose.ZERO);
        this.plate = LayerDefinition.create(mesh, 64, 32).bakeRoot().getChild("plate");
    }

    @Override
    public boolean shouldRenderOffScreen(GringottsVaultDoorBlockEntity door) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public void render(GringottsVaultDoorBlockEntity door, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        int ticks = door.ticks();
        if (ticks < 0 || door.leafPositions().isEmpty() || door.getLevel() == null) {
            return;
        }
        float t = Math.min(ticks + partialTick, GringottsVaultDoorBlockEntity.TOTAL);

        BlockPos origin = door.getBlockPos();
        int uAxis = door.uAxis();
        int vAxis = door.vAxis();
        int nAxis = door.normalAxis();
        float radius = (float) door.maxRadius();

        // Where the hand landed, in the door's plane. Everything radiates from here.
        double[] touch = {door.touch().x, door.touch().y, door.touch().z};
        double[] here = {origin.getX() + 0.5D, origin.getY() + 0.5D, origin.getZ() + 0.5D};
        float tu = (float) (touch[uAxis] - here[uAxis]);
        float tv = (float) (touch[vAxis] - here[vAxis]);

        VertexConsumer buffer = buffers.getBuffer(RenderType.entitySolid(TEXTURE));

        for (BlockPos cell : door.leafPositions()) {
            if (door.getLevel().getBlockState(cell).getBlock() instanceof GringottsVaultDoorBlock
                    && door.getLevel().getBlockState(cell)
                            .getValue(GringottsVaultDoorBlock.PHASE)
                            == GringottsVaultDoorBlock.PARTED) {
                continue;
            }
            float u = (float) door.planeU(cell);
            float v = (float) door.planeV(cell);
            float du = u - tu;
            float dv = v - tv;
            float r = Mth.sqrt(du * du + dv * dv);
            float angle = (float) Math.atan2(dv, du);

            Pose p = poseFor(t, du, dv, r, angle, radius);

            pose.pushPose();
            // Cell centre, in the block entity's local space, plus whatever the beat moved it by.
            float[] offset = new float[3];
            offset[uAxis] = p.du;
            offset[vAxis] = p.dv;
            offset[nAxis] = p.dn;
            pose.translate(cell.getX() - origin.getX() + 0.5F + offset[0],
                    cell.getY() - origin.getY() + 0.5F + offset[1],
                    cell.getZ() - origin.getZ() + 0.5F + offset[2]);
            if (p.spin != 0.0F) {
                pose.mulPose(axisRotation(nAxis, p.spin));
            }
            if (p.fold != 0.0F) {
                pose.mulPose(axisRotation(vAxis, p.fold));
            }
            if (p.tip != 0.0F) {
                pose.mulPose(axisRotation(uAxis, p.tip));
            }
            pose.translate(-0.5F, -0.5F, -0.5F);

            // The crest of the ripple draws at full brightness. That single trick is the whole
            // difference between iron that is bumpy and iron that is molten.
            int cellLight = p.hot ? LightTexture.FULL_BRIGHT : light;
            this.plate.render(pose, buffer, cellLight, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    /** Rotation about one of the three world axes, chosen by index rather than by name. */
    private static org.joml.Quaternionf axisRotation(int axis, float radians) {
        if (axis == 0) {
            return Axis.XP.rotation(radians);
        }
        return axis == 1 ? Axis.YP.rotation(radians) : Axis.ZP.rotation(radians);
    }

    /** Where one plate is and how it is turned, at time {@code t}. */
    private record Pose(float du, float dv, float dn, float spin, float fold, float tip,
                        boolean hot) {
    }

    private static final Pose REST = new Pose(0, 0, 0, 0, 0, 0, false);

    /**
     * The animation, as a pure function of the clock.
     *
     * <p>Pure on purpose: the client counts ticks on its own between corrections, so a pose that
     * depended on history would drift and a correction would snap. This way a correction is
     * invisible.
     */
    private static Pose poseFor(float t, float du, float dv, float r, float angle, float radius) {
        final int TOUCH = GringottsVaultDoorBlockEntity.TOUCH;
        final int RIPPLE = GringottsVaultDoorBlockEntity.RIPPLE;
        final int COGS = GringottsVaultDoorBlockEntity.COGS;
        final int BOLTS = GringottsVaultDoorBlockEntity.BOLTS;
        final int PART = GringottsVaultDoorBlockEntity.PART;

        if (t < TOUCH) {
            // Nothing has happened yet. Every plate leans a degree toward the hand, which is what
            // makes the ripple land rather than simply start.
            float lean = 0.03F * (float) Math.exp(-(r * r) / 9.0F) * (t / TOUCH);
            return new Pose(0, 0, 0, 0, 0, lean, false);
        }

        if (t < TOUCH + RIPPLE) {
            float s = (t - TOUCH) / (float) RIPPLE;
            // Just past the far corner by the end of the beat, so the wave is crossing the
            // door for all of it. At radius + 3 it had run off the edge two thirds of the way in
            // and the last twenty ticks were a flat door.
            float front = s * (radius + 1.2F);
            float d = r - front;
            float env = (float) Math.exp(-(d * d) / (2.0F * RIPPLE_SIGMA * RIPPLE_SIGMA));
            // One crest and one trailing trough, so it reads as a wave rather than as a bulge.
            float push = RIPPLE_AMP * env * Mth.cos(d * 1.25F);
            return new Pose(0, 0, push, 0, 0, push * 1.1F, push > RIPPLE_AMP * 0.62F);
        }

        if (t < TOUCH + RIPPLE + COGS) {
            float s = t - TOUCH - RIPPLE;
            return cogs(du, dv, r, radius, s);
        }

        if (t < TOUCH + RIPPLE + COGS + BOLTS) {
            float s = t - TOUCH - RIPPLE - COGS;
            Pose settled = cogs(du, dv, r, radius, COGS);
            if (r < radius - 1.2F) {
                return settled;     // only the rim is bolted
            }
            // Angular order, so the bolts go round the rim rather than at random.
            float index = ((angle + Mth.PI) / (Mth.TWO_PI)) * GringottsVaultDoorBlockEntity.BOLT_COUNT;
            float fired = s * GringottsVaultDoorBlockEntity.BOLT_COUNT / BOLTS;
            float k = Mth.clamp((fired - index) / 3.0F, 0.0F, 1.0F);
            return new Pose(settled.du, settled.dv, settled.dn - BOLT_PULL * k,
                    settled.spin + k * 0.26F, 0, 0, false);
        }

        if (t < TOUCH + RIPPLE + COGS + BOLTS + PART) {
            float s = (t - TOUCH - RIPPLE - COGS - BOLTS) / (float) PART;
            float eased = s * s * (3.0F - 2.0F * s);
            float side = du >= 0.0F ? 1.0F : -1.0F;
            // Seam first: a plate near the middle starts moving before one out at the frame.
            float local = Mth.clamp(eased * 1.7F - (Math.abs(du) / (radius + 1.0F)) * 0.7F,
                    0.0F, 1.0F);
            return new Pose(side * local * (radius + 1.2F), 0.0F, -local * 1.4F,
                    0.0F, side * local * PART_FOLD, 0.0F, false);
        }

        return REST;
    }

    /**
     * Rings turning behind the face, counter to each other.
     *
     * <p>Two things learned from watching it. The outer ring <b>does not turn</b>: it is the part
     * bolted to the frame, and turning it opened corner gaps that read as the door coming apart
     * rather than as machinery inside it. And the whole effect is small - nine degrees at the end,
     * not thirty. What you are meant to see is that something enormous has started moving, not the
     * door face rotating.
     */
    private static Pose cogs(float du, float dv, float r, float radius, float s) {
        float ease = Math.min(1.0F, s / 8.0F);
        int ring = Mth.clamp((int) (r / Math.max(0.8F, radius / 3.0F)), 0, 2);
        float dir = ring == 2 ? 0.0F : (ring == 0 ? 1.0F : -1.0F);
        float spin = dir * ease * s * 0.004F;
        float cos = Mth.cos(spin);
        float sin = Mth.sin(spin);
        // Orbit the plate about the door's centre, and turn the plate itself by the same amount so
        // the ring reads as one piece of machinery rather than as tiles sliding past each other.
        float nu = du * cos - dv * sin;
        float nv = du * sin + dv * cos;
        return new Pose(nu - du, nv - dv, -0.08F * ease, spin, 0, 0, false);
    }
}
