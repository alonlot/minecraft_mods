package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.renderer.PetrifiedRemainsRenderer;
import com.hogwartsmagic.mixin.client.LivingEntityUseAccessor;
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
    private static final DustParticleOptions DARK_SMOKE = new DustParticleOptions(new Vector3f(0.04f, 0.04f, 0.06f), 1.6f);
    private static final DustParticleOptions SILVER_SMOKE = new DustParticleOptions(new Vector3f(0.78f, 0.8f, 0.88f), 1.3f);

    private static final List<ActorRuntime> ACTORS = new ArrayList<>();
    private static final Map<Entity, ActorRuntime> BY_ENTITY = new IdentityHashMap<>();
    private static ClientLevel level;
    private static BlockPos origin;
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
        if (scene == null || at == null || clientLevel == null) return;
        level = clientLevel;
        origin = at;
        for (SceneDef.ActorDef def : scene.actors()) {
            try {
                ActorRuntime a = create(def, clientLevel);
                if (a != null) {
                    ACTORS.add(a);
                    BY_ENTITY.put(a.entity, a);
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Pensieve: could not create actor '{}' of memory '{}': {}", def.id(), scene.id(), e.toString());
            }
        }
    }

    /** Forget every puppet. Safe to call at any time, including when there are none. */
    public static void clear() {
        ACTORS.clear();
        BY_ENTITY.clear();
        level = null;
        origin = null;
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
        return new ActorRuntime(def, entity, player, sorted);
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
            return new float[]{
                    (float) (Mth.lerp(partial, e.xo, e.getX()) - origin.getX()),
                    (float) (Mth.lerp(partial, e.yo, e.getY()) + e.getEyeHeight() - origin.getY()),
                    (float) (Mth.lerp(partial, e.zo, e.getZ()) - origin.getZ())};
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

    private static void step(ActorRuntime a, float t) {
        Entity e = a.entity;
        LivingEntity l = a.living;
        boolean first = !a.placed;
        e.setOldPosAndRot();
        if (l != null) {
            l.yBodyRotO = l.yBodyRot;
            l.yHeadRotO = l.yHeadRot;
        }
        e.tickCount++;

        // Timed actions whose moment has come.
        while (a.nextAction < a.actions.size() && a.actions.get(a.nextAction).t() <= t) {
            SceneDef.ActionCue cue = a.actions.get(a.nextAction++);
            fire(a, cue, cue.t() < t - 5);
        }

        // Where it stands, and which way it faces.
        float[] p = a.def.path().isEmpty() ? new float[]{0, 0, 0} : a.def.path().sample(t);
        double x = origin.getX() + (p.length > 0 ? p[0] : 0);
        double y = origin.getY() + (p.length > 1 ? p[1] : 0);
        double z = origin.getZ() + (p.length > 2 ? p[2] : 0);
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
        boolean hasLook = !a.def.look().isEmpty();
        if (hasLook) {
            float[] lk = a.def.look().sample(t);
            lookYaw = lk.length > 0 ? lk[0] : 0;
            pitch = lk.length > 1 ? lk[1] : 0;
        }
        float target;
        if (hasLook) {
            target = lookYaw;
        } else if (moving) {
            target = (float) (Mth.atan2(-dx, dz) * (180.0 / Math.PI));
        } else if (first) {
            target = headingAhead(a, t);
        } else {
            target = a.bodyYaw;
        }
        if (first) {
            a.bodyYaw = target;
        } else {
            a.bodyYaw += Mth.wrapDegrees(target - a.bodyYaw) * 0.3f;
        }
        a.headYaw = hasLook ? lookYaw : a.bodyYaw;
        a.bodyYaw = a.headYaw + Mth.clamp(Mth.wrapDegrees(a.bodyYaw - a.headYaw), -75f, 75f);

        e.setPos(x, y, z);
        e.setYRot(a.headYaw);
        e.setXRot(pitch);
        if (l != null) {
            l.setYBodyRot(a.bodyYaw);
            l.setYHeadRot(a.headYaw);
        }
        if (first) {
            e.setOldPosAndRot();
            if (l != null) {
                l.yBodyRotO = l.yBodyRot;
                l.yHeadRotO = l.yHeadRot;
            }
            a.placed = true;
        }

        if (l == null) return;

        // Walk cycle, from the distance covered this tick (as LivingEntity#calculateEntityAnimation does).
        l.walkAnimation.update(first ? 0 : Math.min((float) Math.sqrt(dx * dx + dz * dz) * 4.0f, 1.0f), 0.4f);

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
                poof(a);
            }
        }

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

    /** The way the actor is about to walk, for the very first tick (no previous position yet). */
    private static float headingAhead(ActorRuntime a, float t) {
        if (a.def.path().isEmpty()) return 0;
        float[] p0 = a.def.path().sample(t);
        float[] p1 = a.def.path().sample(t + 2);
        if (p0.length < 3 || p1.length < 3) return 0;
        double dx = p1[0] - p0[0];
        double dz = p1[2] - p0[2];
        if (dx * dx + dz * dz < 1.0e-6) return 0;
        return (float) (Mth.atan2(-dx, dz) * (180.0 / Math.PI));
    }

    /** {@code silent}: the cue is old news (a late joiner): set state, skip the one-shot effects. */
    private static void fire(ActorRuntime a, SceneDef.ActionCue cue, boolean silent) {
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
                a.dying = true;
                a.visible = true;
                l.deathTime = silent ? DEATH_TICKS - 1 : 0;
                l.hurtDuration = HURT_TICKS;
                l.hurtTime = silent ? 0 : HURT_TICKS;
            }
        } else if ("vanish".equals(act)) {
            a.visible = false;
            if (!silent) smokeBurst(a);
        } else if ("appear".equals(act)) {
            a.visible = true;
            a.dying = false;
            if (l != null) {
                l.deathTime = 0;
                l.hurtTime = 0;
            }
            if (!silent) smokeBurst(a);
        } else if ("stone".equals(act)) {
            a.stone = true;
        } else if ("unstone".equals(act)) {
            a.stone = false;
        } else if ("hold".equals(act)) {
            if (l != null) l.setItemSlot(EquipmentSlot.MAINHAND, stack(cue.item()));
        } else if ("use_item".equals(act)) {
            if (l != null && !silent) {
                if (cue.item() != null) l.setItemSlot(EquipmentSlot.MAINHAND, stack(cue.item()));
                l.startUsingItem(InteractionHand.MAIN_HAND);
                ((LivingEntityUseAccessor) l).hogwartsmagic$setFlag(1, true);
                a.useStart = cue.t();
            }
        }
        // "cast" is Task 11's.
    }

    private static void smokeBurst(ActorRuntime a) {
        if (level == null) return;
        Entity e = a.entity;
        double h = Math.max(1.0, e.getBbHeight());
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
        double h = Math.max(0.6, e.getBbHeight());
        for (int i = 0; i < 20; i++) {
            level.addParticle(ParticleTypes.POOF, e.getRandomX(1.0), e.getY() + level.random.nextDouble() * h,
                    e.getRandomZ(1.0), level.random.nextGaussian() * 0.02, level.random.nextGaussian() * 0.02,
                    level.random.nextGaussian() * 0.02);
        }
    }

    // ---- Rendering --------------------------------------------------------------------------------------------

    /** Draw every visible puppet. {@code poseStack} must be an identity stack: see {@link MemoryActorEvents}. */
    @SuppressWarnings("unchecked")
    public static void renderAll(PoseStack poseStack, MultiBufferSource.BufferSource buffer, Camera camera, float partial) {
        if (ACTORS.isEmpty() || level == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) return;
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        Vec3 cam = camera.getPosition();
        for (ActorRuntime a : ACTORS) {
            if (!a.visible) continue;
            Entity e = a.entity;
            try {
                double x = Mth.lerp(partial, e.xo, e.getX());
                double y = Mth.lerp(partial, e.yo, e.getY());
                double z = Mth.lerp(partial, e.zo, e.getZ());
                float yaw = Mth.rotLerp(partial, e.yRotO, e.getYRot());
                int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, y + e.getEyeHeight(), z));
                if (a.stone) {
                    EntityRenderer<Entity> renderer = (EntityRenderer<Entity>) dispatcher.getRenderer(e);
                    poseStack.pushPose();
                    poseStack.translate(x - cam.x, y - cam.y, z - cam.z);
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
                    dispatcher.render(e, x - cam.x, y - cam.y, z - cam.z, yaw, partial, poseStack, buffer, light);
                }
            } catch (RuntimeException ex) {
                LOGGER.warn("Pensieve: actor '{}' failed to render: {}", a.def.id(), ex.toString());
                a.visible = false;
            }
        }
        buffer.endBatch();
    }

    /** Name tags: only for actors the scene named, and never twice. Vanilla would tag every puppet player. */
    public static void onNameTag(RenderNameTagEvent event) {
        ActorRuntime a = runtimeFor(event.getEntity());
        if (a == null) return;
        if (overlayPass || !a.hasName()) {
            event.setResult(Event.Result.DENY);
        } else {
            event.setContent(Component.literal(a.def.name()));
            event.setResult(Event.Result.ALLOW);
        }
    }
}
