package com.hogwartsmagic.client;

import com.hogwartsmagic.block.DiagonArchwayBlock;
import com.hogwartsmagic.block.entity.DiagonArchwayBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.LevelRenderer;
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
 * The wall coming apart, one brick at a time.
 *
 * <p>Not one block at a time. Each block of the panel is drawn as <b>eight separate bricks</b>, two
 * across and four up, and every one of them has its own timing, its own direction and its own spin.
 * A wall that comes apart in one-metre cubes reads as a wall made of cubes; a wall that comes apart
 * into bricks reads as a wall.
 *
 * <p>That is ~880 little boxes for a sixteen-by-seven panel, which sounds like a lot and is not:
 * they are six quads each and only exist for the eight seconds the wall is opening.
 *
 * <p>The real blocks are invisible-but-solid throughout ({@code PHASE = MOVING}), because Minecraft
 * draws a block where its block is, and a brick that has slid two blocks sideways, risen a third of
 * one and turned ninety degrees is not in its block.
 *
 * <h2>The two populations</h2>
 * <ul>
 *   <li><b>Bricks of the archway</b> leave. They peel outward in a spiral from the tapped brick,
 *       screwing about their own axis, and slide into the wall beside the opening where the solid
 *       flank hides them.</li>
 *   <li><b>Every other brick in the wall</b> stays, but ripples. A shockwave rolls out from the tap
 *       and each brick leans out and back as the crest passes, then settles. Without this the
 *       surround stands dead still while a hole appears in the middle of it, which is the single
 *       thing that made the first version look cheap.</li>
 * </ul>
 *
 * <p>Every pose is a pure function of the clock and of the brick's own coordinates. Pure on purpose:
 * the client counts ticks by itself between the two packets it gets, so a pose that depended on
 * history would drift and the correction would snap.
 */
@OnlyIn(Dist.CLIENT)
public class DiagonArchwayRenderer implements BlockEntityRenderer<DiagonArchwayBlockEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "hogwartsmagic", "textures/entity/diagon_subbrick.png");

    /** Bricks per block: two across, four up. Matches the courses in the block texture. */
    private static final int ACROSS = 2;
    private static final int UP = 4;

    /** How far past the edge of the opening a brick travels before the flank wall hides it. */
    private static final float CLEARANCE = 2.2F;
    private static final float SINK = 0.6F;
    private static final float HOP = 0.28F;
    private static final float SCREW = 2.1F;
    private static final float TIP = 0.7F;
    private static final float FLASH = 0.2F;
    /** Spread of the per-brick start times inside one block, so a block never leaves as a lump. */
    private static final float SUB_SPREAD = 7.0F;

    /** How fast the shudder's crest travels outward, in ticks per block. */
    private static final float WAVE_SPEED = 2.2F;
    /** Width of the crest. Narrow, or the whole wall swells at once and nothing reads as moving. */
    private static final float WAVE_SIGMA = 3.4F;
    private static final float RIPPLE_OUT = 0.22F;
    private static final float RIPPLE_SPIN = 0.30F;

    private final ModelPart brick;

    public DiagonArchwayRenderer(BlockEntityRendererProvider.Context context) {
        MeshDefinition mesh = new MeshDefinition();
        // One brick: half a block across, a quarter up, the full thickness of the wall.
        mesh.getRoot().addOrReplaceChild("brick", CubeListBuilder.create()
                .texOffs(0, 0).addBox(0.0F, 0.0F, 0.0F, 8.0F, 4.0F, 16.0F), PartPose.ZERO);
        this.brick = LayerDefinition.create(mesh, 64, 32).bakeRoot().getChild("brick");
    }

    /**
     * Only the brick running the sequence has anything to draw -- every other brick of the wall
     * sits at {@code ticks = -1} and returns immediately from {@link #render}. Answering true for
     * all of them put every brick of every archway wall (thirteen thousand in the old template) in
     * the always-render list, each paying a light lookup every frame for nothing. The wall is still
     * drawn by its real blocks when idle, and the section recompiles when the phase changes.
     */
    @Override
    public boolean shouldRenderOffScreen(DiagonArchwayBlockEntity wall) {
        return wall.ticks() >= 0;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    /** Deterministic 0..1 hash, so a brick behaves the same on every client and on every replay. */
    private static float hash(int a, int b, int c, int salt) {
        // 0x9E3779B1 rather than 2654435761: the same constant, written so it fits in an int.
        int h = (a * 73856093) ^ (b * 19349663) ^ (c * 83492791) ^ (salt * 0x9E3779B1);
        h &= 0x7FFFFFFF;
        h = ((h ^ (h >> 13)) * 1274126177) & 0x7FFFFFFF;
        return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0F;
    }

    @Override
    public void render(DiagonArchwayBlockEntity wall, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        int ticks = wall.ticks();
        if (ticks < 0 || wall.panelCells().isEmpty() || wall.getLevel() == null) {
            return;
        }
        // Closing counts down, so the fraction between two client ticks runs the other way too.
        // A held wall is not advancing, so there is no fraction of a tick to add.
        float t = wall.closing()
                ? Mth.clamp(ticks - partialTick, 0.0F, DiagonArchwayBlockEntity.TOTAL)
                : wall.holding() ? ticks
                : Math.min(ticks + partialTick, DiagonArchwayBlockEntity.TOTAL);
        if (t <= DiagonArchwayBlockEntity.GLOW) {
            return;                     // the seam beat: the real blocks are still drawing themselves
        }

        BlockPos origin = wall.getBlockPos();
        int aAxis = wall.acrossAxis();
        int nAxis = wall.normalAxis();
        int centre = wall.archCentre();
        int bottom = wall.archBottom();
        int tapA = axis(origin, aAxis);
        int tapY = origin.getY();

        VertexConsumer buffer = buffers.getBuffer(RenderType.entitySolid(TEXTURE));

        // The `light` argument is sampled at the block entity's own position, which is inside a
        // solid wall and therefore pitch dark -- so every brick drew black. Sample the air on
        // either side of the wall instead and take the brighter, which is the light a brick
        // pulled out of that wall would actually be standing in.
        int[] nOff = new int[3];
        nOff[nAxis] = 1;
        BlockPos outA = origin.offset(nOff[0], nOff[1], nOff[2]);
        BlockPos outB = origin.offset(-nOff[0], -nOff[1], -nOff[2]);
        int lit = Math.max(light,
                Math.max(LevelRenderer.getLightColor(wall.getLevel(), outA),
                        LevelRenderer.getLightColor(wall.getLevel(), outB)));

        for (BlockPos cell : wall.panelCells()) {
            // Once a cell's real block has gone back to being drawn normally there is nothing left
            // for the renderer to do with it. This also covers a wall reloaded mid-sequence.
            var state = wall.getLevel().getBlockState(cell);
            if (!(state.getBlock() instanceof DiagonArchwayBlock)
                    || state.getValue(DiagonArchwayBlock.PHASE) != DiagonArchwayBlock.MOVING) {
                continue;
            }

            int cellA = axis(cell, aAxis);
            int du = cellA - tapA;                  // from the tapped brick: sets timing
            int dv = cell.getY() - tapY;
            int u = cellA - centre;                 // from the arch centre: sets direction
            int v = cell.getY() - bottom;
            boolean leaving = DiagonArchwayBlockEntity.isArchCell(u, v);
            float dist = Mth.sqrt((float) (du * du + dv * dv));

            for (int sub = 0; sub < ACROSS * UP; sub++) {
                int sa = sub % ACROSS;
                int sv = sub / ACROSS;

                // Where this brick sits inside its block, relative to the block's centre.
                float inAcross = sa * (1.0F / ACROSS) + (0.5F / ACROSS) - 0.5F;
                float inUp = sv * (1.0F / UP) + (0.5F / UP) - 0.5F;

                float da = 0.0F, dy = 0.0F, dn = 0.0F, spin = 0.0F, tip = 0.0F;
                boolean hot = false;

                if (leaving) {
                    float jitter = hash(cell.getX(), cell.getY() * 8 + sub, cell.getZ(), 7);
                    float t0 = DiagonArchwayBlockEntity.PEEL_START
                            + DiagonArchwayBlockEntity.brickDelay(du, dv)
                            + jitter * SUB_SPREAD;
                    float p = (t - t0) / (float) DiagonArchwayBlockEntity.TRAVEL;
                    if (p >= 1.0F) {
                        continue;                   // gone into the flank
                    }
                    if (p > 0.0F) {
                        float e = p * p * (3.0F - 2.0F * p);      // smoothstep: masonry has weight
                        // Bricks on the centre line have no side to prefer, so they alternate by
                        // height and by which half of the block they are in -- otherwise the middle
                        // column all goes the same way and the arch unzips instead of parting.
                        float dir = u != 0 ? Math.signum(u)
                                : (((v + sa) & 1) == 0 ? 1.0F : -1.0F);
                        float reach = (DiagonArchwayBlockEntity.HALF_WIDTH + 1 - Math.abs(u))
                                + CLEARANCE + jitter;
                        da = dir * e * reach;
                        dn = -e * SINK * (0.6F + jitter * 0.8F);
                        dy = Mth.sin((float) Math.PI * e) * HOP * (0.5F + jitter);
                        spin = dir * e * SCREW * (0.7F + jitter * 0.6F);
                        tip = e * TIP * (jitter * 2.0F - 1.0F);
                        hot = p < FLASH;
                    }
                } else {
                    // The shudder: a crest rolls outward and each brick leans out and back as it
                    // passes, then everything settles before the sequence ends.
                    float wave = t - DiagonArchwayBlockEntity.GLOW - dist * WAVE_SPEED;
                    float env = (float) Math.exp(-(wave * wave) / (2.0F * WAVE_SIGMA * WAVE_SIGMA));
                    float settle = Mth.clamp(
                            (DiagonArchwayBlockEntity.TOTAL - t) / (float) DiagonArchwayBlockEntity.SETTLE,
                            0.0F, 1.0F);
                    float jitter = hash(cell.getX(), cell.getY() * 8 + sub, cell.getZ(), 13);
                    float amp = env * settle * (0.5F + jitter);
                    // A brick at rest is still drawn, in place. The real block it belongs to is
                    // MOVING for the whole animation -- RenderShape.INVISIBLE -- so there is no
                    // "real block" underneath to fall back on: skipping a resting brick leaves a hole
                    // in the surround, which is what a wall that was opening (or, on closing, the
                    // whole surround from the first frame) looked like. Below the threshold the
                    // pose is simply the rest pose.
                    if (amp >= 0.002F) {
                        dn = amp * RIPPLE_OUT;
                        spin = amp * RIPPLE_SPIN * (jitter * 2.0F - 1.0F);
                        tip = amp * RIPPLE_SPIN * (hash(cell.getX(), sub, cell.getZ(), 19) - 0.5F);
                        hot = env > 0.75F;
                    }
                }

                float[] offset = new float[3];
                offset[aAxis] = inAcross + da;
                offset[nAxis] = dn;
                offset[1] = inUp + dy;

                pose.pushPose();
                pose.translate(cell.getX() - origin.getX() + 0.5F + offset[0],
                        cell.getY() - origin.getY() + 0.5F + offset[1],
                        cell.getZ() - origin.getZ() + 0.5F + offset[2]);
                // The box is built width-along-X, depth-along-Z. If the wall faces X instead, turn
                // it a quarter so its long face lies in the wall rather than across it.
                if (nAxis == 0) {
                    pose.mulPose(Axis.YP.rotationDegrees(90.0F));
                }
                if (spin != 0.0F) {
                    pose.mulPose(Axis.YP.rotation(spin));
                }
                if (tip != 0.0F) {
                    pose.mulPose(Axis.XP.rotation(tip));
                }
                // Centre the box on the point we just moved to: it is 0.5 x 0.25 x 1.0 blocks.
                pose.translate(-0.25F, -0.125F, -0.5F);

                // A brick draws at full brightness while the crest is on it, or for the first fifth
                // of its travel. It is the trick the vault door uses on its ripple, and it is what
                // stops the wall reading as blocks merely sliding about.
                this.brick.render(pose, buffer, hot ? LightTexture.FULL_BRIGHT : lit,
                        OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }
    }

    private static int axis(BlockPos pos, int which) {
        return which == 0 ? pos.getX() : which == 1 ? pos.getY() : pos.getZ();
    }
}
