package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.MemoryBlockEntity;
import com.hogwartsmagic.entity.MemoryBroomEntity;
import com.hogwartsmagic.entity.MemoryRowboatEntity;
import com.hogwartsmagic.entity.OwlEntity;
import com.hogwartsmagic.client.renderer.PetrifiedRemainsRenderer;
import com.hogwartsmagic.mixin.client.LivingEntityUseAccessor;
import com.hogwartsmagic.pensieve.scene.EyeEase;
import com.hogwartsmagic.pensieve.scene.Airborne;
import com.hogwartsmagic.pensieve.scene.BroomFlight;
import com.hogwartsmagic.pensieve.scene.Vehicle;
import com.hogwartsmagic.pensieve.scene.EquipTimeline;
import com.hogwartsmagic.pensieve.scene.HumanMotion;
import com.hogwartsmagic.pensieve.scene.Rowboat;
import com.hogwartsmagic.pensieve.scene.SceneNames;
import com.hogwartsmagic.mixin.client.PlayerModelPartsAccessor;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The people and creatures inside a memory. Each scene actor is a client-only puppet entity that is <b>never added
 * to the level</b>: it does not tick, think, collide, make sounds or appear in any server-side list, so there is
 * nothing for a dedicated server (or another viewer) to know about it. Every viewer's client computes every actor
 * from the shared scene clock alone, so two viewers see the same film.
 *
 * <p>{@link #tick()} advances the puppets one client tick at a time (position, facing, walk cycle, timed actions);
 * {@link #renderAll} draws them at {@code RenderLevelStageEvent.AFTER_ENTITIES}, with the vanilla entity renderers
 * interpolating between the last two ticks exactly as for a real entity. Poses, anims and limb tracks (Task 10)
 * and casts (Task 11) build on {@link ActorRuntime}.
 */
public final class MemoryActors {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float SWING_TICKS = 6;
    private static final float USE_TICKS = 32;
    private static final int DEATH_TICKS = 20;
    private static final int HURT_TICKS = 10;
    /** An ignited puppet burns this long; nothing ticks it down, so in practice until it dies or appears again. */
    private static final int IGNITE_FIRE_TICKS = 1_000_000;
    private static final DustParticleOptions DARK_SMOKE = new DustParticleOptions(new Vector3f(0.04f, 0.04f, 0.06f), 1.6f);
    private static final DustParticleOptions SILVER_SMOKE = new DustParticleOptions(new Vector3f(0.78f, 0.8f, 0.88f), 1.3f);

    private static final List<ActorRuntime> ACTORS = new ArrayList<>();
    private static final Map<Entity, ActorRuntime> BY_ENTITY = new IdentityHashMap<>();
    private static ClientLevel level;
    private static BlockPos origin;
    /** The scene being played: vehicles and their riders are placed from it (pure, see Vehicle). */
    private static SceneDef scene;
    /** Puppet ids are negative so they can never collide with a real entity's id. */
    private static int nextId = -1000;
    /** True during the second (overlay) pass of a petrified actor, so its name tag is not drawn twice. */
    private static boolean overlayPass;

    private MemoryActors() {
    }

    // ---- Lifecycle --------------------------------------------------------------------------------------------

    /** Build a puppet for every actor of {@code scene}. Called as PLAY begins. */
    public static void start(SceneDef scene, BlockPos at, ClientLevel clientLevel) {
        clear();
        ActorSkins.clearUsernames();
        if (scene == null || at == null || clientLevel == null) return;
        level = clientLevel;
        origin = at;
        MemoryActors.scene = scene;
        MemoryEvents.start(scene, at, clientLevel);
        for (SceneDef.ActorDef def : scene.actors()) {
            try {
                ActorRuntime a = create(def, clientLevel);
                if (a != null) {
                    a.resolvePoses(scene.poses());
                    ACTORS.add(a);
                    BY_ENTITY.put(a.entity, a);
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Pensieve: could not create actor '{}' of memory '{}': {}", def.id(), scene.id(), e.toString());
            }
        }
        if (PensieveClientState.preview()) PartCheck.run(scene, ACTORS);
    }

    /** Forget every puppet. Safe to call at any time, including when there are none. */
    public static void clear() {
        MemoryEvents.clear();
        ACTORS.clear();
        BY_ENTITY.clear();
        com.hogwartsmagic.client.MemoryDoorRenderer.clearState();
        com.hogwartsmagic.client.MemoryBlockRenderer.clearCache();
        level = null;
        origin = null;
        scene = null;
        overlayPass = false;
    }

    private static ActorRuntime create(SceneDef.ActorDef def, ClientLevel lvl) {
        UUID uuid = UUID.nameUUIDFromBytes(("pensieve:" + def.id()).getBytes(StandardCharsets.UTF_8));
        Entity entity;
        boolean player = "player".equals(def.type()) || "minecraft:player".equals(def.type());
        if (player) {
            RemotePlayer p = new RemotePlayer(lvl, new GameProfile(uuid, profileName(def)));
            // Hat, jacket, sleeves and pants layers: a vanilla remote player gets these from its synched byte.
            p.getEntityData().set(PlayerModelPartsAccessor.hogwartsmagic$modelParts(), (byte) 0x7F);
            entity = p;
        } else {
            Optional<EntityType<?>> type = EntityType.byString(def.type());
            if (type.isEmpty() && !def.type().contains(":")) type = EntityType.byString(ExampleMod.MODID + ":" + def.type());
            if (type.isEmpty()) {
                LOGGER.warn("Pensieve: actor '{}' has unknown entity type '{}'", def.id(), def.type());
                return null;
            }
            entity = type.get().create(lvl);
            if (entity == null) {
                LOGGER.warn("Pensieve: actor '{}': entity type '{}' cannot be created", def.id(), def.type());
                return null;
            }
            entity.setUUID(uuid);
            // The flutter phase comes from the actor id: the same every replay (the entity id is not).
            if (entity instanceof com.hogwartsmagic.entity.MemoryLetterEntity letter) {
                letter.flutterSeed = com.hogwartsmagic.pensieve.scene.LetterFlutter.seedFor(def.id());
            }
            if (entity instanceof MemoryBlockEntity block) {
                BlockState state = MemoryBlockEntity.parseState(def.block());
                if (state == null) {
                    LOGGER.warn("Pensieve: actor '{}' has a block '{}' the game does not know; it shows nothing",
                            def.id(), def.block());
                }
                block.setBlockState(state);
            }
        }
        entity.setId(nextId--);
        entity.setSilent(true);
        entity.noPhysics = true;
        entity.setNoGravity(true);
        if (entity instanceof Mob m) m.setNoAi(true);
        for (Map.Entry<String, String> item : def.items().entrySet()) {
            EquipmentSlot slot = slot(item.getKey());
            if (slot != null && entity instanceof LivingEntity l) l.setItemSlot(slot, stack(item.getValue()));
        }
        List<SceneDef.ActionCue> sorted = new ArrayList<>(def.actions());
        sorted.sort(Comparator.comparingDouble(SceneDef.ActionCue::t));
        ActorRuntime rt = new ActorRuntime(def, entity, player, sorted);
        rt.wornItems.putAll(def.items());
        // The vanilla idle arm bob runs off tickCount: start each puppet at its own age so they do not bob in unison.
        if (player) entity.tickCount = (int) (rt.seed & 0x3FF);
        return rt;
    }

    /** A valid Minecraft user name for the puppet's profile (the scene's own display name is shown instead). */
    private static String profileName(SceneDef.ActorDef def) {
        String src = def.name() != null ? def.name() : def.id();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < src.length() && sb.length() < 16; i++) {
            char c = src.charAt(i);
            sb.append(Character.isLetterOrDigit(c) && c < 128 ? c : '_');
        }
        return sb.isEmpty() ? "Actor" : sb.toString();
    }

    private static EquipmentSlot slot(String key) {
        if ("mainhand".equals(key)) return EquipmentSlot.MAINHAND;
        if ("offhand".equals(key)) return EquipmentSlot.OFFHAND;
        if ("head".equals(key)) return EquipmentSlot.HEAD;
        if ("chest".equals(key)) return EquipmentSlot.CHEST;
        if ("legs".equals(key)) return EquipmentSlot.LEGS;
        if ("feet".equals(key)) return EquipmentSlot.FEET;
        return null;
    }

    private static ItemStack stack(String id) {
        if (id == null || id.isBlank()) return ItemStack.EMPTY;
        ResourceLocation rl = ResourceLocation.tryParse(id.contains(":") ? id : ExampleMod.MODID + ":" + id);
        Optional<Item> item = rl == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(rl);
        if (item.isEmpty() && !id.contains(":")) {
            ResourceLocation vanilla = ResourceLocation.tryParse("minecraft:" + id);
            if (vanilla != null) item = BuiltInRegistries.ITEM.getOptional(vanilla);
        }
        if (item.isEmpty()) LOGGER.warn("Pensieve: unknown item '{}' in a memory", id);
        return item.map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    // ---- Queries ----------------------------------------------------------------------------------------------

    public static boolean isActor(Entity entity) {
        return !BY_ENTITY.isEmpty() && BY_ENTITY.containsKey(entity);
    }

    public static ActorRuntime runtimeFor(Entity entity) {
        return BY_ENTITY.isEmpty() ? null : BY_ENTITY.get(entity);
    }

    /** Skin for an actor player, or null when {@code player} is not an actor (vanilla lookup applies). */
    public static PlayerSkin skinOf(AbstractClientPlayer player) {
        ActorRuntime a = runtimeFor(player);
        return a == null ? null : ActorSkins.skinFor(a.def.skin(), player.getUUID());
    }

    /** The actor's eye, relative to the memory's origin, or null when there is no such actor. */
    public static float[] actorEye(String id) {
        if (origin == null) return null;
        float partial = PensieveClientState.partial();
        for (ActorRuntime a : ACTORS) {
            if (!a.def.id().equals(id)) continue;
            Entity e = a.entity;
            double eye = eyeHeightOf(a) * a.def.scale();
            double ex = 0, ey = eye, ez = 0;
            // A broom rider's eye tilts with the broom about its root (the rotation its model is drawn with).
            org.joml.Quaternionf tilt = a.rideBroom || a.rideBroomO ? rideTilt(a, partial) : null;
            if (tilt != null) {
                org.joml.Vector3f v = tilt.transform(new org.joml.Vector3f(0, (float) eye, 0));
                ex = v.x;
                ey = v.y;
                ez = v.z;
            }
            return new float[]{
                    (float) (Mth.lerp(partial, e.xo, e.getX()) + ex - origin.getX()),
                    (float) (Mth.lerp(partial, e.yo, e.getY()) + ey - origin.getY()),
                    (float) (Mth.lerp(partial, e.zo, e.getZ()) + ez - origin.getZ())};
        }
        return null;
    }

    /**
     * The eye height a camera aims at: a player actor's eases between standing and crouching over
     * {@link EyeEase#TICKS} from its sneak/stand cues (vanilla swaps it in one frame, which jolts a following
     * camera); anything else keeps the entity's own.
     */
    private static float eyeHeightOf(ActorRuntime a) {
        if (!a.player) return a.entity.getEyeHeight();
        return EyeEase.eyeHeight(a.crouchFraction(PensieveClientState.sceneTime(PensieveClientState.partial())));
    }

    /**
     * The seat of an actor riding a broom at frame time t, or null when it rides no broom then: ActorPoser's rider
     * layer reads the broom's speed and pitch and the window's weight from it.
     */
    static Vehicle.Seat broomSeat(ActorRuntime a, float t) {
        if (scene == null || a.def.ride() == null) return null;
        Vehicle.Seat s = Vehicle.seat(scene, a.def, t);
        return s != null && s.broom() ? s : null;
    }

    /** The rowboats on screen, for the sea's depth lid ({@link RoughSeaRenderer}). */
    static List<ActorRuntime> rowboats() {
        List<ActorRuntime> out = null;
        for (ActorRuntime a : ACTORS) {
            if (a.visible && a.entity instanceof MemoryRowboatEntity) {
                if (out == null) out = new ArrayList<>(1);
                out.add(a);
            }
        }
        return out == null ? List.of() : out;
    }

    private static ActorRuntime find(String id) {
        if (id == null) return null;
        for (ActorRuntime a : ACTORS) {
            if (a.def.id().equals(id)) return a;
        }
        return null;
    }

    // ---- Ticking ----------------------------------------------------------------------------------------------

    public static void tick() {
        if (ACTORS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level || !PensieveClientState.inMemory()) {
            clear();
            return;
        }
        if (mc.isPaused()) return;
        float t = PensieveClientState.sceneTime(0);
        for (ActorRuntime a : ACTORS) {
            try {
                step(a, t);
            } catch (RuntimeException e) {
                LOGGER.warn("Pensieve: actor '{}' failed to update: {}", a.def.id(), e.toString());
            }
        }
    }

    /** A path that moves an actor farther than this in one tick is a cut, and the client does not interpolate it. */
    private static final double TELEPORT_BLOCKS = 2.0;

    private static void step(ActorRuntime a, float t) {
        Entity e = a.entity;
        LivingEntity l = a.living;
        boolean first = !a.placed;
        e.setOldPosAndRot();
        MemoryBlockEntity block = e instanceof MemoryBlockEntity b ? b : null;
        if (block != null) block.rollO = block.roll;
        MemoryRowboatEntity boat = e instanceof MemoryRowboatEntity rb ? rb : null;
        if (boat != null) {
            boat.pitchO = boat.pitch;
            boat.rollO = boat.roll;
        }
        MemoryBroomEntity broom = e instanceof MemoryBroomEntity mb ? mb : null;
        if (broom != null) {
            broom.pitchO = broom.pitch;
            broom.rollO = broom.roll;
        }
        a.ridePitchO = a.ridePitch;
        a.rideRollO = a.rideRoll;
        a.rideYawO = a.rideYaw;
        a.rideBroomO = a.rideBroom;
        a.riderSeatO = a.riderSeat;
        // A boat rides the sea, a broom flies its path and a rider sits on its vehicle: all placed from the scene
        // alone (Rowboat, BroomFlight, Vehicle; pure).
        Rowboat.Pose boatPose = boat != null && scene != null ? Rowboat.place(scene, a.def, t) : null;
        BroomFlight.Pose broomPose = broom != null && scene != null ? BroomFlight.place(scene, a.def, t) : null;
        Vehicle.Seat seat = boat == null && broom == null && scene != null && a.def.ride() != null
                ? Vehicle.seat(scene, a.def, t) : null;
        a.rideBroom = seat != null && seat.broom();
        if (a.rideBroom) {
            // The owl flight rule on the broom path: a flying rider sits, a landed one stands (no gait either way);
            // the life layer seat eases over 10 ticks.
            SceneDef.ActorDef v = Vehicle.vehicleOf(scene, a.def);
            a.broomFlying = v != null && BroomFlight.flying(v.path(), t, a.broomFlying);
            a.riderSeat = Mth.approach(a.riderSeat, a.broomFlying ? 1f : 0f, 0.1f);
        } else {
            a.broomFlying = false;
        }
        if (l != null) {
            l.yBodyRotO = l.yBodyRot;
            l.yHeadRotO = l.yHeadRot;
        }
        e.tickCount++;

        // Stand on the path before anything fires, so a first-tick effect appears at the actor, not the origin.
        if (first) {
            double[] p0 = root(a, t, boatPose, broomPose, seat);
            e.setPos(origin.getX() + p0[0], origin.getY() + p0[1], origin.getZ() + p0[2]);
        }

        // Timed actions whose moment has come.
        boolean fired = false;
        while (a.nextAction < a.actions.size() && a.actions.get(a.nextAction).t() <= t) {
            SceneDef.ActionCue cue = a.actions.get(a.nextAction++);
            fire(a, cue, cue.t() < t - 5, t);
            fired = true;
        }
        // Worn items are a pure function of time (EquipTimeline), so a late joiner matches a watcher.
        if (fired && l != null) {
            for (String slotName : SceneNames.EQUIP_SLOTS) {
                EquipmentSlot es = slot(slotName);
                String id = EquipTimeline.itemAt(a.actions, a.def.items(), slotName, t);
                if (es != null && id != null) {
                    l.setItemSlot(es, stack(id));
                    a.wornItems.put(slotName, id);
                }
            }
        }

        // Where it stands, and which way it faces.
        double[] p = root(a, t, boatPose, broomPose, seat);
        double x = origin.getX() + p[0];
        double y = origin.getY() + p[1];
        double z = origin.getZ() + p[2];
        if (first) {
            a.lastX = x;
            a.lastZ = z;
        }
        double dx = x - a.lastX;
        double dz = z - a.lastZ;
        boolean moving = dx * dx + dz * dz > 1.0e-6;
        a.lastX = x;
        a.lastZ = z;

        float pitch = 0;
        float lookYaw = 0;
        // A rider's look is relative to its vehicle inside its ride window and world space outside it (Vehicle).
        float[] lk = Vehicle.look(a.def, t);
        boolean hasLook = lk != null;
        // A rider's look yaw is relative to its vehicle; it rocks with the vehicle's pitch and roll (applied in renderAll).
        float yawBase = seat != null ? seat.vehicleYaw() : 0f;
        if (seat != null) {
            a.ridePitch = seat.pitch();
            a.rideRoll = seat.roll();
            a.rideYaw = seat.vehicleYaw();
        } else {
            a.ridePitch = 0;
            a.rideRoll = 0;
        }
        if (hasLook) {
            lookYaw = (lk.length > 0 ? lk[0] : 0) + yawBase;
            pitch = lk.length > 1 ? lk[1] : 0;
            if (block != null) block.roll = lk.length > 2 ? lk[2] : 0;
        } else {
            lookYaw = yawBase;
        }
        float target;
        // A broom rider turns exactly with its broom; anyone else with life lets the body follow the head.
        boolean follow = hasLook && a.lifeOn() && !a.rideBroom;
        boolean exact = boatPose != null || broomPose != null || (seat != null && !follow);
        if (broomPose != null) {
            // The broom faces exactly as BroomFlight says, so its riders and the drawn broom agree.
            target = broomPose.yaw();
            lookYaw = target;
            hasLook = true;
            pitch = 0;
            broom.pitch = broomPose.pitch();
            broom.roll = broomPose.roll();
        } else if (boatPose != null) {
            // The boat faces exactly as Rowboat says, so the seats and the sea's lid agree with the drawn hull.
            target = boatPose.yaw();
            lookYaw = target;
            hasLook = true;
            boat.pitch = boatPose.pitch();
            boat.roll = boatPose.roll();
        } else if (seat != null && !hasLook) {
            target = yawBase;                // facing along the boat
            hasLook = true;
        } else if (follow) {
            // The head goes where look says at once; the body follows a few ticks later (HumanMotion.followYaw).
            float[] win = Vehicle.window(a.def, t);
            target = (Float.isInfinite(win[0]) && Float.isInfinite(win[1]) ? HumanMotion.followYaw(a.def.look(), t)
                    : HumanMotion.followYaw(a.def.look(), t, win[0], win[1])) + yawBase;
        } else if (hasLook) {
            target = lookYaw;
        } else if (block != null) {
            target = 0;                  // a block never turns to face where it travels (a rolling ladder stays square)
        } else if (moving) {
            target = (float) (Mth.atan2(-dx, dz) * (180.0 / Math.PI));
        } else if (first) {
            target = headingAhead(a, t);
        } else {
            target = a.bodyYaw;
        }
        if (first || follow || exact) {
            a.bodyYaw = target;
        } else {
            a.bodyYaw += Mth.wrapDegrees(target - a.bodyYaw) * 0.3f;
        }
        a.headYaw = hasLook ? lookYaw : a.bodyYaw;
        a.bodyYaw = a.headYaw + Mth.clamp(Mth.wrapDegrees(a.bodyYaw - a.headYaw), -75f, 75f);

        double jx = x - e.xo;
        double jy = y - e.yo;
        double jz = z - e.zo;
        e.setPos(x, y, z);
        // An owl flies while its path moves and perches (wings folded) while it is held (Airborne, pure). Owls only:
        // other models read onGround too, and every other puppet has always drawn as airborne.
        if (e instanceof OwlEntity) e.setOnGround(!Airborne.airborne(a.def.path(), t, !first && !e.onGround()));
        if (!first && jx * jx + jy * jy + jz * jz > TELEPORT_BLOCKS * TELEPORT_BLOCKS) {
            // A jump on a cut (a swap, a park under the floor) is a teleport, not a run: do not lerp across it.
            e.xo = e.xOld = x;
            e.yo = e.yOld = y;
            e.zo = e.zOld = z;
        }
        e.setYRot(a.headYaw);
        e.setXRot(pitch);
        if (l != null) {
            l.setYBodyRot(a.bodyYaw);
            l.setYHeadRot(a.headYaw);
        }
        if (first) {
            e.setOldPosAndRot();
            if (block != null) block.rollO = block.roll;
            if (boat != null) {
                boat.pitchO = boat.pitch;
                boat.rollO = boat.roll;
            }
            if (broom != null) {
                broom.pitchO = broom.pitch;
                broom.rollO = broom.roll;
            }
            a.riderSeat = a.broomFlying ? 1f : 0f;
            a.riderSeatO = a.riderSeat;
            a.rideBroomO = a.rideBroom;
            a.ridePitchO = a.ridePitch;
            a.rideRollO = a.rideRoll;
            a.rideYawO = a.rideYaw;
            if (l != null) {
                l.yBodyRotO = l.yBodyRot;
                l.yHeadRotO = l.yHeadRot;
            }
            a.placed = true;
        }

        if (boatPose != null && !first) boatSpray(a, boat, boatPose, t);
        if (l == null) return;

        // Walk cycle, from the distance covered this tick (as LivingEntity#calculateEntityAnimation does). A living
        // player walks with HumanMotion's gait instead, locked to the distance along its path.
        if (a.lifeOn()) {
            l.walkAnimation.update(0f, 1f);
        } else {
            // A rider is carried, not walking: no leg swing however fast the boat goes.
            float stride = first || seat != null ? 0 : Math.min((float) Math.sqrt(dx * dx + dz * dz) * 4.0f, 1.0f);
            l.walkAnimation.update(stride, 0.4f);
        }

        // Swing.
        l.oAttackAnim = l.attackAnim;
        if (l.swinging) {
            l.swingTime++;
            if (l.swingTime >= SWING_TICKS) {
                l.swingTime = 0;
                l.swinging = false;
            }
        } else {
            l.swingTime = 0;
        }
        l.attackAnim = l.swingTime / SWING_TICKS;

        if (l.hurtTime > 0) l.hurtTime--;

        // Death tilt, then the poof.
        if (a.dying) {
            if (l.deathTime < DEATH_TICKS) l.deathTime++;
            if (l.deathTime >= DEATH_TICKS) {
                a.dying = false;
                a.visible = false;
                if (!a.skipPoof) poof(a);
            }
        }

        a.skipPoof = false;

        // use_item: the arm stays raised for USE_TICKS.
        if (a.useStart >= 0) {
            float elapsed = t - a.useStart;
            if (elapsed >= USE_TICKS) {
                a.useStart = -1;
                l.stopUsingItem();
                ((LivingEntityUseAccessor) l).hogwartsmagic$setFlag(1, false);
            } else {
                ((LivingEntityUseAccessor) l).hogwartsmagic$setUseItemRemaining(Math.max(1, (int) (USE_TICKS - elapsed)));
            }
        }
    }

    /**
     * Where the actor's root is at t, relative to the origin: the boat on the sea, the broom in flight, its seat on a
     * vehicle, or its own path (in world space outside a ride window).
     */
    private static double[] root(ActorRuntime a, float t, Rowboat.Pose boatPose, BroomFlight.Pose broomPose,
                                 Vehicle.Seat seat) {
        if (boatPose != null) return new double[]{boatPose.x(), boatPose.y(), boatPose.z()};
        if (broomPose != null) return new double[]{broomPose.x(), broomPose.y(), broomPose.z()};
        if (seat != null) return new double[]{seat.x(), seat.y(), seat.z()};
        float[] p = Vehicle.path(a.def, t);
        if (p == null) p = new float[]{0, 0, 0};
        return new double[]{p.length > 0 ? p[0] : 0, p.length > 1 ? p[1] : 0, p.length > 2 ? p[2] : 0};
    }

    /**
     * The boat's own spray: both blades splash as they bite at the catch and, smaller, as they leave at the finish,
     * and the bow throws spray when it slams down into a wave (pitch falling fast).
     */
    private static void boatSpray(ActorRuntime a, MemoryRowboatEntity boat, Rowboat.Pose pose, float t) {
        if (level == null || origin == null) return;
        float amount = Rowboat.strokeAmount(a.def, t);
        float now = Rowboat.phase(t), before = Rowboat.phase(t - 1);
        float s = a.def.scale();
        if (amount > 0.4f) {
            boolean catchNow = before > now;                         // wrapped: the catch
            boolean finish = before < 0.5f && now >= 0.5f;
            if (catchNow || finish) {
                double[] blade = Rowboat.blade(Rowboat.oar(catchNow ? 0f : 0.5f, amount));
                for (int side = -1; side <= 1; side += 2) {
                    double[] w = Rowboat.toWorld(pose, new double[]{blade[0] * side * s, 0.02, blade[2] * s});
                    splash(w, catchNow ? 12 : 5, 0.25 * s);
                }
            }
        }
        if (boat.pitch - boat.pitchO < -1.2f) {
            double[] bow = Rowboat.toWorld(pose, new double[]{0, 0.15 * s, (Rowboat.HALF_LENGTH - 0.1) * s});
            splash(bow, 14, 0.5 * s);
        }
    }

    private static void splash(double[] at, int n, double spread) {
        double x = origin.getX() + at[0], y = origin.getY() + at[1], z = origin.getZ() + at[2];
        for (int i = 0; i < n; i++) {
            level.addParticle(ParticleTypes.SPLASH, x + (level.random.nextDouble() - 0.5) * spread, y,
                    z + (level.random.nextDouble() - 0.5) * spread, (level.random.nextDouble() - 0.5) * 0.15,
                    0.1 + level.random.nextDouble() * 0.15, (level.random.nextDouble() - 0.5) * 0.15);
        }
    }

    /** The way the actor is about to walk, for the very first tick (no previous position yet). */
    private static float headingAhead(ActorRuntime a, float t) {
        float[] p0 = Vehicle.path(a.def, t);
        float[] p1 = Vehicle.path(a.def, t + 2);
        if (p0 == null || p1 == null || p0.length < 3 || p1.length < 3) return 0;
        double dx = p1[0] - p0[0];
        double dz = p1[2] - p0[2];
        if (dx * dx + dz * dz < 1.0e-6) return 0;
        return (float) (Mth.atan2(-dx, dz) * (180.0 / Math.PI));
    }

    /** {@code silent}: the cue is old news (a late joiner): set state, skip the one-shot effects. */
    private static void fire(ActorRuntime a, SceneDef.ActionCue cue, boolean silent, float now) {
        float age = now - cue.t();
        Entity e = a.entity;
        LivingEntity l = a.living;
        String act = cue.action();
        if ("swing".equals(act)) {
            if (l != null && !silent) {
                l.swinging = true;
                l.swingTime = -1;
                l.swingingArm = InteractionHand.MAIN_HAND;
            }
        } else if ("sneak".equals(act)) {
            e.setPose(Pose.CROUCHING);
        } else if ("stand".equals(act)) {
            e.setPose(Pose.STANDING);
        } else if ("hurt".equals(act)) {
            if (l != null && !silent) {
                l.hurtDuration = HURT_TICKS;
                l.hurtTime = HURT_TICKS;
            }
        } else if ("die".equals(act)) {
            if (l != null) {
                l.hurtDuration = HURT_TICKS;
                if (silent && age >= DEATH_TICKS) {
                    // Long over: the end state, with no tilt and no poof replayed for a late joiner.
                    a.dying = false;
                    a.visible = false;
                    l.deathTime = DEATH_TICKS;
                    l.hurtTime = 0;
                } else {
                    a.dying = true;
                    a.visible = true;
                    l.deathTime = silent ? (int) age : 0;
                    l.hurtTime = silent ? 0 : HURT_TICKS;
                    // Catching up must not land the poof on this very step.
                    a.skipPoof = silent;
                }
            }
        } else if ("vanish".equals(act)) {
            a.visible = false;
            if (!silent) smokeBurst(a);
        } else if ("appear".equals(act)) {
            a.visible = true;
            a.dying = false;
            e.setRemainingFireTicks(0);
            if (l != null) {
                l.deathTime = 0;
                l.hurtTime = 0;
            }
            if (!silent) smokeBurst(a);
        } else if ("ignite".equals(act)) {
            // The vanilla fire overlay: a puppet is never ticked, so the fire ticks never run down.
            e.setRemainingFireTicks(IGNITE_FIRE_TICKS);
            if (!silent) flameBurst(a);
        } else if ("stone".equals(act)) {
            a.stone = true;
        } else if ("unstone".equals(act)) {
            a.stone = false;
        } else if ("equip".equals(act)) {
            // Cues are replayed in time order for a late joiner, so the last one at or before now wins (EquipTimeline).
            EquipmentSlot slot = slot(cue.slot());
            if (l != null && slot != null) l.setItemSlot(slot, stack(cue.item()));
        } else if ("hold".equals(act)) {
            if (l != null) l.setItemSlot(EquipmentSlot.MAINHAND, stack(cue.item()));
        } else if ("use_item".equals(act)) {
            if (l != null && cue.item() != null) l.setItemSlot(EquipmentSlot.MAINHAND, stack(cue.item()));
            if (l != null && (!silent || age < USE_TICKS)) {
                l.startUsingItem(InteractionHand.MAIN_HAND);
                ((LivingEntityUseAccessor) l).hogwartsmagic$setFlag(1, true);
                a.useStart = cue.t();
            }
        } else if ("cast".equals(act)) {
            // A beam is a one-shot effect: a late joiner catching up must not see old casts replayed.
            if (!silent) cast(a, cue);
        }
    }

    /** The cast: a spell from the actor's wand hand to its target, drawn by {@link SpellVisuals}. */
    private static void cast(ActorRuntime a, SceneDef.ActionCue cue) {
        if (!(level instanceof ClientLevel lvl) || origin == null || cue.spell() == null) return;
        Entity e = a.entity;
        Vec3 eye = e.position().add(0, e.getEyeHeight() * a.def.scale() * 0.9, 0);
        Vec3 to = castTarget(a, cue, eye);
        // The wand hand: a little to the right of, and ahead of, the eye, along the line toward the target.
        Vec3 aim = to.subtract(eye);
        Vec3 flat = new Vec3(aim.x, 0, aim.z);
        Vec3 forward = flat.lengthSqr() < 1.0e-6 ? Vec3.directionFromRotation(0, e.getYRot()) : flat.normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 from = eye.add(right.scale(0.3)).add(forward.scale(0.4)).add(0, -0.15, 0);
        SpellVisuals.cast(lvl, from, to, cue.spell());
    }

    /** An actor target is its chest; a point is relative to the origin; none means straight ahead. */
    private static Vec3 castTarget(ActorRuntime caster, SceneDef.ActionCue cue, Vec3 eye) {
        SceneDef.Target t = cue.target();
        if (t != null && t.actor() != null) {
            ActorRuntime other = find(t.actor());
            if (other != null) {
                Entity o = other.entity;
                return o.position().add(0, o.getBbHeight() * other.def.scale() * 0.65, 0);
            }
        }
        if (t != null && t.point() != null && t.point().length >= 3) {
            return new Vec3(origin.getX() + t.point()[0], origin.getY() + t.point()[1], origin.getZ() + t.point()[2]);
        }
        return eye.add(Vec3.directionFromRotation(caster.entity.getXRot(), caster.entity.getYRot()).scale(10));
    }

    /** The moment an actor catches fire: a gout of flame and embers up its body, and the whoosh. */
    private static void flameBurst(ActorRuntime a) {
        if (level == null) return;
        Entity e = a.entity;
        double h = Math.max(1.0, e.getBbHeight() * a.def.scale());
        double w = Math.max(0.4, e.getBbWidth());
        for (int i = 0; i < 28; i++) {
            double ox = (level.random.nextDouble() - 0.5) * w * 1.4;
            double oy = level.random.nextDouble() * h;
            double oz = (level.random.nextDouble() - 0.5) * w * 1.4;
            level.addParticle(i % 4 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, e.getX() + ox, e.getY() + oy,
                    e.getZ() + oz, ox * 0.06, 0.04 + level.random.nextDouble() * 0.08, oz * 0.06);
        }
        for (int i = 0; i < 6; i++) {
            level.addParticle(ParticleTypes.LARGE_SMOKE, e.getRandomX(0.6), e.getY() + h * (0.6 + level.random.nextDouble() * 0.5),
                    e.getRandomZ(0.6), 0, 0.06, 0);
        }
        MemorySounds.play(SoundEvents.FIRECHARGE_USE, e.position(), 0.7f, 0.8f + level.random.nextFloat() * 0.3f);
    }

    private static void smokeBurst(ActorRuntime a) {
        if (level == null) return;
        Entity e = a.entity;
        double h = Math.max(1.0, e.getBbHeight() * a.def.scale());
        double w = Math.max(0.4, e.getBbWidth());
        for (int i = 0; i < 36; i++) {
            double ox = (level.random.nextDouble() - 0.5) * w * 1.6;
            double oy = level.random.nextDouble() * h;
            double oz = (level.random.nextDouble() - 0.5) * w * 1.6;
            level.addParticle(i % 2 == 0 ? DARK_SMOKE : SILVER_SMOKE, e.getX() + ox, e.getY() + oy, e.getZ() + oz,
                    ox * 0.08, 0.03 + level.random.nextDouble() * 0.05, oz * 0.08);
        }
        for (int i = 0; i < 8; i++) {
            level.addParticle(ParticleTypes.LARGE_SMOKE, e.getX() + (level.random.nextDouble() - 0.5) * w,
                    e.getY() + level.random.nextDouble() * h, e.getZ() + (level.random.nextDouble() - 0.5) * w, 0, 0.02, 0);
        }
    }

    private static void poof(ActorRuntime a) {
        if (level == null) return;
        Entity e = a.entity;
        double h = Math.max(0.6, e.getBbHeight() * a.def.scale());
        for (int i = 0; i < 20; i++) {
            level.addParticle(ParticleTypes.POOF, e.getRandomX(1.0), e.getY() + level.random.nextDouble() * h,
                    e.getRandomZ(1.0), level.random.nextGaussian() * 0.02, level.random.nextGaussian() * 0.02,
                    level.random.nextGaussian() * 0.02);
        }
    }

    // ---- Rendering --------------------------------------------------------------------------------------------

    /**
     * Worn items at the frame's own scene time, the time {@link ActorPoser} samples the limbs at. The tick sets them
     * at partial 0 too, but the eased scene clock ({@link PensieveClientState#sceneTime}) does not land on whole
     * ticks, so a cue can fall between two ticks: synced here, the item and a limb step keyed on the same moment
     * (the held-item raise a scene cancels at a hand-over) change on the same frame. Only changes are applied.
     */
    private static void syncItems(ActorRuntime a, float t) {
        LivingEntity l = a.living;
        if (l == null || !a.changesItems) return;
        for (String slotName : SceneNames.EQUIP_SLOTS) {
            String id = EquipTimeline.itemAt(a.actions, a.def.items(), slotName, t);
            if (id == null || id.equals(a.wornItems.get(slotName))) continue;
            EquipmentSlot es = slot(slotName);
            if (es == null) continue;
            l.setItemSlot(es, stack(id));
            a.wornItems.put(slotName, id);
        }
    }

    /** Draw every visible puppet. {@code poseStack} must be an identity stack: see {@link MemoryActorEvents}. */
    @SuppressWarnings("unchecked")
    public static void renderAll(PoseStack poseStack, MultiBufferSource.BufferSource buffer, Camera camera, float partial) {
        if (ACTORS.isEmpty() || level == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) return;
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        Vec3 cam = camera.getPosition();
        float now = PensieveClientState.sceneTime(partial);
        for (ActorRuntime a : ACTORS) {
            syncItems(a, now);
            if (!a.visible) continue;
            Entity e = a.entity;
            try {
                double x = Mth.lerp(partial, e.xo, e.getX());
                double y = Mth.lerp(partial, e.yo, e.getY());
                double z = Mth.lerp(partial, e.zo, e.getZ());
                float yaw = Mth.rotLerp(partial, e.yRotO, e.getYRot());
                float scale = a.def.scale();
                // A rider rocks with its boat: the boat's pitch and roll, turned into the world about the rider's root.
                org.joml.Quaternionf tilt = rideTilt(a, partial);
                boolean scaled = scale != 1f || tilt != null;
                int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, y + e.getEyeHeight() * scale, z));
                if (a.stone) {
                    EntityRenderer<Entity> renderer = (EntityRenderer<Entity>) dispatcher.getRenderer(e);
                    poseStack.pushPose();
                    Vec3 off = renderer.getRenderOffset(e, partial);
                    poseStack.translate(x - cam.x + off.x, y - cam.y + off.y, z - cam.z + off.z);
                    if (tilt != null) poseStack.mulPose(tilt);
                    if (scaled) poseStack.scale(scale, scale, scale);
                    renderer.render(e, yaw, partial, poseStack,
                            new PetrifiedRemainsRenderer.StoneBufferSource(buffer), light);
                    if (a.living != null) {
                        overlayPass = true;
                        try {
                            renderer.render(e, yaw, partial, poseStack, new PetrifiedRemainsRenderer.OverlayOnlyBufferSource(
                                    buffer.getBuffer(RenderType.entityTranslucent(PetrifiedRemainsRenderer.STONE_TEXTURE))), light);
                        } finally {
                            overlayPass = false;
                        }
                    }
                    poseStack.popPose();
                } else {
                    if (scaled) {
                        poseStack.pushPose();
                        poseStack.translate(x - cam.x, y - cam.y, z - cam.z);
                        if (tilt != null) poseStack.mulPose(tilt);
                        poseStack.scale(scale, scale, scale);
                        dispatcher.render(e, 0, 0, 0, yaw, partial, poseStack, buffer, light);
                        poseStack.popPose();
                    } else {
                        dispatcher.render(e, x - cam.x, y - cam.y, z - cam.z, yaw, partial, poseStack, buffer, light);
                    }
                }
            } catch (RuntimeException ex) {
                // Logged once (the actor is hidden from then on), with the stack, so a broken renderer is never silent.
                LOGGER.warn("Pensieve: actor '{}' ({}) failed to render and is hidden for the rest of the memory",
                        a.def.id(), a.def.type(), ex);
                a.visible = false;
            }
        }
        buffer.endBatch();
    }

    /**
     * A rider's rocking as a world rotation about its root, Yaw * Pitch * Roll * Yaw^-1 of its boat (see Rowboat), or
     * null when it rides nothing or the boat is level.
     */
    private static org.joml.Quaternionf rideTilt(ActorRuntime a, float partial) {
        if (a.def.ride() == null) return null;
        float pitch = Mth.lerp(partial, a.ridePitchO, a.ridePitch), roll = Mth.lerp(partial, a.rideRollO, a.rideRoll);
        if (pitch == 0f && roll == 0f) return null;
        float yaw = Mth.rotLerp(partial, a.rideYawO, a.rideYaw);
        org.joml.Quaternionf q = com.hogwartsmagic.client.MemoryRowboatRenderer.rotation(yaw, pitch, roll);
        return q.mul(com.hogwartsmagic.client.MemoryRowboatRenderer.rotation(yaw, 0f, 0f).conjugate());
    }

    /** Name tags: only for actors the scene named, and never twice. Vanilla would tag every puppet player. */
    public static void onNameTag(RenderNameTagEvent event) {
        ActorRuntime a = runtimeFor(event.getEntity());
        if (a == null) return;
        if (overlayPass || !a.hasName()) {
            event.setResult(Event.Result.DENY);
        } else {
            net.minecraft.client.player.LocalPlayer me = net.minecraft.client.Minecraft.getInstance().player;
            event.setContent(Component.literal(com.hogwartsmagic.pensieve.scene.WordText.fill(a.def.name(),
                    me != null ? me.getGameProfile().getName() : null)));
            event.setResult(Event.Result.ALLOW);
        }
    }
}
