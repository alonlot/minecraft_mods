package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.disable.IncarcerousRopeTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Who this client knows to be bound by Incarcerous, and where each bind is in its animation. Fed by
 * {@code incarcerousRopes} packets; drawn by {@link IncarcerousRopeRenderer} and posed by
 * {@link IncarcerousBoundPose}. The rope sounds are played here, locally, on the same clock as the drawing so
 * the creak lands on the strain and the thud on the cinch.
 *
 * <p>A bind lapses on its own when refreshes stop (the server refreshes once a second), so a target that
 * walked out of tracking range never leaves ropes hanging in the air.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class IncarcerousRopesClientState {
    /** Comfortably longer than the server's one-second refresh. */
    private static final int REFRESH_TTL = 45;
    /** Ropes streaming from a wand further than this would just be a line across the sky. */
    private static final double MAX_FLIGHT = 12.0;

    private static final Map<Integer, Bind> BINDS = new HashMap<>();

    private IncarcerousRopesClientState() {
    }

    public static final class Bind {
        public final int entityId;
        public final long seed;
        /** Where the ropes fly from, fixed when the spell lands. */
        public Vec3 origin;
        public Vec3 anchor;
        public int anchorEntityId;
        /** Where the ground rope is staked, fixed the first time it is drawn (just outside the feet). */
        public Vec3 stake;
        public double start;
        public long expiry;
        /** Game time the ropes started falling away, or NaN while they hold. */
        public double releasedAt = Double.NaN;
        boolean whooshed;
        boolean landed;
        boolean cinched;
        boolean straining;

        Bind(int entityId) {
            this.entityId = entityId;
            this.seed = entityId * 0x9E3779B97F4A7C15L;
        }

        /** Ticks since the bind arrived. */
        public double age(double now) {
            return now - start;
        }

        /** Ticks since the release began, or 0 while held. */
        public double sinceRelease(double now) {
            return Double.isNaN(releasedAt) ? 0.0 : Math.max(0.0, now - releasedAt);
        }

        public boolean released() {
            return !Double.isNaN(releasedAt);
        }
    }

    public static void accept(SpellPackets.IncarcerousRopesPacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        Bind bind = BINDS.get(packet.entityId());
        if (packet.remaining() <= 0) {
            if (bind != null && !bind.released()) {
                bind.releasedAt = now;
                Entity e = level.getEntity(packet.entityId());
                if (e != null) {
                    play(level, e.position().add(0.0, e.getBbHeight() * 0.5, 0.0), SoundEvents.LEASH_KNOT_BREAK, 0.9f, 0.75f);
                }
            }
            return;
        }
        Entity target = level.getEntity(packet.entityId());
        boolean fresh = bind == null || bind.released();
        if (fresh) {
            bind = new Bind(packet.entityId());
            BINDS.put(packet.entityId(), bind);
            if (packet.intro() && target != null) {
                bind.start = now;
                bind.origin = flightOrigin(level, target, packet.casterId());
            } else {
                // Walked into range of a bind already tied: show it settled, with no replayed flight or sounds.
                bind.start = now - 1000.0;
                bind.origin = target != null ? target.position() : Vec3.ZERO;
                bind.whooshed = bind.landed = bind.cinched = true;
            }
        }
        bind.anchor = new Vec3(packet.anchorX(), packet.anchorY(), packet.anchorZ());
        bind.anchorEntityId = packet.anchorEntityId();
        bind.expiry = now + Math.min(packet.remaining(), REFRESH_TTL);
    }

    /** The caster's wand tip, pulled in to {@link #MAX_FLIGHT}; or, with no caster in sight, a point in front of the target. */
    private static Vec3 flightOrigin(ClientLevel level, Entity target, int casterId) {
        Vec3 chest = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
        Entity caster = casterId >= 0 ? level.getEntity(casterId) : null;
        if (caster != null && caster != target) {
            Vec3 look = caster.getViewVector(1.0f);
            Vec3 right = new Vec3(-look.z, 0.0, look.x);
            if (right.lengthSqr() > 1.0E-6) {
                right = right.normalize();
            }
            Vec3 wand = caster.getEyePosition().add(look.scale(0.6)).add(right.scale(0.3)).add(0.0, -0.3, 0.0);
            Vec3 toWand = wand.subtract(chest);
            double d = toWand.length();
            return d > MAX_FLIGHT ? chest.add(toWand.scale(MAX_FLIGHT / d)) : wand;
        }
        float yaw = target.getYRot() * ((float) Math.PI / 180f);
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        return chest.add(forward.scale(2.6)).add(0.0, 0.8, 0.0);
    }

    public static Bind get(int entityId) {
        return BINDS.get(entityId);
    }

    public static Collection<Bind> all() {
        return BINDS.values();
    }

    public static boolean isEmpty() {
        return BINDS.isEmpty();
    }

    /** Lapsed or dead binds start their release; finished releases are dropped. The rope sounds play here. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BINDS.isEmpty()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            BINDS.clear();
            return;
        }
        long now = level.getGameTime();
        Iterator<Bind> it = BINDS.values().iterator();
        while (it.hasNext()) {
            Bind bind = it.next();
            Entity e = level.getEntity(bind.entityId);
            if (!bind.released() && (now > bind.expiry || e == null
                    || (e instanceof LivingEntity living && living.isDeadOrDying()))) {
                bind.releasedAt = Math.min(now, bind.expiry);
            }
            if (bind.released() && now - bind.releasedAt > IncarcerousRopeTimeline.RELEASE) {
                it.remove();
                continue;
            }
            if (e != null && !bind.released()) {
                sounds(level, bind, e, bind.age(now));
            }
        }
    }

    private static void sounds(ClientLevel level, Bind bind, Entity e, double t) {
        Vec3 body = e.position().add(0.0, e.getBbHeight() * 0.55, 0.0);
        if (!bind.whooshed) {
            bind.whooshed = true;
            play(level, bind.origin, SoundEvents.FISHING_BOBBER_THROW, 1.0f, 0.5f);
            play(level, bind.origin.lerp(body, 0.5), SoundEvents.FISHING_BOBBER_THROW, 0.7f, 0.65f);
        }
        if (!bind.landed && t >= IncarcerousRopeTimeline.FLIGHT) {
            bind.landed = true;
            play(level, body, SoundEvents.LEASH_KNOT_PLACE, 0.9f, 0.7f);
            play(level, body, SoundEvents.WOOL_PLACE, 1.0f, 0.6f);
        }
        if (!bind.cinched && t >= IncarcerousRopeTimeline.CINCHED) {
            bind.cinched = true;
            play(level, body, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0f, 0.55f);
            play(level, body, SoundEvents.LEASH_KNOT_PLACE, 1.0f, 0.5f);
        }
        boolean straining = IncarcerousRopeTimeline.strain(bind.seed, t) > 0.2;
        if (straining && !bind.straining) {
            play(level, body, SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), 0.45f, 0.45f);
            play(level, body, SoundEvents.LEASH_KNOT_PLACE, 0.35f, 0.4f);
        }
        bind.straining = straining;
    }

    private static void play(ClientLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch, false);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BINDS.clear();
    }
}
