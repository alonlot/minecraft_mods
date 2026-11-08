package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.ChamberDoorBlock;
import com.hogwartsmagic.block.ChamberDoorLeafBlock;
import com.hogwartsmagic.block.entity.ChamberDoorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Draws the Chamber door while it is somewhere other than its blocks.
 *
 * <p>Everything here is the real block models, posed. The leaf is drawn as its own chiseled tuff,
 * the bolt sockets as their blackstone, the bolt heads as the lightning rods they were, so the moving
 * door is exactly the door that was standing there a tick before. Only the serpent is new geometry:
 * a chain of small stone segments with two green eyes, which does not exist as blocks at all.
 *
 * <p>The door moves as one rigid body: tremble, rock loose, then roll sideways into the wall like
 * a millstone, turning by the distance over its radius so that it rolls and does not slide. That is
 * one transform for the whole leaf, built from the tick count alone, so a correction from the server
 * never makes the stone jump. Once it is inside the wall nothing is drawn at all.
 *
 * <p>Directions are measured, not assumed. The block entity sends the door's two in-plane axes and
 * which way the hall is; the door rolls along whichever in-plane axis is horizontal.
 */
@OnlyIn(Dist.CLIENT)
public class ChamberDoorRenderer implements BlockEntityRenderer<ChamberDoorBlockEntity> {

    private final BlockRenderDispatcher blocks;

    public ChamberDoorRenderer(BlockEntityRendererProvider.Context context) {
        this.blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public boolean shouldRenderOffScreen(ChamberDoorBlockEntity door) {
        return true;
    }

    @Override
    public int getViewDistance() {
        // The door is meant to be seen from the far end of a hundred-block hall.
        return 128;
    }

    private static Vector3f unit(int axis, float sign) {
        return new Vector3f(axis == 0 ? sign : 0.0F, axis == 1 ? sign : 0.0F, axis == 2 ? sign : 0.0F);
    }

    private static int axis(BlockPos p, int a) {
        return a == 0 ? p.getX() : a == 1 ? p.getY() : p.getZ();
    }

    private static float smooth(float s) {
        s = Mth.clamp(s, 0.0F, 1.0F);
        return s * s * (3.0F - 2.0F * s);
    }

    /** The larger of two packed light values, block and sky light taken separately. */
    private static int brighter(int a, int b) {
        return LightTexture.pack(Math.max(LightTexture.block(a), LightTexture.block(b)),
                Math.max(LightTexture.sky(a), LightTexture.sky(b)));
    }

    @Override
    public void render(ChamberDoorBlockEntity door, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        Level level = door.getLevel();
        int ticks = door.ticks();
        if (level == null || ticks < 0 || door.leafPositions().isEmpty()) {
            return;
        }
        final int total = ChamberDoorBlockEntity.TOTAL;
        final int awaken = ChamberDoorBlockEntity.AWAKEN;
        final int rimEnd = awaken + ChamberDoorBlockEntity.RIM;
        final int unseatEnd = rimEnd + ChamberDoorBlockEntity.UNSEAT;
        float t = ticks >= total ? total : Math.min(ticks + partialTick, total);

        BlockPos origin = door.getBlockPos();
        int uAxis = door.uAxis();
        int vAxis = door.vAxis();
        int nAxis = door.normalAxis();
        int acrossAxis = uAxis == 1 ? vAxis : uAxis;
        Vector3f inward = unit(nAxis, door.inward());
        Vector3f across = unit(acrossAxis, 1.0F);

        List<BlockPos> leaf = door.leafPositions();
        List<BlockPos> studs = door.studPositions();
        if (ticks >= total) {
            return;                             // rolled away inside the wall: nothing left to see
        }

        // How the leaf sits: how far it reaches across from the lock, and how deep it is.
        float maxAcross = 0.0F;
        float back = 0.0F;
        for (List<BlockPos> cells : List.of(leaf, studs)) {
            for (BlockPos cell : cells) {
                maxAcross = Math.max(maxAcross, Math.abs(axis(cell, acrossAxis) - axis(origin, acrossAxis)));
                back = Math.max(back, (float) door.depth(cell));
            }
        }

        // --- the door's one rigid motion ---------------------------------------------------
        // Tremble as the Parseltongue lands, dying away once the serpent is moving.
        float tremble = 0.0F;
        if (t < awaken) {
            tremble = 0.025F * (t / awaken) * Mth.sin(t * 2.3F);
        } else if (t < awaken + 10) {
            tremble = 0.025F * (1.0F - (t - awaken) / 10.0F) * Mth.sin(t * 2.3F);
        }
        // Unseat: the leaf rocks back and forth about its centre, breaking the seal.
        float unseat = Mth.clamp((t - rimEnd) / ChamberDoorBlockEntity.UNSEAT, 0.0F, 1.0F);
        float rock = t >= rimEnd && t < unseatEnd ? 0.045F * Mth.sin(unseat * 3.0F * Mth.TWO_PI) * (1.0F - unseat) : 0.0F;
        // Roll: sideways into the wall, turning by distance over radius, as a wheel does.
        float distance = (2.0F * maxAcross + 1.0F + (float) ChamberDoorBlockEntity.ROLL_EXTRA)
                * smooth((t - unseatEnd) / ChamberDoorBlockEntity.ROLL);
        float rollRadius = (float) door.maxRadius() + 0.5F;
        Vector3f normal = unit(nAxis, 1.0F);
        // Rolling toward -across, the bottom of the wheel must turn toward +across, so it does not slide.
        Vector3f down = new Vector3f(0.0F, -1.0F, 0.0F);
        float spinSign = new Vector3f(normal).cross(down).dot(across) >= 0.0F ? 1.0F : -1.0F;
        float spin = spinSign * distance / rollRadius + rock;
        // The leaf sinks a hair back from both faces of the wall as it comes loose, so that rolling
        // inside the wall its faces never lie in the same plane as the wall's own and flicker.
        float inset = smooth(unseat * 2.0F);
        float thickness = back + 1.0F;
        float squash = (thickness - 0.2F * inset) / thickness;

        Vector3f centre = new Vector3f(0.5F, 0.5F, 0.5F).add(new Vector3f(inward).mul(back * 0.5F));
        Vector3f shift = new Vector3f(across).mul(-distance).add(new Vector3f(inward).mul(tremble));
        Vector3f scale = new Vector3f(1.0F, 1.0F, 1.0F).add(new Vector3f(normal).mul(squash - 1.0F));
        Matrix4f doorMatrix = new Matrix4f()
                .translate(shift)
                .translate(centre)
                .rotate(new Quaternionf().rotationAxis(spin, normal))
                .scale(scale)
                .translate(-centre.x, -centre.y, -centre.z);

        // Light: what reaches the face of the door from the antechamber, or wherever the cell has
        // moved to, whichever is brighter. Inside its own cell, which is still solid stone, it is 0.
        int frontLight = LevelRenderer.getLightColor(level, origin.relative(door.outward()));

        pose.pushPose();
        pose.translate(shift.x, shift.y, shift.z);
        pose.translate(centre.x, centre.y, centre.z);
        pose.mulPose(new Quaternionf().rotationAxis(spin, normal));
        pose.scale(scale.x, scale.y, scale.z);
        pose.translate(-centre.x, -centre.y, -centre.z);

        BlockState leafState = ExampleMod.CHAMBER_DOOR_LEAF.get().defaultBlockState();
        BlockState studState = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        Vector3f scratch = new Vector3f();
        for (BlockPos cell : leaf) {
            BlockState here = level.getBlockState(cell);
            if (here.hasProperty(ChamberDoorLeafBlock.PHASE)
                    && here.getValue(ChamberDoorLeafBlock.PHASE) == ChamberDoorLeafBlock.SHUT) {
                continue;                       // the world is still drawing it: do not draw it twice
            }
            drawCell(level, pose, buffers, origin, cell, leafState, doorMatrix, frontLight, scratch);
        }
        for (BlockPos cell : studs) {
            if (level.getBlockState(cell).is(Blocks.POLISHED_BLACKSTONE)) {
                continue;
            }
            drawCell(level, pose, buffers, origin, cell, studState, doorMatrix, frontLight, scratch);
        }

        // The lock, lit from inside once it has been spoken to.
        BlockState lockState = ExampleMod.CHAMBER_DOOR.get().defaultBlockState()
                .setValue(ChamberDoorBlock.OPEN, Boolean.TRUE);
        int lockLight = t < rimEnd ? LightTexture.FULL_BRIGHT : brighter(frontLight, LightTexture.pack(9, 0));
        pose.pushPose();
        this.blocks.renderSingleBlock(lockState, pose, buffers, lockLight, OverlayTexture.NO_OVERLAY);
        pose.popPose();

        drawBolts(door, level, pose, buffers, origin, t, nAxis, inward, frontLight);
        if (t >= awaken && t <= rimEnd) {
            drawSerpent(door, pose, buffers, t, uAxis, vAxis, nAxis, frontLight);
        }
        pose.popPose();
    }

    private void drawCell(Level level, PoseStack pose, MultiBufferSource buffers, BlockPos origin, BlockPos cell,
                          BlockState state, Matrix4f doorMatrix, int frontLight, Vector3f scratch) {
        float x = cell.getX() - origin.getX();
        float y = cell.getY() - origin.getY();
        float z = cell.getZ() - origin.getZ();
        doorMatrix.transformPosition(scratch.set(x + 0.5F, y + 0.5F, z + 0.5F));
        BlockPos at = BlockPos.containing(origin.getX() + scratch.x, origin.getY() + scratch.y,
                origin.getZ() + scratch.z);
        int cellLight = brighter(frontLight, LevelRenderer.getLightColor(level, at));
        pose.pushPose();
        pose.translate(x, y, z);
        this.blocks.renderSingleBlock(state, pose, buffers, cellLight, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /**
     * The seven serpent bolts. Each one screws back into the door, a turn and a half, starting the
     * tick the serpent's head reaches it, and is gone into its socket a moment later.
     */
    private void drawBolts(ChamberDoorBlockEntity door, Level level, PoseStack pose, MultiBufferSource buffers,
                           BlockPos origin, float t, int nAxis, Vector3f inward, int frontLight) {
        BlockState rodState = Blocks.LIGHTNING_ROD.defaultBlockState()
                .setValue(LightningRodBlock.FACING, door.outward());
        for (BlockPos rod : door.rodPositions()) {
            if (level.getBlockState(rod).is(Blocks.LIGHTNING_ROD)) {
                continue;                       // still a real block on this client
            }
            float k = (t - (float) door.boltFireTick(rod)) / ChamberDoorBlockEntity.BOLT_TICKS;
            if (k >= 1.0F) {
                continue;                       // home in its socket, hidden by the stone round it
            }
            float e = smooth(k);
            // A drawn bolt flashes as it goes: that is the moment the serpent touched it.
            int rodLight = k > 0.0F && k < 0.5F ? LightTexture.FULL_BRIGHT : frontLight;
            pose.pushPose();
            pose.translate(rod.getX() - origin.getX() + 0.5F + inward.x * e,
                    rod.getY() - origin.getY() + 0.5F + inward.y * e,
                    rod.getZ() - origin.getZ() + 0.5F + inward.z * e);
            pose.mulPose(axisRotation(nAxis, e * 3.0F * Mth.PI));
            pose.translate(-0.5F, -0.5F, -0.5F);
            this.blocks.renderSingleBlock(rodState, pose, buffers, rodLight, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    /**
     * The serpent: a head and a tapering body that follow one path, a circuit of the rim and then a
     * spiral into the lock. The body weaves across the path, which is what reads as a snake and not
     * as a train of beads.
     */
    private void drawSerpent(ChamberDoorBlockEntity door, PoseStack pose, MultiBufferSource buffers, float t,
                             int uAxis, int vAxis, int nAxis, int frontLight) {
        Vector3f u = unit(uAxis, 1.0F);
        Vector3f v = unit(vAxis, 1.0F);
        Vector3f w = new Vector3f(u).cross(v);
        Quaternionf frame = new Quaternionf().setFromNormalized(
                new Matrix3f(u.x, u.y, u.z, v.x, v.y, v.z, w.x, w.y, w.z));
        // Which way local z points relative to "out of the door, toward the antechamber".
        float wOut = (nAxis == 0 ? w.x : w.z) * -door.inward();
        float depth = -0.72F;                   // just proud of the front face
        Vector3f out = unit(nAxis, depth * door.inward());

        BlockState body = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState eye = Blocks.EMERALD_BLOCK.defaultBlockState();
        int bodyLight = brighter(frontLight, LightTexture.pack(10, 0));
        double radius = door.serpentRadius();
        double head = ChamberDoorBlockEntity.headAt(t);
        int segments = ChamberDoorBlockEntity.SERPENT_SEGMENTS;
        double path = ChamberDoorBlockEntity.SERPENT_PATH;

        for (int k = 0; k <= segments; k++) {
            double theta = head - k * ChamberDoorBlockEntity.SERPENT_SPACING;
            if (theta < 0.0 || theta > path) {
                continue;
            }
            double[] p = ChamberDoorBlockEntity.pathPoint(theta, radius);
            double[] behind = ChamberDoorBlockEntity.pathPoint(Math.max(0.0, theta - 0.02), radius);
            float du = (float) (p[0] - behind[0]);
            float dv = (float) (p[1] - behind[1]);
            if (du * du + dv * dv < 1.0E-8F) {
                du = (float) -Math.sin(ChamberDoorBlockEntity.SERPENT_START);
                dv = (float) Math.cos(ChamberDoorBlockEntity.SERPENT_START);
            }
            float heading = (float) Math.atan2(dv, du);
            float r = (float) Math.hypot(p[0], p[1]);
            // Weave across the path; damped near the lock, where there is no room to weave.
            float weave = 0.16F * Mth.sin(k * 0.75F - t * 0.55F) * (k == 0 ? 0.4F : 1.0F)
                    * Mth.clamp(r / 1.5F, 0.0F, 1.0F);
            float pu = (float) p[0] + (r > 0.01F ? weave * (float) p[0] / r : 0.0F);
            float pv = (float) p[1] + (r > 0.01F ? weave * (float) p[1] / r : 0.0F);
            // The last stretch of the spiral: it shrinks away into the keyhole.
            float vanish = Mth.clamp((float) ((path - theta) / (Math.PI * 0.18)), 0.0F, 1.0F);
            if (vanish <= 0.01F) {
                continue;
            }

            pose.pushPose();
            pose.translate(0.5F + u.x * pu + v.x * pv + out.x,
                    0.5F + u.y * pu + v.y * pv + out.y,
                    0.5F + u.z * pu + v.z * pv + out.z);
            pose.mulPose(frame);
            pose.mulPose(Axis.ZP.rotation(heading));
            if (k == 0) {
                pose.pushPose();
                pose.scale(0.78F * vanish, 0.56F * vanish, 0.44F * vanish);
                pose.translate(-0.5F, -0.5F, -0.5F);
                this.blocks.renderSingleBlock(body, pose, buffers, bodyLight, OverlayTexture.NO_OVERLAY);
                pose.popPose();
                for (float side : new float[]{-1.0F, 1.0F}) {
                    pose.pushPose();
                    pose.translate(0.26F * vanish, side * 0.17F * vanish, wOut * 0.21F * vanish);
                    pose.scale(0.11F * vanish, 0.11F * vanish, 0.11F * vanish);
                    pose.translate(-0.5F, -0.5F, -0.5F);
                    this.blocks.renderSingleBlock(eye, pose, buffers, LightTexture.FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY);
                    pose.popPose();
                }
            } else {
                float size = 0.52F * (1.0F - 0.6F * k / (float) segments) * vanish;
                pose.scale(size * 1.3F, size, size * 0.85F);
                pose.translate(-0.5F, -0.5F, -0.5F);
                this.blocks.renderSingleBlock(body, pose, buffers, bodyLight, OverlayTexture.NO_OVERLAY);
            }
            pose.popPose();
        }
    }

    private static Quaternionf axisRotation(int axis, float radians) {
        if (axis == 0) {
            return Axis.XP.rotation(radians);
        }
        return axis == 1 ? Axis.YP.rotation(radians) : Axis.ZP.rotation(radians);
    }
}
