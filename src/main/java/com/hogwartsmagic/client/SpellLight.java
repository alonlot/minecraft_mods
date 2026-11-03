package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

import static com.hogwartsmagic.client.SpellFxGeometry.vertex;

/**
 * Coloured light thrown on the ground and walls by spells: a beam lights the stone it flies past, a strike flares
 * the floor round it, the Killing Curse gathering stains the room green, Lumos casts a warm pool round you, two
 * locked beams light the dome they stand in. Vanilla light has no colour and does not move with a beam, so this
 * draws it: soft additive light on the exposed faces of solid blocks near each source, fading with distance and
 * with the angle the face turns from it, strongest in the dark and faint in full daylight.
 *
 * <p>Sources come from each effect's own state ({@link #collect}); faces are found fresh each frame within a few
 * blocks of each source, so nothing is cached and nothing goes stale.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class SpellLight {
    /** How many sources are lit at once; the rest (the furthest) go without. */
    private static final int MAX_SOURCES = 12;
    private static final double MAX_DISTANCE = 64.0;
    private static final float FACE_OFFSET = 0.01f;

    /** One light: where, what colour, how far it reaches, how bright at its heart. */
    public record Light(Vec3 pos, float r, float g, float b, float radius, float intensity) {
    }

    /** Where each effect reports its lights. */
    @FunctionalInterface
    public interface Sink {
        void add(Vec3 pos, float r, float g, float b, float radius, float intensity);
    }

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
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer out = buffer.getBuffer(ModRenderTypes.AVADA_LIGHT);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        for (Light light : lights) {
            // Strong in the dark, faint where the world is already bright.
            BlockPos at = BlockPos.containing(light.pos);
            float ambient = level.getMaxLocalRawBrightness(at) / 15.0f;
            float strength = light.intensity * (1.0f - 0.7f * ambient);
            if (strength <= 0.01f) {
                continue;
            }
            int reach = Mth.ceil(light.radius);
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dy = -reach; dy <= reach; dy++) {
                    for (int dz = -reach; dz <= reach; dz++) {
                        cursor.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                        if (cursor.distToCenterSqr(light.pos) > (light.radius + 1.0) * (light.radius + 1.0)) {
                            continue;
                        }
                        BlockState state = level.getBlockState(cursor);
                        if (state.isAir() || !state.isSolidRender(level, cursor)) {
                            continue;
                        }
                        for (Direction face : Direction.values()) {
                            neighbour.setWithOffset(cursor, face);
                            if (level.getBlockState(neighbour).isSolidRender(level, neighbour)) {
                                continue;
                            }
                            drawFace(out, m, cursor, face, light, strength, camera);
                        }
                    }
                }
            }
        }
        buffer.endBatch(ModRenderTypes.AVADA_LIGHT);
    }

    /** Light one block face, each corner as bright as its distance and its angle to the source allow. */
    private static void drawFace(VertexConsumer out, Matrix4f m, BlockPos pos, Direction face, Light light,
                                 float strength, Vec3 camera) {
        Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        Vec3 centre = Vec3.atCenterOf(pos).add(normal.scale(0.5));
        if (light.pos.subtract(centre).dot(normal) <= 0.0) {
            return; // facing away
        }
        Vec3[] corners = corners(pos, face);
        float[] alpha = new float[4];
        boolean any = false;
        for (int i = 0; i < 4; i++) {
            Vec3 toLight = light.pos.subtract(corners[i]);
            double distance = toLight.length();
            double falloff = Math.max(0.0, 1.0 - distance / light.radius);
            double facing = Math.max(0.0, toLight.dot(normal) / Math.max(1.0E-6, distance));
            alpha[i] = (float) (strength * falloff * falloff * (0.35 + 0.65 * facing));
            any |= alpha[i] > 0.004f;
        }
        if (!any) {
            return;
        }
        Vec3 lift = normal.scale(FACE_OFFSET);
        for (int i = 0; i < 4; i++) {
            vertex(out, m, corners[i].add(lift).subtract(camera), light.r, light.g, light.b, alpha[i]);
        }
    }

    /** The four corners of a block face, wound consistently. */
    private static Vec3[] corners(BlockPos pos, Direction face) {
        double x0 = pos.getX();
        double y0 = pos.getY();
        double z0 = pos.getZ();
        double x1 = x0 + 1.0;
        double y1 = y0 + 1.0;
        double z1 = z0 + 1.0;
        return faceCorners(face, x0, y0, z0, x1, y1, z1);
    }

    /** Ordered if-chain, not a switch: switch-on-enum has failed to load in this mod's render code before. */
    private static Vec3[] faceCorners(Direction face, double x0, double y0, double z0, double x1, double y1, double z1) {
        if (face == Direction.UP) {
            return new Vec3[]{new Vec3(x0, y1, z0), new Vec3(x0, y1, z1), new Vec3(x1, y1, z1), new Vec3(x1, y1, z0)};
        }
        if (face == Direction.DOWN) {
            return new Vec3[]{new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y0, z1), new Vec3(x0, y0, z1)};
        }
        if (face == Direction.NORTH) {
            return new Vec3[]{new Vec3(x0, y0, z0), new Vec3(x0, y1, z0), new Vec3(x1, y1, z0), new Vec3(x1, y0, z0)};
        }
        if (face == Direction.SOUTH) {
            return new Vec3[]{new Vec3(x0, y0, z1), new Vec3(x1, y0, z1), new Vec3(x1, y1, z1), new Vec3(x0, y1, z1)};
        }
        if (face == Direction.WEST) {
            return new Vec3[]{new Vec3(x0, y0, z0), new Vec3(x0, y0, z1), new Vec3(x0, y1, z1), new Vec3(x0, y1, z0)};
        }
        return new Vec3[]{new Vec3(x1, y0, z0), new Vec3(x1, y1, z0), new Vec3(x1, y1, z1), new Vec3(x1, y0, z1)};
    }
}
