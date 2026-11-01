package com.hogwartsmagic.client.ambience;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ambience.AmbientEffect;
import com.hogwartsmagic.ambience.DoorSwing;
import com.hogwartsmagic.client.pensieve.PensieveClientState;
import com.hogwartsmagic.entity.MemoryDoorEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The world's door leaves (the castle's Great Hall portal): a client-only picture, so it works for everyone and can
 * never trap a player. Each {@code KIND_DOOR} effect within {@link #RANGE} blocks of the player is a puppet
 * {@link MemoryDoorEntity} (or the mirrored one when the effect's {@code count} is 1), made like a Pensieve actor: it is
 * never added to the level (no collision, no ticking, no hit box to click), drawn by the vanilla entity renderer with
 * the pose scaled {@link #SCALE} times, its swing carried by its yaw. The opening behind it is always empty.
 *
 * <p>The leaves open when any player in the level (you or anyone else the client can see) is within
 * {@link DoorSwing#TRIGGER_RANGE} blocks of the doorway, and close a while after the last one leaves
 * ({@link DoorSwing}). The doorway is the middle of the leaf pair: leaves with hinges within {@link #PAIR_REACH}
 * blocks of each other share it, so both swing together. Puppets go with the level (a dimension change, a logout),
 * with a memory (the world's doors are not drawn inside one) and when they are out of range. Render thread only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class WorldDoors {
    /** A leaf is a scale 4 door: 4 wide and 12 tall (the Sorting memory's own leaves). */
    private static final float SCALE = 4f;
    /** Puppets exist for doors within this many blocks, and are dropped a little beyond (so one on the edge does not flicker). */
    private static final double RANGE = 64.0, DROP_RANGE = 72.0;
    /** Two hinges this close (blocks, level) belong to one doorway. */
    private static final double PAIR_REACH = 12.0;
    /** Puppet ids are negative so they never meet a real entity's, and far from the Pensieve actors' (which count down from -1000). */
    private static final int FIRST_ID = -500_000_000;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Door> DOORS = new HashMap<>();
    /** Leaves whose renderer threw: left out until the level changes, so one fault is logged once, not every frame. */
    private static final Set<UUID> BROKEN = new HashSet<>();
    private static ClientLevel level;
    private static int nextId = FIRST_ID;

    private WorldDoors() {
    }

    /** One leaf: its effect, its puppet, its swing and the doorway it opens for. */
    private static final class Door {
        final AmbientEffect effect;
        final MemoryDoorEntity entity;
        final DoorSwing.State swing = new DoorSwing.State();
        final double doorwayX, doorwayZ;

        Door(AmbientEffect effect, MemoryDoorEntity entity, double doorwayX, double doorwayZ) {
            this.effect = effect;
            this.entity = entity;
            this.doorwayX = doorwayX;
            this.doorwayZ = doorwayZ;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel current = mc.level;
        if (current == null || mc.player == null || PensieveClientState.inMemory()) {
            clear();
            return;
        }
        if (current != level) {
            clear();
            level = current;
        }
        List<AmbientEffect> effects = WorldAmbience.ofKind(AmbientEffect.KIND_DOOR);
        if (effects.isEmpty()) {
            clear();
            return;
        }
        Vec3 eyes = mc.player.getEyePosition();
        sync(current, effects, eyes);
        long now = current.getGameTime();
        for (Door d : DOORS.values()) {
            DoorSwing.tick(d.swing, anyoneNear(current, d), now);
            MemoryDoorEntity e = d.entity;
            e.tickCount++;
            e.yRotO = e.getYRot();
            e.setYRot(DoorSwing.yaw(d.effect.yaw(), d.effect.count() == 1, d.swing.progress()));
        }
    }

    /** Makes a puppet for each door that came into range and drops those that left it or are no longer registered. */
    private static void sync(ClientLevel lvl, List<AmbientEffect> effects, Vec3 eyes) {
        for (Iterator<Door> it = DOORS.values().iterator(); it.hasNext(); ) {
            Door d = it.next();
            boolean registered = false;
            for (AmbientEffect e : effects) {
                if (e.equals(d.effect)) {
                    registered = true;
                    break;
                }
            }
            if (!registered || WorldAmbience.distanceSqr(d.effect, eyes) > DROP_RANGE * DROP_RANGE) {
                it.remove();
            }
        }
        for (AmbientEffect e : effects) {
            if (DOORS.containsKey(e.id()) || BROKEN.contains(e.id()) || WorldAmbience.distanceSqr(e, eyes) > RANGE * RANGE) continue;
            Door door = create(lvl, e, effects);
            if (door != null) {
                DOORS.put(e.id(), door);
            }
        }
    }

    private static Door create(ClientLevel lvl, AmbientEffect e, List<AmbientEffect> all) {
        boolean mirrored = e.count() == 1;
        MemoryDoorEntity entity = (mirrored ? ModEntities.MEMORY_DOOR_MIRRORED : ModEntities.MEMORY_DOOR).get().create(lvl);
        if (entity == null) return null;
        if (nextId < FIRST_ID - 100_000_000) nextId = FIRST_ID;
        entity.setId(nextId--);
        entity.setSilent(true);
        entity.noPhysics = true;
        entity.setNoGravity(true);
        entity.moveTo(e.x(), e.y(), e.z(), e.yaw(), 0f);
        entity.tickCount = 1;   // the leaf's renderer treats a lower tick count as "not placed yet"
        // The doorway: the middle of this leaf and of every leaf hinged within reach (its pair).
        double sx = 0, sz = 0;
        int n = 0;
        for (AmbientEffect other : all) {
            double dx = other.x() - e.x(), dz = other.z() - e.z();
            if (dx * dx + dz * dz > PAIR_REACH * PAIR_REACH) continue;
            double[] mid = DoorSwing.leafMid(other.x(), other.z(), other.yaw(), other.count() == 1);
            sx += mid[0];
            sz += mid[1];
            n++;
        }
        return new Door(e, entity, sx / n, sz / n);
    }

    /** Whether any player in the level stands within {@link DoorSwing#TRIGGER_RANGE} of this leaf's doorway. */
    private static boolean anyoneNear(ClientLevel lvl, Door d) {
        double y = d.effect.y();
        for (AbstractClientPlayer p : lvl.players()) {
            if (p.isSpectator() || p.getY() < y - 2.0 || p.getY() > y + 8.0) continue;
            double dx = p.getX() - d.doorwayX, dz = p.getZ() - d.doorwayZ;
            if (dx * dx + dz * dz <= DoorSwing.TRIGGER_RANGE * DoorSwing.TRIGGER_RANGE) return true;
        }
        return false;
    }

    /** Draws the leaves the way {@code MemoryActors} draws an actor: an identity stack, translated to the leaf, scaled. */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || DOORS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != level || PensieveClientState.inMemory()) return;
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        Vec3 cam = event.getCamera().getPosition();
        // The frame's partial tick, not event.getPartialTick() (see PensieveClientState#partial).
        float partial = PensieveClientState.partial();
        // Identity, deliberately: the camera rotation is already on RenderSystem's modelview stack.
        PoseStack pose = new PoseStack();
        List<Door> drawn = new ArrayList<>(DOORS.values());
        for (Door d : drawn) {
            MemoryDoorEntity e = d.entity;
            float yaw = Mth.rotLerp(partial, e.yRotO, e.getYRot());
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(e.getX(), e.getY() + 1.0, e.getZ()));
            pose.pushPose();
            try {
                pose.translate(e.getX() - cam.x, e.getY() - cam.y, e.getZ() - cam.z);
                pose.scale(SCALE, SCALE, SCALE);
                dispatcher.render(e, 0, 0, 0, yaw, partial, pose, buffer, light);
            } catch (RuntimeException ex) {
                // One broken leaf must not take the frame with it: it is dropped for good (until the level changes).
                LOGGER.warn("World door {} failed to render and is hidden", d.effect.id(), ex);
                DOORS.remove(d.effect.id());
                BROKEN.add(d.effect.id());
            } finally {
                pose.popPose();
            }
        }
        buffer.endBatch();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void clear() {
        DOORS.clear();
        BROKEN.clear();
        level = null;
    }
}
