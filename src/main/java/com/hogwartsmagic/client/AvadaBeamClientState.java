package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.mind.AvadaBeamFx;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Killing Curse beams this client is drawing, and the strikes where they ended.
 *
 * <p>A jet is drawn whole, from the wand that cast it to the beam's head, so each beam's starting point is
 * remembered when it is first seen. When the beam is gone the jet stays burned into the air for a moment where it
 * was, and its wake of smoke lives out its life. A strike comes from the server's {@code avadaImpact} packet,
 * which lands before the beam's removal; a beam that vanishes close by with no packet (a memory's scenery beam)
 * plays a strike of its own where it vanished.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class AvadaBeamClientState {
    /** How long a jet stays burned into the air after the beam is gone. */
    public static final float AFTERGLOW_TICKS = 10.0f;
    /** How long the smoke left along the path lives. */
    public static final float WAKE_TICKS = 22.0f;
    public static final float IMPACT_TICKS = 26.0f;
    /** A beam vanishing without a strike packet this close plays its own strike; further off it just left range. */
    private static final double SILENT_END_RANGE = 48.0;

    private static final Map<Integer, Jet> JETS = new HashMap<>();
    private static final List<Impact> IMPACTS = new ArrayList<>();

    private AvadaBeamClientState() {
    }

    public static final class Jet {
        final int entityId;
        final int seed;
        /** World space. */
        final Vec3 origin;
        Vec3 head;
        Vec3 headPrev;
        Vec3 dir;
        /** Blocks a tick; the wake uses it to know when the head passed each point. */
        double speed;
        final double born;
        double goneAt = -1.0;
        boolean struck;

        Jet(int entityId, Vec3 origin, Vec3 head, Vec3 dir, double speed, double born) {
            this.entityId = entityId;
            this.seed = entityId * 0x632BE5AB;
            this.origin = origin;
            this.head = head;
            this.headPrev = head;
            this.dir = dir;
            this.speed = speed;
            this.born = born;
        }

        public boolean gone() {
            return goneAt >= 0.0;
        }

        /** The head this frame: lerped between ticks while flying, fixed once it is gone. */
        Vec3 head(float partial) {
            return gone() ? head : headPrev.lerp(head, partial);
        }
    }

    public static final class Impact {
        final Vec3 pos;
        final Vec3 dir;
        final byte kind;
        final double start;
        final int seed;

        Impact(Vec3 pos, Vec3 dir, byte kind, double start) {
            this.pos = pos;
            this.dir = dir;
            this.kind = kind;
            this.start = start;
            this.seed = (int) (Double.doubleToLongBits(pos.x * 31.0 + pos.z) ^ (long) start * 0x9E3779B1L);
        }
    }

    public static Iterable<Jet> jets() {
        return JETS.values();
    }

    public static Iterable<Impact> impacts() {
        return IMPACTS;
    }

    public static boolean isEmpty() {
        return JETS.isEmpty() && IMPACTS.isEmpty();
    }

    public static void acceptImpact(SpellPackets.AvadaImpactPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        double now = minecraft.level.getGameTime();
        Vec3 pos = new Vec3(packet.x(), packet.y(), packet.z());
        Vec3 dir = new Vec3(packet.dx(), packet.dy(), packet.dz());
        Jet jet = JETS.get(packet.beamId());
        if (jet != null && !jet.gone()) {
            // The jet ends exactly where it struck, not where the beam's last tick left it.
            jet.headPrev = pos;
            jet.head = pos;
            jet.struck = true;
            jet.goneAt = now;
        }
        IMPACTS.add(new Impact(pos, dir, packet.kind(), now));
    }

    /**
     * Find new beams, move the known ones on and notice the ones that ended. Once a tick: a beam's position only
     * changes once a tick, and the head is lerped from {@link Jet#headPrev} each frame.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.level == null || minecraft.isPaused()) {
            return;
        }
        double now = minecraft.level.getGameTime();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof SpellBeamEntity beam) || beam.getSpellType() != SpellType.AVADA_KEDAVRA) {
                continue;
            }
            Vec3 head = centre(beam);
            Vec3 motion = beam.getDeltaMovement();
            Jet jet = JETS.get(beam.getId());
            if (jet == null) {
                Vec3 dir = motion.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : motion.normalize();
                JETS.put(beam.getId(), new Jet(beam.getId(), origin(beam, head), head, dir, motion.length(), now));
                continue;
            }
            if (jet.gone()) {
                continue;
            }
            jet.headPrev = jet.head;
            jet.head = head;
            if (motion.lengthSqr() > 1.0E-8) {
                jet.dir = motion.normalize();
                jet.speed = motion.length();
            }
        }

        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        for (Jet jet : JETS.values()) {
            if (jet.gone()) {
                continue;
            }
            Entity entity = minecraft.level.getEntity(jet.entityId);
            if (entity == null || entity.isRemoved()) {
                jet.headPrev = jet.head;
                jet.goneAt = now;
                if (!jet.struck && jet.head.distanceTo(camera) < SILENT_END_RANGE) {
                    IMPACTS.add(new Impact(jet.head, jet.dir, AvadaBeamFx.WALL, now));
                }
            }
        }
        JETS.values().removeIf(jet -> jet.gone() && now - jet.goneAt > Math.max(AFTERGLOW_TICKS, WAKE_TICKS) + 2.0);
        IMPACTS.removeIf(impact -> now - impact.start > IMPACT_TICKS);
    }

    /** Where the jet starts: the caster's wand tip if we know the caster, else where the beam was first seen. */
    private static Vec3 origin(SpellBeamEntity beam, Vec3 firstSeen) {
        Entity owner = beam.getOwner();
        if (!(owner instanceof LivingEntity caster) || owner.distanceToSqr(beam) > 16.0) {
            return firstSeen;
        }
        AvadaChargeClientState.Charge charge = AvadaChargeClientState.get(caster.getId());
        boolean firstPerson = AvadaChargePose.isOwnFirstPerson(caster);
        if (charge != null) {
            return AvadaChargePose.wandTip(caster, charge, 1.0f, firstPerson);
        }
        // An NPC caster with no windup: near its eyes, off to the wand side.
        Vec3 look = caster.getViewVector(1.0f);
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0E-4 ? Vec3.ZERO : right.normalize();
        return caster.getEyePosition().add(look.scale(0.6)).add(right.scale(0.3)).add(0.0, -0.2, 0.0);
    }

    /** The beam's visual centre: the old bolt was drawn a quarter block above the entity's feet. */
    private static Vec3 centre(SpellBeamEntity beam) {
        return beam.position().add(0.0, Mth.clamp(beam.getBbHeight() * 0.5, 0.0, 0.25), 0.0);
    }

    /**
     * How hard a Killing Curse strike near the camera flashes the edges of the view, 0 to 1: a bright pulse that
     * drains in a third of a second, weaker with distance.
     */
    public static float screenFlash(Vec3 camera, double now) {
        float flash = 0.0f;
        for (Impact impact : IMPACTS) {
            if (impact.kind == AvadaBeamFx.EVADE || impact.kind == AvadaBeamFx.FADE) {
                continue;
            }
            float age = (float) (now - impact.start);
            double distance = impact.pos.distanceTo(camera);
            if (age < 0.0f || age > 7.0f || distance > 20.0) {
                continue;
            }
            float fade = 1.0f - age / 7.0f;
            float strength = impact.kind == AvadaBeamFx.HIT ? 1.0f : 0.6f;
            flash = Math.max(flash, fade * fade * (float) (1.0 - distance / 20.0) * strength);
        }
        return flash;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        JETS.clear();
        IMPACTS.clear();
    }
}
