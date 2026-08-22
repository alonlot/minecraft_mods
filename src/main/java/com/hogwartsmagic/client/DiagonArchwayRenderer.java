package com.hogwartsmagic.client;

import com.hogwartsmagic.block.DiagonArchwayBlock;
import com.hogwartsmagic.block.entity.DiagonArchwayBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import org.joml.Quaternionf;

/**
 * Draws the bricks of the archway while the wall is opening.
 *
 * <p>The real blocks are invisible-but-solid for the whole sequence
 * ({@code PHASE = MOVING}); every brick you see moving is one of these, a free cube that can be
 * anywhere. Which is the only way to do it: Minecraft draws a block where its block is, and a brick
 * that has slid two and a half blocks sideways and turned ninety degrees is not in its block.
 *
 * <p>The pose is a pure function of the clock and of the brick's offset from the tapped one. Pure on
 * purpose -- the client counts ticks by itself between the two packets it gets, so a pose that
 * depended on history would drift, and the correction when the next packet arrived would snap.
 *
 * <p>Everything is written in terms of <b>a</b> across the wall, <b>y</b> up and <b>n</b> out of it,
 * with the actual world axes looked up from the block entity, which measured them. The courtyard
 * can face any direction.
 */
@OnlyIn(Dist.CLIENT)
public class DiagonArchwayRenderer implements BlockEntityRenderer<DiagonArchwayBlockEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "hogwartsmagic", "textures/entity/diagon_archway_brick.png");

    /** How far past the edge of the opening a brick travels before the flank wall hides it. */
    private static final float CLEARANCE = 2.0F;
    /** How far it withdraws into the wall as it goes. */
    private static final float SINK = 0.55F;
    /** The hop as it breaks free. Small: masonry does not bounce. */
    private static final float HOP = 0.3F;
    /** How far it screws about, in radians. */
    private static final float SCREW = 1.7F;
    private static final float TIP = 0.5F;
    /** Fraction of a brick's travel spent glowing, as the seam lets go of it. */
    private static final float FLASH = 0.22F;

    private final ModelPart brick;

    public DiagonArchwayRenderer(BlockEntityRendererProvider.Context context) {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("brick", CubeListBuilder.create()
                .texOffs(0, 0).addBox(0.0F, 0.0F, 0.0F, 16.0F, 16.0F, 16.0F), PartPose.ZERO);
        this.brick = LayerDefinition.create(mesh, 64, 32).bakeRoot().getChild("brick");
    }

    @Override
    public boolean shouldRenderOffScreen(DiagonArchwayBlockEntity wall) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(DiagonArchwayBlockEntity wall, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        int ticks = wall.ticks();
        if (ticks < 0 || wall.archCells().isEmpty() || wall.getLevel() == null) {
            return;
        }
        float t = Math.min(ticks + partialTick, DiagonArchwayBlockEntity.TOTAL);

        BlockPos origin = wall.getBlockPos();
        int aAxis = wall.acrossAxis();
        int nAxis = wall.normalAxis();
        int centre = wall.archCentre();
        int bottom = wall.archBottom();
        int tapA = axis(origin, aAxis);
        int tapY = origin.getY();

        VertexConsumer buffer = buffers.getBuffer(RenderType.entitySolid(TEXTURE));

        for (BlockPos cell : wall.archCells()) {
            // Once the real block has gone to OPEN the sequence is over and there is nothing left
            // to draw; guarding per cell also covers a wall that was part-opened when the chunk
            // reloaded.
            if (wall.getLevel().getBlockState(cell).getBlock() instanceof DiagonArchwayBlock
                    && wall.getLevel().getBlockState(cell).getValue(DiagonArchwayBlock.PHASE)
                    == DiagonArchwayBlock.OPEN) {
                continue;
            }

            int cellA = axis(cell, aAxis);
            int du = cellA - tapA;                      // offset from the tapped brick: sets timing
            int dv = cell.getY() - tapY;
            int u = cellA - centre;                     // offset from the arch centre: sets direction
            int v = cell.getY() - bottom;

            float t0 = DiagonArchwayBlockEntity.GLOW + DiagonArchwayBlockEntity.brickDelay(du, dv);
            float p = (t - t0) / DiagonArchwayBlockEntity.TRAVEL;

            if (p >= 1.0F) {
                continue;                               // gone into the flank wall
            }

            float da = 0.0F, dy = 0.0F, dn = 0.0F, screw = 0.0F, tip = 0.0F;
            boolean hot = false;
            if (p > 0.0F) {
                float e = p * p * (3.0F - 2.0F * p);    // smoothstep: masonry has weight
                // Bricks on the centre line have no side to prefer, so alternate them by height --
                // otherwise the middle column all goes the same way and the arch unzips.
                float dir = u != 0 ? Math.signum(u) : ((v & 1) == 0 ? 1.0F : -1.0F);
                float reach = (DiagonArchwayBlockEntity.HALF_WIDTH + 1 - Math.abs(u)) + CLEARANCE;
                da = dir * e * reach;
                dn = -e * SINK;
                dy = Mth.sin((float) Math.PI * e) * HOP;
                screw = dir * e * SCREW;
                tip = e * TIP;
                hot = p < FLASH;
            }

            float[] offset = new float[3];
            offset[aAxis] = da;
            offset[nAxis] = dn;
            offset[1] = dy;

            pose.pushPose();
            pose.translate(cell.getX() - origin.getX() + 0.5F + offset[0],
                    cell.getY() - origin.getY() + 0.5F + offset[1],
                    cell.getZ() - origin.getZ() + 0.5F + offset[2]);
            if (screw != 0.0F) {
                pose.mulPose(axisRotation(1, screw));           // turn about the vertical
            }
            if (tip != 0.0F) {
                pose.mulPose(axisRotation(aAxis, tip));         // and tilt out of the wall
            }
            pose.translate(-0.5F, -0.5F, -0.5F);

            // A brick draws at full brightness for the first fifth of its travel, as if the seam of
            // light is what let go of it. It is the same trick the vault door uses on the crest of
            // its ripple, and it is what stops the peel reading as blocks merely sliding.
            this.brick.render(pose, buffer, hot ? LightTexture.FULL_BRIGHT : light,
                    OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    private static int axis(BlockPos pos, int which) {
        return which == 0 ? pos.getX() : which == 1 ? pos.getY() : pos.getZ();
    }

    private static Quaternionf axisRotation(int axis, float radians) {
        if (axis == 0) {
            return com.mojang.math.Axis.XP.rotation(radians);
        }
        return axis == 1 ? com.mojang.math.Axis.YP.rotation(radians)
                : com.mojang.math.Axis.ZP.rotation(radians);
    }
}
