package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellBeamFx;
import com.hogwartsmagic.spell.SpellType;
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
 * The spell beams this client is drawing, and the strikes where they ended.
 *
 * <p>Each beam's path is recorded a tick at a time from the wand that cast it, so a jet is drawn the whole way back
 * and a homing beam's tail follows the curve it actually flew; its wake and shed rings are born on that path at
 * the moment the head passed. When the beam is gone it stays burned into the air for a moment and its wake lives
 * out its life. A strike comes from the server's {@code beamImpact} packet, which lands before the beam's removal;
 * the path's last point is put on the beam's own line at the strike, so the beam never bends off to where the
 * target's feet are. A beam that vanishes close by with no packet (a memory's scenery beam) strikes where it was.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class SpellBeamClientState {
    public static final float IMPACT_TICKS = 26.0f;
    /** A beam vanishing without a strike packet this close plays its own strike; further off it just left range. */
    private static final double SILENT_END_RANGE = 48.0;
    /** A path longer than this many ticks keeps only its newest points (a jet is drawn straight back to the wand). */
    private static final int MAX_TRAIL = 400;

    private static final Map<Integer, Beam> BEAMS = new HashMap<>();
    private static final List<Impact> IMPACTS = new ArrayList<>();
    private static final List<Clash> CLASHES = new ArrayList<>();
    /** How long a clash plays: the lock, the collapse, the blast and the scar it leaves. */
    public static final float CLASH_TICKS = 44.0f;
    /** When a mutual clash detonates; a one-sided one shatters sooner. */
    public static final float CLASH_LOCK = 8.0f;
    public static final float CLASH_LOCK_WIN = 4.0f;
    private static final double SHAKE_RANGE = 16.0;

    private SpellBeamClientState() {
    }

    public static final class Beam {
        final int entityId;
        final BeamStyle style;
        final int seed;
        /** World positions: the wand first, then the head each tick. */
        final List<Vec3> trail = new ArrayList<>();
        /** Game time of each trail point. */
        final List<Double> times = new ArrayList<>();
        /** Where the head was the tick before the newest point, for lerping between ticks. */
        Vec3 headPrev;
        Vec3 dir;
        double goneAt = -1.0;
        boolean struck;

        Beam(int entityId, BeamStyle style, Vec3 origin, Vec3 head, Vec3 dir, double now) {
            this.entityId = entityId;
            this.style = style;
            this.seed = entityId * 0x632BE5AB;
            this.trail.add(origin);
            this.times.add(now - 1.0);
            this.trail.add(head);
            this.times.add(now);
            this.headPrev = origin;
            this.dir = dir;
        }

        public boolean gone() {
            return goneAt >= 0.0;
        }

        Vec3 head() {
            return trail.get(trail.size() - 1);
        }

        /** The head this frame: lerped between ticks while flying, fixed once it is gone. */
        Vec3 head(float partial) {
            return gone() ? head() : headPrev.lerp(head(), partial);
        }

        void push(Vec3 head, double now) {
            headPrev = head();
            trail.add(head);
            times.add(now);
            if (trail.size() > MAX_TRAIL) {
                // Keep the wand point; drop the oldest tick after it.
                trail.remove(1);
                times.remove(1);
            }
        }

        /** How long after it ends this beam still has something to draw. */
        float linger() {
            float wakeLife = style.wake == BeamStyle.WAKE_NONE && !style.smokeToo ? 0.0f : style.wakeLife;
            float ringLife = style.ringEvery > 0.0f ? style.ringLife : 0.0f;
            return Math.max(style.afterglow, Math.max(wakeLife, ringLife)) + 2.0f;
        }
    }

    public static final class Impact {
        final Vec3 pos;
        final Vec3 dir;
        final BeamStyle style;
        final byte kind;
        final double start;
        final int seed;

        Impact(Vec3 pos, Vec3 dir, BeamStyle style, byte kind, double start) {
            this.pos = pos;
            this.dir = dir;
            this.style = style;
            this.kind = kind;
            this.start = start;
            this.seed = (int) (Double.doubleToLongBits(pos.x * 31.0 + pos.z) ^ (long) start * 0x9E3779B1L);
        }

        /** How big this ending is next to a full strike. */
        float scale() {
            if (kind == SpellBeamFx.HIT) {
                return style.impactScale;
            }
            if (kind == SpellBeamFx.EVADE) {
                return style.impactScale * 0.8f;
            }
            return style.impactScale * (kind == SpellBeamFx.FIZZLE ? 0.55f : 0.35f);
        }

        /** Rings, arcs, pillars and bursts belong to a real strike; a fizzle is a flash and a few sparks. */
        boolean full() {
            return kind == SpellBeamFx.HIT || kind == SpellBeamFx.EVADE;
        }
    }

    /**
     * Two beams meeting. {@code axis} is the line they push along (A's way); the blast's rings lie across it. A
     * one-sided clash keeps which beam won: the loser shatters back along the winner's line.
     */
    public static final class Clash {
        final Vec3 pos;
        final Vec3 dirA;
        final Vec3 dirB;
        final Vec3 axis;
        final BeamStyle styleA;
        final BeamStyle styleB;
        final byte outcome;
        final double start;
        final int seed;

        Clash(Vec3 pos, Vec3 dirA, Vec3 dirB, BeamStyle styleA, BeamStyle styleB, byte outcome, double start) {
            this.pos = pos;
            this.dirA = dirA;
            this.dirB = dirB;
            Vec3 push = dirA.subtract(dirB);
            this.axis = push.lengthSqr() < 1.0E-6 ? dirA : push.normalize();
            this.styleA = styleA;
            this.styleB = styleB;
            this.outcome = outcome;
            this.start = start;
            this.seed = (int) (Double.doubleToLongBits(pos.y * 17.0 + pos.x) ^ (long) start * 0x7FEB352DL);
        }

        boolean mutual() {
            return outcome == SpellBeamFx.CLASH_MUTUAL;
        }

        /** The tick the lock gives way. */
        float lock() {
            return mutual() ? CLASH_LOCK : CLASH_LOCK_WIN;
        }

        BeamStyle winner() {
            return outcome == SpellBeamFx.CLASH_B_WINS ? styleB : styleA;
        }

        BeamStyle loser() {
            return outcome == SpellBeamFx.CLASH_B_WINS ? styleA : styleB;
        }

        Vec3 winnerDir() {
            return outcome == SpellBeamFx.CLASH_B_WINS ? dirB : dirA;
        }

        boolean dark() {
            return styleA.sheathDark && styleA.sheathWidth > 0.0f || styleB.sheathDark && styleB.sheathWidth > 0.0f;
        }
    }

    /**
     * A beam held between two points (a Priori Incantatem lock's), built fresh each frame for the renderer. Its age
     * counts from {@code start}, so it writhes and crackles on as the lock goes on.
     */
    static Beam held(int id, BeamStyle style, Vec3 from, Vec3 to, double start, double now) {
        Vec3 span = to.subtract(from);
        Vec3 dir = span.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : span.normalize();
        Beam beam = new Beam(id, style, from, to, dir, now);
        beam.times.set(0, start);
        beam.headPrev = to;
        return beam;
    }

    /** A beam that ended in a lock: no strike of its own, it just lets go where it was. */
    static void endSilently(int beamId, double now) {
        Beam beam = BEAMS.get(beamId);
        if (beam != null && !beam.gone()) {
            beam.headPrev = beam.head();
            beam.struck = true;
            beam.goneAt = now;
        }
    }

    public static Iterable<Clash> clashes() {
        return CLASHES;
    }

    public static Iterable<Beam> beams() {
        return BEAMS.values();
    }

    public static Iterable<Impact> impacts() {
        return IMPACTS;
    }

    public static boolean isEmpty() {
        return BEAMS.isEmpty() && IMPACTS.isEmpty() && CLASHES.isEmpty();
    }

    public static void acceptImpact(SpellPackets.BeamImpactPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        SpellType spell = SpellType.fromId(packet.spellId());
        BeamStyle style = spell == null ? null : BeamStyles.of(spell);
        if (minecraft.level == null || style == null) {
            return;
        }
        double now = minecraft.level.getGameTime();
        Vec3 pos = new Vec3(packet.x(), packet.y(), packet.z());
        Vec3 dir = new Vec3(packet.dx(), packet.dy(), packet.dz());
        Beam beam = BEAMS.get(packet.beamId());
        if (beam != null && !beam.gone()) {
            pos = onLine(beam, pos);
            beam.push(pos, now);
            beam.headPrev = pos;
            beam.struck = true;
            beam.goneAt = now;
        }
        IMPACTS.add(new Impact(pos, dir, style, packet.kind(), now));
    }

    public static void acceptClash(SpellPackets.BeamClashPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        SpellType spellA = SpellType.fromId(packet.spellA());
        SpellType spellB = SpellType.fromId(packet.spellB());
        BeamStyle styleA = spellA == null ? null : BeamStyles.of(spellA);
        BeamStyle styleB = spellB == null ? null : BeamStyles.of(spellB);
        if (minecraft.level == null) {
            return;
        }
        // A spell with no drawn beam (Aqua Eructo) still clashes; it lends the other's style.
        if (styleA == null) {
            styleA = styleB;
        }
        if (styleB == null) {
            styleB = styleA;
        }
        if (styleA == null) {
            return;
        }
        double now = minecraft.level.getGameTime();
        Vec3 pos = new Vec3(packet.x(), packet.y(), packet.z());
        byte outcome = packet.outcome();
        // The same clash arrives once per side of it: draw it once.
        for (Clash existing : CLASHES) {
            if (existing.start == now && existing.pos.distanceToSqr(pos) < 1.0E-4) {
                return;
            }
        }
        // Whichever beams the clash ends stop at it, on their own line; a winner flies on.
        if (outcome != SpellBeamFx.CLASH_A_WINS) {
            endAt(packet.beamA(), pos, now);
        }
        if (outcome != SpellBeamFx.CLASH_B_WINS) {
            endAt(packet.beamB(), pos, now);
        }
        CLASHES.add(new Clash(pos, new Vec3(packet.ax(), packet.ay(), packet.az()),
                new Vec3(packet.bx(), packet.by(), packet.bz()), styleA, styleB, outcome, now));
    }

    private static void endAt(int beamId, Vec3 pos, double now) {
        Beam beam = BEAMS.get(beamId);
        if (beam == null || beam.gone()) {
            return;
        }
        Vec3 end = onLine(beam, pos);
        beam.push(end, now);
        beam.headPrev = end;
        beam.struck = true;
        beam.goneAt = now;
    }

    /**
     * The strike point carried onto the beam's own line: the last stretch of its path, extended. Whatever the
     * server reports, the drawn beam ends straight ahead of where it was going.
     */
    private static Vec3 onLine(Beam beam, Vec3 strike) {
        Vec3 from = beam.trail.get(Math.max(0, beam.trail.size() - 2));
        Vec3 dir = beam.dir;
        double along = strike.subtract(from).dot(dir);
        return along <= 0.0 ? strike : from.add(dir.scale(along));
    }

    /**
     * Find new beams, move the known ones on and notice the ones that ended. Once a tick: a beam only moves once
     * a tick, and the head is lerped from {@link Beam#headPrev} each frame.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.level == null || minecraft.isPaused()) {
            return;
        }
        double now = minecraft.level.getGameTime();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof SpellBeamEntity spellBeam)) {
                continue;
            }
            Beam beam = BEAMS.get(spellBeam.getId());
            Vec3 head = centre(spellBeam);
            Vec3 motion = spellBeam.getDeltaMovement();
            if (beam == null) {
                BeamStyle style = BeamStyles.of(spellBeam.getSpellType());
                if (style == null) {
                    continue;
                }
                Vec3 dir = motion.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : motion.normalize();
                BEAMS.put(spellBeam.getId(), new Beam(spellBeam.getId(), style, origin(spellBeam, head), head, dir, now));
                continue;
            }
            if (beam.gone()) {
                continue;
            }
            beam.push(head, now);
            if (motion.lengthSqr() > 1.0E-8) {
                beam.dir = motion.normalize();
            }
        }

        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        for (Beam beam : BEAMS.values()) {
            if (beam.gone()) {
                continue;
            }
            Entity entity = minecraft.level.getEntity(beam.entityId);
            if (entity == null || entity.isRemoved()) {
                beam.headPrev = beam.head();
                beam.goneAt = now;
                if (!beam.struck && beam.head().distanceTo(camera) < SILENT_END_RANGE) {
                    IMPACTS.add(new Impact(beam.head(), beam.dir, beam.style, SpellBeamFx.HIT, now));
                }
            }
        }
        BEAMS.values().removeIf(beam -> beam.gone() && now - beam.goneAt > beam.linger());
        IMPACTS.removeIf(impact -> now - impact.start > IMPACT_TICKS);
        CLASHES.removeIf(clash -> now - clash.start > CLASH_TICKS);
    }

    /** Where the beam starts: the caster's wand tip if we know the caster, else where the beam was first seen. */
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
        CastMotions.Cast cast = CastMotions.get(caster.getId(), AvadaChargeClientState.now(1.0f));
        if (cast != null) {
            return CastMotions.wandTip(caster, cast, 1.0f, firstPerson);
        }
        // Near the eyes, off to the wand side, where a raised wand's tip is.
        Vec3 look = caster.getViewVector(1.0f);
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0E-4 ? Vec3.ZERO : right.normalize();
        return caster.getEyePosition().add(look.scale(0.6)).add(right.scale(0.3)).add(0.0, -0.2, 0.0);
    }

    /** The beam's visual centre: beams were always drawn a quarter block above the entity's feet. */
    private static Vec3 centre(SpellBeamEntity beam) {
        return beam.position().add(0.0, Mth.clamp(beam.getBbHeight() * 0.5, 0.0, 0.25), 0.0);
    }

    /**
     * How hard a strike near the camera flashes the edges of the view, 0 to 1: a bright pulse that drains in a
     * third of a second, weaker with distance. Only the spells whose style asks for it. Returns
     * {strength, r, g, b}, the colour being the strongest strike's glow.
     */
    public static float[] screenFlash(Vec3 camera, double now) {
        float[] flash = {0.0f, 0.0f, 0.0f, 0.0f};
        for (Impact impact : IMPACTS) {
            if (impact.style.screenFlash <= 0.0f || !impact.full() || impact.kind == SpellBeamFx.EVADE) {
                continue;
            }
            float age = (float) (now - impact.start);
            double distance = impact.pos.distanceTo(camera);
            if (age < 0.0f || age > 7.0f || distance > 20.0) {
                continue;
            }
            float fade = 1.0f - age / 7.0f;
            float strength = fade * fade * (float) (1.0 - distance / 20.0) * impact.style.screenFlash;
            if (strength > flash[0]) {
                flash[0] = strength;
                flash[1] = impact.style.glowR;
                flash[2] = impact.style.glowG;
                flash[3] = impact.style.glowB;
            }
        }
        for (Clash clash : CLASHES) {
            float age = (float) (now - clash.start) - clash.lock();
            double distance = clash.pos.distanceTo(camera);
            if (age < 0.0f || age > 8.0f || distance > 24.0) {
                continue;
            }
            float fade = 1.0f - age / 8.0f;
            float strength = fade * fade * (float) (1.0 - distance / 24.0) * (clash.mutual() ? 0.9f : 0.6f);
            if (strength > flash[0]) {
                // Both spells' light, washed toward white.
                flash[0] = strength;
                flash[1] = 0.5f + 0.25f * (clash.styleA.glowR + clash.styleB.glowR);
                flash[2] = 0.5f + 0.25f * (clash.styleA.glowG + clash.styleB.glowG);
                flash[3] = 0.5f + 0.25f * (clash.styleA.glowB + clash.styleB.glowB);
            }
        }
        return flash;
    }

    /**
     * A clash close by shakes the view: a rumble while the beams strain against each other, a jolt when it blows.
     * Low priority, so it adds to whatever a cinematic camera (the Pensieve's) has already set.
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOW)
    public static void onCameraAngles(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (CLASHES.isEmpty() || minecraft.level == null) {
            return;
        }
        double now = minecraft.level.getGameTime() + event.getPartialTick();
        Vec3 camera = event.getCamera().getPosition();
        float shake = 0.0f;
        for (Clash clash : CLASHES) {
            double distance = clash.pos.distanceTo(camera);
            if (distance > SHAKE_RANGE) {
                continue;
            }
            float age = (float) (now - clash.start);
            float near = (float) (1.0 - distance / SHAKE_RANGE);
            float rumble = age < clash.lock() ? 0.35f * Math.min(1.0f, age / 3.0f) : 0.0f;
            float since = age - clash.lock();
            float jolt = since >= 0.0f && since < 14.0f ? (clash.mutual() ? 2.2f : 1.2f) * (1.0f - since / 14.0f)
                    * (1.0f - since / 14.0f) : 0.0f;
            shake = Math.max(shake, (rumble + jolt) * near);
        }
        if (shake <= 0.001f) {
            return;
        }
        float t = (float) (now % 20000.0); // absolute world time would saturate Mth.sin's table in an old world
        event.setPitch(event.getPitch() + shake * (Mth.sin(t * 2.9f) + 0.5f * Mth.sin(t * 7.1f)) * 0.6f);
        event.setYaw(event.getYaw() + shake * (Mth.sin(t * 3.7f + 1.3f) + 0.5f * Mth.sin(t * 6.3f)) * 0.6f);
        event.setRoll(event.getRoll() + shake * Mth.sin(t * 4.3f + 2.1f) * 0.4f);
    }

    /** The light the beams, strikes and clashes throw on what is round them (SpellLight). */
    static void lights(float partial, double now, SpellLight.Sink sink) {
        for (Beam beam : BEAMS.values()) {
            BeamStyle s = beam.style;
            float strength = 0.55f;
            if (beam.gone()) {
                float since = (float) (now - beam.goneAt);
                if (since > s.afterglow) {
                    continue;
                }
                strength *= 1.0f - since / s.afterglow;
            }
            sink.add(beam.head(partial), s.glowR, s.glowG, s.glowB, 4.5f, strength);
        }
        for (Impact impact : IMPACTS) {
            float age = (float) (now - impact.start);
            if (age < 0.0f || age > 14.0f) {
                continue;
            }
            BeamStyle s = impact.style;
            sink.add(impact.pos, s.glowR, s.glowG, s.glowB, 5.5f * impact.scale(),
                    0.9f * (float) Math.pow(1.0f - age / 14.0f, 1.5));
        }
        for (Clash clash : CLASHES) {
            float age = (float) (now - clash.start);
            float since = age - clash.lock();
            float strength = since < 0.0f ? 0.6f : 1.0f * (float) Math.pow(Math.max(0.0f, 1.0f - since / 16.0f), 1.5);
            if (age < 0.0f || strength <= 0.01f) {
                continue;
            }
            float r = 0.5f + 0.25f * (clash.styleA.glowR + clash.styleB.glowR);
            float g = 0.5f + 0.25f * (clash.styleA.glowG + clash.styleB.glowG);
            float b = 0.5f + 0.25f * (clash.styleA.glowB + clash.styleB.glowB);
            sink.add(clash.pos, r, g, b, 7.0f, strength);
        }
    }

    /** Beams, strikes and clashes belong to the level they were in (a dimension change, a respawn). */
    @SubscribeEvent
    public static void onLevelUnload(net.minecraftforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            BEAMS.clear();
            IMPACTS.clear();
            CLASHES.clear();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BEAMS.clear();
        IMPACTS.clear();
        CLASHES.clear();
    }
}
