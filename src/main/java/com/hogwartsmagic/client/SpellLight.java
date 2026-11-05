package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hogwartsmagic.client.SpellFxGeometry.vertex;

/**
 * Coloured light thrown on the ground and walls by spells: a beam lights the stone it flies past, a strike flares
 * the floor round it, the Killing Curse gathering stains the room green, Lumos casts a warm pool round you, two
 * locked beams light the dome they stand in. Vanilla light has no colour and does not move with a beam, so this
 * draws it: soft additive light on the surfaces near each source, fading with distance and with the angle the
 * surface turns from it, strongest in the dark and faint in full daylight, and dimmed by a Dementor's dread.
 *
 * <p>The light spreads from its source through open space (a flood fill through every block that isn't a full
 * cube), so it reaches only the surfaces it can actually see round a corner of the room it is in, never the far
 * side of a wall, and it finds the tops and sides of slabs, stairs and other part-blocks as well as full ones. The
 * surfaces each source can reach are found once a tick per block it sits in and reused every frame of that tick.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class SpellLight {
    /** How many sources are lit at once; the rest (the furthest) go without. */
    private static final int MAX_SOURCES = 10;
    private static final double MAX_DISTANCE = 64.0;
    /** Too faint to see once daylight and dread have had their say: not worth finding surfaces for. */
    private static final float MIN_STRENGTH = 0.06f;
    /** How many open blocks one source's light may spread through (a radius-8 sphere is about 2,100). */
    private static final int MAX_CELLS = 2400;
    /** How many boxes of a part-block's shape are lit (stairs are two, most others one). */
    private static final int MAX_BOXES = 6;

    /** One light: where, what colour, how far it reaches, how bright at its heart. */
    public record Light(Vec3 pos, float r, float g, float b, float radius, float intensity) {
    }

    /** Where each effect reports its lights. */
    @FunctionalInterface
    public interface Sink {
        void add(Vec3 pos, float r, float g, float b, float radius, float intensity);
    }

    /** A lit surface: four corners in world space, wound to face the open block it is seen from, and its normal. */
    private record Surface(Vec3[] corners, Vec3 normal, Vec3 centre) {
    }

    /** Surfaces found this tick, by source block and reach. */
    private static final Map<Long, List<Surface>> SURFACES = new HashMap<>();
    private static long surfacesTick = Long.MIN_VALUE;

    private SpellLight() {
    }

    private static List<Light> collect(float partial, double now, Vec3 camera) {
        List<Light> lights = new ArrayList<>();
        Sink sink = (pos, r, g, b, radius, intensity) -> {
            if (intensity > 0.01f && radius > 0.3f && pos.distanceToSqr(camera) < MAX_DISTANCE * MAX_DISTANCE) {
                lights.add(new Light(pos, r, g, b, Math.min(radius, 8.0f), Math.min(intensity, 1.0f)));
            }
        };
        SpellBeamClientState.lights(partial, now, sink);
        AvadaChargeClientState.lights(partial, now, sink);
        PrioriClient.lights(partial, now, sink);
        TravelFxClient.lights(partial, now, sink);
        CrucioClient.lights(partial, now, sink);
        AquaStreamClient.lights(partial, now, sink);
        lumos(partial, sink);
        if (lights.size() > MAX_SOURCES) {
            lights.sort((x, y) -> Double.compare(x.pos.distanceToSqr(camera), y.pos.distanceToSqr(camera)));
            return lights.subList(0, MAX_SOURCES);
        }
        return lights;
    }

    /** Your own Lumos: a warm pool of light round the wand. */
    private static void lumos(float partial, Sink sink) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.player.hasEffect(ExampleMod.LUMOS_EFFECT.getHolder().get())) {
            return;
        }
        Vec3 look = minecraft.player.getViewVector(partial);
        Vec3 tip = minecraft.player.getEyePosition(partial).add(look.scale(0.7)).add(0.0, -0.25, 0.0);
        sink.add(tip, 1.0f, 0.93f, 0.72f, 7.0f, 0.55f);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + partial;
        Vec3 camera = event.getCamera().getPosition();
        List<Light> lights = collect(partial, now, camera);
        if (lights.isEmpty()) {
            return;
        }
        if (level.getGameTime() != surfacesTick) {
            // Blocks can change between ticks; within one tick the surfaces round a block stay the same.
            SURFACES.clear();
            surfacesTick = level.getGameTime();
        }
        // A Dementor near you smothers light as it smothers everything else.
        float dread = 1.0f - 0.85f * DementorDread.dread();
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer out = buffer.getBuffer(ModRenderTypes.AVADA_LIGHT);
        for (Light light : lights) {
            BlockPos at = BlockPos.containing(light.pos);
            // Strong in the dark, faint where the world is already bright.
            float ambient = level.getMaxLocalRawBrightness(at) / 15.0f;
            float strength = light.intensity * (1.0f - 0.7f * ambient) * dread;
            if (strength <= MIN_STRENGTH) {
                continue;
            }
            int reach = (int) Math.ceil(light.radius);
            long key = at.asLong() * 31L + reach;
            List<Surface> surfaces = SURFACES.get(key);
            if (surfaces == null) {
                surfaces = flood(level, at, reach);
                SURFACES.put(key, surfaces);
            }
            for (Surface surface : surfaces) {
                drawSurface(out, m, surface, light, strength, camera);
            }
        }
        buffer.endBatch(ModRenderTypes.AVADA_LIGHT);
    }

    /**
     * Spread from {@code start} through every block that isn't a full cube, within {@code reach}, and collect the
     * surfaces facing into the space it reached: the faces of full cubes bordering it, and the boxes of the
     * part-blocks (slabs, stairs, carpets...) it touches.
     */
    private static List<Surface> flood(ClientLevel level, BlockPos start, int reach) {
        List<Surface> surfaces = new ArrayList<>();
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        LongOpenHashSet seen = new LongOpenHashSet();
        LongOpenHashSet lit = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        BlockPos origin = start;
        if (level.getBlockState(start).isSolidRender(level, start)) {
            // A source buried in a block (a strike on a wall) lights from the open side nearest it.
            origin = start.above();
        }
        queue.enqueue(origin.asLong());
        seen.add(origin.asLong());
        double reachSqr = (reach + 0.5) * (reach + 0.5);
        while (!queue.isEmpty() && seen.size() < MAX_CELLS) {
            long packed = queue.dequeueLong();
            cell.set(packed);
            for (Direction dir : Direction.values()) {
                next.setWithOffset(cell, dir);
                long nextPacked = next.asLong();
                BlockState state = level.getBlockState(next);
                if (state.isSolidRender(level, next)) {
                    // A full cube bordering the light: its face toward the open block is lit.
                    if (lit.add(nextPacked * 6L + dir.ordinal())) {
                        surfaces.add(face(next, dir.getOpposite()));
                    }
                    continue;
                }
                if (!state.isAir() && lit.add(nextPacked * 6L + dir.ordinal())) {
                    partBlock(level, next, state, dir.getOpposite(), surfaces);
                }
                if (next.distSqr(start) <= reachSqr && seen.add(nextPacked)) {
                    queue.enqueue(nextPacked);
                }
            }
        }
        return surfaces;
    }

    /** The sides of a part-block's boxes that face back toward the open block the light came from. */
    private static void partBlock(ClientLevel level, BlockPos pos, BlockState state, Direction toward,
                                  List<Surface> surfaces) {
        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty()) {
            return;
        }
        int boxes = 0;
        for (AABB box : shape.toAabbs()) {
            if (++boxes > MAX_BOXES) {
                return;
            }
            surfaces.add(boxFace(box.move(pos), toward));
        }
    }

    private static Surface face(BlockPos pos, Direction face) {
        return boxFace(new AABB(pos), face);
    }

    /** One face of a box, wound consistently, with its outward normal. */
    private static Surface boxFace(AABB b, Direction face) {
        Vec3[] corners;
        if (face == Direction.UP) {
            corners = new Vec3[]{new Vec3(b.minX, b.maxY, b.minZ), new Vec3(b.minX, b.maxY, b.maxZ),
                    new Vec3(b.maxX, b.maxY, b.maxZ), new Vec3(b.maxX, b.maxY, b.minZ)};
        } else if (face == Direction.DOWN) {
            corners = new Vec3[]{new Vec3(b.minX, b.minY, b.minZ), new Vec3(b.maxX, b.minY, b.minZ),
                    new Vec3(b.maxX, b.minY, b.maxZ), new Vec3(b.minX, b.minY, b.maxZ)};
        } else if (face == Direction.NORTH) {
            corners = new Vec3[]{new Vec3(b.minX, b.minY, b.minZ), new Vec3(b.minX, b.maxY, b.minZ),
                    new Vec3(b.maxX, b.maxY, b.minZ), new Vec3(b.maxX, b.minY, b.minZ)};
        } else if (face == Direction.SOUTH) {
            corners = new Vec3[]{new Vec3(b.minX, b.minY, b.maxZ), new Vec3(b.maxX, b.minY, b.maxZ),
                    new Vec3(b.maxX, b.maxY, b.maxZ), new Vec3(b.minX, b.maxY, b.maxZ)};
        } else if (face == Direction.WEST) {
            corners = new Vec3[]{new Vec3(b.minX, b.minY, b.minZ), new Vec3(b.minX, b.minY, b.maxZ),
                    new Vec3(b.minX, b.maxY, b.maxZ), new Vec3(b.minX, b.maxY, b.minZ)};
        } else {
            corners = new Vec3[]{new Vec3(b.maxX, b.minY, b.minZ), new Vec3(b.maxX, b.maxY, b.minZ),
                    new Vec3(b.maxX, b.maxY, b.maxZ), new Vec3(b.maxX, b.minY, b.maxZ)};
        }
        Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        Vec3 centre = corners[0].add(corners[2]).scale(0.5);
        return new Surface(corners, normal, centre);
    }

    /** Light one surface, each corner as bright as its distance and its angle to the source allow. */
    private static void drawSurface(VertexConsumer out, Matrix4f m, Surface surface, Light light, float strength,
                                    Vec3 camera) {
        Vec3 normal = surface.normal;
        if (light.pos.subtract(surface.centre).dot(normal) <= 0.0) {
            return; // facing away
        }
        float[] alpha = new float[4];
        boolean any = false;
        for (int i = 0; i < 4; i++) {
            Vec3 toLight = light.pos.subtract(surface.corners[i]);
            double distance = toLight.length();
            double falloff = Math.max(0.0, 1.0 - distance / light.radius);
            double facing = Math.max(0.0, toLight.dot(normal) / Math.max(1.0E-6, distance));
            alpha[i] = (float) (strength * falloff * falloff * (0.35 + 0.65 * facing));
            any |= alpha[i] > 0.004f;
        }
        if (!any) {
            return;
        }
        // Lifted off the surface a little more the further it is, so depth precision never lets it shimmer.
        double lift = 0.008 + surface.centre.distanceTo(camera) * 0.0004;
        Vec3 offset = normal.scale(lift);
        for (int i = 0; i < 4; i++) {
            vertex(out, m, surface.corners[i].add(offset).subtract(camera), light.r, light.g, light.b, alpha[i]);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SURFACES.clear();
        surfacesTick = Long.MIN_VALUE;
    }
}
